package com.forestry.counter.presentation.screens.forestry

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import com.forestry.counter.presentation.utils.StaggerEntrance
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forestry.counter.domain.model.IbpCriterionId
import com.forestry.counter.domain.model.IbpEvaluation
import com.forestry.counter.domain.model.IbpGroup
import com.forestry.counter.domain.model.IbpLevel
import com.forestry.counter.domain.repository.IbpRepository
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IbpDiagnosticScreen(
    parcelleId: String,
    ibpRepository: IbpRepository,
    onNavigateBack: () -> Unit
) {
    val evals by ibpRepository.getByParcelle(parcelleId).collectAsState(initial = emptyList())
    val latest = evals.maxByOrNull { it.observationDate }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostic Biodiversité", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1B5E20),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        if (latest == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.EmojiNature, contentDescription = null,
                        modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f))
                    Text("Aucune évaluation IBP\npour cette parcelle",
                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item { StaggerEntrance(0, staggerMs = 70) { DiagnosticScoreHeader(latest) } }
                item { StaggerEntrance(1, staggerMs = 70) { DiagnosticRadarCard(latest) } }
                item { StaggerEntrance(2, staggerMs = 70) { DiagnosticCriteriaTable(latest) } }
                item { StaggerEntrance(3, staggerMs = 70) { DiagnosticPriorityActions(latest) } }
                item { StaggerEntrance(4, staggerMs = 70) { DiagnosticPotentialCard(latest) } }
                if (evals.size > 1) {
                    item { StaggerEntrance(5, staggerMs = 70) { DiagnosticTrendSummary(evals.sortedBy { it.observationDate }) } }
                }
            }
        }
    }
}

/* ─────────────── Score header ───────────────────────────────────── */
@Composable
private fun DiagnosticScoreHeader(eval: IbpEvaluation) {
    val score = eval.scoreTotal
    val level = IbpLevel.fromScore(score)
    val color = ibpLevelColor(level)
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Score IBP Global", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = .85f))
            val animatedScore by animateIntAsState(
                targetValue = score.coerceAtLeast(0),
                animationSpec = tween(1200, easing = FastOutSlowInEasing),
                label = "diagScore"
            )
            Text("$animatedScore / 50", style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.ExtraBold, color = Color.White)
            Surface(color = Color.White.copy(alpha = .2f), shape = RoundedCornerShape(10.dp)) {
                Text(diagLevelStr(level), style = MaterialTheme.typography.titleSmall,
                    color = Color.White, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }
            HorizontalDivider(color = Color.White.copy(alpha = .3f))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Groupe A", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .8f))
                    Text("${eval.scoreA}/35", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = Color.White)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Groupe B", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .8f))
                    Text("${eval.scoreB}/15", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = Color.White)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Complétude", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .8f))
                    val pct = (IbpCriterionId.ALL.count { eval.answers.get(it) >= 0 } * 10)
                    Text("$pct%", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

/* ─────────────── Radar chart ────────────────────────────────────── */
@Composable
private fun DiagnosticRadarCard(eval: IbpEvaluation) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.TrackChanges,
                    contentDescription = null, tint = Color(0xFF1B5E20), modifier = Modifier.size(20.dp))
                Text("Profil radar A–J", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            val scores = IbpCriterionId.ALL.map { cid -> eval.answers.get(cid).coerceAtLeast(0).toFloat() / 5f }
            IbpDiagnosticRadar(scores = scores, labels = IbpCriterionId.ALL.map { it.displayCode })
        }
    }
}

@Composable
private fun IbpDiagnosticRadar(scores: List<Float>, labels: List<String>) {
    val animated = scores.mapIndexed { i, target ->
        animateFloatAsState(
            targetValue = target,
            animationSpec = tween(
                durationMillis = 900,
                delayMillis = i * 55,
                easing = FastOutSlowInEasing
            ),
            label = "radar_$i"
        ).value
    }
    Canvas(modifier = Modifier.fillMaxWidth().height(260.dp)) {
        val cx = size.width / 2f; val cy = size.height / 2f
        val maxR = minOf(cx, cy) * .82f
        val n = animated.size
        val step = (2 * PI / n).toFloat()

        // Grid
        for (level in 1..5) {
            val r = maxR * level / 5f
            val path = Path()
            for (i in 0 until n) {
                val a = -PI.toFloat() / 2 + i * step
                val x = cx + r * cos(a); val y = cy + r * sin(a)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, Color(0xFF1B5E20).copy(alpha = .08f), style = Stroke(1f))
        }
        // Axes + labels
        for (i in 0 until n) {
            val a = -PI.toFloat() / 2 + i * step
            drawLine(Color(0xFF1B5E20).copy(alpha = .2f), Offset(cx, cy),
                Offset(cx + maxR * cos(a), cy + maxR * sin(a)), strokeWidth = 1f)
        }
        // Score polygon
        val path = Path()
        for (i in 0 until n) {
            val a = -PI.toFloat() / 2 + i * step
            val r = maxR * animated[i]
            val x = cx + r * cos(a); val y = cy + r * sin(a)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, Color(0xFF1B5E20).copy(alpha = .25f))
        drawPath(path, Color(0xFF1B5E20), style = Stroke(2.5f, cap = StrokeCap.Round))
        // Dots
        for (i in 0 until n) {
            val a = -PI.toFloat() / 2 + i * step
            val r = maxR * animated[i]
            drawCircle(Color(0xFF1B5E20), 5f, Offset(cx + r * cos(a), cy + r * sin(a)))
        }
    }
}

