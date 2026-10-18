package com.forestry.counter.domain.journey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ProfessionJourneyResolverTest {
    @Test
    fun `the twelve current codes resolve without changing persisted values`() {
        val expected = mapOf(
            "forestier_onf" to ProfessionProfile.ONF,
            "forestier_cnpf" to ProfessionProfile.CNPF,
            "forestier_ddt" to ProfessionProfile.DDT,
            "expert_forestier" to ProfessionProfile.EXPERT,
            "exploitant_forestier" to ProfessionProfile.LOGGING_CONTRACTOR,
            "conducteur_travaux_forestiers" to ProfessionProfile.WORKSITE_SUPERVISOR,
            "commerce_bois" to ProfessionProfile.TIMBER_TRADE,
            "technicien_forestier" to ProfessionProfile.TECHNICIAN,
            "proprietaire_forestier" to ProfessionProfile.OWNER,
            "etudiant" to ProfessionProfile.STUDENT,
            "chercheur_enseignant" to ProfessionProfile.RESEARCHER_TEACHER,
            "autre" to ProfessionProfile.OTHER,
        )
        expected.forEach { (code, profile) ->
            assertEquals(code, profile, ProfessionJourneyResolver.resolve(code))
            assertEquals(code, ProfessionJourneyResolver.resolve(code).code)
        }
    }

    @Test
    fun `legacy public forester remains management without asserting an organisation`() {
        val profile = ProfessionJourneyResolver.resolve("forestier_onf_cnpf")
        assertEquals(ProfessionProfile.LEGACY_PUBLIC_FORESTER, profile)
        assertEquals(ProfessionJourney.MANAGEMENT, profile.journey)
        assertEquals("forestier_onf_cnpf", profile.code)
    }

    @Test
    fun `timber work profiles use operations while technician uses management`() {
        listOf("exploitant_forestier", "conducteur_travaux_forestiers", "commerce_bois").forEach {
            assertEquals(ProfessionJourney.OPERATIONS, ProfessionJourneyResolver.resolve(it).journey)
        }
        assertEquals(ProfessionJourney.MANAGEMENT, ProfessionJourneyResolver.resolve("technicien_forestier").journey)
    }

    @Test
    fun `free text missing and unknown codes confer neither a guessed journey nor missions`() {
        listOf(null, "", " ", "Technicien", "forestier_onf entreprise", "future_profile", "autre").forEach {
            val profile = ProfessionJourneyResolver.resolve(it)
            assertNull(profile.journey)
            assertFalse(profile.missionsRelevant)
        }
    }

    @Test
    fun `commerce owner student and researcher have no inferred team mission need`() {
        listOf("commerce_bois", "proprietaire_forestier", "etudiant", "chercheur_enseignant").forEach {
            assertFalse(ProfessionJourneyResolver.resolve(it).missionsRelevant)
        }
    }
}
