package com.forestry.counter.presentation.screens.forestry

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateColorAsState
import androidx.core.app.ActivityCompat
import com.forestry.counter.data.preferences.UserPreferencesManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.model.ripisylve.*
import com.forestry.counter.domain.model.station.DiagnosticPhoto
import com.forestry.counter.domain.repository.RipisylveRepository
import com.forestry.counter.domain.repository.TigeRepository
import com.forestry.counter.domain.usecase.export.RipisylvePdfExporter
import com.forestry.counter.domain.usecase.ripisylve.RipisylveScorer
import com.forestry.counter.presentation.components.DiagnosticPhotoCaptureSection
import com.forestry.counter.presentation.components.ExpertTutorialDialog
import com.forestry.counter.domain.usecase.florist.FloristDatabase
import com.forestry.counter.domain.usecase.florist.GradientInferenceEngine
import com.forestry.counter.domain.usecase.florist.TypeMilieu
import com.forestry.counter.presentation.components.*
import com.forestry.counter.presentation.utils.StaggerEntrance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RipisylveDiagnosticScreen(
    parcelleId: String,
    ripisylveRepository: RipisylveRepository,
    tigeRepository: TigeRepository,
    preferencesManager: UserPreferencesManager,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tiges by tigeRepository.getTigesByParcelle(parcelleId).collectAsState(initial = emptyList())
    val diagnostics by ripisylveRepository.getByParcelle(parcelleId).collectAsState(initial = emptyList())

    val tutorialCompleted by preferencesManager.ripisylveTutorialCompleted.collectAsState(initial = true)
    var showTutorial by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(tutorialCompleted) {
        if (!tutorialCompleted) {
            showTutorial = true
        }
    }


    var gpsLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var gpsLon by rememberSaveable { mutableStateOf<Double?>(null) }
    var startGpsLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var startGpsLon by rememberSaveable { mutableStateOf<Double?>(null) }
    var endGpsLat   by rememberSaveable { mutableStateOf<Double?>(null) }
    var endGpsLon   by rememberSaveable { mutableStateOf<Double?>(null) }
    var observerName by rememberSaveable { mutableStateOf("") }
    var sectionLength by rememberSaveable { mutableStateOf("50") }
    var sectionNotes by rememberSaveable { mutableStateOf("") }

    fun captureGpsTo(onResult: (Double, Double) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    loc?.let { withContext(Dispatchers.Main) { onResult(it.latitude, it.longitude) } }
                }
            } catch (_: Exception) {}
        }
    }

    val captureGps = { captureGpsTo { lat, lon -> gpsLat = lat; gpsLon = lon } }

    val tronconDistanceM: Double? = remember(startGpsLat, startGpsLon, endGpsLat, endGpsLon) {
        val sLat = startGpsLat; val sLon = startGpsLon
        val eLat = endGpsLat;   val eLon = endGpsLon
        if (sLat != null && sLon != null && eLat != null && eLon != null) {
            val r = 6371000.0
            val dLat = Math.toRadians(eLat - sLat)
            val dLon = Math.toRadians(eLon - sLon)
            val sinLat = kotlin.math.sin(dLat / 2)
            val sinLon = kotlin.math.sin(dLon / 2)
            val a = sinLat * sinLat +
                    kotlin.math.cos(Math.toRadians(sLat)) * kotlin.math.cos(Math.toRadians(eLat)) *
                    sinLon * sinLon
            r * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        } else null
    }

    LaunchedEffect(tronconDistanceM) {
        tronconDistanceM?.let { sectionLength = it.toInt().toString() }
    }

    LaunchedEffect(Unit) { captureGps() }

    var continuitePct by rememberSaveable { mutableStateOf(0f) }
    var largeurMode by rememberSaveable { mutableStateOf(LargeurMode.UNE_RANGEE) }
    var strateHerbacee by rememberSaveable { mutableStateOf(false) }
    var strateArbustive by rememberSaveable { mutableStateOf(false) }
    var strateArborescente by rememberSaveable { mutableStateOf(false) }
    var nbEspeces by rememberSaveable { mutableStateOf("0") }
    var hasTresPetitBois by rememberSaveable { mutableStateOf(false) }
    var hasPetitBois by rememberSaveable { mutableStateOf(false) }
    var hasMoyenBois by rememberSaveable { mutableStateOf(false) }
    var hasGrosBois by rememberSaveable { mutableStateOf(false) }
    var microCavites by rememberSaveable { mutableStateOf(false) }
    var microFissures by rememberSaveable { mutableStateOf(false) }
    var microDecol by rememberSaveable { mutableStateOf(false) }
    var microChamp by rememberSaveable { mutableStateOf(false) }
    var microBoisMort by rememberSaveable { mutableStateOf(false) }
    var microTresGros by rememberSaveable { mutableStateOf(false) }
    var sanitairePct by rememberSaveable { mutableStateOf(0f) }
    var invasivesPct by rememberSaveable { mutableStateOf(0f) }
    var invasivesSelectedRaw by rememberSaveable { mutableStateOf("") }
    val invasivesSelected: Set<String> = if (invasivesSelectedRaw.isBlank()) emptySet() else invasivesSelectedRaw.split("|").toSet()
    val setInvasivesSelected: (Set<String>) -> Unit = { invasivesSelectedRaw = it.joinToString("|") }
    var inadapteesMode by rememberSaveable { mutableStateOf(InadapteesMode.ABSENCE) }
    var stabilitePct by rememberSaveable { mutableStateOf(0f) }
    var globalNotes by rememberSaveable { mutableStateOf("") }
    
    var photos by remember { mutableStateOf<List<DiagnosticPhoto>>(emptyList()) }

    // ── Flore ripisylve intelligente ─────────────────────────────────────────────
    var selectedFloraIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }

    val dendroAutoFill by remember(tiges) {
        derivedStateOf {
            val obs = RipisylveObservation(parcelleId = parcelleId)
            RipisylveScorer.autoFillFromTiges(obs, tiges)
        }
    }

    val currentObs by remember(
        gpsLat, gpsLon, continuitePct, largeurMode, strateHerbacee, strateArbustive, strateArborescente,
        nbEspeces, hasTresPetitBois, hasPetitBois, hasMoyenBois, hasGrosBois, microCavites, microFissures,
        microDecol, microChamp, microBoisMort, microTresGros, sanitairePct, invasivesPct, inadapteesMode,
        stabilitePct, globalNotes, photos
    ) {
        derivedStateOf {
            RipisylveObservation(
                parcelleId = parcelleId, observerName = observerName, latitude = gpsLat, longitude = gpsLon,
                continuitePct = continuitePct.toDouble(), largeurMode = largeurMode, strateHerbacee = strateHerbacee,
                strateArbustive = strateArbustive, strateArborescente = strateArborescente,
                nbEspecesObservees = maxOf(nbEspeces.toIntOrNull() ?: 0, selectedFloraIds.size),
                especesObservees = selectedFloraIds.mapNotNull { FloristDatabase.findById(it)?.taxonomie?.nomFrancais },
                hasTresPetitBois = if (dendroAutoFill.diamAutoFromDendro) dendroAutoFill.hasTresPetitBois else hasTresPetitBois,
                hasPetitBois = if (dendroAutoFill.diamAutoFromDendro) dendroAutoFill.hasPetitBois else hasPetitBois,
                hasMoyenBois = if (dendroAutoFill.diamAutoFromDendro) dendroAutoFill.hasMoyenBois else hasMoyenBois,
                hasGrosBois = if (dendroAutoFill.diamAutoFromDendro) dendroAutoFill.hasGrosBois else hasGrosBois,
                microhabitatCavites = microCavites, microhabitatFissures = microFissures,
                microhabitatDecollementEcorce = microDecol, microhabitatChampignons = microChamp,
                microhabitatBoisMort = microBoisMort, microhabitatTresGrosBois = microTresGros,
                sanitairePct = sanitairePct.toDouble(), invasivesPct = invasivesPct.toDouble(),
                inadapteesMode = inadapteesMode, stabilitePct = stabilitePct.toDouble(), globalNotes = globalNotes,
                photos = photos
            )
        }
    }

    val score by remember(currentObs) { derivedStateOf { RipisylveScorer.score(currentObs, tiges) } }
    val snackbarHostState = remember { SnackbarHostState() }

    val exportPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching { RipisylvePdfExporter.export(context, uri, currentObs, score) }
                .onFailure { snackbarHostState.showSnackbar("Erreur export PDF") }
        }
    }
    
    val isReadyToFinalize by derivedStateOf {
        photos.size >= 3
    }

    fun saveCurrentDiagnostic(asDraft: Boolean) {
        if (!asDraft && !isReadyToFinalize) {
            scope.launch {
                snackbarHostState.showSnackbar("Impossible de finaliser : ajoutez au moins 3 photos.")
            }
            return
        }

        scope.launch {
            val now = System.currentTimeMillis()
            ripisylveRepository.save(
                currentObs.copy(
                    id = if (currentObs.id.isBlank()) UUID.randomUUID().toString() else currentObs.id,
                    parcelleId = parcelleId, observerName = observerName, observationDate = now,
                    createdAt = if (currentObs.id.isBlank()) now else currentObs.createdAt, updatedAt = now, 
                    latitude = gpsLat, longitude = gpsLon,
                    sectionLengthM = sectionLength.toDoubleOrNull() ?: 50.0,
                    sectionNotes = sectionNotes, globalNotes = globalNotes,
                    isDraft = asDraft
                )
            )
            val msg = if (asDraft) "Brouillon sauvegardé" else "Diagnostic finalisé"
            snackbarHostState.showSnackbar(msg)
        }
    }

    if (showTutorial) {
        ExpertTutorialDialog(
            title = "Diagnostic Ripisylve",
            message = "Bienvenue dans l'outil d'évaluation de l'état fonctionnel de votre ripisylve. Ce diagnostic repose sur une dizaine de critères structuraux et environnementaux.",
            bulletPoints = listOf(
                "Évaluez la continuité boisée et la largeur de la bande riveraine",
                "Renseignez la structure (strates) et la diversité spécifique",
                "Indiquez la présence de microhabitats et le profil sanitaire",
                "Prenez au moins 3 photos (amont, aval, berge) pour valider le rapport",
                "Les classes de diamètres peuvent être remplies automatiquement via les tiges inventoriées"
            ),
            icon = Icons.Default.WaterDrop,
            onDismiss = {
                showTutorial = false
                scope.launch { preferencesManager.setRipisylveTutorialCompleted(true) }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF0F4F8),
        bottomBar = {
            RipisylveBottomBar(
                onSaveDraft    = { saveCurrentDiagnostic(asDraft = true) },
                onFinalize     = { saveCurrentDiagnostic(asDraft = false) },
                onExport       = {
                    val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                    exportPdfLauncher.launch("Ripisylve_${date}.pdf")
                },
                isReadyToFinalize = isReadyToFinalize,
                photoCount     = photos.size
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 8.dp)
        ) {

            // ── Hero header ───────────────────────────────────────────────
            RipisylveHeroHeader(
                score          = score,
                gpsLat         = gpsLat,
                gpsLon         = gpsLon,
                isReadyToFinalize = isReadyToFinalize,
                onBack         = onNavigateBack
            )

            Spacer(Modifier.height(8.dp))

            // ── A. Localisation & Tronçon ──────────────────────────────────
            CollapsibleBlock(
                title = "Localisation & Tronçon étudié",
                icon = Icons.Default.Map,
                accentColor = Color(0xFF1565C0),
                initiallyExpanded = true,
                saveKey = "rip_localisation",
                badge = {
                    val hasGps = gpsLat != null
                    ConfidenceBadge(
                        if (hasGps) BadgeType.TERRAIN_OBS else BadgeType.TO_VERIFY,
                        label = if (hasGps) "GPS OK" else "GPS absent",
                        compact = true
                    )
                },
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                DimensionsTab(
                    gpsLat, gpsLon,
                    startGpsLat, startGpsLon, { captureGpsTo { lat, lon -> startGpsLat = lat; startGpsLon = lon } },
                    endGpsLat, endGpsLon,     { captureGpsTo { lat, lon -> endGpsLat   = lat; endGpsLon   = lon } },
                    tronconDistanceM,
                    observerName, { observerName = it }, sectionLength, { sectionLength = it },
                    sectionNotes, { sectionNotes = it }, continuitePct, { continuitePct = it }, largeurMode, { largeurMode = it }
                )
            }

            Spacer(Modifier.height(6.dp))

            // ── B. Structure végétale ──────────────────────────────────────
            val stratesCount = listOf(strateHerbacee, strateArbustive, strateArborescente).count { it }
            CollapsibleBlock(
                title = "Structure végétale",
                icon = Icons.Default.AccountTree,
                accentColor = Color(0xFF2E7D32),
                initiallyExpanded = true,
                saveKey = "rip_structure",
                badge = {
                    val badgeType = when {
                        stratesCount == 3 -> BadgeType.HIGH_CONFIDENCE
                        stratesCount >= 2 -> BadgeType.INFERRED
                        else              -> BadgeType.TO_VERIFY
                    }
                    ConfidenceBadge(badgeType, label = "$stratesCount strate${if (stratesCount > 1) "s" else ""}", compact = true)
                },
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                StructureTab(
                    strateHerbacee, { strateHerbacee = it }, strateArbustive, { strateArbustive = it },
                    strateArborescente, { strateArborescente = it }, nbEspeces, { nbEspeces = it },
                    hasTresPetitBois, { hasTresPetitBois = it }, hasPetitBois, { hasPetitBois = it },
                    hasMoyenBois, { hasMoyenBois = it }, hasGrosBois, { hasGrosBois = it }, dendroAutoFill,
                    microCavites, { microCavites = it }, microFissures, { microFissures = it },
                    microDecol, { microDecol = it }, microChamp, { microChamp = it }, microBoisMort, { microBoisMort = it }, microTresGros, { microTresGros = it }
                )
            }

            Spacer(Modifier.height(6.dp))

            // ── C. Flore ripisylve intelligente ─────────────────────────────
            RipisylveFloraBlock(
                selectedFloraIds = selectedFloraIds,
                onSpeciesAdded = { id -> if (id !in selectedFloraIds) selectedFloraIds = selectedFloraIds + id },
                onSpeciesRemoved = { id -> selectedFloraIds = selectedFloraIds - id },
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(Modifier.height(6.dp))

            // ── D. Pressions & Menaces ───────────────────────────────────
            val hasPressures = sanitairePct > 10f || invasivesPct > 10f ||
                inadapteesMode != InadapteesMode.ABSENCE || stabilitePct > 10f
            CollapsibleBlock(
                title = "Pressions & Menaces",
                icon = Icons.Default.Warning,
                accentColor = Color(0xFFE65100),
                initiallyExpanded = hasPressures,
                saveKey = "rip_menaces",
                badge = {
                    if (hasPressures) ConfidenceBadge(BadgeType.CONFLICT, label = "Pressions actives", compact = true)
                    else ConfidenceBadge(BadgeType.HIGH_CONFIDENCE, label = "Pas de pression", compact = true)
                },
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                MenacesTab(
                    sanitairePct, { sanitairePct = it }, invasivesPct, { invasivesPct = it },
                    invasivesSelected, setInvasivesSelected,
                    inadapteesMode, { inadapteesMode = it }, stabilitePct, { stabilitePct = it },
                    globalNotes, { globalNotes = it }
                )
            }

            Spacer(Modifier.height(6.dp))

            // ── E. Photos & Validation ───────────────────────────────────
            CollapsibleBlock(
                title = "Photos & Validation",
                icon = Icons.Default.PhotoCamera,
                accentColor = Color(0xFF6A1B9A),
                initiallyExpanded = true,
                saveKey = "rip_photos",
                badge = {
                    ConfidenceBadge(
                        type = if (photos.size >= 3) BadgeType.HIGH_CONFIDENCE else BadgeType.TO_VERIFY,
                        label = "${photos.size}/3 photos",
                        compact = true
                    )
                },
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                MediasRipisylveTab(
                    photos = photos,
                    onAddPhoto = { uri, legend, type -> photos = photos + DiagnosticPhoto(uri, legend, type) },
                    onRemovePhoto = { idx -> photos = photos.filterIndexed { i, _ -> i != idx } },
                    minPhotos = 3
                )
            }

            Spacer(Modifier.height(6.dp))

            // ── E2. Diagnostic fonctionnel ripisylve ──────────────────────
            RipisylveFunctionalDiagBlock(
                observation = currentObs,
                modifier    = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(Modifier.height(6.dp))

            // ── F. Résultats & Analyse ───────────────────────────────────
            CollapsibleBlock(
                title = "Analyse & Diagnostic",
                icon = Icons.Default.Analytics,
                accentColor = Color(0xFF00796B),
                initiallyExpanded = true,
                saveKey = "rip_results",
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                RipisylveRadarChart(score)
                Spacer(Modifier.height(8.dp))
                RipisylveCriteriaCard(score)
                Spacer(Modifier.height(8.dp))
                RipisylvePriorityActions(score)
                if (diagnostics.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    RipisylveTrendSummary(diagnostics)
                }
                if (tiges.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    if (dendroAutoFill.diamAutoFromDendro)
                        InlineAlert("Classes diamètres issues de ${tiges.size} tiges inventoriées", AlertType.INFO)
                    else
                        InlineAlert("Placette : ${tiges.size} tiges mesurées", AlertType.INFO)
                }
            }

            Spacer(Modifier.height(6.dp))

            // ── G. Historique ─────────────────────────────────────────────
            HistoriqueTab(diagnostics) { obs -> scope.launch { ripisylveRepository.delete(obs) } }
        }
    }
}

// Composants de formulaires denses
@Composable
private fun DenseFormSection(title: String, score: Int? = null, content: @Composable () -> Unit) {
    val accentBlue = Color(0xFF1565C0)
    val scoreColor = when {
        score == null -> accentBlue
        score.toFloat() / 10f >= 0.8f -> Color(0xFF2E7D32)
        score.toFloat() / 10f >= 0.5f -> Color(0xFF8BC34A)
        score.toFloat() / 10f >= 0.3f -> Color(0xFFEF6C00)
        else -> Color(0xFFC62828)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            // Top accent stripe
            Box(
                modifier = Modifier.fillMaxWidth().height(3.dp)
                    .background(Brush.horizontalGradient(listOf(accentBlue, accentBlue.copy(alpha = 0.3f))))
            )
            Row(
                Modifier.fillMaxWidth()
                    .background(accentBlue.copy(alpha = 0.05f))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = accentBlue)
                if (score != null) {
                    Surface(
                        color = scoreColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "$score pts",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = scoreColor,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            HorizontalDivider(color = accentBlue.copy(alpha = 0.08f), thickness = 0.5.dp)
            Column(modifier = Modifier.padding(12.dp)) { content() }
        }
    }
}

@Composable
private fun DenseCheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, fontSize = 12.sp)
    }
}

