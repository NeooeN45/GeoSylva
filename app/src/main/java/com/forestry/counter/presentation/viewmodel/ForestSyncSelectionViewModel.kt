package com.forestry.counter.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forestry.counter.domain.model.HierarchyRecoveryPreview
import com.forestry.counter.domain.model.HierarchySyncCandidate
import com.forestry.counter.domain.model.HierarchySyncSelection
import com.forestry.counter.domain.model.HierarchySyncSummary
import com.forestry.counter.domain.repository.HierarchySyncRepository
import com.forestry.counter.domain.repository.IdentityRepository
import com.forestry.counter.domain.repository.IdentitySessionGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive


enum class ForestSyncStep { SELECT, REVIEW, COMPLETE }
enum class ForestSyncError { SESSION_UNAVAILABLE, SESSION_CHANGED, LOAD_FAILED, ACTIVATION_FAILED, TRANSFER_FAILED, PREVIEW_FAILED }

data class ForestSyncTotals(val forests: Long, val parcels: Long, val plots: Long, val sessions: Long, val trees: Long)
fun forestSyncTotals(candidates: List<HierarchySyncCandidate>) = ForestSyncTotals(candidates.size.toLong(),
    candidates.sumOf { it.parcelCount.toLong() }, candidates.sumOf { it.plotCount.toLong() },
    candidates.sumOf { it.sessionCount.toLong() }, candidates.sumOf { it.treeCount.toLong() })

fun isMissingForestBackupRecovery(candidate: HierarchySyncCandidate): Boolean =
    candidate.isActivated && !candidate.blockedByOtherAccount &&
        candidate.state == "ERROR" && candidate.lastErrorCode == "HTTP_404"

data class ForestRemoteReadOnlySummary(val name: String?, val parcels: Int?, val plots: Int?, val sessions: Int?, val trees: Int?)
/** Display only; no deserialization into Room and no replacement of local data. */
fun forestRemoteReadOnlySummary(json: String): ForestRemoteReadOnlySummary? = try {
    val value = Json.parseToJsonElement(json) as? JsonObject
    fun count(key: String) = (value?.get(key) as? JsonArray)?.size
    val name = (value?.get("forest") as? JsonObject)?.get("nom")?.jsonPrimitive?.contentOrNull
    if (value == null) null else ForestRemoteReadOnlySummary(name, count("parcelles"), count("placettes"), count("sessions"), count("tiges"))
} catch (_: Exception) { null }

data class ForestSyncSelectionUiState(
    val loading: Boolean = false,
    val busy: Boolean = false,
    val selection: HierarchySyncSelection? = null,
    val selectedForestIds: Set<String> = emptySet(),
    val step: ForestSyncStep = ForestSyncStep.SELECT,
    val summary: HierarchySyncSummary = HierarchySyncSummary(),
    val preview: HierarchyRecoveryPreview? = null,
    val previewSummary: ForestRemoteReadOnlySummary? = null,
    val previewAllowsMissingBackupRetry: Boolean = false,
    val error: ForestSyncError? = null,
    val activatedCount: Int? = null,
    val transferFinished: Boolean = false,
    val closed: Boolean = false,
)

