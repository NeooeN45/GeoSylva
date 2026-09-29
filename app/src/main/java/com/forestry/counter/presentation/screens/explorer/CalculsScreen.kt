package com.forestry.counter.presentation.screens.explorer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Timeline
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forestry.counter.presentation.theme.GsShape
import com.forestry.counter.presentation.theme.Motion
import com.forestry.counter.presentation.theme.Space

/**
 * Écran Calculs — passerelle vers les outils de calcul forestier de l'app.
 *
 * Chaque tuile décrit un outil disponible et y redirige directement.
 * Les outils non encore reliés à un parcours spécifique sont présentés
 * en grisé avec le statut "depuis une placette".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMartelage: () -> Unit,
    onNavigateToForets: () -> Unit,
    onNavigateToIbp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Calculs") },
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
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            item {
                Text(
                    "Outils disponibles",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items(calculTools) { tool ->
                CalculToolCard(
                    tool = tool,
                    onNavigateToMartelage = onNavigateToMartelage,
                    onNavigateToForets = onNavigateToForets,
                    onNavigateToIbp = onNavigateToIbp,
                )
            }

            item {
                Text(
                    "Depuis une placette",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.xs),
                )
            }

            items(calculToolsFromPlacette) { tool ->
                CalculToolCard(
                    tool = tool,
                    onNavigateToMartelage = onNavigateToMartelage,
                    onNavigateToForets = onNavigateToForets,
                    onNavigateToIbp = onNavigateToIbp,
                )
            }
        }
    }
}

// ─── Modèle de tuile ─────────────────────────────────────────────────────────

private data class CalculTool(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val action: CalculToolAction,
    val enabled: Boolean = true,
)

private sealed interface CalculToolAction {
    object Martelage : CalculToolAction
    object Forets : CalculToolAction
    object Ibp : CalculToolAction
    object FromPlacette : CalculToolAction
}

private val calculTools = listOf(
    CalculTool(
        title = "Martelage & synthèse",
        description = "Cubage, surface terrière, valorisation économique du peuplement.",
        icon = Icons.Filled.Forest,
        action = CalculToolAction.Martelage,
    ),
    CalculTool(
        title = "Inventaire IBP",
        description = "Indice de Biodiversité Potentielle — évaluation écologique de la parcelle.",
        icon = Icons.Filled.Biotech,
        action = CalculToolAction.Ibp,
    ),
    CalculTool(
        title = "Vue d'ensemble des forêts",
        description = "Parcourir les forêts et parcelles pour lancer une analyse par placette.",
        icon = Icons.Filled.Park,
        action = CalculToolAction.Forets,
    ),
)

private val calculToolsFromPlacette = listOf(
    CalculTool(
        title = "Calculatrice dendrométrique",
        description = "Formules personnalisées, compteurs, export CSV.",
        icon = Icons.Filled.Functions,
        action = CalculToolAction.FromPlacette,
        enabled = false,
    ),
    CalculTool(
        title = "Diagnostic sylvicole",
        description = "Évaluation multi-critères de l'état du peuplement.",
        icon = Icons.Filled.Timeline,
        action = CalculToolAction.FromPlacette,
        enabled = false,
    ),
    CalculTool(
        title = "Ripisylve",
        description = "Diagnostic spécifique aux forêts riveraines.",
        icon = Icons.Filled.Grass,
        action = CalculToolAction.FromPlacette,
        enabled = false,
    ),
    CalculTool(
        title = "Station & corrélateur",
        description = "Station sylvicole, corrélation floristique, potentiel dendrométrique.",
        icon = Icons.Filled.Calculate,
        action = CalculToolAction.FromPlacette,
        enabled = false,
    ),
)

// ─── Carte outil ─────────────────────────────────────────────────────────────

@Composable
private fun CalculToolCard(
    tool: CalculTool,
    onNavigateToMartelage: () -> Unit,
    onNavigateToForets: () -> Unit,
    onNavigateToIbp: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && tool.enabled) Motion.PRESS_SCALE else 1f,
        animationSpec = Motion.springSnappy(),
        label = "calcToolScale",
    )

    Card(
        onClick = {
            if (tool.enabled) {
                when (tool.action) {
                    CalculToolAction.Martelage -> onNavigateToMartelage()
                    CalculToolAction.Forets -> onNavigateToForets()
                    CalculToolAction.Ibp -> onNavigateToIbp()
                    CalculToolAction.FromPlacette -> {}
                }
            }
        },
        enabled = tool.enabled,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shape = GsShape.md,
        colors = CardDefaults.cardColors(
            containerColor = if (tool.enabled)
                MaterialTheme.colorScheme.surfaceContainerHigh
            else
                MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        interactionSource = interactionSource,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Icône fantôme
            Icon(
                imageVector = tool.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = if (tool.enabled) 0.05f else 0.03f
                ),
                modifier = Modifier
                    .size(80.dp)
                    .align(Alignment.CenterEnd)
                    .padding(end = Space.sm),
            )

            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Space.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Surface(
                    shape = GsShape.sm,
                    color = if (tool.enabled)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = null,
                            tint = if (tool.enabled)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tool.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (tool.enabled)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                    Text(
                        text = tool.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (tool.enabled) 1f else 0.6f
                        ),
                    )
                }

                if (tool.enabled) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
