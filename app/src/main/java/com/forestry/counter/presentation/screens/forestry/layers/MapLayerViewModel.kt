package com.forestry.counter.presentation.screens.forestry.layers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forestry.counter.domain.model.cartography.*
import com.forestry.counter.domain.repository.*
import com.forestry.counter.domain.usecase.maps.MapDataScope
import com.quintessences.geosylva.cartography.ui.layers.*
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

/**
 * Reconstructed from the recovered repository, passive workspace and historical VM fragments.
 * Repository tokens remain the authority; browser state never grants access to a stored layer.
 */
class MapLayerViewModel(private val repository: CartographicLayerRepository) : ViewModel(), LayerWorkspaceActions {
    private val mutable = MutableStateFlow(MapLayerHostState())
    val state: StateFlow<MapLayerHostState> = mutable.asStateFlow()
    private var access: CartographicLayerAccess? = null
    private var bindingScope: MapDataScope? = null
    private var generation = 0L
    private var readSerial = 0L
    private var operation: Job? = null
    private var observation: Job? = null
    private var refreshRequested = false
    private var layers = emptyList<CartographicLayer>()
    private var stage: StagedCartographicLayer? = null
    private var activeId: String? = null
    private var panel = LayerPanel.LIBRARY
    private val history = mutableListOf<LayerPanel>()
    private data class Browser(
        var query: String = "",
        var filter: LayerFilter? = null,
        var selected: String? = null,
        var pageSize: Int = PAGE_SIZE,
    )
    private val browsers = mutableMapOf<String, Browser>()
    private var appearanceDraft: LayerStyle? = null
    private var appearanceVersion: Long? = null
    private var colorBaseline: LayerStyle? = null
    private var colorInput = ""
    private var editLayer: CartographicLayer? = null
    private var editBaseline: LayerFeature? = null
    private val editInputs = mutableMapOf<String, String>()
    private val nullFields = mutableSetOf<String>()
    private var requestFile: (() -> Unit)? = null

    private sealed interface Mutation {
        val layerId: String
        data class Commit(val staged: StagedCartographicLayer) : Mutation {
            override val layerId get() = staged.layer.id
        }
        data class Attributes(
            override val layerId: String,
            val version: Long,
            val featureId: String,
            val featureVersion: Long,
            val changes: List<LayerAttributeChange>,
        ) : Mutation
        data class Appearance(override val layerId: String, val version: Long, val style: LayerStyle) : Mutation
        data class Visibility(override val layerId: String, val version: Long, val style: LayerStyle) : Mutation
        data class Delete(override val layerId: String, val version: Long) : Mutation
    }
    private data class PendingMutation(
        val command: Mutation,
        val operationId: String = UUID.randomUUID().toString(),
        var unresolved: Boolean = false,
    )
    private var pending: PendingMutation? = null

    init {
        viewModelScope.launch {
            repository.accessChanges().catch { failure ->
                if (failure is CancellationException) throw failure
                invalidate(LayerStoreError.IO)
            }.collect {
                val a = access ?: return@collect
                if (!repository.isCurrent(a)) invalidate()
                else refreshObserved()
            }
        }
    }

