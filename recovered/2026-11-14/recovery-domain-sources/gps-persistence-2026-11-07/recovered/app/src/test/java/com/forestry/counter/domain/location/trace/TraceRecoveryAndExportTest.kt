package com.forestry.counter.domain.location.trace

import com.forestry.counter.domain.repository.CartographicTraceDocument
import com.forestry.counter.presentation.screens.forestry.trace.TraceGeoJsonExport
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class TraceRecoveryAndExportTest {
    @Test fun `process recovery is stopped and preserves only known duration until explicit resume in a new portion`() {
        val recorder = SegmentedTraceRecorder("session", policy)
        val oldToken = requireNotNull(recorder.start(100))
        recorder.accept(oldToken, fix(45.0, 101, "boot-a"), 101)
        recorder.accept(oldToken, fix(45.001, 201, "boot-a"), 201)
        val beforeCrash = recorder.snapshot(300)
        val recovered = SegmentedTraceRecorder.restoreStopped(beforeCrash)
        val review = recovered.snapshot(50) // A reboot may reset the Android monotonic clock.
        assertEquals(TraceCapturePhase.STOPPED_DRAFT, review.phase)
        assertEquals(200L, review.sessionElapsedNanos)
        assertEquals(beforeCrash.portions, review.portions)
        assertTrue(recovered.accept(oldToken, fix(46.0, 60, "boot-b"), 60) is TraceFixResult.Ignored)
        val newToken = requireNotNull(recovered.resume(70))
        recovered.accept(newToken, fix(46.0, 71, "boot-b"), 71)
        val after = recovered.snapshot(80)
        assertEquals(2, after.portions.size)
        assertEquals(TraceBreakReason.REVIEW_RESUME, after.portions.last().startReason)
        assertEquals(beforeCrash.lengthM, after.lengthM, 0.0)
        assertEquals(210L, after.sessionElapsedNanos)
    }

    @Test fun `export keeps portions separate with original source quality clocks and no overall uncertainty`() {
        val recorder = SegmentedTraceRecorder("session", policy)
        val first = requireNotNull(recorder.start(100))
        recorder.accept(first, fix(45.0, 101, "boot"), 101)
        recorder.accept(first, fix(45.001, 102, "boot"), 102)
        recorder.pause(103)
        val second = requireNotNull(recorder.resume(104))
        recorder.accept(second, fix(46.0, 105, "boot"), 105)
        recorder.accept(second, fix(46.001, 106, "boot"), 106)
        val snapshot = recorder.stop(107)
        val doc = CartographicTraceDocument("session", null, snapshot, true, 1, 1, 1, null)
        val exported = Json.parseToJsonElement(TraceGeoJsonExport.encode(doc)).jsonObject
        val features = exported.getValue("features").jsonArray
        assertEquals(2, features.size)
        assertEquals(2, features[0].jsonObject.getValue("geometry").jsonObject.getValue("coordinates").jsonArray.size)
        assertEquals(2, features[1].jsonObject.getValue("geometry").jsonObject.getValue("coordinates").jsonArray.size)
        assertEquals(JsonNull, exported.getValue("properties").jsonObject["overallUncertaintyM"])
        val observation = features[1].jsonObject.getValue("properties").jsonObject.getValue("observations").jsonArray.first().jsonObject
        assertEquals(4.0, observation.getValue("reportedAccuracyM").jsonPrimitive.double, 0.0)
        assertEquals("boot", observation.getValue("clockDomain").jsonPrimitive.content)
        assertTrue(snapshot.lengthM < 230) // The >100km passage between portions is absent.
    }
    private val policy = TraceCapturePolicy(10.0, 15_000_000_000, 0.0, 20_000_000_000)
    private fun fix(lat: Double, nanos: Long, boot: String) = TraceFix(lat, 5.0, 4.0, nanos,
        TracePositionSource.PHONE_LOCATION, "fused", 123, clockDomainId = boot)
}
