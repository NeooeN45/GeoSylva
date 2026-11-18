package com.forestry.counter.presentation.screens.wood

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.forestry.counter.R
import com.forestry.counter.domain.repository.SavedWoodObject
import com.forestry.counter.domain.repository.SavedWoodObservation
import com.forestry.counter.domain.repository.WoodObjectDetails
import com.forestry.counter.domain.wood.RawWoodObservation
import com.forestry.counter.domain.wood.RawWoodMeasurement
import com.forestry.counter.domain.wood.WoodEffectiveSelection
import com.forestry.counter.domain.wood.WoodObjectKind
import com.forestry.counter.presentation.screens.field.RetainedHeading
import com.forestry.counter.presentation.screens.field.RetainedHelp
import com.forestry.counter.presentation.theme.GsShape
import com.forestry.counter.presentation.theme.Space
import java.text.DateFormat
import java.time.ZoneOffset
import java.util.Date
import java.util.SimpleTimeZone

@Composable
internal fun WoodFileMenu(item: SavedWoodObject, enabled: Boolean, onEditIdentity: () -> Unit) {
    var menu by rememberSaveable(item.id.toString()) { mutableStateOf(false) }
    var identity by rememberSaveable(item.id.toString()) { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menu = true }, enabled = enabled) {
            Icon(Icons.Default.MoreVert, stringResource(R.string.wo_file_options))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.retained_identification)) }, onClick = {
                menu = false
                identity = true
            })
            if (item.archivedAt == null) {
                DropdownMenuItem(text = { Text(stringResource(R.string.wo_edit_identity)) }, onClick = {
                    menu = false
                    onEditIdentity()
                })
            }
        }
    }
    if (identity) {
        AlertDialog(
            onDismissRequest = { identity = false },
            title = { Text(stringResource(R.string.retained_identification)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text(item.header.name, style = MaterialTheme.typography.titleMedium)
                    item.header.tag?.let { WoodFileFact(stringResource(R.string.wo_tag), it) }
                    item.header.declaredSpecies?.let { WoodFileFact(stringResource(R.string.wo_species), it) }
                    item.header.declaredQuality?.let { WoodFileFact(stringResource(R.string.wo_quality), it) }
                    item.header.declaredOrigin?.let { WoodFileFact(stringResource(R.string.wo_origin), it) }
                    item.header.description?.let { WoodFileFact(stringResource(R.string.wo_notes), it) }
                }
            },
            confirmButton = { TextButton({ identity = false }) { Text(stringResource(R.string.retained_close)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WoodFileOverview(
    detail: WoodObjectDetails,
    enabled: Boolean,
    onHistory: () -> Unit,
    onJournal: (() -> Unit)?,
) {
    val item = detail.item
    val kindLabel = stringResource(if (item.kind == WoodObjectKind.PILE) R.string.wo_pile else R.string.wo_log)
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text(item.header.name, style = MaterialTheme.typography.headlineSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            WoodFileBadge(Icons.Default.Inventory2, listOfNotNull(kindLabel, item.header.tag).joinToString(" · "))
            WoodFileBadge(Icons.Default.Smartphone, stringResource(R.string.retained_local))
        }
        WoodFileFact(
            stringResource(R.string.wo_species),
            item.header.declaredSpecies ?: stringResource(R.string.wo_no_value),
            stringResource(R.string.wo_file_species_help),
        )
        detail.depotName?.let {
            WoodFileFact(stringResource(R.string.wo_file_depot), it, stringResource(R.string.wo_file_depot_help))
        }
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onHistory,
                enabled = enabled,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("wood_file_surveys"),
                shape = GsShape.md,
            ) {
                Text(stringResource(R.string.retained_surveys))
            }
            if (onJournal != null) {
                OutlinedButton(
                    onClick = onJournal,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = GsShape.md,
                ) {
                    Text(stringResource(R.string.wo_file_journal))
                }
            }
        }
    }
}

@Composable
private fun WoodFileBadge(icon: ImageVector, label: String) {
    Surface(
        shape = GsShape.md,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = Space.sm, vertical = Space.xs),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun WoodFileFact(label: String, value: String, help: String? = null) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1.5f), style = MaterialTheme.typography.bodyMedium)
        help?.let { RetainedHelp(label, it) }
    }
}

