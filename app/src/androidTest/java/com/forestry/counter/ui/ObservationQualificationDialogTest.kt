package com.forestry.counter.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect as AndroidRect
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.R
import com.forestry.counter.data.preferences.ThemeMode
import com.forestry.counter.domain.model.DiameterQualificationDraft
import com.forestry.counter.domain.model.HeightNature
import com.forestry.counter.domain.model.HeightQualificationDraft
import com.forestry.counter.domain.model.MeasurementOrigin
import com.forestry.counter.domain.model.ObservationMeasurementBasis
import com.forestry.counter.domain.model.ObservationQualificationDraft
import com.forestry.counter.domain.model.ObservationSupport
import com.forestry.counter.domain.model.PositionOrigin
import com.forestry.counter.domain.model.PositionQualificationDraft
import com.forestry.counter.presentation.components.ObservationQualificationDialog
import com.forestry.counter.presentation.components.ObservationQualificationDialogError
import com.forestry.counter.presentation.theme.ForestryCounterTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import java.util.UUID

/** Déclarations TEST uniquement ; aucun dossier, compte ou stockage métier de l'application n'est lu. */
@RunWith(AndroidJUnit4::class)
class ObservationQualificationDialogTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var originalConfiguration: Configuration? = null
    private var originalAutomationFlags: Int? = null

    @Before
    @Suppress("DEPRECATION")
    fun use_french_resources_only_in_the_test_activity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val serviceInfo = automation.serviceInfo
        originalAutomationFlags = serviceInfo.flags
        serviceInfo.flags = serviceInfo.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = serviceInfo
        instrumentation.runOnMainSync {
            val resources = compose.activity.resources
            originalConfiguration = Configuration(resources.configuration)
            resources.updateConfiguration(Configuration(resources.configuration).apply { setLocale(Locale.FRANCE) },
                resources.displayMetrics)
        }
    }

    @After
    @Suppress("DEPRECATION")
    fun restore_the_test_activity_configuration() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            originalConfiguration?.let { previous ->
                instrumentation.runOnMainSync {
                    val resources = compose.activity.resources
                    resources.updateConfiguration(previous, resources.displayMetrics)
                }
            }
        } finally {
            originalAutomationFlags?.let { flags ->
                val automation = instrumentation.uiAutomation
                val info = automation.serviceInfo
                info.flags = flags
                automation.serviceInfo = info
            }
            originalAutomationFlags = null
            originalConfiguration = null
        }
    }

    @Test
    fun should_preserve_unknown_support_without_assuming_an_individual_tree() {
        val saved = mutableListOf<ObservationQualificationDraft>()
        content {
            dialog(initial = ObservationQualificationDraft(), onSave = { saved += it })
        }
        compose.onNodeWithTag("qualification_raw_id").assertIsDisplayed()
            .assert(SemanticsMatcher("Identifiant brut en lecture seule") {
                !it.config.contains(SemanticsProperties.EditableText)
            })
        captureScreenshot("qualification-mesures-fr-normal.png")
        node("support_INDIVIDUAL_TREE").performScrollTo().assertIsNotSelected().assertHasClickAction().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        node("support_GROUP_REFERENCE").performScrollTo().assertIsNotSelected()
        node("save").assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(ObservationQualificationDraft()), saved) }
    }

    @Test
    fun should_save_a_partial_declaration_and_preserve_position_dates_and_literal_references() {
        val initial = ObservationQualificationDraft(
            diameter = DiameterQualificationDraft(conventionReference = " TEST : protocole inconnu  ", observedAt = 1234L),
            height = HeightQualificationDraft(observedAt = 2345L),
            position = PositionQualificationDraft(origin = PositionOrigin.REUSED_POINT,
                methodReference = "TEST : point de classe", sourceReference = "TEST : carnet", capturedAt = 3456L),
        )
        val saved = mutableListOf<ObservationQualificationDraft>()
        content { dialog(initial, onSave = { saved += it }) }
        section("diameter").performClick()
        node("diameter_convention_OTHER").performScrollTo().assertIsSelected()
        node("diameter_other_convention").performScrollTo().assert(hasExactEditableText(" TEST : protocole inconnu  "))
        node("diameter_source").performScrollTo().performTextReplacement(" TEST : source littérale  ")
        hideKeyboard()
        node("save").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf(initial.copy(diameter = requireNotNull(initial.diameter)
                .copy(sourceReference = " TEST : source littérale  "))), saved)
        }
    }

    @Test
    fun should_clear_incompatible_individual_pairing_without_discarding_other_declarations() {
        val initial = pairedDraft()
        val saved = mutableListOf<ObservationQualificationDraft>()
        content { dialog(initial, onSave = { saved += it }) }
        section("height").performClick()
        node("height_pairing_true").performScrollTo().assertIsSelected().assertIsEnabled()
        node("height_nature_MERCHANTABLE").performScrollTo().performClick()
        node("height_pairing_true").performScrollTo().assertIsNotSelected().assertIsNotEnabled()
        node("height_pairing_unknown").performScrollTo().assertIsSelected()
        node("save").performClick()
        compose.runOnIdle {
            assertEquals(listOf(initial.copy(height = requireNotNull(initial.height)
                .copy(nature = HeightNature.MERCHANTABLE, pairedWithDiameter = null))), saved)
        }
    }

    @Test
    fun should_keep_draft_on_conflict_until_explicit_reload_replaces_it() {
        var stateKey by mutableStateOf("TEST-ACCOUNT-G1-OBS-R1")
        var initial by mutableStateOf(ObservationQualificationDraft(diameter = DiameterQualificationDraft(sourceReference = "TEST : initial")))
        var currentBasis by mutableStateOf(basis())
        var error by mutableStateOf<ObservationQualificationDialogError?>(null)
        var reloads = 0
        val saved = mutableListOf<ObservationQualificationDraft>()
        content {
            ObservationQualificationDialog(stateKey = stateKey, basis = currentBasis, initialDraft = initial,
                readOnly = false, busy = false, error = error, onDismiss = {},
                onSave = { saved += it; error = ObservationQualificationDialogError.CONFLICT },
                onReload = {
                    reloads++
                    initial = ObservationQualificationDraft(diameter = DiameterQualificationDraft(sourceReference = "TEST : version enregistrée"))
                    currentBasis = basis().copy(hauteurM = 21.5, observationVersion = 2)
                    stateKey = "TEST-ACCOUNT-G1-OBS-R2"
                    error = null
                })
        }
        section("diameter").performClick()
        node("diameter_source").performScrollTo().performTextReplacement("TEST : brouillon local")
        hideKeyboard()
        node("save").performClick().assertIsNotEnabled()
        node("diameter_source").performScrollTo().assert(hasExactEditableText("TEST : brouillon local"))
        compose.runOnIdle {
            assertEquals(0, reloads)
            assertEquals(listOf(ObservationQualificationDraft(diameter = DiameterQualificationDraft(sourceReference = "TEST : brouillon local"))), saved)
        }
        node("reload").assertIsDisplayed().performClick()
        section("diameter").performClick()
        node("diameter_source").performScrollTo().assert(hasExactEditableText("TEST : version enregistrée"))
        compose.runOnIdle { assertEquals(1, reloads) }
        node("raw_height").performScrollTo().assertIsDisplayed()
        node("save").assertIsEnabled()
    }

    @Test
    fun should_restore_draft_only_for_the_same_account_generation_and_observation() {
        val restoration = StateRestorationTester(compose)
        // Variable hors Compose : le changement prend effet à la restauration, pas avant la sauvegarde.
        var activeScope = "TEST-ACCOUNT-A-G1-OBS-RELOAD-1-DECLARATION-R1"
        val position = PositionQualificationDraft(origin = PositionOrigin.REUSED_POINT, capturedAt = 1000L)
        val saved = mutableListOf<ObservationQualificationDraft>()
        restoration.setContent {
            ForestryCounterTheme(themeMode = ThemeMode.LIGHT) {
                ObservationQualificationDialog(stateKey = activeScope, basis = basis(),
                    initialDraft = ObservationQualificationDraft(diameter = DiameterQualificationDraft(
                        sourceReference = when {
                            activeScope.endsWith("-R2") -> "TEST : déclaration B révision 2"
                            activeScope.contains("ACCOUNT-A") -> "TEST : origine A"
                            else -> "TEST : origine B"
                        }), position = position),
                    readOnly = false, busy = false, error = null, onDismiss = {}, onSave = { saved += it }, onReload = {})
            }
        }
        section("diameter").performClick()
        node("diameter_source").performScrollTo().performTextReplacement("TEST : brouillon A")
        hideKeyboard()
        restoration.emulateSavedInstanceStateRestore()
        ensureDiameterExpanded()
        node("diameter_source").performScrollTo().assert(hasExactEditableText("TEST : brouillon A"))
        node("save").performClick()
        compose.runOnIdle { assertEquals(position, saved.single().position) }

        activeScope = "TEST-ACCOUNT-B-G2-OBS-RELOAD-1-DECLARATION-R1"
        restoration.emulateSavedInstanceStateRestore()
        ensureDiameterExpanded()
        node("diameter_source").performScrollTo().assert(hasExactEditableText("TEST : origine B"))

        node("diameter_source").performTextReplacement("TEST : brouillon B révision 1")
        hideKeyboard()
        // Même compte, génération et observation : une nouvelle révision enregistrée change aussi la portée.
        activeScope = "TEST-ACCOUNT-B-G2-OBS-RELOAD-1-DECLARATION-R2"
        restoration.emulateSavedInstanceStateRestore()
        ensureDiameterExpanded()
        node("diameter_source").performScrollTo().assert(hasExactEditableText("TEST : déclaration B révision 2"))
    }

    @Test
    fun should_allow_consultation_only_for_a_closed_version() {
        val saved = mutableListOf<ObservationQualificationDraft>()
        var dismissals = 0
        content {
            ObservationQualificationDialog(stateKey = "TEST-SEALED", basis = basis(), initialDraft = pairedDraft(),
                readOnly = true, busy = false, error = null, onDismiss = { dismissals++ },
                onSave = { saved += it }, onReload = {})
        }
        node("save").assertDoesNotExist()
        node("support_INDIVIDUAL_TREE").performScrollTo().assertIsSelected().assertIsNotEnabled()
        section("diameter").performClick()
        node("diameter_source").performScrollTo().assertIsNotEnabled()
        node("close").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, dismissals); assertTrue(saved.isEmpty()) }
    }

    @Test
    fun should_block_actions_and_back_dismissal_while_saving() {
        var busy by mutableStateOf(true)
        var dismissals = 0
        val saved = mutableListOf<ObservationQualificationDraft>()
        content {
            ObservationQualificationDialog(stateKey = "TEST-BUSY", basis = basis(), initialDraft = ObservationQualificationDraft(),
                readOnly = false, busy = busy, error = null, onDismiss = { dismissals++ },
                onSave = { saved += it }, onReload = {})
        }
        node("save").assertIsNotEnabled()
        node("close").assertIsNotEnabled()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        node("dialog_panel").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, dismissals); assertTrue(saved.isEmpty()); busy = false }
        node("close").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, dismissals) }
    }

    @Test
    fun should_show_stale_declaration_and_require_explicit_save_to_reconfirm_it() {
        val saved = mutableListOf<ObservationQualificationDraft>()
        val initial = pairedDraft()
        content {
            ObservationQualificationDialog(stateKey = "TEST-STALE", basis = basis(), initialDraft = initial,
                readOnly = false, busy = false, error = null, stale = true,
                onDismiss = {}, onSave = { saved += it }, onReload = {})
        }
        node("stale").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertTrue("La consultation seule ne reconfirme rien", saved.isEmpty()) }
        node("save").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(initial), saved) }
    }

    @Test
    fun should_keep_height_source_and_fixed_actions_accessible_with_large_local_typography() {
        val initial = pairedDraft()
        val saved = mutableListOf<ObservationQualificationDraft>()
        content(fontScale = 2f) { dialog(initial, onSave = { saved += it }) }
        section("height").performClick()
        node("height_source").performScrollTo()
        captureScreenshot("qualification-mesures-fr-grande-police.png")
        assertCompleteViewport(node("height_source"))
        assertCompleteViewport(node("save"))
        assertCompleteViewport(node("close"))
        assertTextGeometry()
        node("save").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(initial), saved) }
    }

    @Test
    fun should_keep_save_and_close_above_keyboard_and_reject_oversized_reference() {
        content { dialog(ObservationQualificationDraft(), onSave = {}) }
        section("diameter").performClick()
        node("diameter_source").performScrollTo().performClick().performTextReplacement("TEST : carnet")
        assertControlsVisibleWithKeyboard()
        node("diameter_source").performTextReplacement("x".repeat(301))
        node("save").assertIsNotEnabled()
        node("diameter_source").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        hideKeyboard()
        node("diameter_source").performScrollTo().assert(hasExactEditableText("x".repeat(301)))
    }

    @Composable
    private fun dialog(initial: ObservationQualificationDraft, onSave: (ObservationQualificationDraft) -> Unit) {
        ObservationQualificationDialog(stateKey = "TEST-ACCOUNT-G1-OBS-R1", basis = basis(), initialDraft = initial,
            readOnly = false, busy = false, error = null, onDismiss = {}, onSave = onSave, onReload = {})
    }

    private fun basis() = ObservationMeasurementBasis(tigeId = "TEST-OBSERVATION-01", parcelleId = "TEST-PARCELLE",
        placetteId = "TEST-PLACETTE", sessionId = "TEST-SESSION", originTigeId = null,
        essenceCode = "TEST-ESSENCE", diamCm = 40.0, hauteurM = 20.0, gpsWkt = null,
        precisionM = null, altitudeM = null, timestamp = 1000L, observationVersion = 1)

    private fun pairedDraft() = ObservationQualificationDraft(support = ObservationSupport.INDIVIDUAL_TREE,
        diameter = DiameterQualificationDraft(origin = MeasurementOrigin.MEASURED,
            sourceReference = "TEST : compas forestier"),
        height = HeightQualificationDraft(nature = HeightNature.TOTAL, origin = MeasurementOrigin.MEASURED,
            support = ObservationSupport.INDIVIDUAL_TREE, pairedWithDiameter = true,
            sourceReference = "TEST : mesure de hauteur"))

    private fun content(fontScale: Float = 1f, block: @Composable () -> Unit) {
        compose.setContent {
            ForestryCounterTheme(themeMode = ThemeMode.LIGHT) {
                val original = MaterialTheme.typography
                val scaled = remember(original, fontScale) { original.scaled(fontScale) }
                // Typographie locale uniquement ; ne pas modifier les réglages de police du téléphone.
                MaterialTheme(typography = scaled, content = block)
            }
        }
    }

    private fun node(suffix: String) = compose.onNodeWithTag("qualification_$suffix")
    private fun section(suffix: String) = node("section_$suffix").performScrollTo()
    private fun ensureDiameterExpanded() {
        if (compose.onAllNodesWithTag("qualification_diameter_source").fetchSemanticsNodes().isEmpty()) {
            section("diameter").performClick()
        }
    }
    private fun text(resource: Int) = compose.activity.getString(resource)
    private fun hasExactEditableText(expected: String) = SemanticsMatcher("Contenu éditable exact : $expected") {
        it.config.getOrNull(SemanticsProperties.EditableText)?.text == expected
    }

    private fun Typography.scaled(scale: Float): Typography = Typography(
        displayLarge = displayLarge.scaled(scale), displayMedium = displayMedium.scaled(scale), displaySmall = displaySmall.scaled(scale),
        headlineLarge = headlineLarge.scaled(scale), headlineMedium = headlineMedium.scaled(scale), headlineSmall = headlineSmall.scaled(scale),
        titleLarge = titleLarge.scaled(scale), titleMedium = titleMedium.scaled(scale), titleSmall = titleSmall.scaled(scale),
        bodyLarge = bodyLarge.scaled(scale), bodyMedium = bodyMedium.scaled(scale), bodySmall = bodySmall.scaled(scale),
        labelLarge = labelLarge.scaled(scale), labelMedium = labelMedium.scaled(scale), labelSmall = labelSmall.scaled(scale))

    private fun TextStyle.scaled(scale: Float): TextStyle = copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)

    private fun assertCompleteViewport(interaction: SemanticsNodeInteraction) {
        interaction.assertIsDisplayed()
        val node = interaction.fetchSemanticsNode()
        val full = Rect(node.positionInRoot.x, node.positionInRoot.y,
            node.positionInRoot.x + node.size.width, node.positionInRoot.y + node.size.height)
        var root = node
        while (root.parent != null) root = requireNotNull(root.parent)
        fun contains(container: Rect): Boolean = full.left >= container.left - 1f && full.top >= container.top - 1f &&
            full.right <= container.right + 1f && full.bottom <= container.bottom + 1f
        assertTrue("Commande ou champ coupé : complet=$full ; visible=${node.boundsInRoot} ; fenêtre=${root.boundsInRoot}",
            contains(node.boundsInRoot) && contains(root.boundsInRoot))
    }

    /** Vérifie les fenêtres physiques Android, pas seulement les coordonnées Compose avant déplacement IME. */
    @Suppress("DEPRECATION")
    private fun assertControlsVisibleWithKeyboard() {
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) { visibleKeyboardBounds() != null }
        val keyboard = requireNotNull(visibleKeyboardBounds())
        val screenSize = Point()
        compose.activity.windowManager.defaultDisplay.getRealSize(screenSize)
        val screen = AndroidRect(0, 0, screenSize.x, screenSize.y)
        assertCompleteViewport(node("save"))
        assertCompleteViewport(node("close"))
        listOf(text(R.string.observation_qualification_save), text(R.string.observation_qualification_close)).forEach { label ->
            val matches = mutableListOf<AndroidRect>()
            InstrumentationRegistry.getInstrumentation().uiAutomation.windows
                .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.forEach { window ->
                    val root = window.root
                    if (root != null) {
                        try {
                            if (root.packageName?.toString() == compose.activity.packageName) matches += physicalClickableBounds(root, label)
                        } finally { root.recycle() }
                    }
                }
            assertEquals("Une commande physique unique doit correspondre à $label", 1, matches.size)
            val bounds = matches.single()
            assertTrue("Commande $label hors écran physique : $bounds ; écran=$screen", screen.contains(bounds))
            assertTrue("Commande $label recouverte par le clavier : $bounds ; clavier=$keyboard",
                bounds.bottom <= keyboard.top && !AndroidRect.intersects(bounds, keyboard))
            println("GEOSYLVA_TEST_QUALIFICATION_PHYSICAL_COMMAND=$label ; écran=$screen ; clavier=$keyboard ; commande=$bounds")
        }
    }

    private fun visibleKeyboardBounds(): AndroidRect? = InstrumentationRegistry.getInstrumentation().uiAutomation.windows
        .firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        ?.let { window -> AndroidRect().apply { window.getBoundsInScreen(this) }.takeUnless { it.isEmpty } }

    @Suppress("DEPRECATION")
    private fun physicalClickableBounds(root: AccessibilityNodeInfo, label: String,
        nearestClickableBounds: AndroidRect? = null): List<AndroidRect> {
        if (root.packageName?.toString() != compose.activity.packageName) return emptyList()
        val found = mutableListOf<AndroidRect>()
        val commandBounds = if (root.isClickable) {
            if (root.isVisibleToUser && root.isEnabled) AndroidRect().apply { root.getBoundsInScreen(this) }.takeUnless { it.isEmpty }
            else null
        } else nearestClickableBounds
        if (root.isVisibleToUser && root.isEnabled && root.text?.toString() == label && commandBounds != null) {
            found += AndroidRect(commandBounds)
        }
        for (index in 0 until root.childCount) {
            val child = root.getChild(index) ?: continue
            try { found += physicalClickableBounds(child, label, commandBounds) } finally { child.recycle() }
        }
        return found
    }

    private fun hideKeyboard() {
        compose.waitForIdle()
        if (visibleKeyboardBounds() != null) {
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.waitUntil(timeoutMillis = 5_000) { visibleKeyboardBounds() == null }
        }
        node("dialog_panel").assertIsDisplayed()
    }

    private fun assertTextGeometry() {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        val count = nodes.fetchSemanticsNodes().size
        assertTrue("Les composants réels exposent leurs textes", count > 0)
        val issues = mutableListOf<String>()
        for (index in 0 until count) {
            val layouts = mutableListOf<TextLayoutResult>()
            nodes[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> assertTrue(action(layouts)) }
            layouts.forEach { layout ->
                val text = layout.layoutInput.text.text
                if (text.isNotEmpty()) {
                    if (layout.lineCount == 0 || layout.getLineStart(0) != 0 ||
                        layout.getLineEnd(layout.lineCount - 1, visibleEnd = true) < text.trimEnd().length) issues += "Texte incomplet : $text"
                    if (layout.didOverflowHeight) issues += "Hauteur insuffisante : $text"
                    for (line in 0 until layout.lineCount) {
                        if (layout.isLineEllipsized(line) || layout.getLineLeft(line) < -1f || layout.getLineRight(line) > layout.size.width + 1f ||
                            layout.getLineTop(line) < -1f || layout.getLineBottom(line) > layout.size.height + 1f) issues += "Ligne coupée : $text"
                    }
                    text.indices.filter { !text[it].isWhitespace() }.forEach { offset ->
                        val glyph = layout.getBoundingBox(offset)
                        if (listOf(glyph.left, glyph.right, glyph.top, glyph.bottom).any { !it.isFinite() } || glyph.left < -1f ||
                            glyph.right > layout.size.width + 1f || glyph.top < -1f || glyph.bottom > layout.size.height + 1f) {
                            issues += "Caractère hors boîte : $text ; $offset ; $glyph"
                        }
                    }
                }
            }
        }
        assertTrue("Débordements typographiques :\n${issues.joinToString("\n")}", issues.isEmpty())
    }

    private fun captureScreenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull("Capture native disponible", bitmap)
        val screenshot = requireNotNull(bitmap)
        try {
            val external = requireNotNull(instrumentation.targetContext.getExternalFilesDir(null))
            val directory = File(external, "GeoSylva-TEST-observation-qualification-2026-10-10/${UUID.randomUUID()}")
            assertTrue("Répertoire TEST neuf", directory.mkdirs())
            val destination = File(directory, name)
            assertTrue("Aucune capture antérieure écrasée", destination.createNewFile())
            destination.outputStream().use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            val semantics = File(directory, "${name.removeSuffix(".png")}.semantics.txt")
            assertTrue(semantics.createNewFile())
            semantics.writeText(compose.onAllNodes(isRoot(), useUnmergedTree = true).printToString(maxDepth = 15), Charsets.UTF_8)
            assertTrue(destination.length() > 0L)
            println("GEOSYLVA_TEST_QUALIFICATION_SCREENSHOT=${destination.absolutePath}")
            println("GEOSYLVA_TEST_QUALIFICATION_SEMANTICS=${semantics.absolutePath}")
        } finally { screenshot.recycle() }
    }
}
