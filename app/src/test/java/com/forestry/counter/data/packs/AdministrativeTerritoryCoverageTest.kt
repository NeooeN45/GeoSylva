package com.forestry.counter.data.packs

import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class AdministrativeTerritoryCoverageTest {
    private fun asset() = sequenceOf(
        File("src/main/assets/${AdministrativeTerritoryCoverage.ASSET_PATH}"),
        File("app/src/main/assets/${AdministrativeTerritoryCoverage.ASSET_PATH}")
    ).firstOrNull(File::isFile) ?: error("Snapshot IGN du produit absent")

    @Test
    fun `relations du vrai snapshot couvrent 101 departements et 18 regions`() {
        val coverage = asset().inputStream().use(AdministrativeTerritoryCoverage::read)
        assertEquals(18, coverage.size)
        assertEquals(101, coverage.values.flatten().toSet().size)
        assertEquals(12, coverage.getValue("region:75").size)
        assertEquals(setOf("department:2A", "department:2B"), coverage.getValue("region:94"))
        assertEquals(setOf("department:971"), coverage.getValue("region:01"))
        assertEquals(setOf("department:976"), coverage.getValue("region:06"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `referentiel altere ne change pas silencieusement les groupes`() {
        val bytes = asset().readBytes().also { it[100] = (it[100].toInt() xor 1).toByte() }
        ByteArrayInputStream(bytes).use(AdministrativeTerritoryCoverage::read)
    }
}
