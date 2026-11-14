package com.forestry.counter.ui

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quintessences.geosylva.cartography.ui.MapPreviewKey
import com.quintessences.geosylva.cartography.ui.source.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class MapSourcePanelUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun local_layer_controls_are_disabled_without_host_authority_callbacks() {
        val layer = MapSourceLayerUi("TEST", "TEST mesures", MapPreviewKey("LOCAL_MEASUREMENTS"), true, 0.6f,
            "TEST mesure manuelle", "#2878C7")
        show { MapLayerSourcePanel(MapSourcePanelState(layer.title, layer = layer, showSource = false), {}, {}, {}, {}) }
        compose.onNode(hasContentDescription("Afficher la couche") and isToggleable()).assertIsNotEnabled()
        val slider = opacitySlider().performScrollTo().assertIsNotEnabled()
        assertRealSliderTarget(slider)
        val unchanged = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        slider.performTouchInput { click(Offset(2.dp.toPx(), 2.dp.toPx())) }
        slider.performTouchInput { click(Offset(width - 2.dp.toPx(), height - 2.dp.toPx())) }
        assertEquals("No authority callback means pointer gestures must not change opacity", unchanged,
            slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo])
        compose.onNodeWithText("Légende").performScrollTo().assertIsDisplayed()
    }
    @Test fun missing_edition_and_licence_remain_unknown_and_only_supplied_official_link_is_clickable() {
        val links = mutableListOf<String>()
        val url = "https://docs.mapbox.com/data/tilesets/reference/mapbox-satellite/"
        show { MapLayerSourcePanel(MapSourcePanelState("TEST", distribution = "Mapbox", officialUrl = url), {}, {}, {}, { links += it }) }
        assertTrue(compose.onAllNodesWithText("Non renseignée").fetchSemanticsNodes().size >= 2)
        compose.onNodeWithText("Licence Ouverte 2.0").assertDoesNotExist()
        compose.onNodeWithText("Consulter la fiche officielle").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(links == listOf(url)) }
    }
    @Test fun opacity_keeps_the_real_48dp_gesture_strip_and_host_values_in_zero_to_one_range() {
        val received = mutableListOf<Float>()
        var hostOpacity by mutableFloatStateOf(0.6f)
        val layer = MapSourceLayerUi("TEST", "TEST mesures", MapPreviewKey("LOCAL_MEASUREMENTS"), true, 0.6f,
            "TEST mesure manuelle", "#2878C7")
        show { MapLayerSourcePanel(MapSourcePanelState(layer.title, layer = layer.copy(opacity = hostOpacity), showSource = false),
            {}, {}, {}, {}, onOpacityChanged = { received += it; hostOpacity = it }) }
        val slider = opacitySlider().performScrollTo().assertIsEnabled()
        assertRealSliderTarget(slider)
        val range = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(0f, range.range.start, 0.00001f)
        assertEquals(1f, range.range.endInclusive, 0.00001f)
        assertEquals(0.6f, range.current, 0.00001f)
        // Real pointer taps near both horizontal AND vertical edges, outside the20dp visual circle.
        slider.performTouchInput { click(Offset(2.dp.toPx(), 2.dp.toPx())) }
        compose.runOnIdle {
            assertTrue("Top-left of the48dp gesture strip must reach the host", received.isNotEmpty())
            assertTrue("The left edge must reach the low opacity end", received.last() in 0f..0.1f)
            received.clear()
        }
        slider.performTouchInput { click(Offset(width - 2.dp.toPx(), height - 2.dp.toPx())) }
        val displayed = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        compose.runOnIdle {
            assertTrue("Bottom-right of the48dp gesture strip must reach the host", received.isNotEmpty())
            assertTrue("The right edge must reach the high opacity end", received.last() in 0.9f..1f)
            assertTrue("Every callback remains a finite opacity, never a relief value", received.all { it.isFinite() && it in 0f..1f })
            assertEquals(hostOpacity, displayed.current, 0.00001f)
        }
    }

    private fun opacitySlider() = compose.onNode(hasContentDescription("Opacité") and
        SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))

    private fun assertRealSliderTarget(slider: SemanticsNodeInteraction) {
        val target = slider.fetchSemanticsNode()
        val density = compose.activity.resources.displayMetrics.density
        assertTrue("The actual progress/gesture node must be at least48dp high", target.size.height + 1f >= 48f * density)
        assertTrue("The actual progress/gesture node must be at least48dp wide", target.size.width + 1f >= 48f * density)
        assertTrue("The gesture strip must not be clipped by its scrolling parent", target.boundsInRoot.height >= target.size.height - 1f)
    }

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            val current = LocalContext.current
            val config = Configuration(current.resources.configuration).apply { setLocale(Locale.FRENCH) }
            CompositionLocalProvider(LocalContext provides current.createConfigurationContext(config), LocalConfiguration provides config) { MaterialTheme(content = content) }
        }
    }
}
