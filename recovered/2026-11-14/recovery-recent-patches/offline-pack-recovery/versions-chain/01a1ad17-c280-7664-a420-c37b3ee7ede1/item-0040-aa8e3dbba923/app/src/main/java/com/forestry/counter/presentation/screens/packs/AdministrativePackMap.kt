package com.forestry.counter.presentation.screens.packs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.forestry.counter.BuildConfig
import com.forestry.counter.R
import com.forestry.counter.data.packs.AdministrativePackContent
import com.forestry.counter.presentation.components.maps.MapboxRenderStatusOverlay
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.MultiPolygon
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.quintessences.geosylva.cartography.mapbox.MapCameraCommand
import com.quintessences.geosylva.cartography.mapbox.MapCameraFitCommand
import com.quintessences.geosylva.cartography.mapbox.MapCameraPosition
import com.quintessences.geosylva.cartography.mapbox.MapCoordinate
import com.quintessences.geosylva.cartography.mapbox.MapFailureReason
import com.quintessences.geosylva.cartography.mapbox.MapOverlayStatus
import com.quintessences.geosylva.cartography.mapbox.MapboxStyleSpec
import com.quintessences.geosylva.cartography.mapbox.MapFeatureKey
import com.quintessences.geosylva.cartography.mapbox.MapOverlayFeature
import com.quintessences.geosylva.cartography.mapbox.MapOverlayScene
import com.quintessences.geosylva.cartography.mapbox.MapOverlayStyle
import com.quintessences.geosylva.cartography.mapbox.MapRenderStatus
import com.quintessences.geosylva.cartography.mapbox.MapboxStyles
import com.quintessences.geosylva.cartography.mapbox.SharedMapboxCanvas
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** Display threshold only. Selection identities are independent of the zoom. */
internal const val ADMINISTRATIVE_DEPARTMENT_ZOOM = 6.5
private const val BOUNDARY_LAYER = "administrative-boundary"
private const val LABEL_LAYER = "administrative-label"
private const val SELECTION_LAYER = "administrative-selection"
private val ADMINISTRATIVE_LOCAL_STYLE = MapboxStyleSpec(
    id = "administrative-local",
    uri = "local://administrative",
    demSourceUrl = null,
    styleJson = """{"version":8,"sources":{},"layers":[{"id":"background","type":"background","paint":{"background-color":"#EDF4EF"}}]}""",
)

/**
 * The common Mapbox canvas renders the real bundled/installed administrative
 * geometry. Choosing a region still lets the parent select its departments.
 * No GPS permission or business data read is performed here. Basemap availability
 * and local vector availability are separate: an installed administrative pack
 * is not represented as a downloaded Mapbox satellite region.
 */
