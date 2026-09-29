package com.forestry.counter.presentation.screens.forestry

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forestry.counter.R
import com.forestry.counter.domain.calculation.MartelageStats
import com.forestry.counter.domain.calculation.SanitySeverity
import com.forestry.counter.presentation.theme.Elevation
import com.forestry.counter.presentation.theme.GsShape
import com.forestry.counter.presentation.theme.SemanticError
import com.forestry.counter.presentation.theme.SemanticSuccess
import com.forestry.counter.presentation.theme.SemanticWarning
import com.forestry.counter.presentation.theme.Space
import java.util.Locale

/**
 * Onglet "Dendrométrie" de l'écran Martelage — refonte visuelle 3.0 (tokens
 * Space/GsShape/Elevation, palette MaterialTheme.colorScheme) du contenu
 * extrait de MartelageScreen.kt (ex Tab 0 "Synthèse", renommé Tab 1 "Dendrométrie" : VolumeCard/BasalAreaCard/
 * DensityCard/DataCompletenessCard dans MartelageSummaryCards.kt).
 *
 * Aucune donnée ni logique de calcul n'est modifiée — seule la présentation
 * change. Les mêmes paramètres déjà calculés au site d'appel sont repris
 * tels quels (voir MartelageScreen.kt, tab Synthèse).
 */
@Composable
fun MartelageDendrometryTab(
    stats: MartelageStats,
    vTotalText: String,
    vPerHaText: String,
    revenueTotalText: String,
    revenuePerHaText: String,
    placeholderDash: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        SynthesisVolumeCard(
            vTotalText = vTotalText,
            vPerHaText = vPerHaText,
            revenueTotalText = revenueTotalText,
            revenuePerHaText = revenuePerHaText,
            volumeAvailable = stats.volumeAvailable,
            volumeCompletenessPct = stats.volumeCompletenessPct
        )
        SynthesisBasalAreaCard(
            gTotal = stats.gTotal,
            gPerHa = stats.gPerHa,
            surfaceHa = stats.surfaceHa,
            ratioVG = stats.ratioVG
        )
        SynthesisDensityCard(
            nTotal = stats.nTotal,
            nPerHa = stats.nPerHa,
            dm = stats.dm,
            meanH = stats.meanH,
            dg = stats.dg,
            hLorey = stats.hLorey,
            dMin = stats.dMin,
            dMax = stats.dMax,
            cvDiam = stats.cvDiam,
            placeholderDash = placeholderDash
        )
        SynthesisDataCompletenessCard(stats = stats)
    }
}

