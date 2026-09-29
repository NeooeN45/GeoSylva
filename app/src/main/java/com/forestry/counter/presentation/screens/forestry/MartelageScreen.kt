package com.forestry.counter.presentation.screens.forestry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forestry.counter.R
import com.forestry.counter.data.preferences.UserPreferencesManager
import com.forestry.counter.domain.calculation.*
import com.forestry.counter.domain.calculation.pricing.FrenchRegion
import com.forestry.counter.domain.calculation.tarifs.TarifMethod
import com.forestry.counter.domain.calculation.tarifs.TarifSelection
import com.forestry.counter.domain.repository.EssenceRepository
import com.forestry.counter.domain.repository.IbpRepository
import com.forestry.counter.domain.repository.ParcelleRepository
import com.forestry.counter.domain.repository.TigeRepository
import com.forestry.counter.domain.usecase.fertility.FertilityClassifier
import com.forestry.counter.domain.usecase.fertility.FertilityResult
import com.forestry.counter.domain.model.ClimateZone
import com.forestry.counter.presentation.utils.rememberHapticFeedback
import com.forestry.counter.presentation.utils.rememberSoundFeedback
import kotlinx.coroutines.flow.flowOf
import java.util.Locale

/**
 * Orchestrateur du module Martelage.
 *
 * Responsabilité unique : calculer l'état (stats, paramètres, portée) et
 * router vers le bon sous-écran via [MartelageSubScreen]. Aucune UI inline.
 */
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
    onNavigateToStandClassification: ((parcelleId: String) -> Unit)? = null,
    onNavigateBack: () -> Unit,
) {
    // ═══════════════════════════════════════════════════════════════════════════
    // ViewModel & préférences utilisateur
    // ═══════════════════════════════════════════════════════════════════════════

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
            ibpRepository = ibpRepository,
        )
    }

    val hapticEnabled by userPreferences.hapticEnabled.collectAsStateWithLifecycle(true)
    val soundEnabled by userPreferences.soundEnabled.collectAsStateWithLifecycle(true)
    val hapticIntensity by userPreferences.hapticIntensity.collectAsStateWithLifecycle(2)
    val backgroundImageEnabled by userPreferences.backgroundImageEnabled.collectAsStateWithLifecycle(true)
    val backgroundImageUri by userPreferences.backgroundImageUri.collectAsStateWithLifecycle(null)
    val animationsEnabled by userPreferences.animationsEnabled.collectAsStateWithLifecycle(true)
    val haptic = rememberHapticFeedback()
    val sound = rememberSoundFeedback()
    fun playClick() {
        if (hapticEnabled) haptic.performWithIntensity(hapticIntensity)
        if (soundEnabled) sound.click()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Données des repositories
    // ═══════════════════════════════════════════════════════════════════════════

    val parcelles by viewModel.parcelles.collectAsStateWithLifecycle()
    val essences by essenceRepository.getAllEssences().collectAsStateWithLifecycle(emptyList())
    val allTiges by viewModel.allTiges.collectAsStateWithLifecycle()

    // ═══════════════════════════════════════════════════════════════════════════
    // Clé de portée & IBP
    // ═══════════════════════════════════════════════════════════════════════════

    val scopeKey = remember(scope, forestId, parcelleId, placetteId) {
        when {
            placetteId != null -> "PLACETTE_$placetteId"
            parcelleId != null -> "PARCELLE_$parcelleId"
            forestId != null   -> "FOREST_$forestId"
            else               -> "GLOBAL"
        }
    }

    val ibpEvaluations by remember(ibpRepository, placetteId, parcelleId) {
        when {
            ibpRepository != null && placetteId != null -> ibpRepository.getByPlacette(placetteId)
            ibpRepository != null && parcelleId != null -> ibpRepository.getByParcelle(parcelleId)
            else -> flowOf(emptyList())
        }
    }.collectAsStateWithLifecycle(emptyList())
    val latestIbp = remember(ibpEvaluations) { ibpEvaluations.firstOrNull() }

    // ═══════════════════════════════════════════════════════════════════════════
    // Paramètres de calcul persistés
    // ═══════════════════════════════════════════════════════════════════════════

    val savedSurface  by userPreferences.martelageSurfaceFlow(scopeKey).collectAsStateWithLifecycle(null)
    val savedHo       by userPreferences.martelageHoFlow(scopeKey).collectAsStateWithLifecycle(null)
    val savedNhaAvant by userPreferences.martelageNhaAvantFlow(scopeKey).collectAsStateWithLifecycle(null)
    val savedGhaAvant by userPreferences.martelageGhaAvantFlow(scopeKey).collectAsStateWithLifecycle(null)

    // ═══════════════════════════════════════════════════════════════════════════
    // Hauteurs par essence — merge (global → forêt → local)
    // ═══════════════════════════════════════════════════════════════════════════

    val martelageHeightsLocal by userPreferences.martelageHeightsFlow(scopeKey).collectAsStateWithLifecycle(emptyMap())
    val forestIdForHeights = remember(forestId, parcelleId, parcelles) {
        forestId ?: parcelles.firstOrNull { it.id == parcelleId }?.forestId
    }
    val forestScopeKeyForHeights = remember(forestIdForHeights) {
        forestIdForHeights?.let { "FOREST_$it" }
    }
    val martelageHeightsForest by userPreferences
        .martelageHeightsFlow(forestScopeKeyForHeights ?: scopeKey)
        .collectAsStateWithLifecycle(emptyMap())
    val martelageHeightsGlobal by userPreferences
        .martelageHeightsFlow("GLOBAL")
        .collectAsStateWithLifecycle(emptyMap())

    val martelageHeights = remember(
        martelageHeightsLocal, martelageHeightsForest, martelageHeightsGlobal,
        scopeKey, forestScopeKeyForHeights,
    ) {
        fun normalize(src: Map<String, Map<Int, Double>>): Map<String, Map<Int, Double>> {
            val out = mutableMapOf<String, MutableMap<Int, Double>>()
            src.forEach { (k, v) ->
                val nk = k.trim().uppercase(Locale.getDefault())
                v.forEach { (cls, h) -> out.getOrPut(nk) { mutableMapOf() }[cls] = h }
            }
            return out.mapValues { it.value.toMap() }
        }
        fun merge(base: Map<String, Map<Int, Double>>, over: Map<String, Map<Int, Double>>): Map<String, Map<Int, Double>> {
            val out = base.mapValues { it.value.toMutableMap() }.toMutableMap()
            over.forEach { (ess, map) -> out.getOrPut(ess) { mutableMapOf() }.putAll(map) }
            return out.mapValues { it.value.toMap() }
        }
        val gN = normalize(martelageHeightsGlobal)
        val fN = if (forestScopeKeyForHeights != null && forestScopeKeyForHeights != scopeKey)
            normalize(martelageHeightsForest) else emptyMap()
        merge(merge(gN, fN), normalize(martelageHeightsLocal))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // État UI mutable
    // ═══════════════════════════════════════════════════════════════════════════

    var surfaceInput       by remember { mutableStateOf("") }
    var hoInput            by remember { mutableStateOf("") }
    var nHaAvantInput      by remember { mutableStateOf("") }
    var gHaAvantInput      by remember { mutableStateOf("") }

    var showParamDialog             by remember { mutableStateOf(false) }
    var askedParamsOnce             by remember { mutableStateOf(false) }
    var showParamPanel              by remember { mutableStateOf(false) }
    var editingHeightsEssenceCode   by remember { mutableStateOf<String?>(null) }
    var persistParamsRequested      by remember { mutableStateOf(false) }
    var showHeightPromptDialog      by remember { mutableStateOf(false) }
    var showHeightSnoozeDialog      by remember { mutableStateOf(false) }
    var heightSnoozeHours           by rememberSaveable { mutableStateOf(1) }
    var showTarifMethodDialog       by remember { mutableStateOf(false) }
    var showExportDialog            by remember { mutableStateOf(false) }

    val heightPromptSnoozeUntilMs by userPreferences.heightPromptSnoozeUntilMs
        .collectAsStateWithLifecycle(0L)
    val isHeightPromptSnoozed = heightPromptSnoozeUntilMs > System.currentTimeMillis()

    var currentTarifMethod  by remember { mutableStateOf(TarifMethod.ALGAN) }
    var currentTarifNumero  by remember { mutableStateOf<Int?>(null) }
    var synthesisParamsVersion by remember { mutableStateOf(0) }
    var currentSubScreen    by rememberSaveable { mutableStateOf(MartelageSubScreen.Hub) }
    var breakdownByEssence  by remember { mutableStateOf<Map<String, List<ProductBreakdownRow>>>(emptyMap()) }

    // ═══════════════════════════════════════════════════════════════════════════
    // Portée (Forêt / Parcelle / Placette / Global)
    // ═══════════════════════════════════════════════════════════════════════════

    val parcellesInScope = remember(parcelles, scope, forestId, parcelleId) {
        when (scope.uppercase(Locale.getDefault())) {
            "FOREST"               -> parcelles.filter { it.forestId == forestId }
            "PARCELLE", "PLACETTE" -> parcelles.filter { it.id == parcelleId }
            else                   -> parcelles
        }
    }
    val parcelleIdsInScope = remember(parcellesInScope) { parcellesInScope.map { it.id }.toSet() }

    val initialViewScope = remember(scope, placetteId, parcelleId) {
        when (scope.uppercase(Locale.getDefault())) {
            "PLACETTE" -> MartelageViewScope.PLACETTE
            "PARCELLE" -> MartelageViewScope.PARCELLE
            else       -> MartelageViewScope.GLOBAL
        }
    }
    var viewScope by remember { mutableStateOf(initialViewScope) }

    val tigesInScope = remember(allTiges, viewScope, scope, parcelleIdsInScope, parcelleId, placetteId) {
        when (viewScope) {
            MartelageViewScope.PLACETTE ->
                placetteId?.let { id -> allTiges.filter { it.placetteId == id } } ?: emptyList()
            MartelageViewScope.PARCELLE ->
                parcelleId?.let { id -> allTiges.filter { it.parcelleId == id } } ?: emptyList()
            MartelageViewScope.GLOBAL -> when (scope.uppercase(Locale.getDefault())) {
                "FOREST"   -> allTiges.filter { it.parcelleId in parcelleIdsInScope }
                "PARCELLE" -> parcelleId?.let { id -> allTiges.filter { it.parcelleId == id } } ?: allTiges
                "PLACETTE" -> placetteId?.let { id -> allTiges.filter { it.placetteId == id } } ?: allTiges
                else       -> allTiges
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Essences disponibles dans la portée
    // ═══════════════════════════════════════════════════════════════════════════

    fun normalizeCode(code: String) = code.trim().uppercase(Locale.getDefault())

    val availableEssences = remember(essences, tigesInScope) {
        val codes = tigesInScope.map { normalizeCode(it.essenceCode) }.toSet()
        essences.filter { normalizeCode(it.code) in codes }
    }
    var selectedEssenceCodes by remember(availableEssences) {
        mutableStateOf(availableEssences.map { normalizeCode(it.code) }.toSet())
    }

    val martelageEssenceMap = remember(essences) { essences.associateBy { normalizeCode(it.code) } }
    val martelageClimateZone = remember(tigesInScope) {
        val wkt = tigesInScope.mapNotNull { it.gpsWkt }.firstOrNull()
        if (wkt != null) ClimateZone.detectFromWkt(wkt, null) else ClimateZone.UNKNOWN
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Pré-remplissage des saisies depuis les valeurs persistées
    // ═══════════════════════════════════════════════════════════════════════════

    LaunchedEffect(savedSurface)  { if (savedSurface != null  && surfaceInput.isBlank())  surfaceInput  = "%.0f".format(savedSurface) }
    LaunchedEffect(savedHo)       { if (savedHo != null       && hoInput.isBlank())       hoInput       = "%.1f".format(savedHo) }
    LaunchedEffect(savedNhaAvant) { if (savedNhaAvant != null && nHaAvantInput.isBlank()) nHaAvantInput = "%.0f".format(savedNhaAvant) }
    LaunchedEffect(savedGhaAvant) { if (savedGhaAvant != null && gHaAvantInput.isBlank()) gHaAvantInput = "%.1f".format(savedGhaAvant) }

    // ═══════════════════════════════════════════════════════════════════════════
    // Parsing & surface effective
    // ═══════════════════════════════════════════════════════════════════════════

    fun parseSurfaceM2(raw: String): Double? {
        val s = raw.trim()
        if (s.isBlank()) return null
        val c = s.lowercase(Locale.getDefault()).replace(" ", "")
        return when {
            c.endsWith("ha") -> c.removeSuffix("ha").replace(',', '.').toDoubleOrNull()?.times(10_000.0)
            c.endsWith("m2") -> c.removeSuffix("m2").replace(',', '.').toDoubleOrNull()
            c.endsWith("m²") -> c.removeSuffix("m²").replace(',', '.').toDoubleOrNull()
            else             -> c.replace(',', '.').toDoubleOrNull()
        }
    }

    val surfaceInputValueM2 = parseSurfaceM2(surfaceInput)
    val surfaceM2 = when {
        surfaceInput.isBlank()      -> savedSurface
        surfaceInputValueM2 != null -> surfaceInputValueM2
        else                        -> null
    }
    val hoInputValue = hoInput.replace(',', '.').toDoubleOrNull()
    val hoM = hoInputValue ?: savedHo
    val missingParams = surfaceM2 == null || surfaceM2 <= 0.0

    val nHaAvantValue = nHaAvantInput.replace(',', '.').toDoubleOrNull()
    val nHaAvant = nHaAvantValue ?: savedNhaAvant
    val gHaAvantValue = gHaAvantInput.replace(',', '.').toDoubleOrNull()
    val gHaAvant = gHaAvantValue ?: savedGhaAvant

    // ═══════════════════════════════════════════════════════════════════════════
    // Auto-persistance (debounce 800 ms + validation explicite)
    // ═══════════════════════════════════════════════════════════════════════════

    LaunchedEffect(persistParamsRequested) {
        if (!persistParamsRequested) return@LaunchedEffect
        if (surfaceInputValueM2 != null && surfaceInputValueM2 > 0.0) userPreferences.setMartelageSurface(scopeKey, surfaceInputValueM2)
        if (hoInputValue != null && hoInputValue > 0.0) userPreferences.setMartelageHo(scopeKey, hoInputValue)
        persistParamsRequested = false
    }
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

    // ═══════════════════════════════════════════════════════════════════════════
    // Méthode de cubage — chargement initial
    // ═══════════════════════════════════════════════════════════════════════════

    LaunchedEffect(Unit) {
        val sel = forestryCalculator.loadTarifSelection()
        currentTarifMethod = TarifMethod.fromCode(sel?.method ?: "") ?: TarifMethod.ALGAN
        currentTarifNumero = sel?.schaefferNumero ?: sel?.ifnNumero
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Paramètres de synthèse & classes de diamètre
    // ═══════════════════════════════════════════════════════════════════════════

    val synthesisParams by produceState<ForestrySynthesisParams?>(null, forestryCalculator, synthesisParamsVersion) {
        value = runCatching { forestryCalculator.loadSynthesisParams() }.getOrNull()
    }
    val diameterClasses by produceState<List<Int>>(emptyList(), forestryCalculator, synthesisParamsVersion) {
        value = runCatching { forestryCalculator.diameterClasses() }.getOrElse { emptyList() }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Calcul des stats martelage
    // ═══════════════════════════════════════════════════════════════════════════

    val stats by produceState<MartelageStats?>(
        initialValue = null,
        tigesInScope, surfaceM2, viewScope, selectedEssenceCodes,
        martelageHeights, hoM, synthesisParams, diameterClasses, nHaAvant, gHaAvant,
    ) {
        value = if (surfaceM2 != null && surfaceM2 > 0.0) {
            val region = parcellesInScope
                .firstNotNullOfOrNull { it.codeInseeCommune }
                ?.let { FrenchRegion.fromCodeCommune(it) }
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
                gHaAvant = gHaAvant,
                region = region,
            )
        } else null
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Ventilation produits par essence
    // ═══════════════════════════════════════════════════════════════════════════

    LaunchedEffect(stats?.perEssence) {
        val s = stats ?: return@LaunchedEffect
        if (missingParams) return@LaunchedEffect
        val prices = forestryCalculator.loadPriceEntries()
        val region = parcellesInScope
            .firstNotNullOfOrNull { it.codeInseeCommune }
            ?.let { FrenchRegion.fromCodeCommune(it) }
        val result = mutableMapOf<String, List<ProductBreakdownRow>>()
        s.perEssence.forEach { ess ->
            if (ess.vTotal <= 0.0) return@forEach
            val diam = (ess.dm ?: ess.dg ?: 30.0).toInt()
            val v = ess.vTotal
            val products = when {
                diam >= 60 -> mapOf("BO" to v * 0.85, "BI" to v * 0.10, "BCh" to v * 0.05)
                diam >= 45 -> mapOf("BO" to v * 0.75, "BI" to v * 0.15, "BCh" to v * 0.10)
                diam >= 35 -> mapOf("BO" to v * 0.55, "BI" to v * 0.30, "BCh" to v * 0.15)
                diam >= 25 -> mapOf("BO" to v * 0.30, "BI" to v * 0.45, "BCh" to v * 0.25)
                diam >= 15 -> mapOf("BI" to v * 0.40, "BCh" to v * 0.35, "PATE" to v * 0.25)
                else       -> mapOf("BCh" to v * 0.50, "PATE" to v * 0.50)
            }
            val candidates = EssenceAliases.candidates(ess.essenceCode)
            val base = PriceCalculator.buildBreakdownWithReport(
                prices = prices, essenceCode = ess.essenceCode,
                volumeByProduct = products, diamCm = diam,
                quality = ess.dominantQuality?.code, region = region,
                essenceCandidates = candidates,
            )
            val synRev = ess.revenueTotal
            val baseTotal = base.sumOf { it.totalEur }
            result[ess.essenceCode] = if (synRev != null && synRev > 0.0 && baseTotal > 0.0)
                base.map { it.copy(totalEur = it.totalEur * (synRev / baseTotal)) }
            else base
        }
        breakdownByEssence = result
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Hauteurs manquantes & déclenchement automatique du panneau paramètres
    // ═══════════════════════════════════════════════════════════════════════════

    val promptClasses = remember(diameterClasses) {
        if (diameterClasses.isNotEmpty()) diameterClasses else (5..120 step 5).toList()
    }
    val missingHeightEssenceCodesForPrompt = remember(tigesInScope, martelageHeights, promptClasses, synthesisParams) {
        val heightModes = synthesisParams?.heightModes.orEmpty()
        val byEssence = tigesInScope.groupBy { normalizeCode(it.essenceCode) }
        byEssence.keys.filter { code ->
            val tigesEss = byEssence[code].orEmpty()
            val byClass = tigesEss.groupBy { forestryCalculator.diameterClassFor(it.diamCm, promptClasses) }
            val manual = martelageHeights[code].orEmpty()
            byClass.entries.any { (diamClass, list) ->
                if (list.none { it.hauteurM == null }) return@any false
                val mode = heightModes.firstOrNull { it.essence.equals(code, true) && it.diamClass == diamClass }
                val canResolve = manual[diamClass] != null ||
                    (mode?.mode?.equals("FIXED", true) == true && (mode.fixed ?: 0.0) > 0.0) ||
                    (mode?.mode?.equals("SAMPLES", true) == true && list.any { it.hauteurM != null })
                !canResolve
            }
        }.sorted()
    }
    val missingHeightsForPrompt = missingHeightEssenceCodesForPrompt.isNotEmpty()
    val shouldPromptHeightsNow = missingHeightsForPrompt && !isHeightPromptSnoozed

    LaunchedEffect(missingParams, shouldPromptHeightsNow, tigesInScope.size) {
        if (!askedParamsOnce && tigesInScope.isNotEmpty() && (missingParams || shouldPromptHeightsNow)) {
            askedParamsOnce = true
            showParamPanel = true
            if (missingParams) showParamDialog = true
            if (shouldPromptHeightsNow && editingHeightsEssenceCode == null) showHeightPromptDialog = true
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Valeurs d'affichage partagées entre Hub et sous-écrans
    // ═══════════════════════════════════════════════════════════════════════════

    val scopeLabel = when (scope.uppercase(Locale.getDefault())) {
        "PLACETTE" -> stringResource(R.string.martelage_scope_placette_desc)
        "PARCELLE" -> stringResource(R.string.martelage_scope_parcelle_desc)
        "FOREST"   -> stringResource(R.string.martelage_scope_forest_desc)
        else       -> stringResource(R.string.martelage_scope_global_desc)
    }
    val placeholderDash = stringResource(R.string.placeholder_dash)
    val ellipsis        = stringResource(R.string.ellipsis)
    val euroSymbol      = stringResource(R.string.euro_symbol)
    val surfaceHaForSubs = (surfaceM2 ?: 0.0) / 10_000.0
    val parcelleInScope = remember(parcellesInScope, parcelleId) {
        parcellesInScope.firstOrNull { it.id == parcelleId }
    }
    val gsieSyncState = remember(stats, tigesInScope, missingParams) {
        val s = stats
        if (s != null) resolveGsieSyncState(s, tigesInScope.isEmpty(), missingParams)
        else GsieSyncState.SYNC_PENDING
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Routing
    // ═══════════════════════════════════════════════════════════════════════════

    BackHandler(enabled = currentSubScreen != MartelageSubScreen.Hub) {
        currentSubScreen = MartelageSubScreen.Hub
    }

    when (currentSubScreen) {
        MartelageSubScreen.Hub -> MartelageHubScreen(
            // ── état affiché ──
            stats = stats,
            scopeLabel = scopeLabel,
            parcellesInScope = parcellesInScope,
            parcelle = parcelleInScope,
            surfaceHa = surfaceHaForSubs,
            gsieSyncState = gsieSyncState,
            tigesInScope = tigesInScope,
            essences = essences,
            availableEssences = availableEssences,
            selectedEssenceCodes = selectedEssenceCodes,
            viewScope = viewScope,
            parcelleId = parcelleId,
            placetteId = placetteId,
            scope = scope,
            missingParams = missingParams,
            missingHeightEssenceCodesForPrompt = missingHeightEssenceCodesForPrompt,
            missingHeightsForPrompt = missingHeightsForPrompt,
            // ── saisies paramètres ──
            surfaceInput = surfaceInput,
            surfaceInputValueM2 = surfaceInputValueM2,
            hoInput = hoInput,
            nHaAvantInput = nHaAvantInput,
            gHaAvantInput = gHaAvantInput,
            // ── état dialogues / panneaux ──
            showParamPanel = showParamPanel,
            showParamDialog = showParamDialog,
            showHeightPromptDialog = showHeightPromptDialog,
            showHeightSnoozeDialog = showHeightSnoozeDialog,
            heightSnoozeHours = heightSnoozeHours,
            showTarifMethodDialog = showTarifMethodDialog,
            showExportDialog = showExportDialog,
            editingHeightsEssenceCode = editingHeightsEssenceCode,
            // ── méthode de cubage ──
            currentTarifMethod = currentTarifMethod,
            currentTarifNumero = currentTarifNumero,
            // ── hauteurs & synth ──
            martelageHeights = martelageHeights,
            martelageHeightsLocal = martelageHeightsLocal,
            martelageHeightsForest = martelageHeightsForest,
            forestScopeKeyForHeights = forestScopeKeyForHeights,
            synthesisParams = synthesisParams,
            diameterClasses = diameterClasses,
            breakdownByEssence = breakdownByEssence,
            // ── préférences ──
            animationsEnabled = animationsEnabled,
            backgroundImageEnabled = backgroundImageEnabled,
            backgroundImageUri = backgroundImageUri,
            // ── callbacks ──
            onNavigateTo = { currentSubScreen = it },
            onNavigateBack = onNavigateBack,
            onNavigateToMap = onNavigateToMap,
            onSurfaceInputChange = { surfaceInput = it },
            onHoInputChange = { hoInput = it },
            onNHaAvantInputChange = { nHaAvantInput = it },
            onGHaAvantInputChange = { gHaAvantInput = it },
            onSelectedEssenceCodesChange = { selectedEssenceCodes = it },
            onViewScopeChange = { viewScope = it },
            onShowParamPanelChange = { showParamPanel = it },
            onShowParamDialogChange = { showParamDialog = it },
            onShowHeightPromptDialogChange = { showHeightPromptDialog = it },
            onShowHeightSnoozeDialogChange = { showHeightSnoozeDialog = it },
            onHeightSnoozeHoursChange = { heightSnoozeHours = it },
            onShowTarifMethodDialogChange = { showTarifMethodDialog = it },
            onShowExportDialogChange = { showExportDialog = it },
            onEditingHeightsEssenceCodeChange = { editingHeightsEssenceCode = it },
            onPersistParams = { persistParamsRequested = true },
            onTarifChanged = { method, numero ->
                currentTarifMethod = method
                currentTarifNumero = numero
                synthesisParamsVersion++
            },
            onPlayClick = ::playClick,
            userPreferences = userPreferences,
            forestryCalculator = forestryCalculator,
            scopeKey = scopeKey,
            ellipsis = ellipsis,
            placeholderDash = placeholderDash,
            euroSymbol = euroSymbol,
        )

        MartelageSubScreen.SyntheseDetaillee -> {
            val s = stats
            if (s != null) MartelageSyntheseDetailleeScreen(
                stats = s,
                scopeLabel = scopeLabel,
                parcelle = parcelleInScope,
                surfaceHa = surfaceHaForSubs,
                tarifMethod = currentTarifMethod,
                gsieSyncState = gsieSyncState,
                latestIbp = latestIbp,
                onOpenParams = { currentSubScreen = MartelageSubScreen.Hub; showParamPanel = true },
                onNavigateToCalcul = { currentSubScreen = MartelageSubScreen.CalculFin },
                onNavigateBack = { currentSubScreen = MartelageSubScreen.Hub },
            )
        }

        MartelageSubScreen.CalculFin -> {
            val s = stats
            if (s != null) MartelageCalculFinScreen(
                stats = s,
                essences = essences,
                breakdownByEssence = breakdownByEssence,
                vTotalText = formatVolume(s.vTotal),
                vPerHaText = formatVolume(s.vPerHa),
                revenueTotalText = formatMoney(s.revenueTotal, placeholderDash, euroSymbol),
                revenuePerHaText = formatMoney(s.revenuePerHa, placeholderDash, euroSymbol),
                placeholderDash = placeholderDash,
                missingPriceVol = s.unpricedVolumeTotal,
                missingPriceVolText = formatVolume(s.unpricedVolumeTotal),
                tarifMethod = currentTarifMethod,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToPriceTablesEditor = onNavigateToPriceTablesEditor,
                onNavigateBack = { currentSubScreen = MartelageSubScreen.Hub },
            )
        }

        MartelageSubScreen.Schemas -> {
            val s = stats
            if (s != null) MartelageSchemasScreen(
                stats = s,
                onNavigateBack = { currentSubScreen = MartelageSubScreen.Hub },
            )
        }

        MartelageSubScreen.Carte -> MartelageCarteScreen(
            tiges = tigesInScope,
            parcelle = parcelleInScope,
            onNavigateBack = { currentSubScreen = MartelageSubScreen.Hub },
        )
    }
}
