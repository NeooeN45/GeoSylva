package com.forestry.counter.data.parameters

import com.forestry.counter.domain.calculation.PriceCalculator
import com.forestry.counter.domain.calculation.PriceEntry
import com.forestry.counter.domain.calculation.pricing.GrecoRegion
import com.forestry.counter.domain.calculation.pricing.ProPricingEngine
import com.forestry.counter.domain.calculation.pricing.PricingContext
import com.forestry.counter.domain.calculation.pricing.SalePosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests pour RegionalPricePresets — vérifie les prix 2024-2025 (qualité C référence).
 *
 * Architecture :
 * - Les PriceEntry contiennent des prix de référence qualité C (sur pied, €/m³).
 * - Le coefficient qualité (NF EN 1316/1927) est appliqué par ProPricingEngine.
 * - Exemple : CH_SESSILE BO C=90€ → A=90×2.80=252€, B=90×1.80=162€, D=90×0.55=49.5€
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
    // CHÊNE SESSILE — prix BO qualité C référence (sur pied, 2024-2025)
    // Le coefficient qualité est appliqué par ProPricingEngine :
    // C=90€ (réf) → A=252€ (×2.80), B=162€ (×1.80), D=49.5€ (×0.55)
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `Chene sessile BO qualite C vaut 90 eur_par_m3`() {
        val p = price("CH_SESSILE", "BO", 35)
        assertNotNull("CH_SESSILE BO doit exister", p)
        assertEquals(90.0, p!!.eurPerM3, 0.001)
    }

    @Test
    fun `Chene sessile BO grade A applique coefficient 2_80 via ProPricingEngine`() {
        val context = PricingContext(
            essenceCode = "CH_SESSILE",
            product = "BO",
            diamCm = 40,
            qualityGrade = "A",
            prices = nationalPrices(),
            position = SalePosition.SUR_PIED
        )
        val result = ProPricingEngine.calculate(context)
        // 90€ × 2.80 (qualité A) = 252€
        assertEquals(252.0, result.finalPricePerM3, 0.5)
    }

    @Test
    fun `Chene sessile BO grade B applique coefficient 1_80 via ProPricingEngine`() {
        val context = PricingContext(
            essenceCode = "CH_SESSILE",
            product = "BO",
            diamCm = 40,
            qualityGrade = "B",
            prices = nationalPrices(),
            position = SalePosition.SUR_PIED
        )
        val result = ProPricingEngine.calculate(context)
        // 90€ × 1.80 (qualité B) = 162€
        assertEquals(162.0, result.finalPricePerM3, 0.5)
    }

    @Test
    fun `Chene sessile BO grade D applique coefficient 0_55 via ProPricingEngine`() {
        val context = PricingContext(
            essenceCode = "CH_SESSILE",
            product = "BO",
            diamCm = 40,
            qualityGrade = "D",
            prices = nationalPrices(),
            position = SalePosition.SUR_PIED
        )
        val result = ProPricingEngine.calculate(context)
        // 90€ × 0.55 (qualité D) = 49.5€
        assertEquals(49.5, result.finalPricePerM3, 0.5)
    }

    // ═══════════════════════════════════════════════════════════
    // CHÊNE PÉDONCULÉ — légèrement inférieur au sessile
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `Chene pedoncule BO qualite C vaut 80 eur_par_m3`() {
        val p = price("CH_PEDONCULE", "BO", 35)
        assertNotNull("CH_PEDONCULE BO doit exister", p)
        assertEquals(80.0, p!!.eurPerM3, 0.001)
    }

    @Test
    fun `Chene pedoncule reste inferieur au sessile en prix de reference`() {
        val sessile = price("CH_SESSILE", "BO", 35)!!.eurPerM3
        val pedoncule = price("CH_PEDONCULE", "BO", 35)!!.eurPerM3
        assertTrue(
            "CH_PEDONCULE ($pedoncule) doit être < CH_SESSILE ($sessile)",
            pedoncule < sessile
        )
    }

    // ═══════════════════════════════════════════════════════════
    // HÊTRE — prix BO qualité C référence
    // C=60€ → A=132€ (×2.20), B=90€ (×1.50), D=27€ (×0.45)
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `Hetre commun BO qualite C vaut 60 eur_par_m3`() {
        val p = price("HETRE_COMMUN", "BO", 40)
        assertNotNull("HETRE_COMMUN BO doit exister", p)
        assertEquals(60.0, p!!.eurPerM3, 0.001)
    }

    @Test
    fun `Hetre commun BO grade A applique coefficient 2_20`() {
        val context = PricingContext(
            essenceCode = "HETRE_COMMUN",
            product = "BO",
            diamCm = 45,
            qualityGrade = "A",
            prices = nationalPrices(),
            position = SalePosition.SUR_PIED
        )
        val result = ProPricingEngine.calculate(context)
        // 60€ × 2.20 = 132€
        assertEquals(132.0, result.finalPricePerM3, 0.5)
    }

    // ═══════════════════════════════════════════════════════════
    // NOYER, DOUGLAS, FRÊNE — prix référence 2024-2025
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `Noyer commun BO qualite C vaut 120 eur_par_m3`() {
        val p = price("NOYER_COMMUN", "BO", 30)
        assertNotNull("NOYER_COMMUN BO doit exister", p)
        assertEquals(120.0, p!!.eurPerM3, 0.001)
    }

    @Test
    fun `Noyer commun BO grade A applique coefficient 3_20`() {
        val context = PricingContext(
            essenceCode = "NOYER_COMMUN",
            product = "BO",
            diamCm = 35,
            qualityGrade = "A",
            prices = nationalPrices(),
            position = SalePosition.SUR_PIED
        )
        val result = ProPricingEngine.calculate(context)
        // 120€ × 3.20 = 384€
        assertEquals(384.0, result.finalPricePerM3, 0.5)
    }

    @Test
    fun `Douglas vert BO qualite C vaut 72 eur_par_m3`() {
        val p = price("DOUGLAS_VERT", "BO", 30)
        assertNotNull("DOUGLAS_VERT BO doit exister", p)
        assertEquals(72.0, p!!.eurPerM3, 0.001)
    }

    @Test
    fun `Frene eleve BO qualite C vaut 80 eur_par_m3`() {
        val p = price("FRENE_ELEVE", "BO", 35)
        assertNotNull("FRENE_ELEVE BO doit exister", p)
        assertEquals(80.0, p!!.eurPerM3, 0.001)
    }

    // ═══════════════════════════════════════════════════════════
    // 12 GRECO — vérification structurelle
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

    @Test
    fun `Les 12 GRECO A-L sont presents`() {
        for (code in listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L")) {
            assertNotNull(
                "GRECO $code doit exister dans les presets",
                RegionalPricePresets.ALL.firstOrNull { it.code == code }
            )
        }
    }

    @Test
    fun `GRECO E Jura a des prix superieurs au NATIONAL`() {
        val national = RegionalPricePresets.ALL.first { it.code == "NATIONAL" }
        val jura = RegionalPricePresets.ALL.first { it.code == "E" }
        val nationalChene = national.prices.first { it.essence == "CH_SESSILE" && it.product == "BO" }.eurPerM3
        val juraChene = jura.prices.first { it.essence == "CH_SESSILE" && it.product == "BO" }.eurPerM3
        assertTrue(
            "Chêne sessile Jura ($juraChene) doit être > National ($nationalChene)",
            juraChene > nationalChene
        )
    }

    @Test
    fun `GRECO G Massif Central a des douglas inferieurs au NATIONAL`() {
        val national = RegionalPricePresets.ALL.first { it.code == "NATIONAL" }
        val massifCentral = RegionalPricePresets.ALL.first { it.code == "G" }
        val nationalDouglas = national.prices.first { it.essence == "DOUGLAS_VERT" && it.product == "BO" }.eurPerM3
        val mcDouglas = massifCentral.prices.first { it.essence == "DOUGLAS_VERT" && it.product == "BO" }.eurPerM3
        assertTrue(
            "Douglas Massif Central ($mcDouglas) doit être < National ($nationalDouglas)",
            mcDouglas < nationalDouglas
        )
    }

    // ═══════════════════════════════════════════════════════════
    // Coefficients qualité NF EN 1316/1927
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `Coefficient qualite Chene sessile A est 2_80`() {
        assertEquals(2.80, PriceCalculator.getQualityCoefficient("CH_SESSILE", "A"), 0.001)
    }

    @Test
    fun `Coefficient qualite Douglas A est 1_55`() {
        assertEquals(1.55, PriceCalculator.getQualityCoefficient("DOUGLAS_VERT", "A"), 0.001)
    }

    @Test
    fun `Coefficient qualite Noyer A est 3_20`() {
        assertEquals(3.20, PriceCalculator.getQualityCoefficient("NOYER_COMMUN", "A"), 0.001)
    }

    @Test
    fun `Ecart A_D chene sessile est 5x`() {
        val coefA = PriceCalculator.getQualityCoefficient("CH_SESSILE", "A")
        val coefD = PriceCalculator.getQualityCoefficient("CH_SESSILE", "D")
        val ratio = coefA / coefD
        assertTrue(
            "Ratio A/D chêne sessile ($ratio) doit être ~5x (NF EN 1316-1)",
            ratio > 4.5 && ratio < 6.0
        )
    }
}
