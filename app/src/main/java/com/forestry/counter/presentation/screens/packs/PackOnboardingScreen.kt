package com.forestry.counter.presentation.screens.packs

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.forestry.counter.R
import com.forestry.counter.domain.model.pack.GeoPackDescriptor
import com.forestry.counter.domain.model.pack.PackLevel
import com.forestry.counter.domain.usecase.pack.PackManager
import com.forestry.counter.domain.usecase.pack.RegionalPackSelection
import com.forestry.counter.presentation.viewmodel.PackManagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Two optional first-entry pages, sharing the same real pack manager and settings selection. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackOnboardingScreen(onContinue: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val factory = remember(context) {
        viewModelFactory { initializer { PackManagerViewModel(PackManager.getInstance(context)) } }
    }
    val manager: PackManagerViewModel = viewModel(factory = factory)
    val state by manager.packState.collectAsStateWithLifecycle()
    val actions by manager.actionState.collectAsStateWithLifecycle()
    var showDownloads by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var packToDeactivate by remember { mutableStateOf<GeoPackDescriptor?>(null) }
    val mapState = rememberSaveableStateHolder()
    val preferences = remember(context) {
        context.getSharedPreferences("pack_territory_selection", Context.MODE_PRIVATE)
    }
    var selectionError by remember { mutableStateOf(false) }
    val selectionSaver = remember {
        mapSaver<Map<String, String>>(save = { it }, restore = { saved -> saved.mapValues { (_, name) -> name as String } })
    }
    var selectedTerritories by rememberSaveable(stateSaver = selectionSaver) {
        mutableStateOf(runCatching {
            Json.decodeFromString<Map<String, String>>(preferences.getString("territories", "{}") ?: "{}")
        }.getOrElse { selectionError = true; emptyMap() })
    }
    LaunchedEffect(manager) { manager.refreshCatalog() }
    LaunchedEffect(selectedTerritories, selectionError) {
        if (!selectionError && !withContext(Dispatchers.IO) {
                preferences.edit().putString("territories", Json.encodeToString(selectedTerritories)).commit()
            }) selectionError = true
    }
    val selectedPacks = RegionalPackSelection.visibleSelection(state.allPacks, selectedTerritories.keys)
    val selectedPackIds = selectedPacks.map { it.id }.toSet()
    val visiblePacks = state.allPacks.filter { pack ->
        pack.level != PackLevel.SOCLE_NATIONAL && (
            pack.id in selectedPackIds ||
                state.activePacks.any { it.id == pack.id } || state.retainedPacks.any { it.id == pack.id }
            )
    }
    val missingTerritories = selectedTerritories.filterKeys { territory -> selectedPacks.none { it.territoryId == territory } }
    val continueAfterDownloads = {
        if (actions.running.isNotEmpty() || actions.downloadingSelection) confirmLeave = true
        else onContinue()
    }
    BackHandler {
        if (showDownloads) showDownloads = false else continueAfterDownloads()
    }
    if (!showDownloads) {
        mapState.SaveableStateProvider("initial-pack-map") {
            PackTerritoryOnboardingPage(
                selectedTerritories = selectedTerritories,
                selectionError = selectionError,
                onToggle = { id, name ->
                    selectedTerritories = if (id in selectedTerritories) selectedTerritories - id else selectedTerritories + (id to name)
                },
                onRemove = { selectedTerritories = selectedTerritories - it },
                onRetrySelection = { selectionError = false },
                onNext = { showDownloads = true },
                onSkip = continueAfterDownloads
            )
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Column {
                        Text(stringResource(R.string.pack_onboarding_download_title), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.pack_onboarding_step_two), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } },
                    navigationIcon = { IconButton(onClick = { showDownloads = false }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.pack_onboarding_return_map))
                    } },
                    actions = { IconButton(onClick = manager::refreshCatalog, enabled = !state.catalogRefreshing) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.pack_catalog_refresh))
                    } }
                )
            },
            bottomBar = {
                Surface(shadowElevation = 8.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Button(onClick = continueAfterDownloads, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.pack_onboarding_continue))
                        }
                        Text(stringResource(R.string.pack_onboarding_settings_reminder),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        ) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { PackOverviewHeader(state, actions.catalogRefreshFailed) }
                item {
                    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(selectedTerritories.values.joinToString(" · "), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.pack_content_administrative_summary), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.pack_onboarding_independent_archives), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (missingTerritories.isNotEmpty()) {
                            Text(stringResource(R.string.pack_onboarding_unpublished, missingTerritories.values.joinToString(" · ")),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (actions.planError != null) Text(
                            stringResource(if (actions.incompleteRegion) R.string.pack_region_incomplete else R.string.pack_plan_failed),
                            color = MaterialTheme.colorScheme.error
                        )
                        if (actions.previewError != null) Text(stringResource(R.string.pack_preview_failed), color = MaterialTheme.colorScheme.error)
                        if (actions.planning || actions.loadingPreview) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (actions.downloadingSelection) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            TextButton(onClick = manager::interruptSelection, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.pack_interrupt_selection))
                            }
                        } else if (selectedPacks.isNotEmpty()) {
                            Button(onClick = { manager.prepareDownload(selectedPacks.map { it.id }.toSet()) },
                                enabled = !actions.planning && actions.running.isEmpty(), modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.pack_prepare_download))
                            }
                        }
                    }
                }
                items(visiblePacks, key = { it.id }) { pack ->
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        PackCard(
                            pack = pack,
                            activePack = state.activePacks.firstOrNull { it.id == pack.id },
                            progress = state.downloadProgress[pack.id],
                            isRunning = pack.id in actions.running || pack.id in state.downloadProgress,
                            canInterrupt = pack.id in actions.running,
                            canPrepareDownload = !actions.planning && !actions.downloadingSelection,
                            retainedVersions = state.retainedPacks.filter {
                                it.id == pack.id && it.version != pack.installedVersion &&
                                    it.checksum?.trim()?.matches(Regex("[0-9a-fA-F]{64}")) == true
                            }.distinctBy { it.version },
                            errorMessage = actions.errors[pack.id],
                            onInstall = { manager.prepareDownload(setOf(pack.id)) },
                            onInterrupt = { manager.interrupt(pack.id) },
                            onDeactivate = { packToDeactivate = pack },
                            onReactivate = { manager.reactivatePack(pack.id, it) },
                            onPreview = { manager.previewInstalledPack(pack.id) }
                        )
                    }
                }
            }
        }
    }
    PackManagementDialogs(manager, packToDeactivate) { packToDeactivate = null }
    if (confirmLeave) AlertDialog(
        onDismissRequest = { confirmLeave = false },
        title = { Text(stringResource(R.string.pack_onboarding_leave_title)) },
        text = { Text(stringResource(R.string.pack_onboarding_leave_body)) },
        confirmButton = { TextButton(onClick = {
            manager.interruptSelection()
            confirmLeave = false
            onContinue()
        }) { Text(stringResource(R.string.pack_onboarding_stop_continue)) } },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun PackTerritoryOnboardingPage(
    selectedTerritories: Map<String, String>,
    selectionError: Boolean,
    onToggle: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onRetrySelection: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    var visibleLevel by remember { mutableStateOf("regions") }
    val density = LocalDensity.current
    var headerHeight by remember { mutableStateOf(92.dp) }
    var footerHeight by remember { mutableStateOf(180.dp) }
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        AdministrativePackMap(
            selectedIds = selectedTerritories.keys,
            onTerritoryClick = onToggle,
            onVisibleLevelChanged = { visibleLevel = it },
            modifier = Modifier.fillMaxSize(),
            overlayTop = headerHeight,
            overlayBottom = footerHeight
        )
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp)
                .onSizeChanged { with(density) { headerHeight = it.height.toDp() + 24.dp } },
            shape = RoundedCornerShape(22.dp), shadowElevation = 3.dp,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
        ) {
            Row(Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pack_onboarding_step_one), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.pack_onboarding_map_title), style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onSkip) { Text(stringResource(R.string.pack_onboarding_skip)) }
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)
                .onSizeChanged { with(density) { footerHeight = it.height.toDp() + 24.dp } },
            shape = RoundedCornerShape(24.dp), shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(stringResource(if (visibleLevel == "regions") R.string.pack_map_regions else R.string.pack_map_departments),
                        style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    Text(pluralStringResource(R.plurals.pack_map_selection_count, selectedTerritories.size, selectedTerritories.size),
                        style = MaterialTheme.typography.labelSmall)
                }
                if (selectedTerritories.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(selectedTerritories.entries.toList(), key = { it.key }) { (id, name) ->
                        InputChip(selected = true, onClick = { onRemove(id) }, label = { Text(name) },
                            trailingIcon = { Icon(Icons.Default.Close, stringResource(R.string.pack_map_remove, name), Modifier.size(16.dp)) })
                    }
                } else Text(stringResource(R.string.pack_onboarding_map_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (selectionError) {
                    Text(stringResource(R.string.pack_map_selection_error), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetrySelection) { Text(stringResource(R.string.pack_map_retry_selection)) }
                }
                Button(onClick = onNext, enabled = selectedTerritories.isNotEmpty() && !selectionError,
                    modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pack_onboarding_validate)) }
            }
        }
    }
}
