package com.forestry.counter.data.repository

import androidx.room.withTransaction
import com.forestry.counter.data.local.ForestryDatabase
import com.forestry.counter.data.local.entity.WoodDepotEntity
import com.forestry.counter.data.sync.hierarchy.BusinessMutationGuard
import com.forestry.counter.domain.model.WoodDepotDraft
import com.forestry.counter.domain.model.WoodDepotKind
import com.forestry.counter.domain.model.WoodDepotLocation
import com.forestry.counter.domain.model.WoodDepotLocationSource
import com.forestry.counter.domain.model.WoodDepotOwnerScope
import com.forestry.counter.domain.model.WoodDepotRecord
import com.forestry.counter.domain.model.requireWoodDepotId
import com.forestry.counter.domain.repository.IdentityRepository
import com.forestry.counter.domain.repository.WoodDepotAccess
import com.forestry.counter.domain.repository.WoodDepotRepository
import java.security.MessageDigest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Canonical local ownership, real Room transactions and initiating identity generation. */
@OptIn(ExperimentalCoroutinesApi::class)
class WoodDepotRepositoryImpl(
    private val database: ForestryDatabase,
    private val identity: IdentityRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : WoodDepotRepository {
    private val dao = database.woodDepotDao()
    private val gate = database.woodDataGate
    private val accessOwner = Any()
    private val mutationGuard = BusinessMutationGuard(identity)
    private val json = Json { encodeDefaults = true }

    private class CapturedAccess(
        val owner: Any,
        override val accountId: String?,
        val current: () -> Boolean,
        val epoch: Long,
    ) : WoodDepotAccess {
        val scope = if (accountId == null) WoodDepotOwnerScope.LOCAL_DEVICE else WoodDepotOwnerScope.PERSONAL
        val actor = accountId ?: "anonymous"
    }

    override fun captureAccess(): WoodDepotAccess {
        val account = identity.session.value?.accountId
        val result = CapturedAccess(accessOwner, account, mutationGuard.capture(account), gate.capture())
        requireCurrent(result)
        return result
    }

    override fun isAccessCurrent(access: WoodDepotAccess): Boolean =
        (access as? CapturedAccess)?.let { it.owner === accessOwner && it.current() && gate.isCurrent(it.epoch) } == true

    override fun observeDepots(): Flow<List<WoodDepotRecord>> = flow {
        var subscribed = false
        combine(identity.session, identity.sessionGeneration, gate.state) { session, generation, state -> Triple(session, generation, state) }
            .flatMapLatest {
                flow {
                    // A changed identity clears already displayed rows before any delayed SQL read.
                    if (subscribed) emit(emptyList())
                    subscribed = true
                    val access = runCatching { captureAccess() as CapturedAccess }.getOrNull()
                    if (access == null) {
                        emit(emptyList())
                    } else {
                        dao.observeActive(access.scope.name, access.accountId).collect {
                            val records = database.withTransaction {
                                if (!isAccessCurrent(access)) emptyList()
                                else {
                                    val currentRows = dao.getActive(access.scope.name, access.accountId)
                                    val result = currentRows.filter { it.isOwnedBy(access) && it.deletedAt == null }
                                        .map { it.toRecord() }
                                    if (isAccessCurrent(access)) result else emptyList()
                                }
                            }
                            emit(if (isAccessCurrent(access)) records else emptyList())
                        }
                    }
                }
            }.collect { emit(it) }
    }

    override suspend fun getDepot(id: String, access: WoodDepotAccess): WoodDepotRecord? {
        requireWoodDepotId(id)
        val captured = requireCurrent(access)
        val result = transaction(captured) {
            requireCurrent(captured)
            val row = dao.getByIdIncludingDeleted(id)
            val record = row?.takeIf { it.isOwnedBy(captured) && it.deletedAt == null }?.toRecord()
            requireCurrent(captured)
            record
        }
        requireCurrent(captured)
        return result
    }

    override suspend fun createDepot(id: String, draft: WoodDepotDraft, access: WoodDepotAccess): WoodDepotRecord {
        requireWoodDepotId(id)
        val captured = requireCurrent(access)
        val fingerprint = fingerprint(draft)
        val result = transaction(captured) {
            requireCurrent(captured)
            val previous = dao.getByIdIncludingDeleted(id)
            val saved = if (previous != null) {
                check(previous.isOwnedBy(captured) && previous.deletedAt == null && previous.creationFingerprint == fingerprint) {
                    "WOOD_DEPOT_CREATE_CONFLICT"
                }
                previous.toRecord()
            } else {
                val now = now()
                val entity = WoodDepotEntity(
                    id, draft.name, draft.kind.name, draft.notes, draft.location?.latitude,
                    draft.location?.longitude, draft.location?.accuracyM, draft.location?.source?.name,
                    captured.scope.name, captured.accountId, fingerprint, 1,
                    now, captured.actor, now, captured.actor, null,
                )
                dao.insert(entity)
                entity.toRecord()
            }
            requireCurrent(captured)
            saved
        }
        requireCurrent(captured)
        return result
    }

    override suspend fun updateDepot(
        id: String, expectedRevision: Int, draft: WoodDepotDraft, access: WoodDepotAccess,
    ): WoodDepotRecord {
        requireWoodDepotId(id)
        require(expectedRevision > 0) { "WOOD_DEPOT_INVALID_REVISION" }
        val captured = requireCurrent(access)
        val result = transaction(captured) {
            requireCurrent(captured)
            requireOwnedLive(id, captured)
            val changed = dao.compareAndSet(id, captured.scope.name, captured.accountId, expectedRevision,
                draft.name, draft.kind.name, draft.notes, draft.location?.latitude, draft.location?.longitude,
                draft.location?.accuracyM, draft.location?.source?.name, now(), captured.actor)
            check(changed == 1) { "WOOD_DEPOT_REVISION_CONFLICT" }
            val saved = requireOwnedLive(id, captured).toRecord()
            requireCurrent(captured)
            saved
        }
        requireCurrent(captured)
        return result
    }

    override suspend fun deleteDepot(id: String, expectedRevision: Int, access: WoodDepotAccess): WoodDepotRecord {
        requireWoodDepotId(id)
        require(expectedRevision > 0) { "WOOD_DEPOT_INVALID_REVISION" }
        val captured = requireCurrent(access)
        val result = transaction(captured) {
            requireCurrent(captured)
            requireOwnedLive(id, captured)
            check(dao.softDelete(id, captured.scope.name, captured.accountId, expectedRevision, now(), captured.actor) == 1) {
                "WOOD_DEPOT_REVISION_CONFLICT"
            }
            val saved = checkNotNull(dao.getByIdIncludingDeleted(id)).toRecord()
            requireCurrent(captured)
            saved
        }
        requireCurrent(captured)
        return result
    }

    private suspend fun <T> transaction(access: CapturedAccess, block: suspend () -> T): T = try {
        gate.withAccess(access.epoch) { database.withTransaction { requireCurrent(access); block() } }
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (error: IllegalStateException) {
        requireCurrent(access)
        throw error
    }

    private fun requireCurrent(access: WoodDepotAccess): CapturedAccess {
        check(isAccessCurrent(access)) { "WOOD_DEPOT_ACCESS_CHANGED" }
        return access as CapturedAccess
    }

    private suspend fun requireOwnedLive(id: String, access: CapturedAccess): WoodDepotEntity {
        val row = dao.getByIdIncludingDeleted(id)
        check(row != null && row.isOwnedBy(access) && row.deletedAt == null) { "WOOD_DEPOT_UNAVAILABLE" }
        return row
    }

    private fun WoodDepotEntity.isOwnedBy(access: CapturedAccess) =
        ownerScope == access.scope.name && ownerAccountId == access.accountId

    private fun now(): Long = clock().also { require(it >= 0) { "WOOD_DEPOT_INVALID_TIMESTAMP" } }

    private fun fingerprint(draft: WoodDepotDraft): String = MessageDigest.getInstance("SHA-256")
        .digest(json.encodeToString(draft).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun WoodDepotEntity.toRecord(): WoodDepotRecord {
        check((latitude == null) == (longitude == null) && (latitude == null) == (locationSource == null)) {
            "WOOD_DEPOT_CORRUPT_LOCATION"
        }
        check(latitude != null || accuracyM == null) { "WOOD_DEPOT_CORRUPT_LOCATION" }
        return WoodDepotRecord(depotId, WoodDepotDraft(name, WoodDepotKind.valueOf(kind), notes,
            latitude?.let { WoodDepotLocation(it, checkNotNull(longitude), accuracyM,
                WoodDepotLocationSource.valueOf(checkNotNull(locationSource))) }),
            WoodDepotOwnerScope.valueOf(ownerScope), ownerAccountId, revision,
            createdAt, createdBy, updatedAt, updatedBy, deletedAt)
    }
}
