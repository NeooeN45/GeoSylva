package com.forestry.counter.presentation.screens.packs.offline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forestry.counter.R
import com.forestry.counter.data.local.entity.OfflineMapPackEntity
import com.forestry.counter.data.packs.offline.OfflinePackStatus
import java.text.DateFormat
import java.util.Date

/** An explicit Room lineage; names, shapes and cached Mapbox styles never establish a family. */
@Composable
fun OfflinePackVersionsPanel(
    anchor: OfflineMapPackEntity,
    controller: OfflineMapPackController,
    onDetails: (String) -> Unit,
    onStorage: () -> Unit,
    modifier: Modifier = Modifier,
    onExistingFiles: (() -> Unit)? = null,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val versions = state.packs.filter { it.familyId == anchor.familyId }.sortedWith(
        compareByDescending<OfflineMapPackEntity> { it.selectedVersion }.thenByDescending { it.versionOrdinal })
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(anchor.name, style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.offline_on_phone), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (versions.none { it.selectedVersion }) Text(stringResource(R.string.offline_no_selected_version), style = MaterialTheme.typography.bodySmall)
        versions.forEach { version ->
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(if (version.selectedVersion) R.string.offline_used_version else R.string.offline_saved_version)) },
                supportingContent = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.offline_version_number, version.versionOrdinal))
                    val status = runCatching { OfflinePackStatus.valueOf(version.status) }.getOrDefault(OfflinePackStatus.INCOMPLETE)
                    Text(if (version.selectedVersion && status == OfflinePackStatus.READY) stringResource(R.string.offline_active_version)
                        else offlineStatusLabel(status))
                    Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(version.createdAt)), style = MaterialTheme.typography.bodySmall)
                } },
                leadingContent = { Icon(if (version.selectedVersion) Icons.Default.CheckCircle else Icons.Default.History, null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.offline_version_details)) },
                modifier = Modifier.clickable { onDetails(version.id) },
            )
            if (!version.selectedVersion && version.id !in state.running) TextButton({ controller.prepareVersionChoice(version.id) }, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.offline_use_definition))
            }
        }
        HorizontalDivider()
        OutlinedButton(onStorage, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.offline_storage_title)) }
        onExistingFiles?.let { action -> TextButton(action, Modifier.fillMaxWidth()) { Text(stringResource(R.string.offline_existing_files)) } }
        Text(stringResource(R.string.offline_versions_resource_limits), style = MaterialTheme.typography.bodySmall)
        if (state.error) Text(stringResource(R.string.offline_version_choice_failed), color = MaterialTheme.colorScheme.error)
    }
}
