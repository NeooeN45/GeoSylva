package com.forestry.counter.presentation.screens.forestry

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forestry.counter.domain.calculation.ClassDistEntry
import com.forestry.counter.domain.classification.stand.DiameterCategoryRatio
import com.forestry.counter.domain.classification.stand.StandClassification
import com.forestry.counter.domain.classification.stand.StandClassificationCache
import com.forestry.counter.domain.classification.stand.StandTypologyDatabase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandClassificationScreen(
    parcelleId: String,
    onNavigateBack: () -> Unit
) {
    val stats = remember(parcelleId) {
        if (StandClassificationCache.lastParcelleId == parcelleId)
            StandClassificationCache.lastStats
        else null
    }

    val classification = remember(stats) {
        stats?.let { StandTypologyDatabase.classifyFromStats(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Classification du Peuplement") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
            if (classification == null) {
                item { NoDataCard() }
            } else {
                item { IdentifiedClassCard(classification) }
                item {
                    stats?.let { s ->
                        val ratios = computeDiamRatios(s.classDistribution)
                        StructureTriangleCard(ratios)
                    }
                }
                item { DiagnosticTabCard(classification) }
                item { SrgsCard(classification) }
            }
        }
    }
}

@Composable
private fun NoDataCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Warning, contentDescription = null,
                modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text("Aucune donnée disponible", fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text("Lancez un martelage sur cette parcelle pour obtenir la classification.",
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IdentifiedClassCard(cls: StandClassification) {
    val capitalColor = when (cls.capital) {
        1 -> Color(0xFF9E9E9E)
        2 -> Color(0xFF1976D2)
        3 -> Color(0xFF388E3C)
        4 -> Color(0xFFF57C00)
        else -> Color(0xFFD32F2F)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Peuplement identifié", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(cls.label, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("Code CNPF : ${cls.code}", style = MaterialTheme.typography.bodySmall)
                }
                Box(
                    modifier = Modifier.size(56.dp).background(capitalColor, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("F${cls.capital}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column {
                    Text("G/ha", style = MaterialTheme.typography.labelSmall)
                    Text("%.1f m²/ha".format(cls.gPerHa), fontWeight = FontWeight.SemiBold)
                }
                Column {
                    Text("N/ha", style = MaterialTheme.typography.labelSmall)
                    Text("%.0f t/ha".format(cls.nPerHa), fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ThumbUp, contentDescription = null,
                    modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(cls.advice, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StructureTriangleCard(ratios: DiameterCategoryRatio?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Triangle de structure", fontWeight = FontWeight.Bold)
            Text("Régime × Capital G × Structure 1–9", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            if (ratios != null) {
                TriangleChart(ratios)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    ChipInfo("PB", "%.0f%%".format(ratios.pbPct), Color(0xFF64B5F6))
                    ChipInfo("BM", "%.0f%%".format(ratios.bmPct), Color(0xFF81C784))
                    ChipInfo("GB", "%.0f%%".format(ratios.gbPct), Color(0xFFFFB74D))
                    ChipInfo("TGB", "%.0f%%".format(ratios.tgbPct), Color(0xFFE57373))
                }
                Spacer(Modifier.height(4.dp))
                Text("Position : ${classifyTrianglePosition(ratios)}",
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                Text("GB + TGB cumulés : %.0f%%".format(ratios.gbTgbPct),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Distribution par classes indisponible", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TriangleChart(ratios: DiameterCategoryRatio) {
    val animPb by animateFloatAsState(ratios.pbPct.toFloat() / 100f, tween(800), label = "pb")
    val animGbTgb by animateFloatAsState(ratios.gbTgbPct.toFloat() / 100f, tween(800), label = "gbtgb")

    Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
        val w = size.width
        val h = size.height
        val trianglePath = Path().apply {
            moveTo(0f, h); lineTo(w / 2f, 0f); lineTo(w, h); close()
        }
        drawPath(trianglePath, Color(0xFFE0E0E0), style = Fill)

        val pointX = (animPb * w).coerceIn(0f, w)
        val pointY = (animGbTgb * h).coerceIn(0f, h)
        drawCircle(color = Color(0xFF1976D2), radius = 12f, center = Offset(pointX, h - pointY))
        drawCircle(color = Color(0xFF1976D2).copy(alpha = 0.3f), radius = 24f, center = Offset(pointX, h - pointY))
    }
}

@Composable
private fun ChipInfo(label: String, value: String, color: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.15f)) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DiagnosticTabCard(cls: StandClassification) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Implications sylvicoles", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val implications = getSylviculturalImplications(cls)
            implications.forEach { impl ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("•", modifier = Modifier.padding(end = 6.dp))
                    Text(impl, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Atouts du peuplement", fontWeight = FontWeight.SemiBold,
                color = Color(0xFF2E7D32))
            Spacer(Modifier.height(4.dp))
            getStrengths(cls).forEach { s ->
                Text("✓ $s", style = MaterialTheme.typography.bodySmall, color = Color(0xFF388E3C))
            }
            Spacer(Modifier.height(8.dp))
            Text("Risques identifiés", fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(4.dp))
            getRisks(cls).forEach { r ->
                Text("⚠ $r", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SrgsCard(cls: StandClassification) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Parcours SRGS régional", fontWeight = FontWeight.Bold)
            Text("Schéma Régional de Gestion Sylvicole", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip("Régime", getSrgsRegime(cls), Modifier.weight(1f))
                InfoChip("Structure CNPF", cls.code, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Text("Objectif suggéré : ${getSuggestedObjective(cls)}",
                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Bold)
        }
    }
}

private fun computeDiamRatios(classDist: List<ClassDistEntry>): DiameterCategoryRatio? {
    val total = classDist.sumOf { it.n }.toDouble()
    if (total == 0.0) return null
    val pb = classDist.filter { it.diamClass in 18..27 }.sumOf { it.n }
    val bm = classDist.filter { it.diamClass in 28..47 }.sumOf { it.n }
    val gb = classDist.filter { it.diamClass in 48..67 }.sumOf { it.n }
    val tgb = classDist.filter { it.diamClass >= 68 }.sumOf { it.n }
    return DiameterCategoryRatio(
        pbPct = pb / total * 100,
        bmPct = bm / total * 100,
        gbPct = gb / total * 100,
        tgbPct = tgb / total * 100
    )
}


private fun classifyTrianglePosition(r: DiameterCategoryRatio): String = when {
    r.tgbPct >= 10 -> "Vieux bois (TGB dominant)"
    r.gbTgbPct >= 40 -> "Gros bois (GB+TGB dominant)"
    r.bmPct >= 50 -> "Perchis / BM dominant"
    r.pbPct >= 60 -> "Fourré / PB dominant"
    else -> "Structure équilibrée"
}

private fun getSylviculturalImplications(cls: StandClassification): List<String> = when (cls.capital) {
    1 -> listOf("Régénération naturelle à favoriser", "Pas d'intervention commerciale recommandée",
        "Surveiller la concurrence des espèces pionnières")
    2 -> listOf("Attendre le recrutement naturel", "Martelage non rentable — seuil de capitalisation non atteint",
        "Protéger les tiges d'avenir existantes")
    3 -> listOf("Éclaircie sélective par le bas ou par le haut selon objectif",
        "Définir les tiges d'avenir (80–120/ha)", "Prélèvement possible : 20–30% du G")
    4 -> listOf("Éclaircie forte recommandée (30–40% G)", "Risque de chablis si éclaircie trop tardive",
        "Libérer les couronnes des tiges d'avenir en priorité")
    else -> listOf("Éclaircie urgente (>40% G si possible)", "Risque élevé d'instabilité mécanique",
        "Réduire la densité en 2 passages pour limiter les dégâts de vent")
}

private fun getStrengths(cls: StandClassification): List<String> = when (cls.capital) {
    1 -> listOf("Peuplement ouvert — richesse en lumière au sol", "Favorable à la biodiversité forestière")
    2 -> listOf("Croissance active", "Bon potentiel de valorisation future")
    3 -> listOf("Capital optimal", "Sylviculture commercialement rentable", "Équilibre densité/croissance")
    4 -> listOf("Réserves importantes sur pied", "Potentiel de valorisation élevé à court terme")
    else -> listOf("Volume sur pied maximal")
}

private fun getRisks(cls: StandClassification): List<String> = when (cls.capital) {
    1 -> listOf("Sous-capitalisation — perte de production", "Risque de colonisation par essences non désirées")
    2 -> listOf("Rentabilité faible à court terme")
    3 -> listOf("Vigilance sur le taux de prélèvement (ne pas dépasser 30% G)")
    4 -> listOf("Instabilité mécanique croissante", "Dépérissement possible si retard à l'éclaircie")
    else -> listOf("Instabilité mécanique élevée", "Risque chablis majeur", "Dépérissement probable")
}

private fun getSrgsRegime(cls: StandClassification): String = when {
    cls.gPerHa > 30 -> "Futaie régulière"
    cls.gPerHa > 15 -> "Futaie irrégulière"
    else -> "Taillis / conversion"
}

private fun getSuggestedObjective(cls: StandClassification): String = when (cls.capital) {
    1, 2 -> "Capitalisation et protection — aucune coupe commerciale"
    3 -> "Production durable — éclaircie sélective tous les 8–12 ans"
    4 -> "Récolte prioritaire — réduire les risques sanitaires"
    else -> "Conversion ou coupe rase — renouvellement du peuplement"
}
