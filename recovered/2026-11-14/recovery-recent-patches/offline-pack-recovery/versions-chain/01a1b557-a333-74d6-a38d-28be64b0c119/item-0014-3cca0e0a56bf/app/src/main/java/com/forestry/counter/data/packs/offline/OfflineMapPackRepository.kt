package com.forestry.counter.data.packs.offline

import android.content.Context
import com.forestry.counter.data.cartography.CartographicScopeAuthority
import com.forestry.counter.data.local.ForestryDatabase
import com.forestry.counter.data.local.entity.OfflineMapPackEntity
import com.forestry.counter.domain.repository.CartographicStoreResult
import com.forestry.counter.domain.usecase.maps.MapDataScope
import com.forestry.counter.domain.usecase.pack.PackArtifactIdentity
import com.forestry.counter.domain.usecase.pack.PackManager
import com.quintessences.geosylva.cartography.mapbox.offline.*
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class OfflineDataset(val id: String, val version: String, val sha256: String, val source: String, val sourceDate: String?)

data class OfflinePackInput(
    val id: String,
    val name: String,
    val definition: OfflineMapDefinition,
    val datasets: List<OfflineDataset> = emptyList(),
    val territoryIds: Set<String> = emptySet(),
    val replacesPackId: String? = null,
)

enum class OfflinePackStatus { PAUSED, WAITING_WIFI, DOWNLOADING, VERIFYING, READY, INCOMPLETE, FAILED, RETAINED }

data class OfflinePackPlan(
    val input: OfflinePackInput,
    val nativeRegionId: String,
    val tiles: OfflineMapEstimate,
    val datasetTransferBytes: Long,
    val availableBytes: Long,
    val preparedAt: Long,
) {
    /** Lower bound only: SDK does not provide a style or peak temporary-allocation estimate. */
    val knownAdditionalBytes: Long get() = Math.addExact(tiles.tileStorageBytes, datasetTransferBytes)
    val knownMinimumFits: Boolean get() = knownAdditionalBytes < availableBytes
}

data class OfflineVersionChoice(val familyId: String, val targetId: String, val targetRequestSha256: String,
    val currentId: String?, val currentRequestSha256: String?, val targetStatus: String)

data class OfflinePackRemoval(val id: String, val nativeRegionId: String, val definitionHash: String,
    val status: String, val verifiedAt: Long?)

