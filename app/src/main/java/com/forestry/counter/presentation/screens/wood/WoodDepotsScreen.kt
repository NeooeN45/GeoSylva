package com.forestry.counter.presentation.screens.wood

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.forestry.counter.R
import com.forestry.counter.domain.model.WoodDepotKind
import com.forestry.counter.domain.model.WoodDepotLocationSource
import com.forestry.counter.domain.model.WoodDepotOwnerScope
import com.forestry.counter.domain.model.WoodDepotRecord
import com.forestry.counter.domain.repository.WoodDepotRepository
import com.forestry.counter.presentation.theme.GsShape
import com.forestry.counter.presentation.theme.Space
import com.forestry.counter.presentation.theme.Touch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Real local dossiers only. Reception, stock movements and commercial documents are separate tranches. */
@Composable
fun WoodDepotsScreen(
    repository: WoodDepotRepository,
    onNavigateBack: () -> Unit,
    professionCode: String? = null,
    modifier: Modifier = Modifier,
    onOpenWood: ((String) -> Unit)? = null,
) {
    val factory = remember(repository) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(WoodDepotsViewModel::class.java))
                return WoodDepotsViewModel(repository) as T
            }
        }
    }
    val viewModel: WoodDepotsViewModel = viewModel(factory = factory)
    WoodDepotsContent(viewModel, onNavigateBack, professionCode, modifier, onOpenWood)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WoodDepotsContent(
    viewModel: WoodDepotsViewModel,
    onNavigateBack: () -> Unit,
    professionCode: String? = null,
    modifier: Modifier = Modifier,
    onOpenWood: ((String) -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val stackedActions = density.fontScale > 1.3f || configuration.screenWidthDp < 360
    val compactActions = stackedActions && (configuration.screenHeightDp < 480 || WindowInsets.ime.getBottom(density) > 0)
    var discardRequested by remember(state.form?.id) { mutableStateOf(false) }
    var reloadRequested by remember(state.form?.id) { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.verifyAccess()
        }
        lifecycle.addObserver(observer)
        viewModel.verifyAccess()
        onDispose { lifecycle.removeObserver(observer) }
    }
    val goBack: () -> Unit = {
        if (!state.busy) {
            when {
                state.form != null -> discardRequested = true
                state.detail != null -> viewModel.closeDetail()
                else -> onNavigateBack()
            }
        }
    }
    BackHandler(enabled = state.form != null || state.detail != null || state.busy, onBack = goBack)

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("wood_depots_screen"),
        topBar = {
            Surface(modifier = Modifier.fillMaxWidth().testTag("wood_depots_header")) {
                Row(
                    modifier = Modifier.fillMaxWidth().windowInsetsPadding(TopAppBarDefaults.windowInsets)
                        .heightIn(min = Touch.fieldPrimary).padding(horizontal = Space.xs, vertical = Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    IconButton(onClick = goBack, enabled = !state.busy) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.wood_depots_back))
                    }
                    Text(stringResource(when {
                        state.form?.expectedRevision != null -> R.string.wood_depots_edit_title
                        state.form != null -> R.string.wood_depots_create_title
                        state.detail != null -> R.string.wood_depots_detail_title
                        else -> R.string.wood_depots_title
                    }), style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).padding(end = Space.sm)
                            .testTag("wood_depots_screen_title").semantics { heading() })
                }
            }
        },
        bottomBar = {
            if (state.form != null) {
                Surface(shadowElevation = Space.xxs) {
                    if (stackedActions) {
                        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(Space.md),
                            verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                            DepotSaveButton(viewModel::save, state.busy, Modifier.fillMaxWidth())
                            // In a short viewport/with the keyboard, Back still provides confirmed abandonment.
                            if (!compactActions) DepotCancelButton({ discardRequested = true }, state.busy, Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(
                            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(Space.md),
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DepotCancelButton({ discardRequested = true }, state.busy, Modifier.weight(1f))
                            DepotSaveButton(viewModel::save, state.busy, Modifier.weight(1f))
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = Space.xxl * 18).fillMaxWidth()
                    .testTag("wood_depots_content"),
                contentPadding = PaddingValues(Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                if (state.busy || state.loading) item {
                    LinearProgressIndicator(Modifier.fillMaxWidth().testTag("wood_depots_loading"))
                }
                state.error?.let { error -> item {
                    FeedbackCard(error, onReload = { reloadRequested = true }, onRetry = viewModel::retryList,
                        showReload = state.form != null || state.detail != null, enabled = !state.busy)
                } }
                state.notice?.let { notice -> item {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = GsShape.md) {
                        Text(stringResource(if (notice == WoodDepotsNotice.SAVED) R.string.wood_depots_saved
                            else R.string.wood_depots_archived), Modifier.padding(Space.md))
                    }
                } }
                val form = state.form
                val detail = state.detail
                when {
                    form != null -> {
                        item { DepotFormIntroduction() }
                        item { DepotNameField(form, !state.busy, viewModel::updateName) }
                        item { DepotKindField(form, !state.busy, viewModel::updateKind) }
                        item { DepotPositionField(form, !state.busy, viewModel) }
                        item { DepotNotesField(form, !state.busy, viewModel::updateNotes) }
                    }
                    detail != null -> {
                        item { DepotDetail(detail) }
                        if (onOpenWood != null) item {
                            Button(onClick = { onOpenWood(detail.id) }, enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = Touch.fieldPrimary).testTag("wood_depot_open_wood"), shape = GsShape.md) {
                                Text(stringResource(R.string.wo_open))
                            }
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                                Button(onClick = viewModel::edit, enabled = !state.busy,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = Touch.field)
                                        .testTag("wood_depot_edit"), shape = GsShape.md) {
                                    Icon(Icons.Default.Edit, null, Modifier.size(Space.lg))
                                    Text(stringResource(R.string.wood_depots_edit), Modifier.padding(start = Space.xs))
                                }
                                OutlinedButton(onClick = viewModel::requestArchive, enabled = !state.busy,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = Touch.field)
                                        .testTag("wood_depot_archive"), shape = GsShape.md) {
                                    Icon(Icons.Default.Archive, null, Modifier.size(Space.lg))
                                    Text(stringResource(R.string.wood_depots_archive), Modifier.padding(start = Space.xs))
                                }
                            }
                        }
                    }
                    else -> {
                        item { DepotOverview(professionCode) }
                        item {
                            Button(onClick = viewModel::create,
                                enabled = !state.busy && !state.loading && state.error != WoodDepotsError.LOAD_FAILED,
                                modifier = Modifier.fillMaxWidth().heightIn(min = Touch.fieldPrimary)
                                    .testTag("wood_depot_create"), shape = GsShape.md) {
                                Icon(Icons.Default.Add, null, Modifier.size(Space.lg))
                                Text(stringResource(R.string.wood_depots_create), Modifier.padding(start = Space.xs))
                            }
                        }
                        if (!state.loading && state.depots.isEmpty()) item { DepotEmptyState() }
                        items(state.depots, key = { it.id }) { record ->
                            DepotListCard(record, !state.busy) { viewModel.open(record.id) }
                        }
                    }
                }
            }
        }
    }

    if (discardRequested && state.form != null) AlertDialog(
        onDismissRequest = { discardRequested = false },
        title = { Text(stringResource(R.string.wood_depots_discard_title)) },
        text = { Text(stringResource(R.string.wood_depots_discard_body)) },
        confirmButton = { TextButton(onClick = { discardRequested = false; viewModel.closeForm() }) {
            Text(stringResource(R.string.wood_depots_discard))
        } },
        dismissButton = { TextButton(onClick = { discardRequested = false }) {
            Text(stringResource(R.string.wood_depots_keep_editing))
        } },
    )
    if (reloadRequested && state.error == WoodDepotsError.CONFLICT) AlertDialog(
        onDismissRequest = { reloadRequested = false },
        title = { Text(stringResource(R.string.wood_depots_reload_title)) },
        text = { Text(stringResource(R.string.wood_depots_reload_body)) },
        confirmButton = { TextButton(onClick = { reloadRequested = false; viewModel.reloadAfterConflict() }) {
            Text(stringResource(R.string.wood_depots_reload))
        } },
        dismissButton = { TextButton(onClick = { reloadRequested = false }) {
            Text(stringResource(R.string.wood_depots_keep_editing))
        } },
    )
    state.archiveCandidate?.let { record -> AlertDialog(
        onDismissRequest = viewModel::dismissArchive,
        icon = { Icon(Icons.Default.Archive, null) },
        title = { Text(stringResource(R.string.wood_depots_archive_title)) },
        text = { Text(stringResource(R.string.wood_depots_archive_body, record.draft.name)) },
        confirmButton = { TextButton(onClick = viewModel::confirmArchive, enabled = !state.busy,
            modifier = Modifier.testTag("wood_depot_confirm_archive")) {
            Text(stringResource(R.string.wood_depots_archive))
        } },
        dismissButton = { TextButton(onClick = viewModel::dismissArchive, enabled = !state.busy) {
            Text(stringResource(R.string.wood_depots_cancel))
        } },
    ) }
}

