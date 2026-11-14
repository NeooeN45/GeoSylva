package com.forestry.counter.ui

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forestry.counter.R
import com.forestry.counter.data.preferences.ThemeMode
import com.forestry.counter.presentation.screens.forestry.ManualMeasureAuxiliary
import com.forestry.counter.presentation.screens.forestry.ManualMeasureAuxiliaryPanels
import com.forestry.counter.presentation.screens.forestry.ManualMeasureAuxiliaryState
import com.forestry.counter.presentation.theme.ForestryCounterTheme
import com.quintessences.geosylva.cartography.ui.forestry.ForestryMapLayout
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Physical layout contracts: callbacks must include margins and the real system insets. */
@RunWith(AndroidJUnit4::class)
class ForestryAndMeasureInsetsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var restoreWindow: (() -> Unit)? = null

    @After fun restore_window() { restoreWindow?.invoke() }

    @Test fun geographic_controls_start_below_the_last_header_control_and_its_bottom_margin() {
        prepareWindow()
        val controlsTop = AtomicReference(0.dp)
        compose.setContent {
            ForestryCounterTheme(themeMode = ThemeMode.LIGHT, dynamicColor = false) {
                ForestryMapLayout(title = "TEST · Création", subtitle = "TEST · Contexte", backLabel = "TEST · Retour", onBack = {},
                    headerControls = { Box(Modifier.fillMaxWidth().height(40.dp).testTag(HEADER_CONTROLS)) },
                    map = { modifier, top -> controlsTop.set(top); Box(modifier.testTag(MAP)) },
                    panel = { Text("TEST · Panneau") })
            }
        }
        compose.waitForIdle()
        val lastHeader = compose.onNodeWithTag(HEADER_CONTROLS).fetchSemanticsNode().boundsInRoot
        val map = compose.onNodeWithTag(MAP).fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        val reservedTop = map.top + controlsTop.get().value * density
        // The header has 12dp below its controls and the map requests another 8dp.
        assertTrue("Native commands overlap the header or lose its outer padding", reservedTop >= lastHeader.bottom + 20f * density - 1f)
        assertTrue("The native controls must still fit within the exposed map", reservedTop < map.bottom)
    }

    @Test fun auxiliary_occlusion_includes_the_navigation_inset_and_outer_panel_margin() {
        prepareWindow()
        val reported = AtomicReference<Dp?>(null)
        val title = compose.activity.getString(R.string.manual_aux_saved_title)
        compose.setContent {
            ForestryCounterTheme(themeMode = ThemeMode.LIGHT, dynamicColor = false) {
                Box(Modifier.fillMaxSize().testTag(WORKSPACE)) {
                    ManualMeasureAuxiliaryPanels(ManualMeasureAuxiliaryState(panel = ManualMeasureAuxiliary.SAVED),
                        onClose = {}, onClear = {}, onColor = {}, onSelectSaved = {}, onRequestCopy = {},
                        onRequestShare = {}, onRequestDetails = {}, onCopyShareText = {}, onShareText = {},
                        onRetrySaved = {}, onNewMeasure = {}, onPanelHeightChanged = { reported.set(it) })
                }
            }
        }
        compose.waitForIdle()
        val workspace = compose.onNodeWithTag(WORKSPACE).fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        var card = compose.onNodeWithText(title).fetchSemanticsNode().parent
        // Surface owns a semantic container. Locate the card containing the title,
        // rather than assuming a row/column position in its semantics ancestry.
        while (card != null && (card.boundsInRoot.width < workspace.width - 30f * density || card.boundsInRoot.height <= 60f * density)) card = card.parent
        val bounds = checkNotNull(card) { "The auxiliary card has no semantic surface" }.boundsInRoot
        assertTrue("Expected the card, not the full workspace", bounds.height < workspace.height)
        val occupiedBottom = workspace.bottom - bounds.top
        val actual = checkNotNull(reported.get()) { "Auxiliary height was never reported" }.value * density
        assertTrue("Native ornaments would be positioned behind the auxiliary card", actual >= occupiedBottom - 1f)
        assertTrue("Only the card's 12dp top margin may additionally be reserved", actual <= occupiedBottom + 12f * density + 1f)
    }

    private fun prepareWindow() {
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
        restoreWindow = useFrenchMapTestWindow(compose.activity)
    }
    companion object {
        private const val HEADER_CONTROLS = "TEST-header-controls"
        private const val MAP = "TEST-exposed-map"
        private const val WORKSPACE = "TEST-workspace"
    }
}
