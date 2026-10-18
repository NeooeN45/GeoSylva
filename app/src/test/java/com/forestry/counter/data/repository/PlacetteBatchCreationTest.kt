package com.forestry.counter.data.repository

import com.forestry.counter.data.local.dao.PlacetteDao
import com.forestry.counter.data.local.entity.PlacetteEntity
import com.forestry.counter.data.mapper.toParcelleEntity
import com.forestry.counter.data.service.MetadataService
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import com.forestry.counter.domain.location.ParcelCoordinate
import com.forestry.counter.domain.location.ParcelGeometry
import com.forestry.counter.domain.location.PlotSamplingGeometry
import com.forestry.counter.domain.location.PlotSamplingPlan
import com.forestry.counter.domain.location.SampleFootprintShape

/** DAO doubles qualify validation/metadata only, not SQLite transaction atomicity. */
class PlacetteBatchCreationTest {
    private val dao = mockk<PlacetteDao>()
    private val written = mutableListOf<PlacetteEntity>()
    private var parent = parcelFixture()
    private var links = emptyList<String>()
    private var authorReads = 0
    private var account: String? = "fixture-account-a"
    private val repository = PlacetteRepositoryImpl(dao, MetadataService {
        authorReads++
        account
    })

    init {
        coEvery { dao.getLiveParentForCreation("fixture-parent") } coAnswers {
            parent.takeIf { it.deletedAt == null }?.toParcelleEntity()
        }
        coEvery { dao.getLinkedAccountsForCreation("fixture-parent") } coAnswers { links }
        coEvery { dao.withPlacetteCreationTransaction(any()) } coAnswers { firstArg<suspend () -> Unit>().invoke() }
        coEvery { dao.insertNewPlacettes(any()) } coAnswers { written.addAll(firstArg()) }
    }

    @Test fun `batch snapshots one author for all points`() = runTest {
        repository.insertPlacettesForParcelle(listOf(point("fixture-one"), point("fixture-two", "POINT (2.6 46.6)")))
        assertTrue(authorReads >= 2) // Pure identity checks are separate from the one metadata snapshot.
        assertEquals(2, written.size)
        assertEquals(setOf("fixture-account-a"), written.map { it.auteur }.toSet())
        assertEquals(setOf("manual"), written.map { it.source }.toSet())
    }

    @Test fun `whole parcel preserves parent surface and never stores a polygon as centre`() = runTest {
        repository.insertPlacettesForParcelle(listOf(whole()))
        val plot = written.single()
        assertEquals(12_500.0, requireNotNull(plot.surfaceM2), 0.000001)
        assertNull(plot.rayonM)
        assertNull(plot.centerWkt)
    }

    @Test fun `another account linked to parent prevents all writes`() = runTest {
        links = listOf("fixture-account-b")
        assertTrue(runCatching { repository.insertPlacettesForParcelle(listOf(whole())) }.isFailure)
        assertTrue(written.isEmpty())
    }

    @Test fun `account change before transaction prevents a stale batch`() = runTest {
        coEvery { dao.withPlacetteCreationTransaction(any()) } coAnswers {
            account = "fixture-account-b"
            firstArg<suspend () -> Unit>().invoke()
        }
        assertTrue(runCatching { repository.insertPlacettesForParcelle(listOf(whole())) }.isFailure)
        assertTrue(written.isEmpty())
    }

    @Test fun `deleted parent prevents all writes`() = runTest {
        parent = parent.copy(deletedAt = 100L)
        assertTrue(runCatching { repository.insertPlacettesForParcelle(listOf(whole())) }.isFailure)
        assertTrue(written.isEmpty())
    }

    @Test fun `changed whole parent area prevents stale form insertion`() = runTest {
        parent = parent.copy(surfaceHa = 2.0)
        assertTrue(runCatching { repository.insertPlacettesForParcelle(listOf(whole())) }.isFailure)
        assertTrue(written.isEmpty())
    }

    @Test fun `outside point or polygon centre prevents any batch write`() = runTest {
        for (centre in listOf("POINT (6 46)", "POLYGON ((2 46, 3 46, 3 47, 2 47, 2 46))", "POINT (NaN 46)")) {
            assertTrue(runCatching {
                repository.insertPlacettesForParcelle(listOf(point("fixture-one"), point("fixture-two", centre)))
            }.isFailure)
        }
        assertTrue(written.isEmpty())
    }

    @Test fun `duplicate ids or mixed parents are refused before insert`() = runTest {
        assertTrue(runCatching { repository.insertPlacettesForParcelle(listOf(point("fixture-one"), point("fixture-one"))) }.isFailure)
        assertTrue(runCatching { repository.insertPlacettesForParcelle(listOf(point("fixture-one"), point("fixture-two").copy(parcelleId = "other-parent"))) }.isFailure)
        assertTrue(written.isEmpty())
    }

    @Test fun `whole survey with a radius or mismatching circular area is refused`() = runTest {
        assertTrue(runCatching { repository.insertPlacettesForParcelle(listOf(whole().copy(rayonM = 10.0))) }.isFailure)
        assertTrue(runCatching { repository.insertPlacettesForParcelle(listOf(point("fixture-one").copy(surfaceM2 = 12_500.0))) }.isFailure)
        assertTrue(written.isEmpty())
    }

