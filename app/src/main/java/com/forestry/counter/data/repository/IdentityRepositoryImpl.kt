package com.forestry.counter.data.repository

import android.content.Context
import android.os.SystemClock
import com.forestry.counter.BuildConfig
import com.forestry.counter.data.remote.identity.ApiErrorDto
import com.forestry.counter.data.remote.identity.AccountProfileDto
import com.forestry.counter.data.remote.identity.ActionCodeRequestDto
import com.forestry.counter.data.remote.identity.EncryptedIdentitySessionStore
import com.forestry.counter.data.remote.identity.GoogleLoginRequestDto
import com.forestry.counter.data.remote.identity.HealthResponseDto
import com.forestry.counter.data.remote.identity.IdentityApiFactory
import com.forestry.counter.data.remote.identity.IdentityApiService
import com.forestry.counter.data.remote.identity.IdentitySessionSnapshot
import com.forestry.counter.data.remote.identity.IdentitySessionCoordinator
import com.forestry.counter.data.remote.identity.IdentitySessionStore
import com.forestry.counter.data.remote.identity.IdentitySessionWrite
import com.forestry.counter.data.remote.identity.JwtSessionDecoder
import com.forestry.counter.data.remote.identity.LocalLoginRequestDto
import com.forestry.counter.data.remote.identity.LogoutRequestDto
import com.forestry.counter.data.remote.identity.RefreshRequestDto
import com.forestry.counter.data.remote.identity.PasswordResetConfirmRequestDto
import com.forestry.counter.data.remote.identity.PasswordResetRequestDto
import com.forestry.counter.data.remote.identity.RegistrationRequestDto
import com.forestry.counter.data.remote.identity.StoredIdentityTokens
import com.forestry.counter.data.remote.identity.TokenResponseDto
import com.forestry.counter.data.remote.identity.UpdateProfileRequestDto
import com.forestry.counter.domain.model.AccountProfile
import com.forestry.counter.domain.model.AccountActionCode
import com.forestry.counter.domain.model.AccountSession
import com.forestry.counter.domain.model.ApiConnectionState
import com.forestry.counter.domain.model.ApiDiagnostic
import com.forestry.counter.domain.model.GoogleNonce
import com.forestry.counter.domain.model.IdentityClientException
import com.forestry.counter.domain.model.IdentityError
import com.forestry.counter.domain.model.LoginOutcome
import com.forestry.counter.data.remote.identity.LoginOutcomeDto
import com.forestry.counter.data.remote.identity.MfaChallengeVerifyRequestDto
import com.forestry.counter.domain.model.IdentityProvider
import com.forestry.counter.domain.model.ProviderAvailability
import com.forestry.counter.domain.model.ProviderCapability
import com.forestry.counter.domain.repository.IdentityRepository
import com.forestry.counter.domain.repository.IdentitySessionGuard
import com.forestry.counter.domain.repository.GuardedProfileResult
import java.io.IOException
import java.util.Locale
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.coroutines.coroutineContext
import retrofit2.HttpException
import retrofit2.Response

