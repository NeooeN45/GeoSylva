package com.forestry.counter.domain.calculation.cubage

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class SmalianMethodTest {

    // Vecteur "smalian_log" — docs/recherche/02_marche_prix/vecteurs_tests_cubage.json
    @Test
    fun `Smalian — L=5 Db=0,5 Dt=0,3 matches reference vector`() {
        val result = SmalianMethod.compute(
            LogSegmentMeasurement(lengthM = 5.0, diameterBaseM = 0.5, diameterTopM = 0.3)
        )
        assertTrue(result is CubageResult.Valid)
        val volume = (result as CubageResult.Valid).volumeM3
        assertTrue("expected ≈0.6675884389 but was $volume", abs(volume - 0.6675884389) <= 1e-7)
    }

    @Test
    fun `Smalian — missing base diameter is Blocked`() {
        val result = SmalianMethod.compute(LogSegmentMeasurement(lengthM = 5.0, diameterTopM = 0.3))
        assertTrue(result is CubageResult.Blocked)
    }

    @Test
    fun `Smalian — missing top diameter is Blocked`() {
        val result = SmalianMethod.compute(LogSegmentMeasurement(lengthM = 5.0, diameterBaseM = 0.5))
        assertTrue(result is CubageResult.Blocked)
    }

    @Test
    fun `Smalian — method metadata`() {
        assertEquals("SMALIAN", SmalianMethod.id)
        assertEquals(MethodQualification.QUALIFIED, SmalianMethod.qualification)
    }
}
