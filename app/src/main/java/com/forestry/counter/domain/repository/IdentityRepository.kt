package com.forestry.counter.domain.repository

import com.forestry.counter.domain.model.AccountSession
import com.forestry.counter.domain.model.AccountProfile
import com.forestry.counter.domain.model.AccountConsent
import com.forestry.counter.domain.model.AccountDeviceSession
import com.forestry.counter.domain.model.ApiDiagnostic
import com.forestry.counter.domain.model.GoogleNonce
import com.forestry.counter.domain.model.LoginOutcome
import com.forestry.counter.domain.model.MfaSetup
import com.forestry.counter.domain.model.ProviderCapability
import kotlinx.coroutines.flow.StateFlow

interface IdentityRepository {
    val session: StateFlow<AccountSession?>
    val profile: StateFlow<AccountProfile?>
    val apiBaseUrl: String
    val isApiConfigured: Boolean
    val isGoogleClientConfigured: Boolean

    suspend fun getProviders(): Result<List<ProviderCapability>>

    /**
     * Connexion locale. Peut se terminer sans jetons si le compte exige un
     * second facteur — voir [LoginOutcome].
     */
    suspend fun loginWithPassword(email: String, password: String): Result<LoginOutcome>

    /**
     * Termine une connexion interrompue par un défi MFA.
     *
     * @param code six chiffres TOTP, ou un code de récupération si
     *   [isRecoveryCode] vaut `true`.
     */
    suspend fun completeMfaLogin(
        challengeToken: String,
        code: String,
        isRecoveryCode: Boolean = false,
    ): Result<LoginOutcome>

    suspend fun register(
        email: String,
        password: String,
        displayName: String?,
    ): Result<AccountSession>

    suspend fun requestGoogleNonce(): Result<GoogleNonce>

    suspend fun loginWithGoogle(idToken: String, nonce: String): Result<AccountSession>

    /**
     * Rattache une identité Google au compte connecté (ID-F-008).
     *
     * À déclencher depuis l'espace compte : le serveur exige une session
     * valide, un compte existant n'étant jamais fusionné automatiquement sur
     * la seule correspondance d'adresse e-mail.
     */
    suspend fun linkGoogle(idToken: String, nonce: String): Result<LoginOutcome>

    suspend fun refreshSession(): Result<AccountSession>

    suspend fun logout(): Result<Unit>

    suspend fun loadProfile(): Result<AccountProfile>

    suspend fun updateDisplayName(displayName: String?): Result<AccountProfile>

    /** Exporte les données personnelles autorisées au format JSON. */
    suspend fun exportAccountData(): Result<String>

    suspend fun listConsents(): Result<List<AccountConsent>>

    suspend fun acceptConsent(
        consentType: String,
        documentVersion: String,
    ): Result<AccountConsent>

    suspend fun revokeConsent(consentType: String): Result<Unit>

    suspend fun requestEmailChange(
        currentPassword: String,
        newEmail: String,
    ): Result<Unit>

    suspend fun confirmEmailChange(
        channel: String,
        code: String,
    ): Result<AccountProfile>

    suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
    ): Result<Unit>

    /** Programme la suppression différée ; la finalisation reste serveur. */
    suspend fun requestAccountDeletion(currentPassword: String): Result<Unit>

    suspend fun cancelAccountDeletion(email: String, code: String): Result<Unit>

    suspend fun listSessions(): Result<List<AccountDeviceSession>>

    suspend fun revokeAllSessions(): Result<Unit>

    suspend fun revokeSession(sessionId: String): Result<Unit>

    suspend fun getMfaStatus(): Result<Boolean>

    suspend fun setupMfa(): Result<MfaSetup>

    suspend fun verifyMfa(code: String, isRecoveryCode: Boolean = false): Result<Boolean>

    suspend fun disableMfa(code: String, isRecoveryCode: Boolean = false): Result<Boolean>

    suspend fun requestEmailVerification(): Result<Unit>

    suspend fun confirmEmailVerification(code: String): Result<AccountProfile>

    suspend fun requestPasswordReset(email: String): Result<Unit>

    suspend fun confirmPasswordReset(
        email: String,
        code: String,
        newPassword: String,
    ): Result<Unit>

    suspend fun diagnoseApi(): ApiDiagnostic
}
