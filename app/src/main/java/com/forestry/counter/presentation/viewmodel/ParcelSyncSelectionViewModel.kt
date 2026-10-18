package com.forestry.counter.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forestry.counter.domain.model.ParcelSyncSelection
import com.forestry.counter.domain.repository.IdentityRepository
import com.forestry.counter.domain.repository.ParcelSyncRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ParcelSyncSelectionStep { SELECT, REVIEW, SUCCESS }

enum class ParcelSyncSelectionError { SESSION_UNAVAILABLE, SESSION_CHANGED, LOAD_FAILED, ENQUEUE_FAILED }

data class ParcelSyncSelectionUiState(
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val selection: ParcelSyncSelection? = null,
    val selectedParcelIds: Set<String> = emptySet(),
    val step: ParcelSyncSelectionStep = ParcelSyncSelectionStep.SELECT,
    val error: ParcelSyncSelectionError? = null,
    val addedCount: Int? = null,
    val isClosed: Boolean = false,
)

/** Choix explicite de nouvelles associations ; ouvrir ou annuler ne met rien en file. */
class ParcelSyncSelectionViewModel(
    private val identityRepository: IdentityRepository,
    private val parcelSyncRepository: ParcelSyncRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ParcelSyncSelectionUiState())
    val uiState: StateFlow<ParcelSyncSelectionUiState> = mutableState.asStateFlow()
    private var operation: Job? = null
    private var generation = 0L
    private var loadingAccountId: String? = null

    init {
        viewModelScope.launch {
            identityRepository.session.collect { session ->
                val state = mutableState.value
                if (state.isClosed) return@collect
                val selection = state.selection
                if (selection != null && !isCurrent(selection)) {
                    invalidateSession()
                } else if (state.isLoading && loadingAccountId != null &&
                    (session == null || session.accountId != loadingAccountId || session.isExpired())
                ) {
                    invalidateSession()
                }
            }
        }
        reload()
    }

    fun reload() {
        if (mutableState.value.isSubmitting || mutableState.value.isClosed) return
        operation?.cancel()
        val requestGeneration = ++generation
        val account = identityRepository.session.value?.takeUnless { it.isExpired() }
        loadingAccountId = account?.accountId
        if (account == null) {
            mutableState.value = ParcelSyncSelectionUiState(error = ParcelSyncSelectionError.SESSION_UNAVAILABLE)
            return
        }
        mutableState.value = ParcelSyncSelectionUiState(isLoading = true)
        operation = viewModelScope.launch {
            val result = try {
                parcelSyncRepository.loadSelection()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Result.failure<ParcelSyncSelection>(IllegalStateException())
            }
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            if (requestGeneration != generation) return@launch
            loadingAccountId = null
            result.fold(
                onSuccess = { selection ->
                    if (selection.accountId != account.accountId || !isCurrent(selection)) {
                        invalidateSession()
                    } else {
                        mutableState.value = ParcelSyncSelectionUiState(selection = selection)
                    }
                },
                onFailure = {
                    val current = identityRepository.session.value
                    val error = when {
                        current == null || current.isExpired() -> ParcelSyncSelectionError.SESSION_UNAVAILABLE
                        current.accountId != account.accountId -> ParcelSyncSelectionError.SESSION_CHANGED
                        else -> ParcelSyncSelectionError.LOAD_FAILED
                    }
                    mutableState.value = ParcelSyncSelectionUiState(error = error)
                },
            )
        }
    }

    fun toggleParcel(parcelId: String) {
        val state = mutableState.value
        if (state.isLoading || state.isSubmitting || state.isClosed ||
            state.step != ParcelSyncSelectionStep.SELECT
        ) return
        val selection = state.selection ?: return
        if (!isCurrent(selection)) {
            invalidateSession()
            return
        }
        val candidate = selection.parcels.find { it.parcelId == parcelId } ?: return
        if (candidate.blockedByOtherAccount || (candidate.isLinked && !candidate.recoveryRequired) || candidate.recoveryUnavailable) return
        val selected = if (parcelId in state.selectedParcelIds) {
            state.selectedParcelIds - parcelId
        } else {
            state.selectedParcelIds + parcelId
        }
        mutableState.value = state.copy(selectedParcelIds = selected, error = null)
    }

    fun reviewSelection() {
        val state = mutableState.value
        val selection = state.selection ?: return
        if (state.isSubmitting || state.isClosed || state.selectedParcelIds.isEmpty() ||
            state.step != ParcelSyncSelectionStep.SELECT
        ) return
        if (!isCurrent(selection)) {
            invalidateSession()
            return
        }
        mutableState.value = state.copy(step = ParcelSyncSelectionStep.REVIEW)
    }

    fun editSelection() {
        val state = mutableState.value
        if (state.isSubmitting || state.isClosed || state.step != ParcelSyncSelectionStep.REVIEW) return
        if (!validateSession()) return
        mutableState.value = state.copy(step = ParcelSyncSelectionStep.SELECT)
    }

    fun confirmSelection() {
        val state = mutableState.value
        val selection = state.selection ?: return
        if (state.isSubmitting || state.isClosed || state.step != ParcelSyncSelectionStep.REVIEW ||
            state.selectedParcelIds.isEmpty()
        ) return
        if (!isCurrent(selection)) {
            invalidateSession()
            return
        }
        // La référence capturée et les identifiants autorisés ne sont jamais relus depuis un autre compte.
        val selectedIds = state.selectedParcelIds.toSet()
        val requestGeneration = ++generation
        mutableState.value = state.copy(isSubmitting = true, error = null)
        operation = viewModelScope.launch {
            val result = try {
                parcelSyncRepository.enqueueSelected(selection, selectedIds)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Result.failure<Int>(IllegalStateException())
            }
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            if (requestGeneration != generation) return@launch
            if (!isCurrent(selection)) {
                invalidateSession()
                return@launch
            }
            result.fold(
                onSuccess = { count ->
                    mutableState.value = state.copy(
                        step = ParcelSyncSelectionStep.SUCCESS,
                        selectedParcelIds = emptySet(),
                        addedCount = count,
                    )
                },
                onFailure = {
                    // Une erreur ne démontre pas l'absence d'associations partielles : recharger avant de réessayer.
                    mutableState.value = ParcelSyncSelectionUiState(error = ParcelSyncSelectionError.ENQUEUE_FAILED)
                },
            )
        }
    }

    /** Revérifie aussi la génération d'authentification lors d'un retour au premier plan. */
    fun validateSession(): Boolean {
        val state = mutableState.value
        if (state.isClosed) return false
        val selection = state.selection ?: return false
        if (isCurrent(selection)) return true
        invalidateSession()
        return false
    }

    fun cancel() {
        if (mutableState.value.isSubmitting) return
        ++generation
        operation?.cancel()
        loadingAccountId = null
        mutableState.value = ParcelSyncSelectionUiState(isClosed = true)
    }

    private fun isCurrent(selection: ParcelSyncSelection): Boolean {
        val session = identityRepository.session.value ?: return false
        return !session.isExpired() && session.accountId == selection.accountId &&
            identityRepository.isSessionGuardCurrent(selection.sessionGuard)
    }

    private fun invalidateSession() {
        ++generation
        operation?.cancel()
        loadingAccountId = null
        val session = identityRepository.session.value
        mutableState.value = ParcelSyncSelectionUiState(
            error = if (session == null || session.isExpired()) {
                ParcelSyncSelectionError.SESSION_UNAVAILABLE
            } else {
                ParcelSyncSelectionError.SESSION_CHANGED
            },
        )
    }
}
