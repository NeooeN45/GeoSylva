package com.forestry.counter.domain.calculation

import com.forestry.counter.domain.model.ParameterItem
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.repository.ParameterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StructureTriangleTest {

    /**
     * Dépôt minimal : computeG est une formule pure qui n'accède pas aux paramètres,
     * mais le constructeur de ForestryCalculator exige un ParameterRepository.
     */
    private class EmptyParameterRepository : ParameterRepository {
        override fun getAllParameters(): Flow<List<ParameterItem>> = flow { emit(emptyList()) }
        override fun getParameter(key: String): Flow<ParameterItem?> = flow { emit(null) }
        override suspend fun setParameter(item: ParameterItem) {}
        override suspend fun setParameters(items: List<ParameterItem>) {}
        override suspend fun deleteParameter(key: String) {}
    }

    private val calculator = ForestryCalculator(EmptyParameterRepository())

    private fun tige(diamCm: Double, id: String = "t$diamCm-${System.nanoTime()}"): Tige = Tige(
        id = id,
        parcelleId = "P",
        placetteId = null,
        essenceCode = "HETRE",
        diamCm = diamCm,
        hauteurM = null,
        gpsWkt = null,
        precisionM = null,
        altitudeM = null,
        note = null,
        produit = null,
        fCoef = null,
        valueEur = null
    )

    @Test
    fun `should_return_type_1_when_peuplement_jeune_pb_dominant`() {
        // 15 perches de PB (12,5 cm) + 1 GB (35 cm) → PB dominant
        val tiges = List(15) { tige(12.5) } + tige(35.0)

        val triangle = computeStructureTriangle(tiges, calculator)

        assertEquals(1, triangle.structureType)
        assertTrue("PB doit dominer : pbPct=${triangle.pbPct}", triangle.pbPct >= 60.0)
        assertTrue("BM doit être faible : bmPct=${triangle.bmPct}", triangle.bmPct < 25.0)
    }

    @Test
    fun `should_return_type_5_when_peuplement_mature_gb_dominant`() {
        // 10 GB (35 cm) + 1 BM (22,5 cm) → GB dominant
        val tiges = List(10) { tige(35.0) } + tige(22.5)

        val triangle = computeStructureTriangle(tiges, calculator)

        assertEquals(5, triangle.structureType)
        assertTrue("GB+TGB doit dominer : gbTgbPct=${triangle.gbPct + triangle.tgbPct}", triangle.gbPct + triangle.tgbPct >= 60.0)
        assertTrue("BM doit être faible : bmPct=${triangle.bmPct}", triangle.bmPct < 25.0)
    }

    @Test
    fun `should_return_type_7_when_peuplement_equilibre`() {
        // 20 PB + 6 BM + 2 GB → équilibré (pb≥30, bm≥30, gbTgb≥20)
        val tiges = List(20) { tige(12.5) } + List(6) { tige(22.5) } + List(2) { tige(35.0) }

        val triangle = computeStructureTriangle(tiges, calculator)

        assertEquals(7, triangle.structureType)
        assertTrue("pbPct=${triangle.pbPct} >= 30", triangle.pbPct >= 30.0)
        assertTrue("bmPct=${triangle.bmPct} >= 30", triangle.bmPct >= 30.0)
        assertTrue("gbTgbPct=${triangle.gbPct + triangle.tgbPct} >= 20", triangle.gbPct + triangle.tgbPct >= 20.0)
    }

    @Test
    fun `should_return_all_zeros_when_peuplement_vide`() {
        val triangle = computeStructureTriangle(emptyList(), calculator)

        assertEquals(0.0, triangle.perchesPct, 1e-9)
        assertEquals(0.0, triangle.pbPct, 1e-9)
        assertEquals(0.0, triangle.bmPct, 1e-9)
        assertEquals(0.0, triangle.gbPct, 1e-9)
        assertEquals(0.0, triangle.tgbPct, 1e-9)
        assertEquals(0, triangle.structureType)
        assertEquals(0, triangle.cnpfCode)
    }

    @Test
    fun `should_return_cnpf_code_4_when_tgb_present`() {
        // 2 TGB (55 cm) + 5 PB (12,5 cm) → tgbPct >= 10 → code CNPF 4
        val tiges = List(2) { tige(55.0) } + List(5) { tige(12.5) }

        val triangle = computeStructureTriangle(tiges, calculator)

        assertEquals(4, triangle.cnpfCode)
        assertTrue("tgbPct=${triangle.tgbPct} >= 10", triangle.tgbPct >= 10.0)
    }

    @Test
    fun `should_return_cnpf_code_1_when_peuplement_jeune_sans_bm_majeur`() {
        // 10 PB (12,5 cm) + 2 BM (22,5 cm) → bmPct < 40, pas de GB/TGB → code 1
        val tiges = List(10) { tige(12.5) } + List(2) { tige(22.5) }

        val triangle = computeStructureTriangle(tiges, calculator)

        assertEquals(1, triangle.cnpfCode)
        assertTrue("bmPct=${triangle.bmPct} < 40", triangle.bmPct < 40.0)
    }

    @Test
    fun `should_sum_percentages_to_100`() {
        val tiges = List(5) { tige(5.0) } + List(5) { tige(12.5) } +
            List(5) { tige(22.5) } + List(5) { tige(35.0) } + List(2) { tige(55.0) }

        val triangle = computeStructureTriangle(tiges, calculator)

        val sum = triangle.perchesPct + triangle.pbPct + triangle.bmPct + triangle.gbPct + triangle.tgbPct
        assertEquals(100.0, sum, 1e-6)
    }
}
