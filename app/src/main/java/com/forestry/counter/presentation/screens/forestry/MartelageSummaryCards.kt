package com.forestry.counter.presentation.screens.forestry

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import com.forestry.counter.R
import com.forestry.counter.domain.calculation.BiodiversityIndex
import com.forestry.counter.domain.calculation.ClassDistEntry
import com.forestry.counter.domain.calculation.PerEssenceStats
import com.forestry.counter.domain.calculation.QualityDistEntry
import com.forestry.counter.domain.calculation.SanitySeverity
import com.forestry.counter.domain.calculation.SpecialTreeEntry
import com.forestry.counter.domain.calculation.SanityWarning
import com.forestry.counter.domain.calculation.quality.WoodQualityGrade
import com.forestry.counter.domain.model.Essence
import com.forestry.counter.presentation.utils.ColorUtils
import java.util.Locale

/**
 * Carte Volume & Prix (inclut un indicateur de complétude si partiel).
 */
@Composable
internal fun VolumeCard(
    vTotalText: String,
    vPerHaText: String,
    revenueTotalText: String,
    revenuePerHaText: String,
    volumeAvailable: Boolean,
    volumeCompletenessPct: Double
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(500)) + slideInVertically(tween(500, easing = FastOutSlowInEasing)) { it / 5 }
    ) {
    val volumeCardBg = MaterialTheme.colorScheme.primaryContainer
    val volumeCardContent = ColorUtils.getContrastingTextColor(volumeCardBg)
    val volumeGradient = Brush.linearGradient(
        colors = listOf(
            volumeCardBg,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                .let { p -> Color(red = (volumeCardBg.red * 0.88f + p.red * 0.12f).coerceIn(0f,1f), green = (volumeCardBg.green * 0.92f + p.green * 0.08f).coerceIn(0f,1f), blue = (volumeCardBg.blue * 0.85f + p.blue * 0.15f).coerceIn(0f,1f), alpha = 1f) }
        ),
        start = Offset.Zero,
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(18.dp), spotColor = volumeCardBg.copy(alpha = 0.4f))
            .clip(RoundedCornerShape(18.dp))
            .drawBehind { drawRect(volumeGradient) },
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = volumeCardContent
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(stringResource(R.string.martelage_volume_price_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.martelage_volume_desc),
                style = MaterialTheme.typography.bodySmall,
                color = volumeCardContent.copy(alpha = 0.7f)
            )
            if (!volumeAvailable) {
                Text(
                    stringResource(R.string.martelage_volume_partial_format, volumeCompletenessPct),
                    style = MaterialTheme.typography.labelSmall,
                    color = volumeCardContent.copy(alpha = 0.8f)
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem(label = stringResource(R.string.martelage_label_v_total), value = "$vTotalText m³")
                StatItem(label = stringResource(R.string.martelage_label_v_per_ha), value = "$vPerHaText m³/ha")
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = volumeCardContent.copy(alpha = 0.15f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem(label = stringResource(R.string.martelage_label_revenue), value = revenueTotalText)
                StatItem(label = stringResource(R.string.martelage_label_revenue_per_ha), value = revenuePerHaText)
            }
        }
    }
    } // AnimatedVisibility
}

/**
 * Carte Surface terrière (G prélevé / G/ha) avec surface en ha.
 */
