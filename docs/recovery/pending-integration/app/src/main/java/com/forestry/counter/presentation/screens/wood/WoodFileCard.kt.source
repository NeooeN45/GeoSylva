package com.forestry.counter.presentation.screens.wood

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.forestry.counter.R
import com.forestry.counter.domain.field.*
import com.forestry.counter.domain.wood.RawWoodObservation
import com.forestry.counter.domain.wood.WoodEffectiveSelection
import com.forestry.counter.domain.wood.WoodObjectKind
import com.forestry.counter.presentation.screens.field.*
import java.text.DateFormat
import java.time.ZoneId
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
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
    val removalPalette = fieldQuantityPalette(FieldEntryKind.REMOVAL)
    val writable = !state.busy && !state.pendingRetry && item.archivedAt == null
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.header.name, style = MaterialTheme.typography.headlineSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .35f)) {
                    Text(listOfNotNull(stringResource(if (item.kind == WoodObjectKind.PILE) R.string.wo_pile else R.string.wo_log), item.header.tag).joinToString(" · "),
                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                }
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .35f)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Smartphone, if (known != null) stringResource(R.string.retained_local) else null, Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        if (known == null) Text(stringResource(R.string.retained_local), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (known == null) item.header.declaredSpecies?.let {
                Text(stringResource(R.string.retained_declared_species, it), style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedCard {
            WoodFileAction(Icons.Default.Label, stringResource(R.string.retained_identification), color = MaterialTheme.colorScheme.onSurface) { identity = true }
        }
        if (known == null) WoodFileLinks(onHistory, onJournal)
        OutlinedCard(colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .2f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .15f))) {
            Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                WoodFileHeading(stringResource(R.string.retained_reference), stringResource(R.string.retained_reference_help))
                when (val selection = detail.history.effectiveSelection) {
                    WoodEffectiveSelection.None -> Text(stringResource(R.string.wo_no_reference))
                    is WoodEffectiveSelection.Superseded -> Text(stringResource(R.string.wo_reference_corrected))
                    is WoodEffectiveSelection.Current -> {
                        Calculation(selection.record.observation, compactPresentation = true)
                        selection.record.observation.observedTime?.let { date ->
                            Text(stringResource(R.string.wo_observed, WoodManualForm.DATE_FORMAT.format(date.instant.atZone(ZoneId.of(date.zoneId)))) + " · " + date.zoneId,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = .15f))
                WoodFileAction(Icons.Default.Straighten, stringResource(R.string.retained_measurements_method)) { reference = true }
            }
        }
        Card(modifier = Modifier.testTag("wood_stock_card"), colors = CardDefaults.cardColors(
            containerColor = if (missingBase) missingPalette.surface else missingPalette.stockSurface,
            contentColor = if (missingBase) missingPalette.ink else missingPalette.stockInk)) {
            Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                WoodFileHeading(stringResource(R.string.field_form_stock_reference), if (missingBase) stringResource(R.string.retained_stock_missing_help) else null)
                when {
                    state.stockError != null -> FieldErrorCard(state.stockError)
                    known != null -> {
                        Text("${stockNumber(known.reading.expectedNow)} ${unitLabel(known.reading.key.unit)}", style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.testTag("wood_stock_expected"))
                        Text(stringResource(R.string.field_form_stock_at, stockDate(known.reading.asOfMillis)), style = MaterialTheme.typography.bodySmall)
                        if (known.reading.control == null) Text(stringResource(R.string.wood_presentation_no_control), style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ onStockEntry?.invoke(FieldEntryKind.REMOVAL) }, enabled = writable && state.stockWritable && onStockEntry != null,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("wood_stock_remove"), shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = removalPalette.surface, contentColor = removalPalette.ink),
                                border = BorderStroke(1.dp, removalPalette.ink)) { Text(stringResource(R.string.retained_remove_compact)) }
                            OutlinedButton({ onStockEntry?.invoke(FieldEntryKind.REMAINING) }, enabled = writable && state.stockWritable && onStockEntry != null,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("wood_stock_control"), shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = missingPalette.surface, contentColor = missingPalette.ink),
                                border = BorderStroke(1.dp, missingPalette.ink)) { Text(stringResource(R.string.retained_control_compact)) }
                        }
                        HorizontalDivider(color = missingPalette.stockOutline)
                        WoodFileAction(Icons.Default.Layers, stringResource(R.string.retained_stock_followup), color = missingPalette.stockInk) { followup = true }
                    }
                    missingBase -> {
                        Text(stringResource(R.string.retained_stock_missing), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.wood_presentation_no_initial_stock), style = MaterialTheme.typography.bodySmall)
                        TextButton({ onStockEntry?.invoke(FieldEntryKind.INITIAL_STOCK) }, enabled = writable && state.stockWritable && onStockEntry != null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("wood_initial_stock")) { Text(stringResource(R.string.retained_initial_stock)) }
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
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.header.name)
            listOfNotNull(item.header.tag, item.header.declaredSpecies, item.header.declaredQuality, item.header.declaredOrigin, item.header.description).forEach { Text(it) }
        } }, confirmButton = { TextButton({ identity = false }) { Text(stringResource(R.string.retained_close)) } },
        dismissButton = { if (item.archivedAt == null) TextButton({ identity = false; onIdentity() }, enabled = writable) { Text(stringResource(R.string.wo_edit_identity)) } })
    if (reference) AlertDialog(onDismissRequest = { reference = false }, title = { Text(stringResource(R.string.retained_measurements_method)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { when (val selection = detail.history.effectiveSelection) {
            is WoodEffectiveSelection.Current -> {
                Calculation(selection.record.observation)
                WoodReferenceDates(selection.record.observation)
                for ((label, value) in rawValues(selection.record.observation.measurement)) {
                    Text(stringResource(label) + " : " + (value ?: stringResource(R.string.wo_no_value)))
                }
            }
            else -> Text(stringResource(R.string.wo_no_reference))
        } } }, confirmButton = { TextButton({ reference = false; onHistory() }) { Text(stringResource(R.string.retained_surveys)) } },
        dismissButton = { TextButton({ reference = false }) { Text(stringResource(R.string.retained_close)) } })
    if (followup && state.stock != null) AlertDialog(onDismissRequest = { followup = false }, title = { Text(stringResource(R.string.retained_stock_followup)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            FieldStockSummary(state.stock, { onSelectLedger(it) }, { onSelectLedger(null) }, !state.busy)
        } },
        confirmButton = { TextButton({ followup = false }) { Text(stringResource(R.string.retained_close)) } })
}

@Composable private fun WoodFileHeading(title: String, help: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        help?.let { RetainedHelp(title, it) }
    }
}

@Composable private fun WoodFileAction(icon: ImageVector, title: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary, action: () -> Unit,
) {
    TextButton(action, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), contentPadding = PaddingValues(vertical = 8.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = color), shape = MaterialTheme.shapes.small) {
        Icon(icon, null)
        Spacer(Modifier.width(12.dp))
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp))
    }
}

@Composable private fun WoodReferenceDates(raw: RawWoodObservation) {
    val locale = LocalConfiguration.current.locales[0]
    val date = raw.observedTime
    Text(if (date == null) stringResource(R.string.wo_date_unknown)
        else stringResource(R.string.wo_observed, WoodManualForm.DATE_FORMAT.format(date.instant.atZone(ZoneId.of(date.zoneId)))) + " · " + date.zoneId,
        style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.wo_recorded, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).format(Date.from(raw.recordedAt))),
        style = MaterialTheme.typography.bodySmall)
}

@Composable private fun WoodFileLinks(onHistory: () -> Unit, onJournal: (() -> Unit)?) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onHistory, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.retained_surveys)) }
        if (onJournal != null) OutlinedButton(onJournal, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.field_title)) }
    }
}
