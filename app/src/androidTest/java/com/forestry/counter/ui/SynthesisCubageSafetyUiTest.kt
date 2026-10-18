package com.forestry.counter.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forestry.counter.R
import com.forestry.counter.data.preferences.ThemeMode
import com.forestry.counter.domain.calculation.RecordedDendrometryCalculator
import com.forestry.counter.domain.calculation.SynthesisParameters
import com.forestry.counter.domain.calculation.SynthesisReport
import com.forestry.counter.domain.calculation.SynthesisReportCalculator
import com.forestry.counter.domain.calculation.tarifs.TarifSelection
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.presentation.screens.forestry.DendrometryOverviewCard
import com.forestry.counter.presentation.screens.forestry.TarifDocumentationScreen
import com.forestry.counter.presentation.screens.forestry.VolumeConditionsCard
import com.forestry.counter.presentation.theme.ForestryCounterTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import java.util.UUID

/** Production components with explicit TEST inputs only; no field database or account is read. */
@RunWith(AndroidJUnit4::class)
class SynthesisCubageSafetyUiTest {
    @get:Rule val compose = createComposeRule()

    private val frenchContext: Context by lazy {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.FRANCE)
        })
    }

    @Test fun current_unqualified_volume_explains_conditions_and_keeps_recorded_geometry() {
        val observations = observations()
        val report = report(observations)
        content {
            LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Text("TEST · Synthèse du technicien", style = MaterialTheme.typography.headlineSmall) }
                item { DendrometryOverviewCard(RecordedDendrometryCalculator.calculate(report, observations)) }
                item { VolumeConditionsCard(report) }
            }
        }
        compose.onNodeWithTag("synthesis_dendrometry_overview").assertIsDisplayed()
        text(R.string.synthesis_volume_conditions_title).performScrollTo().assertIsDisplayed()
        text(R.string.synthesis_volume_conditions_body).assertIsDisplayed()
        compose.onNodeWithText(frenchContext.getString(R.string.synthesis_volume_conditions_count, 2, 2))
            .assertIsDisplayed()
        text(R.string.synthesis_volume_conditions_commercial).assertIsDisplayed()
        assertTextHasNoHeightOverflowOrEllipsis()
        screenshot("synthese-cubage-conditions-fr.png")
    }

    @Test fun volume_explanation_remains_scrollable_at_double_font_scale() {
        content(fontScale = 2f) {
            LazyColumn(Modifier.fillMaxSize().padding(16.dp)) { item { VolumeConditionsCard(report(observations())) } }
        }
        text(R.string.synthesis_volume_conditions_title).assertIsDisplayed()
        assertTextHasNoHeightOverflowOrEllipsis()
        screenshot("synthese-cubage-grande-police-fr.png")
        text(R.string.synthesis_volume_conditions_commercial).performScrollTo().assertIsDisplayed()
        assertTextHasNoHeightOverflowOrEllipsis()
    }

    @Test fun a_historical_report_does_not_receive_a_new_engine_notice() {
        val report = report(observations()).copy(engineVersion = SynthesisReport.ENGINE_VERSION)
        content { Column { Text("TEST · Archive historique"); VolumeConditionsCard(report) } }
        compose.onNodeWithTag("synthesis_volume_conditions").assertDoesNotExist()
        text(R.string.synthesis_volume_conditions_title).assertDoesNotExist()
    }

    @Test fun no_selected_method_is_not_mislabeled_as_a_selected_unqualified_method() {
        val report = report(observations(), tariff = null)
        content { Column { Text("TEST · Sans méthode choisie"); VolumeConditionsCard(report) } }
        compose.onNodeWithTag("synthesis_volume_conditions").assertDoesNotExist()
        assertTrue(report.uncomputedReasons.containsKey("MISSING_TARIF"))
    }

    @Test fun documentation_tabs_explain_sources_choices_and_actual_units() {
        content { TarifDocumentationScreen(onNavigateBack = {}) }
        text(R.string.tarif_qualified_title).assertIsDisplayed()
        text(R.string.tarif_qualified_tab_sources).assertIsSelected()
        text(R.string.tarif_qualified_sources_title).assertIsDisplayed()
        text(R.string.tarif_qualified_sources_scope).assertIsDisplayed()
        screenshot("cubage-reperes-sources-fr.png")
        text(R.string.tarif_qualified_tab_choice).performClick().assertIsSelected()
        text(R.string.tarif_qualified_choice_title).assertIsDisplayed()
        screenshot("cubage-reperes-methode-fr.png")
        text(R.string.tarif_qualified_tab_formulas).performClick().assertIsSelected()
        text(R.string.tarif_qualified_basal_formula).assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(4)
        text(R.string.tarif_qualified_slenderness_formula).assertIsDisplayed()
        text(R.string.tarif_qualified_slenderness_limit).performScrollTo().assertIsDisplayed()
        assertTextHasNoHeightOverflowOrEllipsis()
        screenshot("cubage-reperes-hd-fr.png")
    }

    @Test fun documentation_stays_usable_at_double_font_scale() {
        content(fontScale = 2f) { TarifDocumentationScreen(onNavigateBack = {}) }
        text(R.string.tarif_qualified_tab_formulas).performScrollTo().performClick().assertIsSelected()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(4)
        text(R.string.tarif_qualified_slenderness_formula).assertIsDisplayed()
        assertTextHasNoHeightOverflowOrEllipsis()
        screenshot("cubage-reperes-hd-grande-police-fr.png")
    }

    private fun content(fontScale: Float = 1f, body: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalContext provides frenchContext,
                LocalConfiguration provides frenchContext.resources.configuration,
                LocalDensity provides Density(density.density, fontScale)) {
                ForestryCounterTheme(themeMode = ThemeMode.LIGHT) { body() }
            }
        }
    }

    private fun text(id: Int) = compose.onNodeWithText(frenchContext.getString(id))

    private fun assertTextHasNoHeightOverflowOrEllipsis() {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true)
        assertTrue(nodes.fetchSemanticsNodes().isNotEmpty())
        for (index in nodes.fetchSemanticsNodes().indices) {
            val layouts = mutableListOf<TextLayoutResult>()
            nodes[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> action(layouts) }
            for (layout in layouts) {
                assertFalse("Text height must remain unconstrained", layout.didOverflowHeight)
                for (line in 0 until layout.lineCount) assertFalse("No explanatory text may be ellipsized", layout.isLineEllipsized(line))
            }
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val directory = File(requireNotNull(frenchContext.getExternalFilesDir(null)),
            "GeoSylva-TEST-synthesis-cubage-2026-10-12/${UUID.randomUUID()}")
        assertTrue(directory.mkdirs())
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, name).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        println("GEOSYLVA_TEST_SYNTHESIS_SCREENSHOT=${File(directory, name).absolutePath}")
    }

    private fun observations() = listOf(30.0, 40.0).mapIndexed { index, diameter ->
        Tige("TEST-tree-$index", "TEST-parcel", "TEST-plot", "TEST-species", diameter, 20.0,
            null, null, null, 1L, null, null, null, null, sessionId = "TEST-session")
    }

    private fun report(observations: List<Tige>, tariff: TarifSelection? = TarifSelection("FGH")): SynthesisReport {
        val parcel = Parcelle("TEST-parcel", null, "TEST-forest", "TEST-parcelle", 1.0, null, null, null, null, null,
            null, null, null, null, null, null, null, null, createdAt = 1L, updatedAt = 1L)
        val plot = Placette("TEST-plot", parcel.id, "TEST-placette", "AREA", null, 100.0, null, createdAt = 1L, updatedAt = 1L)
        return SynthesisReportCalculator.calculate(parcel, listOf(plot), observations, emptyMap(),
            SynthesisParameters(tariff, selectedPlotId = plot.id, sourcePolicy = SynthesisParameters.CURRENT_SOURCE_POLICY),
            "TEST-session", "TEST-campaign", 1, 2L)
    }
}
