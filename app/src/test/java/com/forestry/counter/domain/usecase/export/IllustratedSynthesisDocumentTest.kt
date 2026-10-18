package com.forestry.counter.domain.usecase.export

import com.forestry.counter.domain.calculation.SynthesisParameters
import com.forestry.counter.domain.calculation.SynthesisReportCalculator
import com.forestry.counter.domain.model.MartelageCalculationSnapshotV1
import com.forestry.counter.domain.model.MartelageSnapshotFingerprint
import com.forestry.counter.domain.model.MartelageSnapshotV1
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.model.Tige
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class IllustratedSynthesisDocumentTest {
    private val parcel = Parcelle("P", null, name = "TEST parcel", surfaceHa = 1.0,
        shape = "POLYGON ((0 0, 1 0, 1 1, 0 1, 0 0),(0.3 0.3,0.3 0.7,0.7 0.7,0.7 0.3,0.3 0.3))",
        slopePct = null, aspect = null, access = null, altitudeM = null, objectifType = null,
        objectifVal = null, tolerancePct = null, samplingMode = null, sampleAreaM2 = null,
        targetSpeciesCsv = null, srid = 4326, remarks = null, createdAt = 1, updatedAt = 1)
    private val plot = Placette("Q", "P", "TEST plot", "ENTIRE_PARCEL", null, 10000.0,
        "POINT (0.1 0.1)", createdAt = 1, updatedAt = 1)
    private val parameters = SynthesisParameters(diameterClasses = listOf(30, 40, 50), selectedPlotId = "Q")
    private fun tree(id: String = "T", diameter: Double = 40.0) = Tige(id, "P", "Q", "CH_PED", diameter,
        20.0, null, null, null, timestamp = 1, note = null, produit = null,
        fCoef = null, valueEur = null, sessionId = "S")
    private fun document(trees: List<Tige> = listOf(tree()), closed: Boolean = false): SynthesisDocument {
        val report = SynthesisReportCalculator.calculate(parcel, listOf(plot), trees,
            mapOf("CH_PED" to "TEST oak"), parameters, "S", "C", 1, 2)
        return SynthesisDocument(report, closed, if (closed) 3 else null,
            if (closed) MartelageSnapshotFingerprint.compute(parcel, listOf(plot), trees) else null,
            Json.encodeToJsonElement(parameters).jsonObject, trees)
    }
    private fun snapshot(doc: SynthesisDocument) = MartelageSnapshotV1(schemaVersion = 2,
        sessionId = "S", parcelleId = "P", campaignId = "C", revisionNumber = 1, previousSessionId = null,
        startedAt = 1, closedAt = 3, activeDurationMs = 2, auteur = null, parcelle = parcel,
        placettes = listOf(plot), tiges = doc.observations, placetteId = "Q",
        calculation = MartelageCalculationSnapshotV1(2, doc.report.engineVersion, doc.parameters,
            Json.encodeToJsonElement(doc.report).jsonObject, doc.sourceFingerprint!!))

    @Test fun frozen_classes_use_same_midpoints_and_exclude_invalid_diameters_without_recalculating_volume() {
        val doc = document(listOf(tree("one", 34.0), tree("boundary", 35.0), tree("three", 46.0), tree("invalid", 0.0)))
        val data = buildIllustratedSynthesisData(IllustratedSynthesisDocument(doc, parcel, listOf(plot)))
        assertEquals(listOf(IllustratedDiameterRow(30, 1), IllustratedDiameterRow(40, 1), IllustratedDiameterRow(50, 1)), data.diameters)
        assertEquals(1, data.invalidDiameters)
        assertNull(doc.report.volumeM3)
        assertEquals(0, doc.report.volumeComputedCount)
    }

    @Test fun closed_capture_ignores_current_renames_and_geometries_and_preserves_original_document_bytes() {
        val doc = document(closed = true)
        val original = Json { encodeDefaults = true }.encodeToString(doc)
        val captured = captureIllustratedSynthesisDocument(doc, snapshot(doc), parcel.copy(name = "changed", shape = null),
            listOf(plot.copy(name = "changed", centerWkt = null)))
        assertEquals(parcel, captured.parcel)
        assertEquals(listOf(plot), captured.plots)
        assertEquals(original, Json { encodeDefaults = true }.encodeToString(captured.document))
        assertEquals(parcel.shape, captured.parcel?.shape)
    }

    @Test fun a_changed_closed_geometry_or_observation_cannot_borrow_the_original_fingerprint() {
        val doc = document(closed = true)
        assertThrows(IllegalArgumentException::class.java) {
            captureIllustratedSynthesisDocument(doc, snapshot(doc).copy(parcelle = parcel.copy(shape = null)), parcel, listOf(plot))
        }
        assertThrows(IllegalArgumentException::class.java) {
            captureIllustratedSynthesisDocument(doc.copy(observations = listOf(tree(diameter = 50.0))), snapshot(doc), parcel, listOf(plot))
        }
    }

    @Test fun absent_frozen_snapshot_cannot_fall_back_to_mutable_geography() {
        assertThrows(IllegalArgumentException::class.java) {
            captureIllustratedSynthesisDocument(document(closed = true), null, parcel, listOf(plot))
        }
    }

    @Test fun a_closed_report_cannot_borrow_another_trace_or_calculation_identity() {
        val doc = document(closed = true)
        val frozen = snapshot(doc)
        assertThrows(IllegalArgumentException::class.java) {
            captureIllustratedSynthesisDocument(doc.copy(traceJson = "borrowed trace"), frozen, parcel, listOf(plot))
        }
        assertThrows(IllegalArgumentException::class.java) {
            captureIllustratedSynthesisDocument(doc, frozen.copy(calculation = frozen.calculation!!.copy(engineVersion = "different")), parcel, listOf(plot))
        }
    }

    @Test fun polygon_holes_and_only_explicit_valid_positions_reach_the_map() {
        val doc = document(listOf(tree().copy(gpsWkt = "prefix POINT (0.2 0.2)")))
        val map = buildIllustratedSynthesisData(IllustratedSynthesisDocument(doc, parcel, listOf(plot))).map!!
        assertEquals(1, map.parcel.size)
        assertEquals(1, map.parcel.single().holes.size)
        assertEquals(listOf(IllustratedMapPoint(0.1, 0.1)), map.plotCenters)
        assertTrue(map.trees.isEmpty())
        assertTrue(map.footprints.isEmpty())
        assertTrue(map.traceSegments.isEmpty())
    }

    @Test fun unknown_projection_projected_coordinates_and_invalid_boundaries_are_unavailable() {
        for (p in listOf(parcel.copy(srid = null), parcel.copy(srid = 2154), parcel.copy(shape = "invalid"))) {
            assertNull(buildIllustratedSynthesisData(IllustratedSynthesisDocument(document(), p, listOf(plot))).map)
        }
    }

    @Test fun only_entire_finite_geographic_point_wkt_is_accepted() {
        assertEquals(IllustratedMapPoint(-0.5, 46.2), illustratedMapPoint("POINT Z (-0.5 46.2 20)"))
        assertEquals(IllustratedMapPoint(-0.5, 46.2), illustratedMapPoint("POINT (-0.5 46.2)"))
        for (raw in listOf("before POINT (1 2)", "POINT (1 2) after", "POINT (181 2)", "POINT (1 -91)",
            "POINT (1e999 2)", "POINT Z (1 2)", "POINT (1 2 3)", "POINT Z (1 2 1e999)")) {
            assertNull(raw, illustratedMapPoint(raw))
        }
    }

    @Test fun duplicate_observations_wrong_scope_and_mismatched_counts_cannot_inflate_charts() {
        val doc = document()
        assertThrows(IllegalArgumentException::class.java) {
            IllustratedSynthesisDocument(doc.copy(observations = listOf(tree(), tree())), parcel, listOf(plot))
        }
        assertThrows(IllegalArgumentException::class.java) {
            IllustratedSynthesisDocument(doc.copy(observations = listOf(tree().copy(placetteId = "another"))), parcel, listOf(plot))
        }
        assertThrows(IllegalArgumentException::class.java) {
            IllustratedSynthesisDocument(doc, parcel, listOf(plot.copy(parcelleId = "another")))
        }
    }

    @Test fun malformed_parameters_cannot_silently_choose_new_diameter_classes() {
        val doc = document().copy(parameters = Json.parseToJsonElement("{\"diameterClasses\":\"not an array\"}").jsonObject)
        assertThrows(Exception::class.java) {
            buildIllustratedSynthesisData(IllustratedSynthesisDocument(doc, parcel, listOf(plot)))
        }
    }

    @Test fun captured_timezone_is_serialized_and_invalid_zone_is_rejected() {
        val input = IllustratedSynthesisDocument(document(), parcel, listOf(plot), timeZoneId = "Europe/Paris")
        val json = Json { encodeDefaults = true }
        assertEquals("Europe/Paris", json.decodeFromString<IllustratedSynthesisDocument>(json.encodeToString(input)).timeZoneId)
        assertThrows(java.time.DateTimeException::class.java) {
            input.copy(timeZoneId = "not-a-timezone")
        }
    }
}
