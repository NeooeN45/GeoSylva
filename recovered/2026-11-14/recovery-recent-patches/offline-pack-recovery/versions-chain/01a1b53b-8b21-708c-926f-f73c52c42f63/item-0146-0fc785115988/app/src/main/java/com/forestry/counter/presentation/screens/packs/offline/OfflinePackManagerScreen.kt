package com.forestry.counter.presentation.screens.packs.offline

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import kotlinx.serialization.decodeFromString

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
    var libraryMap by rememberSaveable { mutableStateOf(false) }
    var storage by rememberSaveable { mutableStateOf(false) }
    var libraryFilter by rememberSaveable { mutableStateOf("ALL") }
    var libraryQuery by rememberSaveable { mutableStateOf("") }
    var storageSelection by rememberSaveable(state.authorityKey) { mutableStateOf(emptyList<String>()) }
    var search by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var draft by remember { mutableStateOf<OfflineViewPackDraft?>(null) }
    var detailsId by rememberSaveable { mutableStateOf<String?>(null) }
    var openedId by rememberSaveable { mutableStateOf<String?>(null) }
    val details = state.packs.firstOrNull { it.id == detailsId }
    val opened = state.packs.firstOrNull { it.id == openedId && it.status == OfflinePackStatus.READY.name }
    val selectedNames = catalog.filter { it.id in selected }.map { it.name }
    val selectionName = selectedNames.joinToString(" · ").take(160)
    val libraryPacks = state.packs.filter { pack ->
        pack.name.contains(libraryQuery, true) && when (libraryFilter) {
            "READY" -> pack.status == OfflinePackStatus.READY.name
            "ATTENTION" -> pack.status != OfflinePackStatus.READY.name && pack.status != OfflinePackStatus.RETAINED.name
            "RETAINED" -> pack.status == OfflinePackStatus.RETAINED.name
            else -> true
        }
    }
    val libraryAreas = remember(libraryPacks) { libraryPacks.mapNotNull { row -> runCatching {
        val geometry = if (row.geometryJson.contains("\"MultiPolygon\"")) MultiPolygon.fromJson(row.geometryJson) else Polygon.fromJson(row.geometryJson)
        MapOverlayFeature(MapFeatureKey("offline-saved-area", row.id), geometry, MapOverlayStyle(
            colorHex = when (row.status) {
                OfflinePackStatus.READY.name -> "#74D6A2"
                OfflinePackStatus.PAUSED.name, OfflinePackStatus.WAITING_WIFI.name, OfflinePackStatus.DOWNLOADING.name -> "#C89037"
                OfflinePackStatus.RETAINED.name -> "#889992"
                else -> "#B8654E"
            }, lineWidth = 2.8, fillOpacity = 0.10))
    }.getOrNull() } }
    val readyCount = state.packs.count { it.status == OfflinePackStatus.READY.name }
    LaunchedEffect(state.authorityKey) { draft = null; detailsId = null; openedId = null }
    LaunchedEffect(state.packs, draft?.id) {
        storageSelection = storageSelection.filter { id -> state.packs.any { it.id == id } }
        draft?.id?.let { id -> if (state.packs.any { it.id == id }) { draft = null; detailsId = id } }
    }
    OfflinePackWorkspace(
        title = when {
            storage -> stringResource(R.string.offline_storage_title)
            details != null -> stringResource(R.string.offline_pack_details)
            draft != null -> stringResource(R.string.offline_content)
            library -> stringResource(R.string.offline_library)
            else -> stringResource(if (level == "regions") R.string.pack_map_regions else R.string.pack_map_departments)
        },
        onBack = {
            when { storage -> storage = false; openedId != null -> openedId = null; detailsId != null -> detailsId = null
                draft != null -> draft = null; library -> library = false; else -> onBack() }
        },
        onSearch = if (!library && draft == null && details == null) ({ search = true }) else null,
        onLibrary = { storage = false; draft = null; openedId = null; detailsId = null; library = !library; controller.refresh() },
        modifier = modifier,
        map = {
            if (opened != null) OfflineSavedAreaMap(opened, Modifier.fillMaxSize())
            else AdministrativePackMap(selectedIds = if (library) state.packs.filter { it.status == OfflinePackStatus.READY.name }
                .flatMap { runCatching { kotlinx.serialization.json.Json.decodeFromString<List<String>>(it.territoriesJson) }.getOrDefault(emptyList()) }.toSet() else selected.toSet(),
                onTerritoryClick = { id, _ ->
                    if (library) {
                        val departmentIds = if (id.startsWith("region:")) catalog.filter { it.regionId == id }.map { it.id }.toSet() else setOf(id)
                        libraryPacks.firstOrNull { pack ->
                            val coverage = runCatching { kotlinx.serialization.json.Json.decodeFromString<List<String>>(pack.territoriesJson) }.getOrDefault(emptyList())
                            departmentIds.isNotEmpty() && departmentIds.all { it in coverage }
                        }?.let { detailsId = it.id }
                    } else if (draft == null) selected = if (id in selected) selected - id else selected + id
                },
                onVisibleLevelChanged = { level = it }, modifier = Modifier.fillMaxSize(), overlayTop = 72.dp,
                overlayBottom = if (library && libraryMap && !storage) 160.dp else if (draft != null || details != null || library) 310.dp else if (selected.isNotEmpty()) 180.dp else 20.dp,
                additionalAreas = if (library) libraryAreas else emptyList(), onSavedAreaClick = { detailsId = it })
        },
        panel = when {
            storage -> ({
                Text(stringResource(R.string.offline_storage_title), style = MaterialTheme.typography.titleLarge)
                state.tileStoreBytes?.let { Text(stringResource(R.string.offline_tile_storage, Formatter.formatFileSize(context, it))) }
                state.freeBytes?.let { Text(stringResource(R.string.offline_free, Formatter.formatFileSize(context, it))) }
                Text(stringResource(R.string.offline_storage_shared), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.offline_storage_active_preserved), style = MaterialTheme.typography.bodySmall)
                state.packs.forEach { pack ->
                    val selectable = pack.status in setOf(OfflinePackStatus.RETAINED.name, OfflinePackStatus.PAUSED.name, OfflinePackStatus.INCOMPLETE.name, OfflinePackStatus.FAILED.name) && pack.id !in state.running
                    Row(Modifier.fillMaxWidth()) {
                        Checkbox(pack.id in storageSelection, onCheckedChange = { checked -> storageSelection = if (checked) storageSelection + pack.id else storageSelection - pack.id }, enabled = selectable)
                        Column(Modifier.weight(1f).padding(top = 10.dp)) {
                            Text(pack.name)
                            Text(offlineStatusLabel(runCatching { OfflinePackStatus.valueOf(pack.status) }.getOrDefault(OfflinePackStatus.INCOMPLETE)), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (state.removed) Text(stringResource(R.string.offline_removed), color = MaterialTheme.colorScheme.primary)
                if (state.error) Text(stringResource(R.string.offline_removal_partial), color = MaterialTheme.colorScheme.error)
                Button({ controller.prepareRemoval(storageSelection.toSet()) }, enabled = storageSelection.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.offline_review_removal, storageSelection.size))
                }
            })
            details != null -> ({ OfflinePackDetails(details, controller, onOpen = { openedId = details.id }, onPrepareUpdate = {
                val required = runCatching { kotlinx.serialization.json.Json.decodeFromString<List<OfflineDataset>>(details.datasetsJson) }.getOrDefault(emptyList())
                val candidates = legacyState.allPacks.filter { current -> required.any { it.id == current.id } && current.signedManifest != null && current.checksum != null }.map {
                    OfflineDataset(it.id, it.version, requireNotNull(it.checksum), it.name, null) }
                draft = OfflineViewPackDraft(name = details.name, geometryJson = details.geometryJson,
                    minZoom = details.minZoom, maxZoom = details.maxZoom, pixelRatio = details.pixelRatio, styleUri = details.styleUri,
                    initiallyRelief = details.extraTilesetsJson.contains("mapbox.mapbox-terrain-dem-v1"),
                    territories = runCatching { kotlinx.serialization.json.Json.decodeFromString<List<String>>(details.territoriesJson).toSet() }.getOrDefault(emptySet()),
                    datasetCandidates = candidates, initiallySelectedDatasetIds = required.map { it.id }.toSet())
                detailsId = null
            }, onContinue = onBack) })
            draft != null -> ({ OfflineViewPackPreparation(controller, MapDataScope.Free, requireNotNull(draft), onDismiss = { draft = null }) })
            library -> ({
                Row(Modifier.fillMaxWidth()) {
                    TextButton({ libraryMap = true }, Modifier.weight(1f)) { Text(stringResource(R.string.pack_tab_map)) }
                    TextButton({ libraryMap = false }, Modifier.weight(1f)) { Text(stringResource(R.string.offline_list)) }
                    Text(stringResource(R.string.offline_ready_count, readyCount), style = MaterialTheme.typography.labelLarge)
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ALL" to R.string.offline_filter_all, "READY" to R.string.offline_filter_ready,
                        "ATTENTION" to R.string.offline_filter_attention, "RETAINED" to R.string.offline_filter_retained).forEach { (key, label) ->
                        FilterChip(libraryFilter == key, { libraryFilter = key }, label = { Text(stringResource(label)) })
                    }
                }
                if (!libraryMap) {
                    OutlinedTextField(libraryQuery, { libraryQuery = it }, singleLine = true, label = { Text(stringResource(R.string.offline_search_packs)) }, modifier = Modifier.fillMaxWidth())
                    if (libraryPacks.isEmpty()) Text(stringResource(R.string.offline_empty))
                    libraryPacks.forEach { pack ->
                        ListItem(headlineContent = { Text(pack.name) }, supportingContent = {
                            val status = if (pack.id in state.running) OfflinePackStatus.DOWNLOADING else runCatching { OfflinePackStatus.valueOf(pack.status) }.getOrDefault(OfflinePackStatus.INCOMPLETE)
                            Text(offlineStatusLabel(status))
                        }, leadingContent = { Icon(Icons.Default.Download, null) }, modifier = Modifier.clickable { detailsId = pack.id })
                    }
                }
                if (state.error) Text(stringResource(R.string.offline_action_failed), color = MaterialTheme.colorScheme.error)
                TextButton({ storageSelection = emptyList(); storage = true; controller.refresh() }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.offline_storage_title)) }
                if (!libraryMap) TextButton(onLegacyFiles, Modifier.fillMaxWidth()) { Text(stringResource(R.string.offline_existing_files)) }
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
                            OfflineDataset(it.id, it.version, requireNotNull(it.checksum), it.name, null) }
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
    val density = LocalDensity.current.density
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
        paddingTopPx = 110.0 * density, paddingBottomPx = 310.0 * density)) }
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
