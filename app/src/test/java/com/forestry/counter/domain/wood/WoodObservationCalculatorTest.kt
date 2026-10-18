package com.forestry.counter.domain.wood

import com.forestry.counter.domain.calculation.reception.BarkConvention
import com.forestry.counter.domain.calculation.reception.HarvestedWoodCalculator
import com.forestry.counter.domain.calculation.reception.HarvestedWoodVolumeKind
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WoodObservationCalculatorTest {
    @Test
    fun pileReferenceIsAnApparentEnvelopeWithItsDeclaredProtocol() {
        val result = calculate(pile()).result
        assertEquals(15.8, result.volumeM3, 1e-12)
        assertEquals(HarvestedWoodVolumeKind.STACKED_ENVELOPE_M3, result.kind)
        assertEquals("test-rectangular-envelope", result.protocolReference)
        assertEquals(HarvestedWoodCalculator.CONTRACT_VERSION, result.contractVersion)
    }

    @Test
    fun huberReferenceUsesMeasuredMiddleDiameterAndPreservesBarkConvention() {
        val result = calculate(log("huber", "4", middle = "32")).result
        assertEquals(0.3216990877275948, result.volumeM3, 1e-12)
        assertEquals(HarvestedWoodVolumeKind.GEOMETRIC_LOG_M3, result.kind)
        assertEquals(BarkConvention.OVER_BARK, result.barkConvention)
    }

    @Test
    fun smalianReferenceUsesBothEndDiameters() {
        val result = calculate(log("smalian", "5", start = "40", end = "32")).result
        assertEquals(0.5152211951887261, result.volumeM3, 1e-12)
    }

    @Test
    fun newtonReferenceUsesThreeSectionsWithoutBarkConversion() {
        val result = calculate(log("newton", "4.5", start = "45", middle = "40", end = "34")
            .copy(barkConventionCode = "UNDER_BARK")).result
        assertEquals(0.5643674852633215, result.volumeM3, 1e-12)
        assertEquals(BarkConvention.UNDER_BARK, result.barkConvention)
    }

    @Test
    fun mixedMetricInputUnitsProduceTheSameGeometry() {
        val input = log("huber", "4", middle = "32")
            .copy(length = RawWoodDimension("4000", "mm"), middleDiameter = RawWoodDimension("0,32", "m"))
        val evaluated = calculate(input)
        assertEquals(0.3216990877275948, evaluated.result.volumeM3, 1e-12)
        assertEquals("0.32", evaluated.dimensions[WoodField.MIDDLE_DIAMETER]!!.canonicalDecimal)
        assertEquals("0,32", evaluated.dimensions[WoodField.MIDDLE_DIAMETER]!!.raw!!.rawText)
    }

    @Test
    fun missingPileDimensionsAreSavedAndUncalculable() {
        val raw = observation(pile().copy(width = null))
        val evaluated = WoodObservationCalculator.calculate(raw) as WoodObservationCalculation.Uncalculable
        assertSame(raw, evaluated.observation)
        assertDiagnostic(evaluated, WoodField.WIDTH, WoodDiagnosticCode.MISSING_VALUE)
    }

    @Test
    fun missingAndUnsupportedShapeDoNotDefaultToRectangular() {
        assertDiagnostic(uncalculable(pile().copy(shapeCode = null)), WoodField.SHAPE, WoodDiagnosticCode.MISSING_SHAPE)
        assertDiagnostic(uncalculable(pile().copy(shapeCode = "TRAPEZOID")), WoodField.SHAPE, WoodDiagnosticCode.UNSUPPORTED_SHAPE)
    }

    @Test
    fun protocolIsRequiredIncludingNonBreakingNewlineWhitespace() {
        for (protocol in listOf(null, "", " \t", "\u0085")) {
            assertDiagnostic(uncalculable(pile().copy(protocolReference = protocol)), WoodField.PROTOCOL, WoodDiagnosticCode.MISSING_PROTOCOL)
        }
    }

    @Test
    fun logMethodAndBarkAreNeverGuessedFromAvailableDiameters() {
        assertDiagnostic(uncalculable(log("huber", "4", middle = "32").copy(methodCode = null)),
            WoodField.METHOD, WoodDiagnosticCode.MISSING_METHOD)
        assertDiagnostic(uncalculable(log("unknown", "4", middle = "32")),
            WoodField.METHOD, WoodDiagnosticCode.UNSUPPORTED_METHOD)
        assertDiagnostic(uncalculable(log("huber", "4", middle = "32").copy(barkConventionCode = null)),
            WoodField.BARK_CONVENTION, WoodDiagnosticCode.MISSING_BARK_CONVENTION)
        assertDiagnostic(uncalculable(log("huber", "4", middle = "32").copy(barkConventionCode = "ESTIMATE")),
            WoodField.BARK_CONVENTION, WoodDiagnosticCode.UNSUPPORTED_BARK_CONVENTION)
    }

    @Test
    fun requiredDiametersCannotBeReplacedByADifferentSection() {
        val evaluated = uncalculable(log("huber", "4", start = "32"))
        assertDiagnostic(evaluated, WoodField.MIDDLE_DIAMETER, WoodDiagnosticCode.MISSING_VALUE)
        assertDiagnostic(evaluated, WoodField.START_DIAMETER, WoodDiagnosticCode.UNUSED_SECTION_PRESENT)
    }

    @Test
    fun unusedSectionsAreRejectedEvenWhenAllRequiredMeasurementsExist() {
        val huber = uncalculable(log("huber", "4", start = "40", middle = "32", end = "25"))
        assertDiagnostic(huber, WoodField.START_DIAMETER, WoodDiagnosticCode.UNUSED_SECTION_PRESENT)
        assertDiagnostic(huber, WoodField.END_DIAMETER, WoodDiagnosticCode.UNUSED_SECTION_PRESENT)
        assertNotNull(huber.dimensions[WoodField.START_DIAMETER])
        val smalian = uncalculable(log("smalian", "4", start = "40", middle = "32", end = "25"))
        assertDiagnostic(smalian, WoodField.MIDDLE_DIAMETER, WoodDiagnosticCode.UNUSED_SECTION_PRESENT)
    }

    @Test
    fun invalidTextAndUnknownUnitsBlockCalculationWithoutDiscardingTheInput() {
        val raw = pile().copy(length = RawWoodDimension("1e3", "foot"))
        val evaluated = uncalculable(raw)
        assertEquals(raw, evaluated.observation.measurement)
        assertDiagnostic(evaluated, WoodField.LENGTH, WoodDiagnosticCode.INVALID_DECIMAL)
        assertDiagnostic(evaluated, WoodField.LENGTH, WoodDiagnosticCode.UNKNOWN_UNIT)
    }

    @Test
    fun negativeDimensionsAreRejectedAndZeroLengthIsNotASmallLog() {
        assertDiagnostic(uncalculable(pile().copy(width = RawWoodDimension("-2", "m"))),
            WoodField.WIDTH, WoodDiagnosticCode.NEGATIVE_VALUE)
        assertDiagnostic(uncalculable(log("huber", "0", middle = "32")),
            WoodField.LENGTH, WoodDiagnosticCode.ZERO_NOT_ALLOWED)
        assertDiagnostic(uncalculable(log("huber", "4", middle = "0")),
            WoodField.MIDDLE_DIAMETER, WoodDiagnosticCode.ZERO_NOT_ALLOWED)
        assertDiagnostic(uncalculable(log("smalian", "4", start = "-1", end = "32")),
            WoodField.START_DIAMETER, WoodDiagnosticCode.NEGATIVE_VALUE)
    }

    @Test
    fun zeroEndsAreAcceptedOnlyForMethodsAndPositiveSectionsThatSupportThem() {
        val smalian = calculate(log("smalian", "4", start = "40", end = "0")).result
        assertEquals(0.25132741228718347, smalian.volumeM3, 1e-12)
        val newton = calculate(log("newton", "4", start = "0", middle = "32", end = "0")).result
        assertEquals(0.2144660584850632, newton.volumeM3, 1e-12)
        assertDiagnostic(uncalculable(log("smalian", "4", start = "0", end = "0")),
            WoodField.SECTION, WoodDiagnosticCode.ZERO_SECTION)
        assertDiagnostic(uncalculable(log("newton", "4", start = "0", middle = "0", end = "0")),
            WoodField.SECTION, WoodDiagnosticCode.ZERO_SECTION)
    }

    @Test
    fun representableMeasurementsWhoseVolumeOverflowsAreUncalculable() {
        val huge = RawWoodDimension("9".repeat(120), "m")
        assertDiagnostic(uncalculable(pile().copy(length = huge, width = huge, meanHeight = huge)),
            WoodField.RESULT, WoodDiagnosticCode.NUMERIC_RESULT_UNREPRESENTABLE)
        val hugeCm = RawWoodDimension("9".repeat(120), "cm")
        assertDiagnostic(uncalculable(log("huber", "4", middle = "32").copy(length = huge, middleDiameter = hugeCm)),
            WoodField.RESULT, WoodDiagnosticCode.NUMERIC_RESULT_UNREPRESENTABLE)
    }

    @Test
    fun nonzeroMeasurementsWhoseVolumeUnderflowsNeverProduceZeroVolume() {
        val tiny = RawWoodDimension("0." + "0".repeat(120) + "1", "m")
        val evaluated = uncalculable(pile().copy(length = tiny, width = tiny, meanHeight = tiny))
        assertDiagnostic(evaluated, WoodField.RESULT, WoodDiagnosticCode.NUMERIC_RESULT_UNREPRESENTABLE)
        assertTrue(evaluated.dimensions.values.all { it.decimalInOriginalUnit!!.signum() > 0 })
    }

    @Test
    fun successfulAndUncalculableResultsExposeReadOnlyMapsAndDiagnosticLists() {
        val success = calculate(pile())
        assertUnsupported { (success.dimensions as MutableMap<WoodField, ParsedWoodDimension>).clear() }
        val failure = uncalculable(pile().copy(width = null))
        assertUnsupported { (failure.diagnostics as MutableList<WoodDiagnostic>).clear() }
        assertUnsupported { (failure.dimensions.getValue(WoodField.WIDTH).diagnostics as MutableList<WoodDiagnostic>).clear() }
    }

    @Test
    fun remeasurementCalculatesEachObservationIndependentlyWithoutAddingThemTogether() {
        val original = observation(pile().copy(meanHeight = RawWoodDimension("1.50", "m")))
        val remeasurement = original.copy(
            observationId = UUID.fromString("d7a98a47-b965-4e36-84e8-497277eb1c5d"),
            measurement = pile(),
        )
        val first = WoodObservationCalculator.calculate(original) as WoodObservationCalculation.Calculated
        val second = WoodObservationCalculator.calculate(remeasurement) as WoodObservationCalculation.Calculated
        assertEquals(15.0, first.result.volumeM3, 1e-12)
        assertEquals(15.8, second.result.volumeM3, 1e-12)
        assertEquals(first.observation.physicalObjectId, second.observation.physicalObjectId)
        assertEquals(WoodRawDecimalParser.CONTRACT_VERSION, second.parserVersion)
    }

    private fun pile() = RawWoodMeasurement.Pile(
        RawWoodDimension("5", "m"), RawWoodDimension("2", "m"), RawWoodDimension("1,58", "m"),
        RawWoodMeasurement.Pile.RECTANGULAR_ENVELOPE, "test-rectangular-envelope",
    )

    private fun log(method: String, length: String, start: String? = null, middle: String? = null, end: String? = null) =
        RawWoodMeasurement.Log(
            length = RawWoodDimension(length, "m"),
            startDiameter = start?.let { RawWoodDimension(it, "cm") },
            middleDiameter = middle?.let { RawWoodDimension(it, "cm") },
            endDiameter = end?.let { RawWoodDimension(it, "cm") },
            methodCode = method,
            barkConventionCode = "OVER_BARK",
        )

    private fun observation(measurement: RawWoodMeasurement) = RawWoodObservation(
        UUID.fromString("6dd3d14a-bb28-44eb-a9a2-fc6f3d78cb0a"),
        UUID.fromString("d988d55a-fdb4-4281-a4f7-d77ad9154fe5"),
        Instant.parse("2026-10-15T10:00:00Z"), measurement,
    )

    private fun calculate(input: RawWoodMeasurement): WoodObservationCalculation.Calculated =
        WoodObservationCalculator.calculate(observation(input)) as WoodObservationCalculation.Calculated

    private fun uncalculable(input: RawWoodMeasurement): WoodObservationCalculation.Uncalculable =
        WoodObservationCalculator.calculate(observation(input)) as WoodObservationCalculation.Uncalculable

    private fun assertDiagnostic(result: WoodObservationCalculation.Uncalculable, field: WoodField, code: WoodDiagnosticCode) {
        assertTrue("Expected $field/$code, got ${result.diagnostics}", result.diagnostics.contains(WoodDiagnostic(field, code)))
    }

    private fun assertUnsupported(block: () -> Unit) {
        try {
            block()
            fail("Expected an immutable result collection")
        } catch (_: UnsupportedOperationException) {
            // Successful isolation of returned data.
        }
    }
}
