package com.forestry.counter.data.sync.hierarchy

import com.forestry.counter.data.local.dao.ParcelSyncDao
import com.forestry.counter.data.local.entity.BusinessReadOwner
import com.forestry.counter.data.local.entity.BusinessReadParcelParent
import com.forestry.counter.domain.repository.IdentityRepository
import com.forestry.counter.domain.repository.IdentitySessionGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

/** Shared local-read policy: explicit cloud ownership is enforced without claiming historical local data. */
@OptIn(ExperimentalCoroutinesApi::class)
class BusinessReadAccess(private val identity: IdentityRepository, private val dao: ParcelSyncDao) {
    class AccessSnapshot internal constructor(
        val accountId: String?,
        val sessionGuard: IdentitySessionGuard?,
        internal val owners: List<BusinessReadOwner>,
        internal val parents: List<BusinessReadParcelParent>,
        internal val ownershipEpoch: Long,
        internal val sessionGeneration: Long,
    )

    private val monitor = Any()
    private var ownershipEpoch = 0L
    private var observed: Pair<List<BusinessReadOwner>, List<BusinessReadParcelParent>>? = null
    private var observing: Job? = null
    private val ownershipChanges = MutableStateFlow(0L)

    /** Application owns this job. It also invalidates synchronous guards during PDF production. */
    fun startObserving(scope: CoroutineScope): Job = synchronized(monitor) {
        observing?.takeIf { it.isActive } ?: scope.launch {
            combine(dao.observeBusinessReadOwners(), dao.observeBusinessReadParcelParents()) { owners, parents ->
                owners to parents
            }.collect { readCanonicalMappings() }
        }.also { observing = it }
    }

    suspend fun capture(): AccessSnapshot {
        val generation = identity.sessionGeneration.value
        val account = identity.session.value?.accountId
        val guard = account?.let { identity.captureSessionGuard(it).getOrNull() }
        val mappings = readCanonicalMappings()
        return AccessSnapshot(account, guard, mappings.owners, mappings.parents, mappings.epoch, generation)
    }

    fun isCurrent(snapshot: AccessSnapshot): Boolean =
        synchronized(monitor) { snapshot.ownershipEpoch == ownershipEpoch } &&
            identity.sessionGeneration.value == snapshot.sessionGeneration &&
            identity.session.value?.accountId == snapshot.accountId &&
            if (snapshot.accountId == null) identity.session.value == null
            else snapshot.sessionGuard?.let(identity::isSessionGuardCurrent) == true

    /** Reads both mappings in one SQLite transaction; late session/mapping changes fail closed. */
    suspend fun revalidate(snapshot: AccessSnapshot): Boolean {
        if (!isCurrent(snapshot)) return false
        val mappings = readCanonicalMappings()
        return mappings.owners == snapshot.owners && mappings.parents == snapshot.parents && isCurrent(snapshot)
    }

    /** Source-scoped guard for a transaction that itself creates new, unrelated local IDs. */
    suspend fun revalidateSources(snapshot: AccessSnapshot, forestIds: Set<String>, parcelIds: Set<String> = emptySet()): Boolean {
        if (!isCurrent(snapshot)) return false
        // This call can be nested in the copy transaction: unrelated newly inserted parents
        // must not advance the application epoch before commit or invalidate this very copy.
        // Keep the originally captured identity; compare source mappings against current SQL.
        val mappings = readCanonicalMappings(publish = false)
        val latest = AccessSnapshot(snapshot.accountId, snapshot.sessionGuard, mappings.owners, mappings.parents,
            snapshot.ownershipEpoch, snapshot.sessionGeneration)
        if (!isCurrent(snapshot) || !isCurrent(latest)) return false
        val relevantParents = snapshot.parents.filter { it.parcelId in parcelIds }
        if (latest.parents.filter { it.parcelId in parcelIds } != relevantParents) return false
        val parentIds = relevantParents.flatMap { listOfNotNull(it.forestId, it.legacyGroupId) }.toSet() + forestIds
        fun relevant(owners: List<BusinessReadOwner>) = owners.filter {
            (it.scope == "FOREST" && it.objectId in parentIds) || (it.scope == "PARCEL" && it.objectId in parcelIds)
        }
        return relevant(latest.owners) == relevant(snapshot.owners) &&
            forestIds.all { canReadForest(it, latest) } && parcelIds.all { canReadParcelle(it, latest) } && isCurrent(snapshot)
    }

