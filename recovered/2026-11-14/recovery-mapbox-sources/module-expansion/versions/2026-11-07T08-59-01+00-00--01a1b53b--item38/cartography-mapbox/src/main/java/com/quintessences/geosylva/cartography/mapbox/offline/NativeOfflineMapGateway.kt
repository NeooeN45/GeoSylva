package com.quintessences.geosylva.cartography.mapbox.offline

import com.mapbox.common.NetworkRestriction
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A single TileStore for every renderer and every preparation entry point. */
object MapboxOfflineResources {
    private val store: TileStore by lazy { TileStore.create() }
    /** Call on the main thread before creating the first map, after configuring the public token. */
    fun configureRendering() {
        MapboxMapsOptions.tileStore = store
        MapboxMapsOptions.tileStoreUsageMode = TileStoreUsageMode.READ_ONLY
    }
    internal fun tileStore(): TileStore = store
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
        require(coordinates.size >= 4 && coordinates.all {
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
    private fun options(definition: OfflineMapDefinition) = TileRegionLoadOptions.Builder()
        .geometry(geometry(definition)).descriptors(descriptors(definition)).acceptExpired(false)
        .networkRestriction(NetworkRestriction.NONE).build()

    override suspend fun estimate(regionId: String, definition: OfflineMapDefinition): OfflineMapEstimate = withContext(Dispatchers.Main.immediate) {
        requireRegionId(regionId)
        val options = options(definition)
        suspendCancellableCoroutine { continuation ->
            val cancelable = tileStore.estimateTileRegion(regionId, options, {}) { result ->
                result.fold({ value -> if (continuation.isActive) continuation.resume(
                    OfflineMapEstimate(value.transferSize, value.storageSize, value.errorMargin)) },
                    { if (continuation.isActive) continuation.resumeWithException(OfflineNativeException()) })
            }
            continuation.invokeOnCancellation { cancelable.cancel() }
        }
    }

    override suspend fun download(regionId: String, definition: OfflineMapDefinition,
        onProgress: (OfflineTransferStage, OfflineResourceProgress) -> Unit) = withContext(Dispatchers.Main.immediate) {
        requireRegionId(regionId)
        // Style packs have no network restriction option. The caller enforces Wi-Fi before this step.
        val options = options(definition)
        suspendCancellableCoroutine<Unit> { continuation ->
            val cancelable = offlineManager.loadStylePack(definition.styleUri, styleOptions(), { p ->
                if (continuation.isActive) onProgress(OfflineTransferStage.STYLE,
                    OfflineResourceProgress(p.completedResourceCount, p.requiredResourceCount, p.completedResourceSize, p.erroredResourceCount))
            }) { result -> result.fold({ if (continuation.isActive) continuation.resume(Unit) },
                { if (continuation.isActive) continuation.resumeWithException(OfflineNativeException()) }) }
            continuation.invokeOnCancellation { cancelable.cancel() }
        }
        suspendCancellableCoroutine<Unit> { continuation ->
            val cancelable = tileStore.loadTileRegion(regionId, options, { p ->
                if (continuation.isActive) onProgress(OfflineTransferStage.TILES,
                    OfflineResourceProgress(p.completedResourceCount, p.requiredResourceCount, p.completedResourceSize, p.erroredResourceCount))
            }) { result -> result.fold({ if (continuation.isActive) continuation.resume(Unit) },
                { if (continuation.isActive) continuation.resumeWithException(OfflineNativeException()) }) }
            continuation.invokeOnCancellation { cancelable.cancel() }
        }
    }

    override suspend fun validate(regionId: String, definition: OfflineMapDefinition): OfflineResourceValidation = withContext(Dispatchers.Main.immediate) {
        requireRegionId(regionId)
        val style: StylePack = suspendCancellableCoroutine { c ->
            offlineManager.getStylePack(definition.styleUri) { r -> r.fold(
                { if (c.isActive) c.resume(it) }, { if (c.isActive) c.resumeWithException(OfflineNativeException()) }) }
        }
        val region = suspendCancellableCoroutine<com.mapbox.common.TileRegion> { c ->
            tileStore.getTileRegion(regionId) { r -> r.fold(
                { if (c.isActive) c.resume(it) }, { if (c.isActive) c.resumeWithException(OfflineNativeException()) }) }
        }
        val matches = suspendCancellableCoroutine<Boolean> { c ->
            tileStore.tileRegionContainsDescriptors(regionId, descriptors(definition)) { r -> r.fold(
                { if (c.isActive) c.resume(it) }, { if (c.isActive) c.resumeWithException(OfflineNativeException()) }) }
        }
        val regionGeometry = suspendCancellableCoroutine<Geometry> { c ->
            tileStore.getTileRegionGeometry(regionId) { r -> r.fold(
                { if (c.isActive) c.resume(it) }, { if (c.isActive) c.resumeWithException(OfflineNativeException()) }) }
        }
        OfflineResourceValidation(
            style.styleURI == definition.styleUri && style.requiredResourceCount > 0 && style.completedResourceCount >= style.requiredResourceCount,
            region.id == regionId && region.requiredResourceCount > 0 && region.completedResourceCount >= region.requiredResourceCount,
            matches, regionGeometry == geometry(definition), style.completedResourceSize, region.completedResourceSize,
            listOfNotNull(style.expires?.time, region.expires?.time).minOrNull())
    }
    override suspend fun removeRegion(regionId: String) = withContext(Dispatchers.Main.immediate) {
        requireRegionId(regionId)
        suspendCancellableCoroutine<Unit> { c -> tileStore.removeTileRegion(regionId) { r -> r.fold(
            { if (c.isActive) c.resume(Unit) }, { if (c.isActive) c.resumeWithException(OfflineNativeException()) }) } }
    }
    private fun requireRegionId(id: String) { require(id.matches(Regex("gs-offline-[a-f0-9]{64}"))) }
}

/** Never expose native error strings: they can contain request URLs and credentials. */
class OfflineNativeException : RuntimeException("Offline map resources unavailable")
