package com.forestry.counter.data.remote.identity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST

internal interface IdentityApiService {
    @GET("api/v1/auth/providers")
    suspend fun providers(): ProvidersResponseDto

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegistrationRequestDto): TokenResponseDto

    // Renvoie soit des jetons, soit un défi MFA, soit une demande de
    // configuration MFA (compte administrateur). Voir [LoginOutcomeDto].
    @POST("api/v1/auth/login/password")
    suspend fun loginWithPassword(@Body request: LocalLoginRequestDto): LoginOutcomeDto

    @POST("api/v1/auth/login/mfa")
    suspend fun loginWithMfa(@Body request: MfaChallengeVerifyRequestDto): LoginOutcomeDto

    @POST("api/v1/auth/google/nonce")
    suspend fun googleNonce(): GoogleNonceResponseDto

    @POST("api/v1/auth/login/google")
    suspend fun loginWithGoogle(@Body request: GoogleLoginRequestDto): TokenResponseDto

    /**
     * Rattache une identité Google au compte déjà connecté.
     *
     * Exige un jeton d'accès : le serveur refuse de fusionner deux comptes
     * sur la seule foi d'une adresse e-mail identique (ID-F-007). Le
     * rattachement est donc une action volontaire, faite depuis l'espace
     * compte, et jamais depuis l'écran de connexion.
     */
    @POST("api/v1/auth/link/google")
    suspend fun linkGoogle(
        @Header("Authorization") authorization: String,
        @Body request: GoogleLoginRequestDto,
    ): LoginOutcomeDto

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequestDto): TokenResponseDto

    @POST("api/v1/auth/logout")
    suspend fun logout(@Body request: LogoutRequestDto): Response<LogoutResponseDto>

    @GET("api/v1/auth/me")
    suspend fun profile(@Header("Authorization") authorization: String): AccountProfileDto

    @PATCH("api/v1/auth/me")
    suspend fun updateProfile(
        @Header("Authorization") authorization: String,
        @Body request: UpdateProfileRequestDto,
    ): AccountProfileDto

    @GET("api/v1/auth/me/export")
    suspend fun exportAccountData(
        @Header("Authorization") authorization: String,
    ): JsonObject

    @GET("api/v1/auth/me/consents")
    suspend fun listConsents(
        @Header("Authorization") authorization: String,
    ): ConsentListResponseDto

    @POST("api/v1/auth/me/consents")
    suspend fun acceptConsent(
        @Header("Authorization") authorization: String,
        @Body request: ConsentRequestDto,
    ): ConsentResponseDto

    @DELETE("api/v1/auth/me/consents/{consent_type}")
    suspend fun revokeConsent(
        @Header("Authorization") authorization: String,
        @retrofit2.http.Path("consent_type") consentType: String,
    ): CompletedResponseDto

    @POST("api/v1/auth/email/change/request")
    suspend fun requestEmailChange(
        @Header("Authorization") authorization: String,
        @Body request: ChangeEmailRequestDto,
    ): AcceptedResponseDto

    @POST("api/v1/auth/email/change/confirm")
    suspend fun confirmEmailChange(
        @Header("Authorization") authorization: String,
        @Body request: ConfirmEmailChangeRequestDto,
    ): AccountProfileDto

    @POST("api/v1/auth/password/change")
    suspend fun changePassword(
        @Header("Authorization") authorization: String,
        @Body request: ChangePasswordRequestDto,
    ): CompletedResponseDto

    @POST("api/v1/auth/me/deletion/request")
    suspend fun requestAccountDeletion(
        @Header("Authorization") authorization: String,
        @Body request: RequestDeletionRequestDto,
    ): AcceptedResponseDto

    @POST("api/v1/auth/deletion/cancel")
    suspend fun cancelAccountDeletion(
        @Body request: CancelDeletionRequestDto,
    ): CompletedResponseDto

    @GET("api/v1/auth/sessions")
    suspend fun listSessions(
        @Header("Authorization") authorization: String,
    ): ListSessionsResponseDto

    @DELETE("api/v1/auth/sessions")
    suspend fun revokeAllSessions(
        @Header("Authorization") authorization: String,
    ): CompletedResponseDto

    @POST("api/v1/auth/sessions/revoke")
    suspend fun revokeSession(
        @Header("Authorization") authorization: String,
        @Body request: RevokeSessionRequestDto,
    ): CompletedResponseDto

    @GET("api/v1/auth/mfa/status")
    suspend fun getMfaStatus(
        @Header("Authorization") authorization: String,
    ): MfaStatusResponseDto

    @POST("api/v1/auth/mfa/setup")
    suspend fun setupMfa(
        @Header("Authorization") authorization: String,
    ): MfaSetupResponseDto

    @POST("api/v1/auth/mfa/verify")
    suspend fun verifyMfa(
        @Header("Authorization") authorization: String,
        @Body request: MfaVerifyRequestDto,
    ): MfaStatusResponseDto

    @HTTP(method = "DELETE", path = "api/v1/auth/mfa", hasBody = true)
    suspend fun disableMfa(
        @Header("Authorization") authorization: String,
        @Body request: MfaVerifyRequestDto,
    ): MfaStatusResponseDto

    @POST("api/v1/auth/email/verification/request")
    suspend fun requestEmailVerification(
        @Header("Authorization") authorization: String,
    ): AcceptedResponseDto

    @POST("api/v1/auth/email/verification/confirm")
    suspend fun confirmEmailVerification(
        @Header("Authorization") authorization: String,
        @Body request: ActionCodeRequestDto,
    ): AccountProfileDto

    @POST("api/v1/auth/password/reset/request")
    suspend fun requestPasswordReset(@Body request: PasswordResetRequestDto): AcceptedResponseDto

    @POST("api/v1/auth/password/reset/confirm")
    suspend fun confirmPasswordReset(
        @Body request: PasswordResetConfirmRequestDto,
    ): CompletedResponseDto

    @GET("health")
    suspend fun health(): Response<HealthResponseDto>

    @GET("ready")
    suspend fun ready(): Response<HealthResponseDto>
}

