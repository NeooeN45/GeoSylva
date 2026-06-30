package com.forestry.counter.presentation.screens.forestry

import android.graphics.BitmapFactory
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiNature
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Water
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.shape.CircleShape
import com.forestry.counter.R
import com.forestry.counter.domain.model.Essence
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.repository.EssenceRepository
import com.forestry.counter.domain.repository.PlacetteRepository
import com.forestry.counter.domain.repository.TigeRepository
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import com.forestry.counter.data.preferences.UserPreferencesManager
import com.forestry.counter.presentation.components.AppMiniDialog
import com.forestry.counter.presentation.utils.rememberHapticFeedback
import com.forestry.counter.presentation.utils.rememberSoundFeedback
import kotlinx.coroutines.launch
import java.io.File
import java.text.Normalizer
import java.util.UUID

private fun getPlacettePhotoDir(context: android.content.Context, placetteId: String): File {
    val dir = File(context.getExternalFilesDir(null), "photos/$placetteId")
    dir.mkdirs()
    return dir
}

private fun getPlacettePhotos(context: android.content.Context, placetteId: String): List<File> =
    getPlacettePhotoDir(context, placetteId)
        .listFiles()
        ?.filter { it.extension.lowercase() == "jpg" }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()

private fun essenceColor(essence: Essence?): Color? {
    if (essence == null) return null
    // Couleur personnalisée prioritaire si définie
    essence.colorHex?.let { hex ->
        return try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { null }
    }

    // Sinon, couleurs par catégorie, puis fallback hash
    val cat = essence.categorie?.uppercase()?.trim()
    return when (cat) {
        "AVENIR" -> Color(0xFF4CAF50)
        "RESERVE" -> Color(0xFF2196F3)
        "ENLEVER" -> Color(0xFFF44336)
        "DEPERIR" -> Color(0xFFFF9800)
        "BIODIV" -> Color(0xFF26A69A)
        else -> hashColorFromCode(essence.code)
    }
}

