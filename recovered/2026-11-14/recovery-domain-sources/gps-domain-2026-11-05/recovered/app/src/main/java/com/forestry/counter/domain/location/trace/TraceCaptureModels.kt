package com.forestry.counter.domain.location.trace

/** All elapsed times use the same Android elapsedRealtimeNanos clock domain. */
data class TraceFix(
    val latitude: Double,
    val longitude: Double,
    val horizontalAccuracyM: Double?,
    val fixElapsedRealtimeNanos: Long,
    val source: TracePositionSource,
    val provider: String? = null,
    val fixTimestampMillis: Long? = null,
    val altitudeM: Double? = null,
    val isMock: Boolean = false,
)

enum class TracePositionSource { PHONE_LOCATION, EXTERNAL_GNSS }

/** Configuration supplied by the host, not a certified forestry protocol. */
data class TraceCapturePolicy(
    val maximumHorizontalAccuracyM: Double,
    val maximumFixAgeNanos: Long,
    val minimumPointDistanceM: Double,
    val maximumObservationGapNanos: Long,
    val maximumAcceptedPoints: Int = 50_000,
) {
    init {
        require(maximumHorizontalAccuracyM.isFinite() && maximumHorizontalAccuracyM > 0)
        require(maximumFixAgeNanos > 0)
        require(minimumPointDistanceM.isFinite() && minimumPointDistanceM >= 0)
        require(maximumObservationGapNanos > 0)
        require(maximumAcceptedPoints > 0)
    }
}

data class TraceCaptureToken(val sessionId: String, val generation: Long)

enum class TraceCapturePhase {
    PREPARING, RECORDING, WAITING_FOR_QUALITY, MANUALLY_PAUSED, STOPPED_DRAFT,
}

enum class TraceBreakReason {
    INITIAL, QUALITY_RECOVERY, MANUAL_RESUME, REVIEW_RESUME, OBSERVATION_GAP,
}

enum class TraceRejectionReason {
    INVALID_COORDINATES, INVALID_ALTITUDE, MISSING_ACCURACY, INVALID_ACCURACY, INSUFFICIENT_ACCURACY,
    MISSING_FIX_TIME, FUTURE_FIX, STALE_FIX, CACHED_BEFORE_REQUEST, MOCK_POSITION,
    SOURCE_UNAVAILABLE, CAPACITY_REACHED,
}

enum class TraceIgnoredReason { OBSOLETE_CALLBACK, OUT_OF_ORDER_FIX, BELOW_MINIMUM_DISTANCE }

enum class TraceEventType { STARTED, QUALITY_WAIT, OBSERVATION_GAP, PAUSED, RESUMED, STOPPED }

data class TraceCaptureEvent(
    val type: TraceEventType,
    val elapsedRealtimeNanos: Long,
    val rejectionReason: TraceRejectionReason? = null,
)

data class TracePortion(
    val id: String,
    val startReason: TraceBreakReason,
    val points: List<TraceFix>,
    /** Horizontal spherical lengths within this portion only; never closes the line. */
    val lengthM: Double,
)

data class TraceCaptureSnapshot(
    val sessionId: String,
    val phase: TraceCapturePhase,
    val generation: Long,
    val portions: List<TracePortion>,
    val events: List<TraceCaptureEvent>,
    /** May be rejected/invalid. It is NOT necessarily the current drawable position. */
    val lastReceivedFix: TraceFix?,
    val waitingReason: TraceRejectionReason?,
    val sessionElapsedNanos: Long,
    val currentManualPauseElapsedNanos: Long?,
    val capturePolicy: TraceCapturePolicy,
    val calculationMethod: String = SphericalTraceDistance.METHOD_ID,
) {
    val acceptedPointCount: Int get() = portions.sumOf { it.points.size }
    val lengthM: Double get() = portions.sumOf { it.lengthM }
    val lastAcceptedFix: TraceFix? get() = portions.lastOrNull()?.points?.lastOrNull()
    /** A final pause without a resumed portion is not an extra spatial gap. */
    val betweenPortionsGapCount: Int get() = (portions.size - 1).coerceAtLeast(0)
    /** A fix accuracy is not the uncertainty of an entire trace. */
    val overallTraceUncertaintyM: Double? get() = null
}

sealed interface TraceFixResult {
    val snapshot: TraceCaptureSnapshot
    data class Accepted(override val snapshot: TraceCaptureSnapshot) : TraceFixResult
    data class Rejected(val reason: TraceRejectionReason, override val snapshot: TraceCaptureSnapshot) : TraceFixResult
    data class Ignored(val reason: TraceIgnoredReason, override val snapshot: TraceCaptureSnapshot) : TraceFixResult
}
