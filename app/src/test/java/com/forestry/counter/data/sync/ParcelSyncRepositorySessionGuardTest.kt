package com.forestry.counter.data.sync

import android.content.Context
import com.forestry.counter.data.local.dao.ParcelleDao
import com.forestry.counter.data.local.dao.ParcelSyncDao
import com.forestry.counter.data.local.dao.ParcelSyncOperationDao
import com.forestry.counter.data.local.entity.ParcelleEntity
import com.forestry.counter.data.local.entity.ParcelSyncEntity
import com.forestry.counter.data.local.entity.ParcelSyncOperationEntity
import java.time.Instant
import com.forestry.counter.data.remote.identity.AccountProfileDto
import com.forestry.counter.data.remote.identity.IdentityApiService
import com.forestry.counter.data.remote.identity.IdentitySessionCoordinator
import com.forestry.counter.data.remote.identity.IdentitySessionSnapshot
import com.forestry.counter.data.remote.identity.IdentitySessionStore
import com.forestry.counter.data.remote.identity.IdentitySessionWrite
import com.forestry.counter.data.remote.identity.StoredIdentityTokens
import com.forestry.counter.data.remote.identity.TokenResponseDto
import com.forestry.counter.data.repository.IdentityRepositoryImpl
import com.forestry.counter.domain.repository.IdentityRepository
import com.forestry.counter.domain.repository.IdentitySessionGuard
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

/** Courses dirigées aux frontières DAO/HTTP, sans compte externe ni jeton de production. */
@OptIn(ExperimentalCoroutinesApi::class)
class ParcelSyncRepositorySessionGuardTest {
    @Test
    fun should_not_send_a_payload_if_account_changes_while_reading_the_immutable_operation() = runTest {
        val f = Fixture()
        val operation = CompletableDeferred<ParcelSyncOperationEntity?>()
        coEvery { f.operationDao.getByOperationId("test-operation-A") } coAnswers { operation.await() }
        val pending = async { f.repository().processPending() }
        runCurrent()
        f.login("B")
        operation.complete(f.originalOperation())
        val result = pending.await()
        assertEquals(0, result.synchronized)
        assertTrue(result.shouldRetry)
        f.assertOriginalOperationRetryable()
        assertTrue(f.pushHeaders.isEmpty())
        coVerify(exactly = 0) { f.identityApi.refresh(any()) }
    }

    @Test
    fun should_not_send_a_payload_after_logout_and_login_of_the_same_account_with_identical_tokens() = runTest {
        val f = Fixture()
        val operation = CompletableDeferred<ParcelSyncOperationEntity?>()
        coEvery { f.operationDao.getByOperationId("test-operation-A") } coAnswers { operation.await() }
        val originalTokens = f.store.read()
        val pending = async { f.repository().processPending() }
        runCurrent()
        f.logout()
        f.login("A", "old")
        assertEquals(originalTokens, f.store.read())
        operation.complete(f.originalOperation())
        assertTrue(pending.await().shouldRetry)
        assertTrue(f.pushHeaders.isEmpty())
        f.assertOriginalOperationRetryable()
    }

    @Test
    fun should_ignore_a_delayed_push_response_after_switching_to_b() = runTest {
        val f = Fixture()
        val response = CompletableDeferred<Response<ParcelSyncResponseDto>>()
        coEvery { f.api.upsert(any(), any(), any()) } coAnswers {
            f.pushHeaders += firstArg<String>()
            response.await()
        }
        val pending = async { f.repository().processPending() }
        runCurrent()
        assertEquals(listOf(header("A", "old")), f.pushHeaders)
        f.login("B")
        response.complete(Response.success(parcelResponse()))
        assertTrue(pending.await().shouldRetry)
        f.assertOriginalOperationRetryable()
        assertFalse(f.queue.containsKey("B" to "parcel-1"))
        coVerify(exactly = 0) { f.operationDao.markSuccessIfClaim(any(), any(), any(), any(), any()) }
    }

    @Test
    fun should_ignore_a_delayed_push_response_after_logout() = runTest {
        val f = Fixture()
        val response = CompletableDeferred<Response<ParcelSyncResponseDto>>()
        coEvery { f.api.upsert(any(), any(), any()) } coAnswers { response.await() }
        val pending = async { f.repository().processPending() }
        runCurrent()
        f.logout()
        response.complete(Response.success(parcelResponse()))
        assertTrue(pending.await().shouldRetry)
        f.assertOriginalOperationRetryable()
        assertNull(f.store.read())
        coVerify(exactly = 0) { f.operationDao.markSuccessIfClaim(any(), any(), any(), any(), any()) }
    }

    @Test
    fun should_resume_the_same_operation_when_a_returns_and_the_original_queue_row_is_due() = runTest {
        val f = Fixture()
        val originalRequest = f.originalOperation().requestJson
        val response = CompletableDeferred<Response<ParcelSyncResponseDto>>()
        coEvery { f.api.upsert(any(), any(), any()) } coAnswers {
            f.pushHeaders += firstArg<String>()
            f.pushRequests += thirdArg<ParcelUpsertRequestDto>()
            response.await()
        }
        val repository = f.repository()
        val pending = async { repository.processPending() }
        runCurrent()
        f.login("B")
        response.complete(Response.success(parcelResponse()))
        assertTrue(pending.await().shouldRetry)
        f.assertOriginalOperationRetryable()
        f.login("A", "returned")
        // Simuler l'échéance du délai ; le request conservé n'est pas reconstruit.
        val retained = f.originalOperation()
        f.operations[retained.operationId] = retained.copy(nextAttemptAt = 0L)
        val resumed = repository.processPending()
        assertEquals(1, resumed.synchronized)
        assertEquals(listOf(header("A", "old"), header("A", "returned")), f.pushHeaders)
        assertEquals(originalRequest, f.originalOperation().requestJson)
        assertEquals("SYNCED", f.originalOperation().state)
        assertEquals(f.pushRequests.first(), f.pushRequests.last())
        assertEquals("SYNCED", f.queue["A" to "parcel-1"]?.state)
        coVerify(exactly = 2) {
            f.api.upsert(any(), "parcel-1", match { it.operationId == "test-operation-A" })
        }
    }

    @Test
    fun should_not_refresh_b_when_account_changes_before_the_401_is_returned() = runTest {
        val f = Fixture()
        coEvery { f.api.upsert(any(), any(), any()) } coAnswers {
            f.pushHeaders += firstArg<String>()
            f.login("B")
            httpError(401)
        }
        assertTrue(f.repository().processPending().shouldRetry)
        assertEquals(listOf(header("A", "old")), f.pushHeaders)
        coVerify(exactly = 0) { f.identityApi.refresh(any()) }
        f.assertOriginalOperationRetryable()
    }

    @Test
    fun should_not_retry_a_push_with_a_refresh_response_for_b() = runTest {
        val f = Fixture()
        coEvery { f.api.upsert(any(), any(), any()) } coAnswers {
            f.pushHeaders += firstArg<String>()
            httpError(401)
        }
        coEvery { f.identityApi.refresh(any()) } returns tokenResponse("B", "foreign")
        val before = f.store.snapshot()
        assertTrue(f.repository().processPending().shouldRetry)
        assertEquals(before, f.store.snapshot())
        assertEquals(listOf(header("A", "old")), f.pushHeaders)
        f.assertOriginalOperationRetryable()
    }

    @Test
    fun should_not_retry_a_push_when_b_logs_in_during_refresh_a() = runTest {
        val f = Fixture()
        val refresh = CompletableDeferred<TokenResponseDto>()
        coEvery { f.api.upsert(any(), any(), any()) } coAnswers {
            f.pushHeaders += firstArg<String>()
            httpError(401)
        }
        coEvery { f.identityApi.refresh(any()) } coAnswers {
            f.refreshTokens += firstArg<com.forestry.counter.data.remote.identity.RefreshRequestDto>().refreshToken
            refresh.await()
        }
        val pending = async { f.repository().processPending() }
        runCurrent()
        assertEquals(listOf("test-refresh-A-old"), f.refreshTokens)
        f.login("B")
        val savedB = f.store.snapshot()
        refresh.complete(tokenResponse("A", "renewed"))
        assertTrue(pending.await().shouldRetry)
        assertEquals(savedB, f.store.snapshot())
        assertEquals(listOf(header("A", "old")), f.pushHeaders)
        f.assertOriginalOperationRetryable()
    }

