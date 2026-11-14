package com.forestry.counter.domain.location.trace

import org.junit.Assert.*
import org.junit.Test

class SegmentedTraceRecorderTest {
    private val second = 1_000_000_000L
    private fun at(seconds: Long) = seconds * second
    private fun policy(minimumDistanceM: Double = 0.0, capacity: Int = 50_000) = TraceCapturePolicy(
        maximumHorizontalAccuracyM = 10.0,
        maximumFixAgeNanos = at(2),
        minimumPointDistanceM = minimumDistanceM,
        maximumObservationGapNanos = at(5),
        maximumAcceptedPoints = capacity,
    )
    private fun fix(seconds: Long, longitude: Double = 0.0, accuracy: Double? = 4.0) = TraceFix(
        latitude = 0.0, longitude = longitude, horizontalAccuracyM = accuracy,
        fixElapsedRealtimeNanos = at(seconds), source = TracePositionSource.PHONE_LOCATION,
        provider = "fused", fixTimestampMillis = 1_700_000_000_000L,
    )
    private fun accepted(result: TraceFixResult) = (result as TraceFixResult.Accepted).snapshot

    @Test fun twoPointsGiveOneOpenSegmentAndNeverItsReturnLeg() {
        val recorder = SegmentedTraceRecorder("session", policy())
        val token = recorder.start(at(10))!!
        val first = accepted(recorder.accept(token, fix(10), at(10)))
        assertEquals(1, first.acceptedPointCount)
        assertEquals(0.0, first.lengthM, 0.0)
        val secondPoint = accepted(recorder.accept(token, fix(11, longitude = 0.001), at(11)))
        val analytic = SphericalTraceDistance.EARTH_RADIUS_M * Math.toRadians(0.001)
        assertEquals(analytic, secondPoint.lengthM, 1e-8)
        assertEquals(1, secondPoint.portions.size)
        assertNull(secondPoint.overallTraceUncertaintyM)
    }

    @Test fun rejectedPositionStartsNewPortionWithoutMeasuringTheGap() {
        val recorder = SegmentedTraceRecorder("session", policy())
        val token = recorder.start(at(10))!!
        recorder.accept(token, fix(10), at(10))
        val before = accepted(recorder.accept(token, fix(11, 0.001), at(11)))
        val rejected = recorder.accept(token, fix(12, 1.0, accuracy = 25.0), at(12)) as TraceFixResult.Rejected
        assertEquals(TraceCapturePhase.WAITING_FOR_QUALITY, rejected.snapshot.phase)
        assertEquals(before.lengthM, rejected.snapshot.lengthM, 0.0)
        assertEquals(2, rejected.snapshot.acceptedPointCount)
        assertEquals(25.0, rejected.snapshot.lastReceivedFix!!.horizontalAccuracyM!!, 0.0)
        assertEquals(4.0, rejected.snapshot.lastAcceptedFix!!.horizontalAccuracyM!!, 0.0)
        val resumed = accepted(recorder.accept(token, fix(13, 1.0), at(13)))
        assertEquals(before.lengthM, resumed.lengthM, 0.0)
        assertEquals(2, resumed.portions.size)
        assertEquals(TraceBreakReason.QUALITY_RECOVERY, resumed.portions[1].startReason)
        val last = accepted(recorder.accept(token, fix(14, 1.001), at(14)))
        assertEquals(before.lengthM * 2, last.lengthM, 1e-8)
        assertEquals(1, last.betweenPortionsGapCount)
    }

    @Test fun manualPauseCannotResumeFromAnyOldCallbackEvenWithGoodQuality() {
        val recorder = SegmentedTraceRecorder("session", policy())
        val oldToken = recorder.start(at(10))!!
        recorder.accept(oldToken, fix(10), at(10))
        recorder.pause(at(11))
        val during = recorder.accept(oldToken, fix(12), at(12)) as TraceFixResult.Ignored
        assertEquals(TraceIgnoredReason.OBSOLETE_CALLBACK, during.reason)
        assertEquals(TraceCapturePhase.MANUALLY_PAUSED, during.snapshot.phase)
        assertEquals(1, during.snapshot.acceptedPointCount)
        assertNull(recorder.start(at(13)))
        val freshToken = recorder.resume(at(14))!!
        assertNotEquals(oldToken, freshToken)
        assertTrue(recorder.accept(oldToken, fix(15), at(15)) is TraceFixResult.Ignored)
        val resumed = accepted(recorder.accept(freshToken, fix(16, 1.0), at(16)))
        assertEquals(2, resumed.portions.size)
        assertEquals(TraceBreakReason.MANUAL_RESUME, resumed.portions[1].startReason)
        assertEquals(0.0, resumed.lengthM, 0.0)
    }

    @Test fun stoppedCallbacksAndOtherSessionCallbacksCannotChangePoints() {
        val recorder = SegmentedTraceRecorder("one", policy())
        val token = recorder.start(at(10))!!
        recorder.accept(token, fix(10), at(10))
        assertTrue(recorder.accept(token.copy(sessionId = "two"), fix(11), at(11)) is TraceFixResult.Ignored)
        recorder.stop(at(12))
        assertTrue(recorder.accept(token, fix(13), at(13)) is TraceFixResult.Ignored)
        assertEquals(1, recorder.snapshot(at(14)).acceptedPointCount)
        assertEquals(TraceCapturePhase.STOPPED_DRAFT, recorder.snapshot(at(14)).phase)
    }

