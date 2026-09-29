package com.forestry.counter.domain.model

/** Fournisseurs d'identité compris par l'écosystème Quintessences. */
enum class IdentityProvider {
    LOCAL,
    GOOGLE,
    ENTERPRISE,
    UNKNOWN,
}

enum class ProviderAvailability {
    AVAILABLE,
    NOT_CONFIGURED,
    DEVELOPMENT,
}

data class ProviderCapability(
    val provider: IdentityProvider,
    val availability: ProviderAvailability,
    val label: String,
)

/** Métadonnées non sensibles extraites d'un jeton GSIE déjà reçu. */
data class AccountSession(
    val accountId: String,
    val provider: IdentityProvider,
    val roles: List<String>,
    val expiresAtEpochSeconds: Long,
) {
    fun isExpired(nowEpochSeconds: Long = System.currentTimeMillis() / 1_000L): Boolean =
        expiresAtEpochSeconds <= nowEpochSeconds
}

data class AccountProfile(
    val accountId: String,
    val displayName: String?,
    val email: String?,
    val emailVerified: Boolean,
    val providers: List<IdentityProvider>,
    val roles: List<String>,
)

/** Consentement juridique versionné enregistré par Quintessences. */
data class AccountConsent(
    val consentType: String,
    val documentVersion: String,
    val acceptedAt: String,
    val revokedAt: String?,
)

/** Session serveur visible depuis l'espace Compte. */
data class AccountDeviceSession(
    val id: String,
    val jti: String,
    val deviceName: String?,
    val userAgent: String?,
    val ipAddress: String?,
    val issuedAt: String,
    val lastSeenAt: String,
    val isCurrent: Boolean,
)

/** Provisionnement MFA renvoyé une seule fois lors de l'activation. */
data class MfaSetup(
    val secret: String,
    val otpauthUri: String,
    val recoveryCodes: List<String>,
)

data class GoogleNonce(
    val value: String,
    val expiresInSeconds: Int,
)

/**
 * Issue d'une tentative de connexion.
 *
 * `POST /auth/login/password` ne renvoie pas toujours des jetons : le serveur
 * peut réclamer un second facteur ([MfaRequired]) ou, pour un compte
 * administrateur sans MFA, exiger sa configuration préalable
 * ([MfaSetupRequired]). Modéliser ces trois issues évite de traiter une
 * réponse légitime comme une erreur de format.
 */
sealed interface LoginOutcome {
    /** Connexion terminée : les jetons sont émis et la session est ouverte. */
    data class Authenticated(val session: AccountSession) : LoginOutcome

    /**
     * Le compte exige un second facteur. Le jeton de défi est à usage unique
     * et à durée de vie courte ([expiresInSeconds], 300 s côté serveur).
     */
    data class MfaRequired(
        val challengeToken: String,
        val expiresInSeconds: Int,
    ) : LoginOutcome

    /**
     * Compte administrateur sans second facteur configuré. GeoSylva ne porte
     * pas ce parcours : la configuration se fait depuis l'interface web.
     */
    data class MfaSetupRequired(
        val setupToken: String,
        val expiresInSeconds: Int,
    ) : LoginOutcome
}

enum class ApiConnectionState {
    CONNECTED,
    DEGRADED,
    UNREACHABLE,
    NOT_CONFIGURED,
}

data class ApiDiagnostic(
    val state: ApiConnectionState,
    val latencyMillis: Long? = null,
    val httpCode: Int? = null,
    val version: String? = null,
    val environment: String? = null,
    val dependencies: Map<String, String> = emptyMap(),
    val checkedAtEpochMillis: Long = System.currentTimeMillis(),
)

enum class IdentityError {
    API_NOT_CONFIGURED,
    GOOGLE_NOT_CONFIGURED,
    SECURE_STORAGE_UNAVAILABLE,
    INVALID_INPUT,
    REGISTRATION_CONSENT_REQUIRED,
    INVALID_CREDENTIALS,
    ACCOUNT_ALREADY_EXISTS,
    ACCOUNT_LINK_REQUIRED,
    /** Cette identité Google est déjà rattachée à un compte. */
    GOOGLE_ALREADY_LINKED,
    ACTION_CODE_INVALID,
    EMAIL_DELIVERY_UNAVAILABLE,
    NETWORK_UNAVAILABLE,
    SERVER_UNAVAILABLE,
    INVALID_SERVER_RESPONSE,
    CANCELLED,
    UNKNOWN,
}

class IdentityClientException(
    val error: IdentityError,
    cause: Throwable? = null,
) : Exception(error.name, cause)
