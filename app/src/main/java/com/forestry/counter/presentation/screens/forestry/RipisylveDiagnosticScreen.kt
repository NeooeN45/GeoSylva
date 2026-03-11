package com.forestry.counter.presentation.screens.forestry

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.forestry.counter.domain.model.ripisylve.InadapteesMode
import com.forestry.counter.domain.model.ripisylve.LargeurMode
import com.forestry.counter.domain.model.ripisylve.RipisylveObservation
import com.forestry.counter.domain.model.ripisylve.RipisylveFonctionnalite
import com.forestry.counter.domain.model.ripisylve.RipisylveScore
import com.forestry.counter.domain.repository.RipisylveRepository
import com.forestry.counter.domain.repository.TigeRepository
import com.forestry.counter.domain.usecase.ripisylve.RipisylveScorer
import com.forestry.counter.presentation.utils.StaggerEntrance
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.foundation.ExperimentalFoundationApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RipisylveDiagnosticScreen(
    parcelleId: String,
    ripisylveRepository: RipisylveRepository,
    tigeRepository: TigeRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tiges by tigeRepository.getTigesByParcelle(parcelleId).collectAsState(initial = emptyList())
    val diagnostics by ripisylveRepository.getByParcelle(parcelleId).collectAsState(initial = emptyList())

    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf("Dimensions", "Structure", "Menaces", "Résultat", "Historique")
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    // Synchronize tabs and pager
    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab) {
            pagerState.animateScrollToPage(selectedTab)
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        if (selectedTab != pagerState.currentPage) {
            selectedTab = pagerState.currentPage
        }
    }

    // ── Saisie terrain (état) ──
    var gpsLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var gpsLon by rememberSaveable { mutableStateOf<Double?>(null) }
    var observerName by rememberSaveable { mutableStateOf("") }
    var sectionLength by rememberSaveable { mutableStateOf("50") }
    var sectionNotes by rememberSaveable { mutableStateOf("") }

    // GPS capture
    val captureGps = {
        scope.launch(Dispatchers.IO) {
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                if (androidx.core.app.ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    val loc = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                        ?: lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                    loc?.let {
                        gpsLat = it.latitude
                        gpsLon = it.longitude
                    }
                }
            } catch (_: Exception) {}
        }
    }
    
    LaunchedEffect(Unit) { captureGps() }

    // Critère 1 – Continuité
    var continuitePct by rememberSaveable { mutableStateOf(0f) }
    // Critère 2 – Largeur
    var largeurMode by rememberSaveable { mutableStateOf(LargeurMode.UNE_RANGEE) }
    // Critère 3 – Strates
    var strateHerbacee by rememberSaveable { mutableStateOf(false) }
    var strateArbustive by rememberSaveable { mutableStateOf(false) }
    var strateArborescente by rememberSaveable { mutableStateOf(false) }
    // Critère 4 – Diversité
    var nbEspeces by rememberSaveable { mutableStateOf("0") }
    // Critère 5 – Classes diamètre (manual override)
    var hasTresPetitBois by rememberSaveable { mutableStateOf(false) }
    var hasPetitBois by rememberSaveable { mutableStateOf(false) }
    var hasMoyenBois by rememberSaveable { mutableStateOf(false) }
    var hasGrosBois by rememberSaveable { mutableStateOf(false) }
    // Critère 6 – Microhabitats
    var microCavites by rememberSaveable { mutableStateOf(false) }
    var microFissures by rememberSaveable { mutableStateOf(false) }
    var microDecol by rememberSaveable { mutableStateOf(false) }
    var microChamp by rememberSaveable { mutableStateOf(false) }
    var microBoisMort by rememberSaveable { mutableStateOf(false) }
    var microTresGros by rememberSaveable { mutableStateOf(false) }
    // Critère 7 – Sanitaire
    var sanitairePct by rememberSaveable { mutableStateOf(0f) }
    // Critère 8 – Invasives
    var invasivesPct by rememberSaveable { mutableStateOf(0f) }
    // Critère 9 – Inadaptées
    var inadapteesMode by rememberSaveable { mutableStateOf(InadapteesMode.ABSENCE) }
    // Critère 10 – Stabilité
    var stabilitePct by rememberSaveable { mutableStateOf(0f) }
    // Notes
    var globalNotes by rememberSaveable { mutableStateOf("") }

    // ── Auto-fill classes diam depuis dendro ──
    val dendroAutoFill by remember(tiges) {
        derivedStateOf {
            val obs = RipisylveObservation(parcelleId = parcelleId)
            RipisylveScorer.autoFillFromTiges(obs, tiges)
        }
    }

    // ── Calcul du score ──
    val currentObs by remember(
        gpsLat, gpsLon,
        continuitePct, largeurMode, strateHerbacee, strateArbustive, strateArborescente,
        nbEspeces, hasTresPetitBois, hasPetitBois, hasMoyenBois, hasGrosBois,
        microCavites, microFissures, microDecol, microChamp, microBoisMort, microTresGros,
        sanitairePct, invasivesPct, inadapteesMode, stabilitePct
    ) {
        derivedStateOf {
            RipisylveObservation(
                parcelleId = parcelleId,
                observerName = observerName,
                latitude = gpsLat,
                longitude = gpsLon,
                continuitePct = continuitePct.toDouble(),
                largeurMode = largeurMode,
                strateHerbacee = strateHerbacee,
                strateArbustive = strateArbustive,
                strateArborescente = strateArborescente,
                nbEspecesObservees = nbEspeces.toIntOrNull() ?: 0,
                hasTresPetitBois = if (dendroAutoFill.diamAutoFromDendro) dendroAutoFill.hasTresPetitBois else hasTresPetitBois,
                hasPetitBois = if (dendroAutoFill.diamAutoFromDendro) dendroAutoFill.hasPetitBois else hasPetitBois,
                hasMoyenBois = if (dendroAutoFill.diamAutoFromDendro) dendroAutoFill.hasMoyenBois else hasMoyenBois,
                hasGrosBois = if (dendroAutoFill.diamAutoFromDendro) dendroAutoFill.hasGrosBois else hasGrosBois,
                microhabitatCavites = microCavites,
                microhabitatFissures = microFissures,
                microhabitatDecollementEcorce = microDecol,
                microhabitatChampignons = microChamp,
                microhabitatBoisMort = microBoisMort,
                microhabitatTresGrosBois = microTresGros,
                sanitairePct = sanitairePct.toDouble(),
                invasivesPct = invasivesPct.toDouble(),
                inadapteesMode = inadapteesMode,
                stabilitePct = stabilitePct.toDouble(),
                globalNotes = globalNotes
            )
        }
    }
    val score by remember(currentObs) {
        derivedStateOf { RipisylveScorer.score(currentObs, tiges) }
    }

    // ── GPS auto-capture ──
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    loc?.let { gpsLat = it.latitude; gpsLon = it.longitude }
                }
            } catch (_: Exception) {}
        }
    }

    // ── Snackbar ──
    val snackbarHostState = remember { SnackbarHostState() }

    fun saveCurrentDiagnostic() {
        scope.launch {
            val now = System.currentTimeMillis()
            ripisylveRepository.save(
                currentObs.copy(
                    id = if (currentObs.id.isBlank()) UUID.randomUUID().toString() else currentObs.id,
                    parcelleId = parcelleId,
                    observerName = observerName,
                    observationDate = now,
                    createdAt = now,
                    updatedAt = now,
                    latitude = gpsLat,
                    longitude = gpsLon,
                    sectionLengthM = sectionLength.toDoubleOrNull() ?: 50.0,
                    sectionNotes = sectionNotes,
                    globalNotes = globalNotes
                )
            )
            snackbarHostState.showSnackbar("Diagnostic sauvegardé")
            selectedTab = 2
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Diagnostic Ripisylve", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { saveCurrentDiagnostic() }) {
                        Icon(Icons.Default.Save, "Sauvegarder")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A6B3C),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // ── Tab row ──
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF1A6B3C),
                contentColor = Color.White,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = Color(0xFFB8F5A0)
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = selectedTab == i,
                        onClick = { selectedTab = i },
                        text = { Text(title, fontSize = 12.sp) }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> DimensionsTab(
                        gpsLat = gpsLat, gpsLon = gpsLon,
                        observerName = observerName, onObserverChange = { observerName = it },
                        sectionLength = sectionLength, onSectionLengthChange = { sectionLength = it },
                        sectionNotes = sectionNotes, onSectionNotesChange = { sectionNotes = it },
                        continuitePct = continuitePct, onContinuiteChange = { continuitePct = it },
                        largeurMode = largeurMode, onLargeurChange = { largeurMode = it }
                    )
                    1 -> StructureTab(
                        strateHerbacee = strateHerbacee, onStrateHerbaceeChange = { strateHerbacee = it },
                        strateArbustive = strateArbustive, onStrateArbustiveChange = { strateArbustive = it },
                        strateArborescente = strateArborescente, onStrateArborescente = { strateArborescente = it },
                        nbEspeces = nbEspeces, onNbEspecesChange = { nbEspeces = it },
                        hasTresPetitBois = hasTresPetitBois, onHasTresPetitBoisChange = { hasTresPetitBois = it },
                        hasPetitBois = hasPetitBois, onHasPetitBoisChange = { hasPetitBois = it },
                        hasMoyenBois = hasMoyenBois, onHasMoyenBoisChange = { hasMoyenBois = it },
                        hasGrosBois = hasGrosBois, onHasGrosBoisChange = { hasGrosBois = it },
                        dendroAutoFill = dendroAutoFill,
                        microCavites = microCavites, onMicroCavitesChange = { microCavites = it },
                        microFissures = microFissures, onMicroFissuresChange = { microFissures = it },
                        microDecol = microDecol, onMicroDecolChange = { microDecol = it },
                        microChamp = microChamp, onMicroChampChange = { microChamp = it },
                        microBoisMort = microBoisMort, onMicroBoisMortChange = { microBoisMort = it },
                        microTresGros = microTresGros, onMicroTresGrosChange = { microTresGros = it }
                    )
                    2 -> MenacesTab(
                        sanitairePct = sanitairePct, onSanitaireChange = { sanitairePct = it },
                        invasivesPct = invasivesPct, onInvasivesChange = { invasivesPct = it },
                        inadapteesMode = inadapteesMode, onInadapteesChange = { inadapteesMode = it },
                        stabilitePct = stabilitePct, onStabiliteChange = { stabilitePct = it },
                        globalNotes = globalNotes, onGlobalNotesChange = { globalNotes = it },
                        onSave = { saveCurrentDiagnostic() }
                    )
                    3 -> ResultTab(score = score, tiges = tiges.size, dendroAutoFill = dendroAutoFill)
                    4 -> HistoriqueTab(
                        diagnostics = diagnostics,
                        onDelete = { obs -> scope.launch { ripisylveRepository.delete(obs) } }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tab 0 – Dimensions
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DimensionsTab(
    gpsLat: Double?, gpsLon: Double?,
    observerName: String, onObserverChange: (String) -> Unit,
    sectionLength: String, onSectionLengthChange: (String) -> Unit,
    sectionNotes: String, onSectionNotesChange: (String) -> Unit,
    continuitePct: Float, onContinuiteChange: (Float) -> Unit,
    largeurMode: LargeurMode, onLargeurChange: (LargeurMode) -> Unit
) {
    val df = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Références ──
        RipCard(title = "Références du relevé", icon = Icons.Default.Info, color = Color(0xFF1565C0)) {
            if (gpsLat != null && gpsLon != null)
                Text("GPS : ${String.format("%.5f", gpsLat)}°N, ${String.format("%.5f", gpsLon)}°E",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2E7D32))
            else Text("GPS non disponible", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text("Date : ${df.format(Date())}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = observerName, onValueChange = onObserverChange,
                label = { Text("Observateur") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = sectionLength, onValueChange = onSectionLengthChange,
                    label = { Text("Longueur étudiée (m)") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = sectionNotes, onValueChange = onSectionNotesChange,
                label = { Text("Notes de localisation") }, modifier = Modifier.fillMaxWidth(),
                minLines = 2)
        }

        // ── C1 : Continuité ──
        RipCriterionCard(
            num = 1, title = "Continuité de la ripisylve",
            maxPts = 30, pts = RipisylveScorer.scoreContinuite(continuitePct.toDouble()),
            description = "% de linéaire couvert par les houppiers"
        ) {
            Text(RipisylveScorer.continuiteLabelForPct(continuitePct.toDouble()),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF1B5E20), fontWeight = FontWeight.SemiBold)
            Slider(value = continuitePct, onValueChange = onContinuiteChange,
                valueRange = 0f..100f, steps = 19,
                colors = SliderDefaults.colors(thumbColor = Color(0xFF2E7D32), activeTrackColor = Color(0xFF4CAF50)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0 %", style = MaterialTheme.typography.labelSmall)
                Text("${continuitePct.toInt()} %", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                Text("100 %", style = MaterialTheme.typography.labelSmall)
            }
        }

        // ── C2 : Largeur ──
        RipCriterionCard(num = 2, title = "Largeur de la ripisylve",
            maxPts = 20, pts = largeurMode.points,
            description = "Distance berge → lisière boisée"
        ) {
            LargeurMode.entries.forEach { mode ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = largeurMode == mode, onClick = { onLargeurChange(mode) })
                    Text(mode.label, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tab 1 – Structure & Biodiversité
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StructureTab(
    strateHerbacee: Boolean, onStrateHerbaceeChange: (Boolean) -> Unit,
    strateArbustive: Boolean, onStrateArbustiveChange: (Boolean) -> Unit,
    strateArborescente: Boolean, onStrateArborescente: (Boolean) -> Unit,
    nbEspeces: String, onNbEspecesChange: (String) -> Unit,
    hasTresPetitBois: Boolean, onHasTresPetitBoisChange: (Boolean) -> Unit,
    hasPetitBois: Boolean, onHasPetitBoisChange: (Boolean) -> Unit,
    hasMoyenBois: Boolean, onHasMoyenBoisChange: (Boolean) -> Unit,
    hasGrosBois: Boolean, onHasGrosBoisChange: (Boolean) -> Unit,
    dendroAutoFill: RipisylveObservation,
    microCavites: Boolean, onMicroCavitesChange: (Boolean) -> Unit,
    microFissures: Boolean, onMicroFissuresChange: (Boolean) -> Unit,
    microDecol: Boolean, onMicroDecolChange: (Boolean) -> Unit,
    microChamp: Boolean, onMicroChampChange: (Boolean) -> Unit,
    microBoisMort: Boolean, onMicroBoisMortChange: (Boolean) -> Unit,
    microTresGros: Boolean, onMicroTresGrosChange: (Boolean) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── C3 : Strates ──
        RipCriterionCard(num = 3, title = "Nombre de strates",
            maxPts = 20, pts = RipisylveScorer.scoreStrates(
                listOf(strateHerbacee, strateArbustive, strateArborescente).count { it }),
            description = "Strate présente si ≥ 25 % recouvrement"
        ) {
            RipCheckRow("Herbacée (h ≤ 70 cm)", strateHerbacee, onStrateHerbaceeChange)
            RipCheckRow("Arbustive (70 cm < h < 7 m)", strateArbustive, onStrateArbustiveChange)
            RipCheckRow("Arborescente (h ≥ 7 m)", strateArborescente, onStrateArborescente)
        }

        // ── C4 : Diversité ──
        RipCriterionCard(num = 4, title = "Diversité spécifique",
            maxPts = 10, pts = RipisylveScorer.scoreDiversite(nbEspeces.toIntOrNull() ?: 0),
            description = "Nombre d'espèces différentes (arbres et arbustes)"
        ) {
            OutlinedTextField(value = nbEspeces, onValueChange = onNbEspecesChange,
                label = { Text("Nombre d'espèces observées") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            val n = nbEspeces.toIntOrNull() ?: 0
            Text(when {
                n >= 8 -> "≥ 8 espèces → très diversifiée"
                n >= 5 -> "5–7 espèces → diversité correcte"
                else   -> "< 5 espèces → diversité insuffisante"
            }, style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF1B5E20), modifier = Modifier.padding(top = 4.dp))
        }

        // ── C5 : Classes diamètre ──
        val diamFromDendro = dendroAutoFill.diamAutoFromDendro
        RipCriterionCard(num = 5, title = "Classes de diamètre",
            maxPts = 10, pts = RipisylveScorer.scoreDiametres(
                listOf(
                    if (diamFromDendro) dendroAutoFill.hasTresPetitBois else hasTresPetitBois,
                    if (diamFromDendro) dendroAutoFill.hasPetitBois else hasPetitBois,
                    if (diamFromDendro) dendroAutoFill.hasMoyenBois else hasMoyenBois,
                    if (diamFromDendro) dendroAutoFill.hasGrosBois else hasGrosBois
                ).count { it }),
            description = if (diamFromDendro) "Auto-calculé depuis l'inventaire" else "Saisie manuelle"
        ) {
            if (diamFromDendro) {
                Surface(
                    color = Color(0xFFE8F5E9), shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Sync, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Calculé automatiquement depuis l'inventaire",
                            style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32))
                    }
                }
                RipCheckRow("Très petit bois (d ≤ 7 cm)", dendroAutoFill.hasTresPetitBois, {}, enabled = false)
                RipCheckRow("Petit bois (7 < d < 20 cm)", dendroAutoFill.hasPetitBois, {}, enabled = false)
                RipCheckRow("Moyen bois (20 ≤ d < 40 cm)", dendroAutoFill.hasMoyenBois, {}, enabled = false)
                RipCheckRow("Gros bois (d ≥ 40 cm)", dendroAutoFill.hasGrosBois, {}, enabled = false)
            } else {
                RipCheckRow("Très petit bois (d ≤ 7 cm)", hasTresPetitBois, onHasTresPetitBoisChange)
                RipCheckRow("Petit bois (7 < d < 20 cm)", hasPetitBois, onHasPetitBoisChange)
                RipCheckRow("Moyen bois (20 ≤ d < 40 cm)", hasMoyenBois, onHasMoyenBoisChange)
                RipCheckRow("Gros bois (d ≥ 40 cm)", hasGrosBois, onHasGrosBoisChange)
            }
        }

        // ── C6 : Microhabitats ──
        RipCriterionCard(num = 6, title = "Microhabitats",
            maxPts = 10, pts = RipisylveScorer.scoreMicrohabitats(
                listOf(microCavites, microFissures, microDecol, microChamp, microBoisMort, microTresGros).count { it }),
            description = "Types de microhabitats présents"
        ) {
            RipCheckRow("Cavités", microCavites, onMicroCavitesChange)
            RipCheckRow("Fissures d'écorce", microFissures, onMicroFissuresChange)
            RipCheckRow("Décollements d'écorce", microDecol, onMicroDecolChange)
            RipCheckRow("Champignons / coulées de sève", microChamp, onMicroChampChange)
            RipCheckRow("Bois mort sur pied/sol (d ≥ 30 cm)", microBoisMort, onMicroBoisMortChange)
            RipCheckRow("Très gros bois vivant (d ≥ 70 cm)", microTresGros, onMicroTresGrosChange)
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tab 2 – Menaces & Stabilité
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MenacesTab(
    sanitairePct: Float, onSanitaireChange: (Float) -> Unit,
    invasivesPct: Float, onInvasivesChange: (Float) -> Unit,
    inadapteesMode: InadapteesMode, onInadapteesChange: (InadapteesMode) -> Unit,
    stabilitePct: Float, onStabiliteChange: (Float) -> Unit,
    globalNotes: String, onGlobalNotesChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HorizontalDivider(color = Color(0xFFB71C1C).copy(alpha = 0.3f), thickness = 1.dp)
        Text("INDICATEURS NÉGATIFS (Pénalités)", style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold)

        // ── C7 : État sanitaire ──
        RipCriterionCard(num = 7, title = "État sanitaire du peuplement",
            maxPts = 0, pts = RipisylveScorer.scoreSanitaire(sanitairePct.toDouble()),
            description = "% recouvrement d'individus atteints", isNegative = true
        ) {
            Slider(value = sanitairePct, onValueChange = onSanitaireChange,
                valueRange = 0f..100f, steps = 19,
                colors = SliderDefaults.colors(thumbColor = Color(0xFFC62828), activeTrackColor = Color(0xFFEF5350)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0 %", style = MaterialTheme.typography.labelSmall)
                Text("${sanitairePct.toInt()} %", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                Text("100 %", style = MaterialTheme.typography.labelSmall)
            }
        }

        // ── C8 : Espèces invasives ──
        RipCriterionCard(num = 8, title = "Espèces exotiques envahissantes",
            maxPts = 0, pts = RipisylveScorer.scoreInvasives(invasivesPct.toDouble()),
            description = "% recouvrement en surface", isNegative = true
        ) {
            Slider(value = invasivesPct, onValueChange = onInvasivesChange,
                valueRange = 0f..100f, steps = 19,
                colors = SliderDefaults.colors(thumbColor = Color(0xFFC62828), activeTrackColor = Color(0xFFEF5350)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0 %", style = MaterialTheme.typography.labelSmall)
                Text("${invasivesPct.toInt()} %", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                Text("100 %", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(4.dp))
            Text("Ex: Balsamine du Cap, Renouée du Japon, Berce du Caucase...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // ── C9 : Espèces inadaptées ──
        RipCriterionCard(num = 9, title = "Espèces inadaptées",
            maxPts = 0, pts = inadapteesMode.points,
            description = "Conifères ou espèces ornementales inadaptées", isNegative = true
        ) {
            InadapteesMode.entries.forEach { mode ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = inadapteesMode == mode, onClick = { onInadapteesChange(mode) })
                    Text(mode.label, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // ── C10 : Stabilité ──
        RipCriterionCard(num = 10, title = "Stabilité des arbres / berges",
            maxPts = 0, pts = RipisylveScorer.scoreStabilite(stabilitePct.toDouble()),
            description = "% d'arbres menaçant de basculer / berges érodées", isNegative = true
        ) {
            Slider(value = stabilitePct, onValueChange = onStabiliteChange,
                valueRange = 0f..100f, steps = 19,
                colors = SliderDefaults.colors(thumbColor = Color(0xFFC62828), activeTrackColor = Color(0xFFEF5350)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0 %", style = MaterialTheme.typography.labelSmall)
                Text("${stabilitePct.toInt()} %", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                Text("100 %", style = MaterialTheme.typography.labelSmall)
            }
        }

        // ── Notes globales ──
        OutlinedTextField(value = globalNotes, onValueChange = onGlobalNotesChange,
            label = { Text("Notes générales et observations") }, modifier = Modifier.fillMaxWidth(), minLines = 3)

        Button(onClick = onSave, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A6B3C))) {
            Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Calculer et sauvegarder", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tab 3 – Résultat (and Auto computation recap)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ResultTab(score: RipisylveScore, tiges: Int, dendroAutoFill: RipisylveObservation) {
    val scoreAnim = remember { Animatable(0f) }
    LaunchedEffect(score.scoreTotal) {
        scoreAnim.animateTo(
            score.scoreTotal.toFloat(),
            tween(1200, easing = FastOutSlowInEasing)
        )
    }
    val animScore = scoreAnim.value.toInt()
    val fColor = Color(score.fonctionnalite.colorHex)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Score principal ──
        Box(
            Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(
                    fColor.copy(alpha = 0.15f), fColor.copy(alpha = 0.05f)))),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Canvas arc score
                Canvas(Modifier.size(160.dp)) {
                    val stroke = 18.dp.toPx()
                    val r = (size.minDimension - stroke) / 2f
                    val cx = size.width / 2f; val cy = size.height / 2f
                    val startAngle = 150f; val totalSweep = 240f
                    // Background
                    drawArc(Color.LightGray.copy(0.3f), startAngle, totalSweep, false,
                        topLeft = androidx.compose.ui.geometry.Offset(cx - r, cy - r),
                        size = Size(2 * r, 2 * r),
                        style = Stroke(stroke, cap = StrokeCap.Round))
                    // Score arc (–20 to 100 range = 120 total)
                    val scoreFraction = ((animScore + 20).coerceIn(0, 120)) / 120f
                    if (scoreFraction > 0f) {
                        drawArc(fColor, startAngle, totalSweep * scoreFraction, false,
                            topLeft = androidx.compose.ui.geometry.Offset(cx - r, cy - r),
                            size = Size(2 * r, 2 * r),
                            style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                }
                Box(Modifier.offset(y = (-84).dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$animScore", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = fColor)
                        Text("/ 100 pts", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            color = fColor, shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(score.fonctionnalite.labelFr,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                color = Color.White, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // Tableau récapitulatif
        Card(
            shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Détail des critères", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
                HorizontalDivider()
                AutoRow("1. Continuité", score.scoreContinuite, 30, false)
                AutoRow("2. Largeur", score.scoreLargeur, 20, false)
                AutoRow("3. Strates (${score.nbStrates}/3)", score.scoreStrates, 20, false)
                AutoRow("4. Diversité", score.scoreDiversite, 10, false)
                AutoRow("5. Classes diam. (${score.nbClassesDiam}/4)${if (dendroAutoFill.diamAutoFromDendro) " ⚡" else ""}", score.scoreDiametres, 10, false)
                AutoRow("6. Microhabitats (${score.nbMicrohabitats})", score.scoreMicrohabitats, 10, false)
                HorizontalDivider()
                AutoRow("7. État sanitaire", score.scoreSanitaire, -20, true)
                AutoRow("8. Espèces invasives", score.scoreInvasives, -20, true)
                AutoRow("9. Espèces inadaptées", score.scoreInadaptees, -10, true)
                AutoRow("10. Stabilité", score.scoreStabilite, -20, true)
                HorizontalDivider(thickness = 2.dp, color = Color(0xFF1A6B3C))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("SCORE TOTAL", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold)
                    Text("${score.scoreTotal} pts",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(score.fonctionnalite.colorHex))
                }
            }
        }

        if (tiges > 0) {
            Surface(
                color = Color(0xFFE8F5E9), shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sync, null, tint = Color(0xFF2E7D32))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Données dendro intégrées", fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium, color = Color(0xFF1B5E20))
                        Text("$tiges tiges de l'inventaire — classes de diamètre calculées automatiquement",
                            style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32))
                    }
                }
            }
        }

        // ── Consigne de gestion ──
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = fColor.copy(alpha = 0.12f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Forest, null, tint = fColor, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Consigne de gestion", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(score.consigneGestion.labelFr, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = fColor)
                }
            }
        }

        // ── Classes de qualité ──
        Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(2.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Classes de fonctionnalité", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
                RipisylveFonctionnalite.entries.forEach { f ->
                    val isSelected = f == score.fonctionnalite
                    val range = when (f) {
                        RipisylveFonctionnalite.TRES_MAUVAISE -> "–20 à 0"
                        RipisylveFonctionnalite.MAUVAISE -> "1 à 20"
                        RipisylveFonctionnalite.MEDIOCRE -> "21 à 40"
                        RipisylveFonctionnalite.MOYENNE -> "41 à 60"
                        RipisylveFonctionnalite.BONNE -> "61 à 80"
                        RipisylveFonctionnalite.TRES_BONNE -> "81 à 100"
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) Color(f.colorHex).copy(0.2f) else Color.Transparent)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(f.colorHex)))
                        Spacer(Modifier.width(8.dp))
                        Text(f.labelFr, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        Text(range, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // ── Synthèse rédigée ──
        Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9))) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Description, null,
                        tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Synthèse automatique", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                }
                Spacer(Modifier.height(8.dp))
                Text(score.generateSummary(), style = MaterialTheme.typography.bodySmall,
                    lineHeight = 20.sp)
            }
        }

        // ── Barre positif/négatif ──
        Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(2.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Décomposition du score", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Indicateurs positifs", style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32))
                        Text("+${score.scorePositif} pts", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Pénalités", style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFC62828))
                        Text("${score.scorePenalite} pts", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tab 4 – Historique
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HistoriqueTab(
    diagnostics: List<RipisylveObservation>,
    onDelete: (RipisylveObservation) -> Unit
) {
    val df = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
    if (diagnostics.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.History, null, modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("Aucun diagnostic sauvegardé",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(diagnostics) { idx, obs ->
            StaggerEntrance(idx) {
                val sc = RipisylveScorer.score(obs)
                val fColor = Color(sc.fonctionnalite.colorHex)
                Card(
                    shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape)
                                .background(fColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${sc.scoreTotal}", fontWeight = FontWeight.ExtraBold,
                                color = fColor, fontSize = 18.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(sc.fonctionnalite.labelFr, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium, color = fColor)
                            Text(df.format(Date(obs.observationDate)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (obs.observerName.isNotBlank())
                                Text(obs.observerName, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onDelete(obs) }) {
                            Icon(Icons.Default.Delete, null,
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoRow(label: String, pts: Int, maxPts: Int, isNegative: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        val color = if (isNegative) {
            if (pts < 0) Color(0xFFC62828) else Color(0xFF388E3C)
        } else {
            if (pts > 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text("$pts pts", style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold, color = color)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Composables helper
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RipCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.7f))))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, color = Color.White, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium)
            }
            Column(Modifier.padding(12.dp)) { content() }
        }
    }
}

@Composable
private fun RipCriterionCard(
    num: Int,
    title: String,
    maxPts: Int,
    pts: Int,
    description: String,
    isNegative: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val ptColor = when {
        isNegative && pts < 0 -> Color(0xFFC62828)
        !isNegative && pts > 0 -> Color(0xFF2E7D32)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val headerColor = if (isNegative) Color(0xFF8B0000) else Color(0xFF1A6B3C)

    Card(
        shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(headerColor, headerColor.copy(alpha = 0.6f))))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(24.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("$num", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(title, color = Color.White, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall)
                        Text(description, color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("$pts pts", color = Color.White, fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleSmall)
                    if (!isNegative) Text("/ $maxPts", color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
            Column(Modifier.padding(12.dp)) { content() }
        }
    }
}

@Composable
private fun RipCheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

