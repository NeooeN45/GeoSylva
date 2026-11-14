package com.forestry.counter.domain.location.trace

import com.forestry.counter.domain.repository.CartographicTraceDocument
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Nouveau test : une portion d'une seule observation reste un point, sans métadonnées inventées. */
class TraceGeoJsonExportTest {
    @Test
    fun `singleton portion preserves longitude latitude and absent clock metadata`() {
        val recorder = SegmentedTraceRecorder("TEST-trace", TraceCapturePolicy(10.0, 1_000L, 0.0, 1_000L))
        val first = requireNotNull(recorder.start(100L))
        recorder.accept(first, fix(45.0, 5.0, 101L), 101L)
        recorder.accept(first, fix(45.001, 5.0, 102L), 102L)
        recorder.pause(103L)
        val resumed = requireNotNull(recorder.resume(104L))
        recorder.accept(resumed, fix(46.0, 6.0, 105L), 105L)
        val snapshot = recorder.stop(106L)
        val document = CartographicTraceDocument("TEST-trace", "TEST chêne", snapshot, true, 1L, 1L, 1L, null)

        val exported = Json.parseToJsonElement(TraceGeoJsonExport.encode(document)).jsonObject
        val features = exported.getValue("features").jsonArray
        assertEquals(2, features.size)
        val singleton = features.last().jsonObject
        val geometry = singleton.getValue("geometry").jsonObject
        assertEquals("Point", geometry.getValue("type").jsonPrimitive.content)
        assertEquals(listOf(6.0, 46.0), geometry.getValue("coordinates").jsonArray.map { it.jsonPrimitive.double })
        val observation = singleton.getValue("properties").jsonObject
            .getValue("observations").jsonArray.single().jsonObject
        assertFalse(observation.containsKey("timestampMillis"))
        assertFalse(observation.containsKey("clockDomain"))
        assertEquals(4.0, observation.getValue("reportedAccuracyM").jsonPrimitive.double, 0.0)
        assertEquals("PHONE_LOCATION", observation.getValue("source").jsonPrimitive.content)
        val properties = exported.getValue("properties").jsonObject
        assertEquals("TEST chêne", properties.getValue("name").jsonPrimitive.content)
        assertEquals(JsonNull, properties.getValue("overallUncertaintyM"))
        assertEquals(snapshot.portions.first().lengthM, properties.getValue("horizontalLengthM").jsonPrimitive.double, 0.0)
    }

    private fun fix(latitude: Double, longitude: Double, nanos: Long) = TraceFix(
        latitude, longitude, 4.0, nanos, TracePositionSource.PHONE_LOCATION,
    )
}
