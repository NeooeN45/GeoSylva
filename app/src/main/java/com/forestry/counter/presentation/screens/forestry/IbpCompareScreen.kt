package com.forestry.counter.presentation.screens.forestry

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forestry.counter.domain.model.IbpCriterionId
import com.forestry.counter.domain.model.IbpEvaluation
import com.forestry.counter.domain.model.IbpLevel
import com.forestry.counter.domain.repository.IbpRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IbpCompareScreen(
    parcelleId: String,
    ibpRepository: IbpRepository,
    onNavigateBack: () -> Unit
) {
    val allEvals by ibpRepository.getByParcelle(parcelleId).collectAsStateWithLifecycle(initialValue = emptyList())
    val evals = remember(allEvals) { allEvals.sortedBy { it.observationDate } }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yy", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Évolution temporelle IBP", fontWeight = FontWeight.Bold) },
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
        if (evals.size < 2) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Timeline, contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f))
                    Text("Il faut au moins 2 évaluations\npour voir l'évolution",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item { CompareHeader(evals, dateFormat) }
                item { CompareTotalScoreChart(evals, dateFormat) }
                item { CompareGroupABChart(evals, dateFormat) }
                item {
                    Text("Évolution par critère",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp))
                }
                item { CompareCriteriaSparklines(evals, dateFormat) }
                item { CompareDataTable(evals, dateFormat) }
            }
        }
    }
}

/* ─────────────── Header summary ────────────────────────────────── */
@Composable
private fun CompareHeader(evals: List<IbpEvaluation>, fmt: SimpleDateFormat) {
    val first = evals.first(); val last = evals.last()
    val delta = last.scoreTotal - first.scoreTotal
    val trendColor = when {
        delta > 0  -> Color(0xFF2E7D32)
        delta < 0  -> Color(0xFFC62828)
        else       -> Color(0xFF757575)
    }
    val trendIcon = when {
        delta > 0  -> Icons.Default.TrendingUp
        delta < 0  -> Icons.Default.TrendingDown
        else       -> Icons.Default.TrendingFlat
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(trendIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
            Column(Modifier.weight(1f)) {
                Text("${evals.size} évaluations comparées",
                    style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .8f))
                Text("${fmt.format(Date(first.observationDate))} → ${fmt.format(Date(last.observationDate))}",
                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .7f))
            }
            Surface(color = trendColor, shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${if (delta >= 0) "+" else ""}$delta pts",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("évolution", style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = .8f))
                }
            }
        }
    }
}

/* ─────────────── Total score line chart ────────────────────────── */
@Composable
private fun CompareTotalScoreChart(evals: List<IbpEvaluation>, fmt: SimpleDateFormat) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Score total /50", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            val scores = evals.map { it.scoreTotal.coerceAtLeast(0).toFloat() }
            val labels = evals.map { fmt.format(Date(it.observationDate)) }
            SparklineChart(
                values = scores, maxVal = 50f, minVal = 0f,
                labels = labels, color = Color(0xFF1B5E20),
                fillAlpha = 0.18f, modifier = Modifier.fillMaxWidth().height(120.dp)
            )
            // Level zones legend
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("Très faible" to Color(0xFFC62828), "Moyen" to Color(0xFFF9A825), "Bon" to Color(0xFF2E7D32)).forEach { (l, c) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(c))
                        Text(l, style = MaterialTheme.typography.labelSmall, color = c)
                    }
                }
            }
        }
    }
}

