package com.forestry.counter.presentation.viewmodel

import com.forestry.counter.domain.model.AccountProfile
import com.forestry.counter.domain.model.AccountSession
import com.forestry.counter.domain.model.ApiConnectionState
import com.forestry.counter.domain.model.ApiDiagnostic
import com.forestry.counter.domain.model.GoogleNonce
import com.forestry.counter.domain.model.IdentityProvider
import com.forestry.counter.domain.model.IdentityClientException
import com.forestry.counter.domain.model.IdentityError
import com.forestry.counter.domain.model.LoginOutcome
import com.forestry.counter.domain.model.ProviderCapability
import com.forestry.counter.domain.repository.IdentityRepository
import com.forestry.counter.domain.repository.IdentitySessionGuard
import com.forestry.counter.domain.repository.GuardedProfileResult
import kotlinx.coroutines.flow.MutableStateFlow

/** Fixtures JVM uniquement ; aucune identité ni réponse fabriquée dans le produit. */
internal class IdentityRepositoryFake : IdentityRepository {
    override val session = MutableStateFlow<AccountSession?>(null)
    override val sessionGeneration = MutableStateFlow(0L)
    override val profile = MutableStateFlow<AccountProfile?>(null)
    override val apiBaseUrl = "https://identity.example.invalid/"
    override val isApiConfigured = true
    override val isGoogleClientConfigured = true
    private var authenticationGeneration = 0L
        set(value) {
            field = value
            sessionGeneration.value = value
        }
    private var lastRenewal: Pair<SessionGuard, SessionGuard>? = null
    private class SessionGuard(
        val owner: IdentityRepositoryFake,
        val capturedSession: AccountSession,
        val generation: Long,
    ) : IdentitySessionGuard {
        override val accountId: String get() = capturedSession.accountId
    }

    override fun captureSessionGuard(expectedAccountId: String): Result<IdentitySessionGuard> {
        val current = session.value?.takeIf { it.accountId == expectedAccountId }
            ?: return Result.failure(IdentityClientException(IdentityError.INVALID_CREDENTIALS))
        return Result.success(SessionGuard(this, current, authenticationGeneration))
    }

    override fun isSessionGuardCurrent(guard: IdentitySessionGuard): Boolean =
        guard is SessionGuard && guard.owner === this &&
            guard.generation == authenticationGeneration && session.value === guard.capturedSession

    override fun isSessionGuardRenewalCurrent(parent: IdentitySessionGuard): Boolean {
        val origin = parent as? SessionGuard ?: return false
        val renewal = lastRenewal ?: return false
        return origin.owner === this && origin.generation == renewal.first.generation &&
            origin.capturedSession === renewal.first.capturedSession && isSessionGuardCurrent(renewal.second)
    }

    /** Rotation synthétique JVM, publiée avec une preuve avant l'émission observée par le VM. */
    fun publishRenewalForTest(nextSession: AccountSession): IdentitySessionGuard {
        val parent = captureSessionGuard(nextSession.accountId).getOrThrow() as SessionGuard
        authenticationGeneration++
        val child = SessionGuard(this, nextSession, authenticationGeneration)
        lastRenewal = parent to child
        session.value = nextSession
        return child
    }

    data class Registration(val email: String, val password: String, val displayName: String?)
    data class PasswordLogin(val email: String, val password: String)
    data class MfaLogin(val challengeToken: String, val code: String, val isRecoveryCode: Boolean)

    val registrations = mutableListOf<Registration>()
    val passwordLogins = mutableListOf<PasswordLogin>()
    val mfaLogins = mutableListOf<MfaLogin>()
    val displayNameUpdates = mutableListOf<String?>()
    var profileLoads = 0
    var logouts = 0
    var verificationRequests = 0
    var verificationConfirmations = 0
    var googleLinks = 0

    var registerHandler: suspend (Registration) -> Result<AccountSession> = {
        Result.success(identitySessionFixture())
    }
    var passwordLoginHandler: suspend (PasswordLogin) -> Result<LoginOutcome> = {
        Result.success(LoginOutcome.Authenticated(identitySessionFixture()))
    }
    var mfaLoginHandler: suspend (MfaLogin) -> Result<LoginOutcome> = {
        Result.success(LoginOutcome.Authenticated(identitySessionFixture()))
    }
    var loadProfileHandler: suspend () -> Result<AccountProfile> = {
        Result.success(requireNotNull(profile.value))
    }
    var guardedProfileHandler: (suspend (IdentitySessionGuard) -> GuardedProfileResult)? = null
    var updateDisplayNameHandler: suspend (String?) -> Result<AccountProfile> = { name ->
        Result.success(requireNotNull(profile.value).copy(displayName = name))
    }
    var logoutHandler: suspend () -> Result<Unit> = { Result.success(Unit) }
    var linkGoogleHandler: suspend () -> Result<LoginOutcome> = {
        Result.success(LoginOutcome.Authenticated(requireNotNull(session.value)))
    }

