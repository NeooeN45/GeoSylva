package com.forestry.counter.presentation.screens.packs.offline

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forestry.counter.BuildConfig
import com.forestry.counter.R
import com.forestry.counter.data.local.entity.OfflineMapPackEntity
import com.forestry.counter.data.packs.offline.*
import com.forestry.counter.domain.usecase.maps.MapDataScope
import com.forestry.counter.domain.usecase.pack.PackManager
import com.forestry.counter.presentation.components.maps.MapboxRenderStatusOverlay
import com.forestry.counter.presentation.screens.packs.AdministrativePackMap
import com.mapbox.geojson.MultiPolygon
import com.mapbox.geojson.Polygon
import com.quintessences.geosylva.cartography.mapbox.*
import com.quintessences.geosylva.cartography.ui.offline.OfflinePackWorkspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflinePackManagerScreen(controller: OfflineMapPackController, onBack: () -> Unit,
    onLegacyFiles: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val state by controller.state.collectAsStateWithLifecycle()
    val legacyState by PackManager.getInstance(context).packState.collectAsStateWithLifecycle()
    val areas = remember(context) { AdministrativeOfflineAreas(context.applicationContext) }
    var catalog by remember { mutableStateOf<List<AdministrativeOfflineAreas.Area>>(emptyList()) }
    var areaError by remember { mutableStateOf(false) }
    LaunchedEffect(areas) { try { catalog = areas.catalog() } catch (_: Exception) { areaError = true } }
    LaunchedEffect(controller) { controller.activate(MapDataScope.Free) }
    var selected by rememberSaveable(state.authorityKey) { mutableStateOf(emptyList<String>()) }
    var level by rememberSaveable { mutableStateOf("regions") }
    var library by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var draft by remember { mutableStateOf<OfflineViewPackDraft?>(null) }
    var detailsId by rememberSaveable { mutableStateOf<String?>(null) }
    var openedId by rememberSaveable { mutableStateOf<String?>(null) }
    val details = state.packs.firstOrNull { it.id == detailsId }
    val opened = state.packs.firstOrNull { it.id == openedId && it.status == OfflinePackStatus.READY.name }
    val selectedNames = catalog.filter { it.id in selected }.map { it.name }
    val selectionName = selectedNames.joinToString(" · ").take(160)
    val readyCount = state.packs.count { it.status == OfflinePackStatus.READY.name }
    LaunchedEffect(state.authorityKey) { draft = null; detailsId = null; openedId = null }
    OfflinePackWorkspace(
        title = when {
            details != null -> stringResource(R.string.offline_pack_details)
            draft != null -> stringResource(R.string.offline_content)
            library -> stringResource(R.string.offline_library)
            else -> stringResource(if (level == "regions") R.string.pack_map_regions else R.string.pack_map_departments)
        },
        onBack = {
            when { openedId != null -> openedId = null; detailsId != null -> detailsId = null
                draft != null -> draft = null; library -> library = false; else -> onBack() }
        },
        onSearch = if (!library && draft == null && details == null) ({ search = true }) else null,
        onLibrary = { draft = null; openedId = null; detailsId = null; library = !library; controller.refresh() },
        modifier = modifier,
        map = {
            if (opened != null) OfflineSavedAreaMap(opened, Modifier.fillMaxSize())
            else AdministrativePackMap(selectedIds = if (library) state.packs.filter { it.status == OfflinePackStatus.READY.name }
                .flatMap { runCatching { kotlinx.serialization.json.Json.decodeFromString<List<String>>(it.territoriesJson) }.getOrDefault(emptyList()) }.toSet() else selected.toSet(),
                onTerritoryClick = { id, _ -> if (!library && draft == null) selected = if (id in selected) selected - id else selected + id },
                onVisibleLevelChanged = { level = it }, modifier = Modifier.fillMaxSize(), overlayTop = 72.dp,
                overlayBottom = if (draft != null || details != null || library) 310.dp else if (selected.isNotEmpty()) 180.dp else 20.dp)
        },
        panel = when {
            details != null -> ({ OfflinePackDetails(details, controller, onOpen = { openedId = details.id }) })
            draft != null -> ({ OfflineViewPackPreparation(controller, MapDataScope.Free, requireNotNull(draft), onDismiss = { draft = null }) })
            library -> ({
                Row(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.offline_library), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.offline_ready_count, readyCount), style = MaterialTheme.typography.labelLarge)
                }
                state.freeBytes?.let { Text(stringResource(R.string.offline_free, Formatter.formatFileSize(context, it)), style = MaterialTheme.typography.bodySmall) }
                Text(stringResource(R.string.offline_storage_shared), style = MaterialTheme.typography.bodySmall)
                if (state.removed) Text(stringResource(R.string.offline_removed), color = MaterialTheme.colorScheme.primary)
                if (state.error) Text(stringResource(R.string.offline_action_failed), color = MaterialTheme.colorScheme.error)
                if (state.packs.isEmpty()) Text(stringResource(R.string.offline_empty))
                state.packs.forEach { pack ->
                    ListItem(headlineContent = { Text(pack.name) }, supportingContent = {
                        val status = if (pack.id in state.running) OfflinePackStatus.DOWNLOADING else runCatching { OfflinePackStatus.valueOf(pack.status) }.getOrDefault(OfflinePackStatus.INCOMPLETE)
                        Text(offlineStatusLabel(status))
                    }, leadingContent = { Icon(Icons.Default.Download, null) }, modifier = Modifier.clickable { detailsId = pack.id })
                }
                TextButton(onLegacyFiles, Modifier.fillMaxWidth()) { Text(stringResource(R.string.offline_existing_files)) }
            })
            selected.isNotEmpty() -> ({
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) { Text(selectionName, style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.offline_whole_territories), style = MaterialTheme.typography.bodyMedium) }
                    IconButton({ selected = emptyList() }) { Icon(Icons.Default.Close, stringResource(R.string.offline_clear_selection)) }
                }
                if (areaError) Text(stringResource(R.string.offline_area_unavailable), color = MaterialTheme.colorScheme.error)
                Button(onClick = {
                    try {
                        val selection = areas.selection(catalog, selected.toSet())
                        val candidates = legacyState.allPacks.filter { it.territoryId in selection.departmentIds && it.signedManifest != null &&
                            it.contentKind in setOf("administrative_boundaries", "forestry_layers") && it.checksum != null }.map {
                            OfflineDataset(it.id, it.version, requireNotNull(it.checksum), it.name, it.buildDate.takeIf(String::isNotBlank)) }
                        draft = OfflineViewPackDraft(name = selectionName, geometryJson = selection.geometryJson,
                            minZoom = 6, maxZoom = 14, pixelRatio = density, styleUri = MapboxStyles.Satellite.uri,
                            territories = selection.departmentIds, datasetCandidates = candidates)
                    } catch (_: Exception) { areaError = true }
                }, enabled = catalog.isNotEmpty() && state.authorityKey != null, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.offline_show_content)) }
            })
            else -> null
        }
    )
    if (search) ModalBottomSheet(onDismissRequest = { search = false }) {
        OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.offline_search_territory)) }, modifier = Modifier.fillMaxWidth().padding(16.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            items(catalog.filter { it.name.contains(query, true) || it.id.contains(query, true) }, key = { it.id }) { area ->
                ListItem(headlineContent = { Text(area.name) }, supportingContent = { Text(area.id.substringAfter(':')) },
                    modifier = Modifier.clickable { selected = (selected + area.id).distinct(); search = false })
            }
        }
    }
    OfflinePackDialogs(controller)
}