@Composable
private fun SynthesisStatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun SynthesisVolumeCard(
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
        enter = fadeIn(tween(400)) + slideInVertically(tween(400, easing = FastOutSlowInEasing)) { it / 5 }
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = GsShape.lg,
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = Elevation.card
        ) {
            Column(
                modifier = Modifier.padding(Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.xxs)
            ) {
                val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
                Text(
                    stringResource(R.string.martelage_volume_price_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onContainer
                )
                Text(
                    stringResource(R.string.martelage_volume_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.75f)
                )
                if (!volumeAvailable) {
                    Text(
                        stringResource(R.string.martelage_volume_partial_format, volumeCompletenessPct),
                        style = MaterialTheme.typography.labelSmall,
                        color = onContainer.copy(alpha = 0.85f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SynthesisStatItem(stringResource(R.string.martelage_label_v_total), "$vTotalText m³", color = onContainer)
                    SynthesisStatItem(stringResource(R.string.martelage_label_v_per_ha), "$vPerHaText m³/ha", color = onContainer)
                }
                HorizontalDivider(color = onContainer.copy(alpha = 0.15f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SynthesisStatItem(stringResource(R.string.martelage_label_revenue), revenueTotalText, color = onContainer)
                    SynthesisStatItem(stringResource(R.string.martelage_label_revenue_per_ha), revenuePerHaText, color = onContainer)
                }
            }
        }
    }
}

@Composable
private fun SynthesisBasalAreaCard(
    gTotal: Double,
    gPerHa: Double,
    surfaceHa: Double? = null,
    ratioVG: Double? = null
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400, delayMillis = 80)) + slideInVertically(tween(400, delayMillis = 80, easing = FastOutSlowInEasing)) { it / 5 }
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = GsShape.lg,
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = Elevation.card
        ) {
            Column(
                modifier = Modifier.padding(Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.xxs)
            ) {
                val onContainer = MaterialTheme.colorScheme.onSecondaryContainer
                Text(
                    stringResource(R.string.martelage_basal_area_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onContainer
                )
                Text(
                    stringResource(R.string.martelage_basal_area_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.75f)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SynthesisStatItem(stringResource(R.string.martelage_label_g_total), "${formatG(gTotal)} m²", color = onContainer)
                    SynthesisStatItem(stringResource(R.string.martelage_label_g_per_ha), "${formatG(gPerHa)} m²/ha", color = onContainer)
                }
                if (surfaceHa != null || ratioVG != null) {
                    HorizontalDivider(color = onContainer.copy(alpha = 0.15f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        if (surfaceHa != null) {
                            SynthesisStatItem(
                                stringResource(R.string.martelage_label_surface),
                                String.format(Locale.getDefault(), "%.4f ha", surfaceHa),
                                color = onContainer
                            )
                        }
                        if (ratioVG != null) {
                            SynthesisStatItem(
                                stringResource(R.string.martelage_label_ratio_vg),
                                String.format(Locale.getDefault(), "%.1f", ratioVG),
                                color = onContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SynthesisDensityCard(
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
        enter = fadeIn(tween(400, delayMillis = 160)) + slideInVertically(tween(400, delayMillis = 160, easing = FastOutSlowInEasing)) { it / 5 }
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = GsShape.lg,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            tonalElevation = Elevation.card
        ) {
            Column(
                modifier = Modifier.padding(Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.xxs)
            ) {
                val onContainer = MaterialTheme.colorScheme.onTertiaryContainer
                Text(
                    stringResource(R.string.martelage_density_structure_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onContainer
                )
                Text(
                    stringResource(R.string.martelage_density_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.75f)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SynthesisStatItem(stringResource(R.string.martelage_label_n_total), "$nTotal", color = onContainer)
                    SynthesisStatItem(stringResource(R.string.martelage_label_n_per_ha), "${formatIntPerHa(nPerHa)}/ha", color = onContainer)
                }
                HorizontalDivider(color = onContainer.copy(alpha = 0.15f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SynthesisStatItem(stringResource(R.string.martelage_label_dm), "${formatDiameter(dm, placeholderDash)} cm", color = onContainer)
                    SynthesisStatItem(stringResource(R.string.martelage_label_dg), "${formatDiameter(dg, placeholderDash)} cm", color = onContainer)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SynthesisStatItem(stringResource(R.string.martelage_label_hm), "${formatHeight(meanH, placeholderDash)} m", color = onContainer)
                    SynthesisStatItem(stringResource(R.string.martelage_label_hlorey), "${formatHeight(hLorey, placeholderDash)} m", color = onContainer)
                }
                if (dMin != null || dMax != null || cvDiam != null) {
                    HorizontalDivider(color = onContainer.copy(alpha = 0.15f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        if (dMin != null && dMax != null) {
                            SynthesisStatItem(
                                stringResource(R.string.martelage_label_d_range),
                                "${formatDiameter(dMin, placeholderDash)} – ${formatDiameter(dMax, placeholderDash)} cm",
                                color = onContainer
                            )
                        }
                        if (cvDiam != null) {
                            SynthesisStatItem(
                                stringResource(R.string.martelage_label_cv_diam),
                                String.format(Locale.getDefault(), "%.0f %%", cvDiam),
                                color = onContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Carte "Qualité des données" — score de complétude + conseils contextuels.
 * Logique de score/conseils identique à l'ancienne DataCompletenessCard
 * (MartelageSummaryCards.kt), seule la présentation change.
 */
@Composable
private fun SynthesisDataCompletenessCard(stats: MartelageStats) {
    val completenessScore = remember(stats) { computeCompletenessScore(stats) }
    val scoreColor = when {
        completenessScore >= 80 -> SemanticSuccess
        completenessScore >= 50 -> SemanticWarning
        else -> SemanticError
    }
    val scoreLabel = when {
        completenessScore >= 80 -> "Données complètes"
        completenessScore >= 50 -> "Données partielles"
        else -> "Données insuffisantes"
    }
    val tips = remember(stats) { buildCompletenessTips(stats) }
    val tarifReco = tarifRecommendationText(stats)
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = GsShape.lg,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = Elevation.card
    ) {
        Column(modifier = Modifier.padding(Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Qualité des données",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        scoreLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = scoreColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
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

            Box(modifier = Modifier.height(Space.sm))

            LinearProgressIndicator(
                progress = { completenessScore / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = scoreColor,
                trackColor = scoreColor.copy(alpha = 0.15f)
            )

            Box(modifier = Modifier.height(Space.sm))

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                shape = GsShape.sm
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.xs)
                ) {
                    Text("📊", style = MaterialTheme.typography.bodyMedium)
                    Box(modifier = Modifier.padding(start = Space.xs)) {
                        Text(
                            tarifReco,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (tips.isNotEmpty()) {
                Box(modifier = Modifier.height(Space.xs))
                val visibleTips = if (expanded) tips else tips.take(2)
                visibleTips.forEach { tip ->
                    Box(modifier = Modifier.height(Space.xxs))
                    Surface(
                        color = if (tip.urgent) SemanticError.copy(alpha = 0.08f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = GsShape.xs
                    ) {
                        Row(modifier = Modifier.padding(Space.xs), verticalAlignment = Alignment.Top) {
                            Text(tip.icon, style = MaterialTheme.typography.bodyMedium)
                            Column(modifier = Modifier.padding(start = Space.xs)) {
                                Text(
                                    tip.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (tip.urgent) SemanticError else MaterialTheme.colorScheme.onSurface
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
                    Box(modifier = Modifier.height(Space.xxs))
                    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.End)) {
                        Text(
                            if (expanded) "Masquer ▲" else "Voir ${tips.size - 2} conseil(s) supplémentaire(s) ▼",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.height(Space.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✅", style = MaterialTheme.typography.bodyMedium)
                    Box(modifier = Modifier.padding(start = Space.xs)) {
                        Text(
                            "Toutes les données clés sont renseignées. Le rapport est complet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SemanticSuccess
                        )
                    }
                }
            }
        }
    }
}

private data class CompletenessTip(val icon: String, val title: String, val body: String, val urgent: Boolean = false)

private fun computeCompletenessScore(stats: MartelageStats): Int {
    var score = 0
    if (stats.nTotal > 0) score += 20
    if (stats.volumeCompletenessPct > 50.0) score += 20
    if (stats.volumeCompletenessPct > 90.0) score += 10
    if (stats.qualityAssessedCount > 0) score += 15
    if (stats.qualityAssessedCount >= stats.qualityTotalCount && stats.qualityTotalCount > 0) score += 10
    if (stats.surfaceHa > 0) score += 10
    if (stats.biodiversity != null) score += 10
    if (stats.specialTrees.isNotEmpty()) score += 5
    return score.coerceIn(0, 100)
}

private fun buildCompletenessTips(stats: MartelageStats): List<CompletenessTip> = buildList {
    if (stats.nTotal < 10) add(
        CompletenessTip(
            "📏", "Échantillon réduit",
            "Seulement ${stats.nTotal} tige(s). La précision des indicateurs est faible. Visez ≥ 30 tiges pour une analyse fiable.",
            urgent = true
        )
    )
    if (stats.surfaceHa <= 0.0) add(
        CompletenessTip(
            "📐", "Surface non renseignée",
            "Sans surface connue, les indicateurs /ha sont incorrects. Renseignez la surface de la parcelle.",
            urgent = true
        )
    )
    if (stats.volumeCompletenessPct < 50.0) add(
        CompletenessTip(
            "📡", "Hauteurs manquantes",
            "${(100 - stats.volumeCompletenessPct).toInt()}% des tiges n'ont pas de hauteur. " +
                "Utilisez un tarif 1 entrée (Schaeffer, IFN Rapide ou Chaudé) ou saisissez des hauteurs d'arbres-type.",
            urgent = true
        )
    ) else if (stats.volumeCompletenessPct < 90.0) add(
        CompletenessTip(
            "📡", "Quelques hauteurs manquantes",
            "${stats.missingHeightEssenceNames.take(3).joinToString(", ")} — " +
                "l'application estime le volume pour ces essences via tarif 1 entrée."
        )
    )
    if (stats.qualityAssessedCount == 0 && stats.nTotal > 0) add(
        CompletenessTip(
            "🔍", "Qualité non évaluée",
            "Aucune tige n'a de classe de qualité (A–D ou 1–5). L'évaluation qualité améliore l'estimation de valorisation."
        )
    ) else if (stats.qualityAssessedCount < stats.qualityTotalCount / 2) add(
        CompletenessTip(
            "🔍",
            "Qualité évaluée sur ${(stats.qualityAssessedCount * 100.0 / stats.qualityTotalCount.coerceAtLeast(1)).toInt()}% des tiges",
            "Complétez l'évaluation qualité pour un rapport de valorisation plus précis."
        )
    )
    if (stats.sanityWarnings.any { it.severity == SanitySeverity.ERROR }) add(
        CompletenessTip(
            "⚠️", "Erreurs de cohérence détectées",
            "Des incohérences critiques ont été détectées dans les données. Consultez l'onglet Alertes sanitaires.",
            urgent = true
        )
    )
    if (stats.ratioVG != null && (stats.ratioVG < 4.0 || stats.ratioVG > 22.0)) add(
        CompletenessTip(
            "🔢", "Ratio V/G anormal (${"%.1f".format(stats.ratioVG)} m³/m²)",
            "Ce ratio suggère un problème de tarif ou des hauteurs aberrantes. Vérifiez le tarif sélectionné."
        )
    )
    if (stats.nTotal >= 10 && stats.biodiversity == null) add(
        CompletenessTip(
            "🌿", "Biodiversité non calculée",
            "Relancez le calcul avec les paramètres IBP pour obtenir l'indice de biodiversité de la parcelle."
        )
    )
}

private fun tarifRecommendationText(stats: MartelageStats): String = when {
    stats.volumeCompletenessPct > 85.0 ->
        "Tarif 2 entrées recommandé (Algan, Schaeffer 2E, IFN Lent) — hauteurs disponibles."
    stats.volumeCompletenessPct > 0.0 ->
        "Tarif mixte : 2 entrées où disponible, sinon Chaudé ou IFN Rapide pour les tiges sans hauteur."
    else ->
        "Tarif 1 entrée recommandé : Chaudé (arbres sur pied) pour feuillus, IFN Rapide pour résineux."
}
