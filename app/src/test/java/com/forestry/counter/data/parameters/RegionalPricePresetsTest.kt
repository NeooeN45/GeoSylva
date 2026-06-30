package com.forestry.counter.data.parameters

import com.forestry.counter.domain.calculation.PriceEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests pour RegionalPricePresets — vérifie les prix ONF 2024-2025 corrigés
 * (C-PRIX-1, C.5). Les prix nationaux ont été réajustés à la hausse car
 * ils étaient sous-évalués de 25-40% par rapport aux références ONF.
 */
class RegionalPricePresetsTest {

    private fun nationalPrices(): List<PriceEntry> =
        RegionalPricePresets.ALL.first { it.code == "NATIONAL" }.prices

    private fun price(
        essence: String,
        product: String,
        minDiam: Int,
        quality: String? = null
    ): PriceEntry? = nationalPrices().firstOrNull {
        it.essence == essence &&
            it.product == product &&
            it.min == minDiam &&
            it.quality == quality
    }

    // ═══════════════════════════════════════════════════════════
    // CHÊNE SESSILE — prix BO par qualité (ONF 2024-2025)
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `Chene sessile BO grade A vaut 280 eur_par_m3`() {
        val p = price("CH_SESSILE", "BO", 35, "A")
        assertNotNull("CH_SESSILE BO grade A doit exister", p)
        assertEquals(280.0, p!!.eurPerM3, 0.001)
    }

    @Test
    fun `Chene sessile BO grade B vaut 160 eur_par_m3`() {
        val p = price("CH_SESSILE", "BO", 35, "B")
        assertNotNull("CH_SESSILE BO grade B doit exister", p)
        assertEquals(160.0, p!!.eurPerM3, 0.001)
    }

    @Test
    fun `Chene sessile BO grade C vaut 90 eur_par_m3`() {
        val p = price("CH_SESSILE", "BO", 35, "C")
        assertNotNull("CH_SESSILE BO grade C doit exister", p)
        assertEquals(90.0, p!!.eurPerM3, 0.001)
    }

    // ═══════════════════════════════════════════════════════════
    // CHÊNE PÉDONCULÉ — légèrement inférieur au sessile
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `Chene pedoncule BO grade A vaut 260 eur_par_m3`() {
        val p = price("CH_PEDONCULE", "BO", 35, "A")
        assertNotNull("CH_PEDONCULE BO grade A doit exister", p)
        assertEquals(260.0, p!!.eurPerM3, 0.001)
    }

    @Test
    fun `Chene pedoncule BO grade B vaut 150 eur_par_m3`() {
        val p = price("CH_PEDONCULE", "BO", 35, "B")
        assertNotNull("CH_PEDONCULE BO grade B doit exister", p)
        assertEquals(150.0, p!!.eurPerM3, 0.001)
    }

    @Test
    fun `Chene pedoncule BO grade C vaut 85 eur_par_m3`() {
        val p = price("CH_PEDONCULE", "BO", 35, "C")
        assertNotNull("CH_PEDONCULE BO grade C doit exister", p)
        assertEquals(85.0, p!!.eurPerM3, 0.001)
    }

    @Test
    fun `Chene pedoncule reste inferieur au sessile pour chaque grade`() {
        for (grade in listOf("A", "B", "C")) {
            val sessile = price("CH_SESSILE", "BO", 35, grade)!!.eurPerM3
            val pedoncule = price("CH_PEDONCULE", "BO", 35, grade)!!.eurPerM3
            assertTrue(
                "CH_PEDONCULE grade $grade ($pedoncule) doit être < CH_SESSILE ($sessile)",
                pedoncule < sessile
            )
        }
    }

    // ═══════════════════════════════════════════════════════════
    // HÊTRE — prix BO par qualité
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `Hetre commun BO grade A vaut 110 eur_par_m3`() {
        val p = price("HETRE_COMMUN", "BO", 40, "A")
        assertNotNull("HETRE_COMMUN BO grade A doit exister", p)
        assertEquals(110.0, p!!.eurPerM3, 0.001)
    }

    @Test
    fun `Hetre commun BO grade B vaut 70 eur_par_m3`() {
        val p = price("HETRE_COMMUN", "BO", 40, "B")
        assertNotNull("HETRE_COMMUN BO grade B doit exister", p)
        assertEquals(70.0, p!!.eurPerM3, 0.001)
    }

    // ═══════════════════════════════════════════════════════════
    // NOYER, DOUGLAS, FRÊNE
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `Noyer commun BO vaut 350 eur_par_m3`() {
        val p = price("NOYER_COMMUN", "BO", 30)
        assertNotNull("NOYER_COMMUN BO doit exister", p)
        assertEquals(350.0, p!!.eurPerM3, 0.001)
    }

    @Test
    fun `Douglas vert BO vaut 82 eur_par_m3`() {
        val p = price("DOUGLAS_VERT", "BO", 30)
        assertNotNull("DOUGLAS_VERT BO doit exister", p)
        assertEquals(82.0, p!!.eurPerM3, 0.001)
    }

    @Test
    fun `Frene eleve BO grade A vaut 120 eur_par_m3`() {
        val p = price("FRENE_ELEVE", "BO", 35, "A")
        assertNotNull("FRENE_ELEVE BO grade A doit exister", p)
        assertEquals(120.0, p!!.eurPerM3, 0.001)
    }

    @Test
    fun `Frene eleve BO grade B vaut 80 eur_par_m3`() {
        val p = price("FRENE_ELEVE", "BO", 35, "B")
        assertNotNull("FRENE_ELEVE BO grade B doit exister", p)
        assertEquals(80.0, p!!.eurPerM3, 0.001)
    }

    // ═══════════════════════════════════════════════════════════
    // Cohérence structurelle
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `Tous les presets regionaux sont non vides`() {
        for (preset in RegionalPricePresets.ALL) {
            assertTrue(
                "Preset ${preset.code} doit contenir au moins un prix",
                preset.prices.isNotEmpty()
            )
        }
    }

    @Test
    fun `Le preset NATIONAL existe`() {
        assertNotNull(
            "Le preset NATIONAL doit exister",
            RegionalPricePresets.ALL.firstOrNull { it.code == "NATIONAL" }
        )
    }
}
