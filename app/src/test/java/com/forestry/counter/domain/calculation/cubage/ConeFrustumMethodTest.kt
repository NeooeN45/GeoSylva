package com.forestry.counter.domain.calculation.cubage

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ConeFrustumMethodTest {

    // Vecteur "cone_frustum_log" — docs/recherche/02_marche_prix/vecteurs_tests_cubage.json
    @Test
    fun `ConeFrustum — L=4 Db=0,6 Dt=0,2 matches reference vector`() {
        val result = ConeFrustumMethod.compute(
            LogSegmentMeasurement(lengthM = 4.0, diameterBaseM = 0.6, diameterTopM = 0.2)
        )
        assertTrue(result is CubageResult.Valid)
        val volume = (result as CubageResult.Valid).volumeM3
        assertTrue("expected ≈0.5445427266 but was $volume", abs(volume - 0.5445427266) <= 1e-7)
    }

    @Test
    fun `ConeFrustum — matches Newton when mid diameter is the linear-taper average`() {
        // Propriété mathématique : pour un vrai tronc de cône (rayon linéaire en position),
        // Newton (Simpson à 3 sections) est exact et coïncide avec la formule fermée.
        val newton = NewtonMethod.compute(
            LogSegmentMeasurement(lengthM = 5.0, diameterBaseM = 0.5, diameterMidM = 0.4, diameterTopM = 0.3)
        ) as CubageResult.Valid
        val frustum = ConeFrustumMethod.compute(
            LogSegmentMeasurement(lengthM = 5.0, diameterBaseM = 0.5, diameterTopM = 0.3)
        ) as CubageResult.Valid
        assertTrue(abs(newton.volumeM3 - frustum.volumeM3) <= 1e-9)
    }

    @Test
    fun `ConeFrustum — missing top diameter is Blocked`() {
        val result = ConeFrustumMethod.compute(LogSegmentMeasurement(lengthM = 4.0, diameterBaseM = 0.6))
        assertTrue(result is CubageResult.Blocked)
    }

    @Test
    fun `ConeFrustum — method metadata`() {
        assertEquals("CONE_FRUSTUM", ConeFrustumMethod.id)
        assertEquals(MethodQualification.QUALIFIED, ConeFrustumMethod.qualification)
    }
}