/* ─────────────── Criteria table ─────────────────────────────────── */
@Composable
private fun DiagnosticCriteriaTable(eval: IbpEvaluation) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Détail des critères", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            IbpCriterionId.ALL.forEachIndexed { cidIdx, cid ->
                val score = eval.answers.get(cid)
                val color = when (score) { 5 -> Color(0xFF2E7D32); 2 -> Color(0xFFF9A825); 0 -> Color(0xFFC62828); else -> Color(0xFFBDBDBD) }
                val groupColor = if (cid.group == IbpGroup.A) Color(0xFF2E7D32) else Color(0xFF1565C0)
                val targetFraction = if (score >= 0) score / 5f else 0f
                val animFraction by animateFloatAsState(
                    targetValue = targetFraction,
                    animationSpec = tween(700, delayMillis = cidIdx * 40, easing = FastOutSlowInEasing),
                    label = "crit_$cidIdx"
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(color = groupColor, shape = RoundedCornerShape(6.dp)) {
                        Text(cid.displayCode, style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold, color = Color.White,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                    }
                    Text(ibpCriterionShortName(cid), style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f))
                    // Animated progress bar
                    Box(Modifier.width(80.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFE0E0E0))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(animFraction).clip(RoundedCornerShape(4.dp)).background(color))
                    }
                    Surface(color = color, shape = CircleShape) {
                        Text(if (score >= 0) "$score" else "?", style = MaterialTheme.typography.labelSmall,
                            color = Color.White, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                    }
                }
            }
        }
    }
}

/* ─────────────── Priority actions ──────────────────────────────── */
data class DiagAction(val title: String, val detail: String, val gainPts: Int, val effort: String)

private fun computePriorityActions(eval: IbpEvaluation): List<DiagAction> {
    val actions = mutableListOf<DiagAction>()
    val scores = IbpCriterionId.ALL.associateWith { eval.answers.get(it) }

    // Actionable criteria (group A, manageable)
    if ((scores[IbpCriterionId.BMS] ?: -1) < 5)
        actions += DiagAction("Créer/conserver des chicots debout",
            "Objectif ≥3 BMg/ha (D>37,5cm) — anneler des arbres sénescents", 5 - maxOf(scores[IbpCriterionId.BMS] ?: 0, 0), "Court terme")
    if ((scores[IbpCriterionId.BMC] ?: -1) < 5)
        actions += DiagAction("Laisser du bois mort au sol",
            "Objectif ≥3 BMg/ha couchés — ne pas sortir les houppiers", 5 - maxOf(scores[IbpCriterionId.BMC] ?: 0, 0), "Court terme")
    if ((scores[IbpCriterionId.DMH] ?: -1) < 5)
        actions += DiagAction("Désigner des arbres à dendromicrohabitats",
            "Marquer ≥5 arbres/ha porteurs de cavités, carpophores, coulées", 5 - maxOf(scores[IbpCriterionId.DMH] ?: 0, 0), "Court terme")
    if ((scores[IbpCriterionId.GB] ?: -1) < 5)
        actions += DiagAction("Réserver les très gros bois (TGB)",
            "Exempter de coupe tous arbres D>67,5cm — objectif ≥5/ha", 5 - maxOf(scores[IbpCriterionId.GB] ?: 0, 0), "Moyen terme")
    if ((scores[IbpCriterionId.VS] ?: -1) < 5)
        actions += DiagAction("Créer des trouées florifères",
            "Objectif 1–5% de milieux ouverts florifères — trouées, layons larges", 5 - maxOf(scores[IbpCriterionId.VS] ?: 0, 0), "Moyen terme")
    if ((scores[IbpCriterionId.E1] ?: -1) < 5)
        actions += DiagAction("Enrichir en essences autochtones",
            "Introduire 2–4 genres supplémentaires — sorbier, alisier, cornouiller…", 5 - maxOf(scores[IbpCriterionId.E1] ?: 0, 0), "Long terme")
    if ((scores[IbpCriterionId.E2] ?: -1) < 5)
        actions += DiagAction("Favoriser la stratification verticale",
            "Maintenir/développer strate arbustive et herbacée — réduire densité", 5 - maxOf(scores[IbpCriterionId.E2] ?: 0, 0), "Long terme")

    return actions.sortedByDescending { it.gainPts }.take(5)
}

