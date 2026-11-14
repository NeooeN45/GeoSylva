package com.forestry.counter.domain.usecase.maps

import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.repository.ParcelleRepository
import com.forestry.counter.domain.repository.TigeRepository
import io.mockk.every
import io.mockk.Called
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveMapTreesTest {
    private val trees = mockk<TigeRepository>()
    private val parcels = mockk<ParcelleRepository>()
    private val observe = ObserveMapTrees(trees, parcels)

    @Test fun `legacy keys preserve the five existing map contexts`() {
        assertEquals(MapDataScope.Free, MapDataScope.fromLegacyKey("none"))
        assertEquals(MapDataScope.AllForests, MapDataScope.fromLegacyKey("all"))
        assertEquals(MapDataScope.Forest("f"), MapDataScope.fromLegacyKey("forest_f"))
        assertEquals(MapDataScope.Plot("plot_a"), MapDataScope.fromLegacyKey("placette_plot_a"))
        assertEquals(MapDataScope.Parcel("p"), MapDataScope.fromLegacyKey("p"))
    }

    @Test fun `empty or malformed contexts never become all forests`() = runTest {
        for (key in listOf("", " ", "forest_", "forest_  ", "placette_")) {
            val scope = MapDataScope.fromLegacyKey(key)
            assertEquals(MapDataScope.Invalid, scope)
            assertEquals(MapTreeContent.Unavailable(scope, MapTreeContent.Reason.INVALID_CONTEXT), observe(scope).toList().last())
        }
        verify { trees wasNot Called; parcels wasNot Called }
    }

    @Test fun `free map is confirmed empty without querying business repositories`() = runTest {
        assertEquals(listOf(MapTreeContent.Loading(MapDataScope.Free), MapTreeContent.Ready(MapDataScope.Free, emptyList())),
            observe(MapDataScope.Free).toList())
        verify { trees wasNot Called; parcels wasNot Called }
    }

    @Test fun `all forests uses the authorised repository result without widening it`() = runTest {
        val visible = listOf(tree("visible"))
        every { trees.getAllTiges() } returns flowOf(visible)
        assertEquals(MapTreeContent.Ready(MapDataScope.AllForests, visible), observe(MapDataScope.AllForests).toList().last())
        verify(exactly = 1) { trees.getAllTiges() }
        verify { parcels wasNot Called }
    }

    @Test fun `parcel and plot query only their exact scope`() = runTest {
        every { trees.getTigesByParcelle("p") } returns flowOf(listOf(tree("p-tree")))
        every { trees.getTigesByPlacette("plot") } returns flowOf(emptyList())
        val parcelResult = observe(MapDataScope.Parcel("p")).toList()
        assertTrue(parcelResult.first() is MapTreeContent.Loading)
        assertEquals("p-tree", (parcelResult.last() as MapTreeContent.Ready).trees.single().id)
        assertEquals(MapTreeContent.Ready(MapDataScope.Plot("plot"), emptyList()), observe(MapDataScope.Plot("plot")).toList().last())
        verify(exactly = 0) { trees.getAllTiges() }
    }

    @Test fun `forest combines only its currently visible parcels and avoids duplicate subscriptions`() = runTest {
        every { parcels.getParcellesByForest("f") } returns flowOf(listOf(parcel("a"), parcel("b"), parcel("a")))
        every { trees.getTigesByParcelle("a") } returns flowOf(listOf(tree("ta", "a")))
        every { trees.getTigesByParcelle("b") } returns flowOf(listOf(tree("tb", "b")))
        val result = observe(MapDataScope.Forest("f")).toList().last() as MapTreeContent.Ready
        assertEquals(listOf("ta", "tb"), result.trees.map { it.id })
        verify(exactly = 1) { trees.getTigesByParcelle("a"); trees.getTigesByParcelle("b") }
        verify(exactly = 0) { trees.getAllTiges() }
    }

    @Test fun `forest without a parcel repository fails closed instead of broadening access`() = runTest {
        val scope = MapDataScope.Forest("f")
        assertEquals(MapTreeContent.Unavailable(scope, MapTreeContent.Reason.FOREST_REPOSITORY_REQUIRED),
            ObserveMapTrees(trees, null)(scope).toList().last())
        verify { trees wasNot Called }
    }

    @Test fun `forest membership changes clear old trees while new child results are pending`() = runTest {
        val parents = MutableStateFlow(listOf(parcel("a")))
        val childA = MutableStateFlow(listOf(tree("ta", "a")))
        val childB = MutableSharedFlow<List<Tige>>()
        every { parcels.getParcellesByForest("f") } returns parents
        every { trees.getTigesByParcelle("a") } returns childA
        every { trees.getTigesByParcelle("b") } returns childB
        val results = mutableListOf<MapTreeContent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { observe(MapDataScope.Forest("f")).collect { results += it } }
        testScheduler.runCurrent()
        assertTrue(results.last() is MapTreeContent.Ready)
        parents.value = listOf(parcel("b"))
        testScheduler.runCurrent()
        assertEquals(MapTreeContent.Loading(MapDataScope.Forest("f")), results.last())
        val boundary = results.size
        childA.value = listOf(tree("late-a", "a"))
        childB.emit(listOf(tree("tb", "b")))
        testScheduler.runCurrent()
        assertEquals(listOf("tb"), (results.last() as MapTreeContent.Ready).trees.map { it.id })
        assertFalse(results.drop(boundary).filterIsInstance<MapTreeContent.Ready>().any { state -> state.trees.any { it.id == "late-a" } })
        parents.value = emptyList()
        testScheduler.runCurrent()
        assertEquals(MapTreeContent.Ready(MapDataScope.Forest("f"), emptyList()), results.last())
    }

    @Test fun `parent metadata and ordering changes do not reload trees or interrupt a valid selection`() = runTest {
        val parents = MutableStateFlow(listOf(parcel("a"), parcel("b")))
        every { parcels.getParcellesByForest("f") } returns parents
        every { trees.getTigesByParcelle("a") } returns MutableStateFlow(listOf(tree("ta", "a")))
        every { trees.getTigesByParcelle("b") } returns MutableStateFlow(listOf(tree("tb", "b")))
        val results = mutableListOf<MapTreeContent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { observe(MapDataScope.Forest("f")).collect { results += it } }
        testScheduler.runCurrent()
        assertTrue(results.last() is MapTreeContent.Ready)
        val before = results.size
        parents.value = listOf(parcel("b"), parcel("a")) // new objects, same membership
        testScheduler.runCurrent()
        assertEquals(before, results.size)
        verify(exactly = 1) { trees.getTigesByParcelle("a"); trees.getTigesByParcelle("b") }
    }

    @Test fun `context switches cancel the previous tree subscription and expose loading`() = runTest {
        val contexts = MutableStateFlow<MapDataScope>(MapDataScope.Parcel("a"))
        var cancelled = false
        every { trees.getTigesByParcelle("a") } returns flow {
            try { emit(listOf(tree("ta", "a"))); awaitCancellation() } finally { cancelled = true }
        }
        every { trees.getTigesByParcelle("b") } returns MutableSharedFlow()
        val results = mutableListOf<MapTreeContent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { contexts.flatMapLatest { observe(it) }.collect { results += it } }
        testScheduler.runCurrent()
        contexts.value = MapDataScope.Parcel("b")
        testScheduler.runCurrent()
        assertTrue(cancelled)
        assertEquals(MapTreeContent.Loading(MapDataScope.Parcel("b")), results.last())
    }

    @Test fun `repository failures are distinct from empty data and expose no raw message`() = runTest {
        val scope = MapDataScope.Parcel("p")
        every { trees.getTigesByParcelle("p") } returns flow { throw IllegalStateException("private diagnostic") }
        assertEquals(MapTreeContent.Unavailable(scope, MapTreeContent.Reason.DATA_READ), observe(scope).toList().last())
        every { trees.getTigesByParcelle("p") } throws IllegalStateException("synchronous private diagnostic")
        assertEquals(MapTreeContent.Unavailable(scope, MapTreeContent.Reason.DATA_READ), observe(scope).toList().last())
    }

    @Test fun `cancellation is propagated rather than converted into a read error`() = runTest {
        every { trees.getAllTiges() } returns flow { throw CancellationException("cancel") }
        val error = runCatching { observe(MapDataScope.AllForests).toList() }.exceptionOrNull()
        assertTrue(error is CancellationException)
    }

    @Test fun `forest ignores a canonical foreign parent even when an older group id matches`() = runTest {
        every { parcels.getParcellesByForest("legacy") } returns flowOf(listOf(
            parcel("own",legacy="legacy"),parcel("foreign",canonical="other",legacy="legacy"),
            parcel("canonical",canonical="legacy",legacy="obsolete"),parcel("deleted",legacy="legacy",deleted=1L)))
        every { trees.getTigesByParcelle("own") } returns flowOf(listOf(tree("own-tree","own")))
        every { trees.getTigesByParcelle("canonical") } returns flowOf(listOf(tree("canonical-tree","canonical")))
        val result=observe(MapDataScope.Forest("legacy")).toList().last() as MapTreeContent.Ready
        assertEquals(setOf("own-tree","canonical-tree"),result.trees.map { it.id }.toSet())
        verify(exactly=0) { trees.getTigesByParcelle("foreign");trees.getTigesByParcelle("deleted") }
    }
    private fun parcel(id: String,canonical: String?=null,legacy: String?="f",deleted: Long?=null) = mockk<Parcelle> {
        every { this@mockk.id } returns id;every { foretId } returns canonical
        every { forestId } returns legacy;every { deletedAt } returns deleted
    }
    private fun tree(id: String, parcel: String = "p") = Tige(
        id = id, parcelleId = parcel, placetteId = null, essenceCode = "CHP",
        diamCm = 35.0, hauteurM = null, gpsWkt = null, precisionM = null,
        altitudeM = null, note = null, produit = null, fCoef = null, valueEur = null,
    )
}
