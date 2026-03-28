package com.forestry.counter.presentation.screens.forestry

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.PI

private val CHART_COLORS = listOf(
    Color(0xFF2E7D32), Color(0xFF1565C0), Color(0xFFE65100), Color(0xFFC62828),
    Color(0xFF6A1B9A), Color(0xFF00695C), Color(0xFFEF6C00), Color(0xFF283593)
)

private fun essenceDisplayColor(essence: Essence?, index: Int): Color {
    essence?.colorHex?.let { hex ->
        return try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { CHART_COLORS[index % CHART_COLORS.size] }
    }
    return CHART_COLORS[index % CHART_COLORS.size]
}

data class CampaignData(
    val label: String,
    val timestamp: Long,
    val tiges: List<Tige>,
    val totalG: Double,
    val totalTiges: Int,
    val avgDiam: Double
)

enum class ReferenceMode(val label: String) {
    INITIALE("Initiale"),
    PRECEDENTE("Précédente")
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
    val tiges by tigeRepository.getTigesByParcelle(parcelleId).collectAsState(initial = emptyList())
    val essences by essenceRepository.getAllEssences().collectAsState(initial = emptyList())
    val essenceMap = remember(essences) { essences.associateBy { it.code.uppercase() } }

    val parcelle by (parcelleRepository?.getParcelleById(parcelleId)
        ?: kotlinx.coroutines.flow.flowOf(null)).collectAsState(initial = null)

    val campaigns = remember(tiges) {
        val df = SimpleDateFormat("MM/yyyy", Locale.FRANCE)
        tiges.groupBy { df.format(Date(it.timestamp)) }
            .map { (label, list) ->
                val totalG = list.sumOf { PI / 4.0 * (it.diamCm / 100.0).let { d -> d * d } }
                val avgDiam = if (list.isEmpty()) 0.0 else list.sumOf { it.diamCm } / list.size
                CampaignData(
                    label = label,
                    timestamp = list.minOf { it.timestamp },
                    tiges = list,
                    totalG = totalG,
                    totalTiges = list.size,
                    avgDiam = avgDiam
                )
            }
            .sortedBy { it.timestamp }
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Synthèse", "Évolution")

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.dashboard_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        if (selectedTab < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[selectedTab]), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface)) {
            if (selectedTab == 0) {
                SyntheseTab(tiges, parcelle, essenceMap)
            } else {
                EvolutionTab(campaigns)
            }
        }
    }
}

