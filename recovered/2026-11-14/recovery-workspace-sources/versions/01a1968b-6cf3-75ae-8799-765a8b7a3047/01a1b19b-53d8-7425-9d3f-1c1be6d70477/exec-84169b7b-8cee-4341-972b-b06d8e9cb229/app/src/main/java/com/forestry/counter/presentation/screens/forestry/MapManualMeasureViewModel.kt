package com.forestry.counter.presentation.screens.forestry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forestry.counter.domain.calculation.cartography.*
import com.forestry.counter.domain.repository.*
import com.forestry.counter.domain.usecase.maps.MapDataScope
import com.quintessences.geosylva.cartography.ui.measurement.MeasureAreaUnit as DisplayAreaUnit
import com.quintessences.geosylva.cartography.ui.measurement.MeasureDistanceUnit
import com.quintessences.geosylva.cartography.ui.measurement.MeasurePanel
import com.quintessences.geosylva.cartography.ui.measurement.MeasureHelp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

internal data class ManualMeasureState(
    val ready: Boolean = false,
    val active: Boolean = false,
    val mode: CartographicMeasureKind = CartographicMeasureKind.DISTANCE,
    val distancePoints: List<CartographicCoordinate> = emptyList(),
    val areaPoints: List<CartographicCoordinate> = emptyList(),
    val distanceSourceId: String? = null,
    val areaSourceId: String? = null,
    val distanceUnit: MeasureDistanceUnit = MeasureDistanceUnit.METRES,
    val areaUnit: DisplayAreaUnit = DisplayAreaUnit.HECTARES,
    val panel: MeasurePanel = MeasurePanel.EDITOR,
    val result: CartographicMeasureResult? = null,
    val calculating: Boolean = false,
    val colorArgb: Int = 0xFF176BDF.toInt(),
    val saveName: String = "",
    val saving: Boolean = false,
    val saveLocked: Boolean = false,
    val error: CartographicStoreError? = null,
    val auxiliary: ManualMeasureAuxiliary = ManualMeasureAuxiliary.NONE,
    val help: MeasureHelp? = null,
    val saved: List<SavedCartographicMeasurement> = emptyList(),
    val selected: SavedCartographicMeasurement? = null,
    val loadingSaved: Boolean = false,
    val savedError: CartographicStoreError? = null,
    val copyConfirmation: Boolean = false,
    val savedNotice: Boolean = false,
) {
    val points: List<CartographicCoordinate> get() = if (mode == CartographicMeasureKind.DISTANCE) distancePoints else areaPoints
    val sourceId: String? get() = if (mode == CartographicMeasureKind.DISTANCE) distanceSourceId else areaSourceId
}