/** Loading or cancelling only reads data; confirmation alone authorizes a whole-forest association. */
class ForestSyncSelectionViewModel(
    private val identity: IdentityRepository,
    private val repository: HierarchySyncRepository,
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val mutable = MutableStateFlow(ForestSyncSelectionUiState())
    val uiState: StateFlow<ForestSyncSelectionUiState> = mutable.asStateFlow()
    private var operation: Job? = null
    private var generation = 0L
    private var loadingGuard: IdentitySessionGuard? = null
    private var latestSummary = HierarchySyncSummary()

    init {
        viewModelScope.launch {
            identity.session.collect {
                val state = mutable.value
                if (state.closed) return@collect
                if ((state.selection != null && !current(state.selection.sessionGuard)) ||
                    (loadingGuard != null && !current(loadingGuard!!))) invalidate()
            }
        }
        viewModelScope.launch {
            repository.observeSummary().collect { summary ->
                val selection = mutable.value.selection
                val guard = selection?.sessionGuard ?: loadingGuard
                latestSummary = if (guard != null && current(guard)) summary else HierarchySyncSummary()
                if (selection != null) mutable.value = mutable.value.copy(summary = latestSummary)
            }
        }
        reload()
    }

    fun reload() {
        if (mutable.value.busy || mutable.value.closed) return
        operation?.cancel()
        val request = ++generation
        val session = identity.session.value?.takeUnless { it.isExpired() }
        val guard = session?.let { identity.captureSessionGuard(it.accountId).getOrNull() }
        if (session == null || guard == null || !current(guard)) {
            mutable.value = ForestSyncSelectionUiState(error = ForestSyncError.SESSION_UNAVAILABLE)
            return
        }
        loadingGuard = guard
        mutable.value = ForestSyncSelectionUiState(loading = true)
        operation = viewModelScope.launch {
            val result = guardedResult { repository.loadSelection() }
            if (generation != request) return@launch
            loadingGuard = null
            if (!current(guard)) { invalidate(); return@launch }
            val selection = result.getOrNull()
            if (selection == null) {
                mutable.value = ForestSyncSelectionUiState(error = ForestSyncError.LOAD_FAILED)
                return@launch
            }
            if (selection.accountId != session.accountId || !current(selection.sessionGuard)) { invalidate(); return@launch }
            val summary = try { repository.observeSummary().first() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { latestSummary }
            if (request != generation) return@launch
            if (!current(selection.sessionGuard)) { invalidate(); return@launch }
            latestSummary = summary
            mutable.value = ForestSyncSelectionUiState(selection = selection, summary = summary)
        }
    }

    fun toggleForest(id: String) {
        val state = mutable.value
        if (state.loading || state.busy || state.closed || state.step != ForestSyncStep.SELECT) return
        val selection = validSelection() ?: return
        val candidate = selection.forests.firstOrNull { it.forestId == id } ?: return
        if (candidate.isActivated || candidate.blockedByOtherAccount) return
        mutable.value = state.copy(selectedForestIds = if (id in state.selectedForestIds) state.selectedForestIds - id else state.selectedForestIds + id, error = null)
    }
    fun reviewSelection() {
        val state = mutable.value
        if (state.busy || state.closed || state.selectedForestIds.isEmpty() || state.step != ForestSyncStep.SELECT || validSelection() == null) return
        mutable.value = state.copy(step = ForestSyncStep.REVIEW)
    }
    fun editSelection() {
        if (mutable.value.busy || mutable.value.closed || validSelection() == null) return
        mutable.value = mutable.value.copy(step = ForestSyncStep.SELECT)
    }
    fun confirmSelection() {
        val state = mutable.value
        if (state.busy || state.closed || state.step != ForestSyncStep.REVIEW || state.selectedForestIds.isEmpty()) return
        val selection = validSelection() ?: return
        val ids = state.selectedForestIds.toSet()
        val request = ++generation
        mutable.value = state.copy(busy = true, error = null)
        operation = viewModelScope.launch {
            val result = guardedResult { repository.activateSelected(selection, ids) }
            if (request != generation) return@launch
            if (!current(selection.sessionGuard)) { invalidate(); return@launch }
            result.fold(onSuccess = { count ->
                mutable.value = state.copy(step = ForestSyncStep.COMPLETE, selectedForestIds = emptySet(), activatedCount = count, summary = latestSummary)
            }, onFailure = { mutable.value = ForestSyncSelectionUiState(error = ForestSyncError.ACTIVATION_FAILED) })
        }
    }

    /** Explicit retry concerns only dossiers already authorized for this personal account. */
    fun synchronizeActivated() {
        val state = mutable.value
        if (state.busy || state.loading || state.closed) return
        val selection = validSelection() ?: return
        val request = ++generation
        mutable.value = state.copy(busy = true, preview = null, previewSummary = null,
            previewAllowsMissingBackupRetry = false, error = null)
        operation = viewModelScope.launch {
            try {
                if (!current(selection.sessionGuard)) { invalidate(); return@launch }
                repository.captureActivated(selection.sessionGuard)
                if (request != generation) return@launch
                if (!current(selection.sessionGuard)) { invalidate(); return@launch }
                repository.processPending(selection.sessionGuard)
                if (request != generation) return@launch
                if (!current(selection.sessionGuard)) { invalidate(); return@launch }
                val loaded = guardedResult { repository.loadSelection() }
                if (request != generation) return@launch
                if (!current(selection.sessionGuard)) { invalidate(); return@launch }
                mutable.value = state.copy(selection = loaded.getOrNull() ?: selection, busy = false,
                    summary = latestSummary, transferFinished = true,
                    error = if (loaded.isFailure) ForestSyncError.LOAD_FAILED else null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (request == generation && current(selection.sessionGuard)) mutable.value = state.copy(busy = false, summary = latestSummary, error = ForestSyncError.TRANSFER_FAILED)
            }
        }
    }
    fun previewConflict(forestId: String) {
        val state = mutable.value
        if (state.busy || state.closed) return
        val selection = validSelection() ?: return
        val candidate = selection.forests.firstOrNull { it.forestId == forestId && it.isActivated && !it.blockedByOtherAccount } ?: return
        if (candidate.state != "CONFLICT" && !isMissingForestBackupRecovery(candidate)) return
        val request = ++generation
        mutable.value = state.copy(busy = true, preview = null, previewSummary = null,
            previewAllowsMissingBackupRetry = false, error = null)
        operation = viewModelScope.launch {
            val result = guardedResult { repository.prepareRecovery(forestId) }
            if (request != generation) return@launch
            if (!current(selection.sessionGuard)) { invalidate(); return@launch }
            val preview = result.getOrNull()?.takeIf { it.forestId == forestId }
            val previewSummary = preview?.remote?.let { remote ->
                withContext(computationDispatcher) { forestRemoteReadOnlySummary(remote.dossierJson) }
            }
            if (request != generation) return@launch
            if (!current(selection.sessionGuard)) { invalidate(); return@launch }
            mutable.value = state.copy(busy = false, preview = preview, previewSummary = previewSummary,
                previewAllowsMissingBackupRetry = preview != null && preview.remote == null && isMissingForestBackupRecovery(candidate),
                error = if (preview == null) ForestSyncError.PREVIEW_FAILED else null)
        }
    }
    /** Only the explicit missing-copy preview can authorize a fresh local publication. */
    fun confirmMissingBackupRetry() {
        val state = mutable.value
        if (state.busy || state.loading || state.closed || !state.previewAllowsMissingBackupRetry) return
        val selection = validSelection() ?: return
        val preview = state.preview ?: return
        if (preview.remote != null) return
        val candidate = selection.forests.firstOrNull { it.forestId == preview.forestId } ?: return
        if (!isMissingForestBackupRecovery(candidate)) return
        val request = ++generation
        mutable.value = state.copy(busy = true, error = null, transferFinished = false)
        operation = viewModelScope.launch {
            if (request != generation) return@launch
            if (!current(selection.sessionGuard)) { invalidate(); return@launch }
            val result = guardedResult { repository.confirmRepublishLocal(preview) }
            if (request != generation) return@launch
            if (!current(selection.sessionGuard)) { invalidate(); return@launch }
            if (result.isFailure) {
                mutable.value = state.copy(busy = false, preview = null, previewSummary = null,
                    previewAllowsMissingBackupRetry = false, transferFinished = false, error = ForestSyncError.TRANSFER_FAILED)
                return@launch
            }
            try {
                val transfer = repository.processPending(selection.sessionGuard)
                if (request != generation) return@launch
                if (!current(selection.sessionGuard)) { invalidate(); return@launch }
                val loaded = guardedResult { repository.loadSelection() }
                if (request != generation) return@launch
                if (!current(selection.sessionGuard)) { invalidate(); return@launch }
                val refreshed = loaded.getOrNull()
                if (refreshed != null && (refreshed.accountId != selection.accountId || !current(refreshed.sessionGuard))) {
                    invalidate(); return@launch
                }
                mutable.value = state.copy(selection = refreshed ?: selection, busy = false,
                    preview = null, previewSummary = null, previewAllowsMissingBackupRetry = false,
                    summary = latestSummary,
                    transferFinished = refreshed?.forests?.any { it.forestId == preview.forestId && it.state == "SYNCED" } == true,
                    error = if (loaded.isFailure) ForestSyncError.LOAD_FAILED
                        else if (transfer.errors > 0) ForestSyncError.TRANSFER_FAILED else null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (request == generation && current(selection.sessionGuard)) mutable.value = state.copy(busy = false,
                    preview = null, previewSummary = null, previewAllowsMissingBackupRetry = false,
                    transferFinished = false, error = ForestSyncError.TRANSFER_FAILED)
            }
        }
    }
    fun dismissPreview() {
        if (mutable.value.busy) return
        ++generation
        mutable.value = mutable.value.copy(preview = null, previewSummary = null, previewAllowsMissingBackupRetry = false)
    }
    fun validateSession(): Boolean = validSelection() != null
    fun cancel() {
        if (mutable.value.busy) return
        ++generation; operation?.cancel(); loadingGuard = null
        mutable.value = ForestSyncSelectionUiState(closed = true)
    }
    private fun validSelection(): HierarchySyncSelection? {
        val selection = mutable.value.selection ?: return null
        if (current(selection.sessionGuard)) return selection
        invalidate(); return null
    }
    private fun current(guard: IdentitySessionGuard): Boolean {
        val session = identity.session.value ?: return false
        return !session.isExpired() && session.accountId == guard.accountId && identity.isSessionGuardCurrent(guard)
    }
    private fun invalidate() {
        ++generation; operation?.cancel(); loadingGuard = null; latestSummary = HierarchySyncSummary()
        val session = identity.session.value
        mutable.value = ForestSyncSelectionUiState(error = if (session == null || session.isExpired()) ForestSyncError.SESSION_UNAVAILABLE else ForestSyncError.SESSION_CHANGED)
    }
    private suspend fun <T> guardedResult(call: suspend () -> Result<T>): Result<T> {
        val result = try { call() } catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) { Result.failure(error) }
        result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        return result
    }
}