    @Test
    fun should_refresh_a_and_retry_with_exactly_the_child_access_token() = runTest {
        val f = Fixture()
        coEvery { f.api.upsert(any(), any(), any()) } coAnswers {
            val authorization = firstArg<String>()
            f.pushHeaders += authorization
            if (authorization == header("A", "old")) httpError(401)
            else Response.success(parcelResponse())
        }
        val result = f.repository().processPending()
        assertEquals(1, result.synchronized)
        assertFalse(result.shouldRetry)
        assertEquals(listOf(header("A", "old"), header("A", "renewed")), f.pushHeaders)
        assertEquals(listOf("test-refresh-A-old"), f.refreshTokens)
        assertEquals("SYNCED", f.queue["A" to "parcel-1"]?.state)
        assertEquals(5, f.queue["A" to "parcel-1"]?.serverVersion)
    }

    @Test
    fun should_reject_a_new_login_a_between_refresh_commit_and_transport_adoption() = runTest {
        val f = Fixture()
        coEvery { f.api.upsert(any(), any(), any()) } coAnswers {
            f.pushHeaders += firstArg<String>()
            httpError(401)
        }
        val intercepted = object : IdentityRepository by f.identity {
            override suspend fun refreshSession(expected: IdentitySessionGuard): Result<IdentitySessionGuard> {
                val child = f.identity.refreshSession(expected)
                assertTrue(child.isSuccess)
                f.login("A", "new-login")
                return child
            }
        }
        assertTrue(f.repository(intercepted).processPending().shouldRetry)
        assertEquals(listOf(header("A", "old")), f.pushHeaders)
        assertEquals(tokens("A", "new-login"), f.store.read())
        f.assertOriginalOperationRetryable()
    }

