package com.forestry.counter.presentation.screens.forestry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.location.ParcelCoordinate
import com.forestry.counter.domain.location.ParcelGeometry
import com.forestry.counter.domain.repository.ParcelleRepository
import com.forestry.counter.domain.repository.PlacetteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.util.UUID
import kotlin.math.PI
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import com.forestry.counter.domain.location.PlotSamplingGeometry
import com.forestry.counter.domain.location.PlotSamplingPlan
import com.forestry.counter.domain.location.SampleFootprintShape
import com.forestry.counter.domain.location.SamplingFootprint
import com.forestry.counter.domain.location.SamplingGridLimitException

/**
 * ViewModel pour CreateParcelleWizard — spec GEOSYLVA-003 §29.7.
 */
class CreateParcelleViewModel(
    private val parcelleRepository: ParcelleRepository,
    private val foretId: String,
) : ViewModel() {

    private val _formState = MutableStateFlow(CreateParcelleFormState())
    val formState: StateFlow<CreateParcelleFormState> = _formState.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    private val parcelDraftId = UUID.randomUUID().toString()

    fun updateName(name: String) = update { copy(name = name) }

    fun updateSurfaceHa(surface: String) {
        update { copy(surfaceHa = decimal(surface)) }
    }

    fun updateSlopePct(slope: String) {
        update { copy(slopePct = decimal(slope)) }
    }

    fun updateAltitudeM(altitude: String) {
        update { copy(altitudeM = decimal(altitude)) }
    }

    fun updateRemarks(remarks: String) {
        update { copy(remarks = remarks.ifBlank { null }) }
    }

    private fun decimal(input: String) = input.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    private fun update(change: CreateParcelleFormState.() -> CreateParcelleFormState) {
        if (_formState.value.isSaving || _isSaved.value) return
        _formState.value = _formState.value.change().copy(saveError = false)
    }

    fun save() {
        val form = _formState.value
        if (!form.isValid || form.isSaving || _isSaved.value) return
        _formState.value = form.copy(isSaving = true, saveError = false)
        viewModelScope.launch {
            try {
                val parcelle = Parcelle(
                    id = parcelDraftId,
                    forestId = foretId,
                    foretId = foretId,
                    name = form.name,
                    surfaceHa = form.surfaceHa,
                    shape = null,
                    slopePct = form.slopePct,
                    aspect = null,
                    access = null,
                    altitudeM = form.altitudeM,
                    objectifType = null,
                    objectifVal = null,
                    tolerancePct = null,
                    samplingMode = null,
                    sampleAreaM2 = null,
                    targetSpeciesCsv = null,
                    srid = null,
                    remarks = form.remarks,
                )
                parcelleRepository.insertParcelle(parcelle)
                _isSaved.value = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _formState.value = _formState.value.copy(saveError = true) }
            finally { _formState.value = _formState.value.copy(isSaving = false) }
        }
    }
}

data class CreateParcelleFormState(
    val name: String = "",
    val surfaceHa: Double? = null,
    val slopePct: Double? = null,
    val altitudeM: Double? = null,
    val remarks: String? = null,
    val isSaving: Boolean = false,
    val saveError: Boolean = false,
) {
    val isValid: Boolean get() = name.isNotBlank() && (surfaceHa == null || (surfaceHa.isFinite() && surfaceHa > 0.0)) &&
        (slopePct == null || (slopePct.isFinite() && slopePct >= 0.0)) &&
        (altitudeM == null || altitudeM.isFinite())
}

/**
 * ViewModel pour CreatePlacetteWizard — spec GEOSYLVA-003 §29.8.
 */
