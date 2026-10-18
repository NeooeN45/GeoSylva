package com.forestry.counter.presentation.screens.forestry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.forestry.counter.data.local.dao.ParcelSyncDao
import com.forestry.counter.data.local.entity.BusinessReadOwner
import com.forestry.counter.data.local.entity.BusinessReadParcelParent
import com.forestry.counter.data.sync.hierarchy.BusinessReadAccess
import com.forestry.counter.domain.calculation.ForestryCalculator
import com.forestry.counter.domain.calculation.ForestrySynthesisParams
import com.forestry.counter.domain.calculation.SynthesisParameters
import com.forestry.counter.domain.calculation.SynthesisReportCalculator
import com.forestry.counter.domain.model.*
import com.forestry.counter.domain.repository.*
import com.forestry.counter.presentation.viewmodel.IdentityRepositoryFake
import com.forestry.counter.presentation.viewmodel.identitySessionFixture
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Repository doubles intentionally keep A's rows: the VM must revoke cached sensitive results. */
@OptIn(ExperimentalCoroutinesApi::class)
class SynthesisReadIsolationTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun `active results disappear on B without changing the observed business rows`() = runTest(dispatcher) {
        val f = fixture(); val vm = f.synthesis(this, closed = false)
        assertEquals("Private parcel", vm.uiState.value.report?.parcelName)
        assertNotNull(vm.uiState.value.dendrometry)
        f.identity.session.value = identitySessionFixture().copy(accountId = "B")
        assertFalse(vm.isReadable(vm.uiState.value))
        runCurrent()
        assertNull(vm.uiState.value.parcel); assertNull(vm.uiState.value.report)
        assertNull(vm.uiState.value.dendrometry)
        assertTrue(vm.uiState.value.observations.isEmpty())
        assertEquals(1, f.parcels.value.size)
    }

    @Test fun `frozen revision and its selected session disappear on logout`() = runTest(dispatcher) {
        val f = fixture(); val vm = f.synthesis(this)
        assertNotNull(vm.uiState.value.snapshot); assertNotNull(vm.uiState.value.report)
        assertNotNull(vm.uiState.value.dendrometry)
        vm.chooseSession("session-A"); runCurrent()
        f.identity.logout(); runCurrent()
        assertNull(vm.uiState.value.snapshot); assertNull(vm.uiState.value.report)
        assertNull(vm.uiState.value.dendrometry)
        assertNull(vm.uiState.value.selection.sessionId); assertTrue(vm.uiState.value.sessions.isEmpty())
    }

    @Test fun `new forest ownership revokes a frozen result without a tree write`() = runTest(dispatcher) {
        val f = fixture(); val vm = f.synthesis(this)
        f.owners.value = listOf(BusinessReadOwner("FOREST", "forest-A", "B")); runCurrent()
        assertNull(vm.uiState.value.snapshot); assertNull(vm.uiState.value.parcel)
        assertTrue(vm.uiState.value.plots.isEmpty())
        assertEquals(1, f.parcels.value.size)
    }

    @Test fun `a child moved below a foreign parent cannot retain the prior frozen report`() = runTest(dispatcher) {
        val f = fixture(); val vm = f.synthesis(this)
        f.owners.value += BusinessReadOwner("FOREST", "forest-B", "B")
        f.parents.value = listOf(BusinessReadParcelParent("parcel-A", "forest-B")); runCurrent()
        assertNull(vm.uiState.value.report); assertNull(vm.uiState.value.snapshot)
    }

    @Test fun `export captured before B or logout cannot be reauthorized for the new actor`() = runTest(dispatcher) {
        val f = fixture(); val vm = f.synthesis(this)
        val original = vm.uiState.value
        val guard = vm.captureExportGuard(original)!!
        f.identity.session.value = identitySessionFixture().copy(accountId = "B")
        assertFalse(guard.isCurrent())
        assertNull(vm.captureExportGuard(original))
        assertTrue(runCatching { guard.requireRevalidated() }.isFailure)
        f.identity.logout(); assertFalse(guard.isCurrent())
    }

    @Test fun `same account relogin does not revive a document captured before the picker`() = runTest(dispatcher) {
        val f = fixture(); val vm = f.synthesis(this)
        val original = vm.uiState.value
        val guard = vm.captureExportGuard(original)!!
        val previousSession = f.identity.session.value
        f.identity.logout(); f.identity.session.value = previousSession
        assertFalse(guard.isCurrent())
        assertNull(vm.captureExportGuard(original))
    }

    @Test fun `same session metadata relogin actively clears and rereads the frozen revision with a new permission`() = runTest(dispatcher) {
        val f = fixture(); val vm = f.synthesis(this)
        val original = vm.uiState.value
        val emissions = mutableListOf<SynthesisUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { emissions += it } }
        emissions.clear()
        val previousSession = f.identity.session.value
        f.identity.logout(); f.identity.session.value = previousSession; runCurrent()
        assertTrue(emissions.any { it.report == null && it.snapshot == null })
        assertNotSame(original.accessSnapshot, vm.uiState.value.accessSnapshot)
        assertNotNull(vm.uiState.value.report)
        assertTrue(vm.isReadable(vm.uiState.value))
        assertFalse(vm.isReadable(original))
    }

    @Test fun `revoked closure is refused before the access observer gets a turn`() = runTest(dispatcher) {
        val f = fixture(); val vm = f.synthesis(this, closed = false)
        assertNotNull(vm.uiState.value.report)
        f.identity.session.value = identitySessionFixture().copy(accountId = "B")
        vm.closeSelectedSession(); runCurrent()
        coVerify(exactly = 0) { f.sessions.closeMartelage(any(), any()) }
    }

    @Test fun `late noncancellable frozen snapshot cannot republish A after B enters`() = runTest(dispatcher) {
        val f = fixture()
        val result = CompletableDeferred<MartelageSnapshotV1?>()
        f.snapshotReader = { withContext(NonCancellable) { result.await() } }
        val vm = f.synthesis(this)
        f.identity.session.value = identitySessionFixture().copy(accountId = "B"); runCurrent()
        result.complete(f.snapshot); runCurrent()
        assertNull(vm.uiState.value.snapshot); assertNull(vm.uiState.value.report)
        assertNull(vm.uiState.value.dendrometry)
        assertNull(vm.uiState.value.parcel)
    }

    @Test fun `closed dendrometry ignores current observations and retains its frozen report`() = runTest(dispatcher) {
        val f = fixture()
        val tree = Tige("later-tree", "parcel-A", null, "TEST", 40.0, 20.0,
            null, null, null, timestamp = 30L, note = null, produit = null, fCoef = null,
            valueEur = null, sessionId = "session-A")
        f.observations.value = listOf(tree)
        val vm = f.synthesis(this)
        val captured = vm.uiState.value.report
        assertEquals(0, vm.uiState.value.dendrometry?.recordedCount)
        assertTrue(vm.uiState.value.dendrometry!!.diameterDistribution.isEmpty())
        f.observations.value = listOf(tree.copy(diamCm = 60.0)); runCurrent()
        assertEquals(captured, vm.uiState.value.report)
        assertEquals(0, vm.uiState.value.dendrometry?.recordedCount)
        assertEquals(2L, vm.uiState.value.dendrometry?.reportGeneratedAt)
        coVerify(exactly = 0) { f.sessions.closeMartelage(any(), any()) }
    }

    @Test fun `invalid frozen calculation has no derived statistics or false zero`() = runTest(dispatcher) {
        val f = fixture()
        f.snapshotReader = { f.snapshot.copy(calculation = f.snapshot.calculation!!.copy(
            sourceFingerprint = "0".repeat(64))) }
        val vm = f.synthesis(this)
        assertTrue(vm.uiState.value.error)
        assertNull(vm.uiState.value.report)
        assertNull(vm.uiState.value.dendrometry)
    }

    @Test fun `forest detail removes owner contact on B and logout without a forest table write`() = runTest(dispatcher) {
        val f = fixture(); val vm = f.detail(this)
        assertEquals("private@example.invalid", (vm.uiState.value as ForestDetailUiState.Success).foret.proprietaireEmail)
        f.identity.session.value = identitySessionFixture().copy(accountId = "B")
        assertFalse(vm.isCurrentState()); runCurrent()
        assertEquals(ForestDetailUiState.NotFound, vm.uiState.value)
        f.identity.logout(); runCurrent()
        assertEquals(ForestDetailUiState.NotFound, vm.uiState.value)
        assertEquals(1, f.forests.value.size)
    }

    @Test fun `forest contact is revoked when an unchanged forest becomes owned by B`() = runTest(dispatcher) {
        val f = fixture(); val vm = f.detail(this)
        f.owners.value = listOf(BusinessReadOwner("FOREST", "forest-A", "B")); runCurrent()
        assertEquals(ForestDetailUiState.NotFound, vm.uiState.value)
    }

    @Test fun `forest detail masks a separately owned child even while forest contacts remain authorized`() = runTest(dispatcher) {
        val f = fixture(); val vm = f.detail(this)
        f.owners.value += BusinessReadOwner("PARCEL", "parcel-A", "B"); runCurrent()
        val state = vm.uiState.value as ForestDetailUiState.Success
        assertEquals("private@example.invalid", state.foret.proprietaireEmail)
        assertTrue(state.parcelles.isEmpty())
    }

    @Test fun `same account opaque relogin rejects a cached forest edit before collector resumes`() = runTest(dispatcher) {
        val f = fixture(); val vm = f.detail(this)
        val original = (vm.uiState.value as ForestDetailUiState.Success).foret
        val session = f.identity.session.value
        f.identity.logout(); f.identity.session.value = session
        assertFalse(vm.isCurrentState())
        vm.updateForet(original.copy(remarques = "must not write")); vm.deleteForet(); runCurrent()
        coVerify(exactly = 0) { f.forestRepository.update(any()) }
        coVerify(exactly = 0) { f.forestRepository.deleteById(any()) }
    }

    @Test fun `same session metadata relogin actively removes contacts before a newly authorized detail read`() = runTest(dispatcher) {
        val f = fixture(); val vm = f.detail(this)
        val emissions = mutableListOf<ForestDetailUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { emissions += it } }
        emissions.clear()
        val previousSession = f.identity.session.value
        val capturedBeforeReauthentication = vm.uiState.value
        f.identity.logout(); f.identity.session.value = previousSession; runCurrent()
        assertFalse(vm.isCurrentState(capturedBeforeReauthentication))
        assertTrue(emissions.contains(ForestDetailUiState.NotFound))
        assertEquals("private@example.invalid", (vm.uiState.value as ForestDetailUiState.Success).foret.proprietaireEmail)
        assertTrue(vm.isCurrentState())
    }

    private fun fixture() = Fixture()
    private inner class Fixture {
        val identity = IdentityRepositoryFake().apply { session.value = identitySessionFixture().copy(accountId = "A") }
        val owners = MutableStateFlow(listOf(BusinessReadOwner("FOREST", "forest-A", "A")))
        val parents = MutableStateFlow(listOf(BusinessReadParcelParent("parcel-A", "forest-A")))
        val dao = mockk<ParcelSyncDao>()
        val access: BusinessReadAccess
        val parcel = Parcelle("parcel-A", "forest-A", null, "Private parcel", 1.0, null, null, null, null, null,
            null, null, null, null, null, null, null, null, createdAt = 1L, updatedAt = 2L)
        val forest = Foret("forest-A", "Private forest", "Owner A", "private@example.invalid", null, null,
            null, null, null, null, null, createdAt = 1L, updatedAt = 2L)
        val parcels = MutableStateFlow(listOf(parcel))
        val observations = MutableStateFlow(emptyList<Tige>())
        val forests = MutableStateFlow(listOf(forest))
        val forestRepository = mockk<ForetRepository>(relaxed = true)
        val parcelRepository = mockk<ParcelleRepository>()
        val sessions = mockk<InventaireSessionRepository>(relaxed = true)
        val calculator = mockk<ForestryCalculator>()
        val session = InventaireSession("session-A", "parcel-A", MartelageStates.TYPE, 1L, 2L, null, null, null,
            null, null, martelageState = MartelageStates.CLOSED, campaignId = "campaign-A")
        val report = SynthesisReportCalculator.calculate(parcel, emptyList(), emptyList(), emptyMap(),
            SynthesisParameters(), "session-A", "campaign-A", 1, 2L)
        val snapshot = MartelageSnapshotV1(sessionId = "session-A", parcelleId = "parcel-A", campaignId = "campaign-A",
            revisionNumber = 1, previousSessionId = null, startedAt = 1L, closedAt = 2L, activeDurationMs = 1L,
            auteur = "A", parcelle = parcel, placettes = emptyList(), tiges = emptyList(),
            calculation = MartelageCalculationSnapshotV1(report.generatedAt, report.engineVersion,
                Json.encodeToJsonElement(SynthesisParameters()).jsonObject, Json.encodeToJsonElement(report).jsonObject,
                MartelageSnapshotFingerprint.compute(parcel, emptyList(), emptyList())))
        var snapshotReader: suspend () -> MartelageSnapshotV1? = { snapshot }
        init {
            every { dao.observeBusinessReadOwners() } returns owners
            every { dao.observeBusinessReadParcelParents() } returns parents
            coEvery { dao.getBusinessReadOwners() } coAnswers { owners.value }
            coEvery { dao.getBusinessReadParcelParents() } coAnswers { parents.value }
            coEvery { dao.withParcelMergeTransaction(any()) } coAnswers { firstArg<suspend () -> Unit>().invoke() }
            access = BusinessReadAccess(identity, dao)
            every { forestRepository.getAll() } returns forests
            every { parcelRepository.getAllParcelles() } returns parcels
            every { parcelRepository.getParcellesByForest("forest-A") } returns parcels
            coEvery { sessions.getClosedSnapshot("session-A") } coAnswers { snapshotReader() }
            coEvery { calculator.loadSynthesisParams() } returns ForestrySynthesisParams(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
            coEvery { calculator.diameterClasses() } returns listOf(30, 35, 40, 45)
        }
        fun synthesis(scope: TestScope, closed: Boolean = true): MartelageViewModel {
            access.startObserving(scope.backgroundScope)
            val trees = mockk<TigeRepository>(); every { trees.getAllTiges() } returns observations
            val essences = mockk<EssenceRepository>(); every { essences.getAllEssences() } returns MutableStateFlow(emptyList())
            val plots = mockk<PlacetteRepository>(); every { plots.getPlacettesByParcelle("parcel-A") } returns MutableStateFlow(emptyList())
            every { sessions.getByParcelle("parcel-A") } returns MutableStateFlow(listOf(if (closed) session else session.copy(martelageState = MartelageStates.PAUSED)))
            val vm = MartelageViewModel("PARCELLE", "forest-A", "parcel-A", null, essences, trees, parcelRepository,
                plots, sessions, calculator, access, dispatcher)
            store.put("synthesis", vm); scope.runCurrent(); return vm
        }
        fun detail(scope: TestScope): ForestDetailViewModel {
            access.startObserving(scope.backgroundScope)
            val vm = ForestDetailViewModel(forestRepository, parcelRepository, "forest-A", access)
            store.put("detail", vm); scope.runCurrent(); return vm
        }
    }
}
