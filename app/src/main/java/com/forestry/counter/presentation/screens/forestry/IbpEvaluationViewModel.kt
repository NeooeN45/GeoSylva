package com.forestry.counter.presentation.screens.forestry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forestry.counter.domain.model.IbpEvaluation
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.repository.IbpRepository
import com.forestry.counter.domain.repository.PlacetteRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class IbpEvaluationViewModel(
    val parcelleId: String,
    val placetteId: String,
    val evaluationId: String?,
    private val ibpRepository: IbpRepository,
    private val placetteRepository: PlacetteRepository?
) : ViewModel() {

    val existingEvaluation: StateFlow<IbpEvaluation?> =
        (if (evaluationId != null) ibpRepository.getById(evaluationId) else flowOf(null))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val placette: StateFlow<Placette?> =
        (placetteRepository?.getPlacetteById(placetteId) ?: flowOf(null))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val placetteLabel: StateFlow<String> = placette
        .map { it?.name?.takeIf { name -> name.isNotBlank() } ?: placetteId.take(LABEL_FALLBACK_LENGTH) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), placetteId.take(LABEL_FALLBACK_LENGTH))

    fun saveEvaluation(evaluation: IbpEvaluation) {
        viewModelScope.launch {
            ibpRepository.save(evaluation)
        }
    }

    fun deleteEvaluation(id: String) {
        viewModelScope.launch {
            ibpRepository.delete(id)
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val LABEL_FALLBACK_LENGTH = 8
    }
}
