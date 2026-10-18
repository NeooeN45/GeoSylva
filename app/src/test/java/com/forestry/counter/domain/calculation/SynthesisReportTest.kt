package com.forestry.counter.domain.calculation

import com.forestry.counter.domain.calculation.tarifs.TarifSelection
import com.forestry.counter.domain.calculation.tarifs.TarifMethod
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.usecase.export.SynthesisDocument
import com.forestry.counter.domain.location.PlotSamplingPlan
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class SynthesisReportTest {
    private val parcel = Parcelle("P", "F", name = "Parcel", surfaceHa = 2.0, shape = null,
        slopePct = null, aspect = null, access = null, altitudeM = null, objectifType = null,
        objectifVal = null, tolerancePct = null, samplingMode = null, sampleAreaM2 = null,
        targetSpeciesCsv = null, srid = null, remarks = null)
    private val whole = Placette("W", "P", "Entire", "ENTIRE_PARCEL", null, 20_000.0, null)
    private val circle = Placette("C", "P", "Circle", "CIRC", 10.0, PI * 100.0, "POINT(0 0)")
    private fun tree(id: String = "T", plot: String? = "W", session: String? = "S", diameter: Double = 40.0, height: Double? = 20.0,
        value: Double? = null) = Tige(id, "P", plot, "CH_SESSILE", diameter, height, "POINT(1 46)", null, null,
        timestamp = 1000L, note = null, produit = null, fCoef = null, valueEur = value, sessionId = session)
    private fun report(trees: List<Tige>, parameters: SynthesisParameters = SynthesisParameters(TarifSelection("ALGAN")),
        plots: List<Placette> = listOf(whole, circle), session: String? = "S") = SynthesisReportCalculator.calculate(
        parcel, plots, trees, mapOf("CH_SESSILE" to "Oak"), parameters, session, "campaign", 2, 2000L)

    @Test fun `campaign revisions and other parcels are never summed`() {
        val result = report(listOf(tree(), tree("old", session = "OLD"), tree("other").copy(parcelleId = "OTHER")))
        assertEquals(1, result.observationCount)
        assertEquals(2, result.revisionNumber)
    }
    @Test fun `legacy selection excludes managed observations`() {
        assertEquals(1, report(listOf(tree("old", session = null), tree()), session = null).observationCount)
    }
    @Test fun `deleted trees and duplicate primary ids do not increase totals`() {
        val trees = listOf(tree(), tree(), tree("deleted").copy(deletedAt = 1))
        assertEquals(1, report(trees).observationCount)
    }
    @Test fun `whole plot inherits parcel area and correct basal area units`() {
        val result = report(listOf(tree()))
        assertEquals(20_000.0, result.surfaceM2!!, 1e-9)
        assertEquals(PI * 0.2 * 0.2, result.basalAreaM2, 1e-9)
        assertEquals(PI * 0.2 * 0.2 / 2.0, result.basalAreaPerHa!!, 1e-9)
    }
    @Test fun `multiple plots cannot fabricate sample coverage or per hectare figures`() {
        val result = report(listOf(tree(), tree("C", plot = "C")))
        assertNull(result.surfaceM2)
        assertNull(result.volumePerHa)
        assertNull(result.observationsPerHa)
        assertEquals(2, result.observationCount)
    }
    @Test fun `plot selection is isolated and circle radius supplies verified area`() {
        val result = report(listOf(tree(), tree("C", plot = "C")), SynthesisParameters(TarifSelection("ALGAN"), selectedPlotId = "C"))
        assertEquals(1, result.observationCount)
        assertEquals(PI * 100.0, result.surfaceM2!!, 1e-9)
    }
    @Test fun `unknown plots never use parcel area as sample area`() {
        assertNull(report(listOf(tree(plot = null))).surfaceM2)
        assertNull(report(listOf(tree(plot = "missing"))).surfaceM2)
    }
    @Test fun `changed parcel and inconsistent circle areas disable per hectare results`() {
        assertNull(synthesisSurface(parcel.copy(surfaceHa = 3.0), listOf(whole), listOf(tree()), null).m2)
        assertNull(synthesisSurface(parcel, listOf(circle.copy(surfaceM2 = 500.0)), listOf(tree(plot = "C")), null).m2)
    }
    @Test fun `a boundary crossing target footprint keeps observations without fabricating hectares or volumes`() {
        val warningPlan = PlotSamplingPlan(placement = "MANUAL", footprintShape = "CIRCLE",
            footprintAreaM2 = circle.surfaceM2!!, boundaryWarning = true).toJson()
        val result = report(listOf(tree(plot = "C")), plots = listOf(circle.copy(samplingPlanJson = warningPlan)))
        assertEquals("SAMPLING_BOUNDARY_UNKNOWN", result.surfaceReason)
        assertNull(result.surfaceM2); assertNull(result.volumePerHa); assertNull(result.basalAreaPerHa)
        assertEquals(1, result.observationCount)
        assertEquals(0, result.volumeComputedCount); assertNull(result.volumeM3)
        assertEquals(1, result.uncomputedReasons["UNQUALIFIED_TARIF"])
    }
    @Test fun `a qualified footprint without a boundary warning preserves the verified circle surface`() {
        val plan = PlotSamplingPlan(placement = "MANUAL", footprintShape = "CIRCLE",
            footprintAreaM2 = circle.surfaceM2!!, boundaryPolicy = "FULLY_INSIDE").toJson()
        val result = report(listOf(tree(plot = "C")), plots = listOf(circle.copy(samplingPlanJson = plan)))
        assertEquals(PI * 100.0, result.surfaceM2!!, 1e-9)
        assertEquals("CIRCULAR_PLOT", result.surfaceReason)
        assertNotNull(result.basalAreaPerHa)
        assertNull(result.volumePerHa)
    }
    @Test fun `volume requires explicitly configured method`() {
        val result = report(listOf(tree()), SynthesisParameters())
        assertNull(result.volumeM3)
        assertEquals(1, result.uncomputedReasons["MISSING_TARIF"])
        assertEquals(0, result.volumeComputedCount)
    }
    @Test fun `selecting historical Algan cannot activate an unqualified formula`() {
        val result = report(listOf(tree()))
        assertNull(result.volumeM3)
        assertEquals(0, result.volumeComputedCount)
        assertEquals(1, result.uncomputedReasons["UNQUALIFIED_TARIF"])
        assertFalse(result.isVolumeComplete)
        assertTrue(result.methods.isEmpty())
    }
    @Test fun `raw height presence cannot qualify a tariff and its absence never produces zero volume`() {
        val result = report(listOf(tree(), tree("noheight", height = null)))
        assertNull(result.volumeM3)
        assertEquals(0, result.volumeComputedCount)
        assertFalse(result.isVolumeComplete)
        assertEquals(2, result.uncomputedReasons["UNQUALIFIED_TARIF"])
        assertEquals(1, result.measuredHeightCount)
    }
    @Test fun `one entry method remains unavailable with or without a tariff number and no height`() {
        val noNumber = report(listOf(tree(height = null)), SynthesisParameters(TarifSelection("SCHAEFFER_1E")))
        assertNull(noNumber.volumeM3)
        assertEquals(1, noNumber.uncomputedReasons["UNQUALIFIED_TARIF"])
        val numbered = report(listOf(tree(height = null)), SynthesisParameters(TarifSelection("SCHAEFFER_1E", schaefferNumero = 8)))
        assertNull(numbered.volumeM3)
        assertEquals(0, numbered.volumeComputedCount)
        assertEquals(1, numbered.uncomputedReasons["UNQUALIFIED_TARIF"])
    }
    @Test fun `known and unknown species and explicit form factor cannot qualify a method`() {
        val unknown = report(listOf(tree().copy(essenceCode = "UNREGISTERED")))
        assertNull(unknown.volumeM3)
        assertEquals(1, unknown.uncomputedReasons["UNQUALIFIED_TARIF"])
        for (factor in listOf(null, 0.45)) {
            val coefficient = report(listOf(tree().copy(fCoef = factor)), SynthesisParameters(TarifSelection("COEF_FORME")))
            assertNull(coefficient.volumeM3)
            assertEquals(0, coefficient.volumeComputedCount)
            assertEquals(1, coefficient.uncomputedReasons["UNQUALIFIED_TARIF"])
        }
    }
    @Test fun `invalid measurements are counted and cannot poison arithmetic`() {
        val result = report(listOf(tree(diameter = Double.NaN), tree("B", diameter = -3.0), tree("C", height = Double.POSITIVE_INFINITY)))
        assertEquals(3, result.observationCount)
        assertEquals(2, result.invalidDiameterCount)
        assertEquals(2, result.measuredHeightCount)
        assertEquals(0, result.volumeComputedCount)
        assertTrue(result.basalAreaM2.isFinite())
        assertEquals(2, result.uncomputedReasons["INVALID_DIAMETER"])
        assertEquals(1, result.uncomputedReasons["UNQUALIFIED_TARIF"])
    }
    @Test fun `fixed and sampled class heights stay recorded without activating cubage`() {
        val rows = listOf(tree(), tree("reference", height = 30.0), tree("unmeasured", height = null))
        val originalRows = rows.map { it.copy() }
        for (mode in listOf(HeightModeEntry("CH_SESSILE", 40, "FIXED", 25.0), HeightModeEntry("CH_SESSILE", 40, "SAMPLES"))) {
            val params = SynthesisParameters(TarifSelection("ALGAN"), listOf(40), listOf(mode))
            val result = report(rows, params)
            assertNull(result.volumeM3)
            assertEquals(0, result.volumeComputedCount)
            assertEquals(3, result.uncomputedReasons["UNQUALIFIED_TARIF"])
            assertEquals(2, result.measuredHeightCount)
            assertEquals(listOf(mode), params.heightModes)
            assertEquals(originalRows, rows)
        }
    }
    @Test fun `every historical method stays unavailable despite explicit numbers height and form factor`() {
        for (method in TarifMethod.entries) {
            val parameters = SynthesisParameters(TarifSelection(method.code, schaefferNumero = 8, ifnNumero = 4))
            val result = report(listOf(tree().copy(fCoef = 0.45)), parameters)
            assertNull(method.code, result.volumeM3)
            assertNull(method.code, result.volumePerHa)
            assertEquals(method.code, 0, result.volumeComputedCount)
            assertFalse(method.code, result.isVolumeComplete)
            assertTrue(method.code, result.methods.isEmpty())
            assertEquals(method.code, mapOf("UNQUALIFIED_TARIF" to 1), result.uncomputedReasons)
            assertTrue(method.code, result.perEssence.all { it.volumeM3 == null && it.volumeComputedCount == 0 })
            assertEquals(method.code, parameters.tarifSelection?.method)
        }
    }
    @Test fun `unknown selected method is unqualified rather than silently absent or replaced`() {
        for (method in listOf("UNREGISTERED", " algan ", "schaeffer_1e")) {
            val result = report(listOf(tree()), SynthesisParameters(TarifSelection(method)))
            assertNull(result.volumeM3)
            assertEquals(mapOf("UNQUALIFIED_TARIF" to 1), result.uncomputedReasons)
            assertTrue(result.methods.isEmpty())
        }
    }
    @Test fun `missing blank and whitespace method selections remain distinct from an unqualified choice`() {
        for (selection in listOf(null, TarifSelection(""), TarifSelection(" \t\n"))) {
            val result = report(listOf(tree()), SynthesisParameters(selection))
            assertNull(result.volumeM3)
            assertEquals(mapOf("MISSING_TARIF" to 1), result.uncomputedReasons)
        }
    }
    @Test fun `per species choices are refused in their selected scope and never listed as applied`() {
        val parameters = SynthesisParameters(TarifSelection("", essenceOverrides = mapOf(" ch_sessile " to "IFN_RAPIDE")))
        val rows = listOf(tree(), tree("unknown").copy(essenceCode = "UNREGISTERED"))
        val result = report(rows, parameters)
        assertEquals(mapOf("UNQUALIFIED_TARIF" to 1, "MISSING_TARIF" to 1), result.uncomputedReasons)
        assertTrue(result.methods.isEmpty())
        assertNull(result.volumeM3)
        assertEquals(mapOf(" ch_sessile " to "IFN_RAPIDE"), parameters.tarifSelection?.essenceOverrides)
        assertTrue(result.perEssence.all { it.volumeM3 == null && it.volumeComputedCount == 0 })
    }
    @Test fun `an unknown override cannot fall back to a known global tariff`() {
        val parameters = SynthesisParameters(TarifSelection("SCHAEFFER_1E", schaefferNumero = 8,
            essenceOverrides = mapOf("CH_SESSILE" to "UNREGISTERED")))
        val result = report(listOf(tree()), parameters)
        assertEquals(mapOf("UNQUALIFIED_TARIF" to 1), result.uncomputedReasons)
        assertNull(result.volumeM3)
        assertTrue(result.methods.isEmpty())
    }
    @Test fun `source policy text cannot authorize cubage or masquerade as a qualified catalog`() {
        for (policy in listOf(SynthesisParameters().sourcePolicy, SynthesisParameters.CURRENT_SOURCE_POLICY, "ACCEPTED_INPUTS")) {
            val result = report(listOf(tree()), SynthesisParameters(TarifSelection("ALGAN"), sourcePolicy = policy))
            assertNull(result.volumeM3)
            assertEquals(mapOf("UNQUALIFIED_TARIF" to 1), result.uncomputedReasons)
        }
    }
    @Test fun `refused volumes keep diameter descriptions and entered zero and positive values`() {
        val result = report(listOf(tree(value = 0.0), tree("B", diameter = 30.0, value = 12.5)))
        assertEquals(2, result.observationCount)
        assertEquals(2, result.measuredHeightCount)
        assertEquals(35.0, result.meanDiameterCm!!, 0.0)
        assertEquals(PI * (0.2 * 0.2 + 0.15 * 0.15), result.basalAreaM2, 1e-9)
        assertEquals(12.5, result.enteredValueEur!!, 0.0)
        assertEquals(2, result.enteredValueCount)
        assertEquals(2, result.perEssence.single().enteredValueCount)
        assertNull(result.volumeM3)
    }
    @Test fun `empty observations remain unknown without fabricated refusal or zero volume`() {
        val result = report(emptyList())
        assertNull(result.volumeM3)
        assertEquals(0, result.volumeComputedCount)
        assertFalse(result.isVolumeComplete)
        assertTrue(result.methods.isEmpty())
        assertTrue(result.uncomputedReasons.isEmpty())
    }
    @Test fun `new calculation version is explicit while omitted historical engine still decodes as version two`() {
        val result = report(listOf(tree()))
        assertEquals("geosylva-observation-synthesis-3", result.engineVersion)
        val json = Json { encodeDefaults = true }
        val withoutEngine = json.encodeToString(result).replace("\"engineVersion\":\"${result.engineVersion}\",", "")
        assertEquals("geosylva-observation-synthesis-2", json.decodeFromString(SynthesisReport.serializer(), withoutEngine).engineVersion)
        val historicalParameters = json.decodeFromString(SynthesisParameters.serializer(), "{}")
        assertEquals("measured-or-explicit-class-height;explicit-tarif;entered-values-only", historicalParameters.sourcePolicy)
        assertNotEquals(historicalParameters.sourcePolicy, SynthesisParameters.CURRENT_SOURCE_POLICY)
    }
    @Test fun `class midpoint belongs to upper class as existing calculator`() {
        assertEquals(40, synthesisDiameterClass(37.5, listOf(35, 40)))
        assertEquals(35, synthesisDiameterClass(37.49, listOf(35, 40)))
        assertEquals(38, synthesisDiameterClass(37.5, emptyList()))
    }
    @Test fun `economics only includes actually recorded finite nonnegative values`() {
        val result = report(listOf(tree(value = 0.0), tree("B", value = 12.5), tree("C", value = -20.0), tree("D", value = Double.NaN)))
        assertEquals(12.5, result.enteredValueEur!!, 1e-9)
        assertEquals(2, result.enteredValueCount)
        assertNull(report(listOf(tree())).enteredValueEur)
    }
    @Test fun `GPS coverage rejects malformed and out of range coordinates`() {
        val result = report(listOf(tree(), tree("B").copy(gpsWkt = "POINT(200 46)"), tree("C").copy(gpsWkt = "POINT(NaN 46)")))
        assertEquals(1, result.locatedCount)
    }
    @Test fun `serialized report is independent of future parameter changes`() {
        val original = report(listOf(tree()))
        val json = Json { encodeDefaults = true }
        val saved = json.encodeToString(original)
        val changed = report(listOf(tree(height = 40.0)), SynthesisParameters(TarifSelection("FGH")))
        assertNull(changed.volumeM3)
        assertEquals(original, json.decodeFromString(SynthesisReport.serializer(), saved))
    }
    @Test fun `closed document requires identity and fingerprint and isolates campaign`() {
        val result = report(listOf(tree()))
        val parameters = Json.encodeToJsonElement(SynthesisParameters()).jsonObject
        try { SynthesisDocument(result, true, 3000, null, parameters, listOf(tree())); fail() } catch (_: IllegalArgumentException) { }
        try { SynthesisDocument(result, true, 3000, "sha", parameters, listOf(tree(session = "OTHER"))); fail() } catch (_: IllegalArgumentException) { }
        assertTrue(SynthesisDocument(result, true, 3000, "sha", parameters, listOf(tree())).closed)
    }

    @Test fun `selected plot name is captured and survives a later rename`() {
        val parameters = SynthesisParameters(selectedPlotId = "C")
        val selected = circle.copy(name = "TEST 1")
        val original = report(listOf(tree(plot = "C")), parameters, listOf(whole, selected))
        assertEquals("TEST 1", original.selectedPlotName)
        val json = Json { encodeDefaults = true }
        val savedReport = json.encodeToString(original)
        val later = report(listOf(tree(plot = "C")), parameters, listOf(selected.copy(name = "Renamed")))
        assertEquals("Renamed", later.selectedPlotName)
        assertEquals("TEST 1", json.decodeFromString(SynthesisReport.serializer(), savedReport).selectedPlotName)
    }

    @Test fun `a matching plot id from another parcel cannot label the report`() {
        val result = report(listOf(tree(plot = "C")), SynthesisParameters(selectedPlotId = "C"),
            listOf(circle.copy(parcelleId = "OTHER", name = "Foreign name")))
        assertNull(result.selectedPlotName)
    }

    @Test fun `deleted and ambiguous selected plots cannot label the report`() {
        val parameters = SynthesisParameters(selectedPlotId = "C")
        assertNull(report(listOf(tree(plot = "C")), parameters,
            listOf(circle.copy(deletedAt = 1L))).selectedPlotName)
        assertNull(report(listOf(tree(plot = "C")), parameters,
            listOf(circle, circle.copy(name = "Duplicate"))).selectedPlotName)
    }

    @Test fun `blank missing and implicit plot names stay absent`() {
        val parameters = SynthesisParameters(selectedPlotId = "C")
        assertNull(report(listOf(tree(plot = "C")), parameters,
            listOf(circle.copy(name = " \t\n"))).selectedPlotName)
        assertNull(report(listOf(tree(plot = "C")), parameters, emptyList()).selectedPlotName)
        assertNull(report(listOf(tree(plot = "C")), plots = listOf(circle)).selectedPlotName)
    }

    @Test fun `historical report and closed document JSON remain byte identical`() {
        // Fixed historical wire input, not produced from the updated serializer under test.
        val legacyReport = """{"schemaVersion":1,"engineVersion":"geosylva-observation-synthesis-2","parcelId":"P","parcelName":"Parcel","selectedPlotId":"W","campaignId":"campaign","sessionId":"S","revisionNumber":1,"generatedAt":2000,"surfaceM2":null,"surfaceReason":"MISSING_PLOT","observationCount":0,"invalidDiameterCount":0,"measuredHeightCount":0,"locatedCount":0,"qualityCount":0,"volumeComputedCount":0,"basalAreaM2":0.0,"volumeM3":null,"enteredValueEur":null,"enteredValueCount":0,"meanDiameterCm":null,"quadraticDiameterCm":null,"firstObservationAt":null,"lastObservationAt":null,"perEssence":[],"categories":{},"methods":[],"uncomputedReasons":{},"sourceReferences":[]}"""
        val json = Json { encodeDefaults = true }
        val decoded = json.decodeFromString(SynthesisReport.serializer(), legacyReport)
        assertNull(decoded.selectedPlotName)
        assertArrayEquals(legacyReport.toByteArray(Charsets.UTF_8), json.encodeToString(decoded).toByteArray(Charsets.UTF_8))
        val legacyDocument = """{"report":$legacyReport,"closed":true,"closedAt":3000,"sourceFingerprint":"legacy-sha","parameters":{},"observations":[]}"""
        val document = json.decodeFromString(SynthesisDocument.serializer(), legacyDocument)
        assertNull(document.report.selectedPlotName)
        assertArrayEquals(legacyDocument.toByteArray(Charsets.UTF_8), json.encodeToString(document).toByteArray(Charsets.UTF_8))
    }

    @Test fun `historical nonnull volume and applied method remain byte identical without recubage`() {
        // Fixed historical input; neither the new calculator nor the current engine produces it.
        val legacyReport = """{"schemaVersion":1,"engineVersion":"geosylva-observation-synthesis-2","parcelId":"P","parcelName":"Parcel","selectedPlotId":"W","campaignId":"campaign","sessionId":"S","revisionNumber":1,"generatedAt":2000,"surfaceM2":20000.0,"surfaceReason":"ENTIRE_PARCEL","observationCount":1,"invalidDiameterCount":0,"measuredHeightCount":1,"locatedCount":1,"qualityCount":0,"volumeComputedCount":1,"basalAreaM2":0.12566370614359174,"volumeM3":0.89123,"enteredValueEur":12.5,"enteredValueCount":1,"meanDiameterCm":40.0,"quadraticDiameterCm":40.0,"firstObservationAt":1000,"lastObservationAt":1000,"perEssence":[{"code":"CH_SESSILE","name":"Oak","count":1,"basalAreaM2":0.12566370614359174,"volumeM3":0.89123,"volumeComputedCount":1,"measuredHeightCount":1,"enteredValueEur":12.5,"enteredValueCount":1}],"categories":{},"methods":["ALGAN"],"uncomputedReasons":{},"sourceReferences":[]}"""
        val json = Json { encodeDefaults = true }
        val historical = json.decodeFromString(SynthesisReport.serializer(), legacyReport)
        assertEquals("geosylva-observation-synthesis-2", historical.engineVersion)
        assertEquals(0.89123, historical.volumeM3!!, 0.0)
        assertEquals(0.89123, historical.perEssence.single().volumeM3!!, 0.0)
        assertEquals(listOf("ALGAN"), historical.methods)
        assertTrue(historical.isVolumeComplete)
        assertNull(report(listOf(tree())).volumeM3)
        assertArrayEquals(legacyReport.toByteArray(Charsets.UTF_8), json.encodeToString(historical).toByteArray(Charsets.UTF_8))
    }
}
