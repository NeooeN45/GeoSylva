package com.forestry.counter.presentation.screens.packs.offline

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forestry.counter.R
import com.forestry.counter.data.local.entity.OfflineMapPackEntity
import com.forestry.counter.data.packs.offline.*
import com.quintessences.geosylva.cartography.mapbox.offline.*
import java.text.DateFormat
import java.util.Date
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private enum class OfflineDetailSection { OVERVIEW, COVERAGE, SOURCES }

/** The same checked record owns overview, coverage and sources; fixed actions never scroll out. */
@Composable
fun OfflinePackDetails(row: OfflineMapPackEntity, controller: OfflineMapPackController, onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier, onPrepareUpdate: (() -> Unit)? = null, onContinue: (() -> Unit)? = null,
    onVersions: (() -> Unit)? = null, updateRequiredMissing: Boolean = false,
    onSectionTitleChanged: ((String?) -> Unit)? = null) {
    val state by controller.state.collectAsStateWithLifecycle()
    var section by rememberSaveable(row.id) { mutableStateOf(OfflineDetailSection.OVERVIEW) }
    var connectionOptions by rememberSaveable(row.id) { mutableStateOf(false) }
    val progress = state.progress[row.id]
    val storedStatus = runCatching { OfflinePackStatus.valueOf(row.status) }.getOrDefault(OfflinePackStatus.INCOMPLETE)
    val status = when {
        storedStatus == OfflinePackStatus.VERIFYING || progress?.first == OfflineTransferStage.VERIFYING -> OfflinePackStatus.VERIFYING
        row.id in state.running -> OfflinePackStatus.DOWNLOADING
        else -> storedStatus
    }
    val departmentIds = remember(row.territoriesJson) { runCatching {
        Json.decodeFromString<List<String>>(row.territoriesJson).filter { it.startsWith("department:") }.distinct()
    }.getOrDefault(emptyList()) }
    val extraSourcesResult = remember(row.extraTilesetsJson) { runCatching { Json.decodeFromString<List<String>>(row.extraTilesetsJson) } }
    val extraSources = extraSourcesResult.getOrDefault(emptyList())
    val title = when (section) {
        OfflineDetailSection.COVERAGE -> stringResource(R.string.offline_coverage_title)
        OfflineDetailSection.SOURCES -> stringResource(R.string.offline_sources_title)
        OfflineDetailSection.OVERVIEW -> null
    }
    SideEffect { onSectionTitleChanged?.invoke(title) }
    BackHandler(section != OfflineDetailSection.OVERVIEW) { section = OfflineDetailSection.OVERVIEW }
    val stateColor = when (status) {
        OfflinePackStatus.READY, OfflinePackStatus.DOWNLOADING, OfflinePackStatus.VERIFYING -> MaterialTheme.colorScheme.primary
        OfflinePackStatus.PAUSED, OfflinePackStatus.WAITING_WIFI -> Color(0xFF926100)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(modifier.fillMaxWidth()) {
        Text(row.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(Modifier.padding(top = 8.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (status == OfflinePackStatus.READY) Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp), tint = stateColor)
            Text(if (status == OfflinePackStatus.WAITING_WIFI && state.wifiOnly) stringResource(R.string.offline_waiting_for_wifi)
                else offlineStatusLabel(status), color = stateColor)
        }
        key(section) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).testTag("offline_pack_detail_body"),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (section) {
                    OfflineDetailSection.OVERVIEW -> {
                        Text(stringResource(R.string.offline_zooms, row.minZoom, row.maxZoom), style = MaterialTheme.typography.bodySmall)
                        if (extraSourcesResult.isFailure) Text(stringResource(R.string.offline_sources_unavailable), color = MaterialTheme.colorScheme.error)
                        if (departmentIds.isNotEmpty()) {
                            CoverageCount(departmentIds.size, status == OfflinePackStatus.READY)
                            TextButton({ section = OfflineDetailSection.COVERAGE }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.offline_coverage_details))
                            }
                        } else Text(stringResource(R.string.offline_saved_view), style = MaterialTheme.typography.bodySmall)
                        if (status in setOf(OfflinePackStatus.DOWNLOADING, OfflinePackStatus.PAUSED, OfflinePackStatus.WAITING_WIFI, OfflinePackStatus.VERIFYING)) {
                            val p = progress?.second ?: OfflineResourceProgress(row.completedResources, row.requiredResources, row.resourceBytes)
                            if (p.fraction != null) LinearProgressIndicator(progress = { p.fraction ?: 0f }, modifier = Modifier.fillMaxWidth(), color = stateColor)
                            else if (status in setOf(OfflinePackStatus.DOWNLOADING, OfflinePackStatus.VERIFYING)) LinearProgressIndicator(Modifier.fillMaxWidth())
                            if (p.required > 0) Text(stringResource(R.string.offline_resource_count, p.completed, p.required), style = MaterialTheme.typography.bodySmall)
                            else Text(stringResource(R.string.offline_progress_unknown), style = MaterialTheme.typography.bodySmall)
                            val stage = progress?.first?.name ?: row.stage
                            Text(stringResource(when (stage) {
                                OfflineTransferStage.STYLE.name -> R.string.offline_stage_style
                                OfflineTransferStage.TILES.name -> if (extraSources.isEmpty()) R.string.offline_stage_tiles else R.string.offline_stage_extra_tiles
                                OfflineTransferStage.VERIFYING.name -> R.string.offline_stage_verify
                                else -> if (status == OfflinePackStatus.VERIFYING) R.string.offline_stage_verify else R.string.offline_stage_not_started
                            }), style = MaterialTheme.typography.bodySmall)
                        }
                        if (status in setOf(OfflinePackStatus.DOWNLOADING, OfflinePackStatus.PAUSED) || connectionOptions) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.offline_wifi_only), Modifier.weight(1f))
                                Switch(state.wifiOnly, controller::wifiOnly)
                            }
                        }
                        if (status == OfflinePackStatus.WAITING_WIFI) TextButton({ controller.pause(row.id) }, Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.offline_pause))
                        }
                        TextButton({ section = OfflineDetailSection.SOURCES }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.offline_sources_versions))
                        }
                        onVersions?.let { action -> TextButton(action, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.offline_saved_versions)) } }
                        if (status !in setOf(OfflinePackStatus.DOWNLOADING, OfflinePackStatus.VERIFYING)) {
                            if (row.selectedVersion) TextButton({ controller.retain(row.id) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.offline_retain_version)) }
                            else TextButton({ controller.prepareRemoval(row.id) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.offline_remove)) }
                        }
                        onPrepareUpdate?.let { action -> TextButton(action, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.offline_prepare_update)) } }
                        if (updateRequiredMissing) Text(stringResource(R.string.offline_update_required_missing), color = MaterialTheme.colorScheme.error)
                    }
                    OfflineDetailSection.COVERAGE -> {
                        Text(stringResource(R.string.offline_coverage_title), style = MaterialTheme.typography.titleMedium)
                        CoverageCount(departmentIds.size, status == OfflinePackStatus.READY)
                        Text(stringResource(R.string.offline_zooms, row.minZoom, row.maxZoom))
                        departmentIds.forEach { id -> Text(id.substringAfter(':')) }
                    }
                    OfflineDetailSection.SOURCES -> OfflinePackSources(row, extraSources, extraSourcesResult.isFailure)
                }
            }
        }
        if (section == OfflineDetailSection.SOURCES) {
            onVersions?.let { action -> OutlinedButton(action, Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 48.dp)
                .testTag("offline_sources_versions")) { Text(stringResource(R.string.offline_saved_versions)) } }
            DetailBack { section = OfflineDetailSection.OVERVIEW }
        } else if (section == OfflineDetailSection.COVERAGE) {
            onOpen?.let { action -> DetailPrimary(R.string.offline_open_map, "offline_detail_open") { controller.open(row.id, action) } }
            DetailBack { section = OfflineDetailSection.OVERVIEW }
        } else {
            when (status) {
                OfflinePackStatus.READY -> {
                    if (onOpen != null) DetailPrimary(R.string.offline_open_map, "offline_detail_open") { controller.open(row.id, onOpen) }
                    else onContinue?.let { action -> DetailPrimary(R.string.offline_continue_app, "offline_detail_continue", onClick = action) }
                    if (!row.selectedVersion) TextButton({ controller.prepareVersionChoice(row.id) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.offline_use_definition)) }
                }
                OfflinePackStatus.DOWNLOADING, OfflinePackStatus.VERIFYING -> {
                    OutlinedButton({ controller.pause(row.id) }, Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 48.dp)
                        .testTag("offline_detail_pause")) { Text(stringResource(R.string.offline_pause)) }
                    onContinue?.let { action -> TextButton(action, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.offline_continue_app)) } }
                }
                OfflinePackStatus.WAITING_WIFI -> {
                    if (onContinue != null) DetailPrimary(R.string.offline_continue_app, "offline_detail_continue", onClick = onContinue)
                    else DetailPrimary(R.string.offline_resume, "offline_detail_resume", enabled = state.connected && (!state.wifiOnly || state.wifi)) { controller.resume(row.id) }
                    TextButton({ connectionOptions = !connectionOptions }, Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("offline_detail_connection_options")) { Text(stringResource(R.string.offline_connection_options)) }
                }
                OfflinePackStatus.RETAINED -> DetailPrimary(R.string.offline_reactivate, "offline_detail_reactivate") { controller.reactivate(row.id) }
                else -> {
                    DetailPrimary(R.string.offline_resume, "offline_detail_resume") { controller.resume(row.id) }
                    onContinue?.let { action -> TextButton(action, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.offline_continue_app)) } }
                }
            }
        }
    }
}

