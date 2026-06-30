package com.forestry.counter.domain.usecase.export

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Tests pour CurrencyFormatter — formatage monétaire i18n (I-C3).
 */
class CurrencyFormatterTest {

    @Test
    fun `format should not throw on valid value`() {
        val result = CurrencyFormatter.format(1500.0)
        assertTrue("Result should not be empty", result.isNotEmpty())
    }

    @Test
    fun `format should handle zero`() {
        val result = CurrencyFormatter.format(0.0)
        assertTrue("Result should contain a digit", result.any { it.isDigit() })
    }

    @Test
    fun `format should handle negative value`() {
        val result = CurrencyFormatter.format(-100.0)
        assertTrue("Result should not be empty", result.isNotEmpty())
    }

    @Test
    fun `format should handle large value`() {
        val result = CurrencyFormatter.format(1_000_000.0)
        assertTrue("Result should not be empty", result.isNotEmpty())
    }

    @Test
    fun `format should handle NaN gracefully`() {
        val result = CurrencyFormatter.format(Double.NaN)
        assertTrue("Result should not crash", result.isNotEmpty())
    }

    @Test
    fun `format should handle infinity gracefully`() {
        val result = CurrencyFormatter.format(Double.POSITIVE_INFINITY)
        assertTrue("Result should not crash", result.isNotEmpty())
    }

    @Test
    fun `symbol should not be empty`() {
        assertTrue("Symbol should not be empty", CurrencyFormatter.symbol.isNotEmpty())
    }

    @Test
    fun `symbol should be euro sign or localized variant`() {
        val symbol = CurrencyFormatter.symbol
        assertTrue(
            "Symbol should be € or a localized euro symbol, got: $symbol",
            symbol == "€" || symbol == "EUR" || symbol.contains("€") || symbol.contains("EUR")
        )
    }
}
