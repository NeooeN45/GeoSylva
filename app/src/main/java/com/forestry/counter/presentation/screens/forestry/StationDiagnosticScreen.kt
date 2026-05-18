package com.forestry.counter.presentation.screens.forestry

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.forestry.counter.data.preferences.UserPreferencesManager
import com.forestry.counter.domain.model.station.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.forestry.counter.domain.repository.StationRepository
import com.forestry.counter.domain.usecase.autecology.AutecologyDatabase
import com.forestry.counter.domain.usecase.autecology.CompatibilityLevel
import com.forestry.counter.domain.usecase.export.StationPdfExporter
import com.forestry.counter.domain.usecase.station.StationDiagnosticEngine
import com.forestry.counter.presentation.components.DiagnosticPhotoCaptureSection
import com.forestry.counter.presentation.components.ExpertTutorialDialog
import com.forestry.counter.domain.model.ClimateZone
import com.forestry.counter.domain.usecase.correlateur.CorrelationEngine
import com.forestry.counter.domain.usecase.autecology.DRIASDatabase
import com.forestry.counter.domain.usecase.florist.FloristDatabase
import com.forestry.counter.domain.usecase.florist.GradientInferenceEngine
import com.forestry.counter.domain.usecase.florist.TypeMilieu
import com.forestry.counter.presentation.components.*
import com.forestry.counter.presentation.utils.StaggerEntrance
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationDiagnosticScreen(
    parcelleId: String,
    stationRepository: StationRepository,
    preferencesManager: UserPreferencesManager,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val diagnostics by stationRepository.getByParcelle(parcelleId).collectAsState(initial = emptyList())

    val tutorialCompleted by preferencesManager.stationTutorialCompleted.collectAsState(initial = true)
    var showTutorial by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(tutorialCompleted) {
        if (!tutorialCompleted) {
            showTutorial = true
        }
    }


    var gpsLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var gpsLon by rememberSaveable { mutableStateOf<Double?>(null) }
    var altitudeM by rememberSaveable { mutableStateOf("0") }
    var commune by rememberSaveable { mutableStateOf("") }

    var pentePct by rememberSaveable { mutableStateOf("0") }
    var exposition by rememberSaveable { mutableStateOf(Exposition.N) }
    var positionTopo by rememberSaveable { mutableStateOf(PositionTopo.MI_VERSANT) }
    var distanceCours by rememberSaveable { mutableStateOf("") }

    var profondeurCm by rememberSaveable { mutableStateOf("50") }
    var texture by rememberSaveable { mutableStateOf(TextureSol.LIMONEUSE) }
    var pierrosite by rememberSaveable { mutableStateOf(Pierrosite.FAIBLE) }
    var hydromorphieCm by rememberSaveable { mutableStateOf("") }
    var humus by rememberSaveable { mutableStateOf(TypeHumus.MULL) }
    var phEstime by rememberSaveable { mutableStateOf("6.0") }
    var testHcl by rememberSaveable { mutableStateOf(TestHCl.NEGATIF) }
    var drainage by rememberSaveable { mutableStateOf(Drainage.BON) }
    var rocheMere by rememberSaveable { mutableStateOf("") }

    var gradientHydrique by rememberSaveable { mutableStateOf(3f) }
    var gradientTrophique by rememberSaveable { mutableStateOf(3f) }
    var gradientLumineux by rememberSaveable { mutableStateOf(3f) }
    var gradientHumique by rememberSaveable { mutableStateOf(3f) }

    var especesIndicatrices by rememberSaveable { mutableStateOf("") }
    var notesLibres by rememberSaveable { mutableStateOf("") }

    var espXerophiles by rememberSaveable { mutableStateOf(false) }
    var espMesophiles by rememberSaveable { mutableStateOf(false) }
    var espHygrophiles by rememberSaveable { mutableStateOf(false) }

    var photos by remember { mutableStateOf<List<DiagnosticPhoto>>(emptyList()) }

    // ── Flore intelligente ────────────────────────────────────────────────────
    var selectedFloraIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    val gradientInference by remember(selectedFloraIds, gradientHydrique, gradientTrophique) {
        derivedStateOf {
            if (selectedFloraIds.size >= 2)
                GradientInferenceEngine.computeGradients(
                    selectedFloraIds,
                    contextHydrique = gradientHydrique.toInt(),
                    contextTrophique = gradientTrophique.toInt()
                )
            else null
        }
    }

    val captureGps = {
        scope.launch(Dispatchers.IO) {
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    loc?.let {
                        gpsLat = it.latitude
                        gpsLon = it.longitude
                        if (it.hasAltitude()) altitudeM = it.altitude.toInt().toString()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) { captureGps() }

    val currentStation by remember(
        gpsLat, gpsLon, altitudeM, commune, pentePct, exposition, positionTopo, distanceCours,
        profondeurCm, texture, pierrosite, hydromorphieCm, humus, phEstime, testHcl, drainage, rocheMere,
        gradientHydrique, gradientTrophique, gradientLumineux, gradientHumique,
        especesIndicatrices, espXerophiles, espMesophiles, espHygrophiles, notesLibres, photos
    ) {
        derivedStateOf {
            StationObservation(
                parcelleId = parcelleId,
                latitude = gpsLat, longitude = gpsLon, altitudeM = altitudeM.toDoubleOrNull(), commune = commune,
                pentePct = pentePct.toDoubleOrNull(), exposition = exposition, positionTopo = positionTopo, distanceCourseauM = distanceCours.toDoubleOrNull(),
                profondeurSolCm = profondeurCm.toIntOrNull(), texture = texture, pierrosite = pierrosite,
                hydromorphieProfondeurCm = hydromorphieCm.toIntOrNull(), humus = humus, phEstime = phEstime.toDoubleOrNull(), testHcl = testHcl,
                drainage = drainage, rocheMere = rocheMere,
                gradientHydrique = gradientHydrique.toInt(), gradientTrophique = gradientTrophique.toInt(), gradientLumineux = gradientLumineux.toInt(), gradientHumique = gradientHumique.toInt(),
                especesIndicatrices = (selectedFloraIds.mapNotNull { id ->
                        FloristDatabase.findById(id)?.taxonomie?.nomFrancais
                    } + especesIndicatrices.split(",").map { it.trim() }.filter { it.isNotEmpty() }).distinct(),
                especesXerophiles = espXerophiles, especesMesophiles = espMesophiles, especesHygrophiles = espHygrophiles,
                notes = notesLibres,
                photos = photos
            )
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    val exportPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching { StationPdfExporter.export(context, uri, currentStation) }
                .onFailure { snackbarHostState.showSnackbar("Erreur export PDF") }
        }
    }

    val isReadyToFinalize by derivedStateOf {
        profondeurCm.isNotBlank() && texture != TextureSol.INCONNUE && photos.size >= 2
    }

    fun saveDiagnostic(asDraft: Boolean) {
        if (!asDraft && !isReadyToFinalize) {
            scope.launch {
                snackbarHostState.showSnackbar("Impossible de finaliser : complétez le sol (prof/texture) et ajoutez min 2 photos.")
            }
            return
        }

        scope.launch {
            val now = System.currentTimeMillis()
            stationRepository.save(
                currentStation.copy(
                    id = if (currentStation.id.isBlank()) UUID.randomUUID().toString() else currentStation.id,
                    parcelleId = parcelleId,
                    observationDate = now,
                    isDraft = asDraft
                )
            )
            val msg = if (asDraft) "Brouillon sauvegardé" else "Diagnostic finalisé"
            snackbarHostState.showSnackbar(msg)
        }
    }

    if (showTutorial) {
        ExpertTutorialDialog(
            title = "Diagnostic de Station",
            message = "Bienvenue dans l'outil de diagnostic de station forestière. Cet outil vous permet de qualifier finement les conditions écologiques de votre parcelle.",
            bulletPoints = listOf(
                "Saisissez les caractéristiques topographiques et pédologiques",
                "Estimez les gradients hydrique et trophique de la station",
                "Ajoutez des photos justificatives (minimum 2 pour finaliser)",
                "Sauvegardez en brouillon ou finalisez pour obtenir des recommandations d'essences",
                "L'engine expert analysera la compatibilité d'une trentaine d'essences avec vos relevés"
            ),
            icon = Icons.Default.Terrain,
            onDismiss = {
                showTutorial = false
                scope.launch { preferencesManager.setStationTutorialCompleted(true) }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Relevé de Station", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = {
                        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                        exportPdfLauncher.launch("Station_${date}.pdf")
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Exporter PDF")
                    }
                    TextButton(onClick = { saveDiagnostic(asDraft = true) }) {
                        Text("Brouillon")
                    }
                    Button(
                        onClick = { saveDiagnostic(asDraft = false) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isReadyToFinalize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isReadyToFinalize) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("Finaliser")
                        if (!isReadyToFinalize) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.Warning, contentDescription = "Incomplet", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(bottom = 32.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ── En-tête score live ────────────────────────────────────────
            StationScoreHeader(currentStation)

            Spacer(Modifier.height(4.dp))

            // ── A. Contexte auto-déduit ───────────────────────────────────
            AutoContextBlock(
                gpsLat = gpsLat, gpsLon = gpsLon,
                altitudeM = altitudeM.toDoubleOrNull(),
                positionTopo = positionTopo,
                exposition = exposition,
                pentePct = pentePct.toDoubleOrNull(),
                distanceCours = distanceCours.toDoubleOrNull(),
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(Modifier.height(6.dp))

            // ── B. Terrain à vérifier ─────────────────────────────────────
            WhatToVerifyBlock(
                station = currentStation,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(Modifier.height(6.dp))

            // ── C. Topographie & Sol (repliable) ──────────────────────────
            CollapsibleBlock(
                title = "Topographie & Sol",
                icon = Icons.Default.Terrain,
                accentColor = Color(0xFF795548),
                initiallyExpanded = true,
                saveKey = "station_topo",
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                TopoSolTab(
                    gpsLat, gpsLon, altitudeM, { altitudeM = it }, commune, { commune = it },
                    pentePct, { pentePct = it }, exposition, { exposition = it }, positionTopo, { positionTopo = it }, distanceCours, { distanceCours = it },
                    profondeurCm, { profondeurCm = it }, texture, { texture = it }, pierrosite, { pierrosite = it },
                    hydromorphieCm, { hydromorphieCm = it }, humus, { humus = it }, phEstime, { phEstime = it }, testHcl, { testHcl = it }, drainage, { drainage = it }, rocheMere, { rocheMere = it }
                )
            }

            Spacer(Modifier.height(6.dp))

            // ── D. Gradients manuels (repliable) ──────────────────────────
            CollapsibleBlock(
                title = "Gradients manuels",
                icon = Icons.Default.Tune,
                accentColor = Color(0xFF1565C0),
                initiallyExpanded = false,
                saveKey = "station_gradients",
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                GradientsTab(
                    gradientHydrique, { gradientHydrique = it }, gradientTrophique, { gradientTrophique = it },
                    gradientLumineux, { gradientLumineux = it }, gradientHumique, { gradientHumique = it }
                )
            }

            Spacer(Modifier.height(6.dp))

            // ── E. Cortège floristique intelligent ────────────────────────
            SmartVegetationBlock(
                selectedFloraIds = selectedFloraIds,
                onSpeciesAdded = { id -> if (id !in selectedFloraIds) selectedFloraIds = selectedFloraIds + id },
                onSpeciesRemoved = { id -> selectedFloraIds = selectedFloraIds - id },
                especesText = especesIndicatrices,
                onEspecesTextChange = { especesIndicatrices = it },
                espXero = espXerophiles, onXero = { espXerophiles = it },
                espMeso = espMesophiles, onMeso = { espMesophiles = it },
                espHygro = espHygrophiles, onHygro = { espHygrophiles = it },
                notes = notesLibres, onNotes = { notesLibres = it },
                positionTopo = positionTopo,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(Modifier.height(6.dp))

            // ── F. Gradients inférés automatiquement ─────────────────────
            val gi = gradientInference
            if (gi != null) {
                GradientInferenceBlock(
                    result = gi,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Spacer(Modifier.height(6.dp))
            }

            // ── F2. Conflits croises flore/station ───────────────────────
            val correlReport = if (gi != null)
                remember(gi, currentStation) {
                    CorrelationEngine.correlate(
                        station        = currentStation,
                        floraGradients = gi,
                        floraIds       = selectedFloraIds
                    )
                } else null
            if (correlReport != null && correlReport.contradictions.isNotEmpty()) {
                ConflitsBlock(report = correlReport, modifier = Modifier.padding(horizontal = 12.dp))
                Spacer(Modifier.height(6.dp))
            }

            // ── F3. Avenir Climatique DRIAS ───────────────────────────────
            val climateZone = if (gpsLat != null && gpsLon != null)
                ClimateZone.detect(gpsLat, gpsLon, altitudeM.toDoubleOrNull())
            else ClimateZone.UNKNOWN
            AvenirClimatBlock(zone = climateZone, modifier = Modifier.padding(horizontal = 12.dp))
            Spacer(Modifier.height(6.dp))

            // ── G. Photos & Validation ────────────────────────────────────
            CollapsibleBlock(
                title = "Photos & Validation",
                icon = Icons.Default.PhotoCamera,
                accentColor = Color(0xFF6A1B9A),
                initiallyExpanded = true,
                saveKey = "station_photos",
                badge = {
                    ConfidenceBadge(
                        type = if (photos.size >= 2) BadgeType.HIGH_CONFIDENCE else BadgeType.TO_VERIFY,
                        label = "${photos.size}/2 photos",
                        compact = true
                    )
                },
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                MediasTab(
                    photos = photos,
                    onAddPhoto = { uri, legend, type -> photos = photos + DiagnosticPhoto(uri, legend, type) },
                    onRemovePhoto = { idx -> photos = photos.filterIndexed { i, _ -> i != idx } },
                    minPhotos = 2
                )
            }

            Spacer(Modifier.height(6.dp))

            // ── H. Résultats & Essences ───────────────────────────────────
            StationResultTab(currentStation)

            Spacer(Modifier.height(6.dp))

            // ── I. Historique ─────────────────────────────────────────────
            HistoriqueTab(diagnostics) { obs -> scope.launch { stationRepository.delete(obs) } }
        }
    }
}

// Composants de formulaires denses
@Composable
private fun DenseFormSection(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
        content()
    }
}

@Composable
private fun DenseSliderRow(label: String, value: Float, onValueChange: (Float) -> Unit, valueLabel: String, range: ClosedFloatingPointRange<Float> = 1f..5f) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall, fontSize = 12.sp)
            Text(valueLabel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range, steps = 3, modifier = Modifier.height(24.dp))
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

// Onglets
@Composable
private fun TopoSolTab(
    gpsLat: Double?, gpsLon: Double?, altitudeM: String, onAltChange: (String) -> Unit, commune: String, onCommuneChange: (String) -> Unit,
    pentePct: String, onPenteChange: (String) -> Unit, expo: Exposition, onExpoChange: (Exposition) -> Unit, posTopo: PositionTopo, onPosChange: (PositionTopo) -> Unit, distCours: String, onDistChange: (String) -> Unit,
    profCm: String, onProfChange: (String) -> Unit, tex: TextureSol, onTexChange: (TextureSol) -> Unit, pierro: Pierrosite, onPierroChange: (Pierrosite) -> Unit,
    hydroCm: String, onHydroChange: (String) -> Unit, hum: TypeHumus, onHumusChange: (TypeHumus) -> Unit, ph: String, onPhChange: (String) -> Unit, hcl: TestHCl, onHclChange: (TestHCl) -> Unit, drain: Drainage, onDrainChange: (Drainage) -> Unit, roche: String, onRocheChange: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DenseFormSection("Localisation & Topographie") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = commune, onValueChange = onCommuneChange, label = { Text("Commune", fontSize = 10.sp) }, modifier = Modifier.weight(1f), singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp))
                OutlinedTextField(value = altitudeM, onValueChange = onAltChange, label = { Text("Alt (m)", fontSize = 10.sp) }, modifier = Modifier.weight(1f), singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp))
            }
            if (gpsLat != null && gpsLon != null) Text("GPS : ${String.format("%.5f", gpsLat)}°N, ${String.format("%.5f", gpsLon)}°E", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = pentePct, onValueChange = onPenteChange, label = { Text("Pente (%)", fontSize = 10.sp) }, modifier = Modifier.weight(1f), singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp))
                OutlinedTextField(value = distCours, onValueChange = onDistChange, label = { Text("Dist. Eau (m)", fontSize = 10.sp) }, modifier = Modifier.weight(1f), singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp))
            }
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Exposition", style = MaterialTheme.typography.bodySmall, fontSize = 12.sp, modifier = Modifier.weight(1f))
                var expandedExpo by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { expandedExpo = true }) { Text(expo.labelFr, fontSize = 12.sp) }
                    DropdownMenu(expanded = expandedExpo, onDismissRequest = { expandedExpo = false }) {
                        Exposition.entries.forEach { e -> DropdownMenuItem(text = { Text(e.labelFr, fontSize = 12.sp) }, onClick = { onExpoChange(e); expandedExpo = false }) }
                    }
                }
            }
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Position Topo", style = MaterialTheme.typography.bodySmall, fontSize = 12.sp, modifier = Modifier.weight(1f))
                var expandedPos by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { expandedPos = true }) { Text(posTopo.labelFr, fontSize = 12.sp) }
                    DropdownMenu(expanded = expandedPos, onDismissRequest = { expandedPos = false }) {
                        PositionTopo.entries.forEach { p -> DropdownMenuItem(text = { Text(p.labelFr, fontSize = 12.sp) }, onClick = { onPosChange(p); expandedPos = false }) }
                    }
                }
            }
        }

        DenseFormSection("Caractéristiques du Sol") {
            OutlinedTextField(value = profCm, onValueChange = onProfChange, label = { Text("Prof. (cm)", fontSize = 10.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp))
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Texture", style = MaterialTheme.typography.bodySmall, fontSize = 12.sp, modifier = Modifier.weight(1f))
                var expandedTex by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { expandedTex = true }) { Text(tex.labelFr, fontSize = 12.sp) }
                    DropdownMenu(expanded = expandedTex, onDismissRequest = { expandedTex = false }) {
                        TextureSol.entries.forEach { t -> DropdownMenuItem(text = { Text(t.labelFr, fontSize = 12.sp) }, onClick = { onTexChange(t); expandedTex = false }) }
                    }
                }
            }
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Pierrosité", style = MaterialTheme.typography.bodySmall, fontSize = 12.sp, modifier = Modifier.weight(1f))
                var expandedPierro by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { expandedPierro = true }) { Text(pierro.labelFr, fontSize = 12.sp) }
                    DropdownMenu(expanded = expandedPierro, onDismissRequest = { expandedPierro = false }) {
                        Pierrosite.entries.forEach { p -> DropdownMenuItem(text = { Text(p.labelFr, fontSize = 12.sp) }, onClick = { onPierroChange(p); expandedPierro = false }) }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Humus", style = MaterialTheme.typography.bodySmall, fontSize = 12.sp, modifier = Modifier.weight(1f))
                var expandedHum by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { expandedHum = true }) { Text(hum.labelFr, fontSize = 12.sp) }
                    DropdownMenu(expanded = expandedHum, onDismissRequest = { expandedHum = false }) {
                        TypeHumus.entries.forEach { h -> DropdownMenuItem(text = { Text(h.labelFr, fontSize = 12.sp) }, onClick = { onHumusChange(h); expandedHum = false }) }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Drainage", style = MaterialTheme.typography.bodySmall, fontSize = 12.sp, modifier = Modifier.weight(1f))
                var expandedDrain by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { expandedDrain = true }) { Text(drain.labelFr, fontSize = 12.sp) }
                    DropdownMenu(expanded = expandedDrain, onDismissRequest = { expandedDrain = false }) {
                        Drainage.entries.forEach { d -> DropdownMenuItem(text = { Text(d.labelFr, fontSize = 12.sp) }, onClick = { onDrainChange(d); expandedDrain = false }) }
                    }
                }
            }
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Test HCl", style = MaterialTheme.typography.bodySmall, fontSize = 12.sp, modifier = Modifier.weight(1f))
                var expandedHcl by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { expandedHcl = true }) { Text(hcl.labelFr, fontSize = 12.sp) }
                    DropdownMenu(expanded = expandedHcl, onDismissRequest = { expandedHcl = false }) {
                        TestHCl.entries.forEach { t -> DropdownMenuItem(text = { Text(t.labelFr, fontSize = 12.sp) }, onClick = { onHclChange(t); expandedHcl = false }) }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = ph, onValueChange = onPhChange, label = { Text("pH (est.)", fontSize = 10.sp) }, modifier = Modifier.weight(1f), singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp))
                OutlinedTextField(value = hydroCm, onValueChange = onHydroChange, label = { Text("Tâches (cm)", fontSize = 10.sp) }, modifier = Modifier.weight(1f), singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp))
            }
            
            OutlinedTextField(value = roche, onValueChange = onRocheChange, label = { Text("Roche Mère (obs.)", fontSize = 10.sp) }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp))
        }
    }
}

