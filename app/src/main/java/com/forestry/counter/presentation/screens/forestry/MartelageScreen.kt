package com.forestry.counter.presentation.screens.forestry

import android.Manifest
import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.content.Intent
import android.widget.ImageView
import android.net.Uri
import androidx.compose.material.icons.filled.Share
import com.forestry.counter.domain.repository.EssenceRepository
import com.forestry.counter.domain.repository.TigeRepository
import com.forestry.counter.domain.repository.ParcelleRepository
import com.forestry.counter.domain.calculation.ForestryCalculator
import com.forestry.counter.domain.calculation.ForestrySynthesisParams
import com.forestry.counter.domain.calculation.ClassSynthesis
import com.forestry.counter.domain.calculation.SynthesisTotals
import com.forestry.counter.domain.calculation.MartelageStats
import com.forestry.counter.domain.calculation.MartelageViewScope
import com.forestry.counter.domain.calculation.computeMartelageStats
import com.forestry.counter.domain.classification.stand.StandClassificationCache
import com.forestry.counter.domain.calculation.PerEssenceStats
import com.forestry.counter.domain.calculation.ClassDistEntry
import com.forestry.counter.domain.calculation.QualityDistEntry
import com.forestry.counter.domain.calculation.SanityWarning
import com.forestry.counter.domain.calculation.SpecialTreeEntry
import com.forestry.counter.domain.calculation.BiodiversityIndex
import com.forestry.counter.domain.calculation.tarifs.TarifMethod
import com.forestry.counter.domain.calculation.tarifs.TarifSelection
import com.forestry.counter.domain.calculation.tarifs.TarifCalculator
import com.forestry.counter.domain.repository.IbpRepository
import com.forestry.counter.domain.model.IbpLevel
import com.forestry.counter.presentation.screens.forestry.ibpLevelColor
import com.forestry.counter.presentation.screens.forestry.IbpLevelBadge
import com.forestry.counter.presentation.screens.forestry.ibpLevelLabel
import com.forestry.counter.domain.usecase.export.QgisExportHelper
import com.forestry.counter.domain.usecase.export.PdfSynthesisExporter
import com.forestry.counter.domain.usecase.export.ShapefileExporter
import com.forestry.counter.data.preferences.UserPreferencesManager
import com.forestry.counter.presentation.components.AppMiniDialog
import com.forestry.counter.presentation.utils.parseHeightInputMean
import com.forestry.counter.presentation.utils.rememberHapticFeedback
import com.forestry.counter.presentation.utils.rememberSoundFeedback
import com.forestry.counter.presentation.utils.ColorUtils
import com.forestry.counter.domain.calculation.PriceCalculator
import com.forestry.counter.domain.calculation.ProductBreakdownRow
import kotlinx.coroutines.launch
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.forestry.counter.domain.model.ClimateZone
import com.forestry.counter.domain.usecase.fertility.FertilityClassifier
import com.forestry.counter.domain.usecase.fertility.FertilityResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import java.util.UUID
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.PI
import kotlin.math.sqrt
import com.forestry.counter.R
import android.os.Build
import android.graphics.Color as AndroidColor
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MartelageScreen(
    scope: String,
    forestId: String?,
    parcelleId: String?,
    placetteId: String?,
    essenceRepository: EssenceRepository,
    tigeRepository: TigeRepository,
    parcelleRepository: ParcelleRepository,
    forestryCalculator: ForestryCalculator,
    userPreferences: UserPreferencesManager,
    onNavigateToSettings: (() -> Unit)? = null,
    onNavigateToPriceTablesEditor: (() -> Unit)? = null,
    onNavigateToMap: ((String) -> Unit)? = null,
    ibpRepository: IbpRepository? = null,
    onNavigateToIbp: ((parcelleId: String, placetteId: String) -> Unit)? = null,
    onNavigateToIbpHistory: ((parcelleId: String, placetteId: String?) -> Unit)? = null,
    onNavigateToSuperCorrelateur: ((parcelleId: String) -> Unit)? = null,
    onNavigateToStandClassification: ((parcelleId: String) -> Unit)? = null,
    onNavigateBack: () -> Unit
) {
    val viewModel = remember(scope, forestId, parcelleId, placetteId) {
        MartelageViewModel(
            scope = scope,
            forestId = forestId,
            parcelleId = parcelleId,
            placetteId = placetteId,
            essenceRepository = essenceRepository,
            tigeRepository = tigeRepository,
            parcelleRepository = parcelleRepository,
            forestryCalculator = forestryCalculator,
            userPreferences = userPreferences,
            ibpRepository = ibpRepository
        )
    }

    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    val hapticEnabled by userPreferences.hapticEnabled.collectAsState(initial = true)
    val soundEnabled by userPreferences.soundEnabled.collectAsState(initial = true)
    val hapticIntensity by userPreferences.hapticIntensity.collectAsState(initial = 2)
    val backgroundImageEnabled by userPreferences.backgroundImageEnabled.collectAsState(initial = true)
    val backgroundImageUri by userPreferences.backgroundImageUri.collectAsState(initial = null)
    val animationsEnabled by userPreferences.animationsEnabled.collectAsState(initial = true)
    val haptic = rememberHapticFeedback()
    val sound = rememberSoundFeedback()

    val parcelles by viewModel.parcelles.collectAsState()

    // Clé de portée pour mémoriser les paramètres par parcelle / placette
    val scopeKey = remember(scope, forestId, parcelleId, placetteId) {
        when {
            placetteId != null -> "PLACETTE_${placetteId}"
            parcelleId != null -> "PARCELLE_${parcelleId}"
            forestId != null -> "FOREST_${forestId}"
            else -> "GLOBAL"
        }
    }

    val ibpEvaluations by remember(ibpRepository, placetteId) {
        if (ibpRepository != null && placetteId != null)
            ibpRepository.getByPlacette(placetteId)
        else if (ibpRepository != null && parcelleId != null)
            ibpRepository.getByParcelle(parcelleId)
        else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val latestIbp = remember(ibpEvaluations) { ibpEvaluations.firstOrNull() }

    val savedSurface by userPreferences.martelageSurfaceFlow(scopeKey).collectAsState(initial = null)
    val savedHo by userPreferences.martelageHoFlow(scopeKey).collectAsState(initial = null)
    val savedNhaAvant by userPreferences.martelageNhaAvantFlow(scopeKey).collectAsState(initial = null)
    val savedGhaAvant by userPreferences.martelageGhaAvantFlow(scopeKey).collectAsState(initial = null)

    val martelageHeightsLocal by userPreferences.martelageHeightsFlow(scopeKey).collectAsState(initial = emptyMap())
    val forestIdForHeights = remember(forestId, parcelleId, parcelles) {
        forestId ?: parcelles.firstOrNull { it.id == parcelleId }?.forestId
    }
    val forestScopeKeyForHeights = remember(forestIdForHeights) {
        forestIdForHeights?.let { "FOREST_${it}" }
    }

    val martelageHeightsForest by userPreferences
        .martelageHeightsFlow(forestScopeKeyForHeights ?: scopeKey)
        .collectAsState(initial = emptyMap())
    val martelageHeightsGlobal by userPreferences
        .martelageHeightsFlow("GLOBAL")
        .collectAsState(initial = emptyMap())

    val martelageHeights = remember(martelageHeightsLocal, martelageHeightsForest, martelageHeightsGlobal, scopeKey, forestScopeKeyForHeights) {
        fun normalizeMap(src: Map<String, Map<Int, Double>>): Map<String, Map<Int, Double>> {
            val out = mutableMapOf<String, MutableMap<Int, Double>>()
            src.forEach { (k, v) ->
                val nk = k.trim().uppercase(Locale.getDefault())
                val m = out.getOrPut(nk) { mutableMapOf() }
                v.forEach { (cls, h) -> m[cls] = h }
            }
            return out.mapValues { it.value.toMap() }
        }

        fun merge(base: Map<String, Map<Int, Double>>, over: Map<String, Map<Int, Double>>): Map<String, Map<Int, Double>> {
            val out = base.mapValues { it.value.toMutableMap() }.toMutableMap()
            over.forEach { (ess, map) ->
                val m = out.getOrPut(ess) { mutableMapOf() }
                m.putAll(map)
            }
            return out.mapValues { it.value.toMap() }
        }

        val globalN = normalizeMap(martelageHeightsGlobal)
        val forestN = if (forestScopeKeyForHeights != null && forestScopeKeyForHeights != scopeKey) normalizeMap(martelageHeightsForest) else emptyMap()
        val localN = normalizeMap(martelageHeightsLocal)

        merge(merge(globalN, forestN), localN)
    }
    val coroutineScope = rememberCoroutineScope()

    fun playClickFeedback() {
        if (hapticEnabled) haptic.performWithIntensity(hapticIntensity)
        if (soundEnabled) sound.click()
    }

    // Données de base
    val essences by essenceRepository.getAllEssences().collectAsState(initial = emptyList())
    val allTiges by viewModel.allTiges.collectAsState()

    // Parcelles incluses dans le périmètre courant
    val parcellesInScope = remember(parcelles, scope, forestId, parcelleId) {
        when (scope.uppercase(Locale.getDefault())) {
            "FOREST" -> parcelles.filter { it.forestId == forestId }
            "PARCELLE", "PLACETTE" -> parcelles.filter { it.id == parcelleId }
            else -> parcelles
        }
    }

    val parcelleIdsInScope = remember(parcellesInScope) { parcellesInScope.map { it.id }.toSet() }

    // Vue locale (Placette / Parcelle / Global) pour la synthèse
    val initialViewScope = remember(scope, placetteId, parcelleId) {
        when (scope.uppercase(Locale.getDefault())) {
            "PLACETTE" -> MartelageViewScope.PLACETTE
            "PARCELLE" -> MartelageViewScope.PARCELLE
            else -> MartelageViewScope.GLOBAL
        }
    }
    var viewScope by remember { mutableStateOf(initialViewScope) }

    // Tiges selon la vue locale
    val tigesInScope = remember(allTiges, viewScope, scope, parcelleIdsInScope, parcelleId, placetteId) {
        when (viewScope) {
            MartelageViewScope.PLACETTE ->
                placetteId?.let { id -> allTiges.filter { it.placetteId == id } } ?: emptyList()

            MartelageViewScope.PARCELLE ->
                parcelleId?.let { id -> allTiges.filter { it.parcelleId == id } } ?: emptyList()

            MartelageViewScope.GLOBAL -> when (scope.uppercase(Locale.getDefault())) {
                "FOREST" -> allTiges.filter { it.parcelleId in parcelleIdsInScope }
                "PARCELLE" -> parcelleId?.let { id -> allTiges.filter { it.parcelleId == id } } ?: allTiges
                "PLACETTE" -> placetteId?.let { id -> allTiges.filter { it.placetteId == id } } ?: allTiges
                else -> allTiges
            }
        }
    }

    fun normalizeEssenceCode(code: String): String = code.trim().uppercase(Locale.getDefault())

    val usedEssenceCodes = remember(tigesInScope) {
        tigesInScope.map { normalizeEssenceCode(it.essenceCode) }.distinct()
    }

    // Toutes les essences présentes dans le périmètre
    val availableEssenceCodes = remember(usedEssenceCodes) { usedEssenceCodes }

    val availableEssences = remember(essences, availableEssenceCodes) {
        val codes = availableEssenceCodes.toSet()
        essences.filter { normalizeEssenceCode(it.code) in codes }
    }

    var selectedEssenceCodes by remember(availableEssences) {
        mutableStateOf(availableEssences.map { normalizeEssenceCode(it.code) }.toSet())
    }

    val martelageEssenceMap = remember(essences) { essences.associateBy { normalizeEssenceCode(it.code) } }
    val martelageClimateZone = remember(tigesInScope) {
        val wkt = tigesInScope.mapNotNull { it.gpsWkt }.firstOrNull()
        if (wkt != null) ClimateZone.detectFromWkt(wkt, null) else ClimateZone.UNKNOWN
    }
    val fertilityResults: List<FertilityResult> = remember(tigesInScope, martelageClimateZone, martelageEssenceMap) {
        FertilityClassifier.classify(
            tiges = tigesInScope,
            climateZone = martelageClimateZone,
            essenceNames = martelageEssenceMap.mapValues { it.value.name }
        )
    }

    // Paramètres communs au calcul (surface d'échantillonnage, Ho, hauteurs par classe)
    val fixedClasses = remember { listOf(20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75) }

    var surfaceInput by remember { mutableStateOf("") }
    var hoInput by remember { mutableStateOf("") }
    var nHaAvantInput by remember { mutableStateOf("") }
    var gHaAvantInput by remember { mutableStateOf("") }
    val hauteurParClasseState = remember {
        mutableStateMapOf<Int, String>().apply {
            fixedClasses.forEach { put(it, "") }
        }
    }

    var showParamDialog by remember { mutableStateOf(false) }
    var askedParamsOnce by remember { mutableStateOf(false) }
    var showParamPanel by remember { mutableStateOf(false) }
    var editingHeightsEssenceCode by remember { mutableStateOf<String?>(null) }
    var persistParamsRequested by remember { mutableStateOf(false) }
    var showHeightPromptDialog by remember { mutableStateOf(false) }
    var showHeightSnoozeDialog by remember { mutableStateOf(false) }
    var heightSnoozeHours by rememberSaveable { mutableStateOf(1) }
    val heightPromptSnoozeUntilMs by userPreferences.heightPromptSnoozeUntilMs.collectAsState(initial = 0L)
    val isHeightPromptSnoozed = heightPromptSnoozeUntilMs > System.currentTimeMillis()

    // Tarif de cubage — sélection depuis le martelage
    var showTarifMethodDialog by remember { mutableStateOf(false) }
    var currentTarifMethod by remember { mutableStateOf(TarifMethod.ALGAN) }
    var currentTarifNumero by remember { mutableStateOf<Int?>(null) }
    // Incrémenté après chaque changement de méthode pour forcer le rechargement de synthesisParams
    var synthesisParamsVersion by remember { mutableStateOf(0) }
    var selectedTabIndex by remember { mutableStateOf(0) }
    var breakdownByEssence by remember { mutableStateOf<Map<String, List<ProductBreakdownRow>>>(emptyMap()) }
    var essenceSortKey by remember { mutableStateOf("name") }

    LaunchedEffect(Unit) {
        val sel = forestryCalculator.loadTarifSelection()
        currentTarifMethod = TarifMethod.fromCode(sel?.method ?: "") ?: TarifMethod.ALGAN
        currentTarifNumero = sel?.schaefferNumero ?: sel?.ifnNumero
    }

    // Export QGIS rapide
    var showExportDialog by remember { mutableStateOf(false) }
    val exportGeoJsonLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/geo+json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val (geojson, count) = QgisExportHelper.buildGeoJson(
                    tiges = tigesInScope,
                    essences = essences
                )
                if (count == 0) {
                    snackbar.showSnackbar(context.getString(R.string.export_qgis_no_gps_stems))
                    return@launch
                }
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(geojson) }
                }.onSuccess {
                    snackbar.showSnackbar(context.getString(R.string.export_qgis_geojson_done, count))
                }.onFailure { e ->
                    snackbar.showSnackbar(context.getString(R.string.export_failed_format, e.message ?: ""))
                }
            }
        }
    }
    val exportCsvXyLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val (csv, count) = QgisExportHelper.buildCsvXY(
                    tiges = tigesInScope,
                    essences = essences
                )
                if (count == 0) {
                    snackbar.showSnackbar(context.getString(R.string.export_qgis_no_gps_stems))
                    return@launch
                }
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv) }
                }.onSuccess {
                    snackbar.showSnackbar(context.getString(R.string.export_qgis_csv_xy_done, count))
                }.onFailure { e ->
                    snackbar.showSnackbar(context.getString(R.string.export_failed_format, e.message ?: ""))
                }
            }
        }
    }

    val exportShapefileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        ShapefileExporter.exportToZip(
                            tiges = tigesInScope,
                            essences = essences,
                            outputStream = os
                        )
                    }
                }.onSuccess { result ->
                    val count = result?.stemCount ?: 0
                    if (count == 0) {
                        snackbar.showSnackbar(context.getString(R.string.export_qgis_no_gps_stems))
                    } else {
                        snackbar.showSnackbar(context.getString(R.string.export_shapefile_done, count))
                    }
                }.onFailure { e ->
                    snackbar.showSnackbar(context.getString(R.string.export_failed_format, e.message ?: ""))
                }
            }
        }
    }

    // Pré-remplissage des champs si des valeurs ont déjà été enregistrées pour cette portée
    LaunchedEffect(savedSurface) {
        if (savedSurface != null && surfaceInput.isBlank()) {
            surfaceInput = String.format(Locale.US, "%.0f", savedSurface)
        }
    }
    LaunchedEffect(savedHo) {
        if (savedHo != null && hoInput.isBlank()) {
            hoInput = String.format(Locale.US, "%.1f", savedHo)
        }
    }
    LaunchedEffect(savedNhaAvant) {
        if (savedNhaAvant != null && nHaAvantInput.isBlank()) {
            nHaAvantInput = String.format(Locale.US, "%.0f", savedNhaAvant)
        }
    }
    LaunchedEffect(savedGhaAvant) {
        if (savedGhaAvant != null && gHaAvantInput.isBlank()) {
            gHaAvantInput = String.format(Locale.US, "%.1f", savedGhaAvant)
        }
    }

    fun parseSurfaceM2(raw: String): Double? {
        val s = raw.trim()
        if (s.isBlank()) return null
        val compact = s.lowercase(Locale.getDefault()).replace(" ", "")
        return when {
            compact.endsWith("ha") -> compact.removeSuffix("ha").replace(',', '.').toDoubleOrNull()?.let { it * 10_000.0 }
            compact.endsWith("m2") -> compact.removeSuffix("m2").replace(',', '.').toDoubleOrNull()
            compact.endsWith("m²") -> compact.removeSuffix("m²").replace(',', '.').toDoubleOrNull()
            else -> compact.replace(',', '.').toDoubleOrNull()
        }
    }

    val surfaceInputValueM2 = parseSurfaceM2(surfaceInput)
    val surfaceM2 = when {
        surfaceInput.isBlank() -> savedSurface
        surfaceInputValueM2 != null -> surfaceInputValueM2
        else -> null
    }
    val hoInputValue = hoInput.replace(',', '.').toDoubleOrNull()
    val hoM = hoInputValue ?: savedHo
    val hauteurMap: Map<Int, Double> = fixedClasses.associateWith { d ->
        hauteurParClasseState[d]?.replace(',', '.')?.toDoubleOrNull() ?: hoM ?: 0.0
    }

    // Seule la surface est obligatoire pour activer la synthèse; Ho reste optionnel
    val missingParams = surfaceM2 == null || surfaceM2 <= 0.0

    // Persistance automatique de la surface / Ho valides pour cette portée
    LaunchedEffect(persistParamsRequested) {
        if (persistParamsRequested) {
            if (surfaceInputValueM2 != null && surfaceInputValueM2 > 0.0) {
                userPreferences.setMartelageSurface(scopeKey, surfaceInputValueM2)
            }
            if (hoInputValue != null && hoInputValue > 0.0) {
                userPreferences.setMartelageHo(scopeKey, hoInputValue)
            }
            persistParamsRequested = false
        }
    }

    // Auto-save debounced : persiste surface/Ho dès que la saisie est valide (pas besoin de cliquer Valider)
    LaunchedEffect(surfaceInputValueM2, scopeKey) {
        if (surfaceInputValueM2 != null && surfaceInputValueM2 > 0.0) {
            kotlinx.coroutines.delay(800L)
            userPreferences.setMartelageSurface(scopeKey, surfaceInputValueM2)
        }
    }
    LaunchedEffect(hoInputValue, scopeKey) {
        if (hoInputValue != null && hoInputValue > 0.0) {
            kotlinx.coroutines.delay(800L)
            userPreferences.setMartelageHo(scopeKey, hoInputValue)
        }
    }

    val nHaAvantValue = nHaAvantInput.replace(',', '.').toDoubleOrNull()
    val nHaAvant = nHaAvantValue ?: savedNhaAvant
    val gHaAvantValue = gHaAvantInput.replace(',', '.').toDoubleOrNull()
    val gHaAvant = gHaAvantValue ?: savedGhaAvant

    LaunchedEffect(nHaAvantValue, scopeKey) {
        if (nHaAvantValue != null && nHaAvantValue > 0.0) {
            kotlinx.coroutines.delay(800L)
            userPreferences.setMartelageNhaAvant(scopeKey, nHaAvantValue)
        }
    }
    LaunchedEffect(gHaAvantValue, scopeKey) {
        if (gHaAvantValue != null && gHaAvantValue > 0.0) {
            kotlinx.coroutines.delay(800L)
            userPreferences.setMartelageGhaAvant(scopeKey, gHaAvantValue)
        }
    }

    val synthesisParams by produceState<ForestrySynthesisParams?>(
        initialValue = null,
        forestryCalculator,
        synthesisParamsVersion
    ) {
        value = try {
            forestryCalculator.loadSynthesisParams()
        } catch (_: Throwable) {
            null
        }
    }

    val diameterClasses by produceState<List<Int>>(
        initialValue = emptyList(),
        forestryCalculator,
        synthesisParamsVersion
    ) {
        value = try {
            forestryCalculator.diameterClasses()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    val promptClasses = remember(diameterClasses) {
        if (diameterClasses.isNotEmpty()) diameterClasses else (5..120 step 5).toList()
    }
    val missingHeightEssenceCodesForPrompt = remember(tigesInScope, martelageHeights, promptClasses, synthesisParams) {
        val heightModes = synthesisParams?.heightModes.orEmpty()
        val byEssence = tigesInScope.groupBy { it.essenceCode.trim().uppercase(Locale.getDefault()) }
        byEssence.keys.filter { code ->
            val tigesEss = byEssence[code].orEmpty()
            val byClass = tigesEss.groupBy { forestryCalculator.diameterClassFor(it.diamCm, promptClasses) }
            val manual = martelageHeights[code.trim().uppercase(Locale.getDefault())].orEmpty()
            byClass.entries.any { (diamClass, list) ->
                val hasMissingMeasured = list.any { it.hauteurM == null }
                if (!hasMissingMeasured) {
                    false
                } else {
                    val manualH = manual[diamClass]
                    val mode = heightModes.firstOrNull { it.essence.equals(code, true) && it.diamClass == diamClass }
                    val fixedH = mode?.mode?.equals("FIXED", ignoreCase = true) == true && (mode.fixed ?: 0.0) > 0.0
                    val sampleH = mode?.mode?.equals("SAMPLES", ignoreCase = true) == true && list.any { it.hauteurM != null }
                    val canResolve = manualH != null || fixedH || sampleH
                    !canResolve
                }
            }
        }.sorted()
    }
    val missingHeightsForPrompt = missingHeightEssenceCodesForPrompt.isNotEmpty()
    val shouldPromptHeightsNow = missingHeightsForPrompt && !isHeightPromptSnoozed

    LaunchedEffect(missingParams, shouldPromptHeightsNow, tigesInScope.size, missingHeightEssenceCodesForPrompt) {
        if (!askedParamsOnce && tigesInScope.isNotEmpty() && (missingParams || shouldPromptHeightsNow)) {
            askedParamsOnce = true
            showParamPanel = true
            if (missingParams) {
                showParamDialog = true
            }
            if (shouldPromptHeightsNow && editingHeightsEssenceCode == null) {
                showHeightPromptDialog = true
            }
        }
    }

    // Agrégation martelage (volumes, G, recettes) pour la vue courante
    val stats by produceState<MartelageStats?>(
        initialValue = null,
        tigesInScope,
        surfaceM2,
        viewScope,
        selectedEssenceCodes,
        martelageHeights,
        hoM,
        hauteurMap,
        synthesisParams,
        diameterClasses,
        nHaAvant,
        gHaAvant
    ) {
        value = if (surfaceM2 != null && surfaceM2 > 0.0) {
            computeMartelageStats(
                tigesInScope = tigesInScope,
                surfaceM2 = surfaceM2,
                selectedEssenceCodes = selectedEssenceCodes,
                martelageHeights = martelageHeights,
                synthesisParams = synthesisParams,
                diameterClasses = diameterClasses,
                essences = essences,
                forestryCalculator = forestryCalculator,
                nHaAvant = nHaAvant,
                gHaAvant = gHaAvant
            )
        } else null
    }

    val exportMartelageCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val s = stats
                if (s == null) {
                    snackbar.showSnackbar(context.getString(R.string.martelage_stats_unavailable_error))
                    return@launch
                }
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { w ->
                        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                        w.write("martelage_export_at;${now}\n")
                        w.write("scope;view;surface_m2;ho_m;volume_total_m3;volume_per_ha_m3;volume_completeness_pct;g_total_m2;g_per_ha_m2;revenue_total_eur;revenue_per_ha_eur;unpriced_volume_m3;unpriced_essences;dm_cm;h_mean_m;dg_cm;h_lorey_m\n")
                        val unpricedNames = s.unpricedEssenceNames.distinct().joinToString(",")
                        val missingNames = s.missingHeightEssenceNames.distinct().joinToString(",")
                        w.write(
                            listOf(
                                scope,
                                viewScope.name,
                                String.format(Locale.US, "%.1f", surfaceM2 ?: 0.0),
                                String.format(Locale.US, "%.1f", hoM ?: 0.0),
                                String.format(Locale.US, "%.3f", s.vTotal),
                                String.format(Locale.US, "%.3f", s.vPerHa),
                                String.format(Locale.US, "%.1f", s.volumeCompletenessPct),
                                String.format(Locale.US, "%.3f", s.gTotal),
                                String.format(Locale.US, "%.3f", s.gPerHa),
                                s.revenueTotal?.let { String.format(Locale.US, "%.2f", it) } ?: "",
                                s.revenuePerHa?.let { String.format(Locale.US, "%.2f", it) } ?: "",
                                String.format(Locale.US, "%.3f", s.unpricedVolumeTotal),
                                unpricedNames,
                                s.dm?.let { String.format(Locale.US, "%.2f", it) } ?: "",
                                s.meanH?.let { String.format(Locale.US, "%.2f", it) } ?: "",
                                s.dg?.let { String.format(Locale.US, "%.2f", it) } ?: "",
                                s.hLorey?.let { String.format(Locale.US, "%.2f", it) } ?: ""
                            ).joinToString(";") + "\n"
                        )

                        if (s.missingHeightEssenceCodes.isNotEmpty()) {
                            w.write("missing_heights;${missingNames}\n")
                        }

                        w.write("\n")
                        w.write("per_essence\n")
                        w.write("essence_code;essence_name;n;v_total_m3;v_per_ha_m3;g_total_m2;g_per_ha_m2;mean_price_eur_m3;revenue_total_eur;revenue_per_ha_eur\n")
                        s.perEssence.forEach { row ->
                            w.write(
                                listOf(
                                    row.essenceCode,
                                    row.essenceName,
                                    row.n.toString(),
                                    String.format(Locale.US, "%.3f", row.vTotal),
                                    String.format(Locale.US, "%.3f", row.vPerHa),
                                    String.format(Locale.US, "%.3f", row.gTotal),
                                    String.format(Locale.US, "%.3f", row.gPerHa),
                                    row.meanPricePerM3?.let { String.format(Locale.US, "%.2f", it) } ?: "",
                                    row.revenueTotal?.let { String.format(Locale.US, "%.2f", it) } ?: "",
                                    row.revenuePerHa?.let { String.format(Locale.US, "%.2f", it) } ?: ""
                                ).joinToString(";") + "\n"
                            )
                        }
                    }
                }.onSuccess {
                    snackbar.showSnackbar(context.getString(R.string.result_exported))
                }.onFailure { e ->
                    snackbar.showSnackbar(context.getString(R.string.export_failed_format, e.message ?: ""))
                }
            }
        }
    }

    // Export PDF synthèse
    val exportPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val s = stats
                if (s == null) {
                    snackbar.showSnackbar(context.getString(R.string.martelage_stats_unavailable_error))
                    return@launch
                }
                runCatching {
                    PdfSynthesisExporter.export(
                        context = context,
                        uri = uri,
                        stats = s,
                        scopeLabel = scopeKey,
                        surfaceM2 = surfaceM2,
                        productBreakdown = breakdownByEssence
                    )
                }.onSuccess {
                    snackbar.showSnackbar(context.getString(R.string.pdf_exported))
                }.onFailure { e ->
                    snackbar.showSnackbar(context.getString(R.string.export_failed_format, e.message ?: ""))
                }
            }
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
            val topBarBackground = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            val topBarContent = ColorUtils.getContrastingTextColor(topBarBackground)
            TopAppBar(
                title = { Text(stringResource(R.string.martelage_before_cut_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        playClickFeedback()
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    // Carte des tiges
                    if (parcelleId != null && onNavigateToMap != null) {
                        IconButton(
                            onClick = {
                                playClickFeedback()
                                onNavigateToMap(parcelleId)
                            }
                        ) {
                            Icon(Icons.Default.Map, contentDescription = stringResource(R.string.map_view))
                        }
                    }
                    // Partager synthèse (texte)
                    IconButton(
                        onClick = {
                            playClickFeedback()
                            val s = stats
                            if (s != null) {
                                val surfHa = (surfaceM2 ?: 0.0) / 10_000.0
                                val text = buildString {
                                    appendLine(context.getString(R.string.share_synthesis_header))
                                    appendLine("${context.getString(R.string.share_scope)}: $scopeKey")
                                    if (surfHa > 0) appendLine("${context.getString(R.string.share_surface)}: ${String.format("%.2f", surfHa)} ha")
                                    appendLine("N: ${s.nTotal} ${context.getString(R.string.stems)} (${String.format("%.0f", s.nPerHa)} /ha)")
                                    appendLine("G: ${String.format("%.2f", s.gTotal)} m² (${String.format("%.2f", s.gPerHa)} m²/ha)")
                                    if (s.vTotal > 0) appendLine("V: ${String.format("%.1f", s.vTotal)} m³ (${String.format("%.1f", s.vPerHa)} m³/ha)")
                                    s.dg?.let { appendLine("Dg: ${String.format("%.1f", it)} cm") }
                                    s.dm?.let { appendLine("Dm: ${String.format("%.1f", it)} cm") }
                                    s.hLorey?.let { appendLine("Hdom: ${String.format("%.1f", it)} m") }
                                    s.revenueTotal?.let { appendLine("${context.getString(R.string.share_revenue)}: ${String.format("%.0f", it)} €") }
                                    appendLine(context.getString(R.string.share_footer))
                                }
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_synthesis_header))
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_synthesis_header)))
                            } else {
                                coroutineScope.launch { snackbar.showSnackbar(context.getString(R.string.martelage_stats_unavailable_error)) }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                    }
                    // Export (ouvre le dialogue avec toutes les options)
                    IconButton(
                        onClick = {
                            playClickFeedback()
                            showExportDialog = true
                        }
                    ) {
                        Icon(Icons.Default.Download, contentDescription = stringResource(R.string.export))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarBackground,
                    titleContentColor = topBarContent,
                    navigationIconContentColor = topBarContent,
                    actionIconContentColor = topBarContent
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
            val contentScrollState = rememberScrollState()

            // Fond semi-opaque pour le contenu et couleur de texte auto-contrastée globale
            val pageBackground = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
            val pageTextColor = ColorUtils.getContrastingTextColor(pageBackground)

            CompositionLocalProvider(LocalContentColor provides pageTextColor) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp })
                        .verticalScroll(contentScrollState)
                        .padding(padding)
                        .background(pageBackground)
                        .padding(12.dp)
                        .animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                // ── En-tête compact ──
                val scopeLabel = when (scope.uppercase(Locale.getDefault())) {
                    "PLACETTE" -> stringResource(R.string.martelage_scope_placette_desc)
                    "PARCELLE" -> stringResource(R.string.martelage_scope_parcelle_desc)
                    "FOREST" -> stringResource(R.string.martelage_scope_forest_desc)
                    else -> stringResource(R.string.martelage_scope_global_desc)
                }
                val gpsCount = remember(tigesInScope) { tigesInScope.count { !it.gpsWkt.isNullOrBlank() } }

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.martelage_summary_title), style = MaterialTheme.typography.titleLarge)
                            AssistChip(
                                onClick = { playClickFeedback(); showParamPanel = !showParamPanel },
                                label = { Text(stringResource(R.string.martelage_parameters)) },
                                leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) }
                            )
                        }
                        Text(scopeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (parcellesInScope.isNotEmpty()) {
                            Text(
                                stringResource(R.string.martelage_included_parcelles_format, parcellesInScope.joinToString { it.name }),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            if (placetteId != null) FilterChip(selected = viewScope == MartelageViewScope.PLACETTE, onClick = { viewScope = MartelageViewScope.PLACETTE }, label = { Text(stringResource(R.string.martelage_view_placette)) })
                            if (parcelleId != null) FilterChip(selected = viewScope == MartelageViewScope.PARCELLE, onClick = { viewScope = MartelageViewScope.PARCELLE }, label = { Text(stringResource(R.string.martelage_view_parcelle)) })
                            FilterChip(selected = viewScope == MartelageViewScope.GLOBAL, onClick = { viewScope = MartelageViewScope.GLOBAL }, label = { Text(stringResource(R.string.martelage_view_global)) })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AssistChip(
                                onClick = { showTarifMethodDialog = true },
                                label = { Text(stringResource(R.string.martelage_cubage_method_current_format, currentTarifMethod.label), style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                            if (gpsCount > 0) AssistChip(
                                onClick = { showExportDialog = true },
                                label = { Text(stringResource(R.string.gps_count_label, gpsCount), style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = { Icon(Icons.Default.GpsFixed, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                }

                if (tigesInScope.isEmpty()) {
                    Text(
                        stringResource(R.string.martelage_no_tiges_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    val placeholderDash = stringResource(R.string.placeholder_dash)
                    val ellipsis = stringResource(R.string.ellipsis)
                    val euroSymbol = stringResource(R.string.euro_symbol)

                    // Sélection d'essences (multi)
                    Text(stringResource(R.string.martelage_available_species), style = MaterialTheme.typography.titleMedium)
                    if (availableEssences.isEmpty()) {
                        Text(
                            stringResource(R.string.martelage_no_resinous_species_detected),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            availableEssences.forEach { e ->
                                val normalized = normalizeEssenceCode(e.code)
                                val selected = selectedEssenceCodes.contains(normalized)
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        playClickFeedback()
                                        selectedEssenceCodes = if (selected) {
                                            (selectedEssenceCodes - normalized).ifEmpty { setOf(normalized) }
                                        } else {
                                            selectedEssenceCodes + normalized
                                        }
                                    },
                                    label = { Text(e.name) }
                                )
                            }
                        }

                    
                    }

                    // Paramètres de calcul (mini menu animé)
                    AnimatedVisibility(
                        visible = showParamPanel,
                        enter = fadeIn(animationSpec = tween(durationMillis = 200)) + expandVertically(),
                        exit = fadeOut(animationSpec = tween(durationMillis = 200)) + shrinkVertically()
                    ) {
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Tune,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            stringResource(R.string.martelage_calc_parameters_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                    ) {
                                        FilledTonalButton(
                                            onClick = {
                                                playClickFeedback()
                                                if (surfaceInputValueM2 == null || surfaceInputValueM2 <= 0.0) {
                                                    coroutineScope.launch {
                                                        snackbar.showSnackbar(context.getString(R.string.martelage_sample_area_missing_error))
                                                    }
                                                    return@FilledTonalButton
                                                } else {
                                                    persistParamsRequested = true
                                                    if (!missingHeightsForPrompt) {
                                                        showParamPanel = false
                                                    }
                                                }
                                            }
                                        ) {
                                            Text(stringResource(R.string.validate))
                                        }
                                        IconButton(
                                            onClick = {
                                                playClickFeedback()
                                                showParamPanel = false
                                            }
                                        ) {
                                            Icon(Icons.Filled.Close, contentDescription = null)
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = surfaceInput,
                                    onValueChange = { surfaceInput = it },
                                    label = { Text(stringResource(R.string.martelage_sample_area_m2)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal,
                                        imeAction = ImeAction.Next
                                    ),
                                    placeholder = { Text(stringResource(R.string.placeholder_surface)) },
                                    suffix = { Text(stringResource(R.string.unit_m2)) },
                                    singleLine = true
                                )
                                // Hint de conversion en hectares
                                if (surfaceInputValueM2 != null && surfaceInputValueM2 > 0) {
                                    Text(
                                        "≈ ${String.format(Locale.getDefault(), "%.4f", surfaceInputValueM2 / 10_000.0)} ha",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = hoInput,
                                    onValueChange = { hoInput = it },
                                    label = { Text(stringResource(R.string.martelage_ho_dominant_optional)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal,
                                        imeAction = ImeAction.Done
                                    ),
                                    placeholder = { Text(stringResource(R.string.placeholder_height)) },
                                    suffix = { Text(stringResource(R.string.unit_m)) },
                                    singleLine = true
                                )

                                // ── Peuplement avant coupe (simulation) ──
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                Row(
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Analytics,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                    )
                                    Text(
                                        stringResource(R.string.martelage_avant_coupe_hint),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = nHaAvantInput,
                                        onValueChange = { nHaAvantInput = it },
                                        label = { Text(stringResource(R.string.martelage_nha_avant_label)) },
                                        modifier = Modifier.weight(1f),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Decimal,
                                            imeAction = ImeAction.Next
                                        ),
                                        placeholder = { Text(stringResource(R.string.placeholder_nha)) },
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = gHaAvantInput,
                                        onValueChange = { gHaAvantInput = it },
                                        label = { Text(stringResource(R.string.martelage_gha_avant_label)) },
                                        modifier = Modifier.weight(1f),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Decimal,
                                            imeAction = ImeAction.Done
                                        ),
                                        placeholder = { Text(stringResource(R.string.placeholder_gha)) },
                                        suffix = { Text(stringResource(R.string.unit_m2)) },
                                        singleLine = true
                                    )
                                }

                                // ── Sélecteur de méthode de cubage ──
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Dashboard,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                stringResource(R.string.martelage_cubage_method),
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                        }
                                        Text(
                                            stringResource(R.string.martelage_cubage_method_current_format, currentTarifMethod.label),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        val tarifNum = currentTarifNumero
                                        if (tarifNum != null) {
                                            Text(
                                                stringResource(R.string.martelage_cubage_method_numero_format, tarifNum),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            playClickFeedback()
                                            showTarifMethodDialog = true
                                        }
                                    ) {
                                        Text(stringResource(R.string.martelage_cubage_method_change))
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Height,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        stringResource(R.string.martelage_heights_by_species_or_ho_desc),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }

                                val heightModes = synthesisParams?.heightModes.orEmpty()
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    availableEssences.forEach { ess ->
                                        val customMap = martelageHeights[normalizeEssenceCode(ess.code)]
                                        val hasCustomManual = customMap != null && customMap.isNotEmpty()
                                        val hasCustomFixed = heightModes.any {
                                            it.essence.equals(ess.code, true) &&
                                                it.mode.equals("FIXED", ignoreCase = true) &&
                                                (it.fixed ?: 0.0) > 0.0
                                        }
                                        val hasCustom = hasCustomManual || hasCustomFixed
                                        val missing = missingHeightEssenceCodesForPrompt.contains(normalizeEssenceCode(ess.code))

                                        val targetCardColor = when {
                                            missing -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                                            hasCustom -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
                                            else -> MaterialTheme.colorScheme.surface
                                        }
                                        val cardColor by animateColorAsState(
                                            targetValue = targetCardColor,
                                            animationSpec = tween(
                                                durationMillis = if (animationsEnabled) 220 else 0,
                                                easing = FastOutSlowInEasing
                                            ),
                                            label = "martelageHeightEssenceCardColor"
                                        )
                                        val targetElevation = when {
                                            missing -> 4.dp
                                            hasCustom -> 2.dp
                                            else -> 0.dp
                                        }
                                        val cardElevation by animateDpAsState(
                                            targetValue = targetElevation,
                                            animationSpec = tween(
                                                durationMillis = if (animationsEnabled) 220 else 0,
                                                easing = FastOutSlowInEasing
                                            ),
                                            label = "martelageHeightEssenceCardElevation"
                                        )
                                        Card(
                                            onClick = {
                                                playClickFeedback()
                                                editingHeightsEssenceCode = ess.code
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = cardColor),
                                            elevation = CardDefaults.cardElevation(defaultElevation = cardElevation)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        ess.name,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    AnimatedVisibility(
                                                        visible = missing,
                                                        enter = fadeIn(animationSpec = tween(durationMillis = if (animationsEnabled) 160 else 0, easing = FastOutSlowInEasing)) +
                                                            expandHorizontally(animationSpec = tween(durationMillis = if (animationsEnabled) 200 else 0, easing = FastOutSlowInEasing)),
                                                        exit = fadeOut(animationSpec = tween(durationMillis = if (animationsEnabled) 140 else 0, easing = FastOutSlowInEasing)) +
                                                            shrinkHorizontally(animationSpec = tween(durationMillis = if (animationsEnabled) 180 else 0, easing = FastOutSlowInEasing))
                                                    ) {
                                                        ToCompleteBadge()
                                                    }
                                                }
                                                Text(
                                                    stringResource(if (hasCustom) R.string.martelage_custom_heights_defined else R.string.martelage_default_height_tables_used),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                if (missingParams) {
                                    Row(
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            stringResource(R.string.martelage_sample_area_missing_error),
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = { showParamDialog = true }) {
                                            Text(stringResource(R.string.martelage_fill_in))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val s = stats
                    if (!missingParams && s != null) {
                        Spacer(modifier = Modifier.height(12.dp))

                        val vTotalAnim by animateFloatAsState(
                            targetValue = s.vTotal.toFloat(),
                            animationSpec = tween(durationMillis = if (animationsEnabled) 380 else 0, easing = FastOutSlowInEasing),
                            label = "vTotalAnim"
                        )
                        val vPerHaAnim by animateFloatAsState(
                            targetValue = s.vPerHa.toFloat(),
                            animationSpec = tween(durationMillis = if (animationsEnabled) 380 else 0, easing = FastOutSlowInEasing),
                            label = "vPerHaAnim"
                        )
                        val revenueTotalAnim by animateFloatAsState(
                            targetValue = (s.revenueTotal ?: 0.0).toFloat(),
                            animationSpec = tween(durationMillis = if (animationsEnabled) 380 else 0, easing = FastOutSlowInEasing),
                            label = "revenueTotalAnim"
                        )
                        val revenuePerHaAnim by animateFloatAsState(
                            targetValue = (s.revenuePerHa ?: 0.0).toFloat(),
                            animationSpec = tween(durationMillis = if (animationsEnabled) 380 else 0, easing = FastOutSlowInEasing),
                            label = "revenuePerHaAnim"
                        )
                        val missingPriceVolAnim by animateFloatAsState(
                            targetValue = s.unpricedVolumeTotal.toFloat(),
                            animationSpec = tween(durationMillis = if (animationsEnabled) 380 else 0, easing = FastOutSlowInEasing),
                            label = "missingPriceVolAnim"
                        )

                        val revenueTotalText = if (s.revenueTotal == null) placeholderDash else formatMoney(revenueTotalAnim.toDouble(), placeholderDash, euroSymbol)
                        val revenuePerHaText = if (s.revenuePerHa == null) placeholderDash else formatMoney(revenuePerHaAnim.toDouble(), placeholderDash, euroSymbol)

                        val missingPriceVol = s.unpricedVolumeTotal
                        val missingPriceVisible = missingPriceVol > 0.0 && s.vTotal > 0.0

                        AnimatedVisibility(
                            visible = s.missingHeightEssenceCodes.isNotEmpty(),
                            enter = fadeIn(animationSpec = tween(durationMillis = if (animationsEnabled) 180 else 0, easing = FastOutSlowInEasing)) +
                                expandVertically(animationSpec = tween(durationMillis = if (animationsEnabled) 220 else 0, easing = FastOutSlowInEasing)),
                            exit = fadeOut(animationSpec = tween(durationMillis = if (animationsEnabled) 180 else 0, easing = FastOutSlowInEasing)) +
                                shrinkVertically(animationSpec = tween(durationMillis = if (animationsEnabled) 220 else 0, easing = FastOutSlowInEasing))
                        ) {
                            val warnBg = MaterialTheme.colorScheme.errorContainer
                            val warnFg = ColorUtils.getContrastingTextColor(warnBg)
                            val names = s.missingHeightEssenceNames.take(3).joinToString(", ") +
                                if (s.missingHeightEssenceNames.size > 3) ellipsis else ""
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp)),
                                colors = CardDefaults.cardColors(
                                    containerColor = warnBg,
                                    contentColor = warnFg
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Warning, contentDescription = null)
                                        Text(
                                            stringResource(R.string.martelage_missing_heights_title),
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                    Text(
                                        stringResource(R.string.martelage_missing_heights_desc, names),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    TextButton(
                                        onClick = {
                                            playClickFeedback()
                                            showParamPanel = true
                                            editingHeightsEssenceCode = s.missingHeightEssenceCodes.firstOrNull()
                                        }
                                    ) {
                                        Text(stringResource(R.string.martelage_fill_heights))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // ── Onglets (TabRow fixe, pas de scroll) ──
                        // Ordre : 0=Synthèse | 1=Valorisation | 2=Analyse | 3=Gestion
                        TabRow(selectedTabIndex = selectedTabIndex) {
                            Tab(
                                selected = selectedTabIndex == 0,
                                onClick = { selectedTabIndex = 0 },
                                text = { Text(stringResource(R.string.tab_synthesis), style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                            )
                            Tab(
                                selected = selectedTabIndex == 1,
                                onClick = { selectedTabIndex = 1 },
                                text = { Text(stringResource(R.string.tab_valuation), style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                            )
                            Tab(
                                selected = selectedTabIndex == 2,
                                onClick = { selectedTabIndex = 2 },
                                text = { Text(stringResource(R.string.tab_analysis), style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                            )
                            Tab(
                                selected = selectedTabIndex == 3,
                                onClick = { selectedTabIndex = 3 },
                                text = { Text("Gestion", style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // ── TAB 0 : Synthèse ──
                        if (selectedTabIndex == 0) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                VolumeCard(
                                    vTotalText = formatVolume(vTotalAnim.toDouble()),
                                    vPerHaText = formatVolume(vPerHaAnim.toDouble()),
                                    revenueTotalText = revenueTotalText,
                                    revenuePerHaText = revenuePerHaText,
                                    volumeAvailable = s.volumeAvailable,
                                    volumeCompletenessPct = s.volumeCompletenessPct
                                )
                                BasalAreaCard(
                                    gTotal = s.gTotal,
                                    gPerHa = s.gPerHa,
                                    surfaceHa = s.surfaceHa,
                                    ratioVG = s.ratioVG
                                )
                                DensityCard(
                                    nTotal = s.nTotal,
                                    nPerHa = s.nPerHa,
                                    dm = s.dm,
                                    meanH = s.meanH,
                                    dg = s.dg,
                                    hLorey = s.hLorey,
                                    dMin = s.dMin,
                                    dMax = s.dMax,
                                    cvDiam = s.cvDiam,
                                    placeholderDash = placeholderDash
                                )
                                DataCompletenessCard(stats = s)
                            }
                        }

                        // ── TAB 1 : Valorisation ──
                        if (selectedTabIndex == 1) {
                            AnimatedVisibility(visible = missingPriceVisible) {
                                val pct = ((missingPriceVol / s.vTotal.coerceAtLeast(1e-9)) * 100.0).coerceIn(0.0, 100.0).roundToInt()
                                val warnBg = MaterialTheme.colorScheme.errorContainer
                                val warnFg = ColorUtils.getContrastingTextColor(warnBg)
                                Card(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                                    colors = CardDefaults.cardColors(containerColor = warnBg, contentColor = warnFg)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Default.Warning, contentDescription = null)
                                            Text(stringResource(R.string.martelage_missing_prices_title), style = MaterialTheme.typography.titleMedium)
                                        }
                                        Text(stringResource(R.string.martelage_missing_prices_desc, formatVolume(missingPriceVolAnim.toDouble()), pct), style = MaterialTheme.typography.bodyMedium)
                                        if (s.unpricedEssenceNames.isNotEmpty()) {
                                            val names = s.unpricedEssenceNames.take(3).joinToString(", ") + if (s.unpricedEssenceNames.size > 3) ellipsis else ""
                                            Text(stringResource(R.string.martelage_missing_prices_essences, names), style = MaterialTheme.typography.bodySmall)
                                        }
                                        (onNavigateToPriceTablesEditor ?: onNavigateToSettings)?.let { nav ->
                                            TextButton(onClick = { playClickFeedback(); nav() }) {
                                                Text(stringResource(R.string.martelage_configure_prices))
                                            }
                                        }
                                    }
                                }
                            }

                            // Ventilation produits par essence
                            LaunchedEffect(s.perEssence) {
                                val prices = forestryCalculator.loadPriceEntries()
                                val result = mutableMapOf<String, List<ProductBreakdownRow>>()
                                s.perEssence.forEach { ess ->
                                    if (ess.vTotal <= 0.0) return@forEach
                                    val diam = (ess.dm ?: ess.dg ?: 30.0).toInt()
                                    val q = ess.dominantQuality?.code
                                    val v = ess.vTotal
                                    val products: Map<String, Double> = when {
                                        diam >= 60 -> mapOf("BO" to v * 0.85, "BI" to v * 0.10, "BCh" to v * 0.05)
                                        diam >= 45 -> mapOf("BO" to v * 0.75, "BI" to v * 0.15, "BCh" to v * 0.10)
                                        diam >= 35 -> mapOf("BO" to v * 0.55, "BI" to v * 0.30, "BCh" to v * 0.15)
                                        diam >= 25 -> mapOf("BO" to v * 0.30, "BI" to v * 0.45, "BCh" to v * 0.25)
                                        diam >= 15 -> mapOf("BI" to v * 0.40, "BCh" to v * 0.35, "PATE" to v * 0.25)
                                        else       -> mapOf("BCh" to v * 0.50, "PATE" to v * 0.50)
                                    }
                                    val baseBreakdown = PriceCalculator.buildBreakdown(
                                        prices = prices, essenceCode = ess.essenceCode,
                                        volumeByProduct = products, diamCm = diam, quality = q
                                    )
                                    // Align product breakdown total with authoritative synthesis revenue
                                    val synRevenue = ess.revenueTotal
                                    val breakdownTotal = baseBreakdown.sumOf { it.totalEur }
                                    result[ess.essenceCode] = if (synRevenue != null && synRevenue > 0.0 && breakdownTotal > 0.0) {
                                        val scale = synRevenue / breakdownTotal
                                        baseBreakdown.map { row -> row.copy(totalEur = row.totalEur * scale) }
                                    } else {
                                        baseBreakdown
                                    }
                                }
                                breakdownByEssence = result
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                s.perEssence.filter { it.vTotal > 0.0 }.forEach { ess ->
                                    val rows = breakdownByEssence[ess.essenceCode]
                                    if (!rows.isNullOrEmpty()) ProductBreakdownCard(essenceName = ess.essenceName, quality = ess.dominantQuality?.code, rows = rows)
                                }
                                if (s.perEssence.isNotEmpty()) {
                                    val sortedPerEssence = remember(s.perEssence, essenceSortKey) {
                                        when (essenceSortKey) {
                                            "n"       -> s.perEssence.sortedByDescending { it.n }
                                            "volume"  -> s.perEssence.sortedByDescending { it.vTotal }
                                            "revenue" -> s.perEssence.sortedByDescending { it.revenueTotal ?: 0.0 }
                                            "g"       -> s.perEssence.sortedByDescending { it.gTotal }
                                            else      -> s.perEssence.sortedBy { it.essenceName }
                                        }
                                    }
                                    val sortKeys = listOf(
                                        "name" to stringResource(R.string.sort_name),
                                        "n" to "N", "g" to "G", "volume" to "V",
                                        "revenue" to stringResource(R.string.sort_revenue)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        sortKeys.forEach { (key, label) ->
                                            FilterChip(selected = essenceSortKey == key, onClick = { essenceSortKey = key }, label = { Text(label, style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.height(28.dp))
                                        }
                                    }
                                    PerEssenceTable(perEssence = sortedPerEssence, essences = essences, placeholderDash = placeholderDash, euroSymbol = euroSymbol)
                                }
                            }
                        }

                        // ── TAB 2 : Analyse ──
                        if (selectedTabIndex == 2) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (s.sanityWarnings.isNotEmpty()) SanityWarningsCard(warnings = s.sanityWarnings)
                                if (s.harvestNhaPct != null || s.harvestGhaPct != null) HarvestSimulationCard(
                                    harvestNhaPct = s.harvestNhaPct, harvestGhaPct = s.harvestGhaPct,
                                    residualNha = s.residualNha, residualGha = s.residualGha
                                )
                                if (s.classDistribution.isNotEmpty()) ClassDistributionCard(classDistribution = s.classDistribution)
                                if (s.qualityDistribution.isNotEmpty()) QualityDistributionCard(qualityDistribution = s.qualityDistribution, assessedCount = s.qualityAssessedCount, totalCount = s.qualityTotalCount)
                                if (s.specialTrees.isNotEmpty()) SpecialTreesCard(specialTrees = s.specialTrees)
                                s.biodiversity?.let { bio -> BiodiversityCard(bio = bio) }
                                CorroborationReportCard(stats = s)
                                SylviculturalKPIsCard(stats = s)
                            }
                        }

                        // ── TAB 3 : Gestion ──
                        if (selectedTabIndex == 3) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                TypelogiqueRapideCard(prefilledGPerHa = s.gPerHa)
                                IbpMartelageCard(
                                    ibpEval = latestIbp,
                                    parcelleId = parcelleId,
                                    placetteId = placetteId,
                                    onNavigateToIbp = onNavigateToIbp,
                                    onNavigateToHistory = onNavigateToIbpHistory,
                                    evaluationCount = ibpEvaluations.size
                                )
                                if (fertilityResults.isNotEmpty()) StandFertilityCard(fertilityResults = fertilityResults)
                                if (parcelleId != null && onNavigateToStandClassification != null) StandClassificationBannerCard(
                                    stats = s,
                                    onNavigateToClassification = {
                                        playClickFeedback()
                                        StandClassificationCache.lastStats = s
                                        StandClassificationCache.lastParcelleId = parcelleId
                                        onNavigateToStandClassification(parcelleId)
                                    }
                                )
                                if (parcelleId != null && onNavigateToSuperCorrelateur != null) SuperCorrelateurBannerCard(
                                    onClick = { playClickFeedback(); onNavigateToSuperCorrelateur(parcelleId) }
                                )
                            }
                        }
                    }

                    if (!missingParams && stats == null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.martelage_stats_unavailable_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    } 
    if (showParamDialog) {
        AppMiniDialog(
            onDismissRequest = { showParamDialog = false },
            animationsEnabled = animationsEnabled,
            icon = Icons.Filled.Tune,
            title = stringResource(R.string.martelage_calc_parameters_title),
            description = stringResource(R.string.martelage_params_dialog_desc),
            confirmText = stringResource(R.string.validate),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                persistParamsRequested = true
                showParamDialog = false
            }
        ) {
            OutlinedTextField(
                value = surfaceInput,
                onValueChange = { surfaceInput = it },
                label = { Text(stringResource(R.string.martelage_sample_area_m2)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                placeholder = { Text(stringResource(R.string.placeholder_surface)) },
                suffix = { Text(stringResource(R.string.unit_m2)) },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = hoInput,
                onValueChange = { hoInput = it },
                label = { Text(stringResource(R.string.martelage_ho_dominant_optional)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                placeholder = { Text(stringResource(R.string.placeholder_height)) },
                suffix = { Text(stringResource(R.string.unit_m)) },
                singleLine = true
            )
            Text(
                stringResource(R.string.martelage_params_dialog_hint),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (showHeightPromptDialog) {
        val names = missingHeightEssenceCodesForPrompt
            .map { code ->
                availableEssences.firstOrNull { normalizeEssenceCode(it.code) == normalizeEssenceCode(code) }?.name ?: code
            }
            .joinToString(", ")
        AppMiniDialog(
            onDismissRequest = { showHeightPromptDialog = false },
            animationsEnabled = animationsEnabled,
            icon = Icons.Default.Warning,
            title = stringResource(R.string.martelage_missing_heights_title),
            description = stringResource(R.string.martelage_missing_heights_desc, names.ifBlank { "—" }),
            confirmText = stringResource(R.string.martelage_fill_heights),
            dismissText = stringResource(R.string.mandatory_heights_skip),
            neutralText = stringResource(R.string.mandatory_heights_snooze_title),
            onDismiss = { showHeightPromptDialog = false },
            onNeutral = {
                showHeightPromptDialog = false
                heightSnoozeHours = 1
                showHeightSnoozeDialog = true
            },
            onConfirm = {
                showHeightPromptDialog = false
                editingHeightsEssenceCode = missingHeightEssenceCodesForPrompt.firstOrNull()
            }
        )
    }

    if (showHeightSnoozeDialog) {
        AppMiniDialog(
            onDismissRequest = { showHeightSnoozeDialog = false },
            animationsEnabled = animationsEnabled,
            icon = Icons.Default.Warning,
            title = stringResource(R.string.mandatory_heights_snooze_title),
            description = stringResource(R.string.mandatory_heights_desc),
            confirmText = stringResource(R.string.validate),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                val hours = heightSnoozeHours.coerceAtLeast(1)
                showHeightSnoozeDialog = false
                coroutineScope.launch {
                    userPreferences.snoozeHeightPromptForHours(hours)
                    snackbar.showSnackbar(context.getString(R.string.mandatory_heights_snoozed_format, hours))
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(1, 4, 24).forEach { hours ->
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(
                            selected = heightSnoozeHours == hours,
                            onClick = { heightSnoozeHours = hours }
                        )
                        Text(stringResource(R.string.mandatory_heights_snooze_format, hours))
                    }
                }
            }
        }
    }

    // Dialog de saisie des hauteurs par essence et par classe de diamètre (uniquement classes où il y a des tiges)
    val editingEssenceCode = editingHeightsEssenceCode
    if (editingEssenceCode != null) {
        val normalizedEditingEssenceCode = remember(editingEssenceCode) { editingEssenceCode.trim().uppercase(Locale.getDefault()) }
        val tigesEssence = remember(tigesInScope, editingEssenceCode) {
            tigesInScope.filter { it.essenceCode.trim().equals(editingEssenceCode.trim(), ignoreCase = true) }
        }
        val classesForDialog = remember(diameterClasses) {
            if (diameterClasses.isNotEmpty()) diameterClasses else (5..120 step 5).toList()
        }
        val byClass = remember(tigesEssence, classesForDialog) {
            tigesEssence.groupBy { forestryCalculator.diameterClassFor(it.diamCm, classesForDialog) }
        }
        val presentClasses = remember(byClass) {
            val counts = byClass.mapValues { it.value.size }
            byClass.keys.sortedWith(compareByDescending<Int> { counts[it] ?: 0 }.thenBy { it })
        }

        val existingForEssence = martelageHeights[normalizedEditingEssenceCode] ?: emptyMap()
        val fixedForEssence = remember(synthesisParams, editingEssenceCode) {
            synthesisParams?.heightModes
                .orEmpty()
                .filter {
                    it.essence.trim().equals(normalizedEditingEssenceCode, true) &&
                        it.mode.equals("FIXED", ignoreCase = true) &&
                        (it.fixed ?: 0.0) > 0.0
                }
                .associate { it.diamClass to (it.fixed ?: 0.0) }
        }
        var localInputs by remember(editingEssenceCode, presentClasses, fixedForEssence) {
            mutableStateOf(
                presentClasses.associateWith { cls ->
                    val v = existingForEssence[cls] ?: fixedForEssence[cls]
                    v?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: ""
                }
            )
        }

        val essenceName = availableEssences.firstOrNull { normalizeEssenceCode(it.code) == normalizedEditingEssenceCode }?.name ?: editingEssenceCode

        val dialogDesc = if (presentClasses.isEmpty()) {
            stringResource(R.string.martelage_no_stem_for_species)
        } else {
            stringResource(R.string.martelage_height_by_class_hint)
        }
        AppMiniDialog(
            onDismissRequest = { editingHeightsEssenceCode = null },
            animationsEnabled = animationsEnabled,
            icon = Icons.Default.Height,
            title = stringResource(R.string.martelage_heights_dialog_title_format, essenceName),
            description = dialogDesc,
            confirmText = stringResource(R.string.validate),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                val cleanedForEssence: Map<Int, Double> = localInputs.mapNotNull { (cls, str) ->
                    val (mean, _) = parseHeightInputMean(str)
                    if (mean != null && mean > 0.0) cls to mean else null
                }.toMap()

                val newMap = martelageHeightsLocal.toMutableMap().apply {
                    if (cleanedForEssence.isEmpty()) remove(normalizedEditingEssenceCode) else put(normalizedEditingEssenceCode, cleanedForEssence)
                    if (editingEssenceCode != normalizedEditingEssenceCode) remove(editingEssenceCode)
                }

                coroutineScope.launch {
                    userPreferences.setMartelageHeights(scopeKey, newMap)

                    // Propager vers le scope FOREST pour éviter l'effet "re-demande" en vue projet / global.
                    val forestKey = forestScopeKeyForHeights
                    if (!forestKey.isNullOrBlank() && forestKey != scopeKey) {
                        val forestMap = martelageHeightsForest.toMutableMap().apply {
                            if (cleanedForEssence.isEmpty()) {
                                remove(normalizedEditingEssenceCode)
                            } else {
                                put(normalizedEditingEssenceCode, cleanedForEssence)
                            }
                        }
                        userPreferences.setMartelageHeights(forestKey, forestMap)
                    }
                }

                editingHeightsEssenceCode = null
            }
        ) {
            if (presentClasses.isNotEmpty()) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                ) {
                    items(presentClasses, key = { it }) { cls ->
                        val count = byClass[cls]?.size ?: 0
                        val missingCount = byClass[cls]?.count { it.hauteurM == null } ?: 0
                        val (meanVal, meanCount) = parseHeightInputMean(localInputs[cls] ?: "")
                        val hasValue = meanVal != null && meanVal > 0.0
                        val needsValue = missingCount > 0 && !hasValue

                        val cardTargetColor = if (needsValue) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        }
                        val cardColor by animateColorAsState(
                            targetValue = cardTargetColor,
                            animationSpec = tween(
                                durationMillis = if (animationsEnabled) 220 else 0,
                                easing = FastOutSlowInEasing
                            ),
                            label = "martelageHeightClassCardColor"
                        )
                        val cardElevation by animateDpAsState(
                            targetValue = if (needsValue) 4.dp else 1.dp,
                            animationSpec = tween(
                                durationMillis = if (animationsEnabled) 220 else 0,
                                easing = FastOutSlowInEasing
                            ),
                            label = "martelageHeightClassCardElevation"
                        )

                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = cardColor
                            ),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = cardElevation)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.martelage_diameter_class_cm_format, cls),
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    AnimatedVisibility(
                                        visible = needsValue,
                                        enter = fadeIn(animationSpec = tween(durationMillis = if (animationsEnabled) 160 else 0, easing = FastOutSlowInEasing)) +
                                            expandHorizontally(animationSpec = tween(durationMillis = if (animationsEnabled) 200 else 0, easing = FastOutSlowInEasing)),
                                        exit = fadeOut(animationSpec = tween(durationMillis = if (animationsEnabled) 140 else 0, easing = FastOutSlowInEasing)) +
                                            shrinkHorizontally(animationSpec = tween(durationMillis = if (animationsEnabled) 180 else 0, easing = FastOutSlowInEasing))
                                    ) {
                                        ToCompleteBadge()
                                    }
                                }
                                OutlinedTextField(
                                    value = localInputs[cls] ?: "",
                                    onValueChange = { newVal ->
                                        localInputs = localInputs.toMutableMap().also { it[cls] = newVal }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    isError = needsValue,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal,
                                        imeAction = ImeAction.Next
                                    ),
                                    placeholder = { Text(stringResource(R.string.placeholder_height_short)) },
                                    suffix = { Text(stringResource(R.string.unit_m)) },
                                    supportingText = {
                                        if (count > 0) {
                                            val label = if (missingCount > 0) {
                                                "N=${count} · manquantes=${missingCount}"
                                            } else {
                                                "N=${count}"
                                            }
                                            val suffix = if (meanCount > 1 && meanVal != null) {
                                                " · moy=${String.format(Locale.getDefault(), "%.1f", meanVal)}"
                                            } else {
                                                ""
                                            }
                                            Text(label + suffix)
                                        }
                                    },
                                    singleLine = true
                                )
                            }
                        }

                    }
                }
            }
        }
    }

    // ── Dialogue sélection méthode de cubage ──
    if (showTarifMethodDialog) {
        TarifMethodDialog(
            currentMethod = currentTarifMethod,
            currentNumero = currentTarifNumero,
            onConfirm = { method, numero ->
                currentTarifMethod = method
                currentTarifNumero = numero
                coroutineScope.launch {
                    forestryCalculator.saveTarifSelection(
                        TarifSelection(
                            method = method.code,
                            schaefferNumero = if (method == TarifMethod.SCHAEFFER_1E || method == TarifMethod.SCHAEFFER_2E) numero else null,
                            ifnNumero = if (method == TarifMethod.IFN_RAPIDE || method == TarifMethod.IFN_LENT) numero else null
                        )
                    )
                    synthesisParamsVersion++
                    snackbar.showSnackbar(context.getString(R.string.martelage_cubage_method_saved))
                }
                showTarifMethodDialog = false
            },
            onDismiss = { showTarifMethodDialog = false }
        )
    }

    // ── Dialogue export QGIS ──
    if (showExportDialog) {
        ExportQgisDialog(
            tigesInScope = tigesInScope,
            scopeKey = scopeKey,
            onDismiss = { showExportDialog = false },
            onPlayClick = { playClickFeedback() },
            exportGeoJsonLauncher = exportGeoJsonLauncher,
            exportCsvXyLauncher = exportCsvXyLauncher,
            exportShapefileLauncher = exportShapefileLauncher,
            exportCsvMartelageLauncher = exportMartelageCsv,
            exportPdfLauncher = exportPdfLauncher,
            viewScopeName = viewScope.name
        )
    }
}

}

@Composable
private fun ToCompleteBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = stringResource(R.string.badge_to_complete),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}


@Composable
private fun SuperCorrelateurBannerCard(onClick: () -> Unit) {
    Card(
        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.Color(0xFF1B5E20).copy(alpha = 0.10f)
        ),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick
    ) {
        Row(
            modifier = androidx.compose.ui.Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = androidx.compose.ui.graphics.Color(0xFF2E7D32).copy(alpha = 0.15f),
                modifier = androidx.compose.ui.Modifier.size(48.dp)
            ) {
                Box(contentAlignment = androidx.compose.ui.Alignment.Center,
                    modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Default.Analytics,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color(0xFF2E7D32),
                        modifier = androidx.compose.ui.Modifier.size(28.dp)
                    )
                }
            }
            Column(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                Text(
                    "Super Corrélateur",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color(0xFF1B5E20)
                )
                Text(
                    "Corrélation multi-sources · Résilience 2050 · Plan d'action sylvicole",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.Analytics,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = androidx.compose.ui.Modifier.size(18.dp)
            )
        }
    }
}

// ── Formulaire terrain typologique rapide ─────────────────────────────────────
// Permet à un professionnel de saisir G + triangle (PB/BM/GB/TGB) directement
// sans tiges individuelles, et obtient instantanément le code CNPF.

@Composable
private fun TypeSliderRow(
    label: String,
    value: Float,
    onChg: (Float) -> Unit,
    color: Color
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            color = color.copy(alpha = 0.15f),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.width(52.dp)
        ) {
            Text(
                label,
                Modifier.padding(4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        Slider(
            value = value,
            onValueChange = onChg,
            valueRange = 0f..100f,
            steps = 99,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color)
        )
        Text(
            "${value.toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(36.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TypelogiqueRapideCard(prefilledGPerHa: Double? = null) {
    var gPerHa by remember { mutableStateOf(prefilledGPerHa?.toFloat() ?: 20f) }
    var pbPct  by remember { mutableFloatStateOf(40f) }
    var bmPct  by remember { mutableFloatStateOf(40f) }
    var gbPct  by remember { mutableFloatStateOf(15f) }
    var tgbPct by remember { mutableFloatStateOf(5f) }

    val total = pbPct + bmPct + gbPct + tgbPct
    val ratio = if (total > 0) com.forestry.counter.domain.classification.stand.DiameterCategoryRatio(
        pbPct  = (pbPct  / total * 100).toDouble(),
        bmPct  = (bmPct  / total * 100).toDouble(),
        gbPct  = (gbPct  / total * 100).toDouble(),
        tgbPct = (tgbPct / total * 100).toDouble()
    ) else null

    val db = com.forestry.counter.domain.classification.stand.StandTypologyDatabase
    val capital   = db.computeClassCapital(gPerHa.toDouble())
    val structure = ratio?.cnpfStructureCode() ?: 0
    val cnpfCode  = if (ratio != null) "F$capital$structure" else "—"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text("Saisie terrain rapide", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("G + triangle sans tiges individuelles", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Badge code CNPF en temps réel
                Surface(color = MaterialTheme.colorScheme.secondary, shape = RoundedCornerShape(8.dp)) {
                    Text(cnpfCode,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSecondary)
                }
            }

            HorizontalDivider()

            // Surface terrière
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Surface terrière G", style = MaterialTheme.typography.labelMedium)
                    Text("%.1f m²/ha  →  classe $capital".format(gPerHa),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Slider(value = gPerHa, onValueChange = { gPerHa = it }, valueRange = 0f..60f, steps = 119,
                    modifier = Modifier.fillMaxWidth())
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Triangle des structures
            Text("Triangle des structures (% sur tiges précomptables ≥ 17.5 cm)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            TypeSliderRow("PB",  pbPct,  { pbPct  = it }, Color(0xFF90CAF9))
            TypeSliderRow("BM",  bmPct,  { bmPct  = it }, Color(0xFF1565C0))
            TypeSliderRow("GB",  gbPct,  { gbPct  = it }, Color(0xFF0D47A1))
            TypeSliderRow("TGB", tgbPct, { tgbPct = it }, Color(0xFF002171))

            // Résumé structure et normalisation avertissement
            if (total > 0 && ratio != null) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Code CNPF : $cnpfCode  ·  Structure $structure  ·  GB+TGB = ${ratio.gbTgbPct.toInt()}%",
                            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("Les % sont normalisés automatiquement à 100%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}

// Data classes et enums déplacés dans MartelageModels.kt

// Fonctions de formatage déplacées dans MartelageFormatters.kt

