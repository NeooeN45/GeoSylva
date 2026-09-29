package com.forestry.counter.domain.calculation.cubage

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow

class CoefFormeCubageMethodTest {

    // Vecteur "form_factor_tree" — docs/recherche/02_marche_prix/vecteurs_tests_cubage.json
    // D=40cm H=25m f=0.45 -> V=1.4137166941 (V = G x H x f, facteur fourni explicitement)
    @Test
    fun `CoefForme — D=40 H=25 f=0,45 matches reference vector`() {
        val result = CoefFormeCubageMethod.compute(
            StandingTreeMeasurement(
                essenceCode = "HETRE_COMMUN",
                diamCm = 40.0,
                hauteurM = 25.0,
                coefFormOverride = 0.45
            )
        )
        assertTrue(result is CubageResult.Valid)
        val volume = (result as CubageResult.Valid).volumeM3
        val expected = PI / 4.0 * (40.0 / 100.0).pow(2.0) * 25.0 * 0.45
        assertTrue("expected ≈$expected but was $volume", abs(volume - expected) <= 1e-7)
        assertTrue("expected ≈1.4137166941 but was $volume", abs(volume - 1.4137166941) <= 1e-7)
    }

    @Test
    fun `CoefForme — missing height is Blocked`() {
        val result = CoefFormeCubageMethod.compute(StandingTreeMeasurement(essenceCode = "HETRE_COMMUN", diamCm = 40.0))
        assertTrue(result is CubageResult.Blocked)
    }

    @Test
    fun `CoefForme — method metadata`() {
        assertEquals("COEF_FORME", CoefFormeCubageMethod.id)
        assertEquals(MethodQualification.QUALIFIED, CoefFormeCubageMethod.qualification)
    }
}
