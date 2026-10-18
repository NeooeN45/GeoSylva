package com.forestry.counter.presentation.screens.forestry

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forestry.counter.R
import com.forestry.counter.domain.location.ParcelMapSource
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParcelMapCreationScreen(viewModel: ParcelMapCreationViewModel, onNavigateBack: () -> Unit, onCreated: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onCreated() }
    val features = remember(state.parcels, state.selected) { viewModel.featureCollection() }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.parcel_map_title)) }, navigationIcon = {
            IconButton(onClick = onNavigateBack, enabled = !state.saving) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(state.forest?.nom ?: stringResource(R.string.geographic_loading), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = true, onClick = { viewModel.changeSource(ParcelMapSource.CADASTRE) },
                    label = { Text(stringResource(R.string.parcel_map_cadastre)) })
                if (state.onfProfession) FilterChip(selected = state.source == ParcelMapSource.ONF,
                    onClick = { viewModel.changeSource(if (state.source == ParcelMapSource.ONF) ParcelMapSource.CADASTRE else ParcelMapSource.ONF) }, enabled = viewModel.sourceAvailable(ParcelMapSource.ONF),
                    label = { Text(stringResource(R.string.parcel_map_onf)) })
            }
            if (state.onfProfession && !viewModel.sourceAvailable(ParcelMapSource.ONF)) {
                Text(stringResource(R.string.parcel_map_onf_pending), style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 16.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp)) {
                if (state.initialBounds != null) GeographicSelectionMap(features, state.selected?.id, state.initialBounds,
                    onMapClick = viewModel::selectAt, onViewportChanged = viewModel::viewportChanged,
                    modifier = Modifier.fillMaxSize(), parcelGeometry = state.parentGeometry)
            }
            Text(if (state.source == ParcelMapSource.ONF) "© IGN / DGFiP · " + com.forestry.counter.data.repository.ParcelMapRepositoryImpl.ONF_ATTRIBUTION
                else com.forestry.counter.data.repository.ParcelMapRepositoryImpl.CADASTRE_ATTRIBUTION,
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp))
            Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    state.loading || state.saving -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.saveError -> Row {
                        Text(stringResource(R.string.geographic_save_failed), Modifier.weight(1f))
                        TextButton(onClick = viewModel::save) { Text(stringResource(R.string.geographic_retry)) }
                    }
                    state.error -> Row {
                        Text(stringResource(R.string.geographic_data_unavailable), Modifier.weight(1f))
                        TextButton(onClick = viewModel::retry) { Text(stringResource(R.string.geographic_retry)) }
                    }
                    state.outsideForest -> Text(stringResource(R.string.parcel_map_outside))
                    state.needsZoom -> Text(stringResource(R.string.parcel_map_zoom))
                    state.truncated -> Text(stringResource(R.string.parcel_map_truncated))
                    state.selected == null -> Text(stringResource(R.string.parcel_map_tap))
                }
                if (state.unavailableSourceIds.isNotEmpty()) Text(
                    stringResource(R.string.pack_invalid_contours_ids, state.unavailableSourceIds.sorted().joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                if (state.rejected > 0) Text(stringResource(R.string.pack_rejected_contours, state.rejected),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                if (state.selected != null) {
                    val selected = state.selected!!
                    OutlinedTextField(value = state.name, onValueChange = viewModel::updateName, singleLine = true,
                        label = { Text(stringResource(R.string.parcel_map_name)) }, shape = RoundedCornerShape(18.dp),
                        enabled = !state.saving, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.parcel_map_surface, String.format(Locale.getDefault(), "%.2f", selected.geometry.areaSquareMetres / 10_000.0)))
                    if (selected.sourceAreaSquareMetres != null) Text(stringResource(R.string.parcel_map_source_area,
                        String.format(Locale.getDefault(), "%.2f", selected.sourceAreaSquareMetres / 10_000.0)), style = MaterialTheme.typography.bodySmall)
                    if (selected.cadastralAreaSquareMetres != null) Text(stringResource(R.string.parcel_map_cadastral_area,
                        String.format(Locale.getDefault(), "%.2f", selected.cadastralAreaSquareMetres / 10_000.0)), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.parcel_map_terrain_unknown), style = MaterialTheme.typography.bodySmall)
                    Text(selected.attribution, style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::clearSelection, enabled = state.selected != null && !state.saving,
                    modifier = Modifier.weight(1f)) { Text(stringResource(R.string.parcel_map_clear)) }
                Button(onClick = viewModel::save, enabled = state.selected != null && state.name.isNotBlank() && !state.saving && !state.saved,
                    modifier = Modifier.weight(1f)) { Text(stringResource(R.string.parcel_map_confirm)) }
            }
        }
    }
}
