@file:OptIn(com.mapbox.maps.MapboxExperimental::class)

package com.quintessences.geosylva.cartography.mapbox

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraBoundsOptions
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapLoadingErrorType
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.ComposeMapInitOptions
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.rememberMapState
import com.mapbox.maps.extension.compose.style.DoubleValue
import com.mapbox.maps.extension.compose.style.GenericStyle
import com.mapbox.maps.extension.compose.style.LongValue
import com.mapbox.maps.extension.compose.style.StringValue
import com.mapbox.maps.extension.compose.style.StyleState
import com.mapbox.maps.extension.compose.style.sources.generated.RasterDemSourceState
import com.mapbox.maps.extension.compose.style.terrain.generated.TerrainState
import com.mapbox.maps.plugin.gestures.generated.GesturesSettings
import com.mapbox.maps.plugin.DistanceUnits
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Shared renderer only: no business repository, permission inference or GPS request.
 *
 * A blank/non-public token creates no MapView. All hosts use the same application
 * public token; it must never be a secret sk token. The host owns loading/error UI
 * and can recreate this composable with a new key for an explicit retry.
 *
 * [initialCamera] is read once. Later moves use uniquely identified commands;
 * [onCameraChanged] reports the SDK camera, including user gestures, independently
 * of the requested camera. The Compose SDK owns lifecycle and required ornaments.
 * [onReliefChanged] acknowledges values read back from the SDK style, never a request.
 * [onDiagnostic] emits fixed codes, deduplicated within the active render generation.
 * [contentPadding] keeps native ornaments outside the host's overlay/sheet area;
 * it does not itself modify camera or measure a physical terrain slope.
 */
@Composable
fun SharedMapboxCanvas(
    accessToken: String?,
    style: MapboxStyleSpec,
    initialCamera: MapCameraPosition,
    modifier: Modifier = Modifier,
    cameraCommand: MapCameraCommand? = null,
    relief: MapRelief = MapRelief(),
    contentPadding: PaddingValues = PaddingValues(12.dp),
    onCameraChanged: (MapCameraPosition) -> Unit = {},
    onReliefChanged: (MapRelief) -> Unit = {},
    onCommandConsumed: (String) -> Unit = {},
    onStatusChanged: (MapRenderStatus) -> Unit = {},
    onDiagnostic: (MapRenderDiagnostic) -> Unit = {},
) {
    val statusCallback by rememberUpdatedState(onStatusChanged)
    val diagnosticCallback by rememberUpdatedState(onDiagnostic)
    val cameraCallback by rememberUpdatedState(onCameraChanged)
    val reliefCallback by rememberUpdatedState(onReliefChanged)
    val consumedCallback by rememberUpdatedState(onCommandConsumed)
    var retainedCamera by remember { mutableStateOf(initialCamera) }
    var lastCommandId by rememberSaveable { mutableStateOf<String?>(null) }
    val issue = mapConfigurationIssue(accessToken, style, relief)
    val rendererKey = remember(style.id, style.uri, style.demSourceUrl, accessToken, issue) { Any() }
    val activeRendererKey by rememberUpdatedState(rendererKey)
    var lastDiagnostic by remember(rendererKey) { mutableStateOf<MapRenderDiagnostic?>(null) }
    fun publishDiagnostic(diagnostic: MapRenderDiagnostic) {
        if (activeRendererKey === rendererKey && lastDiagnostic != diagnostic) {
            lastDiagnostic = diagnostic
            diagnosticCallback(diagnostic)
        }
    }
    if (issue != null) {
        LaunchedEffect(rendererKey, issue) {
            publishDiagnostic(when {
                issue == MapRenderStatus.ConfigurationRequired -> MapRenderDiagnostic.CONFIGURATION_REQUIRED
                issue is MapRenderStatus.Failed && issue.reason == MapFailureReason.DEM_UNAVAILABLE ->
                    MapRenderDiagnostic.DEM_UNAVAILABLE
                else -> MapRenderDiagnostic.PUBLIC_TOKEN_REQUIRED
            })
            statusCallback(issue)
        }
        Box(modifier)
        return
    }

    // Configure before composing MapboxMap: SideEffect would run too late for
    // AndroidView construction. No Mapbox API is touched in the rejected branch.
    var tokenConfigured by remember(accessToken) { mutableStateOf(false) }
    LaunchedEffect(accessToken) {
        publishDiagnostic(MapRenderDiagnostic.TOKEN_CONFIGURING)
        MapboxOptions.accessToken = requireNotNull(accessToken)
        tokenConfigured = true
    }
    LaunchedEffect(rendererKey, style.demSourceUrl, relief) {
        statusCallback(MapRenderStatus.Loading(style.id))
    }
    if (!tokenConfigured) {
        Box(modifier)
        return
    }

    // Dispose the previous renderer when changing styles. A delayed callback from
    // its old resource/style generation cannot overwrite the new host state.
    key(rendererKey) {
        ConfiguredMapboxCanvas(style, retainedCamera, modifier, cameraCommand, lastCommandId, relief, contentPadding,
            onCameraChanged = { camera ->
                if (activeRendererKey === rendererKey) {
                    retainedCamera = camera
                    cameraCallback(camera)
                }
            },
            onReliefChanged = { if (activeRendererKey === rendererKey) reliefCallback(it) },
            onCommandConsumed = {
                if (activeRendererKey === rendererKey) {
                    lastCommandId = it
                    consumedCallback(it)
                }
            },
            onStatusChanged = { if (activeRendererKey === rendererKey) statusCallback(it) },
            onDiagnostic = ::publishDiagnostic)
    }
}

