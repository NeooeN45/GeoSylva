package com.forestry.counter.presentation.screens.forestry

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.Manifest
import androidx.core.content.ContextCompat
import com.forestry.counter.R
import com.forestry.counter.domain.model.*
import com.forestry.counter.domain.repository.IbpRepository
import com.forestry.counter.domain.repository.PlacetteRepository
import com.forestry.counter.domain.usecase.export.IbpPdfExporter
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IbpEvaluationScreen(
    parcelleId: String,
    placetteId: String,
    ibpRepository: IbpRepository,
    placetteRepository: PlacetteRepository? = null,
    userPreferences: com.forestry.counter.data.preferences.UserPreferencesManager? = null,
    evaluationId: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToReference: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic  = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // ── ViewModel ────────────────────────────────────────────────────
    val viewModel = remember(parcelleId, placetteId, evaluationId) {
        IbpEvaluationViewModel(
            parcelleId         = parcelleId,
            placetteId         = placetteId,
            evaluationId       = evaluationId,
            ibpRepository      = ibpRepository,
            placetteRepository = placetteRepository
        )
    }
    val existing by viewModel.existingEvaluation.collectAsStateWithLifecycle()
    val placette by viewModel.placette.collectAsStateWithLifecycle()
    val placetteLabel by viewModel.placetteLabel.collectAsStateWithLifecycle()

    val ibpAnswersSaver = remember {
        val key = "ibp_answers_json"
        val safeJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val serializer = IbpAnswers.serializer()
        mapSaver<IbpAnswers>(
            save = { mapOf(key to safeJson.encodeToString(serializer, it)) },
            restore = { map -> runCatching {
            val raw = safeJson.decodeFromString(serializer, map[key] as String)
            if (raw.schemaVersion < 2) raw.migrateToV2() else raw
        }.getOrElse { IbpAnswers.new() } }
        )
    }
    var answers by rememberSaveable(stateSaver = ibpAnswersSaver) { mutableStateOf(IbpAnswers.new()) }
    var evaluatorName by rememberSaveable { mutableStateOf("") }
    var globalNote by rememberSaveable { mutableStateOf("") }
    var observationDate by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var growthConditionsStr by rememberSaveable { mutableStateOf(IbpGrowthConditions.LOWLAND.name) }
    var ibpModeStr by rememberSaveable { mutableStateOf(IbpMode.COMPLET.name) }
    var gpsLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var gpsLon by rememberSaveable { mutableStateOf<Double?>(null) }
    var initialized by rememberSaveable { mutableStateOf(false) }

    val growthConditions = runCatching { IbpGrowthConditions.valueOf(growthConditionsStr) }.getOrElse { IbpGrowthConditions.LOWLAND }
    val ibpMode = runCatching { IbpMode.valueOf(ibpModeStr) }.getOrElse { IbpMode.COMPLET }

    LaunchedEffect(existing) {
        val ex = existing
        if (!initialized && ex != null) {
            answers = ex.answers
            evaluatorName = ex.evaluatorName
            globalNote = ex.globalNote
            observationDate = ex.observationDate
            growthConditionsStr = ex.growthConditions.name
            ibpModeStr = ex.ibpMode.name
            if (gpsLat == null) gpsLat = ex.latitude
            if (gpsLon == null) gpsLon = ex.longitude
            initialized = true
        } else if (!initialized && evaluationId == null) {
            initialized = true
        }
    }

    fun captureGps() {
        try {
            // Vérifier les permissions avant d'accéder à la localisation
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return // Permissions non accordées, ne pas tenter d'accéder à la localisation
            }
            
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (loc != null) { gpsLat = loc.latitude; gpsLon = loc.longitude }
        } catch (_: Exception) {}
    }

    LaunchedEffect(Unit) { captureGps() }

    var showResultDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val ibpOnboardingSeen by remember(userPreferences) {
        userPreferences?.ibpOnboardingSeen ?: kotlinx.coroutines.flow.flowOf(true)
    }.collectAsStateWithLifecycle(initialValue = true)
    var onboardingStep by remember { mutableStateOf(0) }
    val showOnboarding = !ibpOnboardingSeen && onboardingStep < 3

    val exportPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val eval = IbpEvaluation(
                    id = evaluationId ?: UUID.randomUUID().toString(),
                    placetteId = placetteId,
                    parcelleId = parcelleId,
                    observationDate = observationDate,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    evaluatorName = evaluatorName,
                    answers = answers,
                    globalNote = globalNote,
                    growthConditions = growthConditions,
                    ibpMode = ibpMode,
                    latitude = gpsLat,
                    longitude = gpsLon
                )
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        IbpPdfExporter.export(context, eval, out, placetteLabel)
                    }
                }.onFailure {
                    snackbar.showSnackbar(context.getString(R.string.export_error))
                }
            }
        }
    }

    fun saveEvaluation() {
        scope.launch {
            val id = evaluationId ?: UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val eval = IbpEvaluation(
                id = id,
                placetteId = placetteId,
                parcelleId = parcelleId,
                observationDate = observationDate,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                evaluatorName = evaluatorName,
                answers = answers,
                globalNote = globalNote,
                growthConditions = growthConditions,
                ibpMode = ibpMode,
                latitude = gpsLat,
                longitude = gpsLon
            )
            viewModel.saveEvaluation(eval)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            snackbar.showSnackbar(context.getString(R.string.ibp_saved))
        }
    }

    val savedAnswers = existing?.answers ?: IbpAnswers.new()
    val savedName = existing?.evaluatorName ?: ""
    val savedNote = existing?.globalNote ?: ""
    val savedConditions = existing?.growthConditions ?: IbpGrowthConditions.LOWLAND
    val savedMode = existing?.ibpMode ?: IbpMode.COMPLET
    val hasUnsavedChanges = initialized && (answers != savedAnswers || evaluatorName != savedName || globalNote != savedNote || growthConditions != savedConditions || ibpMode != savedMode)

    BackHandler(enabled = hasUnsavedChanges) { showUnsavedDialog = true }

    val scoreTotal = answers.scoreTotal
    val scoreA = answers.scoreA
    val scoreB = answers.scoreB
    val level = IbpLevel.fromScore(scoreTotal)
    val levelColor = ibpLevelColor(level)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ibp_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (hasUnsavedChanges) showUnsavedDialog = true else onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigateToReference?.invoke() }) {
                        Icon(Icons.Default.MenuBook, contentDescription = "Guide IBP")
                    }
                    if (answers.isComplete) {
                        IconButton(onClick = { showResultDialog = true }) {
                            Icon(Icons.Default.Assessment, contentDescription = stringResource(R.string.ibp_result))
                        }
                    }
                    IconButton(onClick = {
                        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(observationDate))
                        exportPdfLauncher.launch("IBP_${dateStr}.pdf")
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = stringResource(R.string.ibp_export_pdf))
                    }
                    IconButton(onClick = { saveEvaluation() }) {
                        Icon(Icons.Default.Save, contentDescription = stringResource(R.string.save))
                    }
                    if (evaluationId != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.ibp_delete), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                )
            )
        }
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(bottom = 32.dp)
        ) {
            // ── Score Header ──────────────────────────────────────────
            IbpScoreHeader(
                scoreTotal = scoreTotal,
                scoreA = scoreA,
                scoreB = scoreB,
                answeredCount = answers.answeredCount,
                levelColor = levelColor,
                level = level
            )

            // ── Meta (evaluator + date + growth conditions) ────────────────────────────
            IbpMetaSection(
                evaluatorName = evaluatorName,
                onEvaluatorChange = { evaluatorName = it },
                observationDate = observationDate,
                onDateClick = { showDatePicker = true },
                growthConditions = growthConditions,
                onGrowthConditionsChange = { growthConditionsStr = it.name }
            )

            // ── Mode selector ────────────────────────────────────────
            IbpModeSelectorRow(
                selectedMode = ibpMode,
                onModeSelected = { ibpModeStr = it.name }
            )

            // ── Group A ─────────────────────────────────────────────
            val modeGroupA = ibpMode.criteria().filter { it.group == IbpGroup.A }
            val modeGroupB = ibpMode.criteria().filter { it.group == IbpGroup.B }
            if (modeGroupA.isNotEmpty()) {
                IbpGroupHeader(
                    label = stringResource(R.string.ibp_group_a_title),
                    subtitle = stringResource(R.string.ibp_group_a_subtitle),
                    score = scoreA,
                    maxScore = 35,
                    color = Color(0xFF2E7D32)
                )
                modeGroupA.forEach { cid ->
                    IbpCriterionCard(
                        criterionId = cid,
                        currentValue = answers.get(cid),
                        currentDetails = answers.getDetails(cid),
                        currentCounts = answers.counts,
                        growthConditions = growthConditions,
                        onAnswer = { v -> answers = answers.set(cid, v) },
                        onDetailsChange = { items -> answers = answers.setDetails(cid, items) },
                        onCountChange = { key, v -> answers = answers.withCount(key, v) }
                    )
                }
            }

            // ── Group B ─────────────────────────────────────────────
            if (modeGroupB.isNotEmpty()) {
                IbpGroupHeader(
                    label = stringResource(R.string.ibp_group_b_title),
                    subtitle = stringResource(R.string.ibp_group_b_subtitle),
                    score = scoreB,
                    maxScore = 15,
                    color = Color(0xFF1565C0)
                )
                modeGroupB.forEach { cid ->
                    IbpCriterionCard(
                        criterionId = cid,
                        currentValue = answers.get(cid),
                        currentDetails = answers.getDetails(cid),
                        currentCounts = answers.counts,
                        growthConditions = growthConditions,
                        onAnswer = { v -> answers = answers.set(cid, v) },
                        onDetailsChange = { items -> answers = answers.setDetails(cid, items) },
                        onCountChange = { key, v -> answers = answers.withCount(key, v) }
                    )
                }
            }

            // ── Global note ──────────────────────────────────────────
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.ibp_global_note), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = globalNote,
                    onValueChange = { globalNote = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
                    placeholder = { Text(stringResource(R.string.ibp_global_note_hint), style = MaterialTheme.typography.bodySmall) }
                )
            }

            // ── Result card if complete ───────────────────────────────────
            if (answers.isComplete) {
                IbpResultCard(
                    level = level,
                    scoreTotal = scoreTotal,
                    scoreA = scoreA,
                    scoreB = scoreB,
                    levelColor = levelColor,
                    answers = answers
                )
            }
        }
    }

    if (showOnboarding) {
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Default.EmojiNature, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(36.dp)) },
            title = {
                Text(stringResource(when (onboardingStep) {
                    0 -> R.string.ibp_onboarding_title_1
                    1 -> R.string.ibp_onboarding_title_2
                    else -> R.string.ibp_onboarding_title_3
                }))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(when (onboardingStep) {
                        0 -> R.string.ibp_onboarding_body_1
                        1 -> R.string.ibp_onboarding_body_2
                        else -> R.string.ibp_onboarding_body_3
                    }))
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        repeat(3) { i ->
                            if (i > 0) Spacer(Modifier.width(6.dp))
                            Box(Modifier.size(10.dp), contentAlignment = Alignment.Center) {
                                Surface(
                                    modifier = Modifier.size(if (i == onboardingStep) 10.dp else 6.dp),
                                    shape = CircleShape,
                                    color = if (i == onboardingStep) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                ) {}
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (onboardingStep < 2) {
                        onboardingStep++
                    } else {
                        scope.launch { userPreferences?.setIbpOnboardingSeen() }
                        onboardingStep = 3
                    }
                }) {
                    Text(if (onboardingStep < 2) stringResource(R.string.ibp_onboarding_next) else stringResource(R.string.ibp_onboarding_start))
                }
            }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = observationDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { observationDate = it }
                    showDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) { DatePicker(state = datePickerState) }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.ibp_delete)) },
            text = { Text(stringResource(R.string.ibp_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    scope.launch {
                        if (evaluationId != null) viewModel.deleteEvaluation(evaluationId)
                        onNavigateBack()
                    }
                }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
            title = { Text(stringResource(R.string.ibp_unsaved_title)) },
            text = { Text(stringResource(R.string.ibp_unsaved_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    saveEvaluation()
                    onNavigateBack()
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    onNavigateBack()
                }) { Text(stringResource(R.string.discard)) }
            }
        )
    }

    if (showResultDialog) {
        IbpResultDialog(
            answers = answers,
            scoreTotal = scoreTotal,
            scoreA = scoreA,
            scoreB = scoreB,
            level = level,
            onDismiss = { showResultDialog = false }
        )
    }
}

