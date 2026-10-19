package com.forestry.counter.presentation.screens.field

import androidx.lifecycle.ViewModelStore
import com.forestry.counter.domain.field.*
import com.forestry.counter.domain.repository.*
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var repo: Fixture
    private lateinit var vm: ReceiptsViewModel
    @Before fun setup() { Dispatchers.setMain(dispatcher); repo = Fixture(); mount() }
    @After fun close() { store.clear(); Dispatchers.resetMain() }
    private fun mount() { vm = ReceiptsViewModel(repo, repo.site); store.put("receipt", vm) }

    @Test fun `lost validation response is retried with the durable command after recreation`() = runTest(dispatcher) {
        runCurrent(); vm.open(repo.receipt.id); runCurrent()
        repo.loseValidationResponse = true
        vm.validate(); runCurrent()
        assertTrue(vm.state.value.retry)
        assertEquals(1, repo.physicalEntries)
        assertNotNull(repo.pending)
        val firstId = repo.attempts.single()
        store.clear(); mount(); runCurrent()
        assertEquals(ReceiptStatus.VALIDATED, vm.state.value.detail!!.status)
        assertTrue(vm.state.value.retry)
        vm.retry(); runCurrent()
        assertEquals(listOf(firstId, firstId), repo.attempts)
        assertEquals(1, repo.physicalEntries)
        assertFalse(vm.state.value.retry)
        assertNull(repo.pending)
    }

    @Test fun `operation is not submitted until its retry identity is stored`() = runTest(dispatcher) {
        runCurrent(); vm.open(repo.receipt.id); runCurrent()
        repo.failPendingSave = true
        vm.validate(); runCurrent()
        assertEquals(0, repo.physicalEntries)
        assertTrue(repo.attempts.isEmpty())
        assertTrue(vm.state.value.retry)
        repo.failPendingSave = false
        vm.retry(); runCurrent()
        assertEquals(1, repo.physicalEntries)
        assertFalse(vm.state.value.retry)
    }

    @Test fun `draft auto save survives back and viewmodel recreation without stock mutation`() = runTest(dispatcher) {
        runCurrent(); vm.create(); runCurrent()
        vm.change { copy(title = "TEST · Réception à reprendre", notes = "TEST · Pas encore de quantité validée") }
        advanceTimeBy(500); runCurrent()
        assertTrue(vm.state.value.draftSaved)
        val id = vm.state.value.detail!!.id
        vm.leave {}; runCurrent()
        assertNull(vm.state.value.form)
        assertEquals(0, repo.physicalEntries)
        store.clear(); mount(); runCurrent(); vm.open(id); runCurrent(); vm.edit()
        assertEquals("TEST · Réception à reprendre", vm.state.value.form!!.title)
        assertEquals("TEST · Pas encore de quantité validée", vm.state.value.form!!.notes)
    }

    @Test fun `logout clears receipt data and still allows leaving`() = runTest(dispatcher) {
        runCurrent(); vm.open(repo.receipt.id); runCurrent()
        repo.epoch.value++; runCurrent()
        assertTrue(vm.state.value.accessLost)
        assertNull(vm.state.value.detail)
        assertTrue(vm.state.value.receipts.isEmpty())
        var exited = false
        vm.leave { exited = true }
        assertTrue(exited)
        vm.validate(); runCurrent()
        assertEquals(0, repo.physicalEntries)
    }

    @Test fun `double validation tap is one operation and one stock entry`() = runTest(dispatcher) {
        runCurrent(); vm.open(repo.receipt.id); runCurrent()
        vm.validate(); vm.validate(); runCurrent()
        assertEquals(1, repo.attempts.size)
        assertEquals(1, repo.physicalEntries)
    }

    @Test fun `refused cancellation preserves record and can return to document`() = runTest(dispatcher) {
        runCurrent(); vm.open(repo.receipt.id); runCurrent(); vm.validate(); runCurrent()
        vm.cancel("TEST · Correction demandée"); runCurrent()
        assertEquals(FieldError.DEPENDENCIES, vm.state.value.error)
        assertNotNull(repo.pending)
        vm.dismissRefusedOperation(); runCurrent()
        assertEquals(ReceiptStatus.VALIDATED, vm.state.value.detail!!.status)
        assertFalse(vm.state.value.retry)
        assertNull(repo.pending)
        assertEquals(1, repo.physicalEntries)
    }

    internal class Fixture : FieldRepository {
        val site = UUID.randomUUID().toString()
        val epoch = MutableStateFlow(0)
        private data class Access(val epoch: Int) : FieldAccess
        var receipt = SavedReceipt(UUID.randomUUID().toString(), site, 1, ReceiptStatus.READY,
            ReceiptForm("TEST réception", "19/10/2026 10:00", "Europe/Paris", lines = listOf(ReceiptLine(
                objectId = UUID.randomUUID().toString(), depotId = UUID.randomUUID().toString(), observationId = UUID.randomUUID().toString(),
                observationRevision = 1, quantity = "100", unit = FieldQuantityUnit.APPARENT_M3, convention = "TEST enveloppe",
            ))), 1L, "anonymous")
        val saved = mutableMapOf(receipt.id to receipt)
        var pending: FieldDraft? = null
        var loseValidationResponse = false
        var failPendingSave = false
        var physicalEntries = 0
        val attempts = mutableListOf<String>()
        private val results = mutableMapOf<String, SavedReceipt>()
        override suspend fun captureAccess(): FieldAccess = Access(epoch.value)
        override fun isCurrent(access: FieldAccess) = (access as Access).epoch == epoch.value
        override fun accessChanges() = epoch.map { Unit }
        override suspend fun title(target: FieldTarget, access: FieldAccess) = "TEST chantier"
        override suspend fun writable(target: FieldTarget, access: FieldAccess) = true
        override suspend fun receipts(site: String, access: FieldAccess) = saved.values.toList()
        override suspend fun receipt(id: String, access: FieldAccess) = saved.getValue(id)
        override suspend fun receiptHistory(id: String, access: FieldAccess) = listOf(saved.getValue(id))
        override suspend fun receiptWoodChoices(site: String, access: FieldAccess) = emptyList<ReceiptWoodChoice>()
        override suspend fun saveReceipt(id: String, site: String, form: ReceiptForm, expectedRevision: Int, ready: Boolean, access: FieldAccess): SavedReceipt {
            assertTrue(isCurrent(access))
            check((saved[id]?.revision ?: 0) == expectedRevision)
            return SavedReceipt(id, site, expectedRevision + 1, if (ready) ReceiptStatus.READY else ReceiptStatus.DRAFT, form, 2L, "anonymous").also { saved[id] = it }
        }
        override suspend fun validateReceipt(commandId: String, id: String, expectedRevision: Int, access: FieldAccess): SavedReceipt {
            checkNotNull(pending) // Tests the durable-before-mutation ordering.
            attempts += commandId
            results[commandId]?.let { return it }
            val result = saved.getValue(id).copy(revision = expectedRevision + 1, status = ReceiptStatus.VALIDATED)
            physicalEntries++; saved[id] = result; results[commandId] = result
            if (loseValidationResponse) { loseValidationResponse = false; throw FieldException(FieldError.UNKNOWN_RESULT) }
            return result
        }
        override suspend fun cancelReceipt(commandId: String, id: String, expectedRevision: Int, reason: String, access: FieldAccess): SavedReceipt = throw FieldException(FieldError.DEPENDENCIES)
        override suspend fun loadDraft(target: FieldTarget, slot: String, access: FieldAccess) = pending
        override suspend fun saveDraft(target: FieldTarget, slot: String, expected: Long?, content: String, access: FieldAccess): FieldDraft {
            if (failPendingSave) throw FieldException(FieldError.IO)
            return FieldDraft((pending?.revision ?: 0) + 1, content, 2L).also { pending = it }
        }
        override suspend fun deleteDraft(target: FieldTarget, slot: String, expected: Long, access: FieldAccess) { pending = null }
        override suspend fun entries(target: FieldTarget, access: FieldAccess) = emptyList<FieldEntry>()
        override suspend fun sites(access: FieldAccess) = emptyList<SavedWorksite>()
        override suspend fun parcelChoices(access: FieldAccess) = emptyList<WorksiteParcel>()
        override suspend fun woodChoices(access: FieldAccess) = emptyList<FieldTargetChoice>()
        override suspend fun linked(site: String, access: FieldAccess) = emptyList<FieldTargetChoice>()
        override suspend fun link(site: String, target: FieldTarget, access: FieldAccess) = error("Unused")
        override suspend fun saveSite(id: String, form: WorksiteForm, revision: Int, archived: Boolean, access: FieldAccess): SavedWorksite = error("Unused")
        override suspend fun measurements(target: FieldTarget, access: FieldAccess): WoodObjectDetails? = null
        override suspend fun append(id: String, target: FieldTarget, form: FieldEntryForm, access: FieldAccess): FieldEntry = error("Unused")
        override suspend fun addPhoto(target: FieldTarget, bytes: ByteArray, mime: String, caption: String, access: FieldAccess): FieldPhoto = error("Unused")
        override suspend fun photo(id: String, target: FieldTarget, access: FieldAccess): Pair<FieldPhoto, ByteArray> = error("Unused")
    }
}
