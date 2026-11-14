package com.forestry.counter.presentation.screens.packs.offline

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forestry.counter.R
import com.forestry.counter.data.packs.offline.OfflinePackPlan

/** Shared bounded confirmation shell; callers own the immutable review and the fixed actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OfflinePackConfirmationSheet(onDismiss: () -> Unit, tag: String,
    content: @Composable ColumnScope.() -> Unit) {
    val maximum = LocalConfiguration.current.screenHeightDp.dp * 0.52f
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null, scrimColor = Color.Transparent, contentWindowInsets = { WindowInsets(0, 0, 0, 0) }) {
        Column(Modifier.fillMaxWidth().heightIn(max = maximum).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag(tag), content = content)
    }
}

/** Native estimates stay separate from unavailable style/temporary-allocation estimates. */
@Composable
internal fun OfflinePackEstimateSheet(plan: OfflinePackPlan, wifiOnly: Boolean, busy: Boolean,
    onWifiOnly: (Boolean) -> Unit, onConfirm: () -> Unit, onModify: () -> Unit) {
    val context = LocalContext.current
    val transfer = runCatching { Math.addExact(plan.tiles.tileTransferBytes, plan.datasetTransferBytes) }.getOrNull()
    val minimum = runCatching { plan.knownAdditionalBytes }.getOrNull()
    val fits = runCatching { plan.knownMinimumFits }.getOrDefault(false)
    fun bytes(value: Long?) = value?.let { Formatter.formatFileSize(context, it) }
    OfflinePackConfirmationSheet(onModify, "offline_estimate_confirmation_sheet") {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(plan.input.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.offline_zooms, plan.input.definition.minZoom, plan.input.definition.maxZoom), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.offline_estimate_unknown_parts), style = MaterialTheme.typography.bodySmall)
            EstimateLine(stringResource(R.string.offline_estimate_transfer_label), bytes(transfer) ?: stringResource(R.string.offline_unknown))
            EstimateLine(stringResource(R.string.offline_estimate_minimum_label), bytes(minimum) ?: stringResource(R.string.offline_unknown))
            EstimateLine(stringResource(R.string.offline_storage_free_label), bytes(plan.availableBytes) ?: stringResource(R.string.offline_unknown))
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.offline_wifi_only), Modifier.weight(1f))
                Switch(wifiOnly, onWifiOnly)
            }
            Text(stringResource(R.string.offline_estimate_margin, plan.tiles.errorMargin.toString()), style = MaterialTheme.typography.bodySmall)
            if (!fits) Text(stringResource(R.string.offline_storage_insufficient), color = MaterialTheme.colorScheme.error)
        }
        Button(onConfirm, enabled = fits && !busy, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            .heightIn(min = 48.dp).testTag("offline_estimate_download")) { Text(stringResource(R.string.offline_pack_download)) }
        TextButton(onModify, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("offline_estimate_modify")) { Text(stringResource(R.string.offline_modify)) }
    }
}

@Composable
private fun EstimateLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
    HorizontalDivider()
}
