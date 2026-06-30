package com.forestry.counter.domain.calculation.tarifs

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Tests pour DecoupeCalculator.ventilerParProduit — ventilation du volume par produit (C-PRIX-2/3).
 */
class DecoupeCalculatorTest {

    @Test
    fun `ventilerParProduit should return empty map for zero volume`() {
        val result = DecoupeCalculator.ventilerParProduit(0.0, "CHENE", null, 50.0)
        assertTrue("Should return empty map for zero volume", result.isEmpty())
    }

    @Test
    fun `ventilerParProduit should return empty map for negative volume`() {
        val result = DecoupeCalculator.ventilerParProduit(-1.0, "CHENE", null, 50.0)
        assertTrue("Should return empty map for negative volume", result.isEmpty())
    }

    @Test
    fun `ventilerParProduit should return BO-dominant for large diameter with feuillu fallback`() {
        // D=50 with null categorie → fallback to feuillu rules (D 40-999 → BO 55%)
        val result = DecoupeCalculator.ventilerParProduit(2.0, "UNKNOWN", null, 50.0)
        assertTrue("Should contain BO for D=50 feuillu fallback", result.containsKey("BO"))
        assertTrue("BO should be dominant (55%)", result["BO"]!! > result.values.filter { it != result["BO"] }.sum())
    }

    @Test
    fun `ventilerParProduit should return BI for medium diameter with feuillu fallback`() {
        // D=25 with null categorie → fallback to feuillu rules (D 7-39 → BI)
        val result = DecoupeCalculator.ventilerParProduit(0.5, "UNKNOWN", null, 25.0)
        assertTrue("Should contain BI for D=25 feuillu fallback", result.containsKey("BI"))
    }

    @Test
    fun `ventilerParProduit should split volume into multiple products`() {
        val result = DecoupeCalculator.ventilerParProduit(10.0, "CHENE", "feuillu", 50.0)
        val totalVolume = result.values.sum()
        assertEquals(10.0, totalVolume, 0.01)
    }

    @Test
    fun `ventilerParProduit should handle coniferous category`() {
        val result = DecoupeCalculator.ventilerParProduit(5.0, "EPICEA", "resineux", 40.0)
        val totalVolume = result.values.sum()
        assertEquals(5.0, totalVolume, 0.01)
    }

    @Test
    fun `ventilerParProduit should handle resineux with accent`() {
        val result = DecoupeCalculator.ventilerParProduit(5.0, "EPICEA", "résineux", 40.0)
        val totalVolume = result.values.sum()
        assertEquals(5.0, totalVolume, 0.01)
    }

    @Test
    fun `ventilerParProduit should use feuillu fallback for null category`() {
        val result = DecoupeCalculator.ventilerParProduit(3.0, "CHENE", null, 45.0)
        val totalVolume = result.values.sum()
        assertEquals(3.0, totalVolume, 0.01)
    }

    @Test
    fun `ventilerParProduit should produce reasonable BO proportion for large tree`() {
        val result = DecoupeCalculator.ventilerParProduit(10.0, "CHENE", "feuillu", 60.0)
        if (result.containsKey("BO")) {
            assertTrue("BO should be a significant fraction for D=60", result["BO"]!! > 0.0)
        }
    }

    @Test
    fun `ventilerParProduit should not produce negative volumes`() {
        val result = DecoupeCalculator.ventilerParProduit(10.0, "CHENE", "feuillu", 50.0)
        result.values.forEach { v ->
            assertTrue("No negative volume", v >= 0.0)
        }
    }

    // ═══════════════════════════════════════════════════════════
    // VENTILATION CORRIGÉE (C.5) — pourcentages ONF 2024-2025
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `Feuillu D 40 et plus — BO 55pct, BI 25pct, BCh 20pct`() {
        val result = DecoupeCalculator.ventilerParProduit(1.0, "CH_SESSILE", "Feuillu", 45.0)
        assertEquals(1.0, result.values.sum(), 0.001)
        assertEquals(0.55, result["BO"]!!, 0.001)
        assertEquals(0.25, result["BI"]!!, 0.001)
        assertEquals(0.20, result["BCh"]!!, 0.001)
    }

    @Test
    fun `Resineux D 35 et plus — BO 65pct, BI 25pct, BE 10pct`() {
        val result = DecoupeCalculator.ventilerParProduit(1.0, "EPICEA_COMMUN", "Résineux", 40.0)
        assertEquals(1.0, result.values.sum(), 0.001)
        assertEquals(0.65, result["BO"]!!, 0.001)
        assertEquals(0.25, result["BI"]!!, 0.001)
        assertEquals(0.10, result["BE"]!!, 0.001)
    }

    // ═══════════════════════════════════════════════════════════
    // SEUIL FALLBACK ALIGNÉ (C.5) — D >= 40 → BO, sinon BI
    // Note : le fallback path-3 (catRules vide) est inatteignable car les
    // règles feuillus couvrent toute la plage de diamètres ; ce test valide
    // le fallback de catégorie (catégorie inconnue → règles feuillus).
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `Fallback categorie inconnue — D=40 utilise les regles feuillus avec BO`() {
        // categorie "Exotique" ne matche aucune branche → else → feuillu rules (D 40-999)
        val result = DecoupeCalculator.ventilerParProduit(1.0, "UNKNOWN", "Exotique", 40.0)
        assertTrue("D=40 doit contenir BO (règles feuillus)", result.containsKey("BO"))
        assertEquals(0.55, result["BO"]!!, 0.001)
    }
}