@Composable
private fun DenseSliderRow(label: String, value: Float, onValueChange: (Float) -> Unit, valueLabel: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall, fontSize = 12.sp)
            Text(valueLabel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = 0f..100f, modifier = Modifier.height(24.dp))
    }
}

// Onglets
@Composable
private fun DimensionsTab(
    gpsLat: Double?, gpsLon: Double?,
    startLat: Double?, startLon: Double?, onCaptureStart: () -> Unit,
    endLat: Double?,   endLon: Double?,   onCaptureEnd: () -> Unit,
    tronconDistM: Double?,
    obsName: String, onObsChange: (String) -> Unit,
    len: String, onLenChange: (String) -> Unit, notes: String, onNotesChange: (String) -> Unit,
    contPct: Float, onContChange: (Float) -> Unit, largeur: LargeurMode, onLargeurChange: (LargeurMode) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DenseFormSection("Références du relevé") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = obsName, onValueChange = onObsChange, label = { Text("Observateur", fontSize = 10.sp) }, modifier = Modifier.weight(1f), singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp))
                OutlinedTextField(value = len, onValueChange = onLenChange, label = { Text("Longueur (m)", fontSize = 10.sp) }, modifier = Modifier.weight(1f), singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp))
            }
            if (gpsLat != null && gpsLon != null) Text("GPS centre : ${String.format("%.5f", gpsLat)}°N, ${String.format("%.5f", gpsLon)}°E", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
        }

        DenseFormSection("Délimitation GPS du tronçon") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCaptureStart,
                    modifier = Modifier.weight(1f),
                    border = if (startLat != null) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF2E7D32)) else null
                ) {
                    Icon(if (startLat != null) Icons.Default.CheckCircle else Icons.Default.MyLocation,
                        null, modifier = Modifier.size(14.dp),
                        tint = if (startLat != null) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text("Début", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = onCaptureEnd,
                    modifier = Modifier.weight(1f),
                    border = if (endLat != null) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF1565C0)) else null
                ) {
                    Icon(if (endLat != null) Icons.Default.CheckCircle else Icons.Default.MyLocation,
                        null, modifier = Modifier.size(14.dp),
                        tint = if (endLat != null) Color(0xFF1565C0) else MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text("Fin", fontSize = 11.sp)
                }
            }
            if (startLat != null) Text("Début : ${String.format("%.5f", startLat)}°N, ${String.format("%.5f", startLon)}°E",
                style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
            if (endLat != null)   Text("Fin    : ${String.format("%.5f", endLat)}°N, ${String.format("%.5f", endLon)}°E",
                style = MaterialTheme.typography.labelSmall, color = Color(0xFF1565C0))
            if (tronconDistM != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)).padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Straighten, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Longueur calculée :", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
                    }
                    Text("${tronconDistM.toInt()} m", style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
            }
        }

        DenseFormSection("1. Continuité boisée", score = RipisylveScorer.scoreContinuite(contPct.toDouble())) {
            DenseSliderRow("Couverture (%)", contPct, onContChange, "${contPct.toInt()} %")
            Text(RipisylveScorer.continuiteLabelForPct(contPct.toDouble()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }

        DenseFormSection("2. Largeur de la ripisylve", score = largeur.points) {
            LargeurMode.entries.forEach { mode ->
                Row(Modifier.fillMaxWidth().clickable { onLargeurChange(mode) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = largeur == mode, onClick = { onLargeurChange(mode) }, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(mode.label, style = MaterialTheme.typography.bodySmall, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun StructureTab(
    herb: Boolean, onHerb: (Boolean) -> Unit, arbust: Boolean, onArbust: (Boolean) -> Unit, arbor: Boolean, onArbor: (Boolean) -> Unit,
    nbEspeces: String, onNbEspeces: (String) -> Unit,
    tpBois: Boolean, onTpBois: (Boolean) -> Unit, pBois: Boolean, onPBois: (Boolean) -> Unit,
    mBois: Boolean, onMBois: (Boolean) -> Unit, gBois: Boolean, onGBois: (Boolean) -> Unit,
    dendro: RipisylveObservation,
    cavites: Boolean, onCavites: (Boolean) -> Unit, fissures: Boolean, onFissures: (Boolean) -> Unit,
    decol: Boolean, onDecol: (Boolean) -> Unit, champ: Boolean, onChamp: (Boolean) -> Unit,
    bMort: Boolean, onBMort: (Boolean) -> Unit, tGBois: Boolean, onTGBois: (Boolean) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DenseFormSection("3. Strates présentes", score = RipisylveScorer.scoreStrates(listOf(herb, arbust, arbor).count { it })) {
            DenseCheckRow("Herbacée (≤ 70 cm)", herb, onHerb)
            DenseCheckRow("Arbustive (70 cm - 7 m)", arbust, onArbust)
            DenseCheckRow("Arborescente (≥ 7 m)", arbor, onArbor)
        }

        DenseFormSection("4. Diversité spécifique", score = RipisylveScorer.scoreDiversite(nbEspeces.toIntOrNull() ?: 0)) {
            OutlinedTextField(value = nbEspeces, onValueChange = onNbEspeces, label = { Text("Nb d'espèces d'arbres/arbustes", fontSize = 10.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp))
        }

        val diamScore = if (dendro.diamAutoFromDendro) RipisylveScorer.scoreDiametres(listOf(dendro.hasTresPetitBois, dendro.hasPetitBois, dendro.hasMoyenBois, dendro.hasGrosBois).count { it }) else RipisylveScorer.scoreDiametres(listOf(tpBois, pBois, mBois, gBois).count { it })
        DenseFormSection("5. Classes de diamètre" + if (dendro.diamAutoFromDendro) " (Auto)" else "", score = diamScore) {
            if (dendro.diamAutoFromDendro) {
                Text("Données dendrométriques utilisées.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                DenseCheckRow("Très petit bois (< 10 cm)", dendro.hasTresPetitBois, {})
                DenseCheckRow("Petit bois (10-25 cm)", dendro.hasPetitBois, {})
                DenseCheckRow("Bois moyen (25-45 cm)", dendro.hasMoyenBois, {})
                DenseCheckRow("Gros bois (> 45 cm)", dendro.hasGrosBois, {})
            } else {
                DenseCheckRow("Très petit bois (< 10 cm)", tpBois, onTpBois)
                DenseCheckRow("Petit bois (10-25 cm)", pBois, onPBois)
                DenseCheckRow("Bois moyen (25-45 cm)", mBois, onMBois)
                DenseCheckRow("Gros bois (> 45 cm)", gBois, onGBois)
            }
        }

        DenseFormSection("6. Microhabitats", score = RipisylveScorer.scoreMicrohabitats(listOf(cavites, fissures, decol, champ, bMort, tGBois).count { it })) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    DenseCheckRow("Cavités", cavites, onCavites)
                    DenseCheckRow("Fissures", fissures, onFissures)
                    DenseCheckRow("Décollements", decol, onDecol)
                }
                Column(Modifier.weight(1f)) {
                    DenseCheckRow("Champignons", champ, onChamp)
                    DenseCheckRow("Bois mort", bMort, onBMort)
                    DenseCheckRow("Très gros bois", tGBois, onTGBois)
                }
            }
        }
    }
}

private val INVASIVES_RIPISYLVES = listOf(
    "Renouée du Japon"          to "Reynoutria japonica",
    "Balsamine de l'Himalaya"   to "Impatiens glandulifera",
    "Buddleia de David"         to "Buddleja davidii",
    "Robinier faux-acacia"      to "Robinia pseudoacacia",
    "Ailante glanduleux"        to "Ailanthus altissima",
    "Jussie"                    to "Ludwigia grandiflora",
    "Séneçon du Cap"            to "Senecio inaequidens",
    "Solidage géant"            to "Solidago gigantea",
    "Myriophylle du Brésil"     to "Myriophyllum aquaticum",
    "Arbre aux papillons"       to "Buddleja davidii"
)

@Composable
private fun MenacesTab(
    sanitaire: Float, onSanitaire: (Float) -> Unit,
    invasives: Float, onInvasives: (Float) -> Unit,
    invasivesSelected: Set<String>, onInvasivesSelected: (Set<String>) -> Unit,
    inadaptees: InadapteesMode, onInadaptees: (InadapteesMode) -> Unit,
    stabilite: Float, onStabilite: (Float) -> Unit,
    notes: String, onNotes: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DenseFormSection("7. État sanitaire", score = RipisylveScorer.scoreSanitaire(sanitaire.toDouble())) {
            DenseSliderRow("% d'arbres dépérissants", sanitaire, onSanitaire, "${sanitaire.toInt()} %")
        }

        DenseFormSection("8. Espèces invasives", score = RipisylveScorer.scoreInvasives(invasives.toDouble())) {
            DenseSliderRow("% recouvrement invasives", invasives, onInvasives, "${invasives.toInt()} %")
            Spacer(Modifier.height(6.dp))
            Text("Espèces identifiées :", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            val suggestedPct = (invasivesSelected.size * 12f).coerceAtMost(100f)
            INVASIVES_RIPISYLVES.forEach { (nameFr, nameScient) ->
                val checked = nameFr in invasivesSelected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val updated = if (checked) invasivesSelected - nameFr else invasivesSelected + nameFr
                            onInvasivesSelected(updated)
                            onInvasives(((updated.size * 12f).coerceAtMost(100f)))
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.size(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(nameFr, style = MaterialTheme.typography.bodySmall, fontSize = 12.sp)
                        Text(nameScient, style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    }
                    if (checked) Surface(color = Color(0xFFC62828).copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
                        Text("Présente", style = MaterialTheme.typography.labelSmall, color = Color(0xFFC62828),
                            fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
            if (invasivesSelected.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFEF6C00).copy(alpha = 0.08f)).padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${invasivesSelected.size} esp. détectées → % suggéré :",
                        style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
                    Text("${suggestedPct.toInt()} %", style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold, color = Color(0xFFEF6C00), fontSize = 11.sp)
                }
            }
        }

        DenseFormSection("9. Espèces inadaptées", score = inadaptees.points) {
            InadapteesMode.entries.forEach { mode ->
                Row(Modifier.fillMaxWidth().clickable { onInadaptees(mode) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = inadaptees == mode, onClick = { onInadaptees(mode) }, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(mode.label, style = MaterialTheme.typography.bodySmall, fontSize = 12.sp)
                }
            }
        }

        DenseFormSection("10. Stabilité des berges", score = RipisylveScorer.scoreStabilite(stabilite.toDouble())) {
            DenseSliderRow("% berges érodées", stabilite, onStabilite, "${stabilite.toInt()} %")
        }

        DenseFormSection("Notes") {
            OutlinedTextField(value = notes, onValueChange = onNotes, modifier = Modifier.fillMaxWidth(), minLines = 2, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp))
        }

    }
}

@Composable
private fun RipisylveScoreHeader(score: RipisylveScore) {
    val color = Color(score.fonctionnalite.colorHex)
    val animScore by animateIntAsState(score.scoreTotal.coerceAtLeast(0), animationSpec = tween(1200), label = "rip_score")
    val animFrac by animateFloatAsState(score.scoreTotal.coerceAtLeast(0) / 100f, tween(1200), label = "rip_frac")
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.18f), color.copy(alpha = 0.03f))))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "DIAGNOSTIC RIPISYLVE",
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.7f),
                fontWeight = FontWeight.ExtraBold
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                Text("$animScore", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold, color = color)
                Text(" / 100", style = MaterialTheme.typography.titleLarge, color = color.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 6.dp))
            }
            Text(score.fonctionnalite.labelFr.uppercase(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
            Box(
                Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.15f))
            ) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(animFrac).clip(RoundedCornerShape(4.dp))
                        .background(Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.55f))))
                )
            }
        }
    }
}

