package com.forestry.counter.data.cartography

import androidx.room.withTransaction
import com.forestry.counter.data.local.ForestryDatabase
import com.forestry.counter.data.local.entity.CartographicStoreIdentityEntity
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.domain.repository.*
import com.forestry.counter.domain.usecase.maps.MapDataScope
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Shared map authority for layers, measurements, traces and offline packs.
 * Capture is opaque; neither a profession nor a local-storage flag grants access.
 * Lock ordering is always purge gate, then Room transaction. Do not nest transaction().
 */
class CartographicScopeAuthority(
    private val db: ForestryDatabase,
    private val identity: IdentityRepository,
    private val reads: BusinessReadAccess,
) {
    private val owner = Any()
    data class Binding(val kind: String, val id: String, val parents: List<String?> = emptyList())
    class Token internal constructor(
        internal val owner: Any,
        internal val snapshot: BusinessReadAccess.AccessSnapshot,
        internal val epoch: Long,
        override val scope: MapDataScope,
        val binding: Binding,
        override val restorationKey: String,
    ) : CartographicMeasurementAccess {
        val ownerKey: String get() = snapshot.accountId?.let { "ACCOUNT:$it" } ?: "LOCAL"
        override val localDevice: Boolean get() = snapshot.accountId == null
        /** Includes store identity, account, canonical parent binding and full ancestor chain. */
        val authorityKey: String get() = restorationKey
    }

    suspend fun capture(scope: MapDataScope): CartographicStoreResult<Token> = result {
        val snapshot = reads.capture()
        val epoch = try { db.woodDataGate.capture() } catch (_: Exception) { fail(CartographicStoreError.ACCESS_CHANGED) }
        val token = try {
            db.woodDataGate.withAccess(epoch) {
                db.withTransaction {
                    if (!reads.revalidate(snapshot)) fail(CartographicStoreError.ACCESS_CHANGED)
                    val binding = binding(scope, snapshot)
                    val dao = db.cartographicMeasurementDao()
                    if (dao.storeIdentity() == null) dao.insertStoreIdentity(CartographicStoreIdentityEntity(localId = UUID.randomUUID().toString()))
                    val store = dao.storeIdentity() ?: fail(CartographicStoreError.IO)
                    if (runCatching { UUID.fromString(store.localId).toString() }.getOrNull() != store.localId) fail(CartographicStoreError.CORRUPT)
                    val ownerKey = snapshot.accountId?.let { "ACCOUNT:$it" } ?: "LOCAL"
                    val key = sha256(Json.encodeToString(listOf(store.localId, ownerKey, binding.kind, binding.id) + binding.parents))
                    Token(owner, snapshot, epoch, scope, binding, key).also { validate(it) }
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            if (!db.woodDataGate.isCurrent(epoch) || !reads.isCurrent(snapshot)) fail(CartographicStoreError.ACCESS_CHANGED)
            throw failure
        }
        requireCurrent(token)
        token
    }

    fun isCurrent(token: Token): Boolean = token.owner === owner &&
        db.woodDataGate.isCurrent(token.epoch) && reads.isCurrent(token.snapshot)

    fun requireCurrent(token: Token) {
        if (!isCurrent(token)) fail(CartographicStoreError.ACCESS_CHANGED)
    }

    fun accessChanges(): Flow<Unit> {
        val authority = combine(identity.session, identity.sessionGeneration, db.woodDataGate.state,
            db.parcelSyncDao().observeBusinessReadOwners(), db.parcelSyncDao().observeBusinessReadParcelParents()) { _, _, _, _, _ -> Unit }
        return combine(authority, db.foretDao().getAll(), db.parcelleDao().getAllParcelles(),
            db.cartographicMeasurementDao().observePlotParents(), db.groupDao().getAllGroups()) { _, _, _, _, _ -> Unit }
    }

    /** Each read and each write re-evaluates the captured account and original ancestors. */
    suspend fun <T> transaction(token: Token, mutation: Boolean = false, block: suspend (Token) -> T): T {
        requireCurrent(token)
        var committed = false
        try {
            val value = db.woodDataGate.withAccess(token.epoch) {
                db.withTransaction {
                    validate(token)
                    val result = block(token)
                    validate(token)
                    result
                }.also { committed = mutation }
            }
            requireCurrent(token)
            return value
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            if (committed) fail(CartographicStoreError.UNKNOWN_RESULT)
            if (!isCurrent(token)) fail(CartographicStoreError.ACCESS_CHANGED)
            throw failure
        }
    }

    private suspend fun validate(token: Token) {
        requireCurrent(token)
        if (!reads.revalidate(token.snapshot) || binding(token.scope, token.snapshot) != token.binding) fail(CartographicStoreError.ACCESS_CHANGED)
        requireCurrent(token)
    }

    private suspend fun binding(scope: MapDataScope, snapshot: BusinessReadAccess.AccessSnapshot): Binding {
        suspend fun parcel(id: String): List<String?> {
            val parcel = db.parcelleDao().getParcelleById(id) ?: fail(CartographicStoreError.UNAVAILABLE)
            if (!reads.canReadParcelle(id, snapshot)) fail(CartographicStoreError.UNAVAILABLE)
            if (parcel.foretId != null && db.foretDao().getById(parcel.foretId) == null) fail(CartographicStoreError.UNAVAILABLE)
            return listOf(parcel.parcelleId, parcel.foretId, parcel.forestOwnerId)
        }
        return when (scope) {
            MapDataScope.Free -> Binding("FREE", "")
            MapDataScope.AllForests -> Binding("ALL_FORESTS", "")
            MapDataScope.Invalid -> fail(CartographicStoreError.INVALID_INPUT)
            is MapDataScope.Forest -> {
                val canonical = db.foretDao().getByIdAny(scope.id)
                if (!reads.canReadForest(scope.id, snapshot)) fail(CartographicStoreError.UNAVAILABLE)
                if (canonical != null) {
                    if (canonical.deletedAt != null) fail(CartographicStoreError.UNAVAILABLE)
                    Binding("FOREST", scope.id)
                } else {
                    if (db.groupDao().getGroupById(scope.id) == null) fail(CartographicStoreError.UNAVAILABLE)
                    Binding("LEGACY_FOREST_GROUP", scope.id)
                }
            }
            is MapDataScope.Parcel -> Binding("PARCEL", scope.id, parcel(scope.id))
            is MapDataScope.Plot -> {
                val plot = db.placetteDao().getPlacetteById(scope.id) ?: fail(CartographicStoreError.UNAVAILABLE)
                Binding("PLOT", scope.id, parcel(plot.parcelleOwnerId))
            }
        }
    }

    suspend fun <T> result(block: suspend () -> T): CartographicStoreResult<T> = withContext(Dispatchers.IO) {
        try { CartographicStoreResult.Success(block()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: CartographicAuthorityFailure) { CartographicStoreResult.Failure(failure.reason) }
        catch (_: Exception) { CartographicStoreResult.Failure(CartographicStoreError.IO) }
    }
    private fun fail(reason: CartographicStoreError): Nothing = throw CartographicAuthorityFailure(reason)
    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
class CartographicAuthorityFailure(val reason: CartographicStoreError) : RuntimeException()