class CreatePlacetteViewModel(
    private val placetteRepository: PlacetteRepository,
    private val parcelleId: String,
    private val parcelleRepository: ParcelleRepository,
    private val geometryDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _formState = MutableStateFlow(CreatePlacetteFormState())
    val formState: StateFlow<CreatePlacetteFormState> = _formState.asStateFlow()
    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()
    private var generation = 0L
    private val wholePlotId = UUID.randomUUID().toString()

    init {
        viewModelScope.launch {
            try {
                parcelleRepository.getParcelleById(parcelleId).collect { parent ->
                    val geometry = parent?.takeIf { it.deletedAt == null && it.srid == 4326 }
                        ?.let { runCatching { ParcelGeometry.fromWkt(it.geometrieIgnWkt ?: it.shape.orEmpty()) }.getOrNull() }
                    val form = _formState.value
                    val boundaryChanged = form.geometry?.toGeoJson() != geometry?.toGeoJson()
                    if (boundaryChanged) generation++
                    _formState.value = form.copy(
                        isLoading = false, parent = parent?.takeIf { it.deletedAt == null }, geometry = geometry,
                        points = if (boundaryChanged && (form.scope == PlotScope.AUTOMATIC_GRID || geometry == null)) emptyList() else form.points,
                        gridCells = if (boundaryChanged) emptyList() else form.gridCells,
                        pointIds = if (boundaryChanged && (form.scope == PlotScope.AUTOMATIC_GRID || geometry == null)) emptyList() else form.pointIds,
                        selectedPointId = form.selectedPointId.takeUnless { boundaryChanged && (form.scope == PlotScope.AUTOMATIC_GRID || geometry == null) },
                        boundaryReviewConfirmed = form.boundaryReviewConfirmed && !boundaryChanged,
                        gridPreviewReady = form.gridPreviewReady && !boundaryChanged,
                        isGenerating = form.isGenerating && !boundaryChanged,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _formState.value = _formState.value.copy(isLoading = false, error = PlotCreationError.PARENT_UNAVAILABLE)
            }
        }
    }

    fun updateName(name: String) = edit { it.copy(name = name, error = null) }

    fun updateScope(scope: PlotScope) {
        if (_formState.value.scope == scope) return
        edit { form ->
            generation++
            form.copy(scope = scope, points = emptyList(), pointIds = emptyList(), selectedPointId = null,
                boundaryReviewConfirmed = false, gridCells = emptyList(), gridPreviewReady = false,
                isGenerating = false, error = null,
                sizeMeasure = if (scope == PlotScope.AUTOMATIC_GRID) FootprintSizeMeasure.AREA else form.sizeMeasure)
        }
    }

    fun updateRayonM(rayon: String) = invalidatePreview { it.copy(radiusInput = rayon, error = null) }
    fun updateFootprintArea(area: String) = invalidatePreview { it.copy(areaInput = area, error = null) }
    fun updateFootprintShape(shape: SampleFootprintShape) = invalidatePreview { form ->
        form.copy(footprintShape = shape, areaInput = form.footprintAreaM2?.toString() ?: form.areaInput,
            sizeMeasure = if (shape == SampleFootprintShape.HEXAGON) FootprintSizeMeasure.AREA else form.sizeMeasure, error = null)
    }
    fun updateSizeMeasure(measure: FootprintSizeMeasure) = invalidatePreview { form ->
        val area = form.footprintAreaM2
        form.copy(sizeMeasure = measure,
            radiusInput = area?.let { sqrt(it / PI).toString() } ?: form.radiusInput,
            areaInput = area?.toString() ?: form.areaInput, error = null)
    }

    /** One explicit apply updates the draft; opening or cancelling a panel changes nothing. */
    fun applyParameters(parameters: PlotParameterDraft) {
        val form = _formState.value
        if (parameters.copy(name = form.name) == PlotParameterDraft.from(form)) { updateName(parameters.name); return }
        invalidatePreview { current ->
            current.copy(name = parameters.name, radiusInput = parameters.radiusInput, areaInput = parameters.areaInput,
                footprintShape = parameters.shape, sizeMeasure = parameters.measure, rotationInput = parameters.rotationInput, error = null)
        }
    }

    fun applyDistribution(distribution: PlotDistributionDraft) = invalidatePreview { form ->
        form.copy(cellAreaInput = distribution.cellAreaInput, rotationInput = distribution.rotationInput,
            excludeBoundary = distribution.excludeBoundary, error = null)
    }

    fun selectPoint(id: String) = edit { form ->
        if (id in form.pointIds) form.copy(selectedPointId = id) else form
    }

    fun confirmBoundaryReview() = edit { it.copy(boundaryReviewConfirmed = true) }

    fun updateCellAreaHa(area: String) = invalidatePreview { it.copy(cellAreaInput = area, error = null) }
    fun updateRotationDeg(rotation: String) = invalidatePreview { it.copy(rotationInput = rotation, error = null) }
    fun updateExcludeBoundary(exclude: Boolean) = invalidatePreview { it.copy(excludeBoundary = exclude, error = null) }

    /** A preview is a draft. No plot is persisted until the complete batch is confirmed. */
    fun generateGrid() {
        val form = _formState.value
        if (form.isSaving || form.isGenerating || _isSaved.value || !form.canGenerate) return
        val parcel = form.geometry ?: return
        val cellArea = form.cellAreaHa ?: return
        val footprintArea = form.footprintAreaM2 ?: return
        val rotation = form.rotationDeg ?: return
        val ticket = ++generation
        _formState.value = form.copy(isGenerating = true, error = null, gridPreviewReady = false, points = emptyList(), pointIds = emptyList(), selectedPointId = null, boundaryReviewConfirmed = false, gridCells = emptyList())
        viewModelScope.launch {
            try {
                val preview = withContext(geometryDispatcher) {
                    PlotSamplingGeometry.grid(parcel, cellArea, form.footprintShape, footprintArea, rotation)
                }
                if (ticket != generation || _isSaved.value) return@launch
                _formState.value = _formState.value.copy(
                    points = preview.footprints.filter { !form.excludeBoundary || !it.crossesBoundary }.map { it.center },
                    pointIds = preview.footprints.filter { !form.excludeBoundary || !it.crossesBoundary }.map { UUID.randomUUID().toString() },
                    gridCells = preview.cells.filterIndexed { index, _ -> !form.excludeBoundary || !preview.footprints[index].crossesBoundary },
                    excludedBoundaryCount = if (form.excludeBoundary) preview.footprints.count { it.crossesBoundary } else 0,
                    gridPreviewReady = true, isGenerating = false,
                    neighborDistanceM = preview.neighborDistanceM,
                    error = if (preview.footprints.none { !form.excludeBoundary || !it.crossesBoundary }) PlotCreationError.EMPTY_GRID else null,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SamplingGridLimitException) {
                if (ticket == generation) _formState.value = _formState.value.copy(isGenerating = false, error = PlotCreationError.POINT_LIMIT)
            } catch (_: IllegalArgumentException) {
                if (ticket == generation) _formState.value = _formState.value.copy(isGenerating = false, error = PlotCreationError.GRID_UNSUPPORTED)
            }
        }
    }

    fun addPoint(point: ParcelCoordinate) {
        val form = _formState.value
        if (form.isSaving || _isSaved.value || form.scope != PlotScope.MAP_POINTS) return
        if (form.points.size >= PlotSamplingGeometry.MAX_PLOTS) {
            _formState.value = form.copy(error = PlotCreationError.POINT_LIMIT)
            return
        }
        if (form.geometry?.contains(point) != true) {
            _formState.value = form.copy(error = PlotCreationError.POINT_OUTSIDE)
            return
        }
        if (point in form.points) return
        _formState.value = form.copy(points = form.points + point, pointIds = form.pointIds + UUID.randomUUID().toString(),
            boundaryReviewConfirmed = false, error = null)
    }

    fun removePoint(index: Int) {
        val form = _formState.value
        if (form.isSaving || form.isGenerating || _isSaved.value || index !in form.points.indices) return
        _formState.value = form.copy(points = form.points.filterIndexed { i, _ -> i != index },
            pointIds = form.pointIds.filterIndexed { i, _ -> i != index },
            selectedPointId = form.selectedPointId?.takeUnless { it == form.pointIds.getOrNull(index) },
            boundaryReviewConfirmed = false, gridCells = form.gridCells.filterIndexed { i, _ -> i != index }, error = null)
    }

    private fun edit(change: (CreatePlacetteFormState) -> CreatePlacetteFormState) {
        if (_formState.value.isSaving || _isSaved.value) return
        _formState.value = change(_formState.value)
    }

    private fun invalidatePreview(change: (CreatePlacetteFormState) -> CreatePlacetteFormState) {
        edit { form ->
            generation++
            change(form).copy(
                points = if (form.scope == PlotScope.AUTOMATIC_GRID) emptyList() else form.points,
                pointIds = if (form.scope == PlotScope.AUTOMATIC_GRID) emptyList() else form.pointIds,
                selectedPointId = if (form.scope == PlotScope.AUTOMATIC_GRID) null else form.selectedPointId,
                boundaryReviewConfirmed = false,
                gridCells = emptyList(), gridPreviewReady = false, isGenerating = false, excludedBoundaryCount = 0,
            )
        }
    }

    fun save(defaultNamePrefix: String = "Placette") {
        val form = _formState.value
        if (!form.isValid || form.requiresBoundaryReview || form.isSaving || _isSaved.value) return
        _formState.value = form.copy(isSaving = true, error = null)
        viewModelScope.launch {
            try {
                val prefix = form.name.trim().ifBlank { defaultNamePrefix }
                val plots = if (form.scope == PlotScope.ENTIRE_PARCEL) {
                    listOf(Placette(
                        id = wholePlotId, parcelleId = parcelleId,
                        name = prefix, type = "ENTIRE_PARCEL", rayonM = null,
                        surfaceM2 = form.wholeAreaM2, centerWkt = null,
                    ))
                } else {
                    val area = requireNotNull(form.footprintAreaM2)
                    form.footprints.mapIndexed { index, footprint ->
                        val plan = PlotSamplingPlan(
                            placement = if (form.scope == PlotScope.AUTOMATIC_GRID) "AUTO" else "MANUAL",
                            gridLayout = if (form.scope == PlotScope.AUTOMATIC_GRID) "HEX" else null,
                            cellAreaHa = if (form.scope == PlotScope.AUTOMATIC_GRID) form.cellAreaHa else null,
                            rotationDeg = requireNotNull(form.rotationDeg), footprintShape = form.footprintShape.name,
                            footprintAreaM2 = area,
                            boundaryPolicy = if (form.scope == PlotScope.AUTOMATIC_GRID && form.excludeBoundary) "FULLY_INSIDE" else "CENTER_INSIDE",
                            boundaryWarning = footprint.crossesBoundary,
                        ).toJson()
                        Placette(
                            id = form.pointIds[index], parcelleId = parcelleId,
                            name = "$prefix ${index + 1}",
                            type = if (form.footprintShape == SampleFootprintShape.CIRCLE) "CIRC" else "HEX",
                            rayonM = if (form.footprintShape == SampleFootprintShape.CIRCLE) sqrt(area / PI) else null,
                            surfaceM2 = area,
                            centerWkt = "POINT (${footprint.center.longitude} ${footprint.center.latitude})",
                            samplingPlanJson = plan, footprintGeoJson = footprint.geometry.toGeoJson(),
                        )
                    }
                }
                placetteRepository.insertPlacettesForParcelle(plots)
                _isSaved.value = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _formState.value = _formState.value.copy(error = PlotCreationError.SAVE_FAILED)
            } finally {
                _formState.value = _formState.value.copy(isSaving = false)
            }
        }
    }
}

enum class PlotScope { ENTIRE_PARCEL, MAP_POINTS, AUTOMATIC_GRID }
enum class FootprintSizeMeasure { RADIUS, AREA }
enum class PlotCreationError { PARENT_UNAVAILABLE, POINT_OUTSIDE, POINT_LIMIT, SAVE_FAILED, GRID_UNSUPPORTED, EMPTY_GRID }

data class CreatePlacetteFormState(
    val name: String = "",
    val scope: PlotScope = PlotScope.ENTIRE_PARCEL,
    val radiusInput: String = "",
    val areaInput: String = "500",
    val cellAreaInput: String = "1",
    val rotationInput: String = "0",
    val footprintShape: SampleFootprintShape = SampleFootprintShape.CIRCLE,
    val excludeBoundary: Boolean = false,
    val excludedBoundaryCount: Int = 0,
    val sizeMeasure: FootprintSizeMeasure = FootprintSizeMeasure.RADIUS,
    val points: List<ParcelCoordinate> = emptyList(),
    val pointIds: List<String> = emptyList(),
    val selectedPointId: String? = null,
    val boundaryReviewConfirmed: Boolean = false,
    val gridCells: List<ParcelGeometry> = emptyList(),
    val gridPreviewReady: Boolean = false,
    val neighborDistanceM: Double? = null,
    val parent: Parcelle? = null,
    val geometry: ParcelGeometry? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isGenerating: Boolean = false,
    val error: PlotCreationError? = null,
) {
    val rayonM: Double? get() = decimal(radiusInput)?.takeIf { it > 0.0 && (PI * it * it).isFinite() }
    val cellAreaHa: Double? get() = decimal(cellAreaInput)?.takeIf { it in 0.01..10_000.0 }
    val rotationDeg: Double? get() = decimal(rotationInput)?.takeIf { it >= 0.0 && it < 360.0 }
    val footprintAreaM2: Double? get() =
        (if (footprintShape == SampleFootprintShape.CIRCLE && sizeMeasure == FootprintSizeMeasure.RADIUS)
            rayonM?.let { PI * it * it } else decimal(areaInput))?.takeIf { it in 1.0..1_000_000.0 }
    val wholeAreaM2: Double? get() = parent?.surfaceHa?.times(10_000.0)?.takeIf { it.isFinite() && it > 0.0 }
    val footprints: List<SamplingFootprint> by lazy {
        val parcel = geometry
        val area = footprintAreaM2
        val rotation = rotationDeg
        if (parcel == null || area == null || rotation == null || points.any { !parcel.contains(it) }) emptyList()
        else runCatching { PlotSamplingGeometry.footprints(parcel, points, footprintShape, area, rotation) }.getOrDefault(emptyList())
    }
    val edgeWarningCount: Int get() = footprints.count { it.crossesBoundary }
    val requiresBoundaryReview: Boolean get() = edgeWarningCount > 0 && !boundaryReviewConfirmed
    val canGenerate: Boolean get() = scope == PlotScope.AUTOMATIC_GRID && !isLoading && !isSaving &&
        parent != null && parent.deletedAt == null && geometry != null && cellAreaHa != null && footprintAreaM2 != null && rotationDeg != null
    val isValid: Boolean get() = !isLoading && !isGenerating && parent != null && parent.deletedAt == null && geometry != null &&
        when (scope) {
            PlotScope.ENTIRE_PARCEL -> wholeAreaM2 != null
            PlotScope.MAP_POINTS -> footprintAreaM2 != null && rotationDeg != null && points.size in 1..PlotSamplingGeometry.MAX_PLOTS && footprints.size == points.size && pointIds.size == points.size && pointIds.distinct().size == points.size
            PlotScope.AUTOMATIC_GRID -> gridPreviewReady && points.size in 1..PlotSamplingGeometry.MAX_PLOTS && footprints.size == points.size && pointIds.size == points.size && pointIds.distinct().size == points.size
        }
    private fun decimal(value: String): Double? = value.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
}

/** Local editor state: apply is the only operation that changes the planned lot. */
data class PlotParameterDraft(
    val name: String, val radiusInput: String, val areaInput: String,
    val shape: SampleFootprintShape, val measure: FootprintSizeMeasure, val rotationInput: String = "0",
) {
    fun asForm() = CreatePlacetteFormState(name = name, radiusInput = radiusInput,
        areaInput = areaInput, footprintShape = shape, sizeMeasure = measure, rotationInput = rotationInput)
    fun withMeasure(next: FootprintSizeMeasure): PlotParameterDraft {
        val area = asForm().footprintAreaM2
        return copy(measure = next, areaInput = area?.toString() ?: areaInput,
            radiusInput = area?.let { sqrt(it / PI).toString() } ?: radiusInput)
    }
    companion object {
        fun from(form: CreatePlacetteFormState) = PlotParameterDraft(form.name, form.radiusInput,
            form.areaInput, form.footprintShape, form.sizeMeasure, form.rotationInput)
    }
}

data class PlotDistributionDraft(val cellAreaInput: String, val rotationInput: String, val excludeBoundary: Boolean) {
    fun asForm() = CreatePlacetteFormState(cellAreaInput = cellAreaInput, rotationInput = rotationInput)
    companion object {
        fun from(form: CreatePlacetteFormState) = PlotDistributionDraft(form.cellAreaInput, form.rotationInput, form.excludeBoundary)
    }
}
