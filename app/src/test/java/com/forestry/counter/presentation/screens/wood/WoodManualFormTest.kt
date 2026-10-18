package com.forestry.counter.presentation.screens.wood

import com.forestry.counter.domain.wood.*
import org.junit.Assert.*
import org.junit.Test

class WoodManualFormTest {
    @Test fun partial_pile_keeps_raw_comma_and_missing_fields() {
        val payload = WoodManualForm(WoodObjectKind.PILE, length = "12,50", pieceLength = "2,00").payload()
        val pile = payload.measurement as RawWoodMeasurement.Pile
        assertEquals("12,50", pile.length?.rawText); assertEquals("m", pile.length?.unit)
        assertNull(pile.width); assertNull(pile.meanHeight); assertNull(pile.shapeCode)
        assertEquals("2,00", pile.pieceLength?.rawText); assertNull(payload.observedTime)
    }
    @Test fun observation_date_is_separate_and_strict_including_nonexistent_dst_time() {
        val form = WoodManualForm(WoodObjectKind.PILE, observedDate = "18/10/2026 10:30", observedZone = "Europe/Paris")
        assertEquals("2026-10-18T08:30:00Z", form.payload().observedTime?.instant.toString())
        assertTrue(runCatching { form.copy(observedDate = "31/02/2026 10:30").payload() }.isFailure)
        assertTrue(runCatching { form.copy(observedDate = "29/03/2026 02:30").payload() }.isFailure)
        assertTrue(runCatching { form.copy(observedDate = "25/10/2026 02:30").payload() }.isFailure)
    }
    @Test fun log_method_change_does_not_discard_previous_sections() {
        val raw = WoodManualForm(WoodObjectKind.LOG, length = "4", start = "32", middle = "30", end = "28", method = "huber", bark = "OVER_BARK").payload().measurement as RawWoodMeasurement.Log
        assertEquals("32", raw.startDiameter?.rawText); assertEquals("28", raw.endDiameter?.rawText)
        val observation = RawWoodObservation(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), java.time.Instant.EPOCH, raw)
        assertTrue(WoodObservationCalculator.calculate(observation) is WoodObservationCalculation.Uncalculable)
    }
}
