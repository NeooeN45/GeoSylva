package com.forestry.counter.presentation.screens.forestry

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as columnItems
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
import com.forestry.counter.R
import com.forestry.counter.presentation.components.AppMiniDialog
import com.forestry.counter.presentation.components.HeightClassInputField
import com.forestry.counter.presentation.components.TreeHeightMeasureDialog
import com.forestry.counter.presentation.components.WoodQualityDialog
import com.forestry.counter.presentation.components.TipCard
import com.forestry.counter.domain.calculation.ForestryCalculator
import com.forestry.counter.domain.calculation.HeightModeEntry
import com.forestry.counter.domain.calculation.tarifs.TarifMethod
import com.forestry.counter.domain.calculation.tarifs.TarifCalculator
import com.forestry.counter.domain.repository.TigeRepository
import com.forestry.counter.domain.repository.InventaireSessionRepository
import com.forestry.counter.domain.model.MartelageStates
import com.forestry.counter.domain.model.InventaireSession
import com.forestry.counter.data.preferences.UserPreferencesManager
import com.forestry.counter.data.preferences.GpsCaptureMode
import com.forestry.counter.domain.repository.EssenceRepository
import com.forestry.counter.domain.calculation.quality.WoodQualityGrade
import com.forestry.counter.presentation.utils.parseHeightInputMean
import com.forestry.counter.presentation.utils.hasInvalidHeightInput
import com.forestry.counter.presentation.utils.rememberHapticFeedback
import com.forestry.counter.presentation.utils.rememberSoundFeedback
import com.forestry.counter.domain.location.GpsAverager
import com.forestry.counter.domain.location.GpsQuality
import androidx.compose.material.icons.filled.Star
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EssenceDiamScreen(
    parcelleId: String,
    placetteId: String,
    essenceCode: String,
    tigeRepository: TigeRepository,
    calculator: ForestryCalculator?,
    userPreferences: UserPreferencesManager,
    essenceRepository: EssenceRepository? = null,
    sessionRepository: InventaireSessionRepository? = null,
    identityRepository: com.forestry.counter.domain.repository.IdentityRepository? = null,
    observationQualificationRepository: com.forestry.counter.domain.repository.ObservationQualificationRepository? = null,
    businessReadAccess: com.forestry.counter.data.sync.hierarchy.BusinessReadAccess? = null,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val appContext = LocalContext.current
    var sessionReadError by remember(sessionRepository, parcelleId, placetteId) { mutableStateOf(false) }
    var reconciliationError by remember(sessionRepository, parcelleId, placetteId) { mutableStateOf(false) }
    var reconcilingSession by remember(sessionRepository, parcelleId, placetteId) { mutableStateOf(sessionRepository != null) }
    val sessionSource = remember(sessionRepository, parcelleId, placetteId) { sessionRepository?.getByPlacette(placetteId) }
    val sessionsFlow = remember(sessionSource) {
        val source: Flow<List<InventaireSession>?> = sessionSource
            ?.map { it } ?: flowOf(emptyList())
        source.catch { error ->
            if (error is CancellationException) throw error
            sessionReadError = true
            emit(null)
        }
    }
    val sessions by sessionsFlow.collectAsStateWithLifecycle(initialValue = null)
    val sessionsReady = sessionRepository == null || sessions != null
    val selectedSession = remember(sessions, parcelleId, placetteId) {
        selectEssenceSession(sessions.orEmpty(), parcelleId, if (sessionRepository != null) placetteId else null)
    }
    val sessionUnavailable = sessionReadError || reconciliationError
    val canWrite = sessionsReady && !sessionUnavailable && !reconcilingSession && selectedSession.writable
    val currentCanWrite by rememberUpdatedState(canWrite)
    val currentSelection by rememberUpdatedState(selectedSession)

    suspend fun writeObservation(block: suspend (String?) -> Unit): Boolean {
        // Keep the campaign displayed when this callback was created, even if a new one appears meanwhile.
        val expected = selectedSession
        return try {
            check(currentCanWrite && currentSelection.managed == expected.managed &&
                currentSelection.session?.sessionId == expected.session?.sessionId) { "MARTELAGE_ENTRY_STATE_CHANGED" }
            sessionRepository?.let { repository ->
                repository.reconcileForPlacette(parcelleId, placetteId)
                val fresh = selectEssenceSession(checkNotNull(sessionSource).first(), parcelleId, placetteId)
                check(fresh.writable && fresh.managed == expected.managed &&
                    fresh.session?.sessionId == expected.session?.sessionId) { "MARTELAGE_ENTRY_STATE_CHANGED" }
            }
            block(expected.session?.sessionId)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val stateChanged = error is IllegalStateException ||
                error.message?.startsWith("MARTELAGE_") == true || error.message?.startsWith("TIGE_") == true
            snackbar.showSnackbar(appContext.getString(
                if (stateChanged) R.string.essence_session_changed else R.string.essence_session_write_failed
            ))
            false
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, sessionRepository, parcelleId, placetteId) {
        var reconciliationJob: Job? = null
        fun reconcile() {
            val repository = sessionRepository ?: return
            if (reconciliationJob?.isActive == true) return
            reconciliationJob = scope.launch {
                reconcilingSession = true
                try {
                    repository.reconcileForPlacette(parcelleId, placetteId)
                    reconciliationError = false
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    reconciliationError = true
                    snackbar.showSnackbar(appContext.getString(R.string.essence_session_unavailable))
                } finally {
                    reconcilingSession = false
                }
            }
        }
        reconcile()
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) reconcile() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            reconciliationJob?.cancel()
        }
    }

    val hapticEnabled by userPreferences.hapticEnabled.collectAsStateWithLifecycle(initialValue = true)
    val soundEnabled by userPreferences.soundEnabled.collectAsStateWithLifecycle(initialValue = true)
    val hapticIntensity by userPreferences.hapticIntensity.collectAsStateWithLifecycle(initialValue = 2)
    val animationsEnabled by userPreferences.animationsEnabled.collectAsStateWithLifecycle(initialValue = true)
    val haptic = rememberHapticFeedback()
    val sound = rememberSoundFeedback()

    fun playClickFeedback() {
        if (hapticEnabled) {
            haptic.performWithIntensity(hapticIntensity)
        }
        if (soundEnabled) {
            sound.click()
        }
    }

    // Profil GPS unique : équilibre rapidité + précision (6 lectures, max 20m, timeout 15s)
    fun captureGpsForTige(tigeId: String) {
        val appCtx = appContext
        // Utiliser un scope approprié lié au cycle de vie pour éviter les memory leaks
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            val hasPermission = ContextCompat.checkSelfPermission(appCtx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(appCtx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) return@launch
            try {
                val averaged = GpsAverager.collectAndAverage(
                    context = appCtx,
                    targetReadings = 6,
                    maxAccuracyM = 20f,
                    timeoutMs = 15_000L
                ) ?: return@launch
                val saved = tigeRepository.setTigeGps(
                    tigeId = tigeId,
                    wkt = averaged.wkt,
                    precisionM = averaged.precisionM,
                    altitudeM = averaged.altitude
                )
                if (!saved) snackbar.showSnackbar(appContext.getString(R.string.essence_session_changed))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                snackbar.showSnackbar(appContext.getString(R.string.gps_periodic_unavailable))
            }
        }
    }

    // ── Surveillance GPS périodique (toutes les 10 min) ──
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return@LaunchedEffect

        while (true) {
            kotlinx.coroutines.delay(10 * 60 * 1000L) // 10 minutes
            try {
                val quick = GpsAverager.collectAndAverage(
                    context = appContext,
                    targetReadings = 2,
                    maxAccuracyM = 100f,
                    timeoutMs = 5_000L
                )
                if (quick == null) {
                    snackbar.showSnackbar(
                        appContext.getString(R.string.gps_periodic_unavailable),
                        duration = SnackbarDuration.Long
                    )
                } else if (quick.precisionM > 20.0) {
                    snackbar.showSnackbar(
                        appContext.getString(R.string.gps_periodic_poor, quick.precisionM, 20f),
                        duration = SnackbarDuration.Long
                    )
                }
            } catch (_: Throwable) { /* permission lost or other error — silently skip */ }
        }
    }

    val normalizedEssenceCode = remember(essenceCode) { essenceCode.trim().uppercase(Locale.getDefault()) }

    val specialCategories = remember {
        setOf("DEPERISSANT", "ARBRE_BIO", "MORT", "PARASITE")
    }

    // Charger les tiges de la placette
    val tigesFlow = remember(tigeRepository, placetteId) { tigeRepository.getTigesByPlacette(placetteId) }
    val tiges by tigesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    // Tiges "normales" uniquement : les arbres spéciaux doivent rester dans l'onglet "feuille"
    val campaignTiges = remember(tiges, selectedSession, sessionsReady, sessionReadError) {
        if (!sessionsReady || sessionReadError) emptyList() else selectedSession.selectTiges(tiges)
    }
    val tigesEssence = remember(campaignTiges, essenceCode) {
        campaignTiges.filter { t ->
            t.essenceCode.equals(essenceCode, true) &&
                (t.categorie?.uppercase(Locale.getDefault()) !in specialCategories)
        }
    }

    val counts = remember(tigesEssence) {
        tigesEssence
            .groupBy { it.diamCm.toInt() }
            .mapValues { it.value.size }
    }

    val scopeKey = remember(placetteId, selectedSession.session?.sessionId) {
        if (selectedSession.managed) "PLACETTE_${placetteId}_SESSION_${selectedSession.session?.sessionId}"
        else "PLACETTE_${placetteId}"
    }
    val legacyHeightsFlow = remember(userPreferences, scopeKey) { userPreferences.martelageHeightsFlow(scopeKey) }
    val legacyHeights by legacyHeightsFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    val martelageHeights = if (selectedSession.managed) emptyMap() else legacyHeights
    val savedSurfaceFlow = remember(userPreferences, scopeKey) { userPreferences.martelageSurfaceFlow(scopeKey) }
    val savedSurface by savedSurfaceFlow.collectAsStateWithLifecycle(initialValue = null)

    val tigesByDiamClass = remember(tigesEssence) {
        tigesEssence.groupBy { it.diamCm.toInt() }
    }

    val specialTiges = remember(campaignTiges, essenceCode) {
        campaignTiges.filter { t ->
            t.essenceCode.equals(essenceCode, true) &&
                (t.categorie?.uppercase(Locale.getDefault()) in specialCategories)
        }
    }

    // Qualité bois — optionnel
    var showQualityDialog by remember { mutableStateOf(false) }
    var qualityTargetTigeId by remember { mutableStateOf<String?>(null) }
    var qualityTargetDiam by remember { mutableStateOf(30.0) }
    val essenceCategorie by produceState<String?>(initialValue = null, key1 = essenceRepository, key2 = essenceCode) {
        value = try {
            essenceRepository?.getEssenceByCode(essenceCode)?.first()?.categorie
        } catch (_: Throwable) { null }
    }

    var showSpecialList by remember { mutableStateOf(false) }

    var showSpecialDialog by remember { mutableStateOf(false) }
    var specialType by remember { mutableStateOf<SpecialTreeType?>(null) }
    var specialDiameterInput by remember { mutableStateOf("") }
    var specialHeightInput by remember { mutableStateOf("") }
    var specialParasiteInput by remember { mutableStateOf("") }
    var specialNoteInput by remember { mutableStateOf("") }

    fun openSpecialDialog(type: SpecialTreeType) {
        specialType = type
        specialDiameterInput = ""
        specialHeightInput = ""
        specialParasiteInput = ""
        specialNoteInput = ""
        showSpecialDialog = true
    }

    // Classes de base (paramètres) + extensions locales via un petit bouton +
    val baseClasses by produceState(initialValue = (5..120 step 5).toList(), key1 = calculator) {
        value = try {
            val list = calculator?.diameterClasses()
            if (!list.isNullOrEmpty()) list else (5..120 step 5).toList()
        } catch (_: Throwable) {
            (5..120 step 5).toList()
        }
    }
    var extraClasses by remember { mutableStateOf<List<Int>>(emptyList()) }
    val classes = remember(baseClasses, extraClasses, counts) {
        // Toujours inclure les classes réellement utilisées dans les tiges
        (baseClasses + extraClasses + counts.keys).distinct().sorted()
    }

    val orderedClasses = remember(classes) { classes }

    val heightModes by produceState<List<HeightModeEntry>>(
        initialValue = emptyList(),
        key1 = calculator,
        key2 = classes,
        key3 = tigesEssence.size
    ) {
        value = try {
            if (calculator == null) emptyList() else calculator.loadSynthesisParams().heightModes
        } catch (_: Throwable) {
            emptyList()
        }
    }

    // Nom d'essence résolu pour les dialogues
    val essenceName by produceState<String>(initialValue = essenceCode, key1 = essenceRepository, key2 = essenceCode) {
        value = try {
            essenceRepository?.getEssenceByCode(essenceCode)?.first()?.name ?: essenceCode
        } catch (_: Throwable) { essenceCode }
    }

    // Tarif actif — déterminer si les hauteurs sont requises
    val activeTarifMethod by produceState<TarifMethod?>(initialValue = null, key1 = calculator) {
        value = try {
            val params = calculator?.loadSynthesisParams()
            params?.tarifSelection?.method?.let { TarifMethod.fromCode(it) }
        } catch (_: Throwable) { null }
    }
    val tarifRequiresHeight = remember(activeTarifMethod) {
        activeTarifMethod?.let { TarifCalculator.requiresHeight(it) } ?: true
    }

    val missingHeightClasses = remember(tigesEssence, orderedClasses, martelageHeights, tarifRequiresHeight) {
        if (!tarifRequiresHeight) emptyList()
        else {
            val manual = martelageHeights[normalizedEssenceCode].orEmpty()
            orderedClasses.filter { d ->
                val list = tigesByDiamClass[d].orEmpty()
                list.isNotEmpty() && list.any { it.hauteurM == null } && manual[d] == null
            }
        }
    }

    // Classes avec des tiges (pour le dialogue filtré)
    val populatedClasses = remember(orderedClasses, tigesByDiamClass) {
        orderedClasses.filter { (tigesByDiamClass[it]?.size ?: 0) > 0 }
    }

    var showMissingHeightsDialog by remember { mutableStateOf(false) }
    var showSnoozeHeightsDialog by remember { mutableStateOf(false) }
    var showHeightMeasureDialog by remember { mutableStateOf(false) }
    val skipWaistWarning by userPreferences.skipWaistWarning.collectAsStateWithLifecycle(initialValue = false)
    val phoneHeightMPref by userPreferences.phoneHeightM.collectAsStateWithLifecycle(initialValue = 1.0f)
    var snoozeHours by rememberSaveable { mutableStateOf(1) }
    var skipMissingHeightsPrompt by rememberSaveable { mutableStateOf(false) }
    val heightPromptSnoozeUntilMs by userPreferences.heightPromptSnoozeUntilMs.collectAsStateWithLifecycle(initialValue = 0L)
    val isHeightPromptSnoozed = heightPromptSnoozeUntilMs > System.currentTimeMillis()

    val safeNavigateBack = {
        if (canWrite && !skipMissingHeightsPrompt && !isHeightPromptSnoozed && missingHeightClasses.isNotEmpty() && calculator != null) {
            showMissingHeightsDialog = true
        } else {
            onNavigateBack()
        }
    }

    BackHandler {
        safeNavigateBack()
    }

    // Compteurs de hauteurs au niveau composable (utilisés dans le Scaffold et les dialogues)
    val heightTotalPopulated = populatedClasses.size

    // Hauteur globales / par classe de diamètre pour cette essence
    var showHeightDialog by remember { mutableStateOf(false) }
    val heightByClassInput = remember { mutableStateMapOf<Int, String>() }

    LaunchedEffect(canWrite, selectedSession.session?.sessionId) {
        // A stale dialog or Undo must not survive a pause, closure or campaign switch.
        snackbar.currentSnackbarData?.dismiss()
        showQualityDialog = false
        showHeightDialog = false
        showHeightMeasureDialog = false
        showSpecialDialog = false
        showMissingHeightsDialog = false
        showSnoozeHeightsDialog = false
        heightByClassInput.clear()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(essenceName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = safeNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        showSpecialList = !showSpecialList
                        playClickFeedback()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Eco,
                            contentDescription = stringResource(if (showSpecialList) R.string.hide_special_trees else R.string.show_special_trees),
                            tint = if (showSpecialList) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(enabled = canWrite, onClick = {
                        playClickFeedback()
                        val lastTige = tigesEssence.maxByOrNull { it.timestamp }
                        if (lastTige != null) {
                            qualityTargetTigeId = lastTige.id
                            qualityTargetDiam = lastTige.diamCm
                            showQualityDialog = true
                        } else {
                            scope.launch {
                                snackbar.showSnackbar(appContext.getString(R.string.no_tige_to_remove_for_diameter_format, 0))
                            }
                        }
                    }) {
                        Icon(Icons.Default.Star, contentDescription = stringResource(R.string.quality_assess))
                    }
                    IconButton(enabled = canWrite, onClick = {
                        playClickFeedback()
                        val currentMax = classes.maxOrNull() ?: 0
                        val next = if (currentMax < 5) 5 else currentMax + 5
                        if (next !in classes) {
                            extraClasses = extraClasses + next
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_diameter_class))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            val campaignMessage = when {
                sessionUnavailable || selectedSession.inconsistent -> stringResource(R.string.essence_session_unavailable)
                !sessionsReady || reconcilingSession -> stringResource(R.string.essence_session_loading)
                !selectedSession.managed && sessionRepository != null -> stringResource(R.string.martelage_plot_start_required)
                !selectedSession.managed -> stringResource(R.string.essence_session_legacy)
                selectedSession.session?.recoveryRequired == true -> stringResource(R.string.essence_session_recovery)
                selectedSession.session?.martelageState == MartelageStates.PAUSED -> stringResource(R.string.essence_session_paused)
                selectedSession.session?.martelageState == MartelageStates.CLOSED -> stringResource(
                    R.string.essence_session_closed, selectedSession.session?.revisionNumber ?: 1
                )
                else -> stringResource(R.string.essence_session_running, selectedSession.session?.revisionNumber ?: 1)
            }
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                Text(campaignMessage, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(10.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpecialTreeButton(label = stringResource(R.string.special_tree_dying), enabled = canWrite) {
                    playClickFeedback()
                    openSpecialDialog(SpecialTreeType.DEPERISSANT)
                }
                SpecialTreeButton(label = stringResource(R.string.special_tree_bio), enabled = canWrite) {
                    playClickFeedback()
                    openSpecialDialog(SpecialTreeType.ARBRE_BIO)
                }
                SpecialTreeButton(label = stringResource(R.string.special_tree_dead), enabled = canWrite) {
                    playClickFeedback()
                    openSpecialDialog(SpecialTreeType.MORT)
                }
                SpecialTreeButton(label = stringResource(R.string.special_tree_parasite), enabled = canWrite) {
                    playClickFeedback()
                    openSpecialDialog(SpecialTreeType.PARASITE)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Compteur G/ha live
            val gTotal = remember(tigesEssence) {
                tigesEssence.sumOf { t ->
                    val r = t.diamCm / 200.0
                    kotlin.math.PI * r * r
                }
            }
            val gHa = remember(gTotal, savedSurface) {
                val s = savedSurface
                if (s != null && s > 0.0) gTotal * 10_000.0 / s else null
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                ) {
                    Text(
                        stringResource(R.string.live_n_total, tigesEssence.size),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                ) {
                    Text(
                        if (gHa != null)
                            stringResource(R.string.live_g_ha, String.format("%.2f", gHa))
                        else
                            stringResource(R.string.live_g_total, String.format("%.3f", gTotal)),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bouton Hauteur amélioré avec badge de statut
            val heightSetCount = remember(populatedClasses, martelageHeights) {
                val manual = martelageHeights[normalizedEssenceCode].orEmpty()
                populatedClasses.count { d ->
                    val tiges = tigesByDiamClass[d].orEmpty()
                    manual[d] != null || tiges.all { it.hauteurM != null }
                }
            }
            val heightTotalPopulated = populatedClasses.size
            val heightMissingCount = heightTotalPopulated - heightSetCount
            val heightButtonColor = when {
                !tarifRequiresHeight -> MaterialTheme.colorScheme.surfaceVariant
                heightMissingCount == 0 && heightTotalPopulated > 0 -> MaterialTheme.colorScheme.primaryContainer
                heightMissingCount > 0 -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Info tarif
                if (activeTarifMethod != null) {
                    Text(
                        text = if (tarifRequiresHeight) stringResource(R.string.height_tarif_info_2e) else stringResource(R.string.height_tarif_info_1e),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (tarifRequiresHeight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = {
                        playClickFeedback()
                        val fixed = heightModes
                            .filter {
                                it.essence.equals(essenceCode, true) &&
                                    it.mode.uppercase(Locale.getDefault()) == "FIXED" &&
                                    it.fixed != null &&
                                    it.fixed > 0.0
                            }
                            .associate { it.diamClass to (it.fixed ?: 0.0) }
                        val manual = martelageHeights[normalizedEssenceCode].orEmpty()

                        heightByClassInput.clear()
                        populatedClasses.forEach { d ->
                            val measured = tigesByDiamClass[d].orEmpty().mapNotNull { it.hauteurM }.takeIf { it.isNotEmpty() }?.average()
                            val v = if (selectedSession.managed) measured else manual[d] ?: fixed[d]
                            heightByClassInput[d] = v?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: ""
                        }
                        showHeightDialog = true
                    },
                    enabled = calculator != null && canWrite,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = heightButtonColor)
                ) {
                    Icon(
                        imageVector = Icons.Default.Height,
                        contentDescription = stringResource(R.string.configure_heights),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.height))
                    if (heightTotalPopulated > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$heightSetCount/$heightTotalPopulated",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (heightMissingCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (identityRepository != null && observationQualificationRepository != null &&
                businessReadAccess != null && sessionRepository != null) {
                Spacer(modifier = Modifier.height(8.dp))
                ObservationQualificationEntry(
                    parcelleId = parcelleId,
                    placetteId = placetteId,
                    essenceCode = essenceCode,
                    essenceName = essenceName,
                    session = selectedSession.session,
                    observations = campaignTiges.filter { it.essenceCode.equals(essenceCode, true) },
                    available = sessionsReady && !sessionUnavailable && !reconcilingSession &&
                        selectedSession.managed && !selectedSession.inconsistent,
                    identityRepository = identityRepository,
                    businessReadAccess = businessReadAccess,
                    tigeRepository = tigeRepository,
                    sessionRepository = sessionRepository,
                    qualificationRepository = observationQualificationRepository,
                    onSaved = { scope.launch {
                        snackbar.showSnackbar(appContext.getString(R.string.observation_qualification_saved))
                    } },
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            TipCard(
                tipKey = "essences_tip",
                title = stringResource(R.string.tip_essences_title),
                message = stringResource(R.string.tip_essences_msg)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (showSpecialList) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    items(specialTiges, key = { it.id }) { t ->
                        SpecialTreeCard(
                            tige = t,
                            enabled = canWrite,
                            onLongPress = {
                                playClickFeedback()
                                scope.launch {
                                    if (writeObservation { tigeRepository.deleteTige(t.id) }) {
                                        snackbar.showSnackbar(appContext.getString(R.string.tree_deleted))
                                    }
                                }
                            }
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    items(orderedClasses, key = { it }) { d ->
                        val count = counts[d] ?: 0
                        val hStatus = if (count > 0 && tarifRequiresHeight) {
                            val tiges = tigesByDiamClass[d].orEmpty()
                            val manual = martelageHeights[normalizedEssenceCode]?.get(d)
                            when {
                                tiges.all { it.hauteurM != null } -> HeightStatus.MEASURED
                                manual != null -> HeightStatus.SET
                                else -> HeightStatus.MISSING
                            }
                        } else HeightStatus.NONE
                        DiameterCard(
                            diameter = d,
                            count = count,
                            enabled = canWrite,
                            heightStatus = hStatus,
                            onTap = {
                                playClickFeedback()
                                scope.launch {
                                    val id = UUID.randomUUID().toString()
                                    // Récupérer un point GPS existant pour cette classe+essence
                                    val existingGps = tigesEssence
                                        .filter { it.diamCm.toInt() == d && it.gpsWkt != null }
                                        .maxByOrNull { it.timestamp }

                                    val ok = writeObservation { sessionId ->
                                        tigeRepository.insertTige(
                                            com.forestry.counter.domain.model.Tige(
                                                id = id,
                                                parcelleId = parcelleId,
                                                placetteId = placetteId,
                                                essenceCode = essenceCode,
                                                diamCm = d.toDouble(),
                                                hauteurM = null,
                                                gpsWkt = existingGps?.gpsWkt,
                                                precisionM = existingGps?.precisionM,
                                                altitudeM = existingGps?.altitudeM,
                                                note = null,
                                                produit = null,
                                                fCoef = null,
                                                valueEur = null,
                                                sessionId = sessionId
                                            )
                                        )
                                    }
                                    if (ok) {
                                        // Déclencher le GPS immédiatement — persiste même si l'utilisateur quitte
                                        // Seulement si pas de point GPS existant pour cette classe
                                        if (existingGps == null) {
                                            captureGpsForTige(id)
                                        }
                                        val res = snackbar.showSnackbar(
                                            message = appContext.getString(R.string.tige_added),
                                            actionLabel = appContext.getString(R.string.undo)
                                        )
                                        if (res == SnackbarResult.ActionPerformed) {
                                            if (writeObservation { tigeRepository.deleteTige(id) }) {
                                                snackbar.showSnackbar(appContext.getString(R.string.add_undone))
                                            }
                                        }
                                    }
                                }
                            },
                            onLongPress = {
                                playClickFeedback()
                                scope.launch {
                                    val target = tigesByDiamClass[d].orEmpty().maxByOrNull { it.timestamp }
                                    if (target == null) {
                                        snackbar.showSnackbar(appContext.getString(R.string.no_tige_to_remove_for_diameter_format, d))
                                    } else if (writeObservation { tigeRepository.deleteTige(target.id) }) {
                                        snackbar.showSnackbar(appContext.getString(R.string.tree_deleted))
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    var quickFillInput by remember { mutableStateOf("") }

    // Dialogue mesure clinométrique via capteur
    if (showHeightMeasureDialog && canWrite) {
        TreeHeightMeasureDialog(
            skipWaistWarning = skipWaistWarning,
            phoneHeightM     = phoneHeightMPref,
            onDismiss = { showHeightMeasureDialog = false },
            onResult  = { measuredH ->
                showHeightMeasureDialog = false
                val formatted = String.format(Locale.getDefault(), "%.1f", measuredH)
                // Pré-remplir toutes les classes vides avec la hauteur mesurée
                populatedClasses.forEach { d ->
                    if (heightByClassInput[d].isNullOrBlank()) {
                        heightByClassInput[d] = formatted
                    }
                }
                quickFillInput = formatted
                scope.launch { userPreferences.setPhoneHeightM(phoneHeightMPref) }
            },
            onSkipWarningForever = {
                scope.launch { userPreferences.setSkipWaistWarning(true) }
            }
        )
    }

    // Dialogue de configuration des hauteurs (amélioré)
    if (showHeightDialog && calculator != null && canWrite) {
        val invalidHeightInput = hasInvalidHeightInput(populatedClasses.map { heightByClassInput[it] ?: "" })
        AppMiniDialog(
            onDismissRequest = { showHeightDialog = false },
            animationsEnabled = animationsEnabled,
            icon = Icons.Default.Height,
            title = stringResource(R.string.height_dialog_essence_title_format, essenceName),
            description = stringResource(if (selectedSession.managed)
                R.string.essence_session_height_by_class_hint else R.string.martelage_height_by_class_hint),
            confirmText = stringResource(R.string.validate),
            confirmEnabled = canWrite && !invalidHeightInput,
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                scope.launch {
                    // Ne pas enregistrer les classes valides en ignorant une autre saisie invalide.
                    if (hasInvalidHeightInput(populatedClasses.map { heightByClassInput[it] ?: "" })) {
                        snackbar.showSnackbar(appContext.getString(R.string.height_input_invalid))
                        return@launch
                    }
                    val cleaned: Map<Int, Double> = populatedClasses.mapNotNull { d ->
                        val (mean, _) = parseHeightInputMean(heightByClassInput[d] ?: "")
                        if (mean != null && mean > 0.0) d to mean else null
                    }.toMap()

                    if (cleaned.isEmpty()) {
                        snackbar.showSnackbar(appContext.getString(R.string.no_valid_height_entered))
                        return@launch
                    }

                    val saved = writeObservation { sessionId ->
                        if (sessionId != null) {
                            // Campaign heights belong to observations, not to shared legacy/global preferences.
                            for (tree in tigesEssence) {
                                val height = cleaned[tree.diamCm.toInt()] ?: continue
                                check(tree.sessionId == sessionId && tigeRepository.setTigeHeight(tree.id, height)) {
                                    "MARTELAGE_HEIGHT_STATE_CHANGED"
                                }
                            }
                        } else {
                            val newMartelageHeights = martelageHeights.toMutableMap().apply {
                                put(normalizedEssenceCode, cleaned)
                            }
                            userPreferences.setMartelageHeights(scopeKey, newMartelageHeights)

                            // Preserve the separate legacy calculator/preferences flow.
                            val parcelleScopeKey = "PARCELLE_${parcelleId}"
                            if (parcelleScopeKey != scopeKey) {
                                val parcelleHeights = userPreferences.martelageHeightsFlow(parcelleScopeKey).first()
                                val updatedParcelleHeights = parcelleHeights.toMutableMap().apply {
                                    put(normalizedEssenceCode, cleaned)
                                }
                                userPreferences.setMartelageHeights(parcelleScopeKey, updatedParcelleHeights)
                            }
                            orderedClasses.forEach { d ->
                                val height = cleaned[d]
                                calculator.setHeightMode(HeightModeEntry(
                                    essence = essenceCode,
                                    diamClass = d,
                                    mode = if (height != null && height > 0.0) "FIXED" else "SAMPLES",
                                    fixed = height,
                                ))
                            }
                        }
                    }

                    if (saved) {
                        showHeightDialog = false
                        snackbar.showSnackbar(appContext.getString(R.string.heights_saved))
                    }
                }
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Bannière info tarif
                val tarifLabel = activeTarifMethod?.label ?: ""
                Surface(
                    color = if (tarifRequiresHeight) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (tarifRequiresHeight) stringResource(R.string.height_tarif_info_2e) + " ($tarifLabel)"
                            else stringResource(R.string.height_tarif_info_1e) + " ($tarifLabel)",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                // Bouton mesure clinométrique par capteur
                FilledTonalButton(
                    onClick = { showHeightMeasureDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Height,
                        contentDescription = stringResource(R.string.cd_height),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.height_measure_open_button))
                }

                // Quick-fill : appliquer une même hauteur à toutes les classes vides
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = quickFillInput,
                        onValueChange = { quickFillInput = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.height_quick_fill_title)) },
                        placeholder = { Text(stringResource(R.string.placeholder_height)) },
                        suffix = { Text(stringResource(R.string.unit_m)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true
                    )
                    FilledTonalButton(
                        onClick = {
                            val v = quickFillInput.replace(',', '.').toDoubleOrNull()
                            if (v != null && v > 0.0) {
                                val formatted = String.format(Locale.getDefault(), "%.1f", v)
                                populatedClasses.forEach { d ->
                                    heightByClassInput[d] = formatted
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.height_quick_fill_apply), style = MaterialTheme.typography.labelSmall)
                    }
                }

                HorizontalDivider()

                // Liste des classes avec des tiges uniquement
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    populatedClasses.forEach { d -> key(d) {
                        val count = counts[d] ?: 0
                        val tiges = tigesByDiamClass[d].orEmpty()
                        val measuredCount = tiges.count { it.hauteurM != null }
                        val missingCount = tiges.count { it.hauteurM == null }
                        val (meanVal, meanCount) = parseHeightInputMean(heightByClassInput[d] ?: "")
                        val hasValue = meanVal != null && meanVal > 0.0
                        val needsValue = missingCount > 0 && !hasValue

                        val cardTargetColor = when {
                            needsValue && tarifRequiresHeight -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                            hasValue -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        }
                        val cardColor by animateColorAsState(
                            targetValue = cardTargetColor,
                            animationSpec = tween(
                                durationMillis = if (animationsEnabled) 220 else 0,
                                easing = FastOutSlowInEasing
                            ),
                            label = "heightClassCardColor"
                        )
                        val cardElevation by animateDpAsState(
                            targetValue = if (needsValue && tarifRequiresHeight) 4.dp else 1.dp,
                            animationSpec = tween(
                                durationMillis = if (animationsEnabled) 220 else 0,
                                easing = FastOutSlowInEasing
                            ),
                            label = "heightClassCardElevation"
                        )

                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(containerColor = cardColor),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = cardElevation)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.diameter_cm_value_format, d),
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = stringResource(R.string.height_n_stems_format, count),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (needsValue && tarifRequiresHeight) {
                                        val badgeVisibleState = remember(needsValue) { MutableTransitionState(needsValue).apply { targetState = needsValue } }
                                        AnimatedVisibility(
                                            visibleState = badgeVisibleState,
                                            enter = fadeIn(animationSpec = tween(durationMillis = if (animationsEnabled) 160 else 0, easing = FastOutSlowInEasing)) +
                                                expandHorizontally(animationSpec = tween(durationMillis = if (animationsEnabled) 200 else 0, easing = FastOutSlowInEasing)),
                                            exit = fadeOut(animationSpec = tween(durationMillis = if (animationsEnabled) 140 else 0, easing = FastOutSlowInEasing)) +
                                                shrinkHorizontally(animationSpec = tween(durationMillis = if (animationsEnabled) 180 else 0, easing = FastOutSlowInEasing))
                                        ) {
                                            ToCompleteBadge()
                                        }
                                    }
                                }
                                HeightClassInputField(
                                    value = heightByClassInput[d] ?: "",
                                    onValueChange = { heightByClassInput[d] = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    missingCount = missingCount,
                                    recordedCount = measuredCount,
                                    tarifRequiresHeight = tarifRequiresHeight,
                                )
                            }
                        }
                    } }
                }
            }
        }
    }

    if (showMissingHeightsDialog && canWrite) {
        val list = missingHeightClasses
        val tarifLabel = activeTarifMethod?.label ?: ""
        val descText = if (tarifRequiresHeight && tarifLabel.isNotBlank()) {
            stringResource(R.string.mandatory_heights_desc_tarif, tarifLabel)
        } else {
            stringResource(R.string.mandatory_heights_desc)
        }
        AppMiniDialog(
            onDismissRequest = { showMissingHeightsDialog = false },
            animationsEnabled = animationsEnabled,
            icon = Icons.Default.Warning,
            title = stringResource(R.string.mandatory_heights_title) + " — $essenceName",
            description = descText,
            confirmText = stringResource(R.string.configure_heights),
            dismissText = stringResource(R.string.mandatory_heights_skip),
            dismissColor = androidx.compose.ui.graphics.Color(0xFF2E7D32),
            neutralText = stringResource(R.string.mandatory_heights_snooze_title),
            onDismiss = {
                showMissingHeightsDialog = false
                skipMissingHeightsPrompt = true
                onNavigateBack()
            },
            onNeutral = {
                showMissingHeightsDialog = false
                snoozeHours = 1
                showSnoozeHeightsDialog = true
            },
            onConfirm = {
                showMissingHeightsDialog = false
                val fixed = heightModes
                    .filter {
                        it.essence.equals(essenceCode, true) &&
                            it.mode.uppercase(Locale.getDefault()) == "FIXED" &&
                            it.fixed != null && it.fixed > 0.0
                    }
                    .associate { it.diamClass to (it.fixed ?: 0.0) }
                val manual = martelageHeights[normalizedEssenceCode].orEmpty()
                heightByClassInput.clear()
                populatedClasses.forEach { d ->
                    val measured = tigesByDiamClass[d].orEmpty().mapNotNull { it.hauteurM }.takeIf { it.isNotEmpty() }?.average()
                    val v = if (selectedSession.managed) measured else manual[d] ?: fixed[d]
                    if (v != null && v > 0.0) heightByClassInput[d] = v.toString()
                }
                showHeightDialog = true
            }
        ) {
            // Contenu amélioré : barre de progression + chips des classes manquantes
            if (list.isNotEmpty()) {
                val configured = heightTotalPopulated - list.size
                val progress = if (heightTotalPopulated > 0) configured.toFloat() / heightTotalPopulated else 0f
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Barre de progression classes configurées / total
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.weight(1f).height(6.dp),
                            color = if (progress >= 1f) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error,
                            trackColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        )
                        Text(
                            "$configured / $heightTotalPopulated",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (configured < heightTotalPopulated) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary
                        )
                    }
                    // Classes manquantes
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.Height,
                                    contentDescription = stringResource(R.string.cd_height),
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    "${list.size} / $heightTotalPopulated",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Text(
                                list.joinToString("  ·  ") { d -> "$d cm" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSnoozeHeightsDialog && canWrite) {
        AppMiniDialog(
            onDismissRequest = { showSnoozeHeightsDialog = false },
            animationsEnabled = animationsEnabled,
            icon = Icons.Default.Warning,
            title = stringResource(R.string.mandatory_heights_snooze_title),
            description = stringResource(R.string.mandatory_heights_desc),
            confirmText = stringResource(R.string.validate),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                val hours = snoozeHours.coerceAtLeast(1)
                showSnoozeHeightsDialog = false
                skipMissingHeightsPrompt = true
                scope.launch {
                    userPreferences.snoozeHeightPromptForHours(hours)
                    snackbar.showSnackbar(appContext.getString(R.string.mandatory_heights_snoozed_format, hours))
                }
                onNavigateBack()
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(1, 4, 24).forEach { hours ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(
                            selected = snoozeHours == hours,
                            onClick = { snoozeHours = hours }
                        )
                        Text(stringResource(R.string.mandatory_heights_snooze_format, hours))
                    }
                }
            }
        }
    }

    if (showSpecialDialog && specialType != null && canWrite) {
        val type = specialType ?: return
        val titleRes = when (type) {
            SpecialTreeType.DEPERISSANT -> R.string.add_dying_tree_title
            SpecialTreeType.ARBRE_BIO -> R.string.add_bio_tree_title
            SpecialTreeType.MORT -> R.string.add_dead_tree_title
            SpecialTreeType.PARASITE -> R.string.add_parasitized_tree_title
        }
        AppMiniDialog(
            onDismissRequest = { showSpecialDialog = false },
            animationsEnabled = animationsEnabled,
            icon = Icons.Default.Eco,
            title = stringResource(titleRes),
            confirmText = stringResource(R.string.validate),
            confirmEnabled = canWrite,
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                val diam = specialDiameterInput.replace(',', '.').toDoubleOrNull()
                val h = specialHeightInput.replace(',', '.').toDoubleOrNull()
                if (diam == null || !diam.isFinite() || diam <= 0.0) {
                    scope.launch { snackbar.showSnackbar(appContext.getString(R.string.invalid_diameter)) }
                    return@AppMiniDialog
                }
                // Une hauteur inconnue reste vide ; une saisie incorrecte ne devient pas un faux inconnu.
                if (specialHeightInput.isNotBlank() && (h == null || !h.isFinite() || h <= 0.0)) {
                    scope.launch { snackbar.showSnackbar(appContext.getString(R.string.observation_qualification_single_height_invalid)) }
                    return@AppMiniDialog
                }
                val id = UUID.randomUUID().toString()
                val categorie = when (type) {
                    SpecialTreeType.DEPERISSANT -> "DEPERISSANT"
                    SpecialTreeType.ARBRE_BIO -> "ARBRE_BIO"
                    SpecialTreeType.MORT -> "MORT"
                    SpecialTreeType.PARASITE -> "PARASITE"
                }
                val defects = if (type == SpecialTreeType.PARASITE && specialParasiteInput.isNotBlank()) {
                    listOf(specialParasiteInput.trim())
                } else {
                    null
                }
                showSpecialDialog = false
                scope.launch {
                    val ok = writeObservation { sessionId ->
                        tigeRepository.insertTige(
                            com.forestry.counter.domain.model.Tige(
                                id = id,
                                parcelleId = parcelleId,
                                placetteId = placetteId,
                                essenceCode = essenceCode,
                                diamCm = diam,
                                hauteurM = h,
                                gpsWkt = null,
                                precisionM = null,
                                altitudeM = null,
                                note = specialNoteInput.ifBlank { null },
                                produit = null,
                                fCoef = null,
                                valueEur = null,
                                numero = null,
                                categorie = categorie,
                                qualite = null,
                                defauts = defects,
                                photoUri = null,
                                sessionId = sessionId
                            )
                        )
                    }
                    if (ok) {
                        playClickFeedback()
                        captureGpsForTige(id)
                        snackbar.showSnackbar(appContext.getString(R.string.tree_added))
                    }
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = specialDiameterInput,
                    onValueChange = { specialDiameterInput = it },
                    label = { Text(stringResource(R.string.diameter_cm_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = specialHeightInput,
                    onValueChange = { specialHeightInput = it },
                    label = { Text(stringResource(R.string.height_m_optional_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (type == SpecialTreeType.PARASITE) {
                    OutlinedTextField(
                        value = specialParasiteInput,
                        onValueChange = { specialParasiteInput = it },
                        label = { Text(stringResource(R.string.special_tree_parasite)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = specialNoteInput,
                    onValueChange = { specialNoteInput = it },
                    label = { Text(stringResource(R.string.note_optional_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // Dialogue d'évaluation qualité bois (optionnel)
    if (showQualityDialog && qualityTargetTigeId != null && canWrite) {
        val targetTige = tigesEssence.firstOrNull { it.id == qualityTargetTigeId }
        WoodQualityDialog(
            essenceCode = essenceCode,
            categorie = essenceCategorie,
            diamCm = qualityTargetDiam,
            hauteurM = targetTige?.hauteurM,
            initialDetail = targetTige?.qualiteDetail,
            animationsEnabled = animationsEnabled,
            onDismiss = { showQualityDialog = false },
            onConfirm = { grade, classification, detailJson ->
                showQualityDialog = false
                val tigeId = qualityTargetTigeId ?: return@WoodQualityDialog
                scope.launch {
                    val saved = writeObservation {
                        check(tigeRepository.updateTigeQuality(
                            tigeId = tigeId,
                            qualite = grade.ordinal,
                            produit = classification.primary.code,
                            qualiteDetail = detailJson
                        )) { "MARTELAGE_QUALITY_STATE_CHANGED" }
                    }
                    if (saved) {
                        snackbar.showSnackbar(
                            appContext.getString(
                                R.string.quality_saved,
                                grade.shortLabel,
                                classification.primary.label
                            )
                        )
                    }
                }
            }
        )
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

/**
 * Statut hauteur pour une classe de diamètre.
 */
private enum class HeightStatus { NONE, SET, MEASURED, MISSING }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiameterCard(
    diameter: Int,
    count: Int,
    heightStatus: HeightStatus = HeightStatus.NONE,
    enabled: Boolean = true,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val borderColor = when (heightStatus) {
        HeightStatus.SET, HeightStatus.MEASURED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        HeightStatus.MISSING -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        HeightStatus.NONE -> Color.Transparent
    }
    ElevatedCard(
        modifier = Modifier
            .then(
                if (heightStatus != HeightStatus.NONE) Modifier.border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
                else Modifier
            )
            .combinedClickable(enabled = enabled, onClick = onTap, onLongClick = onLongPress),
        colors = CardDefaults.elevatedCardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.diameter_cm_value_format, diameter), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(count.toString(), style = MaterialTheme.typography.headlineSmall)
            if (count > 0 && heightStatus != HeightStatus.NONE) {
                val (label, color) = when (heightStatus) {
                    HeightStatus.SET -> stringResource(R.string.height_status_set) to MaterialTheme.colorScheme.primary
                    HeightStatus.MEASURED -> stringResource(R.string.height_status_measured) to MaterialTheme.colorScheme.tertiary
                    HeightStatus.MISSING -> stringResource(R.string.height_status_missing) to MaterialTheme.colorScheme.error
                    HeightStatus.NONE -> "" to Color.Transparent
                }
                Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            } else {
                Text(stringResource(if (enabled) R.string.diameter_card_hint else R.string.essence_session_read_only),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private enum class SpecialTreeType {
    DEPERISSANT,
    ARBRE_BIO,
    MORT,
    PARASITE
}

@Composable
private fun SpecialTreeButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpecialTreeCard(
    tige: com.forestry.counter.domain.model.Tige,
    enabled: Boolean = true,
    onLongPress: () -> Unit
) {
    val catLabel = when (tige.categorie?.uppercase(Locale.getDefault())) {
        "DEPERISSANT" -> stringResource(R.string.special_tree_dying)
        "ARBRE_BIO" -> stringResource(R.string.special_tree_bio)
        "MORT" -> stringResource(R.string.special_tree_dead)
        "PARASITE" -> stringResource(R.string.special_tree_parasite)
        else -> stringResource(R.string.special_tree_special)
    }
    val parasite = tige.defauts?.joinToString(", ") ?: ""
    val diameterLabel = stringResource(R.string.diameter_cm_value_format, tige.diamCm.toInt())
    val heightLabel = tige.hauteurM?.let { stringResource(R.string.height_m_value_format, it) }
    val subtitle = buildString {
        append(diameterLabel)
        heightLabel?.let { append(" · $it") }
        if (parasite.isNotBlank()) {
            append(" · $parasite")
        }
    }

    ElevatedCard(
        modifier = Modifier
            .combinedClickable(enabled = enabled, onClick = {}, onLongClick = onLongPress),
        colors = CardDefaults.elevatedCardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(catLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
