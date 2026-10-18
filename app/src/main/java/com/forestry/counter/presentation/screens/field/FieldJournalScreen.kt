package com.forestry.counter.presentation.screens.field

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.forestry.counter.R
import com.forestry.counter.domain.field.*
import com.forestry.counter.domain.repository.*
import java.time.LocalDateTime
import com.forestry.counter.presentation.screens.wood.ObservationCard

@Composable fun FieldJournalScreen(repository: FieldRepository, target: FieldTarget, onOpenWood: ((String,String?)->Unit)? = null, onBack: ()->Unit) {
    val factory=remember(repository,target) { object: ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T=FieldJournalViewModel(repository,target) as T
    } }
    val vm: FieldJournalViewModel=viewModel(key="field-${target.kind}-${target.id}",factory=factory)
    FieldJournalContent(vm,onBack,onOpenWood)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun FieldJournalContent(vm: FieldJournalViewModel, onBack: ()->Unit, onOpenWood: ((String,String?)->Unit)? = null) {
    val state by vm.state.collectAsStateWithLifecycle()
    var discard by remember { mutableStateOf(false) }
    var leaveUnsaved by remember { mutableStateOf(false) }
    val back={
        if (state.form != null && state.error in setOf(FieldError.CONFLICT,FieldError.UNAVAILABLE,FieldError.IO)) leaveUnsaved=true
        else vm.leave(onBack)
    }
    BackHandler { if (state.accessLost) onBack() else back() }
    val owner=LocalLifecycleOwner.current
    DisposableEffect(owner,vm) {
        val observer=LifecycleEventObserver { _,event -> if (event==Lifecycle.Event.ON_STOP) vm.flush() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); vm.flush() }
    }
    Scaffold(topBar={ TopAppBar(title={ Column {
        Text(stringResource(R.string.field_title))
        Text(state.title,style=MaterialTheme.typography.labelMedium)
    } },navigationIcon={ IconButton({ if (state.accessLost) onBack() else back() },enabled=!state.busy) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack,stringResource(R.string.field_leave))
    } }) },bottomBar={ if (state.form != null && !state.accessLost && state.writable) Surface(shadowElevation=4.dp) {
        Button(vm::save,enabled=!state.busy,modifier=Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp).heightIn(min=56.dp).testTag("field_save")) {
            Text(stringResource(if (state.retry) R.string.field_retry else R.string.field_save))
        }
    } }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding),contentAlignment=androidx.compose.ui.Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max=760.dp).fillMaxWidth().testTag("field_content"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (!state.busy && !state.writable && !state.accessLost) item {Text(stringResource(R.string.field_error_unavailable))}
                state.error?.let { item { FieldErrorCard(it) } }
                item { Text(stringResource(R.string.field_local),style=MaterialTheme.typography.bodySmall) }
                val form=state.form
                if (!state.accessLost && form != null) {
                    item { Text(stringResource(if (state.draftSaved) R.string.field_draft else R.string.field_draft_pending),style=MaterialTheme.typography.labelMedium) }
                    item { FieldEntryEditor(form,vm.target,!state.busy && !state.retry && state.writable,vm::change) }
                    item { FieldPhotos(vm,form.photoIds,!state.busy && !state.retry && state.writable) }
                    if (!state.retry) item { TextButton({discard=true},enabled=!state.busy) { Text(stringResource(R.string.field_discard)) } }
                } else if (!state.accessLost) {
                    if (state.writable) item { Button({vm.start()},enabled=!state.busy,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("field_add")) { Text(stringResource(R.string.field_add)) } }
                    if (onOpenWood != null && vm.target.kind in setOf(FieldTargetKind.WOOD_OBJECT,FieldTargetKind.WOOD_DEPOT)) item {
                        OutlinedButton({
                            val wood=state.measurements?.item
                            if (wood != null) onOpenWood(wood.depotId.toString(),wood.id.toString())
                            else if (vm.target.kind==FieldTargetKind.WOOD_DEPOT) onOpenWood(vm.target.id,null)
                        }, enabled=vm.target.kind==FieldTargetKind.WOOD_DEPOT || state.measurements!=null, modifier=Modifier.fillMaxWidth()) {Text(stringResource(R.string.field_open_wood))}
                    }
                    if (vm.target.kind==FieldTargetKind.WOOD_OBJECT) item { StockHelp() }
                    if (state.entries.isEmpty()) item { Text(stringResource(R.string.field_no_entries)) }
                    val active=FieldBalance.active(state.entries).map { it.id }.toSet()
                    val timeline=state.entries.map { FieldTimelineRow(note=it) } + state.measurements?.observations.orEmpty().map { FieldTimelineRow(measurement=it) }
                    items(timeline.sortedByDescending { it.time },key={it.key}) { row ->
                        row.note?.let { FieldEntryCard(it,it.id in active,state.entries,vm,state.writable) }
                        row.measurement?.let { m -> ObservationCard(m,
                            state.measurements?.history?.currentStateObservation?.observationId == m.record.observationId,
                            state.measurements?.observations?.any { it.record.correctionOfObservationId == m.record.observationId } == true,
                            false, {}, {}) }
                    }
                }
            }
        }
    }
    if (leaveUnsaved) FieldConfirmation(R.string.field_leave_unsaved,R.string.field_leave_unsaved_body,R.string.field_leave,
        onConfirm={leaveUnsaved=false;onBack()},onDismiss={leaveUnsaved=false})
    if (discard) FieldConfirmation(R.string.field_discard,R.string.field_discard_body,R.string.field_discard,
        onConfirm={discard=false;vm.discard()},onDismiss={discard=false})
}
@Composable internal fun FieldErrorCard(error: FieldError) {
    Surface(color=MaterialTheme.colorScheme.errorContainer,shape=MaterialTheme.shapes.medium) {
        Text(stringResource(when(error) {
            FieldError.INVALID -> R.string.field_error_invalid
            FieldError.CONFLICT -> R.string.field_error_conflict
            FieldError.ACCESS_CHANGED -> R.string.field_error_access
            FieldError.UNAVAILABLE -> R.string.field_error_unavailable
            FieldError.CORRUPT -> R.string.wo_error_data
            else -> R.string.field_error_io
        }),Modifier.padding(16.dp))
    }
}
@Composable internal fun FieldText(label: Int, value: String, enabled: Boolean=true, decimal: Boolean=false, tag: String="", onChange: (String)->Unit) {
    OutlinedTextField(value,onChange,enabled=enabled,label={Text(stringResource(label))},modifier=Modifier.fillMaxWidth().testTag(tag),
        keyboardOptions=KeyboardOptions(keyboardType=if(decimal) KeyboardType.Decimal else KeyboardType.Text),shape=MaterialTheme.shapes.medium)
}
@Composable internal fun <T> FieldChoice(label: Int, selected: T?, values: List<T>, title: @Composable (T)->String, enabled: Boolean=true, onChange: (T)->Unit) {
    var open by remember {mutableStateOf(false)}
    val labels=values.map { title(it) }
    Column {
        Text(stringResource(label),style=MaterialTheme.typography.labelLarge)
        OutlinedButton({open=true},enabled=enabled && values.isNotEmpty(),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(values.indexOf(selected).takeIf {it>=0}?.let {labels[it]} ?: "—")}
        DropdownMenu(open,{open=false},modifier=Modifier.heightIn(max=360.dp)) { values.forEachIndexed {index,value ->
            DropdownMenuItem(text={Text(labels[index])},onClick={open=false;onChange(value)})
        } }
    }
}
@Composable internal fun kindLabel(kind: FieldEntryKind)=stringResource(when(kind) {
    FieldEntryKind.NOTE->R.string.field_note; FieldEntryKind.ACCESS->R.string.field_access; FieldEntryKind.BEFORE->R.string.field_before
    FieldEntryKind.AFTER->R.string.field_after; FieldEntryKind.INCIDENT->R.string.field_incident; FieldEntryKind.ACTIVITY->R.string.field_activity
    FieldEntryKind.QUALITY->R.string.field_quality; FieldEntryKind.INITIAL_STOCK->R.string.field_initial_stock
    FieldEntryKind.INCOMING->R.string.field_incoming; FieldEntryKind.REMOVAL->R.string.field_removal; FieldEntryKind.REMAINING->R.string.field_remaining
})
@Composable internal fun unitLabel(unit: FieldQuantityUnit)=stringResource(when(unit) {
    FieldQuantityUnit.SOLID_M3->R.string.field_solid_m3;FieldQuantityUnit.APPARENT_M3->R.string.field_apparent_m3
    FieldQuantityUnit.TONNE->R.string.field_tonne;FieldQuantityUnit.STERE->R.string.field_stere;FieldQuantityUnit.PIECE->R.string.field_piece
})
@Composable private fun activityLabel(type: FieldActivity)=stringResource(when(type) {
    FieldActivity.PRODUCTION->R.string.field_production; FieldActivity.TRAVEL->R.string.field_travel
    FieldActivity.WAITING->R.string.field_waiting;FieldActivity.MAINTENANCE->R.string.field_maintenance;FieldActivity.SURVEY->R.string.field_survey
})
@Composable private fun remainderLabel(type: FieldRemainder)=stringResource(when(type) {
    FieldRemainder.PARTIAL->R.string.field_partial;FieldRemainder.BOTTOM->R.string.field_bottom
    FieldRemainder.EMPTY->R.string.field_empty;FieldRemainder.UNKNOWN->R.string.field_unknown
})
@Composable private fun FieldEntryEditor(form: FieldEntryForm,target: FieldTarget,enabled: Boolean,change: (FieldEntryForm.()->FieldEntryForm)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        val kinds=FieldEntryKind.entries.filter { target.kind==FieldTargetKind.WOOD_OBJECT || it !in FieldEntryForm.quantityKinds && it!=FieldEntryKind.QUALITY }
        FieldChoice(R.string.field_type,form.kind,kinds,{kindLabel(it)},enabled && form.correctionOf==null) { kind ->
            change { FieldEntryForm(kind=kind,title=title,notes=notes,observed=observed,zone=zone,latitude=latitude,longitude=longitude,photoIds=photoIds) }
        }
        FieldText(R.string.field_name,form.title,enabled,tag="field_name") {change {copy(title=it)}}
        FieldText(R.string.field_date,form.observed,enabled,tag="field_date") {change {copy(observed=it)}}
        TextButton({change {copy(observed=LocalDateTime.now().format(FieldEntryForm.dateFormat))}},enabled=enabled) {Text(stringResource(R.string.field_now))}
        FieldText(R.string.field_zone,form.zone,enabled) {change {copy(zone=it)}}
        when(form.kind) {
            FieldEntryKind.ACCESS -> {
                FieldText(R.string.field_vehicle,form.vehicle,enabled) {change {copy(vehicle=it)}}
                FieldText(R.string.field_restriction,form.restriction,enabled) {change {copy(restriction=it)}}
            }
            FieldEntryKind.ACTIVITY -> {
                FieldChoice(R.string.field_activity,form.activity,FieldActivity.entries,{activityLabel(it)},enabled) {change {copy(activity=it)}}
                FieldText(R.string.field_minutes,form.durationMinutes,enabled,true) {change {copy(durationMinutes=it)}}
                FieldText(R.string.field_equipment,form.equipment,enabled) {change {copy(equipment=it)}}
            }
            FieldEntryKind.QUALITY -> {
                FieldText(R.string.field_product,form.product,enabled) {change {copy(product=it)}}
                FieldText(R.string.field_defects,form.defects,enabled) {change {copy(defects=it)}}
            }
            else -> Unit
        }
        if (form.kind in FieldEntryForm.quantityKinds) {
            StockHelp()
            if (form.kind==FieldEntryKind.REMAINING) FieldChoice(R.string.field_remainder,form.remainder,FieldRemainder.entries,{remainderLabel(it)},enabled) {
                change {copy(remainder=it,quantity=when(it) {FieldRemainder.UNKNOWN->"";FieldRemainder.EMPTY->"0";else->quantity})}
            }
            if (form.remainder!=FieldRemainder.UNKNOWN) {
                FieldText(R.string.field_quantity,form.quantity,enabled,true,tag="field_quantity") {change {copy(quantity=it)}}
                FieldChoice(R.string.field_unit,form.unit,FieldQuantityUnit.entries,{unitLabel(it)},enabled) {change {copy(unit=it)}}
                FieldText(R.string.field_convention,form.convention,enabled,tag="field_convention") {change {copy(convention=it)}}
            }
            FieldText(R.string.field_counterparty,form.counterparty,enabled) {change {copy(counterparty=it)}}
            FieldText(R.string.field_destination,form.destination,enabled) {change {copy(destination=it)}}
            FieldText(R.string.field_document,form.documentReference,enabled) {change {copy(documentReference=it)}}
            FieldText(R.string.field_passage,form.passageReference,enabled) {change {copy(passageReference=it)}}
        }
        FieldText(R.string.field_notes,form.notes,enabled,tag="field_notes") {change {copy(notes=it)}}
        Text(stringResource(R.string.field_location_manual),style=MaterialTheme.typography.labelMedium)
        FieldText(R.string.field_lat,form.latitude,enabled) {change {copy(latitude=it,locationSource="MANUAL",accuracyM="")}}
        FieldText(R.string.field_lon,form.longitude,enabled) {change {copy(longitude=it,locationSource="MANUAL",accuracyM="")}}
        if(form.correctionOf!=null) FieldText(R.string.field_reason,form.correctionReason,enabled) {change {copy(correctionReason=it)}}
    }
}
@Composable private fun FieldEntryCard(entry: FieldEntry,active: Boolean,all: List<FieldEntry>,vm: FieldJournalViewModel,writable: Boolean) {
    Card(shape=MaterialTheme.shapes.large) { Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        val f=entry.form
        Text(kindLabel(f.kind),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
        Text(f.title,style=MaterialTheme.typography.titleLarge)
        if(!active) Text(stringResource(R.string.field_replaced))
        if(f.observed.isNotBlank()) Text("${f.observed} · ${f.zone}")
        val locale=androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
        Text(stringResource(R.string.wo_recorded,java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT,locale).format(java.util.Date(entry.recordedAt))),style=MaterialTheme.typography.bodySmall)
        listOf(f.notes,f.vehicle,f.restriction,f.equipment,f.product,f.defects,f.counterparty,f.destination,f.documentReference,f.passageReference).filter {it.isNotBlank()}.forEach {Text(it)}
        if(f.latitude.isNotBlank()) Text("${f.latitude}, ${f.longitude}")
        f.activity?.let {Text("${activityLabel(it)} · ${f.durationMinutes} min")}
        f.remainder?.let {Text(remainderLabel(it))}
        if(f.quantity.isNotBlank() && f.unit!=null) {
            Text("${f.quantity} ${unitLabel(f.unit)} · ${f.convention}")
            if(active) {
                val balance=FieldBalance.at(all,checkNotNull(f.observedInstant()),f.unit,f.convention)
                val expected=balance.expected
                if(expected!=null) Text(stringResource(R.string.field_balance,"${fieldNumber(expected)} ${unitLabel(f.unit)}"))
                else Text(stringResource(R.string.field_balance_unavailable))
                balance.difference?.let {Text(stringResource(R.string.field_difference,"${fieldNumber(it)} ${unitLabel(f.unit)}"),color=MaterialTheme.colorScheme.tertiary)}
            }
        }
        if(f.correctionReason.isNotBlank()) Text(f.correctionReason)
        if(f.photoIds.isNotEmpty()) FieldPhotos(vm,f.photoIds,false)
        if(active && writable) TextButton({vm.start(entry)}) {Text(stringResource(R.string.field_correction))}
    } }
}

