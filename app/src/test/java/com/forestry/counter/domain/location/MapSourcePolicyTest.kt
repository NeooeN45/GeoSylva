package com.forestry.counter.domain.location

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MapSourcePolicyTest {
    @Test
    fun `la distribution active correspond exactement a la preuve embarquee`() {
        val asset = listOf(
            File("src/main/assets/packs/orthophoto-source.json"),
            File("app/src/main/assets/packs/orthophoto-source.json"),
        ).first { it.isFile }
        val source = Json.parseToJsonElement(asset.readText()).jsonObject
        assertEquals(
            source.getValue("source").jsonObject.getValue("tile_url").jsonPrimitive.content,
            MapSourcePolicy.ORTHOPHOTO_TEMPLATE,
        )
        assertEquals(
            "qualified_commercial_reuse_with_attribution",
            source.getValue("data_rights").jsonObject.getValue("status").jsonPrimitive.content,
        )
        assertEquals(
            "qualified_for_online_commercial_application_display",
            source.getValue("service_conditions").jsonObject.getValue("status").jsonPrimitive.content,
        )
        assertEquals(
            "false",
            source.getValue("implementation_scope").jsonObject
                .getValue("bulk_download_implemented").jsonPrimitive.content,
        )
    }

    @Test
    fun `les anciens fonds non qualifies et les cles inconnues replient vers la distribution qualifiee`() {
        val pending = MapSourcePolicy.sources.filter {
            it.online == MapSourcePolicy.Qualification.QUALIFICATION_PENDING
        }
        assertTrue(pending.isNotEmpty())
        pending.forEach {
            assertFalse(MapSourcePolicy.isLayerActivatable(it.key))
            assertEquals("ORTHO_IGN", MapSourcePolicy.resolveLayerKey(it.key))
        }
        assertEquals("ORTHO_IGN", MapSourcePolicy.resolveLayerKey(null))
        assertEquals("ORTHO_IGN", MapSourcePolicy.resolveLayerKey("unknown"))
    }

    @Test
    fun `le choix local et le choix ortho sont preserves`() {
        listOf("ORTHO_IGN", "OFFLINE_LOCAL").forEach {
            assertTrue(MapSourcePolicy.isLayerActivatable(it))
            assertEquals(it, MapSourcePolicy.resolveLayerKey(it))
        }
    }

    @Test
    fun `le meme domaine un autre layer une autre matrice ou un parametre ajoute ne suffisent pas`() {
        val template = MapSourcePolicy.ORTHOPHOTO_TEMPLATE
        assertTrue(MapSourcePolicy.isOnlineTemplateQualified(template))
        listOf(
            template.replace("ORTHOIMAGERY.ORTHOPHOTOS", "CADASTRALPARCELS.PARCELLAIRE_EXPRESS"),
            template.replace("PM_0_19", "PM"),
            template.replace("https://", "http://"),
            template.replace("data.geopf.fr", "data.geopf.fr.example.org"),
            "$template&key=unexpected",
        ).forEach { assertFalse(MapSourcePolicy.isOnlineTemplateQualified(it)) }
    }

    @Test
    fun `aucun fond raster n a une qualification hors ligne presumee`() {
        assertFalse(MapSourcePolicy.isOfflineRasterTemplateQualified(MapSourcePolicy.ORTHOPHOTO_TEMPLATE))
        assertTrue(MapSourcePolicy.sources.none { it.offlineRasterDownloadQualified })
    }
}
