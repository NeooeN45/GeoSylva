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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.forestry.counter.domain.usecase.fertility.ConfidenceLevel
import com.forestry.counter.domain.usecase.fertility.FertilityClass
import com.forestry.counter.domain.usecase.fertility.FertilityResult
import com.forestry.counter.domain.usecase.fertility.ZoneCompatibility
import com.forestry.counter.R
import com.forestry.counter.domain.calculation.SanitySeverity
import com.forestry.counter.domain.calculation.SanityWarning
import com.forestry.counter.domain.calculation.quality.WoodQualityGrade
import com.forestry.counter.domain.model.Essence
import com.forestry.counter.presentation.utils.ColorUtils
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs

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
                StatItem(
                    label = stringResource(R.string.martelage_label_v_total), value = "$vTotalText m³",
                    info = "V total = volume bois fort tige sur pied.\nCalculé par tarif de cubage par essence (Algan/CNPF).\nSi hauteur manquante : estimée par relation H-D paramétrable."
                )
                StatItem(
                    label = stringResource(R.string.martelage_label_v_per_ha), value = "$vPerHaText m³/ha",
                    info = "V/ha = volume total ramené à l'hectare.\nRatioV/G (=V/G) compris entre 8 et 15 m indique une forêt équilibrée.\nSource : tarifs CNPF/ONF."
                )
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
                StatItem(
                    label = stringResource(R.string.martelage_label_g_total), value = "${formatG(gTotal)} m²",
                    info = "G total = surface terrière totale de toutes les tiges.\nG = Σ(π × D²/4) pour chaque tige.\nExprimée en m²."
                )
                StatItem(
                    label = stringResource(R.string.martelage_label_g_per_ha), value = "${formatG(gPerHa)} m²/ha",
                    info = "G/ha = surface terrière ramenée à l'hectare.\nValeurs de référence : 10–15 m²/ha (forêt claire), 25–35 m²/ha (peuplement dense).\nObjectif sylvicole courant : 20–28 m²/ha."
                )
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
                StatItem(
                    label = stringResource(R.string.martelage_label_n_total), value = "$nTotal",
                    info = "N total : effectif mesuré dans la placette ou la parcelle."
                )
                StatItem(
                    label = stringResource(R.string.martelage_label_n_per_ha), value = "${formatIntPerHa(nPerHa)}/ha",
                    info = "N/ha = nombre de tiges / surface inventoriée (ha).\nDépend directement de la surface saisie."
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = densityCardContent.copy(alpha = 0.15f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem(
                    label = stringResource(R.string.martelage_label_dm), value = "${formatDiameter(dm, placeholderDash)} cm",
                    info = "Dm = diamètre moyen arithmétique.\nDm = Σ(Di) / N\nMesure à 1,30 m du sol (DHP)."
                )
                StatItem(
                    label = stringResource(R.string.martelage_label_dg), value = "${formatDiameter(dg, placeholderDash)} cm",
                    info = "Dg = diamètre de l'arbre de surface terrière moyenne.\nDg = √(4 × G/ha / (π × N/ha)) × 100\nReprésentatif de la structure du peuplement."
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem(
                    label = stringResource(R.string.martelage_label_hm), value = "${formatHeight(meanH, placeholderDash)} m",
                    info = "Hmoy = moyenne arithmétique des hauteurs mesurées.\nBasé sur les mesures terrain (dendromètre/perche/laser)."
                )
                StatItem(
                    label = stringResource(R.string.martelage_label_hlorey), value = "${formatHeight(hLorey, placeholderDash)} m",
                    info = "H Lorey = hauteur de l'arbre de surface terrière moyenne.\nH Lorey = Σ(Gi × Hi) / Σ(Gi)\nPondération par la surface terrière — plus représentative que Hmoy."
                )
            }
            if (dMin != null || dMax != null || cvDiam != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = densityCardContent.copy(alpha = 0.15f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (dMin != null && dMax != null) {
                        StatItem(
                            label = stringResource(R.string.martelage_label_d_range),
                            value = "${formatDiameter(dMin, placeholderDash)} – ${formatDiameter(dMax, placeholderDash)} cm",
                            info = "Plage de diamètres : minimum et maximum des diamètres mesurés.\nUtile pour évaluer l'amplitude de la structure."
                        )
                    }
                    if (cvDiam != null) {
                        StatItem(
                            label = stringResource(R.string.martelage_label_cv_diam),
                            value = String.format(Locale.getDefault(), "%.0f %%", cvDiam),
                            info = "CV(D) = coefficient de variation des diamètres.\nCV = écart-type / Dm × 100\n< 20 % → peuplement régulier\n20–40 % → structure bi-étagée\n> 40 % → structure jardinée"
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
    color: androidx.compose.ui.graphics.Color = LocalContentColor.current,
    info: String? = null
) {
    var showInfo by remember { mutableStateOf(false) }
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.6f))
            if (info != null) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Info",
                    tint = color.copy(alpha = 0.38f),
                    modifier = Modifier
                        .size(11.dp)
                        .clickable { showInfo = true }
                )
            }
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
    if (showInfo && info != null) {
        CalcInfoDialog(text = info, onDismiss = { showInfo = false })
    }
}

@Composable
private fun CalcInfoDialog(text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) },
        title = { Text("Méthode de calcul", style = MaterialTheme.typography.titleSmall) },
        text = { Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
    )
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