internal class IdentityRepositoryImpl(
    private val api: IdentityApiService?,
    override val apiBaseUrl: String,
    override val isGoogleClientConfigured: Boolean,
    private val sessionStore: IdentitySessionStore,
) : IdentityRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val _session = MutableStateFlow(
        sessionStore.read()?.let { JwtSessionDecoder.decode(it.accessToken) }
    )
    private val _profile = MutableStateFlow<AccountProfile?>(null)
    // Une seule continuation prouvée ; une rotation externe inconnue reste refusée.
    private var lastRefreshCommit: RefreshCommit? = null

    override val session: StateFlow<AccountSession?> = _session.asStateFlow()
    override val sessionGeneration: StateFlow<Long> = sessionStore.coordinator.generation.asStateFlow()
    override val profile: StateFlow<AccountProfile?> = _profile.asStateFlow()
    override val isApiConfigured: Boolean = api != null

    override suspend fun getProviders(): Result<List<ProviderCapability>> = apiResult {
        providers().providers.map { capability ->
            ProviderCapability(
                provider = capability.provider.toProvider(),
                availability = capability.status.toAvailability(),
                label = capability.label,
            )
        }
    }

    override suspend fun loginWithPassword(
        email: String,
        password: String,
    ): Result<LoginOutcome> = authenticateOrChallenge {
        loginWithPassword(LocalLoginRequestDto(normalizeEmail(email), password))
    }

    override suspend fun completeMfaLogin(
        challengeToken: String,
        code: String,
        isRecoveryCode: Boolean,
    ): Result<LoginOutcome> = authenticateOrChallenge {
        loginWithMfa(
            MfaChallengeVerifyRequestDto(
                challengeToken = challengeToken,
                code = code.trim(),
                isRecoveryCode = isRecoveryCode,
            )
        )
    }

    override suspend fun register(
        email: String,
        password: String,
        displayName: String?,
    ): Result<AccountSession> = authenticate {
        register(
            RegistrationRequestDto(
                email = normalizeEmail(email),
                password = password,
                displayName = displayName?.trim()?.takeIf(String::isNotEmpty),
            )
        )
    }

    override suspend fun requestGoogleNonce(): Result<GoogleNonce> = apiResult {
        val response = googleNonce()
        GoogleNonce(response.nonce, response.expiresIn)
    }

    override suspend fun loginWithGoogle(
        idToken: String,
        nonce: String,
    ): Result<LoginOutcome> = authenticateOrChallenge {
        loginWithGoogle(GoogleLoginRequestDto(idToken, nonce))
    }

    override suspend fun linkGoogle(
        idToken: String,
        nonce: String,
    ): Result<LoginOutcome> {
        val expected = reserveAuthentication(requireSession = true).getOrElse { return Result.failure(it) }
        val accountId = expected.accountId() ?: return invalidSession()
        return authenticateOrChallenge(expected, accountId) {
            linkGoogle(expected.authorizationHeader(), GoogleLoginRequestDto(idToken, nonce))
        }
    }

    override suspend fun refreshSession(): Result<AccountSession> {
        val requested = sessionStore.snapshot() ?: return secureStorageUnavailable()
        val accountId = requested.accountId() ?: return invalidSession()
        return sessionStore.coordinator.refreshMutex.withLock {
            val current = sessionStore.snapshot() ?: return@withLock secureStorageUnavailable()
            if (current != requested) {
                // Un autre transport a déjà renouvelé : réutiliser seulement
                // une session du même compte, jamais un nouveau compte B.
                if (current.accountId() != accountId || current.tokens == requested.tokens) {
                    return@withLock staleSession()
                }
                val adopted = apiResult { adoptSession(current) }
                return@withLock adopted
            }
            authenticate(current, accountId, recordRefresh = true) {
                refresh(RefreshRequestDto(requireNotNull(current.tokens).refreshToken))
            }
        }
    }

    override fun captureSessionGuard(expectedAccountId: String): Result<IdentitySessionGuard> =
        synchronized(sessionStore.coordinator.monitor) {
            val current = sessionStore.snapshot() ?: return@synchronized secureStorageUnavailable()
            if (current.accountId() != expectedAccountId) return@synchronized staleSession()
            Result.success(StoredSessionGuard(sessionStore.coordinator, current, expectedAccountId))
        }

    override fun isSessionGuardCurrent(guard: IdentitySessionGuard): Boolean =
        synchronized(sessionStore.coordinator.monitor) {
            val stored = guard as? StoredSessionGuard ?: return@synchronized false
            stored.coordinator === sessionStore.coordinator &&
                stored.state.accountId() == stored.accountId && sessionStore.isCurrent(stored.state)
        }

    override fun isSessionGuardRenewalCurrent(parent: IdentitySessionGuard): Boolean =
        synchronized(sessionStore.coordinator.monitor) { provenRefreshChild(parent) != null }

    override suspend fun refreshSession(expected: IdentitySessionGuard): Result<IdentitySessionGuard> =
        refreshSessionGuard(expected, validateProfile = true)

    private suspend fun refreshSessionGuard(
        expected: IdentitySessionGuard,
        validateProfile: Boolean,
    ): Result<StoredSessionGuard> {
        val requested = synchronized(sessionStore.coordinator.monitor) {
            val stored = expected as? StoredSessionGuard
            if (stored == null || stored.coordinator !== sessionStore.coordinator ||
                stored.state.accountId() != stored.accountId ||
                (!isSessionGuardCurrent(stored) && (validateProfile || provenRefreshChild(stored) == null))
            ) null else stored
        } ?: return staleSession()
        return sessionStore.coordinator.refreshMutex.withLock {
            if (!validateProfile) {
                val child = synchronized(sessionStore.coordinator.monitor) { provenRefreshChild(requested) }
                if (child != null) return@withLock apiResult {
                    adoptSession(child.state)
                    child
                }
            }
            // Le mutex peut avoir attendu un autre transport. Ne jamais adopter B,
            // ni une nouvelle connexion A, à la place de la session demandée.
            val stillCurrent = synchronized(sessionStore.coordinator.monitor) {
                isSessionGuardCurrent(requested)
            }
            if (!stillCurrent) return@withLock staleSession()
            val result = if (validateProfile) {
                authenticateCommit(requested.state, requested.accountId, recordRefresh = true) {
                    refresh(RefreshRequestDto(requireNotNull(requested.state.tokens).refreshToken))
                }
            } else apiResult {
                persistSession(
                    requested.state,
                    refresh(RefreshRequestDto(requireNotNull(requested.state.tokens).refreshToken)),
                    requested.accountId,
                    recordRefresh = true,
                )
            }
            if (result.isFailure && !validateProfile) {
                if (!isSessionGuardCurrent(requested)) return@withLock staleSession()
                val failure = result.exceptionOrNull()
                if ((failure as? IdentityClientException)?.error in setOf(
                        IdentityError.INVALID_CREDENTIALS, IdentityError.INVALID_SERVER_RESPONSE,
                    )
                ) {
                    clearRejectedSession(requested.state).exceptionOrNull()?.let {
                        return@withLock Result.failure(it)
                    }
                }
            }
            result.map { committed ->
                StoredSessionGuard(sessionStore.coordinator, committed.state, committed.session.accountId)
            }
        }
    }

    override fun reloadStoredSession() {
        synchronized(sessionStore.coordinator.monitor) {
            val restored = sessionStore.read()?.let { JwtSessionDecoder.decode(it.accessToken) }
            if (_session.value?.accountId != restored?.accountId) _profile.value = null
            _session.value = restored
        }
    }

    override suspend fun logout(): Result<Unit> {
        // Invalider immédiatement les opérations en vol ; une panne réseau
        // ne doit jamais empêcher la déconnexion locale.
        val (refreshToken, cleared) = synchronized(sessionStore.coordinator.monitor) {
            lastRefreshCommit = null
            val token = sessionStore.read()?.refreshToken
            val succeeded = sessionStore.clear()
            _session.value = null
            _profile.value = null
            token to succeeded
        }
        val remote = if (refreshToken == null) Result.success(Unit) else apiResult {
            val response = logout(LogoutRequestDto(refreshToken))
            if (!response.isSuccessful) throw HttpException(response)
            if (response.body() == null) {
                throw IdentityClientException(IdentityError.INVALID_SERVER_RESPONSE)
            }
            Unit
        }
        return if (cleared) remote else secureStorageUnavailable()
    }

    override suspend fun loadProfile(): Result<AccountProfile> {
        val expected = sessionStore.snapshot() ?: return secureStorageUnavailable()
        val accountId = expected.accountId() ?: return invalidSession()
        return loadProfile(StoredSessionGuard(sessionStore.coordinator, expected, accountId)).result
    }

    override suspend fun loadProfile(expected: IdentitySessionGuard): GuardedProfileResult {
        val requested = expected as? StoredSessionGuard
        if (requested == null || requested.coordinator !== sessionStore.coordinator ||
            requested.state.accountId() != requested.accountId
        ) return GuardedProfileResult(expected, staleSession())
        val initial = profileForExactSnapshot(requested.state)
        if (!initial.isHttpUnauthorized()) {
            return GuardedProfileResult(requested, if (isSessionGuardCurrent(requested)) initial else staleSession())
        }
        val refreshed = refreshSessionGuard(requested, validateProfile = false)
        val child = refreshed.getOrElse { return GuardedProfileResult(requested, Result.failure(it)) }
        val retried = profileForExactSnapshot(child.state)
        if (!isSessionGuardCurrent(child)) return GuardedProfileResult(child, staleSession())
        if (retried.isHttpUnauthorized() ||
            (retried.exceptionOrNull() as? IdentityClientException)?.error == IdentityError.INVALID_SERVER_RESPONSE
        ) {
            clearRejectedSession(child.state).exceptionOrNull()?.let {
                return GuardedProfileResult(child, Result.failure(it))
            }
        }
        return GuardedProfileResult(child, retried)
    }

    override suspend fun updateDisplayName(displayName: String?): Result<AccountProfile> = authenticatedProfile { expected ->
        updateProfile(
            expected.authorizationHeader(),
            UpdateProfileRequestDto(displayName?.trim()?.takeIf(String::isNotEmpty)),
        )
    }

    override suspend fun requestEmailVerification(): Result<Unit> = apiResult {
        val expected = requireStoredSession()
        requestEmailVerification(expected.authorizationHeader())
        requireCurrent(expected)
        Unit
    }

    override suspend fun confirmEmailVerification(code: String): Result<AccountProfile> = authenticatedProfile { expected ->
        confirmEmailVerification(
            expected.authorizationHeader(),
            ActionCodeRequestDto(normalizeActionCode(code)),
        )
    }

    override suspend fun requestPasswordReset(email: String): Result<Unit> = apiResult {
        requestPasswordReset(PasswordResetRequestDto(normalizeEmail(email)))
        Unit
    }

    override suspend fun confirmPasswordReset(
        email: String,
        code: String,
        newPassword: String,
    ): Result<Unit> = apiResult {
        val expected = sessionStore.snapshot()
            ?: throw IdentityClientException(IdentityError.SECURE_STORAGE_UNAVAILABLE)
        confirmPasswordReset(
            PasswordResetConfirmRequestDto(
                email = normalizeEmail(email),
                code = normalizeActionCode(code),
                newPassword = newPassword,
            )
        )
        coroutineContext.ensureActive()
        synchronized(sessionStore.coordinator.monitor) {
            // Ne pas déconnecter B si ce reset a été lancé dans l'état A.
            if (sessionStore.isCurrent(expected)) {
                if (!sessionStore.clearIfCurrent(expected)) {
                    throw IdentityClientException(IdentityError.SECURE_STORAGE_UNAVAILABLE)
                }
                _session.value = null
                _profile.value = null
            }
        }
        Unit
    }

    override suspend fun diagnoseApi(): ApiDiagnostic {
        val service = api ?: return ApiDiagnostic(ApiConnectionState.NOT_CONFIGURED)
        val startedAt = SystemClock.elapsedRealtime()
        return try {
            val health = service.health()
            if (!health.isSuccessful || health.body() == null) {
                return ApiDiagnostic(
                    state = ApiConnectionState.UNREACHABLE,
                    latencyMillis = SystemClock.elapsedRealtime() - startedAt,
                    httpCode = health.code(),
                )
            }
            val ready = service.ready()
            val body = ready.body() ?: ready.decodeErrorBody()
            ApiDiagnostic(
                state = if (ready.isSuccessful && body?.status == "healthy") {
                    ApiConnectionState.CONNECTED
                } else {
                    ApiConnectionState.DEGRADED
                },
                latencyMillis = SystemClock.elapsedRealtime() - startedAt,
                httpCode = ready.code(),
                version = body?.version ?: health.body()?.version,
                environment = body?.environment ?: health.body()?.environment,
                dependencies = body?.dependencies.orEmpty(),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ApiDiagnostic(
                state = ApiConnectionState.UNREACHABLE,
                latencyMillis = SystemClock.elapsedRealtime() - startedAt,
            )
        }
    }

    private suspend fun authenticate(
        expected: IdentitySessionSnapshot? = null,
        expectedAccountId: String? = null,
        recordRefresh: Boolean = false,
        block: suspend IdentityApiService.() -> TokenResponseDto,
    ): Result<AccountSession> = authenticateCommit(expected, expectedAccountId, recordRefresh, block).map { it.session }

    private suspend fun authenticateCommit(
        expected: IdentitySessionSnapshot? = null,
        expectedAccountId: String? = null,
        recordRefresh: Boolean = false,
        block: suspend IdentityApiService.() -> TokenResponseDto,
    ): Result<SessionCommit> {
        val reserved = expected ?: reserveAuthentication().getOrElse { return Result.failure(it) }
        val result = apiResult { persistSession(reserved, block(), expectedAccountId, recordRefresh) }
        val committed = result.getOrElse { return Result.failure(it) }
        validateAuthenticatedProfile(committed.state)?.let { return Result.failure(it) }
        return apiResult {
            requireCurrent(committed.state)
            committed
        }
    }

    /**
     * Comme [authenticate], mais accepte les réponses intermédiaires du
     * serveur : défi MFA ou configuration MFA requise. La session n'est
     * enregistrée que lorsque des jetons sont réellement émis.
     */
    private suspend fun authenticateOrChallenge(
        expected: IdentitySessionSnapshot? = null,
        expectedAccountId: String? = null,
        block: suspend IdentityApiService.() -> LoginOutcomeDto,
    ): Result<LoginOutcome> {
        val reserved = expected ?: reserveAuthentication().getOrElse { return Result.failure(it) }
        var committed: SessionCommit? = null
        val result = apiResult {
            val dto = block()
            requireCurrent(reserved)
            val tokens = dto.tokensOrNull()
            val kinds = listOf(tokens != null, dto.mfaRequired, dto.mfaSetupRequired).count { it }
            if (
                kinds != 1 ||
                (tokens == null && (dto.accessToken != null || dto.refreshToken != null))
            ) throw IdentityClientException(IdentityError.INVALID_SERVER_RESPONSE)
            when {
                tokens != null -> {
                    val saved = persistSession(reserved, tokens, expectedAccountId)
                    committed = saved
                    LoginOutcome.Authenticated(saved.session)
                }

                dto.mfaRequired && !dto.challengeToken.isNullOrBlank() && (dto.expiresIn ?: 0) > 0 ->
                    LoginOutcome.MfaRequired(
                        challengeToken = dto.challengeToken,
                        expiresInSeconds = dto.expiresIn ?: 0,
                    )

                dto.mfaSetupRequired && !dto.setupToken.isNullOrBlank() && (dto.expiresIn ?: 0) > 0 ->
                    LoginOutcome.MfaSetupRequired(
                        setupToken = dto.setupToken,
                        expiresInSeconds = dto.expiresIn ?: 0,
                    )

                // Ni jetons, ni défi identifiable : le contrat serveur a
                // changé. Mieux vaut une erreur explicite qu'un état muet.
                else -> throw IdentityClientException(IdentityError.INVALID_SERVER_RESPONSE)
            }
        }
        if (result.isFailure) return result
        committed?.let { saved ->
            validateAuthenticatedProfile(saved.state)?.let { return Result.failure(it) }
        }
        return apiResult {
            requireCurrent(committed?.state ?: reserved)
            result.getOrThrow()
        }
    }

    private suspend fun <T> apiResult(block: suspend IdentityApiService.() -> T): Result<T> {
        val service = api
            ?: return Result.failure(IdentityClientException(IdentityError.API_NOT_CONFIGURED))
        return try {
            Result.success(service.block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error.toIdentityException())
        }
    }

    private data class SessionCommit(val session: AccountSession, val state: IdentitySessionSnapshot)
    private data class RefreshCommit(val parent: IdentitySessionSnapshot, val child: IdentitySessionSnapshot)

    private class StoredSessionGuard(
        val coordinator: IdentitySessionCoordinator,
        val state: IdentitySessionSnapshot,
        override val accountId: String,
    ) : IdentitySessionGuard

    private suspend fun persistSession(
        expected: IdentitySessionSnapshot,
        response: TokenResponseDto,
        expectedAccountId: String?,
        recordRefresh: Boolean = false,
    ): SessionCommit {
        coroutineContext.ensureActive()
        if (
            response.accessToken.isBlank() || response.refreshToken.isBlank() ||
            !response.tokenType.equals("Bearer", ignoreCase = true) || response.expiresIn <= 0
        ) throw IdentityClientException(IdentityError.INVALID_SERVER_RESPONSE)
        val session = JwtSessionDecoder.decode(response.accessToken)
            ?: throw IdentityClientException(IdentityError.INVALID_SERVER_RESPONSE)
        if (expectedAccountId != null && session.accountId != expectedAccountId) {
            throw IdentityClientException(IdentityError.INVALID_SERVER_RESPONSE)
        }
        val saved = sessionStore.saveIfCurrent(
            expected,
            StoredIdentityTokens(response.accessToken, response.refreshToken)
        )
        val state = when (saved) {
            is IdentitySessionWrite.Saved -> saved.state
            IdentitySessionWrite.Stale -> throw IdentityClientException(IdentityError.CANCELLED)
            IdentitySessionWrite.Unavailable -> throw IdentityClientException(IdentityError.SECURE_STORAGE_UNAVAILABLE)
        }
        if (!sessionStore.withCurrent(state) {
                // La preuve précède l'émission de session, y compris pour le collecteur du Compte.
                lastRefreshCommit = if (recordRefresh) RefreshCommit(expected, state) else null
                adoptSession(state)
            }
        ) throw IdentityClientException(IdentityError.CANCELLED)
        return SessionCommit(session, state)
    }

    private fun reserveAuthentication(requireSession: Boolean = false): Result<IdentitySessionSnapshot> {
        if (api == null) return Result.failure(IdentityClientException(IdentityError.API_NOT_CONFIGURED))
        return synchronized(sessionStore.coordinator.monitor) {
            val current = sessionStore.snapshot() ?: return@synchronized secureStorageUnavailable()
            if (requireSession && current.accountId() == null) return@synchronized invalidSession()
            val reserved = sessionStore.reserveAuthentication() ?: return@synchronized secureStorageUnavailable()
            lastRefreshCommit = null
            Result.success(reserved)
        }
    }

    private fun adoptSession(expected: IdentitySessionSnapshot): AccountSession {
        val restored = expected.tokens?.let { JwtSessionDecoder.decode(it.accessToken) }
            ?: throw IdentityClientException(IdentityError.INVALID_CREDENTIALS)
        if (!sessionStore.withCurrent(expected) {
                if (_session.value?.accountId != restored.accountId) _profile.value = null
                _session.value = restored
            }
        ) throw IdentityClientException(IdentityError.CANCELLED)
        return restored
    }

    private suspend fun requireCurrent(expected: IdentitySessionSnapshot) {
        coroutineContext.ensureActive()
        if (!sessionStore.isCurrent(expected)) throw IdentityClientException(IdentityError.CANCELLED)
    }

    private fun requireStoredSession(): IdentitySessionSnapshot {
        val expected = sessionStore.snapshot()
            ?: throw IdentityClientException(IdentityError.SECURE_STORAGE_UNAVAILABLE)
        if (expected.accountId() == null) throw IdentityClientException(IdentityError.INVALID_CREDENTIALS)
        return expected
    }

    private suspend fun authenticatedProfile(
        expected: IdentitySessionSnapshot? = null,
        block: suspend IdentityApiService.(IdentitySessionSnapshot) -> AccountProfileDto,
    ): Result<AccountProfile> = apiResult {
        val origin = expected ?: requireStoredSession()
        requireCurrent(origin)
        val accountId = requireNotNull(origin.accountId())
        val updated = block(origin).toDomain()
        coroutineContext.ensureActive()
        if (updated.accountId != accountId) {
            throw IdentityClientException(IdentityError.INVALID_SERVER_RESPONSE)
        }
        if (!sessionStore.withCurrent(origin) {
                adoptSession(origin)
                _profile.value = updated
            }
        ) throw IdentityClientException(IdentityError.CANCELLED)
        updated
    }

    /** Lecture brute sans renouvellement : nécessaire à la validation d'un commit sous refreshMutex. */
    private suspend fun profileForExactSnapshot(expected: IdentitySessionSnapshot): Result<AccountProfile> =
        authenticatedProfile(expected) { origin -> profile(origin.authorizationHeader()) }

    private fun provenRefreshChild(parent: IdentitySessionGuard): StoredSessionGuard? {
        val guard = parent as? StoredSessionGuard ?: return null
        if (guard.coordinator !== sessionStore.coordinator || guard.state.accountId() != guard.accountId) return null
        val commit = lastRefreshCommit ?: return null
        if (commit.parent != guard.state || !sessionStore.isCurrent(commit.child) ||
            commit.child.accountId() != guard.accountId
        ) return null
        return StoredSessionGuard(sessionStore.coordinator, commit.child, guard.accountId)
    }

    private fun clearRejectedSession(expected: IdentitySessionSnapshot): Result<Unit> =
        synchronized(sessionStore.coordinator.monitor) {
            if (!sessionStore.isCurrent(expected)) return@synchronized staleSession()
            val cleared = sessionStore.clearIfCurrent(expected)
            lastRefreshCommit = null
            _session.value = null
            _profile.value = null
            if (cleared) Result.success(Unit) else secureStorageUnavailable()
        }

    private fun Result<*>.isHttpUnauthorized(): Boolean =
        ((exceptionOrNull() as? IdentityClientException)?.cause as? HttpException)?.code() == 401

    private fun IdentitySessionSnapshot.accountId(): String? =
        tokens?.let { JwtSessionDecoder.decode(it.accessToken)?.accountId }

    private fun IdentitySessionSnapshot.authorizationHeader(): String =
        "Bearer ${tokens?.accessToken ?: throw IdentityClientException(IdentityError.INVALID_CREDENTIALS)}"

    private fun <T> invalidSession(): Result<T> =
        Result.failure(IdentityClientException(IdentityError.INVALID_CREDENTIALS))

    private fun <T> secureStorageUnavailable(): Result<T> =
        Result.failure(IdentityClientException(IdentityError.SECURE_STORAGE_UNAVAILABLE))

    private fun <T> staleSession(): Result<T> =
        Result.failure(IdentityClientException(IdentityError.CANCELLED))

    private suspend fun validateAuthenticatedProfile(expected: IdentitySessionSnapshot): Throwable? {
        val error = profileForExactSnapshot(expected).exceptionOrNull() ?: return null
        if (!sessionStore.isCurrent(expected)) return IdentityClientException(IdentityError.CANCELLED)
        val kind = (error as? IdentityClientException)?.error
        if (kind == IdentityError.CANCELLED) return error
        if (kind !in setOf(IdentityError.INVALID_CREDENTIALS, IdentityError.INVALID_SERVER_RESPONSE)) return null
        synchronized(sessionStore.coordinator.monitor) {
            // Une identité /me incohérente n'est jamais annoncée connectée.
            // Ne pas supprimer une session B établie depuis cette requête.
            if (!sessionStore.isCurrent(expected)) return IdentityClientException(IdentityError.CANCELLED)
            val cleared = sessionStore.clearIfCurrent(expected)
            lastRefreshCommit = null
            _session.value = null
            _profile.value = null
            if (!cleared) return IdentityClientException(IdentityError.SECURE_STORAGE_UNAVAILABLE)
        }
        return error
    }

    private fun Exception.toIdentityException(): IdentityClientException {
        if (this is IdentityClientException) return this
        if (this is IOException) {
            return IdentityClientException(IdentityError.NETWORK_UNAVAILABLE, this)
        }
        if (this !is HttpException) {
            return IdentityClientException(IdentityError.UNKNOWN, this)
        }
        val detail = response()?.errorBody()?.string()?.let { body ->
            runCatching { json.decodeFromString<ApiErrorDto>(body).detail }.getOrNull()
        }
        val identityError = when {
            detail == "ACCOUNT_ALREADY_EXISTS" -> IdentityError.ACCOUNT_ALREADY_EXISTS
            detail == "ACCOUNT_LINK_REQUIRED" -> IdentityError.ACCOUNT_LINK_REQUIRED
            detail == "GOOGLE_IDENTITY_ALREADY_LINKED" -> IdentityError.GOOGLE_ALREADY_LINKED
            detail == "CODE_INVALIDE_OU_EXPIRE" -> IdentityError.ACTION_CODE_INVALID
            code() == 401 -> IdentityError.INVALID_CREDENTIALS
            code() == 503 && detail?.contains("messagerie", ignoreCase = true) == true -> {
                IdentityError.EMAIL_DELIVERY_UNAVAILABLE
            }
            code() == 503 -> IdentityError.SERVER_UNAVAILABLE
            else -> IdentityError.UNKNOWN
        }
        return IdentityClientException(identityError, this)
    }

    private fun Response<HealthResponseDto>.decodeErrorBody(): HealthResponseDto? {
        val body = errorBody()?.string() ?: return null
        return runCatching { json.decodeFromString<HealthResponseDto>(body) }.getOrNull()
    }

    private fun normalizeEmail(email: String): String = email.trim().lowercase(Locale.ROOT)

    private fun normalizeActionCode(value: String): String = AccountActionCode.normalize(value)
        ?: throw IdentityClientException(IdentityError.INVALID_INPUT)

    private fun AccountProfileDto.toDomain(): AccountProfile = AccountProfile(
        accountId = accountId,
        displayName = displayName,
        email = email,
        emailVerified = emailVerified,
        providers = providers.map { it.toProvider() },
        roles = roles,
    )

    private fun String.toProvider(): IdentityProvider = when (this) {
        "local" -> IdentityProvider.LOCAL
        "google" -> IdentityProvider.GOOGLE
        "enterprise" -> IdentityProvider.ENTERPRISE
        else -> IdentityProvider.UNKNOWN
    }

    private fun String.toAvailability(): ProviderAvailability = when (this) {
        "available" -> ProviderAvailability.AVAILABLE
        "development" -> ProviderAvailability.DEVELOPMENT
        else -> ProviderAvailability.NOT_CONFIGURED
    }
}

object IdentityRepositoryFactory {
    fun create(context: Context): IdentityRepository {
        val baseUrl = BuildConfig.GSIE_API_BASE_URL.trim().trimEnd('/').let { value ->
            if (value.isEmpty()) "" else "$value/"
        }
        return IdentityRepositoryImpl(
            api = IdentityApiFactory.create(context, baseUrl),
            apiBaseUrl = baseUrl,
            isGoogleClientConfigured = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank(),
            sessionStore = EncryptedIdentitySessionStore(context),
        )
    }
}