    /** Bind while closed as well, so saved imported layers use the host's common map. */
    fun bind(scope: MapDataScope, open: Boolean = false) {
        if (bindingScope == scope && access?.let(repository::isCurrent) == true) {
            if (open) {
                if (pending?.unresolved != true) {
                    panel = LayerPanel.LIBRARY
                    history.clear()
                }
                publish(visible = true, error = mutable.value.error)
            }
            return
        }
        val previousKey = access?.restorationKey
        generation++
        val g = generation
        readSerial++
        operation?.cancel()
        observation?.cancel()
        discardStage()
        clearDrafts()
        access = null
        bindingScope = scope
        layers = emptyList()
        activeId = null
        panel = LayerPanel.LIBRARY
        history.clear()
        refreshRequested = false
        mutable.value = MapLayerHostState(visible = open, ui = LayerWorkspaceState(panel = panel, busy = true))
        operation = viewModelScope.launch {
            try {
                when (val captured = repository.captureAccess(scope)) {
                    is LayerStoreResult.Failure -> if (generation == g) invalidate(captured.reason)
                    is LayerStoreResult.Success -> if (generation == g) {
                        val a = captured.value
                        if (a.scope != scope || !repository.isCurrent(a)) {
                            invalidate()
                            return@launch
                        }
                        access = a
                        if (previousKey != a.restorationKey) browsers.clear()
                        when (val read = repository.list(a)) {
                            is LayerStoreResult.Failure -> if (current(a, g)) invalidate(read.reason)
                            is LayerStoreResult.Success -> if (current(a, g)) {
                                acceptLayers(read.value)
                                publish(busy = false)
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == g) invalidate(LayerStoreError.IO)
            } finally {
                if (generation == g && !mutable.value.ui.busy && refreshRequested) refreshObserved()
            }
        }
    }

    fun open(scope: MapDataScope) = bind(scope, open = true)
    fun setFileChooser(chooser: (() -> Unit)?) { requestFile = chooser }

    class FileSelectionTicket internal constructor(
        internal val generation: Long,
        internal val access: CartographicLayerAccess,
    )

    fun captureFileSelectionTicket(): FileSelectionTicket? {
        if (!interactive()) return null
        return access?.let { FileSelectionTicket(generation, it) }
    }

    fun reportFileError(error: LayerStoreError, ticket: FileSelectionTicket? = null) {
        if (ticket != null && !current(ticket.access, ticket.generation)) return
        if (interactive()) publish(error = error)
    }

    fun importFile(
        bytes: ByteArray,
        filename: String,
        format: LayerImportFormat,
        ticket: FileSelectionTicket? = null,
    ) {
        if (ticket != null && !current(ticket.access, ticket.generation)) return
        if (!interactive()) return
        val document = bytes.copyOf()
        runOperation { a, g ->
            when (val parsed = repository.stage(document, filename, format, a)) {
                is LayerStoreResult.Failure -> if (current(a, g)) failRead(parsed.reason)
                is LayerStoreResult.Success -> {
                    if (!current(a, g)) {
                        repository.discard(parsed.value.token)
                        return@runOperation
                    }
                    discardStage()
                    clearDrafts()
                    stage = parsed.value
                    activeId = parsed.value.layer.id
                    browsers[parsed.value.layer.id] = Browser()
                    panel = LayerPanel.PREVIEW
                    history.clear()
                    publish(busy = false)
                }
            }
        }
    }

    override fun onChooseFile() { if (interactive()) requestFile?.invoke() }

    override fun onPanel(panel: LayerPanel) {
        if (!interactive() || panel == this.panel) return
        val layer = active()
        if (panel !in setOf(LayerPanel.LIBRARY, LayerPanel.IMPORT, LayerPanel.FORMATS) && layer == null) return
        if (panel == LayerPanel.EDIT) {
            if (stage != null || layer == null) return
            val selected = layer.features.firstOrNull { it.id == browser().selected } ?: return
            editLayer = layer
            editBaseline = selected
            editInputs.clear()
            nullFields.clear()
        }
        if (panel == LayerPanel.APPEARANCE) {
            appearanceDraft = layer?.style
            appearanceVersion = layer?.version
        }
        if (panel == LayerPanel.COLOR) {
            val baseline = appearanceDraft ?: layer?.style ?: return
            if (appearanceDraft == null) appearanceVersion = layer?.version
            appearanceDraft = baseline
            colorBaseline = baseline
            colorInput = LayerColor.format(baseline.colorArgb)
        }
        history += this.panel
        this.panel = panel
        publish()
    }

    override fun onBack() {
        if (!interactive()) return
        if (panel == LayerPanel.LIBRARY && history.isEmpty()) {
            when {
                stage != null -> { panel = LayerPanel.PREVIEW; publish() }
                editBaseline != null -> { panel = LayerPanel.EDIT; publish() }
                appearanceDraft != null -> { panel = LayerPanel.APPEARANCE; publish() }
                else -> onClose()
            }
            return
        }
        when (panel) {
            LayerPanel.COLOR -> { onCancelColor(); return }
            LayerPanel.EDIT -> clearEdit()
            LayerPanel.APPEARANCE -> { appearanceDraft = null; appearanceVersion = null }
            else -> Unit
        }
        panel = previousPanel()
        publish()
    }

    override fun onClose() {
        val a = access ?: return
        if (!current(a, generation) || mutable.value.ui.busy) return
        if (pending?.unresolved == true) {
            // Root closes this panel before opening another tool. Keep the exact retry alive,
            // but honor that visibility handoff; open() restores the unresolved panel.
            publish(visible = false, error = mutable.value.error ?: LayerStoreError.UNKNOWN_RESULT)
            return
        }
        if (!interactive()) return
        discardStage()
        clearDrafts()
        activeId = null
        history.clear()
        panel = LayerPanel.LIBRARY
        publish(visible = false)
    }

    override fun onDiscardPreview() {
        if (!interactive()) return
        discardStage()
        clearDrafts()
        activeId = null
        history.clear()
        panel = LayerPanel.IMPORT
        publish()
    }

    override fun onCommit() {
        if (retryPending { it is Mutation.Commit }) return
        if (!interactive()) return
        val staged = stage ?: return
        submit(Mutation.Commit(staged))
    }

    override fun onOpenLayer(id: String) {
        if (!interactive()) return
        runOperation { a, g ->
            when (val read = repository.read(id, a)) {
                is LayerStoreResult.Failure -> if (current(a, g)) failRead(read.reason)
                is LayerStoreResult.Success -> if (current(a, g)) {
                    if (read.value.id != id) {
                        publish(busy = false, error = LayerStoreError.CORRUPT)
                        return@runOperation
                    }
                    layers = layers.filterNot { it.id == id } + read.value
                    discardStage()
                    clearDrafts()
                    activeId = id
                    history.clear()
                    panel = LayerPanel.MANAGE
                    publish(busy = false)
                }
            }
        }
    }

    override fun onDeleteLayer() {
        if (retryPending { it is Mutation.Delete }) return
        if (!interactive()) return
        val layer = active() ?: return
        if (stage == null) submit(Mutation.Delete(layer.id, layer.version))
    }

    /** Map selections must still belong to the latest visible, filtered immutable snapshot. */
    fun onFeatureSelected(layerId: String, featureId: String) {
        if (!interactive() || editBaseline != null || appearanceDraft != null) return
        if (stage?.layer?.id?.let { it != layerId } == true) return
        val layer = stage?.layer?.takeIf { it.id == layerId } ?: layers.firstOrNull { it.id == layerId } ?: return
        val b = browsers.getOrPut(layer.id) { Browser() }
        if (!layer.style.visible || LayerBrowser.filter(layer, b.query, b.filter).none { it.id == featureId && it.geometryType != null }) return
        activeId = layer.id
        b.selected = featureId
        panel = LayerPanel.ATTRIBUTES
        history.clear()
        publish(visible = true)
    }

    override fun onSelectObject(id: String) {
        if (!interactive() || filtered().none { it.id == id }) return
        browser().selected = id
        history += panel
        panel = LayerPanel.ATTRIBUTES
        publish()
    }

    override fun onLoadMore() {
        if (!interactive()) return
        browser().pageSize = (browser().pageSize + PAGE_SIZE).coerceAtMost(active()?.features?.size ?: PAGE_SIZE)
        publish()
    }

    override fun onQuery(value: String) {
        if (!interactive() || active() == null) return
        browser().query = value.take(240)
        browser().pageSize = PAGE_SIZE
        publish()
    }

    override fun onFilterField(field: String) {
        if (!interactive()) return
        val layer = active() ?: return
        if (field !in layer.fieldNames) return
        browser().filter = LayerFilter(field, LayerFilterOperator.EQUALS, LayerBrowser.observedValues(layer, field).firstOrNull())
        browser().pageSize = PAGE_SIZE
        publish()
    }

    override fun onFilterCondition(condition: LayerCondition) {
        if (!interactive()) return
        val layer = active() ?: return
        val filter = browser().filter ?: layer.fieldNames.firstOrNull()?.let { LayerFilter(it, LayerFilterOperator.EQUALS) } ?: return
        val values = LayerBrowser.observedValues(layer, filter.field)
        val op = LayerFilterOperator.valueOf(condition.name)
        val text = values.firstOrNull { it is JsonPrimitive && it.isString }
        if (op in setOf(LayerFilterOperator.CONTAINS, LayerFilterOperator.IS_EMPTY) && text == null) return
        val value = if (op == LayerFilterOperator.CONTAINS) text else filter.value ?: values.firstOrNull()
        browser().filter = filter.copy(operator = op, value = value)
        browser().pageSize = PAGE_SIZE
        publish()
    }

    override fun onFilterValue(key: String) {
        if (!interactive()) return
        val layer = active() ?: return
        val filter = browser().filter ?: return
        val value = LayerBrowser.observedValues(layer, filter.field).firstOrNull { it.toString() == key } ?: return
        browser().filter = filter.copy(value = value)
        browser().pageSize = PAGE_SIZE
        publish()
    }

    override fun onFilterText(value: String) {
        if (!interactive()) return
        val filter = browser().filter?.takeIf { it.operator == LayerFilterOperator.CONTAINS } ?: return
        browser().filter = filter.copy(value = JsonPrimitive(value.take(240)))
        browser().pageSize = PAGE_SIZE
        publish()
    }

    override fun onResetFilter() {
        if (!interactive()) return
        browser().filter = null
        browser().pageSize = PAGE_SIZE
        publish()
    }

    override fun onAttribute(field: String, input: String) {
        if (!interactive() || !editableField(field)) return
        editInputs[field] = input.take(4001)
        nullFields -= field
        publish()
    }

    override fun onNullAttribute(field: String) {
        if (!interactive() || !editableField(field)) return
        nullFields += field
        editInputs -= field
        publish()
    }

    override fun onSaveAttributes() {
        if (retryPending { it is Mutation.Attributes }) return
        if (!interactive()) return
        val layer = editLayer ?: return
        val original = editBaseline ?: return
        val changes = changes() ?: run { publish(error = LayerStoreError.INVALID_INPUT); return }
        if (changes.isEmpty()) { onBack(); return }
        submit(Mutation.Attributes(layer.id, layer.version, original.id, original.version, changes))
    }

    override fun onAppearance(appearance: LayerAppearanceUi) {
        if (!interactive()) return
        val layer = active() ?: return
        val size = LayerVisualSize.entries.getOrNull(appearance.sizeIndex) ?: return
        if (appearance.colorArgb !in 0..0xFFFFFFFFL || appearance.labelField?.let { it !in LayerLabelFields.available(layer.features) } == true) {
            publish(error = LayerStoreError.INVALID_INPUT)
            return
        }
        if (appearanceDraft == null) appearanceVersion = layer.version
        appearanceDraft = (appearanceDraft ?: layer.style).copy(
            colorArgb = appearance.colorArgb, size = size,
            pointSymbol = if (appearance.pinSymbol) LayerPointSymbol.PIN else LayerPointSymbol.DOT,
            labels = appearance.labels, labelField = appearance.labelField, visible = appearance.visible,
        )
        publish()
    }

    /** The common Couches checkbox never changes another layer's editor or selection. */
    fun setLayerVisible(id: String, visible: Boolean) {
        if (retryPending { it is Mutation.Visibility && it.layerId == id && it.style.visible == visible }) return
        if (!interactive()) return
        val layer = layers.firstOrNull { it.id == id } ?: run { publish(error = LayerStoreError.UNAVAILABLE); return }
        if (layer.style.visible != visible) submit(Mutation.Visibility(id, layer.version, layer.style.copy(visible = visible)))
    }

    override fun onSaveAppearance() {
        if (retryPending { it is Mutation.Appearance }) return
        if (!interactive()) return
        val layer = active() ?: return
        if (stage != null) { publish(error = LayerStoreError.INVALID_INPUT); return }
        val draft = appearanceDraft ?: layer.style
        if (draft == layer.style) { if (panel == LayerPanel.APPEARANCE) onBack(); return }
        submit(Mutation.Appearance(layer.id, appearanceVersion ?: layer.version, draft))
    }

    override fun onColorInput(input: String) {
        if (!interactive() || panel != LayerPanel.COLOR) return
        colorInput = input.take(32)
        LayerColor.parse(colorInput)?.let { color ->
            appearanceDraft = (appearanceDraft ?: active()?.style)?.copy(colorArgb = color)
        }
        publish()
    }

    override fun onUseColor() {
        if (!interactive() || panel != LayerPanel.COLOR) return
        val color = LayerColor.parse(colorInput) ?: return
        appearanceDraft = (appearanceDraft ?: active()?.style)?.copy(colorArgb = color)
        colorBaseline = null
        panel = previousPanel(LayerPanel.APPEARANCE)
        publish()
    }

    override fun onCancelColor() {
        if (!interactive() || panel != LayerPanel.COLOR) return
        appearanceDraft = colorBaseline
        colorBaseline = null
        panel = previousPanel(LayerPanel.APPEARANCE)
        publish()
    }

    private fun editableField(field: String): Boolean {
        val layer = editLayer ?: return false
        val original = editBaseline ?: return false
        return LayerAttributePolicy.supportsLocalField(layer, original, field) && rawText(original.properties[field]).length <= 4000
    }

    private fun changes(): List<LayerAttributeChange>? {
        val layer = editLayer ?: return emptyList()
        val original = editBaseline ?: return emptyList()
        val changes = nullFields.map { LayerAttributeChange(it, JsonNull) }.toMutableList()
        for ((field, input) in editInputs) {
            val old = original.properties[field]
            val value = when {
                old is JsonPrimitive && old != JsonNull && !old.isString && old.booleanOrNull != null ->
                    input.trim().toBooleanStrictOrNull()?.let(::JsonPrimitive) ?: return null
                old is JsonPrimitive && old != JsonNull && !old.isString -> {
                    val parsed = runCatching { Json.parseToJsonElement(input) }.getOrNull() as? JsonPrimitive ?: return null
                    if (parsed.isString || parsed.doubleOrNull?.isFinite() != true || parsed.booleanOrNull != null) return null
                    parsed
                }
                else -> JsonPrimitive(input)
            }
            changes += LayerAttributeChange(field, value)
        }
        return changes.takeIf { LayerAttributePolicy.validate(layer, original, it) }
    }

    /** An uncertain write can only retry its frozen command and operation identity. */
    private fun retryPending(matches: (Mutation) -> Boolean): Boolean {
        val unresolved = pending?.takeIf { it.unresolved } ?: return false
        if (matches(unresolved.command)) submit(unresolved.command)
        else publish(error = LayerStoreError.UNKNOWN_RESULT)
        return true
    }

    private fun submit(command: Mutation) {
        if (mutable.value.ui.busy) return
        val existing = pending
        if (existing?.unresolved == true && existing.command != command) {
            publish(error = LayerStoreError.UNKNOWN_RESULT)
            return
        }
        val a = access ?: return
        if (!current(a, generation)) return
        val request = existing?.takeIf { it.command == command } ?: PendingMutation(command)
        pending = request
        runOperation(mutation = true) { token, g ->
            val result = execute(command, request.operationId, token)
            if (!current(token, g)) return@runOperation
            when (result) {
                is LayerStoreResult.Failure -> {
                    request.unresolved = result.reason in setOf(LayerStoreError.UNKNOWN_RESULT, LayerStoreError.IO)
                    if (!request.unresolved) pending = null
                    if (result.reason == LayerStoreError.ACCESS_CHANGED) invalidate()
                    else publish(busy = false, error = result.reason)
                }
                is LayerStoreResult.Success -> {
                    // A successful write is still not presented as a refreshed snapshot until this read succeeds.
                    request.unresolved = true
                    when (val refreshed = repository.list(token)) {
                        is LayerStoreResult.Failure -> if (current(token, g)) {
                            if (refreshed.reason == LayerStoreError.ACCESS_CHANGED) invalidate()
                            else publish(busy = false, error = refreshed.reason)
                        }
                        is LayerStoreResult.Success -> if (current(token, g)) {
                            val present = refreshed.value.any { it.id == command.layerId }
                            if (command is Mutation.Delete && present) {
                                publish(busy = false, error = LayerStoreError.UNKNOWN_RESULT)
                            } else {
                                pending = null
                                acceptLayers(refreshed.value)
                                finishMutation(command, present)
                                publish(busy = false, error = if (command !is Mutation.Delete && !present) LayerStoreError.UNAVAILABLE else null)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun execute(command: Mutation, id: String, a: CartographicLayerAccess): LayerStoreResult<Unit> {
        val result = when (command) {
            is Mutation.Commit -> repository.commit(command.staged.token, id, a)
            is Mutation.Attributes -> repository.patchAttributes(command.layerId, command.featureId, command.version, command.featureVersion, id, command.changes, a)
            is Mutation.Appearance -> repository.updateStyle(command.layerId, command.version, id, command.style, a)
            is Mutation.Visibility -> repository.updateStyle(command.layerId, command.version, id, command.style, a)
            is Mutation.Delete -> repository.delete(command.layerId, command.version, id, a)
        }
        return when (result) {
            is LayerStoreResult.Success -> LayerStoreResult.Success(Unit)
            is LayerStoreResult.Failure -> result
        }
    }

    private fun finishMutation(command: Mutation, present: Boolean) {
        when (command) {
            is Mutation.Commit -> {
                stage?.let { repository.discard(it.token) }
                stage = null
                clearDrafts()
                activeId = command.layerId.takeIf { present }
                panel = if (present) LayerPanel.MANAGE else LayerPanel.LIBRARY
                history.clear()
            }
            is Mutation.Attributes -> {
                clearEdit()
                if (activeId == command.layerId) panel = if (browser().selected != null) LayerPanel.ATTRIBUTES else LayerPanel.DATA
                if (history.lastOrNull() == LayerPanel.ATTRIBUTES) history.removeAt(history.lastIndex)
            }
            is Mutation.Appearance -> {
                appearanceDraft = null
                appearanceVersion = null
                colorBaseline = null
                if (panel == LayerPanel.APPEARANCE) panel = previousPanel(LayerPanel.MANAGE)
            }
            is Mutation.Visibility -> if (activeId == command.layerId) {
                appearanceDraft = appearanceDraft?.copy(visible = command.style.visible)
                colorBaseline = colorBaseline?.copy(visible = command.style.visible)
                val fresh = active()
                if (fresh != null && appearanceVersion == command.version && command.version < Long.MAX_VALUE &&
                    fresh.version == command.version + 1 && fresh.style == command.style) appearanceVersion = fresh.version
            }
            is Mutation.Delete -> {
                browsers.remove(command.layerId)
                if (activeId == command.layerId) {
                    activeId = null
                    clearDrafts()
                    history.clear()
                    panel = LayerPanel.LIBRARY
                }
            }
        }
    }

    private fun runOperation(mutation: Boolean = false, block: suspend (CartographicLayerAccess, Long) -> Unit) {
        if (mutable.value.ui.busy) return
        val a = access ?: return
        val g = generation
        if (!current(a, g)) return
        readSerial++
        observation?.cancel()
        publish(busy = true)
        operation = viewModelScope.launch {
            try {
                block(a, g)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (current(a, g)) {
                    if (mutation) pending?.unresolved = true
                    publish(busy = false, error = if (mutation) LayerStoreError.UNKNOWN_RESULT else LayerStoreError.IO)
                }
            } finally {
                if (generation == g && access === a && !mutable.value.ui.busy && refreshRequested) refreshObserved()
            }
        }
    }

    private fun refreshObserved() {
        if (mutable.value.ui.busy) { refreshRequested = true; return }
        refreshRequested = false
        val a = access ?: return
        val g = generation
        if (!current(a, g)) return
        val serial = ++readSerial
        observation?.cancel()
        observation = viewModelScope.launch {
            try {
                val result = repository.list(a)
                if (serial != readSerial || !current(a, g)) return@launch
                when (result) {
                    is LayerStoreResult.Failure -> failRead(result.reason)
                    is LayerStoreResult.Success -> {
                        val removed = activeId != null && activeId != stage?.layer?.id && result.value.none { it.id == activeId }
                        acceptLayers(result.value)
                        publish(error = if (removed) LayerStoreError.UNAVAILABLE else mutable.value.error)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (serial == readSerial && current(a, g)) publish(error = LayerStoreError.IO)
            }
        }
    }

    private fun acceptLayers(snapshot: List<CartographicLayer>) {
        layers = snapshot
        if (activeId != null && activeId != stage?.layer?.id && layers.none { it.id == activeId }) {
            clearDrafts()
            activeId = null
            panel = LayerPanel.LIBRARY
            history.clear()
        }
    }

    private fun failRead(reason: LayerStoreError) {
        if (reason in setOf(LayerStoreError.ACCESS_CHANGED, LayerStoreError.UNAVAILABLE)) invalidate(reason)
        else publish(busy = false, error = reason)
    }

    private fun current(a: CartographicLayerAccess, g: Long): Boolean {
        if (generation != g || access !== a) return false
        if (!repository.isCurrent(a)) { invalidate(); return false }
        return true
    }

    private fun interactive(): Boolean {
        val a = access ?: return false
        if (!current(a, generation) || mutable.value.ui.busy) return false
        if (pending?.unresolved == true) { publish(error = LayerStoreError.UNKNOWN_RESULT); return false }
        return true
    }

    private fun invalidate(reason: LayerStoreError = LayerStoreError.ACCESS_CHANGED) {
        generation++
        readSerial++
        operation?.cancel()
        observation?.cancel()
        operation = null
        observation = null
        access = null
        discardStage()
        clearDrafts()
        layers = emptyList()
        browsers.clear()
        activeId = null
        history.clear()
        panel = LayerPanel.LIBRARY
        mutable.value = MapLayerHostState(visible = mutable.value.visible, error = reason, ui = LayerWorkspaceState(panel = panel))
    }

    private fun discardStage() {
        stage?.let { repository.discard(it.token); browsers.remove(it.layer.id) }
        stage = null
    }

    private fun clearEdit() { editLayer = null; editBaseline = null; editInputs.clear(); nullFields.clear() }
    private fun clearDrafts() {
        appearanceDraft = null
        appearanceVersion = null
        colorBaseline = null
        clearEdit()
        pending = null
    }
    private fun active(): CartographicLayer? = stage?.layer?.takeIf { it.id == activeId } ?: layers.firstOrNull { it.id == activeId }
    private fun browser(): Browser = browsers.getOrPut(activeId ?: "") { Browser() }
    private fun filtered(): List<LayerFeature> = active()?.let { LayerBrowser.filter(it, browser().query, browser().filter) }.orEmpty()
    private fun previousPanel(fallback: LayerPanel = if (stage != null) LayerPanel.PREVIEW else LayerPanel.LIBRARY): LayerPanel =
        if (history.isNotEmpty()) history.removeAt(history.lastIndex) else fallback

    private fun publish(busy: Boolean = mutable.value.ui.busy, error: LayerStoreError? = null, visible: Boolean = mutable.value.visible) {
        val layer = active()
        val browser = browser()
        val filtered = filtered()
        if (browser.selected != null && filtered.none { it.id == browser.selected }) browser.selected = null
        val selected = layer?.features?.firstOrNull { it.id == browser.selected }
        val style = appearanceDraft ?: layer?.style ?: LayerStyle()
        val filter = browser.filter
        val observed = if (filter != null && layer != null) LayerBrowser.observedValues(layer, filter.field) else emptyList()
        val fields = layer?.fieldNames.orEmpty()
        val ui = LayerWorkspaceState(
            panel = panel, busy = busy, layerName = layer?.name.orEmpty(), filename = layer?.source?.filename.orEmpty(),
            format = layer?.source?.format.orEmpty(), preview = stage != null, totalCount = layer?.features?.size ?: 0,
            visibleCount = filtered.size, selectedId = browser.selected,
            objects = filtered.take(browser.pageSize).map { objectUi(it, fields) }, canLoadMore = filtered.size > browser.pageSize,
            selected = selected?.let { objectUi(it, fields) }, fields = fields,
            labelFields = layer?.let { LayerLabelFields.available(it.features) }.orEmpty(),
            query = browser.query, filterField = filter?.field,
            filterCondition = filter?.operator?.name?.let(LayerCondition::valueOf) ?: LayerCondition.EQUALS,
            filterText = (filter?.value as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty(),
            filterConditions = LayerCondition.entries.filter {
                it !in setOf(LayerCondition.CONTAINS, LayerCondition.IS_EMPTY) || observed.any { value -> value is JsonPrimitive && value.isString }
            },
            filterValueKey = filter?.value?.toString(), filterActive = filter != null,
            filterValues = observed.map { LayerChoiceUi(it.toString(), text(it), kind(it)) },
            geometry = layer?.geometryFamily?.name?.let(LayerGeometryKind::valueOf) ?: LayerGeometryKind.MIXED,
            appearance = styleUi(style), colorCurrent = colorBaseline?.colorArgb ?: style.colorArgb,
            colorInput = colorInput, colorValid = LayerColor.parse(colorInput) != null,
            sourceCrs = layer?.source?.sourceCrs, sourceCrsFromFormat = layer?.source?.crsEvidence == "FORMAT_CONTRACT",
            sourceOrder = layer?.source?.sourceCoordinateOrder, sourceProducer = layer?.source?.producer,
            sourceVersion = layer?.source?.sourceVersion, sourceHash = layer?.source?.sha256.orEmpty(),
            sourceBasicAttributes = layer?.source?.warnings?.contains("KML_BASIC_ATTRIBUTES") == true,
            sourceFields = selected?.let { feature ->
                val sourceProperties = feature.sourceFeature["properties"] as? JsonObject
                fields.map { field -> LayerAttributeUi(field, text(sourceProperties?.get(field)), kind(sourceProperties?.get(field))) }
            }.orEmpty(),
            editable = stage == null && layer != null && access?.let(repository::isCurrent) == true,
            editValid = changes() != null,
            editFields = editBaseline?.let { original ->
                editLayer?.fieldNames.orEmpty().map { field ->
                    val value = if (field in nullFields) JsonNull else original.properties[field]
                    LayerAttributeUi(field, text(value), kind(value), editableField(field),
                        editInputs[field] ?: if (field in nullFields || value == JsonNull || value == null) "" else rawText(value).take(4000))
                }
            }.orEmpty(),
            layers = layers.map { LayerEntryUi(it.id, it.name, it.features.size, it.style.visible) },
        )
        val renders = (layers.filterNot { it.id == stage?.layer?.id } + listOfNotNull(stage?.layer)).map { source ->
            val b = browsers.getOrPut(source.id) { Browser() }
            val features = LayerBrowser.filter(source, b.query, b.filter)
            if (b.selected != null && features.none { it.id == b.selected }) b.selected = null
            LayerRenderSnapshot(source.id, source.geoJson(features),
                if (source.id == activeId) appearanceDraft ?: source.style else source.style,
                source.geometryFamily, b.selected, source.id == stage?.layer?.id)
        }
        mutable.value = MapLayerHostState(visible, ui, error, renders)
    }

    private fun objectUi(feature: LayerFeature, fields: List<String>): LayerObjectUi {
        val values = feature.properties
        val title = listOf("name", "nom", "label", "title").firstNotNullOfOrNull { field ->
            (values[field] as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotBlank() }?.content
        } ?: feature.id
        val subtitle = values.entries.firstOrNull { it.key !in setOf("name", "nom", "label", "title") && it.value != JsonNull }
            ?.let { it.key + " : " + text(it.value) }.orEmpty()
        return LayerObjectUi(feature.id, title.take(240), subtitle.take(240), feature.geometryType,
            fields.map { field -> LayerAttributeUi(field, text(values[field]), kind(values[field])) })
    }

    private fun styleUi(style: LayerStyle) = LayerAppearanceUi(style.colorArgb, style.size.ordinal,
        style.pointSymbol == LayerPointSymbol.PIN, style.labels, style.labelField, style.visible)
    private fun rawText(value: JsonElement?): String = if (value is JsonPrimitive) value.content else value?.toString().orEmpty()
    private fun text(value: JsonElement?): String = if (value == null || value == JsonNull) "" else rawText(value)
    private fun kind(value: JsonElement?): LayerValueKind = when {
        value == null -> LayerValueKind.ABSENT
        value == JsonNull -> LayerValueKind.NULL
        value is JsonObject -> LayerValueKind.OBJECT
        value is JsonArray -> LayerValueKind.ARRAY
        value is JsonPrimitive && value.isString -> LayerValueKind.TEXT
        value is JsonPrimitive && value.booleanOrNull != null -> LayerValueKind.BOOLEAN
        else -> LayerValueKind.NUMBER
    }

    override fun onCleared() {
        generation++
        operation?.cancel()
        observation?.cancel()
        discardStage()
        clearDrafts()
        requestFile = null
        super.onCleared()
    }

    companion object { private const val PAGE_SIZE = 100 }
}