    @Test fun sessionDurationIncludesPauseButExcludesStoppedReviewAndRepeatedCommands() {
        val recorder = SegmentedTraceRecorder("session", policy())
        recorder.start(at(10))
        recorder.pause(at(20))
        val paused = recorder.pause(at(25))
        assertEquals(at(15), paused.sessionElapsedNanos)
        assertEquals(at(5), paused.currentManualPauseElapsedNanos)
        assertEquals(1, paused.events.count { it.type == TraceEventType.PAUSED })
        assertEquals(at(20), recorder.stop(at(30)).sessionElapsedNanos)
        assertEquals(at(20), recorder.stop(at(80)).sessionElapsedNanos)
        val token = recorder.resume(at(100))!!
        val resumed = accepted(recorder.accept(token, fix(101), at(101)))
        assertEquals(at(21), resumed.sessionElapsedNanos)
        assertNull(resumed.currentManualPauseElapsedNanos)
        assertEquals(TraceBreakReason.REVIEW_RESUME, resumed.portions.single().startReason)
    }

    @Test fun finalManualPauseIsNotAnotherMissingSpatialPassage() {
        val recorder = SegmentedTraceRecorder("session", policy())
        val token = recorder.start(at(10))!!
        recorder.accept(token, fix(10), at(10))
        recorder.pause(at(11))
        assertEquals(0, recorder.stop(at(12)).betweenPortionsGapCount)
    }

    @Test fun missingZeroNegativeNonFiniteAndPoorAccuracyAreExplicitlyRejected() {
        listOf(null, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 10.01).forEach { accuracy ->
            val recorder = SegmentedTraceRecorder("session", policy())
            val token = recorder.start(at(10))!!
            val result = recorder.accept(token, fix(10, accuracy = accuracy), at(10)) as TraceFixResult.Rejected
            assertEquals(0, result.snapshot.acceptedPointCount)
            assertNull(result.snapshot.lastAcceptedFix)
        }
    }

    @Test fun freshBoundaryAccuracyIsAllowedButCoordinatesAndMockAreNot() {
        val recorder = SegmentedTraceRecorder("session", policy())
        val token = recorder.start(at(10))!!
        assertTrue(recorder.accept(token, fix(10, accuracy = 10.0), at(10)) is TraceFixResult.Accepted)
        val invalid = recorder.accept(token, fix(11).copy(latitude = Double.NaN), at(11)) as TraceFixResult.Rejected
        assertEquals(TraceRejectionReason.INVALID_COORDINATES, invalid.reason)
        val mock = recorder.accept(token, fix(12).copy(isMock = true), at(12)) as TraceFixResult.Rejected
        assertEquals(TraceRejectionReason.MOCK_POSITION, mock.reason)
    }

    @Test fun freshnessUsesRealFixMonotonicTimeNotWallTimestamp() {
        val recorder = SegmentedTraceRecorder("session", policy())
        val token = recorder.start(at(10))!!
        val cached = recorder.accept(token, fix(9), at(10)) as TraceFixResult.Rejected
        assertEquals(TraceRejectionReason.CACHED_BEFORE_REQUEST, cached.reason)
        val stale = recorder.accept(token, fix(10), at(13)) as TraceFixResult.Rejected
        assertEquals(TraceRejectionReason.STALE_FIX, stale.reason)
        assertTrue(recorder.accept(token, fix(14).copy(fixTimestampMillis = null), at(14)) is TraceFixResult.Accepted)
        val future = recorder.accept(token, fix(16), at(15)) as TraceFixResult.Rejected
        assertEquals(TraceRejectionReason.FUTURE_FIX, future.reason)
        val zero = recorder.accept(token, fix(16).copy(fixElapsedRealtimeNanos = 0), at(16)) as TraceFixResult.Rejected
        assertEquals(TraceRejectionReason.MISSING_FIX_TIME, zero.reason)
    }

    @Test fun resumeRejectsCachedPositionsFromBeforeTheNewRequest() {
        val recorder = SegmentedTraceRecorder("session", policy())
        val firstToken = recorder.start(at(10))!!
        recorder.accept(firstToken, fix(10), at(10))
        recorder.pause(at(11))
        val resumed = recorder.resume(at(20))!!
        val cached = recorder.accept(resumed, fix(19), at(20)) as TraceFixResult.Rejected
        assertEquals(TraceRejectionReason.CACHED_BEFORE_REQUEST, cached.reason)
        assertEquals(1, cached.snapshot.portions.size)
        assertEquals(2, accepted(recorder.accept(resumed, fix(21), at(21))).portions.size)
    }

