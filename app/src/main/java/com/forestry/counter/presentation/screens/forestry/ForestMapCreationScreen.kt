package com.forestry.counter.presentation.screens.forestry

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forestry.counter.R
import com.forestry.counter.domain.location.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import java.util.Locale

/** Select a real forest first; the manual wizard remains available for unlisted/private forests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForestMapCreationScreen(
    viewModel: CreateForestViewModel,
    maps: ParcelMapRepository,
    onNavigateBack: () -> Unit,
    onCreated: () -> Unit,
) {
    var manual by rememberSaveable { mutableStateOf(false) }
    if (manual) {
        CreateForestWizard(viewModel, onNavigateBack = { manual = false }, onCreated = onCreated)
        return
    }
    val selected by viewModel.selectedForest.collectAsStateWithLifecycle()
    val form by viewModel.formState.collectAsStateWithLifecycle()
    val lookup by viewModel.lookupState.collectAsStateWithLifecycle()
    val saving by viewModel.isSaving.collectAsStateWithLifecycle()
    val saved by viewModel.isSaved.collectAsStateWithLifecycle()
    val saveError by viewModel.saveError.collectAsStateWithLifecycle()
    var viewport by remember { mutableStateOf<ParcelBounds?>(null) }
    var zoom by remember { mutableStateOf(0.0) }
    var forests by remember { mutableStateOf(emptyList<ForestSearchResult>()) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }
    var choices by remember { mutableStateOf(emptyList<ForestSearchResult>()) }
    val camera = selected?.geometry?.bounds ?: selected?.center?.let {
        ParcelBounds(it.longitude - 0.015, it.latitude - 0.015, it.longitude + 0.015, it.latitude + 0.015)
    } ?: ParcelBounds(-5.2, 41.3, 9.7, 51.1)
    LaunchedEffect(saved) { if (saved) onCreated() }
    // Only scalar refs are in the Android saved state; reload the actual polygon after recreation.
    LaunchedEffect(selected?.id, selected?.geometry, retry) {
        val candidate = selected
        if (candidate != null && candidate.geometry == null && candidate.sourceDataset != null) {
            try {
                maps.searchForests(candidate.name).firstOrNull { it.id == candidate.id && it.geometry != null }
                    ?.let(viewModel::selectForest)
            } catch (e: CancellationException) { throw e } catch (_: Exception) { loadError = true }
        }
    }
    val needsZoom = viewport == null || zoom < 8.0 || viewport!!.east - viewport!!.west > 1.0 || viewport!!.north - viewport!!.south > 1.0
    LaunchedEffect(viewport, needsZoom, retry) {
        val bounds = viewport
        if (needsZoom || bounds == null) { forests = emptyList(); loading = false; return@LaunchedEffect }
        delay(350); loading = true; loadError = false
        try { forests = maps.loadForests(bounds) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { forests = emptyList(); loadError = true }
        finally { loading = false }
    }
    val features = remember(forests, selected) {
        forestFeatureCollection((listOfNotNull(selected) + forests).filter { it.geometry != null }.distinctBy { it.id })
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.forest_map_title)) }, navigationIcon = {
            IconButton(onClick = onNavigateBack, enabled = !saving) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(value = form.nom, onValueChange = viewModel::updateNom,
                label = { Text(stringResource(R.string.forest_map_search)) }, singleLine = true, enabled = !saving,
                shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            val shown = if (choices.isNotEmpty()) choices else lookup.suggestions
            if (shown.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().heightIn(max = 180.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    shown.forEach { candidate ->
                        TextButton(onClick = { viewModel.selectForest(candidate); choices = emptyList() }, enabled = !saving) {
                            Text(candidate.name + (candidate.department?.let { " · $it" } ?: ""))
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp)) {
                GeographicSelectionMap(features, selected?.id, camera,
                    onMapClick = { point ->
                        val hits = forests.filter { it.geometry?.contains(point) == true }
                        if (hits.size == 1) viewModel.selectForest(hits.single()) else choices = hits
                    },
                    onViewportChanged = { bounds, level -> viewport = bounds; zoom = level },
                    modifier = Modifier.fillMaxSize())
            }
            Text("Forêts publiques · © ONF Maisons-Alfort, 2026 · Licence Ouverte 2.0",
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp))
            Column(Modifier.fillMaxWidth().heightIn(max = 270.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    saving || loading || lookup.isLoading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    saveError -> Row {
                        Text(stringResource(R.string.geographic_save_failed), Modifier.weight(1f))
                        TextButton(onClick = viewModel::save) { Text(stringResource(R.string.geographic_retry)) }
                    }
                    loadError || lookup.hasError -> Row {
                        Text(stringResource(R.string.geographic_data_unavailable), Modifier.weight(1f))
                        TextButton(onClick = { viewModel.retryLookup(); retry++ }) { Text(stringResource(R.string.geographic_retry)) }
                    }
                    selected == null && needsZoom -> Text(stringResource(R.string.forest_map_zoom))
                    selected == null -> Text(stringResource(R.string.forest_map_tap))
                }
                if (selected?.geometry != null) {
                    Text(selected!!.name, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.forest_map_summary,
                        String.format(Locale.getDefault(), "%.2f", selected!!.geometry!!.areaSquareMetres / 10_000.0), selected!!.department ?: "—"))
                    Text(stringResource(R.string.forest_map_owner_unknown), style = MaterialTheme.typography.bodySmall)
                    Text(selected!!.sourceLabel, style = MaterialTheme.typography.labelSmall)
                } else Text(stringResource(R.string.forest_map_scope), style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { manual = true }, enabled = !saving, modifier = Modifier.weight(1f)) {
                    Text(stringResource(if (selected?.geometry != null) R.string.forest_map_details else R.string.forest_map_manual))
                }
                Button(onClick = viewModel::save, enabled = selected?.geometry != null && !saving && !saved,
                    modifier = Modifier.weight(1f)) { Text(stringResource(R.string.forest_map_confirm)) }
            }
        }
    }
}

private fun forestFeatureCollection(forests: List<ForestSearchResult>) = buildJsonObject {
    put("type", "FeatureCollection")
    putJsonArray("features") {
        forests.forEach { f -> f.geometry?.let { geometry -> add(buildJsonObject {
            put("type", "Feature"); put("id", f.id); put("geometry", Json.parseToJsonElement(geometry.toGeoJson()))
            putJsonObject("properties") { put("parcel_id", f.id); put("parcel_label", f.name) }
        }) } }
    }
}.toString()
