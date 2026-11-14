package com.forestry.counter.presentation.screens.field

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.forestry.counter.R
import com.forestry.counter.domain.field.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal enum class JournalFilter(val label: Int, val kind: FieldEntryKind?) {
    ALL(R.string.retained_all, null), ACTIVITY(R.string.retained_activities, FieldEntryKind.ACTIVITY),
    NOTE(R.string.retained_notes, FieldEntryKind.NOTE), INCIDENT(R.string.retained_incidents, FieldEntryKind.INCIDENT),
    ACCESS(R.string.retained_access, FieldEntryKind.ACCESS),
}
internal data class JournalEntryDate(val millis: Long, val observed: Boolean, val zone: ZoneId) {
    val day: LocalDate get() = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}
internal fun journalEntryDate(entry: FieldEntry): JournalEntryDate {
    val zone = runCatching { ZoneId.of(entry.form.zone) }.getOrDefault(ZoneId.of("UTC"))
    val observed = runCatching { entry.form.observedInstant() }.getOrNull()
    return JournalEntryDate(observed ?: entry.recordedAt, observed != null, zone)
}
/** Only entries already returned for the authorized target participate in a history. */
internal fun journalCorrectionHistory(entries: List<FieldEntry>, selectedId: String): List<FieldEntry> {
    val selected = entries.firstOrNull { it.id == selectedId } ?: return emptyList()
    val sameTarget = entries.filter { it.target == selected.target }
    val connected = mutableSetOf(selected.id)
    do {
        val previousSize = connected.size
        sameTarget.forEach { entry ->
            if (entry.id in connected) entry.form.correctionOf?.let { connected += it }
            if (entry.form.correctionOf in connected) connected += entry.id
        }
    } while (connected.size != previousSize)
    return sameTarget.filter { it.id in connected }.sortedWith(compareByDescending<FieldEntry> { it.recordedAt }.thenBy { it.id })
}

/** Active versions lead the reading even when the recording clock ties; retain the existing order within each group. */
internal fun journalHistoryForDisplay(history: List<FieldEntry>, activeIds: Set<String>): List<FieldEntry> =
    history.filter { it.id in activeIds } + history.filterNot { it.id in activeIds }

@Composable internal fun JournalFilters(selected: JournalFilter, onSelect: (JournalFilter) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        JournalFilter.entries.forEach { filter -> FilterChip(selected == filter, { onSelect(filter) },
            label = { Text(stringResource(filter.label)) }, modifier = Modifier.testTag("journal_filter_${filter.name}")) }
    }
}

@Composable internal fun JournalDay(entry: FieldEntry) {
    val locale = LocalConfiguration.current.locales[0]
    val date = journalEntryDate(entry)
    RetainedHeading(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(date.day), stringResource(R.string.retained_time_help))
}

