package com.forestry.counter.ui

import android.content.res.Configuration
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quintessences.geosylva.cartography.ui.layers.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class LayerWorkspaceUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var restoreWindow: (() -> Unit)? = null
    @After fun restore() { restoreWindow?.invoke() }
    @Test fun invalid_colour_has_no_use_action_and_cancel_is_explicit() {
        var uses = 0; var cancels = 0
        show { CustomLayerColorPanel(0xFF087C96, "#wrong", false, {}, { cancels++ }, { uses++ }) }
        compose.onNodeWithText("Utiliser").assertIsNotEnabled()
        compose.onNodeWithText("Code couleur invalide").assertIsDisplayed()
        compose.onNodeWithText("Annuler").performClick()
        compose.runOnIdle { assertEquals(0, uses); assertEquals(1, cancels) }
    }
    @Test fun preview_attributes_distinguish_empty_null_zero_and_absent_without_edit_action() {
        val selected = LayerObjectUi("f-0", "TEST", "", "Point", listOf(
            LayerAttributeUi("empty", "", LayerValueKind.TEXT), LayerAttributeUi("note", "", LayerValueKind.NULL),
            LayerAttributeUi("count", "0", LayerValueKind.NUMBER), LayerAttributeUi("missing", "", LayerValueKind.ABSENT)))
        show { LayerWorkspace(LayerWorkspaceState(panel = LayerPanel.ATTRIBUTES, preview = true, selected = selected), Actions(), Modifier.fillMaxSize()) }
        compose.onNodeWithText("Texte vide").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Valeur nulle").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("0").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Absent").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Modifier les attributs").assertDoesNotExist()
    }
    @Test fun palette_exposes_accessible_colour_names_and_keeps_current_state_until_host_acknowledges() {
        var changed: LayerAppearanceUi? = null
        val actions = object : Actions() { override fun onAppearance(appearance: LayerAppearanceUi) { changed = appearance } }
        show { LayerWorkspace(LayerWorkspaceState(panel = LayerPanel.APPEARANCE, geometry = LayerGeometryKind.POINT), actions, Modifier.fillMaxSize()) }
        compose.onNodeWithContentDescription("Sapin").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0xFF206C32L, changed?.colorArgb) }
        compose.onNodeWithContentDescription("Pétrole").assertIsSelected()
        compose.onNodeWithContentDescription("Sapin").assertIsNotSelected()
    }
    @Test fun formats_primary_action_stays_wholly_visible_and_reports_total_panel_occlusion() {
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
        restoreWindow=useFrenchMapTestWindow(compose.activity,fontScale=1.5f)
        var reported=0.dp;var chooses=0
        show { LayerWorkspace(LayerWorkspaceState(panel=LayerPanel.FORMATS),object:Actions() {
            override fun onChooseFile() { chooses++ }
        },Modifier.fillMaxSize().testTag("TEST-layer-root"),onPanelHeightChanged={reported=it}) }
        assertWholeButton("Choisir un fichier")
        val root=compose.onNodeWithTag("TEST-layer-root").getUnclippedBoundsInRoot()
        val panel=compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle,"Formats disponibles")).getUnclippedBoundsInRoot()
        compose.runOnIdle { assertEquals((root.bottom-panel.top).value,reported.value,0.6f) }
        compose.onNode(hasText("Choisir un fichier") and hasClickAction()).performClick()
        compose.runOnIdle { assertEquals(1,chooses) }
    }
    @Test fun colour_actions_remain_wholly_visible_while_body_scrolls_and_invalid_input_still_cancels() {
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
        restoreWindow=useFrenchMapTestWindow(compose.activity,fontScale=1.5f)
        var cancels=0;var uses=0
        show { LayerWorkspace(LayerWorkspaceState(panel=LayerPanel.COLOR,colorInput="#wrong",colorValid=false),object:Actions() {
            override fun onCancelColor() { cancels++ }
            override fun onUseColor() { uses++ }
        },Modifier.fillMaxSize()) }
        assertWholeButton("Annuler");assertWholeButton("Utiliser")
        compose.onNodeWithText("Utiliser").assertIsNotEnabled()
        compose.onNodeWithText("Code couleur invalide").performScrollTo().assertIsDisplayed()
        assertWholeButton("Annuler");assertWholeButton("Utiliser")
        compose.onNodeWithText("Annuler").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1,cancels);assertEquals(0,uses) }
    }
    @Test fun managed_layer_swatch_follows_host_colour_without_mutation_and_preserves_navigation_guard() {
        var state by mutableStateOf(LayerWorkspaceState(panel=LayerPanel.MANAGE,layerName="TEST · Repères terrain",
            appearance=LayerAppearanceUi(colorArgb=0xFF3B7D72)))
        var opens=0;var mutations=0
        val actions=object:Actions() {
            override fun onPanel(panel: LayerPanel) { assertEquals(LayerPanel.APPEARANCE,panel);opens++ }
            override fun onAppearance(appearance: LayerAppearanceUi) { mutations++ }
            override fun onSaveAppearance() { mutations++ }
        }
        show { LayerWorkspace(state,actions,Modifier.fillMaxSize()) }
        compose.onNodeWithContentDescription("Couleur #FF3B7D72").assertIsDisplayed()
        val appearance=hasText("Apparence") and hasClickAction()
        compose.onNode(appearance).assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(1,opens);assertEquals(0,mutations)
            state=state.copy(busy=true,appearance=state.appearance.copy(colorArgb=0x00FFFFFF)) }
        compose.onNodeWithContentDescription("Couleur #FF3B7D72").assertDoesNotExist()
        compose.onNodeWithContentDescription("Couleur #00FFFFFF").assertIsDisplayed()
        compose.onNode(appearance).assertIsNotEnabled().assertHeightIsAtLeast(48.dp)
        compose.runOnIdle { assertEquals(1,opens);assertEquals(0,mutations) }
    }
    private fun assertWholeButton(label: String) {
        val node=compose.onNode(hasText(label) and hasClickAction())
        node.assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        val unclipped=node.getUnclippedBoundsInRoot()
        val actual=node.fetchSemanticsNode().boundsInRoot
        val density=compose.activity.resources.displayMetrics.density
        assertEquals("$label is vertically clipped",(unclipped.bottom-unclipped.top).value*density,actual.height,1f)
        assertEquals("$label is horizontally clipped",(unclipped.right-unclipped.left).value*density,actual.width,1f)
    }
    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            val current = LocalContext.current
            val config = Configuration(current.resources.configuration).apply { setLocale(Locale.FRENCH) }
            val context = current.createConfigurationContext(config)
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config) { MaterialTheme(content = content) }
        }
    }
    private open class Actions : LayerWorkspaceActions {
        override fun onPanel(panel: LayerPanel) = Unit
        override fun onBack() = Unit
        override fun onClose() = Unit
        override fun onChooseFile() = Unit
        override fun onDiscardPreview() = Unit
        override fun onCommit() = Unit
        override fun onOpenLayer(id: String) = Unit
        override fun onDeleteLayer() = Unit
        override fun onSelectObject(id: String) = Unit
        override fun onLoadMore() = Unit
        override fun onQuery(value: String) = Unit
        override fun onFilterField(field: String) = Unit
        override fun onFilterCondition(condition: LayerCondition) = Unit
        override fun onFilterValue(key: String) = Unit
        override fun onFilterText(value: String) = Unit
        override fun onResetFilter() = Unit
        override fun onAttribute(field: String, input: String) = Unit
        override fun onNullAttribute(field: String) = Unit
        override fun onSaveAttributes() = Unit
        override fun onAppearance(appearance: LayerAppearanceUi) = Unit
        override fun onSaveAppearance() = Unit
        override fun onColorInput(input: String) = Unit
        override fun onUseColor() = Unit
        override fun onCancelColor() = Unit
    }
}
