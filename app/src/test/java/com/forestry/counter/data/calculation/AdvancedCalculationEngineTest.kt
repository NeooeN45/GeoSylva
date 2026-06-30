package com.forestry.counter.data.calculation

import com.forestry.counter.data.local.entity.AdvancedCalculationEntity
import com.forestry.counter.data.local.entity.CalculationPriority
import com.forestry.counter.data.local.entity.CalculationStatus
import com.forestry.counter.data.local.entity.CalculationType
import com.forestry.counter.data.local.entity.TigeEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

@OptIn(ExperimentalCoroutinesApi::class)
class AdvancedCalculationEngineTest {

    private val engine = AdvancedCalculationEngine()

    private fun tige(
        id: String,
        essenceCode: String,
        diamCm: Double,
        hauteurM: Double? = 20.0
    ): TigeEntity = TigeEntity(
        tigeId = id,
        parcelleOwnerId = "parcelle-1",
        placetteOwnerId = null,
        sessionId = null,
        essenceCode = essenceCode,
        diamCm = diamCm,
        hauteurM = hauteurM,
        gpsWkt = null,
        precisionM = null,
        altitudeM = null,
        note = null,
        produit = null,
        fCoef = null,
        valueEur = null,
        numero = null,
        categorie = null,
        qualite = null,
        defauts = null,
        photoUri = null,
        qualiteDetail = null,
        classeKraft = null,
        etatSanitaire = null,
        vigueur = null,
        origine = null,
        typeCoupe = null,
        biomasseFusTonnes = null,
        carboneFusTonnes = null,
        coefficientElancement = null,
        houppierM = null,
        houppierPct = null,
        isTigeHabitat = false
    )

    private fun biomassCalculation(): AdvancedCalculationEntity = AdvancedCalculationEntity(
        calculationId = "calc-1",
        parcelleId = null,
        calculationType = CalculationType.BIOMASS_ESTIMATION,
        name = "Biomass test",
        description = "",
        formula = "",
        variables = null,
        parameters = null,
        dependencies = null,
        result = 0.0,
        resultMetadata = null,
        status = CalculationStatus.PENDING,
        priority = CalculationPriority.MEDIUM,
        executionTime = null,
        accuracy = null,
        confidence = null,
        error = null,
        optimizationHints = null,
        validUntil = null,
        tags = null,
        createdAt = 0L,
        updatedAt = 0L
    )

    // ───────── Volume coefficients ─────────

    @Test
    fun volumeCoefficients_chene_returns_expected_a() {
        val (a, b, c) = engine.volumeCoefficients("CHENE")
        assertEquals(0.00005, a, 1e-10)
        assertEquals(2.0, b, 1e-10)
        assertEquals(1.0, c, 1e-10)
    }

    @Test
    fun volumeCoefficients_epicea_returns_expected_a() {
        val (a, _, _) = engine.volumeCoefficients("EPICEA_COMMUN")
        assertEquals(0.00004, a, 1e-10)
    }

    @Test
    fun volumeCoefficients_unknown_conifer_uses_default() {
        val (a, _, _) = engine.volumeCoefficients("CEDRE_ATLAS")
        assertEquals(0.000040, a, 1e-10)
    }

    @Test
    fun volumeCoefficients_unknown_feuillu_uses_default() {
        val (a, _, _) = engine.volumeCoefficients("ERABLE")
        assertEquals(0.000045, a, 1e-10)
    }

    // ───────── Wood density ─────────

    @Test
    fun woodDensity_chene_returns_0_61() {
        assertEquals(0.61, engine.woodDensity("CH_SESSILE"), 1e-10)
    }

    @Test
    fun woodDensity_hetre_returns_0_68() {
        assertEquals(0.68, engine.woodDensity("HETRE"), 1e-10)
    }

    @Test
    fun woodDensity_sapin_returns_0_43() {
        assertEquals(0.43, engine.woodDensity("SAPIN_PECTINE"), 1e-10)
    }

    @Test
    fun woodDensity_pin_sylvestre_returns_0_52() {
        assertEquals(0.52, engine.woodDensity("PIN_SYLVESTRE"), 1e-10)
    }

    @Test
    fun woodDensity_unknown_conifer_returns_default_0_45() {
        assertEquals(0.45, engine.woodDensity("CEDRE_ATLAS"), 1e-10)
    }

    @Test
    fun woodDensity_unknown_feuillu_returns_default_0_60() {
        assertEquals(0.60, engine.woodDensity("INCONNU"), 1e-10)
    }

    // ───────── BEF ─────────

    @Test
    fun bef_feuillu_returns_1_65() {
        assertEquals(1.65, engine.biomassExpansionFactor("CHENE"), 1e-10)
    }

