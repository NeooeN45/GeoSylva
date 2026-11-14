package com.quintessences.geosylva.cartography.ui.measurement

/** Presentation of one host-owned measurement snapshot. No geometry or calculation lives here. */
data class MeasureWorkspaceState(
    val contextLabel: String? = null,
    val mode: MeasureMode = MeasureMode.DISTANCE,
    /** Already formatted from the full reference value; null means no usable result. */
    val valueText: String? = null,
    val unitText: String = "",
    val pointCount: Int = 0,
    val validationMessage: String? = null,
    val canSave: Boolean = false,
    val canUndo: Boolean = false,
    val panel: MeasurePanel = MeasurePanel.EDITOR,
    val distanceUnit: MeasureDistanceUnit = MeasureDistanceUnit.METRES,
    val areaUnit: MeasureAreaUnit = MeasureAreaUnit.HECTARES,
    val saveName: String = "",
    val saveNameEditable: Boolean = true,
    val saving: Boolean = false,
    val saveError: String? = null,
    val locationEnabled: Boolean = true,
    val availableMenuActions: Set<MeasureMenuAction> = MeasureMenuAction.entries.toSet(),
)

enum class MeasureMode { DISTANCE, AREA }
enum class MeasurePanel { EDITOR, COLLAPSED, UNITS, SAVE }
enum class MeasureDistanceUnit { METRES, KILOMETRES }
enum class MeasureAreaUnit { SQUARE_METRES, ARES, HECTARES }
enum class MeasureMenuAction { UNITS, SEGMENTS, COLOR, CLEAR, SHARE, SAVED_MEASURES }
enum class MeasureHelp { CONTEXT, METHOD, RESULT, POINTS, UNITS, DISTANCE_UNIT, AREA_UNIT, SAVE, NAME, LOCAL_STORAGE }

/** The host retains drafts, confirms destructive actions and reports durable save success. */
interface MeasureWorkspaceActions {
    fun onBack()
    fun onPanelChange(panel: MeasurePanel)
    fun onModeChange(mode: MeasureMode)
    fun onUndo()
    fun onSaveRequested()
    fun onSaveNameChange(name: String)
    fun onConfirmSave()
    fun onCancelSave()
    fun onDistanceUnitChange(unit: MeasureDistanceUnit)
    fun onAreaUnitChange(unit: MeasureAreaUnit)
    /** CLEAR requests confirmation; it must not silently erase the draft. */
    fun onMenuAction(action: MeasureMenuAction)
    fun onExplain(help: MeasureHelp)
    fun onLayers()
    fun onLocate()
}
