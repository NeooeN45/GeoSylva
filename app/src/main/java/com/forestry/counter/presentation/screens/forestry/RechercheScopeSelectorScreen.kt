package com.forestry.counter.presentation.screens.forestry

import androidx.compose.foundation.Image
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
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.repository.GroupRepository
import com.forestry.counter.domain.repository.ParcelleRepository
import com.forestry.counter.domain.repository.PlacetteRepository
import com.forestry.counter.presentation.components.CompactPageHeader
import com.forestry.counter.presentation.theme.Elevation
import com.forestry.counter.presentation.theme.GsShape
import com.forestry.counter.presentation.theme.Space
import com.forestry.counter.presentation.theme.Touch

import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.forestry.counter.data.cartography.CartographicScopeAuthority
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.domain.repository.ForetRepository
import com.forestry.counter.domain.usecase.maps.ForestScopeChoice
import com.forestry.counter.domain.usecase.maps.MapDataScope
import com.forestry.counter.domain.usecase.maps.belongsToForestScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Authorised canonical forests and historical groups share one display projection, never one stored entity. */
@Composable
fun RechercheScopeSelectorScreen(
    groupRepository: GroupRepository,
    foretRepository: ForetRepository,
    parcelleRepository: ParcelleRepository,
    placetteRepository: PlacetteRepository,
    businessReadAccess: BusinessReadAccess,
    scopeAuthority: CartographicScopeAuthority,
    onSelectScope: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val catalog=remember(foretRepository,groupRepository,businessReadAccess,scopeAuthority) {
        RechercheScopeCatalog(foretRepository,groupRepository,businessReadAccess,scopeAuthority)
    }
    val captured by catalog.states.collectAsStateWithLifecycle(initialValue=RechercheScopeCatalogState())
    val ready=captured.takeIf { it.loaded && it.access?.let(businessReadAccess::isCurrent) == true }
    val work=rememberCoroutineScope()
    val selectedCallback by rememberUpdatedState(onSelectScope)
    var pending by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    fun select(target: RechercheScopeTarget) {
        val displayed=ready ?: return
        if (pending) return
        pending=true;failed=false
        work.launch {
            try {
                val route=catalog.resolve(displayed,target)
                ensureActive()
                if (route != null) selectedCallback(route) else failed=true
            } finally { pending=false }
        }
    }
    // Even one forest requires an explicit choice, so Back returns to this selector without reopening the map.
    Box(modifier=modifier.fillMaxSize()) {
        Image(painter=painterResource(id=R.drawable.forest_background),contentDescription=null,
            modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        Column(Modifier.fillMaxSize()) {
            CompactPageHeader(title=stringResource(R.string.recherche_scope_title),onBack=onBack)
            LazyColumn(Modifier.fillMaxSize(),contentPadding=androidx.compose.foundation.layout.PaddingValues(Space.md),
                verticalArrangement=Arrangement.spacedBy(Space.sm)) {
                if (ready == null) item {
                    Text(stringResource(if (captured.failed) R.string.recherche_scope_unavailable else R.string.recherche_scope_loading),
                        Modifier.padding(Space.md),color=MaterialTheme.colorScheme.onSurface)
                } else {
                    item { ScopeRow(icon=Icons.Default.Map,label=stringResource(R.string.recherche_scope_all),indent=0,
                        enabled=!pending,onClick={select(RechercheScopeTarget(MapDataScope.AllForests))}) }
                    items(ready.forests,key={it.id}) { forest ->
                        ForestScopeItem(forest,requireNotNull(ready.access),businessReadAccess,parcelleRepository,placetteRepository,
                            !pending,::select)
                    }
                    if (ready.forests.isEmpty()) item {
                        Surface(color=MaterialTheme.colorScheme.background.copy(alpha=0.55f),shape=GsShape.md) {
                            Text(stringResource(R.string.recherche_scope_empty),style=MaterialTheme.typography.bodyMedium,
                                color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(Space.md))
                        }
                    }
                    if (failed) item { Text(stringResource(R.string.recherche_scope_unavailable),Modifier.padding(Space.md)) }
                }
            }
        }
    }
}

@Composable
private fun ForestScopeItem(
    forest: ForestScopeChoice,
    access: BusinessReadAccess.AccessSnapshot,
    reads: BusinessReadAccess,
    parcelleRepository: ParcelleRepository,
    placetteRepository: PlacetteRepository,
    enabled: Boolean,
    onSelect: (RechercheScopeTarget) -> Unit,
) {
    var expanded by remember(forest.id,forest.kind,access) { mutableStateOf(false) }
    Column {
        ScopeRow(icon=Icons.Default.Forest,label=forest.name,indent=0,enabled=enabled && reads.isCurrent(access),
            onClick={onSelect(RechercheScopeTarget(MapDataScope.Forest(forest.id),forest=forest))},
            trailingIcon=if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,onTrailingClick={expanded=!expanded})
        if (expanded) key(access) {
            val parcelsFlow=remember(parcelleRepository,forest.id) { parcelleRepository.getParcellesByForest(forest.id) }
            val parcels by parcelsFlow.collectAsStateWithLifecycle(initialValue=emptyList())
            if (reads.isCurrent(access)) parcels.filter { it.deletedAt == null && it.belongsToForestScope(forest.id) }.forEach { parcel ->
                ParcelleScopeItem(forest,parcel,access,reads,placetteRepository,enabled,onSelect)
            }
        }
    }
}

@Composable
private fun ParcelleScopeItem(
    forest: ForestScopeChoice,
    parcelle: Parcelle,
    access: BusinessReadAccess.AccessSnapshot,
    reads: BusinessReadAccess,
    placetteRepository: PlacetteRepository,
    enabled: Boolean,
    onSelect: (RechercheScopeTarget) -> Unit,
) {
    var expanded by remember(parcelle.id,access) { mutableStateOf(false) }
    Column {
        ScopeRow(icon=Icons.Default.GridOn,label=parcelle.name,indent=1,enabled=enabled && reads.isCurrent(access),
            onClick={onSelect(RechercheScopeTarget(MapDataScope.Parcel(parcelle.id),forest,parcelle))},
            trailingIcon=if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,onTrailingClick={expanded=!expanded})
        if (expanded) key(access) {
            val plotsFlow=remember(placetteRepository,parcelle.id) { placetteRepository.getPlacettesByParcelle(parcelle.id) }
            val plots by plotsFlow.collectAsStateWithLifecycle(initialValue=emptyList())
            if (reads.isCurrent(access)) plots.filter { it.deletedAt == null && it.parcelleId == parcelle.id }.forEach { plot ->
                ScopeRow(icon=Icons.Default.MyLocation,label=plot.name ?: stringResource(R.string.recherche_scope_placette_unnamed),indent=2,
                    enabled=enabled,onClick={onSelect(RechercheScopeTarget(MapDataScope.Plot(plot.id),forest,parcelle,plot))})
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
    enabled: Boolean = true,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (Space.lg * indent))
            .clickable(enabled = enabled,onClick = onClick),
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
                        .clickable(enabled = enabled,onClick = onTrailingClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(trailingIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