/** One encrypted Room registry, common authority and existing signed-dataset manager. */
class OfflineMapPackRepository(
    private val context: Context,
    private val db: ForestryDatabase,
    val authority: CartographicScopeAuthority,
    private val datasets: OfflinePackDatasets,
    private val gateway: OfflineMapGateway,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    constructor(context: Context, db: ForestryDatabase, authority: CartographicScopeAuthority,
        datasets: PackManager, gateway: OfflineMapGateway, clock: () -> Long = System::currentTimeMillis) :
        this(context, db, authority, SignedOfflinePackDatasets(datasets), gateway, clock)
    private val operations = ConcurrentHashMap<String, Mutex>()
    private fun lock(id: String) = operations.getOrPut(id) { Mutex() }
    private val dao get() = db.offlineMapPackDao()
    private val json = Json { encodeDefaults = true }
    suspend fun capture(scope: MapDataScope) = authority.capture(scope)
    fun isCurrent(token: CartographicScopeAuthority.Token) = authority.isCurrent(token)
    fun accessChanges() = authority.accessChanges()
    suspend fun requireAccess(token: CartographicScopeAuthority.Token) = authority.transaction(token) { Unit }
    fun freeBytes(): Long = context.filesDir.usableSpace
    suspend fun tileStoreBytes(): Long? = withContext(Dispatchers.IO) { MapboxOfflineResources.allocatedTileStoreBytes() }

    suspend fun list(token: CartographicScopeAuthority.Token): List<OfflineMapPackEntity> = withContext(Dispatchers.IO) {
        val rows = authority.transaction(token) { dao.inAuthority(token.authorityKey) }
        // Persisted readiness is never accepted as evidence after a restart, cache purge or source removal.
        rows.map { row ->
            owned(row, token)
            definition(row)
            when (row.status) {
                OfflinePackStatus.READY.name -> verify(row.id, token)
                OfflinePackStatus.DOWNLOADING.name, OfflinePackStatus.VERIFYING.name ->
                    update(row.id, token) { it.copy(status = OfflinePackStatus.PAUSED.name, issue = "INTERRUPTED") }
                else -> row
            }
        }
    }

    suspend fun prepare(input: OfflinePackInput, token: CartographicScopeAuthority.Token): OfflinePackPlan = withContext(Dispatchers.IO) {
        requireInput(input)
        authority.transaction(token) {
            input.replacesPackId?.let { id -> owned(dao.byId(id) ?: error("PREVIOUS_VERSION_MISSING"), token) }
        }
        val immutable = input.copy(definition = input.definition.copy(extraTilesets = input.definition.extraTilesets.toList()),
            datasets = input.datasets.sortedBy { it.id }.toList(), territoryIds = input.territoryIds.toSet())
        val transfer = datasets.pendingTransferBytes(immutable.datasets)
        val nativeId = nativeId(immutable.id, token.authorityKey, immutable.definition.fingerprint)
        val estimate = gateway.estimate(nativeId, immutable.definition)
        authority.transaction(token) { Unit }
        OfflinePackPlan(immutable, nativeId, estimate, transfer, freeBytes(), clock())
    }

    suspend fun confirm(plan: OfflinePackPlan, token: CartographicScopeAuthority.Token): OfflineMapPackEntity = withContext(Dispatchers.IO) {
        requireInput(plan.input)
        require(plan.nativeRegionId == nativeId(plan.input.id, token.authorityKey, plan.input.definition.fingerprint))
        require(plan.preparedAt > 0 && clock() - plan.preparedAt in 0..PLAN_LIFETIME_MS) { "PLAN_EXPIRED" }
        require(datasets.pendingTransferBytes(plan.input.datasets) == plan.datasetTransferBytes) { "PLAN_CHANGED" }
        require(plan.knownAdditionalBytes < freeBytes()) { "STORAGE" }
        authority.transaction(token, mutation = true) {
            val old = dao.byId(plan.input.id)
            if (old != null) {
                owned(old, token)
                require(old.name == plan.input.name.trim() && old.definitionHash == plan.input.definition.fingerprint && old.datasetsJson == json.encodeToString(plan.input.datasets) &&
                    old.territoriesJson == json.encodeToString(plan.input.territoryIds.sorted()) && old.replacesPackId == plan.input.replacesPackId) { "CONFLICT" }
                old
            } else {
                val previous = plan.input.replacesPackId?.let { id -> (dao.byId(id) ?: error("PREVIOUS_VERSION_MISSING")).also { owned(it, token) } }
                val familyId = previous?.familyId ?: plan.input.id
                val ordinal = if (previous == null) 1L else Math.addExact(dao.inAuthority(token.authorityKey)
                    .filter { it.familyId == familyId }.maxOf { it.versionOrdinal }, 1L)
                val d = plan.input.definition
                val row = OfflineMapPackEntity(plan.input.id, token.authorityKey, token.ownerKey, token.binding.kind, token.binding.id,
                    plan.input.name.trim(), d.geometryJson, d.minZoom, d.maxZoom, d.pixelRatio, d.styleUri,
                    json.encodeToString(d.extraTilesets), json.encodeToString(plan.input.datasets), json.encodeToString(plan.input.territoryIds.sorted()),
                    d.fingerprint, "", plan.nativeRegionId, UUID.randomUUID().toString(), clock(), OfflinePackStatus.PAUSED.name,
                    familyId = familyId, replacesPackId = previous?.id, versionOrdinal = ordinal, selectedVersion = previous == null)
                dao.insert(row.copy(requestSha256 = requestHash(row)))
                dao.byId(row.id)?.also { owned(it, token) } ?: error("STORAGE")
            }
        }
    }

    suspend fun transfer(id: String, token: CartographicScopeAuthority.Token, wifiOnly: Boolean,
        onProgress: (OfflineTransferStage, OfflineResourceProgress) -> Unit): OfflineMapPackEntity = lock(id).withLock {
        transferLocked(id, token, wifiOnly, onProgress)
    }
    private suspend fun transferLocked(id: String, token: CartographicScopeAuthority.Token, wifiOnly: Boolean,
        onProgress: (OfflineTransferStage, OfflineResourceProgress) -> Unit): OfflineMapPackEntity {
        val row = read(id, token)
        require(row.status != OfflinePackStatus.RETAINED.name)
        val definition = withContext(Dispatchers.Default) { definition(row) }
        // Verify every signed dataset identity again; catalog changes never silently replace the plan.
        val required = json.decodeFromString<List<OfflineDataset>>(row.datasetsJson)
        datasets.pendingTransferBytes(required)
        update(id, token) { it.copy(status = OfflinePackStatus.DOWNLOADING.name, issue = null) }
        try {
            for (dataset in required) {
                authority.transaction(token) { Unit }
                if (!datasets.available(dataset)) datasets.install(dataset)
                authority.transaction(token) { Unit }
            }
            gateway.download(row.nativeRegionId, definition, wifiOnly) { stage, progress ->
                if (authority.isCurrent(token)) onProgress(stage, progress)
            }
            update(id, token) { it.copy(status = OfflinePackStatus.VERIFYING.name) }
            val verified = verifyLocked(id, token)
            require(verified.status == OfflinePackStatus.READY.name) { "INCOMPLETE" }
            return verified
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            if (authority.isCurrent(token)) runCatching { update(id, token) { it.copy(status = OfflinePackStatus.FAILED.name, issue = "TRANSFER") } }
            throw failure
        }
    }

    /** Availability is granted only by verifyLocked after native and signed-data checks. */
    suspend fun mark(id: String, status: OfflinePackStatus, token: CartographicScopeAuthority.Token): OfflineMapPackEntity {
        require(status != OfflinePackStatus.READY) { "READINESS_REQUIRES_VALIDATION" }
        return update(id, token) { it.copy(status = status.name, issue = null,
            selectedVersion = if (status == OfflinePackStatus.RETAINED) false else it.selectedVersion) }
    }
    suspend fun saveProgress(id: String, stage: OfflineTransferStage, progress: OfflineResourceProgress, token: CartographicScopeAuthority.Token) = update(id, token) {
        it.copy(stage = stage.name, completedResources = progress.completed.coerceAtLeast(0), requiredResources = progress.required.coerceAtLeast(0),
            resourceBytes = progress.bytes.coerceAtLeast(0))
    }

    suspend fun verify(id: String, token: CartographicScopeAuthority.Token): OfflineMapPackEntity = lock(id).withLock { verifyLocked(id, token) }
    private suspend fun verifyLocked(id: String, token: CartographicScopeAuthority.Token): OfflineMapPackEntity {
        val row = read(id, token)
        val required = json.decodeFromString<List<OfflineDataset>>(row.datasetsJson)
        val validation = try { gateway.validate(row.nativeRegionId, withContext(Dispatchers.Default) { definition(row) }) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        val ready = validation?.ready == true && required.all { datasets.available(it) }
        return update(id, token) { it.copy(status = if (ready) OfflinePackStatus.READY.name else OfflinePackStatus.INCOMPLETE.name,
            validatedAt = clock(), expiresAt = validation?.expiresAt, resourceBytes = validation?.tileBytes ?: it.resourceBytes,
            issue = if (ready) null else "MISSING_RESOURCES") }
    }

    /** Select a saved definition only when its current native resources and active signed data are complete.
     * Mapbox does not promise reproducible historical imagery; this never changes GSIE active versions. */
    suspend fun prepareVersionChoice(id: String, token: CartographicScopeAuthority.Token): OfflineVersionChoice {
        val target = read(id, token)
        require(target.status !in setOf(OfflinePackStatus.DOWNLOADING.name, OfflinePackStatus.VERIFYING.name))
        val rows = authority.transaction(token) { dao.inAuthority(token.authorityKey).filter { it.familyId == target.familyId }
            .onEach { owned(it, token) } }
        val selected = rows.filter { it.selectedVersion }
        require(selected.size <= 1) { "CORRUPT" }
        require(selected.none { it.id == id }) { "ALREADY_SELECTED" }
        val validation = gateway.validate(target.nativeRegionId, withContext(Dispatchers.Default) { definition(target) })
        require(validation.ready) { "MISSING_NATIVE_RESOURCES" }
        val required = json.decodeFromString<List<OfflineDataset>>(target.datasetsJson)
        require(required.all { datasets.available(it) }) { "REQUIRED_GSIE_VERSION_NOT_ACTIVE" }
        authority.transaction(token) { Unit }
        return OfflineVersionChoice(target.familyId, target.id, target.requestSha256,
            selected.singleOrNull()?.id, selected.singleOrNull()?.requestSha256, target.status)
    }
    suspend fun selectVersion(plan: OfflineVersionChoice, token: CartographicScopeAuthority.Token): List<OfflineMapPackEntity> =
        lock("family:${plan.familyId}").withLock {
            require(prepareVersionChoice(plan.targetId, token) == plan) { "VERSION_SELECTION_CHANGED" }
            authority.transaction(token, mutation = true) {
                val family = dao.inAuthority(token.authorityKey).filter { it.familyId == plan.familyId }.onEach { owned(it, token) }
                val target = family.single { it.id == plan.targetId && it.requestSha256 == plan.targetRequestSha256 }
                val selected = family.filter { it.selectedVersion }
                require(selected.size <= 1 && selected.singleOrNull()?.id == plan.currentId && selected.singleOrNull()?.requestSha256 == plan.currentRequestSha256)
                require(target.status == plan.targetStatus)
                family.map { row ->
                    val changed = if (row.id == target.id) row.copy(selectedVersion = true, status = OfflinePackStatus.READY.name, validatedAt = clock(), issue = null)
                        else if (row.selectedVersion) row.copy(selectedVersion = false, status = OfflinePackStatus.RETAINED.name)
                        else row
                    if (changed != row) require(dao.update(changed) == 1)
                    changed
                }
            }
        }

    suspend fun prepareRemoval(id: String, token: CartographicScopeAuthority.Token): OfflinePackRemoval {
        val row = read(id, token)
        // An active version is preserved. The user must explicitly retain it first.
        require(row.status != OfflinePackStatus.READY.name && row.status != OfflinePackStatus.DOWNLOADING.name && row.status != OfflinePackStatus.VERIFYING.name) { "ACTIVE_VERSION" }
        return OfflinePackRemoval(row.id, row.nativeRegionId, row.definitionHash, row.status, row.validatedAt)
    }
    suspend fun remove(plan: OfflinePackRemoval, token: CartographicScopeAuthority.Token) = lock(plan.id).withLock {
        removeLocked(plan, token)
    }
    private suspend fun removeLocked(plan: OfflinePackRemoval, token: CartographicScopeAuthority.Token) {
        val current = prepareRemoval(plan.id, token)
        require(current == plan) { "REMOVAL_CHANGED" }
        gateway.removeRegion(plan.nativeRegionId)
        authority.transaction(token, mutation = true) {
            val row = dao.byId(plan.id) ?: error("UNAVAILABLE")
            owned(row, token)
            require(row.definitionHash == plan.definitionHash && row.status == plan.status) { "REMOVAL_CHANGED" }
            require(dao.delete(plan.id, token.authorityKey) == 1)
        }
        // Do not remove style packs or clear ambient cache: resources are shared with other regions.
    }

    /** Host calls after cancelling tasks and before deleting registry rows during explicit local purge. */
    suspend fun purgeNativeRegions(regionIds: List<String>) {
        for (id in regionIds.distinct()) gateway.removeRegion(id)
    }

    private suspend fun read(id: String, token: CartographicScopeAuthority.Token) = authority.transaction(token) {
        val row = dao.byId(id) ?: error("UNAVAILABLE")
        owned(row, token)
        definition(row)
        row
    }
    private suspend fun update(id: String, token: CartographicScopeAuthority.Token, transform: (OfflineMapPackEntity) -> OfflineMapPackEntity) =
        authority.transaction(token, mutation = true) {
            val old = dao.byId(id) ?: error("UNAVAILABLE")
            owned(old, token)
            val row = transform(old)
            require(dao.update(row) == 1)
            row
        }
    private fun owned(row: OfflineMapPackEntity, token: CartographicScopeAuthority.Token) {
        require(row.authorityKey == token.authorityKey && row.ownerKey == token.ownerKey && row.scopeKind == token.binding.kind && row.scopeId == token.binding.id) { "UNAVAILABLE" }
        require(row.requestSha256 == requestHash(row) && row.nativeRegionId == nativeId(row.id, row.authorityKey, row.definitionHash)) { "CORRUPT" }
    }
    private fun definition(row: OfflineMapPackEntity) = OfflineMapDefinition(row.geometryJson, row.minZoom, row.maxZoom, row.pixelRatio,
        row.styleUri, json.decodeFromString(row.extraTilesetsJson)).also { require(it.fingerprint == row.definitionHash) { "CORRUPT" } }
    private fun requireInput(input: OfflinePackInput) {
        require(UUID.fromString(input.id).toString() == input.id)
        require(input.replacesPackId == null || (input.replacesPackId != input.id && UUID.fromString(input.replacesPackId).toString() == input.replacesPackId))
        require(input.name.trim().length in 1..160 && input.name.none { it.isISOControl() })
        require(input.datasets.map { it.id }.distinct().size == input.datasets.size)
        require(input.datasets.all { it.id.isNotBlank() && it.version.isNotBlank() && it.sha256.matches(Regex("[a-fA-F0-9]{64}")) })
        require(input.territoryIds.size <= 110 && input.territoryIds.all { it.matches(Regex("(region|department):[A-Za-z0-9]+")) })
    }
    private fun requestHash(row: OfflineMapPackEntity): String = MessageDigest.getInstance("SHA-256")
        .digest(json.encodeToString(listOf(row.id, row.name, row.definitionHash, row.datasetsJson, row.territoriesJson,
            row.authorityKey, row.ownerKey, row.scopeKind, row.scopeId, row.localVersion, row.createdAt.toString(),
            row.familyId, row.replacesPackId, row.versionOrdinal.toString())).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    private fun nativeId(id: String, authorityKey: String, fingerprint: String): String = "gs-offline-" + MessageDigest.getInstance("SHA-256")
        .digest("$id\u0000$authorityKey\u0000$fingerprint".toByteArray()).joinToString("") { "%02x".format(it) }
    companion object { private const val PLAN_LIFETIME_MS = 15 * 60 * 1000L }
}
