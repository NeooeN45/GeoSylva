package com.forestry.counter.domain.calculation.pricing

import com.forestry.counter.domain.calculation.PriceEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests du moteur de prix professionnel.
 *
 * Couvre : cascade de lookup, fallback, coefficients (qualité/région/position/lot),
 * cumul multiplicatif des défauts, insensibilité à la casse, et surtout la
 * NON-DIVERGENCE entre les deux chemins de calcul (calculate vs calculateFromEntryOnly).
 */
class ProPricingEngineTest {

    private val douglasBO = PriceEntry(
        essence = "DOUGLAS_VERT", product = "BO", min = 20, max = 80, eurPerM3 = 100.0
    )
    private val cheneBOqualA = PriceEntry(
        essence = "CHENE", product = "BO", min = 30, max = 90, eurPerM3 = 250.0, quality = "A"
    )
    private val wildcardEssence = PriceEntry(
        essence = "*", product = "BCh", min = 7, max = 200, eurPerM3 = 40.0
    )
    private val prices = listOf(douglasBO, cheneBOqualA, wildcardEssence)

    // ─────────────────────────── Lookup / cascade ───────────────────────────

    @Test
    fun `trouve le prix de reference par essence et produit exacts`() {
        val ctx = PricingContext("DOUGLAS_VERT", "BO", diamCm = 40, prices = prices)
        val result = ProPricingEngine.calculate(ctx)
        assertEquals(100.0, result.basePricePerM3, 1e-9)
        assertFalse(result.usedFallback)
    }

    @Test
    fun `lookup insensible a la casse de l'essence`() {
        val ctx = PricingContext("douglas_vert", "bo", diamCm = 40, prices = prices)
        val result = ProPricingEngine.calculate(ctx)
        assertEquals(100.0, result.basePricePerM3, 1e-9)
    }

    @Test
    fun `respecte les bornes de diametre`() {
        val ctx = PricingContext("DOUGLAS_VERT", "BO", diamCm = 10, prices = prices) // hors 20..80
        val result = ProPricingEngine.calculate(ctx)
        // Pas d'entrée → fallback
        assertTrue(result.usedFallback)
    }

    @Test
    fun `utilise le wildcard essence quand aucune essence exacte`() {
        val ctx = PricingContext("INCONNU_XYZ", "BCh", diamCm = 30, prices = prices)
        val result = ProPricingEngine.calculate(ctx)
        assertEquals(40.0, result.basePricePerM3, 1e-9)
        assertFalse(result.usedFallback)
    }

