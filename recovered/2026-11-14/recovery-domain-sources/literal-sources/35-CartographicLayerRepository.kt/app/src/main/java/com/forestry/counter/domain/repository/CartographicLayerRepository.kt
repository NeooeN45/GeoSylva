package com.forestry.counter.domain.repository

import com.forestry.counter.domain.model.cartography.*
import com.forestry.counter.domain.usecase.maps.MapDataScope
import kotlinx.coroutines.flow.Flow

interface CartographicLayerAccess {
    val scope: MapDataScope
    /** Partition identifier only; it confers no authority by itself. */
    val restorationKey: String
}
enum class LayerImportFormat(val extension: String) {
    GEOJSON("geojson"), KML("kml"), KMZ("kmz"), GPX("gpx"), CSV("csv"), GEOPACKAGE("gpkg")
}
enum class LayerStoreError {
    ACCESS_CHANGED, UNAVAILABLE, INVALID_INPUT, UNSUPPORTED_FORMAT, UNSUPPORTED_CRS,
    INVALID_GEOMETRY, LIMIT_EXCEEDED, UNSAFE_ARCHIVE, EMPTY, CONFLICT, CORRUPT, IO, UNKNOWN_RESULT,
}
sealed interface LayerStoreResult<out T> {
    data class Success<T>(val value: T) : LayerStoreResult<T>
    data class Failure(val reason: LayerStoreError) : LayerStoreResult<Nothing>
}
/** A snapshot is held only in memory until explicit commit. */
data class StagedCartographicLayer(val token: String, val layer: CartographicLayer)

interface CartographicLayerRepository {
    suspend fun captureAccess(scope: MapDataScope): LayerStoreResult<CartographicLayerAccess>
    fun isCurrent(access: CartographicLayerAccess): Boolean
    fun accessChanges(): Flow<Unit>
    suspend fun stage(bytes: ByteArray, filename: String, format: LayerImportFormat, access: CartographicLayerAccess): LayerStoreResult<StagedCartographicLayer>
    fun discard(token: String)
    suspend fun commit(token: String, operationId: String, access: CartographicLayerAccess): LayerStoreResult<CartographicLayer>
    suspend fun list(access: CartographicLayerAccess): LayerStoreResult<List<CartographicLayer>>
    suspend fun read(id: String, access: CartographicLayerAccess): LayerStoreResult<CartographicLayer>
    suspend fun updateStyle(id: String, expectedVersion: Long, operationId: String, style: LayerStyle, access: CartographicLayerAccess): LayerStoreResult<CartographicLayer>
    suspend fun patchAttributes(id: String, featureId: String, expectedVersion: Long, expectedFeatureVersion: Long, operationId: String, changes: List<LayerAttributeChange>, access: CartographicLayerAccess): LayerStoreResult<CartographicLayer>
    suspend fun delete(id: String, expectedVersion: Long, operationId: String, access: CartographicLayerAccess): LayerStoreResult<Unit>
}
