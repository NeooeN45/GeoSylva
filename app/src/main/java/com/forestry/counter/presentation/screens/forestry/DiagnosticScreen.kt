package com.forestry.counter.presentation.screens.forestry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forestry.counter.data.local.entity.DiagnosticSylvicoleEntity
import com.forestry.counter.domain.diagnostic.EssenceSuitabilityScorer
import com.forestry.counter.domain.repository.DiagnosticSylvicoleRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticScreen(
    diagnosticId: String,
    diagnosticRepository: DiagnosticSylvicoleRepository,
    onNavigateBack: () -> Unit
) {
    var diagnostic by remember { mutableStateOf<DiagnosticSylvicoleEntity?>(null) }
    var isLoading  by remember { mutableStateOf(true) }

    LaunchedEffect(diagnosticId) {
        diagnostic = diagnosticRepository.getById(diagnosticId)
        isLoading  = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Résultats du diagnostic") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            diagnostic == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Diagnostic introuvable", color = MaterialTheme.colorScheme.error)
            }
            else -> DiagnosticContent(diag = diagnostic!!, paddingValues = padding)
        }
    }
}

@Composable
private fun DiagnosticContent(
    diag: DiagnosticSylvicoleEntity,
    paddingValues: androidx.compose.foundation.layout.PaddingValues
) {
    val json   = remember { Json { ignoreUnknownKeys = true } }
    val scores = remember(diag.essencesRecommandeesJson) {
        parseScoresJson(json, diag.essencesRecommandeesJson)
    }
    val recommandations = remember(diag.recommandationsSylvicolesJson) {
        parseStringListJson(json, diag.recommandationsSylvicolesJson)
    }
    val dateStr = remember(diag.dateCreation) {
        java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.FRANCE)
            .format(java.util.Date(diag.dateCreation))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(paddingValues)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Score global
        ScoreGlobalCard(
            scoreGlobal = diag.scoreGlobal,
            dateStr     = dateStr
        )

        // Indicateurs station
        if (diag.gHa != null || diag.dgCm != null || diag.hoM != null) {
            PeuplementIndicateursCard(diag)
        }

        // Essences recommandées
        if (scores.isNotEmpty()) {
            EssencesRecommandeesCard(scores)
        }

        // Recommandations textuelles
        if (recommandations.isNotEmpty()) {
            RecommandationsCard(recommandations)
        }

        // Scores détaillés
        StationScoresCard(diag)
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Score global
// ──────────────────────────────────────────────────────────────────────────────
@Composable
private fun ScoreGlobalCard(scoreGlobal: Int?, dateStr: String) {
    val score = scoreGlobal ?: 0
    val classe = when {
        score >= 80 -> "Très favorable" to MaterialTheme.colorScheme.primary
        score >= 65 -> "Favorable"      to MaterialTheme.colorScheme.tertiary
        score >= 45 -> "Moyen"          to MaterialTheme.colorScheme.secondary
        score >= 25 -> "Défavorable"    to Color(0xFFFF9800)
        else        -> "Inadapté"       to MaterialTheme.colorScheme.error
    }

    Card(
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape    = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Score global", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                Text(classe.first, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
                Text(dateStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f))
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .background(classe.second.copy(alpha = 0.2f), CircleShape)
            ) {
                Text("$score", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = classe.second)
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Indicateurs peuplement
// ──────────────────────────────────────────────────────────────────────────────
@Composable
private fun PeuplementIndicateursCard(diag: DiagnosticSylvicoleEntity) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Forest, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text("Peuplement", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(thickness = 0.5.dp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                diag.gHa?.let  { IndicateurItem("G", "${String.format("%.1f", it)} m²/ha") }
                diag.nHa?.let  { IndicateurItem("N", "$it tiges/ha") }
                diag.dgCm?.let { IndicateurItem("Dg", "${String.format("%.1f", it)} cm") }
                diag.hoM?.let  { IndicateurItem("Ho", "${String.format("%.1f", it)} m") }
            }
        }
    }
}

@Composable
private fun IndicateurItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Essences recommandées
// ──────────────────────────────────────────────────────────────────────────────
@Composable
private fun EssencesRecommandeesCard(scores: List<Triple<String, Int, String>>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Essences — adéquation station", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            HorizontalDivider(thickness = 0.5.dp)
            scores.take(8).forEach { (code, score, classe) ->
                EssenceScoreRow(code = code, score = score, classe = classe)
            }
        }
    }
}

@Composable
private fun EssenceScoreRow(code: String, score: Int, classe: String) {
    val color = when {
        score >= 80 -> MaterialTheme.colorScheme.primary
        score >= 65 -> MaterialTheme.colorScheme.tertiary
        score >= 45 -> MaterialTheme.colorScheme.secondary
        score >= 25 -> Color(0xFFFF9800)
        else        -> MaterialTheme.colorScheme.error
    }
    val emoji = when {
        score >= 80 -> "✅"
        score >= 65 -> "🟢"
        score >= 45 -> "🟡"
        score >= 25 -> "🔴"
        else        -> "⛔"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("$emoji $code", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(80.dp))
        LinearProgressIndicator(
            progress       = { score / 100f },
            modifier       = Modifier.weight(1f).height(6.dp),
            color          = color,
            trackColor     = color.copy(alpha = 0.15f)
        )
        Text("$score", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.width(32.dp))
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Recommandations textuelles
// ──────────────────────────────────────────────────────────────────────────────
@Composable
private fun RecommandationsCard(recommandations: List<String>) {
    Card(
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
        shape    = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                Text("Recommandations", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            recommandations.forEach { rec ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("•", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Text(rec, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Scores station détaillés
// ──────────────────────────────────────────────────────────────────────────────
@Composable
private fun StationScoresCard(diag: DiagnosticSylvicoleEntity) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Scores composantes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            HorizontalDivider(thickness = 0.5.dp)
            diag.scoreStation?.let  { ScoreComposanteRow("Station",   it) }
            diag.scorePeuplement?.let { ScoreComposanteRow("Peuplement", it) }
            diag.scoreBiodiversite?.let { ScoreComposanteRow("Biodiversité", it) }
        }
    }
}

@Composable
private fun ScoreComposanteRow(label: String, score: Int) {
    val color = when {
        score >= 70 -> MaterialTheme.colorScheme.primary
        score >= 45 -> MaterialTheme.colorScheme.tertiary
        else        -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(96.dp))
        LinearProgressIndicator(
            progress   = { score / 100f },
            modifier   = Modifier.weight(1f).height(5.dp),
            color      = color,
            trackColor = color.copy(alpha = 0.15f)
        )
        Text("$score", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.width(28.dp))
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Parseurs JSON
// ──────────────────────────────────────────────────────────────────────────────
private fun parseScoresJson(json: Json, raw: String?): List<Triple<String, Int, String>> {
    if (raw == null) return emptyList()
    return runCatching {
        val arr = json.parseToJsonElement(raw) as JsonArray
        arr.mapNotNull { el ->
            val obj   = el.jsonObject
            val code  = obj["code"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val score = obj["score"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
            val classe = obj["classe"]?.jsonPrimitive?.content ?: ""
            Triple(code, score, classe)
        }.sortedByDescending { it.second }
    }.getOrElse { emptyList() }
}

private fun parseStringListJson(json: Json, raw: String?): List<String> {
    if (raw == null) return emptyList()
    return runCatching {
        val arr = json.parseToJsonElement(raw) as JsonArray
        arr.mapNotNull { it.jsonPrimitive.content.takeIf { s -> s.isNotBlank() } }
    }.getOrElse { emptyList() }
}
