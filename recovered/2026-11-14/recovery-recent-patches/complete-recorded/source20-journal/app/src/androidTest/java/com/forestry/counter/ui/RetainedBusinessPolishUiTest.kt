package com.forestry.counter.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forestry.counter.R
import com.forestry.counter.data.preferences.ThemeMode
import com.forestry.counter.domain.wood.WoodObjectKind
import com.forestry.counter.presentation.screens.wood.PileMeasurementForm
import com.forestry.counter.presentation.screens.wood.WoodManualForm
import com.forestry.counter.presentation.theme.ForestryCounterTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real calculator and fields. No repository/authority, write or screenshot substitution. */
@RunWith(AndroidJUnit4::class)
class RetainedBusinessPolishUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var restoreWindow: (() -> Unit)? = null
    @After fun restore() { restoreWindow?.invoke() }

    @Test fun pileReserveAndEveryExistingMetadataPanelRemainReachableAt411dp() = checkPile(411, 1f)
    @Test fun pileReserveAndEveryExistingMetadataPanelRemainReachableAt320dpLargeFont() = checkPile(320, 1.6f)

    private fun checkPile(width: Int, fontScale: Float) {
        val original = WoodManualForm(WoodObjectKind.PILE, name = "TEST · Pile du chemin nord", length = "10",
            depth = "2", height = "1,5", shape = true, protocol = "TEST enveloppe mesurée")
        var changes = 0
        restoreWindow = useFrenchMapTestWindow(compose.activity)
        compose.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(currentDensity.density, fontScale)) {
                ForestryCounterTheme(themeMode = ThemeMode.LIGHT, dynamicColor = false) {
                    Box(Modifier.width(width.dp).height(600.dp).testTag("TEST-business-viewport")) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            PileMeasurementForm(original, true) { changes++; this }
                        }
                    }
                }
            }
        }
        val viewport = compose.onNodeWithTag("TEST-business-viewport")
        val value = compose.onNodeWithTag("wood_calculated_value", useUnmergedTree = true).performScrollTo()
        assertCompleteTextWithinBounds(value, "30 m³", fontScale, viewport = viewport)
        val reserve = compose.onNodeWithTag("wood_calculation_essential_reserve", useUnmergedTree = true).performScrollTo()
        assertCompleteTextWithinBounds(reserve, compose.activity.getString(R.string.retained_calculation_reserve_short), fontScale, viewport = viewport)
        val help = compose.onNodeWithTag("wood_calculation_reserve_help").performScrollTo().assertIsDisplayed()
        val bounds = help.fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        assertTrue(bounds.width + .5f >= 48f * density && bounds.height + .5f >= 48f * density)
        help.performTouchInput { click(center) }
        val fullReserve = compose.activity.getString(R.string.wo_no_payable)
        assertCompleteTextWithinBounds(compose.onNodeWithText(fullReserve, useUnmergedTree = true), fullReserve, fontScale)
        compose.onNodeWithText(compose.activity.getString(R.string.retained_close)).performClick()
        listOf("wood_method_panel", "wood_date_panel", "wood_details_panel").forEach { tag ->
            val action = compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
            val actionBounds = action.fetchSemanticsNode().boundsInRoot
            assertTrue(actionBounds.height + .5f >= 48f * density)
            action.performTouchInput { click(center) }
            compose.onNodeWithTag("wood_panel_close").assertIsDisplayed().performClick()
        }
        compose.runOnIdle {
            assertEquals(0, changes)
            assertEquals("", original.observedDate)
            assertEquals("10", original.length)
            assertEquals("1,5", original.height)
        }
    }

    @Test fun incompleteDimensionsKeepUnknownPreviewAndDoNotInventResult() {
        val original = WoodManualForm(WoodObjectKind.PILE, name = "TEST incomplete", length = "10",
            depth = "2", height = "", shape = true, protocol = "TEST enveloppe")
        var changes = 0
        restoreWindow = useFrenchMapTestWindow(compose.activity)
        compose.setContent { ForestryCounterTheme(themeMode = ThemeMode.LIGHT, dynamicColor = false) {
            Column(Modifier.width(411.dp).height(700.dp).verticalScroll(rememberScrollState())) {
                PileMeasurementForm(original, true) { changes++; this }
            }
        } }
        compose.onNodeWithText(compose.activity.getString(R.string.wo_no_calculation)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("wood_calculated_value").assertDoesNotExist()
        compose.onNodeWithTag("wood_calculation_essential_reserve").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, changes); assertEquals("", original.height) }
    }
}