@Composable
fun AdministrativePackMap(
    selectedIds: Set<String>,
    onTerritoryClick: (id: String, name: String) -> Unit,
    onVisibleLevelChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    installedContent: AdministrativePackContent? = null,
    overlayTop: Dp = 0.dp,
    overlayBottom: Dp = 0.dp,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val contentKey = "administrative:${installedContent?.territoryId ?: "metropolitan"}:${installedContent?.version ?: "bundled"}"
    val latestClick by rememberUpdatedState(onTerritoryClick)
    val latestLevelChanged by rememberUpdatedState(onVisibleLevelChanged)
    val sequence = remember { AtomicLong() }
    var savedLatitude by rememberSaveable(contentKey) { mutableStateOf(46.6) }
    var savedLongitude by rememberSaveable(contentKey) { mutableStateOf(2.4) }
    var savedZoom by rememberSaveable(contentKey) { mutableStateOf(4.0) }
    var savedBearing by rememberSaveable(contentKey) { mutableStateOf(0.0) }
    var savedPitch by rememberSaveable(contentKey) { mutableStateOf(0.0) }
    var hasSavedCamera by rememberSaveable(contentKey) { mutableStateOf(false) }
    var satellite by rememberSaveable { mutableStateOf(true) }
    var retry by remember { mutableStateOf(0) }
    var fallbackToLocal by remember(contentKey) { mutableStateOf(false) }
    var overlayFailed by remember(contentKey) { mutableStateOf(false) }
    val showSatellite = satellite && !fallbackToLocal
    val style = if (showSatellite) MapboxStyles.Satellite else ADMINISTRATIVE_LOCAL_STYLE
    var status by remember(contentKey) { mutableStateOf<MapRenderStatus>(MapRenderStatus.Loading(style.id)) }
    var cameraCommand by remember(contentKey) { mutableStateOf<MapCameraCommand?>(null) }
    var frameCommand by remember(contentKey) { mutableStateOf<MapCameraFitCommand?>(null) }

    // Tag the response: produceState retains its previous value until the new
    // coroutine runs, so never display a previous installed pack for one frame.
    val geometryLoad by produceState<AdministrativeLoad?>(null, context, contentKey, installedContent) {
        value = null
        val result = try {
            Result.success(withContext(Dispatchers.IO) { loadAdministrativeGeometry(context, installedContent) })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.failure(IllegalArgumentException("Administrative geometry unavailable"))
        }
        value = AdministrativeLoad(contentKey, result)
    }
    val geometry = geometryLoad?.takeIf { it.scopeId == contentKey }?.result
    val loaded = geometry?.getOrNull()
    val level = if (loaded?.installedPreview == true) {
        if (loaded.departments.features().orEmpty().isNotEmpty()) "departments" else "regions"
    } else visibleAdministrativeLevel(savedZoom)
    LaunchedEffect(contentKey, level) { latestLevelChanged(level) }

    val visibleBoundaries = when (level) {
        "departments" -> loaded?.departments
        else -> loaded?.regions
    }?.features().orEmpty()
    val visibleLabels = when (level) {
        "departments" -> loaded?.departmentLabels
        else -> loaded?.regionLabels
    }?.features().orEmpty()
    val visibleNames = remember(visibleBoundaries) {
        visibleBoundaries.associate { it.getStringProperty("territory_id") to it.getStringProperty("name") }
    }
    val currentNames by rememberUpdatedState(visibleNames)
    val currentInstalled by rememberUpdatedState(loaded?.installedPreview == true)
    val scene = remember(contentKey, loaded, level, selectedIds, showSatellite) {
        val features = buildList {
            visibleBoundaries.forEach { feature ->
                val id = feature.getStringProperty("territory_id")
                val selected = id in selectedIds
                add(MapOverlayFeature(MapFeatureKey(BOUNDARY_LAYER, id), requireNotNull(feature.geometry()),
                    MapOverlayStyle(colorHex = if (selected) "#64CF91" else "#B8DAC6",
                        fillOpacity = if (selected) { if (showSatellite) 0.22 else 0.65 } else { if (showSatellite) 0.035 else 0.72 },
                        lineWidth = if (selected) 3.2 else 1.5),
                    selectable = loaded?.installedPreview != true))
            }
            // A selection survives changing between regions and departments,
            // without making the other level selectable beneath the active one.
            val visibleIds = visibleBoundaries.map { it.getStringProperty("territory_id") }.toSet()
            (loaded?.regions?.features().orEmpty() + loaded?.departments?.features().orEmpty())
                .filter { it.getStringProperty("territory_id") in selectedIds && it.getStringProperty("territory_id") !in visibleIds }
                .forEach { feature ->
                    add(MapOverlayFeature(MapFeatureKey(SELECTION_LAYER, feature.getStringProperty("territory_id")),
                        requireNotNull(feature.geometry()),
                        MapOverlayStyle(colorHex = "#64CF91", fillOpacity = 0.0, lineWidth = 3.2), selectable = false))
                }
            visibleLabels.forEach { feature ->
                add(MapOverlayFeature(MapFeatureKey(LABEL_LAYER, feature.getStringProperty("territory_id")),
                    requireNotNull(feature.geometry()), MapOverlayStyle(colorHex = "#173E2D", pointRadius = 0.0),
                    label = feature.getStringProperty("name"), selectable = false))
            }
        }
        MapOverlayScene(contentKey, sequence.incrementAndGet(), features)
    }

    fun requestFrame() {
        val data = loaded ?: return
        val coordinates = if (data.installedPreview) administrativeCoordinates(data)
            else listOf(MapCoordinate(-5.5, 41.3), MapCoordinate(9.8, 51.2))
        if (coordinates.isNotEmpty()) frameCommand = MapCameraFitCommand(UUID.randomUUID().toString(), coordinates,
            paddingLeftPx = 20.0 * density, paddingTopPx = (100.0 + overlayTop.value) * density,
            paddingRightPx = 84.0 * density, paddingBottomPx = (72.0 + overlayBottom.value) * density)
    }
    LaunchedEffect(contentKey, loaded) { if (loaded != null && !hasSavedCamera) requestFrame() }

    Box(modifier) {
        key(contentKey, retry) {
            SharedMapboxCanvas(
                accessToken = BuildConfig.MAPBOX_PUBLIC_ACCESS_TOKEN,
                style = style,
                initialCamera = MapCameraPosition(savedLongitude, savedLatitude, savedZoom, savedBearing, savedPitch),
                modifier = Modifier.fillMaxSize(),
                overlays = scene,
                cameraCommand = cameraCommand,
                frameCommand = frameCommand,
                contentPadding = PaddingValues(start = 12.dp, top = 12.dp + overlayTop,
                    end = 12.dp, bottom = 44.dp + overlayBottom),
                onMapTap = { tap ->
                    val featureKey = tap.featureKey
                    if (!currentInstalled && featureKey?.layerId == BOUNDARY_LAYER) {
                        currentNames[featureKey.featureId]?.let { latestClick(featureKey.featureId, it) }
                    }
                },
                onCameraChanged = {
                    savedLatitude = it.latitude; savedLongitude = it.longitude
                    savedZoom = it.zoom; savedBearing = it.bearing; savedPitch = it.pitch
                    hasSavedCamera = true
                },
                onCommandConsumed = { if (cameraCommand?.id == it) cameraCommand = null },
                onFrameCommandConsumed = { if (frameCommand?.id == it) { frameCommand = null; hasSavedCamera = true } },
                onStatusChanged = {
                    status = it
                    if (showSatellite && it is MapRenderStatus.Failed && it.reason in setOf(
                            MapFailureReason.STYLE, MapFailureReason.SOURCE, MapFailureReason.TILE,
                            MapFailureReason.GLYPHS, MapFailureReason.SPRITE)) fallbackToLocal = true
                },
                onOverlayStatusChanged = {
                    when (it) {
                        is MapOverlayStatus.Applied -> if (it.scopeId == scene.scopeId && it.revision == scene.revision) overlayFailed = false
                        is MapOverlayStatus.Failed -> overlayFailed = true
                    }
                },
            )
        }
        MapboxRenderStatusOverlay(status = if (overlayFailed) MapRenderStatus.Failed(MapFailureReason.SOURCE) else status, onRetry = {
            fallbackToLocal = false
            overlayFailed = false
            retry++
            status = MapRenderStatus.Loading(style.id)
        })
        when {
            geometry?.isFailure == true -> Surface(
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
                color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium,
            ) {
                Text(stringResource(R.string.pack_map_render_failed), Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer)
            }
            loaded == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        if (loaded != null) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(start = 12.dp, end = 12.dp, top = 12.dp + overlayTop),
                shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), shadowElevation = 2.dp,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf(1.0 to R.string.pack_map_zoom_in, -1.0 to R.string.pack_map_zoom_out).forEach { (delta, label) ->
                        IconButton(onClick = {
                            cameraCommand = MapCameraCommand(UUID.randomUUID().toString(),
                                MapCameraPosition(savedLongitude, savedLatitude, (savedZoom + delta).coerceIn(2.0, 12.0), savedBearing, savedPitch))
                        }, modifier = Modifier.size(48.dp)) {
                            Icon(if (delta > 0) Icons.Default.Add else Icons.Default.Remove,
                                stringResource(label), Modifier.size(24.dp))
                        }
                    }
                    IconButton(onClick = { requestFrame() }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.CenterFocusStrong,
                            stringResource(if (installedContent == null) R.string.pack_map_recenter else R.string.pack_map_recenter_installed),
                            Modifier.size(24.dp))
                    }
                    IconButton(onClick = { satellite = !satellite; fallbackToLocal = false }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Layers, stringResource(com.quintessences.geosylva.cartography.ui.R.string.gs_map_basemap), Modifier.size(24.dp))
                    }
                }
            }
            if (!showSatellite) Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 56.dp + overlayTop),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f), shape = RoundedCornerShape(18.dp),
            ) {
                Text(stringResource(if (fallbackToLocal) R.string.pack_map_photo_unavailable else R.string.pack_map_local_selected),
                    style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, end = 8.dp, bottom = 8.dp + overlayBottom),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), shape = MaterialTheme.shapes.small,
            ) {
                Text(installedContent?.attribution ?: stringResource(R.string.pack_map_embedded_attribution),
                    style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}

private data class AdministrativeLoad(val scopeId: String, val result: Result<AdministrativeGeometry>)

private fun loadAdministrativeGeometry(context: android.content.Context, content: AdministrativePackContent?): AdministrativeGeometry {
    val empty = FeatureCollection.fromFeatures(emptyList<Feature>())
    if (content != null) {
        val prefix = if (content.territoryId.startsWith("region:")) "region:" else "department:"
        val boundaries = validatedAdministrativeCollection(content.boundariesJson, prefix)
        val labels = validatedAdministrativeLabels(content.labelsJson, prefix)
        validateLabelCorrespondence(boundaries, labels)
        require(boundaries.features().orEmpty().map { it.getStringProperty("territory_id") } == listOf(content.territoryId))
        return AdministrativeGeometry(
            regions = if (prefix == "region:") boundaries else empty,
            departments = if (prefix == "department:") boundaries else empty,
            regionLabels = if (prefix == "region:") labels else empty,
            departmentLabels = if (prefix == "department:") labels else empty,
            installedPreview = true,
        )
    }
    fun read(asset: String, prefix: String, labels: Boolean = false) = context.assets.open("packs/$asset.geojson").bufferedReader().use {
        if (labels) validatedAdministrativeLabels(it.readText(), prefix) else validatedAdministrativeCollection(it.readText(), prefix)
    }
    return AdministrativeGeometry(
        read("regions", "region:"), read("departments", "department:"),
        read("regions-labels", "region:", true), read("departments-labels", "department:", true),
    ).also {
        validateLabelCorrespondence(it.regions, it.regionLabels)
        validateLabelCorrespondence(it.departments, it.departmentLabels)
    }
}

private fun administrativeCoordinates(geometry: AdministrativeGeometry): List<MapCoordinate> =
    (geometry.regions.features().orEmpty() + geometry.departments.features().orEmpty()).flatMap { feature ->
        when (val shape = feature.geometry()) {
            is Polygon -> shape.coordinates().flatten()
            is MultiPolygon -> shape.coordinates().flatten().flatten()
            else -> emptyList()
        }
    }.map { MapCoordinate(it.longitude(), it.latitude()) }
private data class AdministrativeGeometry(
    val regions: FeatureCollection,
    val departments: FeatureCollection,
    val regionLabels: FeatureCollection,
    val departmentLabels: FeatureCollection,
    val installedPreview: Boolean = false
)

private fun validatedAdministrativeCollection(raw: String, prefix: String): FeatureCollection {
    val collection = FeatureCollection.fromJson(raw)
    val features = collection.features().orEmpty()
    require(features.isNotEmpty()) { "Couche administrative vide" }
    require(features.all {
        it.hasProperty("territory_id") && it.getStringProperty("territory_id").startsWith(prefix) &&
            it.hasProperty("name") && it.getStringProperty("name").isNotBlank() &&
            (it.geometry() is Polygon || it.geometry() is MultiPolygon)
    }) { "Contours ou identifiants administratifs incompatibles" }
    require(features.map { it.getStringProperty("territory_id") }.distinct().size == features.size) {
        "Identifiants administratifs dupliqués"
    }
    // Validate on the IO loader before publishing to Compose, not while rendering
    // an installed file that may contain malformed or out-of-range coordinates.
    features.forEach {
        MapOverlayFeature(MapFeatureKey("validation", it.getStringProperty("territory_id")), requireNotNull(it.geometry()))
    }
    return collection
}

/** Une seule ancre par territoire, préparée depuis son plus grand morceau. */
private fun validatedAdministrativeLabels(raw: String, prefix: String): FeatureCollection {
    val collection = FeatureCollection.fromJson(raw)
    val features = collection.features().orEmpty()
    require(features.isNotEmpty() && features.all {
        it.hasProperty("territory_id") && it.getStringProperty("territory_id").startsWith(prefix) &&
            it.hasProperty("name") && it.getStringProperty("name").isNotBlank() && it.geometry() is Point
    }) { "Ancres des noms administratifs incompatibles" }
    features.forEach {
        val point = it.geometry() as Point
        MapCoordinate(point.longitude(), point.latitude())
    }
    require(features.map { it.getStringProperty("territory_id") }.distinct().size == features.size) {
        "Un territoire possède plusieurs ancres de nom"
    }
    return collection
}

private fun validateLabelCorrespondence(boundaries: FeatureCollection, labels: FeatureCollection) {
    val namesFromBoundaries = boundaries.features().orEmpty().associate {
        it.getStringProperty("territory_id") to it.getStringProperty("name")
    }
    val namesFromLabels = labels.features().orEmpty().associate {
        it.getStringProperty("territory_id") to it.getStringProperty("name")
    }
    require(namesFromBoundaries == namesFromLabels) { "Les noms ne correspondent pas aux contours administratifs" }
}

private fun visibleAdministrativeLevel(zoom: Double): String =
    if (zoom >= ADMINISTRATIVE_DEPARTMENT_ZOOM) "departments" else "regions"