    @Test fun duplicateAndOutOfOrderCallbacksDoNotCreatePointsOrQualityGaps() {
        val recorder = SegmentedTraceRecorder("session", policy())
        val token = recorder.start(at(10))!!
        recorder.accept(token, fix(10), at(10))
        recorder.accept(token, fix(11, 0.001), at(11))
        val duplicate = recorder.accept(token, fix(11, accuracy = null), at(12)) as TraceFixResult.Ignored
        assertEquals(TraceIgnoredReason.OUT_OF_ORDER_FIX, duplicate.reason)
        assertEquals(TraceCapturePhase.RECORDING, duplicate.snapshot.phase)
        assertEquals(2, duplicate.snapshot.acceptedPointCount)
        assertEquals(1, accepted(recorder.accept(token, fix(13, 0.002), at(13))).portions.size)
    }

    @Test fun delayedFixFromBeforeARejectedObservationCannotPretendRecovery() {
        val recorder = SegmentedTraceRecorder("session", policy())
        val token = recorder.start(at(10))!!
        recorder.accept(token, fix(10), at(10))
        recorder.accept(token, fix(12, accuracy = 25.0), at(12))
        val delayed = recorder.accept(token, fix(11), at(13)) as TraceFixResult.Ignored
        assertEquals(TraceIgnoredReason.OUT_OF_ORDER_FIX, delayed.reason)
        assertEquals(TraceCapturePhase.WAITING_FOR_QUALITY, delayed.snapshot.phase)
        assertEquals(1, delayed.snapshot.acceptedPointCount)
    }

    @Test fun longObservationGapSplitsButDoesNotPretendItsCauseIsKnown() {
        val recorder = SegmentedTraceRecorder("session", policy())
        val token = recorder.start(at(10))!!
        recorder.accept(token, fix(10), at(10))
        val result = accepted(recorder.accept(token, fix(16, 1.0), at(16)))
        assertEquals(2, result.portions.size)
        assertEquals(0.0, result.lengthM, 0.0)
        assertEquals(TraceBreakReason.OBSERVATION_GAP, result.portions[1].startReason)
        val event = result.events.last()
        assertEquals(TraceEventType.OBSERVATION_GAP, event.type)
        assertNull(event.rejectionReason)
    }

    @Test fun healthyStationaryFixesDoNotCreateFalseGapFromDistanceThinning() {
        val recorder = SegmentedTraceRecorder("session", policy(minimumDistanceM = 3.0))
        val token = recorder.start(at(10))!!
        recorder.accept(token, fix(10), at(10))
        (11L..20L).forEach {
            val result = recorder.accept(token, fix(it), at(it)) as TraceFixResult.Ignored
            assertEquals(TraceIgnoredReason.BELOW_MINIMUM_DISTANCE, result.reason)
        }
        val moved = accepted(recorder.accept(token, fix(21, 0.001), at(21)))
        assertEquals(1, moved.portions.size)
        assertEquals(2, moved.acceptedPointCount)
    }

    @Test fun noFixTimeoutSplitsAndManualPauseStillWins() {
        val recorder = SegmentedTraceRecorder("session", policy())
        val token = recorder.start(at(10))!!
        recorder.accept(token, fix(10), at(10))
        val timeout = recorder.reportUnavailable(token, at(20)) as TraceFixResult.Rejected
        assertEquals(TraceRejectionReason.SOURCE_UNAVAILABLE, timeout.reason)
        recorder.pause(at(21))
        assertTrue(recorder.reportUnavailable(token, at(22)) is TraceFixResult.Ignored)
        assertEquals(TraceCapturePhase.MANUALLY_PAUSED, recorder.snapshot(at(22)).phase)
    }

    @Test fun repeatedStartDoesNotEraseAcceptedPointsOrChangeToken() {
        val recorder = SegmentedTraceRecorder("session", policy())
        val token = recorder.start(at(10))!!
        recorder.accept(token, fix(10), at(10))
        assertEquals(token, recorder.start(at(11)))
        assertEquals(1, recorder.snapshot(at(11)).acceptedPointCount)
    }

    @Test fun capacityIsAnExplicitRejectionNotSilentDataLoss() {
        val recorder = SegmentedTraceRecorder("session", policy(capacity = 1))
        val token = recorder.start(at(10))!!
        recorder.accept(token, fix(10), at(10))
        val result = recorder.accept(token, fix(11), at(11)) as TraceFixResult.Rejected
        assertEquals(TraceRejectionReason.CAPACITY_REACHED, result.reason)
        assertEquals(1, result.snapshot.acceptedPointCount)
    }

    @Test fun distanceHandlesDatelineAndPolesWithoutChangingTheModel() {
        val equatorial = SphericalTraceDistance.betweenM(fix(10, 179.999), fix(11, -179.999))!!
        assertEquals(SphericalTraceDistance.EARTH_RADIUS_M * Math.toRadians(0.002), equatorial, 1e-6)
        val pole = fix(10).copy(latitude = 90.0)
        assertEquals(0.0, SphericalTraceDistance.betweenM(pole, pole.copy(longitude = 180.0))!!, 1e-6)
        assertNull(SphericalTraceDistance.betweenM(fix(10).copy(longitude = 181.0), fix(11)))
    }
}