    @Test fun `hexagonal batch retains plan and footprint without inventing a radius`() = runTest {
        val hex = plannedPlot(SampleFootprintShape.HEXAGON)
        repository.insertPlacettesForParcelle(listOf(hex))
        val result = written.single()
        assertEquals("HEX", result.type)
        assertNull(result.rayonM)
        assertEquals(hex.samplingPlanJson, result.samplingPlanJson)
        assertEquals(hex.footprintGeoJson, result.footprintGeoJson)
    }

    @Test fun `planned footprint fields must agree with target area centre shape and boundary warning`() = runTest {
        val plot = plannedPlot(SampleFootprintShape.HEXAGON)
        val encoded = requireNotNull(plot.samplingPlanJson)
        val plan = PlotSamplingPlan.fromJson(encoded)
        val variants = listOf(
            plot.copy(samplingPlanJson = null),
            plot.copy(footprintGeoJson = null),
            plot.copy(samplingPlanJson = plan.copy(footprintAreaM2 = 800.0).toJson()),
            plot.copy(samplingPlanJson = plan.copy(footprintShape = "CIRCLE").toJson()),
            plot.copy(samplingPlanJson = plan.copy(boundaryWarning = !plan.boundaryWarning).toJson()),
            plot.copy(footprintGeoJson = PlotSamplingGeometry.polygon(ParcelCoordinate(2.4, 46.4), SampleFootprintShape.HEXAGON, 500.0).toGeoJson()),
            plot.copy(rayonM = 10.0),
        )
        variants.forEach { invalid ->
            assertTrue(runCatching { repository.insertPlacettesForParcelle(listOf(invalid)) }.isFailure)
        }
        assertTrue(written.isEmpty())
    }

    @Test fun `manual border footprint is retained with warning while false full inclusion is refused`() = runTest {
        val border = plannedPlot(SampleFootprintShape.CIRCLE, ParcelCoordinate(2.000001, 46.3))
        val plan = PlotSamplingPlan.fromJson(requireNotNull(border.samplingPlanJson))
        assertTrue(plan.boundaryWarning)
        repository.insertPlacettesForParcelle(listOf(border))
        assertEquals(1, written.size)
        assertTrue(runCatching {
            repository.insertPlacettesForParcelle(listOf(border.copy(id = "fixture-false-full",
                samplingPlanJson = plan.copy(boundaryPolicy = "FULLY_INSIDE", boundaryWarning = false).toJson())))
        }.isFailure)
        assertEquals(1, written.size)
    }

    @Test fun `a stale preview whose parent geometry changed cannot silently keep its old boundary warning`() = runTest {
        val plot = plannedPlot(SampleFootprintShape.CIRCLE)
        parent = parent.copy(shape = "POLYGON ((2.299999 46.299999, 2.301 46.299999, 2.301 46.301, 2.299999 46.301, 2.299999 46.299999))")
        assertTrue(runCatching { repository.insertPlacettesForParcelle(listOf(plot)) }.isFailure)
        assertTrue(written.isEmpty())
    }

    private fun plannedPlot(shape: SampleFootprintShape, center: ParcelCoordinate = ParcelCoordinate(2.3, 46.3)): Placette {
        val footprint = PlotSamplingGeometry.footprints(ParcelGeometry.fromWkt(requireNotNull(parent.shape)), listOf(center), shape, 500.0).single()
        return Placette(id = "fixture-plan", parcelleId = "fixture-parent", name = "Fixture planifiée",
            type = if (shape == SampleFootprintShape.CIRCLE) "CIRC" else "HEX",
            rayonM = if (shape == SampleFootprintShape.CIRCLE) kotlin.math.sqrt(500.0 / PI) else null,
            surfaceM2 = 500.0, centerWkt = "POINT (${center.longitude} ${center.latitude})",
            samplingPlanJson = PlotSamplingPlan(placement = "MANUAL", footprintShape = shape.name,
                footprintAreaM2 = 500.0, boundaryWarning = footprint.crossesBoundary).toJson(),
            footprintGeoJson = footprint.geometry.toGeoJson())
    }

    private fun point(id: String, centre: String = "POINT (2.3 46.3)") = Placette(
        id = id, parcelleId = "fixture-parent", name = "Fixture", type = "CIRC",
        rayonM = 10.0, surfaceM2 = PI * 100.0, centerWkt = centre,
    )

    private fun whole() = Placette(
        id = "fixture-whole", parcelleId = "fixture-parent", name = "Fixture entière",
        type = "ENTIRE_PARCEL", rayonM = null, surfaceM2 = 12_500.0, centerWkt = null,
    )

    private fun parcelFixture() = Parcelle(
        id = "fixture-parent", forestId = null, name = "Parcelle synthétique",
        surfaceHa = 1.25, shape = "POLYGON ((2 46, 3 46, 3 47, 2 47, 2 46))",
        slopePct = null, aspect = null, access = null, altitudeM = null,
        objectifType = null, objectifVal = null, tolerancePct = null,
        samplingMode = null, sampleAreaM2 = null, targetSpeciesCsv = null,
        srid = 4326, remarks = null,
    )
}
