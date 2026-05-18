package com.forestry.counter.presentation.screens.forestry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forestry.counter.data.preferences.UserPreferencesManager
import com.forestry.counter.domain.model.station.StationObservation
import com.forestry.counter.domain.repository.StationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StationDiagnosticViewModel(
    private val parcelleId: String,
    private val stationRepository: StationRepository,
    private val preferencesManager: UserPreferencesManager
) : ViewModel() {

    val diagnostics: StateFlow<List<StationObservation>> =
        stationRepository.getByParcelle(parcelleId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tutorialCompleted: StateFlow<Boolean> =
        preferencesManager.stationTutorialCompleted
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun saveObservation(obs: StationObservation) {
        viewModelScope.launch {
            stationRepository.save(obs)
        }
    }

    fun markTutorialCompleted() {
        viewModelScope.launch {
            preferencesManager.setStationTutorialCompleted(true)
        }
    }
}