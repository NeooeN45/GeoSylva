package com.forestry.counter.domain.calculation

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Tests pour EssenceAliases — source unique de vérité pour la résolution des codes d'essence.
 * C-PRIX-1 : unifier les alias entre ForestryCalculator et TarifCalculator.
 */
class EssenceAliasesTest {

    @Test
    fun `candidates should return input as first element`() {
        val result = EssenceAliases.candidates("CHENE")
        assertEquals("CHENE", result.first())
    }

    @Test
    fun `candidates should resolve CHENE to sessile and pedoncule`() {
        val result = EssenceAliases.candidates("CHENE")
        assertTrue("Should contain CH_SESSILE", result.contains("CH_SESSILE"))
        assertTrue("Should contain CH_PEDONCULE", result.contains("CH_PEDONCULE"))
    }

    @Test
    fun `candidates should resolve HETRE to HETRE_COMMUN`() {
        val result = EssenceAliases.candidates("HETRE")
        assertTrue("Should contain HETRE_COMMUN", result.contains("HETRE_COMMUN"))
    }

    @Test
    fun `candidates should resolve reverse alias HETRE_COMMUN to HETRE`() {
        val result = EssenceAliases.candidates("HETRE_COMMUN")
        assertTrue("Should contain HETRE", result.contains("HETRE"))
    }

    @Test
    fun `candidates should resolve DOUGLAS to DOUGLAS_VERT`() {
        val result = EssenceAliases.candidates("DOUGLAS")
        assertTrue("Should contain DOUGLAS_VERT", result.contains("DOUGLAS_VERT"))
    }

    @Test
    fun `candidates should resolve PIN to multiple pine species`() {
        val result = EssenceAliases.candidates("PIN")
        assertTrue("Should contain PIN_SYLVESTRE", result.contains("PIN_SYLVESTRE"))
        assertTrue("Should contain PIN_MARITIME", result.contains("PIN_MARITIME"))
        assertTrue("Should contain PIN_NOIR_AUTR", result.contains("PIN_NOIR_AUTR"))
        assertTrue("Should contain PIN_LARICIO", result.contains("PIN_LARICIO"))
    }

    @Test
    fun `candidates should resolve SAPIN to SAPIN_PECTINE`() {
        val result = EssenceAliases.candidates("SAPIN")
        assertTrue("Should contain SAPIN_PECTINE", result.contains("SAPIN_PECTINE"))
    }

    @Test
    fun `candidates should resolve EPICEA to EPICEA_COMMUN`() {
        val result = EssenceAliases.candidates("EPICEA")
        assertTrue("Should contain EPICEA_COMMUN", result.contains("EPICEA_COMMUN"))
    }

    @Test
    fun `candidates should handle unknown code by returning only the input`() {
        val result = EssenceAliases.candidates("UNKNOWN_ESSENCE")
        assertEquals(1, result.size)
        assertEquals("UNKNOWN_ESSENCE", result.first())
    }

    @Test
    fun `candidates should handle lowercase input by uppercasing`() {
        val result = EssenceAliases.candidates("hetre")
        assertTrue("Should resolve lowercase hetre", result.contains("HETRE"))
        assertTrue("Should contain HETRE_COMMUN", result.contains("HETRE_COMMUN"))
    }

    @Test
    fun `candidates should handle whitespace by trimming`() {
        val result = EssenceAliases.candidates("  CHENE  ")
        assertEquals("CHENE", result.first())
        assertTrue(result.contains("CH_SESSILE"))
    }

    @Test
    fun `candidates should resolve TREMBLE and PEUPLIER_TREMB bidirectionally`() {
        val fromTremble = EssenceAliases.candidates("TREMBLE")
        assertTrue(fromTremble.contains("PEUPLIER_TREMB"))

        val fromPeuplier = EssenceAliases.candidates("PEUPLIER_TREMB")
        assertTrue(fromPeuplier.contains("TREMBLE"))
    }

    @Test
    fun `candidates should resolve FRENE to multiple ash species`() {
        val result = EssenceAliases.candidates("FRENE")
        assertTrue("Should contain FRENE_ELEVE", result.contains("FRENE_ELEVE"))
        assertTrue("Should contain FRENE_OXYPHYLLE", result.contains("FRENE_OXYPHYLLE"))
        assertTrue("Should contain FRENE_FLEURS", result.contains("FRENE_FLEURS"))
    }

    @Test
    fun `candidates should resolve ERABLE to multiple maple species`() {
        val result = EssenceAliases.candidates("ERABLE")
        assertTrue("Should contain ERABLE_SYC", result.contains("ERABLE_SYC"))
        assertTrue("Should contain ERABLE_PLANE", result.contains("ERABLE_PLANE"))
        assertTrue("Should contain ERABLE_CHAMP", result.contains("ERABLE_CHAMP"))
    }
}