@Serializable
internal data class ProviderCapabilityDto(
    val provider: String,
    val status: String,
    val label: String,
)

@Serializable
internal data class ProvidersResponseDto(
    val providers: List<ProviderCapabilityDto>,
)

@Serializable
internal data class RegistrationRequestDto(
    val email: String,
    val password: String,
    @SerialName("display_name") val displayName: String? = null,
)

@Serializable
internal data class LocalLoginRequestDto(
    val email: String,
    val password: String,
)

@Serializable
internal data class GoogleLoginRequestDto(
    @SerialName("id_token") val idToken: String,
    val nonce: String,
)

@Serializable
internal data class GoogleNonceResponseDto(
    val nonce: String,
    @SerialName("expires_in") val expiresIn: Int,
)

@Serializable
internal data class TokenResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Int,
)

/**
 * Réponse de `POST /auth/login/password` et `POST /auth/login/mfa`.
 *
 * Le serveur déclare trois modèles distincts — `TokenResponse`,
 * `MfaChallengeResponse` et `AdminMfaSetupRequiredResponse`. Plutôt que de
 * faire de la désérialisation polymorphe sur un JSON sans discriminant, on
 * accepte un objet permissif et on lève l'ambiguïté sur la présence des
 * champs. `ignoreUnknownKeys` est déjà actif côté client.
 *
 * Cette souplesse corrige un vrai défaut : la version précédente attendait
 * strictement `TokenResponseDto`, si bien qu'un compte protégé par un second
 * facteur ne pouvait plus se connecter depuis l'application.
 */
@Serializable
internal data class LoginOutcomeDto(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("expires_in") val expiresIn: Int? = null,
    @SerialName("mfa_required") val mfaRequired: Boolean = false,
    @SerialName("challenge_token") val challengeToken: String? = null,
    @SerialName("mfa_setup_required") val mfaSetupRequired: Boolean = false,
    @SerialName("setup_token") val setupToken: String? = null,
) {
    /** Jetons exploitables, ou `null` s'il s'agit d'une étape intermédiaire. */
    fun tokensOrNull(): TokenResponseDto? {
        val access = accessToken ?: return null
        val refresh = refreshToken ?: return null
        return TokenResponseDto(
            accessToken = access,
            refreshToken = refresh,
            tokenType = tokenType ?: "Bearer",
            expiresIn = expiresIn ?: 0,
        )
    }
}

