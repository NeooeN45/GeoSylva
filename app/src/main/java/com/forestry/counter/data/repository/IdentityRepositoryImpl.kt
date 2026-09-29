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
import com.forestry.counter.data.remote.identity.CancelDeletionRequestDto
import com.forestry.counter.data.remote.identity.ChangeEmailRequestDto
import com.forestry.counter.data.remote.identity.ChangePasswordRequestDto
import com.forestry.counter.data.remote.identity.ConsentRequestDto
import com.forestry.counter.data.remote.identity.ConfirmEmailChangeRequestDto
import com.forestry.counter.data.remote.identity.MfaVerifyRequestDto
import com.forestry.counter.data.remote.identity.RequestDeletionRequestDto
import com.forestry.counter.data.remote.identity.RevokeSessionRequestDto
import com.forestry.counter.domain.model.AccountProfile
import com.forestry.counter.domain.model.AccountSession
import com.forestry.counter.domain.model.AccountConsent
import com.forestry.counter.domain.model.AccountDeviceSession
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
import com.forestry.counter.domain.model.MfaSetup
import com.forestry.counter.domain.repository.IdentityRepository
import java.io.IOException
import java.util.Locale
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import retrofit2.Response

internal class IdentityRepositoryImpl(
    private val api: IdentityApiService?,
    override val apiBaseUrl: String,
    override val isGoogleClientConfigured: Boolean,
    private val sessionStore: EncryptedIdentitySessionStore,
) : IdentityRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val _session = MutableStateFlow(
        sessionStore.read()?.let { JwtSessionDecoder.decode(it.accessToken) }
    )
    private val _profile = MutableStateFlow<AccountProfile?>(null)

    override val session: StateFlow<AccountSession?> = _session.asStateFlow()
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
    ): Result<AccountSession> = authenticate {
        loginWithGoogle(GoogleLoginRequestDto(idToken, nonce))
    }

    override suspend fun linkGoogle(
        idToken: String,
        nonce: String,
    ): Result<LoginOutcome> = authenticateOrChallenge {
        linkGoogle(authorizationHeader(), GoogleLoginRequestDto(idToken, nonce))
    }

    override suspend fun refreshSession(): Result<AccountSession> {
        return refreshSessionInternal(loadProfileAfterRefresh = true)
    }

    override suspend fun logout(): Result<Unit> {
        val refreshToken = sessionStore.read()?.refreshToken
        val result = if (refreshToken == null || api == null) {
            Result.success(Unit)
        } else {
            apiResult {
                logout(LogoutRequestDto(refreshToken))
                Unit
            }
        }
        sessionStore.clear()
        _session.value = null
        _profile.value = null
        return result
    }

    override suspend fun loadProfile(): Result<AccountProfile> = loadProfileInternal(allowRefresh = true)

    override suspend fun updateDisplayName(displayName: String?): Result<AccountProfile> = apiResult {
        updateProfile(
            authorizationHeader(),
            UpdateProfileRequestDto(displayName?.trim()?.takeIf(String::isNotEmpty)),
        ).toDomain().also { _profile.value = it }
    }

    override suspend fun exportAccountData(): Result<String> = apiResult {
        exportAccountData(authorizationHeader()).toString()
    }

    override suspend fun listConsents(): Result<List<AccountConsent>> = apiResult {
        listConsents(authorizationHeader()).consents.map { it.toDomain() }
    }

    override suspend fun acceptConsent(
        consentType: String,
        documentVersion: String,
    ): Result<AccountConsent> = apiResult {
        acceptConsent(
            authorizationHeader(),
            ConsentRequestDto(consentType, documentVersion),
        ).toDomain()
    }

    override suspend fun revokeConsent(consentType: String): Result<Unit> = apiResult {
        revokeConsent(authorizationHeader(), consentType)
        Unit
    }

    override suspend fun requestEmailChange(
        currentPassword: String,
        newEmail: String,
    ): Result<Unit> = apiResult {
        requestEmailChange(
            authorizationHeader(),
            ChangeEmailRequestDto(currentPassword, normalizeEmail(newEmail)),
        )
        Unit
    }

    override suspend fun confirmEmailChange(
        channel: String,
        code: String,
    ): Result<AccountProfile> = apiResult {
        confirmEmailChange(
            authorizationHeader(),
            ConfirmEmailChangeRequestDto(channel, code.trim()),
        ).toDomain().also { _profile.value = it }
    }

    override suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
    ): Result<Unit> = apiResult {
        changePassword(
            authorizationHeader(),
            ChangePasswordRequestDto(currentPassword, newPassword),
        )
        // Le serveur révoque toutes les sessions après ce changement.
        sessionStore.clear()
        _session.value = null
        _profile.value = null
        Unit
    }

    override suspend fun requestAccountDeletion(currentPassword: String): Result<Unit> = apiResult {
        requestAccountDeletion(
            authorizationHeader(),
            RequestDeletionRequestDto(currentPassword),
        )
        Unit
    }

    override suspend fun cancelAccountDeletion(email: String, code: String): Result<Unit> = apiResult {
        cancelAccountDeletion(
            CancelDeletionRequestDto(normalizeEmail(email), code.trim()),
        )
        Unit
    }

    override suspend fun listSessions(): Result<List<AccountDeviceSession>> = apiResult {
        listSessions(authorizationHeader()).sessions.map { it.toDomain() }
    }

    override suspend fun revokeAllSessions(): Result<Unit> = apiResult {
        revokeAllSessions(authorizationHeader())
        Unit
    }

    override suspend fun revokeSession(sessionId: String): Result<Unit> = apiResult {
        revokeSession(authorizationHeader(), RevokeSessionRequestDto(sessionId))
        Unit
    }

    override suspend fun getMfaStatus(): Result<Boolean> = apiResult {
        getMfaStatus(authorizationHeader()).enabled
    }

    override suspend fun setupMfa(): Result<MfaSetup> = apiResult {
        setupMfa(authorizationHeader()).let { response ->
            MfaSetup(response.secret, response.otpauthUri, response.recoveryCodes)
        }
    }

    override suspend fun verifyMfa(code: String, isRecoveryCode: Boolean): Result<Boolean> = apiResult {
        verifyMfa(
            authorizationHeader(),
            MfaVerifyRequestDto(code.trim(), isRecoveryCode),
        ).enabled
    }

    override suspend fun disableMfa(code: String, isRecoveryCode: Boolean): Result<Boolean> = apiResult {
        disableMfa(
            authorizationHeader(),
            MfaVerifyRequestDto(code.trim(), isRecoveryCode),
        ).enabled
    }

    override suspend fun requestEmailVerification(): Result<Unit> = apiResult {
        requestEmailVerification(authorizationHeader())
        Unit
    }

    override suspend fun confirmEmailVerification(code: String): Result<AccountProfile> = apiResult {
        confirmEmailVerification(
            authorizationHeader(),
            ActionCodeRequestDto(code.trim()),
        ).toDomain().also { _profile.value = it }
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
        confirmPasswordReset(
            PasswordResetConfirmRequestDto(
                email = normalizeEmail(email),
                code = code.trim(),
                newPassword = newPassword,
            )
        )
        sessionStore.clear()
        _session.value = null
        _profile.value = null
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

    /**
     * Recharge le profil en réparant une session dont l'access token est
     * arrivé à expiration. Une rotation est tentée au maximum une fois pour
     * éviter toute boucle entre le profil et le refresh.
     */
    private suspend fun loadProfileInternal(allowRefresh: Boolean): Result<AccountProfile> {
        if (allowRefresh && _session.value?.isExpired() == true) {
            val refreshed = refreshSessionInternal(loadProfileAfterRefresh = false)
            if (refreshed.isSuccess) return loadProfileInternal(allowRefresh = false)
            if (refreshed.isInvalidCredentials()) clearLocalSession()
            return Result.failure(
                refreshed.exceptionOrNull()
                    ?: IdentityClientException(IdentityError.INVALID_CREDENTIALS)
            )
        }

        val result = apiResult {
            profile(authorizationHeader()).toDomain().also { _profile.value = it }
        }
        if (result.isFailure && result.isInvalidCredentials()) {
            if (allowRefresh) {
                val refreshed = refreshSessionInternal(loadProfileAfterRefresh = false)
                if (refreshed.isSuccess) return loadProfileInternal(allowRefresh = false)
                if (refreshed.isInvalidCredentials()) clearLocalSession()
            } else {
                clearLocalSession()
            }
        }
        return result
    }

    private suspend fun refreshSessionInternal(
        loadProfileAfterRefresh: Boolean,
    ): Result<AccountSession> {
        val refreshToken = sessionStore.read()?.refreshToken
            ?: return Result.failure(IdentityClientException(IdentityError.INVALID_CREDENTIALS))
        val result = apiResult { persistSession(refresh(RefreshRequestDto(refreshToken))) }
        if (loadProfileAfterRefresh && result.isSuccess) {
            loadProfileInternal(allowRefresh = false)
        }
        return result
    }

    private fun clearLocalSession() {
        sessionStore.clear()
        _session.value = null
        _profile.value = null
    }

    private fun Result<*>.isInvalidCredentials(): Boolean =
        (exceptionOrNull() as? IdentityClientException)?.error == IdentityError.INVALID_CREDENTIALS

    private suspend fun authenticate(
        block: suspend IdentityApiService.() -> TokenResponseDto,
    ): Result<AccountSession> {
        val result = apiResult { persistSession(block()) }
        if (result.isSuccess) loadProfile()
        return result
    }

    /**
     * Comme [authenticate], mais accepte les réponses intermédiaires du
     * serveur : défi MFA ou configuration MFA requise. La session n'est
     * enregistrée que lorsque des jetons sont réellement émis.
     */
    private suspend fun authenticateOrChallenge(
        block: suspend IdentityApiService.() -> LoginOutcomeDto,
    ): Result<LoginOutcome> {
        val result = apiResult {
            val dto = block()
            val tokens = dto.tokensOrNull()
            when {
                tokens != null -> LoginOutcome.Authenticated(persistSession(tokens))

                dto.mfaRequired && dto.challengeToken != null ->
                    LoginOutcome.MfaRequired(
                        challengeToken = dto.challengeToken,
                        expiresInSeconds = dto.expiresIn ?: 0,
                    )

                dto.mfaSetupRequired && dto.setupToken != null ->
                    LoginOutcome.MfaSetupRequired(
                        setupToken = dto.setupToken,
                        expiresInSeconds = dto.expiresIn ?: 0,
                    )

                // Ni jetons, ni défi identifiable : le contrat serveur a
                // changé. Mieux vaut une erreur explicite qu'un état muet.
                else -> throw IdentityClientException(IdentityError.INVALID_SERVER_RESPONSE)
            }
        }
        if (result.getOrNull() is LoginOutcome.Authenticated) loadProfile()
        return result
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

    private fun persistSession(response: TokenResponseDto): AccountSession {
        val session = JwtSessionDecoder.decode(response.accessToken)
            ?: throw IdentityClientException(IdentityError.INVALID_SERVER_RESPONSE)
        val saved = sessionStore.save(
            StoredIdentityTokens(response.accessToken, response.refreshToken)
        )
        if (!saved) {
            sessionStore.clear()
            throw IdentityClientException(IdentityError.SECURE_STORAGE_UNAVAILABLE)
        }
        _session.value = session
        return session
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

    private fun authorizationHeader(): String {
        val accessToken = sessionStore.read()?.accessToken
            ?: throw IdentityClientException(IdentityError.INVALID_CREDENTIALS)
        return "Bearer $accessToken"
    }

    private fun AccountProfileDto.toDomain(): AccountProfile = AccountProfile(
        accountId = accountId,
        displayName = displayName,
        email = email,
        emailVerified = emailVerified,
        providers = providers.map { it.toProvider() },
        roles = roles,
    )

    private fun com.forestry.counter.data.remote.identity.ConsentResponseDto.toDomain() =
        AccountConsent(
            consentType = consentType,
            documentVersion = documentVersion,
            acceptedAt = acceptedAt,
            revokedAt = revokedAt,
        )

    private fun com.forestry.counter.data.remote.identity.SessionResponseDto.toDomain() =
        AccountDeviceSession(
            id = id,
            jti = jti,
            deviceName = deviceName,
            userAgent = userAgent,
            ipAddress = ipAddress,
            issuedAt = issuedAt,
            lastSeenAt = lastSeenAt,
            isCurrent = isCurrent,
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
