package com.forestry.counter.presentation.screens.explorer

import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.ui.platform.testTag
import com.forestry.counter.domain.journey.JourneyFeature
import com.forestry.counter.domain.journey.NavigationAccessDecision
import com.forestry.counter.presentation.navigation.JourneyNavigation
import com.forestry.counter.presentation.screens.home.JourneyActionCard
import com.forestry.counter.presentation.screens.home.JourneyChoiceScreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.forestry.counter.R
import com.forestry.counter.data.preferences.UserPreferencesManager
import com.forestry.counter.presentation.theme.GsShape
import com.forestry.counter.presentation.theme.Space

/** Only connected tools for the current journey. A profession does not grant data access. */
@Composable
fun ExplorerScreen(
    onNavigateToForets: () -> Unit,
    onNavigateToProjects: () -> Unit,
    onCategoryClick: (ExplorerCategory) -> Unit,
    onNavigateToWoodDepots: () -> Unit,
    onChooseProfession: () -> Unit,
    professionCode: String?,
    modifier: Modifier = Modifier,
    onNavigateToWorksites: (() -> Unit)? = null,
) {
    val preferences = LocalContext.current.let { remember(it) { UserPreferencesManager(it) } }
    val backgroundEnabled by preferences.backgroundImageEnabled.collectAsStateWithLifecycle(initialValue = true)
    val backgroundUri by preferences.backgroundImageUri.collectAsStateWithLifecycle(initialValue = null)
    val access = remember(professionCode) { JourneyNavigation.access(professionCode) }
    if (access.profile.journey == null) {
        JourneyChoiceScreen(onChooseProfession, modifier)
        return
    }
    Box(modifier = modifier.fillMaxSize()) {
        if (backgroundEnabled) {
            if (backgroundUri != null) AsyncImage(model = backgroundUri, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Image(painterResource(R.drawable.forest_background), contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        ExplorerContent(access, onNavigateToForets, onNavigateToProjects, onCategoryClick,
            onNavigateToWoodDepots, onChooseProfession, onNavigateToWorksites = onNavigateToWorksites)
    }
}

@Composable
internal fun ExplorerContent(
    access: NavigationAccessDecision,
    onNavigateToForets: () -> Unit,
    onNavigateToProjects: () -> Unit,
    onCategoryClick: (ExplorerCategory) -> Unit,
    onNavigateToWoodDepots: () -> Unit,
    onChooseProfession: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToWorksites: (() -> Unit)? = null,
) {
    // Categories are navigation, not downloaded data: no simulated pull-to-refresh.
    LazyVerticalGrid(
        columns = GridCells.Adaptive(156.dp),
        modifier = modifier.fillMaxSize().testTag("journey_explorer"),
        contentPadding = PaddingValues(Space.screenH),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Surface(color = MaterialTheme.colorScheme.surface, shape = GsShape.lg) {
                Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text(stringResource(R.string.explorer_title), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.journey_explorer_description), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (access.allows(JourneyFeature.WORKSITES) && onNavigateToWorksites != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                JourneyActionCard(Icons.Default.Inventory2, R.string.site_title, R.string.site_empty,
                    onNavigateToWorksites, Modifier.testTag("journey_worksites"), prominent = true)
            }
        }
        if (access.allows(JourneyFeature.WOOD_DEPOTS)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                JourneyActionCard(Icons.Default.Inventory2, R.string.journey_depots_title,
                    R.string.journey_depots_description, onNavigateToWoodDepots,
                    Modifier.testTag("journey_depots"), prominent = true)
            }
        }
        itemsIndexed(JourneyNavigation.categories(access), key = { _, category -> category.name }) { _, category ->
            ExplorerCategoryCard(category, onClick = {
                when (category) {
                    ExplorerCategory.FORETS -> onNavigateToForets()
                    ExplorerCategory.PROJECTS -> onNavigateToProjects()
                    else -> onCategoryClick(category)
                }
            })
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            androidx.compose.material3.TextButton(onClick = onChooseProfession) {
                Text(stringResource(R.string.journey_change_action))
            }
        }
    }
}

@Composable
private fun ExplorerCategoryCard(category: ExplorerCategory, onClick: () -> Unit) {
    Card(onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("explorer_${category.name}"),
        shape = GsShape.lg,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            Icon(category.icon, contentDescription = null)
            Text(stringResource(when (category) {
                ExplorerCategory.FORETS -> R.string.journey_forests
                ExplorerCategory.PARCELLES -> R.string.journey_parcels
                ExplorerCategory.PLACETTES -> R.string.journey_plots
                ExplorerCategory.DIAGNOSTICS -> R.string.journey_diagnostics
                ExplorerCategory.PROJECTS -> R.string.journey_projects
                else -> error("Unconnected Explorer destination: $category")
            }), style = MaterialTheme.typography.titleMedium)
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
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
    ARBRES("Arbres", Icons.Filled.Category),
    OBSERVATIONS("Observations", Icons.Filled.Biotech),
    MESURES("Mesures", Icons.Filled.Straighten),
    CALCULS("Calculs", Icons.Filled.Calculate),
    PREUVES("Preuves", Icons.Filled.PhotoLibrary),
    ESSENCES("Essences", Icons.Filled.Hub, isImplemented = true),
    STATIONS("Stations", Icons.Filled.Science),
    DIAGNOSTICS("Diagnostics", Icons.Filled.BarChart, isImplemented = true),
    PROJECTS("Projets", Icons.Filled.AccountTree, isImplemented = true),
    EVENEMENTS("Événements", Icons.Filled.Event),
}
