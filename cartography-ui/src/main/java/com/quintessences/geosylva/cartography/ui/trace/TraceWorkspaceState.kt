package com.quintessences.geosylva.cartography.ui.trace

/** Presentation only: the host supplies an authorized, coherent capture/record snapshot. */
data class TraceWorkspaceState(
    val screen: TraceScreen,
    val contextTitle: String,
    val captureStatus: TraceCaptureStatus = TraceCaptureStatus.RECORDING,
    val resumed: Boolean = false,
    val name: String = "",
    val destinationLabel: String? = null,
    val location: TraceLocationSummary = TraceLocationSummary(),
    val statistics: TraceStatistics = TraceStatistics(),
    val allowedActions: Set<TraceAction> = emptySet(),
    val selectedPortionId: String? = null,
    val saving: Boolean = false,
    val nameEditable: Boolean = true,
    val errorMessage: String? = null,
)

enum class TraceScreen { PREPARATION, CAPTURE, REVIEW, SAVE, SAVED, DETAILS }
enum class TraceCaptureStatus { RECORDING, WAITING_FOR_QUALITY, MANUALLY_PAUSED }

data class TraceLocationSummary(
    /** Already qualified by the location service, never inferred from a number in this UI. */
    val available: Boolean = false,
    val reportedAccuracyM: Double? = null,
    val fixAgeSeconds: Long? = null,
    val unavailableLabel: String? = null,
)

data class TraceStatistics(
    val acceptedLengthM: Double? = null,
    val acceptedPointCount: Int? = null,
    val portionCount: Int? = null,
    val unrecordedPassageCount: Int? = null,
    val elapsedSeconds: Long? = null,
    val manualPauseSeconds: Long? = null,
    /** Not the latest fix's accuracy; leave absent without a qualified uncertainty model. */
    val overallUncertaintyM: Double? = null,
    val portions: List<TracePortionSummary> = emptyList(),
)

data class TracePortionSummary(val id: String, val ordinal: Int, val lengthM: Double?)

enum class TraceAction {
    BACK, LAYERS, FRAME, SETTINGS, START, PAUSE, RESUME, FINISH, CONTINUE,
    SAVE, CANCEL_SAVE, DETAILS, METHOD_SOURCE, SHARE, DUPLICATE, OVERFLOW,
    REQUEST_PERMISSION,
    LIBRARY,
}

enum class TraceHelp {
    CONTEXT, CAPTURE, POSITION, REPORTED_ACCURACY, FIX_AGE, LENGTH, POINT_COUNT,
    PORTIONS, UNRECORDED_PASSAGES, SESSION_DURATION, PAUSE_DURATION,
    OVERALL_UNCERTAINTY, NAME, LOCAL_STORAGE, NEW_PORTION,
}

/** No action creates data here. Hosts handle permission checks, durable success and navigation. */
interface TraceWorkspaceActions {
    fun onAction(action: TraceAction)
    fun onNameChanged(name: String)
    fun onExplain(help: TraceHelp)
    fun onSelectPortion(id: String)
}
