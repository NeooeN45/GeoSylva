package com.forestry.counter.presentation.utils

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeightInputUtilsTest {
    @Test
    fun should_return_no_height_for_empty_or_whitespace_input() {
        listOf("", " ", "\t\r\n", "\u00a0\u2003\u202f").forEach(::assertUnavailable)
    }

    @Test
    fun should_treat_a_decimal_comma_as_one_height() {
        assertMean("20,5", 20.5, 1)
        assertMean(" 18,75 ", 18.75, 1)
    }

    @Test
    fun should_accept_a_decimal_point_as_one_height() {
        assertMean("20.5", 20.5, 1)
        assertMean("18.75", 18.75, 1)
    }

    @Test
    fun should_average_explicitly_separated_heights_with_both_decimal_formats() {
        assertMean("18,5;20;21.5", 20.0, 3)
        assertMean("18.5 20 21,5", 20.0, 3)
        assertMean("18,5; 20 21.5", 20.0, 3)
    }

    @Test
    fun should_accept_line_breaks_and_tabs_between_heights() {
        assertMean("18,5\t20\n21.5\r\n", 20.0, 3)
    }

    @Test
    fun should_accept_unicode_spaces_pasted_from_a_document() {
        assertMean("\u00a018,5\u202f20\u200321.5\u00a0", 20.0, 3)
    }

    @Test
    fun should_accept_spaces_around_semicolon_separators() {
        assertMean("\t18,5 \u00a0;\n20 ; 21.5\t", 20.0, 3)
    }

    @Test
    fun should_keep_the_existing_height_range_inclusive() {
        assertMean("0,5;80.0", 40.25, 2)
        assertMean(".5", 0.5, 1)
        assertMean(",5", 0.5, 1)
    }

    @Test
    fun should_reject_an_entire_list_when_one_height_is_outside_the_existing_range() {
        listOf("0.49", "80.01", "0", "-1", "20;0.49;21", "20 80.01 21")
            .forEach(::assertUnavailable)
    }

    @Test
    fun should_reject_ambiguous_legacy_comma_lists() {
        listOf("25,20,27", "25,20,27;30", "18;25,20,27", "25, 20, 27")
            .forEach(::assertUnavailable)
    }

    @Test
    fun should_reject_tokens_with_multiple_decimal_separators() {
        listOf("20,5,1", "20.5.1", "20,5.1", "20.5,1", "18;20,5.1;21")
            .forEach(::assertUnavailable)
    }

    @Test
    fun should_never_drop_invalid_text_from_an_otherwise_valid_list() {
        listOf("20;inconnu;21", "20 m 21", "20;21m", "18;?;21", "inconnu")
            .forEach(::assertUnavailable)
    }

    @Test
    fun should_reject_nonfinite_values_without_keeping_the_other_heights() {
        listOf("NaN", "Infinity", "-Infinity", "20;NaN;21", "20 Infinity 21", "1e309")
            .forEach(::assertUnavailable)
    }

    @Test
    fun should_reject_a_missing_height_around_a_semicolon() {
        listOf(";20", "20;", "20;;21", "20; ;21", ";", "20,", "20.")
            .forEach(::assertUnavailable)
    }

    @Test
    fun should_parse_the_same_heights_regardless_of_the_phone_locale() {
        val previous = Locale.getDefault()
        try {
            listOf(Locale.FRANCE, Locale.US, Locale.GERMANY).forEach { locale ->
                Locale.setDefault(locale)
                assertMean("18,5;20;21.5", 20.0, 3)
                assertMean("20,5", 20.5, 1)
                assertUnavailable("25,20,27")
            }
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun should_block_validation_when_one_class_is_valid_and_another_is_invalid() {
        assertTrue(hasInvalidHeightInput(listOf("18,5;20;21", "25,20,27")))
        assertTrue(hasInvalidHeightInput(listOf("20", "hauteur inconnue")))
        assertTrue(hasInvalidHeightInput(listOf("20", "81")))
    }

    @Test
    fun should_allow_valid_classes_and_leave_blank_classes_unknown() {
        assertFalse(hasInvalidHeightInput(listOf("18,5;20;21", "", "\u00a0", "25.5")))
        assertFalse(hasInvalidHeightInput(listOf("", " ", "\t\n")))
        assertFalse(hasInvalidHeightInput(emptyList()))
    }

    @Test
    fun should_report_an_invalid_class_regardless_of_its_position() {
        assertTrue(hasInvalidHeightInput(listOf("0", "20", "21")))
        assertTrue(hasInvalidHeightInput(listOf("20", "0", "21")))
        assertTrue(hasInvalidHeightInput(listOf("20", "21", "0")))
    }

    private fun assertMean(input: String, expectedMean: Double, expectedCount: Int) {
        val (mean, count) = parseHeightInputMean(input)
        assertNotNull(input, mean)
        assertEquals(input, expectedMean, requireNotNull(mean), 1e-12)
        assertEquals(input, expectedCount, count)
    }

    private fun assertUnavailable(input: String) {
        assertEquals(input, null to 0, parseHeightInputMean(input))
    }
}