    @Test
    fun bef_resineux_returns_1_45() {
        assertEquals(1.45, engine.biomassExpansionFactor("SAPIN"), 1e-10)
    }

    // ───────── Biomass breakdown ─────────

    @Test
    fun computeTreeBiomassBreakdown_chene_30cm_25m_matches_formula() {
        val tige = tige("t1", "CHENE", 30.0, 25.0)
        val bd = engine.computeTreeBiomassBreakdownKg(tige)

        // V = 0.00005 × 30² × 25 = 1.125 m³
        val expectedVolume = 0.00005 * 30.0.pow(2.0) * 25.0
        // fût = 1.125 × 0.61 × 1000 = 686.25 kg
        val expectedStem = expectedVolume * 0.61 * 1000.0
        // aérienne = 686.25 × 1.65 = 1132.31 kg
        val expectedAbove = expectedStem * 1.65
        // racines = 1132.31 × 0.25 = 283.08 kg
        val expectedBelow = expectedAbove * 0.25
        val expectedTotal = expectedAbove + expectedBelow

        assertEquals(expectedStem, bd.stemBiomassKg, 1e-6)
        assertEquals(expectedAbove, bd.abovegroundBiomassKg, 1e-6)
        assertEquals(expectedBelow, bd.belowgroundBiomassKg, 1e-6)
        assertEquals(expectedTotal, bd.totalBiomassKg, 1e-6)
    }

    @Test
    fun computeTreeBiomassBreakdown_zero_diameter_returns_zeros() {
        val tige = tige("t2", "CHENE", 0.0, 25.0)
        val bd = engine.computeTreeBiomassBreakdownKg(tige)
        assertEquals(0.0, bd.totalBiomassKg, 1e-10)
        assertEquals(0.0, bd.stemBiomassKg, 1e-10)
    }

    @Test
    fun computeTreeBiomassBreakdown_zero_height_returns_zeros() {
        val tige = tige("t3", "CHENE", 30.0, 0.0)
        val bd = engine.computeTreeBiomassBreakdownKg(tige)
        assertEquals(0.0, bd.totalBiomassKg, 1e-10)
    }

    @Test
    fun computeTreeBiomassBreakdown_resineux_uses_bef_1_45() {
        val tige = tige("t4", "EPICEA_COMMUN", 30.0, 25.0)
        val bd = engine.computeTreeBiomassBreakdownKg(tige)

        val expectedVolume = 0.00004 * 30.0.pow(2.0) * 25.0
        val expectedStem = expectedVolume * 0.43 * 1000.0
        val expectedAbove = expectedStem * 1.45

        assertEquals(expectedAbove, bd.abovegroundBiomassKg, 1e-6)
    }

    // ───────── Carbon fraction = 0.50 ─────────

    @Test
    fun carbon_sequestration_uses_fraction_0_50() = runTest {
        val tige = tige("t5", "CHENE", 30.0, 25.0)
        val calc = biomassCalculation().copy(
            calculationType = CalculationType.CARBON_SEQUESTRATION
        )
        val result = engine.executeCalculation(calc, listOf(tige), null)

        val bd = engine.computeTreeBiomassBreakdownKg(tige)
        val expectedCarbon = bd.totalBiomassKg * 0.50
        val expectedCo2 = expectedCarbon * 3.67

        assertEquals(expectedCo2, result.result!!, 1e-3)
        assertTrue(result.resultMetadata!!.contains("\"carbonFraction\": 0.5"))
    }

    @Test
    fun carbon_metadata_contains_breakdown_fields() = runTest {
        val tige = tige("t6", "CHENE", 30.0, 25.0)
        val calc = biomassCalculation().copy(
            calculationType = CalculationType.CARBON_SEQUESTRATION
        )
        val result = engine.executeCalculation(calc, listOf(tige), null)

        val meta = result.resultMetadata!!
        assertTrue(meta.contains("totalBiomassKg"))
        assertTrue(meta.contains("stemBiomassKg"))
        assertTrue(meta.contains("abovegroundBiomassKg"))
        assertTrue(meta.contains("belowgroundBiomassKg"))
    }

    // ───────── Biomass estimation ─────────

    @Test
    fun biomass_estimation_returns_total_biomass_kg() = runTest {
        val tige = tige("t7", "CHENE", 30.0, 25.0)
        val result = engine.executeCalculation(biomassCalculation(), listOf(tige), null)

        val bd = engine.computeTreeBiomassBreakdownKg(tige)
        assertEquals(bd.totalBiomassKg, result.result!!, 1e-3)
    }

    @Test
    fun biomass_estimation_empty_tiges_returns_error() = runTest {
        val result = engine.executeCalculation(biomassCalculation(), emptyList(), null)
        assertEquals(0.0, result.result!!, 1e-10)
        assertTrue(result.error != null)
    }
}
