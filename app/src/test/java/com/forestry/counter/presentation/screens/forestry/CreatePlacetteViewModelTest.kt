package com.forestry.counter.presentation.screens.forestry

import androidx.lifecycle.ViewModelStore
import com.forestry.counter.domain.location.ParcelCoordinate
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.repository.ParcelleRepository
import com.forestry.counter.domain.repository.PlacetteRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.PI
import com.forestry.counter.domain.location.PlotSamplingPlan
import com.forestry.counter.domain.location.SampleFootprintShape
import com.forestry.counter.domain.location.ParcelGeometry

/** Synthetic geometry fixtures, no network or device/production database. */
@OptIn(ExperimentalCoroutinesApi::class)
class CreatePlacetteViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val parent = MutableStateFlow<Parcelle?>(parcelFixture())
    private val parcels = mockk<ParcelleRepository>()
    private val plots = mockk<PlacetteRepository>()
    private val batches = mutableListOf<List<Placette>>()
    private lateinit var viewModel: CreatePlacetteViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        every { parcels.getParcelleById("fixture-parcel") } returns parent
        coEvery { plots.insertPlacettesForParcelle(any()) } coAnswers {
            batches.add(firstArg<List<Placette>>())
        }
        viewModel = CreatePlacetteViewModel(plots, "fixture-parcel", parcels, dispatcher)
    }

    @After fun cleanup() {
        ViewModelStore().apply { put("plot-wizard", viewModel); clear() }
        Dispatchers.resetMain()
    }

    @Test fun `entire parcel creates one survey with parent area and no radius or point`() = runTest(dispatcher) {
        runCurrent()
        viewModel.updateName("Fixture complète")
        viewModel.save("Fixture")
        runCurrent()
        val plot = batches.single().single()
        assertEquals("ENTIRE_PARCEL", plot.type)
        assertEquals(12_500.0, requireNotNull(plot.surfaceM2), 0.000001)
        assertNull(plot.rayonM)
        assertNull(plot.centerWkt)
        assertEquals("fixture-parcel", plot.parcelleId)
        assertTrue(viewModel.isSaved.value)
    }

    @Test fun `map points remain only in form until one complete batch is saved`() = runTest(dispatcher) {
        runCurrent()
        viewModel.updateScope(PlotScope.MAP_POINTS)
        viewModel.updateRayonM("12,5")
        viewModel.addPoint(ParcelCoordinate(2.2, 46.2))
        viewModel.addPoint(ParcelCoordinate(2.8, 46.8))
        assertTrue(batches.isEmpty())
        viewModel.save("Plot fixture")
        runCurrent()
        val created = batches.single()
        assertEquals(2, created.size)
        assertEquals(2, created.map { it.id }.distinct().size)
        assertEquals(listOf("Plot fixture 1", "Plot fixture 2"), created.map { it.name })
        assertEquals(listOf("POINT (2.2 46.2)", "POINT (2.8 46.8)"), created.map { it.centerWkt })
        created.forEach { assertEquals(PI * 12.5 * 12.5, requireNotNull(it.surfaceM2), 0.000001) }
    }

    @Test fun `outside point is refused and never becomes a plot`() = runTest(dispatcher) {
        runCurrent()
        viewModel.updateScope(PlotScope.MAP_POINTS)
        viewModel.updateRayonM("10")
        viewModel.addPoint(ParcelCoordinate(4.0, 46.2))
        assertTrue(viewModel.formState.value.points.isEmpty())
        assertEquals(PlotCreationError.POINT_OUTSIDE, viewModel.formState.value.error)
        viewModel.save()
        runCurrent()
        assertTrue(batches.isEmpty())
    }

    @Test fun `removing a point and cancelling the form do not persist any data`() = runTest(dispatcher) {
        runCurrent()
        viewModel.updateScope(PlotScope.MAP_POINTS)
        viewModel.updateRayonM("10")
        viewModel.addPoint(ParcelCoordinate(2.2, 46.2))
        viewModel.removePoint(0)
        assertTrue(viewModel.formState.value.points.isEmpty())
        assertTrue(batches.isEmpty())
        assertFalse(viewModel.isSaved.value)
    }

    @Test fun `double save is blocked before coroutine execution and during insertion`() = runTest(dispatcher) {
        runCurrent()
        val finish = CompletableDeferred<Unit>()
        coEvery { plots.insertPlacettesForParcelle(any()) } coAnswers {
            batches.add(firstArg<List<Placette>>())
            finish.await()
        }
        viewModel.save()
        viewModel.save()
        assertTrue(viewModel.formState.value.isSaving)
        runCurrent()
        assertEquals(1, batches.size)
        assertFalse(viewModel.isSaved.value)
        viewModel.save()
        finish.complete(Unit)
        runCurrent()
        viewModel.save()
        runCurrent()
        assertEquals(1, batches.size)
        assertTrue(viewModel.isSaved.value)
    }

    @Test fun `failed batch keeps selected points for correction and retry`() = runTest(dispatcher) {
        runCurrent()
        coEvery { plots.insertPlacettesForParcelle(any()) } throws IllegalStateException("fixture failure")
        viewModel.updateScope(PlotScope.MAP_POINTS)
        viewModel.updateRayonM("10")
        val point = ParcelCoordinate(2.3, 46.3)
        viewModel.addPoint(point)
        viewModel.save()
        runCurrent()
        assertEquals(listOf(point), viewModel.formState.value.points)
        assertEquals(PlotCreationError.SAVE_FAILED, viewModel.formState.value.error)
        assertFalse(viewModel.formState.value.isSaving)
        assertFalse(viewModel.isSaved.value)
    }

    @Test fun `unknown or nonfinite parcel area cannot create an entire parcel survey`() = runTest(dispatcher) {
        for (area in listOf(null, -1.0, 0.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            parent.value = parcelFixture().copy(surfaceHa = area)
            runCurrent()
            assertFalse(viewModel.formState.value.isValid)
            viewModel.save()
            runCurrent()
        }
        assertTrue(batches.isEmpty())
    }

    @Test fun `missing invalid or projected boundary disables both modes`() = runTest(dispatcher) {
        for (fixture in listOf(parcelFixture().copy(shape = null), parcelFixture().copy(shape = "POINT (2 46)"),
            parcelFixture().copy(srid = 2154), parcelFixture().copy(deletedAt = 100L))) {
            parent.value = fixture
            runCurrent()
            viewModel.updateScope(PlotScope.ENTIRE_PARCEL)
            viewModel.save()
            viewModel.updateScope(PlotScope.MAP_POINTS)
            viewModel.updateRayonM("10")
            viewModel.addPoint(ParcelCoordinate(2.3, 46.3))
            viewModel.save()
            runCurrent()
            assertFalse(viewModel.formState.value.isValid)
        }
        assertTrue(batches.isEmpty())
    }

    @Test fun `invalid radius cannot save points and comma decimal is retained`() = runTest(dispatcher) {
        runCurrent()
        viewModel.updateScope(PlotScope.MAP_POINTS)
        viewModel.addPoint(ParcelCoordinate(2.3, 46.3))
        for (radius in listOf("", "0", "-10", "NaN", "Infinity", "1e309")) {
            viewModel.updateRayonM(radius)
            viewModel.save()
            runCurrent()
            assertFalse(viewModel.formState.value.isValid)
        }
        assertTrue(batches.isEmpty())
        viewModel.updateRayonM("10,5")
        assertEquals(10.5, requireNotNull(viewModel.formState.value.rayonM), 0.000001)
    }

    @Test fun `automatic preview creates nothing until confirmation and separates hectare density from footprint`() = runTest(dispatcher) {
        parent.value = smallParcelFixture()
        runCurrent()
        viewModel.updateScope(PlotScope.AUTOMATIC_GRID)
        viewModel.updateCellAreaHa("1")
        viewModel.updateFootprintArea("500")
        viewModel.generateGrid()
        runCurrent()
        val preview = viewModel.formState.value
        assertTrue(preview.gridPreviewReady)
        assertTrue(preview.points.size > 1)
        assertTrue(batches.isEmpty())
        viewModel.save("Plan")
        runCurrent()
        val created = batches.single()
        assertEquals(preview.points.size, created.size)
        assertEquals(created.size, created.map { it.id }.distinct().size)
        created.forEach { plot ->
            assertEquals(500.0, requireNotNull(plot.surfaceM2), 1e-8)
            val plan = PlotSamplingPlan.fromJson(requireNotNull(plot.samplingPlanJson))
            assertEquals("AUTO", plan.placement)
            assertEquals(1.0, requireNotNull(plan.cellAreaHa), 1e-8)
            assertEquals(500.0, plan.footprintAreaM2, 1e-8)
            assertEquals("CENTER_INSIDE", plan.boundaryPolicy)
            ParcelGeometry.fromGeoJson(requireNotNull(plot.footprintGeoJson))
        }
    }

    @Test fun `parameter changes invalidate the automatic draft and require another preview`() = runTest(dispatcher) {
        parent.value = smallParcelFixture()
        runCurrent()
        viewModel.updateScope(PlotScope.AUTOMATIC_GRID)
        viewModel.generateGrid()
        runCurrent()
        assertTrue(viewModel.formState.value.isValid)
        viewModel.updateCellAreaHa("2")
        assertFalse(viewModel.formState.value.gridPreviewReady)
        assertTrue(viewModel.formState.value.points.isEmpty())
        viewModel.save()
        runCurrent()
        assertTrue(batches.isEmpty())
        viewModel.generateGrid()
        viewModel.updateRotationDeg("30") // Invalidates even an in-flight computation.
        runCurrent()
        assertTrue(viewModel.formState.value.points.isEmpty())
        assertFalse(viewModel.formState.value.gridPreviewReady)
    }

    @Test fun `hexagonal manual plots retain target area and no invented radius`() = runTest(dispatcher) {
        runCurrent()
        viewModel.updateScope(PlotScope.MAP_POINTS)
        viewModel.updateFootprintShape(SampleFootprintShape.HEXAGON)
        viewModel.updateFootprintArea("1 000")
        assertFalse(viewModel.formState.value.isValid)
        viewModel.updateFootprintArea("1000")
        viewModel.addPoint(ParcelCoordinate(2.3, 46.3))
        viewModel.save("Hex")
        runCurrent()
        val plot = batches.single().single()
        assertEquals("HEX", plot.type)
        assertNull(plot.rayonM)
        assertEquals(1000.0, requireNotNull(plot.surfaceM2), 1e-8)
        assertEquals("HEXAGON", PlotSamplingPlan.fromJson(requireNotNull(plot.samplingPlanJson)).footprintShape)
    }

    @Test fun `boundary exclusion is explicit and keeps the original parcel and remaining regular centres`() = runTest(dispatcher) {
        parent.value = smallParcelFixture()
        runCurrent()
        viewModel.updateScope(PlotScope.AUTOMATIC_GRID)
        viewModel.updateFootprintArea("10000")
        viewModel.generateGrid()
        runCurrent()
        val original = viewModel.formState.value
        assertTrue(original.edgeWarningCount > 0)
        assertFalse(original.excludeBoundary)
        viewModel.updateExcludeBoundary(true)
        viewModel.generateGrid()
        runCurrent()
        val filtered = viewModel.formState.value
        assertEquals(original.points.size - original.edgeWarningCount, filtered.points.size)
        assertEquals(original.edgeWarningCount, filtered.excludedBoundaryCount)
        assertEquals(0, filtered.edgeWarningCount)
        assertEquals(original.parent?.shape, filtered.parent?.shape)
        assertTrue(filtered.points.all { it in original.points })
    }

    @Test fun `parent boundary changes invalidate a previously generated plan`() = runTest(dispatcher) {
        parent.value = smallParcelFixture()
        runCurrent()
        viewModel.updateScope(PlotScope.AUTOMATIC_GRID)
        viewModel.generateGrid()
        runCurrent()
        assertTrue(viewModel.formState.value.gridPreviewReady)
        parent.value = smallParcelFixture().copy(shape = "POLYGON ((2 46, 2.001 46, 2.001 46.001, 2 46.001, 2 46))")
        runCurrent()
        assertFalse(viewModel.formState.value.gridPreviewReady)
        assertTrue(viewModel.formState.value.points.isEmpty())
    }

    @Test fun `manual edge plot keeps an explicit warning and target area rather than clipping`() = runTest(dispatcher) {
        parent.value = smallParcelFixture()
        runCurrent()
        viewModel.updateScope(PlotScope.MAP_POINTS)
        viewModel.updateRayonM("10")
        viewModel.addPoint(ParcelCoordinate(2.00001, 46.002))
        assertEquals(1, viewModel.formState.value.edgeWarningCount)
        assertTrue(viewModel.formState.value.isValid)
        viewModel.save()
        runCurrent()
        val plan = PlotSamplingPlan.fromJson(requireNotNull(batches.single().single().samplingPlanJson))
        assertTrue(plan.boundaryWarning)
        assertEquals("CENTER_INSIDE", plan.boundaryPolicy)
    }

    private companion object {
        fun smallParcelFixture() = parcelFixture().copy(
            shape = "POLYGON ((2 46, 2.005 46, 2.005 46.005, 2 46.005, 2 46))", surfaceHa = 21.0,
        )
        fun parcelFixture() = Parcelle(
            id = "fixture-parcel", forestId = null, name = "Parcelle synthétique",
            surfaceHa = 1.25, shape = "POLYGON ((2 46, 3 46, 3 47, 2 47, 2 46))",
            slopePct = null, aspect = null, access = null, altitudeM = null,
            objectifType = null, objectifVal = null, tolerancePct = null,
            samplingMode = null, sampleAreaM2 = null, targetSpeciesCsv = null,
            srid = 4326, remarks = null,
        )
    }
}
