package com.forestry.counter.presentation.screens.field

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.forestry.counter.R
import com.forestry.counter.domain.field.*
import com.forestry.counter.domain.repository.*
import com.forestry.counter.domain.wood.*
import java.text.DateFormat
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDateTime
import java.util.Date
import java.util.UUID

@Composable
fun ReceiptsScreen(repository: FieldRepository, siteId: String, onJournal: (FieldTarget) -> Unit, onBack: () -> Unit, onOpenWood: (() -> Unit)? = null) {
    val factory = remember(repository, siteId) { object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ReceiptsViewModel(repository, siteId) as T
    } }
    val vm: ReceiptsViewModel = viewModel(key = "receipts-$siteId", factory = factory)
    ReceiptsContent(vm, onJournal, onBack, onOpenWood)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReceiptsContent(vm: ReceiptsViewModel, onJournal: (FieldTarget) -> Unit, onBack: () -> Unit, onOpenWood: (() -> Unit)? = null) {
    val state by vm.state.collectAsStateWithLifecycle()
    val listState=rememberLazyListState()
    LaunchedEffect(state.error) { if (state.error!=null) listState.scrollToItem(0) }
    var confirm by remember { mutableStateOf(false) }
    var cancelDialog by remember { mutableStateOf(false) }
    var cancelReason by remember { mutableStateOf("") }
    var history by remember(state.detail?.id) { mutableStateOf(false) }
    var leaveUnsaved by remember { mutableStateOf(false) }
    val back = {
        if (state.accessLost) onBack()
        else if (state.form != null && state.error != null && !state.draftSaved) leaveUnsaved = true
        else vm.leave(onBack)
    }
    BackHandler(enabled = !state.busy, onBack = back)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) vm.flush()
            if (event == Lifecycle.Event.ON_RESUME) vm.reload()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); vm.flush() }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Column {
            Text(stringResource(R.string.receipt_title))
            Text(state.siteTitle, style = MaterialTheme.typography.labelMedium)
        } }, navigationIcon = { IconButton(back, enabled = !state.busy) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.field_leave))
        } }) },
        bottomBar = {
            if (!state.accessLost && (state.form != null || state.retry || (state.detail?.status == ReceiptStatus.READY && state.writable))) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.retry) {
                            Button(vm::retry, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("receipt_retry")) { Text(stringResource(R.string.field_retry)) }
                            if (state.error in setOf(FieldError.DEPENDENCIES, FieldError.INVALID, FieldError.CONFLICT)) {
                                TextButton(vm::dismissRefusedOperation, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.receipt_return_document)) }
                            }
                        } else if (state.form != null) {
                            OutlinedButton(vm::saveDraft, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("receipt_save_draft")) { Text(stringResource(R.string.receipt_save_draft)) }
                            Button(vm::review, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("receipt_review")) { Text(stringResource(R.string.receipt_review)) }
                        } else {
                            Button({ confirm = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("receipt_validate")) { Text(stringResource(R.string.receipt_validate)) }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxWidth().testTag("receipt_content"), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (state.busy) item(key = "receipt_loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state.error?.let { item(key = "receipt_error") { ReceiptErrorCard(it) } }
                if (!state.accessLost) {
                    item(key = "receipt_local") { Text(stringResource(R.string.receipt_local_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    val form = state.form
                    val detail = state.detail
                    if (form != null) {
                        item(key = "receipt_draft") { Text(stringResource(if (state.draftSaved) R.string.field_draft else R.string.field_draft_pending), style = MaterialTheme.typography.labelLarge) }
                        item(key = "receipt_editor") { ReceiptHeaderEditor(form, !state.busy && !state.retry, vm::change, vm::setNow) }
                        item(key = "receipt_wood") {
                            Text(stringResource(R.string.receipt_choose_wood), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.receipt_choose_help), style = MaterialTheme.typography.bodyMedium)
                            val candidates = state.choices.filter { !it.alreadyHasStock && form.lines.none { line -> line.objectId == it.objectId } }
                            FieldChoice(R.string.receipt_add_line, null, candidates, { choiceLabel(it) }, !state.busy && !state.retry, vm::addWood)
                            if (candidates.isEmpty()) Text(stringResource(R.string.receipt_no_candidates), style = MaterialTheme.typography.bodySmall)
                        }
                        items(form.lines, key = { it.id }) { line ->
                            ReceiptLineEditor(line, state.choices, state.photos[line.objectId].orEmpty(), !state.busy && !state.retry,
                                { change -> vm.line(line.id, change) }, { vm.removeLine(line.id) })
                        }
                    } else if (detail != null) {
                        item(key = "receipt_header") { ReceiptHeading(detail) }
                        item(key = "receipt_metadata") {
                            Text(stringResource(R.string.receipt_date_summary, detail.form.observed, detail.form.zone), style = MaterialTheme.typography.bodyMedium)
                            if (detail.form.documentReference.isNotBlank()) Text(stringResource(R.string.receipt_document_reference, detail.form.documentReference))
                            if (detail.form.notes.isNotBlank()) Text(detail.form.notes)
                        }
                        if (detail.status == ReceiptStatus.READY) item(key = "receipt_effect") { Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
                            Text(stringResource(R.string.receipt_effect_help), Modifier.padding(20.dp))
                        } }
                        items(detail.form.lines, key = { it.id }) { line ->
                            val source = detail.sources.firstOrNull { it.lineId == line.id }
                            val choice = state.choices.firstOrNull { it.objectId == line.objectId && it.observationId == line.observationId }
                            ReceiptLineSummary(line, source?.objectName ?: choice?.name.orEmpty(), source?.observationPayloadJson ?: choice?.observationPayloadJson,
                                source?.observationSha256 ?: choice?.observationSha256,
                                onJournal = { onJournal(FieldTarget(FieldTargetKind.WOOD_OBJECT, line.objectId)) })
                        }
                        if (state.writable && !state.retry && detail.status in setOf(ReceiptStatus.DRAFT, ReceiptStatus.READY)) item(key = "receipt_edit") {
                            OutlinedButton(vm::edit, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("receipt_edit")) { Text(stringResource(R.string.receipt_edit)) }
                        }
                        if (detail.status == ReceiptStatus.CANCELLED) item(key = "receipt_cancellation") {
                            Text(stringResource(R.string.receipt_cancellation_reason, detail.cancellationReason), style = MaterialTheme.typography.bodyMedium)
                        }
                        item(key = "receipt_history") { TextButton({ history = !history }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.receipt_history, state.history.size)) } }
                        if (history) items(state.history, key = { "history-${it.revision}" }) { old ->
                            OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(R.string.receipt_version, old.revision, receiptStatusLabel(old.status)), style = MaterialTheme.typography.titleSmall)
                                Text(receiptRecordedLabel(old), style = MaterialTheme.typography.bodySmall)
                                Text(old.form.title.ifBlank { stringResource(R.string.receipt_untitled) })
                                old.form.lines.forEach { Text("${it.quantity} ${unitLabel(it.unit)} · ${it.ledgerConvention()}") }
                                if (old.cancellationReason.isNotBlank()) Text(old.cancellationReason)
                            } }
                        }
                        if (state.writable && !state.retry && detail.status == ReceiptStatus.VALIDATED) item(key = "receipt_cancel") {
                            OutlinedButton({ cancelReason = ""; cancelDialog = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.receipt_cancel)) }
                        }
                    } else {
                        item(key = "receipt_create") {
                            Text(stringResource(R.string.receipt_list_heading), style = MaterialTheme.typography.headlineSmall)
                            Text(stringResource(R.string.receipt_list_help))
                            if (state.writable && !state.retry) {
                                val eligible = state.choices.any { !it.alreadyHasStock }
                                Button(vm::create, enabled = !state.busy && eligible, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 56.dp).testTag("receipt_create")) { Text(stringResource(R.string.receipt_new)) }
                                if (!eligible && !state.busy) {
                                    Text(stringResource(R.string.receipt_no_candidates), style = MaterialTheme.typography.bodyMedium)
                                    if (onOpenWood != null) OutlinedButton(onOpenWood, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.site_manage_wood)) }
                                }
                            }
                        }
                        if (state.receipts.isEmpty()) item(key = "receipt_empty") { Text(stringResource(R.string.receipt_empty)) }
                        items(state.receipts, key = { it.id }) { receipt ->
                            Card(onClick = { vm.open(receipt.id) }, enabled = !state.busy && !state.retry, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ReceiptStatusBadge(receipt.status)
                                    Text(receipt.form.title.ifBlank { stringResource(R.string.receipt_untitled) }, style = MaterialTheme.typography.titleLarge)
                                    Text(stringResource(R.string.receipt_line_count, receipt.form.lines.size, receipt.revision), style = MaterialTheme.typography.bodyMedium)
                                    if (receipt.form.observed.isNotBlank()) Text(receipt.form.observed, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, icon = { Icon(Icons.Default.CheckCircle, null) },
        title = { Text(stringResource(R.string.receipt_validate)) }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.receipt_confirm_help))
            state.detail?.form?.lines.orEmpty().forEach { line ->
                Text("+ ${line.quantity} ${unitLabel(line.unit)} · ${line.ledgerConvention()}", style = MaterialTheme.typography.titleMedium)
            }
        } }, confirmButton = { TextButton({ confirm = false; vm.validate() }, modifier = Modifier.testTag("receipt_confirm")) { Text(stringResource(R.string.receipt_confirm)) } },
        dismissButton = { TextButton({ confirm = false }) { Text(stringResource(R.string.field_cancel)) } })
    if (cancelDialog) AlertDialog(onDismissRequest = { cancelDialog = false }, title = { Text(stringResource(R.string.receipt_cancel)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.receipt_cancel_help))
            FieldText(R.string.field_reason, cancelReason) { if (it.length <= 500) cancelReason = it }
            Text("${cancelReason.length}/500", style = MaterialTheme.typography.bodySmall)
        } }, confirmButton = { TextButton({ cancelDialog = false; vm.cancel(cancelReason) }, enabled = cancelReason.isNotBlank()) { Text(stringResource(R.string.receipt_cancel_confirm)) } },
        dismissButton = { TextButton({ cancelDialog = false }) { Text(stringResource(R.string.field_cancel)) } })
    if (leaveUnsaved) FieldConfirmation(R.string.field_leave_unsaved, R.string.field_leave_unsaved_body, R.string.field_leave,
        onConfirm = { leaveUnsaved = false; onBack() }, onDismiss = { leaveUnsaved = false })
}