@Composable
private fun SyntheseTab(tiges: List<Tige>, parcelle: com.forestry.counter.domain.model.Parcelle?, essenceMap: Map<String, Essence>) {
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
        tigesWithHeight.groupBy { ((it.hauteurM!! / 2).toInt() * 2) }
            .toSortedMap().mapValues { it.value.size }
    }
    val qualityDist = remember(tiges) {
        tiges.mapNotNull { it.qualite }.groupBy { it }
            .mapValues { it.value.size }.toSortedMap()
    }

    val climateZone = remember(tiges, parcelle) {
        val altM = parcelle?.altitudeM ?: tiges.mapNotNull { it.altitudeM }.average().takeIf { !it.isNaN() }
        val wkt = tiges.mapNotNull { it.gpsWkt }.firstOrNull()
        if (wkt != null) ClimateZone.detectFromWkt(wkt, altM) else ClimateZone.UNKNOWN
    }

    val fertilityResults = remember(tiges, climateZone, essenceMap) {
        FertilityClassifier.classify(
            tiges = tiges, climateZone = climateZone,
            essenceNames = essenceMap.mapValues { it.value.name }
        )
    }

    val liocourtQ = remember(diamClasses) {
        if (diamClasses.size < 3) null
        else {
            val vals = diamClasses.values.map { it.toDouble() }
            val ratios = vals.zipWithNext { a, b -> if (a > 0) b / a else null }.filterNotNull()
            if (ratios.isEmpty()) null else ratios.average()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (tiges.isEmpty()) {
            item {
                Text(stringResource(R.string.dashboard_no_data), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            item {
                DenseKpiGrid(
                    totalTiges, speciesCount, avgDiam, totalG, avgHeight, climateZone
                )
            }
            
            item {
                DenseDataSection("Répartition par Essence") {
                    EssenceDataTable(tigesByEssence, gByEssence.associate { it.key to it.value }, totalTiges, essenceMap)
                }
            }

            if (diamClasses.isNotEmpty()) {
                item {
                    DenseDataSection("Classes de Diamètres (cm)") {
                        DenseBarChart(diamClasses, "cm")
                    }
                }
            }

            if (heightClasses.isNotEmpty()) {
                item {
                    DenseDataSection("Classes de Hauteurs (m)") {
                        DenseBarChart(heightClasses, "m")
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        DenseDataSection("Qualité du Bois") {
                            QualityDataTable(qualityDist, totalTiges)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        DenseDataSection("Structure Peuplement") {
                            StandStructureTable(diamClasses, liocourtQ)
                        }
                    }
                }
            }

            if (fertilityResults.isNotEmpty()) {
                item {
                    DenseDataSection("Fertilité Stationnelle") {
                        FertilityDataTable(fertilityResults)
                    }
                }
            }
        }
    }
}

@Composable
private fun EvolutionTab(campaigns: List<CampaignData>) {
    if (campaigns.size < 2) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.TrendingUp, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                Text(
                    "Pas assez de données pour afficher une évolution.\nAu moins 2 campagnes (mois différents) sont requises.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }
        return
    }

    var refMode by rememberSaveable { mutableStateOf(ReferenceMode.PRECEDENTE) }

    val latest = campaigns.last()
    val reference = if (refMode == ReferenceMode.INITIALE) campaigns.first() else campaigns[campaigns.size - 2]

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Sélection de la référence", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row {
                        ReferenceMode.entries.forEach { mode ->
                            val selected = refMode == mode
                            Box(
                                modifier = Modifier
                                    .clickable { refMode = mode }
                                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    mode.label,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Comparaison : ${latest.label} vs ${reference.label}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TrendBadge(
                            modifier = Modifier.weight(1f),
                            title = "Surf. Terrière",
                            currentValue = String.format("%.2f m²", latest.totalG),
                            delta = latest.totalG - reference.totalG,
                            deltaFormat = { String.format("%+.2f", it) }
                        )
                        TrendBadge(
                            modifier = Modifier.weight(1f),
                            title = "Nb Tiges",
                            currentValue = "${latest.totalTiges}",
                            delta = (latest.totalTiges - reference.totalTiges).toDouble(),
                            deltaFormat = { "${if (it > 0) "+" else ""}${it.toInt()}" }
                        )
                        TrendBadge(
                            modifier = Modifier.weight(1f),
                            title = "Diam. Moyen",
                            currentValue = String.format("%.1f cm", latest.avgDiam),
                            delta = latest.avgDiam - reference.avgDiam,
                            deltaFormat = { String.format("%+.1f", it) }
                        )
                    }
                }
            }
        }

        item {
            Text("Graphiques d'évolution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
            Spacer(Modifier.height(8.dp))
            EvolutionChartCard("Surface Terrière (m²)", campaigns) { it.totalG.toFloat() }
            Spacer(Modifier.height(12.dp))
            EvolutionChartCard("Nombre de Tiges", campaigns) { it.totalTiges.toFloat() }
            Spacer(Modifier.height(12.dp))
            EvolutionChartCard("Diamètre Moyen (cm)", campaigns) { it.avgDiam.toFloat() }
        }
    }
}

@Composable
private fun TrendBadge(modifier: Modifier = Modifier, title: String, currentValue: String, delta: Double, deltaFormat: (Double) -> String) {
    val color = when {
        delta > 0.001 -> Color(0xFF2E7D32)
        delta < -0.001 -> Color(0xFFC62828)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when {
        delta > 0.001 -> Icons.Default.TrendingUp
        delta < -0.001 -> Icons.Default.TrendingDown
        else -> Icons.Default.TrendingFlat
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.08f)
    ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(currentValue, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
                Text(deltaFormat(delta), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
            }
        }
    }
}

@Composable
private fun EvolutionChartCard(title: String, campaigns: List<CampaignData>, valueSelector: (CampaignData) -> Float) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            val values = campaigns.map(valueSelector)
            val maxVal = values.maxOrNull() ?: 1f
            val minVal = values.minOrNull() ?: 0f
            val range = (maxVal - minVal).coerceAtLeast(0.01f)
            
            val primaryColor = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 12.dp)) {
                val w = size.width
                val h = size.height
                val stepX = if (values.size > 1) w / (values.size - 1) else w
                
                val points = values.mapIndexed { index, value ->
                    val x = index * stepX
                    val normalizedY = (value - minVal) / range
                    val y = h - (normalizedY * h * 0.8f + h * 0.1f)
                    Offset(x, y)
                }
                
                for (i in 0 until points.size - 1) {
                    drawLine(
                        color = primaryColor.copy(alpha = 0.5f),
                        start = points[i],
                        end = points[i + 1],
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                
                points.forEach { point ->
                    drawCircle(
                        color = primaryColor,
                        radius = 5.dp.toPx(),
                        center = point
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.5.dp.toPx(),
                        center = point
                    )
                }
            }
            
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                campaigns.forEach { c ->
                    Text(c.label, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}


@Composable
private fun DenseKpiGrid(
    totalTiges: Int, speciesCount: Int, avgDiam: Double, totalG: Double, avgHeight: Double, climateZone: ClimateZone
) {
    val items = listOf(
        "N (tiges)" to totalTiges.toString(),
        "G (m²)" to String.format("%.3f", totalG),
        "Essences" to speciesCount.toString(),
        "Ø moy (cm)" to String.format("%.1f", avgDiam),
        "H moy (m)" to if (avgHeight > 0) String.format("%.1f", avgHeight) else "-",
        "Zone" to climateZone.labelFr
    )
    Row(
        Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        items.forEach { (label, value) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun DenseDataSection(title: String, content: @Composable () -> Unit) {
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
private fun EssenceDataTable(
    tigesData: List<Map.Entry<String, List<Tige>>>,
    gData: Map<String, Double>,
    totalTiges: Int,
    essenceMap: Map<String, Essence>
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Essence", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(2f))
            Text("N", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
            Text("%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
            Text("G (m²)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(50.dp), textAlign = TextAlign.End)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f), thickness = 0.5.dp)
        tigesData.forEachIndexed { i, (code, list) ->
            val name = essenceMap[code]?.name ?: code
            val count = list.size
            val pct = if (totalTiges > 0) count * 100.0 / totalTiges else 0.0
            val g = gData[code] ?: 0.0
            val color = essenceDisplayColor(essenceMap[code], i)
            
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(color, RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(6.dp))
                Text(name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(2f), fontSize = 11.sp)
                Text(count.toString(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(30.dp), textAlign = TextAlign.End, fontSize = 11.sp)
                Text(String.format("%.0f", pct), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(40.dp), textAlign = TextAlign.End, fontSize = 11.sp)
                Text(String.format("%.3f", g), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(50.dp), textAlign = TextAlign.End, fontSize = 11.sp)
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(Modifier.fillMaxWidth((pct/100.0).toFloat()).height(2.dp).background(color))
            }
        }
    }
}

@Composable
private fun DenseBarChart(classes: Map<Int, Int>, unit: String) {
    val maxVal = classes.values.maxOrNull() ?: 1
    val keys = classes.keys.sorted()
    
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        keys.forEach { k ->
            val v = classes[k] ?: 0
            val frac = (v.toFloat() / maxVal).coerceAtLeast(0.01f)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(26.dp)) {
                Text(v.toString(), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.width(18.dp).height((frac * 60).dp).background(MaterialTheme.colorScheme.primary.copy(alpha=0.8f), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)))
                Text(k.toString(), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun QualityDataTable(dist: Map<Int, Int>, totalTiges: Int) {
    val labels = listOf("A", "B", "C", "D")
    val assessed = dist.values.sum()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { i, label ->
            val count = dist[i] ?: 0
            val pct = if (assessed > 0) count * 100.0 / assessed else 0.0
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                Text(count.toString(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), fontSize = 11.sp)
                Text(String.format("%.0f%%", pct), style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, textAlign = TextAlign.End)
            }
            Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(Modifier.fillMaxWidth((pct/100).toFloat()).height(3.dp).background(MaterialTheme.colorScheme.primary))
            }
        }
        if (assessed < totalTiges) {
            Text("Non qual.: ${totalTiges - assessed}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StandStructureTable(diamClasses: Map<Int, Int>, liocourtQ: Double?) {
    val classesCount = diamClasses.size
    val minDiam = diamClasses.keys.minOrNull() ?: 0
    val maxDiam = diamClasses.keys.maxOrNull() ?: 0
    val lioText = liocourtQ?.let { String.format("%.2f", it) } ?: "-"
    val lioLabel = when {
        liocourtQ == null -> "N/A"
        liocourtQ in 1.1..1.8 -> "Régulière"
        liocourtQ < 0.8 -> "Homogène"
        liocourtQ > 2.2 -> "Irrégulière"
        else -> "Hétérogène"
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("q Liocourt", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
            Text(lioText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Type", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
            Text(lioLabel, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Classes Ø", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
            Text(classesCount.toString(), style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Min/Max", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
            Text("$minDiam / $maxDiam", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
        }
    }
}

@Composable
private fun FertilityDataTable(results: List<FertilityResult>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Essence", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text("Classe", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(40.dp))
            Text("Conf.", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f), thickness = 0.5.dp)
        results.forEach { res ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(res.essenceName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), fontSize = 11.sp)
                Text(res.fertilityClass.roman, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp), fontSize = 11.sp)
                Text(res.confidence.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(40.dp), textAlign = TextAlign.End, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
