package com.forestry.counter.domain.calculation

import com.forestry.counter.domain.model.ParameterItem
import com.forestry.counter.domain.repository.ParameterRepository
import com.forestry.counter.domain.model.Tige
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
class ExpertForestryCalculatorTest {

    // region --- Test infrastructure ---

    private class FakeParameterRepository(initial: Map<String, String>) : ParameterRepository {
        private val data = initial.toMutableMap()

        override fun getAllParameters(): Flow<List<ParameterItem>> = flow {
            emit(data.map { (k, v) -> ParameterItem(key = k, valueJson = v) })
        }

        override fun getParameter(key: String): Flow<ParameterItem?> = flow {
            emit(data[key]?.let { ParameterItem(key = key, valueJson = it) })
        }

        override suspend fun setParameter(item: ParameterItem) {
            data[item.key] = item.valueJson
        }

        override suspend fun setParameters(items: List<ParameterItem>) {
            items.forEach { data[it.key] = it.valueJson }
        }

        override suspend fun deleteParameter(key: String) {
            data.remove(key)
        }
    }

    private fun buildCalculator(): ExpertForestryCalculator {
        val fakeRepo = FakeParameterRepository(emptyMap())
        val base = ForestryCalculator(fakeRepo)
        return ExpertForestryCalculator(base, fakeRepo)
    }

    // endregion

    // =====================================================================
    // 1. calculateIndiceDeStation
    // =====================================================================

    @Test
    fun `should_return_is_within_bounds_when_chene_adult_stand`() {
        val calc = buildCalculator()
        val indiceStation = calc.calculateIndiceDeStation("QUPE", 80, 20.0, 35.0)

        assertTrue("IS should be >= 5", indiceStation >= 5.0)
        assertTrue("IS should be <= 30", indiceStation <= 30.0)
    }

    @Test
    fun `should_return_is_within_bounds_when_hetre_adult_stand`() {
        val calc = buildCalculator()
        val indiceStation = calc.calculateIndiceDeStation("FASY", 80, 22.0, 30.0)

        assertTrue("IS should be >= 10", indiceStation >= 10.0)
        assertTrue("IS should be <= 28", indiceStation <= 28.0)
    }

    @Test
    fun `should_return_default_is_when_unknown_essence`() {
        val calc = buildCalculator()
        val indiceStation = calc.calculateIndiceDeStation("UNKNOWN_SPECIES", 60, 18.0, 25.0)

        assertEquals(15.0, indiceStation, 0.001)
    }

    @Test
    fun `should_cap_is_at_30_when_extreme_values`() {
        val calc = buildCalculator()
        // Very large height and diameter should be capped by coerceAtMost(30.0)
        val indiceStation = calc.calculateIndiceDeStation("QUPE", 100, 100.0, 200.0)

        assertTrue("IS should not exceed 30", indiceStation <= 30.0)
    }

    @Test
    fun `should_compute_chene_is_with_known_formula`() {
        val calc = buildCalculator()
        // C-CALC-2 : IS ≈ Hdom (proxy en l'absence de l'âge de référence).
        // QUPE avec Hdom = 20.0 m -> IS = 20.0 (borné 5–30).
        val indiceStation = calc.calculateIndiceDeStation("QUPE", 80, 20.0, 35.0)

        assertEquals(20.0, indiceStation, 0.001)
    }

    @Test
    fun `should_compute_hetre_is_with_known_formula`() {
        val calc = buildCalculator()
        // C-CALC-2 : IS ≈ Hdom (proxy en l'absence de l'âge de référence).
        // FASY avec Hdom = 22.0 m -> IS = 22.0 (borné 5–30).
        val indiceStation = calc.calculateIndiceDeStation("FASY", 80, 22.0, 30.0)

        assertEquals(22.0, indiceStation, 0.001)
    }

    // =====================================================================
    // 1b. computeHdom — hauteur dominante (norme ONF)
    // =====================================================================

    private fun tige(id: String, diamCm: Double, hauteurM: Double?) =
        Tige(id = id, parcelleId = "p", placetteId = null, essenceCode = "QUPE",
            diamCm = diamCm, hauteurM = hauteurM, gpsWkt = null, precisionM = null,
            altitudeM = null, note = null, produit = null, fCoef = null, valueEur = null)

    @Test
    fun `should_return_null_when_no_heights_available`() {
        val calc = buildCalculator()
        val tiges = listOf(tige("a", 30.0, null), tige("b", 40.0, null))
        val hdom = calc.computeHdom(tiges, surfaceHa = 0.05)

        assertEquals(null, hdom)
    }

    @Test
    fun `should_average_top_100_per_ha_diameters`() {
        val calc = buildCalculator()
        // surfaceHa = 0.02 -> N = ceil(100 * 0.02) = 2 plus gros arbres
        val tiges = listOf(
            tige("a", 50.0, 25.0),
            tige("b", 45.0, 23.0),
            tige("c", 20.0, 15.0)
        )
        val hdom = calc.computeHdom(tiges, surfaceHa = 0.02)

        // 2 plus gros : 50cm/25m et 45cm/23m -> moyenne = 24.0
        assertEquals(24.0, hdom!!, 0.001)
    }