@Composable private fun ReceiptHeaderEditor(form: ReceiptForm, enabled: Boolean, change: (ReceiptForm.() -> ReceiptForm) -> Unit, setNow: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.receipt_draft_help), style = MaterialTheme.typography.bodyMedium)
        FieldText(R.string.receipt_name, form.title, enabled, tag = "receipt_name") { if (it.length <= 200) change { copy(title = it) } }
        FieldText(R.string.field_date, form.observed, enabled, tag = "receipt_date") { if (it.length <= 32) change { copy(observed = it) } }
        TextButton(setNow, enabled = enabled) { Text(stringResource(R.string.field_now)) }
        FieldText(R.string.field_zone, form.zone, enabled) { if (it.length <= 100) change { copy(zone = it) } }
        FieldText(R.string.field_document, form.documentReference, enabled) { if (it.length <= 500) change { copy(documentReference = it) } }
        FieldText(R.string.field_notes, form.notes, enabled) { if (it.length <= 5000) change { copy(notes = it) } }
    }
}

@Composable private fun ReceiptLineEditor(line: ReceiptLine, choices: List<ReceiptWoodChoice>, photos: List<String>, enabled: Boolean,
    change: (ReceiptLine.() -> ReceiptLine) -> Unit, remove: () -> Unit,
) {
    val choice = choices.firstOrNull { it.objectId == line.objectId && it.observationId == line.observationId }
    OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(choice?.name ?: line.objectId, style = MaterialTheme.typography.titleLarge)
            FieldChoice(R.string.receipt_source_observation, choice, choices.filter { it.objectId == line.objectId }, { choiceLabel(it) }, enabled) {
                change { copy(observationId = it.observationId, observationRevision = it.observationRevision) }
            }
            ReceiptSource(line, choice?.observationPayloadJson, choice?.observationSha256)
            FieldText(R.string.field_quantity, line.quantity, enabled, decimal = true, tag = "receipt_quantity_${line.id}") { if (it.length <= 64) change { copy(quantity = it) } }
            FieldChoice(R.string.field_unit, line.unit, FieldQuantityUnit.entries, { unitLabel(it) }, enabled) { change { copy(unit = it) } }
            FieldText(R.string.field_convention, line.convention, enabled) { if (it.length <= 350) change { copy(convention = it) } }
            FieldText(R.string.receipt_convention_version, line.conventionVersion, enabled) { if (it.length <= 100) change { copy(conventionVersion = it) } }
            FieldText(R.string.receipt_difference_reason, line.differenceReason, enabled) { if (it.length <= 2000) change { copy(differenceReason = it) } }
            if (photos.isNotEmpty()) {
                Text(stringResource(R.string.receipt_photo_help), style = MaterialTheme.typography.bodySmall)
                photos.forEachIndexed { index, photo ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(photo in line.photoIds, onCheckedChange = { checked -> change { copy(photoIds = if (checked) (photoIds + photo).distinct().take(6) else photoIds - photo) } }, enabled = enabled && (photo in line.photoIds || line.photoIds.size < 6))
                        Text(stringResource(R.string.receipt_photo_number, index + 1, photo.take(8)))
                    }
                }
            }
            TextButton(remove, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.receipt_remove_line)) }
        }
    }
}