/**
 * Carte des classes de fertilité par essence (guides sylviculture ONF/CNPF).
 */
@Composable
internal fun StandFertilityCard(
    fertilityResults: List<FertilityResult>
) {
    if (fertilityResults.isEmpty()) return
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(500, delayMillis = 350)) +
            slideInVertically(tween(500, delayMillis = 350, easing = FastOutSlowInEasing)) { it / 5 }
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Forest,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Classes de fertilité du peuplement",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "Estimation automatique — guides ONF/CNPF — hauteur dominante et dendrométrie",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                fertilityResults.forEachIndexed { idx, result ->
                    val classColor = Color(result.fertilityClass.color)
                    val confColor = when (result.confidence) {
                        ConfidenceLevel.HIGH         -> Color(0xFF2E7D32)
                        ConfidenceLevel.MEDIUM       -> Color(0xFFF9A825)
                        ConfidenceLevel.LOW          -> Color(0xFFEF6C00)
                        ConfidenceLevel.INSUFFICIENT -> Color(0xFF757575)
                    }
                    val zoneColor = when (result.zoneCompatibility) {
                        ZoneCompatibility.OPTIMAL    -> Color(0xFF2E7D32)
                        ZoneCompatibility.ACCEPTABLE -> Color(0xFFF9A825)
                        ZoneCompatibility.SUBOPTIMAL -> Color(0xFFC62828)
                    }
                    val targetFrac = when (result.fertilityClass) {
                        FertilityClass.I       -> 1.0f
                        FertilityClass.II      -> 0.75f
                        FertilityClass.III     -> 0.50f
                        FertilityClass.IV      -> 0.25f
                        FertilityClass.UNKNOWN -> 0.05f
                    }
                    val animFraction by animateFloatAsState(
                        targetValue = targetFrac,
                        animationSpec = tween(700, delayMillis = idx * 80, easing = FastOutSlowInEasing),
                        label = "fert_$idx"
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = classColor,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        "Cl. ${result.fertilityClass.roman}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        result.essenceName,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        result.fertilityClass.label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = classColor,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                result.dominantHeightM?.let {
                                    Text(
                                        "H₀ ≈ ${String.format(Locale.getDefault(), "%.1f", it)} m",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Surface(
                                        color = confColor.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            result.confidence.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = confColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Surface(
                                        color = zoneColor.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            result.zoneCompatibility.icon + " Zone",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = zoneColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Box(
                            Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                        ) {
                            Box(
                                Modifier.fillMaxHeight().fillMaxWidth(animFraction)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(classColor, classColor.copy(alpha = 0.6f))
                                        )
                                    )
                            )
                        }
                        if (result.notes.isNotEmpty()) {
                            Text(
                                result.notes.first(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (idx < fertilityResults.size - 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
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

// ─────────────────────────────────────────────────────────────────────────────
// Rapport de corroboration
// ─────────────────────────────────────────────────────────────────────────────

private enum class CorrStatus { OK, WARN, ERROR }

private data class CorrCheck(
    val label: String,
    val valueText: String,
    val status: CorrStatus,
    val note: String
)

private fun buildCorroborationChecks(stats: MartelageStats): List<CorrCheck> {
    val list = mutableListOf<CorrCheck>()

    // 1. Ratio V/G
    stats.ratioVG?.let { vg ->
        val (st, note) = when {
            vg < 4.0  -> CorrStatus.ERROR to "Très bas — hauteurs manquantes ou tarif inadapté"
            vg < 6.0  -> CorrStatus.WARN  to "Bas — peuplement jeune/dense ou hauteurs sous-estimées"
            vg > 22.0 -> CorrStatus.ERROR to "Très élevé — vérifier le tarif de cubage"
            vg > 16.0 -> CorrStatus.WARN  to "Élevé — arbres de grande hauteur ou tarif surestimant"
            else      -> CorrStatus.OK    to "Cohérent (norme indicative 8–15 m³/m²)"
        }
        list += CorrCheck("Ratio V/G", String.format(Locale.getDefault(), "%.1f m³/m²", vg), st, note)
    }

    // 2. Cohérence Dm/Dg
    val dm = stats.dm; val dg = stats.dg
    if (dm != null && dg != null && dg > 0.0) {
        val r = dm / dg
        val (st, note) = when {
            r < 0.82 -> CorrStatus.WARN to "Étalée vers les gros diamètres — peuplement déformé ou sélectionné"
            r > 1.08 -> CorrStatus.WARN to "Étalée vers les petits diamètres — régénération dominante ?"
            else     -> CorrStatus.OK   to "Distribution diamétrique symétrique"
        }
        list += CorrCheck("Dm / Dg", String.format(Locale.getDefault(), "%.2f", r), st, note)
    }

    // 3. G/ha recalculé depuis N/ha × Dg²
    if (dg != null && stats.nPerHa > 0.0 && stats.gPerHa > 0.0) {
        val gCalc = stats.nPerHa * PI / 4.0 * (dg / 100.0) * (dg / 100.0)
        val err = abs(gCalc - stats.gPerHa) / stats.gPerHa
        val (st, note) = when {
            err > 0.20 -> CorrStatus.ERROR to "Écart ${String.format(Locale.getDefault(), "%.0f", err * 100)}%% — diamètres ou surface manquants ?"
            err > 0.10 -> CorrStatus.WARN  to "Écart ${String.format(Locale.getDefault(), "%.0f", err * 100)}%% — vérifier les tiges sans diamètre"
            else       -> CorrStatus.OK    to "N/ha × Dg² × π/4 ≈ G/ha mesuré (cohérent)"
        }
        list += CorrCheck("G/ha recalculé", String.format(Locale.getDefault(), "%.2f m²/ha", gCalc), st, note)
    }

    // 4. Structure CV(D) — informatif
    stats.cvDiam?.let { cv ->
        val note = when {
            cv < 15.0 -> "Futaie très régulière / équienne monospécifique"
            cv < 30.0 -> "Futaie régulière normale"
            cv < 50.0 -> "Mélange de classes — futaie irrégulière ou bi-étagée"
            else      -> "Structure complexe — futaie jardinée ou peuplement mixte"
        }
        list += CorrCheck("Structure CV(D)", String.format(Locale.getDefault(), "%.0f%%", cv), CorrStatus.OK, note)
    }

    // 5. Intensité de coupe G%
    stats.harvestGhaPct?.let { hg ->
        val (st, note) = when {
            hg > 40.0 -> CorrStatus.ERROR to "Très intense — risque de déstabilisation du résiduel"
            hg > 30.0 -> CorrStatus.WARN  to "Fort — surveiller la résilience du peuplement résiduel"
            hg > 20.0 -> CorrStatus.OK    to "Modéré à fort — conforme aux rotations sylvicoles"
            else      -> CorrStatus.OK    to "Léger à modéré — conforme aux recommandations ONF/CNPF"
        }
        list += CorrCheck("Intensité coupe ΔG", String.format(Locale.getDefault(), "%.0f%%", hg), st, note)
    }

    return list
}

@Composable
internal fun CorroborationReportCard(stats: MartelageStats) {
    val checks = remember(
        stats.ratioVG, stats.dm, stats.dg,
        stats.nPerHa, stats.gPerHa, stats.cvDiam, stats.harvestGhaPct
    ) { buildCorroborationChecks(stats) }
    if (checks.isEmpty()) return

    val worstStatus = checks.maxOf { it.status }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(500, 80, FastOutSlowInEasing)) +
                slideInVertically(tween(500, 80, FastOutSlowInEasing)) { it / 5 }
    ) {
        val headerColor = when (worstStatus) {
            CorrStatus.ERROR -> MaterialTheme.colorScheme.error
            CorrStatus.WARN  -> Color(0xFFF57C00)
            CorrStatus.OK    -> Color(0xFF2E7D32)
        }
        val cardBg = MaterialTheme.colorScheme.surfaceVariant
        val cardContent = ColorUtils.getContrastingTextColor(cardBg)
        val okCount = checks.count { it.status == CorrStatus.OK }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp)),
            colors = CardDefaults.cardColors(containerColor = cardBg, contentColor = cardContent)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Info, null,
                        tint = headerColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        "Rapport de corroboration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        color = headerColor.copy(alpha = 0.13f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "$okCount / ${checks.size} OK",
                            style = MaterialTheme.typography.labelSmall,
                            color = headerColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Text(
                    "Vérification croisée des indicateurs dendrométriques",
                    style = MaterialTheme.typography.bodySmall,
                    color = cardContent.copy(alpha = 0.55f)
                )
                HorizontalDivider(color = cardContent.copy(alpha = 0.12f))
                checks.forEach { check ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val (icon, tint) = when (check.status) {
                            CorrStatus.OK    -> Icons.Default.Info    to Color(0xFF2E7D32)
                            CorrStatus.WARN  -> Icons.Default.Warning to Color(0xFFF57C00)
                            CorrStatus.ERROR -> Icons.Default.Error   to MaterialTheme.colorScheme.error
                        }
                        Icon(
                            icon, null, tint = tint,
                            modifier = Modifier.size(16.dp).padding(top = 2.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    check.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    check.valueText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = cardContent.copy(alpha = 0.75f)
                                )
                            }
                            Text(
                                check.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = cardContent.copy(alpha = 0.55f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Indicateurs sylvicoles
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun SylviculturalKPIsCard(stats: MartelageStats) {
    if (stats.dg == null && stats.meanH == null) return

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(500, 160, FastOutSlowInEasing)) +
                slideInVertically(tween(500, 160, FastOutSlowInEasing)) { it / 5 }
    ) {
        val cardBg = MaterialTheme.colorScheme.surfaceVariant
        val cardContent = ColorUtils.getContrastingTextColor(cardBg)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp)),
            colors = CardDefaults.cardColors(containerColor = cardBg, contentColor = cardContent)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Forest, null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        stringResource(R.string.sylv_kpi_card_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                HorizontalDivider(color = cardContent.copy(alpha = 0.12f))

                val dg = stats.dg
                val meanH = stats.meanH
                val dominantCode = stats.perEssence.maxByOrNull { it.gPct }?.essenceCode ?: ""
                val isResineuxDominant = dominantCode.uppercase().let { up ->
                    up.contains("PIN") || up.contains("SAPIN") || up.contains("EPICEA") ||
                    up.contains("DOUGLAS") || up.contains("MELEZE") || up.contains("CEDRE") ||
                    up.contains("SEQUOIA") || up.contains("THUYA")
                }

                // Indice d'élancement H/D — résineux uniquement
                if (dg != null && meanH != null && dg > 0.0 && isResineuxDominant) {
                    val slend = meanH / (dg / 100.0)
                    val slendVeryStable = stringResource(R.string.sylv_slend_very_stable)
                    val slendStable = stringResource(R.string.sylv_slend_stable)
                    val slendNormal = stringResource(R.string.sylv_slend_normal)
                    val slendRisky = stringResource(R.string.sylv_slend_risky)
                    val slendInfo = stringResource(R.string.sylv_slend_info)
                    val (sc, sl) = when {
                        slend < 70  -> Color(0xFF2E7D32) to slendVeryStable
                        slend < 85  -> Color(0xFF558B2F) to slendStable
                        slend < 100 -> Color(0xFFF9A825) to slendNormal
                        else        -> Color(0xFFC62828) to slendRisky
                    }
                    SylvKPIRow(
                        label = stringResource(R.string.sylv_slend_label),
                        value = String.format(Locale.getDefault(), "%.0f", slend),
                        statusLabel = sl, statusColor = sc, textColor = cardContent,
                        info = slendInfo
                    )
                }

                // G/ha — densité du peuplement
                val gPerHa = stats.gPerHa
                if (gPerHa > 0.0) {
                    val gLow = stringResource(R.string.sylv_g_low_density)
                    val gModerate = stringResource(R.string.sylv_g_moderate)
                    val gNormal = stringResource(R.string.sylv_g_normal)
                    val gDense = stringResource(R.string.sylv_g_dense)
                    val gOver = stringResource(R.string.sylv_g_overstocked)
                    val gInfo = stringResource(R.string.sylv_g_info)
                    val (gc, gl) = when {
                        gPerHa < 10.0 -> Color(0xFFEF6C00) to gLow
                        gPerHa < 20.0 -> Color(0xFF558B2F) to gModerate
                        gPerHa < 35.0 -> Color(0xFF2E7D32) to gNormal
                        gPerHa < 50.0 -> Color(0xFFF9A825) to gDense
                        else          -> Color(0xFFC62828) to gOver
                    }
                    SylvKPIRow(
                        label = stringResource(R.string.sylv_g_label),
                        value = "${formatG(gPerHa)} m²/ha",
                        statusLabel = gl, statusColor = gc, textColor = cardContent,
                        info = gInfo
                    )
                }

                // H Lorey / H moy — hétérogénéité de hauteur
                val hLorey = stats.hLorey
                if (hLorey != null && meanH != null && meanH > 0.0) {
                    val hr = hLorey / meanH
                    val hHomo = stringResource(R.string.sylv_h_homogeneous)
                    val hHetero = stringResource(R.string.sylv_h_slightly_hetero)
                    val hIrreg = stringResource(R.string.sylv_h_irregular)
                    val hInfo = stringResource(R.string.sylv_h_info)
                    val (hc, hl) = when {
                        hr < 1.05 -> Color(0xFF2E7D32) to hHomo
                        hr < 1.15 -> Color(0xFF558B2F) to hHetero
                        else      -> Color(0xFF1565C0) to hIrreg
                    }
                    SylvKPIRow(
                        label = stringResource(R.string.sylv_h_label),
                        value = String.format(Locale.getDefault(), "%.2f", hr),
                        statusLabel = hl, statusColor = hc, textColor = cardContent,
                        info = hInfo
                    )
                }

                // N/ha — densité en tiges
                val nPerHa = stats.nPerHa
                if (nPerHa > 0.0) {
                    val nSparse = stringResource(R.string.sylv_n_sparse)
                    val nNormal = stringResource(R.string.sylv_g_normal)
                    val nDense = stringResource(R.string.sylv_n_dense_stand)
                    val nHigh = stringResource(R.string.sylv_n_high)
                    val nVeryDense = stringResource(R.string.sylv_n_very_dense)
                    val nInfo = stringResource(R.string.sylv_n_info)
                    val (nc, nl) = when {
                        nPerHa < 100  -> Color(0xFFEF6C00) to nSparse
                        nPerHa < 300  -> Color(0xFF558B2F) to nNormal
                        nPerHa < 700  -> Color(0xFF2E7D32) to nDense
                        nPerHa < 1500 -> Color(0xFFF9A825) to nHigh
                        else          -> Color(0xFFC62828) to nVeryDense
                    }
                    SylvKPIRow(
                        label = stringResource(R.string.sylv_n_label),
                        value = String.format(Locale.getDefault(), "%.0f tiges/ha", nPerHa),
                        statusLabel = nl, statusColor = nc, textColor = cardContent,
                        info = nInfo
                    )
                }

                // Ratio V/G — cohérence volume / surface terrière
                val ratioVG = stats.ratioVG
                if (ratioVG != null && ratioVG > 0.0) {
                    val vgVeryLow = stringResource(R.string.sylv_vg_very_low)
                    val vgLow = stringResource(R.string.sylv_vg_low)
                    val vgCoherent = stringResource(R.string.sylv_vg_coherent)
                    val vgHigh = stringResource(R.string.sylv_vg_high)
                    val vgVeryHigh = stringResource(R.string.sylv_vg_very_high)
                    val vgInfo = stringResource(R.string.sylv_vg_info)
                    val (vc, vl) = when {
                        ratioVG < 4.0  -> Color(0xFFC62828) to vgVeryLow
                        ratioVG < 7.0  -> Color(0xFFF9A825) to vgLow
                        ratioVG < 16.0 -> Color(0xFF2E7D32) to vgCoherent
                        ratioVG < 22.0 -> Color(0xFFF9A825) to vgHigh
                        else           -> Color(0xFFC62828) to vgVeryHigh
                    }
                    SylvKPIRow(
                        label = stringResource(R.string.sylv_vg_label),
                        value = String.format(Locale.getDefault(), "%.1f m³/m²", ratioVG),
                        statusLabel = vl, statusColor = vc, textColor = cardContent,
                        info = vgInfo
                    )
                }

                // Ratio Dg/Dm — symétrie de la distribution diamétrique
                val dm = stats.dm; val dgV = stats.dg
                if (dm != null && dgV != null && dm > 0.0 && dgV > 0.0) {
                    val r = dm / dgV
                    val dmdgLarge = stringResource(R.string.sylv_dmdg_skew_large)
                    val dmdgSmall = stringResource(R.string.sylv_dmdg_skew_small)
                    val dmdgSym = stringResource(R.string.sylv_dmdg_symmetric)
                    val dmdgInfo = stringResource(R.string.sylv_dmdg_info)
                    val (rc, rl) = when {
                        r < 0.82 -> Color(0xFFF9A825) to dmdgLarge
                        r > 1.08 -> Color(0xFFF9A825) to dmdgSmall
                        else     -> Color(0xFF2E7D32) to dmdgSym
                    }
                    SylvKPIRow(
                        label = stringResource(R.string.sylv_dmdg_label),
                        value = String.format(Locale.getDefault(), "%.2f", r),
                        statusLabel = rl, statusColor = rc, textColor = cardContent,
                        info = dmdgInfo
                    )
                }
            }
        }
    }
}

@Composable
private fun SylvKPIRow(
    label: String,
    value: String,
    statusLabel: String,
    statusColor: Color,
    textColor: Color,
    info: String? = null
) {
    var showInfo by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor.copy(alpha = 0.65f)
                )
                if (info != null) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Info",
                        tint = textColor.copy(alpha = 0.32f),
                        modifier = Modifier.size(11.dp).clickable { showInfo = true }
                    )
                }
            }
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Surface(
            color = statusColor.copy(alpha = 0.13f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
    if (showInfo && info != null) CalcInfoDialog(text = info, onDismiss = { showInfo = false })
}

// ═══════════════════════════════════════════════════════════════════════════
// CARTE AIDE CONTEXTUELLE — QUALITÉ DES DONNÉES ET PROCHAINES ÉTAPES
// ═══════════════════════════════════════════════════════════════════════════

@Composable
internal fun DataCompletenessCard(stats: MartelageStats) {
    // ── Score de complétude (0–100) ──────────────────────────────────────
    val completenessScore = run {
        var score = 0
        if (stats.nTotal > 0)                     score += 20
        if (stats.volumeCompletenessPct > 0.5)     score += 20
        if (stats.volumeCompletenessPct > 0.9)     score += 10
        if (stats.qualityAssessedCount > 0)        score += 15
        if (stats.qualityAssessedCount >= stats.qualityTotalCount && stats.qualityTotalCount > 0) score += 10
        if (stats.surfaceHa > 0)                  score += 10
        if (stats.biodiversity != null)            score += 10
        if (stats.specialTrees.isNotEmpty())       score += 5
        score.coerceIn(0, 100)
    }

    val scoreColor = when {
        completenessScore >= 80 -> Color(0xFF2E7D32)
        completenessScore >= 50 -> Color(0xFFF57C00)
        else                    -> Color(0xFFC62828)
    }
    val scoreCompleteLabel = stringResource(R.string.data_quality_complete_label)
    val scorePartialLabel = stringResource(R.string.data_quality_partial_label)
    val scoreInsufficientLabel = stringResource(R.string.data_quality_insufficient_label)
    val scoreLabel = when {
        completenessScore >= 80 -> scoreCompleteLabel
        completenessScore >= 50 -> scorePartialLabel
        else                    -> scoreInsufficientLabel
    }

    // ── Liste des conseils contextuels ────────────────────────────────────
    data class Tip(val icon: String, val title: String, val body: String, val urgent: Boolean = false)
    val tips = buildList {
        if (stats.nTotal < 10) add(Tip("📏", stringResource(R.string.tip_small_sample_title),
            stringResource(R.string.tip_small_sample_body, stats.nTotal), urgent = true))
        if (stats.surfaceHa <= 0.0) add(Tip("📐", stringResource(R.string.tip_no_surface_title),
            stringResource(R.string.tip_no_surface_body), urgent = true))
        if (stats.volumeCompletenessPct < 0.5) add(Tip("📡", stringResource(R.string.mandatory_heights_title),
            stringResource(R.string.tip_missing_heights_body, (100 - stats.volumeCompletenessPct * 100).toInt()), urgent = true))
        else if (stats.volumeCompletenessPct < 0.9) add(Tip("📡", stringResource(R.string.tip_some_heights_missing_title),
            stringResource(R.string.tip_some_heights_missing_body, stats.missingHeightEssenceNames.take(3).joinToString(", "))))
        if (stats.qualityAssessedCount == 0 && stats.nTotal > 0) add(Tip("🔍", stringResource(R.string.tip_quality_not_assessed_title),
            stringResource(R.string.tip_quality_not_assessed_body)))
        else if (stats.qualityAssessedCount < stats.qualityTotalCount / 2) add(Tip("🔍",
            stringResource(R.string.tip_quality_partial_title_format, (stats.qualityAssessedCount * 100.0 / stats.qualityTotalCount.coerceAtLeast(1)).toInt()),
            stringResource(R.string.tip_quality_partial_body)))
        if (stats.sanityWarnings.any { it.severity == com.forestry.counter.domain.calculation.SanitySeverity.ERROR })
            add(Tip("⚠️", stringResource(R.string.tip_sanity_errors_title),
                stringResource(R.string.tip_sanity_errors_body), urgent = true))
        if (stats.ratioVG != null && (stats.ratioVG < 4.0 || stats.ratioVG > 22.0))
            add(Tip("🔢", stringResource(R.string.tip_vg_ratio_abnormal_title, "%.1f".format(stats.ratioVG)),
                stringResource(R.string.tip_vg_ratio_abnormal_body)))
        if (stats.nTotal >= 10 && stats.biodiversity == null) add(Tip("🌿", stringResource(R.string.tip_biodiversity_missing_title),
            stringResource(R.string.tip_biodiversity_missing_body)))
    }

    // ── Recommandation tarif ───────────────────────────────────────────────
    val tarif2 = stringResource(R.string.tarif_reco_2entry)
    val tarifMixed = stringResource(R.string.tarif_reco_mixed)
    val tarif1 = stringResource(R.string.tarif_reco_1entry)
    val tarifReco = when {
        stats.volumeCompletenessPct > 0.85 -> tarif2
        stats.volumeCompletenessPct > 0.0  -> tarifMixed
        else                               -> tarif1
    }

    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── En-tête ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.data_quality_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        scoreLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = scoreColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                // Score circulaire
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp)) {
                    CircularProgressIndicator(
                        progress = { completenessScore / 100f },
                        modifier = Modifier.fillMaxSize(),
                        color = scoreColor,
                        trackColor = scoreColor.copy(alpha = 0.15f),
                        strokeWidth = 5.dp
                    )
                    Text(
                        "$completenessScore%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Barre de progression ──
            LinearProgressIndicator(
                progress = { completenessScore / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = scoreColor,
                trackColor = scoreColor.copy(alpha = 0.15f)
            )

            Spacer(Modifier.height(12.dp))

            // ── Recommandation tarif ──
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("📊", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        tarifReco,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // ── Conseils contextuels ──
            if (tips.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                val visible = if (expanded) tips else tips.take(2)
                visible.forEach { tip ->
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        color = if (tip.urgent)
                            Color(0xFFC62828).copy(alpha = 0.08f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(tip.icon, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    tip.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (tip.urgent) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    tip.body,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                if (tips.size > 2) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            if (expanded) stringResource(R.string.data_quality_tips_hide) else stringResource(R.string.data_quality_tips_more_format, tips.size - 2),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✅", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.data_quality_all_complete),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2E7D32)
                    )
                }
            }
        }
    }
}