    @Test
    fun `should_use_all_trees_when_fewer_than_n_target`() {
        val calc = buildCalculator()
        // surfaceHa = 1.0 -> N = 100, mais seulement 2 arbres disponibles
        val tiges = listOf(tige("a", 50.0, 25.0), tige("b", 45.0, 23.0))
        val hdom = calc.computeHdom(tiges, surfaceHa = 1.0)

        assertEquals(24.0, hdom!!, 0.001)
    }

    @Test
    fun `should_return_null_when_empty_tiges`() {
        val calc = buildCalculator()
        val hdom = calc.computeHdom(emptyList(), surfaceHa = 1.0)

        assertEquals(null, hdom)
    }

    // =====================================================================
    // 2. getClasseStation
    // =====================================================================

    @Test
    fun `should_return_classe_1_when_is_very_low`() {
        val calc = buildCalculator()
        val classe = calc.getClasseStation(5.0, "QUPE")

        assertEquals(1, classe)
    }

    @Test
    fun `should_return_classe_2_when_is_10`() {
        val calc = buildCalculator()
        val classe = calc.getClasseStation(10.0, "QUPE")

        assertEquals(2, classe)
    }

    @Test
    fun `should_return_classe_5_when_is_20_chene`() {
        val calc = buildCalculator()
        // IS=20 for QUPE: 20 < 24 -> classe 5
        val classe = calc.getClasseStation(20.0, "QUPE")

        assertEquals(5, classe)
    }

    @Test
    fun `should_return_classe_max_when_is_28_chene`() {
        val calc = buildCalculator()
        // IS=28 for QUPE: >= 28 -> classe 7 (max for chene)
        val classe = calc.getClasseStation(28.0, "QUPE")

        assertEquals(7, classe)
    }

    @Test
    fun `should_return_classe_6_when_is_28_hetre`() {
        val calc = buildCalculator()
        // IS=28 for FASY: >= 24 -> classe 6 (max for hetre)
        val classe = calc.getClasseStation(28.0, "FASY")

        assertEquals(6, classe)
    }

    @Test
    fun `should_return_default_classe_when_unknown_essence`() {
        val calc = buildCalculator()
        val classe = calc.getClasseStation(15.0, "UNKNOWN")

        assertEquals(3, classe)
    }

    // =====================================================================
    // 3. getProductionData — coherence checks
    // =====================================================================

    @Test
    fun `should_return_higher_volume_at_age_80_than_age_40_chene`() {
        val calc = buildCalculator()
        val dataAge40 = calc.getProductionData("QUPE", 1, 40)
        val dataAge80 = calc.getProductionData("QUPE", 1, 80)

        assertNotNull(dataAge40)
        assertNotNull(dataAge80)
        assertTrue(
            "Volume at age 80 should exceed volume at age 40",
            dataAge80!!.volumeTotal > dataAge40!!.volumeTotal
        )
    }

    @Test
    fun `should_return_higher_height_at_age_100_than_age_60_chene`() {
        val calc = buildCalculator()
        val dataAge60 = calc.getProductionData("QUPE", 2, 60)
        val dataAge100 = calc.getProductionData("QUPE", 2, 100)

        assertNotNull(dataAge60)
        assertNotNull(dataAge100)
        assertTrue(
            "Height at age 100 should exceed height at age 60",
            dataAge100!!.hauteurMoyenne > dataAge60!!.hauteurMoyenne
        )
    }

    @Test
    fun `should_interpolate_production_data_at_intermediate_age`() {
        val calc = buildCalculator()
        // Age 50 is between table entries 40 and 60
        val dataAge50 = calc.getProductionData("QUPE", 1, 50)

        assertNotNull(dataAge50)
        val dataAge40 = calc.getProductionData("QUPE", 1, 40)!!
        val dataAge60 = calc.getProductionData("QUPE", 1, 60)!!

        assertTrue(
            "Interpolated volume should be between age 40 and age 60 values",
            dataAge50!!.volumeTotal > dataAge40.volumeTotal &&
                dataAge50.volumeTotal < dataAge60.volumeTotal
        )
    }

    @Test
    fun `should_return_null_when_unknown_essence_production_data`() {
        val calc = buildCalculator()
        val data = calc.getProductionData("UNKNOWN", 1, 80)

        assertEquals(null, data)
    }

    @Test
    fun `should_return_monotone_volume_growth_for_hetre`() {
        val calc = buildCalculator()
        val ages = listOf(20, 40, 60, 80, 100, 120, 140, 160)
        val volumes = ages.mapNotNull { age ->
            calc.getProductionData("FASY", 2, age)?.volumeTotal
        }

        assertEquals("All ages should have production data", ages.size, volumes.size)
        for (i in 1 until volumes.size) {
            assertTrue(
                "Volume should be monotonically increasing (age ${ages[i]})",
                volumes[i] > volumes[i - 1]
            )
        }
    }

