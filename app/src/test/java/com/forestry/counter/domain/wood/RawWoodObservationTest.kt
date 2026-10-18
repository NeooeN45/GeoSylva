package com.forestry.counter.domain.wood

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RawWoodObservationTest {
    @Test
    fun commaDecimalPreservesRawAndOriginalUnitSeparatelyFromMetricNormalization() {
        val raw = RawWoodDimension(" +0032,500 ", "cm")
        val parsed = WoodRawDecimalParser.parse(raw, WoodField.MIDDLE_DIAMETER)

        assertEquals(raw, parsed.raw)
        assertEquals(" +0032,500 ", parsed.raw!!.rawText)
        assertEquals("32.5", parsed.canonicalDecimal)
        assertEquals(0, parsed.decimalInOriginalUnit!!.compareTo(BigDecimal("32.500")))
        assertEquals(0, parsed.metres!!.compareTo(BigDecimal("0.325")))
        assertEquals(0, parsed.centimetres!!.compareTo(BigDecimal("32.5")))
        assertTrue(parsed.diagnostics.isEmpty())
    }

    @Test
    fun metricUnitsConvertExactlyAndDoNotInferAUnit() {
        val m = WoodRawDecimalParser.parse(RawWoodDimension("1.58", "m"), WoodField.MEAN_HEIGHT)
        val cm = WoodRawDecimalParser.parse(RawWoodDimension("158", "cm"), WoodField.MEAN_HEIGHT)
        val mm = WoodRawDecimalParser.parse(RawWoodDimension("1580", "mm"), WoodField.MEAN_HEIGHT)
        assertEquals(0, m.metres!!.compareTo(cm.metres!!))
        assertEquals(0, m.metres!!.compareTo(mm.metres!!))

        val unknown = WoodRawDecimalParser.parse(RawWoodDimension("12", "inch"), WoodField.LENGTH)
        assertEquals("12", unknown.canonicalDecimal)
        assertEquals("inch", unknown.raw!!.unit)
        assertNull(unknown.metres)
        assertEquals(listOf(WoodDiagnostic(WoodField.LENGTH, WoodDiagnosticCode.UNKNOWN_UNIT)), unknown.diagnostics)

        val missing = WoodRawDecimalParser.parse(RawWoodDimension("12", null), WoodField.LENGTH)
        assertNull(missing.metres)
        assertEquals(WoodDiagnosticCode.MISSING_UNIT, missing.diagnostics.single().code)
    }

    @Test
    fun exponentThousandsSpecialAndNonAsciiNumbersAreRetainedButRejected() {
        for (text in listOf("1e3", "1E-2", "1 234", "1\u00a0234", "1,234.5", "1.234,5", "NaN", "Infinity", "∞", "١٢", "--1", ".5", "5.")) {
            val parsed = WoodRawDecimalParser.parse(RawWoodDimension(text, "m"), WoodField.LENGTH)
            assertEquals(text, parsed.raw!!.rawText)
            assertNull(text, parsed.decimalInOriginalUnit)
            assertEquals(text, listOf(WoodDiagnostic(WoodField.LENGTH, WoodDiagnosticCode.INVALID_DECIMAL)), parsed.diagnostics)
        }
    }

    @Test
    fun singleCommaIsADecimalSeparatorAndNeverInterpretedAsThousands() {
        val parsed = WoodRawDecimalParser.parse(RawWoodDimension("1,234", "m"), WoodField.LENGTH)
        assertEquals("1.234", parsed.canonicalDecimal)
        assertEquals(0, parsed.metres!!.compareTo(BigDecimal("1.234")))
    }

    @Test
    fun emptyDimensionsRemainMissingInsteadOfZero() {
        for (raw in listOf(null, RawWoodDimension(null, "m"), RawWoodDimension("\t \u0085", "m"))) {
            val parsed = WoodRawDecimalParser.parse(raw, WoodField.WIDTH)
            assertNull(parsed.decimalInOriginalUnit)
            assertNull(parsed.metres)
            assertTrue(parsed.diagnostics.contains(WoodDiagnostic(WoodField.WIDTH, WoodDiagnosticCode.MISSING_VALUE)))
        }
    }

    @Test
    fun negativeAndSignedZeroAreParsedWithoutReplacingTheText() {
        val negative = WoodRawDecimalParser.parse(RawWoodDimension("-2,50", "m"), WoodField.LENGTH)
        assertEquals("-2.5", negative.canonicalDecimal)
        assertEquals(-1, negative.metres!!.signum())
        val zero = WoodRawDecimalParser.parse(RawWoodDimension("-000,00", "cm"), WoodField.END_DIAMETER)
        assertEquals("0", zero.canonicalDecimal)
        assertEquals("-000,00", zero.raw!!.rawText)
    }

    @Test
    fun rawPayloadLengthsAreBoundedBeforeParsing() {
        RawWoodDimension("9".repeat(128), "u".repeat(32))
        RawWoodMeasurement.Pile(protocolReference = "p".repeat(300))
        assertStructure(WoodStructureError.RAW_TEXT_TOO_LONG) { RawWoodDimension("9".repeat(129), "m") }
        assertStructure(WoodStructureError.UNIT_TOO_LONG) { RawWoodDimension("1", "u".repeat(33)) }
        assertStructure(WoodStructureError.PROTOCOL_TOO_LONG) { RawWoodMeasurement.Pile(protocolReference = "p".repeat(301)) }
        // Limits count Unicode code points, in agreement with the existing protocol kernel.
        RawWoodMeasurement.Pile(protocolReference = "🌲".repeat(300))
        assertStructure(WoodStructureError.PROTOCOL_TOO_LONG) { RawWoodMeasurement.Pile(protocolReference = "🌲".repeat(301)) }
    }

    @Test
    fun observedTimeRequiresAllPartsAndUsesTheOffsetAtThatInstant() {
        assertNull(WoodObservedTime.fromParts(null, null, null))
        val summer = Instant.parse("2026-07-01T10:00:00Z")
        val winter = Instant.parse("2026-12-01T10:00:00Z")
        assertEquals(7200, WoodObservedTime.fromParts(summer, "Europe/Paris", 7200)!!.offsetSeconds)
        assertEquals(3600, WoodObservedTime.fromParts(winter, "Europe/Paris", 3600)!!.offsetSeconds)
        assertStructure(WoodStructureError.INCOMPLETE_OBSERVED_TIME) { WoodObservedTime.fromParts(summer, null, 7200) }
        assertStructure(WoodStructureError.INCOMPLETE_OBSERVED_TIME) { WoodObservedTime.fromParts(null, "Europe/Paris", 7200) }
        assertStructure(WoodStructureError.INCOMPLETE_OBSERVED_TIME) { WoodObservedTime.fromParts(summer, "Europe/Paris", null) }
        assertStructure(WoodStructureError.INCONSISTENT_TIME_OFFSET) { WoodObservedTime(summer, "Europe/Paris", 3600) }
        assertStructure(WoodStructureError.UNKNOWN_TIME_ZONE) { WoodObservedTime(summer, "+02:00", 7200) }
        assertStructure(WoodStructureError.INVALID_TIME_OFFSET) { WoodObservedTime(summer, "Europe/Paris", 100000) }
    }

    @Test
    fun daylightSavingOverlapIsDisambiguatedByTheInstantAndOffset() {
        WoodObservedTime(Instant.parse("2026-10-25T00:30:00Z"), "Europe/Paris", 7200)
        WoodObservedTime(Instant.parse("2026-10-25T01:30:00Z"), "Europe/Paris", 3600)
        assertStructure(WoodStructureError.INCONSISTENT_TIME_OFFSET) {
            WoodObservedTime(Instant.parse("2026-10-25T01:30:00Z"), "Europe/Paris", 7200)
        }
    }

    @Test
    fun remeasurementKeepsThePhysicalIdentityAndHasItsOwnObservationIdentity() {
        val objectId = UUID.fromString("d988d55a-fdb4-4281-a4f7-d77ad9154fe5")
        val first = RawWoodObservation(
            UUID.fromString("6dd3d14a-bb28-44eb-a9a2-fc6f3d78cb0a"), objectId,
            Instant.parse("2026-10-15T10:00:00Z"), RawWoodMeasurement.Pile(),
        )
        val second = first.copy(observationId = UUID.fromString("d7a98a47-b965-4e36-84e8-497277eb1c5d"))
        assertEquals(first.physicalObjectId, second.physicalObjectId)
        assertTrue(first.observationId != second.observationId)
        assertNull(first.observedTime)
        assertStructure(WoodStructureError.OBSERVATION_AND_OBJECT_ID_IDENTICAL) { first.copy(observationId = objectId) }
    }

    private fun assertStructure(code: WoodStructureError, block: () -> Unit) {
        try {
            block()
            fail("Expected structural error $code")
        } catch (error: WoodStructureException) {
            assertEquals(code, error.code)
        }
    }
}
