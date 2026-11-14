package com.forestry.counter.presentation.screens.packs.offline

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forestry.counter.R
import com.forestry.counter.data.local.entity.OfflineMapPackEntity
import com.forestry.counter.data.packs.offline.*
import com.forestry.counter.domain.usecase.maps.MapDataScope
import com.quintessences.geosylva.cartography.mapbox.offline.*
import com.quintessences.geosylva.cartography.ui.offline.OfflinePackPreparation
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** The host supplies the real visible-footprint snapshot; panning later cannot change this plan. */
data class OfflineViewPackDraft(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val geometryJson: String,
    val minZoom: Int,
    val maxZoom: Int,
    val pixelRatio: Float,
    val styleUri: String,
    val reliefSource: String? = "mapbox://mapbox.mapbox-terrain-dem-v1",
    val initiallyRelief: Boolean = false,
    val territories: Set<String> = emptySet(),
    val datasetCandidates: List<OfflineDataset> = emptyList(),
    val initiallySelectedDatasetIds: Set<String> = emptySet(),
)

@Composable
fun OfflineViewPackPreparation(controller: OfflineMapPackController, dataScope: MapDataScope,
    draft: OfflineViewPackDraft, onDismiss: () -> Unit, modifier: Modifier = Modifier, onOpenPrepared: (() -> Unit)? = null) {
    val state by controller.state.collectAsStateWithLifecycle()
    LaunchedEffect(controller, dataScope) { controller.activate(dataScope) }
    var name by rememberSaveable(draft.id, state.authorityKey) { mutableStateOf(draft.name) }
    var zoom by rememberSaveable(draft.id, state.authorityKey) { mutableStateOf(draft.maxZoom.coerceIn(draft.minZoom, 22)) }
    var relief by rememberSaveable(draft.id, state.authorityKey) { mutableStateOf(draft.initiallyRelief) }
    var selected by rememberSaveable(draft.id, state.authorityKey) { mutableStateOf(draft.initiallySelectedDatasetIds.toList()) }
    var datasetSearch by rememberSaveable(draft.id) { mutableStateOf("") }
    val prepared = state.packs.firstOrNull { it.id == draft.id }
    if (prepared != null) {
        Column(modifier) {
            OfflinePackDetails(prepared, controller, onOpen = onOpenPrepared, onContinue = onDismiss)
            TextButton(onDismiss, Modifier.fillMaxWidth()) { Text(stringResource(R.string.offline_close)) }
        }
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OfflinePackPreparation(name, { name = it }, draft.minZoom, zoom, { zoom = it }, relief,
            if (draft.reliefSource != null) ({ relief = it }) else null, state.planning,
            onEstimate = {
                controller.prepare(OfflinePackInput(draft.id, name,
                    OfflineMapDefinition(draft.geometryJson, draft.minZoom, zoom, draft.pixelRatio, draft.styleUri,
                        if (relief) listOfNotNull(draft.reliefSource) else emptyList()),
                    draft.datasetCandidates.filter { it.id in selected }, draft.territories))
            }, datasets = {
                Text(stringResource(R.string.offline_datasets), style = MaterialTheme.typography.titleSmall)
                if (draft.datasetCandidates.isEmpty()) Text(stringResource(R.string.offline_no_qualified_data), style = MaterialTheme.typography.bodySmall)
                else {
                    OutlinedTextField(datasetSearch, { datasetSearch = it }, singleLine = true,
                        label = { Text(stringResource(R.string.offline_search_data)) }, modifier = Modifier.fillMaxWidth())
                    draft.datasetCandidates.filter { it.source.contains(datasetSearch, true) || it.id.contains(datasetSearch, true) }.forEach { dataset ->
                        Row(Modifier.fillMaxWidth()) {
                            Checkbox(dataset.id in selected, { checked -> selected = if (checked) selected + dataset.id else selected - dataset.id })
                            Column(Modifier.weight(1f).padding(top = 8.dp)) {
                                Text(dataset.source, style = MaterialTheme.typography.bodyMedium)
                                Text(stringResource(R.string.offline_dataset_version, dataset.version, dataset.sourceDate ?: stringResource(R.string.offline_unknown)), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            })
        if (state.error) Text(stringResource(R.string.offline_action_failed), color = MaterialTheme.colorScheme.error)
        TextButton(onDismiss, Modifier.fillMaxWidth()) { Text(stringResource(R.string.offline_close)) }
    }
}

@Composable
fun OfflinePackDialogs(controller: OfflineMapPackController) {
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    state.plan?.let { plan -> AlertDialog(onDismissRequest = controller::dismissPlan,
        title = { Text(stringResource(R.string.offline_confirmation)) },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(plan.input.name, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.offline_zooms, plan.input.definition.minZoom, plan.input.definition.maxZoom))
            Text(stringResource(R.string.offline_download_estimate, Formatter.formatFileSize(context, Math.addExact(plan.tiles.tileTransferBytes, plan.datasetTransferBytes))))
            Text(stringResource(R.string.offline_storage_estimate, Formatter.formatFileSize(context, plan.tiles.tileStorageBytes)))
            Text(stringResource(R.string.offline_free, Formatter.formatFileSize(context, plan.availableBytes)))
            Text(stringResource(R.string.offline_estimate_limits), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.offline_estimate_margin, plan.tiles.errorMargin.toString()), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth()) { Text(stringResource(R.string.offline_wifi_only), Modifier.weight(1f)); Switch(state.wifiOnly, controller::wifiOnly) }
            if (!plan.knownMinimumFits) Text(stringResource(R.string.offline_storage_insufficient), color = MaterialTheme.colorScheme.error)
        } }, confirmButton = { Button(controller::confirm, enabled = plan.knownMinimumFits && !state.planning) { Text(stringResource(R.string.offline_download)) } },
        dismissButton = { TextButton(controller::dismissPlan) { Text(stringResource(R.string.offline_modify)) } }) }
    state.removal.takeIf { it.isNotEmpty() }?.let { removals -> AlertDialog(onDismissRequest = controller::dismissRemoval,
        title = { Text(stringResource(R.string.offline_remove_title)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(stringResource(R.string.offline_remove_count, removals.size)); Text(stringResource(R.string.offline_remove_effects)) } },
        confirmButton = { Button(controller::removeConfirmed, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.offline_remove)) } },
        dismissButton = { TextButton(controller::dismissRemoval) { Text(stringResource(R.string.offline_cancel)) } }) }
}

