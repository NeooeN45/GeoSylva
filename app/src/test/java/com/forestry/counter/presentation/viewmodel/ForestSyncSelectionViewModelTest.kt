package com.forestry.counter.presentation.viewmodel

import androidx.lifecycle.ViewModelStore
import com.forestry.counter.domain.model.HierarchyRecoveryPreview
import com.forestry.counter.domain.model.HierarchyRemoteDossier
import com.forestry.counter.domain.model.HierarchyRemoteMetadata
import com.forestry.counter.domain.model.HierarchySyncCandidate
import com.forestry.counter.domain.model.HierarchySyncProcessResult
import com.forestry.counter.domain.model.HierarchySyncSelection
import com.forestry.counter.domain.model.HierarchySyncSummary
import com.forestry.counter.domain.repository.HierarchySyncRepository
import com.forestry.counter.domain.repository.IdentitySessionGuard
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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** JVM boundary doubles only; no mock data is shipped in the product or uploaded. */
@OptIn(ExperimentalCoroutinesApi::class)
class ForestSyncSelectionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var identity: IdentityRepositoryFake
    private lateinit var repository: TestRepository
    private lateinit var vm: ForestSyncSelectionViewModel
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        identity = IdentityRepositoryFake().apply { session.value = identitySessionFixture() }
        repository = TestRepository().apply { load = { Result.success(selection()) } }
    }
    @After fun cleanup() {
        if (::vm.isInitialized) ViewModelStore().apply { put("forest-sync", vm); clear() }
        Dispatchers.resetMain()
    }
    private fun TestScope.start() { vm = ForestSyncSelectionViewModel(identity, repository, dispatcher); runCurrent() }
    private fun selection() = HierarchySyncSelection(identity.session.value!!.accountId,
        identity.captureSessionGuard(identity.session.value!!.accountId).getOrThrow(),
        listOf(candidate("new"), candidate("active", active = true), candidate("foreign", active = true, blocked = true), candidate("conflict", active = true, state = "CONFLICT")))
    private fun candidate(id: String, active: Boolean = false, blocked: Boolean = false, state: String? = null, error: String? = null) =
        HierarchySyncCandidate(id, "Test forest $id", 2, 3, 4, 5, active, blocked, false, state, error)
    private fun review() { vm.toggleForest("new"); vm.reviewSelection() }
    private fun missingBackupFixture(remote: HierarchyRemoteDossier? = null) {
        repository.load = { Result.success(selection().copy(forests = listOf(
            candidate("missing", active = true, state = "ERROR", error = "HTTP_404")))) }
        repository.recovery = { id -> Result.success(HierarchyRecoveryPreview("opaque-missing", id, "Local test", remote, false)) }
    }

    @Test fun `opening only reads selection and durable status`() = runTest(dispatcher) {
        repository.summary.value = HierarchySyncSummary(activated = 2, pending = 1, conflicts = 1)
        start()
        assertEquals(4, vm.uiState.value.selection!!.forests.size)
        assertEquals(2, vm.uiState.value.summary.activated)
        assertTrue(vm.uiState.value.selectedForestIds.isEmpty())
        assertTrue(repository.activations.isEmpty())
        assertEquals(0, repository.captures.size)
        assertEquals(0, repository.transfers.size)
    }
    @Test fun `review and cancellation do not authorize or upload`() = runTest(dispatcher) {
        start(); review()
        assertEquals(ForestSyncStep.REVIEW, vm.uiState.value.step)
        vm.cancel(); runCurrent()
        assertTrue(vm.uiState.value.closed)
        assertTrue(repository.activations.isEmpty())
        assertTrue(repository.captures.isEmpty())
        assertTrue(repository.transfers.isEmpty())
    }
    @Test fun `confirmation requires review and is single shot`() = runTest(dispatcher) {
        start(); vm.toggleForest("new"); vm.confirmSelection(); runCurrent()
        assertTrue(repository.activations.isEmpty())
        vm.reviewSelection()
        val selection = vm.uiState.value.selection!!
        vm.confirmSelection(); vm.confirmSelection(); runCurrent()
        assertEquals(1, repository.activations.size)
        assertSame(selection, repository.activations.single().first)
        assertEquals(setOf("new"), repository.activations.single().second)
        assertEquals(1, vm.uiState.value.activatedCount)
        assertEquals(ForestSyncStep.COMPLETE, vm.uiState.value.step)
        assertTrue(repository.transfers.isEmpty())
    }
    @Test fun `foreign and already activated dossiers cannot be newly selected`() = runTest(dispatcher) {
        start(); vm.toggleForest("foreign"); vm.toggleForest("active"); vm.toggleForest("unknown")
        assertTrue(vm.uiState.value.selectedForestIds.isEmpty())
        vm.reviewSelection(); vm.confirmSelection(); runCurrent()
        assertTrue(repository.activations.isEmpty())
    }
    @Test fun `account switch invalidates review before a consent mutation`() = runTest(dispatcher) {
        start(); review()
        identity.session.value = identitySessionFixture().copy(accountId = "B")
        // Exercise the direct guard before the session collector gets a turn.
        vm.confirmSelection(); runCurrent()
        assertEquals(ForestSyncError.SESSION_CHANGED, vm.uiState.value.error)
        assertNull(vm.uiState.value.selection)
        assertTrue(repository.activations.isEmpty())
    }
    @Test fun `logout and identical same account login still invalidate the old guard`() = runTest(dispatcher) {
        start(); review()
        val original = identity.session.value
        identity.logout(); identity.session.value = original
        vm.confirmSelection(); runCurrent()
        assertEquals(ForestSyncError.SESSION_CHANGED, vm.uiState.value.error)
        assertTrue(repository.activations.isEmpty())
    }
    @Test fun `late selection read cannot expose old account dossiers`() = runTest(dispatcher) {
        val original = selection()
        val result = CompletableDeferred<Result<HierarchySyncSelection>>()
        repository.load = { withContext(NonCancellable) { result.await() } }
        start()
        identity.logout(); runCurrent()
        result.complete(Result.success(original)); runCurrent()
        assertNull(vm.uiState.value.selection)
        assertEquals(ForestSyncError.SESSION_UNAVAILABLE, vm.uiState.value.error)
        assertTrue(repository.activations.isEmpty())
    }
    @Test fun `cancelling during selection read suppresses late data`() = runTest(dispatcher) {
        val original = selection()
        val result = CompletableDeferred<Result<HierarchySyncSelection>>()
        repository.load = { withContext(NonCancellable) { result.await() } }
        start(); vm.cancel()
        result.complete(Result.success(original)); runCurrent()
        assertTrue(vm.uiState.value.closed)
        assertNull(vm.uiState.value.selection)
        assertTrue(repository.activations.isEmpty())
    }
    @Test fun `late activation receipt never confirms a new session`() = runTest(dispatcher) {
        val receipt = CompletableDeferred<Result<Int>>()
        repository.activate = { _, _ -> withContext(NonCancellable) { receipt.await() } }
        start(); review(); vm.confirmSelection(); runCurrent()
        identity.logout(); runCurrent()
        receipt.complete(Result.success(1)); runCurrent()
        assertEquals(1, repository.activations.size)
        assertNull(vm.uiState.value.activatedCount)
        assertNull(vm.uiState.value.selection)
        assertEquals(ForestSyncError.SESSION_UNAVAILABLE, vm.uiState.value.error)
    }
    @Test fun `explicit retry binds capture and transport to the selected session`() = runTest(dispatcher) {
        start()
        val guard = vm.uiState.value.selection!!.sessionGuard
        vm.synchronizeActivated(); runCurrent()
        assertEquals(1, repository.captures.size)
        assertEquals(1, repository.transfers.size)
        assertSame(guard, repository.captures.single())
        assertSame(guard, repository.transfers.single())
        assertTrue(vm.uiState.value.transferFinished)
        assertTrue(repository.activations.isEmpty())
    }
    @Test fun `logout while capturing prevents subsequent transport`() = runTest(dispatcher) {
        val captured = CompletableDeferred<Int>()
        repository.capture = { withContext(NonCancellable) { captured.await() } }
        start(); vm.synchronizeActivated(); runCurrent()
        identity.logout(); runCurrent()
        captured.complete(1); runCurrent()
        assertEquals(1, repository.captures.size)
        assertTrue(repository.transfers.isEmpty())
        assertNull(vm.uiState.value.selection)
    }
    @Test fun `conflict preview is read only and keeping both never republishes`() = runTest(dispatcher) {
        repository.recovery = { id -> Result.success(HierarchyRecoveryPreview("opaque", id, "Local test", HierarchyRemoteDossier(id, 3, "test-date",
            "{\"forest\":{\"nom\":\"Remote test\"},\"parcelles\":[{}],\"placettes\":[],\"sessions\":[],\"tiges\":[{},{}]}"), false)) }
        start(); vm.previewConflict("conflict"); runCurrent()
        assertEquals("Remote test", vm.uiState.value.previewSummary!!.name)
        assertEquals(2, vm.uiState.value.previewSummary!!.trees)
        assertNotNull(vm.uiState.value.preview)
        vm.dismissPreview(); runCurrent()
        assertNull(vm.uiState.value.preview)
        assertEquals(0, repository.republishes)
        assertTrue(repository.activations.isEmpty())
        assertTrue(repository.transfers.isEmpty())
    }
    @Test fun `a nonconflicting or foreign dossier cannot open recovery`() = runTest(dispatcher) {
        start(); vm.previewConflict("foreign"); vm.previewConflict("active"); vm.previewConflict("new"); runCurrent()
        assertTrue(repository.previews.isEmpty())
    }
    @Test fun `404 preview and cancellation never republish or transfer`() = runTest(dispatcher) {
        missingBackupFixture(); start(); vm.previewConflict("missing"); runCurrent()
        assertTrue(vm.uiState.value.previewAllowsMissingBackupRetry)
        assertNull(vm.uiState.value.preview!!.remote)
        assertEquals(listOf("missing"), repository.previews)
        assertEquals(0, repository.republishes)
        assertTrue(repository.transfers.isEmpty())
        vm.dismissPreview(); vm.confirmMissingBackupRetry(); runCurrent()
        assertFalse(vm.uiState.value.previewAllowsMissingBackupRetry)
        assertEquals(0, repository.republishes)
        assertTrue(repository.transfers.isEmpty())
    }
    @Test fun `only exact activated own ERROR HTTP404 may open missing copy recovery`() = runTest(dispatcher) {
        repository.load = { Result.success(selection().copy(forests = listOf(
            candidate("forbidden", active = true, state = "ERROR", error = "HTTP_403"),
            candidate("unknown", active = true, state = "ERROR"),
            candidate("pending", active = true, state = "PENDING", error = "HTTP_404"),
            candidate("foreign404", active = true, blocked = true, state = "ERROR", error = "HTTP_404"),
            candidate("inactive404", state = "ERROR", error = "HTTP_404")))) }
        start()
        listOf("forbidden", "unknown", "pending", "foreign404", "inactive404").forEach(vm::previewConflict)
        runCurrent()
        assertTrue(repository.previews.isEmpty())
        assertNull(vm.uiState.value.preview)
        assertEquals(0, repository.republishes)
    }
    @Test fun `missing copy requires explicit single confirmation and keeps the same account guard`() = runTest(dispatcher) {
        missingBackupFixture()
        repository.load = { Result.success(selection().copy(forests = listOf(if (repository.republishes == 0)
            candidate("missing", active = true, state = "ERROR", error = "HTTP_404")
            else candidate("missing", active = true, state = "SYNCED")))) }
        start(); vm.previewConflict("missing"); runCurrent()
        val preview = vm.uiState.value.preview!!
        val guard = vm.uiState.value.selection!!.sessionGuard
        assertEquals(0, repository.republishes)
        vm.confirmMissingBackupRetry(); vm.confirmMissingBackupRetry(); runCurrent()
        assertEquals(1, repository.republishes)
        assertSame(preview, repository.republishedPreviews.single())
        assertSame(guard, repository.transfers.single())
        assertTrue(repository.activations.isEmpty())
        assertTrue(repository.captures.isEmpty())
        assertNull(vm.uiState.value.preview)
        assertFalse(vm.uiState.value.previewAllowsMissingBackupRetry)
        assertTrue(vm.uiState.value.transferFinished)
        vm.confirmMissingBackupRetry(); runCurrent()
        assertEquals(1, repository.republishes)
    }
    @Test fun `404 with an existing remote copy stays read only`() = runTest(dispatcher) {
        missingBackupFixture(HierarchyRemoteDossier("missing", 4, "test-date", "{}"))
        start(); vm.previewConflict("missing"); runCurrent()
        assertEquals(4, vm.uiState.value.preview!!.remote!!.serverVersion)
        assertFalse(vm.uiState.value.previewAllowsMissingBackupRetry)
        vm.confirmMissingBackupRetry(); runCurrent()
        assertEquals(0, repository.republishes)
        assertTrue(repository.transfers.isEmpty())
    }
    @Test fun `conflict with no remote copy never grants missing backup confirmation`() = runTest(dispatcher) {
        repository.recovery = { id -> Result.success(HierarchyRecoveryPreview("opaque-conflict", id, "Local test", null, false)) }
        start(); vm.previewConflict("conflict"); runCurrent()
        assertNotNull(vm.uiState.value.preview)
        assertFalse(vm.uiState.value.previewAllowsMissingBackupRetry)
        vm.confirmMissingBackupRetry(); runCurrent()
        assertEquals(0, repository.republishes)
        assertTrue(repository.transfers.isEmpty())
    }
    @Test fun `account switch before confirmation invalidates the missing backup preview`() = runTest(dispatcher) {
        missingBackupFixture(); start(); vm.previewConflict("missing"); runCurrent()
        identity.session.value = identitySessionFixture().copy(accountId = "B")
        vm.confirmMissingBackupRetry(); runCurrent()
        assertEquals(ForestSyncError.SESSION_CHANGED, vm.uiState.value.error)
        assertNull(vm.uiState.value.preview)
        assertEquals(0, repository.republishes)
        assertTrue(repository.transfers.isEmpty())
    }
    @Test fun `account switch between click and coroutine dispatch prevents confirmation`() = runTest(dispatcher) {
        missingBackupFixture(); start(); vm.previewConflict("missing"); runCurrent()
        vm.confirmMissingBackupRetry()
        identity.session.value = identitySessionFixture().copy(accountId = "B")
        runCurrent()
        assertEquals(0, repository.republishes)
        assertTrue(repository.transfers.isEmpty())
        assertNull(vm.uiState.value.preview)
    }
    @Test fun `same account logout login cannot reuse a previous missing backup preview`() = runTest(dispatcher) {
        missingBackupFixture(); start(); vm.previewConflict("missing"); runCurrent()
        val original = identity.session.value
        identity.logout(); identity.session.value = original
        vm.confirmMissingBackupRetry(); runCurrent()
        assertEquals(ForestSyncError.SESSION_CHANGED, vm.uiState.value.error)
        assertEquals(0, repository.republishes)
        assertTrue(repository.transfers.isEmpty())
    }
    @Test fun `late confirmation after logout cannot start transport or display success`() = runTest(dispatcher) {
        missingBackupFixture()
        val result = CompletableDeferred<Result<Unit>>()
        repository.republish = { withContext(NonCancellable) { result.await() } }
        start(); vm.previewConflict("missing"); runCurrent(); vm.confirmMissingBackupRetry(); runCurrent()
        assertEquals(1, repository.republishes)
        identity.logout(); runCurrent()
        result.complete(Result.success(Unit)); runCurrent()
        assertTrue(repository.transfers.isEmpty())
        assertNull(vm.uiState.value.selection)
        assertNull(vm.uiState.value.preview)
        assertFalse(vm.uiState.value.transferFinished)
    }
    @Test fun `a preview for another forest cannot authorize republication`() = runTest(dispatcher) {
        missingBackupFixture()
        repository.recovery = { Result.success(HierarchyRecoveryPreview("opaque-wrong", "other", "Other test", null, false)) }
        start(); vm.previewConflict("missing"); runCurrent(); vm.confirmMissingBackupRetry(); runCurrent()
        assertEquals(ForestSyncError.PREVIEW_FAILED, vm.uiState.value.error)
        assertNull(vm.uiState.value.preview)
        assertFalse(vm.uiState.value.previewAllowsMissingBackupRetry)
        assertEquals(0, repository.republishes)
    }
    @Test fun `expired session never requests selection`() = runTest(dispatcher) {
        identity.session.value = identitySessionFixture().copy(expiresAtEpochSeconds = 1)
        start()
        assertEquals(0, repository.loads)
        assertEquals(ForestSyncError.SESSION_UNAVAILABLE, vm.uiState.value.error)
    }
    @Test fun `failure clears review and requires a fresh selection`() = runTest(dispatcher) {
        repository.activate = { _, _ -> Result.failure(IllegalStateException("boundary failure")) }
        start(); review(); vm.confirmSelection(); runCurrent()
        assertEquals(ForestSyncError.ACTIVATION_FAILED, vm.uiState.value.error)
        assertNull(vm.uiState.value.selection)
        vm.confirmSelection(); runCurrent()
        assertEquals(1, repository.activations.size)
    }
    @Test fun `counts use long arithmetic and missing remote fields stay unknown`() {
        val data = listOf(candidate("A").copy(treeCount = Int.MAX_VALUE), candidate("B").copy(treeCount = Int.MAX_VALUE))
        assertEquals(2L * Int.MAX_VALUE, forestSyncTotals(data).trees)
        assertNull(forestRemoteReadOnlySummary("invalid"))
        assertNull(forestRemoteReadOnlySummary("[]"))
        val partial = forestRemoteReadOnlySummary("{\"forest\":{\"nom\":\"Test\"}}")!!
        assertEquals("Test", partial.name)
        assertNull(partial.trees)
    }

    private class TestRepository : HierarchySyncRepository {
        val summary = MutableStateFlow(HierarchySyncSummary())
        var loads = 0
        lateinit var load: suspend () -> Result<HierarchySyncSelection>
        var activate: suspend (HierarchySyncSelection, Set<String>) -> Result<Int> = { _, ids -> Result.success(ids.size) }
        var capture: suspend () -> Int = { 1 }
        var recovery: suspend (String) -> Result<HierarchyRecoveryPreview> = { Result.failure(IllegalStateException()) }
        var republish: suspend (HierarchyRecoveryPreview) -> Result<Unit> = { Result.success(Unit) }
        val activations = mutableListOf<Pair<HierarchySyncSelection, Set<String>>>()
        val captures = mutableListOf<IdentitySessionGuard?>()
        val transfers = mutableListOf<IdentitySessionGuard?>()
        val previews = mutableListOf<String>()
        var republishes = 0
        val republishedPreviews = mutableListOf<HierarchyRecoveryPreview>()
        override fun observeSummary() = summary
        override suspend fun loadSelection(): Result<HierarchySyncSelection> { loads++; return load() }
        override suspend fun activateSelected(selection: HierarchySyncSelection, forestIds: Set<String>): Result<Int> { activations += selection to forestIds; return activate(selection, forestIds) }
        override suspend fun captureActivated(expected: IdentitySessionGuard?): Int { captures += expected; return capture() }
        override suspend fun processPending(expected: IdentitySessionGuard?): HierarchySyncProcessResult { transfers += expected; return HierarchySyncProcessResult(synchronized = 1) }
        override suspend fun listRemote(): Result<List<HierarchyRemoteMetadata>> = error("Not exposed in selection UI")
        override suspend fun getRemote(forestId: String): Result<HierarchyRemoteDossier?> = error("Use read-only preview")
        override suspend fun prepareRecovery(forestId: String): Result<HierarchyRecoveryPreview> { previews += forestId; return recovery(forestId) }
        override suspend fun confirmRepublishLocal(preview: HierarchyRecoveryPreview): Result<Unit> { republishes++; republishedPreviews += preview; return republish(preview) }
    }
}
