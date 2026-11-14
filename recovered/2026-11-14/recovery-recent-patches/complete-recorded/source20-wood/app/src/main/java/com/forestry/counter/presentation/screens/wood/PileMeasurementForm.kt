package com.forestry.counter.presentation.screens.wood

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.forestry.counter.R
import com.forestry.counter.domain.wood.*
import com.forestry.counter.presentation.screens.field.RetainedActionRow
import com.forestry.counter.presentation.screens.field.RetainedHeading
import com.forestry.counter.presentation.screens.field.RetainedHelp
import java.time.Instant
import java.util.UUID

/** Transient calculator input only. These identities are never persisted or selected. */
internal fun pilePreview(form: WoodManualForm): RawWoodObservation {
    fun dimension(value: String) = value.takeIf { it.isNotEmpty() }?.let { RawWoodDimension(it, "m") }
    return RawWoodObservation(UUID(0, 1), UUID(0, 2), Instant.EPOCH,
        RawWoodMeasurement.Pile(dimension(form.length), dimension(form.depth), dimension(form.height),
            RawWoodMeasurement.Pile.RECTANGULAR_ENVELOPE.takeIf { form.shape },
            form.protocol.takeIf { it.isNotEmpty() }, dimension(form.pieceLength)))
}

@Composable internal fun PileMeasurementForm(form: WoodManualForm, enabled: Boolean, change: (WoodManualForm.() -> WoodManualForm) -> Unit) {
    var panel by remember { mutableStateOf<String?>(null) }
    val preview = remember(form.length, form.depth, form.height, form.shape, form.protocol, form.pieceLength) { pilePreview(form) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(form.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            RetainedHelp(stringResource(R.string.retained_pile_survey), stringResource(R.string.wo_measure_intro),
                Modifier.testTag("wood_pile_intro_help"))
        }
        RetainedHeading(stringResource(R.string.retained_dimensions))
        WoodPileInput(form.length, R.string.wo_length, "wood_length", enabled, numeric = true) { change { copy(length = it) } }
        WoodPileInput(form.depth, R.string.wo_depth, "wood_depth", enabled, numeric = true) { change { copy(depth = it) } }
        WoodPileInput(form.height, R.string.wo_height, "wood_height", enabled, numeric = true) { change { copy(height = it) } }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth().testTag("wood_pile_preview")) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.retained_preview), style = MaterialTheme.typography.labelMedium)
                Calculation(preview, compactPresentation = true)
            }
        }
        HorizontalDivider()
        RetainedActionRow(Icons.Default.Straighten, stringResource(R.string.retained_method),
            stringResource(if (form.shape && form.protocol.isNotBlank()) R.string.retained_method_confirmed else R.string.retained_method_missing),
            enabled, Modifier.testTag("wood_method_panel"), compact = true) { panel = "method" }
        RetainedActionRow(Icons.Default.Event, stringResource(R.string.retained_date_part),
            form.observedDate.ifBlank { stringResource(R.string.retained_no_date) }, enabled,
            Modifier.testTag("wood_date_panel"), compact = true) { panel = "date" }
        RetainedActionRow(Icons.Default.Description, stringResource(R.string.retained_details), stringResource(R.string.retained_optional),
            enabled, Modifier.testTag("wood_details_panel"), compact = true) { panel = "details" }
        if (form.correction != null) {
            Text(stringResource(R.string.wo_correction_hint))
            WoodPileInput(form.reason, R.string.wo_reason, "wood_reason", enabled, limit = 5000) { change { copy(reason = it) } }
        }
    }
    if (panel != null) AlertDialog(onDismissRequest = { panel = null },
        title = { Text(stringResource(when (panel) { "method" -> R.string.retained_method; "date" -> R.string.retained_date_part; else -> R.string.retained_details })) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (panel) {
                "method" -> {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(form.shape, { value -> change { copy(shape = value) } }, enabled = enabled, modifier = Modifier.testTag("wood_shape"))
                        Text(stringResource(R.string.wo_shape), Modifier.weight(1f))
                    }
                    WoodPileInput(form.protocol, R.string.wo_protocol, "wood_protocol", enabled, 300) { change { copy(protocol = it) } }
                    Text(stringResource(R.string.wo_protocol_hint), style = MaterialTheme.typography.bodySmall)
                    WoodPileInput(form.pieceLength, R.string.wo_piece_length, "wood_piece_length", enabled, numeric = true) { change { copy(pieceLength = it) } }
                    Text(stringResource(R.string.wo_piece_hint), style = MaterialTheme.typography.bodySmall)
                }
                "date" -> {
                    WoodPileInput(form.observedDate, R.string.wo_date, "wood_observed_date", enabled, 16) { change { copy(observedDate = it) } }
                    Text(stringResource(R.string.wo_date_hint, form.observedZone), style = MaterialTheme.typography.bodySmall)
                }
                else -> {
                    WoodPileInput(form.species, R.string.wo_species, "wood_species", enabled, 300) { change { copy(species = it) } }
                    WoodPileInput(form.quality, R.string.wo_quality, "wood_quality", enabled, 1000) { change { copy(quality = it) } }
                    WoodPileInput(form.origin, R.string.wo_origin, "wood_origin", enabled, 1000) { change { copy(origin = it) } }
                    WoodPileInput(form.notes, R.string.wo_notes, "wood_notes", enabled, 5000) { change { copy(notes = it) } }
                }
            }
        } }, confirmButton = { TextButton({ panel = null }, modifier = Modifier.testTag("wood_panel_close")) { Text(stringResource(R.string.retained_close)) } })
}

@Composable private fun WoodPileInput(value: String, label: Int, tag: String, enabled: Boolean, limit: Int = 128,
    numeric: Boolean = false, change: (String) -> Unit,
) {
    OutlinedTextField(value, { if (it.length <= limit) change(it) }, enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag(tag), label = { Text(stringResource(label)) },
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
        suffix = if (numeric) {{ Text("m") }} else null)
}