/* ─────────────── Group A vs B stacked chart ────────────────────── */
@Composable
private fun CompareGroupABChart(evals: List<IbpEvaluation>, fmt: SimpleDateFormat) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Groupe A vs Groupe B", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            val scoresA = evals.map { it.scoreA.coerceAtLeast(0).toFloat() }
            val scoresB = evals.map { it.scoreB.coerceAtLeast(0).toFloat() }
            val labels = evals.map { fmt.format(Date(it.observationDate)) }
            Row(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                SparklineChart(values = scoresA, maxVal = 35f, minVal = 0f,
                    labels = labels, color = Color(0xFF2E7D32), fillAlpha = 0.15f,
                    modifier = Modifier.weight(1f).fillMaxHeight())
                Spacer(Modifier.width(8.dp))
                SparklineChart(values = scoresB, maxVal = 15f, minVal = 0f,
                    labels = labels, color = Color(0xFF1565C0), fillAlpha = 0.15f,
                    modifier = Modifier.weight(1f).fillMaxHeight())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF2E7D32)))
                    Text("Groupe A /35", style = MaterialTheme.typography.labelSmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF1565C0)))
                    Text("Groupe B /15", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/* ─────────────── Per-criterion sparklines ──────────────────────── */
@Composable
private fun CompareCriteriaSparklines(evals: List<IbpEvaluation>, fmt: SimpleDateFormat) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val labels = evals.map { fmt.format(Date(it.observationDate)) }
            IbpCriterionId.ALL.forEach { cid ->
                val values = evals.map { ev -> ev.answers.get(cid).coerceAtLeast(0).toFloat() }
                val firstV = values.first().roundToInt()
                val lastV = values.last().roundToInt()
                val delta = lastV - firstV
                val groupColor = if (cid.group == com.forestry.counter.domain.model.IbpGroup.A) Color(0xFF2E7D32) else Color(0xFF1565C0)
                val scoreColor = when (lastV) { 5 -> Color(0xFF2E7D32); 2 -> Color(0xFFF9A825); else -> Color(0xFFC62828) }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(color = groupColor, shape = RoundedCornerShape(6.dp)) {
                        Text(cid.displayCode, style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold, color = Color.White,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                    }
                    SparklineChart(values = values, maxVal = 5f, minVal = 0f,
                        labels = labels, color = groupColor, fillAlpha = 0.1f,
                        showDots = true, modifier = Modifier.weight(1f).height(36.dp))
                    Surface(color = scoreColor, shape = CircleShape) {
                        Text("$lastV", style = MaterialTheme.typography.labelSmall, color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                    }
                    val deltaStr = when {
                        delta > 0 -> "+$delta"
                        delta < 0 -> "$delta"
                        else -> "="
                    }
                    val deltaColor = when {
                        delta > 0 -> Color(0xFF2E7D32); delta < 0 -> Color(0xFFC62828); else -> Color(0xFF757575)
                    }
                    Text(deltaStr, style = MaterialTheme.typography.labelSmall,
                        color = deltaColor, fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/* ─────────────── Data table ────────────────────────────────────── */
@Composable
private fun CompareDataTable(evals: List<IbpEvaluation>, fmt: SimpleDateFormat) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Tableau récapitulatif", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            // Header
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Critère", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(44.dp))
                evals.forEach { ev ->
                    Text(fmt.format(Date(ev.observationDate)), style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                        maxLines = 1, fontWeight = FontWeight.SemiBold)
                }
            }
            HorizontalDivider()
            IbpCriterionId.ALL.forEach { cid ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val groupColor = if (cid.group == com.forestry.counter.domain.model.IbpGroup.A) Color(0xFF2E7D32) else Color(0xFF1565C0)
                    Text(cid.displayCode, style = MaterialTheme.typography.labelSmall,
                        color = groupColor, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
                    evals.forEach { ev ->
                        val v = ev.answers.get(cid)
                        val c = when (v) { 5 -> Color(0xFF2E7D32); 2 -> Color(0xFFF9A825); 0 -> Color(0xFFC62828); else -> Color(0xFFBDBDBD) }
                        Text(if (v >= 0) "$v" else "–", style = MaterialTheme.typography.labelSmall,
                            color = c, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    }
                }
            }
            HorizontalDivider()
            // Total row
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Total", style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(44.dp))
                evals.forEach { ev ->
                    val score = ev.scoreTotal
                    val level = IbpLevel.fromScore(score)
                    val c = ibpLevelColor(level)
                    Text(if (score >= 0) "$score" else "–", style = MaterialTheme.typography.labelSmall,
                        color = c, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/* ─────────────── Sparkline chart composable ─────────────────────── */
@Composable
private fun SparklineChart(
    values: List<Float>,
    maxVal: Float,
    minVal: Float,
    labels: List<String>,
    color: Color,
    fillAlpha: Float = 0.2f,
    showDots: Boolean = false,
    modifier: Modifier = Modifier
) {
    val n = values.size
    if (n < 2) return

    val animatedValues = values.mapIndexed { i, target ->
        animateFloatAsState(targetValue = target, animationSpec = tween(600 + i * 80), label = "spark$i").value
    }

    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val range = (maxVal - minVal).coerceAtLeast(1f)
        fun xOf(i: Int) = if (n == 1) w / 2f else i.toFloat() / (n - 1) * w
        fun yOf(v: Float) = h - ((v - minVal) / range * h).coerceIn(0f, h)

        // Level bands (subtle)
        drawRect(color.copy(alpha = 0.04f))

        // Fill path
        val fillPath = Path()
        fillPath.moveTo(xOf(0), h)
        animatedValues.forEachIndexed { i, v -> fillPath.lineTo(xOf(i), yOf(v)) }
        fillPath.lineTo(xOf(n - 1), h)
        fillPath.close()
        drawPath(fillPath, color.copy(alpha = fillAlpha))

        // Line path
        val linePath = Path()
        animatedValues.forEachIndexed { i, v ->
            if (i == 0) linePath.moveTo(xOf(i), yOf(v)) else linePath.lineTo(xOf(i), yOf(v))
        }
        drawPath(linePath, color, style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Dots
        if (showDots) {
            animatedValues.forEachIndexed { i, v ->
                drawCircle(color, 4f, Offset(xOf(i), yOf(v)))
            }
        }
    }
}
