package com.forestry.counter.presentation.screens.projects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forestry.counter.presentation.screens.common.ComingSoonScreen

/**
 * Détail d'un projet — spec GEOSYLVA-003 §29.11.
 *
 * Onglets : Vue générale, Forêts, Missions, Documents, Carte, Équipe, Historique.
 * Pendant Lot 1, seuls Vue générale et Forêts sont implémentés.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    projectId: String,
    projectName: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val missionAvailable = com.forestry.counter.presentation.navigation.JourneyNavigation.access(null)
        .allows(com.forestry.counter.domain.journey.JourneyFeature.MISSIONS)
    val tabs = ProjectDetailTab.entries.filter { it != ProjectDetailTab.MISSIONS || missionAvailable }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(projectName) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onNavigateBack) {
                        androidx.compose.material3.Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(tab.label) },
                    )
                }
            }
            when (tabs[selectedTab]) {
                ProjectDetailTab.OVERVIEW -> OverviewTab(projectName)
                ProjectDetailTab.FORESTS -> ComingSoonScreen("Gestion des forêts du projet")
                ProjectDetailTab.MISSIONS -> ComingSoonScreen("Missions du projet")
                ProjectDetailTab.DOCUMENTS -> ComingSoonScreen("Documents du projet")
                ProjectDetailTab.MAP -> ComingSoonScreen("Carte du projet")
                ProjectDetailTab.TEAM -> ComingSoonScreen("Équipe du projet")
                ProjectDetailTab.HISTORY -> ComingSoonScreen("Historique du projet")
            }
        }
    }
}

@Composable
private fun OverviewTab(projectName: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
    ) {
        Text(
            projectName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Vue générale du projet. Les détails seront enrichis dans les prochains sprints.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

enum class ProjectDetailTab(val label: String) {
    OVERVIEW("Vue générale"),
    FORESTS("Forêts"),
    MISSIONS("Missions"),
    DOCUMENTS("Documents"),
    MAP("Carte"),
    TEAM("Équipe"),
    HISTORY("Historique"),
}
