package com.forestry.counter.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forestry.counter.domain.journey.ProfessionProfile
import com.forestry.counter.presentation.navigation.BottomNavDestination
import com.forestry.counter.presentation.navigation.CollapsedMiniNav
import com.forestry.counter.presentation.navigation.JourneyNavigation
import com.forestry.counter.presentation.screens.explorer.ExplorerContent
import com.forestry.counter.presentation.screens.home.JourneyChoiceScreen
import com.forestry.counter.presentation.screens.home.OperationsHomeScreen
import com.forestry.counter.presentation.theme.ForestryCounterTheme
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Production composables, isolated state: no preferences, identity or user records are changed. */
@RunWith(AndroidJUnit4::class)
class JourneyEntryUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun supervisor_home_opens_depots_without_forest_or_mission_shortcuts() {
        var opened = ""
        content {
            OperationsHomeScreen(ProfessionProfile.WORKSITE_SUPERVISOR,
                { opened = "depots" }, { opened = "map" }, { opened = "packs" }, { opened = "profession" })
        }
        compose.onNodeWithText("Conducteur de travaux").assertIsDisplayed()
        compose.onNodeWithText("Forêts récentes").assertDoesNotExist()
        compose.onNodeWithText("Missions").assertDoesNotExist()
        screenshot("accueil-conducteur.png")
        compose.onNodeWithTag("journey_depots").performClick()
        compose.runOnIdle { assertEquals("depots", opened) }
        compose.onNodeWithTag("operations_home").performScrollToNode(hasTestTag("journey_packs"))
        compose.onNodeWithTag("journey_packs").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("packs", opened) }
    }

    @Test fun explorer_replaces_management_tools_when_profession_changes() {
        val profession = mutableStateOf("technicien_forestier")
        var opened = ""
        content {
            ExplorerContent(JourneyNavigation.access(profession.value),
                { opened = "forests" }, { opened = "projects" }, { opened = it.name },
                { opened = "depots" }, { opened = "profession" })
        }
        compose.onNodeWithTag("explorer_FORETS").assertIsDisplayed()
        compose.onNodeWithTag("journey_depots").assertDoesNotExist()
        screenshot("explorer-technicien.png")
        compose.runOnIdle { profession.value = "conducteur_travaux_forestiers" }
        compose.onNodeWithTag("explorer_FORETS").assertDoesNotExist()
        compose.onNodeWithTag("explorer_DIAGNOSTICS").assertDoesNotExist()
        compose.onNodeWithTag("journey_depots").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("depots", opened) }
        screenshot("explorer-conducteur.png")
        compose.runOnIdle { profession.value = "technicien_forestier" }
        compose.onNodeWithTag("explorer_FORETS").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("forests", opened) }
        compose.onNodeWithTag("journey_depots").assertDoesNotExist()
    }

    @Test fun compact_navigation_does_not_reintroduce_hidden_missions() {
        var route = ""
        content {
            Box(Modifier.fillMaxSize()) {
                CollapsedMiniNav(JourneyNavigation.tabs(JourneyNavigation.access("forestier_onf")),
                    currentRoute = "wood/depots", onNavigateToTab = { route = it },
                    modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
        compose.onNodeWithContentDescription("Ouvrir la navigation").performClick()
        compose.onNodeWithContentDescription("Missions").assertDoesNotExist()
        compose.onNodeWithContentDescription("Carte").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(BottomNavDestination.CARTE.route, route) }
    }

    @Test fun large_text_keeps_wood_entry_actions_reachable() {
        var opened = ""
        content(fontScale = 2f) {
            OperationsHomeScreen(ProfessionProfile.LOGGING_CONTRACTOR,
                { opened = "depots" }, { opened = "map" }, { opened = "packs" }, {})
        }
        compose.onNodeWithTag("operations_home").performScrollToNode(hasTestTag("journey_depots"))
        compose.onNodeWithTag("journey_depots").assertIsDisplayed()
        screenshot("accueil-bois-grande-police.png")
        compose.onNodeWithTag("journey_depots").performClick()
        compose.runOnIdle { assertEquals("depots", opened) }
        compose.onNodeWithTag("operations_home").performScrollToNode(hasTestTag("journey_packs"))
        compose.onNodeWithTag("journey_packs").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("packs", opened) }
    }

    @Test fun unknown_profession_offers_choice_without_inventing_a_business_profile() {
        var chosen = false
        content { JourneyChoiceScreen({ chosen = true }) }
        compose.onNodeWithTag("journey_depots").assertDoesNotExist()
        compose.onNodeWithText("Choisir mon métier").performClick()
        compose.runOnIdle { assertEquals(true, chosen) }
    }

    private fun content(fontScale: Float = 1f, body: @Composable () -> Unit) {
        compose.setContent {
            val context = LocalContext.current
            val french = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocale(Locale.FRANCE)
            })
            CompositionLocalProvider(LocalContext provides french,
                LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                ForestryCounterTheme { body() }
            }
        }
    }

    private fun screenshot(name: String) {
        val folder = File(compose.activity.getExternalFilesDir(null), "TEST-journeys-2026-10-18")
        check(folder.exists() || folder.mkdirs())
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(folder, name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
