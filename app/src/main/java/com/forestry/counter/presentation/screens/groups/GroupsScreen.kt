package com.forestry.counter.presentation.screens.groups

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import com.forestry.counter.presentation.utils.StaggerEntrance
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import android.os.Build
import android.app.Activity
import android.net.Uri
import android.widget.ImageView
import eightbitlab.com.blurview.BlurView
import eightbitlab.com.blurview.RenderEffectBlur
import android.view.ViewGroup
import com.forestry.counter.R
import com.forestry.counter.domain.model.Group
import com.forestry.counter.domain.repository.GroupRepository
import com.forestry.counter.data.preferences.UserPreferencesManager
import com.forestry.counter.presentation.utils.ColorUtils
import com.forestry.counter.presentation.components.AppCircularProgress
import com.forestry.counter.presentation.components.AppMiniDialog
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun GroupsScreen(
    groupRepository: GroupRepository,
    onNavigateToGroup: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    preferencesManager: UserPreferencesManager,
    onNavigateToMartelage: ((String?) -> Unit)? = null,
    onNavigateToMap: ((String) -> Unit)? = null,
    onNavigateToIbp: (() -> Unit)? = null
) {
    val viewModel = remember { GroupsViewModel(groupRepository) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedGroupIds by remember { mutableStateOf(setOf<String>()) }
    var renameTargetGroupId by remember { mutableStateOf<String?>(null) }
    var renameGroupName by remember { mutableStateOf("") }
    var colorTargetGroupId by remember { mutableStateOf<String?>(null) }
    var colorHex by remember { mutableStateOf("") }
    var showMartelageScopeDialog by remember { mutableStateOf(false) }
    var showMapScopeDialog by remember { mutableStateOf(false) }

    val glassBlurEnabled = false
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val animationsEnabled by preferencesManager.animationsEnabled.collectAsStateWithLifecycle(initialValue = true)
    val tiltDeg by preferencesManager.tiltDeg.collectAsStateWithLifecycle(initialValue = 2f)
    val pressScale by preferencesManager.pressScale.collectAsStateWithLifecycle(initialValue = 0.96f)
    val haloAlpha by preferencesManager.haloAlpha.collectAsStateWithLifecycle(initialValue = 0.35f)
    val haloWidthDp by preferencesManager.haloWidthDp.collectAsStateWithLifecycle(initialValue = 2)
    val blurRadius by preferencesManager.blurRadius.collectAsStateWithLifecycle(initialValue = 16f)
    val blurOverlayAlpha by preferencesManager.blurOverlayAlpha.collectAsStateWithLifecycle(initialValue = 0.6f)
    val animDurationShort by preferencesManager.animDurationShort.collectAsStateWithLifecycle(initialValue = 120)
    val backgroundImageEnabled by preferencesManager.backgroundImageEnabled.collectAsStateWithLifecycle(initialValue = true)
    val backgroundImageUri by preferencesManager.backgroundImageUri.collectAsStateWithLifecycle(initialValue = null)
    val hasSelection = selectedGroupIds.isNotEmpty()

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
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
            topBar = {
                val useBlurTop = glassBlurEnabled && isDarkTheme && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                val topOverlay = MaterialTheme.colorScheme.surface.copy(alpha = blurOverlayAlpha).toArgb()
                Box {
                    if (useBlurTop) {
                        val ctxTop = LocalContext.current
                        val activityTop = (ctxTop as? Activity)
                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                            factory = {
                                val blurView = BlurView(it)
                                val root = activityTop?.window?.decorView?.findViewById<ViewGroup>(android.R.id.content)
                                if (root != null) {
                                    val windowBg = activityTop.window.decorView.background
                                    blurView.setupWith(root, RenderEffectBlur())
                                        .setFrameClearDrawable(windowBg)
                                        .setBlurRadius(blurRadius)
                                        .setBlurAutoUpdate(true)
                                        .setOverlayColor(topOverlay)
                                }
                                blurView
                            }
                        )
                    }
                    TopAppBar(
                        title = { Text(stringResource(R.string.groups_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = if (useBlurTop) Color.Transparent else MaterialTheme.colorScheme.surface),
                        actions = {
                            if (hasSelection) {
                                IconButton(onClick = {
                                    // Dupliquer tous les projets sélectionnés
                                    selectedGroupIds.toList().forEach { id ->
                                        viewModel.duplicateGroup(id)
                                    }
                                    selectedGroupIds = emptySet()
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.duplicate))
                                }
                                IconButton(onClick = {
                                    // Supprimer tous les projets sélectionnés
                                    selectedGroupIds.toList().forEach { id ->
                                        viewModel.deleteGroup(id)
                                    }
                                    selectedGroupIds = emptySet()
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                                }
                            } else {
                                if (onNavigateToMap != null) {
                                    IconButton(onClick = { showMapScopeDialog = true }) {
                                        Icon(Icons.Default.Map, contentDescription = stringResource(R.string.map_view))
                                    }
                                }
                                if (onNavigateToMartelage != null) {
                                    IconButton(
                                        onClick = {
                                            val groups = (uiState as? GroupsUiState.Success)?.groups.orEmpty()
                                            when {
                                                groups.size <= 1 -> onNavigateToMartelage(groups.firstOrNull()?.id)
                                                else -> showMartelageScopeDialog = true
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Straighten, contentDescription = stringResource(R.string.cd_straighten))
                                    }
                                    IconButton(
                                        onClick = {
                                            val groups = (uiState as? GroupsUiState.Success)?.groups.orEmpty()
                                            when {
                                                groups.size <= 1 -> onNavigateToMartelage(groups.firstOrNull()?.id)
                                                else -> showMartelageScopeDialog = true
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Description, contentDescription = stringResource(R.string.martelage))
                                    }
                                }
                                if (onNavigateToIbp != null) {
                                    IconButton(onClick = { onNavigateToIbp() }) {
                                        Icon(Icons.Default.EmojiNature, contentDescription = stringResource(R.string.ibp_title))
                                    }
                                }
                                IconButton(onClick = onNavigateToSettings) {
                                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                                }
                            }
                        }
                    )
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showCreateDialog = true }
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_group))
                }
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                val state = uiState
                val stateKey = when (state) {
                    is GroupsUiState.Loading -> 0
                    is GroupsUiState.Empty -> 1
                    is GroupsUiState.Success -> 2
                }

                Crossfade(
                    targetState = stateKey,
                    animationSpec = if (animationsEnabled) {
                        tween(durationMillis = 220, easing = FastOutSlowInEasing)
                    } else {
                        tween(durationMillis = 0)
                    },
                    label = "groupsStateCrossfade"
                ) { key ->
                    when (key) {
                        0 -> LoadingScreen()
                        1 -> EmptyState(onCreateGroup = { showCreateDialog = true })
                        else -> {
                            val groups = (state as? GroupsUiState.Success)?.groups.orEmpty()
                            GroupsList(
                                groups = groups,
                                onGroupClick = onNavigateToGroup,
                                onDeleteGroup = viewModel::deleteGroup,
                                onDuplicateGroup = viewModel::duplicateGroup,
                                onRequestRenameGroup = { groupId, currentName ->
                                    renameTargetGroupId = groupId
                                    renameGroupName = currentName
                                },
                                onRequestChangeColor = { groupId, currentColor ->
                                    colorTargetGroupId = groupId
                                    colorHex = currentColor ?: ""
                                },
                                glassBlurEnabled = glassBlurEnabled,
                                isDarkTheme = isDarkTheme,
                                preferencesManager = preferencesManager,
                                animationsEnabled = animationsEnabled,
                                tiltDeg = tiltDeg,
                                pressScale = pressScale,
                                haloAlpha = haloAlpha,
                                haloWidthDp = haloWidthDp,
                                blurRadius = blurRadius,
                                blurOverlayAlpha = blurOverlayAlpha,
                                animDurationShort = animDurationShort,
                                selectedGroupIds = selectedGroupIds,
                                onToggleSelection = { groupId ->
                                    selectedGroupIds = if (selectedGroupIds.contains(groupId)) {
                                        selectedGroupIds - groupId
                                    } else {
                                        selectedGroupIds + groupId
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (showMartelageScopeDialog && onNavigateToMartelage != null) {
                val groups = (uiState as? GroupsUiState.Success)?.groups.orEmpty()
                AlertDialog(
                    onDismissRequest = { showMartelageScopeDialog = false },
                    icon = { Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) },
                    title = { Text(stringResource(R.string.martelage_choose_project_title), style = MaterialTheme.typography.titleMedium) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                onClick = {
                                    showMartelageScopeDialog = false
                                    onNavigateToMartelage(null)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                tonalElevation = 2.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Default.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                    Text(stringResource(R.string.martelage_view_global), style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                            if (groups.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                                groups.forEach { g ->
                                    Surface(
                                        onClick = {
                                            showMartelageScopeDialog = false
                                            onNavigateToMartelage(g.id)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(Icons.Default.Forest, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                            Text(g.name, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showMartelageScopeDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            if (showMapScopeDialog && onNavigateToMap != null) {
                val groups = (uiState as? GroupsUiState.Success)?.groups.orEmpty()
                AlertDialog(
                    onDismissRequest = { showMapScopeDialog = false },
                    icon = { Icon(Icons.Default.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) },
                    title = { Text(stringResource(R.string.map_choose_scope_title), style = MaterialTheme.typography.titleMedium) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                onClick = {
                                    showMapScopeDialog = false
                                    onNavigateToMap("all")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                tonalElevation = 2.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Default.Park, contentDescription = stringResource(R.string.cd_forest), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                    Column {
                                        Text(stringResource(R.string.map_scope_all), style = MaterialTheme.typography.bodyLarge)
                                        Text(stringResource(R.string.map_scope_all_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            Surface(
                                onClick = {
                                    showMapScopeDialog = false
                                    onNavigateToMap("none")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Default.Map, contentDescription = stringResource(R.string.cd_map), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                                    Column {
                                        Text(stringResource(R.string.map_scope_empty), style = MaterialTheme.typography.bodyLarge)
                                        Text(stringResource(R.string.map_scope_empty_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            if (groups.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                                Text(stringResource(R.string.map_scope_by_project), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                                groups.forEach { g ->
                                    Surface(
                                        onClick = {
                                            showMapScopeDialog = false
                                            onNavigateToMap("forest_${g.id}")
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(Icons.Default.Forest, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                            Text(g.name, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showMapScopeDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            if (showCreateDialog) {
                CreateGroupDialog(
                    animationsEnabled = animationsEnabled,
                    onDismiss = { showCreateDialog = false },
                    onConfirm = { name, color ->
                        viewModel.createGroup(name, color)
                        showCreateDialog = false
                    }
                )
            }

            if (renameTargetGroupId != null) {
                AppMiniDialog(
                    onDismissRequest = { renameTargetGroupId = null },
                    animationsEnabled = animationsEnabled,
                    icon = Icons.Default.Edit,
                    title = stringResource(R.string.rename_project),
                    confirmText = stringResource(R.string.save),
                    dismissText = stringResource(R.string.cancel),
                    confirmEnabled = renameGroupName.trim().isNotBlank(),
                    onConfirm = {
                        val id = renameTargetGroupId ?: return@AppMiniDialog
                        viewModel.renameGroup(id, renameGroupName)
                        renameTargetGroupId = null
                    }
                ) {
                    OutlinedTextField(
                        value = renameGroupName,
                        onValueChange = { renameGroupName = it },
                        label = { Text(stringResource(R.string.name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            if (colorTargetGroupId != null) {
                val cleanedColor = remember(colorHex) {
                    colorHex.trim().let { c ->
                        if (c.isBlank()) ""
                        else if (!c.startsWith("#") && c.length == 6) "#${c.uppercase()}" else c.uppercase()
                    }
                }
                val isColorValid = remember(cleanedColor) {
                    cleanedColor.isBlank() || cleanedColor.matches(Regex("^#(?i)[0-9A-F]{6}$"))
                }

                AppMiniDialog(
                    onDismissRequest = { colorTargetGroupId = null },
                    animationsEnabled = animationsEnabled,
                    icon = Icons.Default.Palette,
                    title = stringResource(R.string.change_project_color),
                    confirmText = stringResource(R.string.save),
                    dismissText = stringResource(R.string.cancel),
                    confirmEnabled = isColorValid,
                    onConfirm = {
                        val id = colorTargetGroupId ?: return@AppMiniDialog
                        viewModel.setGroupColor(id, cleanedColor.ifBlank { null })
                        colorTargetGroupId = null
                    }
                ) {
                    OutlinedTextField(
                        value = cleanedColor,
                        onValueChange = { colorHex = it },
                        label = { Text(stringResource(R.string.color_hex_optional)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = !isColorValid
                    )

                    Text(
                        text = stringResource(R.string.project_color),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val options = listOf(
                            // Greens
                            "#2E7D32", "#388E3C", "#43A047", "#4CAF50", "#66BB6A", "#81C784",
                            // Blues
                            "#0D47A1", "#1565C0", "#1976D2", "#1E88E5", "#42A5F5", "#64B5F6",
                            // Purples
                            "#4A148C", "#6A1B9A", "#7B1FA2", "#8E24AA", "#AB47BC", "#BA68C8",
                            // Yellows / oranges
                            "#F9A825", "#FBC02D", "#FDD835", "#FB8C00", "#F57C00", "#EF6C00",
                            // Reds / browns / neutrals
                            "#EF5350", "#E53935", "#8D6E63", "#78909C"
                        )

                        val autoSelected = cleanedColor.isBlank()
                        Surface(
                            modifier = Modifier
                                .size(40.dp)
                                .clickable { colorHex = "" },
                            color = MaterialTheme.colorScheme.surface,
                            shape = CircleShape,
                            border = if (autoSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(text = "A", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        options.forEach { hex ->
                            val col = try {
                                Color(android.graphics.Color.parseColor(hex))
                            } catch (_: Exception) {
                                MaterialTheme.colorScheme.surface
                            }
                            val selected = cleanedColor.equals(hex, ignoreCase = true)
                            Surface(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clickable { colorHex = hex },
                                color = col,
                                shape = CircleShape,
                                border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
                                tonalElevation = if (selected) 6.dp else 0.dp,
                                shadowElevation = if (selected) 6.dp else 0.dp
                            ) {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                strokeWidth = 4.dp,
                color = MaterialTheme.colorScheme.primary
            )
            LinearProgressIndicator(
                modifier = Modifier
                    .width(220.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupsList(
    groups: List<Group>,
    onGroupClick: (String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onDuplicateGroup: (String) -> Unit,
    onRequestRenameGroup: (String, String) -> Unit,
    onRequestChangeColor: (String, String?) -> Unit,
    glassBlurEnabled: Boolean,
    isDarkTheme: Boolean,
    preferencesManager: UserPreferencesManager,
    animationsEnabled: Boolean,
    tiltDeg: Float,
    pressScale: Float,
    haloAlpha: Float,
    haloWidthDp: Int,
    blurRadius: Float,
    blurOverlayAlpha: Float,
    animDurationShort: Int,
    selectedGroupIds: Set<String>,
    onToggleSelection: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(groups, key = { it.id }) { group ->
            val idx = groups.indexOf(group)
            StaggerEntrance(index = idx, staggerMs = 55) {
            GroupCard(
                group = group,
                onClick = { onGroupClick(group.id) },
                onDelete = { onDeleteGroup(group.id) },
                onDuplicate = { onDuplicateGroup(group.id) },
                onRename = { onRequestRenameGroup(group.id, group.name) },
                onChangeColor = { onRequestChangeColor(group.id, group.color) },
                glassBlurEnabled = glassBlurEnabled,
                isDarkTheme = isDarkTheme,
                preferencesManager = preferencesManager,
                animationsEnabled = animationsEnabled,
                tiltDeg = tiltDeg,
                pressScale = pressScale,
                haloAlpha = haloAlpha,
                haloWidthDp = haloWidthDp,
                blurRadius = blurRadius,
                blurOverlayAlpha = blurOverlayAlpha,
                animDurationShort = animDurationShort,
                selected = selectedGroupIds.contains(group.id),
                onToggleSelected = { onToggleSelection(group.id) },
                modifier = if (animationsEnabled) Modifier.animateItemPlacement() else Modifier
            )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupCard(
    group: Group,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: () -> Unit,
    onChangeColor: () -> Unit,
    glassBlurEnabled: Boolean,
    isDarkTheme: Boolean,
    preferencesManager: UserPreferencesManager,
    animationsEnabled: Boolean,
    tiltDeg: Float,
    pressScale: Float,
    haloAlpha: Float,
    haloWidthDp: Int,
    blurRadius: Float,
    blurOverlayAlpha: Float,
    animDurationShort: Int,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val prefHeight by preferencesManager.groupCardHeightFlow(group.id).collectAsStateWithLifecycle(initialValue = 140)
    var customHeight by remember { mutableStateOf(prefHeight.dp) }
    LaunchedEffect(prefHeight) { customHeight = prefHeight.dp }
    val scope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressScale else 1f,
        animationSpec = if (animationsEnabled) spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ) else snap(),
        label = "groupCardScale"
    )
    val cardElevationDp by animateDpAsState(
        targetValue = if (isPressed) 1.dp else 6.dp,
        animationSpec = if (animationsEnabled) spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ) else snap(),
        label = "cardElevationDp"
    )
    val haloAnimAlpha by animateFloatAsState(
        targetValue = if (isPressed && animationsEnabled) haloAlpha else 0f,
        animationSpec = tween(80),
        label = "haloAlpha"
    )

    val baseBg = group.color?.let { c ->
        try { Color(android.graphics.Color.parseColor(c)) } catch (e: Exception) { MaterialTheme.colorScheme.surface }
    } ?: MaterialTheme.colorScheme.surface
    val useBlur = glassBlurEnabled && isDarkTheme && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && group.color == null
    val cardBg = if (useBlur) Color.Transparent else if (group.color == null && isDarkTheme) baseBg.copy(alpha = 0.85f) else baseBg
    val contentColor = if (useBlur) ColorUtils.getContrastingTextColor(baseBg) else ColorUtils.getContrastingTextColor(cardBg)
    val border = if (group.color == null && isDarkTheme) BorderStroke(1.dp, contentColor.copy(alpha = 0.12f)) else null

    Card(
        onClick = {
            if (animationsEnabled) {
                isPressed = true
                scope.launch {
                    delay(animDurationShort.toLong())
                    isPressed = false
                }
            }
            onClick()
        },
        modifier = modifier
            .fillMaxWidth()
            .height(customHeight)
            .graphicsLayer {
                rotationX = if (animationsEnabled) (if (isPressed) tiltDeg else 0f) else 0f
                rotationY = if (animationsEnabled) (if (isPressed) -tiltDeg else 0f) else 0f
            }
            .scale(scale),
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevationDp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = border
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (useBlur) {
                val ctxCard = LocalContext.current
                val activityCard = (ctxCard as? Activity)
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        val blurView = BlurView(it)
                        val root = activityCard?.window?.decorView?.findViewById<ViewGroup>(android.R.id.content)
                        if (root != null) {
                            val windowBg = activityCard.window.decorView.background
                            blurView.setupWith(root, RenderEffectBlur())
                                .setFrameClearDrawable(windowBg)
                                .setBlurRadius(blurRadius)
                                .setBlurAutoUpdate(true)
                                .setOverlayColor(baseBg.copy(alpha = blurOverlayAlpha).toArgb())
                        }
                        blurView
                    }
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        color = contentColor
                    )
                    
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.more_options),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rename_project)) },
                                onClick = {
                                    onRename()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.change_project_color)) },
                                onClick = {
                                    onChangeColor()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Palette, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.duplicate)) },
                                onClick = {
                                    onDuplicate()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete)) },
                                onClick = {
                                    onDelete()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                ) {}
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(20.dp)
                    .clickable { onToggleSelected() }
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                            shape = MaterialTheme.shapes.extraSmall
                        )
                        .border(
                            BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.4f)
                            ),
                            shape = MaterialTheme.shapes.extraSmall
                        )
                )
            }
            if (haloAnimAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(BorderStroke(haloWidthDp.dp, MaterialTheme.colorScheme.primary.copy(alpha = haloAnimAlpha)), shape = MaterialTheme.shapes.medium)
                )
            }
        }
    }
}

@Composable
fun EmptyState(onCreateGroup: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = stringResource(R.string.no_groups),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = stringResource(R.string.create_group_to_start),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(onClick = onCreateGroup) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.create_group))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateGroupDialog(
    animationsEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    var colorHex by remember { mutableStateOf("") }

    val cleanedColor = remember(colorHex) {
        colorHex.trim().let { c ->
            if (c.isBlank()) ""
            else if (!c.startsWith("#") && c.length == 6) "#${c.uppercase()}" else c.uppercase()
        }
    }
    val isColorValid = remember(cleanedColor) {
        cleanedColor.isBlank() || cleanedColor.matches(Regex("^#(?i)[0-9A-F]{6}$"))
    }

    val canCreate = groupName.isNotBlank() && isColorValid
    AppMiniDialog(
        onDismissRequest = onDismiss,
        animationsEnabled = animationsEnabled,
        icon = Icons.Default.FolderOpen,
        title = stringResource(R.string.create_group),
        confirmText = stringResource(R.string.create),
        dismissText = stringResource(R.string.cancel),
        confirmEnabled = canCreate,
        onConfirm = {
            val cleaned = cleanedColor.trim().ifBlank { null }
            onConfirm(groupName, cleaned)
        }
    ) {
        TextField(
            value = groupName,
            onValueChange = { groupName = it },
            label = { Text(stringResource(R.string.group_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = cleanedColor,
            onValueChange = { colorHex = it },
            label = { Text(stringResource(R.string.color_hex_optional)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = !isColorValid
        )

        Text(
            text = stringResource(R.string.project_color),
            style = MaterialTheme.typography.bodyMedium
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val autoSelected = colorHex.isBlank()
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clickable { colorHex = "" },
                color = MaterialTheme.colorScheme.surface,
                shape = CircleShape,
                border = if (autoSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ),
                tonalElevation = if (autoSelected) 6.dp else 0.dp,
                shadowElevation = if (autoSelected) 6.dp else 0.dp
            ) {}

            val options = listOf(
                // Greens / teals
                "#00E676", "#00C853", "#A5D6A7", "#66BB6A", "#2E7D32",
                "#64FFDA", "#1DE9B6", "#26A69A", "#00897B", "#00695C",
                // Blues
                "#81D4FA", "#29B6F6", "#039BE5", "#1E88E5", "#1565C0",
                "#536DFE", "#3D5AFE", "#304FFE",
                // Purples / pinks
                "#B39DDB", "#7E57C2", "#5E35B1", "#D81B60", "#F06292",
                // Oranges / yellows
                "#FFB74D", "#FB8C00", "#FDD835", "#F9A825",
                // Reds / browns / neutrals
                "#EF5350", "#E53935", "#8D6E63", "#78909C"
            )

            options.forEach { hex ->
                val col = try {
                    Color(android.graphics.Color.parseColor(hex))
                } catch (_: Exception) {
                    MaterialTheme.colorScheme.surface
                }
                val selected = cleanedColor.equals(hex, ignoreCase = true)
                Surface(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { colorHex = hex },
                    color = col,
                    shape = CircleShape,
                    border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
                    tonalElevation = if (selected) 6.dp else 0.dp,
                    shadowElevation = if (selected) 6.dp else 0.dp
                ) {}
            }
        }
    }
}