@Composable
internal fun BasalAreaCard(
    gTotal: Double,
    gPerHa: Double,
    surfaceHa: Double? = null,
    ratioVG: Double? = null
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(500, delayMillis = 100)) + slideInVertically(tween(500, delayMillis = 100, easing = FastOutSlowInEasing)) { it / 5 }
    ) {
    val surfaceCardBg = MaterialTheme.colorScheme.secondaryContainer
    val surfaceCardContent = ColorUtils.getContrastingTextColor(surfaceCardBg)
    val basalGradient = Brush.linearGradient(
        colors = listOf(
            surfaceCardBg,
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
                .let { s -> Color(red = (surfaceCardBg.red * 0.88f + s.red * 0.12f).coerceIn(0f,1f), green = (surfaceCardBg.green * 0.90f + s.green * 0.10f).coerceIn(0f,1f), blue = (surfaceCardBg.blue * 0.87f + s.blue * 0.13f).coerceIn(0f,1f), alpha = 1f) }
        ),
        start = Offset(Float.POSITIVE_INFINITY, 0f),
        end = Offset(0f, Float.POSITIVE_INFINITY)
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(5.dp, RoundedCornerShape(18.dp), spotColor = surfaceCardBg.copy(alpha = 0.35f))
            .clip(RoundedCornerShape(18.dp))
            .drawBehind { drawRect(basalGradient) },
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = surfaceCardContent
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(stringResource(R.string.martelage_basal_area_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.martelage_basal_area_desc),
                style = MaterialTheme.typography.bodySmall,
                color = surfaceCardContent.copy(alpha = 0.7f)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem(label = stringResource(R.string.martelage_label_g_total), value = "${formatG(gTotal)} m²")
                StatItem(label = stringResource(R.string.martelage_label_g_per_ha), value = "${formatG(gPerHa)} m²/ha")
            }
            if (surfaceHa != null || ratioVG != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = surfaceCardContent.copy(alpha = 0.15f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (surfaceHa != null) {
                        StatItem(
                            label = stringResource(R.string.martelage_label_surface),
                            value = String.format(Locale.getDefault(), "%.4f ha", surfaceHa)
                        )
                    }
                    if (ratioVG != null) {
                        StatItem(
                            label = stringResource(R.string.martelage_label_ratio_vg),
                            value = String.format(Locale.getDefault(), "%.1f", ratioVG)
                        )
                    }
                }
            }
        }
    }
    } // AnimatedVisibility
}

/**
 * Carte Densité & structure dendrométrique complète.
 */
@Composable
internal fun DensityCard(
    nTotal: Int,
    nPerHa: Double,
    dm: Double?,
    meanH: Double?,
    dg: Double?,
    hLorey: Double?,
    dMin: Double?,
    dMax: Double?,
    cvDiam: Double?,
    placeholderDash: String
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(500, delayMillis = 200)) + slideInVertically(tween(500, delayMillis = 200, easing = FastOutSlowInEasing)) { it / 5 }
    ) {
    val densityCardBg = MaterialTheme.colorScheme.tertiaryContainer
    val densityCardContent = ColorUtils.getContrastingTextColor(densityCardBg)
    val densityGradient = Brush.linearGradient(
        colors = listOf(
            densityCardBg,
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.20f)
                .let { t -> Color(red = (densityCardBg.red * 0.87f + t.red * 0.13f).coerceIn(0f,1f), green = (densityCardBg.green * 0.90f + t.green * 0.10f).coerceIn(0f,1f), blue = (densityCardBg.blue * 0.86f + t.blue * 0.14f).coerceIn(0f,1f), alpha = 1f) }
        ),
        start = Offset.Zero,
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(5.dp, RoundedCornerShape(18.dp), spotColor = densityCardBg.copy(alpha = 0.35f))
            .clip(RoundedCornerShape(18.dp))
            .drawBehind { drawRect(densityGradient) },
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = densityCardContent
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(stringResource(R.string.martelage_density_structure_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.martelage_density_desc),
                style = MaterialTheme.typography.bodySmall,
                color = densityCardContent.copy(alpha = 0.7f)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem(label = stringResource(R.string.martelage_label_n_total), value = "$nTotal")
                StatItem(label = stringResource(R.string.martelage_label_n_per_ha), value = "${formatIntPerHa(nPerHa)}/ha")
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = densityCardContent.copy(alpha = 0.15f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem(label = stringResource(R.string.martelage_label_dm), value = "${formatDiameter(dm, placeholderDash)} cm")
                StatItem(label = stringResource(R.string.martelage_label_dg), value = "${formatDiameter(dg, placeholderDash)} cm")
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem(label = stringResource(R.string.martelage_label_hm), value = "${formatHeight(meanH, placeholderDash)} m")
                StatItem(label = stringResource(R.string.martelage_label_hlorey), value = "${formatHeight(hLorey, placeholderDash)} m")
            }
            if (dMin != null || dMax != null || cvDiam != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = densityCardContent.copy(alpha = 0.15f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (dMin != null && dMax != null) {
                        StatItem(
                            label = stringResource(R.string.martelage_label_d_range),
                            value = "${formatDiameter(dMin, placeholderDash)} – ${formatDiameter(dMax, placeholderDash)} cm"
                        )
                    }
                    if (cvDiam != null) {
                        StatItem(
                            label = stringResource(R.string.martelage_label_cv_diam),
                            value = String.format(Locale.getDefault(), "%.0f %%", cvDiam)
                        )
                    }
                }
            }
        }
    }
    } // AnimatedVisibility
}