@Composable
fun OfflinePackDetails(row: OfflineMapPackEntity, controller: OfflineMapPackController, onOpen: (() -> Unit)?, modifier: Modifier = Modifier, onPrepareUpdate: (() -> Unit)? = null, onContinue: (() -> Unit)? = null) {
    val state by controller.state.collectAsStateWithLifecycle()
    var sourceDetails by rememberSaveable(row.id) { mutableStateOf(false) }
    val progress = state.progress[row.id]
    val actualStatus = if (row.id in state.running) OfflinePackStatus.DOWNLOADING else runCatching { OfflinePackStatus.valueOf(row.status) }.getOrDefault(OfflinePackStatus.INCOMPLETE)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(row.name, style = MaterialTheme.typography.headlineSmall)
        Text(offlineStatusLabel(actualStatus), color = if (actualStatus == OfflinePackStatus.READY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.offline_zooms, row.minZoom, row.maxZoom), style = MaterialTheme.typography.bodySmall)
        val departmentIds = remember(row.territoriesJson) { runCatching { Json.decodeFromString<List<String>>(row.territoriesJson).filter { it.startsWith("department:") }.distinct() }.getOrDefault(emptyList()) }
        var coverageDetails by rememberSaveable(row.id) { mutableStateOf(false) }
        if (departmentIds.isNotEmpty()) {
            Text(stringResource(if (actualStatus == OfflinePackStatus.READY) R.string.offline_department_ready else R.string.offline_department_pending, departmentIds.size), style = MaterialTheme.typography.bodySmall)
            TextButton({ coverageDetails = !coverageDetails }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.offline_coverage_details)) }
            if (coverageDetails) Text(departmentIds.joinToString(" · ") { it.substringAfter(':') }, style = MaterialTheme.typography.bodySmall)
        } else Text(stringResource(R.string.offline_saved_view), style = MaterialTheme.typography.bodySmall)

        if (actualStatus == OfflinePackStatus.DOWNLOADING || actualStatus == OfflinePackStatus.PAUSED || actualStatus == OfflinePackStatus.WAITING_WIFI) {
            val p = progress?.second ?: OfflineResourceProgress(row.completedResources, row.requiredResources, row.resourceBytes)
            if (p.fraction != null) LinearProgressIndicator(progress = { p.fraction ?: 0f }, modifier = Modifier.fillMaxWidth())
            else if (actualStatus == OfflinePackStatus.DOWNLOADING) LinearProgressIndicator(Modifier.fillMaxWidth())
            else Text(stringResource(R.string.offline_progress_unknown), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.offline_resource_count, p.completed, p.required), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(when (progress?.first?.name ?: row.stage) {
                OfflineTransferStage.STYLE.name -> R.string.offline_stage_style
                OfflineTransferStage.TILES.name -> R.string.offline_stage_tiles
                else -> R.string.offline_stage_verify
            }), style = MaterialTheme.typography.bodySmall)
        }
        if (actualStatus in setOf(OfflinePackStatus.DOWNLOADING, OfflinePackStatus.PAUSED, OfflinePackStatus.WAITING_WIFI)) {
            Row(Modifier.fillMaxWidth()) { Text(stringResource(R.string.offline_wifi_only), Modifier.weight(1f)); Switch(state.wifiOnly, controller::wifiOnly) }
        }
        when (actualStatus) {
            OfflinePackStatus.READY -> {
                onOpen?.let { action -> Button(action, Modifier.fillMaxWidth()) { Text(stringResource(R.string.offline_open_map)) } }
                TextButton({ controller.retain(row.id) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.offline_retain_version)) }
            }
            OfflinePackStatus.DOWNLOADING, OfflinePackStatus.VERIFYING -> Button({ controller.pause(row.id) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.offline_pause)) }
            OfflinePackStatus.RETAINED -> {
                Button({ controller.reactivate(row.id) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.offline_reactivate)) }
                TextButton({ controller.prepareRemoval(row.id) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.offline_remove)) }
            }
            else -> {
                Button({ controller.resume(row.id) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.offline_resume)) }
                TextButton({ controller.prepareRemoval(row.id) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.offline_remove)) }
            }
        }
        onContinue?.let { action -> TextButton(action, Modifier.fillMaxWidth()) { Text(stringResource(R.string.offline_continue_app)) } }
        onPrepareUpdate?.let { action -> TextButton(action, Modifier.fillMaxWidth()) { Text(stringResource(R.string.offline_prepare_update)) } }
        TextButton({ sourceDetails = !sourceDetails }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.offline_sources_versions)) }
        if (sourceDetails) {
            Text(stringResource(R.string.offline_mapbox_source, row.styleUri), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.offline_source_version_unknown), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.offline_local_version, row.localVersion), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.offline_recorded, DateFormat.getDateTimeInstance().format(Date(row.createdAt))), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.offline_validated, row.validatedAt?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: stringResource(R.string.offline_unknown)), style = MaterialTheme.typography.bodySmall)
            row.expiresAt?.let { Text(stringResource(R.string.offline_expires, DateFormat.getDateTimeInstance().format(Date(it))), style = MaterialTheme.typography.bodySmall) }
            val required = remember(row.datasetsJson) { runCatching { Json.decodeFromString<List<OfflineDataset>>(row.datasetsJson) }.getOrDefault(emptyList()) }
            required.forEach { Text(stringResource(R.string.offline_source_dataset, it.source, it.version, it.sourceDate ?: stringResource(R.string.offline_unknown)), style = MaterialTheme.typography.bodySmall) }
            Text(stringResource(R.string.offline_no_routing), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun offlineStatusLabel(status: OfflinePackStatus): String = stringResource(when (status) {
    OfflinePackStatus.READY -> R.string.offline_ready
    OfflinePackStatus.PAUSED -> R.string.offline_paused
    OfflinePackStatus.WAITING_WIFI -> R.string.offline_waiting_wifi
    OfflinePackStatus.DOWNLOADING -> R.string.offline_downloading
    OfflinePackStatus.VERIFYING -> R.string.offline_verifying
    OfflinePackStatus.INCOMPLETE -> R.string.offline_incomplete
    OfflinePackStatus.FAILED -> R.string.offline_failed
    OfflinePackStatus.RETAINED -> R.string.offline_retained
})
