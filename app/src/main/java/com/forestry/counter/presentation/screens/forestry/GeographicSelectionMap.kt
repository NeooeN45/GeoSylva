package com.forestry.counter.presentation.screens.forestry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.forestry.counter.BuildConfig
import com.forestry.counter.R
import com.forestry.counter.domain.geo.GeoImportCoordinatePolicy
import com.forestry.counter.domain.location.ParcelBounds
import com.forestry.counter.domain.location.ParcelCoordinate
import com.forestry.counter.domain.location.ParcelGeometry
import com.forestry.counter.domain.location.SamplingFootprint
import com.forestry.counter.domain.location.PlotBoundaryDifference
import com.forestry.counter.domain.location.PlotOutsidePreview
import com.forestry.counter.domain.location.PlotOutsidePreviewFailure
import com.forestry.counter.presentation.components.maps.MapboxRenderStatusOverlay
import com.forestry.counter.presentation.components.maps.MapOfflineConnectivityBadge
import com.forestry.counter.presentation.components.maps.rememberMapNetworkAvailable
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Geometry
import com.mapbox.geojson.MultiPolygon
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.quintessences.geosylva.cartography.mapbox.MapCameraCommand
import com.quintessences.geosylva.cartography.mapbox.MapCameraFitCommand
import com.quintessences.geosylva.cartography.mapbox.MapCameraPosition
import com.quintessences.geosylva.cartography.mapbox.MapRelief
import com.quintessences.geosylva.cartography.mapbox.MapCameraPositionSaver
import com.quintessences.geosylva.cartography.mapbox.MapCoordinate
import com.quintessences.geosylva.cartography.mapbox.MapFeatureKey
import com.quintessences.geosylva.cartography.mapbox.MapFailureReason
import com.quintessences.geosylva.cartography.mapbox.MapOverlayFeature
import com.quintessences.geosylva.cartography.mapbox.MapOverlayScene
import com.quintessences.geosylva.cartography.mapbox.MapOverlayStyle
import com.quintessences.geosylva.cartography.mapbox.MapOverlayLabelBadge
import com.quintessences.geosylva.cartography.mapbox.MapOverlayFillPattern
import com.quintessences.geosylva.cartography.mapbox.MapOverlayStatus
import com.quintessences.geosylva.cartography.mapbox.MapRenderStatus
import com.quintessences.geosylva.cartography.mapbox.MapboxStyles
import com.quintessences.geosylva.cartography.mapbox.SharedMapboxCanvas
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

private const val SELECTION_PARCELS = "selection-parcels"
private const val SELECTION_LABELS = "selection-parcel-labels"
private const val SELECTION_PARENT = "selection-parent"
private const val SELECTION_POINTS = "selection-plot-points"
private const val SELECTION_FOOTPRINTS = "selection-plot-footprints"
private const val SELECTION_GRID = "selection-grid-cells"

/**
 * Shared Mapbox renderer for forest/parcel selection and plot planning.
 *
 * The parent supplies already authorised WGS84 vector data and persists choices.
 * Taps return a coordinate, never an inferred parcel, fix or access right. Only
 * native camera-idle bounds trigger viewport queries; selecting another feature
 * does not frame it. Camera framing is explicit and independent of style/data.
 * Satellite/plan remain requested when connectivity is absent. The shared SDK
 * resolves prepared resources from its cache/TileStore. Connectivity never proves
 * resource readiness or completeness of an offline pack.
 */
