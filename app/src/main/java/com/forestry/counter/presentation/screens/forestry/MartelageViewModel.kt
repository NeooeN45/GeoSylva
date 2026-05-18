package com.forestry.counter.presentation.screens.forestry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forestry.counter.data.preferences.UserPreferencesManager
import com.forestry.counter.domain.calculation.ForestryCalculator
import com.forestry.counter.domain.calculation.MartelageStats
import com.forestry.counter.domain.calculation.MartelageViewScope
import com.forestry.counter.domain.calculation.tarifs.TarifSelection
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.repository.EssenceRepository
import com.forestry.counter.domain.repository.IbpRepository
import com.forestry.counter.domain.repository.ParcelleRepository
import com.forestry.counter.domain.repository.TigeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel de l'écran Martelage.
 *
 * Responsabilité unique : exposer les flux de données (tiges, parcelles)
 * sous forme de StateFlow afin de découpler le Composable des appels
 * directs aux repositories.
 *
 * La logique de calcul métier (computeMartelageStats) reste dans le
 * Composable via produceState, car elle dépend d'états UI locaux
 * (surface, hauteurs par classe, tarif, etc.) qui n'appartiennent pas
 * à la couche de données.
 */
class MartelageViewModel(
    val scope: String,
    val forestId: String?,
    val parcelleId: String?,
    val placetteId: String?,
    private val essenceRepository: EssenceRepository,
    private val tigeRepository: TigeRepository,
    private val parcelleRepository: ParcelleRepository,
    val forestryCalculator: ForestryCalculator,
    val userPreferences: UserPreferencesManager,
    val ibpRepository: IbpRepository?
) : ViewModel() {

    /**
     * Toutes les tiges — le filtrage par scope (parcelle / placette / forêt)
     * est délégué au Composable via [tigesInScope] calculé depuis ce flux,
     * car il dépend du viewScope UI local (variable par l'utilisateur).
     */
    val allTiges: StateFlow<List<Tige>> =
        tigeRepository.getAllTiges()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    /** Toutes les parcelles — filtrées dans le Composable selon le scope. */
    val parcelles: StateFlow<List<Parcelle>> =
        parcelleRepository.getAllParcelles()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    /**
     * Stats de martelage calculées.
     * Alimentées par le Composable via [updateMartelageStats] après chaque
     * appel à computeMartelageStats (qui dépend d'états UI locaux).
     */
    private val _martelageStats = MutableStateFlow<MartelageStats?>(null)
    val martelageStats: StateFlow<MartelageStats?> = _martelageStats.asStateFlow()

    /** Sélection de tarif persistée — alimentée par le Composable. */
    private val _tarifSelection = MutableStateFlow<TarifSelection?>(null)
    val tarifSelection: StateFlow<TarifSelection?> = _tarifSelection.asStateFlow()

    /** Scope de synthèse courant — initialisé selon les paramètres de navigation. */
    private val _viewScope = MutableStateFlow(resolveInitialViewScope())
    val viewScope: StateFlow<MartelageViewScope> = _viewScope.asStateFlow()

    // ─────────────────────────────────────────────────────────────────────
    // Mutations
    // ─────────────────────────────────────────────────────────────────────

    fun updateMartelageStats(stats: MartelageStats?) {
        _martelageStats.value = stats
    }

    fun updateTarifSelection(selection: TarifSelection?) {
        _tarifSelection.value = selection
    }

    fun updateViewScope(newScope: MartelageViewScope) {
        _viewScope.value = newScope
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers privés
    // ─────────────────────────────────────────────────────────────────────

    private fun resolveInitialViewScope(): MartelageViewScope = when {
        placetteId != null -> MartelageViewScope.PLACETTE
        parcelleId != null -> MartelageViewScope.PARCELLE
        else               -> MartelageViewScope.GLOBAL
    }
}
