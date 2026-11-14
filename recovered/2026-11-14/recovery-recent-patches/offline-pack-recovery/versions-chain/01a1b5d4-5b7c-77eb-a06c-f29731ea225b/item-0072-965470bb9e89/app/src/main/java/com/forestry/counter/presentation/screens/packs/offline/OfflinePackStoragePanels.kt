package com.forestry.counter.presentation.screens.packs.offline

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forestry.counter.R
import com.forestry.counter.data.local.entity.OfflineMapPackEntity
import com.forestry.counter.data.packs.offline.OfflinePackRemoval
import com.forestry.counter.data.packs.offline.OfflinePackStatus

/** Compact overview. Measurements describe the shared native tiles, never a fictional whole-pack total. */
@Composable
fun OfflinePackStorageSummary(
    state: OfflineMapPackController.State,
    onChooseContents: () -> Unit,
    onPacks: () -> Unit,
    onVersions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showLimits by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.offline_storage_on_phone), style = MaterialTheme.typography.headlineSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StorageMetric(stringResource(R.string.offline_storage_tiles_label), state.tileStoreBytes?.let { Formatter.formatFileSize(context, it) }
                    ?: stringResource(R.string.offline_unknown), Modifier.weight(1f))
                StorageMetric(stringResource(R.string.offline_storage_free_label), state.freeBytes?.let { Formatter.formatFileSize(context, it) }
                    ?: stringResource(R.string.offline_unknown), Modifier.weight(1f))
            }
            HorizontalDivider()
            StorageRoute(stringResource(R.string.offline_storage_packs_views), Icons.Default.Layers, onPacks)
            HorizontalDivider()
            StorageRoute(stringResource(R.string.offline_storage_retained_versions), Icons.Default.History, onVersions)
            if (state.removed) Text(stringResource(R.string.offline_removed), color = MaterialTheme.colorScheme.primary)
            if (state.error) Text(stringResource(R.string.offline_removal_partial), color = MaterialTheme.colorScheme.error)
            TextButton({ showLimits = !showLimits }, Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Default.Info, null, Modifier.size(20.dp))
                Text(stringResource(R.string.offline_versions_limits), Modifier.padding(start = 8.dp))
            }
            if (showLimits) Text(stringResource(R.string.offline_storage_shared), style = MaterialTheme.typography.bodySmall)
        }
        Button(onChooseContents, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("offline_storage_choose_contents")) {
            Text(stringResource(R.string.offline_storage_choose))
        }
    }
}

@Composable
private fun StorageMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun StorageRoute(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    TextButton(onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp), contentPadding = PaddingValues(0.dp)) {
        Icon(icon, null, Modifier.size(30.dp))
        Text(label, Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.titleMedium)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
    }
}

/** Body scrolls independently; the review action remains in the measured 52% panel. */
@Composable
fun OfflinePackStoragePanel(
    state: OfflineMapPackController.State,
    controller: OfflineMapPackController,
    storageSelection: List<String>,
    onSelectionChange: (List<String>) -> Unit,
    query: String = "",
    onQueryChange: ((String) -> Unit)? = null,
) {
    val selectedIds = storageSelection.filter { id -> state.packs.any { it.id == id && removable(it, state.running) } }
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).testTag("offline_storage_selection_body"),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.offline_storage_selection_title), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                Text(pluralStringResource(R.plurals.offline_storage_selected_count, selectedIds.size, selectedIds.size), style = MaterialTheme.typography.labelLarge)
            }
            onQueryChange?.let { change -> OutlinedTextField(query, change, singleLine = true,
                label = { Text(stringResource(R.string.offline_search_packs)) }, modifier = Modifier.fillMaxWidth()) }
            state.packs.filter { it.name.contains(query, ignoreCase = true) }.forEach { pack ->
                val selectable = removable(pack, state.running)
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("offline_storage_item_${pack.id}")
                    .toggleable(value = pack.id in selectedIds, enabled = selectable, role = Role.Checkbox,
                        onValueChange = { checked -> onSelectionChange(if (checked) (selectedIds + pack.id).distinct() else selectedIds - pack.id) })
                    .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(if (pack.status == OfflinePackStatus.RETAINED.name) Icons.Default.History else Icons.Outlined.Map, null,
                        Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(pack.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text(if (pack.selectedVersion) stringResource(R.string.offline_storage_protected_version)
                            else offlineStatusLabel(runCatching { OfflinePackStatus.valueOf(pack.status) }.getOrDefault(OfflinePackStatus.INCOMPLETE)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Checkbox(pack.id in selectedIds, onCheckedChange = null, enabled = selectable)
                }
                HorizontalDivider()
            }
            Text(stringResource(R.string.offline_storage_gain_unknown), style = MaterialTheme.typography.bodySmall)
            if (state.error) Text(stringResource(R.string.offline_removal_partial), color = MaterialTheme.colorScheme.error)
        }
        Button({ controller.prepareRemoval(selectedIds.toSet()) }, enabled = selectedIds.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 48.dp).testTag("offline_storage_review_removal")) {
            Text(stringResource(R.string.offline_review_removal, selectedIds.size))
        }
    }
}

private fun removable(row: OfflineMapPackEntity, running: Set<String>) = !row.selectedVersion && row.id !in running &&
    row.status !in setOf(OfflinePackStatus.DOWNLOADING.name, OfflinePackStatus.VERIFYING.name)

/** Exact reviewed registry identities; native shared resources and business data are never promised as reclaimed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OfflinePackRemovalSheet(
    removals: List<OfflinePackRemoval>,
    packs: List<OfflineMapPackEntity>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val reviewed = removals.map { removal -> packs.firstOrNull { it.id == removal.id } }
    val maximum = LocalConfiguration.current.screenHeightDp.dp * 0.52f
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null, scrimColor = Color.Transparent, contentWindowInsets = { WindowInsets(0, 0, 0, 0) }) {
        Column(Modifier.fillMaxWidth().heightIn(max = maximum).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("offline_removal_confirmation_sheet")) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(pluralStringResource(R.plurals.offline_remove_selected_count, removals.size, removals.size), style = MaterialTheme.typography.headlineSmall)
                reviewed.forEach { row ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(row?.name ?: stringResource(R.string.offline_removal_missing_content), style = MaterialTheme.typography.titleMedium)
                        row?.let { Text(offlineStatusLabel(runCatching { OfflinePackStatus.valueOf(it.status) }.getOrDefault(OfflinePackStatus.INCOMPLETE)),
                            style = MaterialTheme.typography.bodySmall) }
                    }
                    HorizontalDivider()
                }
                Text(stringResource(R.string.offline_storage_gain_unknown), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.offline_removal_coverage_warning), style = MaterialTheme.typography.bodySmall)
                if (packs.any { it.selectedVersion && removals.none { removal -> removal.id == it.id } }) {
                    PreservationLine(stringResource(R.string.offline_removal_preserved_active))
                }
                PreservationLine(stringResource(R.string.offline_removal_preserved_business))
            }
            Button(onConfirm, enabled = reviewed.all { it != null }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                .heightIn(min = 48.dp).testTag("offline_removal_confirm"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA64327), contentColor = Color.White)) {
                Text(stringResource(R.string.offline_remove_from_phone))
            }
            TextButton(onDismiss, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("offline_removal_cancel")) {
                Text(stringResource(R.string.offline_cancel))
            }
        }
    }
}

@Composable
private fun PreservationLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
