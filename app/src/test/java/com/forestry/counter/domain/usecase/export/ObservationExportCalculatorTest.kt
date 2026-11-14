package com.forestry.counter.domain.usecase.export

import com.forestry.counter.domain.calculation.SynthesisParameters
import com.forestry.counter.domain.calculation.SynthesisReportCalculator
import com.forestry.counter.domain.calculation.tarifs.TarifSelection
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Tige
import org.junit.Assert.*
import org.junit.Test

class ObservationExportCalculatorTest {
    private val parcel = Parcelle("P", "F", name = "Parcel", surfaceHa = 2.0, shape = null,
        slopePct = null, aspect = null, access = null, altitudeM = null, objectifType = null,
        objectifVal = null, tolerancePct = null, samplingMode = null, sampleAreaM2 = null,
        targetSpeciesCsv = null, srid = null, remarks = null)
    private fun tree(id: String = "T", diameter: Double = 40.0, height: Double? = 20.0,
        session: String? = "S", value: Double? = null) = Tige(id, "P", "W", "CH_SESSILE", diameter,
        height, null, null, null, timestamp = 1000L, note = null, produit = "BO", fCoef = 0.7,
        valueEur = value, sessionId = session)
    private fun summary(trees: List<Tige>, classes: List<Int> = listOf(20, 40),
        selection: TarifSelection? = TarifSelection("ALGAN")) = ObservationExportCalculator.synthesis(
        "CH_SESSILE", classes, trees, parcel, emptyList(), selection)

    @Test fun `CSV uses the same unavailable volume and reason as current Synthese`() {
        val observation = tree()
        val selection = TarifSelection("ALGAN")
        val report = SynthesisReportCalculator.calculate(parcel, emptyList(), listOf(observation), emptyMap(),
            SynthesisParameters(selection), "S", null, null, 0L)
        val exported = ObservationExportCalculator.volume(observation, parcel, emptyList(), selection)
        assertNull(exported.m3)
        assertEquals(report.volumeM3, exported.m3)
        assertEquals(report.uncomputedReasons, exported.reasons)
        assertEquals("UNQUALIFIED_TARIF:1", exported.status)
    }

    @Test fun `selecting a method number or species override cannot activate a fallback`() {
        val selections = listOf(TarifSelection("ALGAN"), TarifSelection("SCHAEFFER_1E", schaefferNumero = 4),
            TarifSelection("IFN_RAPIDE", ifnNumero = 4),
            TarifSelection("ALGAN", essenceOverrides = mapOf("CH_SESSILE" to "SCHAEFFER_2E")))
        selections.forEach { selection ->
            val exported = summary(listOf(tree()), selection = selection)
            assertNull(exported.totals.vTotal)
            assertNull(exported.perClass.single { it.count > 0 }.vSum)
            assertEquals(0, exported.totals.volumeComputedCount)
            assertEquals(mapOf("UNQUALIFIED_TARIF" to 1), exported.volumeByClass.getValue(40).reasons)
        }
    }

    @Test fun `missing tariff is distinguishable from an unqualified selection`() {
        val exported = summary(listOf(tree("A"), tree("B")), selection = null)
        assertNull(exported.totals.vTotal)
        assertEquals(mapOf("MISSING_TARIF" to 2), exported.volumeByClass.getValue(40).reasons)
    }

    @Test fun `class boundaries and measured diameters remain descriptive`() {
        val exported = summary(listOf(tree("A", 14.9), tree("B", 15.0), tree("C", 22.0)), listOf(20, 10, 20))
        assertEquals(listOf(10, 20), exported.perClass.map { it.diamClass })
        assertEquals(listOf(1, 2), exported.perClass.map { it.count })
        assertEquals(3, exported.totals.nTotal)
        assertEquals((14.9 + 15.0 + 22.0) / 3, exported.totals.dmWeighted!!, 1e-9)
        assertTrue(exported.perClass.all { it.vSum == null })
    }

    @Test fun `project export keeps live observations from legacy and separate sessions`() {
        val exported = summary(listOf(tree("A", session = null), tree("B", session = "S1"), tree("C", session = "S2")))
        assertEquals(3, exported.totals.nTotal)
        assertEquals(3, exported.perClass.sumOf { it.count })
        assertEquals(mapOf("UNQUALIFIED_TARIF" to 3), exported.volumeByClass.getValue(40).reasons)
        assertNull(exported.totals.vTotal)
    }

    @Test fun `missing heights are not replaced by configured class or default heights`() {
        val noHeights = summary(listOf(tree("A", height = null), tree("B", height = null)))
        assertNull(noHeights.totals.hMean)
        assertNull(noHeights.perClass.single { it.count > 0 }.hMean)
        val partial = summary(listOf(tree("A", height = 18.0), tree("B", height = null), tree("C", height = Double.NaN)))
        assertEquals(18.0, partial.totals.hMean!!, 0.0)
        assertEquals(18.0, partial.perClass.single { it.count > 0 }.hMean!!, 0.0)
    }

    @Test fun `only entered finite nonnegative values are exported without market estimates`() {
        val exported = summary(listOf(tree("A", value = 12.0), tree("B", value = 0.0), tree("C"),
            tree("D", value = Double.NaN), tree("E", value = -3.0)))
        assertEquals(12.0, exported.perClass.single { it.count > 0 }.valueSumEur!!, 0.0)
        assertNull(exported.totals.vTotal)
        assertNull(summary(listOf(tree())).perClass.single { it.count > 0 }.valueSumEur)
        assertEquals(0.0, summary(listOf(tree(value = 0.0))).perClass.single { it.count > 0 }.valueSumEur!!, 0.0)
    }

    @Test fun `invalid diameters have a reason and never become a volume of zero`() {
        val observation = tree(diameter = Double.NaN)
        val exported = ObservationExportCalculator.volume(observation, parcel, emptyList(), TarifSelection("ALGAN"))
        assertNull(exported.m3)
        assertEquals(mapOf("INVALID_DIAMETER" to 1), exported.reasons)
        val aggregate = summary(listOf(observation, tree("zero", diameter = 0.0), tree("valid")))
        assertEquals(3, aggregate.totals.nTotal)
        assertEquals(2, aggregate.volumeByClass.getValue(0).reasons["INVALID_DIAMETER"])
        assertEquals(40.0, aggregate.totals.dmWeighted!!, 0.0)
        assertNull(aggregate.totals.vTotal)
    }

    @Test fun `unknown deleted or mismatched parcel does not invent context`() {
        listOf(null, parcel.copy(deletedAt = 1L), parcel.copy(id = "OTHER")).forEach { context ->
            val exported = ObservationExportCalculator.volume(tree(), context, emptyList(), TarifSelection("ALGAN"))
            assertNull(exported.m3)
            assertEquals(mapOf("MISSING_PARCEL" to 1), exported.reasons)
        }
    }

    @Test fun `aggregate follows Synthese duplicate and deleted observation policy`() {
        val exported = summary(listOf(tree(), tree(), tree("deleted").copy(deletedAt = 1L)))
        assertEquals(1, exported.totals.nTotal)
        assertEquals(1, exported.volumeByClass.getValue(40).reasons["UNQUALIFIED_TARIF"])
        val deletedRaw = ObservationExportCalculator.volume(tree().copy(deletedAt = 1L), parcel, emptyList(), null)
        assertEquals("OBSERVATION_DELETED:1", deletedRaw.status)
        assertNull(deletedRaw.m3)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `aggregate rejects mixed parcel groups`() {
        summary(listOf(tree("A"), tree("B").copy(parcelleId = "OTHER")))
    }
}