@Composable
private fun RipisylveCriteriaCard(score: RipisylveScore) {
    val criteria = listOf(
        Triple("Continuité boisée",        score.scoreContinuite,    10),
        Triple("Largeur ripisylve",         score.scoreLargeur,      20),
        Triple("Strates végétales",         score.scoreStrates,      10),
        Triple("Diversité spécifique",      score.scoreDiversite,    10),
        Triple("Classes diamètres",          score.scoreDiametres,    10),
        Triple("Microhabitats",             score.scoreMicrohabitats, 10),
        Triple("État sanitaire",            score.scoreSanitaire,    10),
        Triple("Espèces invasives",          score.scoreInvasives,    10),
        Triple("Stabilité des berges",       score.scoreStabilite,    10),
    )
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Détail des critères", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            HorizontalDivider()
            criteria.forEachIndexed { idx, (label, pts, max) ->
                val fraction = (pts.coerceAtLeast(0).toFloat() / max.toFloat()).coerceIn(0f, 1f)
                val animFrac by animateFloatAsState(fraction, tween(700, delayMillis = idx * 50), label = "rc_$idx")
                val barColor = when {
                    fraction >= 0.8f -> Color(0xFF2E7D32)
                    fraction >= 0.5f -> Color(0xFF8BC34A)
                    fraction >= 0.3f -> Color(0xFFEF6C00)
                    else             -> Color(0xFFC62828)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(130.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(
                        Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(animFrac).clip(RoundedCornerShape(5.dp)).background(barColor))
                    }
                    Text("$pts/$max", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = barColor, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
                }
            }
            if (score.scoreInadaptees < 0) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Inadaptées (malus)", style = MaterialTheme.typography.bodySmall, color = Color(0xFFC62828), modifier = Modifier.width(130.dp))
                    Box(Modifier.weight(1f))
                    Text("${score.scoreInadaptees}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFC62828), modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
                }
            }
        }
    }
}