    override suspend fun getProviders(): Result<List<ProviderCapability>> = Result.success(emptyList())

    override suspend fun register(
        email: String,
        password: String,
        displayName: String?,
    ): Result<AccountSession> {
        val request = Registration(email, password, displayName)
        registrations += request
        return registerHandler(request).onSuccess { authenticationGeneration++; session.value = it }
    }

    override suspend fun loginWithPassword(email: String, password: String): Result<LoginOutcome> {
        val request = PasswordLogin(email, password)
        passwordLogins += request
        return passwordLoginHandler(request).publishAuthenticatedSession()
    }

    override suspend fun completeMfaLogin(
        challengeToken: String,
        code: String,
        isRecoveryCode: Boolean,
    ): Result<LoginOutcome> {
        val request = MfaLogin(challengeToken, code, isRecoveryCode)
        mfaLogins += request
        return mfaLoginHandler(request).publishAuthenticatedSession()
    }

    override suspend fun requestGoogleNonce(): Result<GoogleNonce> =
        Result.success(GoogleNonce("test-only-nonce", 300))

    override suspend fun loginWithGoogle(idToken: String, nonce: String): Result<LoginOutcome> =
        Result.success(LoginOutcome.Authenticated(identitySessionFixture())).publishAuthenticatedSession()

    override suspend fun linkGoogle(idToken: String, nonce: String): Result<LoginOutcome> {
        googleLinks += 1
        return linkGoogleHandler().publishAuthenticatedSession()
    }

    override suspend fun refreshSession(): Result<AccountSession> =
        Result.success(requireNotNull(session.value))

    override fun reloadStoredSession() = Unit

    override suspend fun logout(): Result<Unit> {
        logouts += 1
        // La révocation distante peut échouer : la session locale est déjà fermée.
        authenticationGeneration++
        session.value = null
        profile.value = null
        return logoutHandler()
    }

    override suspend fun loadProfile(): Result<AccountProfile> {
        profileLoads += 1
        return loadProfileHandler().onSuccess { profile.value = it }
    }

    override suspend fun loadProfile(expected: IdentitySessionGuard): GuardedProfileResult {
        guardedProfileHandler?.let { return it(expected) }
        if (!isSessionGuardCurrent(expected)) {
            return GuardedProfileResult(expected, Result.failure(IdentityClientException(IdentityError.CANCELLED)))
        }
        return GuardedProfileResult(expected, loadProfile())
    }

    override suspend fun updateDisplayName(displayName: String?): Result<AccountProfile> {
        displayNameUpdates += displayName
        return updateDisplayNameHandler(displayName).onSuccess { profile.value = it }
    }

    override suspend fun requestEmailVerification(): Result<Unit> {
        verificationRequests += 1
        return Result.success(Unit)
    }

    override suspend fun confirmEmailVerification(code: String): Result<AccountProfile> {
        verificationConfirmations += 1
        return Result.success(requireNotNull(profile.value).copy(emailVerified = true))
            .onSuccess { profile.value = it }
    }

    override suspend fun requestPasswordReset(email: String): Result<Unit> =
        error("Récupération non prévue par ce test")

    override suspend fun confirmPasswordReset(
        email: String,
        code: String,
        newPassword: String,
    ): Result<Unit> = error("Récupération non prévue par ce test")

    override suspend fun diagnoseApi(): ApiDiagnostic = ApiDiagnostic(ApiConnectionState.CONNECTED)

    private fun Result<LoginOutcome>.publishAuthenticatedSession(): Result<LoginOutcome> = onSuccess {
        if (it is LoginOutcome.Authenticated) {
            authenticationGeneration++
            session.value = it.session
        }
    }
}

internal fun identitySessionFixture() = AccountSession(
    accountId = "identity-unit-test",
    provider = IdentityProvider.LOCAL,
    roles = listOf("user"),
    expiresAtEpochSeconds = 4_102_444_800L,
)

internal fun identityProfileFixture() = AccountProfile(
    accountId = "identity-unit-test",
    displayName = "Technicien test JVM",
    email = "operator@example.invalid",
    emailVerified = false,
    providers = listOf(IdentityProvider.LOCAL),
    roles = listOf("user"),
)
