package com.forestry.counter.presentation.screens.forestry

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forestry.counter.R
import com.forestry.counter.domain.model.Group
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.repository.GroupRepository
import com.forestry.counter.domain.repository.ParcelleRepository
import com.forestry.counter.domain.repository.PlacetteRepository
import com.forestry.counter.presentation.components.CompactPageHeader
import com.forestry.counter.presentation.theme.Elevation
import com.forestry.counter.presentation.theme.GsShape
import com.forestry.counter.presentation.theme.Space
import com.forestry.counter.presentation.theme.Touch

/**
 * Choix de la zone à afficher avant l'ouverture de la carte en mode
 * Recherche : toutes les forêts, une forêt, une parcelle ou une placette
 * précise. La carte s'ouvre ensuite déjà zoomée sur les tiges de la zone
 * choisie (comportement déjà en place dans `MapRenderEffects`).
 */
@Composable
fun RechercheScopeSelectorScreen(
    groupRepository: GroupRepository,
    parcelleRepository: ParcelleRepository,
    placetteRepository: PlacetteRepository,
    onSelectScope: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val groupsFlow = remember(groupRepository) { groupRepository.getAllGroups() }
    val groups by groupsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    // Un seul choix évident (une seule forêt enregistrée) : on saute l'écran
    // de sélection et on ouvre directement la carte dessus.
    androidx.compose.runtime.LaunchedEffect(groups) {
        if (groups.size == 1) onSelectScope("forest_${groups[0].id}")
    }
    if (groups.size == 1) return

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.forest_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Column(modifier = Modifier.fillMaxSize()) {
            CompactPageHeader(title = stringResource(R.string.recherche_scope_title), onBack = onBack)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                item {
                    ScopeRow(
                        icon = Icons.Default.Map,
                        label = stringResource(R.string.recherche_scope_all),
                        indent = 0,
                        onClick = { onSelectScope("all") },
                    )
                }
                items(groups, key = { it.id }) { group ->
                    ForestScopeItem(
                        group = group,
                        parcelleRepository = parcelleRepository,
                        placetteRepository = placetteRepository,
                        onSelectScope = onSelectScope,
                    )
                }
                if (groups.isEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                            shape = GsShape.md,
                        ) {
                            Text(
                                stringResource(R.string.recherche_scope_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(Space.md),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ForestScopeItem(
    group: Group,
    parcelleRepository: ParcelleRepository,
    placetteRepository: PlacetteRepository,
    onSelectScope: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        ScopeRow(
            icon = Icons.Default.Forest,
            label = group.name,
            indent = 0,
            onClick = { onSelectScope("forest_${group.id}") },
            trailingIcon = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            onTrailingClick = { expanded = !expanded },
        )
        if (expanded) {
            val parcellesFlow = remember(parcelleRepository, group.id) { parcelleRepository.getParcellesByForest(group.id) }
            val parcelles by parcellesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
            parcelles.forEach { parcelle ->
                ParcelleScopeItem(
                    parcelle = parcelle,
                    placetteRepository = placetteRepository,
                    onSelectScope = onSelectScope,
                )
            }
        }
    }
}

@Composable
private fun ParcelleScopeItem(
    parcelle: Parcelle,
    placetteRepository: PlacetteRepository,
    onSelectScope: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        ScopeRow(
            icon = Icons.Default.GridOn,
            label = parcelle.name,
            indent = 1,
            onClick = { onSelectScope(parcelle.id) },
            trailingIcon = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            onTrailingClick = { expanded = !expanded },
        )
        if (expanded) {
            val placettesFlow = remember(placetteRepository, parcelle.id) { placetteRepository.getPlacettesByParcelle(parcelle.id) }
            val placettes by placettesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
            placettes.forEach { placette ->
                ScopeRow(
                    icon = Icons.Default.MyLocation,
                    label = placette.name ?: stringResource(R.string.recherche_scope_placette_unnamed),
                    indent = 2,
                    onClick = { onSelectScope("placette_${placette.id}") },
                )
            }
        }
    }
}

@Composable
private fun ScopeRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    indent: Int,
    onClick: () -> Unit,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (Space.lg * indent))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        shape = GsShape.md,
        shadowElevation = Elevation.card,
    ) {
        Row(
            modifier = Modifier.padding(Space.sm).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier.size(Touch.min),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (trailingIcon != null && onTrailingClick != null) {
                Box(
                    modifier = Modifier
                        .size(Touch.min)
                        .clickable(onClick = onTrailingClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(trailingIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
