package com.forestry.counter.presentation.viewmodel

import androidx.lifecycle.ViewModelStore
import com.forestry.counter.data.remote.identity.GoogleCredentialClient
import com.forestry.counter.domain.model.AccountProfile
import com.forestry.counter.domain.model.IdentityClientException
import com.forestry.counter.domain.model.IdentityError
import com.forestry.counter.domain.model.LoginOutcome
import com.forestry.counter.domain.repository.GuardedProfileResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: IdentityRepositoryFake
    private lateinit var viewModel: AccountViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = IdentityRepositoryFake().apply {
            session.value = identitySessionFixture()
            profile.value = identityProfileFixture()
        }
    }

    @After
    fun tearDown() {
        if (::viewModel.isInitialized) {
            ViewModelStore().apply {
                put("identity-account", viewModel)
                clear()
            }
        }
        Dispatchers.resetMain()
    }

    @Test
    fun `une modification de profil bloque les actions concurrentes avant execution du scope`() = runTest(dispatcher) {
        viewModel = AccountViewModel(repository)
        runCurrent()
        val finish = CompletableDeferred<Result<AccountProfile>>()
        repository.updateDisplayNameHandler = { finish.await() }
        viewModel.setVerificationCode("ABCD-EFGH")

        viewModel.updateDisplayName("Nouveau nom test JVM")
        viewModel.updateDisplayName("Nom concurrent")
        viewModel.requestEmailVerification()
        viewModel.confirmEmailVerification()
        assertTrue(viewModel.uiState.value.isWorking)
        runCurrent()

        assertEquals(listOf("Nouveau nom test JVM"), repository.displayNameUpdates)
        assertEquals(0, repository.verificationRequests)
        assertEquals(0, repository.verificationConfirmations)
        assertNull(viewModel.uiState.value.notice)

        val updated = identityProfileFixture().copy(displayName = "Nouveau nom test JVM")
        finish.complete(Result.success(updated))
        runCurrent()

        assertFalse(viewModel.uiState.value.isWorking)
        assertEquals(AccountNotice.PROFILE_UPDATED, viewModel.uiState.value.notice)
        assertEquals(updated, viewModel.uiState.value.profile)
        assertEquals(identitySessionFixture(), viewModel.uiState.value.session)
    }

    @Test
    fun `la deconnexion est unique et reste locale meme si la revocation distante echoue`() = runTest(dispatcher) {
        viewModel = AccountViewModel(repository)
        runCurrent()
        val finish = CompletableDeferred<Result<Unit>>()
        repository.logoutHandler = { finish.await() }

        viewModel.logout()
        viewModel.logout()
        viewModel.updateDisplayName("Modification pendant deconnexion")
        viewModel.requestEmailVerification()
        assertTrue(viewModel.uiState.value.isLoggingOut)
        runCurrent()

        assertEquals(1, repository.logouts)
        assertTrue(repository.displayNameUpdates.isEmpty())
        assertEquals(0, repository.verificationRequests)

        finish.complete(Result.failure(IdentityClientException(IdentityError.NETWORK_UNAVAILABLE)))
        runCurrent()

        assertFalse(viewModel.uiState.value.isLoggingOut)
        assertEquals(IdentityError.NETWORK_UNAVAILABLE, viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.session)
        assertNull(viewModel.uiState.value.profile)
    }

    @Test
    fun `un echec tardif de deconnexion du compte A ne contamine pas le compte B`() = runTest(dispatcher) {
        viewModel = AccountViewModel(repository)
        runCurrent()
        val finishLogoutA = CompletableDeferred<Result<Unit>>()
        repository.logoutHandler = { finishLogoutA.await() }

        viewModel.logout()
        runCurrent()
        assertEquals(1, repository.logouts)
        assertTrue(viewModel.uiState.value.isLoggingOut)
        assertNull(viewModel.uiState.value.session)

        val sessionB = identitySessionFixture().copy(accountId = "identity-unit-test-b")
        val profileB = identityProfileFixture().copy(
            accountId = sessionB.accountId,
            displayName = "Compte B test JVM",
            email = "operator-b@example.invalid",
        )
        repository.session.value = sessionB
        repository.profile.value = profileB
        runCurrent()
        assertEquals(sessionB, viewModel.uiState.value.session)
        assertEquals(profileB, viewModel.uiState.value.profile)
        assertNull(viewModel.uiState.value.error)

        finishLogoutA.complete(Result.failure(IdentityClientException(IdentityError.NETWORK_UNAVAILABLE)))
        runCurrent()

        assertFalse(viewModel.uiState.value.isLoggingOut)
        assertEquals(sessionB, viewModel.uiState.value.session)
        assertEquals(profileB, viewModel.uiState.value.profile)
        assertNull(viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.notice)
    }

    @Test
    fun `le collecteur accepte une rotation prouvee puis affiche le 503 de sa garde enfant`() = runTest(dispatcher) {
        val original = identitySessionFixture()
        val renewed = original.copy(expiresAtEpochSeconds = original.expiresAtEpochSeconds + 60)
        val finish = CompletableDeferred<Result<AccountProfile>>()
        repository.guardedProfileHandler = {
            val child = repository.publishRenewalForTest(renewed)
            GuardedProfileResult(child, finish.await())
        }
        viewModel = AccountViewModel(repository)
        runCurrent()

        assertEquals(renewed, viewModel.uiState.value.session)
        assertTrue(viewModel.uiState.value.isRefreshingProfile)
        assertNull(viewModel.uiState.value.error)
        finish.complete(Result.failure(IdentityClientException(IdentityError.SERVER_UNAVAILABLE)))
        runCurrent()

        assertFalse(viewModel.uiState.value.isRefreshingProfile)
        assertEquals(renewed, viewModel.uiState.value.session)
        assertEquals(IdentityError.SERVER_UNAVAILABLE, viewModel.uiState.value.error)
        assertEquals(identityProfileFixture(), viewModel.uiState.value.profile)
    }

    @Test
    fun `le profil renouvele est accepte avec sa garde enfant apres emission de session`() = runTest(dispatcher) {
        val original = identitySessionFixture()
        val renewed = original.copy(expiresAtEpochSeconds = original.expiresAtEpochSeconds + 60)
        val fresh = identityProfileFixture().copy(emailVerified = true)
        val finish = CompletableDeferred<Unit>()
        repository.guardedProfileHandler = {
            val child = repository.publishRenewalForTest(renewed)
            finish.await()
            repository.profile.value = fresh
            GuardedProfileResult(child, Result.success(fresh))
        }
        viewModel = AccountViewModel(repository)
        runCurrent()
        assertTrue(viewModel.uiState.value.isRefreshingProfile)

        finish.complete(Unit)
        runCurrent()

        assertEquals(fresh, viewModel.uiState.value.profile)
        assertEquals(renewed, viewModel.uiState.value.session)
        assertFalse(viewModel.uiState.value.isRefreshingProfile)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `une nouvelle authentification A invalide un ancien resultat enfant meme pour le meme compte`() = runTest(dispatcher) {
        val original = identitySessionFixture()
        val renewed = original.copy(expiresAtEpochSeconds = original.expiresAtEpochSeconds + 60)
        val finish = CompletableDeferred<Result<AccountProfile>>()
        repository.guardedProfileHandler = {
            val child = repository.publishRenewalForTest(renewed)
            GuardedProfileResult(child, finish.await())
        }
        viewModel = AccountViewModel(repository)
        runCurrent()
        assertTrue(viewModel.uiState.value.isRefreshingProfile)
        repository.passwordLoginHandler = { Result.success(LoginOutcome.Authenticated(renewed)) }
        assertTrue(repository.loginWithPassword("a@example.invalid", "test-password").isSuccess)
        val fresh = identityProfileFixture().copy(displayName = "Profil après reconnexion JVM")
        repository.profile.value = fresh
        runCurrent()
        finish.complete(Result.failure(IdentityClientException(IdentityError.INVALID_CREDENTIALS)))
        runCurrent()

        assertEquals(renewed, viewModel.uiState.value.session)
        assertEquals(fresh, viewModel.uiState.value.profile)
        assertFalse(viewModel.uiState.value.isRefreshingProfile)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `la reconnexion valide au meme compte efface seulement son ancienne erreur identification`() = runTest(dispatcher) {
        repository.session.value = identitySessionFixture().copy(expiresAtEpochSeconds = 1)
        repository.loadProfileHandler = { Result.failure(IdentityClientException(IdentityError.INVALID_CREDENTIALS)) }
        viewModel = AccountViewModel(repository)
        runCurrent()
        assertEquals(IdentityError.INVALID_CREDENTIALS, viewModel.uiState.value.error)

        val reconnectedSession = identitySessionFixture()
        repository.session.value = reconnectedSession
        repository.profile.value = identityProfileFixture().copy(emailVerified = true)
        runCurrent()

        assertEquals(reconnectedSession, viewModel.uiState.value.session)
        assertEquals(repository.profile.value, viewModel.uiState.value.profile)
        assertNull(viewModel.uiState.value.error)

        repository.loadProfileHandler = { Result.failure(IdentityClientException(IdentityError.NETWORK_UNAVAILABLE)) }
        viewModel.refreshProfile()
        runCurrent()
        repository.session.value = reconnectedSession.copy(expiresAtEpochSeconds = reconnectedSession.expiresAtEpochSeconds + 60)
        runCurrent()
        assertEquals(IdentityError.NETWORK_UNAVAILABLE, viewModel.uiState.value.error)
    }

    @Test
    fun `un ancien profil 401 retarde ne contamine pas la reconnexion au meme compte`() = runTest(dispatcher) {
        repository.session.value = identitySessionFixture().copy(expiresAtEpochSeconds = 1)
        val finishOldProfile = CompletableDeferred<Result<AccountProfile>>()
        repository.loadProfileHandler = { finishOldProfile.await() }
        viewModel = AccountViewModel(repository)
        runCurrent()
        assertTrue(viewModel.uiState.value.isRefreshingProfile)

        val reconnectedSession = identitySessionFixture()
        val currentProfile = identityProfileFixture().copy(emailVerified = true)
        repository.session.value = reconnectedSession
        repository.profile.value = currentProfile
        runCurrent()
        assertFalse(viewModel.uiState.value.isRefreshingProfile)
        assertEquals(currentProfile, viewModel.uiState.value.profile)
        assertNull(viewModel.uiState.value.error)

        finishOldProfile.complete(Result.failure(IdentityClientException(IdentityError.INVALID_CREDENTIALS)))
        runCurrent()
        assertEquals(reconnectedSession, viewModel.uiState.value.session)
        assertEquals(currentProfile, viewModel.uiState.value.profile)
        assertFalse(viewModel.uiState.value.isRefreshingProfile)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `un rafraichissement echoue sans perdre le profil existant puis peut etre relance`() = runTest(dispatcher) {
        val finish = CompletableDeferred<Result<AccountProfile>>()
        repository.loadProfileHandler = { finish.await() }
        viewModel = AccountViewModel(repository)
        viewModel.refreshProfile()
        viewModel.refreshProfile()
        assertTrue(viewModel.uiState.value.isRefreshingProfile)
        runCurrent()
        assertEquals(1, repository.profileLoads)

        finish.complete(Result.failure(IdentityClientException(IdentityError.NETWORK_UNAVAILABLE)))
        runCurrent()

        assertFalse(viewModel.uiState.value.isRefreshingProfile)
        assertEquals(IdentityError.NETWORK_UNAVAILABLE, viewModel.uiState.value.error)
        assertEquals(identitySessionFixture(), viewModel.uiState.value.session)
        assertEquals(identityProfileFixture(), viewModel.uiState.value.profile)

        val updated = identityProfileFixture().copy(emailVerified = true)
        repository.loadProfileHandler = { Result.success(updated) }
        viewModel.refreshProfile()
        runCurrent()

        assertEquals(2, repository.profileLoads)
        assertFalse(viewModel.uiState.value.isRefreshingProfile)
        assertEquals(updated, viewModel.uiState.value.profile)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `Google ne doit pas etre annonce lie avant MFA ni apres deconnexion pendant le selecteur`() = runTest(dispatcher) {
        viewModel = AccountViewModel(repository)
        runCurrent()
        val credentialClient = mockk<GoogleCredentialClient>()
        val selectedCredential = CompletableDeferred<Result<String>>()
        coEvery { credentialClient.requestIdToken("test-only-nonce") } coAnswers { selectedCredential.await() }
        repository.linkGoogleHandler = {
            Result.success(LoginOutcome.MfaRequired("test-only-challenge", 300))
        }

        viewModel.linkGoogle(credentialClient)
        viewModel.linkGoogle(credentialClient)
        assertTrue(viewModel.uiState.value.isWorking)
        runCurrent()

        viewModel.updateDisplayName("Modification pendant selection Google")
        runCurrent()
        assertTrue(repository.displayNameUpdates.isEmpty())
        assertEquals(0, repository.logouts)
        assertEquals(0, repository.googleLinks)
        assertEquals(identitySessionFixture(), viewModel.uiState.value.session)

        selectedCredential.complete(Result.success("test-only-id-token"))
        runCurrent()

        assertEquals(1, repository.googleLinks)
        assertFalse(viewModel.uiState.value.isWorking)
        assertNotEquals(AccountNotice.GOOGLE_LINKED, viewModel.uiState.value.notice)
        assertEquals("test-only-challenge", viewModel.uiState.value.mfaChallengeToken)
        assertEquals(identitySessionFixture(), viewModel.uiState.value.session)
        assertEquals(identityProfileFixture(), viewModel.uiState.value.profile)

        // Une nouvelle sélection n'autorise pas à rattacher Google à un compte
        // que l'utilisateur a déconnecté pendant que Credential Manager était ouvert.
        viewModel.cancelMfa()
        val lateCredential = CompletableDeferred<Result<String>>()
        coEvery { credentialClient.requestIdToken("test-only-nonce") } coAnswers { lateCredential.await() }
        viewModel.linkGoogle(credentialClient)
        runCurrent()
        assertTrue(viewModel.uiState.value.isWorking)

        viewModel.logout()
        viewModel.logout()
        assertTrue(viewModel.uiState.value.isLoggingOut)
        runCurrent()
        assertEquals(1, repository.logouts)
        assertNull(viewModel.uiState.value.session)
        assertNull(viewModel.uiState.value.profile)

        lateCredential.complete(Result.success("late-test-only-id-token"))
        runCurrent()
        assertEquals(1, repository.googleLinks)
        assertNotEquals(AccountNotice.GOOGLE_LINKED, viewModel.uiState.value.notice)
        assertNull(viewModel.uiState.value.session)
    }
}