@Serializable
internal data class MfaChallengeVerifyRequestDto(
    @SerialName("challenge_token") val challengeToken: String,
    val code: String,
    @SerialName("is_recovery_code") val isRecoveryCode: Boolean = false,
)

@Serializable
internal data class RefreshRequestDto(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
internal data class LogoutRequestDto(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
internal data class LogoutResponseDto(
    val revoked: Boolean,
)

@Serializable
internal data class HealthResponseDto(
    val status: String,
    val version: String,
    val environment: String,
    val timestamp: String,
    val dependencies: Map<String, String> = emptyMap(),
)

@Serializable
internal data class ApiErrorDto(
    val detail: String? = null,
)

@Serializable
internal data class AccountProfileDto(
    @SerialName("account_id") val accountId: String,
    @SerialName("display_name") val displayName: String? = null,
    val email: String? = null,
    @SerialName("email_verified") val emailVerified: Boolean,
    val providers: List<String>,
    val roles: List<String>,
)

@Serializable
internal data class UpdateProfileRequestDto(
    @SerialName("display_name") val displayName: String?,
)

@Serializable
internal data class ConsentRequestDto(
    @SerialName("consent_type") val consentType: String,
    @SerialName("document_version") val documentVersion: String,
)

@Serializable
internal data class ConsentResponseDto(
    @SerialName("consent_type") val consentType: String,
    @SerialName("document_version") val documentVersion: String,
    @SerialName("accepted_at") val acceptedAt: String,
    @SerialName("revoked_at") val revokedAt: String? = null,
)

@Serializable
internal data class ConsentListResponseDto(
    val consents: List<ConsentResponseDto> = emptyList(),
)

@Serializable
internal data class ChangeEmailRequestDto(
    @SerialName("current_password") val currentPassword: String,
    @SerialName("new_email") val newEmail: String,
)

@Serializable
internal data class ConfirmEmailChangeRequestDto(
    val channel: String,
    val code: String,
)

@Serializable
internal data class ChangePasswordRequestDto(
    @SerialName("current_password") val currentPassword: String,
    @SerialName("new_password") val newPassword: String,
)

@Serializable
internal data class RequestDeletionRequestDto(
    @SerialName("current_password") val currentPassword: String,
)

@Serializable
internal data class CancelDeletionRequestDto(
    val email: String,
    val code: String,
)

@Serializable
internal data class SessionResponseDto(
    val id: String,
    val jti: String,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("user_agent") val userAgent: String? = null,
    @SerialName("ip_address") val ipAddress: String? = null,
    @SerialName("issued_at") val issuedAt: String,
    @SerialName("last_seen_at") val lastSeenAt: String,
    @SerialName("is_current") val isCurrent: Boolean = false,
)

@Serializable
internal data class ListSessionsResponseDto(
    val sessions: List<SessionResponseDto> = emptyList(),
    val total: Int = 0,
)

@Serializable
internal data class RevokeSessionRequestDto(
    @SerialName("session_id") val sessionId: String,
)

@Serializable
internal data class MfaStatusResponseDto(
    val enabled: Boolean,
)

@Serializable
internal data class MfaSetupResponseDto(
    val secret: String,
    @SerialName("otpauth_uri") val otpauthUri: String,
    @SerialName("recovery_codes") val recoveryCodes: List<String> = emptyList(),
)

@Serializable
internal data class MfaVerifyRequestDto(
    val code: String,
    @SerialName("is_recovery_code") val isRecoveryCode: Boolean = false,
)

@Serializable
internal data class ActionCodeRequestDto(val code: String)

@Serializable
internal data class PasswordResetRequestDto(val email: String)

@Serializable
internal data class PasswordResetConfirmRequestDto(
    val email: String,
    val code: String,
    @SerialName("new_password") val newPassword: String,
)

@Serializable
internal data class AcceptedResponseDto(val accepted: Boolean)

@Serializable
internal data class CompletedResponseDto(val completed: Boolean)
