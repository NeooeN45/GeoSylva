package com.quintessences.geosylva.cartography.ui.trees

enum class TreeWorkspacePanel { RECORD, GUIDANCE, SPECIES, ACCURACY, EMPTY, RECORDS, HELP }
enum class TreeHelp { RECORD, DIAMETER, HEIGHT, RECORDED_ACCURACY, GUIDANCE, PHONE_ACCURACY, COMPASS, GPS, SPECIES, FILTER }
enum class TreeGpsStatus { WAITING, FRESH, STALE, UNAVAILABLE, PERMISSION_REQUIRED, PAUSED }
enum class TreeHeadingStatus { WAITING, READY, UNKNOWN, UNRELIABLE, STALE, UNAVAILABLE }
enum class TreeReadStatus { LOADING, READY, UNAVAILABLE }

data class TreeRecordUi(
    val treeId: String,
    val parcelId: String,
    val number: Int?,
    val speciesName: String,
    val diameterCm: Double?,
    val diameterClass: String?,
    val heightM: Double?,
    val recordedAccuracyM: Double?,
    val hasCoordinates: Boolean,
    val parentLabel: String? = null,
)
data class TreeSpeciesUi(val code: String, val name: String, val colorArgb: Int, val locatedCount: Int, val selected: Boolean)
data class TreeDisplayCountsUi(
    val total: Int = 0, val located: Int = 0, val withoutCoordinates: Int = 0,
    val speciesHidden: Int = 0, val selectedLocated: Int = 0,
    val aboveThreshold: Int = 0, val unknownAccuracy: Int = 0, val shown: Int = 0,
)
data class TreeGuidanceUi(
    val active: Boolean = false,
    val gps: TreeGpsStatus = TreeGpsStatus.WAITING,
    val heading: TreeHeadingStatus = TreeHeadingStatus.WAITING,
    val distanceM: Double? = null,
    val relativeBearingDeg: Float? = null,
    val phoneAccuracyM: Double? = null,
    val lastFixAgeSeconds: Long? = null,
    /** A display cue, never a finding of the trunk or an accuracy radius. */
    val nearRecordedPoint: Boolean = false,
)
data class TreeWorkspaceUiState(
    val panel: TreeWorkspacePanel? = null,
    val readStatus: TreeReadStatus = TreeReadStatus.LOADING,
    val record: TreeRecordUi? = null,
    val records: List<TreeRecordUi> = emptyList(),
    val species: List<TreeSpeciesUi> = emptyList(),
    val counts: TreeDisplayCountsUi = TreeDisplayCountsUi(),
    val accuracyFilterEnabled: Boolean = false,
    val accuracyThresholdM: Float = 8f,
    val guidance: TreeGuidanceUi = TreeGuidanceUi(),
    val help: TreeHelp = TreeHelp.RECORD,
)

sealed interface TreeWorkspaceAction {
    data object Close : TreeWorkspaceAction
    data object ShowAllSpecies : TreeWorkspaceAction
    data class ToggleSpecies(val code: String, val selected: Boolean) : TreeWorkspaceAction
    data class SetAccuracyFilter(val enabled: Boolean) : TreeWorkspaceAction
    data class SetAccuracyThreshold(val metres: Float) : TreeWorkspaceAction
    data object AdjustAccuracy : TreeWorkspaceAction
    data object StartGuidance : TreeWorkspaceAction
    data object StopGuidance : TreeWorkspaceAction
    data object ViewRecord : TreeWorkspaceAction
    data class SelectRecord(val treeId: String, val parcelId: String) : TreeWorkspaceAction
    data class Help(val topic: TreeHelp) : TreeWorkspaceAction
    data object RetryRead : TreeWorkspaceAction
    data object RequestLocation : TreeWorkspaceAction
}
