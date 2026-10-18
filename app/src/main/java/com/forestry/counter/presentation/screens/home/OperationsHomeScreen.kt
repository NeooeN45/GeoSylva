package com.forestry.counter.presentation.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.forestry.counter.R
import com.forestry.counter.domain.journey.ProfessionProfile
import com.forestry.counter.presentation.theme.GsShape
import com.forestry.counter.presentation.theme.Space

/** Operational entry points only: no invented stocks, amounts, pending tasks or sync status. */
@Composable
fun OperationsHomeScreen(
    profile: ProfessionProfile,
    onDepots: () -> Unit,
    onMap: () -> Unit,
    onPacks: () -> Unit,
    onChangeProfession: () -> Unit,
    modifier: Modifier = Modifier,
    onWorksites: (() -> Unit)? = null,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.testTag("operations_home"),
            contentPadding = PaddingValues(Space.screenH),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text(stringResource(when (profile) {
                        ProfessionProfile.WORKSITE_SUPERVISOR -> R.string.journey_supervisor_label
                        ProfessionProfile.TIMBER_TRADE -> R.string.journey_trade_label
                        else -> R.string.journey_contractor_label
                    }), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.journey_operations_title), style = MaterialTheme.typography.headlineLarge)
                    Text(stringResource(R.string.journey_operations_intro), style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (onWorksites != null) item {
                JourneyActionCard(Icons.Default.Inventory2, R.string.site_title, R.string.site_empty,
                    onWorksites, Modifier.testTag("journey_worksites"), prominent = true)
            }
            item {
                JourneyActionCard(Icons.Default.Inventory2, R.string.journey_depots_title,
                    R.string.journey_depots_description, onDepots, Modifier.testTag("journey_depots"), prominent = true)
            }
            item {
                JourneyActionCard(Icons.Default.Map, R.string.journey_map_title,
                    R.string.journey_map_description, onMap, Modifier.testTag("journey_map"))
            }
            item {
                JourneyActionCard(Icons.Default.OfflinePin, R.string.journey_packs_title,
                    R.string.journey_packs_description, onPacks, Modifier.testTag("journey_packs"))
            }
            item { TextButton(onClick = onChangeProfession) { Text(stringResource(R.string.journey_change_action)) } }
        }
    }
}

@Composable
fun JourneyActionCard(
    icon: ImageVector,
    titleRes: Int,
    descriptionRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = GsShape.lg,
        colors = CardDefaults.cardColors(containerColor = if (prominent)
            MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null)
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(descriptionRes), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun JourneyChoiceScreen(onChooseProfession: () -> Unit, modifier: Modifier = Modifier) {
    JourneyMessage(R.string.journey_choose_title, R.string.journey_choose_description,
        R.string.journey_choose_action, onChooseProfession, modifier)
}

@Composable
fun JourneyUnavailableScreen(onExplorer: () -> Unit, modifier: Modifier = Modifier) {
    JourneyMessage(R.string.journey_unavailable_title, R.string.journey_unavailable_description,
        R.string.journey_back_explorer, onExplorer, modifier)
}

@Composable
private fun JourneyMessage(title: Int, description: Int, action: Int, onAction: () -> Unit, modifier: Modifier) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(contentPadding = PaddingValues(Space.screenH), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            item { Text(stringResource(title), style = MaterialTheme.typography.headlineMedium) }
            item { Text(stringResource(description), style = MaterialTheme.typography.bodyLarge) }
            item { androidx.compose.material3.Button(onClick = onAction) { Text(stringResource(action)) } }
        }
    }
}