    fun canReadForest(id: String, snapshot: AccessSnapshot): Boolean = isCurrent(snapshot) &&
        permitted(snapshot.owners.filter { it.scope == "FOREST" && it.objectId == id }.map { it.accountId }, snapshot)

    fun canReadParcelle(id: String, snapshot: AccessSnapshot): Boolean {
        if (!isCurrent(snapshot)) return false
        val parent = snapshot.parents.firstOrNull { it.parcelId == id }
        val parentIds = listOfNotNull(parent?.forestId, parent?.legacyGroupId)
        val owners = snapshot.owners.filter { (it.scope == "PARCEL" && it.objectId == id) ||
            (it.scope == "FOREST" && it.objectId in parentIds) }.map { it.accountId }
        return permitted(owners, snapshot)
    }

    suspend fun canReadForest(id: String): Boolean {
        val snapshot = capture()
        return canReadForest(id, snapshot) && revalidate(snapshot)
    }
    suspend fun canReadParcelle(id: String): Boolean {
        val snapshot = capture()
        return canReadParcelle(id, snapshot) && revalidate(snapshot)
    }

    /**
     * The first value is the actual authorised Room result, so one-shot `.first()` readers do not
     * mistake a loading sentinel for a missing row. Known authority changes clear cached data
     * before suspended filtering; each notification is checked against transactional SQL and the
     * new result is still revalidated at emission.
     */
    fun <T> observe(source: Flow<T>, denied: T, filter: suspend (T, AccessSnapshot) -> T): Flow<T> = flow {
        // Room notifications are only wake-up signals: separate queries and independent readers
        // can deliver old or mixed tuples. Only a serialized, transactional SQL read advances the
        // shared authority epoch; every reader listens to that epoch to recover after invalidation.
        var identityObserved = false
        var previousEpoch: Long? = null
        combine(identity.session, identity.sessionGeneration) { session, generation -> session to generation }
            .flatMapLatest { (session, generation) ->
                flow {
                    if (identityObserved) emit(denied)
                    identityObserved = true
                    combine(source, dao.observeBusinessReadOwners(), dao.observeBusinessReadParcelParents(), ownershipChanges) {
                            value, _, _, _ -> value
                    }.transformLatest { value ->
                        val previous = previousEpoch
                        val alreadyInvalidated = previous != null && previous != ownershipChanges.value
                        if (alreadyInvalidated) emit(denied)
                        val snapshot = capture()
                        previousEpoch = snapshot.ownershipEpoch
                        // Preserve first(): bootstrap or delayed SQL notifications cannot produce
                        // a synthetic missing row. A real authority transition still clears a
                        // previously captured result before any suspended filter work.
                        if (previous != null && previous != snapshot.ownershipEpoch && !alreadyInvalidated) emit(denied)
                        if (snapshot.accountId != session?.accountId || snapshot.sessionGeneration != generation || !isCurrent(snapshot)) emit(denied)
                        else {
                            val filtered = filter(value, snapshot)
                            emit(if (revalidate(snapshot)) filtered else denied)
                        }
                    }.collect { emit(it) }
                }
            }.collect { emit(it) }
    }

    private fun permitted(owners: List<String>, snapshot: AccessSnapshot): Boolean = owners.distinct().let {
        it.isEmpty() || (it.size == 1 && snapshot.accountId != null && it.single() == snapshot.accountId)
    }

    private data class CanonicalMappings(
        val owners: List<BusinessReadOwner>,
        val parents: List<BusinessReadParcelParent>,
        val epoch: Long,
    )

    /**
     * Never publish an old subscriber's notification as current authority. Room serializes the
     * transaction, including publication, so a late reader cannot overwrite newer SQL authority.
     * No coroutine mutex is held while entering SQLite: callers may already own a Room transaction.
     */
    private suspend fun readCanonicalMappings(publish: Boolean = true): CanonicalMappings {
        var result: CanonicalMappings? = null
        dao.withParcelMergeTransaction {
            val owners = dao.getBusinessReadOwners()
            val parents = dao.getBusinessReadParcelParents()
            synchronized(monitor) {
                val next = owners to parents
                if (publish && observed != next) {
                    observed = next
                    ownershipEpoch += 1
                    ownershipChanges.value = ownershipEpoch
                }
                result = CanonicalMappings(owners, parents, ownershipEpoch)
            }
        }
        return checkNotNull(result)
    }
}
