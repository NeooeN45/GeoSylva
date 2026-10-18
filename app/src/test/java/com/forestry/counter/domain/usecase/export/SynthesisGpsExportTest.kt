package com.forestry.counter.domain.usecase.export

import com.forestry.counter.domain.calculation.SynthesisParameters
import com.forestry.counter.domain.calculation.SynthesisReportCalculator
import com.forestry.counter.domain.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SynthesisGpsExportTest {
    @Test fun trace_positions_and_separate_segments_are_exported_without_a_bridge_between_pauses() {
        val trace = MartelageGpsTrace(points = listOf(point(100L, "1:100:100"), point(200L, "1:100:100"), point(500L, "1:500:500"), point(600L, "1:500:500")))
        val (value, count) = buildSynthesisGeoJson(document(MartelageGpsTraceCodec.encode(trace)))
        val features = Json.parseToJsonElement(value).jsonObject.getValue("features").jsonArray
        assertEquals(4, count)
        assertEquals(4, features.count { it.jsonObject.getValue("geometry").jsonObject["type"]!!.jsonPrimitive.content == "Point" })
        val lines = features.last().jsonObject.getValue("geometry").jsonObject
        assertEquals("MultiLineString", lines["type"]!!.jsonPrimitive.content)
        assertEquals(2, lines.getValue("coordinates").jsonArray.size)
        assertTrue(lines.getValue("coordinates").jsonArray.all { it.jsonArray.size == 2 })
    }

    @Test fun a_single_position_remains_a_point_and_does_not_invent_a_travel_segment() {
        val (value, count) = buildSynthesisGeoJson(document(MartelageGpsTraceCodec.encode(MartelageGpsTrace(points = listOf(point(100L, "1:100:100"))))))
        val features = Json.parseToJsonElement(value).jsonObject.getValue("features").jsonArray
        assertEquals(1, count); assertEquals(1, features.size)
        assertEquals("Point", features.single().jsonObject.getValue("geometry").jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test fun trace_with_unqualified_accuracy_is_not_exported_as_a_geographic_measurement() {
        val invalid = Json.encodeToString(MartelageGpsTrace(points = listOf(point(100L, "1:100:100").copy(accuracyM = 101.0))))
        val (value, count) = buildSynthesisGeoJson(document(invalid))
        val geo = Json.parseToJsonElement(value).jsonObject
        assertEquals(0, count); assertTrue(geo.getValue("features").jsonArray.isEmpty())
        assertEquals("INVALID", geo.getValue("metadata").jsonObject.getValue("gps_trace_status").jsonPrimitive.content)
    }

    @Test fun absent_trace_keeps_historical_document_serialized_input_bytes_unchanged() {
        val oldDocument = document(null)
        val json = Json { encodeDefaults = true }
        val encoded = json.encodeToString(oldDocument)
        assertFalse(Json.parseToJsonElement(encoded).jsonObject.containsKey("traceJson"))
        assertEquals(encoded, json.encodeToString(json.decodeFromString<SynthesisDocument>(encoded)))
    }

    private fun document(trace: String?): SynthesisDocument {
        val parcel = Parcelle("parcel", null, null, "Parcel", 1.0, null, null, null, null, null, null, null,
            null, null, null, null, null, null, createdAt = 1L, updatedAt = 2L)
        val report = SynthesisReportCalculator.calculate(parcel, emptyList(), emptyList(), emptyMap(), SynthesisParameters(),
            "session", "campaign", 1, 2L)
        return SynthesisDocument(report, false, null, null, JsonObject(emptyMap()), emptyList(), trace)
    }
    private fun point(time: Long, segment: String) = MartelageGpsPoint(46.0 + time / 1_000_000.0, 0.4,
        time, time, 4.0, segmentId = segment)
}