@Composable
private fun ConfiguredMapboxCanvas(
    style: MapboxStyleSpec,
    initialCamera: MapCameraPosition,
    modifier: Modifier,
    cameraCommand: MapCameraCommand?,
    lastCommandId: String?,
    relief: MapRelief,
    contentPadding: PaddingValues,
    onCameraChanged: (MapCameraPosition) -> Unit,
    onReliefChanged: (MapRelief) -> Unit,
    onCommandConsumed: (String) -> Unit,
    onStatusChanged: (MapRenderStatus) -> Unit,
    onDiagnostic: (MapRenderDiagnostic) -> Unit,
) {
    val density = LocalDensity.current.density
    val composeInitOptions = remember(density) { ComposeMapInitOptions(pixelRatio = density, textureView = true) }
    val cameraCallback by rememberUpdatedState(onCameraChanged)
    val consumedCallback by rememberUpdatedState(onCommandConsumed)
    val reliefCallback by rememberUpdatedState(onReliefChanged)
    val requestedRelief by rememberUpdatedState(relief)
    val statusCallback by rememberUpdatedState(onStatusChanged)
    val diagnosticCallback by rememberUpdatedState(onDiagnostic)
    val currentStyle by rememberUpdatedState(style)
    val mapState = rememberMapState {
        gesturesState.gesturesSettings = GesturesSettings.Builder().apply { pitchEnabled = true }.build()
    }
    var sdkMap by remember { mutableStateOf<com.mapbox.maps.MapboxMap?>(null) }
    val viewport = rememberMapViewportState { setCameraOptions(initialCamera.toCameraOptions()) }
    val styleState = remember(style.demSourceUrl, relief) {
        StyleState().apply {
            terrainState = if (relief.enabled) {
                TerrainState(RasterDemSourceState(DEM_SOURCE_ID).apply {
                    url = StringValue(requireNotNull(style.demSourceUrl))
                    tileSize = LongValue(512)
                }).apply { exaggeration = DoubleValue(relief.exaggeration) }
            } else TerrainState.DISABLED
        }
    }

    var resourcesLoaded by remember { mutableStateOf(false) }
    var boundsAccepted by remember { mutableStateOf(false) }
    var acknowledgedRelief by remember { mutableStateOf<MapRelief?>(null) }
    var resourceFailure by remember { mutableStateOf<MapFailureReason?>(null) }
    var readyPublished by remember { mutableStateOf(false) }

    fun acknowledgeRelief() {
        val prior = acknowledgedRelief
        if (prior == null || !reliefMatches(prior, requestedRelief)) {
            val map = sdkMap
            if (map == null) {
                diagnosticCallback(MapRenderDiagnostic.SDK_MAP_ABSENT)
                return
            }
            val nativeStyle = map.style
            if (nativeStyle == null) {
                diagnosticCallback(MapRenderDiagnostic.STYLE_ABSENT)
                return
            }
            if (nativeStyle.styleURI != currentStyle.uri) {
                diagnosticCallback(MapRenderDiagnostic.STYLE_URI_MISMATCH)
                return
            }
            if (!nativeStyle.isStyleLoaded()) {
                diagnosticCallback(MapRenderDiagnostic.STYLE_NOT_READY)
                return
            }
            val sourceId = nativeStyle.getStyleTerrainProperty("source").value.contents as? String
            val actual = if (sourceId.isNullOrBlank() && !requestedRelief.enabled) {
                MapRelief(enabled = false) // no elevation multiplication when terrain is disabled
            } else {
                if (sourceId != DEM_SOURCE_ID) {
                    diagnosticCallback(MapRenderDiagnostic.TERRAIN_SOURCE_MISMATCH)
                    return
                }
                if (!nativeStyle.styleSourceExists(DEM_SOURCE_ID)) {
                    diagnosticCallback(MapRenderDiagnostic.DEM_NOT_REGISTERED)
                    return
                }
                val actualUrl = nativeStyle.getStyleSourceProperty(DEM_SOURCE_ID, "url").value.contents as? String
                if (actualUrl.isNullOrBlank()) {
                    diagnosticCallback(MapRenderDiagnostic.DEM_URL_UNAVAILABLE)
                    return
                }
                if (actualUrl != currentStyle.demSourceUrl) {
                    diagnosticCallback(MapRenderDiagnostic.DEM_URL_MISMATCH)
                    return
                }
                val actualExaggeration = (nativeStyle.getStyleTerrainProperty("exaggeration")
                    .value.contents as? Number)?.toDouble()
                if (actualExaggeration == null || !actualExaggeration.isFinite() || actualExaggeration !in 1.0..3.0) {
                    diagnosticCallback(MapRenderDiagnostic.EXAGGERATION_UNREADABLE)
                    return
                }
                MapRelief(enabled = true, exaggeration = actualExaggeration)
            }
            if (!reliefMatches(actual, requestedRelief)) {
                diagnosticCallback(MapRenderDiagnostic.EXAGGERATION_PENDING)
                return
            }
            acknowledgedRelief = actual
            reliefCallback(actual)
        }
        when {
            !resourcesLoaded -> diagnosticCallback(MapRenderDiagnostic.AWAITING_MAP_LOADED)
            !boundsAccepted -> diagnosticCallback(MapRenderDiagnostic.AWAITING_CAMERA_BOUNDS)
            resourceFailure != null -> diagnosticCallback(MapRenderDiagnostic.RESOURCE_FAILURE)
            else -> {
                diagnosticCallback(MapRenderDiagnostic.READY)
                if (!readyPublished) {
                    readyPublished = true
                    statusCallback(MapRenderStatus.Ready(currentStyle.id))
                }
            }
        }
    }

    LaunchedEffect(style.demSourceUrl, relief.enabled) {
        resourcesLoaded = false
        acknowledgeRelief()
    }
    LaunchedEffect(relief) {
        acknowledgedRelief = null
        readyPublished = false
        acknowledgeRelief()
    }
    LaunchedEffect(cameraCommand?.id) {
        val command = cameraCommand ?: return@LaunchedEffect
        if (command.id != lastCommandId) {
            viewport.setCameraOptions(command.position.toCameraOptions())
            consumedCallback(command.id)
        }
    }
    LaunchedEffect(viewport) {
        snapshotFlow { viewport.cameraState }.collect { camera ->
            if (camera != null) cameraCallback(MapCameraPosition(
                longitude = canonicalLongitude(camera.center.longitude()),
                latitude = camera.center.latitude(), zoom = camera.zoom,
                bearing = camera.bearing, pitch = camera.pitch,
            ))
        }
    }
    LaunchedEffect(mapState) {
        launch {
            mapState.mapLoadedEvents.collect {
                if (sdkMap?.style?.styleURI == currentStyle.uri) {
                    resourcesLoaded = true
                    resourceFailure = null
                    acknowledgeRelief()
                }
            }
        }
        launch {
            mapState.mapIdleEvents.collect { acknowledgeRelief() }
        }
        launch {
            mapState.renderFrameFinishedEvents.collect { acknowledgeRelief() }
        }
        launch {
            mapState.mapLoadingErrorEvents.collect { error ->
                val reason = when (error.type) {
                    MapLoadingErrorType.STYLE -> MapFailureReason.STYLE
                    MapLoadingErrorType.SOURCE -> MapFailureReason.SOURCE
                    MapLoadingErrorType.TILE -> MapFailureReason.TILE
                    MapLoadingErrorType.GLYPHS -> MapFailureReason.GLYPHS
                    MapLoadingErrorType.SPRITE -> MapFailureReason.SPRITE
                }
                resourceFailure = reason
                readyPublished = false
                diagnosticCallback(MapRenderDiagnostic.RESOURCE_FAILURE)
                // Never forward error.message/source URL: it may contain a token.
                statusCallback(MapRenderStatus.Failed(reason))
            }
        }
    }

    MapboxMap(
        modifier = modifier,
        composeMapInitOptions = composeInitOptions,
        mapViewportState = viewport,
        mapState = mapState,
        compass = { Compass(contentPadding = contentPadding, alignment = Alignment.TopEnd) },
        logo = { Logo(contentPadding = contentPadding, alignment = Alignment.BottomStart) },
        attribution = { Attribution(contentPadding = contentPadding, alignment = Alignment.BottomEnd) },
        scaleBar = { ScaleBar(modifier = Modifier.padding(bottom = SCALE_ABOVE_LOGO),
            contentPadding = contentPadding, alignment = Alignment.BottomStart,
            distanceUnits = DistanceUnits.METRIC) },
        style = { GenericStyle(style = style.uri, styleState = styleState) },
    ) {
        MapEffect(Unit) { mapView ->
            sdkMap = mapView.mapboxMap
            val result = mapView.mapboxMap.setBounds(CameraBoundsOptions.Builder()
                .minPitch(0.0).maxPitch(60.0).build())
            boundsAccepted = !result.isError
            if (!boundsAccepted) {
                diagnosticCallback(MapRenderDiagnostic.CAMERA_BOUNDS_REJECTED)
                statusCallback(MapRenderStatus.Failed(MapFailureReason.CAMERA_BOUNDS))
            }
        }
    }
}

private fun MapCameraPosition.toCameraOptions(): CameraOptions = CameraOptions.Builder()
    .center(Point.fromLngLat(longitude, latitude)).zoom(zoom).bearing(bearing).pitch(pitch).build()

/** Canonical GeoJSON longitude while the SDK camera may pan through wrapped worlds. */
internal fun canonicalLongitude(longitude: Double): Double =
    ((longitude + 180.0) % 360.0 + 360.0) % 360.0 - 180.0

private const val DEM_SOURCE_ID = "geosylva-terrain-dem"

private val SCALE_ABOVE_LOGO = 36.dp
