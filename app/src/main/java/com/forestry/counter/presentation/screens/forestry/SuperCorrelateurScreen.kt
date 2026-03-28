package com.forestry.counter.presentation.screens.forestry

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forestry.counter.domain.model.ClimateZone
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.model.ripisylve.RipisylveObservation
import com.forestry.counter.domain.model.station.StationObservation
import com.forestry.counter.domain.repository.RipisylveRepository
import com.forestry.counter.domain.repository.StationRepository
import com.forestry.counter.domain.repository.TigeRepository
import com.forestry.counter.domain.usecase.autecology.CompatibilityLevel
import com.forestry.counter.domain.usecase.correlateur.DRIASDatabase
import com.forestry.counter.domain.usecase.correlateur.SuperCorrelateurEngine
import com.forestry.counter.domain.usecase.correlateur.SuperCorrelateurEngine.Urgence
import com.forestry.counter.domain.usecase.correlateur.SuperCorrelateurEngine.NiveauAlerte
import com.forestry.counter.domain.usecase.correlateur.SuperCorrelateurEngine.ZoneFutureCompatibility
import androidx.compose.ui.res.stringResource
import com.forestry.counter.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperCorrelateurScreen(
    parcelleId: String,
    tigeRepository: TigeRepository,
    stationRepository: StationRepository,
    ripisylveRepository: RipisylveRepository,
    surfaceHa: Double = 1.0,
    onNavigateBack: () -> Unit,
    onNavigateToStation: () -> Unit,
    onNavigateToRipisylve: () -> Unit
) {
    var result by remember { mutableStateOf<SuperCorrelateurEngine.CorrelateurResult?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(parcelleId) {
        withContext(Dispatchers.IO) {
            val tiges = tigeRepository.getTigesByParcelle(parcelleId).firstOrNull() ?: emptyList()
            val stations = stationRepository.getByParcelle(parcelleId).firstOrNull() ?: emptyList()
            val ripisylves = ripisylveRepository.getByParcelle(parcelleId).firstOrNull() ?: emptyList()
            val station = stations.lastOrNull()
            val ripisylve = ripisylves.lastOrNull()
            val computed = SuperCorrelateurEngine.correlate(
                tiges        = tiges,
                surfaceHa    = surfaceHa,
                station      = station,
                ripisylve    = ripisylve,
                lat          = station?.latitude,
                lon          = station?.longitude,
                altM         = station?.altitudeM
            )
            withContext(Dispatchers.Main) {
                result = computed
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.super_correlateur_title), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.super_correlateur_subtitle), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.super_correlateur_loading), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            val res = result
            if (res == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.super_correlateur_error), color = MaterialTheme.colorScheme.error)
                }
            } else {
                CorrelateurContent(
                    result = res,
                    modifier = Modifier.padding(padding),
                    onNavigateToStation = onNavigateToStation,
                    onNavigateToRipisylve = onNavigateToRipisylve
                )
            }
        }
    }
}

