package com.forestry.counter.presentation.screens.forestry

import androidx.lifecycle.ViewModelStore
import com.forestry.counter.data.local.dao.ParcelSyncDao
import com.forestry.counter.data.local.entity.BusinessReadOwner
import com.forestry.counter.data.local.entity.BusinessReadParcelParent
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.domain.model.AccountSession
import com.forestry.counter.domain.model.Foret
import com.forestry.counter.domain.model.IdentityProvider
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.repository.ForetRepository
import com.forestry.counter.domain.repository.IdentityRepository
import com.forestry.counter.domain.repository.IdentitySessionGuard
import com.forestry.counter.domain.repository.ParcelleRepository
import com.forestry.counter.domain.repository.PlacetteRepository
import com.forestry.counter.domain.repository.TigeRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DiagnosticScopeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun ibpRequiresAnExistingPlotAndParcelMethodsNeverPretendToUseIt() = runTest {
        val f = Fixture()
        val model = f.model().also { store.put("diagnostics", it) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
        runCurrent()
        model.chooseParcel("parcel"); runCurrent()
        assertNull(model.resolveTarget(ScopedDiagnosticKind.IBP, model.state.value))
        assertEquals(DiagnosticScopeTarget(ScopedDiagnosticKind.STATION, "parcel", null),
            model.resolveTarget(ScopedDiagnosticKind.STATION, model.state.value))
        model.choosePlot("plot"); runCurrent()
        assertEquals(DiagnosticScopeTarget(ScopedDiagnosticKind.IBP, "parcel", "plot"),
            model.resolveTarget(ScopedDiagnosticKind.IBP, model.state.value))
        assertEquals(DiagnosticScopeTarget(ScopedDiagnosticKind.RIPISYLVE, "parcel", null),
            model.resolveTarget(ScopedDiagnosticKind.RIPISYLVE, model.state.value))
    }

    @Test fun aForeignPlotIsNeitherSelectableNorAcceptedByALateEnvelope() = runTest {
        val f = Fixture()
        val foreign = f.plot.copy(id = "foreign", parcelleId = "other-parcel")
        f.plots.value += foreign
        coEvery { f.plotRepo.getPlacetteById("foreign") } returns MutableStateFlow(foreign)
        val model = f.model().also { store.put("diagnostics", it) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
        runCurrent(); model.chooseParcel("parcel"); runCurrent()
        assertEquals(listOf("plot"), model.state.value.plots.map { it.id })
        model.choosePlot("foreign"); runCurrent()
        assertNull(model.state.value.selectedPlot)
        assertNull(model.resolveTarget(ScopedDiagnosticKind.IBP, model.state.value.copy(selectedPlot = foreign)))
    }

    @Test fun canonicalForestLinkCannotBeReplacedByALegacyGroupMatch() = runTest {
        val f = Fixture()
        f.forests.value += f.forest.copy(foretId = "legacy", nom = "Historical group")
        val model = f.model().also { store.put("diagnostics", it) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
        runCurrent(); model.chooseForest("legacy"); runCurrent()
        assertTrue(model.state.value.parcels.isEmpty())
        model.chooseForest("forest"); runCurrent()
        assertEquals(listOf("parcel"), model.state.value.parcels.map { it.id })
        assertTrue(parcelBelongsToDiagnosticForest(f.parcel.copy(foretId = null), "legacy"))
    }

    @Test fun observationsAreReportedOnlyWhenActualParcelTreesExist() = runTest {
        val f = Fixture()
        val model = f.model().also { store.put("diagnostics", it) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
        runCurrent(); model.chooseParcel("parcel"); runCurrent()
        assertFalse(model.state.value.hasObservations)
        f.trees.value = listOf(f.tree.copy(parcelleId = "other")); runCurrent()
        assertFalse(model.state.value.hasObservations)
        f.trees.value = listOf(f.tree.copy(deletedAt = 3L)); runCurrent()
        assertFalse(model.state.value.hasObservations)
        f.trees.value = listOf(f.tree); runCurrent()
        assertTrue(model.state.value.hasObservations)
    }

    @Test fun anAccountChangeDuringLookupCannotNavigateWithTheOldEnvelope() = runTest {
        val f = Fixture()
        val model = f.model().also { store.put("diagnostics", it) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
        runCurrent(); model.chooseParcel("parcel"); runCurrent(); model.choosePlot("plot"); runCurrent()
        val displayed = model.state.value
        val started = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        every { f.parcelRepo.getParcelleById("parcel") } returns flow {
            started.complete(Unit); resume.await(); emit(f.parcel)
        }
        val pending = async { model.resolveTarget(ScopedDiagnosticKind.IBP, displayed) }
        started.await()
        f.login("B"); runCurrent()
        assertTrue(model.state.value.forests.isEmpty())
        assertTrue(model.state.value.parcels.isEmpty())
        assertNull(model.state.value.selectedPlot)
        resume.complete(Unit)
        assertNull(pending.await())
    }

    @Test fun anEqualAccountReauthenticationRevokesTheDisplayedEnvelope() = runTest {
        val f = Fixture()
        val model = f.model().also { store.put("diagnostics", it) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
        runCurrent(); model.chooseParcel("parcel"); runCurrent(); model.choosePlot("plot"); runCurrent()
        val displayed = model.state.value
        f.login("A"); runCurrent()
        assertEquals("plot", model.state.value.selectedPlot?.id)
        assertNull(model.resolveTarget(ScopedDiagnosticKind.IBP, displayed))
        assertNotNull(model.resolveTarget(ScopedDiagnosticKind.IBP, model.state.value))
    }

    @Test fun movingThePlotAfterSelectionRequiresAnotherSelection() = runTest {
        val f = Fixture()
        val model = f.model().also { store.put("diagnostics", it) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
        runCurrent(); model.chooseParcel("parcel"); runCurrent(); model.choosePlot("plot"); runCurrent()
        val displayed = model.state.value
        coEvery { f.plotRepo.getPlacetteById("plot") } returns MutableStateFlow(f.plot.copy(centerWkt = "POINT(1 2)"))
        assertNull(model.resolveTarget(ScopedDiagnosticKind.IBP, displayed))
    }

    private data class Guard(override val accountId: String, val generation: Long) : IdentitySessionGuard
    private class Fixture {
        val forest = Foret("forest", "Test forest", "", null, null, null, null, null, null, null, null, 1L, 2L)
        val parcel = Parcelle("parcel", "legacy", "forest", "Test parcel", null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, createdAt = 1L, updatedAt = 2L)
        val plot = Placette("plot", "parcel", "Test plot", "CIRC", 10.0, null, "POINT(0 0)", 1L, 2L)
        val tree = Tige(id = "tree", parcelleId = "parcel", placetteId = "plot", essenceCode = "CHP", diamCm = 30.0,
            hauteurM = null, gpsWkt = null, precisionM = null, altitudeM = null,
            note = null, produit = null, fCoef = null, valueEur = null)
        val forests = MutableStateFlow(listOf(forest))
        val parcels = MutableStateFlow(listOf(parcel))
        val plots = MutableStateFlow(listOf(plot))
        val trees = MutableStateFlow<List<Tige>>(emptyList())
        val sessions = MutableStateFlow<AccountSession?>(null)
        val generation = MutableStateFlow(0L)
        val owners = MutableStateFlow(listOf(BusinessReadOwner("FOREST", "forest", "A")))
        val parents = MutableStateFlow(listOf(BusinessReadParcelParent("parcel", "forest", "legacy")))
        val forestRepo = mockk<ForetRepository>()
        val parcelRepo = mockk<ParcelleRepository>()
        val plotRepo = mockk<PlacetteRepository>()
        val treeRepo = mockk<TigeRepository>()
        val identity = mockk<IdentityRepository>()
        val dao = mockk<ParcelSyncDao>()
        val access: BusinessReadAccess
        init {
            every { identity.session } returns sessions
            every { identity.sessionGeneration } returns generation
            every { identity.captureSessionGuard(any()) } answers {
                Result.success(Guard(firstArg(), generation.value))
            }
            every { identity.isSessionGuardCurrent(any()) } answers {
                val guard = firstArg<IdentitySessionGuard>() as Guard
                guard.accountId == sessions.value?.accountId && guard.generation == generation.value
            }
            every { dao.observeBusinessReadOwners() } returns owners
            every { dao.observeBusinessReadParcelParents() } returns parents
            coEvery { dao.getBusinessReadOwners() } coAnswers { owners.value }
            coEvery { dao.getBusinessReadParcelParents() } coAnswers { parents.value }
            coEvery { dao.withParcelMergeTransaction(any()) } coAnswers { firstArg<suspend () -> Unit>().invoke() }
            every { forestRepo.getAll() } returns forests
            coEvery { forestRepo.getById("forest") } returns forest
            every { parcelRepo.getAllParcelles() } returns parcels
            every { parcelRepo.getParcelleById("parcel") } returns MutableStateFlow(parcel)
            every { plotRepo.getPlacettesByParcelle("parcel") } returns plots
            every { plotRepo.getPlacetteById("plot") } returns MutableStateFlow(plot)
            every { treeRepo.getTigesByParcelle("parcel") } returns trees
            access = BusinessReadAccess(identity, dao)
            login("A")
        }
        fun login(id: String) {
            generation.value += 1L
            sessions.value = AccountSession(id, IdentityProvider.LOCAL, emptyList(), 4_000_000_000L)
        }
        fun model() = DiagnosticScopeViewModel(forestRepo, parcelRepo, plotRepo, treeRepo, access)
    }
}