@Composable
private fun RipisylvePriorityActions(score: RipisylveScore) {
    data class RipAction(val label: String, val gainMax: Int, val advice: String)
    val actions = buildList {
        if (score.scoreLargeur   < 16) add(RipAction("Largeur ripisylve",      20 - score.scoreLargeur,      "Planter des deux côtés pour élargir le corridor"))
        if (score.scoreContinuite < 8) add(RipAction("Continuité boisée",       10 - score.scoreContinuite,   "Replanter les trouées pour restaurer la continuité"))
        if (score.scoreMicrohabitats < 8) add(RipAction("Microhabitats",         10 - score.scoreMicrohabitats,"Conserver bois mort, cavités et très gros bois"))
        if (score.scoreInadaptees < 0)    add(RipAction("Esp. inadaptées",      -score.scoreInadaptees,       "Supprimer progressivement les espèces inadaptées"))
        if (score.scoreInvasives < 8)     add(RipAction("Esp. invasives",         10 - score.scoreInvasives,    "Mettre en place un plan de lutte contre les invasives"))
        if (score.scoreStabilite < 8)     add(RipAction("Stabilité des berges",  10 - score.scoreStabilite,    "Restaurer par génie végétal et enracinement"))
        if (score.scoreStrates   < 8)     add(RipAction("Strates végétales",     10 - score.scoreStrates,      "Favoriser toutes les strates (herbe, arbustes, arbres)"))
        if (score.scoreDiversite < 8)     add(RipAction("Diversité spécifique",  10 - score.scoreDiversite,    "Augmenter la diversité d'espèces autochtones"))
    }.sortedByDescending { it.gainMax }.take(4)

    if (actions.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2E7D32).copy(alpha = 0.1f))
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(32.dp))
                Column {
                    Text("Ripisylve fonctionnelle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    Text("Maintenir les pratiques de gestion actuelles", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        return
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Assignment, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text("Actions prioritaires", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider()
            actions.forEach { action ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(6.dp)) {
                        Text("+${action.gainMax} pts", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(action.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Text(action.advice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun RipisylveTrendSummary(diagnostics: List<RipisylveObservation>) {
    if (diagnostics.size < 2) return
    val df = SimpleDateFormat("MMM yy", Locale.FRANCE)
    val sorted    = remember(diagnostics) { diagnostics.sortedBy { it.observationDate } }
    val last      = sorted.last()
    val prev      = sorted[sorted.size - 2]
    val lastScore = remember(last.id)  { RipisylveScorer.score(last, emptyList()).scoreTotal }
    val prevScore = remember(prev.id)  { RipisylveScorer.score(prev, emptyList()).scoreTotal }
    val delta = lastScore - prevScore
    val trendColor = if (delta > 0) Color(0xFF2E7D32) else if (delta < 0) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant
    val trendIcon  = if (delta > 0) Icons.Default.TrendingUp else if (delta < 0) Icons.Default.TrendingDown else Icons.Default.Check
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.TrendingUp, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text("Évolution sur ${sorted.size} diagnostics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Précédent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(df.format(Date(prev.observationDate)), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                    Text("$prevScore / 100", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(trendIcon, null, tint = trendColor, modifier = Modifier.size(28.dp))
                    Text(if (delta > 0) "+$delta" else "$delta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = trendColor)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Dernier", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(df.format(Date(last.observationDate)), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                    Text("$lastScore / 100", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MediasRipisylveTab(
    photos: List<DiagnosticPhoto>,
    onAddPhoto: (String, String, String) -> Unit,
    onRemovePhoto: (Int) -> Unit,
    minPhotos: Int
) {
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Validation du rapport", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Au moins $minPhotos photos sont requises pour finaliser (ex: amont, aval, berge). ${photos.size}/$minPhotos ajoutées.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        DiagnosticPhotoCaptureSection(
            photos = photos,
            onAddPhoto = onAddPhoto,
            onRemovePhoto = onRemovePhoto,
            minPhotos = minPhotos,
            photoTypeOptions = listOf("Amont", "Aval", "Berge", "Détail floristique", "Vue d'ensemble")
        )
    }
}

@Composable
private fun ResultTab(score: RipisylveScore, tigesCount: Int, dendroAutoFill: RipisylveObservation, diagnostics: List<RipisylveObservation>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { StaggerEntrance(0, staggerMs = 70) { RipisylveScoreHeader(score) } }
        item { StaggerEntrance(1, staggerMs = 70) { RipisylveRadarChart(score) } }
        item { StaggerEntrance(2, staggerMs = 70) { RipisylveCriteriaCard(score) } }
        item { StaggerEntrance(3, staggerMs = 70) { RipisylvePriorityActions(score) } }
        if (diagnostics.size > 1) {
            item { StaggerEntrance(3, staggerMs = 70) { RipisylveTrendSummary(diagnostics) } }
        }
        if (tigesCount > 0) {
            item {
                StaggerEntrance(4, staggerMs = 70) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text(
                                if (dendroAutoFill.diamAutoFromDendro)
                                    "Classes diamètres issues de $tigesCount tiges inventoriées"
                                else
                                    "Placette : $tigesCount tiges mesurées",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoriqueTab(diagnostics: List<RipisylveObservation>, onDelete: (RipisylveObservation) -> Unit) {
    if (diagnostics.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val df = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("Historique (${diagnostics.size} diagnostic${if (diagnostics.size > 1) "s" else ""})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    diagnostics.sortedByDescending { it.observationDate }.forEach { obs ->
                        val obsScore = remember(obs.id) { RipisylveScorer.score(obs, emptyList()) }
                        val scoreColor = Color(obsScore.fonctionnalite.colorHex)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(df.format(Date(obs.observationDate)), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text("Par : ${obs.observerName.ifBlank { "Inconnu" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(obsScore.fonctionnalite.labelFr, style = MaterialTheme.typography.bodySmall, color = scoreColor, fontWeight = FontWeight.Medium)
                                    if (!obs.globalNotes.isNullOrBlank()) {
                                        Text(obs.globalNotes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Surface(color = scoreColor.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp)) {
                                        Text("${obsScore.scoreTotal}/100", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = scoreColor, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                    }
                                    IconButton(onClick = { onDelete(obs) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Delete, "Supprimer", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  RipisylveHeroHeader — En-tête hero avec gradient bleu rivière + score live
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun RipisylveHeroHeader(
    score: RipisylveScore,
    gpsLat: Double?,
    gpsLon: Double?,
    isReadyToFinalize: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = Color(score.fonctionnalite.colorHex)
    val animScore by animateIntAsState(score.scoreTotal.coerceAtLeast(0), tween(1200), label = "hero_score")
    val animFrac  by animateFloatAsState(score.scoreTotal.coerceAtLeast(0) / 100f, tween(1200), label = "hero_frac")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0xFF0D2137),
                        0.55f to Color(0xFF0D47A1).copy(alpha = 0.95f),
                        1.0f to Color(0xFF1976D2).copy(alpha = 0.80f)
                    )
                )
            )
    ) {
        // Wave decoration
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .align(Alignment.BottomCenter)
        ) {
            val w = size.width; val h = size.height
            val wave = Path().apply {
                moveTo(0f, h * 0.72f)
                cubicTo(w * 0.25f, h * 0.58f, w * 0.50f, h * 0.85f, w * 0.75f, h * 0.68f)
                cubicTo(w * 0.88f, h * 0.60f, w * 0.94f, h * 0.65f, w, h * 0.62f)
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(wave, color = Color.White.copy(alpha = 0.05f))
            val wave2 = Path().apply {
                moveTo(0f, h * 0.82f)
                cubicTo(w * 0.30f, h * 0.70f, w * 0.60f, h * 0.92f, w * 0.85f, h * 0.76f)
                cubicTo(w * 0.93f, h * 0.72f, w * 0.97f, h * 0.78f, w, h * 0.76f)
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(wave2, color = Color.White.copy(alpha = 0.04f))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            // Navigation row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Retour",
                        tint = Color.White
                    )
                }
                Spacer(Modifier.weight(1f))
                // Completion indicator
                Surface(
                    color = if (isReadyToFinalize) Color(0xFF2E7D32).copy(alpha = 0.85f)
                            else Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            if (isReadyToFinalize) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            null, tint = Color.White, modifier = Modifier.size(14.dp)
                        )
                        Text(
                            if (isReadyToFinalize) "Prêt" else "En cours",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Score central
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "DIAGNOSTIC RIPISYLVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.60f),
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "$animScore",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor
                    )
                    Text(
                        " / 100",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Fonctionnalité label pill
                Surface(
                    color = accentColor.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        score.fonctionnalite.labelFr.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                        letterSpacing = 1.sp
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Progress bar
                Box(
                    Modifier
                        .fillMaxWidth(0.80f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animFrac)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(listOf(accentColor, accentColor.copy(0.6f)))
                            )
                    )
                }

                Spacer(Modifier.height(10.dp))

                // GPS row
                if (gpsLat != null && gpsLon != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            Icons.Default.LocationOn, null,
                            tint = Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            "%.4f°N  %.4f°E".format(gpsLat, gpsLon),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 11.sp
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            Icons.Default.LocationOff, null,
                            tint = Color(0xFFEF9A9A),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            "GPS non capturé",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFEF9A9A),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  RipisylveBottomBar — Barre d'actions sticky (Brouillon / Finaliser / PDF)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun RipisylveBottomBar(
    onSaveDraft: () -> Unit,
    onFinalize: () -> Unit,
    onExport: () -> Unit,
    isReadyToFinalize: Boolean,
    photoCount: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 12.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Completion progress row
            if (!isReadyToFinalize) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFFF8E1))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.PhotoCamera, null, tint = Color(0xFFE65100), modifier = Modifier.size(14.dp))
                    Text(
                        "$photoCount / 3 photos requises pour finaliser",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE65100)
                    )
                }
            }
            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // PDF export (icon button)
                OutlinedButton(
                    onClick = onExport,
                    modifier = Modifier.size(48.dp),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(20.dp))
                }
                // Brouillon
                OutlinedButton(
                    onClick = onSaveDraft,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Brouillon")
                }
                // Finaliser
                Button(
                    onClick = onFinalize,
                    modifier = Modifier.weight(1.4f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isReadyToFinalize) Color(0xFF2E7D32) else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor   = if (isReadyToFinalize) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        if (isReadyToFinalize) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        null, modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Finaliser", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Graphique radar des 9 critères ripisylve
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RipisylveRadarChart(score: RipisylveScore) {
    data class RadarAxis(val label: String, val pts: Int, val max: Int)
    val axes = listOf(
        RadarAxis("Continuité",  score.scoreContinuite.coerceAtLeast(0),    10),
        RadarAxis("Largeur",     score.scoreLargeur.coerceAtLeast(0),        20),
        RadarAxis("Strates",     score.scoreStrates.coerceAtLeast(0),        10),
        RadarAxis("Diversité",   score.scoreDiversite.coerceAtLeast(0),      10),
        RadarAxis("Diamètres",   score.scoreDiametres.coerceAtLeast(0),      10),
        RadarAxis("Microhab.",   score.scoreMicrohabitats.coerceAtLeast(0),  10),
        RadarAxis("Sanitaire",   score.scoreSanitaire.coerceAtLeast(0),      10),
        RadarAxis("Invasives",   score.scoreInvasives.coerceAtLeast(0),      10),
        RadarAxis("Stabilité",   score.scoreStabilite.coerceAtLeast(0),      10),
    )
    val rawFracs = axes.map { (it.pts.toFloat() / it.max.toFloat()).coerceIn(0f, 1f) }

    val f0 by animateFloatAsState(rawFracs[0], tween(900, 0),   label = "r0")
    val f1 by animateFloatAsState(rawFracs[1], tween(900, 50),  label = "r1")
    val f2 by animateFloatAsState(rawFracs[2], tween(900, 100), label = "r2")
    val f3 by animateFloatAsState(rawFracs[3], tween(900, 150), label = "r3")
    val f4 by animateFloatAsState(rawFracs[4], tween(900, 200), label = "r4")
    val f5 by animateFloatAsState(rawFracs[5], tween(900, 250), label = "r5")
    val f6 by animateFloatAsState(rawFracs[6], tween(900, 300), label = "r6")
    val f7 by animateFloatAsState(rawFracs[7], tween(900, 350), label = "r7")
    val f8 by animateFloatAsState(rawFracs[8], tween(900, 400), label = "r8")
    val animFracs = listOf(f0, f1, f2, f3, f4, f5, f6, f7, f8)

    val radarColor = Color(score.fonctionnalite.colorHex)
    val n = axes.size

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.TrackChanges, null, tint = radarColor, modifier = Modifier.size(20.dp))
                Text("Vue radar — 9 critères", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider()

            Canvas(modifier = Modifier.fillMaxWidth().height(230.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val maxR = minOf(cx, cy) * 0.72f
                val angleStep = (2.0 * kotlin.math.PI / n).toFloat()
                val startAngle = (-kotlin.math.PI / 2.0).toFloat()

                fun angleAt(i: Int) = startAngle + i * angleStep
                fun ptX(i: Int, r: Float) = cx + r * kotlin.math.cos(angleAt(i).toDouble()).toFloat()
                fun ptY(i: Int, r: Float) = cy + r * kotlin.math.sin(angleAt(i).toDouble()).toFloat()

                // Background rings (3 levels)
                for (ring in 1..3) {
                    val r = maxR * (ring / 3f)
                    val gridPath = Path()
                    for (i in 0 until n) {
                        if (i == 0) gridPath.moveTo(ptX(i, r), ptY(i, r))
                        else gridPath.lineTo(ptX(i, r), ptY(i, r))
                    }
                    gridPath.close()
                    drawPath(gridPath, color = radarColor.copy(alpha = if (ring == 3) 0.07f else 0.04f))
                    drawPath(gridPath, color = radarColor.copy(alpha = 0.2f),
                        style = Stroke(width = if (ring == 3) 1.5f else 0.8f))
                }

                // Radial axes
                for (i in 0 until n) {
                    drawLine(
                        color = radarColor.copy(alpha = 0.22f),
                        start = Offset(cx, cy),
                        end   = Offset(ptX(i, maxR), ptY(i, maxR)),
                        strokeWidth = 1f
                    )
                }

                // Filled data polygon
                val dataPath = Path()
                animFracs.forEachIndexed { i, frac ->
                    val r = maxR * frac
                    if (i == 0) dataPath.moveTo(ptX(i, r), ptY(i, r))
                    else        dataPath.lineTo(ptX(i, r), ptY(i, r))
                }
                dataPath.close()
                drawPath(dataPath, color = radarColor.copy(alpha = 0.25f))
                drawPath(dataPath, color = radarColor,
                    style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                // Data points
                animFracs.forEachIndexed { i, frac ->
                    val r = maxR * frac
                    val pt = Offset(ptX(i, r), ptY(i, r))
                    drawCircle(color = radarColor, radius = 5f, center = pt)
                    drawCircle(color = Color.White, radius = 2.5f, center = pt)
                }
            }

            // Legend grid 3×3
            axes.chunked(3).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEach { axis ->
                        val frac = (axis.pts.toFloat() / axis.max).coerceIn(0f, 1f)
                        val c = when {
                            frac >= 0.8f -> Color(0xFF2E7D32)
                            frac >= 0.5f -> Color(0xFF8BC34A)
                            frac >= 0.3f -> Color(0xFFEF6C00)
                            else         -> Color(0xFFC62828)
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(axis.label, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp,
                                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${axis.pts}/${axis.max}", style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold, color = c, fontSize = 9.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  RipisylveFloraBlock — Saisie floristique intelligente (milieux humides)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun RipisylveFloraBlock(
    selectedFloraIds: List<String>,
    onSpeciesAdded: (String) -> Unit,
    onSpeciesRemoved: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val hygrophyteCount = remember(selectedFloraIds) {
        selectedFloraIds.count { id ->
            FloristDatabase.findById(id)?.valeurIndicatrice?.indicateurHydromorphie == true
        }
    }
    val invasiveCount = remember(selectedFloraIds) {
        selectedFloraIds.count { id ->
            FloristDatabase.findById(id)?.valeurIndicatrice?.indicateurPerturbation == true
        }
    }

    val badge: @Composable () -> Unit = {
        val badgeType = when {
            selectedFloraIds.size >= 5 -> BadgeType.HIGH_CONFIDENCE
            selectedFloraIds.size >= 2 -> BadgeType.INFERRED
            else                       -> BadgeType.INSUFFICIENT
        }
        ConfidenceBadge(badgeType, label = "${selectedFloraIds.size} esp.", compact = true)
    }

    CollapsibleBlock(
        title = "Flore ripisylve",
        icon = Icons.Default.WaterDrop,
        accentColor = Color(0xFF1565C0),
        initiallyExpanded = true,
        saveKey = "rip_flora",
        badge = badge,
        modifier = modifier
    ) {
        InlineAlert(
            "Saisissez les espèces de la bande riveraine — hygrophytes et invasives auto-détectées",
            AlertType.INFO
        )

        SmartPlantInputField(
            selectedSpeciesIds = selectedFloraIds,
            onSpeciesAdded = onSpeciesAdded,
            onSpeciesRemoved = onSpeciesRemoved,
            contextMilieu = TypeMilieu.ZONE_HUMIDE,
            contextIds = selectedFloraIds,
            placeholder = "Ex: aulne, saule, reine des prés, renouée…"
        )

        if (hygrophyteCount > 0 || invasiveCount > 0) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hygrophyteCount > 0) {
                    ConfidenceBadge(
                        BadgeType.TERRAIN_OBS,
                        label = "$hygrophyteCount hygrophyte${if (hygrophyteCount > 1) "s" else ""}",
                        modifier = Modifier
                    )
                }
                if (invasiveCount > 0) {
                    ConfidenceBadge(
                        BadgeType.CONFLICT,
                        label = "$invasiveCount indicateur${if (invasiveCount > 1) "s" else ""} perturbation",
                        modifier = Modifier
                    )
                }
            }
        }

        if (hygrophyteCount >= 3) {
            InlineAlert(
                "Cortège hygrophile marqué ($hygrophyteCount espèces) → fort gradient hydrique probable",
                AlertType.INFO
            )
        }
        if (invasiveCount >= 2) {
            InlineAlert(
                "⚠ Plusieurs indicateurs de perturbation — vérifier pressions anthropiques en amont",
                AlertType.WARNING
            )
        }
    }
}
