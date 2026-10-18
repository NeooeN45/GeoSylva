package com.forestry.counter.presentation.viewmodel

import androidx.lifecycle.ViewModelStore
import com.forestry.counter.domain.model.ParcelSyncCandidate
import com.forestry.counter.domain.model.ParcelSyncProcessResult
import com.forestry.counter.domain.model.ParcelSyncPullResult
import com.forestry.counter.domain.model.ParcelSyncSelection
import com.forestry.counter.domain.model.ParcelSyncSummary
import com.forestry.counter.domain.repository.IdentityRepository
import com.forestry.counter.domain.repository.IdentitySessionGuard
import com.forestry.counter.domain.repository.ParcelSyncRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ParcelSyncSelectionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var identity: IdentityRepositoryFake
    private lateinit var identityRepository: IdentityRepository
    private lateinit var parcels: SelectionRepositoryFake
    private lateinit var activeGuard: TestGuard
    private lateinit var viewModel: ParcelSyncSelectionViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        identity = IdentityRepositoryFake().apply { session.value = identitySessionFixture() }
        activeGuard = TestGuard(identitySessionFixture().accountId)
        identityRepository = object : IdentityRepository by identity {
            override fun isSessionGuardCurrent(guard: IdentitySessionGuard): Boolean = guard === activeGuard
        }
        parcels = SelectionRepositoryFake().apply { load = { Result.success(selection()) } }
    }

    @After
    fun tearDown() {
        if (::viewModel.isInitialized) {
            ViewModelStore().apply {
                put("parcel-selection", viewModel)
                clear()
            }
        }
        Dispatchers.resetMain()
    }

    @Test
    fun `ouvrir la liste ne preselectionne et ne met aucune parcelle en file`() = runTest(dispatcher) {
        start()
        assertEquals(3, viewModel.uiState.value.selection?.parcels?.size)
        assertTrue(viewModel.uiState.value.selectedParcelIds.isEmpty())
        assertTrue(parcels.requests.isEmpty())
    }

    @Test fun `legacy recovery remains read only until explicit review and confirmation`() = runTest(dispatcher) {
        val recoverable = candidate("legacy", linked = true).copy(
            recoveryRequired = true,
            recoveryPreview = com.forestry.counter.domain.model.ParcelSyncRecoveryPreview("TEST-preview", "Local", 1L, "Remote", "2026-10-03T10:00:00Z", 3, false),
        )
        parcels.load = { Result.success(selection().copy(parcels = listOf(recoverable))) }
        start()
        viewModel.toggleParcel("legacy")
        viewModel.confirmSelection()
        runCurrent()
        assertTrue(parcels.requests.isEmpty())
        viewModel.reviewSelection()
        viewModel.confirmSelection()
        runCurrent()
        assertEquals(1, parcels.requests.size)
    }

    @Test fun `legacy recovery unavailable on server cannot be selected or submitted`() = runTest(dispatcher) {
        parcels.load = { Result.success(selection().copy(parcels = listOf(candidate("legacy", linked = true).copy(
            recoveryRequired = true, recoveryUnavailable = true,
        )))) }
        start()
        viewModel.toggleParcel("legacy")
        viewModel.reviewSelection()
        viewModel.confirmSelection()
        runCurrent()
        assertTrue(viewModel.uiState.value.selectedParcelIds.isEmpty())
        assertTrue(parcels.requests.isEmpty())
    }

    @Test
    fun `les parcelles liees et etrangeres restent non selectionnables`() = runTest(dispatcher) {
        start()
        viewModel.toggleParcel("linked")
        viewModel.toggleParcel("foreign")
        viewModel.toggleParcel("unknown")
        viewModel.reviewSelection()
        viewModel.confirmSelection()
        runCurrent()
        assertTrue(viewModel.uiState.value.selectedParcelIds.isEmpty())
        assertEquals(ParcelSyncSelectionStep.SELECT, viewModel.uiState.value.step)
        assertTrue(parcels.requests.isEmpty())
    }

    @Test
    fun `le choix et la revue seuls ne declenchent aucun ajout`() = runTest(dispatcher) {
        start()
        viewModel.toggleParcel("new")
        viewModel.reviewSelection()
        runCurrent()
        assertEquals(setOf("new"), viewModel.uiState.value.selectedParcelIds)
        assertEquals(ParcelSyncSelectionStep.REVIEW, viewModel.uiState.value.step)
        assertTrue(parcels.requests.isEmpty())
    }

    @Test
    fun `confirmer transmet uniquement le choix et la reference capturee une seule fois`() = runTest(dispatcher) {
        start()
        val captured = viewModel.uiState.value.selection
        val finish = CompletableDeferred<Result<Int>>()
        parcels.enqueue = { _, _ -> finish.await() }
        viewModel.toggleParcel("new")
        viewModel.reviewSelection()
        viewModel.confirmSelection()
        viewModel.confirmSelection()
        viewModel.cancel()
        viewModel.editSelection()
        assertTrue(viewModel.uiState.value.isSubmitting)
        assertFalse(viewModel.uiState.value.isClosed)
        runCurrent()
        assertEquals(1, parcels.requests.size)
        assertSame(captured, parcels.requests.single().first)
        assertEquals(setOf("new"), parcels.requests.single().second)
        finish.complete(Result.success(1))
        runCurrent()
        assertEquals(1, viewModel.uiState.value.addedCount)
        assertEquals(ParcelSyncSelectionStep.SUCCESS, viewModel.uiState.value.step)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `le bilan utilise le nombre reel de nouvelles associations`() = runTest(dispatcher) {
        parcels.enqueue = { _, _ -> Result.success(0) }
        start()
        chooseAndReview()
        viewModel.confirmSelection()
        runCurrent()
        assertEquals(0, viewModel.uiState.value.addedCount)
        assertEquals(ParcelSyncSelectionStep.SUCCESS, viewModel.uiState.value.step)
    }

    @Test
    fun `annuler un choix ferme et ne met rien en file`() = runTest(dispatcher) {
        start()
        viewModel.toggleParcel("new")
        viewModel.cancel()
        viewModel.reviewSelection()
        viewModel.confirmSelection()
        runCurrent()
        assertTrue(viewModel.uiState.value.isClosed)
        assertTrue(viewModel.uiState.value.selectedParcelIds.isEmpty())
        assertNull(viewModel.uiState.value.selection)
        assertTrue(parcels.requests.isEmpty())
    }

    @Test
    fun `annuler depuis le recapitulatif ne met rien en file`() = runTest(dispatcher) {
        start()
        chooseAndReview()
        viewModel.cancel()
        runCurrent()
        assertTrue(viewModel.uiState.value.isClosed)
        assertTrue(parcels.requests.isEmpty())
    }

    @Test
    fun `retour au choix conserve uniquement la selection explicite`() = runTest(dispatcher) {
        start()
        chooseAndReview()
        viewModel.editSelection()
        assertEquals(ParcelSyncSelectionStep.SELECT, viewModel.uiState.value.step)
        assertEquals(setOf("new"), viewModel.uiState.value.selectedParcelIds)
        viewModel.toggleParcel("new")
        assertTrue(viewModel.uiState.value.selectedParcelIds.isEmpty())
        assertTrue(parcels.requests.isEmpty())
    }

    @Test
    fun `une liste retardee ne rouvre pas un parcours annule`() = runTest(dispatcher) {
        val response = CompletableDeferred<Result<ParcelSyncSelection>>()
        val original = selection()
        parcels.load = { withContext(NonCancellable) { response.await() } }
        viewModel = ParcelSyncSelectionViewModel(identityRepository, parcels)
        runCurrent()
        assertTrue(viewModel.uiState.value.isLoading)
        viewModel.cancel()
        response.complete(Result.success(original))
        runCurrent()
        assertTrue(viewModel.uiState.value.isClosed)
        assertNull(viewModel.uiState.value.selection)
        assertTrue(parcels.requests.isEmpty())
    }

    @Test
    fun `un changement de compte efface la liste et la selection`() = runTest(dispatcher) {
        start()
        chooseAndReview()
        identity.session.value = identitySessionFixture().copy(accountId = "another-account")
        activeGuard = TestGuard("another-account")
        runCurrent()
        assertEquals(ParcelSyncSelectionError.SESSION_CHANGED, viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.selection)
        assertTrue(viewModel.uiState.value.selectedParcelIds.isEmpty())
        assertTrue(parcels.requests.isEmpty())
    }

    @Test
    fun `le changement avant execution du collecteur bloque la confirmation`() = runTest(dispatcher) {
        start()
        chooseAndReview()
        identity.session.value = identitySessionFixture().copy(accountId = "another-account")
        activeGuard = TestGuard("another-account")
        viewModel.confirmSelection()
        runCurrent()
        assertEquals(ParcelSyncSelectionError.SESSION_CHANGED, viewModel.uiState.value.error)
        assertTrue(parcels.requests.isEmpty())
    }

    @Test
    fun `une reconnexion au meme compte invalide le choix sans attendre un nouveau profil`() = runTest(dispatcher) {
        start()
        chooseAndReview()
        activeGuard = TestGuard(identitySessionFixture().accountId)
        viewModel.confirmSelection()
        runCurrent()
        assertEquals(ParcelSyncSelectionError.SESSION_CHANGED, viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.selectedParcelIds.isEmpty())
        assertTrue(parcels.requests.isEmpty())
    }

    @Test
    fun `la reprise au premier plan detecte une garde perimee meme pour le meme compte`() = runTest(dispatcher) {
        start()
        viewModel.toggleParcel("new")
        activeGuard = TestGuard(identitySessionFixture().accountId)
        assertFalse(viewModel.validateSession())
        assertNull(viewModel.uiState.value.selection)
        assertTrue(viewModel.uiState.value.selectedParcelIds.isEmpty())
    }

    @Test
    fun `une liste du compte precedent retardee est rejetee apres changement de session`() = runTest(dispatcher) {
        val response = CompletableDeferred<Result<ParcelSyncSelection>>()
        val original = selection()
        parcels.load = { withContext(NonCancellable) { response.await() } }
        viewModel = ParcelSyncSelectionViewModel(identityRepository, parcels)
        runCurrent()
        identity.session.value = identitySessionFixture().copy(accountId = "another-account")
        activeGuard = TestGuard("another-account")
        runCurrent()
        response.complete(Result.success(original))
        runCurrent()
        assertEquals(ParcelSyncSelectionError.SESSION_CHANGED, viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.selection)
        assertTrue(parcels.requests.isEmpty())
    }

    @Test
    fun `une liste terminee avec une garde non courante ne peut pas etre selectionnee`() = runTest(dispatcher) {
        val original = selection()
        parcels.load = { Result.success(original) }
        activeGuard = TestGuard(identitySessionFixture().accountId)
        start()
        viewModel.toggleParcel("new")
        assertEquals(ParcelSyncSelectionError.SESSION_CHANGED, viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.selection)
        assertTrue(parcels.requests.isEmpty())
    }

    @Test
    fun `une erreur de chargement peut etre reprise sans choix implicite`() = runTest(dispatcher) {
        parcels.load = { Result.failure(IllegalStateException("synthetic load failure")) }
        start()
        assertEquals(ParcelSyncSelectionError.LOAD_FAILED, viewModel.uiState.value.error)
        parcels.load = { Result.success(selection()) }
        viewModel.reload()
        runCurrent()
        assertNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.selectedParcelIds.isEmpty())
        assertTrue(parcels.requests.isEmpty())
    }

    @Test
    fun `une erreur apres ajout exige de relire les associations et efface le choix`() = runTest(dispatcher) {
        parcels.enqueue = { _, _ -> Result.failure(IllegalStateException("synthetic enqueue failure")) }
        start()
        chooseAndReview()
        viewModel.confirmSelection()
        runCurrent()
        assertEquals(ParcelSyncSelectionError.ENQUEUE_FAILED, viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.selectedParcelIds.isEmpty())
        assertNull(viewModel.uiState.value.selection)
        parcels.load = { Result.success(selection().copy(parcels = listOf(candidate("new", linked = true)))) }
        viewModel.reload()
        runCurrent()
        viewModel.toggleParcel("new")
        assertTrue(viewModel.uiState.value.selectedParcelIds.isEmpty())
        assertEquals(1, parcels.requests.size)
    }

    @Test
    fun `une reponse tardive apres deconnexion ne confirme pas un ajout pour la nouvelle session`() = runTest(dispatcher) {
        val response = CompletableDeferred<Result<Int>>()
        parcels.enqueue = { _, _ -> withContext(NonCancellable) { response.await() } }
        start()
        chooseAndReview()
        viewModel.confirmSelection()
        runCurrent()
        identity.session.value = null
        runCurrent()
        response.complete(Result.success(1))
        runCurrent()
        assertEquals(ParcelSyncSelectionError.SESSION_UNAVAILABLE, viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.addedCount)
        assertEquals(ParcelSyncSelectionStep.SELECT, viewModel.uiState.value.step)
        assertEquals(1, parcels.requests.size)
    }

    @Test
    fun `sans session valide aucune liste ni association ne sont demandees`() = runTest(dispatcher) {
        identity.session.value = identitySessionFixture().copy(expiresAtEpochSeconds = 1)
        start()
        assertEquals(ParcelSyncSelectionError.SESSION_UNAVAILABLE, viewModel.uiState.value.error)
        assertEquals(0, parcels.loadCount)
        assertTrue(parcels.requests.isEmpty())
    }

    private fun TestScope.start() {
        viewModel = ParcelSyncSelectionViewModel(identityRepository, parcels)
        runCurrent()
    }

    private fun chooseAndReview() {
        viewModel.toggleParcel("new")
        viewModel.reviewSelection()
    }

    private fun selection() = ParcelSyncSelection(
        accountId = identitySessionFixture().accountId,
        sessionGuard = activeGuard,
        parcels = listOf(candidate("new"), candidate("linked", linked = true), candidate("foreign", linked = true, foreign = true)),
    )

    private fun candidate(id: String, linked: Boolean = false, foreign: Boolean = false) = ParcelSyncCandidate(
        parcelId = id,
        name = "Parcelle $id JVM",
        surfaceHa = 2.4,
        commune = "Commune JVM",
        isLinked = linked,
        blockedByOtherAccount = foreign,
    )

    private class TestGuard(override val accountId: String) : IdentitySessionGuard

    /** Double de frontière JVM ; aucun remplacement de données dans l'application. */
    private class SelectionRepositoryFake : ParcelSyncRepository {
        var loadCount = 0
        val requests = mutableListOf<Pair<ParcelSyncSelection, Set<String>>>()
        lateinit var load: suspend () -> Result<ParcelSyncSelection>
        var enqueue: suspend (ParcelSyncSelection, Set<String>) -> Result<Int> = { _, ids -> Result.success(ids.size) }
        override fun observeSummary() = MutableStateFlow(ParcelSyncSummary())
        override suspend fun loadSelection(): Result<ParcelSyncSelection> {
            loadCount += 1
            return load()
        }
        override suspend fun enqueueSelected(selection: ParcelSyncSelection, parcelIds: Set<String>): Result<Int> {
            requests += selection to parcelIds
            return enqueue(selection, parcelIds)
        }
        override suspend fun enqueueUpsert(parcelleId: String) = error("Écriture hors du parcours testé")
        override suspend fun enqueueDelete(parcelleId: String) = error("Suppression hors du parcours testé")
        override suspend fun processPending(): ParcelSyncProcessResult = error("Transport hors du parcours testé")
        override suspend fun pull(): Result<ParcelSyncPullResult> = error("Pull hors du parcours testé")
    }
}
