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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import com.forestry.counter.domain.model.SurveyContextDraft
import com.forestry.counter.domain.model.SurveyPopulationScope
import com.forestry.counter.domain.model.SurveyPurpose
import com.forestry.counter.presentation.components.SurveyContextDialog
import com.forestry.counter.presentation.components.SurveyStartDialog
import com.forestry.counter.presentation.screens.forestry.SurveyContextUiError
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

/** Dialogues réels et déclarations TEST ; aucun accès aux dossiers ou à une base de l'application. */
@RunWith(AndroidJUnit4::class)
class SurveyPreparationDialogsTest {
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
            resources.updateConfiguration(
                Configuration(resources.configuration).apply { setLocale(Locale.FRANCE) },
                resources.displayMetrics
            )
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
                val serviceInfo = automation.serviceInfo
                serviceInfo.flags = flags
                automation.serviceInfo = serviceInfo
            }
            originalAutomationFlags = null
            originalConfiguration = null
        }
    }

    @Test
    fun should_require_an_explicit_purpose_and_allow_consultation_without_starting() {
        val starts = mutableListOf<SurveyContextDraft>()
        var consultations = 0
        content {
            SurveyStartDialog(busy = false, failed = false,
                onDismiss = { consultations++ }, onStart = { starts += it })
        }
        compose.onNodeWithText(text(R.string.survey_preparation_start_title)).assertIsDisplayed()
        captureScreenshot("demarrage-releve-fr-normal.png")

        SurveyPurpose.entries.forEach { option ->
            purpose(option).assertIsNotSelected().assertHasClickAction().assert(
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
            )
        }
        startConfirm().assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("survey_start_consult").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertTrue("Consulter n'ouvre aucune collecte", starts.isEmpty())
            assertEquals(1, consultations)
        }
    }

    @Test
    fun should_start_each_purpose_without_inventing_population_protocol_or_area() {
        val starts = mutableListOf<SurveyContextDraft>()
        content {
            SurveyStartDialog(busy = false, failed = false, onDismiss = {}, onStart = { starts += it })
        }

        SurveyPurpose.entries.forEach { selected ->
            purpose(selected).performScrollTo().performClick().assertIsSelected()
            SurveyPurpose.entries.filter { it != selected }.forEach { purpose(it).assertIsNotSelected() }
            startConfirm().assertIsEnabled().performClick()
        }
        compose.runOnIdle {
            assertEquals(SurveyPurpose.entries.map { SurveyContextDraft(purpose = it) }, starts)
        }
    }

    @Test
    fun should_preserve_unknown_context_and_block_invalid_declared_area() {
        val saved = mutableListOf<SurveyContextDraft>()
        content {
            SurveyContextDialog(stateKey = "TEST-UNKNOWN", initialDraft = SurveyContextDraft(),
                readOnly = false, busy = false, error = null, onDismiss = {},
                onSave = { saved += it }, onReload = {})
        }
        contextConfirm().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(SurveyContextDraft()), saved) }

        section("realization").performClick()
        val area = field("area_input")
        area.performScrollTo().performTextReplacement("0")
        try {
            contextConfirm().assertIsNotEnabled()
            area.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
            val error = compose.onNodeWithText(text(R.string.survey_preparation_area_invalid))
            error.performScrollTo().assertIsDisplayed()
            assertCompleteViewport(error)
            assertCompleteViewport(area)
        } catch (failure: AssertionError) {
            try { captureWindowDiagnostic("diagnostic-surface-zero") }
            catch (diagnosticFailure: Exception) { failure.addSuppressed(diagnosticFailure) }
            throw failure
        }
        area.performTextReplacement("500,5")
        area.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error).not())
        contextConfirm().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(SurveyContextDraft(inventoriedHorizontalAreaM2 = 500.5), saved.last())
            assertEquals(2, saved.size)
        }
    }

    @Test
    fun should_save_the_declared_population_protocol_and_realized_horizontal_area() {
        val saved = mutableListOf<SurveyContextDraft>()
        var dismisses = 0
        content {
            SurveyContextDialog(stateKey = "TEST-DECLARED", initialDraft = SurveyContextDraft(),
                readOnly = false, busy = false, error = null, onDismiss = { dismisses++ },
                onSave = { saved += it }, onReload = {})
        }
        compose.onNodeWithText(text(R.string.survey_preparation_context_title)).assertIsDisplayed()
        section("objective").performClick()
        purpose(SurveyPurpose.COMBINED).performScrollTo().performClick()
        section("population").performClick()
        field("population_reference").performScrollTo().performTextReplacement("TEST : tiges du dispositif")
        compose.onNodeWithTag("survey_scope_ALL_STEMS").performScrollTo().performClick().assertIsSelected()
        section("protocol").performClick()
        field("protocol_reference").performScrollTo().performTextReplacement("TEST : protocole déclaré")
        field("protocol_version").performScrollTo().performTextReplacement("TEST-v1")
        field("source_reference").performScrollTo().performTextReplacement("TEST : fiche du protocole")
        section("realization").performClick()
        compose.onNodeWithTag("survey_complete_incomplete").performScrollTo().performClick().assertIsSelected()
        field("area_input").performScrollTo().performTextReplacement("500,5")
        assertControlsVisibleWithKeyboard()
        assertCompleteViewport(compose.onNodeWithText(text(R.string.survey_preparation_area_label), useUnmergedTree = true))
        assertCompleteViewport(compose.onNodeWithText(text(R.string.survey_preparation_square_metres), useUnmergedTree = true))
        field("area_source_reference").performScrollTo().performTextReplacement("TEST : mesurage horizontal")
        assertControlsVisibleWithKeyboard()
        hideKeyboardWithBack()
        compose.runOnIdle { assertEquals("Retour ferme le clavier sans demander de fermer le contexte", 0, dismisses) }
        // Après avoir vérifié les actions avec le clavier ouvert, utiliser les
        // vrais en-têtes pour retrouver une vue générale lisible avant la capture.
        listOf("realization", "protocol", "population", "objective").forEach { section(it).performClick() }
        compose.onNodeWithText(text(R.string.survey_preparation_context_title)).performScrollTo()
        captureScreenshot("contexte-releve-fr-normal.png")
        contextConfirm().assertIsDisplayed().assertIsEnabled().performClick()

        compose.runOnIdle {
            assertEquals(listOf(SurveyContextDraft(
                purpose = SurveyPurpose.COMBINED,
                populationReference = "TEST : tiges du dispositif",
                populationScope = SurveyPopulationScope.ALL_STEMS,
                protocolReference = "TEST : protocole déclaré",
                protocolVersion = "TEST-v1",
                sourceReference = "TEST : fiche du protocole",
                inventoryComplete = false,
                inventoriedHorizontalAreaM2 = 500.5,
                areaSourceReference = "TEST : mesurage horizontal"
            )), saved)
        }
    }

    @Test
    fun should_keep_closed_context_read_only_without_any_save() {
        val saved = mutableListOf<SurveyContextDraft>()
        val existing = SurveyContextDraft(purpose = SurveyPurpose.MARKING,
            protocolReference = "TEST : version clôturée", inventoryComplete = false)
        content {
            SurveyContextDialog(stateKey = "TEST-CLOSED", initialDraft = existing,
                readOnly = true, busy = false, error = null, onDismiss = {},
                onSave = { saved += it }, onReload = {})
        }
        section("objective").performClick()
        purpose(SurveyPurpose.MARKING).assertIsSelected().assertIsNotEnabled()
        section("protocol").performClick()
        field("protocol_reference").performScrollTo().assertIsNotEnabled().assert(
            hasExactEditableText("TEST : version clôturée")
        )
        contextConfirm().assertDoesNotExist()
        compose.runOnIdle { assertTrue("La consultation clôturée ne sauvegarde rien", saved.isEmpty()) }
    }

    @Test
    fun should_preserve_edited_values_on_conflict_until_explicit_reload() {
        val saved = mutableListOf<SurveyContextDraft>()
        var reloads = 0
        var error by mutableStateOf<SurveyContextUiError?>(null)
        var stateKey by mutableStateOf("TEST-CONFLICT-R1")
        var initial by mutableStateOf(SurveyContextDraft(populationReference = "TEST : initial"))
        content {
            SurveyContextDialog(stateKey = stateKey, initialDraft = initial,
                readOnly = false, busy = false, error = error, onDismiss = {},
                onSave = { saved += it; error = SurveyContextUiError.CONFLICT },
                onReload = {
                    reloads++
                    initial = SurveyContextDraft(populationReference = "TEST : revision relue")
                    stateKey = "TEST-CONFLICT-R2"
                    error = null
                })
        }
        section("population").performClick()
        field("population_reference").performScrollTo().performTextReplacement("TEST : modification locale")
        contextConfirm().performClick()
        compose.onNodeWithText(text(R.string.survey_preparation_conflict)).performScrollTo().assertIsDisplayed()
        contextConfirm().assertIsNotEnabled()
        field("population_reference").assert(
            hasExactEditableText("TEST : modification locale")
        )
        compose.runOnIdle {
            assertEquals(0, reloads)
            assertEquals(listOf(SurveyContextDraft(populationReference = "TEST : modification locale")), saved)
        }
        compose.onNodeWithTag("survey_context_reload").assertIsDisplayed().performClick()
        section("population").performClick()
        field("population_reference").performScrollTo().assert(
            hasExactEditableText("TEST : revision relue")
        )
        compose.runOnIdle { assertEquals(1, reloads) }
    }

    @Test
    fun should_keep_start_choices_and_fixed_actions_accessible_with_large_font() {
        val starts = mutableListOf<SurveyContextDraft>()
        content(fontScale = 2f) {
            SurveyStartDialog(busy = false, failed = false, onDismiss = {}, onStart = { starts += it })
        }
        purpose(SurveyPurpose.COMBINED).performScrollTo().performClick()
        captureScreenshot("demarrage-releve-fr-grande-police.png")

        assertCompleteViewport(purpose(SurveyPurpose.COMBINED))
        assertCompleteViewport(startConfirm())
        assertCompleteViewport(compose.onNodeWithTag("survey_start_consult"))
        assertTextGeometry()
        startConfirm().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(SurveyContextDraft(purpose = SurveyPurpose.COMBINED)), starts) }
    }

    @Test
    fun should_keep_declared_context_and_fixed_save_accessible_with_large_font() {
        val saved = mutableListOf<SurveyContextDraft>()
        val existing = SurveyContextDraft(purpose = SurveyPurpose.INVENTORY,
            inventoryComplete = false, inventoriedHorizontalAreaM2 = 500.5,
            areaSourceReference = "TEST : mesurage horizontal")
        content(fontScale = 2f) {
            SurveyContextDialog(stateKey = "TEST-LARGE", initialDraft = existing,
                readOnly = false, busy = false, error = null, onDismiss = {},
                onSave = { saved += it }, onReload = {})
        }
        section("realization").performClick()
        field("area_input").performScrollTo()
        captureScreenshot("contexte-releve-fr-grande-police.png")

        assertCompleteViewport(field("area_input"))
        assertCompleteViewport(contextConfirm())
        assertCompleteViewport(compose.onNodeWithText(text(R.string.survey_preparation_area_label), useUnmergedTree = true))
        assertCompleteViewport(compose.onNodeWithText(text(R.string.survey_preparation_square_metres), useUnmergedTree = true))
        assertTextGeometry()
        contextConfirm().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(existing), saved) }
    }

    private fun content(fontScale: Float = 1f, block: @Composable () -> Unit) {
        compose.setContent {
            ForestryCounterTheme(themeMode = ThemeMode.LIGHT) {
                val original = MaterialTheme.typography
                val scaled = remember(original, fontScale) { original.scaled(fontScale) }
                // Le dialogue hérite ces styles. Ne pas changer la police système
                // ni écraser LocalDensity, que la fenêtre Android recrée.
                MaterialTheme(typography = scaled, content = block)
            }
        }
    }

    private fun purpose(purpose: SurveyPurpose) = compose.onNodeWithTag("survey_purpose_${purpose.name}")
    private fun startConfirm() = compose.onNodeWithTag("survey_start_confirm")
    private fun contextConfirm() = compose.onNodeWithTag("survey_context_confirm")
    private fun section(section: String) = compose.onNodeWithTag("survey_section_$section").performScrollTo()
    private fun field(field: String) = compose.onNodeWithTag("survey_$field")
    private fun text(resource: Int) = compose.activity.getString(resource)
    private fun hasExactEditableText(expected: String) = SemanticsMatcher("Contenu éditable exact : $expected") { node ->
        // Une saisie peut porter des annotations de rendu ajoutées par Compose.
        // Elles ne font pas partie de la référence métier ; comparer les caractères,
        // sans trim ou autre normalisation qui masquerait une modification.
        node.config.getOrNull(SemanticsProperties.EditableText)?.text == expected
    }

    private fun Typography.scaled(scale: Float): Typography = Typography(
        displayLarge = displayLarge.scaled(scale), displayMedium = displayMedium.scaled(scale),
        displaySmall = displaySmall.scaled(scale), headlineLarge = headlineLarge.scaled(scale),
        headlineMedium = headlineMedium.scaled(scale), headlineSmall = headlineSmall.scaled(scale),
        titleLarge = titleLarge.scaled(scale), titleMedium = titleMedium.scaled(scale),
        titleSmall = titleSmall.scaled(scale), bodyLarge = bodyLarge.scaled(scale),
        bodyMedium = bodyMedium.scaled(scale), bodySmall = bodySmall.scaled(scale),
        labelLarge = labelLarge.scaled(scale), labelMedium = labelMedium.scaled(scale),
        labelSmall = labelSmall.scaled(scale)
    )

    private fun TextStyle.scaled(scale: Float): TextStyle = copy(
        fontSize = fontSize * scale, lineHeight = lineHeight * scale
    )

    private fun assertCompleteViewport(interaction: SemanticsNodeInteraction) {
        interaction.assertIsDisplayed()
        val node = interaction.fetchSemanticsNode()
        val full = Rect(node.positionInRoot.x, node.positionInRoot.y,
            node.positionInRoot.x + node.size.width, node.positionInRoot.y + node.size.height)
        var root = node
        while (root.parent != null) root = requireNotNull(root.parent)
        val clipped = node.boundsInRoot
        val viewport = root.boundsInRoot
        fun contains(container: Rect): Boolean = full.left >= container.left - 1f &&
            full.top >= container.top - 1f && full.right <= container.right + 1f &&
            full.bottom <= container.bottom + 1f
        assertTrue("Commande ou champ coupé : complet=$full ; visible=$clipped ; fenêtre=$viewport",
            contains(clipped) && contains(viewport))
    }

    /** Les coordonnées d'accessibilité Android sont physiques, y compris le déplacement de la fenêtre. */
    @Suppress("DEPRECATION")
    private fun assertControlsVisibleWithKeyboard() {
        try {
            compose.waitForIdle()
            compose.waitUntil(timeoutMillis = 5_000) { visibleKeyboardBounds() != null }
            val keyboard = requireNotNull(visibleKeyboardBounds())
            val screenSize = Point()
            compose.activity.windowManager.defaultDisplay.getRealSize(screenSize)
            val screen = AndroidRect(0, 0, screenSize.x, screenSize.y)
            assertCompleteViewport(compose.onNodeWithTag("survey_dialog_panel"))
            assertCompleteViewport(contextConfirm())
            assertCompleteViewport(compose.onNodeWithTag("survey_context_close"))
            val labels = listOf(text(R.string.survey_preparation_save_action), text(R.string.survey_preparation_close_action))
            labels.forEach { label ->
                val matches = mutableListOf<AndroidRect>()
                InstrumentationRegistry.getInstrumentation().uiAutomation.windows
                    .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                    .forEach windowLoop@{ window ->
                        val root = window.root ?: return@windowLoop
                        try {
                            if (root.packageName?.toString() == compose.activity.packageName) {
                                matches += physicalClickableBounds(root, label)
                            }
                        } finally { root.recycle() }
                    }
                assertEquals("Une commande physique unique doit correspondre à $label", 1, matches.size)
                val bounds = matches.single()
                assertTrue("Commande $label hors de l'écran physique : $bounds ; écran=$screen", screen.contains(bounds))
                assertTrue("Commande $label recouverte par le clavier : $bounds ; clavier=$keyboard",
                    bounds.bottom <= keyboard.top && !AndroidRect.intersects(bounds, keyboard))
                println("GEOSYLVA_TEST_SURVEY_PHYSICAL_COMMAND=$label ; écran=$screen ; clavier=$keyboard ; commande=$bounds")
            }
        } catch (failure: AssertionError) {
            try { captureWindowDiagnostic("diagnostic-commandes-clavier") }
            catch (diagnosticFailure: Exception) { failure.addSuppressed(diagnosticFailure) }
            throw failure
        }
    }

    private fun visibleKeyboardBounds(): AndroidRect? = InstrumentationRegistry.getInstrumentation().uiAutomation.windows
        .firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        ?.let { window -> AndroidRect().apply { window.getBoundsInScreen(this) }.takeUnless { it.isEmpty } }

    @Suppress("DEPRECATION")
    private fun physicalClickableBounds(
        root: AccessibilityNodeInfo,
        label: String,
        nearestClickableBounds: AndroidRect? = null,
    ): List<AndroidRect> {
        if (root.packageName?.toString() != compose.activity.packageName) return emptyList()
        val found = mutableListOf<AndroidRect>()
        // Compose peut exposer le libellé dans un TextView enfant du conteneur
        // cliquable. Vérifier la commande entière, jamais le seul rectangle du texte.
        // Un ancêtre cliquable désactivé ou invisible interrompt la résolution.
        val commandBounds = if (root.isClickable) {
            if (root.isVisibleToUser && root.isEnabled) {
                AndroidRect().apply { root.getBoundsInScreen(this) }.takeUnless { it.isEmpty }
            } else null
        } else nearestClickableBounds
        if (root.isVisibleToUser && root.isEnabled && root.text?.toString() == label && commandBounds != null) {
            found += AndroidRect(commandBounds)
        }
        for (index in 0 until root.childCount) {
            val child = root.getChild(index) ?: continue
            try { found += physicalClickableBounds(child, label, commandBounds) }
            finally { child.recycle() }
        }
        return found
    }

    private fun hideKeyboardWithBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(timeoutMillis = 5_000) { visibleKeyboardBounds() == null }
        compose.onNodeWithTag("survey_dialog_panel").assertIsDisplayed()
    }

    private fun assertTextGeometry() {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true)
        val count = nodes.fetchSemanticsNodes().size
        assertTrue("Les composants réels doivent exposer leurs textes", count > 0)
        val issues = mutableListOf<String>()
        for (index in 0 until count) {
            val layouts = mutableListOf<TextLayoutResult>()
            nodes[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                assertTrue(action(layouts))
            }
            for (layout in layouts) {
                val text = layout.layoutInput.text.text
                if (text.isEmpty()) continue
                val completeEnd = text.trimEnd().length
                if (layout.lineCount == 0 || layout.getLineStart(0) != 0 ||
                    layout.getLineEnd(layout.lineCount - 1, visibleEnd = true) < completeEnd) {
                    issues += "Texte incomplet : $text"
                    continue
                }
                if (layout.didOverflowHeight) issues += "Hauteur insuffisante : $text"
                for (line in 0 until layout.lineCount) {
                    val lineBounds = listOf(layout.getLineLeft(line), layout.getLineRight(line),
                        layout.getLineTop(line), layout.getLineBottom(line))
                    if (lineBounds.any { !it.isFinite() } || layout.isLineEllipsized(line) || layout.getLineLeft(line) < -1f ||
                        layout.getLineRight(line) > layout.size.width + 1f ||
                        layout.getLineTop(line) < -1f || layout.getLineBottom(line) > layout.size.height + 1f) {
                        issues += "Ligne $line hors de sa boîte ${layout.size} : $text"
                    }
                }
                for (offset in text.indices.filter { !text[it].isWhitespace() }) {
                    val glyph = layout.getBoundingBox(offset)
                    if (listOf(glyph.left, glyph.right, glyph.top, glyph.bottom).any { !it.isFinite() } ||
                        glyph.left < -1f || glyph.right > layout.size.width + 1f ||
                        glyph.top < -1f || glyph.bottom > layout.size.height + 1f) {
                        issues += "Caractère $offset hors de sa boîte ${layout.size} : $text ; $glyph"
                    }
                }
                // Le drapeau horizontal seul compare parfois une largeur de
                // paragraphe à une boîte intrinsèque plus petite. Les lignes et
                // tous les caractères visibles doivent pourtant tenir réellement.
            }
        }
        assertTrue("Débordements typographiques réels :\n${issues.joinToString("\n")}", issues.isEmpty())
    }

    private fun captureScreenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull("La capture native doit être disponible", screenshot)
        val bitmap = requireNotNull(screenshot)
        try {
            val external = requireNotNull(instrumentation.targetContext.getExternalFilesDir(null))
            val directory = File(external, "GeoSylva-TEST-survey-preparation-2026-10-09/${UUID.randomUUID()}")
            assertTrue("Répertoire TEST neuf", directory.mkdirs())
            val destination = File(directory, name)
            assertTrue("Aucune capture antérieure écrasée", destination.createNewFile())
            destination.outputStream().use { output -> assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) }
            val semantics = File(directory, "${name.removeSuffix(".png")}.semantics.txt")
            assertTrue(semantics.createNewFile())
            semantics.writeText(compose.onAllNodes(isRoot(), useUnmergedTree = true).printToString(maxDepth = 15),
                Charsets.UTF_8)
            assertTrue(destination.length() > 0L)
            println("GEOSYLVA_TEST_SURVEY_SCREENSHOT=${destination.absolutePath}")
            println("GEOSYLVA_TEST_SURVEY_SEMANTICS=${semantics.absolutePath}")
        } finally {
            bitmap.recycle()
        }
    }

    /** Diagnostic natif distinct des quatre captures officielles ; aucune interaction ou attente Compose. */
    @Suppress("DEPRECATION")
    private fun captureWindowDiagnostic(label: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val external = requireNotNull(instrumentation.targetContext.getExternalFilesDir(null))
        val directory = File(external, "GeoSylva-TEST-survey-preparation-2026-10-09/${UUID.randomUUID()}")
        assertTrue("Répertoire de diagnostic neuf", directory.mkdirs())
        val screenshot = requireNotNull(automation.takeScreenshot())
        try {
            val image = File(directory, "$label.png")
            assertTrue("Aucune capture antérieure écrasée", image.createNewFile())
            image.outputStream().use { output -> assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output)) }
            assertTrue(image.length() > 0L)
            println("GEOSYLVA_TEST_SURVEY_SCREENSHOT=${image.absolutePath}")
        } finally { screenshot.recycle() }
        val report = StringBuilder()
        automation.windows.forEach { window ->
            val bounds = AndroidRect().apply { window.getBoundsInScreen(this) }
            report.append("window id=${window.id} type=${window.type} active=${window.isActive} ")
                .append("focused=${window.isFocused} title=${window.title} bounds=$bounds\n")
            val root = window.root
            if (root == null) {
                report.append("  root=null\n")
            } else {
                try {
                    report.append("  package=${root.packageName}\n")
                    // Ne pas lire les suggestions du clavier ou les contenus d'autres applications.
                    if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                        (window.isFocused || window.isActive || root.packageName?.toString() == compose.activity.packageName)) {
                        appendAccessibilityDiagnostic(root, report, depth = 0, remaining = 1_500)
                    }
                } finally { root.recycle() }
            }
        }
        val destination = File(directory, "$label.windows.txt")
        assertTrue("Aucun diagnostic antérieur écrasé", destination.createNewFile())
        destination.writeText(report.toString(), Charsets.UTF_8)
        println("GEOSYLVA_TEST_SURVEY_WINDOWS=${destination.absolutePath}\n$report")
    }

    @Suppress("DEPRECATION")
    private fun appendAccessibilityDiagnostic(
        node: AccessibilityNodeInfo,
        report: StringBuilder,
        depth: Int,
        remaining: Int,
    ): Int {
        val bounds = AndroidRect().apply { node.getBoundsInScreen(this) }
        fun diagnosticText(value: CharSequence?) = value?.toString()?.replace("\n", "\\n")
        report.append("  ".repeat(depth)).append("node class=${node.className} package=${node.packageName} ")
            .append("id=${node.viewIdResourceName} text=${diagnosticText(node.text)} ")
            .append("description=${diagnosticText(node.contentDescription)} clickable=${node.isClickable} ")
            .append("visible=${node.isVisibleToUser} enabled=${node.isEnabled} focused=${node.isFocused} ")
            .append("bounds=$bounds children=${node.childCount}\n")
        var visited = 1
        if (depth >= 40) return visited
        for (index in 0 until node.childCount) {
            if (visited >= remaining) break
            val child = node.getChild(index) ?: continue
            try { visited += appendAccessibilityDiagnostic(child, report, depth + 1, remaining - visited) }
            finally { child.recycle() }
        }
        return visited
    }

}
