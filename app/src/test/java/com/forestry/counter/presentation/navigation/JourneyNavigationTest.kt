package com.forestry.counter.presentation.navigation

import com.forestry.counter.domain.journey.JourneyFeature
import com.forestry.counter.domain.journey.ProfessionProfile
import com.forestry.counter.presentation.screens.explorer.ExplorerCategory
import org.junit.Assert.*
import org.junit.Test

class JourneyNavigationTest {
    @Test fun wood_professions_have_depots_without_a_forest_detour() {
        listOf("conducteur_travaux_forestiers", "exploitant_forestier", "commerce_bois").forEach { code ->
            val access = JourneyNavigation.access(code)
            assertTrue(access.allows(JourneyFeature.WOOD_DEPOTS))
            assertTrue(JourneyNavigation.categories(access).isEmpty())
            assertFalse(access.allows(JourneyFeature.FORESTS))
        }
    }

    @Test fun technician_keeps_connected_management_tools_and_no_depot_shortcut() {
        val access = JourneyNavigation.access("technicien_forestier")
        assertEquals(listOf(ExplorerCategory.FORETS, ExplorerCategory.PARCELLES, ExplorerCategory.PLACETTES,
            ExplorerCategory.DIAGNOSTICS), JourneyNavigation.categories(access))
        assertFalse(access.allows(JourneyFeature.WOOD_DEPOTS))
    }

    @Test fun declaring_a_profession_never_opens_the_unconnected_enterprise_module() {
        ProfessionProfile.entries.forEach { profile ->
            val access = JourneyNavigation.access(profile.code)
            assertFalse(JourneyNavigation.tabs(access).contains(BottomNavDestination.MISSIONS))
            // Same guard is used on restored or direct routes, not just in visible tabs.
            assertFalse(JourneyNavigation.allowsTab(access, BottomNavDestination.MISSIONS))
        }
    }

    @Test fun unknown_or_free_text_profession_never_defaults_to_all_business_tools() {
        listOf(null, "", "autre", "ONF", "conducteur_travaux_forestiers ", "admin").forEach { code ->
            val access = JourneyNavigation.access(code)
            assertTrue(JourneyNavigation.categories(access).isEmpty())
            assertFalse(access.allows(JourneyFeature.WOOD_DEPOTS))
            assertEquals(listOf(BottomNavDestination.ACCUEIL, BottomNavDestination.EXPLORER,
                BottomNavDestination.CARTE, BottomNavDestination.PARAMETRES), JourneyNavigation.tabs(access))
        }
    }

    @Test fun changing_profession_recomputes_tools_without_merging_prior_permissions() {
        val before = JourneyNavigation.access("forestier_onf")
        val after = JourneyNavigation.access("commerce_bois")
        assertTrue(before.allows(JourneyFeature.FORESTS))
        assertFalse(before.allows(JourneyFeature.WOOD_DEPOTS))
        assertTrue(after.allows(JourneyFeature.WOOD_DEPOTS))
        assertFalse(after.allows(JourneyFeature.FORESTS))
    }

    @Test fun incomplete_modules_never_become_clickable_for_the_demo() {
        ProfessionProfile.entries.forEach { profile ->
            val access = JourneyNavigation.access(profile.code)
            listOf(JourneyFeature.WOOD_MEASUREMENTS, JourneyFeature.WOOD_STOCKS,
                JourneyFeature.WOOD_RECEPTIONS, JourneyFeature.WOOD_VALUATION, JourneyFeature.ARCHIVES).forEach {
                assertFalse("$profile must not offer $it yet", access.allows(it))
            }
        }
    }

    @Test fun legacy_public_forester_keeps_management_without_inventing_an_organisation() {
        val access = JourneyNavigation.access("forestier_onf_cnpf")
        assertEquals(ProfessionProfile.LEGACY_PUBLIC_FORESTER, access.profile)
        assertTrue(access.allows(JourneyFeature.FORESTS))
        assertFalse(JourneyNavigation.allowsTab(access, BottomNavDestination.MISSIONS))
    }
}
