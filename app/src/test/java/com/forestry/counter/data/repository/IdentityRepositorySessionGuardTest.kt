package com.forestry.counter.data.repository

import com.forestry.counter.data.remote.identity.AccountProfileDto
import com.forestry.counter.data.remote.identity.IdentityApiService
import com.forestry.counter.data.remote.identity.IdentitySessionCoordinator
import com.forestry.counter.data.remote.identity.IdentitySessionSnapshot
import com.forestry.counter.data.remote.identity.IdentitySessionStore
import com.forestry.counter.data.remote.identity.IdentitySessionWrite
import com.forestry.counter.data.remote.identity.LoginOutcomeDto
import com.forestry.counter.data.remote.identity.LogoutResponseDto
import com.forestry.counter.data.remote.identity.StoredIdentityTokens
import com.forestry.counter.data.remote.identity.TokenResponseDto
import com.forestry.counter.domain.model.IdentityClientException
import com.forestry.counter.domain.model.IdentityError
import com.forestry.counter.domain.model.LoginOutcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.Base64
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import retrofit2.HttpException

/** Réponses différées et stockage réellement partagé ; aucun compte/jeton externe. */
@OptIn(ExperimentalCoroutinesApi::class)
class IdentityRepositorySessionGuardTest {
    private class SharedState(tokens: StoredIdentityTokens? = null) {
        val coordinator = IdentitySessionCoordinator()
        var state = IdentitySessionSnapshot("initial", tokens)
        var writable = true
        var mutateMemoryOnFailure = false
    }

    private class TestStore(private val shared: SharedState) : IdentitySessionStore(shared.coordinator) {
        override fun readState() = shared.state
        override fun writeState(state: IdentitySessionSnapshot): Boolean {
            if (!shared.writable) {
                if (shared.mutateMemoryOnFailure) shared.state = state
                return false
            }
            shared.state = state
            return true
        }
    }

    @Test
    fun should_refresh_me_once_and_return_the_exact_child_guard() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val api = api()
        coEvery { api.profile("Bearer ${token("A", "old")}") } throws httpFailure(401)
        coEvery { api.refresh(any()) } returns response("A", "renewed")
        val repository = repository(api, TestStore(shared))
        val parent = repository.captureSessionGuard("A").getOrThrow()

        val loaded = repository.loadProfile(parent)

