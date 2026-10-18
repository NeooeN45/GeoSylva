package com.forestry.counter.presentation.screens.wood

import androidx.lifecycle.ViewModelStore
import com.forestry.counter.domain.model.WoodDepotDraft
import com.forestry.counter.domain.model.WoodDepotKind
import com.forestry.counter.domain.model.WoodDepotLocation
import com.forestry.counter.domain.model.WoodDepotLocationSource
import com.forestry.counter.domain.model.WoodDepotOwnerScope
import com.forestry.counter.domain.model.WoodDepotRecord
import com.forestry.counter.domain.repository.WoodDepotAccess
import com.forestry.counter.domain.repository.WoodDepotRepository
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WoodDepotsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var repository: Repository
    private lateinit var vm: WoodDepotsViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = Repository()
        vm = WoodDepotsViewModel(repository)
        store.put("TEST", vm)
    }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun `name and explicitly selected kind required but a position is optional`() = runTest(dispatcher) {
        runCurrent(); vm.create(); vm.save(); runCurrent()
        assertTrue(vm.uiState.value.form!!.showValidation)
        assertTrue(repository.createAttempts.isEmpty())
        assertNull(vm.uiState.value.form!!.kind)
        vm.updateName("   "); vm.updateKind(WoodDepotKind.ROADSIDE); vm.save(); runCurrent()
        assertTrue(repository.createAttempts.isEmpty())
        vm.updateName(" TEST - DEPOT "); vm.save(); runCurrent()
        assertEquals("TEST - DEPOT", vm.uiState.value.detail!!.draft.name)
        assertNull(vm.uiState.value.detail!!.draft.location)
        assertEquals(WoodDepotOwnerScope.LOCAL_DEVICE, vm.uiState.value.detail!!.ownerScope)
    }

    @Test fun `coordinate pair requires finite bounds and accepts French commas and negative longitude`() = runTest(dispatcher) {
        runCurrent(); validForm(); vm.setHasPosition(true)
        vm.updateLatitude("46,1827"); vm.updateLongitude(""); vm.save(); runCurrent()
        assertTrue(repository.createAttempts.isEmpty())
        assertFalse(vm.uiState.value.form!!.validLongitude)
        vm.updateLongitude("-0,5371"); vm.save(); runCurrent()
        val position = vm.uiState.value.detail!!.draft.location!!
        assertEquals(46.1827, position.latitude, 0.0)
        assertEquals(-0.5371, position.longitude, 0.0)
        assertNull(position.accuracyM)
        assertEquals(WoodDepotLocationSource.MANUAL, position.source)
    }

    @Test fun `invalid ranges scientific notation and non finite text do not produce positions`() = runTest(dispatcher) {
        runCurrent(); validForm(); vm.setHasPosition(true)
        for (bad in listOf("91", "-91", "NaN", "Infinity", "1e1", "46 000", "46,2.1")) {
            vm.updateLatitude(bad); vm.updateLongitude("0"); vm.save(); runCurrent()
            assertTrue("Invalid latitude $bad", repository.createAttempts.isEmpty())
        }
        vm.updateLatitude("90")
        for (bad in listOf("181", "-181", "NaN", "1e2")) {
            vm.updateLongitude(bad); vm.save(); runCurrent()
            assertTrue("Invalid longitude $bad", repository.createAttempts.isEmpty())
        }
        vm.updateLongitude("-180"); vm.save(); runCurrent()
        assertEquals(-180.0, vm.uiState.value.detail!!.draft.location!!.longitude, 0.0)
    }

    @Test fun `deselecting a partially filled position stores absent rather than fabricated zero`() = runTest(dispatcher) {
        runCurrent(); validForm(); vm.setHasPosition(true); vm.updateLatitude("46")
        vm.setHasPosition(false); vm.save(); runCurrent()
        assertNull(vm.uiState.value.detail!!.draft.location)
    }

    @Test fun `length limits keep the entered text and explain validation without truncating`() = runTest(dispatcher) {
        runCurrent(); validForm()
        vm.updateName("x".repeat(201)); vm.save(); runCurrent()
        assertEquals(201, vm.uiState.value.form!!.name.length)
        assertTrue(repository.createAttempts.isEmpty())
        vm.updateName("TEST"); vm.updateNotes("x".repeat(5_001)); vm.save(); runCurrent()
        assertEquals(5_001, vm.uiState.value.form!!.notes.length)
        assertTrue(repository.createAttempts.isEmpty())
    }

    @Test fun `two save taps enqueue once and freeze the submitted draft until completion`() = runTest(dispatcher) {
        runCurrent(); validForm()
        val gate = CompletableDeferred<Unit>()
        repository.beforeCreate = { gate.await() }
        vm.save(); vm.save(); vm.updateName("TEST - TOO LATE"); runCurrent()
        assertTrue(vm.uiState.value.busy)
        assertEquals(1, repository.createAttempts.size)
        assertEquals("TEST - DEPOT", repository.createAttempts.single().second.name)
        gate.complete(Unit); runCurrent()
        assertEquals("TEST - DEPOT", vm.uiState.value.detail!!.draft.name)
        assertEquals(WoodDepotsNotice.SAVED, vm.uiState.value.notice)
    }

    @Test fun `storage failure preserves draft and same creation UUID on retry`() = runTest(dispatcher) {
        runCurrent(); validForm(); vm.updateNotes("TEST - RETAIN")
        repository.beforeCreate = { throw IOException("TEST failure, not shown to user") }
        val id = vm.uiState.value.form!!.id
        vm.save(); runCurrent()
        assertEquals(WoodDepotsError.SAVE_FAILED, vm.uiState.value.error)
        assertEquals("TEST - RETAIN", vm.uiState.value.form!!.notes)
        repository.beforeCreate = {}; vm.save(); runCurrent()
        assertEquals(listOf(id, id), repository.createAttempts.map { it.first })
        assertEquals(id, vm.uiState.value.detail!!.id)
    }

    @Test fun `logout and same account reauthentication clear captured draft before any mutation`() = runTest(dispatcher) {
        runCurrent(); repository.changeAccount("TEST-A"); runCurrent()
        // This fixture conflates equal empty lists; use the production resume hook for account A.
        vm.verifyAccess()
        validForm(); val initialId = vm.uiState.value.form!!.id
        repository.changeAccount(null); repository.changeAccount("TEST-A")
        vm.save(); runCurrent()
        assertNull(vm.uiState.value.form)
        assertNull(vm.uiState.value.detail)
        assertEquals(WoodDepotsError.ACCESS_CHANGED, vm.uiState.value.error)
        assertTrue(repository.createAttempts.isEmpty())
        vm.create()
        assertNotEquals(initialId, vm.uiState.value.form!!.id)
    }

    @Test fun `late create result is never displayed or reassigned under a changed account`() = runTest(dispatcher) {
        runCurrent(); validForm()
        val gate = CompletableDeferred<Unit>()
        repository.beforeCreate = { withContext(NonCancellable) { gate.await() } }
        vm.save(); runCurrent()
        repository.changeAccount("TEST-B"); runCurrent()
        gate.complete(Unit); runCurrent()
        assertNull(vm.uiState.value.detail)
        assertNull(vm.uiState.value.form)
        assertEquals(WoodDepotsError.ACCESS_CHANGED, vm.uiState.value.error)
        assertTrue(vm.uiState.value.depots.isEmpty())
    }

    @Test fun `late read cannot expose a previous spaces depot`() = runTest(dispatcher) {
        runCurrent(); val record = repository.seed()
        val gate = CompletableDeferred<Unit>()
        repository.beforeRead = { withContext(NonCancellable) { gate.await() } }
        vm.open(record.id); runCurrent()
        repository.changeAccount("TEST-B"); runCurrent()
        gate.complete(Unit); runCurrent()
        assertNull(vm.uiState.value.detail)
        assertTrue(vm.uiState.value.depots.isEmpty())
    }

    @Test fun `revision conflict keeps user input and explicit reload uses latest revision`() = runTest(dispatcher) {
        runCurrent(); val initial = repository.seed()
        vm.open(initial.id); runCurrent(); vm.edit(); vm.updateNotes("TEST - UNSAVED")
        val external = repository.externalUpdate(initial.id, "TEST - EXTERNAL")
        vm.save(); runCurrent()
        assertEquals(WoodDepotsError.CONFLICT, vm.uiState.value.error)
        assertEquals("TEST - UNSAVED", vm.uiState.value.form!!.notes)
        assertEquals(1, vm.uiState.value.form!!.expectedRevision)
        vm.reloadAfterConflict(); runCurrent()
        assertEquals(external.revision, vm.uiState.value.form!!.expectedRevision)
        assertEquals("TEST - EXTERNAL", vm.uiState.value.form!!.name)
        vm.updateNotes("TEST - MERELY EDITED AFTER RELOAD"); vm.save(); runCurrent()
        assertEquals(3, vm.uiState.value.detail!!.revision)
        assertEquals("TEST - MERELY EDITED AFTER RELOAD", vm.uiState.value.detail!!.draft.notes)
    }

    @Test fun `archive requires confirmation and retains original dossier without inventing zero stock`() = runTest(dispatcher) {
        runCurrent(); val record = repository.seed()
        vm.open(record.id); runCurrent(); vm.requestArchive(); runCurrent()
        assertEquals(record.id, vm.uiState.value.archiveCandidate!!.id)
        assertEquals(0, repository.deleteCalls)
        vm.dismissArchive(); runCurrent(); assertNull(vm.uiState.value.archiveCandidate)
        vm.requestArchive(); vm.confirmArchive(); vm.confirmArchive(); runCurrent()
        assertEquals(1, repository.deleteCalls)
        assertNull(vm.uiState.value.detail)
        assertTrue(vm.uiState.value.depots.isEmpty())
        assertEquals(record.draft, repository.records.getValue(record.id).draft)
        assertNotNull(repository.records.getValue(record.id).deletedAt)
    }

    @Test fun `archive conflict can reload then edit and save with captured access`() = runTest(dispatcher) {
        runCurrent(); val record = repository.seed()
        vm.open(record.id); runCurrent(); vm.requestArchive()
        repository.externalUpdate(record.id, "TEST - EXTERNAL")
        vm.confirmArchive(); runCurrent()
        assertEquals(WoodDepotsError.CONFLICT, vm.uiState.value.error)
        assertNull(vm.uiState.value.archiveCandidate)
        vm.reloadAfterConflict(); runCurrent()
        vm.updateName("TEST - EDITABLE AFTER ARCHIVE CONFLICT"); vm.save(); runCurrent()
        assertEquals("TEST - EDITABLE AFTER ARCHIVE CONFLICT", vm.uiState.value.detail!!.draft.name)
        assertEquals(3, vm.uiState.value.detail!!.revision)
    }

    @Test fun `changed access before archive confirmation prevents deletion and clears dialog`() = runTest(dispatcher) {
        runCurrent(); val record = repository.seed()
        vm.open(record.id); runCurrent(); vm.requestArchive()
        repository.changeAccount("TEST-B"); vm.confirmArchive(); runCurrent()
        assertEquals(0, repository.deleteCalls)
        assertNull(vm.uiState.value.archiveCandidate)
        assertNull(vm.uiState.value.detail)
    }

    @Test fun `existing small GPS coordinates remain editable and retain source unless manually changed`() = runTest(dispatcher) {
        runCurrent()
        val location = WoodDepotLocation(1e-8, -1e-6, 4.0, WoodDepotLocationSource.GPS)
        val initial = repository.seed(location = location)
        vm.open(initial.id); runCurrent(); vm.edit()
        assertEquals("-0.0000010", vm.uiState.value.form!!.longitude)
        assertTrue(vm.uiState.value.form!!.isValid)
        vm.updateName("TEST - NAME ONLY"); vm.save(); runCurrent()
        assertEquals(location, vm.uiState.value.detail!!.draft.location)
        vm.edit(); vm.updateLongitude("-0,000002"); vm.save(); runCurrent()
        assertEquals(WoodDepotLocationSource.MANUAL, vm.uiState.value.detail!!.draft.location!!.source)
        assertNull(vm.uiState.value.detail!!.draft.location!!.accuracyM)
    }

    @Test fun `same identity verification does not erase a valid draft and changed generation does`() = runTest(dispatcher) {
        runCurrent(); validForm(); vm.updateNotes("TEST - STILL HERE")
        vm.verifyAccess()
        assertEquals("TEST - STILL HERE", vm.uiState.value.form!!.notes)
        repository.generation++
        vm.verifyAccess()
        assertNull(vm.uiState.value.form)
        assertTrue(vm.uiState.value.depots.isEmpty())
    }

    @Test fun `failed observer clears every dossier and draft before later account change then retry recaptures access`() = runTest(dispatcher) {
        runCurrent(); val record = repository.seed(); runCurrent()
        vm.open(record.id); runCurrent(); vm.edit(); vm.updateNotes("TEST - PRIVATE UNSAVED INPUT")
        repository.failNextObservation = true
        repository.externalUpdate(record.id, "TEST - SIGNAL FAILED READ"); runCurrent()
        assertEquals(WoodDepotsError.LOAD_FAILED, vm.uiState.value.error)
        assertTrue(vm.uiState.value.depots.isEmpty())
        assertNull(vm.uiState.value.form); assertNull(vm.uiState.value.detail)
        assertNull(vm.uiState.value.archiveCandidate)
        assertFalse(vm.uiState.value.busy)
        repository.changeAccount("TEST-B"); runCurrent(); vm.verifyAccess(); vm.create(); vm.save()
        assertTrue(vm.uiState.value.depots.isEmpty())
        assertNull(vm.uiState.value.form); assertNull(vm.uiState.value.detail)
        assertEquals(WoodDepotsError.LOAD_FAILED, vm.uiState.value.error)
        assertTrue(repository.createAttempts.isEmpty())
        vm.retryList(); runCurrent(); validForm(); vm.save(); runCurrent()
        assertEquals("TEST-B", vm.uiState.value.detail!!.ownerAccountId)
        assertEquals(1, repository.createAttempts.size)
        assertEquals("TEST-B", vm.uiState.value.detail!!.createdBy)
        assertNull(repository.records.getValue(record.id).ownerAccountId)
    }

    @Test fun `read response arriving after observer failure cannot restore cleared private content`() = runTest(dispatcher) {
        runCurrent(); val record = repository.seed(); runCurrent()
        val gate = CompletableDeferred<Unit>()
        repository.beforeRead = { withContext(NonCancellable) { gate.await() } }
        vm.open(record.id); runCurrent()
        repository.failNextObservation = true
        repository.externalUpdate(record.id, "TEST - FAILED OBSERVATION"); runCurrent()
        gate.complete(Unit); runCurrent()
        assertEquals(WoodDepotsError.LOAD_FAILED, vm.uiState.value.error)
        assertTrue(vm.uiState.value.depots.isEmpty())
        assertNull(vm.uiState.value.detail); assertNull(vm.uiState.value.form)
        assertFalse(vm.uiState.value.busy)
    }

    private fun validForm() { vm.create(); vm.updateName("TEST - DEPOT"); vm.updateKind(WoodDepotKind.ROADSIDE) }

    private class Repository : WoodDepotRepository {
        var account: String? = null
        var generation = 0L
        val rows = MutableStateFlow<List<WoodDepotRecord>>(emptyList())
        val records = mutableMapOf<String, WoodDepotRecord>()
        val createAttempts = mutableListOf<Pair<String, WoodDepotDraft>>()
        var deleteCalls = 0
        var beforeCreate: suspend () -> Unit = {}
        var beforeRead: suspend () -> Unit = {}
        var failNextObservation = false
        private data class Access(override val accountId: String?, val generation: Long) : WoodDepotAccess
        override fun captureAccess(): WoodDepotAccess = Access(account, generation)
        override fun isAccessCurrent(access: WoodDepotAccess): Boolean =
            access is Access && access.generation == generation && access.accountId == account
        override fun observeDepots() = rows.onEach {
            if (failNextObservation) {
                failNextObservation = false
                throw IOException("TEST observer failure")
            }
        }
        fun changeAccount(value: String?) { account = value; generation++; publish() }
        private fun publish() { rows.value = records.values.filter { it.ownerAccountId == account && it.deletedAt == null } }
        private fun checkAccess(access: WoodDepotAccess) { check(isAccessCurrent(access)) { "WOOD_DEPOT_ACCESS_CHANGED" } }
        fun seed(location: WoodDepotLocation? = null): WoodDepotRecord {
            val record = WoodDepotRecord("00000000-0000-0000-0000-000000000001",
                WoodDepotDraft("TEST - SAVED", WoodDepotKind.ROADSIDE, location = location),
                if (account == null) WoodDepotOwnerScope.LOCAL_DEVICE else WoodDepotOwnerScope.PERSONAL,
                account, 1, 1L, account ?: "anonymous", 1L, account ?: "anonymous")
            records[record.id] = record; publish(); return record
        }
        fun externalUpdate(id: String, name: String): WoodDepotRecord {
            val saved = records.getValue(id).let { it.copy(draft = it.draft.copy(name = name), revision = it.revision + 1) }
            records[id] = saved; publish(); return saved
        }
        override suspend fun getDepot(id: String, access: WoodDepotAccess): WoodDepotRecord? {
            val original = records[id]
            beforeRead(); checkAccess(access)
            return original?.takeIf { it.ownerAccountId == access.accountId && it.deletedAt == null }
        }
        override suspend fun createDepot(id: String, draft: WoodDepotDraft, access: WoodDepotAccess): WoodDepotRecord {
            createAttempts += id to draft; beforeCreate(); checkAccess(access)
            val record = WoodDepotRecord(id, draft,
                if (access.accountId == null) WoodDepotOwnerScope.LOCAL_DEVICE else WoodDepotOwnerScope.PERSONAL,
                access.accountId, 1, 1L, access.accountId ?: "anonymous", 1L, access.accountId ?: "anonymous")
            records[id] = record; publish(); return record
        }
        override suspend fun updateDepot(id: String, expectedRevision: Int, draft: WoodDepotDraft, access: WoodDepotAccess): WoodDepotRecord {
            checkAccess(access)
            val previous = records.getValue(id)
            check(previous.revision == expectedRevision) { "WOOD_DEPOT_REVISION_CONFLICT" }
            val next = previous.copy(draft = draft, revision = previous.revision + 1, updatedAt = 2L)
            records[id] = next; publish(); return next
        }
        override suspend fun deleteDepot(id: String, expectedRevision: Int, access: WoodDepotAccess): WoodDepotRecord {
            deleteCalls++; checkAccess(access)
            val previous = records.getValue(id)
            check(previous.revision == expectedRevision) { "WOOD_DEPOT_REVISION_CONFLICT" }
            val next = previous.copy(revision = previous.revision + 1, updatedAt = 2L, deletedAt = 2L)
            records[id] = next; publish(); return next
        }
    }
}