    @Test
    fun `bascule sur le fallback quand aucun prix ne correspond`() {
        val ctx = PricingContext("DOUGLAS_VERT", "BO", diamCm = 40, prices = emptyList())
        val result = ProPricingEngine.calculate(ctx)
        assertTrue(result.usedFallback)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun `calculateFromEntryOnly retourne null sans entree correspondante`() {
        val price = ProPricingEngine.calculateFromEntryOnly(
            essenceCode = "DOUGLAS_VERT", product = "BO", diamCm = 40, prices = emptyList()
        )
        assertNull(price)
    }

    @Test
    fun `calculateFromEntryOnly resout les alias via candidates`() {
        val price = ProPricingEngine.calculateFromEntryOnly(
            essenceCode = "DGL", product = "BO", diamCm = 40, prices = prices,
            essenceCandidates = listOf("DGL", "DOUGLAS_VERT")
        )
        assertNotNull(price)
    }

    // ─────────────────────────── Coefficient régional ───────────────────────────

    @Test
    fun `applique le coefficient regional specifique essence x GRECO`() {
        val sansRegion = ProPricingEngine.calculate(
            PricingContext("DOUGLAS_VERT", "BO", diamCm = 40, prices = prices, region = null)
        ).finalPricePerM3
        val grandEst = ProPricingEngine.calculate(
            PricingContext("DOUGLAS_VERT", "BO", diamCm = 40, prices = prices, region = GrecoRegion.C)
        ).finalPricePerM3
        // DOUGLAS_VERT × GRECO C = 1.30 (spécifique). Seule la région change → ratio = 1.30.
        assertEquals(1.30, grandEst / sansRegion, 1e-9)
    }

    @Test
    fun `coefficient regional insensible a la casse de l'essence (regression A4)`() {
        val sansRegion = ProPricingEngine.calculate(
            PricingContext("douglas_vert", "BO", diamCm = 40, prices = prices, region = null)
        ).finalPricePerM3
        val grandEst = ProPricingEngine.calculate(
            PricingContext("douglas_vert", "BO", diamCm = 40, prices = prices, region = GrecoRegion.C)
        ).finalPricePerM3
        // Avant correctif : 1.10 (moyenne GRECO) au lieu de 1.30 (spécifique) car la casse ratait la clé.
        assertEquals(1.30, grandEst / sansRegion, 1e-9)
    }

    // ─────────────────────────── Position / lot ───────────────────────────

    @Test
    fun `applique le coefficient de position`() {
        val surPied = ProPricingEngine.calculate(
            PricingContext("DOUGLAS_VERT", "BO", diamCm = 40, prices = prices, position = SalePosition.SUR_PIED)
        ).finalPricePerM3
        val usine = ProPricingEngine.calculate(
            PricingContext("DOUGLAS_VERT", "BO", diamCm = 40, prices = prices, position = SalePosition.USINE)
        ).finalPricePerM3
        assertEquals(SalePosition.USINE.coefficient, usine / surPied, 1e-9)
    }

    @Test
    fun `coefficient de lot par paliers`() {
        assertEquals(1.0, LotSizeCoefficients.coefficient(null), 1e-9)
        assertEquals(0.85, LotSizeCoefficients.coefficient(30.0), 1e-9)
        assertEquals(1.0, LotSizeCoefficients.coefficient(150.0), 1e-9)
        assertEquals(1.10, LotSizeCoefficients.coefficient(800.0), 1e-9)
    }

    // ─────────────────────────── Défauts (cumul multiplicatif B1) ───────────────────────────

    @Test
    fun `cumul des defauts est multiplicatif`() {
        // FOURCHE MAJEUR = 0.50 ; deux défauts à 0.50 → 1-(0.5*0.5) = 0.75 (et non 1.0)
        val deux = listOf(
            WoodDefect.FOURCHE to DefectSeverity.MAJEUR,
            WoodDefect.NOEUDS_SAUTANTS to DefectSeverity.MAJEUR
        )
        assertEquals(0.75, WoodDefect.cumulativeDepreciation(deux), 1e-9)
    }

    @Test
    fun `depreciation cumulee plafonnee a 90 pourcent`() {
        val pourritures = listOf(
            WoodDefect.POURRITURE_CUBIQUE to DefectSeverity.MAJEUR,  // 1.00
            WoodDefect.POURRITURE_FIBREUSE to DefectSeverity.MAJEUR  // 1.00
        )
        assertEquals(WoodDefect.MAX_TOTAL_DEPRECIATION, WoodDefect.cumulativeDepreciation(pourritures), 1e-9)
    }

    @Test
    fun `aucun defaut ne deprecie pas`() {
        assertEquals(0.0, WoodDefect.cumulativeDepreciation(emptyList()), 1e-9)
    }

    @Test
    fun `severite module la depreciation`() {
        val min = WoodDefect.COURBURE.depreciation(DefectSeverity.MINEUR)
        val mod = WoodDefect.COURBURE.depreciation(DefectSeverity.MODERE)
        val maj = WoodDefect.COURBURE.depreciation(DefectSeverity.MAJEUR)
        assertTrue(min < mod && mod < maj)
        assertEquals(0.10, min, 1e-9)
        assertEquals(0.40, maj, 1e-9)
    }

    // ─────────────────────────── NON-DIVERGENCE (régression A1/A3) ───────────────────────────

    @Test
    fun `les deux chemins de calcul donnent le meme prix (regression divergence)`() {
        val ctx = PricingContext(
            essenceCode = "DOUGLAS_VERT", product = "BO", diamCm = 40, qualityGrade = "B",
            prices = prices, region = GrecoRegion.C, position = SalePosition.BORD_ROUTE
        )
        val viaCalculate = ProPricingEngine.calculate(ctx).finalPricePerM3
        val viaEntryOnly = ProPricingEngine.calculateFromEntryOnly(
            essenceCode = "DOUGLAS_VERT", product = "BO", diamCm = 40, qualityGrade = "B",
            prices = prices, position = SalePosition.BORD_ROUTE,
            essenceCandidates = listOf("DOUGLAS_VERT"), region = GrecoRegion.C
        )
        assertNotNull(viaEntryOnly)
        // Doivent être strictement égaux : même source de vérité (buildResult).
        assertEquals(viaCalculate, viaEntryOnly!!, 1e-9)
    }

    @Test
    fun `le coefficient total est le produit de tous les coefficients`() {
        val ctx = PricingContext(
            "DOUGLAS_VERT", "BO", diamCm = 40, qualityGrade = "B",
            prices = prices, region = GrecoRegion.C, position = SalePosition.USINE
        )
        val r = ProPricingEngine.calculate(ctx)
        val b = r.breakdown
        val expected = b.qualityCoefficient * b.defectNetCoefficient * b.regionalCoefficient *
            b.accessibilityCoefficient * b.seasonCoefficient * b.certificationCoefficient *
            b.lotSizeCoefficient * b.positionCoefficient
        assertEquals(expected, b.totalCoefficient, 1e-9)
        assertEquals(r.basePricePerM3 * b.totalCoefficient, r.finalPricePerM3, 1e-9)
    }
}
