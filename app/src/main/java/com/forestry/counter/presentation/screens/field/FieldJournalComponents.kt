package com.forestry.counter.presentation.screens.field

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.forestry.counter.R
import com.forestry.counter.domain.field.*
import java.text.DateFormat
import java.time.ZoneId
import java.util.Date
import java.util.TimeZone

/** Shared presentation of already authorized entries; no repository reads or balance changes. */
internal fun FieldEntryForm.selectFieldEntryKind(kind: FieldEntryKind): FieldEntryForm =
    FieldEntryForm(kind=kind,title=title,notes=notes,observed=observed,zone=zone,
        latitude=latitude,longitude=longitude,photoIds=photoIds,unit=unit,convention=convention)

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

@Composable internal fun JournalEntryDates(entry: FieldEntry) {
    Row(Modifier.fillMaxWidth().testTag("journal_entry_dates"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            entry.form.observed.takeIf { it.isNotBlank() }?.let {
                Text(stringResource(R.string.retained_observed_at, "$it · ${entry.form.zone}"), style = MaterialTheme.typography.bodyMedium)
            }
            Text(stringResource(R.string.retained_recorded_at, journalRecordedDate(entry.recordedAt, entry.form.zone)),
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
                            Text(stringResource(R.string.retained_recorded_at, journalRecordedDate(entry.recordedAt, entry.form.zone)), style = MaterialTheme.typography.bodyMedium)
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

/** The recording instant is formatted in the entry's explicit zone; invalid zones remain unavailable. */
@Composable internal fun journalRecordedDate(at: Long, zoneId: String): String {
    val zone = runCatching { ZoneId.of(zoneId) }.getOrNull() ?: return "—"
    val format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, LocalConfiguration.current.locales[0])
    format.timeZone = TimeZone.getTimeZone(zone)
    return format.format(Date(at)) + " · " + zone.id
}
