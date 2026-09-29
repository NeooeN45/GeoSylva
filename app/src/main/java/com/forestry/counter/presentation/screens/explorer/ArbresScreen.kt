package com.forestry.counter.presentation.screens.explorer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forestry.counter.domain.model.Essence
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.repository.EssenceRepository
import com.forestry.counter.domain.repository.TigeRepository
import com.forestry.counter.presentation.theme.GsShape
import com.forestry.counter.presentation.theme.Space
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Écran Arbres — vue globale de toutes les tiges enregistrées.
 *
 * Affiche les stats clés (effectif total, nombre d'essences distinctes,
 * diamètre moyen) puis la liste des tiges triées par date décroissante,
 * avec pour chaque tige : l'essence, le diamètre, la parcelle d'origine.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArbresScreen(
    tigeRepository: TigeRepository,
    essenceRepository: EssenceRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tiges by tigeRepository.getAllTiges().collectAsStateWithLifecycle(initialValue = emptyList())
    val essences by essenceRepository.getAllEssences().collectAsStateWithLifecycle(initialValue = emptyList())

    val essenceMap: Map<String, Essence> = essences.associateBy { it.code }
    val sortedTiges = tiges.sortedByDescending { it.timestamp }

    val totalCount = tiges.size
    val distinctEssences = tiges.map { it.essenceCode }.distinct().size
    val avgDiam = if (tiges.isEmpty()) 0.0 else tiges.map { it.diamCm }.average()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Arbres") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (tiges.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    Icon(
                        Icons.Filled.Grass,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    Text(
                        "Aucun arbre enregistré",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Les arbres apparaissent ici dès qu'une tige est saisie.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    horizontal = Space.screenH,
                    vertical = Space.sm,
                ),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                // ── Cartes de stats ───────────────────────────────────────
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        ArbreStatCard(
                            label = "Arbres",
                            value = totalCount.toString(),
                            icon = { Icon(Icons.Filled.FormatListNumbered, null) },
                            modifier = Modifier.weight(1f),
                        )
                        ArbreStatCard(
                            label = "Essences",
                            value = distinctEssences.toString(),
                            icon = { Icon(Icons.Filled.Category, null) },
                            modifier = Modifier.weight(1f),
                        )
                        ArbreStatCard(
                            label = "Ø moy.",
                            value = "%.1f cm".format(avgDiam),
                            icon = { Icon(Icons.Filled.Straighten, null) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // ── En-tête liste ─────────────────────────────────────────
                item {
                    Text(
                        "Liste des tiges",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.xs),
                    )
                }

                // ── Tiges ─────────────────────────────────────────────────
                items(sortedTiges, key = { it.id }) { tige ->
                    TigeListItem(tige = tige, essenceMap = essenceMap)
                }
            }
        }
    }
}

@Composable
private fun ArbreStatCard(
    label: String,
    value: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = GsShape.sm,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Space.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.xxs),
        ) {
            Surface(
                shape = GsShape.xs,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    icon()
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
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

@Composable
private fun TigeListItem(
    tige: Tige,
    essenceMap: Map<String, Essence>,
) {
    val essenceName = essenceMap[tige.essenceCode]?.name ?: tige.essenceCode
    val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date(tige.timestamp))

    Card(
        shape = GsShape.sm,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        ListItem(
            headlineContent = {
                Text(essenceName, fontWeight = FontWeight.Medium)
            },
            supportingContent = {
                Text("Ø ${tige.diamCm} cm · ${dateStr}")
            },
            trailingContent = {
                tige.hauteurM?.let { h ->
                    Text(
                        "H %.1f m".format(h),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            leadingContent = {
                Surface(
                    shape = GsShape.xs,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = tige.essenceCode.take(2).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        )
    }
}