@Composable
private fun CoverageCount(count: Int, ready: Boolean) {
    Text(pluralStringResource(if (ready) R.plurals.offline_department_ready_count else R.plurals.offline_department_pending_count,
        count, count), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun DetailPrimary(label: Int, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 48.dp).testTag(tag)) {
        Text(stringResource(label))
    }
}

@Composable
private fun DetailBack(onClick: () -> Unit) {
    TextButton(onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("offline_details_back")) { Text(stringResource(R.string.offline_back_to_details)) }
}

@Composable
private fun OfflinePackSources(row: OfflineMapPackEntity, extraSources: List<String>, extraSourcesUnavailable: Boolean) {
    val locale = LocalConfiguration.current.locales[0]
    val dates = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale) }
    Text(stringResource(R.string.offline_sources_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.offline_mapbox_source, row.styleUri), style = MaterialTheme.typography.bodySmall)
    if (extraSourcesUnavailable) Text(stringResource(R.string.offline_sources_unavailable), color = MaterialTheme.colorScheme.error)
    extraSources.forEach { uri -> Text(stringResource(R.string.offline_extra_source, uri), style = MaterialTheme.typography.bodySmall) }
    Text(stringResource(R.string.offline_source_version_unknown), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.offline_local_version, row.localVersion), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.offline_recorded, dates.format(Date(row.createdAt))), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.offline_validated, row.validatedAt?.let { dates.format(Date(it)) } ?: stringResource(R.string.offline_unknown)), style = MaterialTheme.typography.bodySmall)
    row.expiresAt?.let { Text(stringResource(R.string.offline_expires, dates.format(Date(it))), style = MaterialTheme.typography.bodySmall) }
    val required = remember(row.datasetsJson) { runCatching { Json.decodeFromString<List<OfflineDataset>>(row.datasetsJson) } }
    if (required.isFailure) Text(stringResource(R.string.offline_sources_unavailable), color = MaterialTheme.colorScheme.error)
    required.getOrNull()?.forEach { Text(stringResource(R.string.offline_source_dataset, it.source, it.version,
        it.sourceDate ?: stringResource(R.string.offline_unknown)), style = MaterialTheme.typography.bodySmall) }
    Text(stringResource(R.string.offline_no_routing), style = MaterialTheme.typography.bodySmall)
}
