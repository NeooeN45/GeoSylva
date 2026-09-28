package com.forestry.counter.presentation.screens.forestry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forestry.counter.ForestryCounterApplication
import com.forestry.counter.domain.model.Foret
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.presentation.screens.common.ComingSoonScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Détail d'une forêt — spec GEOSYLVA-003 §29.5.
 *
 * Onglets : Vue générale, Parcelles, Carte, Documents, Historique.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForestDetailScreen(
    viewModel: ForestDetailViewModel,
    app: ForestryCounterApplication,
    onNavigateBack: () -> Unit,
    onNavigateToParcelles: (String) -> Unit,
    onEditForet: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = ForestDetailTab.entries

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (val s = state) {
                            is ForestDetailUiState.Success -> s.foret.nom
                            else -> "Forêt"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    val s = state
                    if (s is ForestDetailUiState.Success) {
                        IconButton(onClick = { onEditForet(s.foret.foretId) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Modifier")
                        }
                        IconButton(onClick = { viewModel.deleteForet(); onNavigateBack() }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Supprimer")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        when (val s = state) {
            is ForestDetailUiState.Loading -> {
                Column(Modifier.fillMaxSize().padding(innerPadding), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Chargement…", modifier = Modifier.padding(16.dp))
                }
            }
            is ForestDetailUiState.NotFound -> {
                Column(Modifier.fillMaxSize().padding(innerPadding), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Forêt introuvable", modifier = Modifier.padding(16.dp))
                }
            }
            is ForestDetailUiState.Success -> {
                Column(modifier = Modifier.padding(innerPadding)) {
                    ScrollableTabRow(selectedTabIndex = selectedTab) {
                        tabs.forEachIndexed { index, tab ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(tab.label) },
                            )
                        }
                    }
                    when (tabs[selectedTab]) {
                        ForestDetailTab.OVERVIEW -> OverviewTab(s.foret)
                        ForestDetailTab.PARCELLES -> ParcellesTab(
                            foretId = s.foret.foretId,
                            parcelles = s.parcelles,
                            onNavigateToParcelles = onNavigateToParcelles,
                        )
                        ForestDetailTab.MAP -> MapRechercheScreen(
                            parcelleId = "forest_${s.foret.foretId}",
                            tigeRepository = app.tigeRepository,
                            essenceRepository = app.essenceRepository,
                            parcelleRepository = app.parcelleRepository,
                            preferencesManager = app.userPreferences,
                            offlineTileManager = app.offlineTileManager,
                            onNavigateBack = {},
                            showBackButton = false,
                            modifier = Modifier.fillMaxSize(),
                        )
                        ForestDetailTab.DOCUMENTS -> ComingSoonScreen("Documents")
                        ForestDetailTab.HISTORY -> ComingSoonScreen("Historique")
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewTab(foret: Foret) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { InfoCard("Propriétaire", foret.proprietaireNom, Icons.Filled.Person) }
        item { foret.proprietaireEmail?.let { InfoCard("Email", it, Icons.Filled.Description) } }
        item { foret.gestionnaireNom?.let { InfoCard("Gestionnaire", it, Icons.Filled.Person) } }
        item { foret.typeForet?.let { InfoCard("Type de forêt", it, Icons.Filled.Category) } }
        item { foret.objectifGestion?.let { InfoCard("Objectif de gestion", it, Icons.Filled.Forest) } }
        item { foret.departement?.let { InfoCard("Département", it, Icons.Filled.LocationOn) } }
        item { foret.psgNumero?.let { InfoCard("PSG N°", it, Icons.Filled.Badge) } }
        item { foret.remarques?.let { InfoCard("Remarques", it, Icons.Filled.Description) } }
        item { MetadataCard(foret) }
    }
}

@Composable
private fun InfoCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun MetadataCard(foret: Foret) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Métadonnées",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Créée le : ${dateFormat.format(Date(foret.createdAt))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Modifiée le : ${dateFormat.format(Date(foret.updatedAt))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            foret.auteur?.let {
                Text(
                    "Auteur : $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            foret.source?.let {
                Text(
                    "Source : $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Version : ${foret.version}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ParcellesTab(
    foretId: String,
    parcelles: List<Parcelle>,
    onNavigateToParcelles: (String) -> Unit,
) {
    if (parcelles.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Filled.Park,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Text("Aucune parcelle", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            Text(
                "Appuyez sur le bouton + pour créer une parcelle.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(parcelles) { parcelle ->
                Card(
                    onClick = { onNavigateToParcelles(parcelle.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(
                            Icons.Filled.Park,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                parcelle.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                "${parcelle.surfaceHa ?: 0.0} ha",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

enum class ForestDetailTab(val label: String) {
    OVERVIEW("Vue générale"),
    PARCELLES("Parcelles"),
    MAP("Carte"),
    DOCUMENTS("Documents"),
    HISTORY("Historique"),
}
