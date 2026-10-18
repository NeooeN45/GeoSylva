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

    fun updateName(name: String) {
        _formState.value = _formState.value.copy(name = name)
    }

    fun updateSurfaceHa(surface: String) {
        _formState.value = _formState.value.copy(surfaceHa = surface.toDoubleOrNull())
    }

    fun updateSlopePct(slope: String) {
        _formState.value = _formState.value.copy(slopePct = slope.toDoubleOrNull())
    }

    fun updateAltitudeM(altitude: String) {
        _formState.value = _formState.value.copy(altitudeM = altitude.toDoubleOrNull())
    }

    fun updateRemarks(remarks: String) {
        _formState.value = _formState.value.copy(remarks = remarks.ifBlank { null })
    }

    fun save() {
        val form = _formState.value
        if (!form.isValid) return
        viewModelScope.launch {
            val parcelle = Parcelle(
                id = UUID.randomUUID().toString(),
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
        }
    }
}

data class CreateParcelleFormState(
    val name: String = "",
    val surfaceHa: Double? = null,
    val slopePct: Double? = null,
    val altitudeM: Double? = null,
    val remarks: String? = null,
) {
    val isValid: Boolean get() = name.isNotBlank()
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
                        points = if (boundaryChanged && form.scope == PlotScope.AUTOMATIC_GRID) emptyList() else form.points,
                        gridCells = if (boundaryChanged) emptyList() else form.gridCells,
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
            form.copy(scope = scope, points = emptyList(), gridCells = emptyList(), gridPreviewReady = false,
                isGenerating = false, error = null,
                sizeMeasure = if (scope == PlotScope.AUTOMATIC_GRID) FootprintSizeMeasure.AREA else form.sizeMeasure)
        }
    }

    fun updateRayonM(rayon: String) = invalidatePreview { it.copy(radiusInput = rayon, error = null) }
    fun updateFootprintArea(area: String) = invalidatePreview { it.copy(areaInput = area, error = null) }
    fun updateFootprintShape(shape: SampleFootprintShape) = invalidatePreview {
        it.copy(footprintShape = shape, sizeMeasure = if (shape == SampleFootprintShape.HEXAGON) FootprintSizeMeasure.AREA else it.sizeMeasure, error = null)
    }
    fun updateSizeMeasure(measure: FootprintSizeMeasure) = invalidatePreview { it.copy(sizeMeasure = measure, error = null) }
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
        _formState.value = form.copy(isGenerating = true, error = null, gridPreviewReady = false, points = emptyList(), gridCells = emptyList())
        viewModelScope.launch {
            try {
                val preview = withContext(geometryDispatcher) {
                    PlotSamplingGeometry.grid(parcel, cellArea, form.footprintShape, footprintArea, rotation)
                }
                if (ticket != generation || _isSaved.value) return@launch
                _formState.value = _formState.value.copy(
                    points = preview.footprints.filter { !form.excludeBoundary || !it.crossesBoundary }.map { it.center },
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
        _formState.value = form.copy(points = form.points + point, error = null)
    }

    fun removePoint(index: Int) {
        val form = _formState.value
        if (form.isSaving || form.isGenerating || _isSaved.value || index !in form.points.indices) return
        _formState.value = form.copy(points = form.points.filterIndexed { i, _ -> i != index },
            gridCells = form.gridCells.filterIndexed { i, _ -> i != index }, error = null)
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
                gridCells = emptyList(), gridPreviewReady = false, isGenerating = false, excludedBoundaryCount = 0,
            )
        }
    }

    fun save(defaultNamePrefix: String = "Placette") {
        val form = _formState.value
        if (!form.isValid || form.isSaving || _isSaved.value) return
        _formState.value = form.copy(isSaving = true, error = null)
        viewModelScope.launch {
            try {
                val prefix = form.name.trim().ifBlank { defaultNamePrefix }
                val plots = if (form.scope == PlotScope.ENTIRE_PARCEL) {
                    listOf(Placette(
                        id = UUID.randomUUID().toString(), parcelleId = parcelleId,
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
                            id = UUID.randomUUID().toString(), parcelleId = parcelleId,
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
    val canGenerate: Boolean get() = scope == PlotScope.AUTOMATIC_GRID && !isLoading && !isSaving &&
        parent != null && parent.deletedAt == null && geometry != null && cellAreaHa != null && footprintAreaM2 != null && rotationDeg != null
    val isValid: Boolean get() = !isLoading && !isGenerating && parent != null && parent.deletedAt == null && geometry != null &&
        when (scope) {
            PlotScope.ENTIRE_PARCEL -> wholeAreaM2 != null
            PlotScope.MAP_POINTS -> footprintAreaM2 != null && rotationDeg != null && points.size in 1..PlotSamplingGeometry.MAX_PLOTS && footprints.size == points.size
            PlotScope.AUTOMATIC_GRID -> gridPreviewReady && points.size in 1..PlotSamplingGeometry.MAX_PLOTS && footprints.size == points.size
        }
    private fun decimal(value: String): Double? = value.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
}