@Composable
private fun CorrelateurContent(
    result: SuperCorrelateurEngine.CorrelateurResult,
    modifier: Modifier = Modifier,
    onNavigateToStation: () -> Unit,
    onNavigateToRipisylve: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Résumé sources de données
        item { DataSourcesCard(result.datasources) }

        // ── Score de résilience global
        item { ResilienceScoreCard(result.score, result.climateZone) }

        // ── Projection DRIAS 2050
        result.driasProjection?.let { proj ->
            item { DRIASProjectionCard(proj) }
        }

        // ── Alertes sanitaires critiques (en haut si présentes)
        val critiques = result.alertesSanitaires.filter { it.niveau == NiveauAlerte.CRITIQUE }
        if (critiques.isNotEmpty()) {
            item {
                Text(stringResource(R.string.super_correlateur_critical_alerts), style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp))
            }
            items(critiques) { alerte -> AlerteSanitaireCard(alerte) }
        }

        // ── Plan d'action (actions immédiates d'abord)
        if (result.planActionSylvicole.isNotEmpty()) {
            item {
                SectionHeader("Plan d'action sylvicole", Icons.Default.Assignment)
            }
            items(result.planActionSylvicole) { action -> ActionCard(action) }
        }

        // ── Bilan par essence
        if (result.essencesBilan.isNotEmpty()) {
            item { SectionHeader("Bilan par essence & résilience 2050", Icons.Default.Forest) }
            items(result.essencesBilan) { bilan -> EssenceBilanCard(bilan) }
        }

        // ── Essences recommandées futur
        if (result.essencesRecommandeesFutur.isNotEmpty()) {
            item { SectionHeader("Essences recommandées pour l'avenir", Icons.Default.EmojiNature) }
            items(result.essencesRecommandeesFutur.take(6)) { reco -> EssenceRecoCard(reco) }
        }

        // ── Risques climatiques
        if (result.risquesClimatiques.isNotEmpty()) {
            item { SectionHeader("Risques climatiques identifiés", Icons.Default.Warning) }
            items(result.risquesClimatiques) { risque -> RisqueCard(risque) }
        }

        // ── Autres alertes sanitaires
        val autresAlertes = result.alertesSanitaires.filter { it.niveau != NiveauAlerte.CRITIQUE }
        if (autresAlertes.isNotEmpty()) {
            item { SectionHeader("Alertes sanitaires", Icons.Default.BugReport) }
            items(autresAlertes) { alerte -> AlerteSanitaireCard(alerte) }
        }

        // ── Synthèse
        item { SyntheseCard(result.synthese, result.score, result.planActionSylvicole.size, result.alertesSanitaires.size) }

        // ── Accès rapide diagnostics
        if (!result.datasources.hasStation || !result.datasources.hasRipisylve) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.super_correlateur_improve_title), style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.super_correlateur_improve_desc),
                            style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!result.datasources.hasStation) {
                                OutlinedButton(onClick = onNavigateToStation, modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.diag_station_btn), fontSize = 12.sp)
                                }
                            }
                            if (!result.datasources.hasRipisylve) {
                                OutlinedButton(onClick = onNavigateToRipisylve, modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.diag_ripisylve_btn), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Score de résilience
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ResilienceScoreCard(score: SuperCorrelateurEngine.ResilienceScore, zone: ClimateZone) {
    val globalColor = resilienceColor(score.global)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = globalColor.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(stringResource(R.string.super_correlateur_resilience_score), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text(score.labelFr, style = MaterialTheme.typography.bodyMedium,
                        color = globalColor, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape)
                        .background(globalColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${score.global}", style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold, color = globalColor)
                }
            }

            LinearProgressIndicator(
                progress = { score.global / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = globalColor,
                trackColor = globalColor.copy(alpha = 0.2f)
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                score.dendro?.let {
                    ScoreChip("Dendro", it, Modifier.weight(1f))
                }
                score.station?.let {
                    ScoreChip("Station", it, Modifier.weight(1f))
                }
                score.ripisylve?.let {
                    ScoreChip("Ripisylve", it, Modifier.weight(1f))
                }
                ScoreChip("Climat", score.climatiqueAdaptation, Modifier.weight(1f))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.LocationOn, null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.super_correlateur_zone_format, zone.labelFr), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ScoreChip(label: String, value: Int, modifier: Modifier = Modifier) {
    val color = resilienceColor(value)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Column(
            Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("$value", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Bilan essence
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EssenceBilanCard(bilan: SuperCorrelateurEngine.EssenceBilan) {
    var expanded by remember { mutableStateOf(false) }

    val futureColor = Color(bilan.futureZoneCompatibility.colorHex)
    val compatColor = when (bilan.compatibility) {
        CompatibilityLevel.OPTIMUM      -> Color(0xFF2E7D32)
        CompatibilityLevel.TOLERATED    -> Color(0xFFF9A825)
        CompatibilityLevel.INCOMPATIBLE -> Color(0xFFC62828)
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.animateContentSize(tween(200)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(futureColor))
                    Text(bilan.nameFr, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPill(
                    text = "${bilan.nTiges} tiges (${bilan.pctBasalArea.toInt()}% G)",
                    color = MaterialTheme.colorScheme.primary
                )
                StatusPill(
                    text = bilan.futureZoneCompatibility.label,
                    color = futureColor
                )
                StatusPill(
                    text = "CC ${bilan.climateChangeResilience}/5",
                    color = resilienceColor(bilan.climateChangeResilience * 20)
                )
            }

            if (expanded) {
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                StatusPill("Station : ${bilan.compatibility.label}", color = compatColor,
                    modifier = Modifier.fillMaxWidth())
                if (bilan.alertes.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        bilan.alertes.take(3).forEach { alert ->
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Info, null,
                                    modifier = Modifier.size(14.dp).padding(top = 2.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(alert, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Recommandation future
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EssenceRecoCard(reco: SuperCorrelateurEngine.EssenceRecommandation) {
    val priorityColor = when (reco.priority) {
        1    -> Color(0xFF1565C0)
        2    -> Color(0xFF2E7D32)
        else -> Color(0xFF4CAF50)
    }
    val priorityLabel = when (reco.priority) {
        1    -> "Priorité 1"
        2    -> "Priorité 2"
        else -> "Opportuniste"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = priorityColor.copy(alpha = 0.06f))
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(priorityColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text("${reco.priority}", fontWeight = FontWeight.ExtraBold, color = priorityColor)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(reco.nameFr, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold)
                    StatusPill(priorityLabel, color = priorityColor)
                }
                StatusPill("Résilience CC : ${reco.climateResilience}/5",
                    color = resilienceColor(reco.climateResilience * 20))
                Text(reco.rationale, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Plan d'action
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActionCard(action: SuperCorrelateurEngine.ActionSylvicole) {
    val urgenceColor = when (action.urgence) {
        Urgence.IMMEDIATE    -> Color(0xFFC62828)
        Urgence.COURT_TERME  -> Color(0xFFEF6C00)
        Urgence.MOYEN_TERME  -> Color(0xFFF9A825)
        Urgence.LONG_TERME   -> Color(0xFF2E7D32)
    }
    val domaineIcon = when (action.domaine) {
        SuperCorrelateurEngine.DomaineSylvicole.ECLAIRCIE     -> Icons.Default.ContentCut
        SuperCorrelateurEngine.DomaineSylvicole.REBOISEMENT   -> Icons.Default.Forest
        SuperCorrelateurEngine.DomaineSylvicole.PROTECTION    -> Icons.Default.Shield
        SuperCorrelateurEngine.DomaineSylvicole.RIPISYLVE     -> Icons.Default.Water
        SuperCorrelateurEngine.DomaineSylvicole.SOLS          -> Icons.Default.Layers
        SuperCorrelateurEngine.DomaineSylvicole.BIODIVERSITE  -> Icons.Default.Pets
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = urgenceColor.copy(alpha = 0.06f))
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(domaineIcon, null,
                modifier = Modifier.size(32.dp).padding(top = 2.dp),
                tint = urgenceColor)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(action.titre, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    StatusPill(action.urgence.label, color = urgenceColor)
                }
                Text(action.domaine.label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(action.description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Risque climatique
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RisqueCard(risque: com.forestry.counter.domain.usecase.correlateur.RisqueClimatique) {
    var expanded by remember { mutableStateOf(false) }
    val color = Color(risque.niveau.colorHex)

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.animateContentSize(tween(200)),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.06f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Warning, null, modifier = Modifier.size(18.dp), tint = color)
                    Text(risque.type, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(risque.niveau.label, color = color)
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
            Text(stringResource(R.string.super_correlateur_horizon_format, risque.horizon), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (expanded) {
                Text(risque.description, style = MaterialTheme.typography.bodySmall)
                if (risque.essencesConcernees.isNotEmpty()) {
                    Text(stringResource(R.string.super_correlateur_essences_format, risque.essencesConcernees.joinToString(", ")),
                        style = MaterialTheme.typography.labelSmall, color = color)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Alerte sanitaire
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AlerteSanitaireCard(alerte: SuperCorrelateurEngine.AlerteSanitaire) {
    val color = Color(alerte.niveau.colorHex)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.BugReport, null,
                modifier = Modifier.size(24.dp),
                tint = color)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(alerte.titre, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    StatusPill(alerte.niveau.label, color = color)
                }
                Text(alerte.description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (alerte.essencesConcernees.isNotEmpty()) {
                    Text(stringResource(R.string.super_correlateur_concerns_format, alerte.essencesConcernees.joinToString(", ")),
                        style = MaterialTheme.typography.labelSmall, color = color)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Sources de données
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DataSourcesCard(ds: SuperCorrelateurEngine.DataSources) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.super_correlateur_data_sources), style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.super_correlateur_completeness_format, ds.completeness), style = MaterialTheme.typography.labelSmall,
                    color = resilienceColor(ds.completeness))
            }
            LinearProgressIndicator(
                progress = { ds.completeness / 100f },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = resilienceColor(ds.completeness)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DataSourceChip("Tiges (${ds.nbTiges})", ds.hasDendro, Icons.Default.Forest, Modifier.weight(1f))
                DataSourceChip("Station", ds.hasStation, Icons.Default.Landscape, Modifier.weight(1f))
                DataSourceChip("Ripisylve", ds.hasRipisylve, Icons.Default.Water, Modifier.weight(1f))
                DataSourceChip("GPS", ds.hasGPS, Icons.Default.LocationOn, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DataSourceChip(label: String, active: Boolean, icon: ImageVector, modifier: Modifier = Modifier) {
    val color = if (active) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.1f)) {
        Column(
            Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Synthèse
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SyntheseCard(
    synthese: String,
    score: SuperCorrelateurEngine.ResilienceScore,
    actionCount: Int,
    alerteCount: Int
) {
    val color = resilienceColor(score.global)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Summarize, null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.super_correlateur_synthese_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text(synthese, style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${score.global}/100", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = color)
                        Text(stringResource(R.string.super_correlateur_resilience_label), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (actionCount > 0) {
                    Surface(color = Color(0xFFEF6C00).copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$actionCount", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, color = Color(0xFFEF6C00))
                            Text(stringResource(R.string.super_correlateur_actions_label), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (alerteCount > 0) {
                    Surface(color = Color(0xFFC62828).copy(alpha = 0.10f), shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$alerteCount", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                            Text(stringResource(R.string.super_correlateur_alertes_label), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Carte projections DRIAS 2050
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DRIASProjectionCard(proj: DRIASDatabase.DRIASProjection) {
    val droughtColor = Color(proj.droughtRisk2050.colorHex)
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.animateContentSize(tween(200)),
        colors = CardDefaults.cardColors(containerColor = droughtColor.copy(alpha = 0.06f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.WbSunny, null, tint = droughtColor, modifier = Modifier.size(20.dp))
                    Column {
                        Text(stringResource(R.string.drias_projections_title), style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.drias_scenario_format, proj.zone.labelFr),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(proj.droughtRisk2050.labelFr, color = droughtColor)
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DRIASMetricChip(
                    label = stringResource(R.string.drias_delta_t_2050),
                    value = "+${proj.deltaT2050_ssp585}°C",
                    color = Color(0xFFEF6C00),
                    modifier = Modifier.weight(1f)
                )
                DRIASMetricChip(
                    label = stringResource(R.string.drias_precip_summer),
                    value = "${proj.deltaPsummer2050_ssp585.toInt()}%",
                    color = if (proj.deltaPsummer2050_ssp585 < 0) Color(0xFFC62828) else Color(0xFF1565C0),
                    modifier = Modifier.weight(1f)
                )
                DRIASMetricChip(
                    label = stringResource(R.string.drias_drought_days),
                    value = "${proj.droughtDays2050} j/an",
                    color = droughtColor,
                    modifier = Modifier.weight(1f)
                )
                DRIASMetricChip(
                    label = stringResource(R.string.drias_extreme_heat),
                    value = "${proj.extremeHeatDays2050} j/an",
                    color = Color(0xFFC62828),
                    modifier = Modifier.weight(1f)
                )
            }

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Text(proj.syntheseTexte, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DRIASMetricChip(
                        label = stringResource(R.string.drias_fire_risk),
                        value = "${proj.fireRisk2050}/5",
                        color = if (proj.fireRisk2050 >= 4) Color(0xFFC62828) else Color(0xFFF9A825),
                        modifier = Modifier.weight(1f)
                    )
                    DRIASMetricChip(
                        label = stringResource(R.string.drias_delta_t_2100),
                        value = "+${proj.deltaT2100_ssp585}°C",
                        color = Color(0xFFC62828),
                        modifier = Modifier.weight(1f)
                    )
                    proj.snowpackReduction2050Pct?.let { snow ->
                        DRIASMetricChip(
                            label = stringResource(R.string.drias_snow_2050),
                            value = "-$snow%",
                            color = Color(0xFF1565C0),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Text(
                    stringResource(R.string.drias_source_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                )
            }
        }
    }
}

@Composable
private fun DRIASMetricChip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.10f)) {
        Column(
            Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Utilitaires
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.12f)) {
        Text(
            text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
            color = color, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

private fun resilienceColor(score: Int): Color = when {
    score >= 80 -> Color(0xFF2E7D32)
    score >= 60 -> Color(0xFF8BC34A)
    score >= 40 -> Color(0xFFF9A825)
    score >= 20 -> Color(0xFFEF6C00)
    else        -> Color(0xFFC62828)
}
