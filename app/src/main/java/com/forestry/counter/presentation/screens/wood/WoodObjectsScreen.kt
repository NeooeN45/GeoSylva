package com.forestry.counter.presentation.screens.wood

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.forestry.counter.R
import com.forestry.counter.presentation.screens.field.RetainedHelp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import com.forestry.counter.domain.repository.*
import com.forestry.counter.domain.wood.*
import com.forestry.counter.presentation.theme.GsShape
import com.forestry.counter.presentation.theme.Space
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.UUID

@Composable
fun WoodObjectsScreen(repository: WoodObjectRepository, depotId: UUID, fieldRepository: FieldRepository? = null, onJournal: ((UUID)->Unit)? = null, onDepotJournal: (()->Unit)? = null, initialObjectId: UUID? = null, onBack: () -> Unit) {
    val factory = remember(repository, depotId) { object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = WoodObjectsViewModel(repository, depotId, fieldRepository) as T
    } }
    val vm: WoodObjectsViewModel = viewModel(key = "wood-$depotId", factory = factory)
    val current by vm.state.collectAsStateWithLifecycle()
    var initialHandled by remember(vm,initialObjectId) { mutableStateOf(false) }
    LaunchedEffect(current.busy,initialObjectId) {
        if (!current.busy && !initialHandled) {
            initialHandled=true
            if (initialObjectId != null && current.form == null && !current.pendingRetry && !current.accessLost) vm.open(initialObjectId)
        }
    }
    WoodObjectsContent(vm, onBack, onJournal, onDepotJournal)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WoodObjectsContent(vm: WoodObjectsViewModel, onBack: () -> Unit, onJournal: ((UUID)->Unit)? = null, onDepotJournal: (()->Unit)? = null) {
    val state by vm.state.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }
    DisposableEffect(vm) { onDispose { vm.flushDraft() } }
    var discard by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val page = when {
        state.form?.identityOnly == true -> "identity"
        state.form != null -> "measure-${state.form?.correction?.record?.observationId}"
        state.detail != null -> "detail-${state.detail?.item?.id}"
        else -> "list"
    }
    LaunchedEffect(page) {
        listState.scrollToItem(0)
        if (state.form == null) { focus.clearFocus(force = true); keyboard?.hide() }
    }
    LaunchedEffect(state.validationError) { if (state.validationError) listState.scrollToItem(0) }
    val back: () -> Unit = {
        if (!state.busy) when {
            state.form != null || state.pendingRetry -> discard = true
            state.detail != null -> vm.close()
            else -> onBack()
        }
    }
    BackHandler(state.form != null || state.detail != null || state.busy, back)
    Scaffold(
        modifier = Modifier.testTag("wood_objects_screen"),
        topBar = {
            Surface {
                Row(Modifier.fillMaxWidth().windowInsetsPadding(TopAppBarDefaults.windowInsets).heightIn(min = 56.dp).padding(Space.xs),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    IconButton(back, enabled = !state.busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.wo_close)) }
                    Text(stringResource(when {
                        state.form?.identityOnly == true -> R.string.wo_create
                        state.form?.correction != null -> R.string.wo_correct
                        state.form != null -> R.string.wo_measure
                        else -> R.string.wo_title
                    }), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(end = Space.sm).testTag("wood_objects_title"))
                }
            }
        },
        bottomBar = {
            if (state.form != null) Surface(shadowElevation = 4.dp) {
                Column(Modifier.navigationBarsPadding().imePadding()) {
                Button(vm::save, enabled = !state.busy && !state.accessLost && state.error != WoodRepositoryError.CONFLICT,
                    modifier = Modifier.fillMaxWidth().padding(Space.md).heightIn(min = 56.dp)
                        .testTag("wood_object_save"), shape = GsShape.md) {
                    Text(stringResource(if (state.pendingRetry) R.string.wo_retry else R.string.wo_save))
                }
                if (state.form?.kind == WoodObjectKind.LOG && state.form?.identityOnly == false && state.form?.correction == null && !state.pendingRetry) {
                    TextButton(vm::saveAndNext, enabled = !state.busy && !state.accessLost && state.error != WoodRepositoryError.CONFLICT,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.md)) {
                        Text(stringResource(R.string.wood_next))
                    }
                }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxWidth().testTag("wood_objects_content"),
                state = listState, contentPadding = PaddingValues(Space.md), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (state.error != null) item {
                    Surface(shape = GsShape.md, color = MaterialTheme.colorScheme.errorContainer) {
                        Column(Modifier.padding(Space.md)) {
                            Text(stringResource(when (state.error) {
                                WoodRepositoryError.ACCESS_CHANGED -> R.string.wo_error_access
                                WoodRepositoryError.CONFLICT -> R.string.wo_error_conflict
                                WoodRepositoryError.UNAVAILABLE -> R.string.wo_error_unavailable
                                WoodRepositoryError.CORRUPT_DATA -> R.string.wo_error_data
                                else -> if (state.pendingRetry) R.string.wo_error_save else R.string.wo_error_load
                            }))
                            if (!state.accessLost && state.form == null) TextButton(vm::retry, enabled = !state.busy) {
                                Text(stringResource(if (state.pendingRetry) R.string.wo_retry else R.string.wo_reload))
                            }
                        }
                    }
                }
                if (state.saved) item { Text(stringResource(R.string.wo_saved), color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("wood_object_saved")) }
                val form = state.form
                val detail = state.detail
                when {
                    state.accessLost -> Unit
                    form != null -> {
                        item { Text(stringResource(if (form.identityOnly) R.string.wo_identity_intro else R.string.wo_measure_intro),
                            style = MaterialTheme.typography.bodyLarge) }
                        if (state.validationError) item { Text(stringResource(R.string.wo_validation), color = MaterialTheme.colorScheme.error) }
                        item { Text(stringResource(if (state.draftSaved) R.string.field_draft else R.string.field_draft_pending), style = MaterialTheme.typography.labelMedium) }
                        if (form.kind == WoodObjectKind.LOG && !form.identityOnly) item { Text(stringResource(R.string.wood_series_help)) }
                        item { WoodForm(form, enabled = !state.busy && !state.pendingRetry && !state.draftConflict, change = vm::change) }
                    }
                    detail != null -> {
                        if (onJournal != null) item {
                            OutlinedButton({ onJournal(detail.item.id) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.field_title)) }
                        }
                        item {
                            Card(shape = GsShape.lg, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                                Column(Modifier.fillMaxWidth().padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                                    Icon(Icons.Default.Inventory2, null)
                                    Text(detail.item.header.name, style = MaterialTheme.typography.headlineSmall)
                                    Text(stringResource(if (detail.item.kind == WoodObjectKind.PILE) R.string.wo_pile else R.string.wo_log))
                                    detail.item.header.tag?.let { Text(it) }
                                    detail.item.header.declaredSpecies?.let { Text(it) }
                                    detail.item.header.declaredQuality?.let { Text(it) }
                                    detail.item.header.declaredOrigin?.let { Text(it) }
                                    detail.item.header.description?.let { Text(it) }
                                    Text(stringResource(R.string.wo_local), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        if (detail.item.archivedAt == null) item {
                            OutlinedButton(vm::editIdentity, enabled = !state.busy && !state.pendingRetry, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.wo_edit_identity))
                            }
                        }
                        if (detail.item.archivedAt == null) item { Button({ vm.measure() }, enabled = !state.busy && !state.pendingRetry,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("wood_object_measure"), shape = GsShape.md) {
                            Text(stringResource(R.string.wo_measure))
                        } } else item { Text(stringResource(R.string.wo_read_only)) }
                        item {
                            Text(stringResource(R.string.wo_reference), style = MaterialTheme.typography.titleLarge)
                            when (val selection = detail.history.effectiveSelection) {
                                WoodEffectiveSelection.None -> Text(stringResource(R.string.wo_no_reference))
                                is WoodEffectiveSelection.Superseded -> Text(stringResource(R.string.wo_reference_corrected))
                                is WoodEffectiveSelection.Current -> Calculation(selection.record.observation)
                            }
                        }
                        item { Text(stringResource(R.string.wo_history), style = MaterialTheme.typography.titleLarge) }
                        val corrected = detail.observations.mapNotNull { it.record.correctionOfObservationId }.toSet()
                        items(detail.observations.asReversed(), key = { it.record.observationId }) { observation ->
                            ObservationCard(observation, observation.record.observationId == detail.history.effectiveObservationId,
                                observation.record.observationId in corrected, enabled = !state.busy && !state.pendingRetry && detail.item.archivedAt == null,
                                onCorrect = { vm.measure(observation) }, onSelect = { vm.select(observation.record.observationId) })
                        }
                    }
                    else -> {
                        if (onDepotJournal != null) item { OutlinedButton(onDepotJournal, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.field_title)) } }
                        item { OutlinedTextField(search, { search = it }, label = { Text(stringResource(R.string.wood_search)) }, modifier = Modifier.fillMaxWidth()) }
                        item { Text(stringResource(R.string.wo_intro), style = MaterialTheme.typography.bodyLarge) }
                        item { Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                            Button({ vm.create(WoodObjectKind.PILE) }, enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("wood_object_create_pile")) { Text(stringResource(R.string.wo_add_pile)) }
                            OutlinedButton({ vm.create(WoodObjectKind.LOG) }, enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("wood_object_create_log")) { Text(stringResource(R.string.wo_add_log)) }
                        } }
                        if (state.items.isEmpty() && !state.busy) item { Text(stringResource(R.string.wo_empty)) }
                        items(state.items.filter { it.header.name.contains(search, true) || it.header.tag?.contains(search, true) == true || it.header.declaredSpecies?.contains(search,true) == true }, key = { it.id }) { item ->
                            Card(onClick = { vm.open(item.id) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("wood_object_${item.id}"), shape = GsShape.lg) {
                                Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                                    Text(item.header.name, style = MaterialTheme.typography.titleLarge)
                                    Text(stringResource(if (item.kind == WoodObjectKind.PILE) R.string.wo_pile else R.string.wo_log))
                                    if (item.archivedAt != null) Text(stringResource(R.string.wo_read_only))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (discard) {
        // Resolve in the screen locale: Android dialog/popup roots can have a different configuration.
        val title = stringResource(R.string.wo_discard_title)
        val body = stringResource(R.string.wo_discard_body)
        val leave = stringResource(R.string.wo_discard)
        val keep = stringResource(R.string.wo_cancel)
        AlertDialog(onDismissRequest = { discard = false }, title = { Text(title) }, text = { Text(body) },
            confirmButton = { TextButton({ discard = false; vm.close() }) { Text(leave) } },
            dismissButton = { TextButton({ discard = false }) { Text(keep) } })
    }
}

@Composable
private fun WoodForm(form: WoodManualForm, enabled: Boolean, change: (WoodManualForm.() -> WoodManualForm) -> Unit) {
    if (form.kind == WoodObjectKind.PILE && !form.identityOnly && !form.newWithMeasurement) {
        PileMeasurementForm(form, enabled, change)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        if (form.identityOnly || form.newWithMeasurement) {
            Field(form.name, R.string.wo_name, "wood_name", enabled, 200) { change { copy(name = it) } }
            Field(form.tag, R.string.wo_tag, "wood_tag", enabled, 200) { change { copy(tag = it) } }
        }
        if (!form.identityOnly) {
            Text(form.name, style = MaterialTheme.typography.titleLarge)
            Field(form.observedDate, R.string.wo_date, "wood_observed_date", enabled, 16) { change { copy(observedDate = it) } }
            Text(stringResource(R.string.wo_date_hint, form.observedZone), style = MaterialTheme.typography.bodySmall)
            Field(form.length, R.string.wo_length, "wood_length", enabled, 128, true) { change { copy(length = it) } }
            if (form.kind == WoodObjectKind.PILE) {
                Field(form.depth, R.string.wo_depth, "wood_depth", enabled, 128, true) { change { copy(depth = it) } }
                Field(form.height, R.string.wo_height, "wood_height", enabled, 128, true) { change { copy(height = it) } }
                Field(form.pieceLength, R.string.wo_piece_length, "wood_piece_length", enabled, 128, true) { change { copy(pieceLength = it) } }
                Text(stringResource(R.string.wo_piece_hint), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("wood_shape")
                    .toggleable(value = form.shape, enabled = enabled, role = Role.Checkbox, onValueChange = { value -> change { copy(shape = value) } }),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(form.shape, null, enabled = enabled)
                    Spacer(Modifier.width(Space.sm))
                    Text(stringResource(R.string.wo_shape), Modifier.weight(1f))
                }
                Field(form.protocol, R.string.wo_protocol, "wood_protocol", enabled, 300) { change { copy(protocol = it) } }
                Text(stringResource(R.string.wo_protocol_hint), style = MaterialTheme.typography.bodySmall)
            } else {
                WoodChoice(R.string.wo_method, form.method, listOf(null to R.string.wo_no_method, "huber" to R.string.wo_huber,
                    "smalian" to R.string.wo_smalian, "newton" to R.string.wo_newton), enabled) { change { copy(method = it) } }
                WoodChoice(R.string.wo_bark, form.bark, listOf(null to R.string.wo_no_method, "OVER_BARK" to R.string.wo_over,
                    "UNDER_BARK" to R.string.wo_under), enabled) { change { copy(bark = it) } }
                // Supplied sections stay visible if the method changes; no silent loss or substitution.
                if (form.method != "huber" || form.start.isNotEmpty()) Field(form.start, R.string.wo_start, "wood_start", enabled, 128, true) { change { copy(start = it) } }
                if (form.method != "smalian" || form.middle.isNotEmpty()) Field(form.middle, R.string.wo_middle, "wood_middle", enabled, 128, true) { change { copy(middle = it) } }
                if (form.method != "huber" || form.end.isNotEmpty()) Field(form.end, R.string.wo_end, "wood_end", enabled, 128, true) { change { copy(end = it) } }
            }
        }
        Field(form.species, R.string.wo_species, "wood_species", enabled, 300) { change { copy(species = it) } }
        Field(form.quality, R.string.wo_quality, "wood_quality", enabled, 1000) { change { copy(quality = it) } }
        Field(form.origin, R.string.wo_origin, "wood_origin", enabled, 1000) { change { copy(origin = it) } }
        Field(form.notes, R.string.wo_notes, "wood_notes", enabled, 5000) { change { copy(notes = it) } }
        if (form.correction != null) {
            Text(stringResource(R.string.wo_correction_hint))
            Field(form.reason, R.string.wo_reason, "wood_reason", enabled, 5000) { change { copy(reason = it) } }
        }
    }
}

@Composable
private fun Field(value: String, label: Int, tag: String, enabled: Boolean, max: Int, numeric: Boolean = false, change: (String) -> Unit) {
    OutlinedTextField(value, { if (it.codePointCount(0, it.length) <= max) change(it) }, enabled = enabled,
        label = { Text(stringResource(label)) }, modifier = Modifier.fillMaxWidth().testTag(tag), shape = GsShape.md,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text))
}

@Composable
private fun WoodChoice(label: Int, selected: String?, options: List<Pair<String?, Int>>, enabled: Boolean, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val localizedOptions = options.map { (value, resource) -> value to stringResource(resource) }
    val labelText = stringResource(label)
    val selectedText = localizedOptions.firstOrNull { it.first == selected }?.second ?: stringResource(R.string.wo_no_method)
    Column {
        Text(labelText, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton({ expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .testTag(if (label == R.string.wo_method) "wood_method" else "wood_bark")
                .semantics { contentDescription = "$labelText : $selectedText" }) {
                Text(selectedText)
            }
            DropdownMenu(expanded, { expanded = false }) {
                localizedOptions.forEach { (value, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { expanded = false; onSelect(value) }) }
            }
        }
    }
}

@Composable
internal fun ObservationCard(saved: SavedWoodObservation, selected: Boolean, corrected: Boolean, enabled: Boolean, onCorrect: () -> Unit, onSelect: () -> Unit) {
    var expanded by remember(saved.record.observationId) { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    OutlinedCard(Modifier.fillMaxWidth().testTag("wood_observation_${saved.record.observationId}"), shape = GsShape.lg) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            val date = saved.payload.observedTime
            Text(if (date == null) stringResource(R.string.wo_date_unknown)
                else stringResource(R.string.wo_observed, WoodManualForm.DATE_FORMAT.format(date.instant.atZone(java.time.ZoneId.of(date.zoneId)))),
                style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.wo_recorded, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).format(Date.from(saved.record.observation.recordedAt))),
                style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.wo_revision, saved.record.observationRevision), style = MaterialTheme.typography.labelLarge)
            if (corrected) Text(stringResource(R.string.wo_superseded))
            else if (selected) Text(stringResource(R.string.wo_selected), color = MaterialTheme.colorScheme.primary)
            Calculation(saved.record.observation)
            saved.record.correctionReason?.let { Text(it) }
            TextButton({ expanded = !expanded }) { Text(stringResource(R.string.wo_details)) }
            if (expanded) {
                for ((label, value) in rawValues(saved.payload.measurement)) Text(stringResource(label) + " : " + (value ?: stringResource(R.string.wo_no_value)))
                saved.payload.declaredSpecies?.let { Text(stringResource(R.string.wo_species) + " : " + it) }
                saved.payload.declaredQuality?.let { Text(stringResource(R.string.wo_quality) + " : " + it) }
                saved.payload.declaredOrigin?.let { Text(stringResource(R.string.wo_origin) + " : " + it) }
                saved.payload.description?.let { Text(it) }
            }
            if (!corrected && enabled) {
                if (!selected) OutlinedButton(onSelect, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.wo_select)) }
                if (canEdit(saved.payload.measurement)) TextButton(onCorrect) { Text(stringResource(R.string.wo_correct)) }
                else Text(stringResource(R.string.wo_units_read_only))
            }
        }
    }
}

