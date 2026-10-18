package com.forestry.counter.presentation.screens.forestry

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import android.net.Uri
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.repository.PlacetteRepository
import com.forestry.counter.domain.repository.ParcelleRepository
import com.forestry.counter.domain.repository.TigeRepository
import com.forestry.counter.domain.repository.InventaireSessionRepository
import com.forestry.counter.data.preferences.UserPreferencesManager
import com.forestry.counter.presentation.components.AppMiniDialog
import com.forestry.counter.presentation.components.LoadingState
import com.forestry.counter.presentation.utils.localizeDefaultName
import com.forestry.counter.presentation.utils.rememberHapticFeedback
import com.forestry.counter.presentation.utils.rememberSoundFeedback
import com.forestry.counter.presentation.utils.StaggerEntrance
import kotlinx.coroutines.launch
import kotlin.math.PI
import com.forestry.counter.R
import android.os.Build
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlacettesScreen(
    parcelleId: String,
    placetteRepository: PlacetteRepository,
    parcelleRepository: ParcelleRepository,
    tigeRepository: TigeRepository,
    userPreferences: UserPreferencesManager,
    onNavigateToMartelage: (String, String) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToMartelageForParcelle: ((String) -> Unit)? = null,
    onNavigateToMap: ((String) -> Unit)? = null,
    onNavigateToDiagnostic: ((String) -> Unit)? = null,
    onNavigateToCreatePlacette: ((String) -> Unit)? = null,
    sessionRepository: InventaireSessionRepository? = null,
    onOpenSessionSynthesis: ((String, String) -> Unit)? = null,
    onOpenFieldJournal: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    // Keep subscriptions across elapsed-time recompositions; these flows still observe access changes.
    val placettesFlow = remember(placetteRepository, parcelleId) { placetteRepository.getPlacettesByParcelle(parcelleId) }
    val placettesState by placettesFlow.collectAsStateWithLifecycle(initialValue = null)
    val placettes = placettesState ?: emptyList<Placette>()
    val parcelleFlow = remember(parcelleRepository, parcelleId) { parcelleRepository.getParcelleById(parcelleId) }
    val parcelle by parcelleFlow.collectAsStateWithLifecycle(initialValue = null)

    val hapticEnabled by userPreferences.hapticEnabled.collectAsStateWithLifecycle(initialValue = true)
    val soundEnabled by userPreferences.soundEnabled.collectAsStateWithLifecycle(initialValue = true)
    val hapticIntensity by userPreferences.hapticIntensity.collectAsStateWithLifecycle(initialValue = 2)
    val animationsEnabled by userPreferences.animationsEnabled.collectAsStateWithLifecycle(initialValue = true)
    val pressScale by userPreferences.pressScale.collectAsStateWithLifecycle(initialValue = 0.96f)
    val animDurationShort by userPreferences.animDurationShort.collectAsStateWithLifecycle(initialValue = 140)
    val backgroundImageEnabled by userPreferences.backgroundImageEnabled.collectAsStateWithLifecycle(initialValue = true)
    val backgroundImageUri by userPreferences.backgroundImageUri.collectAsStateWithLifecycle(initialValue = null)
    val haptic = rememberHapticFeedback()
    val sound = rememberSoundFeedback()

    fun playClickFeedback() {
        if (hapticEnabled) haptic.performWithIntensity(hapticIntensity)
        if (soundEnabled) sound.click()
    }

    var editPlacette by remember { mutableStateOf<Placette?>(null) }
    var deletePlacette by remember { mutableStateOf<Placette?>(null) }
    var editName by remember { mutableStateOf("") }
    var editSurfaceM2 by remember { mutableStateOf("") }
    var editRayonM by remember { mutableStateOf("") }
    var editType by remember { mutableStateOf("CIRC") }
    var sortMode by remember { mutableStateOf(PlacetteSort.NAME) }

    val displayedPlacettes = remember(placettes, sortMode) {
        when (sortMode) {
            PlacetteSort.NAME -> placettes.sortedBy { (it.name ?: it.id).lowercase() }
            PlacetteSort.UPDATED_AT -> placettes.sortedByDescending { it.updatedAt }
        }
    }

    fun addPlacette() {
        playClickFeedback()
        onNavigateToCreatePlacette?.invoke(parcelleId)
    }

    if (placettesState == null) {
        LoadingState("Chargement des placettes…")
        return
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
                    title = {
                        Column {
                            Text(
                                stringResource(R.string.placettes_title),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleLarge
                            )
                            val pName = parcelle?.name?.takeIf { it.isNotBlank() }
                            val subtitle = if (pName != null) {
                                "$pName · ${stringResource(R.string.placettes_count_format, placettes.size)}"
                            } else {
                                stringResource(R.string.placettes_count_format, placettes.size)
                            }
                            Text(
                                subtitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            playClickFeedback()
                            onNavigateBack()
                        }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                    },
                    actions = {
                        onOpenFieldJournal?.let { open ->
                            com.forestry.counter.presentation.screens.field.FieldJournalButton(open)
                        }
                        if (onNavigateToMap != null) {
                            IconButton(onClick = {
                                playClickFeedback()
                                onNavigateToMap(parcelleId)
                            }) {
                                Icon(Icons.Default.Map, contentDescription = stringResource(R.string.map_view))
                            }
                        }
                        if (onNavigateToMartelageForParcelle != null) {
                            IconButton(onClick = {
                                playClickFeedback()
                                onNavigateToMartelageForParcelle(parcelleId)
                            }) {
                                Icon(Icons.Default.Straighten, contentDescription = stringResource(R.string.martelage_synthesis_title))
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { addPlacette() },
                    modifier = Modifier.offset(x = 8.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_placette))
                }
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                Crossfade(
                    targetState = placettes.isEmpty(),
                    animationSpec = if (animationsEnabled) {
                        tween(durationMillis = 220, easing = FastOutSlowInEasing)
                    } else {
                        tween(durationMillis = 0)
                    },
                    label = "placettesCrossfade"
                ) { isEmpty ->
                    if (isEmpty) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(stringResource(R.string.placettes_empty_title), style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.placettes_empty_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { addPlacette() }) {
                                Text(stringResource(R.string.create_placette))
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
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
                                        PlacetteSort.entries.forEach { mode ->
                                            val label = when (mode) {
                                                PlacetteSort.NAME -> stringResource(R.string.sort_by_name)
                                                PlacetteSort.UPDATED_AT -> stringResource(R.string.sort_by_last_update)
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
                            itemsIndexed(displayedPlacettes, key = { _, pl -> pl.id }) { idx, pl ->
                                StaggerEntrance(index = idx, staggerMs = 50) {
                                PlacetteCard(
                                    placette = pl,
                                    animationsEnabled = animationsEnabled,
                                    pressScale = pressScale,
                                    animDurationShort = animDurationShort,
                                    onClick = {
                                        playClickFeedback()
                                        onNavigateToMartelage(parcelleId, pl.id)
                                    },
                                    onEdit = {
                                        playClickFeedback()
                                        editPlacette = pl
                                        editName = pl.name ?: ""
                                        editSurfaceM2 = pl.surfaceM2?.toString() ?: ""
                                        editRayonM = pl.rayonM?.toString() ?: ""
                                        editType = pl.type ?: "CIRC"
                                    },
                                    onDelete = {
                                        playClickFeedback()
                                        deletePlacette = pl
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

    if (editPlacette != null) {
        AppMiniDialog(
            onDismissRequest = { editPlacette = null },
            animationsEnabled = animationsEnabled,
            icon = Icons.Default.Edit,
            title = stringResource(R.string.placette_info_dialog_title),
            confirmText = stringResource(R.string.save),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                val p = editPlacette ?: return@AppMiniDialog
                val newSurface = editSurfaceM2.replace(',', '.').toDoubleOrNull()
                val newRayon = editRayonM.replace(',', '.').toDoubleOrNull()
                val hasMappedGeometry = p.type == "ENTIRE_PARCEL" || p.type == "HEX" ||
                    p.samplingPlanJson != null || p.footprintGeoJson != null
                val retainedRadius = newRayon?.takeIf { it.isFinite() && it > 0.0 && (PI * it * it).isFinite() } ?: p.rayonM
                val retainedType = if (hasMappedGeometry) p.type else editType.ifBlank { p.type }
                val retainedArea = when {
                    hasMappedGeometry -> p.surfaceM2
                    retainedType == "CIRC" && retainedRadius != p.rayonM -> retainedRadius?.let { PI * it * it }
                    retainedType == "CIRC" -> p.surfaceM2
                    else -> newSurface?.takeIf { it.isFinite() && it > 0.0 } ?: p.surfaceM2
                }
                scope.launch {
                    placetteRepository.updatePlacette(
                        p.copy(
                            name = editName.trim().ifBlank { null },
                            surfaceM2 = retainedArea,
                            rayonM = if (hasMappedGeometry) p.rayonM else retainedRadius,
                            type = retainedType,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    snackbar.showSnackbar(context.getString(R.string.placette_updated))
                    editPlacette = null
                }
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text(stringResource(R.string.name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (editPlacette?.let { it.type == "ENTIRE_PARCEL" || it.type == "HEX" ||
                        it.samplingPlanJson != null || it.footprintGeoJson != null } == true) {
                    Text(stringResource(if (editPlacette?.type == "ENTIRE_PARCEL")
                        R.string.plot_whole_edit_hint else R.string.plot_geometric_edit_hint), style = MaterialTheme.typography.bodyMedium)
                } else {
                OutlinedTextField(
                    value = editSurfaceM2,
                    onValueChange = { txt -> editSurfaceM2 = txt.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text(stringResource(R.string.placette_surface_m2)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = editType != "CIRC",
                    singleLine = true,
                    suffix = { Text(stringResource(R.string.unit_m2)) }
                )
                OutlinedTextField(
                    value = editRayonM,
                    onValueChange = { txt -> editRayonM = txt.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = { Text(stringResource(R.string.placette_rayon_m)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    suffix = { Text(stringResource(R.string.unit_m)) }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("CIRC" to stringResource(R.string.sampling_mode_circular), "FIXED_AREA" to stringResource(R.string.sampling_mode_fixed_area)).forEach { (key, label) ->
                        FilterChip(
                            selected = editType == key,
                            onClick = { editType = key },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                }
            }
        }
    }

    if (deletePlacette != null) {
        val p = deletePlacette ?: return
        AppMiniDialog(
            onDismissRequest = { deletePlacette = null },
            animationsEnabled = animationsEnabled,
            icon = Icons.Default.Delete,
            title = stringResource(R.string.delete_placette_title),
            description = stringResource(R.string.delete_placette_confirm_name_format, p.name ?: p.id.take(8)),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            confirmIsDestructive = true,
            onConfirm = {
                scope.launch {
                    try {
                        tigeRepository.deleteTigesByPlacette(p.id)
                        placetteRepository.deletePlacette(p.id)
                        deletePlacette = null
                        snackbar.showSnackbar(context.getString(R.string.placette_deleted))
                    } catch (e: Exception) {
                        deletePlacette = null
                        snackbar.showSnackbar(e.message ?: context.getString(R.string.error))
                    }
                }
            }
        )
    }
}

private enum class PlacetteSort {
    NAME,
    UPDATED_AT
}

@Composable
private fun PlacetteCard(
    placette: Placette,
    animationsEnabled: Boolean,
    pressScale: Float,
    animDurationShort: Int,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (animationsEnabled && isPressed) pressScale else 1f,
        animationSpec = if (animationsEnabled) tween(durationMillis = animDurationShort, easing = FastOutSlowInEasing) else tween(0),
        label = "placetteCardScale"
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
            headlineContent = { Text(localizeDefaultName(placette.name) ?: placette.id.take(8)) },
            supportingContent = if (placette.type == "ENTIRE_PARCEL") {
                { Text(stringResource(R.string.plot_whole_card)) }
            } else null,
            trailingContent = {
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_edit))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_placette))
                    }
                }
            }
        )
    }
}