private data class FieldTimelineRow(val note: FieldEntry?=null,val measurement: SavedWoodObservation?=null) {
    val key: String get()=note?.let { "note-${it.id}" } ?: "measure-${measurement?.record?.observationId}"
    val time: Long get()=note?.let {it.form.observedInstant() ?: it.recordedAt} ?: measurement?.let {it.payload.observedTime?.instant?.toEpochMilli() ?: it.record.observation.recordedAt.toEpochMilli()} ?: 0
}

@Composable private fun fieldNumber(value: java.math.BigDecimal): String {
    val locale=androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    return java.text.NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits=64 }.format(value)
}
@Composable private fun StockHelp() {
    var expanded by remember {mutableStateOf(false)}
    Column {
        Text(stringResource(R.string.field_stock_summary),style=MaterialTheme.typography.bodySmall)
        TextButton({expanded=!expanded}) {Text(stringResource(R.string.field_stock_explain))}
        if(expanded) Text(stringResource(R.string.field_stock_help),style=MaterialTheme.typography.bodyMedium)
    }
}
@Composable internal fun FieldConfirmation(title: Int,body: Int,confirm: Int,onConfirm:()->Unit,onDismiss:()->Unit) {
    val titleText=stringResource(title);val bodyText=stringResource(body)
    val confirmText=stringResource(confirm);val cancelText=stringResource(R.string.field_cancel)
    AlertDialog(onDismissRequest=onDismiss,title={Text(titleText)},text={Text(bodyText)},
        confirmButton={TextButton(onConfirm) {Text(confirmText)}},dismissButton={TextButton(onDismiss) {Text(cancelText)}})
}
