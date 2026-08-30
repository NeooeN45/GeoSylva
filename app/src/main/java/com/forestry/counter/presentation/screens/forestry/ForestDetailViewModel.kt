package com.forestry.counter.presentation.screens.forestry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forestry.counter.domain.model.Foret
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.repository.ForetRepository
import com.forestry.counter.domain.repository.GroupRepository
import com.forestry.counter.domain.repository.ParcelleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
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
    private val groupRepository: GroupRepository? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ForestDetailUiState>(ForestDetailUiState.Loading)
    val uiState: StateFlow<ForestDetailUiState> = _uiState.asStateFlow()

    init {
        loadForest()
    }

    private fun loadForest() {
        viewModelScope.launch {
            val foret = foretRepository.getById(foretId)
            if (foret == null) {
                _uiState.value = ForestDetailUiState.NotFound
                return@launch
            }
            parcelleRepository.getParcellesByForest(foretId).collect { parcelles ->
                _uiState.value = ForestDetailUiState.Success(foret, parcelles)
            }
        }
    }

    fun updateForet(foret: Foret) {
        viewModelScope.launch {
            foretRepository.update(foret)
        }
    }

    fun deleteForet() {
        viewModelScope.launch {
            foretRepository.deleteById(foretId)
            groupRepository?.deleteByForetId(foretId)
        }
    }
}

sealed interface ForestDetailUiState {
    data object Loading : ForestDetailUiState
    data object NotFound : ForestDetailUiState
    data class Success(
        val foret: Foret,
        val parcelles: List<Parcelle>,
    ) : ForestDetailUiState
}