@Composable internal fun JournalEntrySummary(entry: FieldEntry, active: Boolean, onOpen: () -> Unit) {
    val date = journalEntryDate(entry)
    val locale = LocalConfiguration.current.locales[0]
    val display = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).format(Instant.ofEpochMilli(date.millis).atZone(date.zone))
    OutlinedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth().testTag("journal_entry_${entry.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            JournalKindBadge(entry.form.kind)
            Text(stringResource(if (date.observed) R.string.retained_observed_at else R.string.retained_recorded_at, display), style = MaterialTheme.typography.labelSmall)
            Text(entry.form.title, style = MaterialTheme.typography.titleMedium)
            if (!active) Text(stringResource(R.string.field_replaced), style = MaterialTheme.typography.labelSmall)
            entry.form.notes.takeIf { it.isNotBlank() }?.let { Text(it, maxLines = 3, style = MaterialTheme.typography.bodyMedium) }
            if (entry.form.activity != null) Text("${entry.form.durationMinutes} min", style = MaterialTheme.typography.labelMedium)
            if (entry.form.photoIds.isNotEmpty()) Text(stringResource(R.string.field_photos) + " (${entry.form.photoIds.size})", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Decorative type cue only; the enclosing existing entry card retains its action and identity. */
@Composable internal fun JournalKindBadge(kind: FieldEntryKind) {
    val incident = kind == FieldEntryKind.INCIDENT
    val palette = fieldQuantityPalette(if (incident) FieldEntryKind.REMAINING else FieldEntryKind.NOTE)
    Surface(color = palette.surface, contentColor = palette.ink, shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (incident) Icon(Icons.Outlined.WarningAmber, null, Modifier.size(18.dp))
            Text(kindLabel(kind), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable internal fun JournalEntryDetail(entry: FieldEntry, state: FieldJournalState, vm: FieldJournalViewModel,
    active: Boolean, onHistory: () -> Unit, onCorrect: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        Text(kindLabel(entry.form.kind), style = MaterialTheme.typography.labelLarge)
        Text(entry.form.title, style = MaterialTheme.typography.headlineSmall)
        JournalEntryDates(entry)
        FieldEntryCard(entry, active, state.entries, vm, false, entry.id in state.protectedEntryIds, state.controlAssessments[entry.id], showPhotos = false, showHeader = false)
        if (entry.form.photoIds.isNotEmpty()) FieldPhotos(vm, entry.form.photoIds, false)
        RetainedActionRow(Icons.Default.History, stringResource(R.string.retained_view_history), modifier = Modifier.testTag("journal_view_history"), action = onHistory)
        Text(stringResource(R.string.retained_kept_original), style = MaterialTheme.typography.bodySmall)
        if (active && state.writable && entry.id !in state.protectedEntryIds) Button(onCorrect,
            enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("journal_correct")) {
            Text(stringResource(R.string.retained_correct_history))
        }
    }
}

@Composable internal fun JournalEntryDates(entry: FieldEntry) {
    Row(Modifier.fillMaxWidth().testTag("journal_entry_dates"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            entry.form.observed.takeIf { it.isNotBlank() }?.let {
                Text(stringResource(R.string.retained_observed_at, "$it · ${entry.form.zone}"), style = MaterialTheme.typography.bodyMedium)
            }
            Text(stringResource(R.string.retained_recorded_at, stockDate(entry.recordedAt, entry.form.zone)),
                style = MaterialTheme.typography.bodyMedium)
        }
        RetainedHelp(stringResource(R.string.retained_date), stringResource(R.string.retained_time_help),
            Modifier.testTag("journal_dates_help"))
    }
}

@Composable internal fun JournalHistory(entries: List<FieldEntry>, selectedId: String, activeIds: Set<String>, onOpen: (String) -> Unit) {
    val history = journalCorrectionHistory(entries, selectedId)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.retained_history_order), style = MaterialTheme.typography.bodySmall)
        journalHistoryForDisplay(history, activeIds).forEach { entry ->
            OutlinedCard(Modifier.fillMaxWidth().testTag("journal_history_${entry.id}")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(if (entry.id in activeIds) R.string.retained_current_entry else R.string.retained_kept_entry), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(R.string.retained_recorded_at, stockDate(entry.recordedAt, entry.form.zone)))
                    Text(entry.form.title, style = MaterialTheme.typography.titleMedium)
                    if (entry.form.notes.isNotBlank()) Text(entry.form.notes)
                    if (entry.form.correctionReason.isNotBlank()) {
                        Text(stringResource(R.string.retained_correction_reason_label), style = MaterialTheme.typography.labelLarge)
                        Text(entry.form.correctionReason)
                    }
                    TextButton({ onOpen(entry.id) }) { Text(stringResource(R.string.retained_consult_entry)) }
                }
            }
        }
        Text(stringResource(R.string.retained_kept_original), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable internal fun RetainedFieldEntryEditor(form: FieldEntryForm, target: FieldTarget, entries: List<FieldEntry>,
    enabled: Boolean, change: (FieldEntryForm.() -> FieldEntryForm) -> Unit,
) {
    val original = entries.firstOrNull { it.id == form.correctionOf }
    var more by remember(form.correctionOf) { mutableStateOf(false) }
    val correcting = form.correctionOf != null
    val kinds = FieldEntryKind.entries.filter { target.kind == FieldTargetKind.WOOD_OBJECT || it !in FieldEntryForm.quantityKinds && it != FieldEntryKind.QUALITY }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        original?.let { source ->
            Text(stringResource(R.string.retained_original_entry), style = MaterialTheme.typography.titleMedium)
            OutlinedCard { Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(source.form.title); Text(kindLabel(source.form.kind)); Text(source.form.notes)
                if (source.form.observed.isNotBlank()) Text(stringResource(R.string.retained_observed_at, "${source.form.observed} · ${source.form.zone}"))
            } }
        }
        if (!correcting || more) {
            FieldChoice(R.string.retained_entry_type, form.kind, kinds, { kindLabel(it) }, enabled && !correcting) { kind -> change { selectFieldEntryKind(kind) } }
            FieldText(R.string.field_name, form.title, enabled, tag = "field_name") { change { copy(title = it) } }
        }
        if (form.kind == FieldEntryKind.ACTIVITY) {
            FieldChoice(R.string.field_activity, form.activity, FieldActivity.entries, { activityLabel(it) }, enabled) { change { copy(activity = it) } }
            FieldText(R.string.field_minutes, form.durationMinutes, enabled, true, "field_minutes") { change { copy(durationMinutes = it) } }
            FieldText(R.string.field_equipment, form.equipment, enabled) { change { copy(equipment = it) } }
        }
        if (form.kind == FieldEntryKind.ACCESS) {
            FieldText(R.string.field_vehicle, form.vehicle, enabled) { change { copy(vehicle = it) } }
            FieldText(R.string.field_restriction, form.restriction, enabled) { change { copy(restriction = it) } }
        }
        if (!correcting || more) {
            RetainedHeading(stringResource(R.string.retained_date), stringResource(R.string.retained_time_help))
            val parts = form.observed.split(' ', limit = 2)
            val date = parts.getOrElse(0) { "" }; val time = parts.getOrElse(1) { "" }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(date, { value -> change { copy(observed = joinedObservedDate(value, time)) } }, enabled = enabled,
                    label = { Text(stringResource(R.string.retained_date_part)) }, modifier = Modifier.weight(1f).testTag("field_observed_date"))
                OutlinedTextField(time, { value -> change { copy(observed = joinedObservedDate(date, value)) } }, enabled = enabled,
                    label = { Text(stringResource(R.string.retained_time_part)) }, modifier = Modifier.weight(1f).testTag("field_observed_time"))
            }
            Text(form.zone, style = MaterialTheme.typography.bodySmall)
        }
        FieldText(if (correcting) R.string.retained_corrected_notes else R.string.field_notes, form.notes, enabled, tag = "field_notes") { change { copy(notes = it) } }
        if (correcting) FieldText(R.string.field_reason, form.correctionReason, enabled, tag = "field_correction_reason") { change { copy(correctionReason = it) } }
        TextButton({ more = !more }, modifier = Modifier.testTag("field_other_info")) { Text(stringResource(R.string.retained_other_info)) }
        if (more) {
            FieldText(R.string.field_zone, form.zone, enabled) { change { copy(zone = it) } }
            Text(stringResource(R.string.field_location_manual), style = MaterialTheme.typography.labelMedium)
            FieldText(R.string.field_lat, form.latitude, enabled) { change { copy(latitude = it, locationSource = "MANUAL", accuracyM = "") } }
            FieldText(R.string.field_lon, form.longitude, enabled) { change { copy(longitude = it, locationSource = "MANUAL", accuracyM = "") } }
        }
        if (correcting) Text(stringResource(R.string.retained_kept_original), style = MaterialTheme.typography.bodySmall)
    }
}

internal fun joinedObservedDate(date: String, time: String): String =
    if (date.isEmpty() && time.isEmpty()) "" else "$date $time"
