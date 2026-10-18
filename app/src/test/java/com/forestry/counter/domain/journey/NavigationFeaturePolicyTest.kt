package com.forestry.counter.domain.journey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationFeaturePolicyTest {
    private val context = JourneyAccessContext("test-principal", "test-enterprise", 7)
    private val proof = ScopedMissionEvidence(context, 100, 200)
    private val evidence = ServerMissionAccessEvidence(
        MissionEntitlementEvidence(proof, JourneyOffer.ENTERPRISE),
        MissionMembershipEvidence(proof, JourneyWorkspaceKind.ENTERPRISE, JourneyMembershipStatus.ACTIVE),
        MissionCapabilitiesEvidence(proof, setOf(MissionCapability.VIEW)),
    )
    private val allFeatures = JourneyFeature.entries.toSet()

    private fun evaluate(
        profession: String? = "conducteur_travaux_forestiers",
        features: Set<JourneyFeature> = allFeatures,
        active: JourneyAccessContext? = context,
        access: ServerMissionAccessEvidence? = evidence,
        now: Long = 150,
    ) = NavigationFeaturePolicy.evaluate(profession, features, active, access, now)

    private fun assertDenied(expected: MissionAccessDecision, decision: NavigationAccessDecision) {
        assertEquals(expected, decision.missions)
        assertFalse(decision.allows(JourneyFeature.MISSIONS))
    }

    /** Test inputs only: each scope can be changed independently to detect mixed grants. */
    private fun withChangedProofs(replacement: ScopedMissionEvidence): List<ServerMissionAccessEvidence> = listOf(
        evidence.copy(entitlement = evidence.entitlement!!.copy(scope = replacement)),
        evidence.copy(membership = evidence.membership!!.copy(scope = replacement)),
        evidence.copy(capabilities = evidence.capabilities!!.copy(scope = replacement)),
    )

    @Test
    fun `missions are available only with all enterprise conditions fulfilled`() {
        val decision = evaluate()
        assertEquals(MissionAccessDecision.ALLOWED, decision.missions)
        assertTrue(decision.allows(JourneyFeature.MISSIONS))
        assertTrue(decision.allows(JourneyFeature.WOOD_DEPOTS))
    }

    @Test
    fun `enterprise technicians can receive missions in the management journey`() {
        val decision = evaluate(profession = "technicien_forestier")
        assertTrue(decision.allows(JourneyFeature.MISSIONS))
        assertTrue(decision.allows(JourneyFeature.PLOTS))
        assertFalse(decision.allows(JourneyFeature.WOOD_STOCKS))
    }

    @Test
    fun `unknown free and individual offers cannot activate missions`() {
        listOf(JourneyOffer.UNKNOWN, JourneyOffer.FREE, JourneyOffer.INDIVIDUAL).forEach { offer ->
            assertDenied(
                MissionAccessDecision.ENTERPRISE_OFFER_REQUIRED,
                evaluate(access = evidence.copy(entitlement = evidence.entitlement!!.copy(offer = offer))),
            )
        }
    }

    @Test
    fun `personal and unknown workspaces remain excluded even with enterprise offer`() {
        listOf(JourneyWorkspaceKind.PERSONAL, JourneyWorkspaceKind.UNKNOWN).forEach { kind ->
            assertDenied(
                MissionAccessDecision.ENTERPRISE_WORKSPACE_REQUIRED,
                evaluate(access = evidence.copy(membership = evidence.membership!!.copy(workspaceKind = kind))),
            )
        }
    }

    @Test
    fun `missing context membership entitlement or capabilities cannot grant access`() {
        assertDenied(MissionAccessDecision.NO_ACTIVE_CONTEXT, evaluate(active = null))
        listOf(null, evidence.copy(entitlement = null), evidence.copy(membership = null), evidence.copy(capabilities = null)).forEach {
            assertDenied(MissionAccessDecision.SERVER_ACCESS_UNKNOWN, evaluate(access = it))
        }
    }

    @Test
    fun `invalid identity workspace and generation are refused`() {
        listOf(
            context.copy(principalId = ""), context.copy(principalId = " "),
            context.copy(workspaceId = null), context.copy(workspaceId = ""), context.copy(workspaceId = " "),
            context.copy(generation = -1),
        ).forEach { invalid ->
            assertDenied(MissionAccessDecision.INVALID_CONTEXT, evaluate(active = invalid))
        }
    }

    @Test
    fun `every proof must belong to the current principal workspace and generation`() {
        listOf(
            context.copy(principalId = "another-principal"),
            context.copy(workspaceId = "another-workspace"),
            context.copy(generation = 6),
            context.copy(generation = 8),
        ).forEach { foreign ->
            withChangedProofs(proof.copy(context = foreign)).forEach {
                assertDenied(MissionAccessDecision.CONTEXT_MISMATCH, evaluate(access = it))
            }
        }
    }

    @Test
    fun `account or workspace switch invalidates an otherwise valid prior grant`() {
        listOf(
            context.copy(principalId = "another-principal"),
            context.copy(workspaceId = "another-workspace"),
            context.copy(generation = 8),
        ).forEach {
            assertDenied(MissionAccessDecision.CONTEXT_MISMATCH, evaluate(active = it))
        }
    }

    @Test
    fun `each proof must have a valid lifetime and must not be future dated`() {
        listOf(
            proof.copy(issuedAtEpochSeconds = -1),
            proof.copy(issuedAtEpochSeconds = 151),
            proof.copy(expiresAtEpochSeconds = 150),
            proof.copy(expiresAtEpochSeconds = 149),
            proof.copy(expiresAtEpochSeconds = 100),
            proof.copy(expiresAtEpochSeconds = 99),
        ).forEach { invalid ->
            withChangedProofs(invalid).forEach {
                assertDenied(MissionAccessDecision.INVALID_OR_EXPIRED_EVIDENCE, evaluate(access = it))
            }
        }
    }

    @Test
    fun `issue time is inclusive and expiry exclusive`() {
        assertEquals(MissionAccessDecision.ALLOWED, evaluate(now = 100).missions)
        assertEquals(MissionAccessDecision.ALLOWED, evaluate(now = 199).missions)
        assertDenied(MissionAccessDecision.INVALID_OR_EXPIRED_EVIDENCE, evaluate(now = 99))
        assertDenied(MissionAccessDecision.INVALID_OR_EXPIRED_EVIDENCE, evaluate(now = 200))
        assertDenied(MissionAccessDecision.INVALID_OR_EXPIRED_EVIDENCE, evaluate(now = -1))
    }

    @Test
    fun `inactive or unknown membership cannot activate missions`() {
        listOf(JourneyMembershipStatus.INACTIVE, JourneyMembershipStatus.UNKNOWN).forEach { status ->
            assertDenied(
                MissionAccessDecision.MEMBERSHIP_INACTIVE,
                evaluate(access = evidence.copy(membership = evidence.membership!!.copy(status = status))),
            )
        }
    }

    @Test
    fun `view capability must be explicit rather than inferred from other actions`() {
        listOf(emptySet(), setOf(MissionCapability.ASSIGN, MissionCapability.SUBMIT, MissionCapability.REVIEW)).forEach { capabilities ->
            assertDenied(
                MissionAccessDecision.VIEW_CAPABILITY_REQUIRED,
                evaluate(access = evidence.copy(capabilities = evidence.capabilities!!.copy(capabilities = capabilities))),
            )
        }
    }

    @Test
    fun `irrelevant and unknown professions do not expose missions despite valid enterprise evidence`() {
        listOf(null, "autre", "new_profile", "commerce_bois", "proprietaire_forestier", "etudiant", "chercheur_enseignant").forEach {
            assertDenied(MissionAccessDecision.PROFESSION_NOT_ELIGIBLE, evaluate(profession = it))
        }
    }

    @Test
    fun `unimplemented missions never appear even for an entitled supervisor`() {
        assertDenied(MissionAccessDecision.MODULE_UNAVAILABLE, evaluate(features = allFeatures - JourneyFeature.MISSIONS))
    }

    @Test
    fun `a profession on its own cannot grant enterprise missions`() {
        assertDenied(MissionAccessDecision.NO_ACTIVE_CONTEXT, evaluate(active = null, access = null))
    }

    @Test
    fun `forestry and wood tools are separated while shared navigation remains`() {
        val management = evaluate(profession = "technicien_forestier", access = null)
        val operations = evaluate(profession = "exploitant_forestier", access = null)
        listOf(JourneyFeature.HOME, JourneyFeature.EXPLORER, JourneyFeature.MAP, JourneyFeature.SETTINGS, JourneyFeature.ARCHIVES).forEach {
            assertTrue(management.allows(it))
            assertTrue(operations.allows(it))
        }
        listOf(JourneyFeature.FORESTS, JourneyFeature.PARCELS, JourneyFeature.PLOTS, JourneyFeature.FOREST_DIAGNOSTICS, JourneyFeature.FOREST_CALCULATIONS, JourneyFeature.FOREST_SUMMARIES).forEach {
            assertTrue(management.allows(it))
            assertFalse(operations.allows(it))
        }
        listOf(JourneyFeature.WORKSITES, JourneyFeature.WOOD_DEPOTS, JourneyFeature.WOOD_MEASUREMENTS, JourneyFeature.WOOD_RECEPTIONS, JourneyFeature.WOOD_STOCKS, JourneyFeature.WOOD_VALUATION).forEach {
            assertTrue(operations.allows(it))
            assertFalse(management.allows(it))
        }
    }

    @Test
    fun `unknown profiles keep only actual shared tools and never guessed business features`() {
        val decision = evaluate(profession = "unknown")
        assertEquals(setOf(JourneyFeature.HOME, JourneyFeature.EXPLORER, JourneyFeature.MAP, JourneyFeature.SETTINGS), decision.allowedFeatures)
    }

    @Test
    fun `policy never advertises unimplemented modules`() {
        val implemented = setOf(JourneyFeature.HOME, JourneyFeature.MAP, JourneyFeature.WOOD_DEPOTS)
        val decision = evaluate(features = implemented)
        assertEquals(implemented, decision.allowedFeatures)
        assertTrue(evaluate(features = emptySet()).allowedFeatures.isEmpty())
    }

    @Test
    fun `free access still includes the map without enterprise evidence`() {
        val decision = evaluate(active = null, access = null)
        assertTrue(decision.allows(JourneyFeature.MAP))
        assertFalse(decision.allows(JourneyFeature.MISSIONS))
    }
}