/**
 * Carte simulation de coupe — taux de prélèvement et peuplement résiduel.
 */
@Composable
internal fun HarvestSimulationCard(
    harvestNhaPct: Double?,
    harvestGhaPct: Double?,
    residualNha: Double?,
    residualGha: Double?
) {
    if (harvestNhaPct == null && harvestGhaPct == null) return
    val cardBg = MaterialTheme.colorScheme.tertiaryContainer
    val cardContent = ColorUtils.getContrastingTextColor(cardBg)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(
            containerColor = cardBg,
            contentColor = cardContent
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                stringResource(R.string.martelage_harvest_simulation_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            // Removal rates
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (harvestNhaPct != null) {
                    StatItem(
                        label = stringResource(R.string.martelage_harvest_n_pct),
                        value = String.format(Locale.getDefault(), "%.0f %%", harvestNhaPct),
                        color = cardContent
                    )
                }
                if (harvestGhaPct != null) {
                    StatItem(
                        label = stringResource(R.string.martelage_harvest_g_pct),
                        value = String.format(Locale.getDefault(), "%.0f %%", harvestGhaPct),
                        color = cardContent
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 2.dp),
                color = cardContent.copy(alpha = 0.15f)
            )
            // Residual stand
            Text(
                stringResource(R.string.martelage_residual_stand),
                style = MaterialTheme.typography.labelMedium,
                color = cardContent.copy(alpha = 0.7f)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem(
                    label = "N/ha",
                    value = if (residualNha != null) String.format(Locale.getDefault(), "%.0f", residualNha) else "–",
                    color = cardContent
                )
                StatItem(
                    label = "G/ha",
                    value = if (residualGha != null) String.format(Locale.getDefault(), "%.2f m²", residualGha) else "–",
                    color = cardContent
                )
            }
        }
    }
}

/**
 * Carte distribution par classe de diamètre.
 */
@Composable
internal fun ClassDistributionCard(
    classDistribution: List<ClassDistEntry>
) {
    if (classDistribution.isEmpty()) return
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(500, delayMillis = 150)) + slideInVertically(tween(500, delayMillis = 150, easing = FastOutSlowInEasing)) { it / 5 }
    ) {
    val cardBg = MaterialTheme.colorScheme.surfaceVariant
    val cardContent = ColorUtils.getContrastingTextColor(cardBg)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(
            containerColor = cardBg,
            contentColor = cardContent
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(stringResource(R.string.martelage_class_distribution_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val maxN = classDistribution.maxOfOrNull { it.n } ?: 1
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                classDistribution.filter { it.n > 0 }.forEachIndexed { index, entry ->
                    val targetFraction = entry.n.toFloat() / maxN.coerceAtLeast(1).toFloat()
                    val animatedFraction by animateFloatAsState(
                        targetValue = targetFraction,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "bar_${entry.diamClass}"
                    )
                    val barH = (animatedFraction * 96).coerceAtLeast(6f)
                    val primary = MaterialTheme.colorScheme.primary
                    val barGradient = Brush.verticalGradient(
                        colors = listOf(primary, primary.copy(alpha = 0.45f))
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(36.dp)
                    ) {
                        Text(
                            "${entry.n}",
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center
                        )
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height(barH.dp)
                                .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
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
            // Légende
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.martelage_class_dist_legend_n),
                    style = MaterialTheme.typography.labelSmall,
                    color = cardContent.copy(alpha = 0.6f)
                )
                Text(
                    stringResource(R.string.martelage_class_dist_legend_diam),
                    style = MaterialTheme.typography.labelSmall,
                    color = cardContent.copy(alpha = 0.6f)
                )
            }
        }
    }
    } // AnimatedVisibility
}