@Composable
fun GeographicSelectionMap(
    featureCollectionJson: String,
    selectedFeatureId: String?,
    initialBounds: ParcelBounds?,
    onMapClick: (ParcelCoordinate) -> Unit,
    onViewportChanged: (ParcelBounds, Double) -> Unit,
    modifier: Modifier = Modifier,
    plotPoints: List<ParcelCoordinate> = emptyList(),
    parcelGeometry: ParcelGeometry? = null,
    plotFootprints: List<SamplingFootprint> = emptyList(),
    gridCells: List<ParcelGeometry> = emptyList(),
    plotPointIds: List<String> = emptyList(),
    selectedPlotId: String? = null,
    onPlotSelected: (String) -> Unit = {},
    controlsTopPadding: Dp = 12.dp,
    /** Hosts with a visible creation sheet hide the native scale; attribution remains visible. */
    scaleBarVisible: Boolean = true,
    onMapRenderStatusChanged: (MapRenderStatus) -> Unit = {},
    onMapOverlayStatusChanged: (MapOverlayStatus) -> Unit = {},
    onOutsidePreviewChanged: (PlotOutsidePreview) -> Unit = {},
) {
    val density = LocalDensity.current
    val latestClick by rememberUpdatedState(onMapClick)
    val latestPlotSelected by rememberUpdatedState(onPlotSelected)
    val latestViewport by rememberUpdatedState(onViewportChanged)
    val latestRenderStatus by rememberUpdatedState(onMapRenderStatusChanged)
    val latestOverlayStatus by rememberUpdatedState(onMapOverlayStatusChanged)
    val latestOutsidePreview by rememberUpdatedState(onOutsidePreviewChanged)
    val sceneScope = rememberSaveable { "geographic-selection-${UUID.randomUUID()}" }
    val sequence = remember { AtomicLong() }
    var retry by remember { mutableStateOf(0) }
    var preferSatellite by rememberSaveable { mutableStateOf(true) }
    var savedCamera by rememberSaveable(stateSaver = MapCameraPositionSaver) {
        mutableStateOf(MapCameraPosition(2.4, 46.6, 4.0))
    }
    var framedBoundsKey by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraCommand by remember { mutableStateOf<MapCameraCommand?>(null) }
    // Keep an unconsumed initial frame across rotation while the SDK is loading.
    var pendingFrameId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingFrameBounds by rememberSaveable { mutableStateOf<DoubleArray?>(null) }
    val requestedStyle = if (preferSatellite) MapboxStyles.Satellite else MapboxStyles.Plan
    val networkAvailable = rememberMapNetworkAvailable()
    // Connectivity is a hint. Keep the style so prepared TileStore/style-pack resources remain usable.
    val style = requestedStyle
    var renderStatus by remember(style, retry) { mutableStateOf<MapRenderStatus>(MapRenderStatus.Loading(style.id)) }

    val selectedFootprint = plotFootprints.getOrNull(plotPointIds.indexOf(selectedPlotId))
    val outsideRequested = parcelGeometry != null && selectedFootprint?.crossesBoundary == true
    val outsideToken = remember(parcelGeometry, selectedFootprint?.geometry, selectedPlotId, outsideRequested) { Any() }
    val preparedOutside by produceState<OutsidePreparedSnapshot?>(null, outsideToken) {
        value = null
        val parent = parcelGeometry
        val footprint = selectedFootprint
        val result = if (outsideRequested && parent != null && footprint != null) {
            withContext(Dispatchers.Default) { PlotBoundaryDifference.outside(parent, footprint.geometry) }
        } else PlotOutsidePreview.NotRequested
        value = OutsidePreparedSnapshot(outsideToken, result)
    }
    // A former candidate's hatching is never displayed while a new candidate is calculated.
    val outsideStatus = preparedOutside?.takeIf { it.token === outsideToken }?.result
        ?: if (outsideRequested) PlotOutsidePreview.Computing else PlotOutsidePreview.NotRequested
    val outsideGeometry = (outsideStatus as? PlotOutsidePreview.Available)?.geometry

    // Clear displayed vectors while replacing the input snapshot. An invalid or
    // cancelled parse must not keep vectors from an older authorised selection.
    val inputSnapshot = remember(featureCollectionJson, selectedFeatureId, plotPoints, parcelGeometry, plotFootprints, gridCells, plotPointIds, selectedPlotId, outsideGeometry) { Any() }
    val preparedData by produceState<SelectionPreparedSnapshot?>(null, inputSnapshot) {
        value = null
        val outcome: Result<List<MapOverlayFeature>> = try {
            Result.success(withContext(Dispatchers.Default) {
                geographicSelectionFeatures(featureCollectionJson, selectedFeatureId, plotPoints,
                    parcelGeometry, plotFootprints, gridCells, plotPointIds, selectedPlotId, outsideGeometry)
            })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.failure(IllegalArgumentException("Invalid geographic selection display data"))
        }
        value = SelectionPreparedSnapshot(inputSnapshot, outcome)
    }
    // produceState restarts after composition: an input identity prevents its
    // previous value being rendered for one frame in a new scope/selection.
    val renderData = preparedData?.takeIf { it.inputSnapshot === inputSnapshot }?.outcome
    val scene = remember(renderData, selectedFeatureId, selectedPlotId) {
        val features = renderData?.getOrNull().orEmpty()
        MapOverlayScene(
            scopeId = sceneScope,
            revision = sequence.incrementAndGet(),
            features = features,
            selectedKey = (selectedPlotId?.let { MapFeatureKey(SELECTION_POINTS, it) }
                ?: selectedFeatureId?.let { MapFeatureKey(SELECTION_PARCELS, it) })
                ?.takeIf { candidate -> features.any { it.key == candidate } },
        )
    }
    var lastAppliedScene by remember { mutableStateOf<MapOverlayStatus.Applied?>(null) }
    val outsideDisplayStatus = when {
        outsideStatus !is PlotOutsidePreview.Available -> outsideStatus
        lastAppliedScene?.scopeId != scene.scopeId || lastAppliedScene?.revision != scene.revision ||
            scene.features.none { it.key.layerId == "selection-plot-outside" } -> PlotOutsidePreview.Computing
        (lastAppliedScene?.omittedPatternCount ?: 0) > 0 -> PlotOutsidePreview.Unavailable(PlotOutsidePreviewFailure.OPERATION_FAILED)
        else -> outsideStatus
    }
    LaunchedEffect(outsideDisplayStatus, outsideToken, scene.revision) { latestOutsidePreview(outsideDisplayStatus) }

    var overlayFailed by remember(scene, style, retry) { mutableStateOf(false) }

    BoxWithConstraints(modifier) {
        val widthPx = with(density) { maxWidth.toPx() }.toDouble()
        val heightPx = with(density) { maxHeight.toPx() }.toDouble()
        fun padding(dp: Int, size: Double, fraction: Double) =
            with(density) { dp.dp.toPx() }.toDouble().let { requested ->
                if (size.isFinite() && size > 0) requested.coerceAtMost(size * fraction) else requested
            }
        fun frame(bounds: ParcelBounds) {
            pendingFrameBounds = doubleArrayOf(bounds.west, bounds.south, bounds.east, bounds.north)
            pendingFrameId = UUID.randomUUID().toString()
        }
        val frameCommand = remember(pendingFrameId, pendingFrameBounds, widthPx, heightPx, density, controlsTopPadding) {
            val bounds = pendingFrameBounds
            pendingFrameId?.takeIf { bounds != null }?.let { id ->
                requireNotNull(bounds)
                MapCameraFitCommand(
                    id = id,
                    coordinates = listOf(MapCoordinate(bounds[0], bounds[1]), MapCoordinate(bounds[2], bounds[3])),
                    paddingTopPx = padding(maxOf(98, controlsTopPadding.value.toInt() + 12), heightPx, 0.5),
                    paddingLeftPx = padding(28, widthPx, 0.15),
                    paddingBottomPx = padding(62, heightPx, 0.2),
                    paddingRightPx = padding(74, widthPx, 0.25),
                )
            }
        }
        LaunchedEffect(initialBounds, widthPx, heightPx) {
            val bounds = initialBounds ?: return@LaunchedEffect
            val boundsKey = "${bounds.west},${bounds.south},${bounds.east},${bounds.north}"
            if (framedBoundsKey != boundsKey && widthPx.isFinite() && heightPx.isFinite() && widthPx > 0 && heightPx > 0) {
                // A request is not an applied frame. Preserve a matching
                // restored pending request until the native SDK consumes it.
                if (pendingFrameId == null || pendingFrameBounds?.joinToString(",") != boundsKey) frame(bounds)
            }
        }
        key(retry) {
            SharedMapboxCanvas(
                accessToken = BuildConfig.MAPBOX_PUBLIC_ACCESS_TOKEN,
                style = style,
                initialCamera = savedCamera,
                relief = MapRelief(enabled = style.demSourceUrl != null),
                modifier = Modifier.fillMaxSize(),
                cameraCommand = cameraCommand,
                frameCommand = frameCommand,
                overlays = scene,
                contentPadding = PaddingValues(start = 12.dp, top = controlsTopPadding, end = 72.dp, bottom = 12.dp),
                scaleBarVisible = scaleBarVisible,
                onCameraChanged = { savedCamera = it },
                onMapTap = { tap ->
                    val plotId = tap.featureKey?.takeIf { it.layerId == SELECTION_POINTS }?.featureId
                    if (plotId != null && plotId in plotPointIds) latestPlotSelected(plotId)
                    else latestClick(ParcelCoordinate(tap.coordinate.longitude, tap.coordinate.latitude))
                },
                onViewportChanged = { viewport ->
                    val bounds = viewport.bounds
                    if (bounds.west < bounds.east && bounds.south < bounds.north) {
                        latestViewport(ParcelBounds(bounds.west, bounds.south, bounds.east, bounds.north), viewport.camera.zoom)
                    }
                },
                onCommandConsumed = { id -> if (cameraCommand?.id == id) cameraCommand = null },
                onFrameCommandConsumed = { id ->
                    if (pendingFrameId == id) {
                        framedBoundsKey = pendingFrameBounds?.joinToString(",")
                        pendingFrameId = null
                        pendingFrameBounds = null
                    }
                },
                onStatusChanged = { renderStatus = it; latestRenderStatus(it) },
                onOverlayStatusChanged = {
                    overlayFailed = it is MapOverlayStatus.Failed
                    lastAppliedScene = it as? MapOverlayStatus.Applied
                    latestOverlayStatus(it)
                },
            )
        }
        MapboxRenderStatusOverlay(
            status = if (overlayFailed) MapRenderStatus.Failed(MapFailureReason.SOURCE) else renderStatus,
            onRetry = { retry++ },
            modifier = Modifier.align(Alignment.Center),
        )
        if (!networkAvailable) MapOfflineConnectivityBadge(
            Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 100.dp))
        if (outsideDisplayStatus != PlotOutsidePreview.NotRequested) {
            Surface(Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 48.dp).widthIn(max = 330.dp),
                shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .97f)) {
                Text(stringResource(when (outsideDisplayStatus) {
                    is PlotOutsidePreview.Available -> R.string.fc_outside_preview_polygon
                    PlotOutsidePreview.Empty -> R.string.fc_outside_preview_empty
                    PlotOutsidePreview.Computing -> R.string.fc_outside_preview_loading
                    else -> R.string.fc_outside_preview_unavailable
                }), Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (renderData?.isFailure == true) {
            Surface(
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(stringResource(R.string.pack_map_render_failed), Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = controlsTopPadding, end = 12.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            shadowElevation = 2.dp,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                fun zoom(delta: Double) {
                    cameraCommand = MapCameraCommand("selection-zoom-${sequence.incrementAndGet()}",
                        savedCamera.copy(zoom = (savedCamera.zoom + delta).coerceIn(2.0, 22.0)))
                }
                IconButton(onClick = { zoom(1.0) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Add, stringResource(R.string.pack_map_zoom_in))
                }
                IconButton(onClick = { zoom(-1.0) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Remove, stringResource(R.string.pack_map_zoom_out))
                }
                val recenterBounds = initialBounds ?: parcelGeometry?.bounds
                if (recenterBounds != null) {
                    IconButton(onClick = { frame(recenterBounds) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.CenterFocusStrong, stringResource(R.string.geographic_map_recenter))
                    }
                }
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = controlsTopPadding).widthIn(max = 210.dp),
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        ) {
            TextButton(onClick = { preferSatellite = !preferSatellite }) {
                Icon(Icons.Default.Layers, null, Modifier.size(16.dp))
                Text(stringResource(if (preferSatellite) R.string.map_layer_satellite else R.string.map_layer_carto),
                    Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private data class SelectionPreparedSnapshot(
    val inputSnapshot: Any,
    val outcome: Result<List<MapOverlayFeature>>,
)
private data class OutsidePreparedSnapshot(val token: Any, val result: PlotOutsidePreview)

/**
 * Builds display-only features from the host's qualified data, preserving parcel
 * identity and WGS84 geometry. No DAO, GPS, permission inference or reprojection.
 * Plot numbering is presentation order, not a newly generated business identity.
 */
internal fun geographicSelectionFeatures(
    raw: String,
    selectedFeatureId: String?,
    plotPoints: List<ParcelCoordinate>,
    parcelGeometry: ParcelGeometry?,
    plotFootprints: List<SamplingFootprint>,
    gridCells: List<ParcelGeometry>,
    plotPointIds: List<String> = emptyList(),
    selectedPlotId: String? = null,
    outsideFootprint: ParcelGeometry? = null,
): List<MapOverlayFeature> {
    require(raw.length <= 4 * 1024 * 1024)
    require(plotPointIds.isEmpty() || (plotPointIds.size == plotPoints.size && plotPointIds.distinct().size == plotPointIds.size))
    require(plotPoints.size <= 500 && plotFootprints.size <= 500 && gridCells.size <= 500)
    val root = Json.parseToJsonElement(raw).jsonObject
    require(root["type"] == JsonPrimitive("FeatureCollection") && root["features"] is JsonArray)
    GeoImportCoordinatePolicy.validateGeoJson(root)
    val collection = FeatureCollection.fromJson(raw)
    val parcels = collection.features().orEmpty()
    require(parcels.size <= 500)
    val ids = mutableSetOf<String>()
    val result = mutableListOf<MapOverlayFeature>()
    val labels = mutableListOf<MapOverlayFeature>()
    parcels.forEach { feature ->
        val geometry = requireNotNull(feature.geometry())
        require(geometry is Polygon || geometry is MultiPolygon)
        val id = requireNotNull(selectionStringProperty(feature, "parcel_id"))
        require(id.isNotBlank() && id.length <= 200 && ids.add(id))
        // The strict domain parser rejects projected, non-finite or invalid data;
        // its normalised geometry, not a guessed bounds rectangle, is rendered.
        val parcel = ParcelGeometry.fromGeoJson(geometry.toJson())
        val selected = id == selectedFeatureId
        result += MapOverlayFeature(
            key = MapFeatureKey(SELECTION_PARCELS, id), geometry = selectionGeometry(parcel),
            style = MapOverlayStyle(colorHex = if (selected) "#117B43" else "#156579",
                // The shared renderer adds 2px for selected identities.
                lineWidth = if (selected) 2.0 else 1.8, fillOpacity = if (selected) 0.30 else 0.10,
                strokeWidth = if (selected) 1.0 else 1.1),
        )
        val name = (selectionStringProperty(feature, "parcel_label") ?: selectionStringProperty(feature, "label") ?: id).take(60)
        selectionLabelAnchor(parcel, feature)?.let { point ->
            labels += MapOverlayFeature(
                key = MapFeatureKey(SELECTION_LABELS, id), geometry = Point.fromLngLat(point.longitude, point.latitude),
                style = MapOverlayStyle(colorHex = "#103F35", pointRadius = 0.0, fillOpacity = 0.0),
                label = name, selectable = false,
            )
        }
    }
    parcelGeometry?.let {
        result += MapOverlayFeature(MapFeatureKey(SELECTION_PARENT, "parent"), selectionGeometry(it),
            style = MapOverlayStyle(colorHex = "#0D754B", lineWidth = 3.5, fillOpacity = 0.18, strokeWidth = 1.25), selectable = false)
    }
    gridCells.forEachIndexed { index, geometry ->
        result += MapOverlayFeature(MapFeatureKey(SELECTION_GRID, index.toString()), selectionGeometry(geometry),
            style = MapOverlayStyle(colorHex = "#566A61", lineWidth = 1.0, fillOpacity = 0.0,
                lineDashArray = listOf(3.0, 2.0)), selectable = false)
    }
    plotFootprints.forEachIndexed { index, footprint ->
        result += MapOverlayFeature(MapFeatureKey(SELECTION_FOOTPRINTS, index.toString()), selectionGeometry(footprint.geometry),
            style = MapOverlayStyle(colorHex = if (footprint.crossesBoundary) "#6560A5" else "#1E8A58",
                lineWidth = 2.0, fillOpacity = 0.22), selectable = false)
    }
    result += labels
    plotPoints.forEachIndexed { index, coordinate ->
        result += MapOverlayFeature(MapFeatureKey(SELECTION_POINTS, plotPointIds.getOrNull(index) ?: index.toString()),
            Point.fromLngLat(coordinate.longitude, coordinate.latitude),
            style = MapOverlayStyle(colorHex = "#176CAA",
                pointRadius = if (plotPointIds.getOrNull(index) == selectedPlotId) 10.0 else 7.0,
                labelBadge = MapOverlayLabelBadge.CIRCLE),
            label = (index + 1).toString(), selectable = plotPointIds.isNotEmpty())
    }
    if (outsideFootprint != null && selectedPlotId != null && selectedPlotId in plotPointIds) {
        result += MapOverlayFeature(MapFeatureKey("selection-plot-outside", selectedPlotId), selectionGeometry(outsideFootprint),
            MapOverlayStyle(colorHex = "#6560A5", lineWidth = 1.2, fillOpacity = .30,
                fillPattern = MapOverlayFillPattern.HATCH), selectable = false)
    }
    return result
}

private fun selectionGeometry(geometry: ParcelGeometry): Geometry = requireNotNull(
    Feature.fromJson("""{"type":"Feature","properties":{},"geometry":${geometry.toGeoJson()}}""").geometry(),
)

private fun selectionStringProperty(feature: Feature, key: String): String? = feature.getProperty(key)?.let { value ->
    if (value.isJsonPrimitive && value.asJsonPrimitive.isString) value.asString else null
}

/** Display anchor only; never persisted as a survey/navigation coordinate. */
private fun selectionLabelAnchor(geometry: ParcelGeometry, feature: Feature): ParcelCoordinate? {
    val bounds = geometry.bounds
    if (geometry.contains(bounds.center)) return bounds.center
    for (y in 1..7) for (x in 1..7) {
        val point = ParcelCoordinate(bounds.west + (bounds.east - bounds.west) * x / 8,
            bounds.south + (bounds.north - bounds.south) * y / 8)
        if (geometry.contains(point)) return point
    }
    val vertex = when (val polygon = feature.geometry()) {
        is Polygon -> polygon.coordinates().firstOrNull()?.firstOrNull()
        is MultiPolygon -> polygon.coordinates().firstOrNull()?.firstOrNull()?.firstOrNull()
        else -> null
    }
    return vertex?.let { ParcelCoordinate(it.longitude(), it.latitude()) }
}
