package com.forestry.counter.presentation.screens.group

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import java.util.Locale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import android.os.Build
import android.app.Activity
import eightbitlab.com.blurview.BlurView
import eightbitlab.com.blurview.RenderEffectBlur
import android.view.ViewGroup
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.atan2
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import com.forestry.counter.R
import com.forestry.counter.domain.model.Counter
import com.forestry.counter.domain.model.TileSize
import com.forestry.counter.domain.repository.CounterRepository
import com.forestry.counter.domain.repository.GroupRepository
import com.forestry.counter.domain.repository.FormulaRepository
import androidx.compose.material3.rememberModalBottomSheetState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull
import com.forestry.counter.domain.usecase.export.ExportDataUseCase
import com.forestry.counter.domain.usecase.import.ImportDataUseCase
import com.forestry.counter.data.preferences.UserPreferencesManager
import com.forestry.counter.presentation.utils.rememberHapticFeedback
import com.forestry.counter.presentation.utils.HapticType
import com.forestry.counter.presentation.utils.rememberSoundFeedback
import com.forestry.counter.domain.model.TargetAction
import com.forestry.counter.presentation.utils.ColorUtils
import com.forestry.counter.presentation.components.AppLinearProgress
import com.forestry.counter.presentation.components.AppMiniDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    groupId: String,
    groupRepository: GroupRepository,
    counterRepository: CounterRepository,
    formulaRepository: FormulaRepository,
    exportDataUseCase: ExportDataUseCase,
    importDataUseCase: ImportDataUseCase,
    preferencesManager: UserPreferencesManager,
    onNavigateToFormulas: () -> Unit,
    onNavigateToCalculator: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val viewModel = remember { GroupViewModel(groupId, groupRepository, counterRepository, formulaRepository) }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CounterSettingsSheet(
    counter: Counter,
    onDismiss: () -> Unit,
    onSave: (Counter, String?) -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onReset: () -> Unit,
    onDuplicate: (Int) -> Unit,
    onDelete: () -> Unit,
    animationsEnabled: Boolean,
    glassBlurEnabled: Boolean,
    blurRadius: Float,
    blurOverlayAlpha: Float
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    var name by remember { mutableStateOf(counter.name) }
    var step by remember { mutableStateOf(counter.step.toString()) }
    var min by remember { mutableStateOf(counter.min?.toString() ?: "") }
    var max by remember { mutableStateOf(counter.max?.toString() ?: "") }
    var target by remember { mutableStateOf(counter.targetValue?.toString() ?: "") }
    var colorHex by remember { mutableStateOf(counter.bgColor ?: "") }
    var tileSize by remember { mutableStateOf(counter.tileSize) }
    var expression by remember { mutableStateOf("") }
    var decimalPlaces by remember { mutableStateOf(counter.decimalPlaces?.toString() ?: "") }
    var initialValue by remember { mutableStateOf(counter.initialValue?.toString() ?: "") }
    var resetValue by remember { mutableStateOf(counter.resetValue?.toString() ?: "") }
    // tri-state: null (inherit), true, false
    var soundOverride by remember { mutableStateOf(counter.soundEnabled?.let { if (it) "on" else "off" } ?: "inherit") }
    var vibrationOverride by remember { mutableStateOf(counter.vibrationEnabled?.let { if (it) "on" else "off" } ?: "inherit") }
    var vibrationIntensity by remember { mutableStateOf((counter.vibrationIntensity ?: 0).toString()) }
    var targetAction by remember { mutableStateOf((counter.targetAction ?: TargetAction.BOTH).name) }

    val currentExpr by produceState(initialValue = "", key1 = counter.formulaId) {
        if (counter.formulaId != null) {
            val f = formulaRepository.getFormulaById(counter.formulaId).firstOrNull()
            value = f?.expression ?: ""
        } else value = ""
    }
    LaunchedEffect(currentExpr) { if (counter.isComputed) expression = currentExpr }

    ModalBottomSheet(
        onDismissRequest = {
            val updated = counter.copy(
                name = name,
                step = step.toDoubleOrNull() ?: counter.step,
                min = min.toDoubleOrNull(),
                max = max.toDoubleOrNull(),
                targetValue = target.toDoubleOrNull(),
                bgColor = colorHex.trim().let { c -> if (c.isBlank()) null else if (!c.startsWith("#") && c.length == 6) "#$c" else c },
                tileSize = tileSize,
                decimalPlaces = decimalPlaces.toIntOrNull(),
                initialValue = initialValue.toDoubleOrNull(),
                resetValue = resetValue.toDoubleOrNull(),
                soundEnabled = when (soundOverride) { "on" -> true; "off" -> false; else -> null },
                vibrationEnabled = when (vibrationOverride) { "on" -> true; "off" -> false; else -> null },
                vibrationIntensity = vibrationIntensity.toIntOrNull(),
                targetAction = runCatching { TargetAction.valueOf(targetAction) }.getOrNull() ?: TargetAction.BOTH
            )
            onSave(updated, expression.takeIf { counter.isComputed })
            onDismiss()
        },
        sheetState = sheetState
    ) {
        val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val useBlurSheet = glassBlurEnabled && isDarkTheme && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        if (useBlurSheet) {
            val sheetOverlay = MaterialTheme.colorScheme.surface.copy(alpha = blurOverlayAlpha).toArgb()
            val ctxSheet = LocalContext.current
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = {
                    val blurView = BlurView(it)
                    try {
                        val activity = (ctxSheet as? Activity)
                        val root = activity?.window?.decorView?.findViewById<ViewGroup>(android.R.id.content)
                        val windowBg = activity?.window?.decorView?.background
                        if (root != null && windowBg != null) {
                            blurView.setupWith(root, RenderEffectBlur())
                                .setFrameClearDrawable(windowBg)
                                .setBlurRadius(blurRadius)
                                .setBlurAutoUpdate(true)
                                .setOverlayColor(sheetOverlay)
                        }
                    } catch (_: Throwable) {}
                    blurView
                }
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = stringResource(R.string.counter_settings_title), style = MaterialTheme.typography.titleLarge)

            // Current value with +/- (disabled for computed)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDecrement, enabled = !counter.isComputed) { Text(stringResource(R.string.symbol_minus)) }
                Text(
                    text = counter.value.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                OutlinedButton(onClick = onIncrement, enabled = !counter.isComputed) { Text(stringResource(R.string.symbol_plus)) }
            }

            TextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.name)) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(value = colorHex, onValueChange = { colorHex = it }, label = { Text(stringResource(R.string.color_hex_label)) }, modifier = Modifier.weight(1f))
                var showColorPicker by remember { mutableStateOf(false) }
                Button(onClick = { showColorPicker = true }) { Text(stringResource(R.string.palette)) }
                if (showColorPicker) {
                    var size by remember { mutableStateOf(IntSize(0, 0)) }
                    var hue by remember { mutableStateOf(120f) }
                    var sat by remember { mutableStateOf(1f) }
                    var value by remember { mutableStateOf(1f) }
                    val hsv = floatArrayOf(hue, sat.coerceIn(0f, 1f), value.coerceIn(0f, 1f))
                    val argb = android.graphics.Color.HSVToColor(hsv)
                    val picked = Color(argb)
                    val wheelSize = 240.dp

                    AppMiniDialog(
                        onDismissRequest = { showColorPicker = false },
                        animationsEnabled = animationsEnabled,
                        icon = Icons.Default.Palette,
                        title = stringResource(R.string.color_palette_title),
                        confirmText = stringResource(R.string.choose),
                        dismissText = stringResource(R.string.cancel),
                        onConfirm = {
                            val r = ((picked.red * 255).roundToInt()).coerceIn(0, 255)
                            val g = ((picked.green * 255).roundToInt()).coerceIn(0, 255)
                            val b = ((picked.blue * 255).roundToInt()).coerceIn(0, 255)
                            colorHex = String.format("#%02X%02X%02X", r, g, b)
                            showColorPicker = false
                        }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(wheelSize)
                                    .onSizeChanged { size = it }
                            ) {
                                Canvas(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .pointerInput(Unit) {
                                            detectDragGestures { change, _ ->
                                                val cx = size.width / 2f
                                                val cy = size.height / 2f
                                                val x = change.position.x - cx
                                                val y = change.position.y - cy
                                                val r = min(1f, kotlin.math.sqrt(x * x + y * y) / (min(cx, cy)))
                                                sat = r
                                                var deg = Math.toDegrees(atan2(y.toDouble(), x.toDouble()))
                                                if (deg < 0) deg += 360.0
                                                hue = deg.toFloat()
                                            }
                                        }
                                ) {
                                    val colors = listOf(
                                        Color(0xFFFF0000),
                                        Color(0xFFFFFF00),
                                        Color(0xFF00FF00),
                                        Color(0xFF00FFFF),
                                        Color(0xFF0000FF),
                                        Color(0xFFFF00FF),
                                        Color(0xFFFF0000)
                                    )
                                    drawCircle(brush = Brush.sweepGradient(colors))
                                    drawCircle(brush = Brush.radialGradient(listOf(Color.White, Color.Transparent)))
                                    val angleRad = (hue / 180f * PI).toFloat()
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    val radius = min(cx, cy) * sat
                                    val px = cx + radius * kotlin.math.cos(angleRad)
                                    val py = cy + radius * kotlin.math.sin(angleRad)
                                    drawCircle(
                                        color = Color.Black,
                                        radius = 8f,
                                        center = androidx.compose.ui.geometry.Offset(px, py),
                                        style = Stroke(width = 3f)
                                    )
                                }
                            }
                            Text(stringResource(R.string.brightness))
                            Slider(value = value, onValueChange = { value = it })
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp),
                                color = picked,
                                shape = MaterialTheme.shapes.small
                            ) {}
                        }
                    }
                }
            }
            var showMoreColors by remember { mutableStateOf(false) }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val autoSelected = colorHex.isBlank()
                Surface(
                    modifier = Modifier
                        .size(34.dp)
                        .clickable { colorHex = "" },
                    color = MaterialTheme.colorScheme.surface,
                    shape = CircleShape,
                    border = if (autoSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    tonalElevation = if (autoSelected) 6.dp else 0.dp,
                    shadowElevation = if (autoSelected) 6.dp else 0.dp
                ) {}
                val options = listOf(
                    "#00E676", "#00C853", "#64FFDA", "#1DE9B6", "#A5FF8B",
                    "#B9F6CA", "#69F0AE", "#18FFFF", "#00BFA5", "#00FF88",
                    "#00FF66", "#00FFA2", "#00FFC6", "#66FF99", "#33FFAA",
                    "#00FFD1", "#11FFEE", "#22E3B3", "#49FFDF", "#5BFFB5",
                    "#C6FF00", "#AEEA00", "#00BCD4", "#00E5FF", "#2979FF", "#00B0FF",
                    "#7C4DFF", "#E040FB", "#FFC400", "#FFAB00", "#FF5252", "#FF6E6E"
                )
                val base = options.take(10)
                (if (showMoreColors) options else base).forEach { hex ->
                    val col = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { MaterialTheme.colorScheme.surface }
                    val selected = colorHex.equals(hex, ignoreCase = true)
                    Surface(
                        modifier = Modifier
                            .size(34.dp)
                            .clickable { colorHex = hex },
                        color = col,
                        shape = CircleShape,
                        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
                        tonalElevation = if (selected) 6.dp else 0.dp,
                        shadowElevation = if (selected) 6.dp else 0.dp
                    ) {}
                }
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { showMoreColors = !showMoreColors }) {
                    Text(stringResource(if (showMoreColors) R.string.less_colors else R.string.more_colors))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = step,
                    onValueChange = { step = it },
                    label = { Text(stringResource(R.string.step)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text(stringResource(R.string.target)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = min,
                    onValueChange = { min = it },
                    label = { Text(stringResource(R.string.min)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextField(
                    value = max,
                    onValueChange = { max = it },
                    label = { Text(stringResource(R.string.max)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = decimalPlaces,
                    onValueChange = { decimalPlaces = it.filter { ch -> ch.isDigit() } },
                    label = { Text(stringResource(R.string.decimal_places)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextField(
                    value = initialValue,
                    onValueChange = { initialValue = it },
                    label = { Text(stringResource(R.string.initial_value)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            TextField(
                value = resetValue,
                onValueChange = { resetValue = it },
                label = { Text(stringResource(R.string.reset_value)) },
                singleLine = true
            )

            if (counter.isComputed) {
                TextField(
                    value = expression,
                    onValueChange = { expression = it },
                    label = { Text(stringResource(R.string.expression)) }
                )
            }

            Text(stringResource(R.string.tile_size))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tileSize == TileSize.SMALL, onClick = { tileSize = TileSize.SMALL }, label = { Text(stringResource(R.string.small)) })
                FilterChip(selected = tileSize == TileSize.NORMAL, onClick = { tileSize = TileSize.NORMAL }, label = { Text(stringResource(R.string.normal_size)) })
                FilterChip(selected = tileSize == TileSize.LARGE, onClick = { tileSize = TileSize.LARGE }, label = { Text(stringResource(R.string.large)) })
            }

            Text(stringResource(R.string.sound))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = soundOverride == "inherit", onClick = { soundOverride = "inherit" }, label = { Text(stringResource(R.string.inherit)) })
                FilterChip(selected = soundOverride == "on", onClick = { soundOverride = "on" }, label = { Text(stringResource(R.string.on_state)) })
                FilterChip(selected = soundOverride == "off", onClick = { soundOverride = "off" }, label = { Text(stringResource(R.string.off_state)) })
            }

            Text(stringResource(R.string.vibration))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = vibrationOverride == "inherit", onClick = { vibrationOverride = "inherit" }, label = { Text(stringResource(R.string.inherit)) })
                FilterChip(selected = vibrationOverride == "on", onClick = { vibrationOverride = "on" }, label = { Text(stringResource(R.string.on_state)) })
                FilterChip(selected = vibrationOverride == "off", onClick = { vibrationOverride = "off" }, label = { Text(stringResource(R.string.off_state)) })
            }

            Text(stringResource(R.string.vibration_intensity_hint))
            TextField(
                value = vibrationIntensity,
                onValueChange = { v -> vibrationIntensity = v.filter { it.isDigit() }.take(1) },
                label = { Text(stringResource(R.string.intensity)) },
                singleLine = true
            )

            Text(stringResource(R.string.target_action))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(TargetAction.NONE, TargetAction.SOUND, TargetAction.VIBRATE, TargetAction.BOTH).forEach { ta ->
                    FilterChip(selected = targetAction == ta.name, onClick = { targetAction = ta.name }, label = { Text(ta.name) })
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onReset, enabled = !counter.isComputed) { Text(stringResource(R.string.reset_to_zero)) }
                Spacer(Modifier.weight(1f))
                var dupCount by remember { mutableStateOf("1") }
                TextField(
                    value = dupCount,
                    onValueChange = { dupCount = it },
                    label = { Text(stringResource(R.string.copies)) },
                    singleLine = true,
                    modifier = Modifier.width(120.dp)
                )
                Button(onClick = { onDuplicate(dupCount.toIntOrNull() ?: 1) }) { Text(stringResource(R.string.duplicate)) }
            }

            Button(
                onClick = onDelete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 10.dp)
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.delete_counter), fontWeight = FontWeight.SemiBold)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = {
                    val updated = counter.copy(
                        name = name,
                        step = step.toDoubleOrNull() ?: counter.step,
                        min = min.toDoubleOrNull(),
                        max = max.toDoubleOrNull(),
                        targetValue = target.toDoubleOrNull(),
                        bgColor = colorHex.trim().let { c -> if (c.isBlank()) null else if (!c.startsWith("#") && c.length == 6) "#$c" else c },
                        tileSize = tileSize,
                        decimalPlaces = decimalPlaces.toIntOrNull(),
                        initialValue = initialValue.toDoubleOrNull(),
                        resetValue = resetValue.toDoubleOrNull(),
                        soundEnabled = when (soundOverride) { "on" -> true; "off" -> false; else -> null },
                        vibrationEnabled = when (vibrationOverride) { "on" -> true; "off" -> false; else -> null },
                        vibrationIntensity = vibrationIntensity.toIntOrNull(),
                        targetAction = runCatching { TargetAction.valueOf(targetAction) }.getOrNull() ?: TargetAction.BOTH
                    )
                    onSave(updated, expression.takeIf { counter.isComputed })
                }) { Text(stringResource(R.string.back)) }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        val updated = counter.copy(
                            name = name,
                            step = step.toDoubleOrNull() ?: counter.step,
                            min = min.toDoubleOrNull(),
                            max = max.toDoubleOrNull(),
                            targetValue = target.toDoubleOrNull(),
                            bgColor = colorHex.trim().let { c -> if (c.isBlank()) null else if (!c.startsWith("#") && c.length == 6) "#$c" else c },
                            tileSize = tileSize,
                            decimalPlaces = decimalPlaces.toIntOrNull(),
                            initialValue = initialValue.toDoubleOrNull(),
                            resetValue = resetValue.toDoubleOrNull(),
                            soundEnabled = when (soundOverride) { "on" -> true; "off" -> false; else -> null },
                            vibrationEnabled = when (vibrationOverride) { "on" -> true; "off" -> false; else -> null },
                            vibrationIntensity = vibrationIntensity.toIntOrNull(),
                            targetAction = runCatching { TargetAction.valueOf(targetAction) }.getOrNull() ?: TargetAction.BOTH
                        )
                        onSave(updated, expression.takeIf { counter.isComputed })
                    },
                    modifier = Modifier
                        .height(56.dp)
                        .widthIn(min = 160.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 12.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.save_and_apply), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
    val group by viewModel.group.collectAsStateWithLifecycle()
    val counters by viewModel.counters.collectAsStateWithLifecycle()
    val selectedCounterId by viewModel.selectedCounterId.collectAsStateWithLifecycle()

    // Preferences
    val animationsEnabled by preferencesManager.animationsEnabled.collectAsStateWithLifecycle(initialValue = true)
    val hapticEnabled by preferencesManager.hapticEnabled.collectAsStateWithLifecycle(initialValue = true)
    val csvSeparator by preferencesManager.csvSeparator.collectAsStateWithLifecycle(initialValue = ",")
    val soundEnabled by preferencesManager.soundEnabled.collectAsStateWithLifecycle(initialValue = true)
    val hapticIntensity by preferencesManager.hapticIntensity.collectAsStateWithLifecycle(initialValue = 2)
    val glassBlurEnabled = false
    val tiltDeg by preferencesManager.tiltDeg.collectAsStateWithLifecycle(initialValue = 2f)
    val pressScale by preferencesManager.pressScale.collectAsStateWithLifecycle(initialValue = 0.96f)
    val haloAlpha by preferencesManager.haloAlpha.collectAsStateWithLifecycle(initialValue = 0.35f)
    val haloWidthDp by preferencesManager.haloWidthDp.collectAsStateWithLifecycle(initialValue = 2)
    val blurRadius by preferencesManager.blurRadius.collectAsStateWithLifecycle(initialValue = 16f)
    val blurOverlayAlpha by preferencesManager.blurOverlayAlpha.collectAsStateWithLifecycle(initialValue = 0.6f)
    val animDurationShort by preferencesManager.animDurationShort.collectAsStateWithLifecycle(initialValue = 120)

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = rememberHapticFeedback()
    val sound = rememberSoundFeedback()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showImportMenu by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var fabMenuExpanded by remember { mutableStateOf(false) }
    var pendingOpenSettingsForId by remember { mutableStateOf<String?>(null) }

    val computedDefaultName = stringResource(R.string.computed_default_name)

    BackHandler(enabled = showSettingsSheet || showImportMenu || showExportMenu || showCreateDialog || fabMenuExpanded) {
        when {
            showSettingsSheet -> {
                showSettingsSheet = false
                viewModel.selectCounter(null)
            }
            showImportMenu -> showImportMenu = false
            showExportMenu -> showExportMenu = false
            showCreateDialog -> showCreateDialog = false
            fabMenuExpanded -> fabMenuExpanded = false
        }
    }

    // Export launchers
    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            val res = exportDataUseCase.exportToJson(uri)
            snackbarHostState.showSnackbar(res.fold({ "Export JSON réussi" }, { "Échec de l'export : ${it.message}" }))
        }
    }
    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) scope.launch {
            val sep = csvSeparator.firstOrNull() ?: ','
            val res = exportDataUseCase.exportToCsv(uri, separator = sep)
            snackbarHostState.showSnackbar(res.fold({ "Export CSV réussi" }, { "Échec de l'export : ${it.message}" }))
        }
    }
    val exportZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) scope.launch {
            val res = exportDataUseCase.exportToZip(uri)
            snackbarHostState.showSnackbar(res.fold({ "Export ZIP réussi" }, { "Échec de l'export : ${it.message}" }))
        }
    }

    val exportExcelLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) scope.launch {
            val res = exportDataUseCase.exportToExcel(uri)
            snackbarHostState.showSnackbar(res.fold({ "Export Excel réussi" }, { "Échec de l'export : ${it.message}" }))
        }
    }

    // Import launcher (OpenDocument), MIME types passed at launch
    var pendingImportType by remember { mutableStateOf("json") }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            val res = if (pendingImportType == "json") {
                importDataUseCase.importFromJson(uri)
            } else if (pendingImportType == "csv") {
                val sep = csvSeparator.firstOrNull() ?: ','
                importDataUseCase.importFromCsv(uri, groupId = groupId, separator = sep)
            } else {
                importDataUseCase.importFromExcel(uri, groupId = groupId)
            }
            snackbarHostState.showSnackbar(res.fold({ r -> "Import terminé : ${r.importedCount} éléments" }, { "Échec de l'import : ${it.message}" }))
        }
    }

    // Feedback helper available to whole screen
    val playFeedback: (Counter, Double) -> Unit = remember(hapticEnabled, soundEnabled, hapticIntensity, haptic, sound) {
        { counter, delta ->
            val vibEnabled = counter.vibrationEnabled ?: hapticEnabled
            val sndEnabled = counter.soundEnabled ?: soundEnabled
            if (vibEnabled) haptic.performWithIntensity(counter.vibrationIntensity ?: hapticIntensity)
            if (sndEnabled) sound.click()

            val target = counter.targetValue
            val willCross = target?.let { counter.value < it && (counter.value + delta) >= it } ?: false
            if (willCross) {
                when (counter.targetAction ?: TargetAction.BOTH) {
                    TargetAction.SOUND -> if (sndEnabled) sound.click()
                    TargetAction.VIBRATE -> if (vibEnabled) haptic.performHapticFeedback(HapticType.SUCCESS)
                    TargetAction.BOTH -> {
                        if (sndEnabled) sound.click()
                        if (vibEnabled) haptic.performHapticFeedback(HapticType.SUCCESS)
                    }
                    TargetAction.NONE -> {}
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
            val useBlurTop = glassBlurEnabled && isDarkTheme && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            Box {
                if (useBlurTop) {
                    val ctxTop = LocalContext.current
                    val topOverlay = MaterialTheme.colorScheme.surface.copy(alpha = blurOverlayAlpha).toArgb()
                    AndroidView(modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp), factory = {
                        val blurView = BlurView(it)
                        try {
                            val activity = (ctxTop as? Activity)
                            val root = activity?.window?.decorView?.findViewById<ViewGroup>(android.R.id.content)
                            val windowBg = activity?.window?.decorView?.background
                            if (root != null && windowBg != null) {
                                blurView.setupWith(root, RenderEffectBlur())
                                    .setFrameClearDrawable(windowBg)
                                    .setBlurRadius(blurRadius)
                                    .setBlurAutoUpdate(true)
                                    .setOverlayColor(topOverlay)
                            }
                        } catch (_: Throwable) {}
                        blurView
                    })
                }
                TopAppBar(
                    title = { Text(group?.name ?: stringResource(R.string.group)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = if (useBlurTop) Color.Transparent else MaterialTheme.colorScheme.surface),
                    actions = {
                    IconButton(onClick = onNavigateToFormulas) {
                        Icon(Icons.Default.Calculate, contentDescription = stringResource(R.string.formulas))
                    }
                    IconButton(onClick = onNavigateToCalculator) {
                        Icon(Icons.Default.Functions, contentDescription = stringResource(R.string.calculator))
                    }
                    Box {
                        IconButton(onClick = { showImportMenu = true }) {
                            Icon(Icons.Default.Upload, contentDescription = stringResource(R.string.import_label))
                        }
                        DropdownMenu(expanded = showImportMenu, onDismissRequest = { showImportMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.import_json)) }, onClick = {
                                pendingImportType = "json"; showImportMenu = false
                                importLauncher.launch(arrayOf("application/json", "application/*"))
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.import_csv)) }, onClick = {
                                pendingImportType = "csv"; showImportMenu = false
                                importLauncher.launch(arrayOf("text/csv", "text/*"))
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.import_excel)) }, onClick = {
                                pendingImportType = "xlsx"; showImportMenu = false
                                importLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel"))
                            })
                        }
                    }
                    Box {
                        IconButton(onClick = { showExportMenu = true }) {
                            Icon(Icons.Default.Download, contentDescription = stringResource(R.string.export))
                        }
                        DropdownMenu(expanded = showExportMenu, onDismissRequest = { showExportMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.export_json)) }, onClick = {
                                showExportMenu = false
                                val ts = java.text.SimpleDateFormat("yyyyMMdd-HHmm").format(java.util.Date())
                                exportJsonLauncher.launch("forestry-$ts.json")
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.export_csv)) }, onClick = {
                                showExportMenu = false
                                val ts = java.text.SimpleDateFormat("yyyyMMdd-HHmm").format(java.util.Date())
                                exportCsvLauncher.launch("forestry-$ts.csv")
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.export_zip)) }, onClick = {
                                showExportMenu = false
                                val ts = java.text.SimpleDateFormat("yyyyMMdd-HHmm").format(java.util.Date())
                                exportZipLauncher.launch("forestry-$ts.zip")
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.export_excel)) }, onClick = {
                                showExportMenu = false
                                val ts = java.text.SimpleDateFormat("yyyyMMdd-HHmm").format(java.util.Date())
                                exportExcelLauncher.launch("forestry-$ts.xlsx")
                            })
                        }
                    }
                    }
                )
            }
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { fabMenuExpanded = true }
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_counter))
                }
                DropdownMenu(expanded = fabMenuExpanded, onDismissRequest = { fabMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.standard_counter)) },
                        onClick = {
                            fabMenuExpanded = false
                            showCreateDialog = true
                        },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.calculated_counter)) },
                        onClick = {
                            fabMenuExpanded = false
                            val newId = java.util.UUID.randomUUID().toString()
                            // Create a computed counter placeholder and open settings when ready
                            viewModel.createCounter(
                                id = newId,
                                name = computedDefaultName,
                                step = 1.0,
                                expression = "0"
                            )
                            pendingOpenSettingsForId = newId
                        },
                        leadingIcon = { Icon(Icons.Default.Calculate, contentDescription = null) }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Crossfade(
                targetState = counters.isEmpty(),
                animationSpec = if (animationsEnabled) {
                    tween(durationMillis = 220, easing = FastOutSlowInEasing)
                } else {
                    tween(durationMillis = 0)
                },
                label = "countersCrossfade"
            ) { isEmpty ->
                if (isEmpty) {
                    EmptyCountersState(onCreateCounter = { showCreateDialog = true })
                } else {
                    CountersGrid(
                        counters = counters,
                        onCounterClick = { id ->
                            val c = counters.find { it.id == id } ?: return@CountersGrid
                            playFeedback(c, +c.step)
                            viewModel.incrementCounter(id)
                        },
                        onCounterLongPress = { counterId ->
                            if (hapticEnabled) haptic.performHapticFeedback(HapticType.MEDIUM)
                            viewModel.selectCounter(counterId)
                            showSettingsSheet = true
                        },
                        animationsEnabled = animationsEnabled,
                        glassBlurEnabled = glassBlurEnabled,
                        tiltDeg = tiltDeg,
                        pressScale = pressScale,
                        haloAlpha = haloAlpha,
                        haloWidthDp = haloWidthDp,
                        blurRadius = blurRadius,
                        blurOverlayAlpha = blurOverlayAlpha,
                        animDurationShort = animDurationShort
                    )
                }
            }
        }

        if (showCreateDialog) {
            CreateCounterDialog(
                animationsEnabled = animationsEnabled,
                onDismiss = { showCreateDialog = false },
                onConfirm = { name, step, min, max, target, colorHex, expression, tileSize ->
                    viewModel.createCounter(
                        name = name,
                        step = step,
                        min = min,
                        max = max,
                        targetValue = target,
                        bgColor = colorHex?.trim()?.let { c -> if (c.isEmpty()) null else if (!c.startsWith("#") && c.length == 6) "#$c" else c },
                        expression = expression?.takeIf { it.isNotBlank() },
                        tileSize = tileSize
                    )
                    showCreateDialog = false
                }
            )
        }

        // Auto-open settings when a newly created calculated counter appears
        LaunchedEffect(counters, pendingOpenSettingsForId) {
            val id = pendingOpenSettingsForId
            if (id != null && counters.any { it.id == id }) {
                viewModel.selectCounter(id)
                showSettingsSheet = true
                pendingOpenSettingsForId = null
            }
        }

        if (showSettingsSheet && selectedCounterId != null) {
            val counter = counters.find { it.id == selectedCounterId }
            if (counter != null) {
                CounterSettingsSheet(
                    counter = counter,
                    onDismiss = {
                        showSettingsSheet = false
                        viewModel.selectCounter(null)
                    },
                    onSave = { updated, expr ->
                        viewModel.updateCounterWithExpression(updated, expr)
                        showSettingsSheet = false
                        viewModel.selectCounter(null)
                    },
                    onIncrement = {
                        playFeedback(counter, +counter.step)
                        viewModel.incrementCounter(counter.id)
                    },
                    onDecrement = {
                        playFeedback(counter, -counter.step)
                        viewModel.decrementCounter(counter.id)
                    },
                    onReset = { viewModel.resetCounter(counter.id) },
                    onDuplicate = { count -> viewModel.duplicateCounter(counter.id, count) },
                    onDelete = {
                        viewModel.deleteCounter(counter.id)
                        showSettingsSheet = false
                        viewModel.selectCounter(null)
                    },
                    animationsEnabled = animationsEnabled,
                    glassBlurEnabled = glassBlurEnabled,
                    blurRadius = blurRadius,
                    blurOverlayAlpha = blurOverlayAlpha
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CountersGrid(
    counters: List<Counter>,
    onCounterClick: (String) -> Unit,
    onCounterLongPress: (String) -> Unit,
    animationsEnabled: Boolean,
    glassBlurEnabled: Boolean,
    tiltDeg: Float,
    pressScale: Float,
    haloAlpha: Float,
    haloWidthDp: Int,
    blurRadius: Float,
    blurOverlayAlpha: Float,
    animDurationShort: Int
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(counters, key = { it.id }) { counter ->
            CounterCard(
                counter = counter,
                onClick = { onCounterClick(counter.id) },
                onLongPress = { onCounterLongPress(counter.id) },
                animationsEnabled = animationsEnabled,
                glassBlurEnabled = glassBlurEnabled,
                tiltDeg = tiltDeg,
                pressScale = pressScale,
                haloAlpha = haloAlpha,
                haloWidthDp = haloWidthDp,
                blurRadius = blurRadius,
                blurOverlayAlpha = blurOverlayAlpha,
                animDurationShort = animDurationShort,
                modifier = if (animationsEnabled) Modifier.animateItemPlacement() else Modifier
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CounterCard(
    counter: Counter,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    animationsEnabled: Boolean,
    glassBlurEnabled: Boolean,
    tiltDeg: Float,
    pressScale: Float,
    haloAlpha: Float,
    haloWidthDp: Int,
    blurRadius: Float,
    blurOverlayAlpha: Float,
    animDurationShort: Int,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressScale else 1f,
        animationSpec = if (animationsEnabled) tween(durationMillis = animDurationShort) else tween(durationMillis = 0),
        label = "scale"
    )

    val cardHeight = when (counter.tileSize) {
        TileSize.SMALL -> 120.dp
        TileSize.NORMAL -> 140.dp
        TileSize.LARGE -> 180.dp
    }

    val baseBg = counter.bgColor?.let {
        try { Color(android.graphics.Color.parseColor(it)) } catch (e: Exception) { MaterialTheme.colorScheme.surface }
    } ?: MaterialTheme.colorScheme.surface
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val useBlur = glassBlurEnabled && counter.bgColor == null && isDarkTheme && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val cardBg = if (useBlur) Color.Transparent else if (counter.bgColor == null && isDarkTheme) baseBg.copy(alpha = 0.85f) else baseBg
    val contentColor = if (useBlur) ColorUtils.getContrastingTextColor(baseBg) else ColorUtils.getContrastingTextColor(cardBg)
    val cardBorder = if (counter.bgColor == null && isDarkTheme) BorderStroke(1.dp, contentColor.copy(alpha = 0.12f)) else null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(cardHeight)
            .graphicsLayer {
                rotationX = if (animationsEnabled) (if (isPressed) tiltDeg else 0f) else 0f
                rotationY = if (animationsEnabled) (if (isPressed) -tiltDeg else 0f) else 0f
            }
            .scale(scale)
            .combinedClickable(
                onClick = {
                    isPressed = true
                    onClick()
                    // Reset after a short delay for animation
                    scope.launch {
                        delay(100)
                        isPressed = false
                    }
                },
                onLongClick = {
                    isPressed = true
                    onLongPress()
                    scope.launch {
                        delay(120)
                        isPressed = false
                    }
                }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBg
        ),
        border = cardBorder
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val ctx = LocalContext.current
            if (useBlur) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        val blurView = BlurView(it)
                        try {
                            val activity = (ctx as? Activity)
                            val root = activity?.window?.decorView?.findViewById<ViewGroup>(android.R.id.content)
                            val windowBg = activity?.window?.decorView?.background
                            if (root != null && windowBg != null) {
                                blurView.setupWith(root, RenderEffectBlur())
                                    .setFrameClearDrawable(windowBg)
                                    .setBlurRadius(blurRadius)
                                    .setBlurAutoUpdate(true)
                                    .setOverlayColor(baseBg.copy(alpha = blurOverlayAlpha).toArgb())
                            }
                        } catch (_: Throwable) {}
                        blurView
                    }
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                val displayValue = counter.decimalPlaces?.let { places ->
                    try { String.format(Locale.getDefault(), "%.${places}f", counter.value) } catch (e: Exception) { counter.value.toString() }
                } ?: counter.value.toInt().toString()
                Box(modifier = Modifier.align(Alignment.Center)) {
                    Text(
                        text = displayValue,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                if (counter.targetValue != null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                    ) {
                        AppLinearProgress(
                            progress = { counter.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            baseBg = baseBg
                        )
                        Text(
                            text = stringResource(R.string.target_value, counter.targetValue.toInt()),
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            if (isPressed && animationsEnabled) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(
                            BorderStroke(
                                haloWidthDp.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = haloAlpha)
                            ),
                            shape = MaterialTheme.shapes.medium
                        )
                )
            }
        }
    }
}

@Composable
fun EmptyCountersState(onCreateCounter: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.GridView,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.no_counters),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.add_counter_to_start),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onCreateCounter) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.add_counter))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateCounterDialog(
    animationsEnabled: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, Double?, Double?, Double?, String?, String?, TileSize) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var step by remember { mutableStateOf("1") }
    var min by remember { mutableStateOf("") }
    var max by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var colorHex by remember { mutableStateOf("") }
    var isComputed by remember { mutableStateOf(false) }
    var expression by remember { mutableStateOf("") }
    var tileSize by remember { mutableStateOf(TileSize.NORMAL) }

    val canCreate = name.isNotBlank()
    AppMiniDialog(
        onDismissRequest = onDismiss,
        animationsEnabled = animationsEnabled,
        icon = if (isComputed) Icons.Default.Functions else Icons.Default.Add,
        title = stringResource(R.string.create_counter_title),
        confirmText = stringResource(R.string.create),
        dismissText = stringResource(R.string.cancel),
        confirmEnabled = canCreate,
        onConfirm = {
            if (!canCreate) return@AppMiniDialog
            onConfirm(
                name,
                step.toDoubleOrNull() ?: 1.0,
                min.toDoubleOrNull(),
                max.toDoubleOrNull(),
                target.toDoubleOrNull(),
                colorHex,
                if (isComputed) expression else null,
                tileSize
            )
        }
    ) {
        TextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.counter_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        TextField(
            value = step,
            onValueChange = { step = it },
            label = { Text(stringResource(R.string.counter_step)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        TextField(
            value = min,
            onValueChange = { min = it },
            label = { Text(stringResource(R.string.min_optional)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        TextField(
            value = max,
            onValueChange = { max = it },
            label = { Text(stringResource(R.string.max_optional)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        TextField(
            value = target,
            onValueChange = { target = it },
            label = { Text(stringResource(R.string.target_optional)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        TextField(
            value = colorHex,
            onValueChange = { colorHex = it },
            label = { Text(stringResource(R.string.color_hex_optional)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Text(stringResource(R.string.quick_colors))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val options = listOf("#00E676", "#00C853", "#64FFDA", "#1DE9B6", "#A5FF8B", "#B9F6CA", "#69F0AE", "#18FFFF", "#00BFA5", "#00FF88")
            options.forEach { hex ->
                val col = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { MaterialTheme.colorScheme.surface }
                val selected = colorHex.equals(hex, ignoreCase = true)
                Surface(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { colorHex = hex },
                    color = col,
                    shape = MaterialTheme.shapes.small,
                    tonalElevation = if (selected) 4.dp else 0.dp,
                    shadowElevation = if (selected) 4.dp else 0.dp
                ) {}
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isComputed, onCheckedChange = { isComputed = it })
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.computed_use_formula))
        }
        if (isComputed) {
            TextField(
                value = expression,
                onValueChange = { expression = it },
                label = { Text(stringResource(R.string.computed_expression_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false
            )
        }

        // Tile size selector
        Text(stringResource(R.string.tile_size))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tileSize == TileSize.SMALL, onClick = { tileSize = TileSize.SMALL }, label = { Text(stringResource(R.string.small)) })
            FilterChip(selected = tileSize == TileSize.NORMAL, onClick = { tileSize = TileSize.NORMAL }, label = { Text(stringResource(R.string.normal_size)) })
            FilterChip(selected = tileSize == TileSize.LARGE, onClick = { tileSize = TileSize.LARGE }, label = { Text(stringResource(R.string.large)) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CounterOptionsSheet(
    counter: Counter,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onDuplicate: (Int) -> Unit,
    onDelete: () -> Unit,
    animationsEnabled: Boolean = true
) {
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = counter.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.reset_to_zero)) },
                leadingContent = {
                    Icon(Icons.Default.RestartAlt, contentDescription = null)
                },
                modifier = Modifier.clickable {
                    onReset()
                }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.duplicate)) },
                leadingContent = {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                },
                modifier = Modifier.clickable {
                    showDuplicateDialog = true
                }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.delete)) },
                leadingContent = {
                    Icon(Icons.Default.Delete, contentDescription = null)
                },
                modifier = Modifier.clickable {
                    showDeleteConfirm = true
                }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showDuplicateDialog) {
        var count by remember { mutableStateOf("1") }
        AppMiniDialog(
            onDismissRequest = { showDuplicateDialog = false },
            animationsEnabled = animationsEnabled,
            icon = Icons.Default.ContentCopy,
            title = stringResource(R.string.duplicate_counter),
            confirmText = stringResource(R.string.duplicate),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                onDuplicate(count.toIntOrNull() ?: 1)
                showDuplicateDialog = false
            }
        ) {
            TextField(
                value = count,
                onValueChange = { count = it },
                label = { Text(stringResource(R.string.number_of_copies)) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showDeleteConfirm) {
        AppMiniDialog(
            onDismissRequest = { showDeleteConfirm = false },
            animationsEnabled = animationsEnabled,
            icon = Icons.Default.Delete,
            title = stringResource(R.string.delete_counter_title),
            description = stringResource(R.string.delete_counter_confirm, counter.name),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            confirmIsDestructive = true,
            onConfirm = {
                onDelete()
                showDeleteConfirm = false
            }
        )
    }
}
