package com.forestry.counter.presentation.screens.forestry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forestry.counter.domain.location.*
import com.forestry.counter.domain.model.Foret
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.repository.ForetRepository
import com.forestry.counter.domain.repository.ParcelleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class ParcelMapCreationState(
    val forest: Foret? = null,
    val parentGeometry: ParcelGeometry? = null,
    val initialBounds: ParcelBounds? = null,
    val parcels: List<SourceParcel> = emptyList(),
    val selected: SourceParcel? = null,
    val name: String = "",
    val source: ParcelMapSource = ParcelMapSource.CADASTRE,
    val onfProfession: Boolean = false,
    val loading: Boolean = false,
    val needsZoom: Boolean = true,
    val truncated: Boolean = false,
    val rejected: Int = 0,
    val unavailableSourceIds: Set<String> = emptySet(),
    val error: Boolean = false,
    val saveError: Boolean = false,
    val outsideForest: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
)

/** One explicit source object per local parcel. Technical values are source snapshots, not inputs. */
class ParcelMapCreationViewModel(
    private val parcels: ParcelleRepository,
    private val forests: ForetRepository,
    private val maps: ParcelMapRepository,
    private val forestId: String,
    profession: Flow<String?>,
) : ViewModel() {
    private val mutable = MutableStateFlow(ParcelMapCreationState())
    val state: StateFlow<ParcelMapCreationState> = mutable.asStateFlow()
    private var queryJob: Job? = null
    private var lastBounds: ParcelBounds? = null
    private var lastZoom = 0.0
    private var nameEdited = false
    private val draftId = UUID.randomUUID().toString()
    val createdParcelId: String get() = draftId

    init {
        viewModelScope.launch { profession.collect { professionId ->
            val onfAllowed = professionId == "forestier_onf"
            if (!onfAllowed && mutable.value.source == ParcelMapSource.ONF) {
                queryJob?.cancel()
                nameEdited = false
                mutable.update { it.copy(onfProfession = false, source = ParcelMapSource.CADASTRE,
                    parcels = emptyList(), selected = null, name = "", loading = false) }
                lastBounds?.let { viewportChanged(it, lastZoom) }
            } else mutable.update { it.copy(onfProfession = onfAllowed) }
        } }
        viewModelScope.launch { loadParent() }
    }

    private suspend fun loadParent() {
        try {
            val forest = forests.getById(forestId)?.takeIf { it.deletedAt == null }
            if (forest == null) { mutable.update { it.copy(error = true) }; return }
            val geometry = forest.shape?.let { runCatching { ParcelGeometry.fromWkt(it) }.getOrNull() }
            var bounds = geometry?.bounds
            if (bounds == null) {
                val existing = parcels.getParcellesByForest(forestId).first().firstOrNull { it.deletedAt == null && it.shape != null }
                bounds = existing?.shape?.let { runCatching { ParcelGeometry.fromWkt(it).bounds }.getOrNull() }
            }
            if (bounds == null) {
                try {
                    val matches = maps.searchForests(forest.nom)
                    val exact = matches.filter { normalize(it.name) == normalize(forest.nom) }
                    val chosen = exact.singleOrNull()
                    bounds = chosen?.geometry?.bounds ?: chosen?.center?.let {
                        ParcelBounds(it.longitude - 0.01, it.latitude - 0.01, it.longitude + 0.01, it.latitude + 0.01)
                    }
                } catch (e: CancellationException) { throw e } catch (_: Exception) { /* manual forest still opens */ }
            }
            mutable.update { it.copy(forest = forest, parentGeometry = geometry,
                error = false, initialBounds = bounds ?: ParcelBounds(-5.2, 41.3, 9.7, 51.1)) }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { mutable.update { it.copy(error = true) } }
    }

    fun updateName(name: String) { if (!mutable.value.saving && !mutable.value.saved) { nameEdited = true; mutable.update { it.copy(name = name.take(120)) } } }
    fun sourceAvailable(source: ParcelMapSource) = maps.isAvailable(source) && (source != ParcelMapSource.ONF || mutable.value.onfProfession)
    fun changeSource(source: ParcelMapSource) {
        if (!sourceAvailable(source) || mutable.value.saving || mutable.value.source == source) return
        queryJob?.cancel(); mutable.update { it.copy(source = source, parcels = emptyList(), selected = null, name = "") }
        nameEdited = false; lastBounds?.let { viewportChanged(it, lastZoom) }
    }
    fun viewportChanged(bounds: ParcelBounds, zoom: Double) {
        lastBounds = bounds; lastZoom = zoom
        queryJob?.cancel()
        if (zoom < 13.0 || bounds.east - bounds.west > 0.15 || bounds.north - bounds.south > 0.15) {
            mutable.update { it.copy(needsZoom = true, loading = false, parcels = emptyList()) }; return
        }
        val source = mutable.value.source
        queryJob = viewModelScope.launch {
            delay(350)
            mutable.update { it.copy(loading = true, needsZoom = false, error = false) }
            try {
                val cadastral = maps.loadParcels(bounds, ParcelMapSource.CADASTRE)
                var overlayError = false
                val overlay = if (source == ParcelMapSource.ONF) {
                    try { maps.loadParcels(bounds, ParcelMapSource.ONF) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { overlayError = true; null }
                } else null
                mutable.update { it.copy(parcels = (cadastral.parcels + overlay?.parcels.orEmpty()).distinctBy { p -> p.id }, loading = false,
                    error = overlayError, truncated = cadastral.truncated || overlay?.truncated == true,
                    rejected = cadastral.rejected + (overlay?.rejected ?: 0), unavailableSourceIds = cadastral.unavailableSourceIds + overlay?.unavailableSourceIds.orEmpty()) }
            } catch (e: CancellationException) { throw e } catch (_: Exception) {
                mutable.update { it.copy(loading = false, error = true, parcels = emptyList()) }
            }
        }
    }
    fun retry() {
        if (mutable.value.forest == null) viewModelScope.launch { loadParent() }
        else lastBounds?.let { viewportChanged(it, lastZoom) }
    }
    fun selectAt(point: ParcelCoordinate) {
        val s = mutable.value
        if (s.saving || s.saved) return
        if (s.parentGeometry != null && !s.parentGeometry.contains(point)) {
            mutable.update { it.copy(outsideForest = true) }; return
        }
        val selected = s.parcels.filter { it.source == s.source && it.geometry.contains(point) }.singleOrNull() ?: return
        mutable.update { it.copy(selected = selected, name = if (nameEdited) it.name else selected.label, outsideForest = false) }
    }
    fun clearSelection() { if (!mutable.value.saving) mutable.update { it.copy(selected = null, outsideForest = false) } }
    fun featureCollection(): String {
        val s = mutable.value
        return maps.featureCollection((listOfNotNull(s.selected) + s.parcels).distinctBy { it.id })
    }
    fun save() {
        val s = mutable.value
        val selected = s.selected ?: return
        if (s.saving || s.saved || s.name.isBlank() || s.forest == null || selected.source != s.source || !sourceAvailable(selected.source)) return
        mutable.update { it.copy(saving = true, saveError = false) }
        viewModelScope.launch {
            try {
                val freshParent = forests.getById(forestId)
                require(freshParent != null && freshParent.deletedAt == null)
                require(sourceAvailable(selected.source))
                val shape = selected.geometry.toWkt()
                parcels.insertParcelle(Parcelle(id = draftId, forestId = forestId, foretId = forestId,
                    name = s.name.trim(), surfaceHa = (selected.sourceAreaSquareMetres ?: selected.geometry.areaSquareMetres) / 10_000.0,
                    shape = shape, slopePct = null, aspect = null, access = null, altitudeM = null,
                    objectifType = null, objectifVal = null, tolerancePct = null, samplingMode = null,
                    sampleAreaM2 = null, targetSpeciesCsv = null, srid = 4326, remarks = null,
                    codeInseeCommune = selected.communeCode, nomCommune = selected.communeName,
                    sectionCadastrale = selected.cadastralSection, numeroCadastral = selected.cadastralNumber,
                    contenanceCadastraleHa = selected.cadastralAreaSquareMetres?.div(10_000.0),
                    geometrieIgnWkt = shape, localisationMode = "MAP_${selected.source.name}", source = selected.provenance))
                mutable.update { it.copy(saving = false, saved = true) }
            } catch (e: CancellationException) { mutable.update { it.copy(saving = false) }; throw e }
            catch (_: Exception) { mutable.update { it.copy(saving = false, saveError = true) } }
        }
    }
    private fun normalize(name: String) = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(java.util.Locale.ROOT).trim()
}
