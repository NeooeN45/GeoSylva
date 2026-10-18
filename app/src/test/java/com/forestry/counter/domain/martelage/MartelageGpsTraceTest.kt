package com.forestry.counter.domain.martelage

import com.forestry.counter.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class MartelageGpsTraceTest {
    @Test fun positions_retain_measurement_precision_and_pause_segments() {
        val points = listOf(point(), point().copy(elapsedRealtimeMs = 1200),
            point().copy(segmentId = "7:2000:2100", elapsedRealtimeMs = 2200))
        val value = MartelageGpsTrace(points = points)
        assertEquals(value, MartelageGpsTraceCodec.decode(MartelageGpsTraceCodec.encode(value)))
        assertEquals(2, value.points.groupBy { it.segmentId }.size)
    }
    @Test fun cache_before_start_pause_reboot_and_future_are_rejected() {
        val reading = MartelageClockReading(2000, 1500, 7)
        assertTrue(MartelageGpsTraceCodec.isCurrentPoint(point(), session(), reading))
        assertFalse(MartelageGpsTraceCodec.isCurrentPoint(point().copy(elapsedRealtimeMs = 900), session(), reading))
        assertFalse(MartelageGpsTraceCodec.isCurrentPoint(point().copy(elapsedRealtimeMs = 1600), session(), reading))
        assertFalse(MartelageGpsTraceCodec.isCurrentPoint(point(), session().copy(martelageState = MartelageStates.PAUSED), reading))
        assertFalse(MartelageGpsTraceCodec.isCurrentPoint(point(), session(), reading.copy(bootCount = 8)))
        assertFalse(MartelageGpsTraceCodec.isCurrentPoint(point(), session(), reading.copy(elapsedRealtimeMs = 20_000)))
        assertFalse(MartelageGpsTraceCodec.isCurrentPoint(point(), session().copy(placetteId = null), reading))
    }
    @Test fun invalid_coordinate_precision_and_segment_are_rejected() {
        listOf(point().copy(latitude = 91.0), point().copy(longitude = Double.NaN), point().copy(accuracyM = 0.0),
            point().copy(accuracyM = 101.0), point().copy(altitudeM = Double.POSITIVE_INFINITY),
            point().copy(segmentId = "unknown"), point().copy(capturedAtMs = 0), point().copy(elapsedRealtimeMs = -1)).forEach {
            assertTrue(runCatching { MartelageGpsTraceCodec.encode(MartelageGpsTrace(points = listOf(it))) }.isFailure)
        }
    }
    @Test fun duplicates_reordering_and_reused_segment_are_refused() {
        val a = point()
        val b = a.copy(segmentId = "7:2000:2100", elapsedRealtimeMs = 2200)
        listOf(listOf(a, a), listOf(a, a.copy(elapsedRealtimeMs = 1000)), listOf(a, b, a.copy(elapsedRealtimeMs = 1300))).forEach {
            assertTrue(runCatching { MartelageGpsTraceCodec.encode(MartelageGpsTrace(points = it)) }.isFailure)
        }
    }
    @Test fun unknown_schema_empty_trace_and_unbounded_payload_are_rejected() {
        assertTrue(runCatching { MartelageGpsTraceCodec.encode(MartelageGpsTrace(points = emptyList())) }.isFailure)
        assertTrue(runCatching { MartelageGpsTraceCodec.encode(MartelageGpsTrace(version = 2, points = listOf(point()))) }.isFailure)
        assertTrue(runCatching { MartelageGpsTraceCodec.decode(" ".repeat(MartelageGpsTraceCodec.MAX_BYTES + 1)) }.isFailure)
        assertTrue(runCatching { MartelageGpsTraceCodec.decode("""{"version":1,"points":[],"unknown":true}""") }.isFailure)
    }
    @Test fun sensor_restart_in_the_same_boot_never_accepts_a_backwards_or_duplicate_elapsed_position() {
        val first = point().copy(elapsedRealtimeMs = 1200)
        val newSegment = point().copy(segmentId = "7:1000:1005")
        listOf(newSegment.copy(elapsedRealtimeMs = 1100), newSegment.copy(elapsedRealtimeMs = 1200)).forEach {
            assertTrue(runCatching { MartelageGpsTraceCodec.encode(MartelageGpsTrace(points = listOf(first, it))) }.isFailure)
        }
        val later = newSegment.copy(elapsedRealtimeMs = 1300)
        assertEquals(listOf(first, later), MartelageGpsTraceCodec.decode(
            MartelageGpsTraceCodec.encode(MartelageGpsTrace(points = listOf(first, later)))).points)
    }

    @Test fun different_boots_allow_elapsed_reset_but_each_boot_remains_globally_monotone() {
        val first = point().copy(elapsedRealtimeMs = 1200)
        val reboot = point().copy(segmentId = "8:10:15", elapsedRealtimeMs = 20)
        val valid = MartelageGpsTrace(points = listOf(first, reboot))
        assertEquals(valid, MartelageGpsTraceCodec.decode(MartelageGpsTraceCodec.encode(valid)))
        val backwardsOldBoot = point().copy(segmentId = "7:1000:1005", elapsedRealtimeMs = 1100)
        assertTrue(runCatching {
            MartelageGpsTraceCodec.encode(MartelageGpsTrace(points = listOf(first, reboot, backwardsOldBoot)))
        }.isFailure)
    }

    @Test fun segment_integer_overflow_and_sensor_before_start_are_refused() {
        listOf("2147483648:1000:1050", "7:9223372036854775808:9223372036854775809",
            "7:1000:9223372036854775808", "7:1000:999", "7:1000:1200").forEach { segment ->
            assertTrue(runCatching { MartelageGpsTraceCodec.validatePoint(point().copy(segmentId = segment)) }.isFailure)
            assertFalse(MartelageGpsTraceCodec.isCurrentPoint(point().copy(segmentId = segment), session(),
                MartelageClockReading(2000, 1500, 7)))
        }
        val upperBound = point().copy(segmentId = "2147483647:9223372036854775806:9223372036854775807",
            elapsedRealtimeMs = Long.MAX_VALUE)
        MartelageGpsTraceCodec.validatePoint(upperBound)
    }

    private fun point() = MartelageGpsPoint(46.1, -0.1, 1000, 1100, 6.5, null, "7:1000:1050")
    private fun session() = InventaireSession("s", "p", MartelageStates.TYPE, 1000, null, null, null, null, null, null,
        martelageState = MartelageStates.RUNNING, segmentStartedElapsedMs = 1000, segmentBootCount = 7, placetteId = "plot")
}