@Composable
private fun DepotSaveButton(onSave: () -> Unit, busy: Boolean, modifier: Modifier) {
    Button(onClick = onSave, enabled = !busy,
        modifier = modifier.heightIn(min = Touch.field).testTag("wood_depot_save"), shape = GsShape.md) {
        Icon(Icons.Default.Save, null, Modifier.size(Space.lg))
        Text(stringResource(if (busy) R.string.wood_depots_saving else R.string.wood_depots_save),
            modifier = Modifier.padding(start = Space.xs).testTag("wood_depot_save_label"))
    }
}

@Composable
private fun DepotCancelButton(onCancel: () -> Unit, busy: Boolean, modifier: Modifier) {
    OutlinedButton(onClick = onCancel, enabled = !busy,
        modifier = modifier.heightIn(min = Touch.field).testTag("wood_depot_cancel"), shape = GsShape.md) {
        Text(stringResource(R.string.wood_depots_cancel))
    }
}

@Composable
private fun DepotOverview(professionCode: String?) {
    val headingRes = when (professionCode) {
        "conducteur_travaux_forestiers" -> R.string.wood_depots_conductor_heading
        "commerce_bois" -> R.string.wood_depots_trade_heading
        else -> R.string.wood_depots_operator_heading
    }
    val description = when (professionCode) {
        "conducteur_travaux_forestiers" -> R.string.wood_depots_conductor_description
        "commerce_bois" -> R.string.wood_depots_trade_description
        else -> R.string.wood_depots_operator_description
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = GsShape.lg) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Icon(Icons.Default.Inventory2, null, Modifier.size(Space.xl))
            Text(stringResource(headingRes), style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() })
            Text(stringResource(description), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.wood_depots_local_only), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun DepotEmptyState() {
    OutlinedCard(shape = GsShape.lg) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text(stringResource(R.string.wood_depots_empty_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.wood_depots_empty_body), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun DepotListCard(record: WoodDepotRecord, enabled: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, enabled = enabled, shape = GsShape.lg,
        modifier = Modifier.fillMaxWidth().testTag("wood_depot_${record.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(Space.lg), horizontalArrangement = Arrangement.spacedBy(Space.md),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(record.draft.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(stringResource(kindLabel(record.draft.kind)), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(if (record.draft.location != null) R.string.wood_depots_position_recorded
                    else R.string.wood_depots_position_unknown), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(Space.lg))
        }
    }
}