/* ─────────────── Score Header ─────────────────────────────────── */
@Composable
private fun IbpScoreHeader(
    scoreTotal: Int,
    scoreA: Int,
    scoreB: Int,
    answeredCount: Int,
    levelColor: Color,
    level: IbpLevel
) {
    val progress = if (scoreTotal >= 0) scoreTotal / 50f else 0f
    val animProgress by animateFloatAsState(progress, animationSpec = tween(600), label = "ibpProg")

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(stringResource(R.string.ibp_score_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (scoreTotal >= 0) "$scoreTotal" else "—",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = levelColor,
                            modifier = Modifier.alignByBaseline()
                        )
                        Text(" / 50", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.alignByBaseline())
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    IbpLevelBadge(level = level, color = levelColor)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$answeredCount / 10 ${stringResource(R.string.ibp_answered)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            // Progress bar
            Box(modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (animProgress > 0f) {
                    Box(modifier = Modifier.fillMaxWidth(animProgress).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF66BB6A), levelColor))))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                ScorePill(label = "A", score = scoreA, max = 35, color = Color(0xFF2E7D32))
                ScorePill(label = "B", score = scoreB, max = 15, color = Color(0xFF1565C0))
            }
        }
    }
}

@Composable
private fun ScorePill(label: String, score: Int, max: Int, color: Color) {
    Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(20.dp)) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text("$label : ${if (score >= 0) "$score" else "—"} / $max", style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}

