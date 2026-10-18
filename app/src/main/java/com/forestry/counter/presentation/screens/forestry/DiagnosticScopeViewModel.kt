package com.forestry.counter.presentation.screens.forestry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.domain.model.Foret
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.repository.ForetRepository
import com.forestry.counter.domain.repository.ParcelleRepository
import com.forestry.counter.domain.repository.PlacetteRepository
import com.forestry.counter.domain.repository.TigeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

internal enum class ScopedDiagnosticKind { IBP, STATION, RIPISYLVE }

internal data class DiagnosticScopeChoice(
    val forestId: String? = null,
    val parcelId: String? = null,
    val plotId: String? = null,
)

internal data class DiagnosticScopeUiState(
    val loaded: Boolean = false,
    val forests: List<Foret> = emptyList(),
    val parcels: List<Parcelle> = emptyList(),
    val plots: List<Placette> = emptyList(),
    val selectedForest: Foret? = null,
    val selectedParcel: Parcelle? = null,
    val selectedPlot: Placette? = null,
    val hasObservations: Boolean = false,
    val access: BusinessReadAccess.AccessSnapshot? = null,
)

internal data class DiagnosticScopeTarget(val kind: ScopedDiagnosticKind, val parcelId: String, val plotId: String?)

/** A canonical forest ID takes precedence over the historical group ID. */
internal fun parcelBelongsToDiagnosticForest(parcel: Parcelle, forestId: String): Boolean =
    parcel.foretId?.let { it == forestId } ?: (parcel.forestId == forestId)

internal fun diagnosticPlotBelongsToParcel(plot: Placette, parcelId: String): Boolean =
    plot.deletedAt == null && plot.parcelleId == parcelId

/** Selector-only VM: existing station/ripisylve screens still persist at parcel scope. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class DiagnosticScopeViewModel(
    private val foretRepository: ForetRepository,
    private val parcelleRepository: ParcelleRepository,
    private val placetteRepository: PlacetteRepository,
    private val tigeRepository: TigeRepository,
    private val readAccess: BusinessReadAccess,
) : ViewModel() {
    private val choice = MutableStateFlow(DiagnosticScopeChoice())
    private val catalog = combine(foretRepository.getAll(), parcelleRepository.getAllParcelles()) { forests, parcels ->
        forests.filter { it.deletedAt == null } to parcels.filter { it.deletedAt == null }
    }
    private val selection = combine(catalog, choice) { (forests, parcels), chosen ->
        val forest = forests.firstOrNull { it.foretId == chosen.forestId }
        val available = parcels.filter { forest == null || parcelBelongsToDiagnosticForest(it, forest.foretId) }
        val parcel = available.firstOrNull { it.id == chosen.parcelId }
        DiagnosticScopeUiState(loaded = true, forests = forests, parcels = available,
            selectedForest = forest, selectedParcel = parcel)
    }
    private val source = selection.flatMapLatest { selected ->
        val parcel = selected.selectedParcel
        if (parcel == null) flowOf(selected)
        else combine(placetteRepository.getPlacettesByParcelle(parcel.id),
            tigeRepository.getTigesByParcelle(parcel.id), choice) { plots, trees, chosen ->
            val available = plots.filter { diagnosticPlotBelongsToParcel(it, parcel.id) }
            selected.copy(plots = available, selectedPlot = available.firstOrNull { it.id == chosen.plotId },
                hasObservations = trees.any { it.deletedAt == null && it.parcelleId == parcel.id })
        }
    }
    val state = readAccess.observe(source, DiagnosticScopeUiState()) { selected, access ->
        val forests = selected.forests.filter { readAccess.canReadForest(it.foretId, access) }
        val parcels = selected.parcels.filter { readAccess.canReadParcelle(it.id, access) }
        val forest = selected.selectedForest?.takeIf { candidate -> forests.any { it.foretId == candidate.foretId } }
        val parcel = selected.selectedParcel?.takeIf { candidate -> parcels.any { it.id == candidate.id } &&
            (forest == null || parcelBelongsToDiagnosticForest(candidate, forest.foretId)) }
        val plots = if (parcel == null) emptyList() else selected.plots.filter { diagnosticPlotBelongsToParcel(it, parcel.id) }
        selected.copy(forests = forests, parcels = parcels, plots = plots, selectedForest = forest,
            selectedParcel = parcel, selectedPlot = selected.selectedPlot?.takeIf { candidate -> plots.any { it.id == candidate.id } },
            hasObservations = parcel != null && selected.hasObservations, access = access)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), DiagnosticScopeUiState())

    fun chooseForest(id: String?) {
        val current = state.value
        val access = current.access ?: return
        if (!readAccess.isCurrent(access) || (id != null && current.forests.none { it.foretId == id })) return
        choice.value = DiagnosticScopeChoice(forestId = id)
    }

    fun chooseParcel(id: String) {
        val current = state.value
        val access = current.access ?: return
        val parcel = current.parcels.firstOrNull { it.id == id } ?: return
        if (!readAccess.canReadParcelle(id, access)) return
        val forestId = current.forests.firstOrNull { parcelBelongsToDiagnosticForest(parcel, it.foretId) }?.foretId
        choice.value = DiagnosticScopeChoice(forestId = forestId, parcelId = id)
    }

    fun choosePlot(id: String?) {
        val current = state.value
        val access = current.access ?: return
        val parcel = current.selectedParcel ?: return
        if (!readAccess.canReadParcelle(parcel.id, access) ||
            (id != null && current.plots.none { it.id == id && diagnosticPlotBelongsToParcel(it, parcel.id) })) return
        choice.value = choice.value.copy(plotId = id)
    }

    /** Keep the displayed authority through all lookups; a late callback cannot adopt another account. */
    suspend fun resolveTarget(kind: ScopedDiagnosticKind, displayed: DiagnosticScopeUiState): DiagnosticScopeTarget? {
        val access = displayed.access ?: return null
        val parcel = displayed.selectedParcel ?: return null
        if (!readAccess.canReadParcelle(parcel.id, access) || !readAccess.revalidate(access)) return null
        val freshParcel = parcelleRepository.getParcelleById(parcel.id).first() ?: return null
        val forest = displayed.selectedForest
        if (forest != null && (!readAccess.canReadForest(forest.foretId, access) ||
                !parcelBelongsToDiagnosticForest(freshParcel, forest.foretId) ||
                foretRepository.getById(forest.foretId) == null)) return null
        val plot = displayed.selectedPlot
        if (plot != null) {
            val freshPlot = placetteRepository.getPlacetteById(plot.id).first() ?: return null
            if (!diagnosticPlotBelongsToParcel(freshPlot, parcel.id) ||
                freshPlot.centerWkt != plot.centerWkt || freshPlot.footprintGeoJson != plot.footprintGeoJson) return null
        }
        if (kind == ScopedDiagnosticKind.IBP && plot == null) return null
        if (state.value.selectedParcel?.id != parcel.id || state.value.selectedPlot?.id != plot?.id ||
            !readAccess.revalidate(access)) return null
        return DiagnosticScopeTarget(kind, parcel.id, if (kind == ScopedDiagnosticKind.IBP) plot?.id else null)
    }
}
