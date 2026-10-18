package com.forestry.counter.presentation.screens.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.forestry.counter.R
import com.forestry.counter.domain.model.HierarchySyncCandidate
import com.forestry.counter.domain.repository.HierarchySyncRepository
import com.forestry.counter.domain.repository.IdentityRepository
import com.forestry.counter.presentation.viewmodel.ForestSyncError
import com.forestry.counter.presentation.viewmodel.ForestSyncSelectionViewModel
import com.forestry.counter.presentation.viewmodel.ForestSyncStep
import com.forestry.counter.presentation.viewmodel.ForestSyncTotals
import com.forestry.counter.presentation.viewmodel.GeoSylvaViewModelFactory
import com.forestry.counter.presentation.viewmodel.forestSyncTotals
import com.forestry.counter.presentation.viewmodel.isMissingForestBackupRecovery

/** Explicit whole-forest consent; missing-copy recovery requires a separate confirmation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForestSyncSelectionScreen(
    identityRepository: IdentityRepository,
    hierarchySyncRepository: HierarchySyncRepository,
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
) {
    val factory = remember(identityRepository, hierarchySyncRepository) {
        GeoSylvaViewModelFactory { ForestSyncSelectionViewModel(identityRepository, hierarchySyncRepository) }
    }
    val vm: ForestSyncSelectionViewModel = viewModel(factory = factory)
    val state by vm.uiState.collectAsStateWithLifecycle()
    val profile by identityRepository.profile.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, vm) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.validateSession() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.closed) { if (state.closed) onNavigateBack() }
    val onBack = {
        if (!state.busy) {
            if (state.preview != null) vm.dismissPreview()
            else if (state.step == ForestSyncStep.REVIEW) vm.editSelection()
            else vm.cancel()
        }
    }
    BackHandler { onBack() }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.forest_sync_title)) }, navigationIcon = {
            IconButton(enabled = !state.busy, onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
        }) },
        bottomBar = {
            if (state.selection != null && !state.loading) Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state.step) {
                    ForestSyncStep.SELECT -> Button(onClick = vm::reviewSelection, enabled = !state.busy && state.selectedForestIds.isNotEmpty(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Text(stringResource(R.string.forest_sync_review))
                    }
                    ForestSyncStep.REVIEW -> {
                        Button(onClick = vm::confirmSelection, enabled = !state.busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                            Text(stringResource(R.string.forest_sync_confirm))
                        }
                        TextButton(onClick = vm::editSelection, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.forest_sync_modify)) }
                    }
                    ForestSyncStep.COMPLETE -> OutlinedButton(onClick = vm::reload, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.forest_sync_refresh)) }
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text(stringResource(R.string.forest_sync_intro), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (state.loading || state.busy) item {
                ForestSyncCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(stringResource(if (state.loading) R.string.forest_sync_loading else R.string.forest_sync_busy))
                    }
                }
            }
            state.error?.let { error -> item {
                ForestSyncCard {
                    Text(stringResource(forestSyncErrorResource(error)), color = MaterialTheme.colorScheme.error)
                    if (error == ForestSyncError.SESSION_UNAVAILABLE) TextButton(onClick = onNavigateToLogin) { Text(stringResource(R.string.forest_sync_login)) }
                    else TextButton(enabled = !state.busy, onClick = vm::reload) { Text(stringResource(R.string.forest_sync_refresh)) }
                }
            } }
            val selection = state.selection
            if (selection != null) {
                item { ForestSyncCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null)
                        Column {
                            Text(stringResource(R.string.forest_sync_personal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            val currentProfile = profile?.takeIf { it.accountId == selection.accountId }
                            val accountName = currentProfile?.email ?: currentProfile?.displayName ?: selection.accountId
                            Text(stringResource(R.string.forest_sync_account, accountName), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } }
                if (state.step == ForestSyncStep.REVIEW) {
                    val chosen = selection.forests.filter { it.forestId in state.selectedForestIds && !it.blockedByOtherAccount }
                    item { ForestSyncCard {
                        Text(stringResource(R.string.forest_sync_review), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        ForestSyncCounts(forestSyncTotals(chosen))
                        Text(stringResource(R.string.forest_sync_full_dossier), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.forest_sync_counts_history), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.forest_sync_consent), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.forest_sync_photos), style = MaterialTheme.typography.bodySmall)
                    } }
                } else {
                    if (state.step == ForestSyncStep.COMPLETE) item { ForestSyncCard {
                        Text(stringResource(R.string.forest_sync_success), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.forest_sync_success_count, state.activatedCount ?: 0))
                    } }
                    item { ForestSyncCard {
                        Text(stringResource(R.string.forest_sync_summary), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        ForestSyncLine(stringResource(R.string.forest_sync_linked), state.summary.activated.toString())
                        ForestSyncLine(stringResource(R.string.forest_sync_pending), state.summary.pending.toString())
                        ForestSyncLine(stringResource(R.string.forest_sync_synced), state.summary.synced.toString())
                        ForestSyncLine(stringResource(R.string.forest_sync_conflict), state.summary.conflicts.toString())
                        ForestSyncLine(stringResource(R.string.forest_sync_error_status), state.summary.errors.toString())
                        Text(stringResource(R.string.forest_sync_retry_hint), style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(enabled = !state.busy && selection.forests.any { it.isActivated && !it.blockedByOtherAccount }, onClick = vm::synchronizeActivated, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.forest_sync_retry))
                        }
                        TextButton(enabled = !state.busy, onClick = vm::reload) { Text(stringResource(R.string.forest_sync_refresh)) }
                        if (state.transferFinished) Text(stringResource(R.string.forest_sync_finished), style = MaterialTheme.typography.bodySmall)
                    } }
                    item { Text(stringResource(R.string.forest_sync_selection_count, state.selectedForestIds.size), style = MaterialTheme.typography.titleMedium) }
                }
                val shown = selection.forests.let { forests -> if (state.step == ForestSyncStep.REVIEW) forests.filter { it.forestId in state.selectedForestIds } else forests }
                if (shown.isEmpty()) item { ForestSyncCard { Text(stringResource(R.string.forest_sync_no_forests)) } }
                items(shown, key = { it.forestId }) { forest ->
                    ForestSyncCandidateCard(forest, forest.forestId in state.selectedForestIds,
                        selectable = state.step == ForestSyncStep.SELECT && !state.busy,
                        onToggle = { vm.toggleForest(forest.forestId) },
                        onPreview = { vm.previewConflict(forest.forestId) })
                }
            }
        }
    }
    state.preview?.let { preview ->
        val remote = preview.remote
        val readOnly = state.previewSummary
        val canRetryMissingBackup = state.previewAllowsMissingBackupRetry && remote == null
        AlertDialog(onDismissRequest = vm::dismissPreview,
            title = { Text(stringResource(R.string.forest_sync_preview_title)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(preview.localName, fontWeight = FontWeight.SemiBold)
                if (remote == null) Text(stringResource(R.string.forest_sync_preview_absent))
                else {
                    Text(stringResource(R.string.forest_sync_preview_version, remote.serverVersion))
                    Text(stringResource(R.string.forest_sync_preview_date, remote.serverUpdatedAt), style = MaterialTheme.typography.bodySmall)
                    if (readOnly == null) Text(stringResource(R.string.forest_sync_preview_unavailable))
                    else {
                        readOnly.name?.let { Text(it) }
                        ForestSyncLine(stringResource(R.string.forest_sync_parcels), readOnly.parcels?.toString() ?: "—")
                        ForestSyncLine(stringResource(R.string.forest_sync_plots), readOnly.plots?.toString() ?: "—")
                        ForestSyncLine(stringResource(R.string.forest_sync_sessions), readOnly.sessions?.toString() ?: "—")
                        ForestSyncLine(stringResource(R.string.forest_sync_trees), readOnly.trees?.toString() ?: "—")
                    }
                }
                Text(stringResource(if (canRetryMissingBackup) R.string.forest_sync_missing_retry_hint else R.string.forest_sync_keep_hint),
                    style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = {
                if (canRetryMissingBackup) TextButton(onClick = vm::confirmMissingBackupRetry, enabled = !state.busy) {
                    Text(stringResource(R.string.forest_sync_missing_retry))
                } else TextButton(onClick = vm::dismissPreview, enabled = !state.busy) {
                    Text(stringResource(R.string.forest_sync_keep_both))
                }
            },
            dismissButton = {
                if (canRetryMissingBackup) TextButton(onClick = vm::dismissPreview, enabled = !state.busy) {
                    Text(stringResource(R.string.cancel))
                }
            })
    }
}

@Composable
private fun ForestSyncCandidateCard(forest: HierarchySyncCandidate, selected: Boolean, selectable: Boolean, onToggle: () -> Unit, onPreview: () -> Unit) {
    val canSelect = selectable && !forest.isActivated && !forest.blockedByOtherAccount
    Card(modifier = Modifier.fillMaxWidth().then(if (canSelect) Modifier.toggleable(selected, role = Role.Checkbox, onValueChange = { onToggle() }) else Modifier),
        shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (forest.blockedByOtherAccount) {
                Text(stringResource(R.string.forest_sync_other_account), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.forest_sync_other_hint), style = MaterialTheme.typography.bodySmall)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = selected || forest.isActivated, enabled = canSelect, onCheckedChange = null)
                    Text(forest.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                }
                ForestSyncCounts(forestSyncTotals(listOf(forest)))
                Text(stringResource(forestSyncStateResource(forest.state, forest.isActivated)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                if (forest.containsLocalPhotos) Text(stringResource(R.string.forest_sync_photos), style = MaterialTheme.typography.bodySmall)
                if (forest.state == "CONFLICT" || isMissingForestBackupRecovery(forest)) TextButton(onClick = onPreview, enabled = !selectable || forest.isActivated) { Text(stringResource(R.string.forest_sync_preview)) }
            }
        }
    }
}

@Composable
private fun ForestSyncCounts(totals: ForestSyncTotals) {
    ForestSyncLine(stringResource(R.string.forest_sync_parcels), totals.parcels.toString())
    ForestSyncLine(stringResource(R.string.forest_sync_plots), totals.plots.toString())
    ForestSyncLine(stringResource(R.string.forest_sync_sessions), totals.sessions.toString())
    ForestSyncLine(stringResource(R.string.forest_sync_trees), totals.trees.toString())
}
@Composable
private fun ForestSyncLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
@Composable
private fun ForestSyncCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
private fun forestSyncErrorResource(error: ForestSyncError) = when (error) {
    ForestSyncError.SESSION_UNAVAILABLE -> R.string.forest_sync_error_session
    ForestSyncError.SESSION_CHANGED -> R.string.forest_sync_error_changed
    ForestSyncError.LOAD_FAILED -> R.string.forest_sync_error_load
    ForestSyncError.ACTIVATION_FAILED -> R.string.forest_sync_error_activate
    ForestSyncError.TRANSFER_FAILED -> R.string.forest_sync_error_transfer
    ForestSyncError.PREVIEW_FAILED -> R.string.forest_sync_error_preview
}
private fun forestSyncStateResource(state: String?, activated: Boolean) = when (state) {
    "PENDING", "SYNCING" -> R.string.forest_sync_pending
    "SYNCED" -> R.string.forest_sync_synced
    "CONFLICT" -> R.string.forest_sync_conflict
    "ERROR" -> R.string.forest_sync_error_status
    else -> if (activated) R.string.forest_sync_linked else R.string.forest_sync_unknown_status
}
