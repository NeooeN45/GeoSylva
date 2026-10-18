package com.forestry.counter.presentation.screens.field

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.forestry.counter.R
import com.forestry.counter.domain.field.*
import com.forestry.counter.domain.repository.FieldRepository

@Composable fun WorksitesScreen(repository:FieldRepository,onJournal:(FieldTarget)->Unit,onBack:()->Unit) {
    val factory=remember(repository) {object:ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T=WorksitesViewModel(repository) as T
    }}
    val vm:WorksitesViewModel=viewModel(factory=factory)
    WorksitesContent(vm,onJournal,onBack)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun WorksitesContent(vm:WorksitesViewModel,onJournal:(FieldTarget)->Unit,onBack:()->Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var archives by remember {mutableStateOf(false)}
    var confirmArchive by remember {mutableStateOf(false)}
    var discard by remember {mutableStateOf(false)}
    val back={if(!state.busy) {if(state.form!=null) discard=true else if(state.detail!=null) vm.close() else onBack()}}
    BackHandler(onBack=back)
    Scaffold(topBar={TopAppBar(title={Text(stringResource(R.string.site_title))},navigationIcon={IconButton(back,enabled=!state.busy) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack,stringResource(R.string.field_leave))
    }})},bottomBar={if(state.form!=null) Surface(shadowElevation=4.dp) {
        Button(vm::save,enabled=!state.busy,modifier=Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp).heightIn(min=56.dp).testTag("site_save")) {Text(stringResource(R.string.field_save))}
    }}) {padding ->
        Box(Modifier.fillMaxSize().padding(padding),contentAlignment=androidx.compose.ui.Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max=760.dp).fillMaxWidth().testTag("site_content"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                if(state.busy) item {LinearProgressIndicator(Modifier.fillMaxWidth())}
                state.error?.let {item {FieldErrorCard(it)}}
                if(!state.accessLost) {
                    val form=state.form;val detail=state.detail
                    if(form!=null) item {WorksiteEditor(form,state.parcels,!state.busy,vm::change)}
                    else if(detail!=null) {
                        item {Text(detail.form.name,style=MaterialTheme.typography.headlineMedium);Text(detail.form.notes)}
                        item {Text(stringResource(R.string.site_parcels),style=MaterialTheme.typography.titleMedium)}
                        items(detail.form.parcels) {Text("${it.label}\n${it.source} · ${it.reference}")}
                        item {Button({onJournal(FieldTarget(FieldTargetKind.WORKSITE,detail.id))},modifier=Modifier.fillMaxWidth()) {Text(stringResource(R.string.field_title))}}
                        item {Text(stringResource(R.string.site_wood),style=MaterialTheme.typography.titleMedium);Text(stringResource(R.string.site_link_help))}
                        items(state.linked,key={"${it.target.kind}-${it.target.id}"}) {choice ->
                            OutlinedButton({onJournal(choice.target)},modifier=Modifier.fillMaxWidth()) {Text(choice.label)}
                        }
                        if(!detail.archived) item {FieldChoice(R.string.site_link,null,state.choices.filter {it !in state.linked},{it.label},!state.busy,vm::link)}
                        if(!detail.archived) item {OutlinedButton(vm::edit,enabled=!state.busy,modifier=Modifier.fillMaxWidth()) {Text(stringResource(R.string.site_edit))}}
                        item {OutlinedButton({confirmArchive=true},enabled=!state.busy,modifier=Modifier.fillMaxWidth()) {Text(stringResource(if(detail.archived) R.string.site_restore else R.string.site_archive))}}
                    } else {
                        item {Text(stringResource(R.string.site_empty));Button(vm::create,enabled=!state.busy,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("site_create")) {Text(stringResource(R.string.site_new))}}
                        item {Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {Switch(archives,{archives=it});Text(stringResource(R.string.site_archived),Modifier.padding(start=12.dp))}}
                        items(state.sites.filter {it.archived==archives},key={it.id}) {site ->
                            Card(onClick={vm.open(site)},enabled=!state.busy,shape=MaterialTheme.shapes.large) {Column(Modifier.fillMaxWidth().padding(20.dp)) {
                                Text(site.form.name,style=MaterialTheme.typography.titleLarge)
                                Text(site.form.parcels.joinToString {it.label},style=MaterialTheme.typography.bodyMedium)
                            }}
                        }
                    }
                }
            }
        }
    }
    if(confirmArchive) FieldConfirmation(if(state.detail?.archived==true) R.string.site_restore else R.string.site_archive,
        R.string.site_archive_help,R.string.field_save,onConfirm={confirmArchive=false;vm.archive()},onDismiss={confirmArchive=false})
    if(discard) FieldConfirmation(R.string.field_discard,R.string.field_discard_body,R.string.field_discard,
        onConfirm={discard=false;vm.close()},onDismiss={discard=false})
}
@Composable private fun WorksiteEditor(form:WorksiteForm,parcels:List<WorksiteParcel>,enabled:Boolean,change:(WorksiteForm)->Unit) {
    var source by remember {mutableStateOf("")};var reference by remember {mutableStateOf("")};var label by remember {mutableStateOf("")};var version by remember {mutableStateOf("")}
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        FieldText(R.string.site_name,form.name,enabled,tag="site_name") {change(form.copy(name=it))}
        FieldText(R.string.field_notes,form.notes,enabled) {change(form.copy(notes=it))}
        Text(stringResource(R.string.site_parcels),style=MaterialTheme.typography.titleLarge)
        form.parcels.forEach {p ->
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Text("${p.label}\n${p.source} · ${p.reference}",Modifier.weight(1f))
                TextButton({change(form.copy(parcels=form.parcels-p))},enabled=enabled) {Text("×")}
            }
        }
        FieldChoice(R.string.site_local_parcel,null,parcels.filter {p->form.parcels.none {it.source==p.source && it.reference==p.reference}},{it.label},enabled) {change(form.copy(parcels=form.parcels+it))}
        FieldText(R.string.site_source,source,enabled) {source=it}
        FieldText(R.string.site_reference,reference,enabled) {reference=it}
        FieldText(R.string.site_label,label,enabled) {label=it}
        FieldText(R.string.site_version,version,enabled) {version=it}
        Text(stringResource(R.string.site_external_help),style=MaterialTheme.typography.bodySmall)
        OutlinedButton({
            change(form.copy(parcels=form.parcels+WorksiteParcel(source.trim(),reference.trim(),label.trim(),sourceVersion=version)))
            reference="";label="";version=""
        },enabled=enabled && source.isNotBlank() && reference.isNotBlank() && label.isNotBlank() && form.parcels.none {it.source==source.trim() && it.reference==reference.trim()},modifier=Modifier.fillMaxWidth()) {Text(stringResource(R.string.site_add_parcel))}
    }
}