/** One controller for all map hosts. Commands retain the authority captured for their draft. */
internal class MapManualMeasureViewModel(
    private val repository: CartographicMeasurementRepository,
    private val scope: MapDataScope,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val calculationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val mutable = MutableStateFlow(ManualMeasureState())
    val state = mutable.asStateFlow()
    private var access: CartographicMeasurementAccess? = null
    private var calculationJob: Job? = null
    private var calculationRevision = 0L
    private var pendingInput: CartographicMeasurementInput? = null
    private var operationId: String? = null
    private var restored = false
    private var navigationRevision = 0L
    private var listRevision = 0L

    init {
        viewModelScope.launch {
            repository.accessChanges().onStart { emit(Unit) }.collectLatest { refreshAccess() }
        }
    }

    private suspend fun refreshAccess() {
        navigationRevision++; listRevision++
        access = null
        mutable.update { it.copy(ready = false, selected = null, saved = emptyList(),
            auxiliary = ManualMeasureAuxiliary.NONE, loadingSaved = false) }
        when (val result = repository.captureAccess(scope)) {
            is CartographicStoreResult.Failure -> {
                if (result.reason == CartographicStoreError.ACCESS_CHANGED) forgetDrafts()
                mutable.update { it.copy(ready = false, saving = false, error = result.reason) }
            }
            is CartographicStoreResult.Success -> {
                val next = result.value
                if (!repository.isCurrent(next)) return
                val previousKey: String? = savedState[OWNER_KEY]
                if (previousKey != next.restorationKey) forgetDrafts()
                access = next
                savedState[OWNER_KEY] = next.restorationKey
                if (!restored) restoreDrafts()
                mutable.update { it.copy(ready = true, error = null) }
                recalculate()
            }
        }
    }

    fun retryAccess() { viewModelScope.launch { refreshAccess() } }
    private fun current(): CartographicMeasurementAccess? = access?.takeIf { repository.isCurrent(it) && mutable.value.ready }
    private fun editable() = current() != null && !mutable.value.saving && mutable.value.panel != MeasurePanel.SAVE && !mutable.value.saveLocked

    fun open(saved: Boolean = false) {
        navigationRevision++
        mutable.update { it.copy(active = true, panel = if (saved) MeasurePanel.COLLAPSED else MeasurePanel.EDITOR,
            selected = null, loadingSaved = false, auxiliary = ManualMeasureAuxiliary.NONE, savedNotice = false) }
        if (saved) openSaved()
        persistDrafts()
    }

    fun leave() {
        if (mutable.value.saving) return
        navigationRevision++
        mutable.update { it.copy(active = false, selected = null, loadingSaved = false, auxiliary = ManualMeasureAuxiliary.NONE) }
        persistDrafts()
    }

    fun panel(panel: MeasurePanel) {
        if (mutable.value.saving || mutable.value.panel == MeasurePanel.SAVE) return
        navigationRevision++
        mutable.update { it.copy(panel = panel, selected = null, loadingSaved = false, auxiliary = ManualMeasureAuxiliary.NONE) }
        persistDrafts()
    }

    fun mode(kind: CartographicMeasureKind) {
        if (!editable() || mutable.value.mode == kind) return
        navigationRevision++
        // Separate drafts prevent a mode switch from silently erasing the other geometry.
        mutable.update { it.copy(mode = kind, selected = null, savedNotice = false) }
        recalculate(); persistDrafts()
    }

    fun addPoint(latitude: Double, longitude: Double) {
        val s = mutable.value
        if (!editable() || !s.active || s.panel != MeasurePanel.EDITOR || s.auxiliary != ManualMeasureAuxiliary.NONE) return
        val point = CartographicCoordinate(latitude, longitude)
        if (!CartographicMeasureCalculator.isValidCoordinate(point) || s.points.size >= CartographicMeasureCalculator.MAX_POINTS) return
        // A second tap at the same coordinate is not an additional vertex.
        if (s.points.lastOrNull() == point) return
        replacePoints(s.points + point)
    }

    fun undo() { if (editable()) replacePoints(mutable.value.points.dropLast(1)) }
    fun clear() {
        if (!editable()) return
        val s = mutable.value
        mutable.value = if (s.mode == CartographicMeasureKind.DISTANCE)
            s.copy(distanceSourceId = null) else s.copy(areaSourceId = null)
        replacePoints(emptyList()); auxiliary(ManualMeasureAuxiliary.NONE)
    }

    private fun replacePoints(points: List<CartographicCoordinate>) {
        mutable.update { if (it.mode == CartographicMeasureKind.DISTANCE) it.copy(distancePoints = points.toList(), savedNotice = false)
            else it.copy(areaPoints = points.toList(), savedNotice = false) }
        recalculate(); persistDrafts()
    }

    private fun recalculate() {
        calculationJob?.cancel()
        val ticket = ++calculationRevision
        val snapshot = mutable.value
        mutable.update { it.copy(result = null, calculating = true) }
        calculationJob = viewModelScope.launch {
            val result = withContext(calculationDispatcher) { CartographicMeasureCalculator.calculate(snapshot.mode, snapshot.points) }
            if (ticket == calculationRevision && current() != null) mutable.update { it.copy(result = result, calculating = false) }
        }
    }

    fun distanceUnit(unit: MeasureDistanceUnit) {
        if (mutable.value.saving) return
        mutable.update { it.copy(distanceUnit = unit) }; persistDrafts()
    }
    fun areaUnit(unit: DisplayAreaUnit) {
        if (mutable.value.saving) return
        mutable.update { it.copy(areaUnit = unit) }; persistDrafts()
    }
    fun color(argb: Int) { if (editable()) { mutable.update { it.copy(colorArgb = argb) }; persistDrafts() } }
    fun auxiliary(panel: ManualMeasureAuxiliary) {
        if (mutable.value.saving) return
        navigationRevision++
        val keepSelection = panel in setOf(ManualMeasureAuxiliary.DETAIL, ManualMeasureAuxiliary.HELP, ManualMeasureAuxiliary.SHARE)
        mutable.update { it.copy(auxiliary = panel, selected = if (keepSelection) it.selected else null,
            loadingSaved = false, copyConfirmation = false) }
    }
    fun help(help: MeasureHelp) { auxiliary(ManualMeasureAuxiliary.HELP); mutable.update { it.copy(help = help) } }

    fun requestSave() {
        if (current() == null || mutable.value.result !is CartographicMeasureResult.Available || mutable.value.saving) return
        navigationRevision++
        if (operationId == null) operationId = UUID.randomUUID().toString()
        mutable.update { it.copy(panel = MeasurePanel.SAVE, auxiliary = ManualMeasureAuxiliary.NONE, error = null) }
        persistDrafts()
    }

    fun name(value: String) {
        if (mutable.value.saving || mutable.value.saveLocked) return
        pendingInput = null
        mutable.update { it.copy(saveName = value.take(160), error = null) }; persistDrafts()
    }

    fun cancelSave() {
        if (mutable.value.saving) return
        if (!mutable.value.saveLocked) { pendingInput = null; operationId = null }
        mutable.update { it.copy(panel = MeasurePanel.EDITOR, error = null) }; persistDrafts()
    }

    fun confirmSave() {
        val authority = current() ?: return
        val s = mutable.value
        if (s.saving || s.panel != MeasurePanel.SAVE || s.result !is CartographicMeasureResult.Available) return
        val key = authority.restorationKey
        val input = pendingInput ?: CartographicMeasurementInput(
            id = operationId ?: UUID.randomUUID().toString().also { operationId = it },
            name = s.saveName.trim().ifBlank { null }, kind = s.mode, points = s.points.toList(),
            sourceMeasurementId = s.sourceId,
        ).also { pendingInput = it }
        mutable.update { it.copy(saving = true, error = null) }; persistDrafts()
        viewModelScope.launch {
            val result = repository.save(input, authority)
            // A late answer from an old account/context may never update the new editor.
            if (access?.restorationKey != key || operationId != input.id) return@launch
            if (!repository.isCurrent(authority)) {
                mutable.update { it.copy(saving = false, saveLocked = true, error = CartographicStoreError.ACCESS_CHANGED) }
                return@launch
            }
            when (result) {
                is CartographicStoreResult.Success -> {
                    pendingInput = null; operationId = null
                    mutable.update { it.copy(saving = false, saveLocked = false, panel = MeasurePanel.EDITOR,
                        error = null, saveName = "", savedNotice = true) }
                    loadSaved(authority, show = false)
                }
                is CartographicStoreResult.Failure -> {
                    val uncertain = result.reason == CartographicStoreError.UNKNOWN_RESULT || result.reason == CartographicStoreError.ACCESS_CHANGED
                    mutable.update { it.copy(saving = false, error = result.reason, saveLocked = uncertain) }
                    if (!uncertain) pendingInput = null
                }
            }
            persistDrafts()
        }
    }

    fun openSaved() {
        navigationRevision++
        mutable.update { it.copy(auxiliary = ManualMeasureAuxiliary.SAVED, selected = null) }
        val authority = current() ?: return
        viewModelScope.launch { loadSaved(authority, show = true) }
    }

    private suspend fun loadSaved(authority: CartographicMeasurementAccess, show: Boolean) {
        val ticket = ++listRevision
        val navigation = navigationRevision
        if (show) mutable.update { it.copy(loadingSaved = true, savedError = null) }
        val result = repository.list(authority)
        if (ticket != listRevision || (show && navigation != navigationRevision) ||
            !repository.isCurrent(authority) || access?.restorationKey != authority.restorationKey) return
        mutable.update { s -> when (result) {
            is CartographicStoreResult.Success -> s.copy(loadingSaved = if (show) false else s.loadingSaved, saved = result.value.toList())
            is CartographicStoreResult.Failure -> s.copy(loadingSaved = if (show) false else s.loadingSaved, saved = emptyList(), savedError = result.reason)
        } }
    }

    fun selectSaved(id: String) {
        val authority = current() ?: return
        val ticket = ++navigationRevision
        mutable.update { it.copy(loadingSaved = true, selected = null, savedError = null, auxiliary = ManualMeasureAuxiliary.DETAIL) }
        viewModelScope.launch {
            val result = repository.read(id, authority)
            if (ticket != navigationRevision || !repository.isCurrent(authority) || access?.restorationKey != authority.restorationKey) return@launch
            mutable.update { when (result) {
                is CartographicStoreResult.Success -> it.copy(loadingSaved = false, selected = result.value)
                is CartographicStoreResult.Failure -> it.copy(loadingSaved = false, selected = null, savedError = result.reason)
            } }
        }
    }

    fun requestCopy() {
        if (mutable.value.selected == null || !editable()) return
        val s = mutable.value
        val target = if (s.selected?.kind == CartographicMeasureKind.DISTANCE) s.distancePoints else s.areaPoints
        if (target.isNotEmpty()) mutable.update { it.copy(copyConfirmation = true) }
        else copySelected()
    }

    fun cancelCopy() { mutable.update { it.copy(copyConfirmation = false) } }
    fun copySelected() {
        val authority = current() ?: return
        val id = mutable.value.selected?.id ?: return
        if (!editable()) return
        val ticket = navigationRevision
        viewModelScope.launch {
            val result = repository.read(id, authority)
            if (ticket != navigationRevision || !repository.isCurrent(authority) || access?.restorationKey != authority.restorationKey) return@launch
            if (result is CartographicStoreResult.Success) {
                val record = result.value
                mutable.update { if (record.kind == CartographicMeasureKind.DISTANCE)
                    it.copy(mode = record.kind, distancePoints = record.points.toList(), distanceSourceId = record.id)
                    else it.copy(mode = record.kind, areaPoints = record.points.toList(), areaSourceId = record.id) }
                pendingInput = null; operationId = null
                mutable.update { it.copy(active = true, selected = null, panel = MeasurePanel.EDITOR,
                    saveName = "", auxiliary = ManualMeasureAuxiliary.NONE, copyConfirmation = false, saveLocked = false) }
                recalculate(); persistDrafts()
            } else mutable.update { it.copy(savedError = (result as CartographicStoreResult.Failure).reason) }
        }
    }

    /** Explicit external actions re-read their selected immutable record under live authority. */
    fun withSelectedRecord(action: (SavedCartographicMeasurement) -> Unit) {
        val authority = current() ?: return
        val id = mutable.value.selected?.id ?: return
        val ticket = navigationRevision
        viewModelScope.launch {
            when (val result = repository.read(id, authority)) {
                is CartographicStoreResult.Success -> if (ticket == navigationRevision && repository.isCurrent(authority) &&
                    access?.restorationKey == authority.restorationKey && mutable.value.selected?.id == id) action(result.value)
                is CartographicStoreResult.Failure -> mutable.update { it.copy(savedError = result.reason) }
            }
        }
    }

    fun withCurrentDraft(action: (ManualMeasureState) -> Unit) {
        val authority = current() ?: return
        val snapshot = mutable.value
        val ticket = navigationRevision
        if (snapshot.result !is CartographicMeasureResult.Available) return
        viewModelScope.launch {
            val checked = repository.captureAccess(scope)
            if (checked is CartographicStoreResult.Success && repository.isCurrent(checked.value) &&
                checked.value.restorationKey == authority.restorationKey && ticket == navigationRevision &&
                current() != null && mutable.value.points == snapshot.points && mutable.value.mode == snapshot.mode) action(snapshot)
        }
    }

    fun consumeSavedNotice() { mutable.update { it.copy(savedNotice = false) } }

    private fun persistDrafts() {
        if (access == null) return
        val s = mutable.value
        savedState["distance"] = encode(s.distancePoints); savedState["area"] = encode(s.areaPoints)
        savedState["mode"] = s.mode.name; savedState["active"] = s.active; savedState["panel"] = s.panel.name
        savedState["distanceUnit"] = s.distanceUnit.name; savedState["areaUnit"] = s.areaUnit.name
        savedState["name"] = s.saveName; savedState["operationId"] = operationId
        savedState["sourceDistance"] = s.distanceSourceId; savedState["sourceArea"] = s.areaSourceId
        savedState["color"] = s.colorArgb; savedState["locked"] = s.saveLocked || s.saving
    }

    private fun restoreDrafts() {
        restored = true
        fun <T : Enum<T>> parse(value: String?, values: Array<T>, fallback: T) = values.firstOrNull { it.name == value } ?: fallback
        val s = mutable.value
        val panel = parse(savedState["panel"], MeasurePanel.entries.toTypedArray(), MeasurePanel.EDITOR)
        mutable.value = s.copy(distancePoints = decode(savedState["distance"]), areaPoints = decode(savedState["area"]),
            mode = parse(savedState["mode"], CartographicMeasureKind.entries.toTypedArray(), CartographicMeasureKind.DISTANCE),
            active = savedState["active"] ?: false, panel = panel,
            distanceUnit = parse(savedState["distanceUnit"], MeasureDistanceUnit.entries.toTypedArray(), MeasureDistanceUnit.METRES),
            areaUnit = parse(savedState["areaUnit"], DisplayAreaUnit.entries.toTypedArray(), DisplayAreaUnit.HECTARES),
            saveName = savedState.get<String>("name").orEmpty().take(160),
            distanceSourceId = savedState["sourceDistance"], areaSourceId = savedState["sourceArea"],
            colorArgb = savedState["color"] ?: s.colorArgb, saveLocked = savedState["locked"] ?: false)
        operationId = savedState.get<String>("operationId")?.takeIf { runCatching { UUID.fromString(it) }.isSuccess }
    }

    private fun forgetDrafts() {
        calculationJob?.cancel(); calculationRevision++
        pendingInput = null; operationId = null; restored = false
        savedState.keys().forEach { savedState.remove<Any?>(it) }
        mutable.value = ManualMeasureState()
    }

    private fun encode(points: List<CartographicCoordinate>) = points.flatMap { listOf(it.latitude, it.longitude) }.toDoubleArray()
    private fun decode(raw: DoubleArray?): List<CartographicCoordinate> {
        if (raw == null || raw.size > 2 * CartographicMeasureCalculator.MAX_POINTS || raw.size % 2 != 0) return emptyList()
        return raw.toList().chunked(2).map { CartographicCoordinate(it[0], it[1]) }
            .takeIf { it.all(CartographicMeasureCalculator::isValidCoordinate) }.orEmpty()
    }

    companion object { private const val OWNER_KEY = "cartographicDraftOwner" }
}
