package com.forestry.counter.presentation.screens.field

import com.forestry.counter.domain.field.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test

class RetainedJournalPresentationTest {
    private val target = FieldTarget(FieldTargetKind.WORKSITE, UUID(0, 1).toString())
    private fun entry(id: Long, form: FieldEntryForm = FieldEntryForm(title = "TEST entry"), recorded: String = "2026-11-01T23:30:00Z") =
        FieldEntry(UUID(0, id).toString(), target, form, Instant.parse(recorded).toEpochMilli(), "TEST")

    @Test fun `undated entry groups by recording day in the displayed zone with an explicit fallback`() {
        val value = journalEntryDate(entry(2, FieldEntryForm(title = "TEST", zone = "Europe/Paris")))
        assertFalse(value.observed)
        assertEquals(LocalDate.of(2026, 11, 2), value.day)
    }

    @Test fun `observation date controls both ordering and displayed day`() {
        val value = journalEntryDate(entry(2, FieldEntryForm(title = "TEST", observed = "01/11/2026 23:45", zone = "Europe/Paris")))
        assertTrue(value.observed)
        assertEquals(Instant.parse("2026-11-01T22:45:00Z").toEpochMilli(), value.millis)
        assertEquals(LocalDate.of(2026, 11, 1), value.day)
    }

    @Test fun `correction history includes original and later corrections but excludes another target`() {
        val original = entry(2)
        val correction = entry(3, original.form.copy(correctionOf = original.id, correctionReason = "TEST fix"), "2026-11-02T01:00:00Z")
        val latest = entry(4, correction.form.copy(correctionOf = correction.id), "2026-11-02T02:00:00Z")
        val other = entry(5, original.form.copy(correctionOf = original.id)).copy(target = target.copy(id = UUID(0, 9).toString()))
        assertEquals(listOf(latest, correction, original), journalCorrectionHistory(listOf(original, other, latest, correction), correction.id))
    }

    @Test fun `cycle cannot hang the history renderer`() {
        val first = entry(2)
        val second = entry(3, first.form.copy(correctionOf = first.id))
        val cycle = first.copy(form = first.form.copy(correctionOf = second.id))
        assertEquals(2, journalCorrectionHistory(listOf(cycle, second), first.id).size)
    }

    @Test fun `date editing keeps incomplete input without inventing a time`() {
        assertEquals("", joinedObservedDate("", ""))
        assertEquals("02/11/2026 ", joinedObservedDate("02/11/2026", ""))
        assertEquals(" 10:15", joinedObservedDate("", "10:15"))
    }

    @Test fun `legacy lightweight choice decodes with absent metadata`() {
        val json = """{"target":{"kind":"WORKSITE","id":"${target.id}"},"label":"TEST"}"""
        val choice = FieldCodec.json.decodeFromString<FieldTargetChoice>(json)
        assertNull(choice.depotId)
        assertNull(choice.declaredSpecies)
    }
}
