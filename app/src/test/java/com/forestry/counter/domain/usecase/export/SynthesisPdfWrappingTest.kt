package com.forestry.counter.domain.usecase.export

import org.junit.Assert.*
import org.junit.Test

class SynthesisPdfWrappingTest {
    private fun wrap(text: String, maximum: Int) = wrapPdfText(text, maximum.toFloat()) { value, width -> minOf(value.length, width.toInt()) }
    @Test fun `long paragraphs wrap without losing words`() {
        val words = "Oak beech birch fir pine spruce larch ash walnut"
        val lines = wrap(words, 14)
        assertTrue(lines.size > 1)
        assertTrue(lines.all { it.length <= 14 })
        assertEquals(words, lines.joinToString(" "))
    }
    @Test fun `unbroken identifiers are wrapped completely`() {
        val fingerprint = "a".repeat(64)
        val lines = wrap(fingerprint, 13)
        assertEquals(fingerprint, lines.joinToString(""))
        assertTrue(lines.all { it.length <= 13 })
    }
    @Test fun `paragraph breaks remain visible`() {
        assertEquals(listOf("first", "", "last"), wrap("first\n\nlast", 20))
    }
    @Test fun `a fitting surrogate pair is never split at a line end`() {
        val text = "abc🌲def"
        val lines = wrap(text, 4)
        assertEquals(text, lines.joinToString(""))
        assertFalse(lines.any { it.lastOrNull()?.let(Character::isHighSurrogate) == true })
    }
    @Test fun `tiny width still progresses`() {
        assertEquals(listOf("a", "b", "c"), wrapPdfText("abc", 0f) { _, _ -> 0 })
    }
}