    // =====================================================================
    // 4. computeSurfaceTerriere — G = sum(pi * (D/200)^2)
    // =====================================================================

    @Test
    fun `should_compute_surface_terriere_for_single_tree`() {
        val calc = buildCalculator()
        // D=40cm -> radius=0.2m -> G = pi * 0.04 = 0.12566...
        val surfaceTerriere = calc.computeSurfaceTerriere(listOf(40.0))

        val expected = PI * (40.0 / 200.0) * (40.0 / 200.0)
        assertEquals(expected, surfaceTerriere, 0.001)
    }

    @Test
    fun `should_compute_surface_terriere_for_two_trees`() {
        val calc = buildCalculator()
        // Two trees D=30cm each -> G = 2 * pi * (0.15)^2 = 2 * 0.07069 = 0.14137
        val surfaceTerriere = calc.computeSurfaceTerriere(listOf(30.0, 30.0))

        val expectedSingle = PI * (30.0 / 200.0) * (30.0 / 200.0)
        val expected = 2.0 * expectedSingle
        assertEquals(expected, surfaceTerriere, 0.001)
    }

    @Test
    fun `should_return_zero_surface_terriere_when_empty_list`() {
        val calc = buildCalculator()
        val surfaceTerriere = calc.computeSurfaceTerriere(emptyList())

        assertEquals(0.0, surfaceTerriere, 0.0)
    }

    @Test
    fun `should_compute_surface_terriere_for_mixed_diameters`() {
        val calc = buildCalculator()
        val diameters = listOf(20.0, 30.0, 50.0)
        val surfaceTerriere = calc.computeSurfaceTerriere(diameters)

        val expected = diameters.sumOf { d -> PI * (d / 200.0) * (d / 200.0) }
        assertEquals(expected, surfaceTerriere, 0.001)
    }

    // =====================================================================
    // 5. schumacherHallVolume — V = exp(a + b*ln(D) + c*ln(H))
    // =====================================================================

    @Test
    fun `should_return_positive_volume_for_chene`() {
        val calc = buildCalculator()
        val volume = calc.schumacherHallVolume("QUPE", 40.0, 22.0)

        assertTrue("Volume should be positive", volume > 0.0)
    }

    @Test
    fun `should_increase_volume_with_diameter_at_constant_height`() {
        val calc = buildCalculator()
        val volumeSmall = calc.schumacherHallVolume("QUPE", 20.0, 22.0)
        val volumeLarge = calc.schumacherHallVolume("QUPE", 40.0, 22.0)

        assertTrue(
            "Volume should increase with diameter",
            volumeLarge > volumeSmall
        )
    }

    @Test
    fun `should_increase_volume_with_height_at_constant_diameter`() {
        val calc = buildCalculator()
        val volumeShort = calc.schumacherHallVolume("QUPE", 40.0, 15.0)
        val volumeTall = calc.schumacherHallVolume("QUPE", 40.0, 25.0)

        assertTrue(
            "Volume should increase with height",
            volumeTall > volumeShort
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `should_throw_when_diameter_is_zero`() {
        val calc = buildCalculator()
        calc.schumacherHallVolume("QUPE", 0.0, 22.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `should_throw_when_diameter_is_negative`() {
        val calc = buildCalculator()
        calc.schumacherHallVolume("QUPE", -5.0, 22.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `should_throw_when_height_is_zero`() {
        val calc = buildCalculator()
        calc.schumacherHallVolume("QUPE", 40.0, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `should_throw_when_height_is_negative`() {
        val calc = buildCalculator()
        calc.schumacherHallVolume("QUPE", 40.0, -3.0)
    }

    @Test
    fun `should_compute_known_schumacher_hall_value_for_chene`() {
        val calc = buildCalculator()
        // QUPE params: a=-2.0, b=2.0, c=1.0
        // V = exp(-2.0 + 2.0*ln(40) + 1.0*ln(22))
        val expected = kotlin.math.exp(-2.0 + 2.0 * kotlin.math.ln(40.0) + 1.0 * kotlin.math.ln(22.0))
        val volume = calc.schumacherHallVolume("QUPE", 40.0, 22.0)

        assertEquals(expected, volume, 0.001)
    }

    @Test
    fun `should_compute_different_volumes_for_different_essences`() {
        val calc = buildCalculator()
        val volumeChene = calc.schumacherHallVolume("QUPE", 40.0, 22.0)
        val volumeHetre = calc.schumacherHallVolume("FASY", 40.0, 22.0)

        // Different species should yield different volumes (different parameters)
        assertTrue(
            "Different essences should produce different volumes",
            abs(volumeChene - volumeHetre) > 0.01
        )
    }
}
