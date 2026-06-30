package com.forestry.counter.presentation.screens.forestry

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forestry.counter.R
import com.forestry.counter.domain.model.ripisylve.*
import com.forestry.counter.domain.usecase.ripisylve.RipisylveGestionEngine
import com.forestry.counter.domain.usecase.ripisylve.RipisylveScorer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RipisylveDiagnosticScreen(
    parcelleId: String,
    onNavigateBack: () -> Unit
) {
    var obs by remember { mutableStateOf(RipisylveObservation(parcelleId = parcelleId)) }
    val score by remember(obs) { derivedStateOf { RipisylveScorer.score(obs) } }
    val diag by remember(score, obs) { derivedStateOf { RipisylveGestionEngine.buildDiagnostic(obs, score) } }
    var showResults by remember { mutableStateOf(false) }
    var expandedSection by remember { mutableStateOf<String?>("continuite") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ripi_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    TextButton(onClick = { showResults = true }) {
                        Text(stringResource(R.string.ripi_calculate), fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showResults) {
                item { ResultCard(score, diag) }
                item { ActionsCard(diag) }
                if (diag.invasivesDetectees.isNotEmpty()) {
                    item { InvasivesCard(diag.invasivesDetectees) }
                }
                item {
                    OutlinedButton(
                        onClick = { showResults = false },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.ripi_modify)) }
                }
            } else {
                item {
                    SectionCard(
                        title = stringResource(R.string.ripi_section_continuite),
                        expanded = expandedSection == "continuite",
                        onToggle = { expandedSection = if (expandedSection == "continuite") null else "continuite" }
                    ) {
                        ContinuiteSection(obs) { obs = it }
                    }
                }
                item {
                    SectionCard(
                        title = stringResource(R.string.ripi_section_largeur),
                        expanded = expandedSection == "largeur",
                        onToggle = { expandedSection = if (expandedSection == "largeur") null else "largeur" }
                    ) {
                        LargeurSection(obs) { obs = it }
                    }
                }
                item {
                    SectionCard(
                        title = stringResource(R.string.ripi_section_strates),
                        expanded = expandedSection == "strates",
                        onToggle = { expandedSection = if (expandedSection == "strates") null else "strates" }
                    ) {
                        StratesSection(obs) { obs = it }
                    }
                }
                item {
                    SectionCard(
                        title = stringResource(R.string.ripi_section_diversite),
                        expanded = expandedSection == "diversite",
                        onToggle = { expandedSection = if (expandedSection == "diversite") null else "diversite" }
                    ) {
                        DiversiteSection(obs) { obs = it }
                    }
                }
                item {
                    SectionCard(
                        title = stringResource(R.string.ripi_section_penalites),
                        expanded = expandedSection == "penalites",
                        onToggle = { expandedSection = if (expandedSection == "penalites") null else "penalites" }
                    ) {
                        PenalitesSection(obs) { obs = it }
                    }
                }
                item {
                    ScorePreviewCard(score)
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(0.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.cd_collapse else R.string.cd_expand)
                )
            }
            AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun ContinuiteSection(obs: RipisylveObservation, onChange: (RipisylveObservation) -> Unit) {
    Text(stringResource(R.string.ripi_continuite_label), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    Slider(
        value = obs.continuitePct.toFloat(),
        onValueChange = { onChange(obs.copy(continuitePct = it.toDouble())) },
        valueRange = 0f..100f,
        steps = 9
    )
    Text("${obs.continuitePct.toInt()}%", fontWeight = FontWeight.Bold,
        color = when {
            obs.continuitePct >= 75 -> Color(0xFF2E7D32)
            obs.continuitePct >= 50 -> Color(0xFF388E3C)
            obs.continuitePct >= 25 -> Color(0xFFF57C00)
            else -> Color(0xFFD32F2F)
        })
}

@Composable
private fun LargeurSection(obs: RipisylveObservation, onChange: (RipisylveObservation) -> Unit) {
    LargeurMode.entries.forEach { mode ->
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onChange(obs.copy(largeurMode = mode)) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = obs.largeurMode == mode, onClick = { onChange(obs.copy(largeurMode = mode)) })
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(mode.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.ripi_points, mode.points), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StratesSection(obs: RipisylveObservation, onChange: (RipisylveObservation) -> Unit) {
    listOf(
        Triple(stringResource(R.string.ripi_strate_herbacee), obs.strateHerbacee) { v: Boolean -> onChange(obs.copy(strateHerbacee = v)) },
        Triple(stringResource(R.string.ripi_strate_arbustive), obs.strateArbustive) { v: Boolean -> onChange(obs.copy(strateArbustive = v)) },
        Triple(stringResource(R.string.ripi_strate_arborescente), obs.strateArborescente) { v: Boolean -> onChange(obs.copy(strateArborescente = v)) }
    ).forEach { (label, checked, onCheck) ->
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onCheck(!checked) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = onCheck)
            Text(label, modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DiversiteSection(obs: RipisylveObservation, onChange: (RipisylveObservation) -> Unit) {
    Text(stringResource(R.string.ripi_nb_especes), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconButton(onClick = { if (obs.nbEspecesObservees > 0) onChange(obs.copy(nbEspecesObservees = obs.nbEspecesObservees - 1)) }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.cd_remove))
        }
        Text("${obs.nbEspecesObservees}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        IconButton(onClick = { onChange(obs.copy(nbEspecesObservees = obs.nbEspecesObservees + 1)) }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add))
        }
    }
    listOf(
        Triple(stringResource(R.string.ripi_tpb), obs.hasTresPetitBois) { v: Boolean -> onChange(obs.copy(hasTresPetitBois = v)) },
        Triple(stringResource(R.string.ripi_petit_bois), obs.hasPetitBois) { v: Boolean -> onChange(obs.copy(hasPetitBois = v)) },
        Triple(stringResource(R.string.ripi_moyen_bois), obs.hasMoyenBois) { v: Boolean -> onChange(obs.copy(hasMoyenBois = v)) },
        Triple(stringResource(R.string.ripi_gros_bois), obs.hasGrosBois) { v: Boolean -> onChange(obs.copy(hasGrosBois = v)) }
    ).forEach { (label, checked, onCheck) ->
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onCheck(!checked) }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = onCheck)
            Text(label, modifier = Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PenalitesSection(obs: RipisylveObservation, onChange: (RipisylveObservation) -> Unit) {
    Text(stringResource(R.string.ripi_invasives_label), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Slider(
        value = obs.invasivesPct.toFloat(),
        onValueChange = { onChange(obs.copy(invasivesPct = it.toDouble())) },
        valueRange = 0f..100f, steps = 9
    )
    Text("${obs.invasivesPct.toInt()}%", fontWeight = FontWeight.Bold,
        color = if (obs.invasivesPct > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.ripi_sanitaire_label), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Slider(
        value = obs.sanitairePct.toFloat(),
        onValueChange = { onChange(obs.copy(sanitairePct = it.toDouble())) },
        valueRange = 0f..100f, steps = 9
    )
    Text("${obs.sanitairePct.toInt()}%", fontWeight = FontWeight.Bold,
        color = if (obs.sanitairePct > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.ripi_stabilite_label), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Slider(
        value = obs.stabilitePct.toFloat(),
        onValueChange = { onChange(obs.copy(stabilitePct = it.toDouble())) },
        valueRange = 0f..100f, steps = 9
    )
    Text("${obs.stabilitePct.toInt()}%", fontWeight = FontWeight.Bold,
        color = if (obs.stabilitePct > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
}

@Composable
private fun ScorePreviewCard(score: RipisylveScore) {
    val color = Color(score.fonctionnalite.colorHex)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(stringResource(R.string.ripi_score_estime), style = MaterialTheme.typography.bodySmall)
                Text("${score.scoreTotal}/100", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
                Text(score.fonctionnalite.labelFr, color = color, fontWeight = FontWeight.Medium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.ripi_positif, score.scorePositif), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.ripi_penalite, score.scorePenalite), style = MaterialTheme.typography.bodySmall,
                    color = if (score.scorePenalite < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun ResultCard(score: RipisylveScore, diag: RipisylveGestionEngine.DiagnosticFonctionnel) {
    val color = Color(diag.niveauFonctionnalite.color)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.ripi_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(color),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${score.scoreTotal}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(diag.niveauFonctionnalite.label, fontWeight = FontWeight.Bold, color = color)
                    Text(score.consigneGestion.labelFr, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(diag.syntheseFr, style = MaterialTheme.typography.bodySmall)
            if (diag.pointsForts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.ripi_points_forts), fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32))
                diag.pointsForts.forEach { Text("✓ $it", style = MaterialTheme.typography.bodySmall, color = Color(0xFF388E3C)) }
            }
            if (diag.pointsFaibles.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.ripi_points_faibles), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                diag.pointsFaibles.forEach { Text("✗ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun ActionsCard(diag: RipisylveGestionEngine.DiagnosticFonctionnel) {
    if (diag.actions.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.ripi_plan_action), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            diag.actions.forEach { action ->
                val urgenceColor = when (action.urgence) {
                    RipisylveGestionEngine.Urgence.IMMEDIATE  -> Color(0xFFD32F2F)
                    RipisylveGestionEngine.Urgence.COURT_TERME  -> Color(0xFFF57C00)
                    RipisylveGestionEngine.Urgence.MOYEN_TERME  -> Color(0xFF1976D2)
                    RipisylveGestionEngine.Urgence.LONG_TERME   -> Color(0xFF388E3C)
                }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(urgenceColor).align(Alignment.Top).padding(top = 6.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(action.titre, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(action.description, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(action.urgence.name.replace("_", " "), fontSize = 11.sp, color = urgenceColor)
                    }
                }
                if (diag.actions.last() != action) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun InvasivesCard(invasives: List<RipisylveGestionEngine.InvasiveInfo>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.ripi_invasives_detectees), fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.height(8.dp))
            invasives.forEach { inv ->
                Text("${inv.nomFr} (${inv.nomLatin})", fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onErrorContainer)
                Text(inv.actionPrioritaire, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f))
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
