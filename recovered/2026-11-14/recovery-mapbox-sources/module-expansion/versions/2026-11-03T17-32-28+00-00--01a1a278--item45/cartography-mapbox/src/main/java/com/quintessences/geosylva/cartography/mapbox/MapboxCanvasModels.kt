package com.quintessences.geosylva.cartography.mapbox

/** Geographic coordinates: longitude first, never the legacy [latitude, longitude] order. */
data class MapCameraPosition(
    val longitude: Double,
    val latitude: Double,
    val zoom: Double,
    val bearing: Double = 0.0,
    val pitch: Double = 0.0,
) {
    init {
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Invalid camera longitude." }
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Invalid camera latitude." }
        require(zoom.isFinite() && zoom in 0.0..25.5) { "Invalid camera zoom." }
        require(bearing.isFinite()) { "Invalid camera bearing." }
        require(pitch.isFinite() && pitch in 0.0..60.0) { "Camera pitch must be between 0 and 60 degrees." }
    }
}

/** IDs are unique per host session. Acknowledgement means queued, not that rendering has finished. */
data class MapCameraCommand(val id: String, val position: MapCameraPosition) {
    init { require(id.isNotBlank()) { "A camera command needs an ID." } }
}

/** Basemap resources are independent from business layers and their access controls. */
data class MapboxStyleSpec(
    val id: String,
    val uri: String,
    val demSourceUrl: String? = "mapbox://mapbox.mapbox-terrain-dem-v1",
) {
    init {
        require(id.isNotBlank() && uri.isNotBlank()) { "A style needs an ID and URI." }
        require(demSourceUrl == null || demSourceUrl.isNotBlank()) { "A DEM source cannot be blank." }
    }
}

object MapboxStyles {
    val Plan = MapboxStyleSpec("plan", "mapbox://styles/mapbox/standard")
    val Satellite = MapboxStyleSpec("satellite", "mapbox://styles/mapbox/satellite-v9")
    val SatelliteLabels = MapboxStyleSpec("satellite-labels", "mapbox://styles/mapbox/satellite-streets-v12")
    val Topographic = MapboxStyleSpec("topo", "mapbox://styles/mapbox/outdoors-v12")
}

/** DEM elevation/exaggeration and camera pitch are deliberately separate controls. */
data class MapRelief(val enabled: Boolean = false, val exaggeration: Double = 1.0) {
    init {
        require(exaggeration.isFinite() && exaggeration in 1.0..3.0) {
            "Terrain exaggeration must be between 1 and 3."
        }
    }
}

enum class MapFailureReason {
    PUBLIC_TOKEN_REQUIRED, DEM_UNAVAILABLE, STYLE, SOURCE, TILE, GLYPHS, SPRITE, CAMERA_BOUNDS,
}

sealed interface MapRenderStatus {
    data object ConfigurationRequired : MapRenderStatus
    data class Loading(val styleId: String) : MapRenderStatus
    /** Raised by an SDK map-loaded event, not merely by a selected style or a requested camera. */
    data class Ready(val styleId: String) : MapRenderStatus
    /** Contains no raw SDK message or URL, which could contain an access token. */
    data class Failed(val reason: MapFailureReason) : MapRenderStatus
}

/** Presence/shape guard only. Authenticity and permissions are established by the SDK/network. */
internal fun mapConfigurationIssue(
    accessToken: String?, style: MapboxStyleSpec, relief: MapRelief,
): MapRenderStatus? = when {
    accessToken.isNullOrBlank() -> MapRenderStatus.ConfigurationRequired
    !accessToken.startsWith("pk.") -> MapRenderStatus.Failed(MapFailureReason.PUBLIC_TOKEN_REQUIRED)
    relief.enabled && style.demSourceUrl == null -> MapRenderStatus.Failed(MapFailureReason.DEM_UNAVAILABLE)
    else -> null
}
