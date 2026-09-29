package com.forestry.counter.presentation.screens.forestry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forestry.counter.R
import com.forestry.counter.domain.calculation.MartelageStats
import com.forestry.counter.domain.calculation.ProductBreakdownRow
import com.forestry.counter.domain.calculation.tarifs.TarifMethod
import com.forestry.counter.domain.model.Essence
import com.forestry.counter.presentation.components.AutomatedDataDisclaimer
import com.forestry.counter.presentation.theme.GsShape
import com.forestry.counter.presentation.theme.Space
import com.forestry.counter.presentation.utils.ColorUtils

/**
 * Écran Calcul fin du Hub Martelage.
 *
 * Regroupe en trois sous-sections séparées :
 *  6.1 Dendrométrie — contenu de l'onglet Dendrométrie existant
 *  6.2 Valorisation — contenu de l'onglet Valorisation existant
 *  6.3 Indices sylvicoles sourcés — Hart-Becking, Reineke SDI, CV structural
 *
 * Spec : §6 de docs/superpowers/specs/2026-09-02-martelage-hub-redesign-design.md
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MartelageCalculFinScreen(
    stats: MartelageStats,
    essences: List<Essence>,
    breakdownByEssence: Map<String, List<ProductBreakdownRow>>,
    vTotalText: String,
    vPerHaText: String,
    revenueTotalText: String,
    revenuePerHaText: String,
    placeholderDash: String,
    missingPriceVol: Double,
    missingPriceVolText: String,
    tarifMethod: TarifMethod,
    onNavigateToSettings: (() -> Unit)?,
    onNavigateToPriceTablesEditor: (() -> Unit)?,
    onNavigateBack: () -> Unit,
    onShare: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
) {
    var essenceSortKey by remember { mutableStateOf("name") }
    val euroSymbol = "€"
    val ellipsis = "…"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calcul fin") },
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

            // ── §6.1 Dendrométrie ──
            item {
                CalculFinSectionTitle("Dendrométrie")
            }
            item {
                MartelageDendrometryTab(
                    stats = stats,
                    vTotalText = vTotalText,
                    vPerHaText = vPerHaText,
                    revenueTotalText = revenueTotalText,
                    revenuePerHaText = revenuePerHaText,
                    placeholderDash = placeholderDash,
                )
            }

            // ── §6.2 Valorisation ──
            item {
                CalculFinSectionTitle("Valorisation")
            }
            item {
                ValorisationContent(
                    stats = stats,
                    essences = essences,
                    breakdownByEssence = breakdownByEssence,
                    missingPriceVol = missingPriceVol,
                    missingPriceVolText = missingPriceVolText,
                    essenceSortKey = essenceSortKey,
                    onSortKeyChange = { essenceSortKey = it },
                    placeholderDash = placeholderDash,
                    euroSymbol = euroSymbol,
                    ellipsis = ellipsis,
                    onNavigateToPriceTablesEditor = onNavigateToPriceTablesEditor,
                    onNavigateToSettings = onNavigateToSettings,
                )
            }

            // ── §6.3 Indices sylvicoles sourcés ──
            item {
                CalculFinSectionTitle("Indices sylvicoles")
            }
            item {
                SylviculturalIndicesSection(stats = stats)
            }

            item { AutomatedDataDisclaimer() }
            item { Spacer(Modifier.height(Space.lg)) }
        }
    }
}

// ─── Titre de sous-section ────────────────────────────────────────────────────

@Composable
private fun CalculFinSectionTitle(title: String) {
    Column {
        Spacer(Modifier.height(Space.xs))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()
        Spacer(Modifier.height(Space.xxs))
    }
}

// ─── Contenu Valorisation (extrait de MartelageScreen Tab 2) ─────────────────

@Composable
private fun ValorisationContent(
    stats: MartelageStats,
    essences: List<Essence>,
    breakdownByEssence: Map<String, List<ProductBreakdownRow>>,
    missingPriceVol: Double,
    missingPriceVolText: String,
    essenceSortKey: String,
    onSortKeyChange: (String) -> Unit,
    placeholderDash: String,
    euroSymbol: String,
    ellipsis: String,
    onNavigateToPriceTablesEditor: (() -> Unit)?,
    onNavigateToSettings: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        if (missingPriceVol > 0.0 && stats.vTotal > 0.0) {
            val pct = ((missingPriceVol / stats.vTotal.coerceAtLeast(1e-9)) * 100.0)
                .toInt().coerceIn(0, 100)
            val warnBg = MaterialTheme.colorScheme.errorContainer
            val warnFg = ColorUtils.getContrastingTextColor(warnBg)
            Card(
                modifier = Modifier.fillMaxWidth().clip(GsShape.lg),
                colors = CardDefaults.cardColors(containerColor = warnBg, contentColor = warnFg),
            ) {
                Column(
                    modifier = Modifier.padding(Space.sm),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null)
                        Text(
                            stringResource(R.string.martelage_missing_prices_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Text(
                        stringResource(R.string.martelage_missing_prices_desc, missingPriceVolText, pct),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (stats.unpricedEssenceNames.isNotEmpty()) {
                        val names = stats.unpricedEssenceNames.take(3).joinToString(", ") +
                            if (stats.unpricedEssenceNames.size > 3) ellipsis else ""
                        Text(
                            stringResource(R.string.martelage_missing_prices_essences, names),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    (onNavigateToPriceTablesEditor ?: onNavigateToSettings)?.let { nav ->
                        TextButton(onClick = nav) {
                            Text(stringResource(R.string.martelage_configure_prices))
                        }
                    }
                }
            }
        }

        stats.perEssence.filter { it.vTotal > 0.0 }.forEach { ess ->
            val rows = breakdownByEssence[ess.essenceCode]
            if (!rows.isNullOrEmpty()) ProductBreakdownCard(
                essenceName = ess.essenceName,
                quality = ess.dominantQuality?.code,
                rows = rows,
            )
        }

        if (stats.perEssence.isNotEmpty()) {
            val sortedPerEssence = remember(stats.perEssence, essenceSortKey) {
                when (essenceSortKey) {
                    "n"       -> stats.perEssence.sortedByDescending { it.n }
                    "volume"  -> stats.perEssence.sortedByDescending { it.vTotal }
                    "revenue" -> stats.perEssence.sortedByDescending { it.revenueTotal ?: 0.0 }
                    "g"       -> stats.perEssence.sortedByDescending { it.gTotal }
                    else      -> stats.perEssence.sortedBy { it.essenceName }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Sort,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                listOf(
                    "name" to stringResource(R.string.sort_name),
                    "n" to "N",
                    "g" to "G",
                    "volume" to "V",
                    "revenue" to stringResource(R.string.sort_revenue),
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = essenceSortKey == key,
                        onClick = { onSortKeyChange(key) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
            PerEssenceTable(
                perEssence = sortedPerEssence,
                essences = essences,
                placeholderDash = placeholderDash,
                euroSymbol = euroSymbol,
            )
        }
    }
}

// ─── §6.3 Indices sylvicoles sourcés ─────────────────────────────────────────

@Composable
private fun SylviculturalIndicesSection(stats: MartelageStats) {
    // Les indices Hart-Becking, Reineke SDI et CV sont affichés depuis
    // SylviculturalKPIsCard (déjà existant) et seront enrichis dans le lot
    // suivant après la création de SylviculturalValidityEngine.kt.
    // Pour l'instant, la carte existante est réutilisée ici.
    SylviculturalKPIsCard(stats = stats)
}
