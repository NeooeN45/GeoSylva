package com.forestry.counter.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.data.mapper.toParcelleEntity
import com.forestry.counter.data.mapper.toPlacette
import com.forestry.counter.data.repository.PlacetteRepositoryImpl
import com.forestry.counter.data.service.MetadataService
import com.forestry.counter.domain.location.ParcelCoordinate
import com.forestry.counter.domain.location.ParcelGeometry
import com.forestry.counter.domain.location.PlotSamplingGeometry
import com.forestry.counter.domain.location.PlotSamplingPlan
import com.forestry.counter.domain.location.SampleFootprintShape
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real isolated Room/SQLite; never accesses the app database or account fixtures. */
@RunWith(AndroidJUnit4::class)
class PlotSamplingPersistenceDaoTest {
    private lateinit var database: ForestryDatabase
    private lateinit var repository: PlacetteRepositoryImpl

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().context, ForestryDatabase::class.java).build()
        repository = PlacetteRepositoryImpl(database.placetteDao(), MetadataService { "sampling-fixture-account" })
    }
    @After fun cleanup() { database.close() }

    @Test fun planned_hexagons_preserve_density_footprint_and_boundary_policy_round_trip() = runBlocking {
        withTimeout(15_000L) {
            database.parcelleDao().insertParcelle(parcel().toParcelleEntity())
            val plot = plot("sampling-fixture-plot", ParcelCoordinate(2.002, 46.002))
            repository.insertPlacettesForParcelle(listOf(plot))
            val stored = requireNotNull(database.placetteDao().getPlacetteById(plot.id)).toPlacette()
            assertEquals("HEX", stored.type)
            assertNull(stored.rayonM)
            assertEquals(plot.samplingPlanJson, stored.samplingPlanJson)
            assertEquals(plot.footprintGeoJson, stored.footprintGeoJson)
            val plan = PlotSamplingPlan.fromJson(requireNotNull(stored.samplingPlanJson))
            assertEquals(1.0, requireNotNull(plan.cellAreaHa), 1e-9)
            assertEquals(500.0, plan.footprintAreaM2, 1e-9)
            assertEquals("CENTER_INSIDE", plan.boundaryPolicy)
        }
    }

    @Test fun false_boundary_warning_on_second_plot_prevents_every_insert() = runBlocking {
        withTimeout(15_000L) {
            database.parcelleDao().insertParcelle(parcel().toParcelleEntity())
            val first = plot("sampling-fixture-first", ParcelCoordinate(2.002, 46.002))
            val second = plot("sampling-fixture-second", ParcelCoordinate(2.00001, 46.002))
            val plan = PlotSamplingPlan.fromJson(requireNotNull(second.samplingPlanJson))
            assertTrue(plan.boundaryWarning)
            assertTrue(runCatching { repository.insertPlacettesForParcelle(listOf(first,
                second.copy(samplingPlanJson = plan.copy(boundaryWarning = false).toJson()))) }.isFailure)
            assertTrue(database.placetteDao().getAllPlacettesNow().isEmpty())
        }
    }

    private fun plot(id: String, center: ParcelCoordinate): Placette {
        val footprint = PlotSamplingGeometry.footprints(ParcelGeometry.fromWkt(requireNotNull(parcel().shape)), listOf(center), SampleFootprintShape.HEXAGON, 500.0).single()
        return Placette(id = id, parcelleId = "sampling-fixture-parent", name = "Synthetic sampling plot", type = "HEX",
            rayonM = null, surfaceM2 = 500.0, centerWkt = "POINT (${center.longitude} ${center.latitude})",
            samplingPlanJson = PlotSamplingPlan(placement = "AUTO", gridLayout = "HEX", cellAreaHa = 1.0,
                footprintShape = "HEXAGON", footprintAreaM2 = 500.0, boundaryWarning = footprint.crossesBoundary).toJson(),
            footprintGeoJson = footprint.geometry.toGeoJson())
    }
    private fun parcel() = Parcelle(id = "sampling-fixture-parent", forestId = null, name = "Synthetic sampling parcel",
        surfaceHa = 21.0, shape = "POLYGON ((2 46, 2.005 46, 2.005 46.005, 2 46.005, 2 46))",
        slopePct = null, aspect = null, access = null, altitudeM = null, objectifType = null, objectifVal = null,
        tolerancePct = null, samplingMode = null, sampleAreaM2 = null, targetSpeciesCsv = null, srid = 4326, remarks = null)
}
