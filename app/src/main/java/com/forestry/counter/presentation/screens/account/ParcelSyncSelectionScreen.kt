package com.forestry.counter.presentation.screens.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.forestry.counter.R
import com.forestry.counter.domain.model.ParcelSyncCandidate
import com.forestry.counter.domain.repository.IdentityRepository
import com.forestry.counter.domain.repository.ParcelSyncRepository
import com.forestry.counter.presentation.theme.GsShape
import com.forestry.counter.presentation.theme.Space
import com.forestry.counter.presentation.theme.Touch
import com.forestry.counter.presentation.viewmodel.GeoSylvaViewModelFactory
import com.forestry.counter.presentation.viewmodel.ParcelSyncSelectionError
import com.forestry.counter.presentation.viewmodel.ParcelSyncSelectionStep
import com.forestry.counter.presentation.viewmodel.ParcelSyncSelectionUiState
import com.forestry.counter.presentation.viewmodel.ParcelSyncSelectionViewModel
import java.text.NumberFormat

/** Parcours unique d'ajout, sans modification des associations déjà existantes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParcelSyncSelectionScreen(
    identityRepository: IdentityRepository,
    parcelSyncRepository: ParcelSyncRepository,
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
) {
    val factory = remember(identityRepository, parcelSyncRepository) {
        GeoSylvaViewModelFactory { ParcelSyncSelectionViewModel(identityRepository, parcelSyncRepository) }
    }
    val viewModel: ParcelSyncSelectionViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.validateSession()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.isClosed) {
        if (state.isClosed) onNavigateBack()
    }
    val onBack = {
        if (!state.isSubmitting) {
            if (state.step == ParcelSyncSelectionStep.REVIEW) viewModel.editSelection()
            else viewModel.cancel()
        }
    }
    BackHandler { onBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.parcel_selection_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.isSubmitting) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                },
            )
        },
        bottomBar = {
            SelectionActions(
                state = state,
                onReview = viewModel::reviewSelection,
                onConfirm = viewModel::confirmSelection,
                onEdit = viewModel::editSelection,
                onCancel = viewModel::cancel,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Space.screenH, vertical = Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            if (state.isLoading) {
                item {
                    SelectionCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.md),
                        ) {
                            CircularProgressIndicator(Modifier.size(Space.lg), strokeWidth = Space.xxs)
                            Text(stringResource(R.string.parcel_selection_loading))
                        }
                    }
                }
            } else if (state.error != null) {
                item {
                    SelectionErrorCard(state.error!!, viewModel::reload, onNavigateToLogin)
                }
            } else if (state.step == ParcelSyncSelectionStep.SUCCESS) {
                item {
                    SelectionCard {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Touch.min),
                        )
                        Text(
                            stringResource(R.string.parcel_selection_success_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        val addedCount = state.addedCount ?: 0
                        Text(pluralStringResource(R.plurals.parcel_selection_added, addedCount, addedCount))
                        Text(
                            stringResource(R.string.parcel_selection_queued_explanation),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                val reviewing = state.step == ParcelSyncSelectionStep.REVIEW
                item {
                    SelectionCard {
                        Text(
                            stringResource(
                                if (reviewing) R.string.parcel_selection_review_title
                                else R.string.parcel_selection_choose_title
                            ),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(
                                if (reviewing) R.string.parcel_selection_review_hint
                                else R.string.parcel_selection_choose_hint
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null)
                            Text(
                                stringResource(R.string.parcel_selection_scope),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                item { SelectionDestination() }
                val candidates = state.selection?.parcels.orEmpty().let { parcels ->
                    if (reviewing) parcels.filter { it.parcelId in state.selectedParcelIds }
                    else parcels
                }
                if (candidates.isEmpty()) {
                    item {
                        SelectionCard {
                            Text(stringResource(R.string.parcel_selection_empty_title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.parcel_selection_empty_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(candidates, key = { it.parcelId }) { parcel ->
                        SelectionParcelCard(
                            parcel = parcel,
                            selected = parcel.parcelId in state.selectedParcelIds,
                            reviewing = reviewing,
                            enabled = !state.isSubmitting,
                            onToggle = { viewModel.toggleParcel(parcel.parcelId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionDestination() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = GsShape.lg,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(Space.md),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.AccountCircle, contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                Text(stringResource(R.string.parcel_selection_destination_label), style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.parcel_selection_destination), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun SelectionParcelCard(
    parcel: ParcelSyncCandidate,
    selected: Boolean,
    reviewing: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val selectable = (!parcel.isLinked || parcel.recoveryRequired) && !parcel.blockedByOtherAccount && !parcel.recoveryUnavailable
    val numberFormat = remember { NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 } }
    val surface = parcel.surfaceHa?.takeIf { it.isFinite() && it > 0 }
    val details = if (parcel.blockedByOtherAccount) "" else listOfNotNull(
        parcel.commune?.takeIf { it.isNotBlank() },
        surface?.let { stringResource(R.string.parcel_selection_surface, numberFormat.format(it)) },
    ).joinToString(" · ")
    Card(
        modifier = Modifier.fillMaxWidth().then(
            if (!reviewing) Modifier.toggleable(
                value = selected,
                enabled = selectable && enabled,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            ) else Modifier
        ),
        shape = GsShape.md,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(Space.md).heightIn(min = Touch.min),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!reviewing) {
                Checkbox(checked = selected, onCheckedChange = null, enabled = selectable && enabled)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(
                    if (parcel.blockedByOtherAccount) stringResource(R.string.parcel_selection_other_account_title)
                    else parcel.name.ifBlank { stringResource(R.string.parcel_selection_unnamed) },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodyMedium)
                if (parcel.recoveryRequired) {
                    Text(stringResource(if (parcel.recoveryUnavailable) R.string.parcel_recovery_unavailable else R.string.parcel_recovery_required),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    parcel.recoveryPreview?.let { preview ->
                        Text(stringResource(R.string.parcel_recovery_preview,
                            preview.localName, preview.remoteName ?: stringResource(R.string.parcel_recovery_remote_absent),
                            preview.remoteVersion?.toString() ?: "—"), style = MaterialTheme.typography.bodySmall)
                        if (preview.deletesParcel) Text(stringResource(R.string.parcel_recovery_deletion),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        if (reviewing) Text(stringResource(R.string.parcel_recovery_consent),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (!selectable && !parcel.recoveryRequired) {
                    Text(
                        stringResource(
                            if (parcel.blockedByOtherAccount) R.string.parcel_selection_other_account
                            else R.string.parcel_selection_already_linked
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionErrorCard(error: ParcelSyncSelectionError, onReload: () -> Unit, onLogin: () -> Unit) {
    SelectionCard {
        Text(stringResource(R.string.parcel_selection_error_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(
                when (error) {
                    ParcelSyncSelectionError.SESSION_UNAVAILABLE -> R.string.parcel_selection_error_session
                    ParcelSyncSelectionError.SESSION_CHANGED -> R.string.parcel_selection_error_changed
                    ParcelSyncSelectionError.LOAD_FAILED -> R.string.parcel_selection_error_load
                    ParcelSyncSelectionError.ENQUEUE_FAILED -> R.string.parcel_selection_error_enqueue
                }
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (error == ParcelSyncSelectionError.SESSION_UNAVAILABLE) {
            Button(onClick = onLogin, shape = GsShape.md, modifier = Modifier.fillMaxWidth().heightIn(min = Touch.min)) {
                Text(stringResource(R.string.parcel_selection_login))
            }
        }
        OutlinedButton(onClick = onReload, shape = GsShape.md, modifier = Modifier.fillMaxWidth().heightIn(min = Touch.min)) {
            Text(stringResource(R.string.parcel_selection_reload))
        }
    }
}

@Composable
private fun SelectionActions(
    state: ParcelSyncSelectionUiState,
    onReview: () -> Unit,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = Space.screenH, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            if (state.step == ParcelSyncSelectionStep.SUCCESS) {
                Button(onClick = onCancel, shape = GsShape.md, modifier = Modifier.fillMaxWidth().heightIn(min = Touch.min)) {
                    Text(stringResource(R.string.parcel_selection_done))
                }
            } else {
                if (state.selection != null && state.error == null) {
                    val count = state.selectedParcelIds.size
                    if (state.step == ParcelSyncSelectionStep.SELECT) {
                        Text(
                            pluralStringResource(R.plurals.parcel_selection_selected, count, count),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Button(
                            onClick = onReview,
                            enabled = count > 0 && !state.isLoading,
                            shape = GsShape.md,
                            modifier = Modifier.fillMaxWidth().heightIn(min = Touch.min),
                        ) { Text(stringResource(R.string.parcel_selection_review)) }
                    } else {
                        Button(
                            onClick = onConfirm,
                            enabled = !state.isSubmitting,
                            shape = GsShape.md,
                            modifier = Modifier.fillMaxWidth().heightIn(min = Touch.min),
                        ) {
                            if (state.isSubmitting) {
                                CircularProgressIndicator(Modifier.size(Space.lg), strokeWidth = Space.xxs, color = MaterialTheme.colorScheme.onPrimary)
                                Text(stringResource(R.string.parcel_selection_submitting), Modifier.padding(start = Space.sm))
                            } else {
                                Text(pluralStringResource(R.plurals.parcel_selection_confirm, count, count))
                            }
                        }
                        OutlinedButton(
                            onClick = onEdit,
                            enabled = !state.isSubmitting,
                            shape = GsShape.md,
                            modifier = Modifier.fillMaxWidth().heightIn(min = Touch.min),
                        ) { Text(stringResource(R.string.parcel_selection_edit)) }
                    }
                }
                TextButton(onClick = onCancel, enabled = !state.isSubmitting, modifier = Modifier.fillMaxWidth().heightIn(min = Touch.min)) {
                    Text(stringResource(R.string.parcel_selection_cancel))
                }
            }
        }
    }
}

@Composable
private fun SelectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = GsShape.lg,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
            content = content,
        )
    }
}
