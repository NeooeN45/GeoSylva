package com.forestry.counter.presentation.screens.field

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.forestry.counter.R
import com.forestry.counter.domain.field.*
import com.forestry.counter.domain.repository.FieldRepository
import com.forestry.counter.domain.repository.FieldError

@Composable
fun WorksitesScreen(repository: FieldRepository, onJournal: (FieldTarget) -> Unit, onBack: () -> Unit,
    onReceipts: ((String) -> Unit)? = null, onExport: ((String) -> Unit)? = null, onManageWood: (() -> Unit)? = null,
) {
    val factory = remember(repository) { object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = WorksitesViewModel(repository) as T
    } }
    val vm: WorksitesViewModel = viewModel(factory = factory)
    WorksitesContent(vm, onJournal, onBack, onReceipts, onExport, onManageWood)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorksitesContent(vm: WorksitesViewModel, onJournal: (FieldTarget) -> Unit, onBack: () -> Unit,
    onReceipts: ((String) -> Unit)? = null, onExport: ((String) -> Unit)? = null, onManageWood: (() -> Unit)? = null,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nameMissing = state.error == FieldError.INVALID && state.form?.name?.isBlank() == true
    val listState = rememberLazyListState()
    val pageKey = when {
        state.form != null -> "editor-${state.detail?.id ?: "new"}"
        state.detail != null -> "detail-${state.detail?.id}"
        else -> "list"
    }
    // A saved worksite opens at its heading. Typing or clearing an error must never scroll.
    LaunchedEffect(pageKey) { listState.scrollToItem(0) }
    var archives by remember { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val back = { if (!state.busy) { if (state.form != null) discard = true else if (state.detail != null) vm.close() else onBack() } }
    BackHandler(onBack = back)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, vm) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.reload() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.site_title)) }, navigationIcon = { IconButton(back, enabled = !state.busy) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.field_leave))
    } }) }, bottomBar = { if (state.form != null) Surface(shadowElevation = 4.dp) {
        Button(vm::save, enabled = !state.busy, modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp).heightIn(min = 56.dp).testTag("site_save")) { Text(stringResource(R.string.field_save)) }
    } }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxWidth().testTag("site_content"), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (state.busy) item(key = "site_loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state.error?.takeUnless { nameMissing }?.let { item(key = "site_error") { FieldErrorCard(it) } }
                if (!state.accessLost) {
                    val form = state.form; val detail = state.detail
                    if (form != null) item(key = "site_editor") { WorksiteEditor(form, state.parcels, !state.busy, nameMissing, vm::change) }
                    else if (detail != null) {
                        item(key = "site_detail_header") {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraLarge) {
                                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(stringResource(if (detail.archived) R.string.site_archive_badge else R.string.site_active_badge), style = MaterialTheme.typography.labelLarge)
                                    Text(detail.form.name, style = MaterialTheme.typography.headlineMedium)
                                    if (detail.form.notes.isNotBlank()) Text(detail.form.notes, style = MaterialTheme.typography.bodyMedium)
                                    Text(stringResource(R.string.site_parcel_count, detail.form.parcels.size))
                                    Text(stringResource(R.string.site_linked_count, state.linked.count { it.target.kind == FieldTargetKind.WOOD_DEPOT }, state.linked.count { it.target.kind == FieldTargetKind.WOOD_OBJECT }))
                                }
                            }
                        }
                        if (onReceipts != null) item(key = "site_receipts") { WorksiteAction(Icons.Default.ReceiptLong, R.string.receipt_title, R.string.site_section_receipts_help,
                            !state.busy, "site_receipts") { onReceipts(detail.id) } }
                        item(key = "site_wood") {
                            Text(stringResource(R.string.site_section_wood), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.site_link_help), style = MaterialTheme.typography.bodyMedium)
                            if (state.linked.isEmpty()) Text(stringResource(R.string.site_empty_wood), Modifier.padding(top = 8.dp))
                            if (onManageWood != null && !detail.archived) OutlinedButton(onManageWood, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp)) {
                                Icon(Icons.Default.Inventory2, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.site_manage_wood))
                            }
                            if (!detail.archived) FieldChoice(R.string.site_link, null, state.choices.filter { choice -> state.linked.none { it.target == choice.target } }, { it.label }, !state.busy, vm::link)
                        }
                        item(key = "site_journal_heading") {
                            Text(stringResource(R.string.site_section_journal), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.site_section_journal_help), style = MaterialTheme.typography.bodyMedium)
                        }
                        items(state.linked, key = { "${it.target.kind}-${it.target.id}" }) { choice ->
                            OutlinedButton({ onJournal(choice.target) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(choice.label) }
                        }
                        item(key = "site_journal") { OutlinedButton({ onJournal(FieldTarget(FieldTargetKind.WORKSITE, detail.id)) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.field_title)) } }
                        if (onExport != null) item(key = "site_documents") { WorksiteAction(Icons.Default.Description, R.string.site_section_documents, R.string.site_section_documents_help,
                            !state.busy, "site_export") { onExport(detail.id) } }
                        item(key = "site_parcels") { HorizontalDivider(); Text(stringResource(R.string.site_parcels), Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium) }
                        items(detail.form.parcels, key = { "parcel-${it.source.length}:${it.source}:${it.reference}" }) { Text("${it.label}\n${it.source} · ${it.reference}") }
                        if (!detail.archived) item(key = "site_edit") { OutlinedButton(vm::edit, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.site_edit)) } }
                        item(key = "site_refresh") { TextButton(vm::reload, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.site_refresh)) } }
                        item(key = "site_archive") { OutlinedButton({ confirmArchive = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(if (detail.archived) R.string.site_restore else R.string.site_archive)) } }
                    } else {
                        item(key = "site_create") { Text(stringResource(R.string.site_empty)); Button(vm::create, enabled = !state.busy, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 56.dp).testTag("site_create")) { Text(stringResource(R.string.site_new)) } }
                        item(key = "site_archives") { Row(verticalAlignment = Alignment.CenterVertically) { Switch(archives, { archives = it }); Text(stringResource(R.string.site_archived), Modifier.padding(start = 12.dp)) } }
                        items(state.sites.filter { it.archived == archives }, key = { it.id }) { site ->
                            Card(onClick = { vm.open(site) }, enabled = !state.busy, shape = MaterialTheme.shapes.large) { Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(site.form.name, style = MaterialTheme.typography.titleLarge)
                                Text(site.form.parcels.joinToString { it.label }, style = MaterialTheme.typography.bodyMedium)
                            } }
                        }
                    }
                }
            }
        }
    }
    if (confirmArchive) FieldConfirmation(if (state.detail?.archived == true) R.string.site_restore else R.string.site_archive,
        if (state.detail?.archived == true) R.string.site_restore_help else R.string.site_archive_help, R.string.field_save,
        onConfirm = { confirmArchive = false; vm.archive() }, onDismiss = { confirmArchive = false })
    if (discard) FieldConfirmation(R.string.field_discard, R.string.field_discard_body, R.string.field_discard,
        onConfirm = { discard = false; vm.close() }, onDismiss = { discard = false })
}

