package com.forestry.counter.presentation.screens.field

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
            label = { Text(stringResource(filter.label)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("journal_filter_${filter.name}"),
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary)) }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.form.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
            }
            if (!active) Text(stringResource(R.string.field_replaced), style = MaterialTheme.typography.labelSmall)
            entry.form.notes.takeIf { it.isNotBlank() }?.let { Text(it, maxLines = 3, style = MaterialTheme.typography.bodyMedium) }
            if (entry.form.activity != null) Text("${entry.form.durationMinutes} min", style = MaterialTheme.typography.labelMedium)
            if (entry.form.photoIds.isNotEmpty()) Text(stringResource(R.string.field_photos) + " (${entry.form.photoIds.size})", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Decorative type cue only; the enclosing existing entry card retains its action and identity. */
private fun journalKindIcon(kind: FieldEntryKind): ImageVector = when (kind) {
    FieldEntryKind.ACTIVITY -> Icons.Outlined.Schedule
    FieldEntryKind.ACCESS -> Icons.Outlined.DirectionsCar
    FieldEntryKind.INCIDENT -> Icons.Outlined.WarningAmber
    else -> Icons.Outlined.Description
}

@Composable internal fun JournalKindBadge(kind: FieldEntryKind) {
    val incident = kind == FieldEntryKind.INCIDENT
    val palette = fieldQuantityPalette(if (incident) FieldEntryKind.REMAINING else FieldEntryKind.NOTE)
    Surface(color = palette.surface, contentColor = palette.ink, shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(journalKindIcon(kind), null, Modifier.size(18.dp))
            Text(kindLabel(kind), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** One compact status; the existing offline and storage explanations remain in its own help. */
@Composable internal fun JournalStorageStatus(saved: Boolean? = null) {
    val title = stringResource(when (saved) {
        true -> R.string.retained_draft_local
        false -> R.string.field_draft_pending
        null -> R.string.retained_local
    })
    Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(start = 12.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.PhoneAndroid, null, Modifier.size(20.dp))
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            RetainedHelp(title, stringResource(R.string.field_local) + "\n\n" + stringResource(R.string.retained_local_help))
        }
    }
}

@Composable internal fun JournalEntryDetail(entry: FieldEntry, state: FieldJournalState, vm: FieldJournalViewModel,
    active: Boolean, onHistory: () -> Unit, onCorrect: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        JournalKindBadge(entry.form.kind)
        Text(entry.form.title, style = MaterialTheme.typography.headlineSmall)
        JournalEntryDates(entry)
        if (entry.form.notes.isNotBlank()) Text(stringResource(R.string.retained_observation), style = MaterialTheme.typography.titleMedium)
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
    val displayed = journalHistoryForDisplay(history, activeIds)
    val selected = history.firstOrNull { it.id == selectedId }
    val lineColor = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.surface
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        selected?.let { selected ->
            JournalKindBadge(selected.form.kind)
            Text(selected.form.title, style = MaterialTheme.typography.headlineSmall)
            JournalEntryDates(selected)
        }
        RetainedHeading(stringResource(R.string.retained_changes_kept), stringResource(R.string.retained_history_order))
        Column {
            displayed.forEachIndexed { index, entry ->
                val active = entry.id in activeIds
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    Canvas(Modifier.width(24.dp).fillMaxHeight()) {
                        val center = Offset(size.width / 2, 26.dp.toPx())
                        if (index > 0) drawLine(lineColor, Offset(center.x, 0f), center, 1.dp.toPx())
                        if (index < displayed.lastIndex) drawLine(lineColor, center, Offset(center.x, size.height), 1.dp.toPx())
                        drawCircle(if (active) lineColor else background, 6.dp.toPx(), center)
                        drawCircle(lineColor, 6.dp.toPx(), center, style = Stroke(2.dp.toPx()))
                    }
                    OutlinedCard(Modifier.weight(1f).padding(bottom = 12.dp).testTag("journal_history_${entry.id}"),
                        colors = CardDefaults.outlinedCardColors(containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else background)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                shape = RoundedCornerShape(24.dp)) {
                                Text(stringResource(if (active) R.string.retained_current_entry else R.string.retained_kept_entry),
                                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge)
                            }
                            Text(stringResource(if (entry.form.correctionOf == null) R.string.retained_original_entry else R.string.retained_correction_recorded),
                                style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.retained_recorded_at, stockDate(entry.recordedAt, entry.form.zone)), style = MaterialTheme.typography.bodyMedium)
                            if (entry.form.title != selected?.form?.title) Text(entry.form.title, style = MaterialTheme.typography.titleSmall)
                            if (entry.form.notes.isNotBlank()) Text(entry.form.notes)
                            if (entry.form.correctionReason.isNotBlank()) {
                                HorizontalDivider()
                                Text(stringResource(R.string.retained_correction_reason_label), style = MaterialTheme.typography.labelLarge)
                                Text(entry.form.correctionReason)
                            }
                            HorizontalDivider()
                            TextButton({ onOpen(entry.id) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(stringResource(if (entry.form.correctionOf == null) R.string.retained_consult_original else R.string.retained_consult_entry), Modifier.weight(1f))
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                            }
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.retained_kept_original), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable internal fun RetainedFieldEntryEditor(form: FieldEntryForm, target: FieldTarget, entries: List<FieldEntry>,
    enabled: Boolean, change: (FieldEntryForm.() -> FieldEntryForm) -> Unit,
    photos: @Composable () -> Unit = {},
) {
    val original = entries.firstOrNull { it.id == form.correctionOf }
    var more by remember(form.correctionOf) { mutableStateOf(false) }
    val correcting = form.correctionOf != null
    val kinds = FieldEntryKind.entries.filter { target.kind == FieldTargetKind.WOOD_OBJECT || it !in FieldEntryForm.quantityKinds && it != FieldEntryKind.QUALITY }
    val identityFields: @Composable () -> Unit = {
        FieldChoice(R.string.retained_entry_type, form.kind, kinds, { kindLabel(it) }, enabled && !correcting) { kind -> change { selectFieldEntryKind(kind) } }
        FieldText(R.string.retained_required_title, form.title, enabled, tag = "field_name") { change { copy(title = it) } }
    }
    val observedFields: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.retained_date), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.retained_optional), style = MaterialTheme.typography.bodySmall)
            RetainedHelp(stringResource(R.string.retained_date), stringResource(R.string.retained_time_help))
        }
        val parts = form.observed.split(' ', limit = 2)
        val date = parts.getOrElse(0) { "" }; val time = parts.getOrElse(1) { "" }
        val largeFont = LocalDensity.current.fontScale >= 1.3f
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val dateField: @Composable (Modifier) -> Unit = { modifier ->
                OutlinedTextField(date, { value -> change { copy(observed = joinedObservedDate(value, time)) } }, enabled = enabled,
                    label = { Text(stringResource(R.string.retained_date_part)) }, modifier = modifier.testTag("field_observed_date"))
            }
            val timeField: @Composable (Modifier) -> Unit = { modifier ->
                OutlinedTextField(time, { value -> change { copy(observed = joinedObservedDate(date, value)) } }, enabled = enabled,
                    label = { Text(stringResource(R.string.retained_time_part)) }, modifier = modifier.testTag("field_observed_time"))
            }
            if (maxWidth < 340.dp || largeFont) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                dateField(Modifier.fillMaxWidth()); timeField(Modifier.fillMaxWidth())
            } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                dateField(Modifier.weight(1f)); timeField(Modifier.weight(1f))
            }
        }
        Text(form.zone, style = MaterialTheme.typography.bodySmall)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        original?.let { source ->
            Text(stringResource(R.string.retained_original_entry), style = MaterialTheme.typography.titleMedium)
            OutlinedCard { Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(source.form.title, style = MaterialTheme.typography.titleMedium)
                JournalKindBadge(source.form.kind)
                if (source.form.observed.isNotBlank()) Text(stringResource(R.string.retained_observed_at, "${source.form.observed} · ${source.form.zone}"), style = MaterialTheme.typography.bodySmall)
                Text(source.form.notes)
            } }
        }
        if (!correcting) identityFields()
        if (form.kind == FieldEntryKind.ACTIVITY) {
            FieldChoice(R.string.retained_activity_performed, form.activity, FieldActivity.entries, { activityLabel(it) }, enabled) { change { copy(activity = it) } }
            FieldText(R.string.field_minutes, form.durationMinutes, enabled, true, "field_minutes") { change { copy(durationMinutes = it) } }
        }
        if (form.kind == FieldEntryKind.ACCESS) {
            FieldText(R.string.field_vehicle, form.vehicle, enabled) { change { copy(vehicle = it) } }
            FieldText(R.string.field_restriction, form.restriction, enabled) { change { copy(restriction = it) } }
        }
        if (!correcting) observedFields()
        if (form.kind == FieldEntryKind.ACTIVITY) FieldText(R.string.field_equipment, form.equipment, enabled) { change { copy(equipment = it) } }
        FieldText(if (correcting) R.string.retained_corrected_notes else R.string.field_notes, form.notes, enabled, tag = "field_notes") { change { copy(notes = it) } }
        if (correcting) FieldText(R.string.field_reason, form.correctionReason, enabled, tag = "field_correction_reason") { change { copy(correctionReason = it) } }
        if (!correcting) photos()
        OutlinedButton({ more = !more }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("field_other_info"), shape = MaterialTheme.shapes.medium) {
            Icon(Icons.Outlined.Tune, null)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.retained_other_info), Modifier.weight(1f))
            Icon(if (more) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
        }
        if (more) {
            if (correcting) { identityFields(); observedFields() }
            FieldText(R.string.field_zone, form.zone, enabled) { change { copy(zone = it) } }
            Text(stringResource(R.string.field_location_manual), style = MaterialTheme.typography.labelMedium)
            FieldText(R.string.field_lat, form.latitude, enabled) { change { copy(latitude = it, locationSource = "MANUAL", accuracyM = "") } }
            FieldText(R.string.field_lon, form.longitude, enabled) { change { copy(longitude = it, locationSource = "MANUAL", accuracyM = "") } }
            if (correcting) photos()
        }
        if (correcting) Text(stringResource(R.string.retained_kept_original), style = MaterialTheme.typography.bodySmall)
    }
}

internal fun joinedObservedDate(date: String, time: String): String =
    if (date.isEmpty() && time.isEmpty()) "" else "$date $time"