/**
 * Carte distribution qualité du bois (A/B/C/D).
 */
@Composable
internal fun QualityDistributionCard(
    qualityDistribution: List<QualityDistEntry>,
    assessedCount: Int,
    totalCount: Int
) {
    if (assessedCount == 0) return
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(500, delayMillis = 250)) + slideInVertically(tween(500, delayMillis = 250, easing = FastOutSlowInEasing)) { it / 5 }
    ) {
    val cardBg = MaterialTheme.colorScheme.surfaceVariant
    val cardContent = ColorUtils.getContrastingTextColor(cardBg)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(
            containerColor = cardBg,
            contentColor = cardContent
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.martelage_quality_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.martelage_quality_assessed_format, assessedCount, totalCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = cardContent.copy(alpha = 0.6f)
                )
            }

            qualityDistribution.forEachIndexed { index, entry ->
                val gradeColor = when (entry.grade) {
                    WoodQualityGrade.A -> Color(0xFF4CAF50)
                    WoodQualityGrade.B -> Color(0xFF2196F3)
                    WoodQualityGrade.C -> Color(0xFFFF9800)
                    WoodQualityGrade.D -> Color(0xFFF44336)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Badge grade
                    Surface(
                        color = gradeColor,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.width(32.dp)
                    ) {
                        Text(
                            entry.grade.shortLabel,
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    // Barre proportionnelle
                    val targetFraction = (entry.pct / 100.0).coerceIn(0.0, 1.0).toFloat()
                    val animatedFraction by animateFloatAsState(
                        targetValue = targetFraction,
                        animationSpec = tween(700, delayMillis = 80 * index),
                        label = "quality_${entry.grade}"
                    )
                    Box(modifier = Modifier.weight(1f).height(14.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedFraction)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(gradeColor.copy(alpha = 0.85f), gradeColor.copy(alpha = 0.4f))
                                    )
                                )
                        )
                    }
                    // Count + pct
                    Text(
                        "${entry.count} (${String.format(Locale.getDefault(), "%.0f", entry.pct)}%)",
                        style = MaterialTheme.typography.labelMedium,
                        color = cardContent.copy(alpha = 0.8f),
                        modifier = Modifier.width(72.dp),
                        textAlign = TextAlign.End
                    )
                }
            }

            // Coverage indicator
            val coveragePct = if (totalCount > 0) assessedCount.toDouble() / totalCount * 100.0 else 0.0
            if (coveragePct < 100.0) {
                Text(
                    stringResource(R.string.martelage_quality_coverage_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = cardContent.copy(alpha = 0.5f)
                )
            }
        }
    }
    } // AnimatedVisibility
}

/**
 * Tableau par essence enrichi (N, N%, V, V%, G, G%, Dm, Dg, €/m³, recette).
 */