@Composable
private fun GradientsTab(
    hydrique: Float, onHydrique: (Float) -> Unit, trophique: Float, onTrophique: (Float) -> Unit,
    lumineux: Float, onLumineux: (Float) -> Unit, humique: Float, onHumique: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DenseFormSection("Gradients Écologiques (1 à 5)") {
            DenseSliderRow("Hydrique (sec -> inondé)", hydrique, onHydrique, hydrique.toInt().toString())
            DenseSliderRow("Trophique (olig. -> eut.)", trophique, onTrophique, trophique.toInt().toString())
            DenseSliderRow("Lumineux (ombr. -> clair)", lumineux, onLumineux, lumineux.toInt().toString())
            DenseSliderRow("Humique (mor -> mull)", humique, onHumique, humique.toInt().toString())
        }
    }
}

@Composable
private fun VegetationTab(
    especes: String, onEspeces: (String) -> Unit,
    espXero: Boolean, onXero: (Boolean) -> Unit,
    espMeso: Boolean, onMeso: (Boolean) -> Unit,
    espHygro: Boolean, onHygro: (Boolean) -> Unit,
    notes: String, onNotes: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DenseFormSection("Espèces Indicatrices & Notes") {
            OutlinedTextField(value = especes, onValueChange = onEspeces, label = { Text("Espèces indicatrices (séparées par des virgules)") }, modifier = Modifier.fillMaxWidth(), minLines = 3, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp))
            Spacer(Modifier.height(8.dp))
            DenseCheckRow("Espèces Xérophiles présentes", espXero, onXero)
            DenseCheckRow("Espèces Mésophiles présentes", espMeso, onMeso)
            DenseCheckRow("Espèces Hygrophiles présentes", espHygro, onHygro)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = notes, onValueChange = onNotes, label = { Text("Notes supplémentaires sur la végétation") }, modifier = Modifier.fillMaxWidth(), minLines = 4, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp))
        }
    }
}

