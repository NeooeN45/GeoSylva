package com.quintessences.geosylva.cartography.mapbox.offline

import com.mapbox.common.NetworkRestriction
import com.mapbox.common.TileRegionErrorType
import com.mapbox.common.TileRegionLoadOptions
import com.mapbox.common.TileStore
import com.mapbox.geojson.Geometry
import com.mapbox.geojson.Polygon
import com.mapbox.geojson.MultiPolygon
import com.mapbox.maps.GlyphsRasterizationMode
import com.mapbox.maps.MapboxMapsOptions
import com.mapbox.maps.OfflineManager
import com.mapbox.maps.StylePack
import com.mapbox.maps.StylePackLoadOptions
import com.mapbox.maps.TileStoreUsageMode
import com.mapbox.maps.TilesetDescriptorOptions
import com.quintessences.geosylva.cartography.mapbox.validOverlayGeometry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import android.system.Os
import android.system.OsConstants
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A single TileStore for every renderer and every preparation entry point. */
object MapboxOfflineResources {
    private var storageRoot: File? = null
    private var created = false
    private val store: TileStore by lazy { created = true; TileStore.create() }
    /** Call on the main thread before creating the first map, after configuring the public token. */
    fun configureRendering(storageDirectory: File? = null) {
        if (storageDirectory != null && !created) {
            require(storageDirectory.isAbsolute && (storageDirectory.isDirectory || storageDirectory.mkdirs()))
            check(TileStore.setRootPath(storageDirectory.absolutePath).isValue)
            storageRoot = storageDirectory
        }
        MapboxMapsOptions.tileStore = store
        MapboxMapsOptions.tileStoreUsageMode = TileStoreUsageMode.READ_ONLY
    }
    internal fun tileStore(): TileStore = store
    /** Native shared files measured once by allocated filesystem blocks, never a sum of pack estimates. */
    fun allocatedTileStoreBytes(): Long? = storageRoot?.let { root -> runCatching {
        val inodes = hashSetOf<Pair<Long, Long>>()
        var bytes = 0L
        Files.walk(root.toPath()).use { paths -> paths.forEach { path ->
            val stat = Os.lstat(path.toString())
            if (OsConstants.S_ISREG(stat.st_mode) && inodes.add(stat.st_dev to stat.st_ino)) {
                bytes = Math.addExact(bytes, Math.multiplyExact(stat.st_blocks, 512L))
            }
        } }
        bytes
    }.getOrNull() }

}

/** SDK 11.34.1. Cancellation stops native work; restarting the same ID reuses downloaded resources. */
class NativeOfflineMapGateway : OfflineMapGateway {
    private val offlineManager by lazy { OfflineManager() }
    private val tileStore get() = MapboxOfflineResources.tileStore()
    private fun geometry(definition: OfflineMapDefinition): Geometry {
        val geometry = if (definition.geometryJson.contains("\"MultiPolygon\"")) MultiPolygon.fromJson(definition.geometryJson)
            else Polygon.fromJson(definition.geometryJson)
        val coordinates = when (geometry) {
            is Polygon -> geometry.coordinates().flatten()
            is MultiPolygon -> geometry.coordinates().flatten().flatten()
            else -> error("Unsupported offline geometry")
        }
        require(validOverlayGeometry(geometry) && coordinates.size >= 4 && coordinates.all {
            it.longitude().isFinite() && it.longitude() in -180.0..180.0 &&
                it.latitude().isFinite() && it.latitude() in -85.051129..85.051129
        })
        return geometry
    }
    private fun descriptors(definition: OfflineMapDefinition) = listOf(offlineManager.createTilesetDescriptor(
        TilesetDescriptorOptions.Builder().styleURI(definition.styleUri)
            .minZoom(definition.minZoom.toByte()).maxZoom(definition.maxZoom.toByte())
            .pixelRatio(definition.pixelRatio).tilesets(definition.extraTilesets)
            .stylePackOptions(styleOptions()).build()
    ))
    private fun styleOptions() = StylePackLoadOptions.Builder().acceptExpired(false)
        .glyphsRasterizationMode(GlyphsRasterizationMode.NO_GLYPHS_RASTERIZED_LOCALLY).build()
    private suspend fun options(definition: OfflineMapDefinition, wifiOnly: Boolean = false): TileRegionLoadOptions {
        val shape = withContext(Dispatchers.Default) { geometry(definition) }
        return TileRegionLoadOptions.Builder().geometry(shape).descriptors(descriptors(definition)).acceptExpired(false)
            .networkRestriction(if (wifiOnly) NetworkRestriction.DISALLOW_EXPENSIVE else NetworkRestriction.NONE).build()
    }

