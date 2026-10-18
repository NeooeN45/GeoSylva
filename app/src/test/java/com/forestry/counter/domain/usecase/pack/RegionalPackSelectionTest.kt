package com.forestry.counter.domain.usecase.pack

import com.forestry.counter.domain.model.pack.GeoPackDescriptor
import com.forestry.counter.domain.model.pack.PackFeatures
import com.forestry.counter.domain.model.pack.PackLevel
import com.forestry.counter.domain.model.pack.PackStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class RegionalPackSelectionTest {
    private fun region(code: String) = GeoPackDescriptor(
        "fr-region-$code", PackLevel.REGIONAL, "Fixture JVM région $code", code,
        version = "test-v1", buildDate = "test-only", sizeKb = 0L, status = PackStatus.AVAILABLE,
        features = PackFeatures(), territoryId = "region:$code", contentKind = "administrative_boundaries"
    )
    private fun department(code: String, parent: String) = region(parent).copy(
        id = "fr-department-$code", level = PackLevel.DEPARTEMENTAL, territoryId = "department:$code",
        parentId = "fr-region-$parent", codeINSEE = code
    )

    @Test
    fun `selection de plusieurs regions est une union sans doublon ni voisin implicite`() {
        val first = region("75")
        val second = region("76")
        val children = listOf(department("16", "75"), department("17", "75"), department("30", "76"))
        val excluded = department("18", "24")
        val catalog = listOf(first, second) + children + excluded
        val selected = RegionalPackSelection.resolve(catalog, setOf(first.id, second.id, children.first().id), mapOf(
            "region:75" to setOf("department:16", "department:17"), "region:76" to setOf("department:30")
        ))
        assertEquals((listOf(first, second) + children).toSet(), selected.toSet())
        assertEquals(5, selected.size)
        assertEquals(selected.toSet(), RegionalPackSelection.visibleSelection(catalog, setOf("region:75", "region:76", "department:16")).toSet())
    }

    @Test(expected = IncompleteRegionalPackException::class)
    fun `rattachement catalogue divergent du referentiel refuse le groupe`() {
        val region = region("75")
        RegionalPackSelection.resolve(listOf(region, department("16", "76")), setOf(region.id),
            mapOf("region:75" to setOf("department:16")))
    }

    @Test(expected = IncompleteRegionalPackException::class)
    fun `enfant supplementaire sous mauvais parent refuse le groupe`() {
        val region = region("75")
        RegionalPackSelection.resolve(listOf(region, department("16", "75"), department("30", "75")), setOf(region.id),
            mapOf("region:75" to setOf("department:16")))
    }

    @Test(expected = IncompleteRegionalPackException::class)
    fun `deux publications pour un meme territoire ne masquent pas un enfant manquant`() {
        val region = region("75")
        val child = department("16", "75")
        RegionalPackSelection.resolve(listOf(region, child, child.copy(id = "test-only-duplicate")), setOf(region.id),
            mapOf("region:75" to setOf("department:16", "department:17")))
    }

    @Test(expected = IncompleteRegionalPackException::class)
    fun `region inconnue du referentiel ne suppose pas un groupe vide`() {
        val region = region("75")
        RegionalPackSelection.resolve(listOf(region), setOf(region.id), emptyMap())
    }
}
