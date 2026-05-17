package com.forestry.counter.presentation.screens.forestry

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.EmojiNature
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import android.net.Uri
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.repository.ParcelleRepository
import com.forestry.counter.domain.repository.PlacetteRepository
import com.forestry.counter.domain.repository.TigeRepository
import com.forestry.counter.data.preferences.UserPreferencesManager
import com.forestry.counter.presentation.components.AppMiniDialog
import com.forestry.counter.presentation.components.TipCard
import com.forestry.counter.presentation.utils.rememberHapticFeedback
import com.forestry.counter.presentation.utils.rememberSoundFeedback
import com.forestry.counter.presentation.utils.StaggerEntrance
import com.forestry.counter.domain.repository.GroupRepository
import kotlinx.coroutines.launch
import java.util.UUID
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.forestry.counter.R
import android.os.Build
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ParcellesScreen(
    forestId: String?,
    groupRepository: GroupRepository,
    parcelleRepository: ParcelleRepository,
    placetteRepository: PlacetteRepository,
    tigeRepository: TigeRepository,
    userPreferences: UserPreferencesManager,
    onNavigateToPlacettes: (String) -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    onNavigateToMartelage: ((String) -> Unit)? = null,
    onNavigateToMap: (() -> Unit)? = null,
    onNavigateToDiagnostic: ((String) -> Unit)? = null,
    onNavigateToIbp: ((String) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val parcelles by (
        if (forestId != null) parcelleRepository.getParcellesByForest(forestId) else parcelleRepository.getAllParcelles()
    ).collectAsState(initial = emptyList())

    var editParcelle by remember { mutableStateOf<Parcelle?>(null) }
    var deleteParcelle by remember { mutableStateOf<Parcelle?>(null) }
    var name by remember { mutableStateOf("") }
    var surface by remember { mutableStateOf("") }
    var shape by remember { mutableStateOf("") }
    var slopePct by remember { mutableStateOf("") }
    var aspect by remember { mutableStateOf("") }
    var access by remember { mutableStateOf("") }
    var altitudeM by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    var tolerance by remember { mutableStateOf("") }
    var samplingMode by remember { mutableStateOf("CIRCULAR") }
    var sampleArea by remember { mutableStateOf("2000") }
    val group by remember(forestId) {
        if (forestId != null) groupRepository.getGroupById(forestId) else kotlinx.coroutines.flow.flowOf(null)
    }.collectAsState(initial = null)

    var showRenameProjectDialog by remember { mutableStateOf(false) }
    var renameProjectName by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(ParcelleSort.NAME) }

    val hapticEnabled by userPreferences.hapticEnabled.collectAsState(initial = true)
    val soundEnabled by userPreferences.soundEnabled.collectAsState(initial = true)
    val hapticIntensity by userPreferences.hapticIntensity.collectAsState(initial = 2)
    val animationsEnabled by userPreferences.animationsEnabled.collectAsState(initial = true)
    val pressScale by userPreferences.pressScale.collectAsState(initial = 0.96f)
    val animDurationShort by userPreferences.animDurationShort.collectAsState(initial = 140)
    val backgroundImageEnabled by userPreferences.backgroundImageEnabled.collectAsState(initial = true)
    val backgroundImageUri by userPreferences.backgroundImageUri.collectAsState(initial = null)
    val haptic = rememberHapticFeedback()
    val sound = rememberSoundFeedback()

    fun playClickFeedback() {
        if (hapticEnabled) haptic.performWithIntensity(hapticIntensity)
        if (soundEnabled) sound.click()
    }
    val toleranceOptions = listOf("5", "10", "15", "20", "25", "30")

    fun addParcelle() {
        playClickFeedback()
        scope.launch {
            val id = UUID.randomUUID().toString()
            val defaultName = context.getString(R.string.parcelle_default_name_format, parcelles.size + 1)
            parcelleRepository.insertParcelle(
                Parcelle(
                    id = id,
                    forestId = forestId,
                    name = defaultName,
                    surfaceHa = null,
                    shape = null,
                    slopePct = null,
                    aspect = null,
                    access = null,
                    altitudeM = null,
                    objectifType = null,
                    objectifVal = null,
                    tolerancePct = null,
                    samplingMode = "CIRCULAR",
                    sampleAreaM2 = 2000.0,
                    targetSpeciesCsv = null,
                    srid = null,
                    remarks = null
                )
            )
            snackbar.showSnackbar(context.getString(R.string.parcelle_added))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (backgroundImageEnabled) {
            val uriString = backgroundImageUri
            if (uriString != null) {
                val uri = remember(uriString) { Uri.parse(uriString) }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        ImageView(context).apply {
                            scaleType = ImageView.ScaleType.CENTER_CROP
                        }
                    },
                    update = { imageView ->
                        imageView.setImageURI(uri)
                    }
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.forest_background),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbar) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.parcelles_title)) },
                    navigationIcon = {
                        if (onNavigateBack != null) {
                            IconButton(onClick = {
                                playClickFeedback()
                                onNavigateBack()
                            }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                        }
                    },
                    actions = {
                        if (onNavigateToMap != null) {
                            IconButton(onClick = {
                                playClickFeedback()
                                onNavigateToMap()
                            }) {
                                Icon(Icons.Default.Map, contentDescription = stringResource(R.string.map_view))
                            }
                        }
                        if (forestId != null) {
                            IconButton(onClick = {
                                playClickFeedback()
                                renameProjectName = group?.name ?: ""
                                showRenameProjectDialog = true
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.rename_project))
                            }
                        }
                        if (forestId != null && onNavigateToMartelage != null) {
                            IconButton(onClick = {
                                playClickFeedback()
                                onNavigateToMartelage(forestId)
                            }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Straighten, contentDescription = null)
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(Icons.Default.Description, contentDescription = stringResource(R.string.martelage))
                                }
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = {
                    addParcelle()
                }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_parcelle))
                }
            },
            containerColor = Color.Transparent
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                val displayedParcelles = remember(parcelles, sortMode) {
                    when (sortMode) {
                        ParcelleSort.NAME -> parcelles.sortedBy { it.name.lowercase() }
                        ParcelleSort.SURFACE -> parcelles.sortedWith(
                            compareByDescending<Parcelle> { it.surfaceHa ?: Double.NEGATIVE_INFINITY }
                                .thenBy { it.name.lowercase() }
                        )
                        ParcelleSort.UPDATED_AT -> parcelles.sortedWith(
                            compareByDescending<Parcelle> { it.updatedAt }
                                .thenBy { it.name.lowercase() }
                        )
                    }
                }

                Crossfade(
                    targetState = parcelles.isEmpty(),
                    animationSpec = if (animationsEnabled) {
                        tween(durationMillis = 220, easing = FastOutSlowInEasing)
                    } else {
                        tween(durationMillis = 0)
                    },
                    label = "parcellesCrossfade"
                ) { isEmpty ->
                    if (isEmpty) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(stringResource(R.string.parcelles_empty_title), style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.parcelles_empty_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { addParcelle() }) {
                                Text(stringResource(R.string.create_parcelle))
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Tri compact en chips discrets en tête de liste
                                item {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        shape = RoundedCornerShape(12.dp),
                                        tonalElevation = 1.dp
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.Sort,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                            ParcelleSort.entries.forEach { mode ->
                                                val label = when (mode) {
                                                    ParcelleSort.NAME -> stringResource(R.string.sort_by_name)
                                                    ParcelleSort.SURFACE -> stringResource(R.string.sort_by_surface)
                                                    ParcelleSort.UPDATED_AT -> stringResource(R.string.sort_by_last_update)
                                                }
                                                FilterChip(
                                                    selected = sortMode == mode,
                                                    onClick = { sortMode = mode },
                                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                                    modifier = Modifier.height(28.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                item {
                                    TipCard(
                                        tipKey = "parcelles_tip",
                                        title = stringResource(R.string.tip_parcelles_title),
                                        message = stringResource(R.string.tip_parcelles_msg)
                                    )
                                }
                                itemsIndexed(displayedParcelles, key = { _, p -> p.id }) { idx, p ->
                                    StaggerEntrance(index = idx, staggerMs = 50) {
                                    ParcelleCard(
                                        parcelle = p,
                                        animationsEnabled = animationsEnabled,
                                        pressScale = pressScale,
                                        animDurationShort = animDurationShort,
                                        onClick = {
                                            playClickFeedback()
                                            onNavigateToPlacettes(p.id)
                                        },
                                        onNavigateToDiagnostic = onNavigateToDiagnostic?.let { nav -> { nav(p.id) } },
                                        onNavigateToIbp = onNavigateToIbp?.let { nav -> { nav(p.id) } },
                                        onEdit = {
                                            playClickFeedback()
                                            editParcelle = p
                                            name = p.name
                                            surface = p.surfaceHa?.toString() ?: ""
                                            shape = p.shape ?: ""
                                            slopePct = p.slopePct?.toString() ?: ""
                                            aspect = p.aspect ?: ""
                                            access = p.access ?: ""
                                            altitudeM = p.altitudeM?.toString() ?: ""
                                            remarks = p.remarks ?: ""
                                            tolerance = p.tolerancePct?.toString() ?: ""
                                            samplingMode = p.samplingMode ?: "CIRCULAR"
                                            sampleArea = p.sampleAreaM2?.toString() ?: "2000"
                                        },
                                        onDelete = {
                                            playClickFeedback()
                                            deleteParcelle = p
                                        },
                                        modifier = if (animationsEnabled) Modifier.animateItemPlacement() else Modifier
                                    )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editParcelle != null) {
        AppMiniDialog(
            onDismissRequest = { editParcelle = null },
            animationsEnabled = animationsEnabled,
            icon = Icons.Default.Edit,
            title = stringResource(R.string.parcelle_defaults_title),
            confirmText = stringResource(R.string.save),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                val p = editParcelle ?: return@AppMiniDialog
                scope.launch {
                    parcelleRepository.updateParcelle(
                        p.copy(
                            name = name.trim().ifBlank { p.name },
                            surfaceHa = surface.toDoubleOrNull(),
                            shape = shape.trim().ifBlank { null },
                            slopePct = slopePct.replace(',', '.').toDoubleOrNull(),
                            aspect = aspect.trim().ifBlank { null },
                            access = access.trim().ifBlank { null },
                            altitudeM = altitudeM.replace(',', '.').toDoubleOrNull(),
                            samplingMode = samplingMode.trim().ifBlank { null },
                            sampleAreaM2 = sampleArea.toDoubleOrNull(),
                            targetSpeciesCsv = p.targetSpeciesCsv,
                            objectifType = p.objectifType,
                            objectifVal = p.objectifVal,
                            tolerancePct = tolerance.toDoubleOrNull(),
                            remarks = remarks.trim().ifBlank { null },
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    snackbar.showSnackbar(context.getString(R.string.parcelle_updated))
                    editParcelle = null
                }
            }
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = surface,
                    onValueChange = { surface = it },
                    label = { Text(stringResource(R.string.surface_ha)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = shape,
                    onValueChange = { shape = it },
                    label = { Text(stringResource(R.string.parcelle_shape)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = slopePct,
                    onValueChange = { txt -> slopePct = txt.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text(stringResource(R.string.parcelle_slope_pct)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = aspect,
                    onValueChange = { aspect = it },
                    label = { Text(stringResource(R.string.parcelle_aspect)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = access,
                    onValueChange = { access = it },
                    label = { Text(stringResource(R.string.parcelle_access)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = altitudeM,
                    onValueChange = { txt -> altitudeM = txt.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text(stringResource(R.string.parcelle_altitude_m)) },
                    modifier = Modifier.fillMaxWidth()
                )

                var samplingExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = samplingExpanded, onExpandedChange = { samplingExpanded = !samplingExpanded }) {
                    val samplingModeLabel = when (samplingMode) {
                        "CIRCULAR" -> stringResource(R.string.sampling_mode_circular)
                        "FIXED_AREA" -> stringResource(R.string.sampling_mode_fixed_area)
                        else -> samplingMode
                    }
                    OutlinedTextField(
                        value = samplingModeLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.sampling_mode)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = samplingExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = samplingExpanded, onDismissRequest = { samplingExpanded = false }) {
                        listOf("CIRCULAR", "FIXED_AREA").forEach { opt ->
                            val optLabel = when (opt) {
                                "CIRCULAR" -> stringResource(R.string.sampling_mode_circular)
                                "FIXED_AREA" -> stringResource(R.string.sampling_mode_fixed_area)
                                else -> opt
                            }
                            DropdownMenuItem(text = { Text(optLabel) }, onClick = { samplingMode = opt; samplingExpanded = false })
                        }
                    }
                }

                OutlinedTextField(
                    value = tolerance,
                    onValueChange = { txt ->
                        tolerance = txt.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }
                    },
                    label = { Text(stringResource(R.string.tolerance_pct_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    toleranceOptions.forEach { v ->
                        AssistChip(
                            onClick = { tolerance = v },
                            label = { Text(v) },
                            leadingIcon = null,
                            enabled = true
                        )
                    }
                }

                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text(stringResource(R.string.parcelle_remarks)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (deleteParcelle != null) {
        val p = deleteParcelle!!
        AppMiniDialog(
            onDismissRequest = { deleteParcelle = null },
            animationsEnabled = animationsEnabled,
            icon = Icons.Default.Delete,
            title = stringResource(R.string.delete_parcelle_title),
            description = stringResource(R.string.delete_parcelle_confirm_format, p.name),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            confirmIsDestructive = true,
            onConfirm = {
                scope.launch {
                    try {
                        tigeRepository.deleteTigesByParcelle(p.id)
                        placetteRepository.deletePlacettesByParcelle(p.id)
                        parcelleRepository.deleteParcelle(p.id)
                        deleteParcelle = null
                        snackbar.showSnackbar(context.getString(R.string.parcelle_deleted))
                    } catch (e: Exception) {
                        deleteParcelle = null
                        snackbar.showSnackbar(e.message ?: context.getString(R.string.error))
                    }
                }
            }
        )
    }

    if (showRenameProjectDialog && forestId != null) {
        AppMiniDialog(
            onDismissRequest = { showRenameProjectDialog = false },
            animationsEnabled = animationsEnabled,
            icon = Icons.Default.Edit,
            title = stringResource(R.string.rename_project),
            confirmText = stringResource(R.string.save),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                val newName = renameProjectName.trim()
                if (newName.isNotBlank()) {
                    scope.launch {
                        val current = group
                        if (current != null) {
                            groupRepository.updateGroup(
                                current.copy(
                                    name = newName,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }
                        showRenameProjectDialog = false
                    }
                } else {
                    showRenameProjectDialog = false
                }
            }
        ) {
            OutlinedTextField(
                value = renameProjectName,
                onValueChange = { renameProjectName = it },
                label = { Text(stringResource(R.string.name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

private enum class ParcelleSort {
    NAME,
    SURFACE,
    UPDATED_AT
}

@Composable
private fun ParcelleCard(
    parcelle: Parcelle,
    animationsEnabled: Boolean,
    pressScale: Float,
    animDurationShort: Int,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onNavigateToDiagnostic: (() -> Unit)? = null,
    onNavigateToIbp: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (animationsEnabled && isPressed) pressScale else 1f,
        animationSpec = if (animationsEnabled) tween(durationMillis = animDurationShort, easing = FastOutSlowInEasing) else tween(0),
        label = "parcelleCardScale"
    )

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        ListItem(
            headlineContent = { Text(parcelle.name) },
            supportingContent = {
                val surfaceHa = parcelle.surfaceHa
                if (surfaceHa != null && surfaceHa > 0.0) {
                    Text(stringResource(R.string.surface_ha_format, surfaceHa))
                }
            },
            trailingContent = {
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_parcelle))
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Plus d'options")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            if (onNavigateToIbp != null) {
                                DropdownMenuItem(
                                    text = { Text("IBP Biodiversité") },
                                    leadingIcon = { Icon(Icons.Default.EmojiNature, contentDescription = null) },
                                    onClick = { menuExpanded = false; onNavigateToIbp() }
                                )
                            }
                            if (onNavigateToDiagnostic != null) {
                                DropdownMenuItem(
                                    text = { Text("Diagnostic sylvicole") },
                                    leadingIcon = { Icon(Icons.Default.Science, contentDescription = null) },
                                    onClick = { menuExpanded = false; onNavigateToDiagnostic() }
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}