/** Opens the exact saved sources and geometry through the same native map engine and TileStore. */
@Composable
private fun OfflineSavedAreaMap(row: OfflineMapPackEntity, modifier: Modifier = Modifier) {
    val shape = remember(row.definitionHash) {
        if (row.geometryJson.contains("\"MultiPolygon\"")) MultiPolygon.fromJson(row.geometryJson) else Polygon.fromJson(row.geometryJson)
    }
    val points = remember(shape) { when (shape) {
        is MultiPolygon -> shape.coordinates().flatten().flatten()
        is Polygon -> shape.coordinates().flatten()
        else -> emptyList()
    }.map { MapCoordinate(it.longitude(), it.latitude()) } }
    val coordinates = points.ifEmpty { listOf(MapCoordinate(2.4, 46.6)) }
    val scene = remember(row.definitionHash) { MapOverlayScene("offline:${row.id}", 1L,
        listOf(MapOverlayFeature(MapFeatureKey("offline-area", row.id), shape, MapOverlayStyle("#B9F1CB", fillOpacity = 0.04, lineWidth = 2.0), selectable = false))) }
    var frame by remember(row.id) { mutableStateOf<MapCameraFitCommand?>(MapCameraFitCommand(UUID.randomUUID().toString(), coordinates,
        paddingTopPx = 120.0, paddingBottomPx = 380.0)) }
    var status by remember(row.id) { mutableStateOf<MapRenderStatus>(MapRenderStatus.Loading("offline")) }
    var retry by remember { mutableStateOf(0) }
    key(row.id, retry) {
        SharedMapboxCanvas(accessToken = BuildConfig.MAPBOX_PUBLIC_ACCESS_TOKEN,
            style = MapboxStyleSpec("offline:${row.id}", row.styleUri),
            initialCamera = MapCameraPosition(coordinates.first().longitude, coordinates.first().latitude, row.minZoom.toDouble()),
            relief = MapRelief(enabled = row.extraTilesetsJson.contains("mapbox.mapbox-terrain-dem-v1")),
            overlays = scene, frameCommand = frame, onFrameCommandConsumed = { frame = null },
            onStatusChanged = { status = it }, modifier = modifier,
            contentPadding = PaddingValues(top = 90.dp, bottom = 310.dp))
    }
    MapboxRenderStatusOverlay(status, onRetry = { retry++ })
}