@Composable
internal fun PerEssenceTable(
    perEssence: List<PerEssenceStats>,
    essences: List<Essence>,
    placeholderDash: String,
    euroSymbol: String
) {
    Spacer(modifier = Modifier.height(12.dp))
    Text(stringResource(R.string.martelage_per_species_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

    val perEssenceCardBg = MaterialTheme.colorScheme.surfaceVariant
    val perEssenceCardContent = ColorUtils.getContrastingTextColor(perEssenceCardBg)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(
            containerColor = perEssenceCardBg,
            contentColor = perEssenceCardContent
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (row in perEssence) {
                val tint = essenceTintColor(row.essenceCode, essences)
                val bg = tint?.copy(alpha = 0.12f)
                    ?: MaterialTheme.colorScheme.surface.copy(alpha = 0.04f)
                val rowTextColor = ColorUtils.getContrastingTextColor(bg)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = bg, contentColor = rowTextColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    row.essenceName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (row.dominantQuality != null) {
                                    val qColor = when (row.dominantQuality) {
                                        WoodQualityGrade.A -> Color(0xFF4CAF50)
                                        WoodQualityGrade.B -> Color(0xFF2196F3)
                                        WoodQualityGrade.C -> Color(0xFFFF9800)
                                        WoodQualityGrade.D -> Color(0xFFF44336)
                                    }
                                    Surface(
                                        color = qColor,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            row.dominantQuality.shortLabel,
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                "N=${row.n} (${String.format(Locale.getDefault(), "%.0f", row.nPct)}%)",
                                style = MaterialTheme.typography.labelMedium,
                                color = rowTextColor.copy(alpha = 0.7f)
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatItem(
                                label = "V",
                                value = "${formatVolume(row.vTotal)} m³ (${String.format(Locale.getDefault(), "%.0f", row.vPct)}%)",
                                color = rowTextColor
                            )
                            StatItem(
                                label = "G",
                                value = "${formatG(row.gTotal)} m² (${String.format(Locale.getDefault(), "%.0f", row.gPct)}%)",
                                color = rowTextColor
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatItem(
                                label = "Dm",
                                value = "${formatDiameter(row.dm, placeholderDash)} cm",
                                color = rowTextColor
                            )
                            StatItem(
                                label = "Dg",
                                value = "${formatDiameter(row.dg, placeholderDash)} cm",
                                color = rowTextColor
                            )
                            if (row.meanPricePerM3 != null) {
                                StatItem(
                                    label = "€/m³",
                                    value = formatPrice(row.meanPricePerM3, placeholderDash),
                                    color = rowTextColor
                                )
                            }
                        }
                        if (row.revenueTotal != null) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                StatItem(
                                    label = stringResource(R.string.martelage_label_revenue),
                                    value = formatMoney(row.revenueTotal, placeholderDash, euroSymbol),
                                    color = rowTextColor
                                )
                                StatItem(
                                    label = stringResource(R.string.martelage_label_revenue_per_ha),
                                    value = formatMoney(row.revenuePerHa, placeholderDash, euroSymbol),
                                    color = rowTextColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = LocalContentColor.current
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/**
 * Carte Garde-fou : affiche les alertes de cohérence des calculs.
 */
@Composable
internal fun SanityWarningsCard(warnings: List<SanityWarning>) {
    if (warnings.isEmpty()) return

    val errors = warnings.filter { it.severity == SanitySeverity.ERROR }
    val warns = warnings.filter { it.severity == SanitySeverity.WARNING }
    val infos = warnings.filter { it.severity == SanitySeverity.INFO }

    val hasErrors = errors.isNotEmpty()
    val cardBg = if (hasErrors)
        MaterialTheme.colorScheme.errorContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    val cardContent = if (hasErrors)
        MaterialTheme.colorScheme.onErrorContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(
            containerColor = cardBg,
            contentColor = cardContent
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (hasErrors) Icons.Default.Error else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (hasErrors) MaterialTheme.colorScheme.error else Color(0xFFF57C00)
                )
                Text(
                    stringResource(R.string.sanity_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (errors.isNotEmpty()) {
                errors.take(5).forEach { w ->
                    SanityRow(w, MaterialTheme.colorScheme.error)
                }
            }
            if (warns.isNotEmpty()) {
                warns.take(5).forEach { w ->
                    SanityRow(w, Color(0xFFF57C00))
                }
            }
            if (infos.isNotEmpty()) {
                infos.take(3).forEach { w ->
                    SanityRow(w, MaterialTheme.colorScheme.primary)
                }
            }

            val remaining = warnings.size - minOf(warnings.size, 13)
            if (remaining > 0) {
                Text(
                    stringResource(R.string.sanity_more_warnings, remaining),
                    style = MaterialTheme.typography.labelSmall,
                    color = cardContent.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun SanityRow(warning: SanityWarning, iconColor: Color) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            when (warning.severity) {
                SanitySeverity.ERROR -> Icons.Default.Error
                SanitySeverity.WARNING -> Icons.Default.Warning
                SanitySeverity.INFO -> Icons.Default.Info
            },
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(16.dp)
        )
        Text(
            sanityMessage(warning),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * Carte résumé des arbres spéciaux (dépérissant, bio, mort, parasité)
 * avec détails par arbre : essence, diamètre, hauteur, type parasite.
 */
@Composable
internal fun SpecialTreesCard(
    specialTrees: List<SpecialTreeEntry>
) {
    if (specialTrees.isEmpty()) return
    val cardBg = MaterialTheme.colorScheme.surfaceVariant
    val cardContent = ColorUtils.getContrastingTextColor(cardBg)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(
            containerColor = cardBg,
            contentColor = cardContent
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                stringResource(R.string.martelage_special_trees_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            specialTrees.forEach { entry ->
                val (icon, label, color) = when (entry.categorie) {
                    "DEPERISSANT" -> Triple("\u26A0\uFE0F", stringResource(R.string.special_tree_dying), Color(0xFFFF9800))
                    "ARBRE_BIO" -> Triple("\uD83C\uDF3F", stringResource(R.string.special_tree_bio), Color(0xFF4CAF50))
                    "MORT" -> Triple("\uD83D\uDC80", stringResource(R.string.special_tree_dead), Color(0xFF424242))
                    "PARASITE" -> Triple("\uD83D\uDC1B", stringResource(R.string.special_tree_parasite), Color(0xFFF44336))
                    else -> Triple("\uD83C\uDF32", entry.categorie, cardContent)
                }
                // Category header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = color.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            icon,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        color = color.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "${entry.count}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = color,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                        )
                    }
                }
                // Per-tree detail rows
                entry.trees.forEach { tree ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val detail = buildString {
                            append(tree.essenceName)
                            append(" \u00b7 \u2300 ")
                            append(String.format(Locale.getDefault(), "%.0f", tree.diamCm))
                            append(" cm")
                            tree.hauteurM?.let {
                                append(" \u00b7 H ")
                                append(String.format(Locale.getDefault(), "%.1f", it))
                                append(" m")
                            }
                            if (!tree.defauts.isNullOrEmpty()) {
                                append(" \u00b7 ")
                                append(tree.defauts.joinToString(", "))
                            }
                            if (tree.hasGps) append(" \uD83D\uDCCD")
                        }
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = cardContent.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Carte indice de biodiversité (Shannon, Piélou, IBP simplifié) avec interprétations.
 */
@Composable
internal fun BiodiversityCard(
    bio: BiodiversityIndex
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(500, delayMillis = 300)) + slideInVertically(tween(500, delayMillis = 300, easing = FastOutSlowInEasing)) { it / 5 }
    ) {
    val cardBg = MaterialTheme.colorScheme.surfaceVariant
    val cardContent = ColorUtils.getContrastingTextColor(cardBg)
    val ibpPct = if (bio.ibpMax > 0) bio.ibpScore.toFloat() / bio.ibpMax.toFloat() else 0f
    val scoreColor = when {
        ibpPct >= 0.7f -> Color(0xFF2E7D32)
        ibpPct >= 0.5f -> Color(0xFF558B2F)
        ibpPct >= 0.3f -> Color(0xFFEF6C00)
        else -> Color(0xFFC62828)
    }
    val ibpLevelRes = when {
        ibpPct >= 0.7f -> R.string.biodiversity_level_very_good
        ibpPct >= 0.5f -> R.string.biodiversity_level_good
        ibpPct >= 0.3f -> R.string.biodiversity_level_medium
        ibpPct >= 0.15f -> R.string.biodiversity_level_low
        else -> R.string.biodiversity_level_very_low
    }
    // Shannon interpretation
    val shannonLevelRes = when {
        bio.shannonH >= 2.5 -> R.string.biodiversity_shannon_diverse
        bio.shannonH >= 1.5 -> R.string.biodiversity_shannon_mixed
        bio.shannonH >= 0.5 -> R.string.biodiversity_shannon_low
        else -> R.string.biodiversity_shannon_mono
    }
    val shannonColor = when {
        bio.shannonH >= 2.5 -> Color(0xFF2E7D32)
        bio.shannonH >= 1.5 -> Color(0xFF558B2F)
        bio.shannonH >= 0.5 -> Color(0xFFEF6C00)
        else -> Color(0xFFC62828)
    }
    // Pielou interpretation
    val pielou = bio.pielou
    val pieloulLevelRes = when {
        pielou == null -> null
        pielou >= 0.7 -> R.string.biodiversity_pielou_even
        pielou >= 0.4 -> R.string.biodiversity_pielou_moderate
        else -> R.string.biodiversity_pielou_uneven
    }
    val pieloulColor = when {
        pielou == null -> cardContent
        pielou >= 0.7 -> Color(0xFF2E7D32)
        pielou >= 0.4 -> Color(0xFFEF6C00)
        else -> Color(0xFFC62828)
    }

    val bioGradient = Brush.linearGradient(
        colors = listOf(
            cardBg,
            scoreColor.copy(alpha = 0.08f)
                .let { s -> Color(red = (cardBg.red * 0.94f + s.red * 0.06f).coerceIn(0f,1f), green = (cardBg.green * 0.94f + s.green * 0.06f).coerceIn(0f,1f), blue = (cardBg.blue * 0.94f + s.blue * 0.06f).coerceIn(0f,1f), alpha = 1f) }
        ),
        start = Offset.Zero,
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )
    val animatedIbpPct by animateFloatAsState(
        targetValue = ibpPct,
        animationSpec = tween(1400, easing = FastOutSlowInEasing),
        label = "ibp_progress"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "ibp_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ibp_badge_alpha"
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(5.dp, RoundedCornerShape(18.dp), spotColor = scoreColor.copy(alpha = 0.25f))
            .clip(RoundedCornerShape(18.dp))
            .drawBehind { drawRect(bioGradient) },
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = cardContent
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header row: title + IBP score badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.biodiversity_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = scoreColor.copy(alpha = pulseAlpha),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        stringResource(R.string.biodiversity_ibp_score, bio.ibpScore, bio.ibpMax),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    color = scoreColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        stringResource(ibpLevelRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = scoreColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // IBP progress bar — animated fill
            LinearProgressIndicator(
                progress = { animatedIbpPct },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = scoreColor,
                trackColor = cardContent.copy(alpha = 0.08f)
            )

            HorizontalDivider(color = cardContent.copy(alpha = 0.12f))

            // Shannon row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            stringResource(R.string.biodiversity_shannon),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = cardContent.copy(alpha = 0.7f)
                        )
                        Surface(
                            color = shannonColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                stringResource(shannonLevelRes),
                                style = MaterialTheme.typography.labelSmall,
                                color = shannonColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        String.format(Locale.getDefault(), "%.2f", bio.shannonH),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.biodiversity_shannon_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = cardContent.copy(alpha = 0.55f)
                    )
                }
                // Pielou column
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            stringResource(R.string.biodiversity_pielou),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = cardContent.copy(alpha = 0.7f)
                        )
                        if (pieloulLevelRes != null) {
                            Surface(
                                color = pieloulColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    stringResource(pieloulLevelRes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = pieloulColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        pielou?.let { String.format(Locale.getDefault(), "%.2f", it) } ?: "–",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.biodiversity_pielou_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = cardContent.copy(alpha = 0.55f)
                    )
                }
            }

            // Species count + IBP detail badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = cardContent.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        stringResource(R.string.biodiversity_species) + ": ${bio.speciesCount}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                if (bio.tgbCount > 0) {
                    Surface(color = Color(0xFF795548).copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            stringResource(R.string.biodiversity_tgb, bio.tgbCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF795548),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
                if (bio.bioTreeCount > 0) {
                    Surface(color = Color(0xFF4CAF50).copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            stringResource(R.string.biodiversity_bio_trees, bio.bioTreeCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
                if (bio.deadTreeCount > 0) {
                    Surface(color = Color(0xFF424242).copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            stringResource(R.string.biodiversity_dead_trees, bio.deadTreeCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = cardContent.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
                if (bio.dyingTreeCount > 0) {
                    Surface(color = Color(0xFFFF9800).copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            stringResource(R.string.biodiversity_dying_trees, bio.dyingTreeCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFEF6C00),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Context note
            Text(
                stringResource(R.string.biodiversity_ibp_context),
                style = MaterialTheme.typography.bodySmall,
                color = cardContent.copy(alpha = 0.45f)
            )
        }
    }
    } // AnimatedVisibility
}

@Composable
private fun sanityMessage(w: SanityWarning): String {
    return when (w.code) {
        "diam_zero" -> stringResource(R.string.sanity_diam_zero)
        "diam_too_small" -> stringResource(R.string.sanity_diam_too_small, w.value ?: 0.0)
        "diam_too_large" -> stringResource(R.string.sanity_diam_too_large, w.value ?: 0.0)
        "diam_very_large" -> stringResource(R.string.sanity_diam_very_large, w.value ?: 0.0)
        "height_zero" -> stringResource(R.string.sanity_height_zero)
        "height_too_small" -> stringResource(R.string.sanity_height_too_small, w.value ?: 0.0)
        "height_too_large" -> stringResource(R.string.sanity_height_too_large, w.value ?: 0.0)
        "height_very_large" -> stringResource(R.string.sanity_height_very_large, w.value ?: 0.0)
        "hd_ratio_extreme" -> stringResource(R.string.sanity_hd_ratio_extreme)
        "hd_ratio_high" -> stringResource(R.string.sanity_hd_ratio_high)
        "coef_forme_out_of_range" -> stringResource(R.string.sanity_coef_forme_range, w.value ?: 0.0)
        "volume_negative" -> stringResource(R.string.sanity_volume_negative)
        "volume_tree_extreme" -> stringResource(R.string.sanity_volume_tree_extreme, w.value ?: 0.0)
        "volume_tree_very_large" -> stringResource(R.string.sanity_volume_tree_large, w.value ?: 0.0)
        "volume_vs_diam_incoherent" -> stringResource(R.string.sanity_volume_vs_diam)
        "many_input_errors" -> stringResource(R.string.sanity_many_input_errors, (w.value ?: 0.0).toInt())
        "potential_duplicates" -> stringResource(R.string.sanity_potential_duplicates, (w.value ?: 0.0).toInt())
        "surface_zero" -> stringResource(R.string.sanity_surface_zero)
        "surface_very_small" -> stringResource(R.string.sanity_surface_very_small)
        "n_ha_extreme" -> stringResource(R.string.sanity_n_ha_extreme, (w.value ?: 0.0).toInt())
        "n_ha_very_high" -> stringResource(R.string.sanity_n_ha_very_high, (w.value ?: 0.0).toInt())
        "g_ha_extreme" -> stringResource(R.string.sanity_g_ha_extreme, w.value ?: 0.0)
        "g_ha_very_high" -> stringResource(R.string.sanity_g_ha_very_high, w.value ?: 0.0)
        "g_ha_very_low" -> stringResource(R.string.sanity_g_ha_very_low, w.value ?: 0.0)
        "v_ha_extreme" -> stringResource(R.string.sanity_v_ha_extreme, w.value ?: 0.0)
        "v_ha_very_high" -> stringResource(R.string.sanity_v_ha_very_high, w.value ?: 0.0)
        "vg_ratio_low" -> stringResource(R.string.sanity_vg_ratio_low, w.value ?: 0.0)
        "vg_ratio_high" -> stringResource(R.string.sanity_vg_ratio_high, w.value ?: 0.0)
        "revenue_negative" -> stringResource(R.string.sanity_revenue_negative)
        "revenue_tree_extreme" -> stringResource(R.string.sanity_revenue_tree_extreme)
        "revenue_ha_negative" -> stringResource(R.string.sanity_revenue_ha_negative)
        "revenue_ha_very_high" -> stringResource(R.string.sanity_revenue_ha_very_high, w.value ?: 0.0)
        else -> w.code
    }
}