@Composable
private fun DepotFormIntroduction() {
    Text(stringResource(R.string.wood_depots_form_intro), style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DepotNameField(form: WoodDepotForm, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(value = form.name, onValueChange = onChange, enabled = enabled,
        label = { Text(stringResource(R.string.wood_depots_name)) },
        supportingText = { Text(stringResource(if (form.showValidation && !form.validName)
            R.string.wood_depots_name_error else R.string.wood_depots_name_hint)) },
        isError = form.showValidation && !form.validName, singleLine = true, shape = GsShape.md,
        modifier = Modifier.fillMaxWidth().testTag("wood_depot_name"))
}

@Composable
private fun DepotKindField(form: WoodDepotForm, enabled: Boolean, onChange: (WoodDepotKind) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(stringResource(R.string.wood_depots_kind), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() })
        WoodDepotKind.entries.forEach { kind ->
            Surface(shape = GsShape.md,
                color = if (form.kind == kind) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().selectable(selected = form.kind == kind,
                    enabled = enabled, role = Role.RadioButton, onClick = { onChange(kind) })
                    .testTag("wood_depot_kind_${kind.name}")) {
                Row(Modifier.heightIn(min = Touch.field).padding(horizontal = Space.sm, vertical = Space.xs),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = form.kind == kind, onClick = null, enabled = enabled)
                    Text(stringResource(kindLabel(kind)), Modifier.padding(start = Space.sm),
                        style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (form.showValidation && form.kind == null) Text(stringResource(R.string.wood_depots_kind_error),
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DepotPositionField(form: WoodDepotForm, enabled: Boolean, viewModel: WoodDepotsViewModel) {
    OutlinedCard(shape = GsShape.lg) {
        Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, null, Modifier.size(Space.lg))
                Text(stringResource(R.string.wood_depots_position_title),
                    modifier = Modifier.padding(start = Space.xs).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.wood_depots_position_hint), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth().selectable(selected = form.hasPosition, enabled = enabled,
                role = Role.Checkbox, onClick = { viewModel.setHasPosition(!form.hasPosition) })
                .heightIn(min = Touch.field).testTag("wood_depot_position_toggle"),
                verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = form.hasPosition, onCheckedChange = null, enabled = enabled)
                Text(stringResource(R.string.wood_depots_position_enable), Modifier.padding(start = Space.xs))
            }
            if (form.hasPosition) {
                OutlinedTextField(value = form.latitude, onValueChange = viewModel::updateLatitude, enabled = enabled,
                    label = { Text(stringResource(R.string.wood_depots_latitude)) },
                    trailingIcon = { CoordinateSignButton(form.latitude, enabled, viewModel::updateLatitude) },
                    supportingText = { Text(stringResource(if (form.showValidation && !form.validLatitude)
                        R.string.wood_depots_latitude_error else R.string.wood_depots_latitude_hint)) },
                    isError = form.showValidation && !form.validLatitude, singleLine = true, shape = GsShape.md,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("wood_depot_latitude"))
                OutlinedTextField(value = form.longitude, onValueChange = viewModel::updateLongitude, enabled = enabled,
                    label = { Text(stringResource(R.string.wood_depots_longitude)) },
                    trailingIcon = { CoordinateSignButton(form.longitude, enabled, viewModel::updateLongitude) },
                    supportingText = { Text(stringResource(if (form.showValidation && !form.validLongitude)
                        R.string.wood_depots_longitude_error else R.string.wood_depots_longitude_hint)) },
                    isError = form.showValidation && !form.validLongitude, singleLine = true, shape = GsShape.md,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("wood_depot_longitude"))
                Text(stringResource(if (form.originalLocation?.source == WoodDepotLocationSource.GPS && !form.positionEdited)
                    R.string.wood_depots_position_gps_preserved else R.string.wood_depots_position_manual),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CoordinateSignButton(value: String, enabled: Boolean, onChange: (String) -> Unit) {
    IconButton(onClick = {
        val current = value.trim()
        onChange(if (current.startsWith('-')) current.removePrefix("-") else "-" + current.removePrefix("+"))
    }, enabled = enabled) {
        Icon(Icons.Default.SwapHoriz, stringResource(R.string.wood_depots_change_sign))
    }
}

@Composable
private fun DepotNotesField(form: WoodDepotForm, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(value = form.notes, onValueChange = onChange, enabled = enabled,
        label = { Text(stringResource(R.string.wood_depots_notes)) },
        supportingText = { Text(stringResource(if (form.showValidation && !form.validNotes)
            R.string.wood_depots_notes_error else R.string.wood_depots_notes_hint)) },
        isError = form.showValidation && !form.validNotes, minLines = 3, shape = GsShape.md,
        modifier = Modifier.fillMaxWidth().testTag("wood_depot_notes"))
}

@Composable
private fun DepotDetail(record: WoodDepotRecord) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val dateFormat = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale) }
    Card(shape = GsShape.lg, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Text(record.draft.name, style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.testTag("wood_depot_detail_name").semantics { heading() })
            Text(stringResource(kindLabel(record.draft.kind)), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(if (record.ownerScope == WoodDepotOwnerScope.LOCAL_DEVICE)
                R.string.wood_depots_scope_local else R.string.wood_depots_scope_personal),
                style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.wood_depots_position_title), style = MaterialTheme.typography.titleMedium)
            val location = record.draft.location
            if (location == null) Text(stringResource(R.string.wood_depots_position_unknown))
            else {
                Text(stringResource(R.string.wood_depots_coordinates, location.latitude.toString(), location.longitude.toString()),
                    style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(if (location.source == WoodDepotLocationSource.GPS)
                    R.string.wood_depots_position_source_gps else R.string.wood_depots_position_manual),
                    style = MaterialTheme.typography.bodyMedium)
                location.accuracyM?.let { Text(stringResource(R.string.wood_depots_accuracy, it.toString()),
                    style = MaterialTheme.typography.bodyMedium) }
            }
            Text(stringResource(R.string.wood_depots_notes), style = MaterialTheme.typography.titleMedium)
            Text(record.draft.notes ?: stringResource(R.string.wood_depots_no_notes), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.wood_depots_revision, record.revision), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.wood_depots_created_at, dateFormat.format(Date(record.createdAt))),
                style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.wood_depots_updated_at, dateFormat.format(Date(record.updatedAt))),
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FeedbackCard(error: WoodDepotsError, onReload: () -> Unit, onRetry: () -> Unit,
    showReload: Boolean, enabled: Boolean) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = GsShape.md) {
        Column(Modifier.fillMaxWidth().padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(stringResource(when (error) {
                WoodDepotsError.ACCESS_CHANGED -> R.string.wood_depots_access_changed
                WoodDepotsError.LOAD_FAILED -> R.string.wood_depots_load_error
                WoodDepotsError.SAVE_FAILED -> R.string.wood_depots_save_error
                WoodDepotsError.CONFLICT -> R.string.wood_depots_conflict
                WoodDepotsError.UNAVAILABLE -> R.string.wood_depots_unavailable
                WoodDepotsError.ARCHIVE_FAILED -> R.string.wood_depots_archive_error
            }), modifier = Modifier.testTag("wood_depots_error"))
            if (error == WoodDepotsError.CONFLICT && showReload) TextButton(onClick = onReload, enabled = enabled) {
                Text(stringResource(R.string.wood_depots_reload))
            }
            if (error == WoodDepotsError.LOAD_FAILED && !showReload) TextButton(onClick = onRetry, enabled = enabled) {
                Text(stringResource(R.string.wood_depots_retry))
            }
        }
    }
}

@StringRes
private fun kindLabel(kind: WoodDepotKind) = when (kind) {
    WoodDepotKind.ROADSIDE -> R.string.wood_depots_kind_roadside
    WoodDepotKind.YARD -> R.string.wood_depots_kind_yard
    WoodDepotKind.OTHER -> R.string.wood_depots_kind_other
}
