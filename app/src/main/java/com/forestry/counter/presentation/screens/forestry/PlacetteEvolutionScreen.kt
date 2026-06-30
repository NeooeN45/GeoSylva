package com.forestry.counter.presentation.screens.forestry

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forestry.counter.R
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.repository.EssenceRepository
import com.forestry.counter.domain.repository.PlacetteRepository
import com.forestry.counter.domain.repository.TigeRepository
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacetteEvolutionScreen(
    placetteId: String,
    year: Int,
    tigeRepository: TigeRepository,
    essenceRepository: EssenceRepository,
    placetteRepository: PlacetteRepository,
    onNavigateBack: () -> Unit
) {
    val viewModel = remember(placetteId, year) {
        PlacetteEvolutionViewModel(
            placetteId = placetteId,
            year = year,
            tigeRepository = tigeRepository,
            essenceRepository = essenceRepository,
            placetteRepository = placetteRepository
        )
    }

    val tiges by viewModel.tiges.collectAsStateWithLifecycle()
    val essences by viewModel.essences.collectAsStateWithLifecycle()
    val placette by viewModel.placette.collectAsStateWithLifecycle()

    val surfaceM2 = placette?.surfaceM2 ?: placette?.rayonM?.let { r ->
        Math.PI * r * r
    }

    val yearStats = remember(tiges, essences, surfaceM2, year) {
        PlacetteEvolutionViewModel.computeYearStats(year, tiges, essences, surfaceM2)
    }

    val allYearSummaries = remember(tiges, surfaceM2) {
        PlacetteEvolutionViewModel.computeYearSummaries(tiges, surfaceM2)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.evolution_detail_title, year)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        if (yearStats.stemCount == 0) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Science,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.evolution_detail_no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Section 1 : Indicateurs globaux
            item {
                SectionCard(
                    title = stringResource(R.string.evolution_detail_section_global),
                    icon = Icons.Default.Insights
                ) {
                    GlobalIndicatorsGrid(yearStats)
                }
            }

            // Section 2 : Distribution des diamètres
            if (yearStats.diameterDistribution.isNotEmpty()) {
                item {
                    SectionCard(
                        title = stringResource(R.string.evolution_detail_section_distribution),
                        icon = Icons.Default.Straighten
                    ) {
                        DiameterDistributionChart(yearStats.diameterDistribution)
                    }
                }
            }

            // Section 3 : Évolution temporelle
            if (allYearSummaries.size >= 2) {
                item {
                    SectionCard(
                        title = stringResource(R.string.evolution_detail_section_temporal),
                        icon = Icons.Default.TrendingUp
                    ) {
                        TemporalEvolutionChart(allYearSummaries)
                    }
                }
            }

            // Section 4 : Par essence
            if (yearStats.byEssence.isNotEmpty()) {
                item {
                    SectionCard(
                        title = stringResource(R.string.evolution_detail_section_by_essence),
                        icon = Icons.Default.Park
                    ) {
                        EssenceTable(yearStats.byEssence)
                    }
                }
            }

            // Section 5 : Catégories de martelage (donut + barres)
            if (yearStats.byCategory.isNotEmpty()) {
                item {
                    SectionCard(
                        title = stringResource(R.string.evolution_detail_section_categories),
                        icon = Icons.Default.Forest
                    ) {
                        CategoryDonutChart(yearStats.byCategory)
                        Spacer(Modifier.height(12.dp))
                        CategoryBreakdown(yearStats.byCategory)
                    }
                }
            }

            // Section 6 : Indicateurs dendrométriques détaillés
            item {
                SectionCard(
                    title = stringResource(R.string.evolution_detail_section_dendro),
                    icon = Icons.Default.Eco
                ) {
                    DendrometricIndicators(yearStats)
                }
            }

            // Section 7 : Détail des tiges (liste sélectionnable)
            if (yearStats.tiges.isNotEmpty()) {
                item {
                    SectionCard(
                        title = stringResource(R.string.evolution_detail_section_stems),
                        icon = Icons.Default.Grass
                    ) {
                        StemsList(yearStats.tiges, essences)
                    }
                }
            }

            // Espace pour le FAB potentiel
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ── Section card générique ────────────────────────────────────────────────────

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

// ── Section 1 : Indicateurs globaux (grille 2 colonnes) ───────────────────────

@Composable
private fun GlobalIndicatorsGrid(stats: YearEvolutionStats) {
    val indicators = buildList {
        add(IndicatorItem(stringResource(R.string.evolution_detail_stems), stats.stemCount.toString(), null))
        add(IndicatorItem(stringResource(R.string.evolution_detail_species), stats.essenceCount.toString(), null))
        add(IndicatorItem(stringResource(R.string.evolution_detail_dm), formatCm(stats.meanDiameterCm), stringResource(R.string.evolution_detail_unit_cm)))
        add(IndicatorItem(stringResource(R.string.evolution_detail_dg), formatCm(stats.quadraticMeanDiameterCm), stringResource(R.string.evolution_detail_unit_cm)))
        add(IndicatorItem(stringResource(R.string.evolution_detail_g), formatM2(stats.basalAreaM2), stringResource(R.string.evolution_detail_unit_m2)))
        stats.basalAreaPerHaM2?.let {
            add(IndicatorItem(stringResource(R.string.evolution_detail_g_per_ha), formatM2(it), stringResource(R.string.evolution_detail_unit_m2_ha)))
        }
        stats.stemsPerHa?.let {
            add(IndicatorItem(stringResource(R.string.evolution_detail_n_per_ha), formatInt(it.toInt()), stringResource(R.string.evolution_detail_unit_stems_ha)))
        }
        stats.meanHeightM?.let {
            add(IndicatorItem(stringResource(R.string.evolution_detail_hm), formatCm(it), stringResource(R.string.evolution_detail_unit_cm)))
        }
        stats.loreyHeightM?.let {
            add(IndicatorItem(stringResource(R.string.evolution_detail_hg), formatCm(it), stringResource(R.string.evolution_detail_unit_cm)))
        }
        stats.volumeM3?.let {
            add(IndicatorItem(stringResource(R.string.evolution_detail_volume), formatM3(it), stringResource(R.string.evolution_detail_unit_m3)))
        }
        stats.volumePerHaM3?.let {
            add(IndicatorItem(stringResource(R.string.evolution_detail_v_per_ha), formatM3(it), stringResource(R.string.evolution_detail_unit_m3_ha)))
        }
        stats.biomassTonnes?.let {
            add(IndicatorItem(stringResource(R.string.evolution_detail_biomass), formatT(it), stringResource(R.string.evolution_detail_unit_t)))
        }
        stats.carbonTonnes?.let {
            add(IndicatorItem(stringResource(R.string.evolution_detail_carbon), formatT(it), stringResource(R.string.evolution_detail_unit_t)))
        }
        if (stats.habitatTreesCount > 0) {
            add(IndicatorItem(stringResource(R.string.evolution_detail_habitat_trees), stats.habitatTreesCount.toString(), null))
        }
    }

    // Grille 2 colonnes
    val rows = indicators.chunked(2)
    rows.forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            row.forEach { item ->
                IndicatorTile(item, modifier = Modifier.weight(1f))
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }

    if (stats.meanHeightM == null) {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.evolution_detail_no_heights),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )
    }
}

