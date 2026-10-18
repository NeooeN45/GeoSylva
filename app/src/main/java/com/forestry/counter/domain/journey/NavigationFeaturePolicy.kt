package com.forestry.counter.domain.journey

/** Identifiants partagés entre navigation, raccourcis, recherche et routes. */
enum class JourneyFeature {
    HOME, EXPLORER, MAP, SETTINGS,
    FORESTS, PARCELS, PLOTS, FOREST_DIAGNOSTICS, FOREST_CALCULATIONS, FOREST_SUMMARIES,
    WORKSITES, WOOD_DEPOTS, WOOD_MEASUREMENTS, WOOD_RECEPTIONS, WOOD_STOCKS, WOOD_VALUATION,
    ARCHIVES, MISSIONS,
}

enum class MissionAccessDecision {
    ALLOWED,
    MODULE_UNAVAILABLE,
    PROFESSION_NOT_ELIGIBLE,
    NO_ACTIVE_CONTEXT,
    INVALID_CONTEXT,
    SERVER_ACCESS_UNKNOWN,
    CONTEXT_MISMATCH,
    INVALID_OR_EXPIRED_EVIDENCE,
    ENTERPRISE_OFFER_REQUIRED,
    ENTERPRISE_WORKSPACE_REQUIRED,
    MEMBERSHIP_INACTIVE,
    VIEW_CAPABILITY_REQUIRED,
}

data class NavigationAccessDecision(
    val profile: ProfessionProfile,
    val allowedFeatures: Set<JourneyFeature>,
    val missions: MissionAccessDecision,
) {
    fun allows(feature: JourneyFeature): Boolean = feature in allowedFeatures
}

/**
 * Politique pure d'affichage et d'accès aux routes. Toutes les entrées doivent
 * utiliser le même résultat et le recalculer à chaque changement de contexte,
 * de droits et à leur expiration. Les routes restaurées doivent aussi être
 * contrôlées avant affichage ; cacher un bouton ne protège pas des données.
 *
 * [implementedFeatures] doit refléter les écrans réellement raccordés. Ce module
 * n'implémente aucune page, aucun abonnement, aucune validation cryptographique.
 * En l'absence du futur adaptateur authentifié, conserver [serverAccess] null.
 * [nowEpochSeconds] est fourni par la couche de validité de session, jamais par
 * une préférence utilisateur. Une horloge locale seule ne prouve pas une durée
 * d'autorisation hors ligne ; un doute doit invalider les preuves fournies.
 *
 * Le résultat ne remplace jamais les autorisations serveur/repository. VIEW
 * autorise uniquement la présence de Missions, pas l'affectation, la remise,
 * le contrôle ou une réception contractuelle.
 */
object NavigationFeaturePolicy {
    private val commonFeatures = setOf(
        JourneyFeature.HOME, JourneyFeature.EXPLORER, JourneyFeature.MAP, JourneyFeature.SETTINGS,
    )
    private val managementFeatures = setOf(
        JourneyFeature.FORESTS, JourneyFeature.PARCELS, JourneyFeature.PLOTS,
        JourneyFeature.FOREST_DIAGNOSTICS, JourneyFeature.FOREST_CALCULATIONS,
        JourneyFeature.FOREST_SUMMARIES, JourneyFeature.ARCHIVES,
    )
    private val operationsFeatures = setOf(
        JourneyFeature.WORKSITES, JourneyFeature.WOOD_DEPOTS, JourneyFeature.WOOD_MEASUREMENTS,
        JourneyFeature.WOOD_RECEPTIONS, JourneyFeature.WOOD_STOCKS,
        JourneyFeature.WOOD_VALUATION, JourneyFeature.ARCHIVES,
    )

    fun evaluate(
        professionCode: String?,
        implementedFeatures: Set<JourneyFeature>,
        activeContext: JourneyAccessContext? = null,
        serverAccess: ServerMissionAccessEvidence? = null,
        nowEpochSeconds: Long,
    ): NavigationAccessDecision {
        val profile = ProfessionJourneyResolver.resolve(professionCode)
        val journeyFeatures = when (profile.journey) {
            ProfessionJourney.MANAGEMENT -> managementFeatures
            ProfessionJourney.OPERATIONS -> operationsFeatures
            null -> emptySet()
        }
        val missions = evaluateMissions(
            profile, JourneyFeature.MISSIONS in implementedFeatures,
            activeContext, serverAccess, nowEpochSeconds,
        )
        val allowed = (commonFeatures + journeyFeatures).intersect(implementedFeatures).toMutableSet()
        if (missions == MissionAccessDecision.ALLOWED) allowed.add(JourneyFeature.MISSIONS)
        return NavigationAccessDecision(profile, allowed.toSet(), missions)
    }

    private fun evaluateMissions(
        profile: ProfessionProfile,
        implemented: Boolean,
        context: JourneyAccessContext?,
        access: ServerMissionAccessEvidence?,
        now: Long,
    ): MissionAccessDecision {
        if (!implemented) return MissionAccessDecision.MODULE_UNAVAILABLE
        if (!profile.missionsRelevant) return MissionAccessDecision.PROFESSION_NOT_ELIGIBLE
        if (context == null) return MissionAccessDecision.NO_ACTIVE_CONTEXT
        if (context.principalId.isBlank() || context.workspaceId.isNullOrBlank() || context.generation < 0) {
            return MissionAccessDecision.INVALID_CONTEXT
        }
        val entitlement = access?.entitlement ?: return MissionAccessDecision.SERVER_ACCESS_UNKNOWN
        val membership = access.membership ?: return MissionAccessDecision.SERVER_ACCESS_UNKNOWN
        val capabilities = access.capabilities ?: return MissionAccessDecision.SERVER_ACCESS_UNKNOWN
        val scopes = listOf(entitlement.scope, membership.scope, capabilities.scope)
        if (scopes.any { it.context != context }) return MissionAccessDecision.CONTEXT_MISMATCH
        if (now < 0 || scopes.any {
                it.issuedAtEpochSeconds < 0 || it.issuedAtEpochSeconds > now ||
                    it.expiresAtEpochSeconds <= now || it.expiresAtEpochSeconds <= it.issuedAtEpochSeconds
            }
        ) return MissionAccessDecision.INVALID_OR_EXPIRED_EVIDENCE
        if (entitlement.offer != JourneyOffer.ENTERPRISE) return MissionAccessDecision.ENTERPRISE_OFFER_REQUIRED
        if (membership.workspaceKind != JourneyWorkspaceKind.ENTERPRISE) {
            return MissionAccessDecision.ENTERPRISE_WORKSPACE_REQUIRED
        }
        if (membership.status != JourneyMembershipStatus.ACTIVE) return MissionAccessDecision.MEMBERSHIP_INACTIVE
        if (MissionCapability.VIEW !in capabilities.capabilities) return MissionAccessDecision.VIEW_CAPABILITY_REQUIRED
        return MissionAccessDecision.ALLOWED
    }
}
