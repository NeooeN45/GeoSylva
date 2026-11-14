package com.forestry.counter.presentation.screens.wood

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.forestry.counter.R
import com.forestry.counter.domain.field.*
import com.forestry.counter.domain.wood.WoodEffectiveSelection
import com.forestry.counter.domain.wood.WoodObjectKind
import com.forestry.counter.presentation.screens.field.*

@Composable internal fun WoodFileCard(
    state: WoodObjectsState, onIdentity: () -> Unit,
    onJournal: (() -> Unit)?, onStockEntry: ((FieldEntryKind) -> Unit)?,
    onHistory: () -> Unit, onSelectLedger: (FieldStockLedgerKey?) -> Unit,
) {
    val detail = state.detail ?: return
    val item = detail.item
    var identity by remember(item.id) { mutableStateOf(false) }
    var reference by remember(item.id) { mutableStateOf(false) }
    var followup by remember(item.id) { mutableStateOf(false) }
    val known = state.stock as? FieldStockResult.Known
    val missingBase = (state.stock as? FieldStockResult.Unavailable)?.reason == "BASE_REQUIRED"
    val missingPalette = fieldQuantityPalette(FieldEntryKind.REMAINING)
    val writable = !state.busy && !state.pendingRetry && item.archivedAt == null
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(item.header.name, style = MaterialTheme.typography.headlineSmall)
        Text(listOfNotNull(stringResource(if (item.kind == WoodObjectKind.PILE) R.string.wo_pile else R.string.wo_log), item.header.tag).joinToString(" · "),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (known == null) {
            item.header.declaredSpecies?.let { Text(stringResource(R.string.retained_declared_species, it)) }
            Text(stringResource(R.string.retained_local), style = MaterialTheme.typography.labelMedium)
        }
        RetainedActionRow(Icons.Default.Label, stringResource(R.string.retained_identification), compact = true) { identity = true }
        if (known == null) WoodFileLinks(onHistory, onJournal)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RetainedHeading(stringResource(R.string.retained_reference), stringResource(R.string.retained_reference_help))
                when (val selection = detail.history.effectiveSelection) {
                    WoodEffectiveSelection.None -> Text(stringResource(R.string.wo_no_reference))
                    is WoodEffectiveSelection.Superseded -> Text(stringResource(R.string.wo_reference_corrected))
                    is WoodEffectiveSelection.Current -> Calculation(selection.record.observation, compactPresentation = true)
                }
                RetainedActionRow(Icons.Default.Straighten, stringResource(R.string.retained_measurements_method), compact = true) { reference = true }
            }
        }
        Card(modifier = Modifier.testTag("wood_stock_card"), colors = CardDefaults.cardColors(
            containerColor = if (missingBase) missingPalette.surface else missingPalette.stockSurface,
            contentColor = if (missingBase) missingPalette.ink else missingPalette.stockInk)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RetainedHeading(stringResource(R.string.field_form_stock_reference))
                when {
                    state.stockError != null -> FieldErrorCard(state.stockError)
                    known != null -> {
                        Text("${stockNumber(known.reading.expectedNow)} ${unitLabel(known.reading.key.unit)}", style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.testTag("wood_stock_expected"))
                        Text(stringResource(R.string.field_stock_theoretical_at, stockDate(known.reading.asOfMillis)), style = MaterialTheme.typography.bodySmall)
                        if (known.reading.control == null) Text(stringResource(R.string.field_stock_no_control), style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ onStockEntry?.invoke(FieldEntryKind.REMOVAL) }, enabled = writable && state.stockWritable && onStockEntry != null,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("wood_stock_remove")) { Text(stringResource(R.string.retained_remove_compact)) }
                            OutlinedButton({ onStockEntry?.invoke(FieldEntryKind.REMAINING) }, enabled = writable && state.stockWritable && onStockEntry != null,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("wood_stock_control")) { Text(stringResource(R.string.retained_control_compact)) }
                        }
                        RetainedActionRow(Icons.Default.Layers, stringResource(R.string.retained_stock_followup), compact = true) { followup = true }
                    }
                    missingBase -> {
                        Text(stringResource(R.string.retained_stock_missing), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.retained_stock_missing_help), style = MaterialTheme.typography.bodySmall)
                        TextButton({ onStockEntry?.invoke(FieldEntryKind.INITIAL_STOCK) }, enabled = writable && state.stockWritable && onStockEntry != null,
                            modifier = Modifier.testTag("wood_initial_stock")) { Text(stringResource(R.string.retained_initial_stock)) }
                    }
                    state.stock != null -> FieldStockSummary(state.stock, { onSelectLedger(it) }, { onSelectLedger(null) }, !state.busy)
                    else -> Text(stringResource(R.string.field_balance_unavailable))
                }
            }
        }
        if (known != null) WoodFileLinks(onHistory, onJournal)
        if (item.archivedAt != null) Text(stringResource(R.string.wo_read_only))
    }
    if (identity) AlertDialog(onDismissRequest = { identity = false }, title = { Text(stringResource(R.string.retained_identification)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.header.name)
            listOfNotNull(item.header.tag, item.header.declaredSpecies, item.header.declaredQuality, item.header.declaredOrigin, item.header.description).forEach { Text(it) }
        } }, confirmButton = { TextButton({ identity = false }) { Text(stringResource(R.string.retained_close)) } },
        dismissButton = { if (item.archivedAt == null) TextButton({ identity = false; onIdentity() }, enabled = writable) { Text(stringResource(R.string.wo_edit_identity)) } })
    if (reference) AlertDialog(onDismissRequest = { reference = false }, title = { Text(stringResource(R.string.retained_measurements_method)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { when (val selection = detail.history.effectiveSelection) {
            is WoodEffectiveSelection.Current -> {
                Calculation(selection.record.observation)
                for ((label, value) in rawValues(selection.record.observation.measurement)) {
                    Text(stringResource(label) + " : " + (value ?: stringResource(R.string.wo_no_value)))
                }
            }
            else -> Text(stringResource(R.string.wo_no_reference))
        } } }, confirmButton = { TextButton({ reference = false; onHistory() }) { Text(stringResource(R.string.retained_surveys)) } },
        dismissButton = { TextButton({ reference = false }) { Text(stringResource(R.string.retained_close)) } })
    if (followup && state.stock != null) AlertDialog(onDismissRequest = { followup = false }, title = { Text(stringResource(R.string.retained_stock_followup)) },
        text = { FieldStockSummary(state.stock, { onSelectLedger(it) }, { onSelectLedger(null) }, !state.busy) },
        confirmButton = { TextButton({ followup = false }) { Text(stringResource(R.string.retained_close)) } })
}

@Composable private fun WoodFileLinks(onHistory: () -> Unit, onJournal: (() -> Unit)?) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onHistory, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.retained_surveys)) }
        if (onJournal != null) OutlinedButton(onJournal, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.field_title)) }
    }
}