private data class IndicatorItem(val label: String, val value: String, val unit: String?)

@Composable
private fun IndicatorTile(item: IndicatorItem, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                item.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    item.value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                item.unit?.let {
                    Spacer(Modifier.width(2.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}

// ── Section 2 : Distribution des diamètres (histogramme animé) ────────────────

@Composable
private fun DiameterDistributionChart(distribution: List<DiameterClassEntry>) {
    val maxN = distribution.maxOfOrNull { it.stemCount } ?: 1
    val primary = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        distribution.forEach { entry ->
            val targetFraction = entry.stemCount.toFloat() / maxN.coerceAtLeast(1).toFloat()
            val animatedFraction by animateFloatAsState(
                targetValue = targetFraction,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "bar_${entry.diamClass}"
            )
            val barH = (animatedFraction * 120).coerceAtLeast(6f)
            val barGradient = Brush.verticalGradient(
                colors = listOf(primary, primary.copy(alpha = 0.45f))
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(40.dp)
            ) {
                Text(
                    "${entry.stemCount}",
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .width(26.dp)
                        .height(barH.dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(barGradient)
                )
                Text(
                    "${entry.diamClass}",
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            stringResource(R.string.evolution_detail_chart_n),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(R.string.evolution_detail_chart_diam),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Section 3 : Évolution temporelle (line chart canvas) ──────────────────────

@Composable
private fun TemporalEvolutionChart(summaries: List<YearSummary>) {
    val years = summaries.map { it.year.toString() }
    val stemValues = summaries.map { it.stemCount.toFloat() }
    val dmValues = summaries.map { it.meanDiameterCm.toFloat() }
    val gValues = summaries.map { it.basalAreaM2.toFloat() }

    val maxStems = (stemValues.maxOrNull() ?: 1f).coerceAtLeast(1f)
    val maxDm = (dmValues.maxOrNull() ?: 1f).coerceAtLeast(1f)
    val maxG = (gValues.maxOrNull() ?: 1f).coerceAtLeast(0.1f)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Tiges
        TemporalLine(
            label = stringResource(R.string.evolution_detail_temporal_stems),
            values = stemValues,
            labels = years,
            color = MaterialTheme.colorScheme.primary,
            maxVal = maxStems,
            minVal = 0f
        )
        // Dm
        TemporalLine(
            label = stringResource(R.string.evolution_detail_temporal_dm),
            values = dmValues,
            labels = years,
            color = Color(0xFF4CAF50),
            maxVal = maxDm,
            minVal = 0f
        )
        // G
        TemporalLine(
            label = stringResource(R.string.evolution_detail_temporal_g),
            values = gValues,
            labels = years,
            color = Color(0xFF2196F3),
            maxVal = maxG,
            minVal = 0f
        )
    }
}

@Composable
private fun TemporalLine(
    label: String,
    values: List<Float>,
    labels: List<String>,
    color: Color,
    maxVal: Float,
    minVal: Float
) {
    val n = values.size
    if (n < 2) return

    val animatedValues = values.mapIndexed { i, target ->
        animateFloatAsState(
            targetValue = target,
            animationSpec = tween(600 + i * 80, easing = FastOutSlowInEasing),
            label = "temporal_$i"
        ).value
    }

    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
            val w = size.width
            val h = size.height
            val range = (maxVal - minVal).coerceAtLeast(0.1f)
            fun xOf(i: Int) = if (n == 1) w / 2f else i.toFloat() / (n - 1) * w
            fun yOf(v: Float) = h - ((v - minVal) / range * h).coerceIn(0f, h)

            // Fill path
            val fillPath = Path()
            fillPath.moveTo(xOf(0), h)
            animatedValues.forEachIndexed { i, v -> fillPath.lineTo(xOf(i), yOf(v)) }
            fillPath.lineTo(xOf(n - 1), h)
            fillPath.close()
            drawPath(fillPath, color.copy(alpha = 0.18f))

            // Line path
            val linePath = Path()
            animatedValues.forEachIndexed { i, v ->
                if (i == 0) linePath.moveTo(xOf(i), yOf(v)) else linePath.lineTo(xOf(i), yOf(v))
            }
            drawPath(
                linePath,
                color,
                style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Dots
            animatedValues.forEachIndexed { i, v ->
                drawCircle(color, 4f, Offset(xOf(i), yOf(v)))
            }
        }
        // Labels années
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            labels.forEach { y ->
                Text(
                    y,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
        }
    }
}

// ── Section 4 : Tableau par essence ───────────────────────────────────────────

@Composable
private fun EssenceTable(byEssence: List<EssenceYearStats>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // En-tête
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.evolution_detail_table_essence),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1.5f)
            )
            Text(stringResource(R.string.evolution_detail_table_stems), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(0.6f))
            Text(stringResource(R.string.evolution_detail_table_dm), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(0.7f))
            Text(stringResource(R.string.evolution_detail_table_g), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(0.7f))
            Text(stringResource(R.string.evolution_detail_table_pct), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.weight(0.8f))
        }
        HorizontalDividerLite()
        byEssence.forEach { ess ->
            val essColor = ess.colorHex?.let {
                runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
            } ?: MaterialTheme.colorScheme.primary
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1.5f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(essColor)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        ess.essenceName,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text("${ess.stemCount}", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.weight(0.6f))
                Text(formatCm(ess.meanDiameterCm), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.weight(0.7f))
                Text(formatM2(ess.basalAreaM2), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.weight(0.7f))
                Text("${"%.0f".format(ess.percentOfBasalArea)}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(0.8f))
            }
        }
    }
}

@Composable
private fun HorizontalDividerLite() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}

// ── Section 5 : Catégories de martelage ───────────────────────────────────────

@Composable
private fun CategoryBreakdown(categories: List<CategoryYearStats>) {
    val maxCount = (categories.maxOfOrNull { it.stemCount } ?: 1).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.forEach { cat ->
            val catColor = Color(cat.color)
            val targetFraction = cat.stemCount.toFloat() / maxCount.toFloat()
            val animatedFraction by animateFloatAsState(
                targetValue = targetFraction,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "cat_${cat.category}"
            )
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(catColor)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        cat.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${cat.stemCount}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = catColor
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedFraction)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(catColor.copy(alpha = 0.7f))
                    )
                }
            }
        }
    }
}

