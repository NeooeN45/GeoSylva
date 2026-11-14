package com.forestry.counter.data.repository

import androidx.room.withTransaction
import com.forestry.counter.data.local.ForestryDatabase
import com.forestry.counter.data.local.entity.CartographicMeasurementEntity
import com.forestry.counter.data.local.entity.CartographicStoreIdentityEntity
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.domain.calculation.cartography.*
import com.forestry.counter.domain.repository.*
import com.forestry.counter.domain.usecase.maps.MapDataScope
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One encrypted local store for every map host; no profession grants authority. */
class CartographicMeasurementRepositoryImpl(
    private val db: ForestryDatabase,
    private val identity: IdentityRepository,
    private val reads: BusinessReadAccess,
    private val clock: () -> Long = System::currentTimeMillis,
) : CartographicMeasurementRepository {
    private val dao = db.cartographicMeasurementDao()
    private val owner = Any()
    private val json = Json { encodeDefaults = true }
    private data class Binding(val kind: String, val id: String, val parents: List<String?> = emptyList())
    private class Access(
        val owner: Any,
        val snapshot: BusinessReadAccess.AccessSnapshot,
        val epoch: Long,
        override val scope: MapDataScope,
        val binding: Binding,
        override val restorationKey: String,
    ) : CartographicMeasurementAccess {
        val ownerKey = snapshot.accountId?.let { "ACCOUNT:$it" } ?: "LOCAL"
        override val localDevice get() = snapshot.accountId == null
    }

    override suspend fun captureAccess(scope: MapDataScope): CartographicStoreResult<CartographicMeasurementAccess> = result {
        val snapshot = reads.capture()
        val epoch = runCatching { db.woodDataGate.capture() }.getOrElse { fail(CartographicStoreError.ACCESS_CHANGED) }
        db.woodDataGate.withAccess(epoch) {
            db.withTransaction {
                if (!reads.revalidate(snapshot)) fail(CartographicStoreError.ACCESS_CHANGED)
                val binding = binding(scope, snapshot)
                if (dao.storeIdentity() == null) dao.insertStoreIdentity(CartographicStoreIdentityEntity(localId = UUID.randomUUID().toString()))
                val store = dao.storeIdentity() ?: fail(CartographicStoreError.IO)
                if (runCatching { UUID.fromString(store.localId).toString() }.getOrNull() != store.localId) fail(CartographicStoreError.CORRUPT)
                val ownerKey = snapshot.accountId?.let { "ACCOUNT:$it" } ?: "LOCAL"
                val fingerprint = hash(json.encodeToString(listOf(store.localId, ownerKey, binding.kind, binding.id) + binding.parents))
                Access(owner, snapshot, epoch, scope, binding, fingerprint).also {
                    if (!reads.revalidate(snapshot) || !isCurrent(it)) fail(CartographicStoreError.ACCESS_CHANGED)
                }
            }
        }
    }

    override fun isCurrent(access: CartographicMeasurementAccess): Boolean = (access as? Access)?.let {
        it.owner === owner && db.woodDataGate.isCurrent(it.epoch) && reads.isCurrent(it.snapshot)
    } == true

    override fun accessChanges(): Flow<Unit> {
        val authority = combine(identity.session, identity.sessionGeneration, db.woodDataGate.state,
            db.parcelSyncDao().observeBusinessReadOwners(), db.parcelSyncDao().observeBusinessReadParcelParents()) { _, _, _, _, _ -> Unit }
        return combine(authority, db.foretDao().getAll(), db.parcelleDao().getAllParcelles(), dao.observePlotParents()) { _, _, _, _ -> Unit }
    }

    override suspend fun save(input: CartographicMeasurementInput, access: CartographicMeasurementAccess): CartographicStoreResult<SavedCartographicMeasurement> = result {
        current(access)
        canonicalId(input.id)
        input.sourceMeasurementId?.let {
            canonicalId(it)
            if (it == input.id) fail(CartographicStoreError.INVALID_INPUT)
        }
        if (input.points.size > CartographicMeasureCalculator.MAX_POINTS) fail(CartographicStoreError.INVALID_INPUT)
        val points = input.points.toList()
        val name = input.name?.trim()?.takeIf { it.isNotEmpty() }
        if (name != null && (name.length > MAX_NAME_LENGTH || name.any { it.isISOControl() })) fail(CartographicStoreError.INVALID_INPUT)
        val calculation = CartographicMeasureCalculator.calculate(input.kind, points) as? CartographicMeasureResult.Available
            ?: fail(CartographicStoreError.INVALID_INPUT)
        val geometry = json.encodeToString(Geometry(points = points.map { Coordinate(it.latitude, it.longitude) }))
        transaction(access, mutation = true) { a ->
            input.sourceMeasurementId?.let { id ->
                val original = dao.byId(id) ?: fail(CartographicStoreError.UNAVAILABLE)
                requireOwned(original, a)
                saved(original, a.scope)
            }
            val previous = dao.byId(input.id)
            if (previous != null) {
                requireOwned(previous, a)
                if (previous.name != name || previous.kind != input.kind.name || previous.geometryJson != geometry ||
                    previous.valueSi != calculation.valueSi || previous.methodId != calculation.methodId || previous.methodVersion != calculation.methodVersion ||
                    previous.sourceMeasurementId != input.sourceMeasurementId) {
                    fail(CartographicStoreError.CONFLICT)
                }
                saved(previous, a.scope)
            } else {
                val now = clock().takeIf { it > 0 } ?: fail(CartographicStoreError.IO)
                val row = CartographicMeasurementEntity(input.id, a.ownerKey, a.binding.kind, a.binding.id,
                    name, input.kind.name, geometry, calculation.valueSi, calculation.methodId,
                    calculation.methodVersion, now, "", input.sourceMeasurementId)
                dao.insert(row.copy(payloadSha256 = payloadHash(row)))
                val readBack = dao.byId(input.id) ?: fail(CartographicStoreError.IO)
                requireOwned(readBack, a)
                saved(readBack, a.scope)
            }
        }
    }

    override suspend fun list(access: CartographicMeasurementAccess): CartographicStoreResult<List<SavedCartographicMeasurement>> = result {
        transaction(access) { a -> dao.inScope(a.ownerKey, a.binding.kind, a.binding.id).map { saved(it, a.scope) } }
    }

    override suspend fun read(id: String, access: CartographicMeasurementAccess): CartographicStoreResult<SavedCartographicMeasurement> = result {
        current(access)
        canonicalId(id)
        transaction(access) { a ->
            val row = dao.byId(id) ?: fail(CartographicStoreError.UNAVAILABLE)
            requireOwned(row, a)
            saved(row, a.scope)
        }
    }

    private suspend fun <T> transaction(access: CartographicMeasurementAccess, mutation: Boolean = false, block: suspend (Access) -> T): T {
        val a = current(access)
        var committed = false
        try {
            val value = db.woodDataGate.withAccess(a.epoch) {
                db.withTransaction {
                    validate(a)
                    val value = block(a)
                    validate(a)
                    value
                }.also { committed = mutation }
            }
            current(a)
            return value
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            if (committed) fail(CartographicStoreError.UNKNOWN_RESULT)
            if (!isCurrent(a)) fail(CartographicStoreError.ACCESS_CHANGED)
            throw failure
        }
    }

    private fun current(access: CartographicMeasurementAccess): Access {
        if (!isCurrent(access)) fail(CartographicStoreError.ACCESS_CHANGED)
        return access as Access
    }

    private suspend fun validate(a: Access) {
        current(a)
        if (!reads.revalidate(a.snapshot)) fail(CartographicStoreError.ACCESS_CHANGED)
        if (binding(a.scope, a.snapshot) != a.binding) fail(CartographicStoreError.ACCESS_CHANGED)
        current(a)
    }

    private suspend fun binding(scope: MapDataScope, snapshot: BusinessReadAccess.AccessSnapshot): Binding {
        suspend fun parcel(id: String): List<String?> {
            val p = db.parcelleDao().getParcelleById(id) ?: fail(CartographicStoreError.UNAVAILABLE)
            if (!reads.canReadParcelle(id, snapshot)) fail(CartographicStoreError.UNAVAILABLE)
            if (p.foretId != null && db.foretDao().getById(p.foretId) == null) fail(CartographicStoreError.UNAVAILABLE)
            return listOf(p.parcelleId, p.foretId, p.forestOwnerId)
        }
        return when (scope) {
            MapDataScope.Free -> Binding("FREE", "")
            MapDataScope.AllForests -> Binding("ALL_FORESTS", "")
            MapDataScope.Invalid -> fail(CartographicStoreError.INVALID_INPUT)
            is MapDataScope.Forest -> {
                if (db.foretDao().getById(scope.id) == null || !reads.canReadForest(scope.id, snapshot)) fail(CartographicStoreError.UNAVAILABLE)
                Binding("FOREST", scope.id)
            }
            is MapDataScope.Parcel -> Binding("PARCEL", scope.id, parcel(scope.id))
            is MapDataScope.Plot -> {
                val p = db.placetteDao().getPlacetteById(scope.id) ?: fail(CartographicStoreError.UNAVAILABLE)
                Binding("PLOT", scope.id, parcel(p.parcelleOwnerId))
            }
        }
    }

    private fun requireOwned(row: CartographicMeasurementEntity, access: Access) {
        if (row.ownerKey != access.ownerKey || row.scopeKind != access.binding.kind || row.scopeId != access.binding.id) fail(CartographicStoreError.UNAVAILABLE)
    }

    private fun saved(row: CartographicMeasurementEntity, scope: MapDataScope): SavedCartographicMeasurement {
        if (row.geometryJson.length > MAX_GEOMETRY_CHARACTERS || row.name?.let { it.length > MAX_NAME_LENGTH || it.any(Char::isISOControl) } == true ||
            row.methodId.length > 128 || row.payloadSha256 != payloadHash(row) ||
            !row.valueSi.isFinite() || row.valueSi <= 0 || row.recordedAt <= 0 || row.methodVersion < 1 || row.methodId.isBlank()) {
            fail(CartographicStoreError.CORRUPT)
        }
        val geometry = try { json.decodeFromString<Geometry>(row.geometryJson) } catch (_: Exception) { fail(CartographicStoreError.CORRUPT) }
        if (geometry.crs != "EPSG:4326" || geometry.coordinateOrder != "latitude_longitude" || geometry.version != 1 ||
            geometry.points.size > CartographicMeasureCalculator.MAX_POINTS) fail(CartographicStoreError.CORRUPT)
        val points = geometry.points.map { CartographicCoordinate(it.latitude, it.longitude) }
        val kind = try { CartographicMeasureKind.valueOf(row.kind) } catch (_: IllegalArgumentException) { fail(CartographicStoreError.CORRUPT) }
        if (points.size < (if (kind == CartographicMeasureKind.DISTANCE) 2 else 3) || points.any { !CartographicMeasureCalculator.isValidCoordinate(it) }) fail(CartographicStoreError.CORRUPT)
        return SavedCartographicMeasurement(row.id, scope, row.name, kind, points.toList(), row.valueSi,
            row.methodId, row.methodVersion, row.recordedAt,
            source = if (row.sourceMeasurementId == null) "manual_map" else "manual_map_copy",
            sourceMeasurementId = row.sourceMeasurementId)
    }

    private fun payloadHash(row: CartographicMeasurementEntity): String = hash(json.encodeToString(Integrity(
        row.id, row.ownerKey, row.scopeKind, row.scopeId, row.name, row.kind, row.geometryJson,
        row.valueSi, row.methodId, row.methodVersion, row.recordedAt, row.sourceMeasurementId)))

    private fun canonicalId(id: String) {
        if (runCatching { UUID.fromString(id).toString() }.getOrNull() != id) fail(CartographicStoreError.INVALID_INPUT)
    }

    private suspend fun <T> result(block: suspend () -> T): CartographicStoreResult<T> = withContext(Dispatchers.IO) {
        try { CartographicStoreResult.Success(block()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: StoreFailure) { CartographicStoreResult.Failure(failure.reason) }
        catch (_: Exception) { CartographicStoreResult.Failure(CartographicStoreError.IO) }
    }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun fail(reason: CartographicStoreError): Nothing = throw StoreFailure(reason)
    private class StoreFailure(val reason: CartographicStoreError) : RuntimeException()
    @Serializable private data class Coordinate(val latitude: Double, val longitude: Double)
    @Serializable private data class Geometry(val points: List<Coordinate>, val version: Int = 1,
        val crs: String = "EPSG:4326", val coordinateOrder: String = "latitude_longitude")
    @Serializable private data class Integrity(val id: String, val ownerKey: String, val scopeKind: String,
        val scopeId: String, val name: String?, val kind: String, val geometryJson: String, val valueSi: Double,
        val methodId: String, val methodVersion: Int, val recordedAt: Long, val sourceMeasurementId: String?)

    companion object {
        const val MAX_NAME_LENGTH = 160
        private const val MAX_GEOMETRY_CHARACTERS = 256_000
    }
}
