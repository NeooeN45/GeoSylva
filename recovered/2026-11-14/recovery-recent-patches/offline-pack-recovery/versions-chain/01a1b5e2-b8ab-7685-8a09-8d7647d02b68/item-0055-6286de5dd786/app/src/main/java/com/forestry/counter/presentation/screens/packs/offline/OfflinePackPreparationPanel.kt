package com.forestry.counter.presentation.screens.packs.offline

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forestry.counter.R
import com.forestry.counter.data.packs.offline.*
import com.forestry.counter.domain.usecase.maps.MapDataScope
import com.quintessences.geosylva.cartography.mapbox.offline.OfflineMapDefinition
import com.quintessences.geosylva.cartography.ui.offline.OfflinePackPreparation
import com.quintessences.geosylva.cartography.ui.R as MapUiR

/** A bounded editor: its body scrolls, while estimate/apply and close remain visible. */
@Composable
fun OfflineViewPackPreparation(controller: OfflineMapPackController, dataScope: MapDataScope,
    draft: OfflineViewPackDraft, onDismiss: () -> Unit, modifier: Modifier = Modifier,
    onOpenPrepared: (() -> Unit)? = null, onSectionTitleChanged: ((String?) -> Unit)? = null) {
    val state by controller.state.collectAsStateWithLifecycle()
    LaunchedEffect(controller, dataScope) { controller.activate(dataScope) }
    DisposableEffect(controller, draft.id) {
        controller.beginPreparation(draft.id)
        onDispose { controller.endPreparation(draft.id) }
    }
    var name by rememberSaveable(draft.id, state.authorityKey) { mutableStateOf(draft.name) }
    var zoom by rememberSaveable(draft.id, state.authorityKey) { mutableStateOf(draft.maxZoom.coerceIn(draft.minZoom, 22)) }
    var relief by rememberSaveable(draft.id, state.authorityKey) { mutableStateOf(draft.initiallyRelief) }
    var selected by rememberSaveable(draft.id, state.authorityKey) { mutableStateOf(draft.initiallySelectedDatasetIds.toList()) }
    var choosing by rememberSaveable(draft.id, state.authorityKey) { mutableStateOf(false) }
    var pendingSelection by rememberSaveable(draft.id, state.authorityKey) { mutableStateOf(selected) }
    var search by rememberSaveable(draft.id) { mutableStateOf("") }
    var info by remember { mutableStateOf<OfflineDataset?>(null) }
    val sectionTitle = if (choosing) stringResource(R.string.offline_layers_title) else null
    SideEffect { onSectionTitleChanged?.invoke(sectionTitle) }
    BackHandler(choosing) { choosing = false }
    val prepared = state.packs.firstOrNull { it.id == draft.id }
    if (prepared != null) {
        OfflinePackDetails(prepared, controller, onOpen = onOpenPrepared, onContinue = onDismiss, modifier = modifier)
        return
    }
    val estimate: () -> Unit = {
        controller.prepare(OfflinePackInput(draft.id, name,
            OfflineMapDefinition(draft.geometryJson, draft.minZoom, zoom, draft.pixelRatio, draft.styleUri,
                if (relief) listOfNotNull(draft.reliefSource) else emptyList()),
            draft.datasetCandidates.filter { it.id in selected }, draft.territories, draft.replacesPackId))
    }
    Column(modifier.fillMaxWidth()) {
        key(choosing) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                .testTag("offline_preparation_body"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (choosing) {
                    Text(draft.name, style = MaterialTheme.typography.titleMedium)
                    Text(pluralStringResource(R.plurals.offline_selected_datasets_count, pendingSelection.size, pendingSelection.size),
                        style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(search, { search = it }, singleLine = true,
                        label = { Text(stringResource(R.string.offline_search_data)) }, modifier = Modifier.fillMaxWidth())
                    draft.datasetCandidates.filter { it.source.contains(search, true) || it.id.contains(search, true) }.forEach { dataset ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(dataset.id in pendingSelection, { checked -> pendingSelection =
                                if (checked) (pendingSelection + dataset.id).distinct() else pendingSelection - dataset.id },
                                modifier = Modifier.testTag("offline_dataset_${dataset.id}"))
                            Column(Modifier.weight(1f)) {
                                Text(dataset.source, style = MaterialTheme.typography.bodyMedium)
                                Text(dataset.version, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton({ info = dataset }, Modifier.size(48.dp)) {
                                Icon(Icons.Default.Info, stringResource(R.string.offline_dataset_information))
                            }
                        }
                    }
                } else {
                    OfflinePackPreparation(name, { name = it }, draft.minZoom, zoom, { zoom = it }, relief,
                        if (draft.reliefSource != null) ({ relief = it }) else null, state.planning,
                        onEstimate = estimate, estimateActionVisible = false, datasets = {
                            if (draft.datasetCandidates.isEmpty()) Text(stringResource(R.string.offline_no_qualified_data),
                                style = MaterialTheme.typography.bodySmall)
                            else OutlinedButton({ pendingSelection = selected; search = ""; choosing = true },
                                Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("offline_preparation_choose_layers")) {
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.offline_choose_datasets))
                                    Text(pluralStringResource(R.plurals.offline_selected_datasets_count, selected.size, selected.size),
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                            }
                        })
                    if (state.error) Text(stringResource(R.string.offline_action_failed), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (choosing) {
            Button({ selected = pendingSelection; choosing = false },
                Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 48.dp).testTag("offline_preparation_apply_layers")) {
                Text(stringResource(R.string.offline_apply_datasets))
            }
            TextButton({ choosing = false }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.offline_back_to_content)) }
        } else {
            Button(estimate, enabled = !state.planning && state.authorityKey != null && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 48.dp).testTag("offline_preparation_estimate")) {
                Text(stringResource(MapUiR.string.gs_offline_estimate))
            }
            TextButton(onDismiss, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("offline_preparation_close")) { Text(stringResource(R.string.offline_close)) }
        }
    }
    info?.let { dataset -> AlertDialog(onDismissRequest = { info = null },
        title = { Text(dataset.source) }, text = { Text(stringResource(R.string.offline_source_dataset,
            dataset.source, dataset.version, dataset.sourceDate ?: stringResource(R.string.offline_unknown))) },
        confirmButton = { TextButton({ info = null }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.offline_close)) } }) }
}