// ── Section 6 : Indicateurs dendrométriques détaillés ─────────────────────────

@Composable
private fun DendrometricIndicators(stats: YearEvolutionStats) {
    val rows = buildList {
        add(stringResource(R.string.evolution_detail_dm) to formatCm(stats.meanDiameterCm) + " " + stringResource(R.string.evolution_detail_unit_cm))
        add(stringResource(R.string.evolution_detail_dg) to formatCm(stats.quadraticMeanDiameterCm) + " " + stringResource(R.string.evolution_detail_unit_cm))
        add(stringResource(R.string.evolution_detail_g) to formatM2(stats.basalAreaM2) + " " + stringResource(R.string.evolution_detail_unit_m2))
        stats.basalAreaPerHaM2?.let {
            add(stringResource(R.string.evolution_detail_g_per_ha) to formatM2(it) + " " + stringResource(R.string.evolution_detail_unit_m2_ha))
        }
        stats.stemsPerHa?.let {
            add(stringResource(R.string.evolution_detail_n_per_ha) to formatInt(it.toInt()) + " " + stringResource(R.string.evolution_detail_unit_stems_ha))
        }
        stats.meanHeightM?.let {
            add(stringResource(R.string.evolution_detail_hm) to formatCm(it) + " " + stringResource(R.string.evolution_detail_unit_cm))
        }
        stats.loreyHeightM?.let {
            add(stringResource(R.string.evolution_detail_hg) to formatCm(it) + " " + stringResource(R.string.evolution_detail_unit_cm))
        }
        stats.volumeM3?.let {
            add(stringResource(R.string.evolution_detail_volume) to formatM3(it) + " " + stringResource(R.string.evolution_detail_unit_m3))
        }
        stats.volumePerHaM3?.let {
            add(stringResource(R.string.evolution_detail_v_per_ha) to formatM3(it) + " " + stringResource(R.string.evolution_detail_unit_m3_ha))
        }
    }
    rows.forEach { (label, value) ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// ── Helpers formatage ─────────────────────────────────────────────────────────

private fun formatCm(value: Double): String = "%.1f".format(value)
private fun formatM2(value: Double): String = "%.3f".format(value)
private fun formatM3(value: Double): String = "%.2f".format(value)
private fun formatT(value: Double): String = "%.2f".format(value)
private fun formatInt(value: Int): String = value.toString()

// ── Section 5b : Donut chart catégories de martelage ──────────────────────────

@Composable
private fun CategoryDonutChart(categories: List<CategoryYearStats>) {
    val total = categories.sumOf { it.stemCount }.toFloat().coerceAtLeast(1f)
    val animatedSweeps = categories.map { cat ->
        val target = 360f * cat.stemCount / total
        animateFloatAsState(
            targetValue = target,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "donut_${cat.category}"
        ).value
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Donut canvas
        Canvas(modifier = Modifier.size(140.dp)) {
            val strokeW = 28.dp.toPx()
            val r = (size.minDimension - strokeW) / 2f
            val tl = Offset((size.width - 2 * r - strokeW) / 2f, (size.height - 2 * r - strokeW) / 2f)
            val arcSz = Size(2 * r + strokeW, 2 * r + strokeW)

            // Anneau de fond
            drawArc(
                Color.Gray.copy(alpha = 0.08f), -90f, 360f, false, tl, arcSz,
                style = Stroke(strokeW, cap = StrokeCap.Round)
            )

            var startAngle = -90f
            categories.forEachIndexed { i, cat ->
                val sweep = animatedSweeps[i]
                if (sweep > 0.5f) {
                    val catColor = Color(cat.color)
                    // Halo
                    drawArc(
                        catColor.copy(alpha = 0.2f), startAngle, sweep - 1f, false, tl, arcSz,
                        style = Stroke(strokeW + 6.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // Arc principal
                    drawArc(
                        catColor, startAngle, sweep - 1f, false, tl, arcSz,
                        style = Stroke(strokeW, cap = StrokeCap.Round)
                    )
                }
                startAngle += 360f * cat.stemCount / total
            }
        }

        Spacer(Modifier.width(20.dp))

        // Légende compacte
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.forEach { cat ->
                val pct = if (total > 0) cat.stemCount / total * 100 else 0.0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(cat.color))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        cat.label,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${cat.stemCount} (${pct.toInt()}%)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(cat.color)
                    )
                }
            }
        }
    }
}

