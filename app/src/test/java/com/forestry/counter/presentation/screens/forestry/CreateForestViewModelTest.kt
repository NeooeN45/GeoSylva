package com.forestry.counter.presentation.screens.forestry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.forestry.counter.domain.location.ForestSearchResult
import com.forestry.counter.domain.location.ParcelCoordinate
import com.forestry.counter.domain.location.ParcelGeometry
import com.forestry.counter.domain.model.Foret
import com.forestry.counter.domain.repository.ForetRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CreateForestViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: ForetRepository
    private lateinit var viewModel: CreateForestViewModel
    private val stores = mutableListOf<ViewModelStore>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mockk()
        coEvery { repository.insert(any()) } returns Unit
        viewModel = createViewModel()
    }

    @After
    fun tearDown() {
        stores.forEach { it.clear() }
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        handle: SavedStateHandle = SavedStateHandle(),
        lookup: suspend (String) -> List<ForestSearchResult> = { emptyList() },
    ): CreateForestViewModel =
        CreateForestViewModel(repository, handle, lookup).also {
            stores += ViewModelStore().apply { put("forest-creation", it) }
        }

    @Test
    fun `a name creates a forest without invented owner location or management`() = runTest(dispatcher) {
        var saved: Foret? = null
        coEvery { repository.insert(any()) } coAnswers { saved = firstArg() }
        viewModel.updateNom("  TEST - FORET  ")

        assertTrue(viewModel.formState.value.isValid)
        viewModel.save()
        runCurrent()

        assertEquals("TEST - FORET", saved?.nom)
        assertEquals("", saved?.proprietaireNom)
        assertNull(saved?.proprietaireEmail)
        assertNull(saved?.gestionnaireNom)
        assertNull(saved?.departement)
        assertNull(saved?.typeForet)
        assertNull(saved?.objectifGestion)
        assertTrue(viewModel.isSaved.value)
        assertFalse(viewModel.isSaving.value)
    }

    @Test
    fun `double tap is blocked before the scope runs and fields stay fixed during save`() = runTest(dispatcher) {
        val finish = CompletableDeferred<Unit>()
        var saved: Foret? = null
        coEvery { repository.insert(any()) } coAnswers {
            saved = firstArg()
            finish.await()
        }
        viewModel.updateNom("TEST - FIRST")
        viewModel.nextStep()
        viewModel.nextStep()

        viewModel.save()
        viewModel.save()
        viewModel.updateNom("TEST - SECOND")
        viewModel.previousStep()
        assertTrue(viewModel.isSaving.value)
        runCurrent()

        coVerify(exactly = 1) { repository.insert(any()) }
        assertEquals("TEST - FIRST", saved?.nom)
        assertEquals("TEST - FIRST", viewModel.formState.value.nom)
        assertEquals(2, viewModel.formState.value.currentStep)
        finish.complete(Unit)
        runCurrent()
        viewModel.save()
        runCurrent()
        coVerify(exactly = 1) { repository.insert(any()) }
    }

    @Test
    fun `storage failure preserves the draft and retry retains its identity`() = runTest(dispatcher) {
        val attempts = mutableListOf<Foret>()
        coEvery { repository.insert(any()) } coAnswers {
            attempts += firstArg<Foret>()
            if (attempts.size == 1) throw IOException("Storage unavailable in test")
        }
        viewModel.updateNom("TEST - RETRY")
        viewModel.updateProprietaireNom("TEST - OWNER")
        viewModel.updateRemarques("TEST - KEEP NOTES")

        viewModel.save()
        runCurrent()
        assertTrue(viewModel.saveError.value)
        assertFalse(viewModel.isSaving.value)
        assertFalse(viewModel.isSaved.value)
        assertEquals("TEST - KEEP NOTES", viewModel.formState.value.remarques)

        viewModel.save()
        runCurrent()
        assertEquals(2, attempts.size)
        assertEquals(attempts[0].foretId, attempts[1].foretId)
        assertEquals(attempts[0].nom, attempts[1].nom)
        assertFalse(viewModel.saveError.value)
        assertTrue(viewModel.isSaved.value)
    }

    @Test
    fun `cancellation is not reported as a storage failure or a success`() = runTest(dispatcher) {
        coEvery { repository.insert(any()) } throws CancellationException("Test cancellation")
        viewModel.updateNom("TEST - CANCEL")

        viewModel.save()
        runCurrent()

        assertFalse(viewModel.saveError.value)
        assertFalse(viewModel.isSaved.value)
        assertFalse(viewModel.isSaving.value)
        assertEquals("TEST - CANCEL", viewModel.formState.value.nom)
    }

    @Test
    fun `invalid optional email blocks next step and save without rewriting the address`() = runTest(dispatcher) {
        viewModel.updateNom("TEST - EMAIL")
        viewModel.nextStep()
        viewModel.updateProprietaireEmail("incorrect address")

        viewModel.nextStep()
        viewModel.save()
        runCurrent()

        assertFalse(viewModel.formState.value.ownerEmailValid)
        assertEquals(1, viewModel.formState.value.currentStep)
        assertEquals("incorrect address", viewModel.formState.value.proprietaireEmail)
        coVerify(exactly = 0) { repository.insert(any()) }

        viewModel.updateProprietaireEmail("owner+field@example.invalid")
        viewModel.nextStep()
        assertTrue(viewModel.formState.value.isValid)
        assertEquals(2, viewModel.formState.value.currentStep)
    }

    @Test
    fun `blank name cannot advance or save`() = runTest(dispatcher) {
        viewModel.updateNom("   ")
        viewModel.nextStep()
        viewModel.save()
        runCurrent()

        assertEquals(0, viewModel.formState.value.currentStep)
        assertFalse(viewModel.formState.value.isValid)
        assertFalse(viewModel.isSaving.value)
        coVerify(exactly = 0) { repository.insert(any()) }
    }

    @Test
    fun `saved state restores the draft step and identity without copying loading errors`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val original = createViewModel(handle)
        original.updateNom("TEST - RESTORED")
        original.updateDepartement("17")
        original.updateProprietaireNom("TEST - OWNER")
        original.updateProprietaireEmail("owner@example.invalid")
        original.updateGestionnaireNom("TEST - MANAGER")
        original.updateTypeForet("TEST - TYPE")
        original.updateObjectifGestion("TEST - OBJECTIVE")
        original.updatePsgNumero("TEST - REFERENCE")
        original.updateRemarques("TEST - NOTES")
        original.nextStep()
        original.nextStep()
        val copiedState = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })

        val restored = createViewModel(copiedState)
        assertEquals(original.formState.value, restored.formState.value)
        assertFalse(restored.isSaving.value)
        assertFalse(restored.saveError.value)
        val attempts = mutableListOf<Foret>()
        coEvery { repository.insert(any()) } coAnswers { attempts += firstArg<Foret>() }
        original.save()
        runCurrent()
        restored.save()
        runCurrent()
        assertEquals(attempts[0].foretId, attempts[1].foretId)
    }

    @Test
    fun `completed save survives saved state restoration without another insert`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val original = createViewModel(handle)
        original.updateNom("TEST - COMPLETED")
        original.save()
        runCurrent()
        val copiedState = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })

        val restored = createViewModel(copiedState)
        assertTrue(restored.isSaved.value)
        restored.save()
        runCurrent()
        coVerify(exactly = 1) { repository.insert(any()) }
    }

    private fun forestSuggestion(name: String = "TEST - REAL SUGGESTION", id: String = "test-source-id") = ForestSearchResult(
        id = id,
        name = name,
        department = "17",
        geometry = null,
        center = null,
        sourceLabel = "TEST - SOURCE",
    )

    @Test
    fun `forest search waits for three characters and debounce without automatically selecting`() = runTest(dispatcher) {
        val queries = mutableListOf<String>()
        val suggestion = forestSuggestion()
        viewModel = createViewModel { query ->
            queries += query
            listOf(suggestion)
        }
        viewModel.updateNom("TE")
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(queries.isEmpty())

        viewModel.updateNom("TEST - USER SPELLING")
        runCurrent()
        advanceTimeBy(349)
        runCurrent()
        assertTrue(queries.isEmpty())
        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf("TEST - USER SPELLING"), queries)
        assertEquals(listOf(suggestion), viewModel.lookupState.value.suggestions)
        assertNull(viewModel.lookupState.value.selected)
        assertEquals("TEST - USER SPELLING", viewModel.formState.value.nom)
        assertNull(viewModel.formState.value.departement)
    }

    @Test
    fun `explicit forest selection applies its known name department without changing owner data`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val suggestion = forestSuggestion()
        viewModel = createViewModel(handle) { listOf(suggestion) }
        viewModel.updateProprietaireNom("TEST - OWNER")
        viewModel.updateProprietaireEmail("owner@example.invalid")
        viewModel.updateGestionnaireNom("TEST - MANAGER")
        viewModel.updateNom("TEST - TYPO")
        advanceTimeBy(350)
        runCurrent()

        viewModel.selectForestSuggestion(suggestion)
        assertEquals(suggestion.name, viewModel.formState.value.nom)
        assertEquals("17", viewModel.formState.value.departement)
        assertEquals("TEST - OWNER", viewModel.formState.value.proprietaireNom)
        assertEquals("owner@example.invalid", viewModel.formState.value.proprietaireEmail)
        assertEquals("TEST - MANAGER", viewModel.formState.value.gestionnaireNom)
        assertEquals(suggestion, viewModel.lookupState.value.selected)
        assertEquals(suggestion.id, createViewModel(handle).lookupState.value.selected?.id)

        viewModel.updateNom("TEST - MANUALLY CHANGED")
        assertNull(viewModel.lookupState.value.selected)
        assertNull(createViewModel(handle).lookupState.value.selected)
        assertEquals("TEST - MANUALLY CHANGED", viewModel.formState.value.nom)
    }

    @Test
    fun `new forest query cancels a pending lookup without presenting an error`() = runTest(dispatcher) {
        val firstResult = CompletableDeferred<List<ForestSearchResult>>()
        val second = forestSuggestion("TEST - SECOND", "test-second-id")
        val queries = mutableListOf<String>()
        viewModel = createViewModel { query ->
            queries += query
            if (query == "TEST - FIRST") firstResult.await() else listOf(second)
        }
        viewModel.updateNom("TEST - FIRST")
        advanceTimeBy(350)
        runCurrent()
        assertTrue(viewModel.lookupState.value.isLoading)

        viewModel.updateNom("TEST - SECOND")
        advanceTimeBy(350)
        runCurrent()
        firstResult.complete(listOf(forestSuggestion()))
        runCurrent()

        assertEquals(listOf("TEST - FIRST", "TEST - SECOND"), queries)
        assertEquals(listOf(second), viewModel.lookupState.value.suggestions)
        assertFalse(viewModel.lookupState.value.hasError)
        assertFalse(viewModel.lookupState.value.isLoading)
        assertEquals("TEST - SECOND", viewModel.formState.value.nom)
    }

    @Test
    fun `unavailable forest lookup leaves the manually entered forest usable`() = runTest(dispatcher) {
        viewModel = createViewModel { throw IOException("Test lookup unavailable") }
        viewModel.updateNom("TEST - MANUAL FOREST")
        advanceTimeBy(350)
        runCurrent()

        assertTrue(viewModel.lookupState.value.hasError)
        assertFalse(viewModel.lookupState.value.isLoading)
        assertEquals("TEST - MANUAL FOREST", viewModel.formState.value.nom)
        assertTrue(viewModel.formState.value.isValid)
        viewModel.save()
        runCurrent()
        assertTrue(viewModel.isSaved.value)
    }

    @Test
    fun `forest lookup cancellation is propagated without becoming a search error`() = runTest(dispatcher) {
        viewModel = createViewModel { throw CancellationException("Test lookup cancellation") }
        viewModel.updateNom("TEST - CANCEL LOOKUP")
        advanceTimeBy(350)
        runCurrent()

        assertFalse(viewModel.lookupState.value.hasError)
        assertFalse(viewModel.lookupState.value.isLoading)
        assertEquals("TEST - CANCEL LOOKUP", viewModel.formState.value.nom)
    }


    @Test
    fun `stale suggestion cannot replace a newly edited name`() = runTest(dispatcher) {
        val suggestion = forestSuggestion()
        viewModel = createViewModel { listOf(suggestion) }
        viewModel.updateNom("TEST - FIRST QUERY")
        advanceTimeBy(350)
        runCurrent()
        viewModel.updateNom("TEST - NEW NAME")

        viewModel.selectForestSuggestion(suggestion)

        assertEquals("TEST - NEW NAME", viewModel.formState.value.nom)
        assertNull(viewModel.selectedForest.value)
        assertNull(viewModel.lookupState.value.selected)
    }

    @Test
    fun `explicit map feature selection is available without a prior text search`() = runTest(dispatcher) {
        val candidate = forestSuggestion()
        viewModel.updateProprietaireNom("TEST - OWNER")

        viewModel.selectForest(candidate)

        assertEquals(candidate, viewModel.selectedForest.value)
        assertEquals(candidate.name, viewModel.formState.value.nom)
        assertEquals("17", viewModel.formState.value.departement)
        assertEquals("TEST - OWNER", viewModel.formState.value.proprietaireNom)
    }


    @Test
    fun `confirmed forest polygon and official references are saved as an exact geometry snapshot`() = runTest(dispatcher) {
        val geometry = ParcelGeometry.fromWkt("POLYGON ((1 46, 1.01 46, 1.01 46.01, 1 46.01, 1 46))")
        val candidate = forestSuggestion().copy(
            geometry = geometry,
            sourceDataset = "TEST - QUALIFIED DATASET",
        )
        var saved: Foret? = null
        coEvery { repository.insert(any()) } coAnswers { saved = firstArg() }
        viewModel.selectForest(candidate)

        viewModel.save()
        runCurrent()

        assertEquals(geometry.toWkt(), saved?.shape)
        assertEquals(candidate.id, saved?.sourceForestId)
        assertEquals(candidate.sourceDataset, saved?.sourceForestDataset)
        assertEquals("", saved?.proprietaireNom)
        assertNull(saved?.proprietaireEmail)
    }

    @Test
    fun `a forest point candidate does not become a polygon or official forest boundary reference`() = runTest(dispatcher) {
        val pointOnly = forestSuggestion().copy(
            center = ParcelCoordinate(1.0, 46.0),
            sourceDataset = "TEST - POINT DATASET",
        )
        var saved: Foret? = null
        coEvery { repository.insert(any()) } coAnswers { saved = firstArg() }
        viewModel.selectForest(pointOnly)

        viewModel.save()
        runCurrent()

        assertNull(saved?.shape)
        assertNull(saved?.sourceForestId)
        assertNull(saved?.sourceForestDataset)
        assertEquals(pointOnly.name, saved?.nom)
    }

    @Test
    fun `manual forest rename discards the selected official geometry before saving`() = runTest(dispatcher) {
        val geometry = ParcelGeometry.fromWkt("POLYGON ((1 46, 1.01 46, 1.01 46.01, 1 46.01, 1 46))")
        val candidate = forestSuggestion().copy(geometry = geometry, sourceDataset = "TEST - DATASET")
        var saved: Foret? = null
        coEvery { repository.insert(any()) } coAnswers { saved = firstArg() }
        viewModel.selectForest(candidate)
        viewModel.updateNom("TEST - UNLINKED FOREST")

        viewModel.save()
        runCurrent()

        assertNull(viewModel.selectedForest.value)
        assertNull(saved?.shape)
        assertNull(saved?.sourceForestId)
        assertNull(saved?.sourceForestDataset)
        assertEquals("TEST - UNLINKED FOREST", saved?.nom)
    }

}
