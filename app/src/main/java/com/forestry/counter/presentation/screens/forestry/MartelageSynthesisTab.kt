package com.forestry.counter.presentation.screens.forestry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.forestry.counter.domain.calculation.MartelageStats
import com.forestry.counter.domain.calculation.SanitySeverity
import com.forestry.counter.domain.calculation.cubage.CubageMethodRegistry
import com.forestry.counter.domain.calculation.cubage.MethodQualification
import com.forestry.counter.domain.calculation.tarifs.TarifMethod
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.presentation.theme.Elevation
import com.forestry.counter.presentation.theme.GsShape
import com.forestry.counter.presentation.theme.SemanticError
import com.forestry.counter.presentation.theme.SemanticSuccess
import com.forestry.counter.presentation.theme.SemanticWarning
import com.forestry.counter.presentation.theme.Space
import java.util.Locale

/**
 * Onglet "Synthèse" de l'écran Martelage — résumé exécutif conscient de GSIE
 * (docs/SYNTHESE_V2_SPECIFICATION.md). Sections réellement remplies aujourd'hui
 * à partir de [MartelageStats] et du contexte parcelle/placette, sans données
 * fabriquées : résumé exécutif (§4.1), contexte (§4.2), couverture (§4.3),
 * calculs local vs serveur (§4.5), incertitudes (§4.10 partiel), sources (§4.11
 * partiel). Décisions/conclusions/recommandations (§4.6/4.8/4.9) volontairement
 * omises — aucun modèle de données ne les alimente honnêtement aujourd'hui.
 *
 * Aucune synchronisation réseau réelle : le bloc "Vérification serveur GSIE" est
 * toujours figé sur SYNC_PENDING, inerte, avec une mention explicite.
 */
@Composable
fun MartelageSynthesisTab(
    stats: MartelageStats,
    scopeLabel: String,
    parcelle: Parcelle?,
    surfaceHa: Double,
    tarifMethod: TarifMethod,
    gsieSyncState: GsieSyncState,
    onOpenParams: () -> Unit,
    onNavigateToValorisation: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        ExecutiveSummarySection(stats, scopeLabel, gsieSyncState, onOpenParams)
        ContextSection(parcelle, surfaceHa, tarifMethod)
        CoverageSection(stats)
        CalculationsSection(stats, tarifMethod, gsieSyncState)
        UncertaintiesSection(stats)
        if (stats.revenueTotal != null && stats.revenueTotal > 0.0) {
            ValorisationPointerSection(stats, onNavigateToValorisation)
        }
        SourcesSection(tarifMethod)
    }
}