@Composable private fun ReceiptLineSummary(line: ReceiptLine, name: String, payload: String?, hash: String?, onJournal: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(name.ifBlank { line.objectId }, style = MaterialTheme.typography.titleLarge)
            Text("${line.quantity} ${unitLabel(line.unit)}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text(line.ledgerConvention(), style = MaterialTheme.typography.bodyMedium)
            ReceiptSource(line, payload, hash)
            if (line.differenceReason.isNotBlank()) Text(stringResource(R.string.receipt_difference_summary, line.differenceReason))
            if (line.photoIds.isNotEmpty()) Text(stringResource(R.string.receipt_photo_count, line.photoIds.size))
            OutlinedButton(onJournal, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.receipt_open_movements)) }
        }
    }
}

@Composable private fun ReceiptSource(line: ReceiptLine, payloadJson: String?, hash: String?) {
    val raw = remember(line.observationId, payloadJson, hash) { runCatching {
        val payload = WoodContractCodec.readPayload(checkNotNull(payloadJson).toByteArray(), checkNotNull(hash))
        RawWoodObservation(UUID.fromString(line.observationId), UUID.fromString(line.objectId), Instant.EPOCH, payload.measurement, payload.observedTime)
    }.getOrNull() }
    val calculation = remember(raw) { raw?.let(WoodObservationCalculator::calculate) }
    val locale = LocalConfiguration.current.locales[0]
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.receipt_source_revision, line.observationRevision, line.observationId.take(8)), style = MaterialTheme.typography.labelLarge)
            if (calculation is WoodObservationCalculation.Calculated) {
                val number = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 4 }.format(calculation.result.volumeM3)
                Text(stringResource(if (raw?.measurement is RawWoodMeasurement.Pile) R.string.receipt_source_apparent else R.string.receipt_source_solid, number))
                val method = (raw?.measurement as? RawWoodMeasurement.Log)?.methodCode
                when (method) {
                    "huber" -> Text(stringResource(R.string.wo_huber))
                    "smalian" -> Text(stringResource(R.string.wo_smalian))
                    "newton" -> Text(stringResource(R.string.wo_newton))
                }
            } else Text(stringResource(R.string.receipt_source_not_calculable))
            Text(stringResource(R.string.receipt_source_help), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun ReceiptHeading(receipt: SavedReceipt) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ReceiptStatusBadge(receipt.status)
        Text(receipt.form.title.ifBlank { stringResource(R.string.receipt_untitled) }, style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.receipt_line_count, receipt.form.lines.size, receipt.revision), style = MaterialTheme.typography.bodyMedium)
        Text(receiptRecordedLabel(receipt), style = MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun ReceiptStatusBadge(status: ReceiptStatus) {
    Surface(color = if (status == ReceiptStatus.VALIDATED) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.extraLarge) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (status == ReceiptStatus.VALIDATED) Icons.Default.CheckCircle else Icons.Default.ReceiptLong, null, Modifier.size(18.dp))
            Text(receiptStatusLabel(status), style = MaterialTheme.typography.labelLarge)
        }
    }
}
@Composable private fun receiptStatusLabel(status: ReceiptStatus) = stringResource(when (status) {
    ReceiptStatus.DRAFT -> R.string.receipt_status_draft
    ReceiptStatus.READY -> R.string.receipt_status_ready
    ReceiptStatus.VALIDATED -> R.string.receipt_status_validated
    ReceiptStatus.CANCELLED -> R.string.receipt_status_cancelled
})
@Composable private fun receiptRecordedLabel(receipt: SavedReceipt): String {
    val locale = LocalConfiguration.current.locales[0]
    val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).format(Date(receipt.recordedAt))
    return if (receipt.recordedBy == "anonymous") stringResource(R.string.receipt_recorded_local, date)
    else stringResource(R.string.receipt_recorded_account, date, receipt.recordedBy)
}
@Composable private fun choiceLabel(choice: ReceiptWoodChoice) = stringResource(R.string.receipt_choice_label, choice.name, choice.observationRevision, choice.observationId.take(8))
@Composable internal fun ReceiptErrorCard(error: FieldError) {
    val message = when (error) {
        FieldError.DEPENDENCIES -> R.string.receipt_error_dependencies
        FieldError.UNKNOWN_RESULT -> R.string.receipt_error_unknown
        FieldError.INVALID -> R.string.receipt_error_invalid
        else -> null
    }
    if (message == null) FieldErrorCard(error) else Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
        Text(stringResource(message), Modifier.padding(16.dp))
    }
}