internal fun canEdit(measurement: RawWoodMeasurement): Boolean {
    fun RawWoodDimension?.compatible(unit: String) = this == null || this.unit == unit
    return when (measurement) {
        is RawWoodMeasurement.Pile -> listOf(measurement.length, measurement.width, measurement.meanHeight, measurement.pieceLength).all { it.compatible("m") } &&
            measurement.shapeCode in listOf(null, RawWoodMeasurement.Pile.RECTANGULAR_ENVELOPE)
        is RawWoodMeasurement.Log -> measurement.length.compatible("m") &&
            listOf(measurement.startDiameter, measurement.middleDiameter, measurement.endDiameter).all { it.compatible("cm") } &&
            measurement.methodCode in listOf(null, "huber", "smalian", "newton") && measurement.barkConventionCode in listOf(null, "OVER_BARK", "UNDER_BARK")
    }
}

internal fun rawValues(measurement: RawWoodMeasurement): List<Pair<Int, String?>> {
    fun RawWoodDimension?.display() = this?.let { listOfNotNull(it.rawText, it.unit).joinToString(" ") }
    return when (measurement) {
        is RawWoodMeasurement.Pile -> listOf(R.string.wo_length to measurement.length.display(), R.string.wo_depth to measurement.width.display(),
            R.string.wo_height to measurement.meanHeight.display(), R.string.wo_piece_length to measurement.pieceLength.display(), R.string.wo_protocol to measurement.protocolReference)
        is RawWoodMeasurement.Log -> listOf(R.string.wo_length to measurement.length.display(), R.string.wo_start to measurement.startDiameter.display(),
            R.string.wo_middle to measurement.middleDiameter.display(), R.string.wo_end to measurement.endDiameter.display(),
            R.string.wo_method to measurement.methodCode, R.string.wo_bark to measurement.barkConventionCode)
    }
}

