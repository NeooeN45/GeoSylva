package com.forestry.counter.presentation.screens.forestry

import androidx.lifecycle.ViewModelStore
import com.forestry.counter.data.repository.ParcelMapRepositoryImpl
import com.forestry.counter.domain.location.ForestSearchResult
import com.forestry.counter.domain.location.ParcelBounds
import com.forestry.counter.domain.location.ParcelCoordinate
import com.forestry.counter.domain.location.ParcelGeometry
import com.forestry.counter.domain.location.ParcelMapRepository
import com.forestry.counter.domain.location.ParcelMapResult
import com.forestry.counter.domain.location.ParcelMapSource
import com.forestry.counter.domain.location.SourceParcel
import com.forestry.counter.domain.model.Foret
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.repository.ForetRepository
import com.forestry.counter.domain.repository.ParcelleRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ParcelMapCreationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    private lateinit var forests: Forests
    private lateinit var parcels: Parcels
    private lateinit var maps: Maps
    private lateinit var profession: MutableStateFlow<String?>

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        forests = Forests(forest())
        parcels = Parcels()
        maps = Maps()
        profession = MutableStateFlow("technicien_forestier")
    }

    @After fun cleanup() {
        stores.forEach { it.clear() }
        Dispatchers.resetMain()
    }

    @Test fun `forest polygon frames map without looking up unconfirmed alternatives`() = runTest(dispatcher) {
        val model = model()
        runCurrent()
        assertEquals(FOREST_ID, model.state.value.forest?.foretId)
        assertEquals(ParcelGeometry.fromWkt(FOREST_SHAPE).bounds, model.state.value.initialBounds)
        assertNotNull(model.state.value.parentGeometry)
        assertEquals(0, maps.searches)
        assertNull(model.state.value.selected)
    }

    @Test fun `initial parent repository failure is recoverable without crashing or inserting data`() = runTest(dispatcher) {
        forests.beforeRead = { if (forests.readAttempts == 1) throw IllegalStateException("TEST database unavailable") }
        val model = model()
        runCurrent()
        assertTrue(model.state.value.error)
        assertFalse(model.state.value.saveError)
        assertNull(model.state.value.forest)
        assertNull(model.state.value.initialBounds)
        assertTrue(parcels.attempts.isEmpty())

        model.retry()
        runCurrent()

        assertEquals(2, forests.readAttempts)
        assertFalse(model.state.value.error)
        assertEquals(FOREST_ID, model.state.value.forest?.foretId)
        assertEquals(ParcelGeometry.fromWkt(FOREST_SHAPE).bounds, model.state.value.initialBounds)
        assertTrue(parcels.attempts.isEmpty())
    }

    @Test fun `single parcel selection replaces preceding source object`() = runTest(dispatcher) {
        val model = loaded()
        model.selectAt(POINT_A)
        assertEquals("TEST-A", model.state.value.selected?.id)
        assertEquals("TEST parcel A", model.state.value.name)
        model.selectAt(POINT_B)
        assertEquals("TEST-B", model.state.value.selected?.id)
        assertEquals("TEST parcel B", model.state.value.name)
        model.save()
        runCurrent()
        assertEquals(1, parcels.saved.value.size)
        assertEquals("TEST parcel B", parcels.saved.value.single().name)
    }

    @Test fun `explicitly edited name survives replacement of geometry selection`() = runTest(dispatcher) {
        val model = loaded()
        model.selectAt(POINT_A)
        model.updateName("TEST USER LABEL")
        model.selectAt(POINT_B)
        assertEquals("TEST-B", model.state.value.selected?.id)
        assertEquals("TEST USER LABEL", model.state.value.name)
    }

    @Test fun `selected geometry snapshot remains displayed until explicit reselection of updated source`() = runTest(dispatcher) {
        val model = loaded()
        model.selectAt(POINT_A)
        val original = maps.result.parcels.first()
        val updated = original.copy(
            geometry = ParcelGeometry.fromWkt("POLYGON ((0.001 0.001, 0.014 0.001, 0.014 0.014, 0.001 0.014, 0.001 0.001))"),
            fetchedAt = 456L,
            geometrySha256 = "a".repeat(64),
        )
        maps.result = maps.result.copy(parcels = listOf(updated) + maps.result.parcels.drop(1))
        model.viewportChanged(BOUNDS, 14.0)
        advanceTimeBy(350)
        runCurrent()

        assertEquals(updated.geometry.toGeoJson(), model.state.value.parcels.first().geometry.toGeoJson())
        assertEquals(original.geometry.toGeoJson(), model.state.value.selected?.geometry?.toGeoJson())
        model.featureCollection()
        val displayed = maps.renderedParcels.single { it.id == original.id }
        assertEquals(original.geometry.toGeoJson(), displayed.geometry.toGeoJson())
        assertEquals(original.geometrySha256, displayed.geometrySha256)
        assertEquals(original.provenance, displayed.provenance)

        model.selectAt(POINT_A)
        model.featureCollection()
        assertEquals(updated.geometry.toGeoJson(), maps.renderedParcels.single { it.id == updated.id }.geometry.toGeoJson())
        model.save()
        runCurrent()
        assertEquals(updated.geometry.toWkt(), parcels.saved.value.single().shape)
        assertEquals(updated.provenance, parcels.saved.value.single().source)
    }

    @Test fun `save persists WGS84 snapshot provenance and only genuinely known technical values`() = runTest(dispatcher) {
        val model = loaded()
        model.selectAt(POINT_A)
        model.updateName("  TEST SELECTED PARCEL  ")
        model.save()
        runCurrent()
        val saved = parcels.saved.value.single()
        val selected = maps.result.parcels.first()
        assertEquals(FOREST_ID, saved.foretId)
        assertEquals(FOREST_ID, saved.forestId)
        assertEquals("TEST SELECTED PARCEL", saved.name)
        assertEquals(4326, saved.srid)
        assertEquals(selected.geometry.toWkt(), saved.shape)
        assertEquals(selected.geometry.toWkt(), saved.geometrieIgnWkt)
        assertEquals(selected.geometry.areaSquareMetres / 10_000.0, saved.surfaceHa!!, 1e-12)
        assertEquals(0.0999, saved.contenanceCadastraleHa!!, 1e-12)
        assertEquals("TEST-INSEE", saved.codeInseeCommune)
        assertEquals("TEST commune", saved.nomCommune)
        assertEquals("TEST SECTION", saved.sectionCadastrale)
        assertEquals("TEST 0001", saved.numeroCadastral)
        assertEquals("MAP_CADASTRE", saved.localisationMode)
        assertEquals(selected.provenance, saved.source)
        assertNull(saved.slopePct)
        assertNull(saved.altitudeM)
        assertNull(saved.access)
        assertNull(saved.aspect)
        assertNull(saved.objectifType)
        assertNull(saved.sampleAreaM2)
        assertTrue(model.state.value.saved)
    }

    @Test fun `unknown cadastral contenance remains null while polygon area is known`() = runTest(dispatcher) {
        maps.result = maps.result.copy(parcels = maps.result.parcels.map { it.copy(cadastralAreaSquareMetres = null) })
        val model = loaded()
        model.selectAt(POINT_A)
        model.save()
        runCurrent()
        assertNull(parcels.saved.value.single().contenanceCadastraleHa)
        assertTrue(parcels.saved.value.single().surfaceHa!! > 0.0)
    }

    @Test fun `parent deletion after selection blocks insert using fresh repository state`() = runTest(dispatcher) {
        val model = loaded()
        model.selectAt(POINT_A)
        forests.current = forests.current!!.copy(deletedAt = 42L)
        model.save()
        runCurrent()
        assertTrue(parcels.saved.value.isEmpty())
        assertTrue(parcels.attempts.isEmpty())
        assertFalse(model.state.value.saved)
        assertFalse(model.state.value.saving)
        assertTrue(model.state.value.saveError)
        assertFalse(model.state.value.error)
    }

    @Test fun `parent absence after selection blocks insert`() = runTest(dispatcher) {
        val model = loaded()
        model.selectAt(POINT_A)
        forests.current = null
        model.save()
        runCurrent()
        assertTrue(parcels.attempts.isEmpty())
        assertTrue(model.state.value.saveError)
        assertFalse(model.state.value.error)
    }

    @Test fun `double save and field selection changes are blocked before coroutine starts`() = runTest(dispatcher) {
        val completion = CompletableDeferred<Unit>()
        parcels.beforeInsert = { completion.await() }
        val model = loaded()
        model.selectAt(POINT_A)
        model.save()
        model.save()
        model.selectAt(POINT_B)
        model.updateName("TEST CHANGED")
        model.clearSelection()
        assertTrue(model.state.value.saving)
        assertEquals("TEST-A", model.state.value.selected?.id)
        runCurrent()
        assertEquals(1, parcels.attempts.size)
        assertEquals("TEST parcel A", parcels.attempts.single().name)
        completion.complete(Unit)
        runCurrent()
        model.save()
        runCurrent()
        assertEquals(1, parcels.attempts.size)
        assertEquals(1, parcels.saved.value.size)
    }

    @Test fun `storage error preserves selected draft and retry keeps stable dossier identity`() = runTest(dispatcher) {
        parcels.beforeInsert = { if (parcels.attempts.size == 1) throw IOException("TEST write failure") }
        val model = loaded()
        model.selectAt(POINT_A)
        model.save()
        runCurrent()
        assertTrue(model.state.value.saveError)
        assertFalse(model.state.value.error)
        assertFalse(model.state.value.saving)
        assertFalse(model.state.value.saved)
        assertEquals("TEST-A", model.state.value.selected?.id)
        model.save()
        runCurrent()
        assertEquals(2, parcels.attempts.size)
        assertEquals(parcels.attempts[0].id, parcels.attempts[1].id)
        assertEquals(parcels.attempts[0].shape, parcels.attempts[1].shape)
        assertEquals(1, parcels.saved.value.size)
        assertFalse(model.state.value.saveError)
        assertFalse(model.state.value.error)
        assertTrue(model.state.value.saved)
    }

    @Test fun `storage cancellation becomes neither a success nor a write error`() = runTest(dispatcher) {
        parcels.beforeInsert = { throw CancellationException("TEST cancelled write") }
        val model = loaded()
        model.selectAt(POINT_A)
        model.save()
        runCurrent()
        assertFalse(model.state.value.saved)
        assertFalse(model.state.value.saving)
        assertFalse(model.state.value.saveError)
        assertFalse(model.state.value.error)
        assertTrue(parcels.saved.value.isEmpty())
        assertEquals("TEST-A", model.state.value.selected?.id)
    }

    @Test fun `cartographic retry preserves storage failure until the draft is really saved`() = runTest(dispatcher) {
        parcels.beforeInsert = { if (parcels.attempts.size == 1) throw IOException("TEST write failure") }
        val model = loaded()
        model.selectAt(POINT_A)
        model.save()
        runCurrent()
        assertTrue(model.state.value.saveError)
        assertFalse(model.state.value.error)

        model.retry()
        advanceTimeBy(350)
        runCurrent()

        assertTrue(model.state.value.saveError)
        assertFalse(model.state.value.error)
        assertFalse(model.state.value.saved)
        assertEquals(1, parcels.attempts.size)
        assertTrue(parcels.saved.value.isEmpty())

        model.save()
        runCurrent()
        assertFalse(model.state.value.saveError)
        assertTrue(model.state.value.saved)
        assertEquals(2, parcels.attempts.size)
        assertEquals(parcels.attempts[0].id, parcels.attempts[1].id)
        assertEquals(1, parcels.saved.value.size)
    }

    @Test fun `point outside confirmed forest cannot select even a provider parcel containing point`() = runTest(dispatcher) {
        val outside = sourceParcel("TEST-OUTSIDE", "TEST outside", "POLYGON ((0.04 0.04, 0.05 0.04, 0.05 0.05, 0.04 0.05, 0.04 0.04))")
        maps.result = maps.result.copy(parcels = listOf(outside))
        val model = loaded()
        model.selectAt(ParcelCoordinate(0.045, 0.045))
        assertNull(model.state.value.selected)
        assertTrue(model.state.value.outsideForest)
        model.save()
        runCurrent()
        assertTrue(parcels.attempts.isEmpty())
    }

    @Test fun `overlapping provider candidates require disambiguation rather than arbitrary first`() = runTest(dispatcher) {
        maps.result = maps.result.copy(parcels = listOf(maps.result.parcels.first(), maps.result.parcels.first().copy(id = "TEST-DUPLICATE-GEOMETRY")))
        val model = loaded()
        model.selectAt(POINT_A)
        assertNull(model.state.value.selected)
        model.save()
        runCurrent()
        assertTrue(parcels.attempts.isEmpty())
    }

    @Test fun `unqualified ONF provider cannot be enabled even for ONF profession`() = runTest(dispatcher) {
        profession.value = "forestier_onf"
        val model = loaded()
        assertTrue(model.state.value.onfProfession)
        assertFalse(model.sourceAvailable(ParcelMapSource.ONF))
        model.changeSource(ParcelMapSource.ONF)
        assertEquals(ParcelMapSource.CADASTRE, model.state.value.source)
    }

    @Test fun `qualified ONF source remains unavailable to other professions`() = runTest(dispatcher) {
        maps.available += ParcelMapSource.ONF
        val model = loaded()
        assertFalse(model.state.value.onfProfession)
        assertFalse(model.sourceAvailable(ParcelMapSource.ONF))
        model.changeSource(ParcelMapSource.ONF)
        assertEquals(ParcelMapSource.CADASTRE, model.state.value.source)
    }

    @Test fun `qualified ONF source can replace cadastre for explicit ONF profession`() = runTest(dispatcher) {
        profession.value = "forestier_onf"
        maps.available += ParcelMapSource.ONF
        val model = loaded()
        model.selectAt(POINT_A)
        assertTrue(model.sourceAvailable(ParcelMapSource.ONF))
        model.changeSource(ParcelMapSource.ONF)
        assertEquals(ParcelMapSource.ONF, model.state.value.source)
        assertNull(model.state.value.selected)
        advanceTimeBy(350)
        runCurrent()
        assertEquals(ParcelMapSource.ONF, maps.requestedSources.last())
    }

    @Test fun `ONF overlay keeps cadastre loaded but an overlapping click selects only active source`() = runTest(dispatcher) {
        profession.value = "forestier_onf"
        maps.available += ParcelMapSource.ONF
        val cadastre = maps.result.parcels.first()
        val onf = cadastre.copy(id = "TESTONF:168", label = "TEST ONF 168", source = ParcelMapSource.ONF)
        maps.onfResult = ParcelMapResult(listOf(onf), false, 0)
        val model = loaded()
        model.changeSource(ParcelMapSource.ONF)
        advanceTimeBy(350)
        runCurrent()

        assertEquals(listOf(ParcelMapSource.CADASTRE, ParcelMapSource.ONF), maps.requestedSources.takeLast(2))
        assertTrue(model.state.value.parcels.any { it.source == ParcelMapSource.CADASTRE })
        assertTrue(model.state.value.parcels.any { it.source == ParcelMapSource.ONF })
        model.selectAt(POINT_A)
        assertEquals(onf.id, model.state.value.selected?.id)
        assertEquals(ParcelMapSource.ONF, model.state.value.selected?.source)

        model.changeSource(ParcelMapSource.CADASTRE)
        advanceTimeBy(350)
        runCurrent()
        assertNull(model.state.value.selected)
        model.selectAt(POINT_A)
        assertEquals(cadastre.id, model.state.value.selected?.id)
        assertEquals(ParcelMapSource.CADASTRE, model.state.value.selected?.source)
    }

    @Test fun `saving actual ONF parcel uses Lambert93 source area without inventing cadastral or terrain data`() = runTest(dispatcher) {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/geography/onf-parcels-chize-2026-10-03.json")).use { it.readBytes() }
        val onf = ParcelMapRepositoryImpl.parseOnf(bytes.toString(Charsets.UTF_8), 456L).parcels.single { it.id == "F09793Q:168" }
        forests.current = forest().copy(shape = "POLYGON ((-0.4684946 46.08871418, -0.34317899 46.08871418, -0.34317899 46.18568061, -0.4684946 46.18568061, -0.4684946 46.08871418))")
        profession.value = "forestier_onf"
        maps.available += ParcelMapSource.ONF
        maps.onfResult = ParcelMapResult(listOf(onf), false, 0)
        val model = model()
        runCurrent()
        model.viewportChanged(ParcelBounds(-0.407, 46.123, -0.389, 46.136), 14.0)
        model.changeSource(ParcelMapSource.ONF)
        advanceTimeBy(350)
        runCurrent()
        model.selectAt(ParcelCoordinate(-0.3965089511604008, 46.132814829012005))
        assertEquals(onf.id, model.state.value.selected?.id)
        model.save()
        runCurrent()

        val saved = parcels.saved.value.single()
        assertEquals(241_442.859375 / 10_000.0, requireNotNull(saved.surfaceHa), 0.0)
        assertTrue(kotlin.math.abs(requireNotNull(saved.surfaceHa) - onf.geometry.areaSquareMetres / 10_000.0) > 0.0001)
        assertEquals(onf.geometry.toWkt(), saved.shape)
        assertEquals(onf.provenance, saved.source)
        assertEquals("MAP_ONF", saved.localisationMode)
        assertNull(saved.contenanceCadastraleHa)
        assertNull(saved.sectionCadastrale)
        assertNull(saved.numeroCadastral)
        assertNull(saved.slopePct)
        assertNull(saved.altitudeM)
        assertNull(saved.access)
    }

    @Test fun `viewport requires suitable zoom before requesting cadastral objects`() = runTest(dispatcher) {
        val model = model()
        runCurrent()
        model.viewportChanged(BOUNDS, 12.9)
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(model.state.value.needsZoom)
        assertTrue(maps.requestedSources.isEmpty())
        model.viewportChanged(ParcelBounds(0.0, 0.0, 0.2, 0.2), 16.0)
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(maps.requestedSources.isEmpty())
        model.viewportChanged(BOUNDS, 14.0)
        advanceTimeBy(349)
        runCurrent()
        assertTrue(maps.requestedSources.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, maps.requestedSources.size)
        assertFalse(model.state.value.needsZoom)
    }

    @Test fun `provider failure is retryable and does not discard existing confirmed selection`() = runTest(dispatcher) {
        val model = loaded()
        model.selectAt(POINT_A)
        maps.onLoad = { throw IOException("TEST provider unavailable") }
        model.viewportChanged(BOUNDS, 14.0)
        advanceTimeBy(350)
        runCurrent()
        assertTrue(model.state.value.error)
        assertFalse(model.state.value.loading)
        assertEquals("TEST-A", model.state.value.selected?.id)
        maps.onLoad = null
        model.retry()
        advanceTimeBy(350)
        runCurrent()
        assertFalse(model.state.value.error)
        assertEquals(2, model.state.value.parcels.size)
    }

    @Test fun `cleared or unnamed selection cannot be saved`() = runTest(dispatcher) {
        val model = loaded()
        model.selectAt(POINT_A)
        model.updateName("  ")
        model.save()
        runCurrent()
        assertTrue(parcels.attempts.isEmpty())
        model.updateName("TEST NAME")
        model.clearSelection()
        model.save()
        runCurrent()
        assertTrue(parcels.attempts.isEmpty())
    }

    private fun model() = ParcelMapCreationViewModel(parcels, forests, maps, FOREST_ID, profession).also {
        stores += ViewModelStore().apply { put("TEST-parcel-map", it) }
    }

    private fun TestScope.loaded(): ParcelMapCreationViewModel = model().also {
        runCurrent()
        it.viewportChanged(BOUNDS, 14.0)
        advanceTimeBy(350)
        runCurrent()
    }

    private class Forests(var current: Foret?) : ForetRepository {
        var readAttempts = 0
        var beforeRead: suspend (String) -> Unit = {}
        override fun getAll(): Flow<List<Foret>> = MutableStateFlow(listOfNotNull(current))
        override suspend fun getById(id: String): Foret? {
            readAttempts++
            beforeRead(id)
            return current?.takeIf { it.foretId == id }
        }
        override suspend fun insert(foret: Foret) { current = foret }
        override suspend fun update(foret: Foret) { current = foret }
        override suspend fun deleteById(id: String) { current = null }
    }

    private class Parcels : ParcelleRepository {
        val saved = MutableStateFlow<List<Parcelle>>(emptyList())
        val attempts = mutableListOf<Parcelle>()
        var beforeInsert: suspend (Parcelle) -> Unit = {}
        override fun getAllParcelles(): Flow<List<Parcelle>> = saved
        override fun getParcellesByForest(forestId: String) = saved.map { list -> list.filter { it.foretId == forestId } }
        override fun getParcelleById(parcelleId: String) = saved.map { list -> list.singleOrNull { it.id == parcelleId } }
        override suspend fun insertParcelle(parcelle: Parcelle) { attempts += parcelle; beforeInsert(parcelle); saved.value += parcelle }
        override suspend fun updateParcelle(parcelle: Parcelle) { saved.value = saved.value.map { if (it.id == parcelle.id) parcelle else it } }
        override suspend fun deleteParcelle(parcelleId: String) { saved.value = saved.value.filterNot { it.id == parcelleId } }
        override suspend fun deleteAllParcelles() { saved.value = emptyList() }
    }

    private class Maps : ParcelMapRepository {
        var result = ParcelMapResult(listOf(
            sourceParcel("TEST-A", "TEST parcel A", "POLYGON ((0.001 0.001, 0.009 0.001, 0.009 0.009, 0.001 0.009, 0.001 0.001))"),
            sourceParcel("TEST-B", "TEST parcel B", "POLYGON ((0.02 0.02, 0.029 0.02, 0.029 0.029, 0.02 0.029, 0.02 0.02))"),
        ), false, 0)
        var onfResult = ParcelMapResult(emptyList(), false, 0)
        val available = mutableSetOf(ParcelMapSource.CADASTRE)
        val requestedSources = mutableListOf<ParcelMapSource>()
        var renderedParcels: List<SourceParcel> = emptyList()
        var searches = 0
        var onLoad: (suspend () -> ParcelMapResult)? = null
        override suspend fun searchForests(query: String): List<ForestSearchResult> { searches++; return emptyList() }
        override suspend fun loadForests(bounds: ParcelBounds): List<ForestSearchResult> = emptyList()
        override suspend fun loadParcels(bounds: ParcelBounds, source: ParcelMapSource): ParcelMapResult {
            requestedSources += source
            return onLoad?.invoke() ?: if (source == ParcelMapSource.ONF) onfResult else result
        }
        override fun featureCollection(parcels: List<SourceParcel>): String {
            renderedParcels = parcels.toList()
            return "TEST feature collection: " + parcels.joinToString { it.id }
        }
        override fun isAvailable(source: ParcelMapSource) = source in available
    }

    private companion object {
        const val FOREST_ID = "TEST-forest-dossier"
        const val FOREST_SHAPE = "POLYGON ((0 0, 0.03 0, 0.03 0.03, 0 0.03, 0 0))"
        val BOUNDS = ParcelBounds(0.0, 0.0, 0.03, 0.03)
        val POINT_A = ParcelCoordinate(0.005, 0.005)
        val POINT_B = ParcelCoordinate(0.025, 0.025)
        fun forest() = Foret(
            FOREST_ID, "TEST forest", "TEST owner", null, null, null, null, null, null, null, null,
            shape = FOREST_SHAPE,
        )
        fun sourceParcel(id: String, name: String, wkt: String) = SourceParcel(
            id, name, ParcelGeometry.fromWkt(wkt), ParcelMapSource.CADASTRE, "TEST attribution", 123L, "0".repeat(64),
            "TEST-INSEE", "TEST commune", "TEST SECTION", "TEST 0001", 999.0,
        )
    }
}
