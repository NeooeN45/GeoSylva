package com.quintessences.geosylva.cartography.ui

/** The host supplies already-authorized context. This UI never reads a repository. */
data class MapWorkspaceState(
    val contextTitle: String,
    val contextSubtitle: String? = null,
    val basemaps: List<MapBasemapChoice> = emptyList(),
    val selectedBasemapId: String? = null,
    val layers: List<MapLayerChoice> = emptyList(),
    val panel: MapPanel? = null,
    val pitchDegrees: Double = 0.0,
    val verticalExaggeration: Double = 1.0,
    val controlsEnabled: Boolean = false,
    val controlsUnavailableReason: String? = null,
    val presentation: MapPresentation = MapPresentation.TERRAIN,
    val selectedTask: MapTask? = MapTask.SURVEYS,
    val taskBarVisible: Boolean = true,
    val locationEnabled: Boolean = true,
    /** Null means the contextual title is informational, not a selector. */
    val contextActionLabel: String? = null,
    /** A focused host tool can keep the same canvas without competing chrome. */
    val chromeVisible: Boolean = true,
)

enum class MapPresentation { TERRAIN, FREE }
enum class MapPanel { BASEMAPS, LAYERS, RELIEF }
enum class MapTask { SURVEYS, MEASURE, TOOLS }
enum class MapHelp { BASEMAP_SOURCE, RELIEF_SOURCE, VERTICAL_EXAGGERATION }
enum class MapChoiceAvailability { AVAILABLE, CONFIGURATION_REQUIRED, QUALIFICATION_PENDING, DATA_REQUIRED }

/** A preview key identifies an illustration, never a tile URL or a permission. */
@JvmInline
value class MapPreviewKey(val value: String)

data class MapBasemapChoice(
    val id: String,
    val title: String,
    val provider: String,
    val previewKey: MapPreviewKey = MapPreviewKey(id),
    val availability: MapChoiceAvailability = MapChoiceAvailability.QUALIFICATION_PENDING,
)

data class MapLayerChoice(
    val id: String,
    val title: String,
    val onMap: Boolean,
    val visible: Boolean = false,
    val previewKey: MapPreviewKey = MapPreviewKey(id),
    val availability: MapChoiceAvailability = MapChoiceAvailability.QUALIFICATION_PENDING,
    /** Only set when the host can open the actual layer and its provenance. */
    val detailActionLabel: String? = null,
)

/** Every action is handled by the host; selecting a catalog item is not optimistic activation. */
interface MapWorkspaceActions {
    fun onBack()
    fun onChooseContext()
    fun onPanelChanged(panel: MapPanel?)
    fun onLocate()
    fun onTask(task: MapTask)
    fun onSelectBasemap(id: String)
    fun onToggleLayer(id: String, visible: Boolean)
    fun onAddLayer(id: String)
    fun onLayerDetails(id: String) = Unit
    fun onPitchChange(degrees: Double)
    fun onExaggerationChange(exaggeration: Double)
    fun onFlatView(flat: Boolean)
    fun onExplain(help: MapHelp)
}
