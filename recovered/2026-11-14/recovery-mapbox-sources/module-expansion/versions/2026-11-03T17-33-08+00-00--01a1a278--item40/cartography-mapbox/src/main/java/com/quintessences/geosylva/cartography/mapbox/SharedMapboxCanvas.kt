@file:OptIn(com.mapbox.maps.MapboxExperimental::class)

package com.quintessences.geosylva.cartography.mapbox

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraBoundsOptions
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapLoadingErrorType
import com.mapbox.maps.extension.compose.MapEffect
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
 */
@Composable
fun SharedMapboxCanvas(
    accessToken: String?,
    style: MapboxStyleSpec,
    initialCamera: MapCameraPosition,
    modifier: Modifier = Modifier,
    cameraCommand: MapCameraCommand? = null,
    relief: MapRelief = MapRelief(),
    onCameraChanged: (MapCameraPosition) -> Unit = {},
    onCommandConsumed: (String) -> Unit = {},
    onStatusChanged: (MapRenderStatus) -> Unit = {},
) {
    val statusCallback by rememberUpdatedState(onStatusChanged)
    val issue = mapConfigurationIssue(accessToken, style, relief)
    if (issue != null) {
        LaunchedEffect(issue) { statusCallback(issue) }
        Box(modifier)
        return
    }

    // Configure before composing MapboxMap: SideEffect would run too late for
    // AndroidView construction. No Mapbox API is touched in the rejected branch.
    var tokenConfigured by remember(accessToken) { mutableStateOf(false) }
    LaunchedEffect(accessToken) {
        MapboxOptions.accessToken = requireNotNull(accessToken)
        tokenConfigured = true
    }
    LaunchedEffect(style.id, style.uri) { statusCallback(MapRenderStatus.Loading(style.id)) }
    if (!tokenConfigured) {
        Box(modifier)
        return
    }

    ConfiguredMapboxCanvas(style, initialCamera, modifier, cameraCommand, relief,
        onCameraChanged, onCommandConsumed, onStatusChanged)
}

@Composable
private fun ConfiguredMapboxCanvas(
    style: MapboxStyleSpec,
    initialCamera: MapCameraPosition,
    modifier: Modifier,
    cameraCommand: MapCameraCommand?,
    relief: MapRelief,
    onCameraChanged: (MapCameraPosition) -> Unit,
    onCommandConsumed: (String) -> Unit,
    onStatusChanged: (MapRenderStatus) -> Unit,
) {
    val cameraCallback by rememberUpdatedState(onCameraChanged)
    val consumedCallback by rememberUpdatedState(onCommandConsumed)
    val statusCallback by rememberUpdatedState(onStatusChanged)
    val currentStyle by rememberUpdatedState(style)
    val mapState = rememberMapState()
    val viewport = rememberMapViewportState { setCameraOptions(initialCamera.toCameraOptions()) }
    var lastCommandId by rememberSaveable { mutableStateOf<String?>(null) }
    val styleState = remember(style.demSourceUrl, relief) {
        StyleState(terrainState = if (relief.enabled) {
            TerrainState(RasterDemSourceState(DEM_SOURCE_ID).apply {
                url = StringValue(requireNotNull(style.demSourceUrl))
                tileSize = LongValue(512)
            }).apply { exaggeration = DoubleValue(relief.exaggeration) }
        } else TerrainState.DISABLED)
    }

    LaunchedEffect(cameraCommand?.id) {
        val command = cameraCommand ?: return@LaunchedEffect
        if (command.id != lastCommandId) {
            viewport.setCameraOptions(command.position.toCameraOptions())
            lastCommandId = command.id
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
                statusCallback(MapRenderStatus.Ready(currentStyle.id))
            }
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
                // Never forward error.message/source URL: it may contain a token.
                statusCallback(MapRenderStatus.Failed(reason))
            }
        }
    }

    MapboxMap(
        modifier = modifier,
        mapViewportState = viewport,
        mapState = mapState,
        style = { GenericStyle(style = style.uri, styleState = styleState) },
    ) {
        MapEffect(Unit) { mapView ->
            val result = mapView.mapboxMap.setBounds(CameraBoundsOptions.Builder()
                .minPitch(0.0).maxPitch(60.0).build())
            if (result.isError) statusCallback(MapRenderStatus.Failed(MapFailureReason.CAMERA_BOUNDS))
        }
    }
}

private fun MapCameraPosition.toCameraOptions(): CameraOptions = CameraOptions.Builder()
    .center(Point.fromLngLat(longitude, latitude)).zoom(zoom).bearing(bearing).pitch(pitch).build()

/** Canonical GeoJSON longitude while the SDK camera may pan through wrapped worlds. */
internal fun canonicalLongitude(longitude: Double): Double =
    ((longitude + 180.0) % 360.0 + 360.0) % 360.0 - 180.0

private const val DEM_SOURCE_ID = "geosylva-terrain-dem"