@Composable
private fun DiagnosticPriorityActions(eval: IbpEvaluation) {
    val actions = remember(eval.id) { computePriorityActions(eval) }
    if (actions.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.EmojiObjects, contentDescription = null, tint = Color(0xFFF9A825), modifier = Modifier.size(20.dp))
                Text("Actions prioritaires", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            actions.forEachIndexed { idx, action ->
                val effortColor = when (action.effort) {
                    "Court terme" -> Color(0xFF2E7D32); "Moyen terme" -> Color(0xFFF9A825); else -> Color(0xFF1565C0)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(color = Color(0xFF1B5E20), shape = CircleShape) {
                        Text("${idx + 1}", style = MaterialTheme.typography.labelSmall, color = Color.White,
                            fontWeight = FontWeight.Bold, modifier = Modifier.padding(6.dp),
                            textAlign = TextAlign.Center)
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(action.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Surface(color = Color(0xFF2E7D32), shape = RoundedCornerShape(6.dp)) {
                                Text("+${action.gainPts} pts", style = MaterialTheme.typography.labelSmall,
                                    color = Color.White, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        Text(action.detail, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(color = effortColor.copy(alpha = .12f), shape = RoundedCornerShape(6.dp)) {
                            Text(action.effort, style = MaterialTheme.typography.labelSmall, color = effortColor,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

/* ─────────────── Potential gain card ───────────────────────────── */
@Composable
private fun DiagnosticPotentialCard(eval: IbpEvaluation) {
    val current = eval.scoreTotal.coerceAtLeast(0)
    val maxActionable = IbpCriterionId.ALL
        .filter { it.group == IbpGroup.A }
        .sumOf { cid -> (5 - eval.answers.get(cid).coerceAtLeast(0)).coerceAtLeast(0) }
    val potential = minOf(current + maxActionable, 50)
    val currentLevel = IbpLevel.fromScore(current)
    val potentialLevel = IbpLevel.fromScore(potential)
    val currentColor = ibpLevelColor(currentLevel)
    val potentialColor = ibpLevelColor(potentialLevel)

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.TrendingUp, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                Text("Potentiel d'amélioration", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Actuel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$current", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = currentColor)
                    Text(ibpLevelLabel(currentLevel), style = MaterialTheme.typography.labelSmall, color = currentColor, fontWeight = FontWeight.SemiBold)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Potentiel groupe A", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$potential", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = potentialColor)
                    Text(ibpLevelLabel(potentialLevel), style = MaterialTheme.typography.labelSmall, color = potentialColor, fontWeight = FontWeight.SemiBold)
                }
            }
            LinearProgressIndicator(
                progress = { current / 50f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = currentColor,
                trackColor = potentialColor.copy(alpha = .25f)
            )
            Text("+$maxActionable pts accessibles par gestion du peuplement (groupe A)",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/* ─────────────── Trend summary (if ≥2 evals) ───────────────────── */
@Composable
private fun DiagnosticTrendSummary(evals: List<IbpEvaluation>) {
    val first = evals.first(); val last = evals.last()
    val delta = last.scoreTotal - first.scoreTotal
    val trendColor = if (delta > 0) Color(0xFF2E7D32) else if (delta < 0) Color(0xFFC62828) else Color(0xFF757575)
    val trendIcon = if (delta > 0) Icons.Default.TrendingUp else if (delta < 0) Icons.Default.TrendingDown else Icons.Default.TrendingFlat

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(trendIcon, contentDescription = null, tint = trendColor, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text("Évolution (${evals.size} évaluations)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Score initial ${first.scoreTotal} → actuel ${last.scoreTotal}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(color = trendColor, shape = RoundedCornerShape(10.dp)) {
                Text("${if (delta > 0) "+" else ""}$delta pts", style = MaterialTheme.typography.labelLarge,
                    color = Color.White, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
    }
}

/* ─────────────── Helpers ────────────────────────────────────────── */
private fun ibpCriterionShortName(cid: IbpCriterionId): String = when (cid) {
    IbpCriterionId.E1  -> "Essences autochtones"
    IbpCriterionId.E2  -> "Strates de végétation"
    IbpCriterionId.BMS -> "Bois mort sur pied"
    IbpCriterionId.BMC -> "Bois mort au sol"
    IbpCriterionId.GB  -> "Très gros bois vivants"
    IbpCriterionId.DMH -> "Dendromicrohabitats"
    IbpCriterionId.VS  -> "Milieux ouverts florifères"
    IbpCriterionId.CF  -> "Ancienneté forêt"
    IbpCriterionId.CO  -> "Milieux aquatiques"
    IbpCriterionId.HC  -> "Milieux rocheux"
}

private fun diagLevelStr(level: IbpLevel): String = when (level) {
    IbpLevel.VERY_LOW  -> "Très faible"
    IbpLevel.LOW       -> "Faible"
    IbpLevel.MEDIUM    -> "Moyen"
    IbpLevel.GOOD      -> "Bon"
    IbpLevel.VERY_GOOD -> "Très bon"
}