// ── Section 7 : Détail des tiges (liste sélectionnable) ───────────────────────

@Composable
private fun StemsList(tiges: List<Tige>, essences: List<com.forestry.counter.domain.model.Essence>) {
    var selectedTigeId by remember { mutableStateOf<String?>(null) }

    Column {
        Text(
            stringResource(R.string.evolution_detail_stems_count_format, tiges.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        tiges.forEach { tige ->
            val essence = essences.firstOrNull { it.code == tige.essenceCode }
            val isSelected = selectedTigeId == tige.id
            val essColor = essence?.colorHex?.let {
                runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
            } ?: MaterialTheme.colorScheme.primary

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface
                ),
                onClick = {
                    selectedTigeId = if (isSelected) null else tige.id
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pastille couleur essence
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(essColor)
                    )
                    Spacer(Modifier.width(8.dp))
                    // Numéro tige si présent
                    tige.numero?.let { num ->
                        Text(
                            "#$num",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                    // Nom essence
                    Text(
                        essence?.name ?: tige.essenceCode,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Diamètre
                    Text(
                        formatCm(tige.diamCm) + " " + stringResource(R.string.evolution_detail_unit_cm),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Détails expansés si sélectionné
                if (isSelected) {
                    HorizontalDividerLite()
                    Column(modifier = Modifier.padding(10.dp)) {
                        StemDetailRow(
                            stringResource(R.string.evolution_detail_stem_diam),
                            formatCm(tige.diamCm) + " " + stringResource(R.string.evolution_detail_unit_cm)
                        )
                        StemDetailRow(
                            stringResource(R.string.evolution_detail_stem_height),
                            tige.hauteurM?.let { formatCm(it) + " " + stringResource(R.string.evolution_detail_unit_cm) }
                                ?: stringResource(R.string.evolution_detail_stem_no_height)
                        )
                        StemDetailRow(
                            stringResource(R.string.evolution_detail_stem_category),
                            tige.categorie?.let { cat ->
                                val label = when (cat.uppercase().trim()) {
                                    "AVENIR" -> "Avenir"
                                    "RESERVE" -> "Réserve"
                                    "ENLEVER" -> "Enlever"
                                    "DEPERIR" -> "Dépérir"
                                    "BIODIV" -> "Biodiversité"
                                    else -> cat
                                }
                                label
                            } ?: stringResource(R.string.evolution_detail_stem_no_category)
                        )
                        StemDetailRow(
                            stringResource(R.string.evolution_detail_stem_quality),
                            tige.qualite?.let { "Grade $it" } ?: "—"
                        )
                        // Biomasse / carbone si disponibles
                        tige.biomasseFusTonnes?.let { bio ->
                            StemDetailRow(
                                stringResource(R.string.evolution_detail_biomass),
                                formatT(bio) + " " + stringResource(R.string.evolution_detail_unit_t)
                            )
                        }
                        tige.carboneFusTonnes?.let { carb ->
                            StemDetailRow(
                                stringResource(R.string.evolution_detail_carbon),
                                formatT(carb) + " " + stringResource(R.string.evolution_detail_unit_t)
                            )
                        }
                        if (tige.isTigeHabitat) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "🌳 " + stringResource(R.string.evolution_detail_habitat_trees),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StemDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}
