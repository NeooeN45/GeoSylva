package com.forestry.counter.presentation.screens.forestry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.domain.model.Foret
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.repository.ForetRepository
import com.forestry.counter.domain.repository.ParcelleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * ViewModel pour ForestDetailScreen — spec GEOSYLVA-003 §29.5.
 *
 * Charge une forêt et ses parcelles associées.
 */
class ForestDetailViewModel(
    private val foretRepository: ForetRepository,
    private val parcelleRepository: ParcelleRepository,
    private val foretId: String,
    private val businessReadAccess: BusinessReadAccess? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ForestDetailUiState>(ForestDetailUiState.Loading)
    val uiState: StateFlow<ForestDetailUiState> = _uiState.asStateFlow()
    private val currentAccess: BusinessReadAccess.AccessSnapshot?
        get() = (_uiState.value as? ForestDetailUiState.Success)?.accessSnapshot

    init {
        loadForest()
    }

    private fun loadForest() {
        viewModelScope.launch {
            val source: Flow<ForestDetailUiState> = combine(foretRepository.getAll(), parcelleRepository.getParcellesByForest(foretId)) { forests, parcels ->
                forests.firstOrNull { it.foretId == foretId && it.deletedAt == null }?.let {
                    ForestDetailUiState.Success(it, parcels.filter { parcel -> parcel.deletedAt == null })
                } ?: ForestDetailUiState.NotFound
            }
            val guarded = businessReadAccess?.observe(source, ForestDetailUiState.NotFound) { state, access ->
                val policy = checkNotNull(businessReadAccess)
                if (!policy.canReadForest(foretId, access)) ForestDetailUiState.NotFound
                else if (state is ForestDetailUiState.Success) state.copy(
                    parcelles = state.parcelles.filter { policy.canReadParcelle(it.id, access) },
                    accessSnapshot = access,
                )
                else state
            } ?: source
            guarded.collect { state ->
                // Authority travels with its result. An earlier queued denial cannot erase the
                // permission of a later result while both are crossing the Flow buffer.
                _uiState.value = if (state is ForestDetailUiState.Success && !isReadable(state.accessSnapshot))
                    ForestDetailUiState.NotFound else state
            }
        }
    }

    /** Also closes the small window before the access observer gets a coroutine turn. */
    fun isCurrentState(state: ForestDetailUiState = _uiState.value): Boolean =
        if (state is ForestDetailUiState.Success) isReadable(state.accessSnapshot) else businessReadAccess == null

    private fun isReadable(snapshot: BusinessReadAccess.AccessSnapshot?): Boolean = businessReadAccess?.let { access ->
        snapshot?.let { access.isCurrent(it) && access.canReadForest(foretId, it) } == true
    } ?: true

    private suspend fun validateCapturedAccess(snapshot: BusinessReadAccess.AccessSnapshot?): Boolean =
        businessReadAccess?.let { access -> snapshot != null && access.canReadForest(foretId, snapshot) && access.revalidate(snapshot) } ?: true

    fun updateForet(foret: Foret) {
        val access = currentAccess
        viewModelScope.launch {
            if (foret.foretId != foretId || !validateCapturedAccess(access)) return@launch
            foretRepository.update(foret)
        }
    }

    fun deleteForet() {
        val access = currentAccess
        viewModelScope.launch {
            if (!validateCapturedAccess(access)) return@launch
            foretRepository.deleteById(foretId)
        }
    }
}

sealed interface ForestDetailUiState {
    data object Loading : ForestDetailUiState
    data object NotFound : ForestDetailUiState
    data class Success(
        val foret: Foret,
        val parcelles: List<Parcelle>,
        val accessSnapshot: BusinessReadAccess.AccessSnapshot? = null,
    ) : ForestDetailUiState
}
