package com.forestry.counter.data.remote.identity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
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
    suspend fun loginWithGoogle(@Body request: GoogleLoginRequestDto): LoginOutcomeDto

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
