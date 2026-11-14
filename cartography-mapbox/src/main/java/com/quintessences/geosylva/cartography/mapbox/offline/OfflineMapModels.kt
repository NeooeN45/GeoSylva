package com.quintessences.geosylva.cartography.mapbox.offline

import java.security.MessageDigest

/** Immutable preparation snapshot. Geometry is EPSG:4326 GeoJSON, longitude first. */
data class OfflineMapDefinition(
    val geometryJson: String,
    val minZoom: Int,
    val maxZoom: Int,
    val pixelRatio: Float,
    val styleUri: String,
    val extraTilesets: List<String> = emptyList(),
) {
    init {
        require(geometryJson.length in 20..2_000_000)
        require(minZoom in 0..22 && maxZoom in minZoom..22)
        require(pixelRatio.isFinite() && pixelRatio in 0.5f..4f)
        require(styleUri.matches(Regex("mapbox://styles/[A-Za-z0-9_-]+/[A-Za-z0-9_-]+")))
        require(extraTilesets.distinct().size == extraTilesets.size && extraTilesets.all {
            it.matches(Regex("mapbox://[A-Za-z0-9_.-]+"))
        })
    }
    /** Stable resource identity; changing geometry, zoom, density or source changes it. */
    val fingerprint: String get() = sha256(listOf(geometryJson, minZoom, maxZoom, pixelRatio, styleUri,
        extraTilesets.sorted().joinToString(",")).joinToString("\u0000"))
}

/** SDK estimation excludes an unknown style download and native temporary allocation. */
data class OfflineMapEstimate(val tileTransferBytes: Long, val tileStorageBytes: Long, val errorMargin: Double) {
    init { require(tileTransferBytes >= 0 && tileStorageBytes >= 0 && errorMargin.isFinite() && errorMargin >= 0) }
}

data class OfflineResourceProgress(val completed: Long, val required: Long, val bytes: Long, val errors: Long = 0) {
    val fraction: Float? get() = if (required > 0) (completed.toDouble() / required).coerceIn(0.0, 1.0).toFloat() else null
}

data class OfflineResourceValidation(
    val styleComplete: Boolean,
    val tilesComplete: Boolean,
    val descriptorsMatch: Boolean,
    val geometryMatches: Boolean,
    val styleBytes: Long,
    val tileBytes: Long,
    val expiresAt: Long?,
) {
    val ready: Boolean get() = styleComplete && tilesComplete && descriptorsMatch && geometryMatches
}

enum class OfflineTransferStage { STYLE, TILES, VERIFYING }

/** Native implementation has no account, business data, or persistence authority. */
interface OfflineMapGateway {
    suspend fun estimate(regionId: String, definition: OfflineMapDefinition): OfflineMapEstimate
    suspend fun download(regionId: String, definition: OfflineMapDefinition, wifiOnly: Boolean, onProgress: (OfflineTransferStage, OfflineResourceProgress) -> Unit)
    suspend fun validate(regionId: String, definition: OfflineMapDefinition): OfflineResourceValidation
    /** Removes only this explicit region reference. Shared cached resources may remain. */
    suspend fun removeRegion(regionId: String)
}

internal fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
