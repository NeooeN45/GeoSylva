package com.forestry.counter.domain.repository

import com.forestry.counter.domain.model.AccountSession
import com.forestry.counter.domain.model.AccountProfile
import com.forestry.counter.domain.model.ApiDiagnostic
import com.forestry.counter.domain.model.GoogleNonce
import com.forestry.counter.domain.model.IdentityClientException
import com.forestry.counter.domain.model.IdentityError
import com.forestry.counter.domain.model.LoginOutcome
import com.forestry.counter.domain.model.ProviderCapability
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private val legacyIdentityGeneration = MutableStateFlow(0L).asStateFlow()

/** Référence opaque à une génération de session authentifiée, sans exposer ses jetons. */
interface IdentitySessionGuard {
    val accountId: String
}

/** La garde accompagne aussi une panne du profil après renouvellement, sans exposer de jeton. */
data class GuardedProfileResult(
    val sessionGuard: IdentitySessionGuard,
    val result: Result<AccountProfile>,
)

interface IdentityRepository {
    val session: StateFlow<AccountSession?>
    /** Opaque revocation epoch; emits even when account metadata is unchanged. */
    val sessionGeneration: StateFlow<Long> get() = legacyIdentityGeneration
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

    suspend fun loginWithGoogle(idToken: String, nonce: String): Result<LoginOutcome>

    /**
     * Rattache une identité Google au compte connecté (ID-F-008).
     *
     * À déclencher depuis l'espace compte : le serveur exige une session
     * valide, un compte existant n'étant jamais fusionné automatiquement sur
     * la seule correspondance d'adresse e-mail.
     */
    suspend fun linkGoogle(idToken: String, nonce: String): Result<LoginOutcome>

    suspend fun refreshSession(): Result<AccountSession>

    /** Capture une référence uniquement pour une session du compte [expectedAccountId]. */
    fun captureSessionGuard(expectedAccountId: String): Result<IdentitySessionGuard> =
        Result.failure(IdentityClientException(IdentityError.CANCELLED))

    /** Logout ou nouvelle authentification invalident la référence, même pour le même compte. */
    fun isSessionGuardCurrent(guard: IdentitySessionGuard): Boolean = false

    /** Accepte uniquement l'enfant encore courant d'un commit de refresh démontré pour [parent]. */
    fun isSessionGuardRenewalCurrent(parent: IdentitySessionGuard): Boolean = false

    /**
     * Renouvelle précisément [expected], ou refuse avant toute requête.
     * La référence renvoyée identifie ce renouvellement, jamais une connexion ultérieure.
     * Une implémentation sans cette capacité refuse ; aucun repli vers le compte courant.
     */
    suspend fun refreshSession(expected: IdentitySessionGuard): Result<IdentitySessionGuard> =
        Result.failure(IdentityClientException(IdentityError.CANCELLED))

    /** Adopte la session chiffrée renouvelée par un autre transport, sans requête réseau. */
    fun reloadStoredSession()

    suspend fun logout(): Result<Unit>

    suspend fun loadProfile(): Result<AccountProfile>

    /** Lit exactement [expected], avec un seul renouvellement sur HTTP 401 et sa garde enfant. */
    suspend fun loadProfile(expected: IdentitySessionGuard): GuardedProfileResult = GuardedProfileResult(
        expected,
        Result.failure(IdentityClientException(IdentityError.CANCELLED)),
    )

    suspend fun updateDisplayName(displayName: String?): Result<AccountProfile>

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