@Composable
private fun SynthesisSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = GsShape.lg,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = Elevation.card
    ) {
        Column(
            modifier = Modifier.padding(Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

/** §4.1 Résumé exécutif — ne présente jamais une simulation/recommandation bloquée comme certaine. */
@Composable
private fun ExecutiveSummarySection(
    stats: MartelageStats,
    scopeLabel: String,
    gsieSyncState: GsieSyncState,
    onOpenParams: () -> Unit
) {
    val criticalCount = stats.sanityWarnings.count { it.severity == SanitySeverity.ERROR }
    SynthesisSectionCard(title = "Résumé exécutif") {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(scopeLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${stats.nTotal} tige(s) · ${"%.0f".format(Locale.getDefault(), stats.volumeCompletenessPct)} % de cubage couvert",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            GsieStateBadge(gsieSyncState)
        }
        if (criticalCount > 0) {
            Surface(color = SemanticError.copy(alpha = 0.12f), shape = GsShape.sm) {
                Row(
                    modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.xxs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$criticalCount alerte(s) critique(s) — voir l'onglet Analyse",
                        style = MaterialTheme.typography.labelMedium,
                        color = SemanticError
                    )
                }
            }
        }
        TextButton(onClick = onOpenParams) {
            Text("Ouvrir les paramètres de calcul")
        }
    }
}

/** §4.2 Contexte — seulement les champs réellement disponibles, jamais de "N/A" fabriqué. */
@Composable
private fun ContextSection(parcelle: Parcelle?, surfaceHa: Double, tarifMethod: TarifMethod) {
    SynthesisSectionCard(title = "Contexte") {
        ContextRow("Parcelle", parcelle?.name)
        if (surfaceHa > 0.0) ContextRow("Surface", "%.4f ha".format(Locale.getDefault(), surfaceHa))
        ContextRow("Objectif", parcelle?.objectifType)
        ContextRow("Méthode de cubage", tarifMethod.label)
    }
}

@Composable
private fun ContextRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/**
 * §4.3 Couverture et qualité — liste détaillée, jamais réduite à un seul score
 * opaque (règle explicite du spec). La carte "Qualité des données" de l'onglet
 * Dendrométrie garde son score déjà validé ; ce bloc-ci ne le reproduit pas.
 */
@Composable
private fun CoverageSection(stats: MartelageStats) {
    SynthesisSectionCard(title = "Couverture et qualité") {
        CoverageRow("Tiges mesurées", "${stats.nTotal}")
        CoverageRow("Couverture du cubage", "%.0f %%".format(Locale.getDefault(), stats.volumeCompletenessPct))
        if (stats.missingHeightEssenceNames.isNotEmpty()) {
            CoverageRow(
                "Essences sans hauteur",
                stats.missingHeightEssenceNames.take(3).joinToString(", ") +
                    if (stats.missingHeightEssenceNames.size > 3) "…" else ""
            )
        }
        if (stats.unpricedEssenceNames.isNotEmpty()) {
            CoverageRow(
                "Essences sans prix",
                stats.unpricedEssenceNames.take(3).joinToString(", ") +
                    if (stats.unpricedEssenceNames.size > 3) "…" else ""
            )
        }
        CoverageRow(
            "Qualité évaluée",
            "${stats.qualityAssessedCount} / ${stats.qualityTotalCount} tige(s)"
        )
    }
}

@Composable
private fun CoverageRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * §4.5 Calculs — le cubage local GeoSylva et une vérification serveur GSIE sont
 * TOUJOURS deux résultats distincts, jamais fusionnés (règle explicite du spec).
 */
@Composable
private fun CalculationsSection(stats: MartelageStats, tarifMethod: TarifMethod, gsieSyncState: GsieSyncState) {
    val methodMeta = CubageMethodRegistry.findStandingTree(tarifMethod.code)
    SynthesisSectionCard(title = "Calculs") {
        // Bloc 1 — cubage local GeoSylva (résultat réel)
        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = GsShape.sm) {
            Column(modifier = Modifier.padding(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Cubage local (GeoSylva)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    GsieStateBadge(gsieSyncState, compact = true)
                }
                Text(
                    "${formatVolume(stats.vTotal)} m³ · ${tarifMethod.label}" +
                        (methodMeta?.let { " · ${it.qualification.label()}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (methodMeta?.qualification == MethodQualification.EXPERIMENTAL) {
                    Text(
                        "Méthode expérimentale : coefficients historiques, pas encore vérifiables indépendamment.",
                        style = MaterialTheme.typography.labelSmall,
                        color = SemanticWarning
                    )
                }
            }
        }
        // Bloc 2 — vérification serveur GSIE (jamais atteinte aujourd'hui, toujours distincte)
        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), shape = GsShape.sm) {
            Column(modifier = Modifier.padding(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Vérification serveur GSIE", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    GsieStateBadge(GsieSyncState.SYNC_PENDING, compact = true)
                }
                Text(
                    gsieStateSubtitle(GsieSyncState.SYNC_PENDING) ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun MethodQualification.label(): String = when (this) {
    MethodQualification.QUALIFIED -> "qualifiée"
    MethodQualification.EXPERIMENTAL -> "expérimentale"
    MethodQualification.UNQUALIFIED -> "non qualifiée"
}

/** §4.10 Incertitudes — réutilise sanityWarnings ; pas de contradictions fabriquées. */
@Composable
private fun UncertaintiesSection(stats: MartelageStats) {
    val warnings = stats.sanityWarnings
    if (warnings.isEmpty() && stats.missingHeightEssenceNames.isEmpty()) return
    SynthesisSectionCard(title = "Incertitudes") {
        if (warnings.isNotEmpty()) {
            Text(
                "${warnings.size} alerte(s) de cohérence — détail dans l'onglet Analyse.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (stats.missingHeightEssenceNames.isNotEmpty()) {
            Text(
                "Hauteurs estimées (non mesurées) pour : ${stats.missingHeightEssenceNames.take(3).joinToString(", ")}.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        HorizontalDivider()
        Text(
            "Contradictions local/serveur : sans connexion GSIE, il n'y a rien à comparer aujourd'hui.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ValorisationPointerSection(stats: MartelageStats, onNavigateToValorisation: () -> Unit) {
    SynthesisSectionCard(title = "Valorisation") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Recette estimée", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "%.0f €".format(Locale.getDefault(), stats.revenueTotal ?: 0.0),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            TextButton(onClick = onNavigateToValorisation) { Text("Détail par essence") }
        }
    }
}

/** §4.11 Sources — citation de méthode seulement ; pas de reproductibilité versionnée aujourd'hui. */
@Composable
private fun SourcesSection(tarifMethod: TarifMethod) {
    val methodMeta = CubageMethodRegistry.findStandingTree(tarifMethod.code)
    SynthesisSectionCard(title = "Sources") {
        Text(
            methodMeta?.sourceCitation ?: tarifMethod.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Document recalculé à chaque affichage — pas encore de version figée persistée.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}
