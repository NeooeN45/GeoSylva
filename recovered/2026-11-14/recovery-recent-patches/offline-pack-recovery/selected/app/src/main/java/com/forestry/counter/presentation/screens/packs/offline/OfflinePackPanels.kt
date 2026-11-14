package com.forestry.counter.presentation.screens.packs.offline

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forestry.counter.R
import com.forestry.counter.data.local.entity.OfflineMapPackEntity
import com.forestry.counter.data.packs.offline.*
import com.forestry.counter.domain.usecase.maps.MapDataScope
import com.quintessences.geosylva.cartography.mapbox.offline.*
import com.quintessences.geosylva.cartography.ui.offline.OfflinePackPreparation
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** The host supplies the real visible-footprint snapshot; panning later cannot change this plan. */
data class OfflineViewPackDraft(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val geometryJson: String,
    val minZoom: Int,
    val maxZoom: Int,
    val pixelRatio: Float,
    val styleUri: String,
    val reliefSource: String? = "mapbox://mapbox.mapbox-terrain-dem-v1",
    val initiallyRelief: Boolean = false,
    val territories: Set<String> = emptySet(),
    val datasetCandidates: List<OfflineDataset> = emptyList(),
    val initiallySelectedDatasetIds: Set<String> = emptySet(),
    val replacesPackId: String? = null,
)

@Composable
fun OfflinePackDialogs(controller: OfflineMapPackController) {
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    state.plan?.let { plan -> OfflinePackEstimateSheet(plan, state.wifiOnly, state.planning,
        onWifiOnly = controller::wifiOnly, onConfirm = controller::confirm, onModify = controller::dismissPlan) }
    state.versionChoice?.let { choice -> AlertDialog(onDismissRequest = controller::dismissVersionChoice,
        title = { Text(stringResource(R.string.offline_version_change_title)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val target = state.packs.firstOrNull { it.id == choice.targetId }
            target?.let { Text(stringResource(R.string.offline_version_number, it.versionOrdinal), style = MaterialTheme.typography.titleMedium) }
            Text(stringResource(R.string.offline_version_change_effects))
            Text(stringResource(R.string.offline_source_version_unknown), style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { Button(controller::confirmVersionChoice, enabled = !state.planning) { Text(stringResource(R.string.offline_use_definition)) } },
        dismissButton = { TextButton(controller::dismissVersionChoice) { Text(stringResource(R.string.offline_cancel)) } }) }
    state.removal.takeIf { it.isNotEmpty() }?.let { removals -> OfflinePackRemovalSheet(removals, state.packs,
        onConfirm = controller::removeConfirmed, onDismiss = controller::dismissRemoval) }

}

@Composable
internal fun offlineStatusLabel(status: OfflinePackStatus): String = stringResource(when (status) {
    OfflinePackStatus.READY -> R.string.offline_ready
    OfflinePackStatus.PAUSED -> R.string.offline_paused
    OfflinePackStatus.WAITING_WIFI -> R.string.offline_waiting_wifi
    OfflinePackStatus.DOWNLOADING -> R.string.offline_downloading
    OfflinePackStatus.VERIFYING -> R.string.offline_verifying
    OfflinePackStatus.INCOMPLETE -> R.string.offline_incomplete
    OfflinePackStatus.FAILED -> R.string.offline_failed
    OfflinePackStatus.RETAINED -> R.string.offline_retained
})
