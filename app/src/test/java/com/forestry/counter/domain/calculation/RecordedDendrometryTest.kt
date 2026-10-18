package com.forestry.counter.domain.calculation

import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Tige
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class RecordedDendrometryTest {
    private val parcel = Parcelle("P", "F", name = "TEST Parcel", surfaceHa = null, shape = null,
        slopePct = null, aspect = null, access = null, altitudeM = null, objectifType = null,
        objectifVal = null, tolerancePct = null, samplingMode = null, sampleAreaM2 = null,
        targetSpeciesCsv = null, srid = null, remarks = null)

    private fun tree(id: String, diameter: Double, height: Double? = 20.0) = Tige(
        id, "P", "W", "TEST_OAK", diameter, height, null, null, null,
        timestamp = 1000, note = null, produit = null, fCoef = null, valueEur = null,
        sessionId = "S", source = "TEST stored observation",
    )

    private fun report(rows: List<Tige>, plot: String? = "W", session: String? = "S") =
        SynthesisReportCalculator.calculate(parcel, emptyList(), rows, emptyMap(),
            SynthesisParameters(selectedPlotId = plot), session, "TEST campaign", 2, 2000)

    private fun calculate(rows: List<Tige>) =
        RecordedDendrometryCalculator.calculate(report(rows), rows)

    @Test fun `sections and means match independent circles but stay exactly frozen`() {
        val rows = listOf(tree("A", 10.0), tree("B", 30.0))
        val frozen = report(rows)
        val result = RecordedDendrometryCalculator.calculate(frozen, rows)
        assertEquals(RecordedDendrometryStatus.AVAILABLE, result.status)
        assertEquals(2, result.recordedCount)
        assertEquals(0.07853981633974483, result.basalAreaM2!!, 1e-12)
        assertEquals(20.0, result.meanDiameterCm!!, 1e-12)
        assertEquals(22.360679774997898, result.quadraticDiameterCm!!, 1e-12)
        assertEquals(frozen.basalAreaM2.toBits(), result.basalAreaM2!!.toBits())
        assertEquals(frozen.quadraticDiameterCm!!.toBits(), result.quadraticDiameterCm!!.toBits())
        assertTrue(result.diameterBasisUnknown)
        assertEquals(listOf("A", "B"), result.observationReferences.map { it.id })
    }

    @Test fun `distribution preserves stored decimals and counts rather than rounding classes`() {
        val result = calculate(listOf(tree("A", 20.25), tree("B", 20.25), tree("C", 20.5)))
        assertEquals(listOf(RecordedDiameterFrequency(20.25, 2),
            RecordedDiameterFrequency(20.5, 1)), result.diameterDistribution)
        assertEquals(3, result.validDiameterCount)
    }

    @Test fun `parcel session plot deleted and exact duplicates cannot inflate selected scope`() {
        val kept = tree("A", 20.0)
        val input = listOf(kept, kept, tree("parcel", 30.0).copy(parcelleId = "OTHER"),
            tree("old", 40.0).copy(sessionId = "OLD"),
            tree("plot", 50.0).copy(placetteId = "OTHER"),
            tree("deleted", 60.0).copy(deletedAt = 1))
        val result = RecordedDendrometryCalculator.calculate(report(listOf(kept)), input)
        assertEquals(RecordedDendrometryStatus.AVAILABLE, result.status)
        assertEquals(1, result.recordedCount)
        assertEquals(listOf(RecordedDiameterFrequency(20.0, 1)), result.diameterDistribution)
    }

    @Test fun `legacy session excludes managed rows and null plot keeps its report scope`() {
        val old = tree("old", 20.0).copy(sessionId = null, placetteId = null)
        val result = RecordedDendrometryCalculator.calculate(
            report(listOf(old), plot = null, session = null), listOf(old, tree("managed", 30.0)))
        assertEquals(RecordedDendrometryStatus.AVAILABLE, result.status)
        assertEquals(1, result.recordedCount)
    }

    @Test fun `conflicting copies of one identity are not chosen arbitrarily`() {
        val row = tree("A", 20.0)
        val result = RecordedDendrometryCalculator.calculate(report(listOf(row)),
            listOf(row, row.copy(diamCm = 30.0)))
        assertEquals(RecordedDendrometryStatus.REPORT_MISMATCH, result.status)
        assertNull(result.recordedCount)
        assertTrue(result.diameterDistribution.isEmpty())
    }

    @Test fun `older report with changed count or same count other diameters is refused`() {
        val original = listOf(tree("A", 10.0), tree("B", 30.0))
        val frozen = report(original)
        for (rows in listOf(original.take(1), listOf(tree("A", 20.0), tree("B", 20.0)))) {
            val result = RecordedDendrometryCalculator.calculate(frozen, rows)
            assertEquals(RecordedDendrometryStatus.REPORT_MISMATCH, result.status)
            assertNull(result.basalAreaM2)
            assertNull(result.quadraticDiameterCm)
        }
    }

    @Test fun `same overall count cannot hide changed essence or observation dates`() {
        val row = tree("A", 20.0)
        val frozen = report(listOf(row))
        for (changed in listOf(row.copy(essenceCode = "TEST_PINE"), row.copy(timestamp = 2000))) {
            assertEquals(RecordedDendrometryStatus.REPORT_MISMATCH,
                RecordedDendrometryCalculator.calculate(frozen, listOf(changed)).status)
        }
    }

    @Test fun `zero recorded observations differs from invalid missing diameters`() {
        val empty = calculate(emptyList())
        assertEquals(RecordedDendrometryStatus.AVAILABLE, empty.status)
        assertEquals(0, empty.recordedCount)
        assertEquals(0.0, empty.basalAreaM2!!, 0.0)
        assertNull(empty.meanDiameterCm)
        val invalid = calculate(listOf(tree("bad", Double.NaN)))
        assertEquals(1, invalid.recordedCount)
        assertEquals(1, invalid.invalidDiameterCount)
        assertNull(invalid.basalAreaM2)
        assertNull(invalid.quadraticDiameterCm)
    }

    @Test fun `invalid values stay excluded with valid subset coverage explicit`() {
        val rows = listOf(tree("ok", 20.0), tree("nan", Double.NaN),
            tree("inf", Double.POSITIVE_INFINITY), tree("zero", 0.0), tree("neg", -20.0))
        val result = calculate(rows)
        assertEquals(RecordedDendrometryStatus.AVAILABLE, result.status)
        assertEquals(5, result.recordedCount)
        assertEquals(1, result.validDiameterCount)
        assertEquals(4, result.invalidDiameterCount)
        assertEquals(PI * 0.01, result.basalAreaM2!!, 1e-12)
    }

    @Test fun `overflow and positive diameter area underflow are unavailable not false zero`() {
        for (diameter in listOf(1e308, Double.MIN_VALUE)) {
            val result = calculate(listOf(tree("extreme", diameter)))
            assertEquals(RecordedDendrometryStatus.NUMERIC_ERROR, result.status)
            assertNull(result.basalAreaM2)
            assertNull(result.recordedCount)
        }
    }

    @Test fun `positive diameters cannot become zero under consistency tolerance`() {
        val rows = listOf(tree("tiny", 1e-5))
        val frozen = report(rows).copy(basalAreaM2 = 0.0)
        val result = RecordedDendrometryCalculator.calculate(frozen, rows)
        assertEquals(RecordedDendrometryStatus.REPORT_MISMATCH, result.status)
        assertNull(result.basalAreaM2)
    }

    @Test fun `legacy height and stored slenderness never acquire a total measured protocol`() {
        val row = tree("A", 20.0, 18.0).copy(coefficientElancement = 90.0)
        val result = calculate(listOf(row))
        assertNull(result.heightDiameterRatio)
        assertEquals(RecordedDendrometryReason.HEIGHT_PROTOCOL_UNDOCUMENTED,
            result.heightDiameterReason)
        assertNull(result.dominantSlenderness)
        assertNull(result.spacingPercent)
        assertEquals(RecordedDendrometryReason.STAND_PROTOCOL_UNDOCUMENTED,
            result.standIndicatorReason)
    }

    @Test fun `planned report area and missing height cannot enable stand indicators`() {
        val rows = listOf(tree("A", 20.0, null))
        val frozen = report(rows).copy(surfaceM2 = 500.0, surfaceReason = "CIRCULAR_PLOT")
        val result = RecordedDendrometryCalculator.calculate(frozen, rows)
        assertEquals(RecordedDendrometryStatus.AVAILABLE, result.status)
        assertNull(result.heightDiameterRatio)
        assertNull(result.dominantSlenderness)
        assertNull(result.spacingPercent)
    }

    @Test fun `changed sources duplicate summaries and invalid frozen numbers are refused`() {
        val row = tree("A", 20.0)
        val frozen = report(listOf(row))
        assertEquals(RecordedDendrometryStatus.REPORT_MISMATCH,
            RecordedDendrometryCalculator.calculate(frozen,
                listOf(row.copy(source = "OTHER"))).status)
        assertEquals(RecordedDendrometryStatus.REPORT_MISMATCH,
            RecordedDendrometryCalculator.calculate(frozen.copy(
                perEssence = frozen.perEssence + frozen.perEssence), listOf(row)).status)
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0)) {
            assertEquals(RecordedDendrometryStatus.NUMERIC_ERROR,
                RecordedDendrometryCalculator.calculate(frozen.copy(basalAreaM2 = bad),
                    listOf(row)).status)
        }
    }
}
