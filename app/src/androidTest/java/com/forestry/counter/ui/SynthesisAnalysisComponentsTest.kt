package com.forestry.counter.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forestry.counter.R
import com.forestry.counter.domain.calculation.RecordedDendrometryCalculator
import com.forestry.counter.domain.calculation.RecordedDendrometryStatus
import com.forestry.counter.domain.calculation.SynthesisParameters
import com.forestry.counter.domain.calculation.SynthesisReport
import com.forestry.counter.domain.calculation.SynthesisReportCalculator
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Placette
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.presentation.screens.forestry.DendrometryOverviewCard
import com.forestry.counter.presentation.screens.forestry.DiameterDistributionCard
import com.forestry.counter.presentation.screens.forestry.EcologicalObservationCard
import com.forestry.counter.presentation.screens.forestry.HeightProtocolHelpDialog
import com.forestry.counter.presentation.screens.forestry.SynthesisSection
import com.forestry.counter.presentation.screens.forestry.SynthesisSectionSelector
import com.forestry.counter.presentation.screens.forestry.UnqualifiedHeightIndicatorsCard
import com.forestry.counter.presentation.screens.forestry.WoodQualityCoverageCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sqrt

/** Real components and pure public test observations; no app database or business mutations. */
@RunWith(AndroidJUnit4::class)
class SynthesisAnalysisComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun selector_selects_all_three_uses_with_accessible_state_and_actions() {
        val selections = mutableListOf<SynthesisSection>()
        content(width = 280.dp) {
            var selected by remember { mutableStateOf(SynthesisSection.MANAGEMENT) }
            SynthesisSectionSelector(selected, { selected = it; selections += it })
        }
        compose.onNodeWithTag("synthesis_section_picker")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        for (destination in listOf(SynthesisSection.WOOD, SynthesisSection.ECOLOGY, SynthesisSection.MANAGEMENT)) {
            compose.onNodeWithTag("synthesis_section_picker").performClick()
            compose.onNodeWithTag(sectionTag(destination)).performScrollTo().assertHasClickAction().performClick()
            compose.onNodeWithText(context.getString(destination.title)).assertIsDisplayed()
            compose.onNodeWithTag("synthesis_section_picker").assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                    context.getString(R.string.analysis_selected_section, context.getString(destination.title)))
            )
            compose.onNodeWithTag("synthesis_section_picker").performClick()
            compose.onNodeWithTag(sectionTag(destination)).performScrollTo().assertIsSelected()
            SynthesisSection.entries.filter { it != destination }.forEach {
                compose.onNodeWithTag(sectionTag(it)).assertIsNotSelected()
            }
            compose.onNodeWithTag(sectionTag(destination)).performClick()
        }
        compose.runOnIdle {
            assertEquals(listOf(SynthesisSection.WOOD, SynthesisSection.WOOD,
                SynthesisSection.ECOLOGY, SynthesisSection.ECOLOGY,
                SynthesisSection.MANAGEMENT, SynthesisSection.MANAGEMENT), selections)
        }
    }

    @Test
    fun compact_selector_wraps_real_labels_at_double_font_scale() {
        content(width = 280.dp, fontScale = 2f) {
            var selected by remember { mutableStateOf(SynthesisSection.MANAGEMENT) }
            SynthesisSectionSelector(selected, { selected = it })
        }
        assertTextLayoutsDoNotOverflow()
        compose.onNodeWithTag("synthesis_section_picker").performClick()
        compose.onNodeWithTag(sectionTag(SynthesisSection.ECOLOGY)).performScrollTo().assertIsDisplayed()
        assertTextLayoutsDoNotOverflow()
        compose.onNodeWithTag(sectionTag(SynthesisSection.ECOLOGY)).performClick()
        compose.onNodeWithText(context.getString(SynthesisSection.ECOLOGY.explanation)).assertIsDisplayed()
        assertTextLayoutsDoNotOverflow()
    }

    @Test
    fun overview_displays_computed_recorded_geometry_and_its_limit() {
        val observations = observations()
        val report = report(observations)
        val data = RecordedDendrometryCalculator.calculate(report, observations)
        assertEquals(RecordedDendrometryStatus.AVAILABLE, data.status)
        assertEquals(0.18 * PI, data.basalAreaM2!!, 1e-12)
        assertEquals(sqrt(1800.0), data.quadraticDiameterCm!!, 1e-12)
        assertNull(report.volumeM3) // No configured tariff or manufactured height/pricing.
        content { DendrometryOverviewCard(data) }
        compose.onNodeWithTag("synthesis_dendrometry_overview").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.analysis_basal_value,
            String.format(Locale.getDefault(), "%.3f", 0.18 * PI))).assertExists()
        compose.onNodeWithText(context.getString(R.string.analysis_diameter_value,
            String.format(Locale.getDefault(), "%.1f", sqrt(1800.0)))).assertExists()
        compose.onNodeWithText(context.getString(R.string.analysis_valid_diameters, 4, 4)).assertExists()
        compose.onNodeWithText(context.getString(R.string.analysis_diameter_basis_unknown)).assertExists()
    }

    @Test
    fun basal_area_help_shows_its_formula_and_opens_sources_once() {
        val observations = observations()
        val data = RecordedDendrometryCalculator.calculate(report(observations), observations)
        var sourcesOpened = 0
        content { DendrometryOverviewCard(data, onShowSources = { sourcesOpened++ }) }

        compose.onNodeWithTag("synthesis_metric_help_basal").performScrollTo()
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).assertHasClickAction().performClick()
        compose.onNodeWithTag("synthesis_metric_help_sheet").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.analysis_help_basal_formula))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("synthesis_metric_help_sources").performScrollTo().assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals(1, sourcesOpened) }
        compose.onNodeWithTag("synthesis_metric_help_sheet").assertDoesNotExist()
    }

    @Test
    fun inconsistent_report_projection_shows_unavailable_instead_of_other_rows() {
        val observations = observations()
        val report = report(observations)
        val data = RecordedDendrometryCalculator.calculate(report, observations.dropLast(1))
        assertEquals(RecordedDendrometryStatus.REPORT_MISMATCH, data.status)
        content { DendrometryOverviewCard(data) }
        compose.onNodeWithText(context.getString(R.string.analysis_projection_unavailable)).assertIsDisplayed()
        compose.onNodeWithTag("synthesis_metric_help_basal").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.analysis_valid_diameters, 4, 4)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.analysis_diameter_value,
            String.format(Locale.getDefault(), "%.1f", sqrt(1800.0)))).assertDoesNotExist()
    }

    @Test
    fun distribution_exposes_actual_diameter_frequencies_to_accessibility() {
        val observations = observations()
        val report = report(observations)
        val data = RecordedDendrometryCalculator.calculate(report, observations)
        val expectedRows = listOf(20.0 to 1, 40.0 to 2, 60.0 to 1).map { (diameter, count) ->
            context.getString(R.string.analysis_frequency_row,
                String.format(Locale.getDefault(), "%.2f", diameter), count)
        }
        content { DiameterDistributionCard(data) }
        compose.onNodeWithContentDescription(context.getString(R.string.analysis_distribution_accessible,
            expectedRows.joinToString("; "))).assertIsDisplayed()
        expectedRows.forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText(context.getString(R.string.analysis_distribution_scope)).assertExists()
        compose.onNodeWithText(context.getString(R.string.analysis_distribution_axes)).assertExists()
    }

    @Test
    fun missing_or_zero_heights_keep_unqualified_ratios_unknown_and_explain_requirements() {
        val missing = report(observations().map { it.copy(hauteurM = null) })
        val zero = report(observations().map { it.copy(hauteurM = 0.0) })
        val recorded = report(observations())
        assertEquals(0, missing.measuredHeightCount)
        assertEquals(0, zero.measuredHeightCount)
        assertEquals(1, recorded.measuredHeightCount)
        var visibleHeightCount by mutableStateOf(missing.measuredHeightCount)
        content(width = 280.dp, fontScale = 2f) {
            var showHelp by remember { mutableStateOf(false) }
            UnqualifiedHeightIndicatorsCard(visibleHeightCount, missing.observationCount,
                onExplainMissing = { showHelp = true })
            if (showHelp) {
                HeightProtocolHelpDialog(visibleHeightCount, missing.observationCount, onDismiss = { showHelp = false })
            }
        }
        compose.onNodeWithTag("synthesis_hd_unavailable").assertExists()
        compose.onNodeWithTag("synthesis_spacing_unavailable").assertExists()
        compose.onAllNodesWithText(context.getString(R.string.analysis_unavailable)).assertCountEquals(2)
        compose.onNodeWithText(context.getString(R.string.analysis_height_coverage, 0, 4)).assertExists()
        assertTextLayoutsDoNotOverflow()
        compose.onNodeWithText(context.getString(R.string.analysis_explain_measurements))
            .performScrollTo().assertHasClickAction().performClick()
        compose.onNodeWithText(context.getString(R.string.analysis_protocol_help_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.analysis_protocol_help_paired)).assertExists()
        compose.onNodeWithText(context.getString(R.string.analysis_protocol_help_stand)).assertExists()
        compose.onNodeWithText(context.getString(R.string.analysis_protocol_help_convention)).assertExists()
        assertTextLayoutsDoNotOverflow()
        compose.onNodeWithText(context.getString(R.string.analysis_protocol_help_close)).performClick()
        compose.runOnIdle { visibleHeightCount = recorded.measuredHeightCount }
        compose.onNodeWithText(context.getString(R.string.analysis_height_coverage, 1, 4)).assertExists()
        compose.onAllNodesWithText(context.getString(R.string.analysis_unavailable)).assertCountEquals(2)
        assertTextLayoutsDoNotOverflow()
    }

    @Test
    fun wood_and_ecology_cards_use_actual_coverage_and_descriptive_categories() {
        val report = report(observations())
        assertEquals(0, report.qualityCount)
        assertEquals(0, report.locatedCount)
        assertNull(report.enteredValueEur)
        content {
            Column {
                WoodQualityCoverageCard(report)
                EcologicalObservationCard(report)
            }
        }
        compose.onNodeWithTag("synthesis_wood_quality").assertExists()
        compose.onNodeWithTag("synthesis_ecological_observations").assertExists()
        compose.onAllNodesWithText(context.getString(R.string.analysis_coverage_value, 0, 4)).assertCountEquals(2)
        compose.onNodeWithText(context.getString(R.string.analysis_products_missing)).assertExists()
        compose.onNodeWithText(context.getString(R.string.analysis_category_count,
            context.getString(R.string.special_tree_bio), 1)).assertExists()
        compose.onNodeWithText(context.getString(R.string.analysis_category_count,
            context.getString(R.string.special_tree_dead), 1)).assertExists()
        compose.onNodeWithText(context.getString(R.string.analysis_station_missing)).assertExists()
        compose.onNodeWithText(context.getString(R.string.analysis_ecology_limit)).assertExists()
    }

    private fun content(width: Dp = 320.dp, fontScale: Float = 1f, body: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme {
                    Box(Modifier.width(width).heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                        body()
                    }
                }
            }
        }
    }

    private fun assertTextLayoutsDoNotOverflow() {
        val matcher = SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)
        val nodes = compose.onAllNodes(matcher, useUnmergedTree = true)
        val count = nodes.fetchSemanticsNodes().size
        assertTrue("The real component must expose rendered text layouts", count > 0)
        for (index in 0 until count) {
            val layouts = mutableListOf<TextLayoutResult>()
            nodes[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getLayouts ->
                assertTrue(getLayouts(layouts))
            }
            assertFalse("Rendered text must wrap at fontScale 2: ${layouts.map { "${it.layoutInput.text}: size=${it.size}, paragraph=${it.multiParagraph.width}x${it.multiParagraph.height}, widthOverflow=${it.didOverflowWidth}, heightOverflow=${it.didOverflowHeight}, constraints=${it.layoutInput.constraints}" }}",
                layouts.any { it.hasVisualOverflow })
        }
    }

    private fun sectionTag(section: SynthesisSection) =
        "synthesis_section_" + section.name.lowercase(Locale.ROOT)

    private fun observations(): List<Tige> = listOf(20.0, 40.0, 40.0, 60.0).mapIndexed { index, diameter ->
        Tige(id = "native-observation-$index", parcelleId = "native-parcel", placetteId = "native-plot",
            essenceCode = "CH_SESSILE", diamCm = diameter, hauteurM = if (index == 1) 20.0 else null,
            gpsWkt = null, precisionM = null, altitudeM = null, timestamp = 1000L + index,
            note = null, produit = null, fCoef = null, valueEur = null, qualite = null,
            categorie = when (index) { 0 -> "ARBRE_BIO"; 2 -> "MORT"; else -> null },
            sessionId = "native-session", source = "public-native-fixture")
    }

    private fun report(observations: List<Tige>): SynthesisReport {
        val parcel = Parcelle(id = "native-parcel", forestId = "native-forest", name = "Native fixture",
            surfaceHa = 2.0, shape = null, slopePct = null, aspect = null, access = null,
            altitudeM = null, objectifType = null, objectifVal = null, tolerancePct = null,
            samplingMode = null, sampleAreaM2 = null, targetSpeciesCsv = null, srid = null, remarks = null)
        val plot = Placette("native-plot", parcel.id, "Native fixture plot", "ENTIRE_PARCEL", null, 20_000.0, null)
        return SynthesisReportCalculator.calculate(parcel, listOf(plot), observations,
            mapOf("CH_SESSILE" to "Oak"), SynthesisParameters(selectedPlotId = plot.id),
            "native-session", "native-campaign", 1, 2000L)
    }
}