@Composable
private fun MediasTab(
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
                    "Au moins $minPhotos photos requises pour finaliser (ex: profil sol, paysage, végétation). ${photos.size}/$minPhotos ajoutées.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        DiagnosticPhotoCaptureSection(
            photos = photos,
            onAddPhoto = onAddPhoto,
            onRemovePhoto = onRemovePhoto,
            minPhotos = minPhotos,
            photoTypeOptions = listOf("Paysage", "Profil de sol", "Végétation indicatrice", "Point dur", "Vue d'ensemble")
        )
    }
}

@Composable
private fun StationResultTab(station: StationObservation) {
    val result = remember(station) { StationDiagnosticEngine.diagnose(station) }
    val confColor = when (result.confidence) {
        StationDiagnosticEngine.DiagConfidenceStation.FORTE   -> Color(0xFF2E7D32)
        StationDiagnosticEngine.DiagConfidenceStation.MOYENNE -> Color(0xFFEF6C00)
        StationDiagnosticEngine.DiagConfidenceStation.FAIBLE  -> Color(0xFFC62828)
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StaggerEntrance(0, 70) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = confColor.copy(alpha = 0.10f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.horizontalGradient(listOf(confColor.copy(alpha = 0.18f), confColor.copy(alpha = 0.03f))))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("DIAGNOSTIC STATIONNEL", style = MaterialTheme.typography.labelSmall, color = confColor.copy(alpha = 0.7f), fontWeight = FontWeight.ExtraBold)
                        Text(result.typeStation, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = confColor)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(color = confColor.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) {
                                Text("Confiance : ${result.confidence.labelFr}", style = MaterialTheme.typography.labelSmall, color = confColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                            if (result.risqueEngorgement) Surface(color = Color(0xFFE65100).copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) {
                                Text("⚠ Engorgement", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE65100), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                            if (result.risqueDepiecement) Surface(color = Color(0xFFC62828).copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) {
                                Text("⚠ Dépiècement", style = MaterialTheme.typography.labelSmall, color = Color(0xFFC62828), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }
            }
        }
        StaggerEntrance(1, 70) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Profil Écologique", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        HorizontalDivider()
                        listOf(
                            Triple("Hydrique",  result.gradientHydriqueFinal,  Color(0xFF1565C0)),
                            Triple("Trophique", result.gradientTrophiqueFinal, Color(0xFF6D4C41)),
                        ).forEachIndexed { idx, (label, value, color) ->
                            val animFrac by animateFloatAsState(value / 5f, tween(700, delayMillis = idx * 100), label = "grad_$idx")
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(72.dp))
                                Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                    Box(Modifier.fillMaxHeight().fillMaxWidth(animFrac).clip(RoundedCornerShape(6.dp)).background(color))
                                }
                                Text("$value/5", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
                            }
                        }
                        HorizontalDivider()
                        Text("Contraintes identifiées", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        listOf(
                            Pair("Hydrique",    result.contrainteHydrique),
                            Pair("Trophique",   result.contrainteTrophique),
                            Pair("Profondeur",  result.contrainteProfondeur),
                        ).forEach { (label, contrainte) ->
                            val cColor = when (contrainte) {
                                StationDiagnosticEngine.Contrainte.NULLE      -> Color(0xFF2E7D32)
                                StationDiagnosticEngine.Contrainte.FAIBLE     -> Color(0xFF8BC34A)
                                StationDiagnosticEngine.Contrainte.MODEREE    -> Color(0xFFF9A825)
                                StationDiagnosticEngine.Contrainte.FORTE      -> Color(0xFFEF6C00)
                                StationDiagnosticEngine.Contrainte.TRES_FORTE -> Color(0xFFC62828)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(label, style = MaterialTheme.typography.bodySmall)
                                Surface(color = cColor.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                                    Text(contrainte.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = cColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
            }
        }
        StaggerEntrance(2, 70) {
            SolProfilCard(station)
        }
        if (result.atouts.isNotEmpty() || result.contraintes.isNotEmpty()) {
            StaggerEntrance(3, 70) {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (result.atouts.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                                    Text("Atouts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                }
                                result.atouts.forEach { atout ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("•", color = Color(0xFF2E7D32), style = MaterialTheme.typography.bodySmall)
                                        Text(atout, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            if (result.contraintes.isNotEmpty()) {
                                if (result.atouts.isNotEmpty()) HorizontalDivider()
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Warning, null, tint = Color(0xFFEF6C00), modifier = Modifier.size(18.dp))
                                    Text("Contraintes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFFEF6C00))
                                }
                                result.contraintes.forEach { c ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("•", color = Color(0xFFEF6C00), style = MaterialTheme.typography.bodySmall)
                                        Text(c, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
            }
        }
        if (result.alertes.isNotEmpty()) {
            StaggerEntrance(3, 70) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFC62828).copy(alpha = 0.08f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Error, null, tint = Color(0xFFC62828), modifier = Modifier.size(18.dp))
                                Text("Alertes d'incohérence", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                            }
                            result.alertes.forEach { alerte ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("⚠", color = Color(0xFFC62828), style = MaterialTheme.typography.bodySmall)
                                    Text(alerte, style = MaterialTheme.typography.bodySmall, color = Color(0xFFC62828))
                                }
                            }
                        }
                    }
            }
        }
        StaggerEntrance(4, 70) {
            FullEssencesCompatibilityCard(station)
        }
    }
}

@Composable
private fun HistoriqueTab(diagnostics: List<StationObservation>, onDelete: (StationObservation) -> Unit) {
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
                    Text("Historique (${diagnostics.size} relevé${if (diagnostics.size > 1) "s" else ""})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (diagnostics.size >= 2) GradientTrendCard(diagnostics)
                    diagnostics.sortedByDescending { it.observationDate }.forEach { obs ->
                        val result = remember(obs.id) { StationDiagnosticEngine.diagnose(obs) }
                        val confColor = when (result.confidence) {
                            StationDiagnosticEngine.DiagConfidenceStation.FORTE   -> Color(0xFF2E7D32)
                            StationDiagnosticEngine.DiagConfidenceStation.MOYENNE -> Color(0xFFEF6C00)
                            StationDiagnosticEngine.DiagConfidenceStation.FAIBLE  -> Color(0xFFC62828)
                        }
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
                                    Text("${obs.commune.ifBlank { "Lieu inconnu" }} — Alt. ${obs.altitudeM?.toInt() ?: "?"} m", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(result.typeStation, style = MaterialTheme.typography.bodySmall, color = confColor, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Surface(color = confColor.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
                                        Text(result.confidence.labelFr, style = MaterialTheme.typography.labelSmall, color = confColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
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

// ─────────────────────────────────────────────────────────────────────────────
//  En-tête score station (live, style IBP)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StationScoreHeader(station: StationObservation) {
    val result = remember(station) { StationDiagnosticEngine.diagnose(station) }
    val confColor = when (result.confidence) {
        StationDiagnosticEngine.DiagConfidenceStation.FORTE   -> Color(0xFF2E7D32)
        StationDiagnosticEngine.DiagConfidenceStation.MOYENNE -> Color(0xFFEF6C00)
        StationDiagnosticEngine.DiagConfidenceStation.FAIBLE  -> Color(0xFF9E9E9E)
    }
    val animH by animateFloatAsState(result.gradientHydriqueFinal / 5f, tween(900), label = "sh_h")
    val animT by animateFloatAsState(result.gradientTrophiqueFinal / 5f, tween(900, 100), label = "sh_t")

    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = confColor.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(confColor.copy(alpha = 0.14f), confColor.copy(alpha = 0.03f))))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("STATION FORESTIÈRE", style = MaterialTheme.typography.labelSmall, color = confColor.copy(alpha = 0.7f), fontWeight = FontWeight.ExtraBold)
                    Text(result.typeStation, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = confColor)
                }
                Surface(color = confColor.copy(alpha = 0.18f), shape = RoundedCornerShape(10.dp)) {
                    Text(result.confidence.labelFr, style = MaterialTheme.typography.labelSmall, color = confColor, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
            HorizontalDivider(color = confColor.copy(alpha = 0.15f))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    Triple("Hydrique", animH, Color(0xFF1565C0)),
                    Triple("Trophique", animT, Color(0xFF6D4C41))
                ).forEach { (label, frac, color) ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(64.dp))
                        Box(modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(frac).clip(RoundedCornerShape(4.dp)).background(color))
                        }
                        Text("${(frac * 5).toInt()}/5", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.width(24.dp), textAlign = TextAlign.End)
                    }
                }
            }
            if (result.risqueEngorgement || result.risqueDepiecement || result.alertes.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (result.risqueEngorgement) Surface(color = Color(0xFFE65100).copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                        Text("⚠ Engorgement", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE65100), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                    }
                    if (result.risqueDepiecement) Surface(color = Color(0xFFC62828).copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                        Text("⚠ Dépiècement", style = MaterialTheme.typography.labelSmall, color = Color(0xFFC62828), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tableau étendu de compatibilité essences
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FullEssencesCompatibilityCard(station: StationObservation) {
    data class EssRow(
        val nameFr: String,
        val compatibility: CompatibilityLevel,
        val resilience: Int,
        val reasons: List<String>
    )

    val allResults = remember(station) {
        AutecologyDatabase.species.map { ess ->
            val res = StationDiagnosticEngine.evaluateCompatibility(ess, station)
            EssRow(ess.nameFr, res.compatibility, ess.climateChangeResilience, res.reasons)
        }.sortedWith(compareBy(
            { when (it.compatibility) { CompatibilityLevel.OPTIMUM -> 0; CompatibilityLevel.TOLERATED -> 1; else -> 2 } },
            { it.nameFr }
        ))
    }

    val optimumCount     = remember(allResults) { allResults.count { it.compatibility == CompatibilityLevel.OPTIMUM } }
    val toleratedCount   = remember(allResults) { allResults.count { it.compatibility == CompatibilityLevel.TOLERATED } }
    val incompatCount    = remember(allResults) { allResults.count { it.compatibility == CompatibilityLevel.INCOMPATIBLE } }

    var filterLevel by remember { mutableStateOf<CompatibilityLevel?>(null) }
    val displayed = if (filterLevel == null) allResults else allResults.filter { it.compatibility == filterLevel }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Forest, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                Text(
                    "Compatibilité essences (${allResults.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Tapez pour détails",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val filterChips = listOf(
                    null          to "Toutes (${allResults.size})",
                    CompatibilityLevel.OPTIMUM      to "✓ Optimum ($optimumCount)",
                    CompatibilityLevel.TOLERATED    to "~ Toléré ($toleratedCount)",
                    CompatibilityLevel.INCOMPATIBLE to "✗ Incompatible ($incompatCount)"
                )
                filterChips.forEach { (level, label) ->
                    val selected = filterLevel == level
                    val chipColor = when (level) {
                        CompatibilityLevel.OPTIMUM      -> Color(0xFF2E7D32)
                        CompatibilityLevel.TOLERATED    -> Color(0xFFEF6C00)
                        CompatibilityLevel.INCOMPATIBLE -> Color(0xFFC62828)
                        null                            -> MaterialTheme.colorScheme.primary
                        else                            -> Color(0xFF78909C)
                    }
                    FilterChip(
                        selected = selected,
                        onClick  = { filterLevel = if (selected) null else level },
                        label    = { Text(label, fontSize = 11.sp) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor      = chipColor.copy(alpha = 0.18f),
                            selectedLabelColor          = chipColor,
                            selectedLeadingIconColor    = chipColor
                        )
                    )
                }
            }

            displayed.forEach { row ->
                EssenceCompatibilityRowItem(row.nameFr, row.compatibility, row.resilience, row.reasons)
                if (row != displayed.last()) HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun EssenceCompatibilityRowItem(
    nameFr: String,
    compatibility: CompatibilityLevel,
    resilience: Int,
    reasons: List<String>
) {
    var expanded by remember { mutableStateOf(false) }
    val color = when (compatibility) {
        CompatibilityLevel.OPTIMUM      -> Color(0xFF2E7D32)
        CompatibilityLevel.TOLERATED    -> Color(0xFFEF6C00)
        CompatibilityLevel.INCOMPATIBLE -> Color(0xFFC62828)
        else                            -> Color(0xFF78909C)
    }
    val icon = when (compatibility) {
        CompatibilityLevel.OPTIMUM      -> Icons.Default.CheckCircle
        CompatibilityLevel.TOLERATED    -> Icons.Default.Warning
        CompatibilityLevel.INCOMPATIBLE -> Icons.Default.Cancel
        else                            -> Icons.Default.Info
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = reasons.isNotEmpty()) { expanded = !expanded }
            .padding(vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(nameFr, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text(compatibility.label, style = MaterialTheme.typography.labelSmall, color = color, fontSize = 10.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                repeat(5) { i ->
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = if (i < resilience) Color(0xFFF9A825) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
            if (reasons.isNotEmpty()) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        if (expanded && reasons.isNotEmpty()) {
            Column(modifier = Modifier.padding(start = 26.dp, top = 3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                reasons.forEach { reason ->
                    Text(
                        "• $reason",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Profil de sol visuel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SolProfilCard(station: StationObservation) {
    val profondeur  = station.profondeurSolCm ?: 50
    val hydroDepth  = station.hydromorphieProfondeurCm

    val humusColor = when (station.humus) {
        TypeHumus.MULL      -> Color(0xFF5D4037)
        TypeHumus.MODER     -> Color(0xFF795548)
        TypeHumus.MOR       -> Color(0xFF3E2723)
        else                -> Color(0xFF6D4C41)
    }
    val textureColor = when (station.texture) {
        TextureSol.ARGILEUSE       -> Color(0xFFB26A00)
        TextureSol.LIMONEUSE       -> Color(0xFFA1887F)
        TextureSol.SABLEUSE        -> Color(0xFFFFD54F)
        TextureSol.GRAVELEUSE      -> Color(0xFFBDBDBD)
        TextureSol.INCONNUE        -> Color(0xFFE0E0E0)
        else                       -> Color(0xFF8D6E63)
    }
    val drainColor = when {
        "BON"     in station.drainage.name && "TRES" !in station.drainage.name -> Color(0xFF2E7D32)
        "TRES_BON" in station.drainage.name  -> Color(0xFF1565C0)
        "NORMAL"   in station.drainage.name  -> Color(0xFF4CAF50)
        "IMPARFAIT" in station.drainage.name -> Color(0xFFF9A825)
        "TRES_MAUVAIS" in station.drainage.name -> Color(0xFF6A1B9A)
        "MAUVAIS" in station.drainage.name   -> Color(0xFFC62828)
        else -> Color(0xFF757575)
    }
    val hclColor = when {
        "NEGATIF" in station.testHcl.name              -> Color(0xFF2E7D32)
        "FAIBLE"  in station.testHcl.name              -> Color(0xFF8BC34A)
        "MODERE"  in station.testHcl.name              -> Color(0xFFF9A825)
        "TRES_FORT" in station.testHcl.name            -> Color(0xFFC62828)
        "FORT"    in station.testHcl.name              -> Color(0xFFEF6C00)
        else -> Color(0xFF757575)
    }

    val humusFrac   = 0.12f
    val hydroFrac   = if (hydroDepth != null && hydroDepth < profondeur)
        (1f - hydroDepth.toFloat() / profondeur.toFloat()).coerceIn(0f, 0.85f) else 0f
    val mineralFrac = (1f - humusFrac - hydroFrac - 0.05f).coerceAtLeast(0.05f)

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Layers, null, tint = Color(0xFF795548), modifier = Modifier.size(20.dp))
                Text("Profil Pédologique", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider()

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(
                    modifier = Modifier
                        .width(44.dp)
                        .height(180.dp)
                        .clip(RoundedCornerShape(6.dp))
                ) {
                    Box(Modifier.fillMaxWidth().weight(humusFrac).background(humusColor)) {
                        Text("H", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.align(Alignment.Center), fontSize = 9.sp)
                    }
                    Box(Modifier.fillMaxWidth().weight(mineralFrac).background(textureColor)) {
                        Text("A", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.align(Alignment.Center), fontSize = 9.sp)
                    }
                    if (hydroFrac > 0f) {
                        Box(Modifier.fillMaxWidth().weight(hydroFrac).background(Color(0xFF90CAF9))) {
                            Text("G", style = MaterialTheme.typography.labelSmall, color = Color(0xFF1565C0).copy(alpha = 0.8f),
                                modifier = Modifier.align(Alignment.Center), fontSize = 9.sp)
                        }
                    }
                    Box(Modifier.fillMaxWidth().weight(0.05f).background(Color(0xFF616161)))
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    SolInfoRow("Profondeur",  "${profondeur} cm",                    Color(0xFF5D4037))
                    SolInfoRow("Texture",     station.texture.labelFr,               textureColor)
                    SolInfoRow("Humus",       station.humus.labelFr,                 humusColor)
                    station.phEstime?.let {
                        SolInfoRow("pH estimé",   String.format("%.1f", it),         Color(0xFF1565C0))
                    }
                    SolInfoRow("Drainage",    station.drainage.labelFr,              drainColor)
                    SolInfoRow("Pierrosité",  station.pierrosite.labelFr,            Color(0xFF757575))
                    if (station.rocheMere.isNotBlank()) {
                        SolInfoRow("Roche mère",  station.rocheMere,                 Color(0xFF616161))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(color = hclColor.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
                    Text("HCl : ${station.testHcl.labelFr}", style = MaterialTheme.typography.labelSmall,
                        color = hclColor, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
                if (hydroDepth != null) {
                    Surface(color = Color(0xFF1565C0).copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
                        Text("Hydromorphie à ${hydroDepth} cm", style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF1565C0), fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SolInfoRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
            color = valueColor, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  NOUVEAUX BLOCS DASHBOARD — Contexte auto, terrain, flore intelligente, gradients
// ═══════════════════════════════════════════════════════════════════════════════

// ─── A. Contexte auto-déduit ───────────────────────────────────────────────

@Composable
private fun AutoContextBlock(
    gpsLat: Double?,
    gpsLon: Double?,
    altitudeM: Double?,
    positionTopo: PositionTopo,
    exposition: Exposition,
    pentePct: Double?,
    distanceCours: Double?,
    modifier: Modifier = Modifier
) {
    val inferredContext = remember(gpsLat, gpsLon, altitudeM, positionTopo, exposition, pentePct) {
        buildAutoContext(altitudeM, positionTopo, exposition, pentePct, distanceCours)
    }

    CollapsibleBlock(
        title = "Contexte auto-déduit",
        icon = Icons.Default.MyLocation,
        accentColor = Color(0xFF1565C0),
        initiallyExpanded = true,
        saveKey = "station_context",
        badge = { ConfidenceBadge(BadgeType.AUTO_DEDUCED, compact = true) },
        modifier = modifier
    ) {
        if (gpsLat != null && gpsLon != null) {
            BlockInfoRow(
                label = "Coordonnées GPS",
                value = "${String.format("%.5f", gpsLat)}°N, ${String.format("%.5f", gpsLon)}°E",
                icon = Icons.Default.GpsFixed,
                badge = { ConfidenceBadge(BadgeType.TERRAIN_OBS, compact = true) }
            )
        } else {
            InlineAlert("GPS non capturé — déplacer en extérieur pour la position", AlertType.WARNING)
        }
        altitudeM?.let {
            val altAlert = when {
                it > 1200 -> "⚠ Altitude subalpine — diagnostics non calibrés au-delà de 1200 m"
                it > 800  -> "Zone montagnarde"
                else      -> null
            }
            BlockInfoRow(
                label = "Altitude",
                value = "${it.toInt()} m",
                icon = Icons.Default.Height,
                valueColor = if (it > 1200) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurface
            )
            if (altAlert != null) InlineAlert(altAlert, if (it > 1200) AlertType.WARNING else AlertType.INFO)
        }
        BlockInfoRow("Position topographique", positionTopo.labelFr, Icons.Default.Landscape)
        BlockInfoRow("Exposition", exposition.labelFr, Icons.Default.Explore)
        pentePct?.let {
            BlockInfoRow(
                label = "Pente",
                value = "${it.toInt()} %",
                icon = Icons.Default.Terrain,
                valueColor = if (it > 50) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurface
            )
        }
        distanceCours?.let { BlockInfoRow("Distance cours d'eau", "${it.toInt()} m", Icons.Default.Water) }

        if (inferredContext.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Text(
                "Déductions automatiques",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            inferredContext.forEach { deduction ->
                InlineAlert(deduction, AlertType.INFO)
            }
        }
    }
}

private fun buildAutoContext(
    altitudeM: Double?,
    positionTopo: PositionTopo,
    exposition: Exposition,
    pentePct: Double?,
    distanceCours: Double?
): List<String> {
    val inferences = mutableListOf<String>()

    // Inférences topographiques
    if (positionTopo == PositionTopo.VALLON || positionTopo == PositionTopo.BAS_VERSANT)
        inferences += "Position basse : gradient hydrique probablement élevé (accumulation d'eau)"
    if (positionTopo == PositionTopo.CRETE)
        inferences += "Sommet de versant : gradient hydrique probablement faible (ressuyage rapide)"
    if (positionTopo == PositionTopo.MI_VERSANT && exposition in listOf(Exposition.S, Exposition.SE, Exposition.SO))
        inferences += "Mi-versant exposé au sud : conditions xériques probables en été"

    // Exposition
    if (exposition in listOf(Exposition.N, Exposition.NE, Exposition.NO))
        inferences += "Versant nord : gradient lumineux faible, humidité maintenue, gelées tardives possibles"
    if (exposition in listOf(Exposition.S, Exposition.SE))
        inferences += "Versant sud : ensoleillement fort, conditions plus sèches"

    // Altitude
    if (altitudeM != null && altitudeM > 600)
        inferences += "Altitude > 600 m : risque de gelées tardives à considérer pour les essences sensibles"

    // Pente
    if (pentePct != null && pentePct > 40)
        inferences += "Forte pente (${pentePct.toInt()}%) : risque d'érosion, préférer essences à enracinement profond"

    // Proximité cours d'eau
    if (distanceCours != null && distanceCours < 30)
        inferences += "Proximité cours d'eau (${distanceCours.toInt()} m) : hydromorphie possible, contexte ripisylve"

    return inferences
}

// ─── B. Terrain à vérifier ────────────────────────────────────────────────

@Composable
private fun WhatToVerifyBlock(
    station: StationObservation,
    modifier: Modifier = Modifier
) {
    val checks = remember(station) { buildVerificationChecklist(station) }
    if (checks.isEmpty()) return

    CollapsibleBlock(
        title = "Points à vérifier / observer",
        icon = Icons.Default.Checklist,
        accentColor = Color(0xFFE65100),
        initiallyExpanded = true,
        saveKey = "station_verify",
        badge = {
            val pending = checks.count { !it.second }
            if (pending > 0) ConfidenceBadge(BadgeType.TO_VERIFY, label = "$pending en attente", compact = true)
        },
        modifier = modifier
    ) {
        checks.forEach { (item, done) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (done) Color(0xFF2E7D32) else Color(0xFFE65100),
                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                )
                Text(item, style = MaterialTheme.typography.bodySmall,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

private fun buildVerificationChecklist(station: StationObservation): List<Pair<String, Boolean>> =
    listOf(
        "Test à l'eau oxygénée / HCl sur sol frais" to (station.testHcl != TestHCl.NEGATIF || station.rocheMere.isNotBlank()),
        "Mesure profondeur sol (bâton ou tarière)" to ((station.profondeurSolCm ?: 0) > 0),
        "Observation texture sol (triangle textural)" to (station.texture != TextureSol.INCONNUE),
        "Type d'humus identifié (mor/moder/mull)" to (station.humus != TypeHumus.INCONNU),
        "Espèces indicatrices notées (min. 3)" to (station.especesIndicatrices.size >= 3),
        "pH estimé (bandelettes ou réaction HCl)" to (station.phEstime != null),
        "Drainage observé (taches rouille en profondeur)" to (station.drainage != Drainage.NORMAL || station.hydromorphieProfondeurCm != null),
        "Minimum 2 photos justificatives" to (station.photos.size >= 2)
    )

// ─── E. Cortège floristique intelligent ───────────────────────────────────

@Composable
private fun SmartVegetationBlock(
    selectedFloraIds: List<String>,
    onSpeciesAdded: (String) -> Unit,
    onSpeciesRemoved: (String) -> Unit,
    especesText: String,
    onEspecesTextChange: (String) -> Unit,
    espXero: Boolean, onXero: (Boolean) -> Unit,
    espMeso: Boolean, onMeso: (Boolean) -> Unit,
    espHygro: Boolean, onHygro: (Boolean) -> Unit,
    notes: String, onNotes: (String) -> Unit,
    positionTopo: PositionTopo,
    modifier: Modifier = Modifier
) {
    val contextMilieu = when (positionTopo) {
        PositionTopo.VALLON, PositionTopo.BAS_VERSANT -> TypeMilieu.ZONE_HUMIDE
        PositionTopo.CRETE                            -> TypeMilieu.MILIEU_ROCHEUX
        else                                          -> TypeMilieu.FORET_FEUILLUE
    }
    val badge: @Composable () -> Unit = {
        val conf = when {
            selectedFloraIds.size >= 5 -> BadgeType.HIGH_CONFIDENCE
            selectedFloraIds.size >= 2 -> BadgeType.INFERRED
            else                       -> BadgeType.INSUFFICIENT
        }
        ConfidenceBadge(conf, label = "${selectedFloraIds.size} esp.", compact = true)
    }

    CollapsibleBlock(
        title = "Cortège floristique",
        icon = Icons.Default.Grass,
        accentColor = Color(0xFF2E7D32),
        initiallyExpanded = true,
        saveKey = "station_flora",
        badge = badge,
        modifier = modifier
    ) {
        InlineAlert(
            "Saisissez les espèces du sous-bois — gradients calculés automatiquement",
            AlertType.INFO
        )

        SmartPlantInputField(
            selectedSpeciesIds = selectedFloraIds,
            onSpeciesAdded = onSpeciesAdded,
            onSpeciesRemoved = onSpeciesRemoved,
            contextMilieu = contextMilieu,
            contextIds = selectedFloraIds,
            placeholder = "Ex: fougère, oxalide, ronce, muguet…"
        )

        // Saisie libre complémentaire (espèces inconnues de la base)
        if (selectedFloraIds.isEmpty()) {
            OutlinedTextField(
                value = especesText,
                onValueChange = onEspecesTextChange,
                label = { Text("Espèces hors base (séparées par virgules)", fontSize = 10.sp) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
            )
        }

        // Cases à cocher rapides
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Text("Catégories observées", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "Xérophiles" to Pair(espXero, onXero),
                "Mésophiles" to Pair(espMeso, onMeso),
                "Hygrophiles" to Pair(espHygro, onHygro)
            ).forEach { (label, pair) ->
                val (checked, onChange) = pair
                FilterChip(
                    selected = checked,
                    onClick = { onChange(!checked) },
                    label = { Text(label, fontSize = 10.sp) }
                )
            }
        }

        OutlinedTextField(
            value = notes,
            onValueChange = onNotes,
            label = { Text("Notes végétation", fontSize = 10.sp) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
        )
    }
}

// ─── F. Bloc gradients inférés ─────────────────────────────────────────────

@Composable
private fun GradientInferenceBlock(
    result: GradientInferenceEngine.GradientResult,
    modifier: Modifier = Modifier
) {
    val coherenceColor = when (result.cohérenceInterne) {
        GradientInferenceEngine.Coherence.FORTE          -> Color(0xFF2E7D32)
        GradientInferenceEngine.Coherence.MOYENNE        -> Color(0xFF8BC34A)
        GradientInferenceEngine.Coherence.FAIBLE         -> Color(0xFFF9A825)
        GradientInferenceEngine.Coherence.CONTRADICTOIRE -> Color(0xFFC62828)
        GradientInferenceEngine.Coherence.INSUFFISANT    -> Color(0xFF9E9E9E)
    }
    val badgeType = when (result.confidenceLevel) {
        GradientInferenceEngine.ConfidenceGradient.HAUTE         -> BadgeType.HIGH_CONFIDENCE
        GradientInferenceEngine.ConfidenceGradient.MOYENNE       -> BadgeType.INFERRED
        GradientInferenceEngine.ConfidenceGradient.FAIBLE        -> BadgeType.TO_VERIFY
        GradientInferenceEngine.ConfidenceGradient.INSUFFISANTE  -> BadgeType.INSUFFICIENT
    }

    CollapsibleBlock(
        title = "Gradients calculés (flore)",
        icon = Icons.Default.Analytics,
        accentColor = Color(0xFF00796B),
        initiallyExpanded = true,
        saveKey = "station_inference",
        badge = {
            ConfidenceBadge(badgeType, label = result.confidenceLevel.labelFr.take(12), compact = true)
        },
        modifier = modifier
    ) {
        // Radar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniGradientRadar(
                axes = gradientAxesFromResult(result),
                size = 130f,
                showLabels = true
            )

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BlockInfoRow("Humidité", "${String.format("%.1f", result.hydrique)}/7",
                    icon = Icons.Default.Water, valueColor = Color(0xFF1565C0))
                BlockInfoRow("Fertilité", "${String.format("%.1f", result.trophique)}/6",
                    icon = Icons.Default.Agriculture, valueColor = Color(0xFF2E7D32))
                BlockInfoRow("Réaction", result.acidite.labelFr.take(18),
                    icon = Icons.Default.Science, valueColor = Color(0xFF7B1FA2))
                Text(
                    result.hydriqueLabelFr,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF1565C0)
                )
                Text(
                    result.trophiqueLabelFr,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2E7D32)
                )
            }
        }

        // Cohérence
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (result.cohérenceInterne) {
                    GradientInferenceEngine.Coherence.FORTE -> Icons.Default.CheckCircle
                    GradientInferenceEngine.Coherence.CONTRADICTOIRE -> Icons.Default.Cancel
                    else -> Icons.Default.Info
                },
                contentDescription = null,
                tint = coherenceColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                "${result.cohérenceInterne.labelFr} — ${result.nbTaxonsAnalysables} taxons analysés",
                style = MaterialTheme.typography.labelSmall,
                color = coherenceColor
            )
        }

        // Alertes hydromorphie / perturbation
        if (result.probabiliteHydromorphie > 0.30) {
            InlineAlert(
                "⚠ Hydromorphie probable (${(result.probabiliteHydromorphie*100).toInt()}% hygrophytes dans le cortège)",
                AlertType.WARNING
            )
        }
        if (result.probabilitePerturbation > 0.35) {
            InlineAlert(
                "⚠ Flore perturbée (${(result.probabilitePerturbation*100).toInt()}% indicateurs nitrophiles/perturbation)",
                AlertType.WARNING
            )
        }

        // Conflits
        result.conflits.forEach { conflit ->
            InlineAlert(
                "Conflit ${conflit.gradient} : ${conflit.espece1} ↔ ${conflit.espece2}",
                if (conflit.severite == GradientInferenceEngine.Severite.FORTE) AlertType.ERROR else AlertType.WARNING
            )
        }

        // Suggestions d'observation
        if (result.observationsComplementaires.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Text("Observations suggérées", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            result.observationsComplementaires.forEach { obs ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("→", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(obs, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Évolution historique des gradients (ligne)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GradientTrendCard(diagnostics: List<StationObservation>) {
    val df       = remember { SimpleDateFormat("MM/yy", Locale.FRANCE) }
    val sorted   = remember(diagnostics) { diagnostics.sortedBy { it.observationDate } }
    val n        = sorted.size
    val colorH   = Color(0xFF1565C0)
    val colorT   = Color(0xFF6D4C41)
    val colorL   = Color(0xFF2E7D32)
    val colorHum = Color(0xFFEF6C00)

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Timeline, null, tint = colorH, modifier = Modifier.size(20.dp))
                Text("Évolution des gradients ($n relevés)",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider()

            Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                val padL = 28f; val padR = 12f; val padT = 12f; val padB = 28f
                val chartW = size.width - padL - padR
                val chartH = size.height - padT - padB

                // Grid lines for values 1–5
                for (v in 1..5) {
                    val y = padT + chartH * (1f - (v - 1) / 4f)
                    drawLine(Color.Gray.copy(alpha = 0.15f),
                        Offset(padL, y), Offset(padL + chartW, y), strokeWidth = 1f)
                    drawContext.canvas.nativeCanvas.drawText(
                        "$v", padL - 6f, y + 4f,
                        android.graphics.Paint().apply {
                            textSize = 22f; color = android.graphics.Color.GRAY
                            textAlign = android.graphics.Paint.Align.RIGHT
                        }
                    )
                }

                // X-axis ticks
                sorted.forEachIndexed { i, obs ->
                    val x = padL + if (n == 1) chartW / 2f else (i.toFloat() / (n - 1)) * chartW
                    drawContext.canvas.nativeCanvas.drawText(
                        df.format(Date(obs.observationDate)),
                        x, size.height - 2f,
                        android.graphics.Paint().apply {
                            textSize = 20f; color = android.graphics.Color.GRAY
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                    )
                }

                // Draw one line per gradient series
                data class Series(val values: List<Int>, val color: Color)
                val series = listOf(
                    Series(sorted.map { it.gradientHydrique },  colorH),
                    Series(sorted.map { it.gradientTrophique }, colorT),
                    Series(sorted.map { it.gradientLumineux },  colorL),
                    Series(sorted.map { it.gradientHumique },   colorHum),
                )
                series.forEach { (values, col) ->
                    if (values.any { it > 0 }) {
                        val path = Path()
                        values.forEachIndexed { i, v ->
                            val x = padL + if (n == 1) chartW / 2f else (i.toFloat() / (n - 1)) * chartW
                            val y = padT + chartH * (1f - (v.coerceIn(1, 5) - 1) / 4f)
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, color = col.copy(alpha = 0.8f),
                            style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        values.forEachIndexed { i, v ->
                            val x = padL + if (n == 1) chartW / 2f else (i.toFloat() / (n - 1)) * chartW
                            val y = padT + chartH * (1f - (v.coerceIn(1, 5) - 1) / 4f)
                            drawCircle(col, radius = 5f, center = Offset(x, y))
                            drawCircle(Color.White, radius = 2.5f, center = Offset(x, y))
                        }
                    }
                }
            }

            // Legend
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(
                    colorH   to "Hydrique",
                    colorT   to "Trophique",
                    colorL   to "Lumineux",
                    colorHum to "Humique"
                ).forEach { (c, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(c))
                        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Bloc F2 — Conflits croisés flore / station
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConflitsBlock(
    report: CorrelationEngine.CorrelationReport,
    modifier: Modifier = Modifier
) {
    CollapsibleBlock(
        title      = "Conflits détectés (${report.contradictions.size})",
        icon       = Icons.Default.SyncProblem,
        accentColor = Color(0xFFC62828),
        initiallyExpanded = true,
        saveKey    = "station_conflits",
        badge      = {
            ConfidenceBadge(
                type    = BadgeType.CONFLICT,
                label   = report.coherenceGlobale.labelFr.split(" — ").first(),
                compact = true
            )
        },
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            report.alertesCritiques.forEach { alerte ->
                InlineAlert(alerte, AlertType.ERROR)
            }
            report.contradictions.forEach { fact ->
                Card(
                    shape  = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (fact.severite) {
                            CorrelationEngine.Severite.CRITIQUE -> Color(0xFFFFEBEE)
                            CorrelationEngine.Severite.MODERE   -> Color(0xFFFFF8E1)
                            CorrelationEngine.Severite.INFO     -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                when (fact.severite) {
                                    CorrelationEngine.Severite.CRITIQUE -> Icons.Default.Error
                                    CorrelationEngine.Severite.MODERE   -> Icons.Default.Warning
                                    CorrelationEngine.Severite.INFO     -> Icons.Default.Info
                                },
                                null,
                                tint = when (fact.severite) {
                                    CorrelationEngine.Severite.CRITIQUE -> Color(0xFFC62828)
                                    CorrelationEngine.Severite.MODERE   -> Color(0xFFE65100)
                                    CorrelationEngine.Severite.INFO     -> MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "${fact.source1.labelFr} vs ${fact.source2.labelFr}",
                                style      = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(fact.description, style = MaterialTheme.typography.bodySmall)
                        if (fact.conseil.isNotBlank()) {
                            Text(
                                "Conseil : ${fact.conseil}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            if (report.enrichissements.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Text(
                    "Déductions croisées (${report.enrichissements.size})",
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.primary
                )
                report.enrichissements.take(3).forEach { e ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment     = Alignment.Top
                    ) {
                        Icon(Icons.Default.AutoAwesome, null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp).padding(top = 2.dp))
                        Text(e.description, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Bloc F3 — Avenir Climatique DRIAS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AvenirClimatBlock(
    zone: ClimateZone,
    modifier: Modifier = Modifier
) {
    val projection = remember(zone) { DRIASDatabase.getProjection(zone) }
    val risques    = remember(zone) { DRIASDatabase.generateRisques(zone) }
    val essences   = remember(zone) { DRIASDatabase.droughtResistantEssences(zone) }
    val score      = remember(zone) { DRIASDatabase.computeVulnerabilityScore(zone) }

    val riskColor = when (projection.droughtRisk2050) {
        DRIASDatabase.DroughtRisk.FAIBLE    -> Color(0xFF2E7D32)
        DRIASDatabase.DroughtRisk.MODERE    -> Color(0xFFF9A825)
        DRIASDatabase.DroughtRisk.FORT      -> Color(0xFFEF6C00)
        DRIASDatabase.DroughtRisk.TRES_FORT -> Color(0xFFC62828)
    }

    CollapsibleBlock(
        title       = "Avenir Climatique (DRIAS 2021)",
        icon        = Icons.Default.WbSunny,
        accentColor = riskColor,
        initiallyExpanded = false,
        saveKey     = "station_drias",
        badge       = {
            ConfidenceBadge(
                type    = when (projection.droughtRisk2050) {
                    DRIASDatabase.DroughtRisk.FAIBLE    -> BadgeType.HIGH_CONFIDENCE
                    DRIASDatabase.DroughtRisk.MODERE    -> BadgeType.TO_VERIFY
                    DRIASDatabase.DroughtRisk.FORT      -> BadgeType.CONFLICT
                    DRIASDatabase.DroughtRisk.TRES_FORT -> BadgeType.CONFLICT
                },
                label   = "Zone ${zone.labelFr}",
                compact = true
            )
        },
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Synthèse zone
            Surface(
                color  = riskColor.copy(alpha = 0.08f),
                shape  = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    projection.syntheseTexte,
                    style    = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp)
                )
            }

            // Indicateurs clés
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DriasKpiCard(
                    label = "+${projection.deltaT2050_ssp585}°C",
                    sublabel = "Réchauffement 2050\n(SSP8.5)",
                    color = Color(0xFFEF6C00),
                    modifier = Modifier.weight(1f)
                )
                DriasKpiCard(
                    label = "${projection.deltaPsummer2050_ssp585.toInt()}%",
                    sublabel = "Précipit. été 2050\n(SSP8.5)",
                    color = Color(0xFF1565C0),
                    modifier = Modifier.weight(1f)
                )
                DriasKpiCard(
                    label = "${projection.droughtDays2050}j",
                    sublabel = "Sécheresse/an\n2050",
                    color = riskColor,
                    modifier = Modifier.weight(1f)
                )
            }

            // Risques
            if (risques.isNotEmpty()) {
                Text("Risques identifiés",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface)
                risques.forEach { risque ->
                    InlineAlert(risque, if (riskColor == Color(0xFFC62828)) AlertType.ERROR else AlertType.WARNING)
                }
            }

            // Essences recommandées
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Text("Essences résistantes à la sécheresse — ${projection.droughtRisk2050.labelFr}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32))
            essences.forEach { essence ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Forest, null,
                        tint     = Color(0xFF2E7D32),
                        modifier = Modifier.size(12.dp))
                    Text(essence, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }

            // Score vulnérabilité
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("Score vulnérabilité climatique",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$score / 100",
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color      = riskColor)
            }
            LinearProgressIndicator(
                progress   = { score / 100f },
                modifier   = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color      = riskColor,
                trackColor = riskColor.copy(alpha = 0.15f)
            )
        }
    }
}

@Composable
private fun DriasKpiCard(
    label: String, sublabel: String, color: Color, modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape    = RoundedCornerShape(10.dp),
        colors   = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.09f))
    ) {
        Column(
            modifier            = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold, color = color)
            Text(sublabel, style = MaterialTheme.typography.labelSmall,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign  = TextAlign.Center,
                fontSize   = 9.sp)
        }
    }
}
