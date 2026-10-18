package com.forestry.counter.presentation.screens.forestry

import com.forestry.counter.domain.model.SurveyContextDraft
import com.forestry.counter.domain.model.SurveyPopulationScope
import com.forestry.counter.domain.model.SurveyPurpose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SurveyContextFormStateTest {
    @Test
    fun empty_form_keeps_every_context_fact_unknown() {
        val draft = SurveyContextFormState().toDraft()
        assertEquals(SurveyContextDraft(), draft)
        assertNull(draft?.purpose)
        assertNull(draft?.populationScope)
        assertNull(draft?.inventoryComplete)
        assertNull(draft?.inventoriedHorizontalAreaM2)
    }

    @Test
    fun choosing_any_purpose_does_not_invent_population_protocol_or_completeness() {
        SurveyPurpose.entries.forEach { purpose ->
            val draft = requireNotNull(SurveyContextFormState(purpose = purpose).toDraft())
            assertEquals(purpose, draft.purpose)
            assertNull(draft.populationScope)
            assertNull(draft.populationReference)
            assertNull(draft.protocolReference)
            assertNull(draft.inventoryComplete)
            assertNull(draft.inventoriedHorizontalAreaM2)
        }
    }

    @Test
    fun unknown_incomplete_and_complete_remain_distinct_for_every_population() {
        (SurveyPopulationScope.entries + listOf(null)).forEach { population ->
            listOf(null, false, true).forEach { completeness ->
                val draft = requireNotNull(SurveyContextFormState(
                    populationScope = population,
                    inventoryComplete = completeness,
                ).toDraft())
                assertEquals(population, draft.populationScope)
                assertEquals(completeness, draft.inventoryComplete)
                assertNull(draft.purpose)
                assertNull(draft.populationReference)
            }
        }
    }

    @Test
    fun a_partial_draft_and_all_raw_reference_characters_roundtrip_without_requalification() {
        val draft = SurveyContextDraft(
            purpose = SurveyPurpose.COMBINED,
            populationReference = "  TEST-population\n",
            populationScope = SurveyPopulationScope.MARKED_STEMS,
            protocolReference = "TEST-protocole",
            protocolVersion = " TEST-version ",
            sourceReference = " TEST-source ",
            inventoryComplete = false,
            inventoriedHorizontalAreaM2 = 1250.5,
            areaSourceReference = " TEST-source-surface ",
        )
        assertEquals(draft, SurveyContextFormState.fromDraft(draft).toDraft())
    }

    @Test
    fun extreme_positive_finite_inherited_areas_roundtrip_without_a_scientific_notation_error() {
        listOf(Double.MIN_VALUE, 0.000001, 1.0, 123456789.25, Double.MAX_VALUE).forEach { area ->
            val draft = SurveyContextDraft(inventoriedHorizontalAreaM2 = area)
            val form = SurveyContextFormState.fromDraft(draft)
            assertFalse(form.inventoriedHorizontalAreaInput.contains('E', ignoreCase = true))
            assertTrue(form.validation().isValid)
            assertEquals(draft, form.toDraft())
        }
    }

    @Test
    fun blank_references_and_area_stay_absent_instead_of_becoming_zero_or_a_default() {
        val form = SurveyContextFormState(
            populationReference = " \t\n",
            protocolReference = " ",
            protocolVersion = "\n",
            sourceReference = "\t",
            inventoriedHorizontalAreaInput = " \n",
            areaSourceReference = " ",
        )
        assertTrue(form.validation().isValid)
        assertEquals(SurveyContextDraft(), form.toDraft())
    }

    @Test
    fun even_long_blank_input_stays_unknown_instead_of_becoming_a_reference_length_error() {
        val blank = " ".repeat(301)
        val form = SurveyContextFormState(
            populationReference = blank,
            protocolReference = blank,
            protocolVersion = blank,
            sourceReference = blank,
            inventoriedHorizontalAreaInput = blank,
            areaSourceReference = blank,
        )
        assertTrue(form.validation().isValid)
        assertEquals(SurveyContextDraft(), form.toDraft())
    }

    @Test
    fun decimal_comma_and_decimal_dot_are_both_square_metres_without_conversion() {
        listOf("1250,5", "1250.5", " 1250,5 ").forEach { input ->
            val form = SurveyContextFormState(inventoriedHorizontalAreaInput = input)
            assertTrue(form.validation().isValid)
            assertEquals(1250.5, requireNotNull(form.toDraft()?.inventoriedHorizontalAreaM2), 0.0)
            assertNull(form.toDraft()?.areaSourceReference)
        }
        assertEquals(0.5, requireNotNull(parseInventoriedHorizontalArea(",5")), 0.0)
        assertEquals(0.5, requireNotNull(parseInventoriedHorizontalArea(".5")), 0.0)
    }

    @Test
    fun zero_negative_nonfinite_and_ambiguous_area_inputs_block_the_whole_draft() {
        listOf("0", "0,0", "-0", "-1", "NaN", "Infinity", "1e3", "1 000",
            "1,2.3", "1;2", "12 m²", "1,", "+1", "9".repeat(310), "0." + "0".repeat(330) + "1")
            .forEach { input ->
                val form = SurveyContextFormState(
                    purpose = SurveyPurpose.INVENTORY,
                    inventoriedHorizontalAreaInput = input,
                )
                assertTrue(input, form.validation().invalidHorizontalArea)
                assertFalse(input, form.validation().isValid)
                assertNull(input, form.toDraft())
            }
    }

    @Test
    fun reference_limits_are_300_and_protocol_version_limit_is_100() {
        val atLimit = SurveyContextFormState(
            populationReference = "p".repeat(300),
            protocolReference = "r".repeat(300),
            protocolVersion = "v".repeat(100),
            sourceReference = "s".repeat(300),
            areaSourceReference = "a".repeat(300),
        )
        assertTrue(atLimit.validation().isValid)
        assertEquals(300, atLimit.toDraft()?.populationReference?.length)
        assertEquals(100, atLimit.toDraft()?.protocolVersion?.length)
        assertTrue(atLimit.copy(populationReference = "p".repeat(301)).validation().populationReferenceTooLong)
        assertTrue(atLimit.copy(protocolReference = "r".repeat(301)).validation().protocolReferenceTooLong)
        assertTrue(atLimit.copy(protocolVersion = "v".repeat(101)).validation().protocolVersionTooLong)
        assertTrue(atLimit.copy(sourceReference = "s".repeat(301)).validation().sourceReferenceTooLong)
        assertTrue(atLimit.copy(areaSourceReference = "a".repeat(301)).validation().areaSourceReferenceTooLong)
    }

    @Test
    fun an_invalid_reference_does_not_drop_the_value_and_save_the_other_fields() {
        val original = SurveyContextFormState(
            purpose = SurveyPurpose.MARKING,
            inventoriedHorizontalAreaInput = "250,5",
            areaSourceReference = "a".repeat(301),
        )
        assertNull(original.toDraft())
        assertEquals("a".repeat(301), original.areaSourceReference)
        val corrected = original.copy(areaSourceReference = "TEST-source")
        assertEquals(250.5, requireNotNull(corrected.toDraft()?.inventoriedHorizontalAreaM2), 0.0)
    }

    @Test
    fun a_supplied_area_does_not_assert_a_source_or_inventory_completeness() {
        val draft = requireNotNull(SurveyContextFormState(
            inventoriedHorizontalAreaInput = "10000",
        ).toDraft())
        assertNull(draft.areaSourceReference)
        assertNull(draft.populationScope)
        assertNull(draft.populationReference)
        assertNull(draft.inventoryComplete)
        assertNull(draft.protocolReference)
    }

    @Test
    fun removing_an_existing_declaration_restores_unknown_values_without_reusing_the_initial_draft() {
        val form = SurveyContextFormState.fromDraft(SurveyContextDraft(
            purpose = SurveyPurpose.INVENTORY,
            populationScope = SurveyPopulationScope.ALL_STEMS,
            inventoryComplete = true,
            inventoriedHorizontalAreaM2 = 250.0,
            areaSourceReference = "TEST-source",
        )).copy(
            purpose = null,
            populationScope = null,
            inventoryComplete = null,
            inventoriedHorizontalAreaInput = "",
            areaSourceReference = "",
        )
        assertEquals(SurveyContextDraft(), form.toDraft())
    }
}
