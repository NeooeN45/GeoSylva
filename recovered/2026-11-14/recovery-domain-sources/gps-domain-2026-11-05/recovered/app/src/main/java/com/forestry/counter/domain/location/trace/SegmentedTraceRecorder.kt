package com.forestry.counter.domain.location.trace

/**
 * Thread-safe, Android-free capture state machine. The host supplies qualified access,
 * lifecycle subscriptions and a monotonic clock; this class never requests GPS or writes
 * files. Persist immutable snapshots through the same authorized repository as the map.
 *
 * Callback tokens must be bound when subscribing, never read afresh inside a callback.
 * Pause/stop invalidate them. Resume always starts another portion on the first fix.
 */
class SegmentedTraceRecorder(
    val sessionId: String,
    private val policy: TraceCapturePolicy,
) {
    init { require(sessionId.isNotBlank()) }

    private var phase = TraceCapturePhase.PREPARING
    private var generation = 0L
    private var requestStartedNanos = 0L
    private var lastCommandNanos = 0L
    private var intervalStartedNanos: Long? = null
    private var accumulatedElapsedNanos = 0L
    private var pauseStartedNanos: Long? = null
    private var lastObservedFixNanos: Long? = null
    private var lastQualifiedFixNanos: Long? = null
    private var pendingStart = TraceBreakReason.INITIAL
    private var startsNewPortion = true
    private var portions = emptyList<TracePortion>()
    private var events = emptyList<TraceCaptureEvent>()
    private var lastReceivedFix: TraceFix? = null
    private var waitingReason: TraceRejectionReason? = null

    /** Repeated start is idempotent; a stopped session must be resumed explicitly. */
    @Synchronized
    fun start(nowNanos: Long): TraceCaptureToken? {
        checkClock(nowNanos)
        if (phase == TraceCapturePhase.RECORDING || phase == TraceCapturePhase.WAITING_FOR_QUALITY) return token()
        if (phase != TraceCapturePhase.PREPARING) return null
        intervalStartedNanos = nowNanos
        beginRequest(nowNanos)
        events = events + TraceCaptureEvent(TraceEventType.STARTED, nowNanos)
        return token()
    }

    @Synchronized
    fun pause(nowNanos: Long): TraceCaptureSnapshot {
        checkClock(nowNanos)
        if (phase == TraceCapturePhase.RECORDING || phase == TraceCapturePhase.WAITING_FOR_QUALITY) {
            generation++
            phase = TraceCapturePhase.MANUALLY_PAUSED
            pauseStartedNanos = nowNanos
            waitingReason = null
            events = events + TraceCaptureEvent(TraceEventType.PAUSED, nowNanos)
        }
        return snapshotAt(nowNanos)
    }

    /** Includes manual pause time, excludes the time spent in stopped draft review. */
    @Synchronized
    fun resume(nowNanos: Long): TraceCaptureToken? {
        checkClock(nowNanos)
        if (phase == TraceCapturePhase.RECORDING || phase == TraceCapturePhase.WAITING_FOR_QUALITY) return token()
        if (phase != TraceCapturePhase.MANUALLY_PAUSED && phase != TraceCapturePhase.STOPPED_DRAFT) return null
        pendingStart = if (phase == TraceCapturePhase.MANUALLY_PAUSED)
            TraceBreakReason.MANUAL_RESUME else TraceBreakReason.REVIEW_RESUME
        if (phase == TraceCapturePhase.STOPPED_DRAFT) intervalStartedNanos = nowNanos
        startsNewPortion = true
        pauseStartedNanos = null
        beginRequest(nowNanos)
        events = events + TraceCaptureEvent(TraceEventType.RESUMED, nowNanos)
        return token()
    }

    @Synchronized
    fun stop(nowNanos: Long): TraceCaptureSnapshot {
        checkClock(nowNanos)
        if (phase != TraceCapturePhase.STOPPED_DRAFT && phase != TraceCapturePhase.PREPARING) {
            accumulatedElapsedNanos = elapsedAt(nowNanos)
            intervalStartedNanos = null
            pauseStartedNanos = null
            generation++
            phase = TraceCapturePhase.STOPPED_DRAFT
            waitingReason = null
            events = events + TraceCaptureEvent(TraceEventType.STOPPED, nowNanos)
        }
        return snapshotAt(nowNanos)
    }

    /** Also used for a no-fix timeout/provider loss; retaining a token does not bypass pause. */
    @Synchronized
    fun reportUnavailable(
        callbackToken: TraceCaptureToken,
        nowNanos: Long,
        reason: TraceRejectionReason = TraceRejectionReason.SOURCE_UNAVAILABLE,
    ): TraceFixResult {
        checkClock(nowNanos)
        if (!acceptsToken(callbackToken)) return ignored(TraceIgnoredReason.OBSOLETE_CALLBACK, nowNanos)
        return reject(reason, nowNanos)
    }

    @Synchronized
    fun accept(callbackToken: TraceCaptureToken, fix: TraceFix, nowNanos: Long): TraceFixResult {
        checkClock(nowNanos)
        if (!acceptsToken(callbackToken)) return ignored(TraceIgnoredReason.OBSOLETE_CALLBACK, nowNanos)
        // An older or duplicate callback cannot invalidate a more recent qualified position.
        if (fix.fixElapsedRealtimeNanos > 0 &&
            lastObservedFixNanos?.let { fix.fixElapsedRealtimeNanos <= it } == true)
            return ignored(TraceIgnoredReason.OUT_OF_ORDER_FIX, nowNanos)

        lastReceivedFix = fix
        // Remember a plausible observation time even if its coordinate/accuracy is
        // rejected: an older delayed fix must not masquerade as quality recovery.
        if (fix.fixElapsedRealtimeNanos > 0 &&
            fix.fixElapsedRealtimeNanos in requestStartedNanos..nowNanos)
            lastObservedFixNanos = fix.fixElapsedRealtimeNanos
        rejectionOf(fix, nowNanos)?.let { return reject(it, nowNanos) }
        if (portions.sumOf { it.points.size } >= policy.maximumAcceptedPoints)
            return reject(TraceRejectionReason.CAPACITY_REACHED, nowNanos)

        val previousQualified = lastQualifiedFixNanos
        if (!startsNewPortion && previousQualified != null &&
            fix.fixElapsedRealtimeNanos - previousQualified > policy.maximumObservationGapNanos) {
            startsNewPortion = true
            pendingStart = TraceBreakReason.OBSERVATION_GAP
            // Detection time is known; the cause and exact onset of the gap are not.
            events = events + TraceCaptureEvent(TraceEventType.OBSERVATION_GAP, nowNanos)
        }
        lastQualifiedFixNanos = fix.fixElapsedRealtimeNanos
        if (!startsNewPortion) {
            val previous = portions.last().points.last()
            val distance = requireNotNull(SphericalTraceDistance.betweenM(previous, fix))
            if (distance < policy.minimumPointDistanceM)
                return ignored(TraceIgnoredReason.BELOW_MINIMUM_DISTANCE, nowNanos)
            val last = portions.last()
            portions = portions.dropLast(1) + last.copy(points = last.points + fix, lengthM = last.lengthM + distance)
        } else {
            portions = portions + TracePortion("$sessionId:portion:${portions.size + 1}", pendingStart, listOf(fix), 0.0)
            startsNewPortion = false
        }
        phase = TraceCapturePhase.RECORDING
        waitingReason = null
        return TraceFixResult.Accepted(snapshotAt(nowNanos))
    }

    @Synchronized
    fun snapshot(nowNanos: Long): TraceCaptureSnapshot {
        checkClock(nowNanos)
        return snapshotAt(nowNanos)
    }

    private fun beginRequest(nowNanos: Long) {
        generation++
        requestStartedNanos = nowNanos
        lastObservedFixNanos = null
        lastQualifiedFixNanos = null
        lastReceivedFix = null
        waitingReason = null
        phase = TraceCapturePhase.RECORDING
    }

    private fun rejectionOf(fix: TraceFix, nowNanos: Long): TraceRejectionReason? = when {
        !SphericalTraceDistance.isValidCoordinate(fix.latitude, fix.longitude) -> TraceRejectionReason.INVALID_COORDINATES
        fix.altitudeM?.isFinite() == false -> TraceRejectionReason.INVALID_ALTITUDE
        fix.isMock -> TraceRejectionReason.MOCK_POSITION
        fix.horizontalAccuracyM == null -> TraceRejectionReason.MISSING_ACCURACY
        !fix.horizontalAccuracyM.isFinite() || fix.horizontalAccuracyM <= 0 -> TraceRejectionReason.INVALID_ACCURACY
        fix.horizontalAccuracyM > policy.maximumHorizontalAccuracyM -> TraceRejectionReason.INSUFFICIENT_ACCURACY
        fix.fixElapsedRealtimeNanos <= 0 -> TraceRejectionReason.MISSING_FIX_TIME
        fix.fixElapsedRealtimeNanos > nowNanos -> TraceRejectionReason.FUTURE_FIX
        fix.fixElapsedRealtimeNanos < requestStartedNanos -> TraceRejectionReason.CACHED_BEFORE_REQUEST
        nowNanos - fix.fixElapsedRealtimeNanos > policy.maximumFixAgeNanos -> TraceRejectionReason.STALE_FIX
        else -> null
    }

    private fun reject(reason: TraceRejectionReason, nowNanos: Long): TraceFixResult.Rejected {
        if (phase != TraceCapturePhase.WAITING_FOR_QUALITY) {
            events = events + TraceCaptureEvent(TraceEventType.QUALITY_WAIT, nowNanos, reason)
            if (portions.isNotEmpty() && !startsNewPortion) {
                startsNewPortion = true
                pendingStart = TraceBreakReason.QUALITY_RECOVERY
            }
        }
        phase = TraceCapturePhase.WAITING_FOR_QUALITY
        waitingReason = reason
        return TraceFixResult.Rejected(reason, snapshotAt(nowNanos))
    }

    private fun acceptsToken(value: TraceCaptureToken): Boolean = value == token() &&
        (phase == TraceCapturePhase.RECORDING || phase == TraceCapturePhase.WAITING_FOR_QUALITY)

    private fun ignored(reason: TraceIgnoredReason, nowNanos: Long) =
        TraceFixResult.Ignored(reason, snapshotAt(nowNanos))

    private fun token() = TraceCaptureToken(sessionId, generation)
    private fun elapsedAt(nowNanos: Long) = accumulatedElapsedNanos +
        (intervalStartedNanos?.let { nowNanos - it } ?: 0L)

    private fun snapshotAt(nowNanos: Long) = TraceCaptureSnapshot(
        sessionId, phase, generation, portions, events, lastReceivedFix, waitingReason,
        elapsedAt(nowNanos), pauseStartedNanos?.let { nowNanos - it }, policy,
    )

    private fun checkClock(nowNanos: Long) {
        require(nowNanos >= lastCommandNanos) { "A trace uses one nondecreasing elapsed-time clock" }
        lastCommandNanos = nowNanos
    }
}
