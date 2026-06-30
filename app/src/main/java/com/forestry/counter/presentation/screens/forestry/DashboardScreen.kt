package com.forestry.counter.presentation.screens.forestry

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forestry.counter.R
import com.forestry.counter.domain.model.ClimateZone
import com.forestry.counter.domain.model.Essence
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.repository.EssenceRepository
import com.forestry.counter.domain.repository.ParcelleRepository
import com.forestry.counter.domain.repository.TigeRepository
import com.forestry.counter.domain.usecase.fertility.ConfidenceLevel
import com.forestry.counter.domain.usecase.fertility.FertilityClass
import com.forestry.counter.domain.usecase.fertility.FertilityClassifier
import com.forestry.counter.domain.usecase.fertility.FertilityResult
import com.forestry.counter.presentation.utils.AnimatedCounter
import com.forestry.counter.presentation.utils.StaggerEntrance
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

// ── Couleurs palette dashboard ──
private val CHART_COLORS = listOf(
    Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFF44336), Color(0xFFFF9800),
    Color(0xFF9C27B0), Color(0xFF009688), Color(0xFF795548), Color(0xFF607D8B),
    Color(0xFFE91E63), Color(0xFF3F51B5), Color(0xFFCDDC39), Color(0xFF00BCD4),
    Color(0xFFFF5722), Color(0xFF8BC34A), Color(0xFF673AB7), Color(0xFFFFC107)
)