@Composable
internal fun WoodFileReference(detail: WoodObjectDetails) {
    val selection = detail.history.effectiveSelection
    val record = (selection as? WoodEffectiveSelection.Current)?.record
    val saved = detail.observations.firstOrNull { it.record.observationId == record?.observationId }
    var protocol by rememberSaveable(detail.item.id.toString(), record?.observationId?.toString()) {
        mutableStateOf(false)
    }
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().testTag("wood_file_reference"),
        shape = GsShape.lg,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            RetainedHeading(stringResource(R.string.wo_reference), stringResource(R.string.retained_reference_help))
            when (selection) {
                WoodEffectiveSelection.None -> Text(stringResource(R.string.wo_no_reference))
                is WoodEffectiveSelection.Superseded -> Text(stringResource(R.string.wo_reference_corrected))
                is WoodEffectiveSelection.Current -> {
                    val raw = selection.record.observation
                    WoodFileDates(raw, saved)
                    HorizontalDivider(Modifier.padding(vertical = Space.xs))
                    Calculation(raw, compactPresentation = true)
                    val measurement = raw.measurement
                    if (measurement is RawWoodMeasurement.Pile &&
                        measurement.shapeCode == RawWoodMeasurement.Pile.RECTANGULAR_ENVELOPE
                    ) {
                        Text(stringResource(R.string.wo_file_envelope), style = MaterialTheme.typography.bodySmall)
                        val dimensions = rawValues(measurement).take(3).mapNotNull { it.second }
                        if (dimensions.size == 3) {
                            Text(dimensions.joinToString(" × "), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    HorizontalDivider()
                    TextButton(
                        onClick = { protocol = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("wood_file_protocol"),
                    ) {
                        Text(stringResource(R.string.wo_file_protocol))
                    }
                }
            }
        }
    }
    if (protocol && record != null) WoodFileProtocol(record.observation) { protocol = false }
}

@Composable
private fun WoodFileDates(raw: RawWoodObservation, saved: SavedWoodObservation?) {
    val observed = raw.observedTime
    val observedText = if (observed == null) stringResource(R.string.wo_date_unknown) else {
        val localTime = observed.instant.atOffset(ZoneOffset.ofTotalSeconds(observed.offsetSeconds))
        stringResource(R.string.wo_file_observed, WoodManualForm.DATE_FORMAT.format(localTime), observed.zoneId)
    }
    Text(observedText, style = MaterialTheme.typography.bodySmall)
    if (saved != null) {
        Text(
            woodRecordedLabel(saved),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun woodRecordedLabel(saved: SavedWoodObservation): String {
    val locale = LocalConfiguration.current.locales[0]
    val format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).apply {
        timeZone = SimpleTimeZone(saved.recordedOffsetSeconds * 1000, saved.recordedZoneId)
    }
    val localTime = format.format(Date.from(saved.record.observation.recordedAt))
    return stringResource(R.string.wo_file_recorded, localTime, saved.recordedZoneId)
}

@Composable
private fun WoodFileProtocol(raw: RawWoodObservation, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.retained_measurements_method)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                for ((label, value) in rawValues(raw.measurement)) {
                    WoodFileFact(stringResource(label), value ?: stringResource(R.string.wo_no_value))
                }
                Text(stringResource(R.string.wo_no_payable), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClose) { Text(stringResource(R.string.retained_close)) } },
    )
}

@Composable
internal fun WoodFileStockLink(enabled: Boolean, onJournal: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = GsShape.lg,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            RetainedHeading(
                stringResource(R.string.wo_file_stock),
                stringResource(R.string.wo_file_stock_help),
            )
            TextButton(onJournal, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Default.MenuBook, null, modifier = Modifier.padding(end = Space.xs))
                Text(stringResource(R.string.wo_file_stock_journal))
            }
        }
    }
}

@Composable
internal fun WoodFileNewSurvey(enabled: Boolean, onMeasure: () -> Unit) {
    Button(
        onClick = onMeasure,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("wood_object_measure"),
        shape = GsShape.md,
    ) {
        Icon(Icons.Default.Straighten, null, modifier = Modifier.padding(end = Space.sm))
        Text(stringResource(R.string.wo_measure))
    }
}