        assertTrue(loaded.result.isSuccess)
        assertEquals("A", loaded.result.getOrThrow().accountId)
        assertFalse(repository.isSessionGuardCurrent(parent))
        assertTrue(repository.isSessionGuardCurrent(loaded.sessionGuard))
        assertTrue(repository.isSessionGuardRenewalCurrent(parent))
        coVerify(exactly = 1) { api.refresh(match { it.refreshToken == tokens("A", "old").refreshToken }) }
        coVerify(exactly = 1) { api.profile("Bearer ${token("A", "renewed")}") }
        coVerify(exactly = 2) { api.profile(any()) }
    }

    @Test
    fun should_return_the_current_child_guard_when_the_me_retry_is_503() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val api = api()
        coEvery { api.profile("Bearer ${token("A", "old")}") } throws httpFailure(401)
        coEvery { api.profile("Bearer ${token("A", "renewed")}") } throws httpFailure(503)
        coEvery { api.refresh(any()) } returns response("A", "renewed")
        val repository = repository(api, TestStore(shared))
        val parent = repository.captureSessionGuard("A").getOrThrow()

        val loaded = repository.loadProfile(parent)

        assertError(IdentityError.SERVER_UNAVAILABLE, loaded.result)
        assertTrue(repository.isSessionGuardCurrent(loaded.sessionGuard))
        assertTrue(repository.isSessionGuardRenewalCurrent(parent))
        assertEquals(tokens("A", "renewed"), shared.state.tokens)
        assertEquals("A", repository.session.value?.accountId)
        coVerify(exactly = 1) { api.refresh(any()) }
        coVerify(exactly = 2) { api.profile(any()) }
    }

    @Test
    fun should_preserve_the_session_without_refresh_when_me_is_503() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val before = shared.state
        val api = api()
        coEvery { api.profile(any()) } throws httpFailure(503)
        val repository = repository(api, TestStore(shared))

        assertError(IdentityError.SERVER_UNAVAILABLE, repository.loadProfile())

        assertEquals(before, shared.state)
        assertEquals("A", repository.session.value?.accountId)
        coVerify(exactly = 0) { api.refresh(any()) }
    }

    @Test
    fun should_preserve_the_session_without_refresh_when_me_is_offline() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val before = shared.state
        val api = api()
        coEvery { api.profile(any()) } throws IOException("JVM offline")
        val repository = repository(api, TestStore(shared))

        assertError(IdentityError.NETWORK_UNAVAILABLE, repository.loadProfile())

        assertEquals(before, shared.state)
        assertEquals("A", repository.session.value?.accountId)
        coVerify(exactly = 0) { api.refresh(any()) }
    }

    @Test
    fun should_preserve_the_original_session_when_refresh_is_503() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val before = shared.state
        val api = api()
        coEvery { api.profile(any()) } throws httpFailure(401)
        coEvery { api.refresh(any()) } throws httpFailure(503)
        val repository = repository(api, TestStore(shared))

        assertError(IdentityError.SERVER_UNAVAILABLE, repository.loadProfile())

        assertEquals(before, shared.state)
        assertEquals("A", repository.session.value?.accountId)
        coVerify(exactly = 1) { api.profile(any()) }
    }

    @Test
    fun should_preserve_the_original_session_when_refresh_is_offline() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val before = shared.state
        val api = api()
        coEvery { api.profile(any()) } throws httpFailure(401)
        coEvery { api.refresh(any()) } throws IOException("JVM offline")
        val repository = repository(api, TestStore(shared))

        assertError(IdentityError.NETWORK_UNAVAILABLE, repository.loadProfile())

        assertEquals(before, shared.state)
        assertEquals("A", repository.session.value?.accountId)
        coVerify(exactly = 1) { api.profile(any()) }
    }

    @Test
    fun should_require_reconnection_when_the_refresh_token_is_rejected() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val api = api()
        coEvery { api.profile(any()) } throws httpFailure(401)
        coEvery { api.refresh(any()) } throws httpFailure(401)
        val repository = repository(api, TestStore(shared))

        assertError(IdentityError.INVALID_CREDENTIALS, repository.loadProfile())

        assertNull(shared.state.tokens)
        assertNull(repository.session.value)
        assertNull(repository.profile.value)
        coVerify(exactly = 1) { api.refresh(any()) }
    }

    @Test
    fun should_stop_after_one_refresh_when_the_child_me_is_also_401() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val api = api()
        coEvery { api.profile(any()) } throws httpFailure(401)
        coEvery { api.refresh(any()) } returns response("A", "renewed")
        val repository = repository(api, TestStore(shared))

        assertError(IdentityError.INVALID_CREDENTIALS, repository.loadProfile())

        assertNull(shared.state.tokens)
        assertNull(repository.session.value)
        coVerify(exactly = 1) { api.refresh(any()) }
        coVerify(exactly = 2) { api.profile(any()) }
    }

    @Test
    fun should_reject_a_foreign_profile_after_refresh_without_publishing_it() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val api = api()
        coEvery { api.profile("Bearer ${token("A", "old")}") } throws httpFailure(401)
        coEvery { api.profile("Bearer ${token("A", "renewed")}") } returns profile("B")
        coEvery { api.refresh(any()) } returns response("A", "renewed")
        val repository = repository(api, TestStore(shared))

        assertError(IdentityError.INVALID_SERVER_RESPONSE, repository.loadProfile())

        assertNull(repository.profile.value)
        assertNull(shared.state.tokens)
        coVerify(exactly = 1) { api.refresh(any()) }
    }

    @Test
    fun should_reject_a_foreign_refresh_subject_without_loading_its_profile() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val api = api()
        coEvery { api.profile(any()) } throws httpFailure(401)
        coEvery { api.refresh(any()) } returns response("B", "foreign")
        val repository = repository(api, TestStore(shared))

        assertError(IdentityError.INVALID_SERVER_RESPONSE, repository.loadProfile())

        assertNull(shared.state.tokens)
        assertNull(repository.profile.value)
        coVerify(exactly = 1) { api.profile(any()) }
    }

    @Test
    fun should_not_refresh_a_late_me_401_after_logout() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val api = api()
        val delayed = CompletableDeferred<AccountProfileDto>()
        coEvery { api.profile(any()) } coAnswers { delayed.await() }
        coEvery { api.logout(any()) } returns Response.success(LogoutResponseDto(true))
        val repository = repository(api, TestStore(shared))
        val pending = async { repository.loadProfile() }
        runCurrent()
        assertTrue(repository.logout().isSuccess)
        delayed.completeExceptionally(httpFailure(401))

        assertError(IdentityError.CANCELLED, pending.await())
        assertNull(shared.state.tokens)
        coVerify(exactly = 0) { api.refresh(any()) }
    }

    @Test
    fun should_not_refresh_a_reauthenticated_same_account_even_with_identical_tokens() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val api = api()
        val delayed = CompletableDeferred<AccountProfileDto>()
        coEvery { api.profile(any()) } coAnswers { delayed.await() }
        val repository = repository(api, TestStore(shared))
        val original = shared.state
        val pending = async { repository.loadProfile() }
        runCurrent()
        coEvery { api.profile(any()) } returns profile("A")
        coEvery { api.loginWithPassword(any()) } returns outcome("A", "old")
        assertTrue(repository.loginWithPassword("a@example.invalid", "test-password").isSuccess)
        val reauthenticated = shared.state
        assertEquals(original.tokens, reauthenticated.tokens)
        assertNotEquals(original.revision, reauthenticated.revision)
        assertNotEquals(original.epoch, reauthenticated.epoch)
        delayed.completeExceptionally(httpFailure(401))

        assertError(IdentityError.CANCELLED, pending.await())
        assertEquals(reauthenticated, shared.state)
        assertEquals("A", repository.profile.value?.accountId)
        coVerify(exactly = 0) { api.refresh(any()) }
    }

    @Test
    fun should_not_refresh_the_final_a_session_after_a_b_a_authentication() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val api = api()
        val delayed = CompletableDeferred<AccountProfileDto>()
        coEvery { api.profile(any()) } coAnswers { delayed.await() }
        val repository = repository(api, TestStore(shared))
        val pending = async { repository.loadProfile() }
        runCurrent()
        coEvery { api.profile(any()) } returns profile("B")
        coEvery { api.loginWithPassword(any()) } returns outcome("B", "login")
        assertTrue(repository.loginWithPassword("b@example.invalid", "test-password").isSuccess)
        coEvery { api.profile(any()) } returns profile("A")
        coEvery { api.loginWithPassword(any()) } returns outcome("A", "old")
        assertTrue(repository.loginWithPassword("a@example.invalid", "test-password").isSuccess)
        val finalA = shared.state
        delayed.completeExceptionally(httpFailure(401))

        assertError(IdentityError.CANCELLED, pending.await())
        assertEquals(finalA, shared.state)
        coVerify(exactly = 0) { api.refresh(any()) }
    }

    @Test
    fun should_recheck_the_me_origin_after_waiting_for_the_refresh_mutex() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val api = api()
        coEvery { api.profile(any()) } throws httpFailure(401)
        val repository = repository(api, TestStore(shared))
        shared.coordinator.refreshMutex.lock()
        val pending = async { repository.loadProfile() }
        runCurrent()
        coEvery { api.profile(any()) } returns profile("B")
        coEvery { api.loginWithPassword(any()) } returns outcome("B", "login")
        assertTrue(repository.loginWithPassword("b@example.invalid", "test-password").isSuccess)
        val savedB = shared.state
        shared.coordinator.refreshMutex.unlock()

        assertError(IdentityError.CANCELLED, pending.await())
        assertEquals(savedB, shared.state)
        coVerify(exactly = 0) { api.refresh(any()) }
    }

    @Test
    fun should_rotate_once_for_two_concurrent_me_requests_on_the_same_instance() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val api = api()
        val refreshed = CompletableDeferred<TokenResponseDto>()
        coEvery { api.profile("Bearer ${token("A", "old")}") } throws httpFailure(401)
        coEvery { api.refresh(any()) } coAnswers { refreshed.await() }
        val repository = repository(api, TestStore(shared))
        val one = async { repository.loadProfile() }
        runCurrent()
        val two = async { repository.loadProfile() }
        runCurrent()
        refreshed.complete(response("A", "renewed"))

        assertTrue(one.await().isSuccess)
        assertTrue(two.await().isSuccess)
        assertEquals(tokens("A", "renewed"), shared.state.tokens)
        coVerify(exactly = 1) { api.refresh(any()) }
        coVerify(exactly = 2) { api.profile("Bearer ${token("A", "renewed")}") }
        coVerify(exactly = 4) { api.profile(any()) }
    }

    @Test
    fun should_refuse_an_unproven_rotation_from_another_transport() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val api = api()
        val delayed = CompletableDeferred<AccountProfileDto>()
        coEvery { api.profile("Bearer ${token("A", "old")}") } coAnswers { delayed.await() }
        val first = repository(api, TestStore(shared))
        val other = repository(api, TestStore(shared))
        val pending = async { first.loadProfile() }
        runCurrent()
        coEvery { api.refresh(any()) } returns response("A", "renewed")
        assertTrue(other.refreshSession().isSuccess)
        val renewed = shared.state
        delayed.completeExceptionally(httpFailure(401))

        assertError(IdentityError.CANCELLED, pending.await())
        assertEquals(renewed, shared.state)
        coVerify(exactly = 1) { api.refresh(any()) }
    }

    @Test
    fun should_not_restore_a_refresh_started_by_me_after_login_b() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val api = api()
        val delayed = CompletableDeferred<TokenResponseDto>()
        coEvery { api.profile("Bearer ${token("A", "old")}") } throws httpFailure(401)
        coEvery { api.refresh(any()) } coAnswers { delayed.await() }
        val repository = repository(api, TestStore(shared))
        val pending = async { repository.loadProfile() }
        runCurrent()
        coEvery { api.profile("Bearer ${token("B", "login")}") } returns profile("B")
        coEvery { api.loginWithPassword(any()) } returns outcome("B", "login")
        assertTrue(repository.loginWithPassword("b@example.invalid", "test-password").isSuccess)
        val savedB = shared.state
        delayed.complete(response("A", "renewed"))

        assertError(IdentityError.CANCELLED, pending.await())
        assertEquals(savedB, shared.state)
        assertEquals("B", repository.profile.value?.accountId)
        coVerify(exactly = 0) { api.profile("Bearer ${token("A", "renewed")}") }
    }

    @Test
    fun should_propagate_cancellation_before_publishing_the_me_response() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val before = shared.state
        val api = api()
        val delayed = CompletableDeferred<AccountProfileDto>()
        coEvery { api.profile(any()) } coAnswers { withContext(NonCancellable) { delayed.await() } }
        val repository = repository(api, TestStore(shared))
        val pending = async { repository.loadProfile() }
        runCurrent()
        pending.cancel()
        delayed.complete(profile("A"))
        pending.join()

        assertTrue(pending.isCancelled)
        assertEquals(before, shared.state)
        assertNull(repository.profile.value)
        coVerify(exactly = 0) { api.refresh(any()) }
    }

    @Test
    fun should_propagate_cancellation_before_saving_the_me_refresh_response() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val before = shared.state
        val api = api()
        val delayed = CompletableDeferred<TokenResponseDto>()
        coEvery { api.profile(any()) } throws httpFailure(401)
        coEvery { api.refresh(any()) } coAnswers { withContext(NonCancellable) { delayed.await() } }
        val repository = repository(api, TestStore(shared))
        val pending = async { repository.loadProfile() }
        runCurrent()
        pending.cancel()
        delayed.complete(response("A", "renewed"))
        pending.join()

        assertTrue(pending.isCancelled)
        assertEquals(before, shared.state)
        coVerify(exactly = 1) { api.profile(any()) }
    }

    @Test
    fun should_not_restore_a_delayed_refresh_after_logout_from_another_instance() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val api = api()
        val delayed = CompletableDeferred<TokenResponseDto>()
        coEvery { api.refresh(any()) } coAnswers { delayed.await() }
        coEvery { api.logout(any()) } returns Response.success(LogoutResponseDto(false))
        val account = repository(api, TestStore(shared))
        val transport = repository(api, TestStore(shared))
        val pending = async { transport.refreshSession() }
        runCurrent()
        assertTrue(account.logout().isSuccess)
        assertNull(shared.state.tokens)
        delayed.complete(response("A", "new"))
        assertError(IdentityError.CANCELLED, pending.await())
        account.reloadStoredSession()
        assertNull(account.session.value)
        assertNull(shared.state.tokens)
        coVerify(exactly = 0) { api.profile(any()) }
    }

    @Test
    fun should_preserve_account_b_when_refresh_a_finishes_late() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val api = api()
        val delayed = CompletableDeferred<TokenResponseDto>()
        coEvery { api.refresh(any()) } coAnswers { delayed.await() }
        coEvery { api.loginWithPassword(any()) } returns outcome("B", "login")
        coEvery { api.profile(any()) } returns profile("B")
        val account = repository(api, TestStore(shared))
        val transport = repository(api, TestStore(shared))
        val pending = async { transport.refreshSession() }
        runCurrent()
        assertTrue(account.loginWithPassword("b@example.invalid", "test-password").isSuccess)
        val savedB = shared.state
        delayed.complete(response("A", "new"))
        assertError(IdentityError.CANCELLED, pending.await())
        assertEquals(savedB, shared.state)
        assertEquals("B", account.session.value?.accountId)
        assertEquals("B", account.profile.value?.accountId)
    }

    @Test
    fun should_rotate_once_when_two_instances_refresh_the_same_session() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val api = api()
        val delayed = CompletableDeferred<TokenResponseDto>()
        coEvery { api.refresh(any()) } coAnswers { delayed.await() }
        val first = repository(api, TestStore(shared))
        val second = repository(api, TestStore(shared))
        val one = async { first.refreshSession() }
        runCurrent()
        val two = async { second.refreshSession() }
        runCurrent()
        delayed.complete(response("A", "new"))
        assertTrue(one.await().isSuccess)
        assertTrue(two.await().isSuccess)
        assertEquals(first.session.value, second.session.value)
        coVerify(exactly = 1) { api.refresh(any()) }
        coVerify(exactly = 1) { api.profile(any()) }
    }

    @Test
    fun should_ignore_delayed_authentication_a_after_new_authentication_b() = runTest {
        val shared = SharedState()
        val api = api()
        val delayed = CompletableDeferred<LoginOutcomeDto>()
        coEvery { api.loginWithPassword(match { it.email == "a@example.invalid" }) } coAnswers { delayed.await() }
        coEvery { api.loginWithPassword(match { it.email == "b@example.invalid" }) } returns outcome("B", "login")
        coEvery { api.profile(any()) } returns profile("B")
        val repository = repository(api, TestStore(shared))
        val pending = async { repository.loginWithPassword("a@example.invalid", "test-password") }
        runCurrent()
        assertTrue(repository.loginWithPassword("b@example.invalid", "test-password").isSuccess)
        val savedB = shared.state
        delayed.complete(outcome("A", "late"))
        assertError(IdentityError.CANCELLED, pending.await())
        assertEquals(savedB, shared.state)
        assertEquals("B", repository.profile.value?.accountId)
    }

    @Test
    fun should_not_publish_profile_a_after_another_instance_switches_to_b() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val store = TestStore(shared)
        val api = api()
        val delayed = CompletableDeferred<AccountProfileDto>()
        coEvery { api.profile(any()) } coAnswers { delayed.await() }
        val repository = repository(api, store)
        val pending = async { repository.loadProfile() }
        runCurrent()
        val other = TestStore(shared)
        val reserved = requireNotNull(other.reserveAuthentication())
        assertTrue(other.saveIfCurrent(reserved, tokens("B", "login")) is IdentitySessionWrite.Saved)
        repository.reloadStoredSession()
        delayed.complete(profile("A"))
        assertError(IdentityError.CANCELLED, pending.await())
        assertEquals("B", repository.session.value?.accountId)
        assertNull(repository.profile.value)
    }

    @Test
    fun should_reject_a_profile_for_a_different_account() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val api = api()
        coEvery { api.profile(any()) } returns profile("B")
        val repository = repository(api, TestStore(shared))
        assertError(IdentityError.INVALID_SERVER_RESPONSE, repository.loadProfile())
        assertNull(repository.profile.value)
        assertEquals("A", repository.session.value?.accountId)
    }

    @Test
    fun should_reject_a_refresh_that_changes_the_account_subject() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val previous = shared.state
        val api = api()
        coEvery { api.refresh(any()) } returns response("B", "foreign")
        val repository = repository(api, TestStore(shared))
        assertError(IdentityError.INVALID_SERVER_RESPONSE, repository.refreshSession())
        assertEquals(previous, shared.state)
        assertEquals("A", repository.session.value?.accountId)
    }

    @Test
    fun should_disconnect_locally_and_report_http_failure_when_logout_is_500() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val api = api()
        coEvery { api.logout(any()) } returns Response.error(500, "{}".toResponseBody("application/json".toMediaType()))
        val repository = repository(api, TestStore(shared))
        assertTrue(repository.logout().isFailure)
        assertNull(repository.session.value)
        assertNull(shared.state.tokens)
    }

    @Test
    fun should_report_invalid_logout_response_and_preserve_local_disconnect() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val api = api()
        coEvery { api.logout(any()) } returns Response.success<LogoutResponseDto>(null)
        val repository = repository(api, TestStore(shared))
        assertError(IdentityError.INVALID_SERVER_RESPONSE, repository.logout())
        assertNull(shared.state.tokens)
    }

    @Test
    fun should_return_google_mfa_setup_without_storing_intermediate_tokens() = runTest {
        val shared = SharedState()
        val api = api()
        val dto = Json.decodeFromString<LoginOutcomeDto>(
            """{"mfa_setup_required":true,"setup_token":"test-only-setup","expires_in":900}"""
        )
        coEvery { api.loginWithGoogle(any()) } returns dto
        val repository = repository(api, TestStore(shared))
        assertTrue(repository.loginWithGoogle("test-only-google", "test-only-nonce").getOrThrow() is LoginOutcome.MfaSetupRequired)
        assertNull(shared.state.tokens)
        assertNull(repository.session.value)
        coVerify(exactly = 0) { api.profile(any()) }
    }

    @Test
    fun should_return_google_mfa_challenge_without_storing_intermediate_tokens() = runTest {
        val shared = SharedState()
        val api = api()
        coEvery { api.loginWithGoogle(any()) } returns LoginOutcomeDto(
            mfaRequired = true, challengeToken = "test-only-challenge", expiresIn = 300,
        )
        val repository = repository(api, TestStore(shared))
        assertTrue(repository.loginWithGoogle("test-only-google", "test-only-nonce").getOrThrow() is LoginOutcome.MfaRequired)
        assertNull(shared.state.tokens)
    }

    @Test
    fun should_reject_ambiguous_token_and_mfa_response_without_storing_tokens() = runTest {
        val shared = SharedState()
        val api = api()
        coEvery { api.loginWithGoogle(any()) } returns outcome("A", "login").copy(
            mfaRequired = true, challengeToken = "test-only-challenge",
        )
        val repository = repository(api, TestStore(shared))
        assertError(IdentityError.INVALID_SERVER_RESPONSE, repository.loginWithGoogle("test-only-google", "test-only-nonce"))
        assertNull(shared.state.tokens)
    }

    @Test
    fun should_invalidate_in_flight_refresh_even_when_durable_clear_fails() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val api = api()
        val delayed = CompletableDeferred<TokenResponseDto>()
        coEvery { api.refresh(any()) } coAnswers { delayed.await() }
        coEvery { api.logout(any()) } returns Response.success(LogoutResponseDto(true))
        val repository = repository(api, TestStore(shared))
        val pending = async { repository.refreshSession() }
        runCurrent()
        shared.writable = false
        assertError(IdentityError.SECURE_STORAGE_UNAVAILABLE, repository.logout())
        shared.writable = true
        delayed.complete(response("A", "new"))
        assertError(IdentityError.CANCELLED, pending.await())
        assertNull(repository.session.value)
        assertEquals(tokens("A", "old"), shared.state.tokens)
        repository.reloadStoredSession()
        assertNull(repository.session.value)
        assertNull(TestStore(shared).read())
        assertNull(repository(api, TestStore(shared)).session.value)
    }

    @Test
    fun should_not_complete_authentication_when_me_returns_another_account() = runTest {
        val shared = SharedState()
        val api = api()
        coEvery { api.loginWithPassword(any()) } returns outcome("A", "login")
        coEvery { api.profile(any()) } returns profile("B")
        val repository = repository(api, TestStore(shared))
        assertError(IdentityError.INVALID_SERVER_RESPONSE, repository.loginWithPassword("a@example.invalid", "test-password"))
        assertNull(repository.session.value)
        assertNull(repository.profile.value)
        assertNull(shared.state.tokens)
    }

    @Test
    fun should_quarantine_tokens_when_save_mutates_memory_but_commit_reports_failure() = runTest {
        val shared = SharedState()
        val api = api()
        val delayed = CompletableDeferred<LoginOutcomeDto>()
        coEvery { api.loginWithPassword(any()) } coAnswers { delayed.await() }
        val repository = repository(api, TestStore(shared))
        val pending = async { repository.loginWithPassword("a@example.invalid", "test-password") }
        runCurrent()
        shared.writable = false
        shared.mutateMemoryOnFailure = true
        delayed.complete(outcome("A", "login"))
        assertError(IdentityError.SECURE_STORAGE_UNAVAILABLE, pending.await())
        assertNotNull(shared.state.tokens) // Carte mémoire écrite avant l'échec disque.
        assertNull(TestStore(shared).read())
        repository.reloadStoredSession()
        assertNull(repository.session.value)
        assertNull(repository(api, TestStore(shared)).session.value)
    }

    @Test
    fun should_not_store_a_response_after_the_authentication_coroutine_is_cancelled() = runTest {
        val shared = SharedState()
        val api = api()
        val delayed = CompletableDeferred<LoginOutcomeDto>()
        coEvery { api.loginWithPassword(any()) } coAnswers {
            withContext(NonCancellable) { delayed.await() }
        }
        val repository = repository(api, TestStore(shared))
        val pending = async { repository.loginWithPassword("a@example.invalid", "test-password") }
        runCurrent()
        pending.cancel()
        delayed.complete(outcome("A", "login"))
        pending.join()
        assertTrue(pending.isCancelled)
        assertNull(shared.state.tokens)
        assertNull(repository.session.value)
    }

    @Test
    fun should_keep_the_current_session_when_linking_requires_mfa_without_issuing_tokens() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val previous = shared.state.tokens
        val api = api()
        coEvery { api.linkGoogle(any(), any()) } returns LoginOutcomeDto(
            mfaRequired = true, challengeToken = "test-only-challenge", expiresIn = 300,
        )
        val repository = repository(api, TestStore(shared))
        assertTrue(repository.linkGoogle("test-only-google", "test-only-nonce").getOrThrow() is LoginOutcome.MfaRequired)
        assertEquals(previous, shared.state.tokens)
        assertEquals("A", repository.session.value?.accountId)
        coVerify(exactly = 0) { api.profile(any()) }
    }

    @Test
    fun should_not_clear_a_new_account_after_remote_logout_a_finishes() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val api = api()
        val delayed = CompletableDeferred<Response<LogoutResponseDto>>()
        coEvery { api.logout(any()) } coAnswers { delayed.await() }
        coEvery { api.loginWithPassword(any()) } returns outcome("B", "login")
        coEvery { api.profile(any()) } returns profile("B")
        val repository = repository(api, TestStore(shared))
        val pending = async { repository.logout() }
        runCurrent()
        assertNull(repository.session.value)
        assertTrue(repository.loginWithPassword("b@example.invalid", "test-password").isSuccess)
        val savedB = shared.state
        delayed.complete(Response.success(LogoutResponseDto(true)))
        assertTrue(pending.await().isSuccess)
        assertEquals(savedB, shared.state)
        assertEquals("B", repository.session.value?.accountId)
    }

    @Test
    fun should_emit_revocation_while_authentication_is_pending_with_unchanged_metadata() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val api = api()
        val delayed = CompletableDeferred<LoginOutcomeDto>()
        coEvery { api.loginWithPassword(any()) } coAnswers { delayed.await() }
        val repository = repository(api, TestStore(shared))
        val initialSession = repository.session.value
        val guard = repository.captureSessionGuard("A").getOrThrow()
        val initialGeneration = repository.sessionGeneration.value
        val pending = async { repository.loginWithPassword("a@example.invalid", "test-password") }
        runCurrent()
        assertEquals(initialSession, repository.session.value)
        assertTrue(repository.sessionGeneration.value > initialGeneration)
        assertFalse(repository.isSessionGuardCurrent(guard))
        pending.cancel()
        pending.join()
    }

    @Test
    fun should_publish_external_store_rotation_even_with_identical_tokens_and_no_reload() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val repository = repository(api(), TestStore(shared))
        val original = repository.session.value
        val guard = repository.captureSessionGuard("A").getOrThrow()
        val observed = mutableListOf<Long>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.sessionGeneration.collect { observed.add(it) }
        }
        val otherTransport = TestStore(shared)
        val initial = otherTransport.snapshot()!!
        assertTrue(otherTransport.saveIfCurrent(initial, tokens("A", "old")) is IdentitySessionWrite.Saved)
        assertEquals(original, repository.session.value)
        assertFalse(repository.isSessionGuardCurrent(guard))
        assertEquals(2, observed.size)
        assertTrue(observed.last() > observed.first())
    }

    @Test
    fun should_publish_local_revocation_even_when_the_clear_cannot_be_persisted() = runTest {
        val shared = SharedState(tokens("A", "old"))
        val api = api()
        coEvery { api.logout(any()) } returns Response.success(LogoutResponseDto(true))
        val repository = repository(api, TestStore(shared))
        val guard = repository.captureSessionGuard("A").getOrThrow()
        val initial = repository.sessionGeneration.value
        shared.writable = false
        assertError(IdentityError.SECURE_STORAGE_UNAVAILABLE, repository.logout())
        assertNull(repository.session.value)
        assertTrue(repository.sessionGeneration.value > initial)
        assertFalse(repository.isSessionGuardCurrent(guard))
    }

    @Test
    fun should_keep_the_generation_on_profile_reads_that_do_not_rotate_the_store() = runTest {
        val repository = repository(api(), TestStore(SharedState(tokens("A", "old"))))
        val initial = repository.sessionGeneration.value
        assertTrue(repository.loadProfile().isSuccess)
        assertTrue(repository.loadProfile().isSuccess)
        assertEquals(initial, repository.sessionGeneration.value)
    }

    private fun api(): IdentityApiService = mockk<IdentityApiService>().also { api ->
        coEvery { api.profile(any()) } returns profile("A")
    }

    private fun httpFailure(code: Int) = HttpException(
        Response.error<AccountProfileDto>(code, "{}".toResponseBody("application/json".toMediaType()))
    )

    private fun repository(api: IdentityApiService, store: IdentitySessionStore) =
        IdentityRepositoryImpl(api, "https://gsie.example.invalid/", true, store)

    private fun profile(account: String) = AccountProfileDto(
        accountId = account, emailVerified = false, providers = listOf("local"), roles = listOf("user"),
    )

    private fun tokens(account: String, version: String) = StoredIdentityTokens(
        token(account, version), "test-only-refresh-$account-$version",
    )

    private fun response(account: String, version: String) = TokenResponseDto(
        accessToken = token(account, version), refreshToken = "test-only-refresh-$account-$version",
        tokenType = "bearer", expiresIn = 900,
    )

    private fun outcome(account: String, version: String) = response(account, version).let {
        LoginOutcomeDto(it.accessToken, it.refreshToken, it.tokenType, it.expiresIn)
    }

    private fun token(account: String, version: String): String {
        val payload = """{"sub":"$account","exp":4102444800,"auth_provider":"local","roles":["user"],"jti":"test-only-$version"}"""
        return "test-header." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8)) + ".test-signature"
    }

    private fun assertError(error: IdentityError, result: Result<*>) {
        assertEquals(error, (result.exceptionOrNull() as? IdentityClientException)?.error)
    }
}
