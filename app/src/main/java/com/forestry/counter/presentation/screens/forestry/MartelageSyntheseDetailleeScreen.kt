package com.forestry.counter.presentation.screens.forestry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.forestry.counter.domain.calculation.MartelageStats
import com.forestry.counter.domain.calculation.tarifs.TarifMethod
import com.forestry.counter.domain.model.IbpLevel
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.presentation.components.AutomatedDataDisclaimer
import com.forestry.counter.presentation.theme.Space

/**
 * Écran Synthèse détaillée du Hub Martelage.
 *
 * Regroupe le contenu de l'onglet Synthèse (GSIE-aware) et de l'onglet
 * Analyse (avertissements, distribution, biodiversité, corroboration) en
 * un seul écran dédié plein écran avec TopAppBar et flèche retour.
 *
 * Spec : §5 de docs/superpowers/specs/2026-09-02-martelage-hub-redesign-design.md
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MartelageSyntheseDetailleeScreen(
    stats: MartelageStats,
    scopeLabel: String,
    parcelle: Parcelle?,
    surfaceHa: Double,
    tarifMethod: TarifMethod,
    gsieSyncState: GsieSyncState,
    latestIbp: com.forestry.counter.domain.model.IbpEvaluation?,
    onOpenParams: () -> Unit,
    onNavigateToCalcul: () -> Unit,
    onNavigateBack: () -> Unit,
    onShare: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Synthèse détaillée") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    onShare?.let {
                        IconButton(onClick = it) {
                            Icon(Icons.Default.Share, contentDescription = "Partager")
                        }
                    }
                    onExport?.let {
                        IconButton(onClick = it) {
                            Icon(Icons.Default.Download, contentDescription = "Exporter")
                        }
                    }
                },
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Space.screenH),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            item { Spacer(Modifier.height(Space.xs)) }

            // ── §5.1-5.8 : contenu de l'onglet Synthèse existant ──
            item {
                MartelageSynthesisTab(
                    stats = stats,
                    scopeLabel = scopeLabel,
                    parcelle = parcelle,
                    surfaceHa = surfaceHa,
                    tarifMethod = tarifMethod,
                    gsieSyncState = gsieSyncState,
                    onOpenParams = onOpenParams,
                    onNavigateToValorisation = onNavigateToCalcul,
                )
            }

            // ── §5 Analyse : contenu de l'onglet Analyse (Tab 3) ──
            item {
                SectionTitle("Analyse du peuplement")
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    if (stats.sanityWarnings.isNotEmpty()) SanityWarningsCard(warnings = stats.sanityWarnings)
                    if (stats.harvestNhaPct != null || stats.harvestGhaPct != null) HarvestSimulationCard(
                        harvestNhaPct = stats.harvestNhaPct,
                        harvestGhaPct = stats.harvestGhaPct,
                        residualNha = stats.residualNha,
                        residualGha = stats.residualGha,
                    )
                    if (stats.classDistribution.isNotEmpty()) ClassDistributionCard(
                        classDistribution = stats.classDistribution,
                    )
                    if (stats.qualityDistribution.isNotEmpty()) QualityDistributionCard(
                        qualityDistribution = stats.qualityDistribution,
                        assessedCount = stats.qualityAssessedCount,
                        totalCount = stats.qualityTotalCount,
                    )
                    if (stats.specialTrees.isNotEmpty()) SpecialTreesCard(specialTrees = stats.specialTrees)
                    stats.biodiversity?.let { bio -> BiodiversityCard(bio = bio) }
                    CorroborationReportCard(stats = stats)
                    SylviculturalKPIsCard(stats = stats)
                }
            }

            // ── §5.9 Ancrage futur moteur de corrélation GSIE ──
            item { GsieCorrelationAnchorCard() }

            item { AutomatedDataDisclaimer() }
            item { Spacer(Modifier.height(Space.lg)) }
        }
    }
}

/** Titre de sous-section dans la Synthèse détaillée. */
@Composable
private fun SectionTitle(title: String) {
    Column {
        Spacer(Modifier.height(Space.xs))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()
    }
}

/**
 * Encart « en attente du moteur de corrélation GSIE ».
 *
 * Signale honnêtement que la synthèse adaptative automatique n'est pas
 * encore active, sans créer de fausse promesse de contenu généré.
 * Spec §5.9.
 */
@Composable
private fun GsieCorrelationAnchorCard() {
    androidx.compose.material3.OutlinedCard(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = Space.xs),
        border = androidx.compose.foundation.BorderStroke(
            width = Space.xxs / 2,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Text(
                text = "Synthèse adaptative GSIE — à venir",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Cette synthèse sera à terme produite par un moteur de corrélation " +
                    "local et GSIE Serveur, qui relira l'ensemble des données de la parcelle " +
                    "(localisation, historique, facteurs limitants détectés) pour adapter " +
                    "automatiquement les conclusions. Non actif aujourd'hui.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
