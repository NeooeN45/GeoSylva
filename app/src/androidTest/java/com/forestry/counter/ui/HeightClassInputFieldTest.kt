package com.forestry.counter.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forestry.counter.R
import com.forestry.counter.data.preferences.ThemeMode
import com.forestry.counter.presentation.components.AppMiniDialog
import com.forestry.counter.presentation.components.HeightClassInputField
import com.forestry.counter.presentation.theme.ForestryCounterTheme
import com.forestry.counter.presentation.utils.hasInvalidHeightInput
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import java.util.UUID

/** Composants de production et saisies de test ; aucune lecture ou mutation des dossiers terrain. */
@RunWith(AndroidJUnit4::class)
class HeightClassInputFieldTest {
    @get:Rule
    val compose = createComposeRule()

    private val frenchContext: Context by lazy {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.FRANCE) }
        )
    }

    @Test
    fun should_keep_decimal_comma_as_one_height_without_error() {
        content(initialValue = "", missingCount = 1, recordedCount = 0, requiresHeight = true)

        field().assert(hasError)
        compose.onNodeWithText(frenchContext.getString(R.string.height_input_invalid)).assertDoesNotExist()
        field().performTextReplacement("20,5")

        field().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("20,5"))
        ).assert(hasError.not())
        compose.onNodeWithText(frenchContext.getString(R.string.height_input_invalid)).assertDoesNotExist()
        compose.onNodeWithText(frenchContext.getString(R.string.height_mean_computed_format, 20.5, 1))
            .assertDoesNotExist()
        confirm().assertIsEnabled()
    }

    @Test
    fun should_display_mean_and_recorded_count_for_semicolon_separated_heights() {
        content(initialValue = "18,5;20", missingCount = 2, recordedCount = 3, requiresHeight = true)
        captureScreenshot("hauteurs-moyenne-fr-normal.png")

        field().assert(hasError.not())
        compose.onNodeWithText(
            frenchContext.getString(R.string.height_mean_computed_format, 19.25, 2),
            substring = true
        ).assertIsDisplayed()
        compose.onNodeWithText(
            frenchContext.getString(R.string.height_n_recorded_format, 3),
            substring = true
        ).assertIsDisplayed()
        confirm().assertIsEnabled()
    }

    @Test
    fun should_reject_ambiguous_commas_and_clear_error_after_correction() {
        content(initialValue = "18,5,20", missingCount = 0, recordedCount = 0, requiresHeight = false)
        dumpSemantics("virgules-ambigues")

        field().assert(hasError)
        compose.onNodeWithText(
            frenchContext.getString(R.string.height_input_invalid),
            substring = true
        ).assertIsDisplayed()
        confirm().assertIsNotEnabled()

        field().performTextReplacement("18,5;20")

        field().assert(hasError.not())
        compose.onNodeWithText(frenchContext.getString(R.string.height_input_invalid), substring = true)
            .assertDoesNotExist()
        compose.onNodeWithText(
            frenchContext.getString(R.string.height_mean_computed_format, 19.25, 2),
            substring = true
        ).assertIsDisplayed()
        confirm().assertIsEnabled()
    }

    @Test
    fun should_allow_unknown_height_when_tariff_does_not_require_it() {
        content(initialValue = "", missingCount = 3, recordedCount = 0, requiresHeight = false)
        dumpSemantics("hauteur-inconnue")

        field().assert(hasError.not())
        compose.onNodeWithText(frenchContext.getString(R.string.height_n_missing_format, 3))
            .assertIsDisplayed()
        compose.onNodeWithText(frenchContext.getString(R.string.height_input_invalid)).assertDoesNotExist()
        confirm().assertIsEnabled()
    }

    @Test
    fun should_keep_height_and_mean_readable_with_large_font() {
        content(initialValue = "18,5;20", missingCount = 2, recordedCount = 1,
            requiresHeight = true, fontScale = 2f)
        captureScreenshot("hauteurs-moyenne-fr-grande-police.png")

        field().assertIsDisplayed().assert(hasError.not())
        compose.onNodeWithText(
            frenchContext.getString(R.string.height_mean_computed_format, 19.25, 2),
            substring = true
        ).assertIsDisplayed()
        confirm().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText(frenchContext.getString(R.string.height), useUnmergedTree = true)
            .assertIsDisplayed()
        compose.onNodeWithText(frenchContext.getString(R.string.unit_m), useUnmergedTree = true).assertIsDisplayed()
        assertTextLayoutsDoNotOverflow()
    }

    private fun content(
        initialValue: String,
        missingCount: Int,
        recordedCount: Int,
        requiresHeight: Boolean,
        fontScale: Float = 1f
    ) {
        compose.setContent {
            ForestryCounterTheme(themeMode = ThemeMode.LIGHT) {
                val typography = MaterialTheme.typography
                val scaledTypography = remember(typography, fontScale) { typography.scaled(fontScale) }
                // Le dialogue Android recrée son contexte et sa densité ; les styles du thème restent hérités.
                MaterialTheme(typography = scaledTypography) {
                    var value by remember { mutableStateOf(initialValue) }
                    AppMiniDialog(
                        onDismissRequest = {},
                        title = frenchContext.getString(R.string.height_by_class_m),
                        description = frenchContext.getString(
                            if (requiresHeight) R.string.height_tarif_info_2e else R.string.height_tarif_info_1e
                        ),
                        confirmText = frenchContext.getString(R.string.action_save),
                        onConfirm = {},
                        confirmEnabled = !hasInvalidHeightInput(listOf(value)),
                        animationsEnabled = false
                    ) {
                        // Fournir le contexte français après la création de la fenêtre du dialogue.
                        CompositionLocalProvider(
                            LocalContext provides frenchContext,
                            LocalConfiguration provides frenchContext.resources.configuration
                        ) {
                            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                                // Le champ réel est sous l'en-tête d'une classe, dans un
                                // contenu rembourré ; le monter seul en tête du scroll
                                // coupe artificiellement la boîte de son libellé flottant.
                                Column(Modifier.fillMaxWidth().padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(frenchContext.getString(R.string.diameter_cm_value_format, 40),
                                            style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                        Text(frenchContext.getString(R.string.height_n_stems_format,
                                            missingCount + recordedCount), style = MaterialTheme.typography.labelSmall)
                                    }
                                    HeightClassInputField(
                                        value = value,
                                        onValueChange = { value = it },
                                        missingCount = missingCount,
                                        recordedCount = recordedCount,
                                        tarifRequiresHeight = requiresHeight,
                                        modifier = Modifier.testTag(FIELD_TAG)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun Typography.scaled(scale: Float): Typography = Typography(
        displayLarge = displayLarge.scaled(scale),
        displayMedium = displayMedium.scaled(scale),
        displaySmall = displaySmall.scaled(scale),
        headlineLarge = headlineLarge.scaled(scale),
        headlineMedium = headlineMedium.scaled(scale),
        headlineSmall = headlineSmall.scaled(scale),
        titleLarge = titleLarge.scaled(scale),
        titleMedium = titleMedium.scaled(scale),
        titleSmall = titleSmall.scaled(scale),
        bodyLarge = bodyLarge.scaled(scale),
        bodyMedium = bodyMedium.scaled(scale),
        bodySmall = bodySmall.scaled(scale),
        labelLarge = labelLarge.scaled(scale),
        labelMedium = labelMedium.scaled(scale),
        labelSmall = labelSmall.scaled(scale)
    )

    private fun TextStyle.scaled(scale: Float): TextStyle = copy(
        fontSize = fontSize * scale,
        lineHeight = lineHeight * scale
    )

    private fun field() = compose.onNodeWithTag(FIELD_TAG)

    private fun confirm() = compose.onNodeWithText(frenchContext.getString(R.string.action_save))

    private fun assertTextLayoutsDoNotOverflow() {
        val nodes = compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true
        )
        val count = nodes.fetchSemanticsNodes().size
        val anomalies = mutableListOf<String>()
        if (count == 0) anomalies += "Les composants réels n'exposent aucun texte rendu"
        for (index in 0 until count) {
            val layouts = mutableListOf<TextLayoutResult>()
            nodes[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getLayouts ->
                if (!getLayouts(layouts)) anomalies += "Le nœud $index ne fournit pas son rendu textuel"
            }
            val node = nodes[index].fetchSemanticsNode()
            val bounds = node.boundsInRoot
            if (layouts.isEmpty()) anomalies += "Le nœud $index ne fournit aucun rendu textuel"
            for (layout in layouts) {
                val intrinsicWidthCase = layout.lineCount == 1 && layout.didOverflowWidth &&
                    layout.multiParagraph.width > layout.size.width
                val issues = if (intrinsicWidthCase) {
                    singleLineGeometryIssues(layout, nodes[index].isDisplayed()) + fullVisibilityIssues(node)
                } else if (layout.hasVisualOverflow) {
                    listOf("Débordement du texte selon les métriques strictes")
                } else {
                    emptyList()
                }
                if (issues.isNotEmpty()) {
                    anomalies += "${issues.joinToString(" ; ")} ; bornes=$bounds ; métriques=${textLayoutDiagnostic(layout)}"
                }
            }
        }
        assertTrue("Anomalies des textes avec une police agrandie :\n${anomalies.joinToString("\n")}",
            anomalies.isEmpty())
    }

    private fun fullVisibilityIssues(node: SemanticsNode): List<String> {
        val tolerancePx = 1f
        val position = node.positionInRoot
        val full = Rect(position.x, position.y,
            position.x + node.size.width, position.y + node.size.height)
        val visible = node.boundsInRoot
        var root = node
        while (root.parent != null) root = requireNotNull(root.parent)
        val viewport = root.boundsInRoot
        fun contains(container: Rect, content: Rect): Boolean =
            content.left >= container.left - tolerancePx &&
                content.top >= container.top - tolerancePx &&
                content.right <= container.right + tolerancePx &&
                content.bottom <= container.bottom + tolerancePx
        // isDisplayed accepte une intersection partielle. Ces dialogues n'ont pas
        // de rotation : la position et la taille donnent donc la boîte complète,
        // qui doit tenir dans les limites clippées des parents et de la fenêtre.
        return if (contains(visible, full) && contains(viewport, full)) emptyList()
        else listOf("Boîte de texte coupée par un parent ou la fenêtre : complète=$full ; visible=$visible ; fenêtre=$viewport")
    }

    private fun singleLineGeometryIssues(layout: TextLayoutResult, displayed: Boolean): List<String> {
        val tolerancePx = 1f
        val text = layout.layoutInput.text.text
        val issues = mutableListOf<String>()
        if (!displayed) issues += "Texte non affiché"
        if (layout.getLineStart(0) != 0 || layout.getLineEnd(0, visibleEnd = true) != text.length) {
            issues += "Ligne visible incomplète"
        }
        if (layout.isLineEllipsized(0)) issues += "Ligne remplacée par une ellipse"
        if (layout.didOverflowHeight) issues += "Débordement vertical"
        if (!(layout.getLineLeft(0) >= -tolerancePx && layout.getLineRight(0) <= layout.size.width + tolerancePx)) {
            issues += "Ligne hors des limites horizontales"
        }
        if (!(layout.getLineTop(0) >= -tolerancePx && layout.getLineBottom(0) <= layout.size.height + tolerancePx)) {
            issues += "Ligne hors des limites verticales"
        }
        if (layout.getLineBaseline(0) !in -tolerancePx..(layout.size.height + tolerancePx)) {
            issues += "Ligne de base hors de la boîte"
        }
        for (offset in text.indices) {
            val bounds = layout.getBoundingBox(offset)
            if (!(bounds.left >= -tolerancePx && bounds.right <= layout.size.width + tolerancePx &&
                bounds.top >= -tolerancePx && bounds.bottom <= layout.size.height + tolerancePx)) {
                issues += "Caractère $offset hors de la boîte"
            }
        }
        // Une ligne peut conserver un paragraphe mesuré à la largeur du champ,
        // puis recevoir une boîte réduite à sa largeur intrinsèque. Le drapeau
        // horizontal compare ces deux largeurs ; seuls le texte affiché et tous
        // ses caractères entiers dans la boîte permettent d'accepter ce cas.
        return issues
    }

    private fun textLayoutDiagnostic(layout: TextLayoutResult): JSONObject = JSONObject().apply {
        val text = layout.layoutInput.text.text
        put("text", text)
        put("textLength", text.length)
        put("width", layout.size.width)
        put("height", layout.size.height)
        put("paragraphWidth", layout.multiParagraph.width)
        put("paragraphHeight", layout.multiParagraph.height)
        put("constraints", layout.layoutInput.constraints.toString())
        put("fontSize", layout.layoutInput.style.fontSize.toString())
        put("lineHeight", layout.layoutInput.style.lineHeight.toString())
        put("didOverflowWidth", layout.didOverflowWidth)
        put("didOverflowHeight", layout.didOverflowHeight)
        put("hasVisualOverflow", layout.hasVisualOverflow)
        put("lineCount", layout.lineCount)
        put("lines", JSONArray().apply {
            for (line in 0 until layout.lineCount) {
                put(JSONObject().apply {
                    put("start", layout.getLineStart(line))
                    put("end", layout.getLineEnd(line))
                    put("visibleEnd", layout.getLineEnd(line, visibleEnd = true))
                    put("ellipsis", layout.isLineEllipsized(line))
                    put("baseline", layout.getLineBaseline(line))
                    put("top", layout.getLineTop(line))
                    put("bottom", layout.getLineBottom(line))
                    put("left", layout.getLineLeft(line))
                    put("right", layout.getLineRight(line))
                })
            }
        })
        if (layout.lineCount == 1 && layout.didOverflowWidth && layout.multiParagraph.width > layout.size.width) {
            put("characters", JSONArray().apply {
                for (offset in text.indices) {
                    put(JSONObject().apply {
                        put("offset", offset)
                        put("character", text[offset].toString())
                        put("bounds", layout.getBoundingBox(offset).toString())
                    })
                }
            })
        }
    }

    private fun captureScreenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull("La capture native du dialogue doit être disponible", bitmap)
        val screenshot = requireNotNull(bitmap)
        try {
            val externalRoot = requireNotNull(instrumentation.targetContext.getExternalFilesDir(null))
            val directory = File(externalRoot, "GeoSylva-TEST-height-2026-10-09/${UUID.randomUUID()}")
            assertTrue("Le répertoire de capture est neuf", directory.mkdirs())
            writeSemantics(directory, name.removeSuffix(".png"))
            val destination = File(directory, name)
            assertTrue("Une capture antérieure ne doit jamais être écrasée", destination.createNewFile())
            destination.outputStream().use { output ->
                assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            assertTrue(destination.length() > 0L)
            println("GEOSYLVA_TEST_HEIGHT_SCREENSHOT=${destination.absolutePath}")
        } finally {
            screenshot.recycle()
        }
    }

    private fun dumpSemantics(label: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val externalRoot = requireNotNull(context.getExternalFilesDir(null))
        val directory = File(externalRoot, "GeoSylva-TEST-height-2026-10-09/${UUID.randomUUID()}")
        assertTrue("Le répertoire de diagnostic est neuf", directory.mkdirs())
        writeSemantics(directory, label)
    }

    private fun writeSemantics(directory: File, label: String) {
        val tree = compose.onAllNodes(isRoot(), useUnmergedTree = true).printToString(maxDepth = 12)
        val destination = File(directory, "$label.semantics.txt")
        assertTrue("Un diagnostic antérieur ne doit pas être écrasé", destination.createNewFile())
        destination.writeText(tree, Charsets.UTF_8)
        println("GEOSYLVA_TEST_HEIGHT_SEMANTICS=${destination.absolutePath}\n$tree")
    }

    private companion object {
        const val FIELD_TAG = "height_class_input_test"
        val hasError = SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)
    }
}