    override suspend fun estimate(regionId: String, definition: OfflineMapDefinition): OfflineMapEstimate = withContext(Dispatchers.Main.immediate) {
        requireRegionId(regionId)
        val options = options(definition)
        suspendCancellableCoroutine { continuation ->
            val cancelable = tileStore.estimateTileRegion(regionId, options, {}) { result ->
                result.onError { if (continuation.isActive) continuation.resumeWithException(OfflineNativeException()) }
                    .onValue { value ->
                        val estimate = runCatching { OfflineMapEstimate(value.transferSize, value.storageSize, value.errorMargin) }
                        if (continuation.isActive) estimate.fold({ continuation.resume(it) }, { continuation.resumeWithException(OfflineNativeException()) })
                    }
            }
            continuation.invokeOnCancellation { cancelable.cancel() }
        }
    }

    override suspend fun download(regionId: String, definition: OfflineMapDefinition, wifiOnly: Boolean,
        onProgress: (OfflineTransferStage, OfflineResourceProgress) -> Unit) = withContext(Dispatchers.Main.immediate) {
        requireRegionId(regionId)
        // Style packs have no network restriction option. The caller enforces Wi-Fi before this step.
        val options = options(definition, wifiOnly)
        suspendCancellableCoroutine<Unit> { continuation ->
            val cancelable = offlineManager.loadStylePack(definition.styleUri, styleOptions(), { p ->
                if (continuation.isActive) onProgress(OfflineTransferStage.STYLE,
                    OfflineResourceProgress(p.completedResourceCount, p.requiredResourceCount, p.completedResourceSize, p.erroredResourceCount))
            }) { result -> result.onError { if (continuation.isActive) continuation.resumeWithException(OfflineNativeException()) }
                .onValue { if (continuation.isActive) continuation.resume(Unit) } }
            continuation.invokeOnCancellation { cancelable.cancel() }
        }
        suspendCancellableCoroutine<Unit> { continuation ->
            val cancelable = tileStore.loadTileRegion(regionId, options, { p ->
                if (continuation.isActive) onProgress(OfflineTransferStage.TILES,
                    OfflineResourceProgress(p.completedResourceCount, p.requiredResourceCount, p.completedResourceSize, p.erroredResourceCount))
            }) { result -> result.onError { if (continuation.isActive) continuation.resumeWithException(OfflineNativeException()) }
                .onValue { if (continuation.isActive) continuation.resume(Unit) } }
            continuation.invokeOnCancellation { cancelable.cancel() }
        }
    }

    override suspend fun validate(regionId: String, definition: OfflineMapDefinition): OfflineResourceValidation = withContext(Dispatchers.Main.immediate) {
        requireRegionId(regionId)
        val style: StylePack = suspendCancellableCoroutine { c ->
            offlineManager.getStylePack(definition.styleUri) { r -> r.onError { if (c.isActive) c.resumeWithException(OfflineNativeException()) }
                .onValue { if (c.isActive) c.resume(it) } }
        }
        val region = suspendCancellableCoroutine<com.mapbox.common.TileRegion> { c ->
            tileStore.getTileRegion(regionId) { r -> r.onError { if (c.isActive) c.resumeWithException(OfflineNativeException()) }
                .onValue { if (c.isActive) c.resume(it) } }
        }
        val matches = suspendCancellableCoroutine<Boolean> { c ->
            tileStore.tileRegionContainsDescriptors(regionId, descriptors(definition)) { r -> r.onError { if (c.isActive) c.resumeWithException(OfflineNativeException()) }
                .onValue { if (c.isActive) c.resume(it) } }
        }
        val regionGeometry = suspendCancellableCoroutine<Geometry> { c ->
            tileStore.getTileRegionGeometry(regionId) { r -> r.onError { if (c.isActive) c.resumeWithException(OfflineNativeException()) }
                .onValue { if (c.isActive) c.resume(it) } }
        }
        val expectedGeometry = withContext(Dispatchers.Default) { geometry(definition) }
        OfflineResourceValidation(
            style.styleURI == definition.styleUri && style.requiredResourceCount > 0 && style.completedResourceCount >= style.requiredResourceCount,
            region.id == regionId && region.requiredResourceCount > 0 && region.completedResourceCount >= region.requiredResourceCount,
            matches, regionGeometry == expectedGeometry, style.completedResourceSize, region.completedResourceSize,
            listOfNotNull(style.expires?.time, region.expires?.time).minOrNull())
    }
    override suspend fun removeRegion(regionId: String) = withContext(Dispatchers.Main.immediate) {
        requireRegionId(regionId)
        suspendCancellableCoroutine<Unit> { c -> tileStore.removeTileRegion(regionId) { r -> r.onError { if (c.isActive) { if (it.type == TileRegionErrorType.DOES_NOT_EXIST) c.resume(Unit) else c.resumeWithException(OfflineNativeException()) } }
            .onValue { if (c.isActive) c.resume(Unit) } } }
    }
    private fun requireRegionId(id: String) { require(id.matches(Regex("gs-offline-[a-f0-9]{64}"))) }
}

/** Never expose native error strings: they can contain request URLs and credentials. */
class OfflineNativeException : RuntimeException("Offline map resources unavailable")