private fun essenceDisplayColor(essence: Essence?, index: Int): Color {
    essence?.colorHex?.let { hex ->
        return try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { CHART_COLORS[index % CHART_COLORS.size] }
    }
    return CHART_COLORS[index % CHART_COLORS.size]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    parcelleId: String,
    tigeRepository: TigeRepository,
    essenceRepository: EssenceRepository,
    parcelleRepository: ParcelleRepository? = null,
    onNavigateBack: () -> Unit
) {
    val tiges by tigeRepository.getTigesByParcelle(parcelleId).collectAsStateWithLifecycle(initialValue = emptyList())
    val essences by essenceRepository.getAllEssences().collectAsStateWithLifecycle(initialValue = emptyList())
    val essenceMap = remember(essences) { essences.associateBy { it.code.uppercase() } }

    val parcelle by (parcelleRepository?.getParcelleById(parcelleId)
        ?: kotlinx.coroutines.flow.flowOf(null)).collectAsStateWithLifecycle(initialValue = null)

    // ── Données agrégées ──
    val tigesByEssence = remember(tiges) {
        tiges.groupBy { it.essenceCode.uppercase() }
            .entries.sortedByDescending { it.value.size }
    }
    val diamClasses = remember(tiges) {
        tiges.groupBy { ((it.diamCm / 5).toInt() * 5) }
            .toSortedMap().mapValues { it.value.size }
    }
    val gByEssence = remember(tiges) {
        tiges.groupBy { it.essenceCode.uppercase() }
            .mapValues { (_, list) -> list.sumOf { PI / 4.0 * (it.diamCm / 100.0).let { d -> d * d } } }
            .entries.sortedByDescending { it.value }
    }
    val totalG = remember(gByEssence) { gByEssence.sumOf { it.value } }
    val totalTiges = tiges.size
    val speciesCount = tigesByEssence.size
    val avgDiam = remember(tiges) { if (tiges.isEmpty()) 0.0 else tiges.sumOf { it.diamCm } / tiges.size }
    val tigesWithHeight = remember(tiges) { tiges.filter { (it.hauteurM ?: 0.0) > 0.0 } }
    val avgHeight = remember(tigesWithHeight) {
        if (tigesWithHeight.isEmpty()) 0.0 else tigesWithHeight.sumOf { it.hauteurM ?: 0.0 } / tigesWithHeight.size
    }
    val heightClasses = remember(tigesWithHeight) {
        tigesWithHeight.groupBy { (((it.hauteurM ?: 0.0) / 2).toInt() * 2) }
            .toSortedMap().mapValues { it.value.size }
    }
    val qualityDist = remember(tiges) {
        tiges.mapNotNull { it.qualite }.groupBy { it }
            .mapValues { it.value.size }.toSortedMap()
    }

    // ── Zone bioclimatique ──
    val climateZone = remember(tiges, parcelle) {
        val altM = parcelle?.altitudeM
            ?: tiges.mapNotNull { it.altitudeM }.average().takeIf { !it.isNaN() }
        val wkt = tiges.mapNotNull { it.gpsWkt }.firstOrNull()
        if (wkt != null) ClimateZone.detectFromWkt(wkt, altM)
        else ClimateZone.UNKNOWN
    }

    // ── Classification de fertilité ──
    val fertilityResults = remember(tiges, climateZone, essenceMap) {
        FertilityClassifier.classify(
            tiges = tiges,
            climateZone = climateZone,
            essenceNames = essenceMap.mapValues { it.value.name }
        )
    }

    // ── Coefficient de Liocourt ──
    val liocourtQ = remember(diamClasses) {
        if (diamClasses.size < 3) null
        else {
            val vals = diamClasses.values.map { it.toDouble() }
            val ratios = vals.zipWithNext { a, b -> if (a > 0) b / a else null }.filterNotNull()
            if (ratios.isEmpty()) null else ratios.average()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.drawBehind {
                    drawRect(
                        Brush.horizontalGradient(listOf(Color(0xFF1B5E20), Color(0xFF2E7D32), Color(0xFF1565C0)))
                    )
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (tiges.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), Alignment.Center) {
                        Text(stringResource(R.string.dashboard_no_data),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── KPI hero grid ──
            if (tiges.isNotEmpty()) item {
                StaggerEntrance(0, staggerMs = 60) {
                    PremiumKpiGrid(
                        totalTiges = totalTiges, speciesCount = speciesCount,
                        avgDiam = avgDiam, totalG = totalG,
                        avgHeight = avgHeight, climateZone = climateZone
                    )
                }
            }

            // ── Donut essence ──
            if (tigesByEssence.isNotEmpty()) item {
                StaggerEntrance(1, staggerMs = 60) {
                    PremiumDashboardCard(
                        title = stringResource(R.string.dashboard_species_distribution),
                        accentColor = Color(0xFF2E7D32),
                        icon = Icons.Default.Park
                    ) {
                        AnimatedDonutChart(
                            data = tigesByEssence.map { (code, list) ->
                                Triple(essenceMap[code]?.name ?: code, list.size,
                                    essenceDisplayColor(essenceMap[code], tigesByEssence.indexOfFirst { it.key == code }))
                            },
                            total = totalTiges
                        )
                    }
                }
            }

            // ── Barres diamètres ──
            if (diamClasses.isNotEmpty()) item {
                StaggerEntrance(2, staggerMs = 60) {
                    PremiumDashboardCard(
                        title = stringResource(R.string.dashboard_diameter_distribution),
                        accentColor = Color(0xFF1565C0),
                        icon = Icons.Default.Straighten
                    ) {
                        AnimatedBarChart(
                            classes = diamClasses,
                            barGradient = Brush.verticalGradient(listOf(Color(0xFF42A5F5), Color(0xFF1565C0))),
                            labelSuffix = " cm"
                        )
                    }
                }
            }

            // ── Surface terrière par essence ──
            if (gByEssence.isNotEmpty()) item {
                StaggerEntrance(3, staggerMs = 60) {
                    PremiumDashboardCard(
                        title = stringResource(R.string.dashboard_basal_area_by_species),
                        accentColor = Color(0xFF6A1B9A),
                        icon = Icons.Default.Analytics
                    ) {
                        AnimatedHorizontalBars(
                            data = gByEssence.map { (code, g) ->
                                Triple(essenceMap[code]?.name ?: code, g,
                                    essenceDisplayColor(essenceMap[code], gByEssence.indexOfFirst { it.key == code }))
                            },
                            maxVal = gByEssence.maxOfOrNull { it.value } ?: 1.0,
                            valueFmt = { String.format("%.3f m²", it) }
                        )
                    }
                }
            }

            // ── Distribution des hauteurs ──
            if (heightClasses.isNotEmpty()) item {
                StaggerEntrance(4, staggerMs = 60) {
                    PremiumDashboardCard(
                        title = stringResource(R.string.dashboard_height_distribution),
                        accentColor = Color(0xFF00695C),
                        icon = Icons.Default.Forest
                    ) {
                        AnimatedBarChart(
                            classes = heightClasses,
                            barGradient = Brush.verticalGradient(listOf(Color(0xFF80CBC4), Color(0xFF00695C))),
                            labelSuffix = " m",
                            showCurve = true
                        )
                    }
                }
            }

            // ── Structure peuplement ──
            if (diamClasses.size >= 3) item {
                StaggerEntrance(5, staggerMs = 60) {
                    StandStructureCard(diamClasses = diamClasses, liocourtQ = liocourtQ)
                }
            }

            // ── Répartition qualité ──
            if (qualityDist.isNotEmpty()) item {
                StaggerEntrance(6, staggerMs = 60) {
                    PremiumDashboardCard(
                        title = stringResource(R.string.dashboard_quality_distribution),
                        accentColor = Color(0xFFE65100),
                        icon = Icons.Default.Eco
                    ) {
                        AnimatedQualityBars(qualityDist, totalTiges)
                    }
                }
            }

            // ── Classification de fertilité ──
            if (fertilityResults.isNotEmpty()) item {
                StaggerEntrance(7, staggerMs = 60) {
                    FertilityCard(results = fertilityResults, climateZone = climateZone)
                }
            }
        }
    }
}

// ════════════════════════════════════════════════
// Composables internes
// ════════════════════════════════════════════════

// ── Premium KPI grid (6 cards, 3 per row) ──
@Composable
private fun PremiumKpiGrid(
    totalTiges: Int, speciesCount: Int,
    avgDiam: Double, totalG: Double,
    avgHeight: Double, climateZone: ClimateZone
) {
    val animTiges by animateIntAsState(totalTiges, tween(900, easing = FastOutSlowInEasing), label = "kpi_tiges")
    val animSp    by animateIntAsState(speciesCount, tween(900, 80, FastOutSlowInEasing), label = "kpi_sp")
    val kpiItems = listOf(
        Triple("Tiges", "$animTiges", Color(0xFF2E7D32)),
        Triple("Essences", "$animSp", Color(0xFF1565C0)),
        Triple("Ø moyen", "${String.format("%.1f", avgDiam)} cm", Color(0xFF6A1B9A)),
        Triple("G total", "${String.format("%.3f", totalG)} m²", Color(0xFFBF360C)),
        Triple("H moy.", if (avgHeight > 0) "${String.format("%.1f", avgHeight)} m" else "—", Color(0xFF00695C)),
        Triple("Zone", climateZone.labelFr, Color(0xFF4527A0))
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in 0..1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (col in 0..2) {
                    val idx = row * 3 + col
                    val (lbl, val_, accent) = kpiItems[idx]
                    PremiumKpiCard(label = lbl, value = val_, accent = accent, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PremiumKpiCard(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(visible, enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 3 }) {
        Box(
            modifier = modifier
                .shadow(4.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .drawBehind {
                    drawRect(Brush.verticalGradient(listOf(accent.copy(alpha = 0.18f), accent.copy(alpha = 0.06f))))
                    drawRect(Brush.horizontalGradient(listOf(accent.copy(0.10f), Color.Transparent),
                        endX = size.width * 0.5f))
                }
                .padding(horizontal = 10.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(3.dp, 18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent))
                Text(value, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

// ── Premium card container ──
@Composable
private fun PremiumDashboardCard(
    title: String,
    accentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(visible, enter = fadeIn(tween(500)) + slideInVertically(tween(500, easing = FastOutSlowInEasing)) { it / 6 }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column {
                // Header strip with gradient
                Box(
                    Modifier.fillMaxWidth().height(48.dp)
                        .background(Brush.horizontalGradient(listOf(accentColor, accentColor.copy(alpha = 0.65f))))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text(title, style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
                // Content area
                Column(Modifier.padding(16.dp), content = content)
            }
        }
    }
}

// ── Animated donut chart ──
@Composable
private fun AnimatedDonutChart(
    data: List<Triple<String, Int, Color>>,
    total: Int
) {
    var started by remember { mutableStateOf(false) }
    val animatables = remember(data.size) { List(data.size) { Animatable(0f) } }
    LaunchedEffect(data.size) {
        started = true
        data.forEachIndexed { i, (_, count, _) ->
            val target = if (total > 0) 360f * count / total else 0f
            launch {
                animatables[i].animateTo(target, tween(900, i * 60, FastOutSlowInEasing))
            }
        }
    }
    val sweepAnims = animatables.map { it.value }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(150.dp)) {
                val strokeW = 30.dp.toPx()
                val r = (size.minDimension - strokeW) / 2f
                val tl = Offset((size.width - 2*r - strokeW)/2f, (size.height - 2*r - strokeW)/2f)
                val arcSz = Size(2*r + strokeW, 2*r + strokeW)
                // Shadow ring
                drawArc(Color.Black.copy(alpha=0.06f), -90f, 360f, false, tl, arcSz,
                    style = Stroke(strokeW + 4.dp.toPx(), cap = StrokeCap.Round))
                // Track ring
                drawArc(Color.Gray.copy(alpha=0.07f), -90f, 360f, false, tl, arcSz,
                    style = Stroke(strokeW, cap = StrokeCap.Round))
                var startAngle = -90f
                data.forEachIndexed { i, (_, count, color) ->
                    val sweep = sweepAnims[i]
                    if (sweep > 0.5f) {
                        drawArc(color.copy(alpha=0.20f), startAngle, sweep - 1f, false, tl, arcSz,
                            style = Stroke(strokeW + 7.dp.toPx(), cap = StrokeCap.Round))
                        drawArc(color, startAngle, sweep - 1f, false, tl, arcSz,
                            style = Stroke(strokeW, cap = StrokeCap.Round))
                    }
                    val targetSweep = if (total > 0) 360f * count / total else 0f
                    startAngle += targetSweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedCounter(total, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("tiges", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            data.take(10).forEachIndexed { i, (name, count, color) ->
                key(i) {
                    val pct = if (total > 0) count * 100f / total else 0f
                    val animPct by animateFloatAsState(if (started) pct / 100f else 0f,
                        tween(700, i * 55, FastOutSlowInEasing), label = "donut_leg_$i")
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                            Spacer(Modifier.width(6.dp))
                            Text("$name", style = MaterialTheme.typography.bodySmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f))
                            Text("${String.format("%.0f", pct)}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(2.dp))
                        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
                            .background(color.copy(alpha = 0.15f))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(animPct)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Brush.horizontalGradient(listOf(color, color.copy(0.6f)))))
                        }
                    }
                }
            }
            if (data.size > 10) {
                Text("+${data.size-10} ${stringResource(R.string.dashboard_others)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Animated vertical bar chart (diameter, height) ──
@Composable
private fun AnimatedBarChart(
    classes: Map<Int, Int>,
    barGradient: Brush,
    labelSuffix: String = "",
    showCurve: Boolean = false
) {
    val maxCount = classes.values.maxOrNull() ?: 1
    val entries = classes.entries.toList()
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val maxBarH = 130.dp

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().height(maxBarH + 40.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            entries.forEachIndexed { i, (cls, count) ->
                key(i) {
                    val fraction by animateFloatAsState(
                        if (started) count.toFloat() / maxCount else 0f,
                        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
                        label = "bar_$i"
                    )
                    val barH = (maxBarH.value * fraction).coerceAtLeast(4f)
                    Column(
                        Modifier.width(38.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        if (fraction > 0.02f) Text("$count", fontSize = 12.sp, color = labelColor,
                            style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(2.dp))
                        Box(Modifier.width(26.dp).height(barH.dp)
                            .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                            .background(barGradient))
                        Spacer(Modifier.height(3.dp))
                        Text("$cls$labelSuffix", fontSize = 12.sp, color = labelColor,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center, maxLines = 1)
                    }
                }
            }
        }
        if (showCurve && entries.size >= 3) {
            Spacer(Modifier.height(6.dp))
            val curveColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            Text("Distribution de fréquence", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

// ── Animated horizontal bars (G per essence, etc.) ──
@Composable
private fun AnimatedHorizontalBars(
    data: List<Triple<String, Double, Color>>,
    maxVal: Double,
    valueFmt: (Double) -> String = { String.format("%.3f", it) }
) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        data.take(14).forEachIndexed { i, (name, v, color) ->
            key(i) {
                val frac by animateFloatAsState(
                    if (started) (v / maxVal).toFloat().coerceIn(0f, 1f) else 0f,
                    tween(800, i * 60, FastOutSlowInEasing), label = "hbar_$i"
                )
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f))
                        Text(valueFmt(v), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(3.dp))
                    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
                        .background(color.copy(alpha = 0.12f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(frac)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Brush.horizontalGradient(listOf(color, color.copy(0.55f)))))
                        // Accent dot at end
                        if (frac > 0.04f) Box(
                            Modifier.size(10.dp).align(Alignment.CenterStart)
                                .offset(x = (frac * 1f - 0.04f).coerceAtLeast(0f).let { 0.dp })
                        )
                    }
                }
            }
        }
    }
}

// ── Animated quality bars ──
private val QUALITY_COLORS = listOf(
    Color(0xFF2E7D32), Color(0xFF1565C0), Color(0xFFE65100), Color(0xFFC62828)
)
private val QUALITY_LABELS = listOf("A", "B", "C", "D")

@Composable
private fun AnimatedQualityBars(dist: Map<Int, Int>, totalTiges: Int) {
    val assessed = dist.values.sum()
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        dist.entries.toList().forEachIndexed { i, (gradeIdx, count) ->
            key(i) {
                val label = QUALITY_LABELS.getOrElse(gradeIdx) { "?" }
                val color = QUALITY_COLORS.getOrElse(gradeIdx) { Color.Gray }
                val pct = if (assessed > 0) count * 100f / assessed else 0f
                val aFrac by animateFloatAsState(
                    if (started) pct / 100f else 0f,
                    tween(800, i * 80, FastOutSlowInEasing), label = "qual_$i"
                )
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(26.dp).clip(CircleShape)
                            .background(color.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center) {
                            Text(label, style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold, color = color)
                        }
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f).height(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(color.copy(alpha = 0.10f))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(aFrac)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Brush.horizontalGradient(listOf(color, color.copy(0.5f)))))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("$count (${String.format("%.0f",pct)}%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(68.dp), textAlign = TextAlign.End)
                    }
                }
            }
        }
        if (assessed < totalTiges) {
            Text(stringResource(R.string.dashboard_quality_unassessed, totalTiges - assessed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp))
        }
    }
}

// ── Stand Structure card (Liocourt / structure analysis) ──
@Composable
private fun StandStructureCard(diamClasses: Map<Int, Int>, liocourtQ: Double?) {
    val accentColor = Color(0xFF37474F)
    Box(Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(20.dp))
        .clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)) {
        Column {
            Box(Modifier.fillMaxWidth().height(48.dp)
                .background(Brush.horizontalGradient(listOf(accentColor, Color(0xFF546E7A))))
                .padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Terrain, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("Structure du peuplement", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (liocourtQ != null) {
                    val qColor = when {
                        liocourtQ in 1.1..1.8 -> Color(0xFF2E7D32)
                        liocourtQ in 0.8..2.2  -> Color(0xFFE65100)
                        else                    -> Color(0xFFC62828)
                    }
                    val structureLabel = when {
                        liocourtQ in 1.1..1.8 -> "Régulière (Liocourt)"
                        liocourtQ < 0.8       -> "Peuplement jeune ou homogène"
                        liocourtQ > 2.2       -> "Structure irrégulière forte"
                        else                  -> "Structure hétérogène"
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Coeff. de Liocourt (q)", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(String.format("%.2f", liocourtQ),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold, color = qColor)
                        }
                        Box(Modifier.clip(RoundedCornerShape(8.dp))
                            .background(qColor.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(structureLabel, style = MaterialTheme.typography.labelSmall,
                                color = qColor, fontWeight = FontWeight.Medium)
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                // Number of diameter classes
                val classesCount = diamClasses.size
                val minDiam = diamClasses.keys.minOrNull() ?: 0
                val maxDiam = diamClasses.keys.maxOrNull() ?: 0
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StructureStat("Classes diam.", "$classesCount", Modifier.weight(1f))
                    StructureStat("Ø min", "${minDiam} cm", Modifier.weight(1f))
                    StructureStat("Ø max", "${maxDiam} cm", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StructureStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

// ── Fertility classification card ──
@Composable
private fun FertilityCard(results: List<FertilityResult>, climateZone: ClimateZone) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val accentColor = Color(0xFF1B5E20)

    Box(Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(20.dp))
        .clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)) {
        Column {
            // Header
            Box(Modifier.fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(accentColor, Color(0xFF2E7D32), Color(0xFF1565C0))))
                .padding(horizontal = 16.dp, vertical = 12.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.WaterDrop, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text("Classes de fertilité", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Terrain, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(14.dp))
                        Text("Zone : ${climateZone.labelFr}",
                            style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.85f))
                    }
                }
            }

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                results.forEachIndexed { i, result ->
                    key(i) {
                        FertilityRow(result = result, index = i, started = started)
                        if (i < results.lastIndex) Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.5f))
                    }
                }
                // Disclaimer
                Text("Basé sur ONF/CNPF — indicatif. Sans âge connu, la précision dépend des mesures dendrométriques disponibles.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun FertilityRow(result: FertilityResult, index: Int, started: Boolean) {
    val classColor = Color(result.fertilityClass.color.toInt())
    val confAlpha = when (result.confidence) {
        ConfidenceLevel.HIGH -> 1f
        ConfidenceLevel.MEDIUM -> 0.85f
        ConfidenceLevel.LOW -> 0.65f
        ConfidenceLevel.INSUFFICIENT -> 0.45f
    }
    val animScore by animateFloatAsState(
        if (started) 1f else 0f,
        tween(600, index * 80, FastOutSlowInEasing), label = "fert_$index"
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Class badge
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                .background(classColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center) {
                Text(result.fertilityClass.roman,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold, color = classColor.copy(confAlpha))
            }
            Column(Modifier.weight(1f)) {
                Text(result.essenceName, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${result.fertilityClass.label} · ${result.confidence.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = classColor.copy(alpha = confAlpha * 0.9f))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${result.treeCount} t.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (result.avgDiamCm > 0) Text("Ø ${String.format("%.0f",result.avgDiamCm)} cm",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Progress bar representing class I..IV
        val classOrdinal = when (result.fertilityClass) {
            FertilityClass.I -> 1f; FertilityClass.II -> 0.75f
            FertilityClass.III -> 0.5f; FertilityClass.IV -> 0.25f
            else -> 0f
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
            .background(classColor.copy(alpha = 0.10f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(classOrdinal * animScore)
                .clip(RoundedCornerShape(3.dp))
                .background(Brush.horizontalGradient(listOf(classColor, classColor.copy(0.5f)))))
        }
        // Height info
        val heightStr = when {
            result.dominantHeightM != null -> "H0 ~${String.format("%.1f",result.dominantHeightM)} m"
            result.loreyHeightM != null    -> "HL ~${String.format("%.1f",result.loreyHeightM)} m"
            else -> null
        }
        if (heightStr != null || result.zoneCompatibility.icon != "✓") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (heightStr != null) Text(heightStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (result.zoneCompatibility.icon != "✓") Text(
                    "${result.zoneCompatibility.icon} ${result.zoneCompatibility.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = when (result.zoneCompatibility) {
                        com.forestry.counter.domain.usecase.fertility.ZoneCompatibility.SUBOPTIMAL -> Color(0xFFC62828)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    })
            }
        }
    }
}
