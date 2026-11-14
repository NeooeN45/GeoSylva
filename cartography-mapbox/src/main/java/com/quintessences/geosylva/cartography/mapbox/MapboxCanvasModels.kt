package com.quintessences.geosylva.cartography.mapbox

/** Observed native camera padding, in physical pixels, preserved across style/configuration changes. */
data class MapCameraPadding(
    val top: Double = 0.0,
    val left: Double = 0.0,
    val bottom: Double = 0.0,
    val right: Double = 0.0,
) {
    init { require(listOf(top, left, bottom, right).all { it.isFinite() && it >= 0.0 }) }
}

/** Geographic coordinates: longitude first, never the legacy [latitude, longitude] order. */
data class MapCameraPosition(
    val longitude: Double,
    val latitude: Double,
    val zoom: Double,
    val bearing: Double = 0.0,
    val pitch: Double = 0.0,
    val padding: MapCameraPadding = MapCameraPadding(),
    /** Optional same-observation physical witness, consumed only during native recreation. */
    val nativePose: MapNativeCameraPose? = null,
) {
    init {
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Invalid camera longitude." }
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Invalid camera latitude." }
        require(zoom.isFinite() && zoom in 0.0..25.5) { "Invalid camera zoom." }
        require(bearing.isFinite()) { "Invalid camera bearing." }
        require(pitch.isFinite() && pitch in 0.0..60.0) { "Camera pitch must be between 0 and 60 degrees." }
    }
}

/** IDs are unique per host session. Consumed means matching native camera read-back, not a rendered frame. */
data class MapCameraCommand(val id: String, val position: MapCameraPosition) {
    init { require(id.isNotBlank()) { "A camera command needs an ID." } }
}

/** Basemap resources are independent from business layers and their access controls. */
data class MapboxStyleSpec(
    val id: String,
    val uri: String,
    val demSourceUrl: String? = "mapbox://mapbox.mapbox-terrain-dem-v1",
    val styleJson: String? = null,
    /** Photo imagery needs light system icons and a local status-bar scrim. */
    val isPhotographic: Boolean = false,
    /** Explicit basemap contrast; null keeps the application theme fallback. */
    val backgroundIsDark: Boolean? = null,
) {
    init {
        require(id.isNotBlank() && uri.isNotBlank()) { "A style needs an ID and URI." }
        require(demSourceUrl == null || demSourceUrl.isNotBlank()) { "A DEM source cannot be blank." }
        require(styleJson == null || styleJson.trimStart().startsWith("{")) { "Invalid style document." }
    }

    val document: String get() = styleJson ?: uri
    val isInlineJson: Boolean get() = document.trimStart().startsWith("{")
}

/** Inline JSON has no SDK style URI. Its renderer generation supplies the document identity. */
internal fun MapboxStyleSpec.matchesNativeStyleUri(nativeUri: String): Boolean =
    if (isInlineJson) nativeUri.isBlank() else nativeUri == uri

object MapboxStyles {
    val Plan = MapboxStyleSpec("plan", "mapbox://styles/mapbox/standard", backgroundIsDark = false)
    val Satellite = MapboxStyleSpec("satellite", "mapbox://styles/mapbox/satellite-v9", isPhotographic = true)
    val SatelliteLabels = MapboxStyleSpec("satellite-labels", "mapbox://styles/mapbox/satellite-streets-v12", isPhotographic = true)
    val Topographic = MapboxStyleSpec("topo", "mapbox://styles/mapbox/outdoors-v12", backgroundIsDark = false)
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
    PUBLIC_TOKEN_REQUIRED, DEM_UNAVAILABLE, STYLE, SOURCE, TILE, GLYPHS, SPRITE, CAMERA_BOUNDS, TERRAIN_CONTROL, CAMERA_FIT, CAMERA_RESTORE,
}

/** Fixed diagnostics only: never an SDK URL, raw message, token or business data. */
enum class MapRenderDiagnostic {
    CONFIGURATION_REQUIRED, PUBLIC_TOKEN_REQUIRED, DEM_UNAVAILABLE, TOKEN_CONFIGURING,
    SDK_MAP_ABSENT, STYLE_ABSENT, STYLE_NOT_READY, STYLE_URI_MISMATCH,
    TERRAIN_SOURCE_MISMATCH, DEM_NOT_REGISTERED, DEM_URL_UNAVAILABLE, DEM_URL_MISMATCH,
    DEM_SOURCE_INVALID, DEM_REGISTRATION_REJECTED, TERRAIN_UPDATE_REJECTED,
    EXAGGERATION_UNREADABLE, EXAGGERATION_PENDING,
    AWAITING_RESOURCES, AWAITING_CAMERA_BOUNDS, CAMERA_BOUNDS_REJECTED,
    RESOURCE_FAILURE, CAMERA_FIT_FAILED, CAMERA_RESTORE_FAILED, READY,
}

sealed interface MapRenderStatus {
    data object ConfigurationRequired : MapRenderStatus
    data class Loading(val styleId: String) : MapRenderStatus
    /** Raised after SDK MapLoaded or a newer MapIdle and matching native relief read-back. */
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


/** Disabled terrain has no effective elevation multiplier to compare. */
fun reliefMatches(applied: MapRelief, requested: MapRelief): Boolean =
    applied.enabled == requested.enabled && (!requested.enabled ||
        kotlin.math.abs(applied.exaggeration - requested.exaggeration) < 0.00001)
