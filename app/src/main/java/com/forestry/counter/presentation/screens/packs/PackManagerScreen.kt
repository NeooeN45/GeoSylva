package com.forestry.counter.presentation.screens.packs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.forestry.counter.R
import com.forestry.counter.domain.model.pack.GeoPackDescriptor
import com.forestry.counter.domain.model.pack.PackLevel
import com.forestry.counter.domain.model.pack.PackStatus
import com.forestry.counter.domain.usecase.pack.PackManager
import com.forestry.counter.domain.usecase.pack.RegionalPackSelection
import com.forestry.counter.presentation.viewmodel.PackManagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackManagerScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val factory = remember(context) {
        viewModelFactory { initializer { PackManagerViewModel(PackManager.getInstance(context)) } }
    }
    val packViewModel: PackManagerViewModel = viewModel(factory = factory)
    val packState by packViewModel.packState.collectAsStateWithLifecycle()
    val actions by packViewModel.actionState.collectAsStateWithLifecycle()
    var packToDeactivate by remember { mutableStateOf<GeoPackDescriptor?>(null) }
    var mapVisible by rememberSaveable { mutableStateOf(true) }
    val mapSaveableState = rememberSaveableStateHolder()
    val selectionPrefs = remember(context) {
        context.getSharedPreferences("pack_territory_selection", android.content.Context.MODE_PRIVATE)
    }
    var selectionError by remember { mutableStateOf(false) }
    val selectionSaver = remember {
        mapSaver<Map<String, String>>(
            save = { it },
            restore = { saved -> saved.mapValues { (_, name) -> name as String } }
        )
    }
    var selectedTerritories by rememberSaveable(stateSaver = selectionSaver) {
        mutableStateOf(runCatching {
            Json.decodeFromString<Map<String, String>>(selectionPrefs.getString("territories", "{}") ?: "{}")
        }.getOrElse {
            selectionError = true
            emptyMap()
        })
    }
    var visibleLevel by remember { mutableStateOf("regions") }
    LaunchedEffect(packViewModel) { packViewModel.refreshCatalog() }
    LaunchedEffect(selectedTerritories, selectionError) {
        if (!selectionError) {
            val persisted = withContext(Dispatchers.IO) {
                selectionPrefs.edit().putString("territories", Json.encodeToString(selectedTerritories)).commit()
            }
            if (!persisted) selectionError = true
        }
    }
    val selectedPacks = RegionalPackSelection.visibleSelection(packState.allPacks, selectedTerritories.keys)
    val selectedPackIds = selectedPacks.map { it.id }.toSet()
    val visiblePacks = packState.allPacks.filter { pack ->
        pack.level != PackLevel.SOCLE_NATIONAL && (
            selectedTerritories.isEmpty() || pack.id in selectedPackIds ||
                packState.activePacks.any { it.id == pack.id } || packState.retainedPacks.any { it.id == pack.id }
            )
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.packs_title), fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                }
            },
            actions = {
                IconButton(onClick = packViewModel::refreshCatalog, enabled = !packState.catalogRefreshing) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.pack_catalog_refresh))
                }
            }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PackOverviewHeader(packState, actions.catalogRefreshFailed)
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ) {
                Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PackSectionTab(mapVisible, stringResource(R.string.pack_tab_map), Icons.Default.Map, { mapVisible = true }, Modifier.weight(1f))
                    PackSectionTab(!mapVisible, stringResource(R.string.pack_tab_files), Icons.Default.Folder, { mapVisible = false }, Modifier.weight(1f))
                }
            }
            if (mapVisible) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(if (visibleLevel == "regions") R.string.pack_map_regions else R.string.pack_map_departments),
                        style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.pack_map_multiselect), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                mapSaveableState.SaveableStateProvider("pack-selection-map") {
                    AdministrativePackMap(
                    selectedIds = selectedTerritories.keys,
                    onTerritoryClick = { id, name ->
                        selectedTerritories = if (id in selectedTerritories) selectedTerritories - id
                        else selectedTerritories + (id to name)
                    },
                    onVisibleLevelChanged = { visibleLevel = it },
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(24.dp))
                    )
                }
                TerritorySelection(
                    selectedTerritories = selectedTerritories,
                    selectionError = selectionError,
                    onRemove = { selectedTerritories = selectedTerritories - it },
                    onClear = { selectedTerritories = emptyMap() },
                    onRetry = { selectionError = false }
                )
                Button(onClick = { mapVisible = false }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.pack_show_selected_files, selectedPacks.size))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(stringResource(R.string.pack_territories_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        if (selectedTerritories.isNotEmpty()) {
                            Text(selectedTerritories.values.joinToString(" · "), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    item {
                        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)) {
                            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Default.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Column {
                                    val containsForestry = packState.allPacks.any { it.contentKind == "forestry_layers" }
                                    Text(stringResource(if (containsForestry) R.string.pack_content_forestry_title else R.string.pack_content_administrative_title), style = MaterialTheme.typography.titleSmall)
                                    Text(stringResource(if (containsForestry) R.string.pack_content_forestry_catalog_summary else R.string.pack_content_administrative_summary), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                                    Text(stringResource(R.string.pack_region_departments_included), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                    if (visiblePacks.isEmpty()) item {
                        Text(stringResource(R.string.pack_catalog_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(visiblePacks, key = { it.id }) { pack ->
                        PackCard(
                            pack = pack,
                            activePack = packState.activePacks.firstOrNull { it.id == pack.id },
                            progress = packState.downloadProgress[pack.id],
                            isRunning = pack.id in actions.running || pack.id in packState.downloadProgress,
                            canInterrupt = pack.id in actions.running,
                            canPrepareDownload = !actions.planning && !actions.downloadingSelection,
                            retainedVersions = packState.retainedPacks.filter {
                                it.id == pack.id && it.version != pack.installedVersion &&
                                    it.checksum?.trim()?.matches(Regex("[0-9a-fA-F]{64}")) == true
                            }.distinctBy { it.version },
                            errorMessage = actions.errors[pack.id],
                            onInstall = { packViewModel.prepareDownload(setOf(pack.id)) },
                            onInterrupt = { packViewModel.interrupt(pack.id) },
                            onDeactivate = { packToDeactivate = pack },
                            onReactivate = { version -> packViewModel.reactivatePack(pack.id, version) },
                            onPreview = { packViewModel.previewInstalledPack(pack.id) }
                        )
                    }
                    item {
                        if (actions.planError != null) {
                            Text(stringResource(if (actions.incompleteRegion) R.string.pack_region_incomplete else R.string.pack_plan_failed),
                                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        if (actions.errors.isNotEmpty()) {
                            Text(stringResource(R.string.pack_action_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        if (actions.previewError != null) {
                            Text(stringResource(R.string.pack_preview_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        if (actions.planning || actions.loadingPreview) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (actions.downloadingSelection) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            TextButton(onClick = packViewModel::interruptSelection, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.pack_interrupt_selection))
                            }
                        } else if (selectedPacks.isNotEmpty()) {
                            Button(
                                onClick = { packViewModel.prepareDownload(selectedPacks.map { it.id }.toSet()) },
                                enabled = !actions.planning && actions.running.isEmpty(),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.pack_prepare_download)) }
                        }
                    }
                }
            }
        }
    }
    PackManagementDialogs(packViewModel, packToDeactivate) { packToDeactivate = null }
}

@Composable
internal fun PackManagementDialogs(
    packViewModel: PackManagerViewModel,
    packToDeactivate: GeoPackDescriptor?,
    onDismissDeactivate: () -> Unit
) {
    val actions by packViewModel.actionState.collectAsStateWithLifecycle()
    actions.plan?.let { plan ->
        AlertDialog(
            onDismissRequest = { if (!actions.downloadingSelection) packViewModel.dismissPlan() },
            title = { Text(stringResource(R.string.pack_plan_title)) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (plan.includedDepartmentCount > 0) Text(
                        stringResource(R.string.pack_region_department_count, plan.includedDepartmentCount),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (plan.packs.isEmpty()) Text(stringResource(R.string.pack_plan_already_installed))
                    else {
                        plan.packs.forEach { pack ->
                            Text(pack.name.removePrefix("Limites administratives — "), style = MaterialTheme.typography.titleSmall)
                            Text(shortPackVersion(pack.version), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(humanBytes(plan.archiveBytes), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.pack_plan_archive_bytes, formatBytes(plan.archiveBytes)))
                        Text(stringResource(R.string.pack_plan_required_bytes, formatBytes(plan.archiveBytes)))
                        Text(stringResource(R.string.pack_plan_available_bytes, formatBytes(plan.availableBytes)))
                        Text(stringResource(R.string.pack_plan_bytes_note), style = MaterialTheme.typography.bodySmall)
                        if (!plan.fits) Text(stringResource(R.string.pack_plan_no_space), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = packViewModel::confirmDownload,
                    enabled = plan.fits && plan.packs.isNotEmpty() && !actions.downloadingSelection) {
                    Text(stringResource(R.string.pack_confirm_download))
                }
            },
            dismissButton = { TextButton(onClick = packViewModel::dismissPlan, enabled = !actions.downloadingSelection) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
    actions.preview?.let { content ->
        Dialog(onDismissRequest = packViewModel::closePreview, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = packViewModel::closePreview) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                        Text(stringResource(R.string.pack_preview_title), style = MaterialTheme.typography.titleLarge)
                    }
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Text(content.name.ifBlank { content.territoryId }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text(stringResource(R.string.pack_preview_actual_file_short), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        var showSource by remember(content.territoryId, content.version) { mutableStateOf(false) }
                        TextButton(onClick = { showSource = !showSource }, contentPadding = PaddingValues(vertical = 4.dp)) {
                            Text(stringResource(R.string.pack_source_and_version), style = MaterialTheme.typography.labelMedium)
                            Icon(if (showSource) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        if (showSource) SelectionContainer {
                            Column {
                                Text(stringResource(R.string.pack_version_format, content.version), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                Text(content.licence, style = MaterialTheme.typography.bodySmall)
                                Text(content.attribution, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    AdministrativePackMap(
                        selectedIds = emptySet(), onTerritoryClick = { _, _ -> }, onVisibleLevelChanged = {},
                        installedContent = content, modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp, vertical = 8.dp).clip(RoundedCornerShape(24.dp))
                    )
                }
            }
        }
    }
    packToDeactivate?.let { pack ->
        AlertDialog(
            onDismissRequest = { onDismissDeactivate() },
            title = { Text(stringResource(R.string.pack_deactivate_title, pack.name)) },
            text = { Text(stringResource(R.string.pack_deactivate_description)) },
            confirmButton = {
                TextButton(onClick = {
                    packViewModel.deactivatePack(pack.id)
                    onDismissDeactivate()
                }) { Text(stringResource(R.string.pack_deactivate)) }
            },
            dismissButton = { TextButton(onClick = { onDismissDeactivate() }) { Text(stringResource(R.string.pack_keep_active)) } }
        )
    }
}

@Composable
private fun TerritorySelection(
    selectedTerritories: Map<String, String>, selectionError: Boolean,
    onRemove: (String) -> Unit, onClear: () -> Unit, onRetry: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(pluralStringResource(R.plurals.pack_map_selection_count, selectedTerritories.size, selectedTerritories.size),
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (selectedTerritories.isNotEmpty()) TextButton(onClick = onClear) { Text(stringResource(R.string.pack_map_clear)) }
        }
        if (selectedTerritories.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(selectedTerritories.entries.toList(), key = { it.key }) { entry ->
                InputChip(selected = true, onClick = { onRemove(entry.key) }, label = { Text(entry.value) },
                    trailingIcon = { Icon(Icons.Default.Close, stringResource(R.string.pack_map_remove, entry.value), Modifier.size(18.dp)) })
            }
        }
        if (selectionError) {
            Text(stringResource(R.string.pack_map_selection_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.pack_map_retry_selection)) }
        }
    }
}

@Composable
internal fun CatalogStatusPanel(state: PackManager.PackState, refreshFailed: Boolean) {
    // Le flux utilisateur reçoit uniquement les statuts publiés, jamais le
    // message brut d’une exception (URL, chemins, détails réseau ou serveur).
    val message = PackManagerViewModel.catalogMessage(state, refreshFailed)
    val label = when (message) {
        PackManagerViewModel.CatalogMessage.LOADING -> R.string.pack_catalog_loading
        PackManagerViewModel.CatalogMessage.NOT_CONFIGURED -> R.string.pack_catalog_not_configured
        PackManagerViewModel.CatalogMessage.AUTH_REQUIRED -> R.string.pack_catalog_auth_required
        PackManagerViewModel.CatalogMessage.NETWORK -> R.string.pack_catalog_network_error
        PackManagerViewModel.CatalogMessage.INVALID_RESPONSE -> R.string.pack_catalog_invalid
        PackManagerViewModel.CatalogMessage.STORAGE -> R.string.pack_catalog_storage_error
        PackManagerViewModel.CatalogMessage.SERVER -> R.string.pack_catalog_server_error
        PackManagerViewModel.CatalogMessage.CACHE -> R.string.pack_catalog_cache
        PackManagerViewModel.CatalogMessage.CURRENT -> R.string.pack_catalog_current
        PackManagerViewModel.CatalogMessage.EMPTY -> R.string.pack_catalog_empty
        PackManagerViewModel.CatalogMessage.UNAVAILABLE -> R.string.pack_catalog_refresh_failed
    }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.bodySmall,
            color = if (state.catalogIssue != null || refreshFailed || state.catalogError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.catalogFromCache && (state.catalogIssue != null || refreshFailed || state.catalogError != null)) {
            Text(stringResource(R.string.pack_catalog_cache), style = MaterialTheme.typography.labelSmall)
        }
        if (state.catalogRefreshing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
        if (state.lastError != null) {
            Text(stringResource(R.string.pack_action_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

internal fun formatBytes(bytes: Long): String = java.text.NumberFormat.getIntegerInstance().format(bytes)

@Composable
internal fun PackOverviewHeader(state: PackManager.PackState, refreshFailed: Boolean) {
    val installedCount = state.installed.count { it.level != PackLevel.SOCLE_NATIONAL }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(pluralStringResource(R.plurals.pack_active_count, installedCount, installedCount), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.pack_stored_files_size, humanBytes(state.diskUsageBytes)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            CatalogStatusPanel(state, refreshFailed)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PackSectionTab(
    selected: Boolean, title: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit, modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { this.selected = selected; role = Role.Tab },
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        shadowElevation = if (selected) 1.dp else 0.dp
    ) {
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
internal fun humanBytes(bytes: Long): String = android.text.format.Formatter.formatShortFileSize(LocalContext.current, bytes)

private fun shortPackVersion(version: String): String = if (version.length > 20) version.take(13) + "…" else version

@Composable
private fun readablePackDate(raw: String): String {
    val locale = LocalContext.current.resources.configuration.locales[0]
    return remember(raw, locale) {
        runCatching {
            java.time.OffsetDateTime.parse(raw).toLocalDate().format(
                java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(locale)
            )
        }.getOrDefault(raw)
    }
}

@Composable
internal fun PackCard(
    pack: GeoPackDescriptor,
    activePack: GeoPackDescriptor?,
    progress: Float?,
    isRunning: Boolean,
    canInterrupt: Boolean,
    canPrepareDownload: Boolean,
    retainedVersions: List<GeoPackDescriptor>,
    errorMessage: String?,
    onInstall: () -> Unit,
    onInterrupt: () -> Unit,
    onDeactivate: () -> Unit,
    onReactivate: (String) -> Unit,
    onPreview: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showPresentation by rememberSaveable(pack.territoryId) { mutableStateOf(false) }
    val distributionAvailable = PackManagerViewModel.isAllowedDownloadUrl(pack.downloadUrl) &&
        pack.checksum?.trim()?.matches(Regex("[0-9a-fA-F]{64}")) == true && pack.signedManifest != null
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pack.territoryId != null) {
                    TerritoryPresentationBadge(
                        territoryId = pack.territoryId,
                        modifier = Modifier.size(48.dp),
                        onClick = { showPresentation = true }
                    )
                } else Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = packLevelColor(pack.level).copy(alpha = 0.15f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(packLevelIcon(pack.level), contentDescription = null,
                            modifier = Modifier.size(20.dp), tint = packLevelColor(pack.level))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val displayName = if (pack.contentKind == "administrative_boundaries") {
                        pack.name.removePrefix("Limites administratives — ")
                    } else pack.name.removePrefix("Cartographie forestière — ")
                    Text(displayName, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = if (pack.territoryId != null) Modifier.clickable { showPresentation = true } else Modifier)
                    val levelTitle = stringResource(if (pack.level == PackLevel.REGIONAL) R.string.pack_level_region_short else R.string.pack_level_department_short)
                    Text(levelTitle + (pack.archiveBytes?.let { " · " + humanBytes(it) } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                StatusChip(pack.status, distributionAvailable)
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(48.dp)) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(if (expanded) R.string.cd_collapse else R.string.cd_expand), modifier = Modifier.size(18.dp))
                }
            }

            if (pack.territoryId != null) {
                TextButton(onClick = { showPresentation = true }, contentPadding = PaddingValues(vertical = 2.dp)) {
                    Text(stringResource(R.string.pack_presentation_open), style = MaterialTheme.typography.labelMedium)
                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                if (pack.contentKind == "forestry_layers") {
                    Text(stringResource(R.string.pack_content_forestry_summary), style = MaterialTheme.typography.bodySmall)
                    val unavailable = pack.forestryQuarantinedCounts.values.sum()
                    if (unavailable > 0) Text(stringResource(R.string.pack_invalid_contours_count, unavailable),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    for ((layer, count) in pack.forestryLayerCounts) {
                        val label = when (layer) {
                            "cadastre" -> R.string.pack_forestry_cadastral_count
                            "public_forests" -> R.string.pack_forestry_forest_count
                            "onf_parcels" -> R.string.pack_forestry_onf_count
                            else -> continue
                        }
                        Text(stringResource(label, count), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (pack.contentKind !in setOf("administrative_boundaries", "forestry_layers")) {
                    Text(stringResource(R.string.pack_local_metadata_unqualified), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (pack.buildDate.isNotBlank()) {
                    Text(stringResource(R.string.pack_created_date, readablePackDate(pack.buildDate)), style = MaterialTheme.typography.bodySmall)
                }
                if (pack.version.isNotBlank()) {
                    Text(stringResource(R.string.pack_version_format, shortPackVersion(pack.version)), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                var showTechnical by remember { mutableStateOf(false) }
                TextButton(onClick = { showTechnical = !showTechnical }, contentPadding = PaddingValues(vertical = 4.dp)) {
                    Text(stringResource(R.string.pack_source_and_version), style = MaterialTheme.typography.labelMedium)
                    Icon(if (showTechnical) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                if (showTechnical) SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.pack_insee_code, pack.codeINSEE), style = MaterialTheme.typography.bodySmall)
                        if (pack.version.isNotBlank()) Text(stringResource(R.string.pack_version_format, pack.version), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        pack.installedVersion?.takeIf { it != pack.version }?.let { version ->
                            Text(stringResource(R.string.pack_active_version_format, version), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                        pack.archiveBytes?.let { bytes -> Text(stringResource(R.string.pack_archive_bytes, formatBytes(bytes)), style = MaterialTheme.typography.bodySmall) }
                        if (pack.contentKind in setOf("administrative_boundaries", "forestry_layers")) {
                            Text(stringResource(if (pack.contentKind == "forestry_layers") R.string.pack_content_forestry_summary else R.string.pack_content_administrative_summary), style = MaterialTheme.typography.bodySmall)
                            Text(pack.metaInfo.license, style = MaterialTheme.typography.bodySmall)
                            Text(pack.metaInfo.notes, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (errorMessage != null) {
                    Text(stringResource(R.string.pack_action_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (!distributionAvailable && pack.status != PackStatus.EMBEDDED) {
                    Text(stringResource(R.string.pack_distribution_unavailable), style = MaterialTheme.typography.bodySmall)
                }
                if (activePack?.contentKind in setOf("administrative_boundaries", "forestry_layers") && !isRunning) {
                    OutlinedButton(onClick = onPreview, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.pack_preview_open))
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (!isRunning && pack.status == PackStatus.INSTALLED && pack.level == PackLevel.REGIONAL) {
                    OutlinedButton(onClick = onInstall, enabled = distributionAvailable && canPrepareDownload,
                        modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.pack_region_download_departments))
                    }
                }
                if (isRunning) {
                    if (progress == null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                    if (canInterrupt) {
                        TextButton(onClick = onInterrupt, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.pack_interrupt))
                        }
                    }
                } else when (pack.status) {
                    PackStatus.AVAILABLE -> OutlinedButton(
                        onClick = onInstall,
                        enabled = distributionAvailable && canPrepareDownload,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.pack_install))
                    }
                    PackStatus.UPDATE_PENDING -> Button(
                        onClick = onInstall,
                        enabled = distributionAvailable && canPrepareDownload,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Update, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.pack_update))
                    }
                    PackStatus.INSTALLED -> TextButton(
                        onClick = onDeactivate,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.pack_deactivate), color = MaterialTheme.colorScheme.error) }
                    PackStatus.ERROR -> OutlinedButton(
                        onClick = onInstall,
                        enabled = distributionAvailable && canPrepareDownload,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.pack_error_retry))
                    }
                    PackStatus.EMBEDDED, PackStatus.DOWNLOADING -> {}
                }
                if (!isRunning && pack.status == PackStatus.UPDATE_PENDING && pack.installedVersion != null) {
                    TextButton(onClick = onDeactivate, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.pack_deactivate), color = MaterialTheme.colorScheme.error)
                    }
                }
                if (!isRunning) retainedVersions.forEach { retained ->
                    OutlinedButton(onClick = { onReactivate(retained.version) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.pack_reactivate_version_format, retained.version))
                    }
                }
            }
        }
    }
    if (showPresentation) pack.territoryId?.let { territoryId ->
        TerritoryPresentationSheet(territoryId = territoryId, onDismiss = { showPresentation = false })
    }
}

@Composable
private fun StatusChip(status: PackStatus, distributionAvailable: Boolean = true) {
    val (label, color) = when (status) {
        PackStatus.INSTALLED       -> stringResource(R.string.pack_status_installed) to Color(0xFF2E7D32)
        PackStatus.EMBEDDED        -> stringResource(R.string.pack_status_embedded) to Color(0xFF1565C0)
        PackStatus.AVAILABLE       -> if (distributionAvailable) {
            stringResource(R.string.pack_status_available) to Color(0xFF1565C0)
        } else {
            stringResource(R.string.pack_status_unavailable) to MaterialTheme.colorScheme.onSurfaceVariant
        }
        PackStatus.UPDATE_PENDING  -> if (distributionAvailable) {
            stringResource(R.string.pack_update_available) to Color(0xFFE65100)
        } else {
            stringResource(R.string.pack_status_update_unavailable) to MaterialTheme.colorScheme.onSurfaceVariant
        }
        PackStatus.DOWNLOADING     -> "…" to Color(0xFF6A1B9A)
        PackStatus.ERROR           -> stringResource(R.string.pack_status_error) to Color(0xFFC62828)
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = color)
    }
}

private fun packLevelColor(level: PackLevel): Color = when (level) {
    PackLevel.SOCLE_NATIONAL  -> Color(0xFF1565C0)
    PackLevel.REGIONAL        -> Color(0xFF388E3C)
    PackLevel.DEPARTEMENTAL   -> Color(0xFFE65100)
}

private fun packLevelIcon(level: PackLevel) = when (level) {
    PackLevel.SOCLE_NATIONAL -> Icons.Default.Public
    PackLevel.REGIONAL       -> Icons.Default.Map
    PackLevel.DEPARTEMENTAL  -> Icons.Default.LocationOn
}
