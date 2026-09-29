package com.forestry.counter.presentation.screens.explorer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.forestry.counter.R
import com.forestry.counter.data.preferences.UserPreferencesManager
import com.forestry.counter.presentation.theme.GsShape
import com.forestry.counter.presentation.theme.Motion
import com.forestry.counter.presentation.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Écran Explorer — 13 catégories de données forestières.
 *
 * Spec GEOSYLVA-003 §29.4 : grille de catégories permettant de parcourir
 * toutes les entités du contrat de données.
 *
 * Refondu dans le style de l'accueil Réglages (cartes arrondies sur
 * `surfaceContainerHigh`, icône en pastille de couleur, pression animée) —
 * même fond d'écran par défaut que l'Accueil pour rester le même produit
 * d'un onglet à l'autre. Entrée en cascade et tirer-pour-actualiser en plus,
 * couleurs de l'indicateur adaptées au thème.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    onNavigateToForets: () -> Unit,
    onNavigateToProjects: () -> Unit,
    onCategoryClick: (ExplorerCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val preferencesManager = LocalContext.current.let { remember(it) { UserPreferencesManager(it) } }
    val backgroundImageEnabled by preferencesManager.backgroundImageEnabled.collectAsStateWithLifecycle(initialValue = true)
    val backgroundImageUri by preferencesManager.backgroundImageUri.collectAsStateWithLifecycle(initialValue = null)
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        if (backgroundImageEnabled) {
            val uriString = backgroundImageUri
            if (uriString != null) {
                AsyncImage(
                    model = uriString,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.forest_background),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
        ) { innerPadding ->
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    // Rien à recharger tant que les catégories restent des
                    // compteurs statiques (Lot 1) — le geste reste branché
                    // pour les écrans à données réelles à mesure qu'ils
                    // arrivent, sans revenir modifier cet écran plus tard.
                    isRefreshing = true
                    scope.launch {
                        delay(700)
                        isRefreshing = false
                    }
                },
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    // Noir + bleu en sombre, blanc + bleu en clair — demandé
                    // explicitement, délibérément hors de la palette verte de
                    // marque pour cette seule interaction.
                    PullToRefreshDefaults.Indicator(
                        state = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState(),
                        isRefreshing = isRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter),
                        containerColor = MaterialTheme.colorScheme.background,
                        color = ExplorerRefreshBlue,
                    )
                },
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Space.screenH,
                        end = Space.screenH,
                        top = innerPadding.calculateTopPadding() + Space.sm,
                        bottom = innerPadding.calculateBottomPadding() + Space.xxl,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    // Titre collé au fond de la page, scrollant avec les
                    // cartes — pas de barre séparée qui se replie à son
                    // propre rythme : le défilement doit rester aussi fluide
                    // que pour n'importe quelle carte de la grille.
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Surface(
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                            shape = GsShape.md,
                            modifier = Modifier.padding(bottom = Space.sm),
                        ) {
                            Text(
                                text = stringResource(R.string.explorer_title),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = Space.md, vertical = Space.xs),
                            )
                        }
                    }
                    itemsIndexed(ExplorerCategory.entries) { index, category ->
                        ExplorerCategoryCard(
                            category = category,
                            index = index,
                            onClick = {
                                when (category) {
                                    ExplorerCategory.FORETS -> onNavigateToForets()
                                    ExplorerCategory.PROJECTS -> onNavigateToProjects()
                                    else -> onCategoryClick(category)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

// Bleu volontairement hors palette de marque — demandé pour l'indicateur
// de tirer-pour-actualiser uniquement, identique dans les deux thèmes.
private val ExplorerRefreshBlue = androidx.compose.ui.graphics.Color(0xFF1E88E5)

@Composable
private fun ExplorerCategoryCard(
    category: ExplorerCategory,
    index: Int,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) Motion.PRESS_SCALE else 1f,
        animationSpec = Motion.springSnappy(),
        label = "explorerCardScale",
    )

    // Pas d'entrée en cascade ici : `LazyVerticalGrid` dispose et recompose
    // les cartes hors-écran, donc une animation basée sur `index` rejouait
    // à chaque défilement — la carte n'existait pas encore (alpha 0, non
    // cliquable) le temps du délai. Les cartes doivent être disponibles
    // immédiatement dès qu'elles entrent dans le viewport.
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .scale(scale),
        shape = GsShape.lg,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f)),
        interactionSource = interactionSource,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
                // Icône fantôme — grande, très pâle, en fond de carte : une
                // touche de profondeur sans surcharger le badge, qui reste
                // seul repère net à l'échelle de lecture.
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    modifier = Modifier
                        .size(96.dp)
                        .align(Alignment.BottomEnd)
                        .padding(end = 4.dp, bottom = 4.dp),
                )

                Column(
                    modifier = Modifier.padding(Space.md).fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = category.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.xxs),
                        ) {
                            Text(
                                text = category.label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (category.isImplemented) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        if (!category.isImplemented) {
                            Text(
                                text = stringResource(R.string.explorer_coming_soon),
                                style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Les 13 catégories de l'Explorer — spec GESYLVA-003 §29.4.
 */
enum class ExplorerCategory(
    val label: String,
    val icon: ImageVector,
    val isImplemented: Boolean = false,
) {
    FORETS("Forêts", Icons.Filled.Forest, isImplemented = true),
    // Parcelles/Placettes/Diagnostics mènent toutes à la liste globale des
    // parcelles (scope "all", même écran que Forêts → parcelle) : c'est le
    // seul point d'entrée cohérent pour des données qui n'existent que
    // rattachées à une parcelle — pas de vue "toutes placettes" ou "tous
    // diagnostics" indépendante à construire.
    PARCELLES("Parcelles", Icons.Filled.Park, isImplemented = true),
    PLACETTES("Placettes", Icons.Filled.Map, isImplemented = true),
    ARBRES("Arbres", Icons.Filled.Category, isImplemented = true),
    OBSERVATIONS("Observations", Icons.Filled.Biotech),
    MESURES("Mesures", Icons.Filled.Straighten, isImplemented = true),
    CALCULS("Calculs", Icons.Filled.Calculate, isImplemented = true),
    PREUVES("Preuves", Icons.Filled.PhotoLibrary),
    ESSENCES("Essences", Icons.Filled.Hub, isImplemented = true),
    STATIONS("Stations", Icons.Filled.Science),
    DIAGNOSTICS("Diagnostics", Icons.Filled.BarChart, isImplemented = true),
    PROJECTS("Projets", Icons.Filled.AccountTree, isImplemented = true),
    EVENEMENTS("Événements", Icons.Filled.Event),
}
