package com.forestry.counter.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forestry.counter.R
import com.forestry.counter.data.preferences.ThemeMode
import com.forestry.counter.domain.field.*
import com.forestry.counter.domain.wood.WoodObjectKind
import com.forestry.counter.presentation.screens.field.*
import com.forestry.counter.presentation.screens.wood.PileMeasurementForm
import com.forestry.counter.presentation.screens.wood.WoodManualForm
import com.forestry.counter.presentation.theme.ForestryCounterTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

/** Presentation tests only. No repository, user data, fake screenshot or domain calculation replacement. */
@RunWith(AndroidJUnit4::class)
class RetainedBusinessErgonomicsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var restoreWindow: (() -> Unit)? = null
    @After fun restore() { restoreWindow?.invoke() }

    @Test fun parcelHelpStaysInItsActionRowAndDoesNotTriggerNavigation() {
        var opened = 0
        render {
            RetainedActionRow(Icons.Default.Map, text(R.string.retained_parcels), "2 · TEST A, TEST B",
                modifier = Modifier.testTag("TEST-parcel-line"), help = text(R.string.retained_parcels_help)) { opened++ }
        }
        val help = compose.onNodeWithContentDescription(compose.activity.getString(R.string.retained_explain,
            compose.activity.getString(R.string.retained_parcels)))
        assertAnchored48dp(help, "TEST-parcel-line")
        help.performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.retained_parcels_help)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, opened) }
        compose.onNodeWithText(compose.activity.getString(R.string.retained_close)).performClick()
        compose.onNodeWithTag("TEST-parcel-line").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun dateHelpStaysBesideBothCompleteDatesWithItsOwn48dpTarget() {
        val entry = entry(1)
        render { JournalEntryDates(entry) }
        val help = compose.onNodeWithTag("journal_dates_help")
        assertAnchored48dp(help, "journal_entry_dates")
        help.performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.retained_time_help)).assertIsDisplayed()
    }

    @Test fun realPilePreviewKeepsEssentialReserveVisibleAndItsFullReserveInOwn48dpHelp() {
        val form = WoodManualForm(WoodObjectKind.PILE, name = "TEST · Pile", length = "10", depth = "2",
            height = "1,5", shape = true, protocol = "TEST enveloppe mesurée")
        var changes = 0
        render {
            Box(Modifier.width(411.dp).height(620.dp).testTag("TEST-viewport")) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    PileMeasurementForm(form, true) { changes++; this }
                }
            }
        }
        val viewport = compose.onNodeWithTag("TEST-viewport").fetchSemanticsNode().boundsInRoot
        val preview = compose.onNodeWithTag("wood_pile_preview").assertIsDisplayed().fetchSemanticsNode()
        val reserve = compose.onNodeWithTag("wood_calculation_essential_reserve")
            .assertIsDisplayed().fetchSemanticsNode()
        compose.onNodeWithText("30 m³").assertIsDisplayed()
        assertTrue("The complete preview must be in the first viewport", preview.positionInRoot.y + preview.size.height <= viewport.bottom + 1f)
        assertTrue("The complete essential scientific reserve must be visible", reserve.positionInRoot.y + reserve.size.height <= viewport.bottom + 1f)
        assertTrue(preview.boundsInRoot.top < compose.onNodeWithTag("wood_method_panel").fetchSemanticsNode().boundsInRoot.top)
        val reserveHelp = compose.onNodeWithTag("wood_calculation_reserve_help").fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        assertTrue(reserveHelp.width >= 48f * density && reserveHelp.height >= 48f * density)
        compose.onNodeWithTag("wood_calculation_reserve_help").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.wo_no_payable)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.retained_close)).performClick()
        compose.onNodeWithTag("wood_pile_intro_help").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.wo_measure_intro)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, changes) }
    }

    @Test fun remainingControlExplainsNoStockAdjustmentBeforeItsEditableQuantity() {
        val form = FieldEntryForm(kind = FieldEntryKind.REMAINING, title = "TEST contrôle", quantity = "24",
            unit = FieldQuantityUnit.APPARENT_M3, convention = "TEST apparent", remainder = FieldRemainder.PARTIAL)
        var changes = 0
        render {
            Column(Modifier.width(411.dp).height(620.dp).verticalScroll(rememberScrollState())) {
                FieldQuantityEditor(form, emptyList(), true, false, { changes++; this }, photos = {})
            }
        }
        val warning = compose.onNodeWithTag("field_control_no_adjustment").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val quantity = compose.onNodeWithTag("field_quantity").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(warning.bottom < quantity.top)
        compose.runOnIdle { assertEquals(0, changes); assertEquals("24", form.quantity) }
    }

    @Test fun activeHistoryEntryIsReadFirstDespiteTiedRecordedDatesAndOriginalRemainsOpenable() {
        val original = entry(1)
        val current = entry(2, original.form.copy(notes = "TEST corrected", correctionOf = original.id, correctionReason = "TEST reason"))
        val entries = listOf(original, current)
        var opened: String? = null
        render { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            JournalHistory(entries, current.id, setOf(current.id)) { opened = it }
        } }
        val active = compose.onNodeWithTag("journal_history_${current.id}").fetchSemanticsNode().boundsInRoot
        val old = compose.onNodeWithTag("journal_history_${original.id}").fetchSemanticsNode().boundsInRoot
        assertTrue(active.top < old.top)
        compose.onNode(hasText(compose.activity.getString(R.string.retained_consult_entry)) and
            hasAnyAncestor(hasTestTag("journal_history_${original.id}"))).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(original.id, opened)
            assertEquals(listOf(original, current), entries)
        }
    }

    private fun render(content: @Composable () -> Unit) {
        restoreWindow = useFrenchMapTestWindow(compose.activity)
        compose.setContent { ForestryCounterTheme(themeMode = ThemeMode.LIGHT, dynamicColor = false) { content() } }
    }
    @Composable private fun text(resource: Int) = androidx.compose.ui.res.stringResource(resource)
    private fun entry(id: Long, form: FieldEntryForm = FieldEntryForm(title = "TEST entry", notes = "TEST original",
        observed = "02/11/2026 10:15", zone = "Europe/Paris")) = FieldEntry(UUID(0, id).toString(),
        FieldTarget(FieldTargetKind.WORKSITE, UUID(0, 99).toString()), form, Instant.parse("2026-11-02T10:20:00Z").toEpochMilli(), "TEST")

    private fun assertAnchored48dp(help: SemanticsNodeInteraction, owner: String) {
        val bounds = help.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val row = compose.onNodeWithTag(owner).fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        assertTrue(bounds.width + .5f >= 48f * density)
        assertTrue(bounds.height + .5f >= 48f * density)
        assertTrue(bounds.top >= row.top && bounds.bottom <= row.bottom)
    }
}
