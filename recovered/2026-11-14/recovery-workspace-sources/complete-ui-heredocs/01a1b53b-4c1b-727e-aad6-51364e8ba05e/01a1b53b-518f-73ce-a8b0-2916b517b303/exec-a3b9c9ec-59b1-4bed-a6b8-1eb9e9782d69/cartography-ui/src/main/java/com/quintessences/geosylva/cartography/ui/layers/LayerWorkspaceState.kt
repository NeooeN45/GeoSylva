package com.quintessences.geosylva.cartography.ui.layers

enum class LayerPanel { LIBRARY, IMPORT, FORMATS, PREVIEW, DATA, ATTRIBUTES, SOURCE, MANAGE, EDIT, FILTER, APPEARANCE, COLOR }
enum class LayerValueKind { TEXT, NUMBER, BOOLEAN, NULL, ABSENT, OBJECT, ARRAY }
enum class LayerGeometryKind { POINT, LINE, POLYGON, MIXED }
enum class LayerCondition { EQUALS, CONTAINS, IS_NULL, IS_ABSENT, IS_EMPTY }
data class LayerAttributeUi(val key: String, val value: String, val kind: LayerValueKind, val editable: Boolean = false, val input: String = "")
data class LayerObjectUi(val id: String, val title: String, val subtitle: String, val geometry: String?, val attributes: List<LayerAttributeUi>)
data class LayerEntryUi(val id: String, val name: String, val count: Int, val visible: Boolean)
data class LayerChoiceUi(val key: String, val value: String, val kind: LayerValueKind)
data class LayerAppearanceUi(
    val colorArgb: Long = 0xFF087C96,
    val sizeIndex: Int = 1,
    val pinSymbol: Boolean = false,
    val labels: Boolean = true,
    val labelField: String? = null,
    val visible: Boolean = true,
)
data class LayerWorkspaceState(
    val panel: LayerPanel = LayerPanel.IMPORT,
    val busy: Boolean = false,
    val error: String? = null,
    val layers: List<LayerEntryUi> = emptyList(),
    val layerName: String = "",
    val filename: String = "",
    val format: String = "",
    val preview: Boolean = false,
    val totalCount: Int = 0,
    val visibleCount: Int = 0,
    val selectedId: String? = null,
    val objects: List<LayerObjectUi> = emptyList(),
    val selected: LayerObjectUi? = null,
    val fields: List<String> = emptyList(),
    val query: String = "",
    val filterField: String? = null,
    val filterCondition: LayerCondition = LayerCondition.EQUALS,
    val filterValueKey: String? = null,
    val filterValues: List<LayerChoiceUi> = emptyList(),
    val filterActive: Boolean = false,
    val geometry: LayerGeometryKind = LayerGeometryKind.MIXED,
    val appearance: LayerAppearanceUi = LayerAppearanceUi(),
    val colorCurrent: Long = 0xFF087C96,
    val colorInput: String = "#FF087C96",
    val colorValid: Boolean = true,
    val sourceCrs: String? = null,
    val sourceOrder: String? = null,
    val sourceProducer: String? = null,
    val sourceVersion: String? = null,
    val sourceHash: String = "",
    val sourceBasicAttributes: Boolean = false,
    val editable: Boolean = false,
    val editValid: Boolean = true,
    val editFields: List<LayerAttributeUi> = emptyList(),
)
interface LayerWorkspaceActions {
    fun onPanel(panel: LayerPanel)
    fun onBack()
    fun onClose()
    fun onChooseFile()
    fun onDiscardPreview()
    fun onCommit()
    fun onOpenLayer(id: String)
    fun onDeleteLayer()
    fun onSelectObject(id: String)
    fun onQuery(value: String)
    fun onFilterField(field: String)
    fun onFilterCondition(condition: LayerCondition)
    fun onFilterValue(key: String)
    fun onResetFilter()
    fun onAttribute(field: String, input: String)
    fun onNullAttribute(field: String)
    fun onSaveAttributes()
    fun onAppearance(appearance: LayerAppearanceUi)
    fun onSaveAppearance()
    fun onColorInput(input: String)
    fun onUseColor()
    fun onCancelColor()
}
