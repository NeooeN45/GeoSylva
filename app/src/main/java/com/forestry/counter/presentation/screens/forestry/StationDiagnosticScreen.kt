package com.forestry.counter.presentation.screens.forestry

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.forestry.counter.domain.model.station.Drainage
import com.forestry.counter.domain.model.station.Exposition
import com.forestry.counter.domain.model.station.Pierrosite
import com.forestry.counter.domain.model.station.PositionTopo
import com.forestry.counter.domain.model.station.StationObservation
import com.forestry.counter.domain.model.station.TestHCl
import com.forestry.counter.domain.model.station.TextureSol
import com.forestry.counter.domain.model.station.TypeHumus
import com.forestry.counter.domain.repository.TigeRepository
import com.forestry.counter.domain.repository.ParcelleRepository
import com.forestry.counter.domain.usecase.station.StationDiagnosticEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationDiagnosticScreen(
    parcelleId: String,
    tigeRepository: TigeRepository,
    parcelleRepository: ParcelleRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val tiges by tigeRepository.getTigesByParcelle(parcelleId).collectAsState(initial = emptyList())
    val parcelles by parcelleRepository.getAllParcelles().collectAsState(initial = emptyList())
    val parcelle = remember(parcelles, parcelleId) { parcelles.firstOrNull { p -> p.id == parcelleId } }

    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf("Topo / Sol", "Gradients", "Végétation", "Résultat")

    // ── GPS ──
    var gpsLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var gpsLon by rememberSaveable { mutableStateOf<Double?>(null) }
    var altitudeM by rememberSaveable { mutableStateOf("") }

    // ── Topo ──
    var commune by rememberSaveable { mutableStateOf("") }
    var pentePct by rememberSaveable { mutableStateOf("") }
    var exposition by rememberSaveable { mutableStateOf(Exposition.INCONNUE) }
    var positionTopo by rememberSaveable { mutableStateOf(PositionTopo.INCONNUE) }
    var distanceCours by rememberSaveable { mutableStateOf("") }

    // ── Pédologie ──
    var profondeurCm by rememberSaveable { mutableStateOf("") }
    var texture by rememberSaveable { mutableStateOf(TextureSol.INCONNUE) }
    var pierrosite by rememberSaveable { mutableStateOf(Pierrosite.FAIBLE) }
    var hydromorphieCm by rememberSaveable { mutableStateOf("") }
    var humus by rememberSaveable { mutableStateOf(TypeHumus.INCONNU) }
    var phEstime by rememberSaveable { mutableStateOf("") }
    var testHcl by rememberSaveable { mutableStateOf(TestHCl.NEGATIF) }
    var drainage by rememberSaveable { mutableStateOf(Drainage.NORMAL) }
    var rocheMere by rememberSaveable { mutableStateOf("") }

    // ── Gradients (1–5) ──
    var gradientHydrique by rememberSaveable { mutableStateOf(3) }
    var gradientTrophique by rememberSaveable { mutableStateOf(3) }
    var gradientLumineux by rememberSaveable { mutableStateOf(3) }
    var gradientHumique by rememberSaveable { mutableStateOf(3) }

    // ── Végétation ──
    var especesIndicatrices by rememberSaveable { mutableStateOf("") }
    var especesXerophiles by rememberSaveable { mutableStateOf(false) }
    var especesMesophiles by rememberSaveable { mutableStateOf(false) }
    var especesHygrophiles by rememberSaveable { mutableStateOf(false) }
    var notes by rememberSaveable { mutableStateOf("") }

    // GPS capture
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    loc?.let {
                        gpsLat = it.latitude
                        gpsLon = it.longitude
                        if (altitudeM.isBlank() && it.altitude > 0)
                            altitudeM = it.altitude.toInt().toString()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // ── Build observation & result from current state ──
    val currentObs by remember(
        gpsLat, gpsLon, altitudeM, commune, pentePct, exposition, positionTopo, distanceCours,
        profondeurCm, texture, pierrosite, hydromorphieCm, humus, phEstime, testHcl, drainage, rocheMere,
        gradientHydrique, gradientTrophique, gradientLumineux, gradientHumique,
        especesIndicatrices, especesXerophiles, especesMesophiles, especesHygrophiles, notes
    ) {
        derivedStateOf {
            StationObservation(
                parcelleId = parcelleId,
                latitude = gpsLat,
                longitude = gpsLon,
                altitudeM = altitudeM.toDoubleOrNull(),
                commune = commune,
                pentePct = pentePct.toDoubleOrNull(),
                exposition = exposition,
                positionTopo = positionTopo,
                distanceCourseauM = distanceCours.toDoubleOrNull(),
                profondeurSolCm = profondeurCm.toIntOrNull(),
                texture = texture,
                pierrosite = pierrosite,
                hydromorphieProfondeurCm = hydromorphieCm.toIntOrNull(),
                humus = humus,
                phEstime = phEstime.toDoubleOrNull(),
                testHcl = testHcl,
                drainage = drainage,
                rocheMere = rocheMere,
                gradientHydrique = gradientHydrique,
                gradientTrophique = gradientTrophique,
                gradientLumineux = gradientLumineux,
                gradientHumique = gradientHumique,
                especesIndicatrices = especesIndicatrices.split(",").map { it.trim() }.filter { it.isNotBlank() },
                especesXerophiles = especesXerophiles,
                especesMesophiles = especesMesophiles,
                especesHygrophiles = especesHygrophiles,
                notes = notes
            )
        }
    }

    val dendroCtx by remember(tiges) {
        derivedStateOf {
            StationDiagnosticEngine.computeDendroContext(tiges)
        }
    }
    val result by remember(currentObs, dendroCtx) {
        derivedStateOf {
            StationDiagnosticEngine.diagnose(currentObs, dendroCtx)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Diagnostic Station", fontWeight = FontWeight.Bold)
                        if (parcelle != null) {
                            Text(parcelle.name, style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF2E5902),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF2E5902),
                contentColor = Color.White,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = Color(0xFFAED581)
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = selectedTab == i,
                        onClick = { selectedTab = i },
                        text = { Text(title, fontSize = 11.sp) }
                    )
                }
            }

            when (selectedTab) {
                0 -> TopoSolTab(
                    gpsLat = gpsLat, gpsLon = gpsLon,
                    altitudeM = altitudeM, onAltitudeChange = { altitudeM = it },
                    commune = commune, onCommuneChange = { commune = it },
                    pentePct = pentePct, onPenteChange = { pentePct = it },
                    exposition = exposition, onExpositionChange = { exposition = it },
                    positionTopo = positionTopo, onPositionTopoChange = { positionTopo = it },
                    distanceCours = distanceCours, onDistanceCoursChange = { distanceCours = it },
                    profondeurCm = profondeurCm, onProfondeurChange = { profondeurCm = it },
                    texture = texture, onTextureChange = { texture = it },
                    pierrosite = pierrosite, onPierrositeChange = { pierrosite = it },
                    hydromorphieCm = hydromorphieCm, onHydromorphieChange = { hydromorphieCm = it },
                    humus = humus, onHumusChange = { humus = it },
                    phEstime = phEstime, onPhChange = { phEstime = it },
                    testHcl = testHcl, onTestHclChange = { testHcl = it },
                    drainage = drainage, onDrainageChange = { drainage = it },
                    rocheMere = rocheMere, onRocheMereChange = { rocheMere = it }
                )
                1 -> GradientsTab(
                    gradientHydrique = gradientHydrique, onHydriqueChange = { gradientHydrique = it },
                    gradientTrophique = gradientTrophique, onTrophiqueChange = { gradientTrophique = it },
                    gradientLumineux = gradientLumineux, onLumineuxChange = { gradientLumineux = it },
                    gradientHumique = gradientHumique, onHumiqueChange = { gradientHumique = it }
                )
                2 -> VegetationTab(
                    especesIndicatrices = especesIndicatrices, onEspecesChange = { especesIndicatrices = it },
                    especesXerophiles = especesXerophiles, onXerophilesChange = { especesXerophiles = it },
                    especesMesophiles = especesMesophiles, onMesophilesChange = { especesMesophiles = it },
                    especesHygrophiles = especesHygrophiles, onHygrophilesChange = { especesHygrophiles = it },
                    notes = notes, onNotesChange = { notes = it },
                    dendroCtx = dendroCtx,
                    nbTiges = tiges.size
                )
                3 -> ResultatTab(result = result, currentObs = currentObs, dendroCtx = dendroCtx)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tab 0 – Topo / Sol
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TopoSolTab(
    gpsLat: Double?, gpsLon: Double?,
    altitudeM: String, onAltitudeChange: (String) -> Unit,
    commune: String, onCommuneChange: (String) -> Unit,
    pentePct: String, onPenteChange: (String) -> Unit,
    exposition: Exposition, onExpositionChange: (Exposition) -> Unit,
    positionTopo: PositionTopo, onPositionTopoChange: (PositionTopo) -> Unit,
    distanceCours: String, onDistanceCoursChange: (String) -> Unit,
    profondeurCm: String, onProfondeurChange: (String) -> Unit,
    texture: TextureSol, onTextureChange: (TextureSol) -> Unit,
    pierrosite: Pierrosite, onPierrositeChange: (Pierrosite) -> Unit,
    hydromorphieCm: String, onHydromorphieChange: (String) -> Unit,
    humus: TypeHumus, onHumusChange: (TypeHumus) -> Unit,
    phEstime: String, onPhChange: (String) -> Unit,
    testHcl: TestHCl, onTestHclChange: (TestHCl) -> Unit,
    drainage: Drainage, onDrainageChange: (Drainage) -> Unit,
    rocheMere: String, onRocheMereChange: (String) -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Localisation
        StaCard(title = "Localisation", icon = Icons.Default.Landscape, color = Color(0xFF37474F)) {
            if (gpsLat != null)
                Text("GPS : ${String.format("%.5f", gpsLat)}°N, ${String.format("%.5f", gpsLon)}°E",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFF1B5E20))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = commune, onValueChange = onCommuneChange,
                    label = { Text("Commune") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = altitudeM, onValueChange = onAltitudeChange,
                    label = { Text("Altitude (m)") }, modifier = Modifier.weight(1f), singleLine = true)
            }
        }

        // Topographie
        StaCard(title = "Topographie", icon = Icons.Default.Landscape, color = Color(0xFF4E342E)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = pentePct, onValueChange = onPenteChange,
                    label = { Text("Pente (%)") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = distanceCours, onValueChange = onDistanceCoursChange,
                    label = { Text("Distance cours d'eau (m)") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Spacer(Modifier.height(8.dp))
            StaDropdown("Exposition", Exposition.entries, exposition,
                { it.labelFr }, onExpositionChange)
            Spacer(Modifier.height(8.dp))
            StaDropdown("Position topographique", PositionTopo.entries, positionTopo,
                { it.labelFr }, onPositionTopoChange)
        }

        // Pédologie
        StaCard(title = "Pédologie", icon = Icons.Default.Layers, color = Color(0xFF5D4037)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = profondeurCm, onValueChange = onProfondeurChange,
                    label = { Text("Profondeur (cm)") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = phEstime, onValueChange = onPhChange,
                    label = { Text("pH estimé") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Spacer(Modifier.height(8.dp))
            StaDropdown("Texture", TextureSol.entries, texture, { it.labelFr }, onTextureChange)
            Spacer(Modifier.height(8.dp))
            StaDropdown("Pierrosité", Pierrosite.entries, pierrosite, { it.labelFr }, onPierrositeChange)
            Spacer(Modifier.height(8.dp))
            StaDropdown("Type d'humus", TypeHumus.entries, humus, { it.labelFr }, onHumusChange)
            Spacer(Modifier.height(8.dp))
            StaDropdown("Test HCl (effervescence)", TestHCl.entries, testHcl, { it.labelFr }, onTestHclChange)
            Spacer(Modifier.height(8.dp))
            StaDropdown("Drainage", Drainage.entries, drainage, { it.labelFr }, onDrainageChange)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = hydromorphieCm, onValueChange = onHydromorphieChange,
                label = { Text("Profondeur hydromorphie (cm)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = rocheMere, onValueChange = onRocheMereChange,
                label = { Text("Roche mère / matériau parental") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tab 1 – Gradients
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GradientsTab(
    gradientHydrique: Int, onHydriqueChange: (Int) -> Unit,
    gradientTrophique: Int, onTrophiqueChange: (Int) -> Unit,
    gradientLumineux: Int, onLumineuxChange: (Int) -> Unit,
    gradientHumique: Int, onHumiqueChange: (Int) -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Gradients écologiques", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold)
        Text("Évaluer chaque gradient sur une échelle de 1 (extrême sec/pauvre/ombragé/acide) à 5 (extrême humide/riche/lumineux/calcique).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        GradientSlider(
            title = "Gradient hydrique",
            value = gradientHydrique,
            onChange = onHydriqueChange,
            minLabel = "Très sec\n(xérophile)",
            maxLabel = "Très humide\n(hygrophile)",
            color = Color(0xFF1565C0),
            icon = Icons.Default.WaterDrop
        )
        GradientSlider(
            title = "Gradient trophique",
            value = gradientTrophique,
            onChange = onTrophiqueChange,
            minLabel = "Oligotrophe\n(pauvre)",
            maxLabel = "Eutrophe\n(très riche)",
            color = Color(0xFF2E7D32),
            icon = Icons.Default.Layers
        )
        GradientSlider(
            title = "Gradient lumineux",
            value = gradientLumineux,
            onChange = onLumineuxChange,
            minLabel = "Très ombragé\n(sciaphile)",
            maxLabel = "Plein soleil\n(héliophile)",
            color = Color(0xFFF57F17),
            icon = Icons.Default.Landscape
        )
        GradientSlider(
            title = "Gradient humique",
            value = gradientHumique,
            onChange = onHumiqueChange,
            minLabel = "Mor\n(humus brut)",
            maxLabel = "Mull calcique\n(humus doux)",
            color = Color(0xFF4E342E),
            icon = Icons.Default.Forest
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun GradientSlider(
    title: String,
    value: Int,
    onChange: (Int) -> Unit,
    minLabel: String,
    maxLabel: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium)
                }
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$value", color = Color.White, fontWeight = FontWeight.ExtraBold)
                }
            }
            Column(Modifier.padding(12.dp)) {
                Slider(
                    value = value.toFloat(),
                    onValueChange = { onChange(it.toInt()) },
                    valueRange = 1f..5f,
                    steps = 3,
                    colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color.copy(0.7f))
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("1 – $minLabel", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("5 – $maxLabel", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
                // Visual 1–5 bullets
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    (1..5).forEach { i ->
                        Box(
                            Modifier.size(if (i == value) 16.dp else 10.dp)
                                .clip(CircleShape)
                                .background(if (i == value) color else color.copy(alpha = 0.2f))
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tab 2 – Végétation
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VegetationTab(
    especesIndicatrices: String, onEspecesChange: (String) -> Unit,
    especesXerophiles: Boolean, onXerophilesChange: (Boolean) -> Unit,
    especesMesophiles: Boolean, onMesophilesChange: (Boolean) -> Unit,
    especesHygrophiles: Boolean, onHygrophilesChange: (Boolean) -> Unit,
    notes: String, onNotesChange: (String) -> Unit,
    dendroCtx: StationDiagnosticEngine.DendroContext,
    nbTiges: Int
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Dendro context from inventory
        if (nbTiges > 0) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, null, tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Données dendro intégrées ($nbTiges tiges)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        DendroKpi("G (m²/ha)", dendroCtx.gHa?.let { String.format("%.1f", it) } ?: "–")
                        DendroKpi("Dg (cm)", dendroCtx.dg?.let { String.format("%.1f", it) } ?: "–")
                        DendroKpi("Hd (m)", dendroCtx.hd?.let { String.format("%.1f", it) } ?: "–")
                        DendroKpi("Élancement", dendroCtx.slenderness?.let { String.format("%.0f", it) } ?: "–")
                    }
                    if (dendroCtx.classesDiam.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Classes : ${dendroCtx.classesDiam.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32))
                    }
                }
            }
        }

        // Espèces indicatrices
        StaCard(title = "Espèces indicatrices", icon = Icons.Default.Forest, color = Color(0xFF2E7D32)) {
            OutlinedTextField(
                value = especesIndicatrices, onValueChange = onEspecesChange,
                label = { Text("Espèces (séparées par virgules)") },
                modifier = Modifier.fillMaxWidth(), minLines = 3,
                placeholder = { Text("Ex: Fougère aigle, Myrtille, Jacinthe des bois...") }
            )
            Spacer(Modifier.height(8.dp))
            Text("Groupes écologiques indicateurs :", style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StaCheckChip("Xérophiles", especesXerophiles, onXerophilesChange, Color(0xFFF57F17))
                StaCheckChip("Mésophiles", especesMesophiles, onMesophilesChange, Color(0xFF2E7D32))
                StaCheckChip("Hygrophiles", especesHygrophiles, onHygrophilesChange, Color(0xFF1565C0))
            }
        }

        // Notes
        OutlinedTextField(value = notes, onValueChange = onNotesChange,
            label = { Text("Observations et notes libres") },
            modifier = Modifier.fillMaxWidth(), minLines = 4)

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DendroKpi(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleSmall,
            color = Color(0xFF1B5E20))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StaCheckChip(
    label: String, checked: Boolean,
    onCheckedChange: (Boolean) -> Unit, color: Color
) {
    FilterChip(
        selected = checked, onClick = { onCheckedChange(!checked) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(alpha = 0.2f),
            selectedLabelColor = color
        )
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tab 3 – Résultat
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ResultatTab(
    result: StationDiagnosticEngine.StationResult,
    currentObs: StationObservation,
    dendroCtx: StationDiagnosticEngine.DendroContext
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Type de station ──
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Forest, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Type de station", color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(8.dp))
                Text(result.typeStation, color = Color.White, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    GradientBadge("Hydrique", result.gradientHydriqueFinal, Color(0xFF1565C0))
                    GradientBadge("Trophique", result.gradientTrophiqueFinal, Color(0xFF2E7D32))
                }
            }
        }

        // ── Confiance ──
        val confColor = when (result.confidence) {
            StationDiagnosticEngine.DiagConfidenceStation.FORTE -> Color(0xFF2E7D32)
            StationDiagnosticEngine.DiagConfidenceStation.MOYENNE -> Color(0xFFF57F17)
            StationDiagnosticEngine.DiagConfidenceStation.FAIBLE -> Color(0xFFC62828)
        }
        Surface(
            color = confColor.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null, tint = confColor, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Confiance du diagnostic : ${result.confidence.labelFr}",
                    style = MaterialTheme.typography.bodySmall, color = confColor,
                    fontWeight = FontWeight.SemiBold)
            }
        }

        // ── Alertes ──
        if (result.alertes.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFE65100),
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Alertes d'incohérence", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                    }
                    result.alertes.forEach { alerte ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text("⚠ ", color = Color(0xFFE65100))
                            Text(alerte, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // ── Atouts / Contraintes ──
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (result.atouts.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Atouts", style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        }
                        result.atouts.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            if (result.contraintes.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = Color(0xFFC62828),
                                modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Contraintes", style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                        }
                        result.contraintes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }

        // ── Essences recommandées ──
        if (result.recommendedEssences.isNotEmpty() || result.discouragedEssences.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Adéquation des essences", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold)
                    if (result.recommendedEssences.isNotEmpty()) {
                        Text("Recommandées :", style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                        Text(result.recommendedEssences.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall, color = Color(0xFF1B5E20))
                    }
                    if (result.discouragedEssences.isNotEmpty()) {
                        Text("Déconseillées sur cette station :", style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFC62828), fontWeight = FontWeight.SemiBold)
                        Text(result.discouragedEssences.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall, color = Color(0xFF8B0000))
                    }
                }
            }
        }

        // ── Synthèse ──
        Card(
            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9))
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Synthèse automatique", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                Spacer(Modifier.height(8.dp))
                Text(result.syntheseTextuelle, style = MaterialTheme.typography.bodySmall,
                    lineHeight = 20.sp)
            }
        }

        // ── Risques ──
        if (result.risqueDepiecement || result.risqueEngorgement) {
            Card(
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4))
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Risques identifiés", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = Color(0xFFF57F17))
                    if (result.risqueDepiecement)
                        Text("⚡ Risque de dépérissement par stress hydrique (station sèche basse altitude)",
                            style = MaterialTheme.typography.bodySmall)
                    if (result.risqueEngorgement)
                        Text("💧 Risque d'engorgement / asphyxie racinaire (hydromorphie superficielle)",
                            style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun GradientBadge(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text("$value", color = Color.White, fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleSmall)
        }
        Text(label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helper composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StaCard(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> StaDropdown(
    label: String,
    entries: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = labelOf(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(labelOf(entry)) },
                    onClick = { onSelect(entry); expanded = false }
                )
            }
        }
    }
}