/* ─────────────── Level Badge ──────────────────────────────────── */
@Composable
fun IbpLevelBadge(level: IbpLevel, color: Color, modifier: Modifier = Modifier) {
    Surface(color = color, shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Text(
            ibpLevelLabel(level),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun ibpLevelLabel(level: IbpLevel): String = when (level) {
    IbpLevel.VERY_LOW  -> stringResource(R.string.ibp_level_very_low)
    IbpLevel.LOW       -> stringResource(R.string.ibp_level_low)
    IbpLevel.MEDIUM    -> stringResource(R.string.ibp_level_medium)
    IbpLevel.GOOD      -> stringResource(R.string.ibp_level_good)
    IbpLevel.VERY_GOOD -> stringResource(R.string.ibp_level_very_good)
}

fun ibpLevelColor(level: IbpLevel): Color = when (level) {
    IbpLevel.VERY_LOW  -> Color(0xFFC62828)
    IbpLevel.LOW       -> Color(0xFFE65100)
    IbpLevel.MEDIUM    -> Color(0xFFF9A825)
    IbpLevel.GOOD      -> Color(0xFF2E7D32)
    IbpLevel.VERY_GOOD -> Color(0xFF1565C0)
}

/* ─────────────── Group Header ─────────────────────────────────── */
@Composable
private fun IbpGroupHeader(label: String, subtitle: String, score: Int, maxScore: Int, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp, 40.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(color = color.copy(alpha = .15f), shape = RoundedCornerShape(10.dp)) {
            Text(
                "${if (score >= 0) score else "\u2014"} / $maxScore",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = color.copy(alpha = .2f))
}

/* ───────────────── Meta Section ───────────────── */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IbpMetaSection(
    evaluatorName: String,
    onEvaluatorChange: (String) -> Unit,
    observationDate: Long,
    onDateClick: () -> Unit = {},
    growthConditions: IbpGrowthConditions = IbpGrowthConditions.LOWLAND,
    onGrowthConditionsChange: (IbpGrowthConditions) -> Unit = {}
) {
    val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(observationDate))
    var conditionsExpanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = evaluatorName,
                onValueChange = onEvaluatorChange,
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.ibp_evaluator), style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Box(modifier = Modifier.width(140.dp).clickable { onDateClick() }) {
                OutlinedTextField(
                    value = dateStr,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.ibp_date), style = MaterialTheme.typography.bodySmall) },
                    readOnly = true,
                    enabled = false,
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
        ExposedDropdownMenuBox(
            expanded = conditionsExpanded,
            onExpandedChange = { conditionsExpanded = it }
        ) {
            OutlinedTextField(
                value = ibpGrowthConditionsLabel(growthConditions),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.ibp_growth_conditions), style = MaterialTheme.typography.bodySmall) },
                leadingIcon = { Icon(Icons.Default.Landscape, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = conditionsExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                singleLine = true,
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )
            ExposedDropdownMenu(expanded = conditionsExpanded, onDismissRequest = { conditionsExpanded = false }) {
                IbpGrowthConditions.values().forEach { cond ->
                    DropdownMenuItem(
                        text = { Text(ibpGrowthConditionsLabel(cond)) },
                        leadingIcon = { Text(ibpGrowthConditionsIcon(cond), style = MaterialTheme.typography.bodyLarge) },
                        onClick = { onGrowthConditionsChange(cond); conditionsExpanded = false }
                    )
                }
            }
        }
    }
}

@Composable
fun ibpGrowthConditionsLabel(cond: IbpGrowthConditions): String = when (cond) {
    IbpGrowthConditions.LOWLAND      -> stringResource(R.string.ibp_growth_lowland)
    IbpGrowthConditions.HIGHLAND     -> stringResource(R.string.ibp_growth_highland)
    IbpGrowthConditions.SUBALPINE    -> stringResource(R.string.ibp_growth_subalpine)
    IbpGrowthConditions.MEDITERRANEAN -> stringResource(R.string.ibp_growth_mediterranean)
}

fun ibpGrowthConditionsIcon(cond: IbpGrowthConditions): String = when (cond) {
    IbpGrowthConditions.LOWLAND       -> "🌿"
    IbpGrowthConditions.HIGHLAND      -> "⛰️"
    IbpGrowthConditions.SUBALPINE     -> "🏔️"
    IbpGrowthConditions.MEDITERRANEAN -> "☀️"
}

