package com.forestry.counter.presentation.screens.explorer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.repository.TigeRepository
import com.forestry.counter.presentation.theme.GsShape
import com.forestry.counter.presentation.theme.Space
import kotlin.math.PI

/**
 * Écran Mesures — tableau de bord des métriques dendrométriques globales.
 *
 * Affiche : effectif total, surface terrière (G en m²/ha estimée),
 * hauteur moyenne (si disponible), répartition par classes de diamètre
 * (histogram 10 cm par 10 cm).
 *
 * Nota : G estimée ici sans surface de référence (toutes tiges confondues),
 * exprimée en m² (aire de section cumulée). Pour une G/ha correcte, il
 * faudrait connaître la surface de chaque placette — c'est un affichage
 * exploratoire, pas une valeur réglementaire.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MesuresScreen(
    tigeRepository: TigeRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tiges by tigeRepository.getAllTiges().collectAsStateWithLifecycle(initialValue = emptyList())

    val totalCount = tiges.size
    val totalG = tiges.sumOf { g(it.diamCm) }           // m² section cumulée
    val avgDiam = if (tiges.isEmpty()) 0.0 else tiges.map { it.diamCm }.average()
    val avgH = tiges.mapNotNull { it.hauteurM }.let { hs ->
        if (hs.isEmpty()) null else hs.average()
    }
    val classDist = diamClassDistribution(tiges)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Mesures") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                horizontal = Space.screenH,
                vertical = Space.sm,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            // ── Cartes indicateurs ─────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    MesureKpiCard(
                        label = "Tiges",
                        value = totalCount.toString(),
                        icon = Icons.Filled.AccountTree,
                        modifier = Modifier.weight(1f),
                    )
                    MesureKpiCard(
                        label = "Ø moy.",
                        value = "%.1f cm".format(avgDiam),
                        icon = Icons.Filled.Straighten,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    MesureKpiCard(
                        label = "Section ∑",
                        value = "%.3f m²".format(totalG),
                        icon = Icons.Filled.Forest,
                        modifier = Modifier.weight(1f),
                    )
                    MesureKpiCard(
                        label = "H moy.",
                        value = avgH?.let { "%.1f m".format(it) } ?: "—",
                        icon = Icons.Filled.BarChart,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ── Histogramme classes de diamètre ───────────────────────
            if (classDist.isNotEmpty()) {
                item {
                    Card(
                        shape = GsShape.md,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Space.md),
                            verticalArrangement = Arrangement.spacedBy(Space.sm),
                        ) {
                            Text(
                                "Répartition par classes de diamètre (cm)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            DiamClassHistogram(
                                classDist = classDist,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp),
                            )
                            // Légende axe X
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                classDist.keys.sorted().take(6).forEach { cls ->
                                    Text(
                                        "$cls",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Note méthode ──────────────────────────────────────────
            item {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = GsShape.sm,
                ) {
                    Text(
                        "Section terrière ∑ = somme des aires de section basale " +
                            "(π·(d/2)²) de toutes les tiges. " +
                            "Pour une G/ha réglementaire, divisez par la surface totale des placettes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(Space.sm),
                    )
                }
            }
        }
    }
}

// ── Composables internes ──────────────────────────────────────────────────────

@Composable
private fun MesureKpiCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = GsShape.sm,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Surface(
                shape = GsShape.xs,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DiamClassHistogram(
    classDist: Map<Int, Int>,
    modifier: Modifier = Modifier,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = modifier) {
        val sortedClasses = classDist.keys.sorted()
        if (sortedClasses.isEmpty()) return@Canvas

        val maxCount = classDist.values.maxOrNull() ?: 1
        val barCount = sortedClasses.size
        val totalGap = 4.dp.toPx() * (barCount - 1)
        val barWidth = (size.width - totalGap) / barCount

        sortedClasses.forEachIndexed { index, cls ->
            val count = classDist[cls] ?: 0
            val barHeight = (count.toFloat() / maxCount) * (size.height - 16.dp.toPx())
            val left = index * (barWidth + 4.dp.toPx())
            val top = size.height - barHeight - 16.dp.toPx()

            drawRect(
                color = barColor,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
            )
        }
    }
}

// ── Fonctions utilitaires ─────────────────────────────────────────────────────

/** Section terrière d'un arbre en m² (g = π/4 · d²). */
private fun g(diamCm: Double): Double = PI / 4.0 * (diamCm / 100.0) * (diamCm / 100.0)

/**
 * Répartition par classes de diamètre de 10 cm.
 * Retourne une map : borne inférieure de classe → effectif.
 */
private fun diamClassDistribution(tiges: List<Tige>): Map<Int, Int> {
    if (tiges.isEmpty()) return emptyMap()
    return tiges
        .groupBy { tige -> (tige.diamCm.toInt() / 10) * 10 }
        .mapValues { (_, list) -> list.size }
}
