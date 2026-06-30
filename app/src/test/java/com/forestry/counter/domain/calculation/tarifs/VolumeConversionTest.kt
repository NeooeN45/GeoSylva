package com.forestry.counter.domain.calculation.tarifs

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse

/**
 * Tests pour VolumeConversion — conversion stère ↔ m³ (H-PRIX-1).
 */
class VolumeConversionTest {

    @Test
    fun `stereToM3 should convert hardwood with 0_7 coefficient`() {
        val result = VolumeConversion.stereToM3(10.0, "CHENE")
        assertEquals(7.0, result, 0.001)
    }

    @Test
    fun `stereToM3 should convert softwood with 0_65 coefficient`() {
        val result = VolumeConversion.stereToM3(10.0, "SAPIN")
        assertEquals(6.5, result, 0.001)
    }

    @Test
    fun `stereToM3 should handle douglas as softwood`() {
        val result = VolumeConversion.stereToM3(10.0, "DOUGLAS_VERT")
        assertEquals(6.5, result, 0.001)
    }

    @Test
    fun `stereToM3 should handle pin as softwood`() {
        val result = VolumeConversion.stereToM3(10.0, "PIN_SYLVESTRE")
        assertEquals(6.5, result, 0.001)
    }

    @Test
    fun `stereToM3 should handle hetre as hardwood`() {
        val result = VolumeConversion.stereToM3(10.0, "HETRE_COMMUN")
        assertEquals(7.0, result, 0.001)
    }

    @Test
    fun `stereToM3 should return zero for zero input`() {
        val result = VolumeConversion.stereToM3(0.0, "CHENE")
        assertEquals(0.0, result, 0.001)
    }

    @Test
    fun `stereToM3 should return zero for negative input`() {
        val result = VolumeConversion.stereToM3(-5.0, "CHENE")
        assertEquals(0.0, result, 0.001)
    }

    @Test
    fun `m3ToStere should convert hardwood with 0_7 coefficient`() {
        val result = VolumeConversion.m3ToStere(7.0, "CHENE")
        assertEquals(10.0, result, 0.001)
    }

    @Test
    fun `m3ToStere should convert softwood with 0_65 coefficient`() {
        val result = VolumeConversion.m3ToStere(6.5, "SAPIN")
        assertEquals(10.0, result, 0.001)
    }

    @Test
    fun `m3ToStere should return zero for zero input`() {
        val result = VolumeConversion.m3ToStere(0.0, "CHENE")
        assertEquals(0.0, result, 0.001)
    }

    @Test
    fun `isConifer should return true for pin`() {
        assertTrue(VolumeConversion.isConifer("PIN_SYLVESTRE"))
    }

    @Test
    fun `isConifer should return true for sapin`() {
        assertTrue(VolumeConversion.isConifer("SAPIN_PECTINE"))
    }

    @Test
    fun `isConifer should return true for epicea`() {
        assertTrue(VolumeConversion.isConifer("EPICEA_COMMUN"))
    }

    @Test
    fun `isConifer should return true for douglas`() {
        assertTrue(VolumeConversion.isConifer("DOUGLAS_VERT"))
    }

    @Test
    fun `isConifer should return false for chene`() {
        assertFalse(VolumeConversion.isConifer("CH_SESSILE"))
    }

    @Test
    fun `isConifer should return false for hetre`() {
        assertFalse(VolumeConversion.isConifer("HETRE_COMMUN"))
    }

    @Test
    fun `isConifer should handle lowercase input`() {
        assertTrue(VolumeConversion.isConifer("sapin"))
        assertFalse(VolumeConversion.isConifer("chene"))
    }

    @Test
    fun `conversionFactor should return 0_7 for hardwood`() {
        assertEquals(0.7, VolumeConversion.conversionFactor("CHENE"), 0.001)
    }

    @Test
    fun `conversionFactor should return 0_65 for softwood`() {
        assertEquals(0.65, VolumeConversion.conversionFactor("SAPIN"), 0.001)
    }

    @Test
    fun `roundtrip stere to m3 to stere should preserve original value`() {
        val original = 15.0
        val m3 = VolumeConversion.stereToM3(original, "CHENE")
        val back = VolumeConversion.m3ToStere(m3, "CHENE")
        assertEquals(original, back, 0.001)
    }
}
