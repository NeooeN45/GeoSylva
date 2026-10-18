package com.forestry.counter.presentation.screens.wood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forestry.counter.domain.model.WoodDepotDraft
import com.forestry.counter.domain.model.WoodDepotKind
import com.forestry.counter.domain.model.WoodDepotLocation
import com.forestry.counter.domain.model.WoodDepotLocationSource
import com.forestry.counter.domain.model.WoodDepotRecord
import com.forestry.counter.domain.repository.WoodDepotAccess
import com.forestry.counter.domain.repository.WoodDepotRepository
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class WoodDepotsError { ACCESS_CHANGED, LOAD_FAILED, SAVE_FAILED, CONFLICT, UNAVAILABLE, ARCHIVE_FAILED }
enum class WoodDepotsNotice { SAVED, ARCHIVED }

/** The initiating access is deliberately memory-only; never restore a draft under another account. */
data class WoodDepotForm(
    val id: String,
    val expectedRevision: Int? = null,
    val name: String = "",
    val kind: WoodDepotKind? = null,
    val notes: String = "",
    val hasPosition: Boolean = false,
    val latitude: String = "",
    val longitude: String = "",
    val originalLocation: WoodDepotLocation? = null,
    val positionEdited: Boolean = false,
    val showValidation: Boolean = false,
) {
    val validName: Boolean get() = name.trim().isNotEmpty() && name.length <= WoodDepotDraft.MAX_NAME_LENGTH
    val validNotes: Boolean get() = notes.length <= WoodDepotDraft.MAX_NOTES_LENGTH
    val validLatitude: Boolean get() = parseCoordinate(latitude)?.let { it in -90.0..90.0 } == true
    val validLongitude: Boolean get() = parseCoordinate(longitude)?.let { it in -180.0..180.0 } == true
    val isValid: Boolean get() = validName && kind != null && validNotes && (!hasPosition || validLatitude && validLongitude)

    fun draft(): WoodDepotDraft {
        check(isValid)
        val location = if (!hasPosition) null else if (!positionEdited && originalLocation != null) {
            originalLocation
        } else {
            WoodDepotLocation(checkNotNull(parseCoordinate(latitude)), checkNotNull(parseCoordinate(longitude)),
                source = WoodDepotLocationSource.MANUAL)
        }
        return WoodDepotDraft(name.trim(), checkNotNull(kind), notes.trim().ifEmpty { null }, location)
    }

    companion object {
        fun from(record: WoodDepotRecord) = WoodDepotForm(
            id = record.id, expectedRevision = record.revision, name = record.draft.name,
            kind = record.draft.kind, notes = record.draft.notes.orEmpty(),
            hasPosition = record.draft.location != null,
            latitude = record.draft.location?.latitude?.let { BigDecimal.valueOf(it).toPlainString() }.orEmpty(),
            longitude = record.draft.location?.longitude?.let { BigDecimal.valueOf(it).toPlainString() }.orEmpty(),
            originalLocation = record.draft.location,
        )

        /** French decimal commas accepted; separators, non-finite values and exponents are refused. */
        private fun parseCoordinate(input: String): Double? {
            val text = input.trim()
            if (!text.matches(Regex("[+-]?(?:[0-9]+(?:[.,][0-9]*)?|[.,][0-9]+)"))) return null
            return text.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
        }
    }
}

data class WoodDepotsUiState(
    val depots: List<WoodDepotRecord> = emptyList(),
    val loading: Boolean = true,
    val detail: WoodDepotRecord? = null,
    val form: WoodDepotForm? = null,
    val archiveCandidate: WoodDepotRecord? = null,
    val busy: Boolean = false,
    val error: WoodDepotsError? = null,
    val notice: WoodDepotsNotice? = null,
)

