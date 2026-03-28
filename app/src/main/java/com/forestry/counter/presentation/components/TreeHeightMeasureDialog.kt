package com.forestry.counter.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forestry.counter.R
import com.forestry.counter.domain.location.*
import com.forestry.counter.domain.location.TwoStationResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.*

/**
 * Dialogue multi-étapes pour mesurer la hauteur d'un arbre par la méthode clinométrique.
 *
 * @param skipWaistWarning  Si vrai, passe directement à la saisie de distance (avertissement déjà vu)
 * @param phoneHeightM      Hauteur initiale du téléphone (m) — réglable par l'utilisateur
 * @param onDismiss         Fermeture sans résultat
 * @param onResult          Résultat confirmé : hauteur en mètres
 * @param onSkipWarningForever  L'utilisateur demande à ne plus voir l'avertissement
 */
@Composable
fun TreeHeightMeasureDialog(
    skipWaistWarning: Boolean,
    phoneHeightM: Float = 1.5f,
    onDismiss: () -> Unit,
    onResult: (heightM: Double) -> Unit,
    onSkipWarningForever: () -> Unit
) {
    val context = LocalContext.current
    val haptic  = LocalHapticFeedback.current
    val view    = LocalView.current
    val capability = remember { TreeHeightMeasureTool.detectCapability(context) }

    // Garde l'écran allumé pendant toute la mesure (essentiel en forêt)
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val startStep = if (skipWaistWarning) MeasureStep.DISTANCE else MeasureStep.WAIST_WARNING
    var step by remember { mutableStateOf(startStep) }

    var distanceInput        by remember { mutableStateOf("") }
    var angleTop             by remember { mutableStateOf<Double?>(null) }
    var angleBase            by remember { mutableStateOf<Double?>(null) }
    var liveAngle            by remember { mutableStateOf<AngleMeasurement?>(null) }
    var phoneHeight          by remember { mutableStateOf(phoneHeightM.toDouble()) }
    var useBaseAngle         by remember { mutableStateOf(false) }
    var neverShowWarning     by remember { mutableStateOf(false) }
    var manualAngleTopInput  by remember { mutableStateOf("") }
    var manualAngleBaseInput by remember { mutableStateOf("") }
    var autoCaptureProgress  by remember { mutableStateOf(0f) }
    var compassReading  by remember { mutableStateOf<CompassReading?>(null) }
    var baroAltitude    by remember { mutableStateOf<Float?>(null) }
    var showGpsDistance by remember { mutableStateOf(false) }

    // 2-station extras
    var measureMode      by remember { mutableStateOf(MeasureMode.SINGLE) }
    var distance2Input   by remember { mutableStateOf("") }
    var angleTop2        by remember { mutableStateOf<Double?>(null) }
    var showGpsDistance2 by remember { mutableStateOf(false) }

    val distanceM = distanceInput.replace(',', '.').toDoubleOrNull()

    val effectiveAngleTop: Double? = if (capability != SensorCapability.NONE) angleTop
    else manualAngleTopInput.replace(',', '.').toDoubleOrNull()

    val effectiveAngleBase: Double? = if (capability != SensorCapability.NONE) angleBase
    else manualAngleBaseInput.replace(',', '.').toDoubleOrNull()

    // Collecte du capteur
    LaunchedEffect(capability) {
        if (capability != SensorCapability.NONE) {
            TreeHeightMeasureTool.pitchFlow(context, capability).collectLatest { meas ->
                liveAngle = meas
            }
        }
    }

    // Boussole (F3)
    LaunchedEffect(Unit) {
        if (CompassManager.isAvailable(context)) {
            CompassManager.bearingFlow(context).collectLatest { compassReading = it }
        }
    }
    // Baromètre (F4)
    LaunchedEffect(Unit) {
        TreeHeightMeasureTool.barometerAltitudeFlow(context)?.collectLatest { baroAltitude = it }
    }

    // Auto-capture : déclenche après ~1,5 s de stabilité ≥ 82 %
    val isAngleStep   = step == MeasureStep.ANGLE_TOP || step == MeasureStep.ANGLE_BASE || step == MeasureStep.STATION2_ANGLE_TOP
    val alreadyCaught = when (step) {
        MeasureStep.ANGLE_TOP          -> angleTop != null
        MeasureStep.ANGLE_BASE         -> angleBase != null
        MeasureStep.STATION2_ANGLE_TOP -> angleTop2 != null
        else                           -> false
    }

    LaunchedEffect(step, alreadyCaught) {
        autoCaptureProgress = 0f
        if (!isAngleStep || alreadyCaught || capability == SensorCapability.NONE) return@LaunchedEffect
        while (true) {
            delay(100L)
            val s = liveAngle?.stabilityScore ?: 0f
            if (s >= 0.82f) {
                autoCaptureProgress = (autoCaptureProgress + 0.067f).coerceAtMost(1f)
                if (autoCaptureProgress >= 1f) {
                    val avg = liveAngle?.avgPitchDeg?.toDouble()
                    if (avg != null) {
                        when (step) {
                            MeasureStep.ANGLE_TOP          -> angleTop  = avg
                            MeasureStep.ANGLE_BASE         -> angleBase = avg
                            MeasureStep.STATION2_ANGLE_TOP -> angleTop2 = avg
                            else -> {}
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        autoCaptureProgress = 0f
                    }
                    break
                }
            } else {
                autoCaptureProgress = (autoCaptureProgress - 0.15f).coerceAtLeast(0f)
            }
        }
    }

    // Indicateur d'étape
    val hasBaseStep   = useBaseAngle && capability != SensorCapability.NONE
    val hasWaistStep  = !skipWaistWarning
    val isTwoStation  = measureMode == MeasureMode.TWO_STATION
    val totalSteps = 2 + (if (hasWaistStep) 1 else 0) + (if (hasBaseStep) 1 else 0) +
                     (if (isTwoStation) 2 else 0) + 1
    val currentStepNum = when (step) {
        MeasureStep.WAIST_WARNING      -> 1
        MeasureStep.DISTANCE           -> if (hasWaistStep) 2 else 1
        MeasureStep.ANGLE_TOP          -> if (hasWaistStep) 3 else 2
        MeasureStep.ANGLE_BASE         -> if (hasWaistStep) 4 else 3
        MeasureStep.STATION2_DISTANCE  -> totalSteps - 2
        MeasureStep.STATION2_ANGLE_TOP -> totalSteps - 1
        MeasureStep.RESULT             -> totalSteps
    }

    val distance2M = distance2Input.replace(',', '.').toDoubleOrNull()

    val result = remember(effectiveAngleTop, distanceM, phoneHeight, effectiveAngleBase, useBaseAngle, capability) {
        val top = effectiveAngleTop
        val d   = distanceM
        if (top != null && d != null && d > 0) {
            TreeHeightMeasureTool.calculateHeight(
                distanceM    = d,
                angleTopDeg  = top,
                angleBaseDeg = if (useBaseAngle) (effectiveAngleBase ?: 0.0) else 0.0,
                phoneHeightM = phoneHeight,
                capability   = capability
            )
        } else null
    }

    val twoStationResult = remember(result, angleTop2, distance2M, phoneHeight, capability) {
        val top2 = angleTop2; val d2 = distance2M; val r1 = result
        if (isTwoStation && top2 != null && d2 != null && d2 > 0 && r1 != null) {
            val top1 = effectiveAngleTop ?: return@remember null
            val d1   = distanceM       ?: return@remember null
            TreeHeightMeasureTool.calculateHeight2Stations(
                distance1M   = d1, angleTop1Deg = top1,
                angleBase1Deg = if (useBaseAngle) (effectiveAngleBase ?: 0.0) else 0.0,
                distance2M   = d2, angleTop2Deg = top2,
                phoneHeightM = phoneHeight, capability = capability
            )
        } else null
    }

    if (showGpsDistance) {
        GpsDistanceMeasureDialog(
            onResult  = { d -> distanceInput = String.format("%.1f", d); showGpsDistance = false },
            onDismiss = { showGpsDistance = false }
        )
    }
    if (showGpsDistance2) {
        GpsDistanceMeasureDialog(
            onResult  = { d -> distance2Input = String.format("%.1f", d); showGpsDistance2 = false },
            onDismiss = { showGpsDistance2 = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        icon  = { Icon(Icons.Default.Height, contentDescription = null) },
        title = {
            Column {
                Text(stringResource(R.string.height_measure_title))
                if (step != MeasureStep.WAIST_WARNING) {
                    Text(
                        stringResource(R.string.height_measure_step_format, currentStepNum, totalSteps),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (step != MeasureStep.WAIST_WARNING) {
                    SensorCapabilityBadge(capability)
                }
                when (step) {
                    MeasureStep.WAIST_WARNING -> WaistWarningStep(
                        neverShow = neverShowWarning,
                        onNeverShowChange = { neverShowWarning = it }
                    )
                    MeasureStep.DISTANCE -> DistanceStep(
                        distanceInput        = distanceInput,
                        onDistanceChange     = { distanceInput = it },
                        distanceM            = distanceM,
                        phoneHeight          = phoneHeight,
                        onPhoneHeightChange  = { phoneHeight = it },
                        useBaseAngle         = useBaseAngle,
                        onUseBaseAngleChange = { useBaseAngle = it },
                        capability           = capability,
                        compassReading       = compassReading,
                        onGpsDistanceMeasure = { showGpsDistance = true },
                        measureMode          = measureMode,
                        onMeasureModeChange  = { measureMode = it }
                    )
                    MeasureStep.ANGLE_TOP -> AngleCaptureStep(
                        label               = stringResource(R.string.height_measure_aim_top),
                        description         = stringResource(R.string.height_measure_aim_top_desc),
                        liveAngle           = liveAngle,
                        capability          = capability,
                        capturedAngle       = angleTop,
                        autoCaptureProgress = autoCaptureProgress,
                        isBaseAngle         = false,
                        manualInput         = manualAngleTopInput,
                        onManualChange      = { manualAngleTopInput = it },
                        onCapture = {
                            val avg = liveAngle?.avgPitchDeg?.toDouble() ?: liveAngle?.pitchDeg?.toDouble()
                            if (avg != null) { angleTop = avg; haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                        },
                        onRecapture     = { angleTop = null; autoCaptureProgress = 0f },
                        onCameraCapture = { deg -> angleTop = deg; haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                    )
                    MeasureStep.ANGLE_BASE -> AngleCaptureStep(
                        label               = stringResource(R.string.height_measure_aim_base),
                        description         = stringResource(R.string.height_measure_aim_base_desc),
                        liveAngle           = liveAngle,
                        capability          = capability,
                        capturedAngle       = angleBase,
                        autoCaptureProgress = autoCaptureProgress,
                        isBaseAngle         = true,
                        manualInput         = manualAngleBaseInput,
                        onManualChange      = { manualAngleBaseInput = it },
                        onCapture = {
                            val avg = liveAngle?.avgPitchDeg?.toDouble() ?: liveAngle?.pitchDeg?.toDouble()
                            if (avg != null) { angleBase = avg; haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                        },
                        onRecapture     = { angleBase = null; autoCaptureProgress = 0f },
                        onCameraCapture = { deg -> angleBase = deg; haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                    )
                    MeasureStep.STATION2_DISTANCE -> DistanceStep(
                        distanceInput        = distance2Input,
                        onDistanceChange     = { distance2Input = it },
                        distanceM            = distance2M,
                        phoneHeight          = phoneHeight,
                        onPhoneHeightChange  = { phoneHeight = it },
                        useBaseAngle         = false,
                        onUseBaseAngleChange = {},
                        capability           = capability,
                        compassReading       = compassReading,
                        onGpsDistanceMeasure = { showGpsDistance2 = true },
                        headerText           = stringResource(R.string.height_station2_title),
                        descriptionText      = stringResource(R.string.height_station2_move)
                    )
                    MeasureStep.STATION2_ANGLE_TOP -> AngleCaptureStep(
                        label               = stringResource(R.string.height_station2_label) + " — " + stringResource(R.string.height_measure_aim_top),
                        description         = stringResource(R.string.height_measure_aim_top_desc),
                        liveAngle           = liveAngle,
                        capability          = capability,
                        capturedAngle       = angleTop2,
                        autoCaptureProgress = autoCaptureProgress,
                        isBaseAngle         = false,
                        manualInput         = "",
                        onManualChange      = {},
                        onCapture = {
                            val avg = liveAngle?.avgPitchDeg?.toDouble() ?: liveAngle?.pitchDeg?.toDouble()
                            if (avg != null) { angleTop2 = avg; haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                        },
                        onRecapture     = { angleTop2 = null; autoCaptureProgress = 0f },
                        onCameraCapture = { deg -> angleTop2 = deg; haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                    )
                    MeasureStep.RESULT -> {
                        if (isTwoStation && twoStationResult != null) {
                            TwoStationResultStep(twoStationResult = twoStationResult, baroAltitude = baroAltitude)
                        } else {
                            ResultStep(
                                result       = result,
                                angleTop     = effectiveAngleTop,
                                angleBase    = if (useBaseAngle) effectiveAngleBase else null,
                                distanceM    = distanceM,
                                phoneHeight  = phoneHeight,
                                baroAltitude = baroAltitude
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                MeasureStep.WAIST_WARNING -> Button(onClick = {
                    if (neverShowWarning) onSkipWarningForever()
                    step = MeasureStep.DISTANCE
                }) { Text(stringResource(R.string.height_measure_waist_ok)) }

                MeasureStep.DISTANCE -> Button(
                    onClick  = { step = MeasureStep.ANGLE_TOP },
                    enabled  = distanceM != null && distanceM > 0
                ) { Text(stringResource(R.string.height_measure_next)) }

                MeasureStep.ANGLE_TOP -> Button(
                    onClick = {
                        step = when {
                            useBaseAngle && capability != SensorCapability.NONE -> MeasureStep.ANGLE_BASE
                            isTwoStation                                         -> MeasureStep.STATION2_DISTANCE
                            else                                                 -> MeasureStep.RESULT
                        }
                    },
                    enabled = effectiveAngleTop != null
                ) { Text(stringResource(R.string.height_measure_next)) }

                MeasureStep.ANGLE_BASE -> Button(
                    onClick  = { step = if (isTwoStation) MeasureStep.STATION2_DISTANCE else MeasureStep.RESULT },
                    enabled  = effectiveAngleBase != null
                ) { Text(stringResource(R.string.height_measure_next)) }

                MeasureStep.STATION2_DISTANCE -> Button(
                    onClick  = { step = MeasureStep.STATION2_ANGLE_TOP },
                    enabled  = distance2M != null && distance2M > 0
                ) { Text(stringResource(R.string.height_measure_next)) }

                MeasureStep.STATION2_ANGLE_TOP -> Button(
                    onClick  = { step = MeasureStep.RESULT },
                    enabled  = angleTop2 != null
                ) { Text(stringResource(R.string.height_measure_next)) }

                MeasureStep.RESULT -> Button(
                    onClick = {
                        val h = if (isTwoStation) twoStationResult?.combinedHeightM
                                else result?.heightM
                        h?.let { onResult(it) }
                    },
                    enabled = if (isTwoStation) twoStationResult != null else result != null
                ) { Text(stringResource(R.string.height_measure_use_result)) }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (step.ordinal > 0) {
                    TextButton(onClick = {
                        step = when (step) {
                            MeasureStep.DISTANCE           -> MeasureStep.WAIST_WARNING
                            MeasureStep.ANGLE_TOP          -> MeasureStep.DISTANCE
                            MeasureStep.ANGLE_BASE         -> MeasureStep.ANGLE_TOP
                            MeasureStep.STATION2_DISTANCE  -> if (hasBaseStep) MeasureStep.ANGLE_BASE else MeasureStep.ANGLE_TOP
                            MeasureStep.STATION2_ANGLE_TOP -> MeasureStep.STATION2_DISTANCE
                            MeasureStep.RESULT             -> if (isTwoStation) MeasureStep.STATION2_ANGLE_TOP
                                                             else if (hasBaseStep) MeasureStep.ANGLE_BASE
                                                             else MeasureStep.ANGLE_TOP
                            else                           -> MeasureStep.DISTANCE
                        }
                    }) { Text(stringResource(R.string.height_measure_back)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}

// ── Étapes ──────────────────────────────────────────────────────────────────

private enum class MeasureStep {
    WAIST_WARNING, DISTANCE, ANGLE_TOP, ANGLE_BASE, STATION2_DISTANCE, STATION2_ANGLE_TOP, RESULT
}

private enum class MeasureMode { SINGLE, TWO_STATION }

// ── Sous-composables ─────────────────────────────────────────────────────────

@Composable
private fun WaistWarningStep(neverShow: Boolean, onNeverShowChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.AccessibilityNew,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.height_measure_waist_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.height_measure_waist_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Checkbox(checked = neverShow, onCheckedChange = onNeverShowChange)
            Text(
                stringResource(R.string.height_measure_waist_never_show),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SensorCapabilityBadge(capability: SensorCapability) {
    val (labelRes, color) = when (capability) {
        SensorCapability.HIGH   -> R.string.height_sensor_high   to Color(0xFF2E7D32)
        SensorCapability.MEDIUM -> R.string.height_sensor_medium to Color(0xFF1565C0)
        SensorCapability.BASIC  -> R.string.height_sensor_basic  to Color(0xFFE65100)
        SensorCapability.NONE   -> R.string.height_sensor_none   to MaterialTheme.colorScheme.error
    }
    val icon = when (capability) {
        SensorCapability.HIGH   -> Icons.Default.GpsFixed
        SensorCapability.MEDIUM -> Icons.Default.GpsNotFixed
        SensorCapability.BASIC  -> Icons.Default.LocationSearching
        SensorCapability.NONE   -> Icons.Default.GpsOff
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = color)
            Text(
                stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DistanceStep(
    distanceInput: String,
    onDistanceChange: (String) -> Unit,
    distanceM: Double?,
    phoneHeight: Double,
    onPhoneHeightChange: (Double) -> Unit,
    useBaseAngle: Boolean,
    onUseBaseAngleChange: (Boolean) -> Unit,
    capability: SensorCapability,
    compassReading: CompassReading? = null,
    onGpsDistanceMeasure: () -> Unit = {},
    measureMode: MeasureMode = MeasureMode.SINGLE,
    onMeasureModeChange: (MeasureMode) -> Unit = {},
    headerText: String? = null,
    descriptionText: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // Mode selector (1 station / 2 stations) — only shown on station 1
        if (headerText == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(MeasureMode.SINGLE to R.string.height_mode_single,
                       MeasureMode.TWO_STATION to R.string.height_mode_two).forEach { (mode, labelRes) ->
                    val selected = measureMode == mode
                    FilterChip(
                        selected = selected,
                        onClick  = { onMeasureModeChange(mode) },
                        label    = { Text(stringResource(labelRes), style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = if (selected) ({
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                        }) else null
                    )
                }
            }
            if (measureMode == MeasureMode.TWO_STATION) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CompareArrows, null, modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(
                            stringResource(R.string.height_mode_two_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        } else {
            // Station 2 header
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Column {
                        Text(headerText, style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer)
                        if (descriptionText != null)
                            Text(descriptionText, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
        }

        Text(
            stringResource(R.string.height_measure_distance_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Boussole (F3) + bouton GPS distance (F5)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            compassReading?.let { cr ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Explore, null, modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(
                            stringResource(R.string.height_measure_compass_badge,
                                cr.cardinalPoint, cr.bearingDeg.toInt()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            } ?: Spacer(Modifier.weight(1f))
            FilledTonalButton(
                onClick = onGpsDistanceMeasure,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.GpsFixed, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.height_measure_gps_distance),
                    style = MaterialTheme.typography.labelSmall)
            }
        }

        // Chips de distances prédéfinies
        Text(
            stringResource(R.string.height_measure_distance_chips),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(10, 15, 20, 25).forEach { d ->
                val selected = distanceInput == d.toString()
                FilterChip(
                    selected = selected,
                    onClick  = { onDistanceChange(d.toString()) },
                    label    = { Text("${d}m", style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        OutlinedTextField(
            value = distanceInput,
            onValueChange = onDistanceChange,
            label = { Text(stringResource(R.string.height_measure_distance)) },
            suffix = { Text("m") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next
            ),
            singleLine = true,
            isError = distanceInput.isNotEmpty() && distanceM == null,
            placeholder = { Text("ex : 15") },
            supportingText = {
                Text(
                    stringResource(R.string.height_measure_tip_steps),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )

        var phoneInput by remember(phoneHeight) {
            mutableStateOf(String.format("%.1f", phoneHeight))
        }
        OutlinedTextField(
            value = phoneInput,
            onValueChange = {
                phoneInput = it
                it.replace(',', '.').toDoubleOrNull()?.let { v ->
                    if (v in 0.3..2.5) onPhoneHeightChange(v)
                }
            },
            label = { Text(stringResource(R.string.height_measure_phone_height)) },
            suffix = { Text("m") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done
            ),
            singleLine = true,
            supportingText = {
                Text(
                    stringResource(R.string.height_measure_phone_height_hint),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        )

        if (capability != SensorCapability.NONE) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Checkbox(checked = useBaseAngle, onCheckedChange = onUseBaseAngleChange)
                Column {
                    Text(
                        stringResource(R.string.height_measure_use_base_angle),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        stringResource(R.string.height_measure_use_base_angle_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AngleCaptureStep(
    label: String,
    description: String,
    liveAngle: AngleMeasurement?,
    capability: SensorCapability,
    capturedAngle: Double?,
    autoCaptureProgress: Float,
    isBaseAngle: Boolean,
    manualInput: String,
    onManualChange: (String) -> Unit,
    onCapture: () -> Unit,
    onRecapture: () -> Unit,
    onCameraCapture: (Double) -> Unit = {}
) {
    val warningColor = Color(0xFFE65100)
    var showCameraAim by remember { mutableStateOf(capability != SensorCapability.NONE) }

    if (showCameraAim) {
        HeightCameraAimOverlay(
            liveAngle    = liveAngle,
            isBaseAngle  = isBaseAngle,
            onCapture    = { deg -> showCameraAim = false; onCameraCapture(deg) },
            onDismiss    = { showCameraAim = false }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (capability != SensorCapability.NONE) {
            val stability   = liveAngle?.stabilityScore ?: 0f
            val displayDeg  = liveAngle?.avgPitchDeg ?: 0f

            // Anneau auto-capture + jauge d'angle
            AngleGauge(
                angleDeg            = displayDeg,
                captured            = capturedAngle != null,
                autoCaptureProgress = autoCaptureProgress
            )

            liveAngle?.let { meas ->
                val angleColor by animateColorAsState(
                    targetValue = if (stability > 0.7f) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    label = "angleColor"
                )
                Text(
                    "${String.format("%.1f", meas.avgPitchDeg)}°",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = angleColor
                )
                StabilityBar(stability)

                // Auto-capture countdown
                if (stability >= 0.82f && capturedAngle == null) {
                    val secsLeft = ((1f - autoCaptureProgress) / 0.067f * 0.1f).toInt().coerceAtLeast(0)
                    Text(
                        stringResource(R.string.height_measure_auto_capture_countdown, secsLeft),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Avertissements d'angle
                val livePitch = meas.avgPitchDeg.toDouble()
                val angleWarning: String? = when {
                    !isBaseAngle && livePitch > 80.0  -> stringResource(R.string.height_measure_angle_warning_high)
                    !isBaseAngle && livePitch < 5.0 && livePitch > -5.0 -> stringResource(R.string.height_measure_angle_warning_low)
                    isBaseAngle  && livePitch > 5.0   -> stringResource(R.string.height_measure_angle_warning_base_pos)
                    else -> null
                }
                angleWarning?.let { msg ->
                    Surface(
                        color = warningColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = warningColor
                            )
                            Text(
                                msg,
                                style = MaterialTheme.typography.labelSmall,
                                color = warningColor
                            )
                        }
                    }
                }
            }

            if (capturedAngle == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = onCapture,
                        enabled = stability > 0.35f
                    ) {
                        Icon(Icons.Default.Adjust, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.height_measure_capture))
                    }
                    FilledTonalButton(
                        onClick = { showCameraAim = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.height_camera_aim_button))
                    }
                }
            } else {
                // Résultat capturé + bouton recapturer
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                stringResource(
                                    R.string.height_measure_captured_format,
                                    String.format("%.1f", capturedAngle)
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        TextButton(
                            onClick = onRecapture,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.height_measure_recapture),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = manualInput,
                onValueChange = onManualChange,
                label = { Text(stringResource(R.string.height_measure_angle_manual)) },
                suffix = { Text("°") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                singleLine = true,
                supportingText = {
                    Text(stringResource(R.string.height_measure_angle_manual_hint))
                }
            )
        }
    }
}

@Composable
private fun AngleGauge(angleDeg: Float, captured: Boolean, autoCaptureProgress: Float = 0f) {
    val primary       = MaterialTheme.colorScheme.primary
    val onSurface     = MaterialTheme.colorScheme.onSurface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val autoColor     = Color(0xFF2E7D32)  // vert auto-capture

    val animAngle by animateFloatAsState(
        targetValue = angleDeg.coerceIn(-90f, 90f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness    = Spring.StiffnessMediumLow
        ),
        label = "gaugeAngle"
    )
    val animAutoCapture by animateFloatAsState(
        targetValue = autoCaptureProgress,
        animationSpec = tween(100),
        label = "autoCapture"
    )
    val needleColor = if (captured) primary else onSurface.copy(alpha = 0.6f)

    Canvas(modifier = Modifier.size(96.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r  = size.minDimension / 2f - 10f

        // Cercle fond
        drawArc(
            color = surfaceVariant,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = 6f)
        )
        // Anneau d'auto-capture (vert, tourne dans le sens horaire)
        if (animAutoCapture > 0f) {
            drawArc(
                color = autoColor.copy(alpha = 0.8f),
                startAngle = -90f,
                sweepAngle = 360f * animAutoCapture,
                useCenter = false,
                style = Stroke(width = 6f, cap = StrokeCap.Round)
            )
        }
        // Arc de l'angle courant
        drawArc(
            color = needleColor.copy(alpha = 0.25f),
            startAngle = -90f,
            sweepAngle = animAngle * 2.5f,
            useCenter = false,
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )
        // Aiguille
        val rad = Math.toRadians((animAngle - 90.0))
        val nx = cx + r * cos(rad).toFloat()
        val ny = cy + r * sin(rad).toFloat()
        drawLine(
            color = needleColor,
            start = Offset(cx, cy),
            end   = Offset(nx, ny),
            strokeWidth = 3.5f,
            cap = StrokeCap.Round
        )
        drawCircle(color = needleColor, radius = 5f, center = Offset(cx, cy))
    }
}

@Composable
private fun StabilityBar(stability: Float) {
    val animStability by animateFloatAsState(targetValue = stability, label = "stability")
    val color = when {
        animStability > 0.7f -> MaterialTheme.colorScheme.primary
        animStability > 0.4f -> Color(0xFFE65100)
        else -> MaterialTheme.colorScheme.error
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LinearProgressIndicator(
            progress = { animStability },
            modifier = Modifier.width(110.dp).height(4.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(
            stringResource(R.string.height_measure_stability_format, (stability * 100).toInt()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun ResultStep(
    result: TreeHeightResult?,
    angleTop: Double?,
    angleBase: Double?,
    distanceM: Double?,
    phoneHeight: Double,
    baroAltitude: Float? = null
) {
    if (result == null) {
        Text(
            stringResource(R.string.height_measure_result_incomplete),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.height_measure_result_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    String.format("%.1f m", result.heightM),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    stringResource(
                        R.string.height_measure_precision_format,
                        String.format("%.2f", result.precisionM)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DetailRow(
                    stringResource(R.string.height_measure_detail_distance),
                    "${distanceM?.let { String.format("%.1f", it) } ?: "-"} m"
                )
                DetailRow(
                    stringResource(R.string.height_measure_detail_angle_top),
                    "${angleTop?.let { String.format("%.1f", it) } ?: "-"}°"
                )
                angleBase?.let {
                    DetailRow(
                        stringResource(R.string.height_measure_detail_angle_base),
                        "${String.format("%.1f", it)}°"
                    )
                }
                DetailRow(
                    stringResource(R.string.height_measure_detail_phone_h),
                    "${String.format("%.2f", phoneHeight)} m"
                )
                baroAltitude?.let { alt ->
                    DetailRow(
                        stringResource(R.string.height_measure_altitude_baro, alt.toInt()),
                        ""
                    )
                }
            }
        }
    }
}

@Composable
private fun TwoStationResultStep(
    twoStationResult: TwoStationResult,
    baroAltitude: Float? = null
) {
    val r = twoStationResult
    val (agreementColor, agreementText) = when {
        r.agreementPct >= 90 -> Color(0xFF2E7D32) to R.string.height_agreement_good
        r.agreementPct >= 70 -> Color(0xFFE65100) to R.string.height_agreement_medium
        else                 -> MaterialTheme.colorScheme.error to R.string.height_agreement_poor
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Main result card
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.height_two_station_result_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    String.format("%.1f m", r.combinedHeightM),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    stringResource(R.string.height_measure_precision_format, String.format("%.2f", r.precisionM)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(
                        R.string.height_confidence_band,
                        String.format("%.1f", r.confidenceLowM),
                        String.format("%.1f", r.confidenceHighM)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // Agreement badge
        Surface(
            color = agreementColor.copy(alpha = 0.10f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CompareArrows, null, modifier = Modifier.size(14.dp), tint = agreementColor)
                Column {
                    Text(
                        stringResource(R.string.height_agreement_format, r.agreementPct),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = agreementColor
                    )
                    Text(
                        stringResource(agreementText),
                        style = MaterialTheme.typography.labelSmall,
                        color = agreementColor
                    )
                }
            }
        }
        // Per-station details
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DetailRow(stringResource(R.string.height_station1_label), String.format("%.1f m", r.height1M))
                DetailRow(stringResource(R.string.height_station2_label), String.format("%.1f m", r.height2M))
                baroAltitude?.let { alt ->
                    DetailRow(stringResource(R.string.height_measure_altitude_baro, alt.toInt()), "")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
