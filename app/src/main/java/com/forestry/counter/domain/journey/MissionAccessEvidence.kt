package com.forestry.counter.domain.journey

/**
 * Contexte actif fourni par la couche d'identité. Une nouvelle génération est
 * nécessaire lors d'un changement/invalidation de compte ou d'espace. Ce numéro
 * ne doit pas provenir d'une préférence métier ni d'un jeu de démonstration.
 */
data class JourneyAccessContext(
    val principalId: String,
    val workspaceId: String?,
    val generation: Long,
)

enum class JourneyOffer { UNKNOWN, FREE, INDIVIDUAL, ENTERPRISE }
enum class JourneyWorkspaceKind { UNKNOWN, PERSONAL, ENTERPRISE }
enum class JourneyMembershipStatus { UNKNOWN, INACTIVE, ACTIVE }
enum class MissionCapability { VIEW, ASSIGN, SUBMIT, REVIEW }

/**
 * Métadonnées de validité d'une preuve reçue du serveur. Cette classe ne vérifie
 * aucune signature et sa construction ne prouve pas une authentification.
 * Le futur adaptateur de confiance doit vérifier l'origine, la session et la
 * portée avant construction ; la politique vérifie ensuite liaison et durée.
 */
data class ScopedMissionEvidence(
    val context: JourneyAccessContext,
    val issuedAtEpochSeconds: Long,
    val expiresAtEpochSeconds: Long,
)

data class MissionEntitlementEvidence(
    val scope: ScopedMissionEvidence,
    val offer: JourneyOffer,
)

data class MissionMembershipEvidence(
    val scope: ScopedMissionEvidence,
    val workspaceKind: JourneyWorkspaceKind,
    val status: JourneyMembershipStatus,
)

data class MissionCapabilitiesEvidence(
    val scope: ScopedMissionEvidence,
    val capabilities: Set<MissionCapability>,
)

/**
 * Entrée du futur adaptateur serveur, actuellement non branché. Chaque preuve
 * porte sa propre liaison : impossible d'assembler une offre du compte A avec
 * une appartenance ou des capacités du compte B/espace B/génération précédente.
 * Une preuve absente doit rester null. Ne jamais dériver ENTERPRISE du fournisseur
 * d'identité, des rôles génériques, du métier ou d'un achat individuel.
 */
data class ServerMissionAccessEvidence(
    val entitlement: MissionEntitlementEvidence?,
    val membership: MissionMembershipEvidence?,
    val capabilities: MissionCapabilitiesEvidence?,
)