@Composable
internal fun Calculation(raw: RawWoodObservation, compactPresentation: Boolean = false) {
    val locale = LocalConfiguration.current.locales[0]
    when (val calculation = remember(raw) { WoodObservationCalculator.calculate(raw) }) {
        is WoodObservationCalculation.Calculated -> {
            val label = stringResource(if (raw.measurement.kind == WoodObjectKind.PILE) {
                if (compactPresentation) R.string.retained_apparent_volume else R.string.wo_apparent
            } else R.string.wo_calculated)
            val volume = stringResource(R.string.wo_volume, NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 3 }.format(calculation.result.volumeM3))
            if (compactPresentation) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.bodySmall)
                        Text(volume, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("wood_calculated_value"))
                    }
                    RetainedHelp(label, stringResource(R.string.wo_no_payable), Modifier.testTag("wood_calculation_reserve_help"))
                }
                Text(stringResource(R.string.retained_calculation_reserve_short), style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("wood_calculation_essential_reserve"))
            } else {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(volume, style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.wo_no_payable), style = MaterialTheme.typography.bodySmall)
            }
        }
        is WoodObservationCalculation.Uncalculable -> {
            Text(stringResource(R.string.wo_no_calculation), style = MaterialTheme.typography.titleSmall)
            val fields = calculation.diagnostics.map { stringResource(fieldLabel(it.field)) }.distinct().joinToString(", ")
            Text(stringResource(R.string.wo_missing, fields), style = MaterialTheme.typography.bodyMedium)
            if (calculation.diagnostics.any { it.code == WoodDiagnosticCode.UNUSED_SECTION_PRESENT }) Text(stringResource(R.string.wo_unused))
        }
    }
}
private fun fieldLabel(field: WoodField) = when (field) {
    WoodField.LENGTH -> R.string.wo_length
    WoodField.WIDTH -> R.string.wo_depth
    WoodField.MEAN_HEIGHT -> R.string.wo_height
    WoodField.START_DIAMETER -> R.string.wo_start
    WoodField.MIDDLE_DIAMETER -> R.string.wo_middle
    WoodField.END_DIAMETER -> R.string.wo_end
    WoodField.SHAPE -> R.string.wo_shape
    WoodField.PROTOCOL -> R.string.wo_protocol
    WoodField.METHOD -> R.string.wo_method
    WoodField.BARK_CONVENTION -> R.string.wo_bark
    WoodField.SECTION -> R.string.wo_section
    WoodField.RESULT -> R.string.wo_result
}
