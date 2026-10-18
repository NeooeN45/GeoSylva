package com.forestry.counter.presentation.screens.forestry

import com.forestry.counter.domain.location.MapSourcePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapLayerCommercialPolicyTest {
    @Test
    fun `le catalogue consomme uniquement ortho qualifie et le fond local`() {
        assertEquals(setOf("ORTHO_IGN", "OFFLINE_LOCAL"), MAP_LAYERS.map { it.key }.toSet())
        val ortho = MAP_LAYERS.first { it.key == "ORTHO_IGN" }
        assertEquals(listOf(MapSourcePolicy.ORTHOPHOTO_TEMPLATE), ortho.tileUrls)
        assertTrue(ortho.attribution.contains("11/09/2026"))
        assertTrue(ortho.attribution.contains("prises de vue variables"))
        assertTrue(REFERENCED_MAP_LAYERS.any { it.key == "SATELLITE" })
        assertTrue(REFERENCED_MAP_LAYERS.any { it.key == "PLAN_IGN_CADASTRE" })
    }

    @Test
    fun `un fond non qualifie ou composite ne genere aucune source cartographique distante`() {
        val esri = REFERENCED_MAP_LAYERS.first { it.key == "SATELLITE" }
        assertFalse(activeStyleJsonFor(esri).contains("arcgisonline"))
        val composite = rasterStyleMulti(
            "test",
            "test",
            MapSourcePolicy.ORTHOPHOTO_TEMPLATE,
            listOf(geopfLayer("CADASTRALPARCELS.PARCELLAIRE_EXPRESS")),
        )
        assertFalse(composite.contains("https://"))
        assertTrue(composite.contains("\"sources\": {}"))
    }
}