@Composable private fun WorksiteAction(icon: ImageVector, title: Int, description: Int, enabled: Boolean, tag: String, action: () -> Unit) {
    OutlinedCard(onClick = action, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag(tag), shape = MaterialTheme.shapes.large) {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
@Composable private fun WorksiteEditor(form:WorksiteForm,parcels:List<WorksiteParcel>,enabled:Boolean,nameMissing:Boolean,change:(WorksiteForm)->Unit) {
    var source by remember {mutableStateOf("")};var reference by remember {mutableStateOf("")};var label by remember {mutableStateOf("")};var version by remember {mutableStateOf("")}
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(form.name, { change(form.copy(name=it)) }, enabled=enabled,
            label={ Text(stringResource(R.string.site_name)) }, isError=nameMissing,
            supportingText=if (nameMissing) {{ Text(stringResource(R.string.site_name_required)) }} else null,
            modifier=Modifier.fillMaxWidth().testTag("site_name"), shape=MaterialTheme.shapes.medium)
        FieldText(R.string.field_notes,form.notes,enabled) {change(form.copy(notes=it))}
        Text(stringResource(R.string.site_parcels),style=MaterialTheme.typography.titleLarge)
        form.parcels.forEach {p ->
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Text("${p.label}\n${p.source} · ${p.reference}",Modifier.weight(1f))
                TextButton({change(form.copy(parcels=form.parcels-p))},enabled=enabled) {Text(stringResource(R.string.site_remove_parcel))}
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