    @Test
    fun should_leave_rows_pending_without_network_when_secure_storage_is_unavailable() = runTest {
        val f = Fixture()
        f.store.available = false
        assertTrue(f.repository().processPending().shouldRetry)
        assertEquals("PENDING", f.queue["A" to "parcel-1"]?.state)
        assertTrue(f.pushHeaders.isEmpty())
        coVerify(exactly = 0) { f.operationDao.claimIfCurrent(any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { f.identityApi.refresh(any()) }
    }

    @Test
    fun should_not_send_when_the_store_subject_disagrees_with_the_display_session() = runTest {
        val f = Fixture()
        f.replaceStoredTokens("B", "foreign") // Conserver volontairement la session d'affichage A.
        assertTrue(f.repository().processPending().shouldRetry)
        assertEquals("PENDING", f.queue["A" to "parcel-1"]?.state)
        assertTrue(f.pushHeaders.isEmpty())
    }

    @Test
    fun should_ignore_a_delayed_pull_response_after_switching_to_b() = runTest {
        val f = Fixture(queued = false)
        val response = CompletableDeferred<Response<GeoSylvaParcelPageDto>>()
        coEvery { f.api.list(any(), any(), any()) } coAnswers {
            f.pullHeaders += firstArg<String>()
            response.await()
        }
        val pending = async { f.repository().pull() }
        runCurrent()
        f.login("B")
        response.complete(Response.success(page()))
        assertTrue(pending.await().isFailure)
        assertEquals(listOf(header("A", "old")), f.pullHeaders)
        f.assertNoParcelWrites()
        assertTrue(f.queue.isEmpty())
    }

    @Test
    fun should_ignore_a_delayed_pull_response_after_logout() = runTest {
        val f = Fixture(queued = false)
        val response = CompletableDeferred<Response<GeoSylvaParcelPageDto>>()
        coEvery { f.api.list(any(), any(), any()) } coAnswers { response.await() }
        val pending = async { f.repository().pull() }
        runCurrent()
        f.logout()
        response.complete(Response.success(page()))
        assertTrue(pending.await().isFailure)
        f.assertNoParcelWrites()
        assertTrue(f.queue.isEmpty())
    }

    @Test
    fun should_recheck_the_session_after_a_suspended_local_merge_read() = runTest {
        val f = Fixture(queued = false)
        val existing = CompletableDeferred<ParcelleEntity?>()
        coEvery { f.parcels.getParcelleByIdAny("parcel-1") } coAnswers { existing.await() }
        val pending = async { f.repository().pull() }
        runCurrent()
        f.login("B")
        existing.complete(null)
        assertTrue(pending.await().isFailure)
        f.assertNoParcelWrites()
        assertTrue(f.queue.isEmpty())
    }

    @Test
    fun should_not_refresh_b_when_pull_a_returns_401_after_the_switch() = runTest {
        val f = Fixture(queued = false)
        coEvery { f.api.list(any(), any(), any()) } coAnswers {
            f.pullHeaders += firstArg<String>()
            f.login("B")
            httpError(401)
        }
        assertTrue(f.repository().pull().isFailure)
        coVerify(exactly = 0) { f.identityApi.refresh(any()) }
        assertEquals(listOf(header("A", "old")), f.pullHeaders)
        f.assertNoParcelWrites()
    }

    @Test
    fun should_pull_with_the_refreshed_child_session_of_a() = runTest {
        val f = Fixture(queued = false)
        f.localParcels.clear()
        coEvery { f.api.list(any(), any(), any()) } coAnswers {
            val authorization = firstArg<String>()
            f.pullHeaders += authorization
            if (authorization == header("A", "old")) httpError(401)
            else Response.success(page())
        }
        val result = f.repository().pull().getOrThrow()
        assertEquals(1, result.inserted)
        assertEquals(listOf(header("A", "old"), header("A", "renewed")), f.pullHeaders)
        assertEquals(listOf("test-refresh-A-old"), f.refreshTokens)
        assertEquals(listOf("Bois du test"), f.parcelWrites.map { it.name })
        assertEquals("SYNCED", f.queue["A" to "parcel-1"]?.state)
    }

    @Test
    fun should_rethrow_cancellation_instead_of_returning_a_pull_failure() = runTest {
        val f = Fixture(queued = false)
        coEvery { f.api.list(any(), any(), any()) } throws CancellationException("test cancellation")
        try {
            f.repository().pull()
            fail("Cancellation must be propagated")
        } catch (_: CancellationException) {
            f.assertNoParcelWrites()
            assertTrue(f.queue.isEmpty())
        }
    }

    @Test
    fun should_rethrow_push_cancellation_without_marking_the_queue_as_a_permanent_error() = runTest {
        val f = Fixture()
        coEvery { f.api.upsert(any(), any(), any()) } throws CancellationException("test cancellation")
        try {
            f.repository().processPending()
            fail("Cancellation must be propagated")
        } catch (_: CancellationException) {
            assertEquals("SYNCING", f.originalOperation().state)
            assertEquals("test-operation-A", f.originalOperation().operationId)
            coVerify(exactly = 0) {
                f.operationDao.markFailureIfClaim(any(), any(), any(), any(), any(), any(), any())
            }
            coVerify(exactly = 0) { f.operationDao.markSuccessIfClaim(any(), any(), any(), any(), any()) }
        }
    }

    @Test
    fun should_not_merge_a_response_returned_by_a_non_cooperative_transport_after_cancellation() = runTest {
        val f = Fixture(queued = false)
        val response = CompletableDeferred<Response<GeoSylvaParcelPageDto>>()
        coEvery { f.api.list(any(), any(), any()) } coAnswers {
            withContext(NonCancellable) { response.await() }
        }
        val pending = async { f.repository().pull() }
        runCurrent()
        pending.cancel()
        response.complete(Response.success(page()))
        pending.join()
        assertTrue(pending.isCancelled)
        f.assertNoParcelWrites()
        assertTrue(f.queue.isEmpty())
    }

    @Test
    fun should_refuse_refresh_if_b_is_current_before_the_guarded_method_captures_a_session() = runTest {
        val f = Fixture()
        val guard = f.identity.captureSessionGuard("A").getOrThrow()
        f.login("B")
        val savedB = f.store.snapshot()
        assertTrue(f.identity.refreshSession(guard).isFailure)
        assertEquals(savedB, f.store.snapshot())
        coVerify(exactly = 0) { f.identityApi.refresh(any()) }
    }

    @Test
    fun should_refuse_refresh_if_b_becomes_current_while_waiting_for_the_refresh_mutex() = runTest {
        val f = Fixture()
        val guard = f.identity.captureSessionGuard("A").getOrThrow()
        f.store.coordinator.refreshMutex.lock()
        val pending = async { f.identity.refreshSession(guard) }
        runCurrent()
        f.login("B")
        val savedB = f.store.snapshot()
        f.store.coordinator.refreshMutex.unlock()
        assertTrue(pending.await().isFailure)
        assertEquals(savedB, f.store.snapshot())
        coVerify(exactly = 0) { f.identityApi.refresh(any()) }
    }

    @Test
    fun should_refuse_the_old_guard_after_logout_and_relogin_a_even_with_identical_tokens() = runTest {
        val f = Fixture()
        val guard = f.identity.captureSessionGuard("A").getOrThrow()
        val originalTokens = f.store.read()
        f.logout()
        f.login("A", "old")
        assertEquals(originalTokens, f.store.read())
        assertFalse(f.identity.isSessionGuardCurrent(guard))
        assertTrue(f.identity.refreshSession(guard).isFailure)
        coVerify(exactly = 0) { f.identityApi.refresh(any()) }
    }

    @Test
    fun should_return_an_exact_child_guard_and_invalidate_it_after_a_new_login_a() = runTest {
        val f = Fixture()
        val parent = f.identity.captureSessionGuard("A").getOrThrow()
        val child = f.identity.refreshSession(parent).getOrThrow()
        assertEquals("A", child.accountId)
        assertTrue(f.identity.isSessionGuardCurrent(child))
        assertFalse(f.identity.isSessionGuardCurrent(parent))
        f.login("A", "renewed") // Mêmes jetons que le refresh, révision et époque différentes.
        assertFalse(f.identity.isSessionGuardCurrent(child))
    }

    @Test
    fun should_refuse_guards_from_an_unrelated_store_or_an_unknown_implementation() = runTest {
        val first = Fixture()
        val second = Fixture()
        val foreign = first.identity.captureSessionGuard("A").getOrThrow()
        assertFalse(second.identity.isSessionGuardCurrent(foreign))
        assertTrue(second.identity.refreshSession(foreign).isFailure)
        val unknown = object : IdentitySessionGuard { override val accountId = "A" }
        assertFalse(second.identity.isSessionGuardCurrent(unknown))
        assertTrue(second.identity.refreshSession(unknown).isFailure)
        coVerify(exactly = 0) { second.identityApi.refresh(any()) }
    }

    @Test
    fun legacy_selection_redacts_a_foreign_hierarchy_without_any_leaf_queue_association() = runTest {
        val f = Fixture(queued = false)
        f.hierarchyOwners["parcel-1"] = listOf("B")
        val candidate = f.repository().loadSelection().getOrThrow().parcels.single()
        assertTrue(candidate.blockedByOtherAccount)
        assertEquals("", candidate.name)
        assertNull(candidate.surfaceHa)
        assertNull(candidate.commune)
        assertFalse(candidate.isLinked)
    }

    @Test
    fun legacy_confirmation_refuses_a_foreign_hierarchy_created_after_preview() = runTest {
        val f = Fixture(queued = false)
        val repository = f.repository()
        val selection = repository.loadSelection().getOrThrow()
        f.hierarchyOwners["parcel-1"] = listOf("B")
        assertTrue(repository.enqueueSelected(selection, setOf("parcel-1")).isFailure)
        assertTrue(f.queue.isEmpty())
        assertTrue(f.operations.isEmpty())
    }

    @Test
    fun legacy_transport_and_pull_refuse_a_foreign_hierarchy_even_with_a_current_leaf_link() = runTest {
        val f = Fixture()
        f.hierarchyOwners["parcel-1"] = listOf("B")
        val repository = f.repository()
        assertEquals(1, repository.processPending().errors)
        assertTrue(f.pushHeaders.isEmpty())
        assertEquals("PARCEL_LINKED_TO_ANOTHER_ACCOUNT", f.originalOperation().lastErrorCode)
        val pull = repository.pull().getOrThrow()
        assertEquals(1, pull.skippedOtherAccount)
        f.assertNoParcelWrites()
    }

    @Test
    fun business_mutation_guard_rejects_same_account_relogin_with_identical_tokens() = runTest {
        val f = Fixture()
        val guard = com.forestry.counter.data.sync.hierarchy.BusinessMutationGuard(f.identity).capture("A")
        assertTrue(guard())
        f.logout(); f.login("A", "old")
        assertFalse(guard())
        val newGuard = com.forestry.counter.data.sync.hierarchy.BusinessMutationGuard(f.identity).capture("A")
        assertTrue(newGuard())
        f.login("B")
        assertFalse(newGuard())
    }

    @Test
    fun business_guest_guard_allows_unlinked_work_only_while_the_device_remains_logged_out() = runTest {
        val f = Fixture()
        f.logout()
        val guard = com.forestry.counter.data.sync.hierarchy.BusinessMutationGuard(f.identity).capture(null)
        assertTrue(guard())
        f.login("A")
        assertFalse(guard())
    }

    @Test
    fun should_offer_current_account_links_and_block_foreign_bindings_without_enabling_sync() = runTest {
        val f = Fixture()
        f.localParcels["parcel-2"] = f.parcel().copy(
            parcelleId = "parcel-2", name = "Other local parcel", surfaceHa = 7.5, nomCommune = "Ville du test",
        )
        f.queue["B" to "parcel-2"] = queuedParcel().copy(accountId = "B", parcelId = "parcel-2")
        val selection = f.repository().loadSelection().getOrThrow()

        assertEquals("A", selection.accountId)
        assertTrue(f.identity.isSessionGuardCurrent(selection.sessionGuard))
        assertTrue(selection.parcels.single { it.parcelId == "parcel-1" }.isLinked)
        assertFalse(selection.parcels.single { it.parcelId == "parcel-1" }.blockedByOtherAccount)
        assertTrue(selection.parcels.single { it.parcelId == "parcel-2" }.blockedByOtherAccount)
        assertFalse(selection.parcels.single { it.parcelId == "parcel-2" }.isLinked)
        val foreignCandidate = selection.parcels.single { it.parcelId == "parcel-2" }
        assertEquals("", foreignCandidate.name)
        assertNull(foreignCandidate.surfaceHa)
        assertNull(foreignCandidate.commune)
        verify(exactly = 0) { f.activation.enable(any()) }
    }

    @Test
    fun should_leave_everything_local_when_the_selection_is_empty() = runTest {
        val f = Fixture(queued = false)
        val repository = f.repository()
        val selection = repository.loadSelection().getOrThrow()
        assertEquals(0, repository.enqueueSelected(selection, emptySet()).getOrThrow())
        assertTrue(f.queue.isEmpty())
        assertEquals(0, f.scheduled)
        verify(exactly = 0) { f.activation.enable(any()) }
        coVerify(exactly = 0) { f.operationDao.append(any()) }
    }

    @Test
    fun should_link_only_the_explicitly_chosen_parcel_and_leave_other_local_parcels_unselected() = runTest {
        val f = Fixture(queued = false)
        f.localParcels["parcel-2"] = f.parcel().copy(parcelleId = "parcel-2")
        val repository = f.repository()
        val selection = repository.loadSelection().getOrThrow()
        assertEquals(1, repository.enqueueSelected(selection, setOf("parcel-1")).getOrThrow())
        assertEquals(setOf("A" to "parcel-1"), f.queue.keys)
        assertEquals("PENDING", f.queue["A" to "parcel-1"]?.state)
        assertEquals(1, f.scheduled)
        verify(exactly = 1) { f.activation.enable("A") }
    }

    @Test
    fun should_reject_an_id_not_offered_in_the_selection() = runTest {
        val f = Fixture(queued = false)
        val repository = f.repository()
        val selection = repository.loadSelection().getOrThrow()
        assertTrue(repository.enqueueSelected(selection, setOf("unoffered")).isFailure)
        assertTrue(f.queue.isEmpty())
        verify(exactly = 0) { f.activation.enable(any()) }
    }

    @Test
    fun should_reject_a_parcel_deleted_after_the_selection_was_loaded() = runTest {
        val f = Fixture(queued = false)
        val repository = f.repository()
        val selection = repository.loadSelection().getOrThrow()
        f.localParcels["parcel-1"] = f.parcel().copy(deletedAt = 1L)
        assertTrue(repository.enqueueSelected(selection, setOf("parcel-1")).isFailure)
        assertTrue(f.queue.isEmpty())
        assertTrue(f.operations.isEmpty())
        assertEquals(0, f.scheduled)
        verify(exactly = 1) { f.activation.enable("A") }
    }

    @Test
    fun should_reject_a_foreign_binding_added_after_the_dialog_was_opened() = runTest {
        val f = Fixture(queued = false)
        val repository = f.repository()
        val selection = repository.loadSelection().getOrThrow()
        val foreign = queuedParcel().copy(accountId = "B")
        f.queue["B" to "parcel-1"] = foreign
        assertTrue(repository.enqueueSelected(selection, setOf("parcel-1")).isFailure)
        assertEquals(mapOf(("B" to "parcel-1") to foreign), f.queue)
        verify(exactly = 1) { f.activation.enable("A") }
    }

    @Test
    fun should_never_reset_any_already_linked_state_when_confirmation_is_repeated() = runTest {
        for (state in listOf("PENDING", "SYNCING", "CONFLICT", "ERROR", "SYNCED")) {
            val f = Fixture()
            val existing = queuedParcel().copy(state = state, retryCount = 4, lastErrorCode = "retained-error")
            f.queue["A" to "parcel-1"] = existing
            val repository = f.repository()
            val selection = repository.loadSelection().getOrThrow()
            assertEquals(0, repository.enqueueSelected(selection, setOf("parcel-1")).getOrThrow())
            assertEquals(existing, f.queue["A" to "parcel-1"])
            assertEquals(0, f.scheduled)
            verify(exactly = 1) { f.activation.enable("A") }
        }
    }

    @Test
    fun should_preserve_a_conflict_created_after_the_fresh_read_before_insert_ignore() = runTest {
        val f = Fixture(queued = false)
        val repository = f.repository()
        val selection = repository.loadSelection().getOrThrow()
        val conflict = queuedParcel().copy(state = "CONFLICT", operationId = "new-conflict")
        coEvery { f.queueDao.insertIfAbsent(any()) } coAnswers {
            f.queue["A" to "parcel-1"] = conflict
            f.insertIfAbsent(firstArg())
        }
        assertEquals(0, repository.enqueueSelected(selection, setOf("parcel-1")).getOrThrow())
        assertEquals(conflict, f.queue["A" to "parcel-1"])
        assertTrue(f.operations.isEmpty())
        assertEquals(0, f.scheduled)
    }

    @Test
    fun should_refuse_confirmation_from_the_previous_account() = runTest {
        val f = Fixture(queued = false)
        val repository = f.repository()
        val selection = repository.loadSelection().getOrThrow()
        f.login("B")
        assertTrue(repository.enqueueSelected(selection, setOf("parcel-1")).isFailure)
        assertTrue(f.queue.isEmpty())
        verify(exactly = 0) { f.activation.enable(any()) }
    }

    @Test
    fun should_refuse_the_selection_after_logout_and_relogin_to_A_with_the_same_tokens() = runTest {
        val f = Fixture(queued = false)
        val repository = f.repository()
        val selection = repository.loadSelection().getOrThrow()
        f.logout()
        f.login("A", "old")
        assertTrue(repository.enqueueSelected(selection, setOf("parcel-1")).isFailure)
        assertTrue(f.queue.isEmpty())
        verify(exactly = 0) { f.activation.enable(any()) }
    }

    @Test
    fun should_recheck_the_session_after_the_confirmation_reads_a_parcel() = runTest {
        val f = Fixture(queued = false)
        val repository = f.repository()
        val selection = repository.loadSelection().getOrThrow()
        val read = CompletableDeferred<ParcelleEntity?>()
        coEvery { f.parcels.getParcelleByIdAny("parcel-1") } coAnswers { read.await() }
        val confirmation = async { repository.enqueueSelected(selection, setOf("parcel-1")) }
        runCurrent()
        f.login("B")
        read.complete(f.parcel())
        assertTrue(confirmation.await().isFailure)
        assertTrue(f.queue.isEmpty())
        assertTrue(f.operations.isEmpty())
        assertEquals(0, f.scheduled)
        // L'activation est un booléen de consentement, jamais une association ou un request.
        verify(exactly = 1) { f.activation.enable("A") }
    }

    @Test
    fun should_keep_post_commit_rows_on_A_without_scheduling_or_rebinding_them_to_B() = runTest {
        val f = Fixture(queued = false)
        val repository = f.repository()
        val selection = repository.loadSelection().getOrThrow()
        coEvery { f.operationDao.runJournalTransaction(any()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
            // Frontière dirigée APRES commit, pas au milieu du bloc transactionnel.
            f.login("B")
        }
        assertTrue(repository.enqueueSelected(selection, setOf("parcel-1")).isFailure)
        assertEquals(setOf("A" to "parcel-1"), f.queue.keys)
        assertEquals("A", f.operations.values.single().originAccountId)
        assertEquals(API_ORIGIN, f.operations.values.single().apiOrigin)
        assertEquals(0, f.scheduled)
    }

    @Test
    fun should_not_enqueue_unselected_local_edits_even_when_account_activation_is_enabled() = runTest {
        val f = Fixture(queued = false)
        val repository = f.repository()
        repository.enqueueUpsert("parcel-1")
        repository.enqueueDelete("parcel-1")
        assertTrue(f.queue.isEmpty())
        assertEquals(0, f.scheduled)
        coVerify(exactly = 0) { f.queueDao.updateLinkedOperation(any(), any(), any(), any(), any()) }
    }

    @Test
    fun should_preserve_conflicts_when_local_edits_or_deletion_occur() = runTest {
        val f = Fixture()
        val conflict = queuedParcel().copy(state = "CONFLICT", lastErrorCode = "SYNC_VERSION_CONFLICT")
        f.queue["A" to "parcel-1"] = conflict
        val operation = f.originalOperation().copy(state = "CONFLICT", lastErrorCode = "SYNC_VERSION_CONFLICT", nextAttemptAt = Long.MAX_VALUE)
        f.operations[operation.operationId] = operation
        val repository = f.repository()
        repository.enqueueUpsert("parcel-1")
        repository.enqueueDelete("parcel-1")
        assertEquals(conflict, f.queue["A" to "parcel-1"])
        assertEquals(operation, f.originalOperation())
        assertEquals(0, f.scheduled)
    }

    @Test
    fun should_not_enqueue_a_local_edit_for_a_legacy_double_binding() = runTest {
        val f = Fixture()
        val original = queuedParcel()
        val originalOperation = f.originalOperation()
        f.queue["B" to "parcel-1"] = original.copy(accountId = "B")
        f.repository().enqueueUpsert("parcel-1")
        assertEquals(original, f.queue["A" to "parcel-1"])
        assertEquals(originalOperation, f.originalOperation())
        assertEquals(1, f.operations.size)
        // Le callback de compatibilité peut réveiller le travail conservé ; il ne crée rien.
        assertEquals(1, f.scheduled)
        assertTrue(f.pushHeaders.isEmpty())
    }

    @Test
    fun should_mark_a_legacy_double_binding_as_permanent_error_without_sending_payload() = runTest {
        val f = Fixture()
        val checkpoint = queuedParcel()
        f.queue["B" to "parcel-1"] = checkpoint.copy(accountId = "B")
        val repository = f.repository()
        val result = repository.processPending()
        assertEquals(1, result.errors)
        assertFalse(result.shouldRetry)
        val retained = f.originalOperation()
        assertEquals("PARCEL_LINKED_TO_ANOTHER_ACCOUNT", retained.lastErrorCode)
        assertEquals(Long.MAX_VALUE, retained.nextAttemptAt)
        assertEquals("test-operation-A", retained.operationId)
        assertEquals(checkpoint, f.queue["A" to "parcel-1"])
        assertTrue(f.pushHeaders.isEmpty())
        assertEquals(0, repository.processPending().errors)
    }

    @Test
    fun should_recheck_foreign_binding_after_the_lease_read_before_http() = runTest {
        val f = Fixture()
        val read = CompletableDeferred<ParcelSyncOperationEntity?>()
        coEvery { f.operationDao.getByOperationId("test-operation-A") } coAnswers { read.await() }
        val pending = async { f.repository().processPending() }
        runCurrent()
        f.queue["B" to "parcel-1"] = queuedParcel().copy(accountId = "B")
        read.complete(f.originalOperation())
        assertEquals(1, pending.await().errors)
        assertTrue(f.pushHeaders.isEmpty())
        assertEquals("PARCEL_LINKED_TO_ANOTHER_ACCOUNT", f.originalOperation().lastErrorCode)
    }

    @Test
    fun should_skip_pull_B_when_the_server_id_is_already_linked_to_A() = runTest {
        val f = Fixture()
        val original = queuedParcel()
        f.login("B")
        val result = f.repository().pull().getOrThrow()
        assertEquals(1, result.skippedOtherAccount)
        assertEquals(0, result.inserted + result.updated + result.deleted)
        assertEquals(original, f.queue["A" to "parcel-1"])
        assertFalse(f.queue.containsKey("B" to "parcel-1"))
        assertEquals(1, f.mergeTransactions)
        f.assertNoParcelWrites()
    }

    @Test
    fun should_recheck_the_session_after_waiting_to_enter_the_pull_transaction() = runTest {
        val f = Fixture(queued = false)
        val enter = CompletableDeferred<Unit>()
        coEvery { f.queueDao.withParcelMergeTransaction(any()) } coAnswers {
            enter.await()
            firstArg<suspend () -> Unit>().invoke()
        }
        val pending = async { f.repository().pull() }
        runCurrent()
        f.login("B")
        enter.complete(Unit)
        assertTrue(pending.await().isFailure)
        assertTrue(f.queue.isEmpty())
        f.assertNoParcelWrites()
    }

    @Test
    fun should_recheck_the_session_after_loading_the_initial_selection() = runTest {
        val f = Fixture(queued = false)
        val loaded = CompletableDeferred<List<ParcelleEntity>>()
        coEvery { f.parcels.getAllParcellesNow() } coAnswers { loaded.await() }
        val pending = async { f.repository().loadSelection() }
        runCurrent()
        f.login("B")
        loaded.complete(listOf(f.parcel()))
        assertTrue(pending.await().isFailure)
        verify(exactly = 0) { f.activation.enable(any()) }
    }

    @Test
    fun should_send_the_stored_request_without_reading_the_current_business_parcel() = runTest {
        val f = Fixture()
        val original = f.originalOperation()
        f.localParcels["parcel-1"] = f.parcel().copy(name = "Version locale plus récente", updatedAt = 300L)
        coEvery { f.parcels.getParcelleById(any()) } throws IllegalStateException("Unexpected business read during send")
        coEvery { f.parcels.getParcelleByIdAny(any()) } throws IllegalStateException("Unexpected business read during send")
        assertEquals(1, f.repository().processPending().synchronized)
        assertEquals(ParcelMutationJournal.upsertRequest(original), f.pushRequests.single())
        assertEquals(original.requestJson, f.originalOperation().requestJson)
        coVerify(exactly = 0) { f.parcels.getParcelleById(any()) }
        coVerify(exactly = 0) { f.parcels.getParcelleByIdAny(any()) }
    }

    @Test
    fun should_ignore_a_success_receipt_for_a_lease_replaced_while_http_was_pending() = runTest {
        val f = Fixture()
        val response = CompletableDeferred<Response<ParcelSyncResponseDto>>()
        coEvery { f.api.upsert(any(), any(), any()) } coAnswers { response.await() }
        val pending = async { f.repository().processPending() }
        runCurrent()
        val checkpoint = requireNotNull(f.queue["A" to "parcel-1"])
        val current = f.originalOperation()
        val replacement = current.copy(attemptId = "newer-lease", lastAttemptAt = System.currentTimeMillis())
        f.operations[current.operationId] = replacement
        response.complete(Response.success(parcelResponse()))
        assertEquals(0, pending.await().synchronized)
        assertEquals(replacement, f.originalOperation())
        assertEquals(checkpoint, f.queue["A" to "parcel-1"])
        coVerify(exactly = 0) { f.queueDao.upsert(any()) }
    }

    @Test
    fun should_keep_version_five_when_an_older_get_returns_after_the_checkpoint_advanced() = runTest {
        val f = Fixture()
        val response = CompletableDeferred<Response<GeoSylvaParcelPageDto>>()
        coEvery { f.api.list(any(), any(), any()) } coAnswers { response.await() }
        val pending = async { f.repository().pull() }
        runCurrent()
        // GET a capturé v4 ; un push concurrent a ensuite validé v5, sans outstanding.
        val checkpoint = queuedParcel().copy(state = "SYNCED", serverVersion = 5, lastSuccessAt = 500L)
        f.queue["A" to "parcel-1"] = checkpoint
        f.operations["test-operation-A"] = f.originalOperation().copy(state = "SYNCED", ackVersion = 5, ackAt = 500L)
        val originalParcel = f.localParcels["parcel-1"]
        val stale = parcelResponse().copy(
            serverVersion = 4,
            parcel = ParcelSyncPayloadDto(name = "Ancienne version serveur", createdAtMs = 100L, updatedAtMs = 150L),
        )
        response.complete(Response.success(page().copy(items = listOf(stale))))
        val result = pending.await().getOrThrow()
        assertEquals(0, result.inserted + result.updated + result.deleted)
        assertEquals(checkpoint, f.queue["A" to "parcel-1"])
        assertEquals(originalParcel, f.localParcels["parcel-1"])
        f.assertNoParcelWrites()
        coVerify(exactly = 0) { f.queueDao.upsert(any()) }
    }

    @Test
    fun should_refuse_pull_from_another_api_origin_without_writing_business_data_or_checkpoint() = runTest {
        val f = Fixture()
        val checkpoint = queuedParcel().copy(state = "SYNCED", serverVersion = 5)
        f.queue["A" to "parcel-1"] = checkpoint
        val original = f.originalOperation().copy(
            apiOrigin = "https://another-gsie.test", state = "SYNCED", ackVersion = 5, ackAt = 500L,
        )
        f.operations[original.operationId] = original
        assertTrue(f.repository().pull().isFailure)
        assertEquals(checkpoint, f.queue["A" to "parcel-1"])
        assertEquals(original, f.originalOperation())
        f.assertNoParcelWrites()
        coVerify(exactly = 0) { f.queueDao.upsert(any()) }
    }

    @Test
    fun should_restart_legacy_only_after_confirmation_with_new_id_observed_base_and_retained_reference() = runTest {
        val f = Fixture()
        f.operations.clear() // Une ancienne queue n'a pas d'instantané transmissible connu.
        val legacy = requireNotNull(f.queue["A" to "parcel-1"])
        val repository = f.repository()
        val selection = repository.loadSelection().getOrThrow()
        val candidate = selection.parcels.single()
        assertTrue(candidate.recoveryRequired)
        assertFalse(candidate.recoveryUnavailable)
        assertEquals(5, candidate.recoveryPreview?.remoteVersion)
        assertTrue(f.operations.isEmpty())
        assertEquals(legacy, f.queue["A" to "parcel-1"])
        assertEquals(0, f.scheduled)

        assertEquals(1, repository.enqueueSelected(selection, setOf("parcel-1")).getOrThrow())
        val operation = f.operations.values.single()
        assertNotEquals(legacy.operationId, operation.operationId)
        assertEquals(5, operation.baseVersion)
        assertEquals(6, operation.expectedServerVersion)
        assertEquals("A", operation.originAccountId)
        assertEquals(API_ORIGIN, operation.apiOrigin)
        assertEquals(f.parcel().toSyncPayload(), ParcelMutationJournal.upsertRequest(operation).parcel)
        assertTrue(requireNotNull(operation.legacyReferenceJson).contains("\"operationId\":\"test-operation-A\""))
        assertEquals(operation.operationId, f.queue["A" to "parcel-1"]?.operationId)
        assertEquals(1, f.scheduled)
        assertTrue(f.pushHeaders.isEmpty())
    }

    @Test
    fun should_leave_legacy_recovery_unavailable_when_the_final_remote_page_is_incomplete() = runTest {
        val f = Fixture()
        f.operations.clear()
        val legacy = requireNotNull(f.queue["A" to "parcel-1"])
        coEvery { f.api.list(any(), any(), any()) } returns Response.success(
            GeoSylvaParcelPageDto(items = emptyList(), page = 1, size = 200, total = 1),
        )
        val repository = f.repository()
        val selection = repository.loadSelection().getOrThrow()
        val candidate = selection.parcels.single()
        assertTrue(candidate.recoveryRequired)
        assertTrue(candidate.recoveryUnavailable)
        assertNull(candidate.recoveryPreview)
        assertTrue(repository.enqueueSelected(selection, setOf("parcel-1")).isFailure)
        assertEquals(legacy, f.queue["A" to "parcel-1"])
        assertTrue(f.operations.isEmpty())
        assertEquals(0, f.scheduled)
        assertTrue(f.pushHeaders.isEmpty())
    }

    @Test
    fun should_reject_a_legacy_preview_after_the_local_snapshot_changed() = runTest {
        val f = Fixture()
        f.operations.clear()
        val legacy = requireNotNull(f.queue["A" to "parcel-1"])
        val repository = f.repository()
        val selection = repository.loadSelection().getOrThrow()
        val changed = f.parcel().copy(name = "Modification après aperçu", updatedAt = 300L)
        f.localParcels["parcel-1"] = changed
        assertTrue(repository.enqueueSelected(selection, setOf("parcel-1")).isFailure)
        assertEquals(changed, f.localParcels["parcel-1"])
        assertEquals(legacy, f.queue["A" to "parcel-1"])
        assertTrue(f.operations.isEmpty())
        assertEquals(0, f.scheduled)
        assertTrue(f.pushHeaders.isEmpty())
    }

    @Test
    fun should_reject_receipts_with_a_different_parcel_version_status_timestamp_or_payload() = runTest {
        val valid = parcelResponse()
        val invalid = listOf(
            valid.copy(clientId = "another-parcel"),
            valid.copy(serverVersion = 4),
            valid.copy(serverVersion = 6),
            valid.copy(status = "deleted", parcel = null),
            valid.copy(clientUpdatedAt = Instant.ofEpochMilli(201L).toString()),
            valid.copy(clientUpdatedAt = null),
            valid.copy(parcel = valid.parcel?.copy(name = "Different server content")),
        )
        invalid.forEach { receipt ->
            val f = Fixture()
            val frozen = f.originalOperation().requestJson
            coEvery { f.api.upsert(any(), any(), any()) } returns Response.success(receipt)
            val result = f.repository().processPending()
            assertEquals(receipt.toString(), 0, result.synchronized)
            assertEquals(1, result.errors)
            assertFalse(result.shouldRetry)
            assertEquals("ERROR", f.originalOperation().state)
            assertEquals(Long.MAX_VALUE, f.originalOperation().nextAttemptAt)
            assertEquals("INVALID_SERVER_RECEIPT", f.originalOperation().lastErrorCode)
            assertEquals(frozen, f.originalOperation().requestJson)
            assertEquals(4, f.queue["A" to "parcel-1"]?.serverVersion)
            coVerify(exactly = 0) { f.operationDao.markSuccessIfClaim(any(), any(), any(), any(), any()) }
        }
    }

    @Test
    fun should_block_a_frozen_request_when_the_configured_api_origin_changes() = runTest {
        val f = Fixture()
        val original = f.originalOperation()
        f.operations[original.operationId] = original.copy(apiOrigin = "https://previous-origin.invalid")
        val result = f.repository().processPending()
        assertEquals(0, result.synchronized)
        assertEquals(1, result.errors)
        assertEquals("BLOCKED_API_ORIGIN", f.originalOperation().state)
        assertEquals("https://previous-origin.invalid", f.originalOperation().apiOrigin)
        assertEquals(original.requestJson, f.originalOperation().requestJson)
        assertTrue(f.pushHeaders.isEmpty())
        coVerify(exactly = 0) { f.api.upsert(any(), any(), any()) }
    }

    @Test
    fun should_retain_and_block_a_successor_after_a_conflict_without_rebasing_it() = runTest {
        val f = Fixture()
        val original = f.originalOperation()
        val successor = ParcelMutationJournal.freeze(
            f.parcel().copy(name = "Next local observation", updatedAt = 300L), "UPSERT", "A",
            API_ORIGIN, 5, original.operationId, "PENDING", 300L, "successor-operation",
        ).copy(sequence = 2L, nextAttemptAt = 0L)
        f.operations[successor.operationId] = successor
        coEvery { f.api.upsert(any(), any(), any()) } returns httpError(409)
        val result = f.repository().processPending()
        assertEquals(1, result.conflicts)
        assertEquals("CONFLICT", f.originalOperation().state)
        val retained = requireNotNull(f.operations[successor.operationId])
        assertEquals("BLOCKED_PREDECESSOR", retained.state)
        assertEquals(successor.requestJson, retained.requestJson)
        assertEquals(successor.baseVersion, retained.baseVersion)
        assertEquals(successor.predecessorOperationId, retained.predecessorOperationId)
        coVerify(exactly = 1) { f.api.upsert(any(), "parcel-1", any()) }
    }

    @Test
    fun should_reject_incomplete_or_malformed_pull_pages_without_writing_their_items() = runTest {
        val valid = parcelResponse()
        val invalidPages = listOf(
            page().copy(page = 2),
            page().copy(size = 0),
            page().copy(total = -1),
            page().copy(items = emptyList()),
            page().copy(items = listOf(valid, valid), total = 2),
            page().copy(items = listOf(valid.copy(serverVersion = 0))),
            page().copy(items = listOf(valid.copy(status = "unknown"))),
            page().copy(items = listOf(valid.copy(status = "deleted"))),
            page().copy(items = listOf(valid.copy(parcel = null))),
        )
        invalidPages.forEach { badPage ->
            val f = Fixture(queued = false)
            coEvery { f.api.list(any(), any(), any()) } returns Response.success(badPage)
            assertTrue(badPage.toString(), f.repository().pull().isFailure)
            f.assertNoParcelWrites()
            assertTrue(f.queue.isEmpty())
        }
    }

    private class TestStore : IdentitySessionStore(IdentitySessionCoordinator()) {
        var state = IdentitySessionSnapshot("initial", tokens("A", "old"))
        var available = true
        override fun readState() = state.takeIf { available }
        override fun writeState(state: IdentitySessionSnapshot): Boolean {
            if (!available) return false
            this.state = state
            return true
        }
    }

    private class Fixture(queued: Boolean = true) {
        val store = TestStore()
        val identityApi = mockk<IdentityApiService>()
        val identity: IdentityRepositoryImpl
        val api = mockk<ParcelSyncApiService>()
        val queueDao = mockk<ParcelSyncDao>()
        val operationDao = mockk<ParcelSyncOperationDao>()
        val parcels = mockk<ParcelleDao>()
        private val context = mockk<Context>()
        val activation = mockk<ParcelSyncActivationStore>()
        val hierarchyOwners = mutableMapOf<String, List<String>>()
        val queue = linkedMapOf<Pair<String, String>, ParcelSyncEntity>()
        val operations = linkedMapOf<String, ParcelSyncOperationEntity>()
        val pushHeaders = mutableListOf<String>()
        val pushRequests = mutableListOf<ParcelUpsertRequestDto>()
        val deleteRequests = mutableListOf<ParcelDeleteRequestDto>()
        val pullHeaders = mutableListOf<String>()
        val refreshTokens = mutableListOf<String>()
        val parcelWrites = mutableListOf<ParcelleEntity>()
        val localParcels = linkedMapOf<String, ParcelleEntity>()
        var scheduled = 0
        var mergeTransactions = 0

        init {
            every { context.applicationContext } returns context
            every { activation.isEnabled(any()) } returns true
            every { activation.enable(any()) } returns true
            coEvery { identityApi.refresh(any()) } coAnswers {
                refreshTokens += firstArg<com.forestry.counter.data.remote.identity.RefreshRequestDto>().refreshToken
                tokenResponse("A", "renewed")
            }
            coEvery { identityApi.profile(any()) } returns AccountProfileDto(
                accountId = "A", emailVerified = false, providers = listOf("local"), roles = listOf("user"),
            )
            identity = IdentityRepositoryImpl(identityApi, "$API_ORIGIN/", false, store)
            localParcels["parcel-1"] = parcel()
            if (queued) {
                queue["A" to "parcel-1"] = queuedParcel()
                val original = ParcelMutationJournal.freeze(
                    parcel(), "UPSERT", "A", API_ORIGIN, 4, null, "PENDING", 100L, "test-operation-A",
                ).copy(sequence = 1L, nextAttemptAt = 0L)
                operations[original.operationId] = original
            }
            coEvery { queueDao.get(any(), any()) } coAnswers {
                queue[firstArg<String>() to secondArg<String>()]
            }
            coEvery { queueDao.getAccountIdsForParcel(any()) } coAnswers {
                val parcelId = firstArg<String>()
                queue.values.filter { it.parcelId == parcelId }.map { it.accountId }
            }
            coEvery { queueDao.getHierarchyAccountsForParcel(any()) } coAnswers { hierarchyOwners[firstArg<String>()].orEmpty() }
            coEvery { queueDao.getHierarchyAccountsForForest(any()) } returns emptyList()
            coEvery { queueDao.getLegacyRecoverable(any()) } coAnswers {
                val accountId = firstArg<String>()
                queue.values.filter {
                    it.accountId == accountId && it.state !in setOf("SYNCED", "CONFLICT") &&
                        it.operationId !in operations
                }
            }
            coEvery { queueDao.countLiveParcel(any()) } coAnswers {
                val id = firstArg<String>()
                if (id in localParcels && localParcels[id]?.deletedAt == null) 1 else 0
            }
            coEvery { queueDao.insertIfAbsent(any()) } coAnswers { insertIfAbsent(firstArg()) }
            coEvery { queueDao.upsert(any()) } coAnswers {
                val row = firstArg<ParcelSyncEntity>()
                queue[row.accountId to row.parcelId] = row
                Unit
            }
            coEvery { queueDao.withParcelMergeTransaction(any()) } coAnswers {
                mergeTransactions += 1
                firstArg<suspend () -> Unit>().invoke()
            }
            coEvery { operationDao.append(any()) } coAnswers {
                val row = firstArg<ParcelSyncOperationEntity>()
                check(row.operationId !in operations) { "DUPLICATE_OPERATION_ID" }
                val sequence = (operations.values.maxOfOrNull { it.sequence } ?: 0L) + 1L
                operations[row.operationId] = row.copy(sequence = sequence)
                sequence
            }
            coEvery { operationDao.getByOperationId(any()) } coAnswers { operations[firstArg<String>()] }
            coEvery { operationDao.getLastActiveForParcel(any(), any()) } coAnswers {
                val accountId = firstArg<String>()
                val parcelId = secondArg<String>()
                operations.values.filter {
                    it.originAccountId == accountId && it.parcelId == parcelId &&
                        it.state !in setOf("LOCAL_ONLY", "SUPERSEDED")
                }.maxByOrNull { it.sequence }
            }
            coEvery { operationDao.hasOutstanding(any(), any()) } coAnswers {
                val accountId = firstArg<String>()
                val parcelId = secondArg<String>()
                operations.values.any {
                    it.originAccountId == accountId && it.parcelId == parcelId &&
                        it.state !in setOf("LOCAL_ONLY", "SYNCED", "SUPERSEDED")
                }
            }
            coEvery { operationDao.getPendingCountForAccount(any()) } coAnswers {
                val accountId = firstArg<String>()
                operations.values.count {
                    it.originAccountId == accountId && (
                        it.state in setOf("PENDING", "SYNCING") ||
                            (it.state == "ERROR" && it.nextAttemptAt != Long.MAX_VALUE)
                        )
                }
            }
            coEvery { operationDao.getReady(any(), any(), any(), any()) } coAnswers {
                val accountId = firstArg<String>()
                val now = secondArg<Long>()
                val staleBefore = thirdArg<Long>()
                operations.values.filter { row ->
                    row.originAccountId == accountId && row.destinationKind == "PERSONAL_ACCOUNT" &&
                        row.apiOrigin != null && row.expectedServerVersion != null && isFifoHead(row) && (
                        (row.state in setOf("PENDING", "ERROR") && row.nextAttemptAt <= now) ||
                            (row.state == "SYNCING" && row.lastAttemptAt?.let { it <= staleBefore } == true)
                        )
                }.sortedBy { it.sequence }.take(arg<Int>(3))
            }
            coEvery { operationDao.claimIfCurrent(any(), any(), any(), any(), any(), any(), any()) } coAnswers {
                val row = operations[firstArg<String>()]
                if (row == null || row.originAccountId != secondArg<String>() ||
                    row.state != thirdArg<String>() || row.lastAttemptAt != arg<Long?>(3) ||
                    row.attemptId != arg<String?>(4) || !isFifoHead(row)
                ) 0 else {
                    operations[row.operationId] = row.copy(state = "SYNCING", lastAttemptAt = arg(5), attemptId = arg(6))
                    1
                }
            }
            coEvery { operationDao.markSuccessIfClaim(any(), any(), any(), any(), any()) } coAnswers {
                val row = operations[firstArg<String>()]
                if (row == null || row.originAccountId != secondArg<String>() || row.state != "SYNCING" ||
                    row.attemptId != thirdArg<String>() || row.expectedServerVersion != arg<Int>(3)
                ) 0 else {
                    operations[row.operationId] = row.copy(
                        state = "SYNCED", retryCount = 0, nextAttemptAt = arg(4), lastErrorCode = null,
                        ackVersion = arg(3), ackAt = arg(4),
                    )
                    1
                }
            }
            coEvery { operationDao.markFailureIfClaim(any(), any(), any(), any(), any(), any(), any()) } coAnswers {
                val row = operations[firstArg<String>()]
                if (row == null || row.originAccountId != secondArg<String>() || row.state != "SYNCING" ||
                    row.attemptId != thirdArg<String>()
                ) 0 else {
                    operations[row.operationId] = row.copy(
                        state = arg(3), retryCount = arg(4), nextAttemptAt = arg(5), lastErrorCode = arg(6),
                    )
                    1
                }
            }
            coEvery { operationDao.blockFollowing(any(), any(), any(), any()) } coAnswers {
                val accountId = firstArg<String>()
                val parcelId = secondArg<String>()
                val sequence = thirdArg<Long>()
                val affected = operations.values.filter {
                    it.originAccountId == accountId && it.parcelId == parcelId && it.sequence > sequence &&
                        it.state !in setOf("LOCAL_ONLY", "SYNCED", "SUPERSEDED")
                }
                affected.forEach { operations[it.operationId] = it.copy(state = "BLOCKED_PREDECESSOR", lastErrorCode = arg(3)) }
                affected.size
            }
            coEvery { operationDao.supersedeOutstandingForParcel(any(), any(), any()) } coAnswers {
                val accountId = firstArg<String>()
                val parcelId = secondArg<String>()
                val affected = operations.values.filter {
                    it.originAccountId == accountId && it.parcelId == parcelId &&
                        it.state !in setOf("LOCAL_ONLY", "SYNCED", "SUPERSEDED", "CONFLICT")
                }
                affected.forEach { operations[it.operationId] = it.copy(state = "SUPERSEDED", nextAttemptAt = arg(2)) }
                affected.size
            }
            coEvery { operationDao.runJournalTransaction(any()) } coAnswers {
                // Double dirigé avec rollback : ne constitue pas une preuve SQL/Room.
                mergeTransactions += 1
                val beforeQueue = queue.toMap()
                val beforeOperations = operations.toMap()
                val beforeParcels = localParcels.toMap()
                val beforeWrites = parcelWrites.toList()
                try {
                    firstArg<suspend () -> Unit>().invoke()
                } catch (failure: Throwable) {
                    queue.clear(); queue.putAll(beforeQueue)
                    operations.clear(); operations.putAll(beforeOperations)
                    localParcels.clear(); localParcels.putAll(beforeParcels)
                    parcelWrites.clear(); parcelWrites.addAll(beforeWrites)
                    throw failure
                }
            }
            coEvery { parcels.getAllParcellesNow() } coAnswers { localParcels.values.filter { it.deletedAt == null } }
            coEvery { parcels.getParcelleById(any()) } coAnswers { localParcels[firstArg<String>()]?.takeIf { it.deletedAt == null } }
            coEvery { parcels.getParcelleByIdAny(any()) } coAnswers { localParcels[firstArg<String>()] }
            coEvery { parcels.insertParcelle(any()) } coAnswers {
                val parcel = firstArg<ParcelleEntity>()
                parcelWrites += parcel
                localParcels[parcel.parcelleId] = parcel
                Unit
            }
            coEvery { parcels.updateParcelle(any()) } coAnswers {
                val parcel = firstArg<ParcelleEntity>()
                parcelWrites += parcel
                localParcels[parcel.parcelleId] = parcel
                Unit
            }
            coEvery { parcels.deleteParcelleById(any(), any()) } coAnswers {
                val id = firstArg<String>()
                localParcels[id]?.let { localParcels[id] = it.copy(deletedAt = secondArg<Long>()) }
                Unit
            }
            coEvery { api.upsert(any(), any(), any()) } coAnswers {
                pushHeaders += firstArg<String>()
                val request = thirdArg<ParcelUpsertRequestDto>()
                pushRequests += request
                Response.success(successfulUpsert(request, secondArg()))
            }
            coEvery { api.delete(any(), any(), any()) } coAnswers {
                pushHeaders += firstArg<String>()
                val request = thirdArg<ParcelDeleteRequestDto>()
                deleteRequests += request
                Response.success(ParcelSyncResponseDto(
                    clientId = secondArg(), status = "deleted", serverVersion = (request.baseVersion ?: 0) + 1,
                    clientUpdatedAt = request.clientUpdatedAt, parcel = null,
                ))
            }
            coEvery { api.list(any(), any(), any()) } coAnswers {
                pullHeaders += firstArg<String>()
                Response.success(page())
            }
        }

        fun repository(account: IdentityRepository = identity) = ParcelSyncRepositoryImpl(
            context = context, syncDao = queueDao, parcelleDao = parcels, operationDao = operationDao,
            mutationJournal = ParcelMutationJournal(parcels, queueDao, operationDao, API_ORIGIN, { identity.session.value?.accountId }),
            apiOrigin = API_ORIGIN, identityRepository = account, api = api, sessionStore = store,
            activationStore = activation, scheduleWork = { scheduled += 1 },
        )

        fun insertIfAbsent(rows: List<ParcelSyncEntity>): List<Long> = rows.map { row ->
            val key = row.accountId to row.parcelId
            if (key in queue) -1L else { queue[key] = row; 1L }
        }

        private fun isFifoHead(row: ParcelSyncOperationEntity): Boolean {
            val predecessor = row.predecessorOperationId?.let { operations[it] }
            if (row.predecessorOperationId != null &&
                (predecessor?.state != "SYNCED" || predecessor?.ackVersion != row.baseVersion)
            ) return false
            return operations.values.none {
                it.originAccountId == row.originAccountId && it.parcelId == row.parcelId &&
                    it.sequence < row.sequence && it.state !in setOf("LOCAL_ONLY", "SYNCED", "SUPERSEDED")
            }
        }

        fun replaceStoredTokens(account: String, version: String) {
            val reserved = requireNotNull(store.reserveAuthentication())
            assertTrue(store.saveIfCurrent(reserved, tokens(account, version)) is IdentitySessionWrite.Saved)
        }

        fun login(account: String, version: String = "login") {
            replaceStoredTokens(account, version)
            identity.reloadStoredSession()
        }

        fun logout() {
            assertTrue(store.clear())
            identity.reloadStoredSession()
        }

        fun parcel(): ParcelleEntity = requireNotNull(parcelResponse().toParcelleEntity(null))
        fun originalOperation(): ParcelSyncOperationEntity = requireNotNull(operations["test-operation-A"])

        fun assertOriginalOperationRetryable() {
            val row = originalOperation()
            assertEquals("test-operation-A", row.operationId)
            assertEquals("A", row.originAccountId)
            assertEquals(API_ORIGIN, row.apiOrigin)
            assertEquals("ERROR", row.state)
            assertTrue(row.nextAttemptAt < Long.MAX_VALUE)
            assertTrue(row.nextAttemptAt > row.createdAt)
        }

        fun assertNoParcelWrites() {
            assertTrue(parcelWrites.isEmpty())
            coVerify(exactly = 0) { parcels.insertParcelle(any()) }
            coVerify(exactly = 0) { parcels.updateParcelle(any()) }
            coVerify(exactly = 0) { parcels.deleteParcelleById(any(), any()) }
        }
    }

    private companion object {
        const val API_ORIGIN = "https://gsie.test"

        fun queuedParcel() = ParcelSyncEntity(
            accountId = "A", parcelId = "parcel-1", operation = "UPSERT", operationId = "test-operation-A",
            state = "PENDING", serverVersion = 4, retryCount = 0, queuedAt = 100L,
            lastAttemptAt = null, lastSuccessAt = null, nextAttemptAt = 0L, lastErrorCode = null,
        )

        fun parcelResponse() = ParcelSyncResponseDto(
            clientId = "parcel-1", status = "active", serverVersion = 5,
            clientUpdatedAt = Instant.ofEpochMilli(200L).toString(),
            parcel = ParcelSyncPayloadDto(name = "Bois du test", createdAtMs = 100L, updatedAtMs = 200L),
        )

        fun successfulUpsert(request: ParcelUpsertRequestDto, clientId: String = "parcel-1") = ParcelSyncResponseDto(
            clientId = clientId, status = "active", serverVersion = (request.baseVersion ?: 0) + 1,
            clientUpdatedAt = request.clientUpdatedAt, parcel = request.parcel,
        )

        fun page() = GeoSylvaParcelPageDto(items = listOf(parcelResponse()), page = 1, size = 200, total = 1)

        fun <T> httpError(code: Int): Response<T> =
            Response.error(code, "{}".toResponseBody("application/json".toMediaType()))

        fun tokens(account: String, version: String) = StoredIdentityTokens(
            token(account, version), "test-refresh-$account-$version",
        )

        fun tokenResponse(account: String, version: String) = TokenResponseDto(
            accessToken = token(account, version), refreshToken = "test-refresh-$account-$version",
            tokenType = "bearer", expiresIn = 900,
        )

        fun header(account: String, version: String) = "Bearer ${token(account, version)}"

        fun token(account: String, version: String): String {
            val payload = """{"sub":"$account","exp":4102444800,"auth_provider":"local","roles":["user"],"jti":"test-only-$version"}"""
            return "test-header." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.toByteArray(Charsets.UTF_8)) + ".test-signature"
        }
    }
}
