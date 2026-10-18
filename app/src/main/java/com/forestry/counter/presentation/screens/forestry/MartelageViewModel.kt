package com.forestry.counter.presentation.screens.forestry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.domain.calculation.ForestryCalculator
import com.forestry.counter.domain.calculation.HeightModeEntry
import com.forestry.counter.domain.calculation.SynthesisParameters
import com.forestry.counter.domain.calculation.SynthesisReport
import com.forestry.counter.domain.calculation.SynthesisReportCalculator
import com.forestry.counter.domain.calculation.RecordedDendrometry
import com.forestry.counter.domain.calculation.RecordedDendrometryCalculator
import com.forestry.counter.domain.calculation.tarifs.TarifSelection
import com.forestry.counter.domain.model.Essence
import com.forestry.counter.domain.model.InventaireSession
import com.forestry.counter.domain.model.MartelageCalculationSnapshotV1
import com.forestry.counter.domain.model.MartelageSnapshotFingerprint
import com.forestry.counter.domain.model.MartelageSnapshotV1
import com.forestry.counter.domain.model.MartelageStates
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.repository.EssenceRepository
import com.forestry.counter.domain.repository.InventaireSessionRepository
import com.forestry.counter.domain.repository.ParcelleRepository
import com.forestry.counter.domain.repository.PlacetteRepository
import com.forestry.counter.domain.repository.TigeRepository
import com.forestry.counter.domain.usecase.export.SynthesisReadGuard
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/** An observation day is a legacy filter, never an inferred campaign. */
fun synthesisObservationDay(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate().toString()

data class SynthesisSelection(val sessionId: String? = null, val legacyDay: String? = null, val plotId: String? = null)

data class SynthesisUiState(
    val loading: Boolean = true,
    val parcels: List<Parcelle> = emptyList(),
    val parcel: Parcelle? = null,
    val plots: List<Placette> = emptyList(),
    val sessions: List<InventaireSession> = emptyList(),
    val legacyDays: List<String> = emptyList(),
    val selection: SynthesisSelection = SynthesisSelection(),
    val session: InventaireSession? = null,
    val report: SynthesisReport? = null,
    val snapshot: MartelageSnapshotV1? = null,
    val observations: List<Tige> = emptyList(),
    val essences: List<Essence> = emptyList(),
    val parameters: SynthesisParameters = SynthesisParameters(),
    val error: Boolean = false,
    val accessSnapshot: BusinessReadAccess.AccessSnapshot? = null,
    /** Read-only projection of the selected report; never persisted into a closed calculation. */
    val dendrometry: RecordedDendrometry? = null,
) {
    val closed: Boolean get() = session?.martelageState == MartelageStates.CLOSED
    val sealedCalculation: Boolean get() = closed && snapshot?.calculation != null && report != null
}

private data class ScopeInputs(val parcels: List<Parcelle>, val trees: List<Tige>, val essences: List<Essence>)
private data class ParcelInputs(val scope: ScopeInputs, val parcel: Parcelle?, val plots: List<Placette>, val sessions: List<InventaireSession>)
private data class ReadableParcelInputs(val data: ParcelInputs? = null, val access: BusinessReadAccess.AccessSnapshot? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class MartelageViewModel(
    val scope: String,
    val forestId: String?,
    val parcelleId: String?,
    val placetteId: String?,
    essenceRepository: EssenceRepository,
    tigeRepository: TigeRepository,
    parcelleRepository: ParcelleRepository,
    private val placetteRepository: PlacetteRepository?,
    private val sessionRepository: InventaireSessionRepository?,
    private val forestryCalculator: ForestryCalculator,
    private val businessReadAccess: BusinessReadAccess? = null,
    private val calculationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }
    private val selectedParcelId = MutableStateFlow(parcelleId)
    private val selection = MutableStateFlow(SynthesisSelection(plotId = placetteId))
    private val parameters = MutableStateFlow(
        SynthesisParameters(sourcePolicy = SynthesisParameters.CURRENT_SOURCE_POLICY),
    )
    private val parametersLoaded = MutableStateFlow(false)
    private val failed = MutableStateFlow(false)
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val inputs = combine(
        parcelleRepository.getAllParcelles(), tigeRepository.getAllTiges(), essenceRepository.getAllEssences(),
    ) { parcels, trees, essences ->
        ScopeInputs(parcels.filter { it.deletedAt == null && (forestId == null || it.forestId == forestId || it.foretId == forestId) &&
            (parcelleId == null || it.id == parcelleId) }, trees, essences)
    }
    private val parcelInputs = selectedParcelId.flatMapLatest { id ->
        combine(inputs,
            if (id != null && placetteRepository != null) placetteRepository.getPlacettesByParcelle(id) else flowOf(emptyList()),
            when {
                id == null || sessionRepository == null -> flowOf(emptyList())
                placetteId != null -> sessionRepository.getByPlacette(placetteId)
                else -> sessionRepository.getByParcelle(id)
            },
        ) { base, plots, sessions ->
            ParcelInputs(base, base.parcels.firstOrNull { it.id == id }, plots.filter { it.deletedAt == null }, sessions.filter { it.deletedAt == null })
        }
    }
    private var lastAccess: BusinessReadAccess.AccessSnapshot? = null
    private val readableInputs = businessReadAccess?.observe(
        parcelInputs.map { ReadableParcelInputs(it) }, ReadableParcelInputs(),
    ) { value, access ->
        val policy = checkNotNull(businessReadAccess)
        if (lastAccess?.let { !policy.isCurrent(it) } == true) selection.value = SynthesisSelection()
        lastAccess = access
        val data = value.data ?: return@observe ReadableParcelInputs()
        val allowedParcels = data.scope.parcels.filter { policy.canReadParcelle(it.id, access) }
        val parcel = data.parcel?.takeIf { policy.canReadParcelle(it.id, access) }
        ReadableParcelInputs(data.copy(scope = data.scope.copy(parcels = allowedParcels,
            trees = data.scope.trees.filter { policy.canReadParcelle(it.parcelleId, access) }),
            parcel = parcel, plots = if (parcel != null) data.plots else emptyList(),
            sessions = if (parcel != null) data.sessions else emptyList()), access)
    }?.onEach {
        if (it.data == null) {
            if (lastAccess != null) selection.value = SynthesisSelection()
            lastAccess = null
            _busy.value = false
        }
    } ?: parcelInputs.map { ReadableParcelInputs(it) }
    val uiState: StateFlow<SynthesisUiState> = combine(readableInputs, selection, parameters, parametersLoaded, failed) {
        data, chosen, params, loaded, failure -> arrayOf(data, chosen, params, loaded, failure)
    }.mapLatest { values ->
        val readable = values[0] as ReadableParcelInputs
        val data = readable.data ?: return@mapLatest SynthesisUiState(loading = false)
        val chosen = values[1] as SynthesisSelection
        val params = values[2] as SynthesisParameters
        val loaded = values[3] as Boolean
        val failure = values[4] as Boolean
        val state = withContext(calculationDispatcher) { buildState(data, chosen, params, loaded, failure) }
        val access = readable.access
        if (businessReadAccess != null && (access == null || !businessReadAccess.revalidate(access))) SynthesisUiState(loading = false)
        else state.copy(accessSnapshot = access)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SynthesisUiState())

    init { reloadParameters() }

    fun isReadable(state: SynthesisUiState): Boolean = businessReadAccess?.let { access ->
        state.accessSnapshot?.let { snapshot -> access.isCurrent(snapshot) &&
            (state.parcel?.let { access.canReadParcelle(it.id, snapshot) } ?: true) } == true
    } ?: true

    /** Uses the snapshot behind these results, never a fresh permission after the picker. */
    suspend fun captureExportGuard(state: SynthesisUiState): SynthesisReadGuard? {
        val access = businessReadAccess ?: return SynthesisReadGuard({ true }, { true })
        val snapshot = state.accessSnapshot ?: return null
        val parcel = state.parcel ?: return null
        val guard = SynthesisReadGuard(
            { access.isCurrent(snapshot) && access.canReadParcelle(parcel.id, snapshot) },
            { access.canReadParcelle(parcel.id, snapshot) && access.revalidate(snapshot) },
        )
        return try { guard.requireRevalidated(); guard } catch (error: Exception) {
            if (error is CancellationException) throw error
            null
        }
    }

    fun chooseParcel(id: String) {
        selection.value = SynthesisSelection()
        selectedParcelId.value = id
        failed.value = false
    }
    fun chooseSession(id: String) { selection.value = SynthesisSelection(sessionId = id, plotId = placetteId); failed.value = false }
    fun chooseLegacyDay(day: String) { selection.value = SynthesisSelection(legacyDay = day, plotId = placetteId); failed.value = false }
    fun choosePlot(id: String?) {
        if (uiState.value.session?.placetteId != null || (placetteId != null && id != placetteId)) return
        selection.value = selection.value.copy(plotId = id)
    }

    fun reloadParameters() {
        viewModelScope.launch {
            try {
                val params = forestryCalculator.loadSynthesisParams()
                parameters.value = SynthesisParameters(
                    params.tarifSelection,
                    forestryCalculator.diameterClasses(),
                    params.heightModes,
                    sourcePolicy = SynthesisParameters.CURRENT_SOURCE_POLICY,
                )
                parametersLoaded.value = true
                failed.value = false
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                failed.value = true; parametersLoaded.value = true
            }
        }
    }

    fun chooseTarif(selection: TarifSelection) {
        viewModelScope.launch {
            try {
                forestryCalculator.saveTarifSelection(selection)
                parameters.value = parameters.value.copy(tarifSelection = selection)
                failed.value = false
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                failed.value = true
            }
        }
    }

    fun setClassHeight(essence: String, diameterClass: Int, height: Double) {
        if (!height.isFinite() || height <= 0.0) return
        viewModelScope.launch {
            try {
                forestryCalculator.setHeightMode(HeightModeEntry(essence, diameterClass, "FIXED", height))
                reloadParameters()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                failed.value = true
            }
        }
    }

    fun closeSelectedSession() {
        val state = uiState.value
        val session = state.session ?: return
        val repository = sessionRepository ?: return
        val parcel = state.parcel ?: return
        if (session.martelageState !in MartelageStates.ACTIVE || _busy.value || !isReadable(state)) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val guard = captureExportGuard(state) ?: return@launch
                val allTrees = state.observations
                // V2 seals exactly its plot. V1 retains the whole original parcel campaign.
                val scopedPlotId = session.placetteId
                val plots = if (scopedPlotId != null) listOf(checkNotNull(state.plots.singleOrNull { it.id == scopedPlotId })) else state.plots
                val params = state.parameters.copy(selectedPlotId = scopedPlotId)
                val report = withContext(calculationDispatcher) {
                    SynthesisReportCalculator.calculate(parcel, plots, allTrees,
                        state.essences.associate { it.code to it.name }, params,
                        session.sessionId, session.campaignId, session.revisionNumber, System.currentTimeMillis())
                }
                val calculation = MartelageCalculationSnapshotV1(report.generatedAt, report.engineVersion,
                    json.encodeToJsonElement(params).jsonObject, json.encodeToJsonElement(report).jsonObject,
                    if (scopedPlotId == null) MartelageSnapshotFingerprint.computeLegacyV1(parcel, plots, allTrees)
                    else MartelageSnapshotFingerprint.compute(parcel, plots, allTrees))
                guard.requireRevalidated()
                repository.closeMartelage(session.sessionId, calculation)
                if (guard.isCurrent()) failed.value = false
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                failed.value = true
            }
            finally { _busy.value = false }
        }
    }

    private suspend fun buildState(data: ParcelInputs, chosen: SynthesisSelection, params: SynthesisParameters, loaded: Boolean, failure: Boolean): SynthesisUiState {
        val parcel = data.parcel
        if (parcel == null) return SynthesisUiState(loading = false, parcels = data.scope.parcels, error = failure)
        val trees = data.scope.trees.filter { it.parcelleId == parcel.id && it.deletedAt == null }
        val legacy = trees.filter { it.sessionId == null && (placetteId == null || it.placetteId == placetteId) }
            .map { synthesisObservationDay(it.timestamp) }.distinct().sortedDescending()
        val defaultSession = data.sessions.firstOrNull { it.martelageState in MartelageStates.ACTIVE } ?: data.sessions.firstOrNull()
        val effective = if (chosen.sessionId == null && chosen.legacyDay == null) {
            if (defaultSession != null) chosen.copy(sessionId = defaultSession.sessionId) else chosen.copy(legacyDay = legacy.firstOrNull())
        } else chosen
        val session = data.sessions.firstOrNull { it.sessionId == effective.sessionId }
        val snapshot = if (session?.martelageState == MartelageStates.CLOSED) {
            try { sessionRepository?.getClosedSnapshot(session.sessionId) } catch (error: Exception) {
                if (error is CancellationException) throw error
                null
            }
        } else null
        val plotScope = session?.placetteId ?: effective.plotId
        val selectedTrees = if (session?.martelageState == MartelageStates.CLOSED) snapshot?.tiges.orEmpty()
            else if (session != null) trees.filter { it.sessionId == session.sessionId && (session.placetteId == null || it.placetteId == session.placetteId) }
            else trees.filter { it.sessionId == null && (plotScope == null || it.placetteId == plotScope) && effective.legacyDay != null && synthesisObservationDay(it.timestamp) == effective.legacyDay }
        val closed = session?.martelageState == MartelageStates.CLOSED
        var invalidSnapshot = false
        val savedReport = if (closed && snapshot?.calculation != null) try {
            val calc = snapshot.calculation
            require(calc.sourceFingerprint == if (snapshot.schemaVersion == 1)
                MartelageSnapshotFingerprint.computeLegacyV1(snapshot.parcelle, snapshot.placettes, snapshot.tiges)
                else MartelageSnapshotFingerprint.compute(snapshot.parcelle, snapshot.placettes, snapshot.tiges))
            json.decodeFromJsonElement(SynthesisReport.serializer(), calc.results).also {
                require(it.schemaVersion == 1 && it.sessionId == snapshot.sessionId && it.parcelId == snapshot.parcelleId &&
                    it.revisionNumber == snapshot.revisionNumber && it.engineVersion == calc.engineVersion && it.generatedAt == calc.generatedAt)
                require(snapshot.schemaVersion != 2 || it.selectedPlotId == snapshot.placetteId)
            }
        } catch (_: Exception) { invalidSnapshot = true; null } else null
        val plots = if (session?.placetteId != null) data.plots.filter { it.id == session.placetteId } else data.plots
        val activeParams = params.copy(selectedPlotId = plotScope)
        val report = if (closed) savedReport else if (loaded) SynthesisReportCalculator.calculate(parcel, plots, selectedTrees,
            data.scope.essences.associate { it.code to it.name }, activeParams,
            session?.sessionId, session?.campaignId, session?.revisionNumber, System.currentTimeMillis()) else null
        return SynthesisUiState(!loaded, data.scope.parcels, snapshot?.parcelle ?: parcel,
            snapshot?.placettes ?: plots, data.sessions, legacy, effective.copy(plotId = plotScope), session, report, snapshot,
            selectedTrees, data.scope.essences, activeParams, failure || invalidSnapshot || (closed && snapshot == null),
            dendrometry = report?.let { RecordedDendrometryCalculator.calculate(it, selectedTrees) })
    }
}