private fun hashColorFromCode(code: String): Color {
    val palette = listOf(
        Color(0xFF4CAF50), // green
        Color(0xFF2196F3), // blue
        Color(0xFFF44336), // red
        Color(0xFFFF9800), // orange
        Color(0xFF26A69A), // teal
        Color(0xFF9C27B0), // purple
        Color(0xFF009688), // deep teal
        Color(0xFF795548), // brown
        Color(0xFF607D8B)  // blue gray
    )
    val idx = (code.hashCode() and 0x7FFFFFFF) % palette.size
    return palette[idx]
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun PlacetteDetailScreen(
    parcelleId: String,
    placetteId: String,
    essenceRepository: EssenceRepository,
    tigeRepository: TigeRepository,
    placetteRepository: PlacetteRepository,
    userPreferences: UserPreferencesManager,
    onNavigateToDiametres: (parcelleId: String, placetteId: String, essenceCode: String) -> Unit,
    onNavigateToMartelage: (parcelleId: String, placetteId: String) -> Unit,
    onNavigateToIbp: ((parcelleId: String, placetteId: String) -> Unit)? = null,
    onNavigateToStationDiag: ((parcelleId: String) -> Unit)? = null,
    onNavigateToRipisylveDiag: ((parcelleId: String) -> Unit)? = null,
    onNavigateToEvolution: ((parcelleId: String, placetteId: String, year: Int) -> Unit)? = null,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showColorDialog by remember { mutableStateOf(false) }
    var colorTargetEssence by remember { mutableStateOf<Essence?>(null) }
    var actionTargetEssenceCode by remember { mutableStateOf<String?>(null) }
    var showEssenceActionsDialog by remember { mutableStateOf(false) }
    var deleteTargetEssenceCode by remember { mutableStateOf<String?>(null) }
    var showDeletePlacetteDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery  by remember { mutableStateOf("") }

    val allEssences by essenceRepository.getAllEssences().collectAsStateWithLifecycle(initialValue = emptyList())
    val tiges by tigeRepository.getTigesByPlacette(placetteId).collectAsStateWithLifecycle(initialValue = emptyList())
    val persistedOrder by userPreferences.essenceOrderFlow(placetteId).collectAsStateWithLifecycle(initialValue = emptyList())

    val hapticEnabled by userPreferences.hapticEnabled.collectAsStateWithLifecycle(initialValue = true)
    val soundEnabled by userPreferences.soundEnabled.collectAsStateWithLifecycle(initialValue = true)
    val hapticIntensity by userPreferences.hapticIntensity.collectAsStateWithLifecycle(initialValue = 2)
    val animationsEnabled by userPreferences.animationsEnabled.collectAsStateWithLifecycle(initialValue = true)
    val haptic = rememberHapticFeedback()
    val sound = rememberSoundFeedback()

    fun playClickFeedback() {
        if (hapticEnabled) haptic.performWithIntensity(hapticIntensity)
        if (soundEnabled) sound.click()
    }

    // Usage par essence dans cette placette
    val usageByEssence = remember(tiges) {
        tiges.groupBy { it.essenceCode }.mapValues { it.value.size }
    }

    // Essences actuellement présentes (blocs existants)
    // On garde aussi les essences présentes dans l'ordre persistant, même sans tige,
    // pour éviter que les blocs disparaissent au retour.
    val presentEssences = remember(usageByEssence, persistedOrder) {
        (usageByEssence.keys + persistedOrder).distinct()
    }

    // Ordre override (drag & drop). Si vide => tri par usage desc puis nom, ou ordre persistant si disponible.
    var orderOverride by remember { mutableStateOf<List<String>>(emptyList()) }
    var reorderMode by remember { mutableStateOf(false) }
    var draggingCode by remember { mutableStateOf<String?>(null) }
    var dragAccum by remember { mutableStateOf(0f) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val itemStepPx = with(density) { (84.dp + 12.dp).toPx() }

    fun defaultSorted(): List<String> = presentEssences.sortedWith(
        compareByDescending<String> { usageByEssence[it] ?: 0 }.thenBy { code ->
            allEssences.firstOrNull { it.code == code }?.name ?: code
        }
    )

    fun mergedPersisted(): List<String> {
        if (persistedOrder.isEmpty()) return defaultSorted()
        val filtered = persistedOrder.filter { it in presentEssences }
        val missing = presentEssences.filter { it !in filtered }
        return filtered + missing
    }

    fun displayEssenceOrder(): List<String> {
        return when {
            orderOverride.isNotEmpty() -> orderOverride
            persistedOrder.isNotEmpty() -> mergedPersisted()
            else -> defaultSorted()
        }
    }

    // Dialog ajout essence
    var showAddDialog by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    // ── Photos (F6) ──────────────────────────────────────────────────────────
    var photoFiles        by remember { mutableStateOf(getPlacettePhotos(context, placetteId)) }
    var pendingPhotoFile  by remember { mutableStateOf<File?>(null) }
    var deleteTargetPhoto by remember { mutableStateOf<File?>(null) }
    var photoViewerIndex  by remember { mutableStateOf<Int?>(null) }
    var renameTargetPhoto by remember { mutableStateOf<File?>(null) }
    var photoSelectionMode by remember { mutableStateOf(false) }
    val selectedPhotos = remember { mutableStateListOf<File>() }

    fun exportPhotos(files: List<File>) {
        if (files.isEmpty()) return
        runCatching {
            val uris = files.map {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
            }
            val intent = if (uris.size == 1) {
                android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(android.content.Intent.EXTRA_STREAM, uris.first())
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/jpeg"
                    putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, ArrayList(uris))
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            context.startActivity(
                android.content.Intent.createChooser(intent, context.getString(R.string.photo_export_chooser))
            )
        }.onFailure { e ->
            scope.launch { snackbar.showSnackbar(e.message ?: context.getString(R.string.error)) }
        }
        photoSelectionMode = false
        selectedPhotos.clear()
    }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            photoFiles = getPlacettePhotos(context, placetteId)
            scope.launch { snackbar.showSnackbar(context.getString(R.string.photo_taken)) }
        }
        pendingPhotoFile = null
    }

    var pendingCameraLaunch by remember { mutableStateOf(false) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingCameraLaunch) {
            val photoDir = getPlacettePhotoDir(context, placetteId)
            val newFile = File(photoDir, "${UUID.randomUUID()}.jpg")
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", newFile
            )
            pendingPhotoFile = newFile
            photoLauncher.launch(uri)
        }
        pendingCameraLaunch = false
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.placette_essences_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        playClickFeedback()
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    // Recherche
                    IconButton(onClick = {
                        playClickFeedback()
                        searchActive = !searchActive
                        if (!searchActive) searchQuery = ""
                    }) {
                        Icon(
                            if (searchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = stringResource(R.string.search_essences)
                        )
                    }
                    // Menu overflow : actions de gestion
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            // ── Actions de gestion ──
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.reorder)) },
                                leadingIcon = { Icon(Icons.Default.SwapVert, contentDescription = stringResource(R.string.cd_swap)) },
                                onClick = {
                                    showMoreMenu = false
                                    playClickFeedback()
                                    reorderMode = !reorderMode
                                    if (reorderMode && orderOverride.isEmpty()) orderOverride = displayEssenceOrder()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.photo_take)) },
                                leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = stringResource(R.string.cd_camera)) },
                                onClick = {
                                    showMoreMenu = false
                                    playClickFeedback()
                                    val hasCameraPermission = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasCameraPermission) {
                                        val photoDir = getPlacettePhotoDir(context, placetteId)
                                        val newFile = File(photoDir, "${UUID.randomUUID()}.jpg")
                                        val uri = FileProvider.getUriForFile(
                                            context, "${context.packageName}.fileprovider", newFile
                                        )
                                        pendingPhotoFile = newFile
                                        photoLauncher.launch(uri)
                                    } else {
                                        pendingCameraLaunch = true
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_placette), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete), tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMoreMenu = false
                                    playClickFeedback()
                                    showDeletePlacetteDialog = true
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                playClickFeedback()
                showAddDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            // ── Actions diagnostics centrées (Martelage / Station / Ripisylve / IBP) ──
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Button(onClick = {
                    playClickFeedback()
                    onNavigateToMartelage(parcelleId, placetteId)
                }) {
                    Icon(Icons.Default.Straighten, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.martelage))
                }
                if (onNavigateToStationDiag != null) {
                    OutlinedButton(onClick = {
                        playClickFeedback()
                        onNavigateToStationDiag(parcelleId)
                    }) {
                        Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF1565C0))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.diag_station_btn))
                    }
                }
                if (onNavigateToRipisylveDiag != null) {
                    OutlinedButton(onClick = {
                        playClickFeedback()
                        onNavigateToRipisylveDiag(parcelleId)
                    }) {
                        Icon(Icons.Default.Water, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF0277BD))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.diag_ripisylve_btn))
                    }
                }
                if (onNavigateToIbp != null) {
                    OutlinedButton(onClick = {
                        playClickFeedback()
                        onNavigateToIbp(parcelleId, placetteId)
                    }) {
                        Icon(Icons.Default.EmojiNature, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF2E7D32))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ibp_start))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))

            // ── TabRow : Essences / Évolution ──────────────────────────────────────
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { playClickFeedback(); selectedTab = 0 },
                    text = { Text(stringResource(R.string.placette_tab_essences)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { playClickFeedback(); selectedTab = 1 },
                    text = { Text(stringResource(R.string.placette_tab_evolution)) }
                )
            }
            Spacer(Modifier.height(8.dp))

            Crossfade(
                targetState = selectedTab,
                animationSpec = if (animationsEnabled) {
                    tween(durationMillis = 220, easing = FastOutSlowInEasing)
                } else {
                    tween(durationMillis = 0)
                },
                label = "placetteTabCrossfade"
            ) { tab ->
                when (tab) {
                    0 -> Column {
            // Barre de recherche animée
            androidx.compose.animation.AnimatedVisibility(
                visible = searchActive,
                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    placeholder = { Text(stringResource(R.string.search_essences)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close))
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                )
            }

            // ── Galerie photos ────────────────────────────────────────────────────────
            if (photoFiles.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                ) {
                    Text(
                        stringResource(R.string.placette_photos_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (photoSelectionMode) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                stringResource(R.string.photo_selected_count, selectedPhotos.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(
                                onClick = { exportPhotos(selectedPhotos.toList()) },
                                enabled = selectedPhotos.isNotEmpty()
                            ) {
                                Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.photo_export))
                            }
                            TextButton(onClick = {
                                photoSelectionMode = false
                                selectedPhotos.clear()
                            }) { Text(stringResource(R.string.cancel)) }
                        }
                    } else {
                        TextButton(onClick = { photoSelectionMode = true }) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.photo_select))
                        }
                    }
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    items(photoFiles, key = { it.absolutePath }) { file ->
                        val bmp = remember(file.absolutePath) {
                            runCatching {
                                BitmapFactory.decodeFile(file.absolutePath)
                                    ?.let { android.graphics.Bitmap.createScaledBitmap(it, 120, 120, true) }
                                    ?.asImageBitmap()
                            }.getOrNull()
                        }
                        val isSelected = file in selectedPhotos
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .size(80.dp)
                                .combinedClickable(
                                    onClick = {
                                        playClickFeedback()
                                        if (photoSelectionMode) {
                                            if (isSelected) selectedPhotos.remove(file) else selectedPhotos.add(file)
                                        } else {
                                            photoViewerIndex = photoFiles.indexOf(file)
                                        }
                                    },
                                    onLongClick = { deleteTargetPhoto = file }
                                )
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (bmp != null) {
                                    Image(
                                        painter = BitmapPainter(bmp),
                                        contentDescription = null,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(Icons.Default.CameraAlt, null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (photoSelectionMode && isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val rawOrder = displayEssenceOrder()
            val displayOrder = remember(rawOrder, searchQuery, allEssences) {
                if (searchQuery.isBlank()) rawOrder
                else {
                    val q = searchQuery.trim().lowercase()
                    rawOrder.filter { code ->
                        val name = allEssences.firstOrNull { it.code == code }?.name ?: code
                        code.lowercase().contains(q) || name.lowercase().contains(q)
                    }
                }
            }

            Crossfade(
                targetState = displayOrder.isEmpty(),
                animationSpec = if (animationsEnabled) {
                    tween(durationMillis = 220, easing = FastOutSlowInEasing)
                } else {
                    tween(durationMillis = 0)
                },
                label = "placetteDetailEssencesCrossfade"
            ) { isEmpty ->
                if (isEmpty) {
                    Text(
                        if (searchQuery.isNotBlank()) stringResource(R.string.search_no_result)
                        else stringResource(R.string.placette_essences_empty_desc)
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(displayOrder, key = { it }) { code ->
                            val essence = allEssences.firstOrNull { it.code == code }
                            val containerColor: Color? = essenceColor(essence)

                            val baseModifier = if (animationsEnabled) Modifier.animateItemPlacement() else Modifier
                            val itemModifier = baseModifier

                            EssenceBlock(
                                code = code,
                                name = essence?.name ?: code,
                                count = usageByEssence[code] ?: 0,
                                reorderMode = reorderMode,
                                animationsEnabled = animationsEnabled,
                                onDragStart = {
                                    draggingCode = code
                                    dragAccum = 0f
                                    if (orderOverride.isEmpty()) orderOverride = displayOrder
                                },
                                onDrag = { dragY ->
                                    dragAccum += dragY
                                    var idx = orderOverride.indexOf(code)
                                    while (dragAccum <= -itemStepPx && idx > 0) {
                                        val list = orderOverride.toMutableList()
                                        list.removeAt(idx)
                                        list.add(idx - 1, code)
                                        orderOverride = list
                                        idx -= 1
                                        dragAccum += itemStepPx
                                    }
                                    while (dragAccum >= itemStepPx && idx < orderOverride.lastIndex) {
                                        val list = orderOverride.toMutableList()
                                        list.removeAt(idx)
                                        list.add(idx + 1, code)
                                        orderOverride = list
                                        idx += 1
                                        dragAccum -= itemStepPx
                                    }
                                },
                                onDragEnd = {
                                    draggingCode = null
                                    scope.launch { userPreferences.setEssenceOrder(placetteId, orderOverride) }
                                },
                                onClick = {
                                    playClickFeedback()
                                    onNavigateToDiametres(parcelleId, placetteId, code)
                                },
                                onLongPress = {
                                    playClickFeedback()
                                    // En mode réorganisation: garder le comportement suppression protégée.
                                    if (reorderMode) {
                                        val uses = usageByEssence[code] ?: 0
                                        if (uses == 0 && orderOverride.isNotEmpty()) {
                                            orderOverride = orderOverride.filterNot { it == code }
                                            scope.launch { userPreferences.setEssenceOrder(placetteId, orderOverride) }
                                        } else {
                                            scope.launch { snackbar.showSnackbar(context.getString(R.string.cannot_delete_existing_data)) }
                                        }
                                    } else {
                                        actionTargetEssenceCode = code
                                        showEssenceActionsDialog = true
                                    }
                                },
                                modifier = itemModifier,
                                containerColor = containerColor
                            )
                        }
                    }
                }
            }
                    } // fin tab 0 Column
                    1 -> PlacetteEvolutionTab(
                        tiges = tiges,
                        allEssences = allEssences,
                        animationsEnabled = animationsEnabled,
                        onYearClick = { clickedYear ->
                            playClickFeedback()
                            onNavigateToEvolution?.invoke(parcelleId, placetteId, clickedYear)
                        }
                    )
                }
            } // fin tab Crossfade
        }
    }

    if (showColorDialog && colorTargetEssence != null) {
        val target = colorTargetEssence ?: return
        AppMiniDialog(
            onDismissRequest = { showColorDialog = false },
            animationsEnabled = animationsEnabled,
            icon = Icons.Default.Palette,
            title = stringResource(R.string.color_for_format, target.name),
            description = stringResource(R.string.choose_color_for_essence),
            confirmText = stringResource(R.string.close),
            onConfirm = { showColorDialog = false }
        ) {
            val options = listOf(
                "#4CAF50", "#388E3C", "#1B5E20",
                "#2196F3", "#1976D2", "#0D47A1",
                "#F44336", "#D32F2F", "#B71C1C",
                "#FF9800", "#F57C00", "#E65100",
                "#FFEB3B", "#FBC02D",
                "#26A69A", "#009688",
                "#9C27B0", "#6A1B9A",
                "#795548", "#607D8B"
            )
            val rows = options.chunked(6)
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { hex ->
                        val col = try {
                            Color(android.graphics.Color.parseColor(hex))
                        } catch (_: Exception) {
                            MaterialTheme.colorScheme.primary
                        }
                        Surface(
                            modifier = Modifier
                                .size(32.dp)
                                .combinedClickable(
                                    onClick = {
                                        playClickFeedback()
                                        scope.launch {
                                            essenceRepository.updateEssence(target.copy(colorHex = hex))
                                            showColorDialog = false
                                        }
                                    },
                                    onLongClick = {
                                        playClickFeedback()
                                        scope.launch {
                                            essenceRepository.updateEssence(target.copy(colorHex = null))
                                            showColorDialog = false
                                        }
                                    }
                                ),
                            color = col,
                            shape = MaterialTheme.shapes.small
                        ) {}
                    }
                }
            }
            Text(
                stringResource(R.string.color_long_press_tip),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (showEssenceActionsDialog && actionTargetEssenceCode != null) {
        val code = actionTargetEssenceCode ?: return
        val e = allEssences.firstOrNull { it.code == code }
        val name = e?.name ?: code
        AppMiniDialog(
            onDismissRequest = {
                showEssenceActionsDialog = false
                actionTargetEssenceCode = null
            },
            animationsEnabled = animationsEnabled,
            icon = Icons.Default.Palette,
            title = stringResource(R.string.essence_actions_title_format, name),
            confirmText = stringResource(R.string.close),
            onConfirm = {
                showEssenceActionsDialog = false
                actionTargetEssenceCode = null
            }
        ) {
            // ── Fiche propriétés forestières ──
            if (e != null && e.densiteBois != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    @Composable
                    fun InfoRow(label: String, value: String?) {
                        if (!value.isNullOrBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(label, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(value, style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.essence_info_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    InfoRow(stringResource(R.string.essence_density), "${e.densiteBois?.toInt()} kg/m³")
                    InfoRow(stringResource(R.string.essence_quality), e.qualiteTypique)
                    InfoRow(stringResource(R.string.essence_logging), e.typeCoupePreferee)
                    InfoRow(stringResource(R.string.essence_wood_use), e.usageBois)
                    InfoRow(stringResource(R.string.essence_growth), e.vitesseCroissance)
                    InfoRow(stringResource(R.string.essence_shade), e.toleranceOmbre)
                    if (e.hauteurMaxM != null || e.diametreMaxCm != null) {
                        val dims = buildString {
                            e.hauteurMaxM?.let { append("H=${it.toInt()}m") }
                            if (e.hauteurMaxM != null && e.diametreMaxCm != null) append(" / ")
                            e.diametreMaxCm?.let { append("D=${it.toInt()}cm") }
                        }
                        InfoRow(stringResource(R.string.essence_max_dims), dims)
                    }
                    if (!e.remarques.isNullOrBlank()) {
                        Text(
                            e.remarques,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                }
            }

            FilledTonalButton(
                onClick = {
                    showEssenceActionsDialog = false
                    actionTargetEssenceCode = null
                    if (e != null) {
                        colorTargetEssence = e
                        showColorDialog = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.change_color))
            }
            TextButton(
                onClick = {
                    showEssenceActionsDialog = false
                    actionTargetEssenceCode = null
                    deleteTargetEssenceCode = code
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.delete))
            }
        }
    }

    if (deleteTargetEssenceCode != null) {
        val code = deleteTargetEssenceCode ?: return
        val e = allEssences.firstOrNull { it.code == code }
        val name = e?.name ?: code
        val uses = usageByEssence[code] ?: 0
        AppMiniDialog(
            onDismissRequest = { deleteTargetEssenceCode = null },
            animationsEnabled = animationsEnabled,
            icon = Icons.Default.Delete,
            title = stringResource(R.string.delete_essence_title),
            description = stringResource(R.string.delete_essence_confirm_format, name, uses),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            confirmIsDestructive = true,
            onConfirm = {
                scope.launch {
                    tigeRepository.deleteTigesByPlacetteAndEssence(placetteId, code)
                    val baseOrder = if (orderOverride.isNotEmpty()) orderOverride else displayEssenceOrder()
                    val newOrder = baseOrder.filterNot { it == code }
                    orderOverride = newOrder
                    userPreferences.setEssenceOrder(placetteId, newOrder)
                    deleteTargetEssenceCode = null
                }
            }
        )
    }

    if (showDeletePlacetteDialog) {
        val uses = tiges.size
        AppMiniDialog(
            onDismissRequest = { showDeletePlacetteDialog = false },
            animationsEnabled = animationsEnabled,
            icon = Icons.Default.Delete,
            title = stringResource(R.string.delete_placette_title),
            description = stringResource(R.string.delete_placette_confirm_format, uses),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            confirmIsDestructive = true,
            onConfirm = {
                scope.launch {
                    try {
                        tigeRepository.deleteTigesByPlacette(placetteId)
                        placetteRepository.deletePlacette(placetteId)
                        showDeletePlacetteDialog = false
                        onNavigateBack()
                    } catch (e: Exception) {
                        showDeletePlacetteDialog = false
                        snackbar.showSnackbar(e.message ?: context.getString(R.string.error))
                    }
                }
            }
        )
    }

    deleteTargetPhoto?.let { photoToDelete ->
        AppMiniDialog(
            onDismissRequest = { deleteTargetPhoto = null },
            animationsEnabled = animationsEnabled,
            icon = Icons.Default.Delete,
            title = stringResource(R.string.photo_delete_title),
            description = stringResource(R.string.photo_delete_confirm),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            confirmIsDestructive = true,
            onConfirm = {
                photoToDelete.delete()
                photoFiles = getPlacettePhotos(context, placetteId)
                selectedPhotos.remove(photoToDelete)
                deleteTargetPhoto = null
            }
        )
    }

    // ── Visualiseur photo plein écran ─────────────────────────────────────────
    photoViewerIndex?.let { idx ->
        if (idx in photoFiles.indices) {
            PlacettePhotoViewerDialog(
                file = photoFiles[idx],
                canPrev = idx > 0,
                canNext = idx < photoFiles.lastIndex,
                onPrev = { photoViewerIndex = idx - 1 },
                onNext = { photoViewerIndex = idx + 1 },
                onRename = {
                    renameTargetPhoto = photoFiles[idx]
                    photoViewerIndex = null
                },
                onExport = { exportPhotos(listOf(photoFiles[idx])) },
                onDelete = {
                    deleteTargetPhoto = photoFiles[idx]
                    photoViewerIndex = null
                },
                onDismiss = { photoViewerIndex = null }
            )
        } else {
            photoViewerIndex = null
        }
    }

    // ── Renommage photo ───────────────────────────────────────────────────────
    renameTargetPhoto?.let { target ->
        val initialName = target.nameWithoutExtension
        var newName by remember(target) { mutableStateOf(initialName) }
        AlertDialog(
            onDismissRequest = { renameTargetPhoto = null },
            title = { Text(stringResource(R.string.photo_rename_title)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.photo_rename_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = newName.isNotBlank() && newName.trim() != initialName,
                    onClick = {
                        val sanitized = newName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
                        val dest = File(target.parentFile, "$sanitized.jpg")
                        if (!dest.exists() && target.renameTo(dest)) {
                            photoFiles = getPlacettePhotos(context, placetteId)
                            scope.launch { snackbar.showSnackbar(context.getString(R.string.photo_renamed)) }
                        } else {
                            scope.launch { snackbar.showSnackbar(context.getString(R.string.photo_rename_error)) }
                        }
                        renameTargetPhoto = null
                    }
                ) { Text(stringResource(R.string.validate)) }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetPhoto = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showAddDialog) {
        AddEssenceDialog(
            allEssences = allEssences,
            presentEssenceCodes = presentEssences.toSet(),
            usageByEssence = usageByEssence,
            query = query,
            onQueryChange = { query = it },
            animationsEnabled = animationsEnabled,
            onDismiss = { showAddDialog = false; query = "" },
            onAdd = { code ->
                // On ajoute l'essence dans l'ordre et on persiste pour qu'elle reste visible.
                if (orderOverride.isEmpty()) {
                    orderOverride = displayEssenceOrder()
                }
                if (code !in orderOverride) {
                    orderOverride = orderOverride + code
                    scope.launch { userPreferences.setEssenceOrder(placetteId, orderOverride) }
                }
                showAddDialog = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EssenceBlock(
    code: String,
    name: String,
    count: Int,
    reorderMode: Boolean,
    animationsEnabled: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = if (containerColor != null) CardDefaults.cardColors(containerColor = containerColor) else CardDefaults.cardColors()
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(code, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(count.toString(), style = MaterialTheme.typography.titleLarge)
                    
                    AnimatedVisibility(
                        visible = reorderMode,
                        enter = fadeIn(animationSpec = tween(durationMillis = if (animationsEnabled) 160 else 0, easing = FastOutSlowInEasing)) +
                            expandHorizontally(animationSpec = tween(durationMillis = if (animationsEnabled) 200 else 0, easing = FastOutSlowInEasing)),
                        exit = fadeOut(animationSpec = tween(durationMillis = if (animationsEnabled) 160 else 0, easing = FastOutSlowInEasing)) +
                            shrinkHorizontally(animationSpec = tween(durationMillis = if (animationsEnabled) 200 else 0, easing = FastOutSlowInEasing))
                    ) {
                        Row {
                            Spacer(Modifier.width(16.dp))
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = stringResource(R.string.reorder),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(32.dp)
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = { _ -> onDragStart() },
                                            onDrag = { change, dragAmount -> 
                                                change.consume()
                                                onDrag(dragAmount.y) 
                                            },
                                            onDragEnd = { onDragEnd() },
                                            onDragCancel = { onDragEnd() }
                                        )
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddEssenceDialog(
    allEssences: List<Essence>,
    presentEssenceCodes: Set<String>,
    usageByEssence: Map<String, Int>,
    query: String,
    onQueryChange: (String) -> Unit,
    animationsEnabled: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    AppMiniDialog(
        onDismissRequest = onDismiss,
        animationsEnabled = animationsEnabled,
        icon = Icons.Default.Add,
        title = stringResource(R.string.add_essence_title),
        confirmText = stringResource(R.string.close),
        onConfirm = onDismiss
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.search_tolerant_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )
        val candidates = remember(allEssences, presentEssenceCodes, usageByEssence, query) {
            val base = allEssences.filter { it.code !in presentEssenceCodes }
            if (query.isBlank()) {
                base.sortedWith(
                    compareByDescending<Essence> { usageByEssence[it.code] ?: 0 }
                        .thenBy { it.name }
                )
            } else {
                val normQuery = normalizeText(query)
                val tokens = normQuery.split(' ')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                val filteredBySubstring = if (tokens.isEmpty()) {
                    base
                } else {
                    base.filter { e ->
                        val normText = normalizeText("${e.name} ${e.code}")
                        tokens.all { tok -> normText.contains(tok) }
                    }
                }

                val list = if (filteredBySubstring.isNotEmpty()) filteredBySubstring else base
                list.sortedWith(
                    compareByDescending<Essence> { fuzzyScore("${it.name} ${it.code}", query) }
                        .thenBy { it.name }
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 360.dp)
        ) {
            items(candidates, key = { it.code }) { e ->
                ElevatedCard(onClick = { onAdd(e.code) }) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(e.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(2.dp))
                        Text(e.code, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private fun normalizeText(input: String): String {
    val normalized = Normalizer.normalize(input.lowercase(), Normalizer.Form.NFD)
    return normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
}

// Fuzzy score simple basé sur la distance de Levenshtein (normalisée 0..1)
private fun fuzzyScore(text: String, query: String): Double {
    val t = normalizeText(text)
    val q = normalizeText(query)
    if (q.isEmpty()) return 0.0
    val d = levenshtein(t, q).toDouble()
    return 1.0 - (d / (t.length.coerceAtLeast(q.length).coerceAtLeast(1)))
}

private fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    val dp = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        var prev = i - 1
        dp[0] = i
        for (j in 1..b.length) {
            val temp = dp[j]
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            dp[j] = minOf(
                dp[j] + 1,      // deletion
                dp[j - 1] + 1,  // insertion
                prev + cost     // substitution
            )
            prev = temp
        }
    }
    return dp[b.length]
}

// ── Onglet Évolution : évolution des tiges par année ──────────────────────────

@Composable
private fun PlacetteEvolutionTab(
    tiges: List<Tige>,
    allEssences: List<Essence>,
    animationsEnabled: Boolean,
    onYearClick: (Int) -> Unit = {}
) {
    val context = LocalContext.current

    // Grouper les tiges par année (extraite du timestamp)
    val tigesByYear = remember(tiges) {
        tiges.groupBy { tige ->
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = tige.timestamp }
            cal.get(java.util.Calendar.YEAR)
        }.toSortedMap(reverseOrder())
    }

    if (tiges.isEmpty()) {
        // État vide : aucune tige enregistrée
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Science,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.placette_evolution_empty),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.placette_evolution_empty_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            Text(
                stringResource(R.string.placette_evolution_by_year, tigesByYear.size),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        tigesByYear.forEach { (year, yearTiges) ->
            item(key = "year-$year") {
                YearEvolutionCard(
                    year = year,
                    yearTiges = yearTiges,
                    allEssences = allEssences,
                    animationsEnabled = animationsEnabled,
                    onClick = { onYearClick(year) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun YearEvolutionCard(
    year: Int,
    yearTiges: List<Tige>,
    allEssences: List<Essence>,
    animationsEnabled: Boolean,
    onClick: () -> Unit = {}
) {
    val essenceCount = yearTiges.map { it.essenceCode }.distinct().size
    val meanDiam = yearTiges.map { it.diamCm }.average()

    // Grouper par catégorie de martelage
    val byCategory = remember(yearTiges) {
        yearTiges.groupBy { it.categorie?.uppercase()?.trim() ?: "AUTRE" }
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // En-tête année + stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    year.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        stringResource(R.string.placette_evolution_stems_format, yearTiges.size, essenceCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.placette_evolution_diam_mean_format, meanDiam),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Répartition par catégorie de martelage
            byCategory.forEach { (category, catTiges) ->
                val catColor = martelageCategoryColor(category)
                val catLabel = martelageCategoryLabel(category)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = catColor,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.size(12.dp)
                    ) {}
                    Spacer(Modifier.width(8.dp))
                    Text(
                        catLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        catTiges.size.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = catColor
                    )
                }
            }
        }
    }
}

private fun martelageCategoryColor(category: String): Color = when (category) {
    "AVENIR" -> Color(0xFF4CAF50)
    "RESERVE" -> Color(0xFF2196F3)
    "ENLEVER" -> Color(0xFFF44336)
    "DEPERIR" -> Color(0xFFFF9800)
    "BIODIV" -> Color(0xFF26A69A)
    "AUTRE" -> Color(0xFF607D8B)
    else -> Color(0xFF607D8B)
}

private fun martelageCategoryLabel(category: String): String = when (category) {
    "AVENIR" -> "Avenir"
    "RESERVE" -> "Réserve"
    "ENLEVER" -> "Enlever"
    "DEPERIR" -> "Dépérir"
    "BIODIV" -> "Biodiversité"
    "AUTRE" -> "Non catégorisé"
    else -> category.lowercase().replaceFirstChar { it.uppercase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlacettePhotoViewerDialog(
    file: File,
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val bmp = remember(file.absolutePath) {
        runCatching {
            BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
        }.getOrNull()
    }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // ── Barre supérieure : nom + fermer ──
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        file.nameWithoutExtension,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }
                // ── Image plein cadre + navigation prev/next ──
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (bmp != null) {
                        Image(
                            painter = BitmapPainter(bmp),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    } else {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    if (canPrev) {
                        IconButton(
                            onClick = onPrev,
                            modifier = Modifier.align(Alignment.CenterStart)
                        ) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous))
                        }
                    }
                    if (canNext) {
                        IconButton(
                            onClick = onNext,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = stringResource(R.string.next))
                        }
                    }
                }
                // ── Actions : Renommer / Exporter / Supprimer ──
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = onRename) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.photo_rename))
                    }
                    TextButton(onClick = onExport) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.photo_export))
                    }
                    TextButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