/* ─────────────── Criterion Card (enriched) ────────────────────── */
@Composable
private fun IbpCriterionCard(
    criterionId: IbpCriterionId,
    currentValue: Int,
    currentDetails: List<String>,
    currentCounts: Map<String, Float> = emptyMap(),
    growthConditions: IbpGrowthConditions,
    onAnswer: (Int) -> Unit,
    onDetailsChange: (List<String>) -> Unit,
    onCountChange: (String, Float) -> Unit = { _, _ -> }
) {
    val isAnswered = currentValue >= 0
    var detailsExpanded by remember { mutableStateOf(false) }
    val badgeColor = if (criterionId.group == IbpGroup.A) Color(0xFF2E7D32) else Color(0xFF1565C0)

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAnswered)
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(if (isAnswered) 2.dp else 1.dp),
        border = if (isAnswered) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── Header row ──────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = badgeColor, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        criterionId.displayCode,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    IbpCriterionIllustration(criterionId = criterionId, modifier = Modifier.size(44.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(ibpCriterionTitle(criterionId), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(ibpCriterionSubtitle(criterionId), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isAnswered) {
                    Surface(color = badgeColor, shape = CircleShape, modifier = Modifier.size(28.dp)) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text("$currentValue", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(onClick = { detailsExpanded = !detailsExpanded }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (detailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null, modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Expandable detail panel ──────────────────────────────
            AnimatedVisibility(visible = detailsExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = badgeColor.copy(alpha = .2f))
                    Spacer(Modifier.height(10.dp))
                    when (criterionId) {
                        IbpCriterionId.E1 -> IbpSpeciesPanel(
                            selected = currentDetails, conditions = growthConditions
                        ) { items -> onDetailsChange(items); onAnswer(IbpCriterionData.scoreA(items, growthConditions)) }
                        IbpCriterionId.E2 -> IbpChecklistPanel(
                            title = stringResource(R.string.ibp_strata_present),
                            items = IbpCriterionData.strataLayers, selected = currentDetails
                        ) { items -> onDetailsChange(items); onAnswer(IbpCriterionData.scoreB(items)) }
                        IbpCriterionId.CO -> IbpChecklistPanel(
                            title = stringResource(R.string.ibp_aquatic_types),
                            items = IbpCriterionData.aquaticTypes, selected = currentDetails
                        ) { items -> onDetailsChange(items); onAnswer(IbpCriterionData.scoreIJ(items)) }
                        IbpCriterionId.HC -> IbpChecklistPanel(
                            title = stringResource(R.string.ibp_rocky_types),
                            items = IbpCriterionData.rockyTypes, selected = currentDetails
                        ) { items -> onDetailsChange(items); onAnswer(IbpCriterionData.scoreIJ(items)) }
                        IbpCriterionId.CF -> IbpRadioGuidePanel(
                            title = stringResource(R.string.ibp_temporal_continuity),
                            items = IbpCriterionData.forestContinuityOptions
                        ) { idx -> onAnswer(when (idx) { 0 -> 0; 1 -> 2; else -> 5 }) }
                        IbpCriterionId.BMS -> IbpDeadwoodCountPanel(
                            bmgValue = currentCounts[IbpCriterionData.KEY_BMS_BMG] ?: 0f,
                            bmmValue = currentCounts[IbpCriterionData.KEY_BMS_BMM] ?: 0f,
                            labelBmg = "BMg debout (Ø>37.5 cm)", labelBmm = "BMm debout (17.5–37.5 cm)",
                            onBmgChange = { v -> onCountChange(IbpCriterionData.KEY_BMS_BMG, v)
                                onAnswer(IbpCriterionData.scoreCFromCounts(v, currentCounts[IbpCriterionData.KEY_BMS_BMM] ?: 0f)) },
                            onBmmChange = { v -> onCountChange(IbpCriterionData.KEY_BMS_BMM, v)
                                onAnswer(IbpCriterionData.scoreCFromCounts(currentCounts[IbpCriterionData.KEY_BMS_BMG] ?: 0f, v)) }
                        )
                        IbpCriterionId.BMC -> IbpDeadwoodCountPanel(
                            bmgValue = currentCounts[IbpCriterionData.KEY_BMC_BMG] ?: 0f,
                            bmmValue = currentCounts[IbpCriterionData.KEY_BMC_BMM] ?: 0f,
                            labelBmg = "BMg au sol (Ø>37.5 cm)", labelBmm = "BMm au sol (17.5–37.5 cm)",
                            onBmgChange = { v -> onCountChange(IbpCriterionData.KEY_BMC_BMG, v)
                                onAnswer(IbpCriterionData.scoreDFromCounts(v, currentCounts[IbpCriterionData.KEY_BMC_BMM] ?: 0f)) },
                            onBmmChange = { v -> onCountChange(IbpCriterionData.KEY_BMC_BMM, v)
                                onAnswer(IbpCriterionData.scoreDFromCounts(currentCounts[IbpCriterionData.KEY_BMC_BMG] ?: 0f, v)) }
                        )
                        IbpCriterionId.GB -> IbpBigTreeCountPanel(
                            tgbValue = currentCounts[IbpCriterionData.KEY_GB_TGB] ?: 0f,
                            gbValue  = currentCounts[IbpCriterionData.KEY_GB_GB] ?: 0f,
                            conditions = growthConditions,
                            onTgbChange = { v -> onCountChange(IbpCriterionData.KEY_GB_TGB, v)
                                onAnswer(IbpCriterionData.scoreEFromCounts(v, currentCounts[IbpCriterionData.KEY_GB_GB] ?: 0f, growthConditions)) },
                            onGbChange  = { v -> onCountChange(IbpCriterionData.KEY_GB_GB, v)
                                onAnswer(IbpCriterionData.scoreEFromCounts(currentCounts[IbpCriterionData.KEY_GB_TGB] ?: 0f, v, growthConditions)) }
                        )
                        IbpCriterionId.DMH -> IbpDmhActivePanel(
                            selected = currentDetails,
                            dmhCount = currentCounts[IbpCriterionData.KEY_DMH_N] ?: 0f,
                            onDmhCountChange = { v -> onCountChange(IbpCriterionData.KEY_DMH_N, v)
                                onAnswer(IbpCriterionData.scoreFFromCounts(v)) },
                            onDetailsChange = onDetailsChange
                        )
                        IbpCriterionId.VS -> IbpOpenHabitatPanel(
                            pct = currentCounts[IbpCriterionData.KEY_VS_PCT] ?: 0f,
                            onPctChange = { v -> onCountChange(IbpCriterionData.KEY_VS_PCT, v)
                                onAnswer(IbpCriterionData.scoreGFromPct(v)) }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = badgeColor.copy(alpha = .2f))
                    Spacer(Modifier.height(10.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Score selector (Larrieu & Gonin 2008: 0/2/5 only) ────
            val scoreList = listOf(0, 2, 5)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                scoreList.forEach { pts ->
                    IbpOptionRow(
                        score = pts,
                        label = ibpCriterionOptionLabel(criterionId, pts),
                        selected = currentValue == pts,
                        onSelect = { onAnswer(pts) }
                    )
                }
            }
        }
    }
}

/* ─────────────── Criterion detail helpers ──────────────────────── */

@Composable
private fun IbpSpeciesPanel(
    selected: List<String>,
    conditions: IbpGrowthConditions,
    onSelectionChange: (List<String>) -> Unit
) {
    val autoScore = IbpCriterionData.scoreA(selected, conditions)
    Column {
        Text(stringResource(R.string.ibp_native_species_genres), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Text(
            if (conditions == IbpGrowthConditions.SUBALPINE)
                "Subalpin : ≥3 genres → 5 pts · 1–2 genres → 2 pts · 0 genre → 0 pt"
            else "≥5 genres → 5 pts · 3–4 genres → 2 pts · 2 genres → 1 pt · ≤1 genre → 0 pt",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        IbpCriterionData.speciesGenres.forEach { species ->
            val checked = species in selected
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                    .clickable {
                        val newList = if (checked) selected - species else selected + species
                        onSelectionChange(newList)
                    }.padding(vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(6.dp))
                Text(species, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
        }
        if (selected.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .7f), shape = RoundedCornerShape(6.dp)) {
                Text(
                    "${selected.size} genre(s) → score calculé : $autoScore pts",
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun IbpChecklistPanel(
    title: String,
    items: List<String>,
    selected: List<String>,
    onSelectionChange: (List<String>) -> Unit
) {
    Column {
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        items.forEach { item ->
            val checked = item in selected
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                    .clickable {
                        val newList = if (checked) selected - item else selected + item
                        onSelectionChange(newList)
                    }.padding(vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(6.dp))
                Text(item, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
        }
        if (selected.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            val score = IbpCriterionData.scoreIJ(selected)
            Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .7f), shape = RoundedCornerShape(6.dp)) {
                Text(
                    "${selected.size} type(s) → score calculé : $score pts",
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun IbpInfoListPanel(title: String, items: List<String>) {
    Column {
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        items.forEach { item ->
            Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                Text("• ", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Text(item, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun IbpRadioGuidePanel(title: String, items: List<String>, onSelect: (Int) -> Unit) {
    val chipColors = listOf(Color(0xFFC62828), Color(0xFFF9A825), Color(0xFF2E7D32))
    Column {
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        items.forEachIndexed { idx, option ->
            val c = chipColors[idx]
            Surface(
                color = c.copy(alpha = .08f), shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, c.copy(alpha = .35f)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { onSelect(idx) }
            ) {
                Text(option, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp), color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun IbpOptionRow(score: Int, label: String, selected: Boolean, onSelect: () -> Unit) {
    val bgColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "ibpOptionBg"
    )
    val borderColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f),
        label = "ibpOptionBorder"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onSelect() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = when (score) {
                0 -> Color(0xFFC62828)
                1 -> Color(0xFFE65100)
                2 -> Color(0xFFF9A825)
                else -> Color(0xFF2E7D32)
            },
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                when (score) { 0 -> "0 pt"; 1 -> "1 pt"; 2 -> "2 pts"; else -> "5 pts" },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
    }
}

/* ─────────────── Result Card (inline) ─────────────────────────── */
@Composable
private fun IbpResultCard(level: IbpLevel, scoreTotal: Int, scoreA: Int, scoreB: Int, levelColor: Color, answers: IbpAnswers) {
    val weakCriteria = IbpCriterionId.ALL
        .filter { answers.get(it) in listOf(0, 2) }
        .sortedBy { answers.get(it) }
        .take(3)
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = levelColor.copy(alpha = .10f)),
        border = BorderStroke(1.5.dp, levelColor.copy(alpha = .4f))
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.EmojiNature, contentDescription = null, tint = levelColor, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(4.dp))
            Text(ibpLevelLabel(level), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = levelColor)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$scoreA / 35", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    Text(stringResource(R.string.ibp_group_a_short), style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$scoreB / 15", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                    Text(stringResource(R.string.ibp_group_b_short), style = MaterialTheme.typography.labelSmall, color = Color(0xFF1565C0))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(ibpLevelComment(level, scoreTotal), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = levelColor.copy(alpha = .3f))
            Spacer(Modifier.height(8.dp))
            IbpContextVsPeuplementChart(scoreA = scoreA, scoreB = scoreB)
            if (weakCriteria.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = levelColor.copy(alpha = .3f))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.ibp_priority_improvements), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                weakCriteria.forEach { cid ->
                    val sc = answers.get(cid)
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                        Surface(color = ibpLevelColor(IbpLevel.fromScore(sc)).copy(alpha = .15f), shape = RoundedCornerShape(4.dp)) {
                            Text("${cid.code} $sc/5", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = ibpLevelColor(IbpLevel.fromScore(sc)), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(ibpCriterionTip(cid), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = levelColor.copy(alpha = .3f))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.ibp_recommendations), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            ibpLevelRecommendations(level).forEach { rec ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("• ", color = levelColor, fontWeight = FontWeight.Bold)
                    Text(rec, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/* ─────────────── Result Dialog ─────────────────────────────────── */
@Composable
private fun IbpResultDialog(
    answers: IbpAnswers,
    scoreTotal: Int,
    scoreA: Int,
    scoreB: Int,
    level: IbpLevel,
    onDismiss: () -> Unit
) {
    val levelColor = ibpLevelColor(level)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.EmojiNature, contentDescription = null, tint = levelColor, modifier = Modifier.size(36.dp)) },
        title = { Text(stringResource(R.string.ibp_result_title), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Big score
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$scoreTotal / 50", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, color = levelColor)
                        IbpLevelBadge(level = level, color = levelColor)
                    }
                }
                Spacer(Modifier.height(4.dp))
                // Score breakdown
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$scoreA / 35", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        Text(stringResource(R.string.ibp_group_a_short), style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$scoreB / 15", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                        Text(stringResource(R.string.ibp_group_b_short), style = MaterialTheme.typography.labelSmall, color = Color(0xFF1565C0))
                    }
                }
                HorizontalDivider()
                IbpRadarChart(answers = answers, color = levelColor, modifier = Modifier.fillMaxWidth().height(200.dp))
                HorizontalDivider()
                IbpContextVsPeuplementChart(scoreA = scoreA, scoreB = scoreB)
                HorizontalDivider()
                Text(ibpLevelComment(level, scoreTotal), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, fontStyle = FontStyle.Italic)
                HorizontalDivider()
                Text(stringResource(R.string.ibp_recommendations), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                ibpLevelRecommendations(level).forEach { rec ->
                    Row {
                        Text("• ", color = levelColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        Text(rec, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        }
    )
}

/* ─────────────── Criterion data: titles ─────────────────────────── */
@Composable
fun ibpCriterionTitle(id: IbpCriterionId): String = when (id) {
    IbpCriterionId.E1  -> stringResource(R.string.ibp_e1_title)
    IbpCriterionId.E2  -> stringResource(R.string.ibp_e2_title)
    IbpCriterionId.GB  -> stringResource(R.string.ibp_gb_title)
    IbpCriterionId.BMS -> stringResource(R.string.ibp_bms_title)
    IbpCriterionId.BMC -> stringResource(R.string.ibp_bmc_title)
    IbpCriterionId.DMH -> stringResource(R.string.ibp_dmh_title)
    IbpCriterionId.VS  -> stringResource(R.string.ibp_vs_title)
    IbpCriterionId.CF  -> stringResource(R.string.ibp_cf_title)
    IbpCriterionId.CO  -> stringResource(R.string.ibp_co_title)
    IbpCriterionId.HC  -> stringResource(R.string.ibp_hc_title)
}

@Composable
fun ibpCriterionSubtitle(id: IbpCriterionId): String = when (id) {
    IbpCriterionId.E1  -> stringResource(R.string.ibp_e1_subtitle)
    IbpCriterionId.E2  -> stringResource(R.string.ibp_e2_subtitle)
    IbpCriterionId.GB  -> stringResource(R.string.ibp_gb_subtitle)
    IbpCriterionId.BMS -> stringResource(R.string.ibp_bms_subtitle)
    IbpCriterionId.BMC -> stringResource(R.string.ibp_bmc_subtitle)
    IbpCriterionId.DMH -> stringResource(R.string.ibp_dmh_subtitle)
    IbpCriterionId.VS  -> stringResource(R.string.ibp_vs_subtitle)
    IbpCriterionId.CF  -> stringResource(R.string.ibp_cf_subtitle)
    IbpCriterionId.CO  -> stringResource(R.string.ibp_co_subtitle)
    IbpCriterionId.HC  -> stringResource(R.string.ibp_hc_subtitle)
}

@Composable
fun ibpCriterionOptions(id: IbpCriterionId): List<String> = when (id) {
    IbpCriterionId.E1  -> listOf(stringResource(R.string.ibp_e1_o0), stringResource(R.string.ibp_e1_o1), stringResource(R.string.ibp_e1_o2))
    IbpCriterionId.E2  -> listOf(stringResource(R.string.ibp_e2_o0), stringResource(R.string.ibp_e2_o1), stringResource(R.string.ibp_e2_o2))
    IbpCriterionId.GB  -> listOf(stringResource(R.string.ibp_gb_o0), stringResource(R.string.ibp_gb_o1), stringResource(R.string.ibp_gb_o2))
    IbpCriterionId.BMS -> listOf(stringResource(R.string.ibp_bms_o0), stringResource(R.string.ibp_bms_o1), stringResource(R.string.ibp_bms_o2))
    IbpCriterionId.BMC -> listOf(stringResource(R.string.ibp_bmc_o0), stringResource(R.string.ibp_bmc_o1), stringResource(R.string.ibp_bmc_o2))
    IbpCriterionId.DMH -> listOf(stringResource(R.string.ibp_dmh_o0), stringResource(R.string.ibp_dmh_o1), stringResource(R.string.ibp_dmh_o2))
    IbpCriterionId.VS  -> listOf(stringResource(R.string.ibp_vs_o0), stringResource(R.string.ibp_vs_o1), stringResource(R.string.ibp_vs_o2))
    IbpCriterionId.CF  -> listOf(stringResource(R.string.ibp_cf_o0), stringResource(R.string.ibp_cf_o1), stringResource(R.string.ibp_cf_o2))
    IbpCriterionId.CO  -> listOf(stringResource(R.string.ibp_co_o0), stringResource(R.string.ibp_co_o1), stringResource(R.string.ibp_co_o2))
    IbpCriterionId.HC  -> listOf(stringResource(R.string.ibp_hc_o0), stringResource(R.string.ibp_hc_o1), stringResource(R.string.ibp_hc_o2))
}

@Composable
fun ibpCriterionOptionLabel(id: IbpCriterionId, score: Int): String = when (id) {
    IbpCriterionId.E1  -> when (score) { 0 -> stringResource(R.string.ibp_e1_o0); 1 -> stringResource(R.string.ibp_e1_o1pt); 2 -> stringResource(R.string.ibp_e1_o1); else -> stringResource(R.string.ibp_e1_o2) }
    IbpCriterionId.E2  -> when (score) { 0 -> stringResource(R.string.ibp_e2_o0); 1 -> stringResource(R.string.ibp_e2_o1pt); 2 -> stringResource(R.string.ibp_e2_o1); else -> stringResource(R.string.ibp_e2_o2) }
    IbpCriterionId.BMS -> when (score) { 0 -> stringResource(R.string.ibp_bms_o0); 1 -> stringResource(R.string.ibp_bms_o1pt); 2 -> stringResource(R.string.ibp_bms_o1); else -> stringResource(R.string.ibp_bms_o2) }
    IbpCriterionId.BMC -> when (score) { 0 -> stringResource(R.string.ibp_bmc_o0); 1 -> stringResource(R.string.ibp_bmc_o1pt); 2 -> stringResource(R.string.ibp_bmc_o1); else -> stringResource(R.string.ibp_bmc_o2) }
    IbpCriterionId.GB  -> when (score) { 0 -> stringResource(R.string.ibp_gb_o0); 1 -> stringResource(R.string.ibp_gb_o1pt); 2 -> stringResource(R.string.ibp_gb_o1); else -> stringResource(R.string.ibp_gb_o2) }
    IbpCriterionId.DMH -> when (score) { 0 -> stringResource(R.string.ibp_dmh_o0); 1 -> stringResource(R.string.ibp_dmh_o1pt); 2 -> stringResource(R.string.ibp_dmh_o1); else -> stringResource(R.string.ibp_dmh_o2) }
    else               -> ibpCriterionOptions(id).getOrElse(score / 2) { ibpCriterionOptions(id).last() }
}

@Composable
fun ibpLevelComment(level: IbpLevel, score: Int): String = when (level) {
    IbpLevel.VERY_LOW  -> stringResource(R.string.ibp_comment_very_low)
    IbpLevel.LOW       -> stringResource(R.string.ibp_comment_low)
    IbpLevel.MEDIUM    -> stringResource(R.string.ibp_comment_medium)
    IbpLevel.GOOD      -> stringResource(R.string.ibp_comment_good)
    IbpLevel.VERY_GOOD -> stringResource(R.string.ibp_comment_very_good)
}

/* ─────────────── IBP card in MartelageScreen ────────────────── */
@Composable
fun IbpMartelageCard(
    ibpEval: com.forestry.counter.domain.model.IbpEvaluation?,
    parcelleId: String?,
    placetteId: String?,
    onNavigateToIbp: ((parcelleId: String, placetteId: String) -> Unit)? = null,
    onNavigateToHistory: ((parcelleId: String, placetteId: String?) -> Unit)? = null,
    evaluationCount: Int = 0
) {
    val canNavigate = onNavigateToIbp != null && parcelleId != null && placetteId != null
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.EmojiNature, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ibp_title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                if (ibpEval != null && ibpEval.answers.isComplete) {
                    val level = ibpEval.levelColor()
                    val color = ibpLevelColor(level)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${ibpEval.scoreTotal} / 50", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
                        IbpLevelBadge(level = level, color = color)
                    }
                } else {
                    Text(stringResource(R.string.ibp_no_evaluation), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (canNavigate) {
                    TextButton(onClick = { onNavigateToIbp?.invoke(parcelleId ?: return@TextButton, placetteId ?: return@TextButton) }) {
                        Text(if (ibpEval != null) stringResource(R.string.ibp_edit) else stringResource(R.string.ibp_start))
                    }
                }
                if (evaluationCount >= 2 && onNavigateToHistory != null && parcelleId != null) {
                    TextButton(onClick = { onNavigateToHistory(parcelleId, placetteId) }) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ibp_history_title), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
fun ibpLevelRecommendations(level: IbpLevel): List<String> = when (level) {
    IbpLevel.VERY_LOW  -> listOf(
        stringResource(R.string.ibp_rec_vl_1),
        stringResource(R.string.ibp_rec_vl_2),
        stringResource(R.string.ibp_rec_vl_3)
    )
    IbpLevel.LOW       -> listOf(
        stringResource(R.string.ibp_rec_l_1),
        stringResource(R.string.ibp_rec_l_2),
        stringResource(R.string.ibp_rec_l_3)
    )
    IbpLevel.MEDIUM    -> listOf(
        stringResource(R.string.ibp_rec_m_1),
        stringResource(R.string.ibp_rec_m_2),
        stringResource(R.string.ibp_rec_m_3)
    )
    IbpLevel.GOOD      -> listOf(
        stringResource(R.string.ibp_rec_g_1),
        stringResource(R.string.ibp_rec_g_2)
    )
    IbpLevel.VERY_GOOD -> listOf(
        stringResource(R.string.ibp_rec_vg_1),
        stringResource(R.string.ibp_rec_vg_2)
    )
}

@Composable
fun ibpCriterionTip(id: IbpCriterionId): String = when (id) {
    IbpCriterionId.E1  -> stringResource(R.string.ibp_tip_e1)
    IbpCriterionId.E2  -> stringResource(R.string.ibp_tip_e2)
    IbpCriterionId.GB  -> stringResource(R.string.ibp_tip_gb)
    IbpCriterionId.BMS -> stringResource(R.string.ibp_tip_bms)
    IbpCriterionId.BMC -> stringResource(R.string.ibp_tip_bmc)
    IbpCriterionId.DMH -> stringResource(R.string.ibp_tip_dmh)
    IbpCriterionId.VS  -> stringResource(R.string.ibp_tip_vs)
    IbpCriterionId.CF  -> stringResource(R.string.ibp_tip_cf)
    IbpCriterionId.CO  -> stringResource(R.string.ibp_tip_co)
    IbpCriterionId.HC  -> stringResource(R.string.ibp_tip_hc)
}

/* ─────────────── IBP Contexte vs Peuplement & Gestion Chart ─────── */
@Composable
fun IbpContextVsPeuplementChart(
    scoreA: Int,
    scoreB: Int,
    modifier: Modifier = Modifier
) {
    val colorA = Color(0xFF2E7D32)
    val colorB = Color(0xFF1565C0)
    val levelColor = when {
        scoreA < 0 || scoreB < 0 -> Color(0xFF9E9E9E)
        scoreA + scoreB < 10     -> Color(0xFFC62828)
        scoreA + scoreB < 20     -> Color(0xFFE65100)
        scoreA + scoreB < 30     -> Color(0xFFF9A825)
        scoreA + scoreB < 40     -> Color(0xFF2E7D32)
        else                     -> Color(0xFF1565C0)
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("IBP Contexte vs Peuplement & Gestion",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(colorA))
                Spacer(Modifier.width(4.dp))
                Text("Peuplement A–G : ${if (scoreA >= 0) "$scoreA/35" else "—"}",
                    style = MaterialTheme.typography.labelSmall, color = colorA)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(colorB))
                Spacer(Modifier.width(4.dp))
                Text("Contexte H–J : ${if (scoreB >= 0) "$scoreB/15" else "—"}",
                    style = MaterialTheme.typography.labelSmall, color = colorB)
            }
        }

        Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            val pL = 40f; val pB = 28f; val pR = 16f; val pT = 14f
            val cW = size.width - pL - pR
            val cH = size.height - pB - pT

            // Colored vertical zones (Peuplement, X axis)
            val zoneColorsX = listOf(
                Color(0xFFC62828), Color(0xFFE65100), Color(0xFFF9A825),
                Color(0xFF2E7D32), Color(0xFF1565C0)
            )
            val xBounds = listOf(0f, 7f, 14f, 21f, 28f, 35f)
            zoneColorsX.forEachIndexed { i, zc ->
                val x0 = pL + xBounds[i] / 35f * cW
                val x1 = pL + xBounds[i + 1] / 35f * cW
                drawRect(zc.copy(alpha = .07f), topLeft = Offset(x0, pT), size = Size(x1 - x0, cH))
            }

            // Colored horizontal zones (Contexte, Y axis)
            val zoneColorsY = listOf(Color(0xFFC62828), Color(0xFFF9A825), Color(0xFF2E7D32))
            val yBounds = listOf(0f, 5f, 10f, 15f)
            zoneColorsY.forEachIndexed { i, zc ->
                val y1 = pT + cH - yBounds[i] / 15f * cH
                val y0 = pT + cH - yBounds[i + 1] / 15f * cH
                drawRect(zc.copy(alpha = .04f), topLeft = Offset(pL, y0), size = Size(cW, y1 - y0))
            }

            // Grid lines
            val gridC = Color(0xFF9E9E9E).copy(alpha = .35f)
            for (i in 0..5) {
                val gx = pL + i * cW / 5f
                drawLine(gridC, Offset(gx, pT), Offset(gx, pT + cH), strokeWidth = 1f)
            }
            for (i in 0..3) {
                val gy = pT + i * cH / 3f
                drawLine(gridC, Offset(pL, gy), Offset(pL + cW, gy), strokeWidth = 1f)
            }

            // Axes
            drawLine(Color(0xFF757575), Offset(pL, pT), Offset(pL, pT + cH), strokeWidth = 2f)
            drawLine(Color(0xFF757575), Offset(pL, pT + cH), Offset(pL + cW, pT + cH), strokeWidth = 2f)

            // Plot the evaluation point
            if (scoreA >= 0 && scoreB >= 0) {
                val px = pL + (scoreA.toFloat() / 35f) * cW
                val py = pT + cH - (scoreB.toFloat() / 15f) * cH
                drawLine(colorA.copy(alpha = .35f), Offset(px, pT + cH), Offset(px, py), strokeWidth = 1.5f)
                drawLine(colorB.copy(alpha = .35f), Offset(pL, py), Offset(px, py), strokeWidth = 1.5f)
                drawCircle(Color.White, radius = 9f, center = Offset(px, py))
                drawCircle(levelColor, radius = 8f, center = Offset(px, py), style = Stroke(width = 2.5f))
                drawCircle(levelColor.copy(alpha = .7f), radius = 4f, center = Offset(px, py))
            }
        }

        // X axis labels
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 40.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("0", "7", "14", "21", "28", "35").forEach { l ->
                Text(l, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("← Peuplement et Gestion (A–G) / 35 →",
                style = MaterialTheme.typography.labelSmall, color = colorA)
        }
    }
}

/* ─────────────── Radar Chart ────────────────────────────────────── */
@Composable
fun IbpRadarChart(
    answers: IbpAnswers,
    color: Color,
    modifier: Modifier = Modifier
) {
    val criteria = IbpCriterionId.ALL
    val n = criteria.size
    val values = criteria.map { id -> answers.get(id).coerceAtLeast(0).toFloat() / 5f }

    val gridColor = color.copy(alpha = 0.15f)
    val fillColor = color.copy(alpha = 0.25f)
    val strokeColor = color

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = minOf(cx, cy) * 0.72f
        val angleStep = (2 * Math.PI / n).toFloat()
        val startAngle = (-Math.PI / 2).toFloat()

        fun axisPoint(index: Int, r: Float): Offset {
            val angle = startAngle + index * angleStep
            return Offset(cx + r * kotlin.math.cos(angle), cy + r * kotlin.math.sin(angle))
        }

        // Draw grid rings (0.4 = 2pts and 1.0 = 5pts)
        listOf(0.4f, 1.0f).forEach { fraction ->
            val gridPath = Path()
            for (i in 0 until n) {
                val pt = axisPoint(i, radius * fraction)
                if (i == 0) gridPath.moveTo(pt.x, pt.y) else gridPath.lineTo(pt.x, pt.y)
            }
            gridPath.close()
            drawPath(gridPath, color = gridColor, style = Stroke(width = 1.dp.toPx()))
        }

        // Draw axes
        for (i in 0 until n) {
            val outer = axisPoint(i, radius)
            drawLine(color = gridColor, start = Offset(cx, cy), end = outer, strokeWidth = 1.dp.toPx())
        }

        // Draw filled polygon
        val dataPath = Path()
        for (i in 0 until n) {
            val pt = axisPoint(i, radius * values[i])
            if (i == 0) dataPath.moveTo(pt.x, pt.y) else dataPath.lineTo(pt.x, pt.y)
        }
        dataPath.close()
        drawPath(dataPath, color = fillColor)
        drawPath(dataPath, color = strokeColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

        // Draw dots at each vertex
        for (i in 0 until n) {
            val pt = axisPoint(i, radius * values[i])
            drawCircle(color = strokeColor, radius = 4.dp.toPx(), center = pt)
        }
    }
}

/* ─────────────── Mode Selector Row ─────────────────────────────── */
@Composable
private fun IbpModeSelectorRow(
    selectedMode: IbpMode,
    onModeSelected: (IbpMode) -> Unit
) {
    val modes = IbpMode.values()
    val modeLabels = mapOf(
        IbpMode.COMPLET    to stringResource(R.string.ibp_mode_complet),
        IbpMode.RAPIDE     to stringResource(R.string.ibp_mode_rapide),
        IbpMode.BOIS_MORT  to stringResource(R.string.ibp_mode_bois_mort),
        IbpMode.CONTEXTE   to stringResource(R.string.ibp_mode_contexte),
        IbpMode.PEUPLEMENT to stringResource(R.string.ibp_mode_peuplement)
    )
    val modeIcons = mapOf(
        IbpMode.COMPLET    to Icons.Default.FormatListBulleted,
        IbpMode.RAPIDE     to Icons.Default.FlashOn,
        IbpMode.BOIS_MORT  to Icons.Default.Park,
        IbpMode.CONTEXTE   to Icons.Default.Terrain,
        IbpMode.PEUPLEMENT to Icons.Default.AccountTree
    )
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(stringResource(R.string.ibp_mode_eval), style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(modes.size) { i ->
                val mode = modes[i]
                val selected = mode == selectedMode
                FilterChip(
                    selected = selected,
                    onClick = { onModeSelected(mode) },
                    label = { Text(modeLabels[mode] ?: mode.name, style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        modeIcons[mode]?.let { iv ->
                            Icon(iv, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                )
            }
        }
    }
}

/* ─────────────── Deadwood Count Panel (C & D) ──────────────────── */
@Composable
private fun IbpDeadwoodCountPanel(
    bmgValue: Float, bmmValue: Float,
    labelBmg: String, labelBmm: String,
    onBmgChange: (Float) -> Unit,
    onBmmChange: (Float) -> Unit
) {
    val autoScore = if (bmgValue >= 3f) 5 else if (bmgValue >= 1f || bmmValue >= 1f) 2 else 0
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.ibp_field_counting), style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        IbpCountField(label = labelBmg, hint = "ex: 3.0", value = bmgValue, onChange = onBmgChange,
            badge = if (bmgValue >= 3f) "→ 5 pts" else if (bmgValue >= 1f) "→ 2 pts" else null)
        IbpCountField(label = labelBmm, hint = "ex: 1.0", value = bmmValue, onChange = onBmmChange,
            badge = if (bmgValue < 1f && bmmValue >= 1f) "→ 2 pts" else null)
        IbpAutoScoreBadge(autoScore)
        Text(stringResource(R.string.ibp_bmg_help),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/* ─────────────── Big Tree Count Panel (E) ──────────────────────── */
@Composable
private fun IbpBigTreeCountPanel(
    tgbValue: Float, gbValue: Float,
    conditions: IbpGrowthConditions,
    onTgbChange: (Float) -> Unit,
    onGbChange: (Float) -> Unit
) {
    val autoScore = IbpCriterionData.scoreEFromCounts(tgbValue, gbValue, conditions)
    val tgbLabel = if (conditions == IbpGrowthConditions.SUBALPINE) "TGB (∅>47.5 cm) /ha" else "TGB (∅>67.5 cm) /ha"
    val gbLabel  = if (conditions == IbpGrowthConditions.SUBALPINE) "GB (∅ 27.5–47.5 cm) /ha" else "GB (∅ 47.5–67.5 cm) /ha"
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Comptage terrain – arbres vivants (arbres/ha)", style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        IbpCountField(label = tgbLabel, hint = "ex: 5.0", value = tgbValue, onChange = onTgbChange,
            badge = if (tgbValue >= 5f) "→ 5 pts" else if (tgbValue >= 1f) "→ 2 pts" else null)
        IbpCountField(label = gbLabel, hint = "ex: 2.0", value = gbValue, onChange = onGbChange,
            badge = if (tgbValue < 1f && gbValue >= 1f) "→ 2 pts" else null)
        IbpAutoScoreBadge(autoScore)
    }
}

/* ─────────────── DMH Active Panel (F) ──────────────────────────── */
@Composable
private fun IbpDmhActivePanel(
    selected: List<String>,
    dmhCount: Float,
    onDmhCountChange: (Float) -> Unit,
    onDetailsChange: (List<String>) -> Unit
) {
    val autoScore = IbpCriterionData.scoreFFromCounts(dmhCount)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        IbpCountField(
            label = "Arbres porteurs de ≥1 dmh (arbres/ha)",
            hint = "ex: 5.0",
            value = dmhCount,
            onChange = onDmhCountChange,
            badge = if (dmhCount >= 5f) "→ 5 pts" else if (dmhCount >= 2f) "→ 2 pts" else "→ 0 pt"
        )
        IbpAutoScoreBadge(autoScore)
        HorizontalDivider()
        Text(stringResource(R.string.ibp_dmh_types_observed), style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        IbpCriterionData.dmhTypes.forEach { dmh ->
            val checked = dmh in selected
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                    .clickable { onDetailsChange(if (checked) selected - dmh else selected + dmh) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                Text(dmh, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
        }
    }
}

/* ─────────────── Open Habitat Panel (G) ────────────────────────── */
@Composable
private fun IbpOpenHabitatPanel(pct: Float, onPctChange: (Float) -> Unit) {
    val autoScore = IbpCriterionData.scoreGFromPct(pct)
    val sliderPct = pct.coerceIn(0f, 100f)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.ibp_open_habitat_surface),
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Slider(
                value = sliderPct,
                onValueChange = { onPctChange(it) },
                valueRange = 0f..100f,
                steps = 19,
                modifier = Modifier.weight(1f)
            )
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
                Text("${pct.toInt()} %", style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val zones = listOf("0%" to Color(0xFFC62828), "<1% ou >5%" to Color(0xFFE65100), "1–5%" to Color(0xFF2E7D32))
            zones.forEach { (label, color) ->
                Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(12.dp)) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = color,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }
        IbpAutoScoreBadge(autoScore)
        Text("Seuil optimal : 1–5 % → 5 pts | >0 % → 2 pts | 0 % → 0 pt",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/* ─────────────── Shared count input field ──────────────────────── */
@Composable
private fun IbpCountField(
    label: String,
    hint: String,
    value: Float,
    onChange: (Float) -> Unit,
    badge: String? = null
) {
    var text by remember(value) { mutableStateOf(if (value == 0f) "" else value.toString()) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                text = raw
                raw.toFloatOrNull()?.let { onChange(it) } ?: run { if (raw.isEmpty()) onChange(0f) }
            },
            modifier = Modifier.weight(1f),
            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
            placeholder = { Text(hint, style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
            )
        )
        if (badge != null) {
            Surface(color = Color(0xFF2E7D32).copy(alpha = .12f), shape = RoundedCornerShape(8.dp)) {
                Text(badge, style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
            }
        }
    }
}

/* ─────────────── Auto-score badge ──────────────────────────────── */
@Composable
private fun IbpAutoScoreBadge(score: Int) {
    val color = when (score) { 5 -> Color(0xFF2E7D32); 2 -> Color(0xFFF9A825); else -> Color(0xFFC62828) }
    Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(10.dp)) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Text(stringResource(R.string.ibp_score_auto_format, score),
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
