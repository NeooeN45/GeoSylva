package com.forestry.counter.domain.calculation.cubage

import com.forestry.counter.domain.calculation.tarifs.TarifCalculator
import com.forestry.counter.domain.calculation.tarifs.TarifMethod
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/**
 * Non-régression : chaque wrapper doit renvoyer EXACTEMENT la même valeur que
 * `TarifCalculator.computeVolume(...)` appelé directement (voir TarifCalculatorTest.kt).
 * Le wrapper ne doit rien recalculer ni altérer.
 */
class TarifCalculatorAdapterTest {

    @Test
    fun `Schaeffer1E adapter matches TarifCalculator directly`() {
        val direct = TarifCalculator.computeVolume(TarifMethod.SCHAEFFER_1E, "HETRE_COMMUN", 35.0, null, tarifNumero = 8)!!
        val wrapped = Schaeffer1ECubageMethod.compute(
            StandingTreeMeasurement(essenceCode = "HETRE_COMMUN", diamCm = 35.0, tarifNumero = 8)
        )
        assertTrue(wrapped is CubageResult.Valid)
        assertTrue(abs(direct - (wrapped as CubageResult.Valid).volumeM3) <= 1e-12)
    }

    @Test
    fun `Schaeffer2E adapter — missing height is Blocked (matches direct null)`() {
        val direct = TarifCalculator.computeVolume(TarifMethod.SCHAEFFER_2E, "CH_SESSILE", 40.0, null, tarifNumero = 4)
        assertNull(direct)
        val wrapped = Schaeffer2ECubageMethod.compute(
            StandingTreeMeasurement(essenceCode = "CH_SESSILE", diamCm = 40.0, tarifNumero = 4)
        )
        assertTrue(wrapped is CubageResult.Blocked)
    }

    @Test
    fun `Algan adapter matches TarifCalculator directly`() {
        val direct = TarifCalculator.computeVolume(TarifMethod.ALGAN, "HETRE_COMMUN", 35.0, 22.0)!!
        val wrapped = AlganCubageMethod.compute(
            StandingTreeMeasurement(essenceCode = "HETRE_COMMUN", diamCm = 35.0, hauteurM = 22.0)
        )
        assertTrue(wrapped is CubageResult.Valid)
        assertTrue(abs(direct - (wrapped as CubageResult.Valid).volumeM3) <= 1e-12)
    }

    @Test
    fun `IfnRapide adapter matches TarifCalculator directly`() {
        val direct = TarifCalculator.computeVolume(TarifMethod.IFN_RAPIDE, "HETRE_COMMUN", 35.0, null, tarifNumero = 12)!!
        val wrapped = IfnRapideCubageMethod.compute(
            StandingTreeMeasurement(essenceCode = "HETRE_COMMUN", diamCm = 35.0, tarifNumero = 12)
        )
        assertTrue(wrapped is CubageResult.Valid)
        assertTrue(abs(direct - (wrapped as CubageResult.Valid).volumeM3) <= 1e-12)
    }

    @Test
    fun `IfnLent adapter matches TarifCalculator directly`() {
        val direct = TarifCalculator.computeVolume(TarifMethod.IFN_LENT, "HETRE_COMMUN", 35.0, 22.0, tarifNumero = 5)!!
        val wrapped = IfnLentCubageMethod.compute(
            StandingTreeMeasurement(essenceCode = "HETRE_COMMUN", diamCm = 35.0, hauteurM = 22.0, tarifNumero = 5)
        )
        assertTrue(wrapped is CubageResult.Valid)
        assertTrue(abs(direct - (wrapped as CubageResult.Valid).volumeM3) <= 1e-12)
    }

    @Test
    fun `FGH adapter matches TarifCalculator directly`() {
        val direct = TarifCalculator.computeVolume(TarifMethod.FGH, "HETRE_COMMUN", 35.0, 22.0)!!
        val wrapped = FghCubageMethod.compute(
            StandingTreeMeasurement(essenceCode = "HETRE_COMMUN", diamCm = 35.0, hauteurM = 22.0)
        )
        assertTrue(wrapped is CubageResult.Valid)
        assertTrue(abs(direct - (wrapped as CubageResult.Valid).volumeM3) <= 1e-12)
    }

    @Test
    fun `all six historical wrappers are qualification EXPERIMENTAL`() {
        val all = listOf(
            Schaeffer1ECubageMethod, Schaeffer2ECubageMethod, AlganCubageMethod,
            IfnRapideCubageMethod, IfnLentCubageMethod, FghCubageMethod
        )
        for (m in all) {
            assertEquals("${m.id} should be EXPERIMENTAL", MethodQualification.EXPERIMENTAL, m.qualification)
        }
    }

    @Test
    fun `zero diameter is Blocked, never a silent zero`() {
        val wrapped = AlganCubageMethod.compute(
            StandingTreeMeasurement(essenceCode = "HETRE_COMMUN", diamCm = 0.0, hauteurM = 22.0)
        )
        assertTrue(wrapped is CubageResult.Blocked)
    }
}