/** All mutations use captured access and optimistic revisions, never the identity at completion. */
class WoodDepotsViewModel(private val repository: WoodDepotRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(WoodDepotsUiState())
    val uiState: StateFlow<WoodDepotsUiState> = mutableState.asStateFlow()
    private var screenAccess: WoodDepotAccess? = runCatching { repository.captureAccess() }.getOrNull()
    private var formAccess: WoodDepotAccess? = null
    private var detailAccess: WoodDepotAccess? = null
    private var archiveAccess: WoodDepotAccess? = null
    private var operation: Job? = null
    private var observation: Job? = null
    private var observationFailed = false
    private var operationGeneration = 0L

    init { observe() }

    private fun observe() {
        observation?.cancel()
        observation = viewModelScope.launch {
            try {
                repository.observeDepots().collect { rows ->
                    if (screenAccess?.let(repository::isAccessCurrent) != true) resetForChangedAccess()
                    if (screenAccess?.let(repository::isAccessCurrent) == true) {
                        mutableState.value = mutableState.value.copy(depots = rows, loading = false)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                clearAfterObservationFailure()
            }
        }
    }

    fun retryList() {
        if (mutableState.value.busy) return
        val access = runCatching { repository.captureAccess() }.getOrNull()
        if (access == null || !repository.isAccessCurrent(access)) {
            clearAfterObservationFailure()
            return
        }
        screenAccess = access
        observationFailed = false
        mutableState.value = WoodDepotsUiState(loading = true)
        observe()
    }

    /** Also called when the screen resumes, so stale drafts clear before another tap. */
    fun verifyAccess() {
        if (observationFailed) return
        if (screenAccess?.let(repository::isAccessCurrent) != true) resetForChangedAccess()
    }

    fun create() {
        if (!currentScreen() || mutableState.value.busy) return
        formAccess = captureCurrentAccess() ?: return
        detailAccess = null
        archiveAccess = null
        mutableState.value = mutableState.value.copy(form = WoodDepotForm(UUID.randomUUID().toString()),
            detail = null, archiveCandidate = null, error = null, notice = null)
    }

    fun open(id: String) {
        if (!currentScreen() || mutableState.value.busy) return
        val access = captureCurrentAccess() ?: return
        val generation = ++operationGeneration
        mutableState.value = mutableState.value.copy(busy = true, error = null, notice = null)
        operation = viewModelScope.launch {
            try {
                val record = repository.getDepot(id, access)
                if (!acceptResult(generation, access)) return@launch
                if (record == null) {
                    mutableState.value = mutableState.value.copy(busy = false, error = WoodDepotsError.UNAVAILABLE)
                } else {
                    detailAccess = access
                    mutableState.value = mutableState.value.copy(busy = false, detail = record, form = null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                fail(generation, access, error, WoodDepotsError.LOAD_FAILED)
            }
        }
    }

    fun edit() {
        val state = mutableState.value
        val record = state.detail ?: return
        val access = detailAccess ?: return
        if (state.busy || !currentScreen() || !repository.isAccessCurrent(access)) return
        formAccess = access
        mutableState.value = state.copy(form = WoodDepotForm.from(record), error = null, notice = null)
    }

    fun updateName(value: String) = updateForm { copy(name = value) }
    fun updateKind(value: WoodDepotKind) = updateForm { copy(kind = value) }
    fun updateNotes(value: String) = updateForm { copy(notes = value) }
    fun setHasPosition(value: Boolean) = updateForm { copy(hasPosition = value) }
    fun updateLatitude(value: String) = updateForm { copy(latitude = value, positionEdited = true) }
    fun updateLongitude(value: String) = updateForm { copy(longitude = value, positionEdited = true) }

    private fun updateForm(change: WoodDepotForm.() -> WoodDepotForm) {
        if (!currentScreen() || mutableState.value.busy) return
        val access = formAccess ?: return
        if (!repository.isAccessCurrent(access)) { resetForChangedAccess(); return }
        val form = mutableState.value.form ?: return
        mutableState.value = mutableState.value.copy(form = form.change(), error = null, notice = null)
    }

    fun save() {
        if (!currentScreen() || mutableState.value.busy) return
        val form = mutableState.value.form ?: return
        val access = formAccess ?: return
        if (!repository.isAccessCurrent(access)) { resetForChangedAccess(); return }
        if (!form.isValid) {
            mutableState.value = mutableState.value.copy(form = form.copy(showValidation = true))
            return
        }
        val generation = ++operationGeneration
        val draft = form.draft()
        // Busy is synchronous; repeated taps cannot enqueue a duplicate mutation.
        mutableState.value = mutableState.value.copy(busy = true, error = null, notice = null)
        operation = viewModelScope.launch {
            try {
                val saved = if (form.expectedRevision == null) repository.createDepot(form.id, draft, access)
                else repository.updateDepot(form.id, form.expectedRevision, draft, access)
                if (!acceptResult(generation, access)) return@launch
                formAccess = null
                detailAccess = access
                mutableState.value = mutableState.value.copy(form = null, detail = saved, busy = false,
                    notice = WoodDepotsNotice.SAVED)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                fail(generation, access, error, WoodDepotsError.SAVE_FAILED)
            }
        }
    }

    /** Explicit user action only: a revision conflict never silently replaces the user's input. */
    fun reloadAfterConflict() {
        val state = mutableState.value
        if (state.busy || state.error != WoodDepotsError.CONFLICT || !currentScreen()) return
        val id = state.form?.id ?: state.detail?.id ?: return
        val access = formAccess ?: detailAccess ?: return
        val generation = ++operationGeneration
        mutableState.value = state.copy(busy = true)
        operation = viewModelScope.launch {
            try {
                val record = repository.getDepot(id, access)
                if (!acceptResult(generation, access)) return@launch
                if (record == null) {
                    formAccess = null
                    detailAccess = null
                    mutableState.value = mutableState.value.copy(form = null, detail = null, busy = false,
                        error = WoodDepotsError.UNAVAILABLE)
                } else {
                    formAccess = access
                    detailAccess = access
                    mutableState.value = mutableState.value.copy(form = WoodDepotForm.from(record), detail = record,
                        busy = false, error = null, notice = null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                fail(generation, access, error, WoodDepotsError.LOAD_FAILED)
            }
        }
    }

    fun requestArchive() {
        val state = mutableState.value
        val record = state.detail ?: return
        val access = detailAccess ?: return
        if (state.busy || state.form != null || !currentScreen()) return
        archiveAccess = access
        mutableState.value = state.copy(archiveCandidate = record, error = null, notice = null)
    }

    fun dismissArchive() {
        if (mutableState.value.busy) return
        archiveAccess = null
        mutableState.value = mutableState.value.copy(archiveCandidate = null)
    }

    fun confirmArchive() {
        val state = mutableState.value
        val record = state.archiveCandidate ?: return
        val access = archiveAccess ?: return
        if (state.busy || !currentScreen()) return
        if (!repository.isAccessCurrent(access)) { resetForChangedAccess(); return }
        val generation = ++operationGeneration
        mutableState.value = state.copy(busy = true, error = null)
        operation = viewModelScope.launch {
            try {
                repository.deleteDepot(record.id, record.revision, access)
                if (!acceptResult(generation, access)) return@launch
                detailAccess = null
                archiveAccess = null
                mutableState.value = mutableState.value.copy(detail = null, archiveCandidate = null, busy = false,
                    notice = WoodDepotsNotice.ARCHIVED)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                archiveAccess = null
                if (generation == operationGeneration) mutableState.value = mutableState.value.copy(archiveCandidate = null)
                fail(generation, access, error, WoodDepotsError.ARCHIVE_FAILED)
            }
        }
    }

    fun closeForm() {
        if (mutableState.value.busy || !currentScreen()) return
        formAccess = null
        mutableState.value = mutableState.value.copy(form = null, error = null, notice = null)
    }

    fun closeDetail() {
        if (mutableState.value.busy || !currentScreen()) return
        detailAccess = null
        mutableState.value = mutableState.value.copy(detail = null, archiveCandidate = null, error = null, notice = null)
    }

    private fun currentScreen(): Boolean {
        if (observationFailed) return false
        if (screenAccess?.let(repository::isAccessCurrent) == true) return true
        resetForChangedAccess()
        return false
    }

    private fun captureCurrentAccess(): WoodDepotAccess? {
        val captured = runCatching { repository.captureAccess() }.getOrNull()
        if (captured == null || screenAccess?.let(repository::isAccessCurrent) != true ||
            !repository.isAccessCurrent(captured)) {
            resetForChangedAccess()
            return null
        }
        return captured
    }

    private fun resetForChangedAccess() {
        operationGeneration++
        operation?.cancel()
        operation = null
        formAccess = null
        detailAccess = null
        archiveAccess = null
        screenAccess = runCatching { repository.captureAccess() }.getOrNull()
        mutableState.value = WoodDepotsUiState(loading = false, error = WoodDepotsError.ACCESS_CHANGED)
    }

    /** A stopped identity-aware collector cannot safely retain any previously displayed dossier. */
    private fun clearAfterObservationFailure() {
        operationGeneration++
        operation?.cancel()
        operation = null
        formAccess = null
        detailAccess = null
        archiveAccess = null
        screenAccess = null
        observationFailed = true
        mutableState.value = WoodDepotsUiState(loading = false, error = WoodDepotsError.LOAD_FAILED)
    }

    private fun acceptResult(generation: Long, access: WoodDepotAccess): Boolean {
        if (generation != operationGeneration) return false
        if (!repository.isAccessCurrent(access)) { resetForChangedAccess(); return false }
        return true
    }

    private fun fail(generation: Long, access: WoodDepotAccess, error: Exception, fallback: WoodDepotsError) {
        if (!acceptResult(generation, access)) return
        val messages = generateSequence<Throwable>(error) { it.cause }.mapNotNull { it.message }.toList()
        val mapped = when {
            messages.any { it.contains("WOOD_DEPOT_ACCESS_CHANGED") } -> WoodDepotsError.ACCESS_CHANGED
            messages.any { it.contains("WOOD_DEPOT_REVISION_CONFLICT") || it.contains("WOOD_DEPOT_CREATE_CONFLICT") } -> WoodDepotsError.CONFLICT
            messages.any { it.contains("WOOD_DEPOT_UNAVAILABLE") } -> WoodDepotsError.UNAVAILABLE
            else -> fallback
        }
        if (mapped == WoodDepotsError.ACCESS_CHANGED) resetForChangedAccess()
        else mutableState.value = mutableState.value.copy(busy = false, error = mapped)
    }
}
