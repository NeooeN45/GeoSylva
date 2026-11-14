package com.forestry.counter.presentation.screens.field

import com.forestry.counter.domain.field.FieldEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Literal pure helpers retained independently of the pending journal screen.
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

internal fun joinedObservedDate(date: String, time: String): String =
    if (date.isEmpty() && time.isEmpty()) "" else "$date $time"
