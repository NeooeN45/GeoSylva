package com.forestry.counter.ui

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quintessences.geosylva.cartography.ui.MapPreviewKey
import com.quintessences.geosylva.cartography.ui.source.*
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
        compose.onNode(hasContentDescription("Opacité") and SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertIsNotEnabled()
        compose.onNodeWithText("Légende").performScrollTo().assertIsDisplayed()
    }
    @Test fun missing_edition_and_licence_remain_unknown_and_only_supplied_official_link_is_clickable() {
        val links = mutableListOf<String>()
        val url = "https://docs.mapbox.com/data/tilesets/reference/mapbox-satellite/"
        show { MapLayerSourcePanel(MapSourcePanelState("TEST", distribution = "Mapbox", officialUrl = url), {}, {}, {}, links::add) }
        assertTrue(compose.onAllNodesWithText("Non renseignée").fetchSemanticsNodes().size >= 2)
        compose.onNodeWithText("Licence Ouverte 2.0").assertDoesNotExist()
        compose.onNodeWithText("Consulter la fiche officielle").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(links == listOf(url)) }
    }
    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            val current = LocalContext.current
            val config = Configuration(current.resources.configuration).apply { setLocale(Locale.FRENCH) }
            CompositionLocalProvider(LocalContext provides current.createConfigurationContext(config), LocalConfiguration provides config) { MaterialTheme(content = content) }
        }
    }
}
