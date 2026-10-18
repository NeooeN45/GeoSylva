package com.forestry.counter.domain.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineTilePolicyTest {

    @Test
    fun `le domaine IGN ne qualifie pas un telechargement raster hors ligne`() {
        val error = OfflineTilePolicy.validateTemplate(
            "https://data.geopf.fr/wmts?FORMAT=image/png&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}"
        )

        assertTrue(error.orEmpty().contains("hors ligne"))
    }

    @Test
    fun `la qualification ortho en ligne ne permet pas le telechargement organise`() {
        assertTrue(
            OfflineTilePolicy.validateTemplate(MapSourcePolicy.ORTHOPHOTO_TEMPLATE)
                .orEmpty().contains("pas encore qualifié")
        )
    }

    @Test
    fun `refuse les tuiles vectorielles declarees comme raster`() {
        val error = OfflineTilePolicy.validateTemplate(
            "https://api.maptiler.com/tiles/topo/{z}/{x}/{y}.pbf?key=secret"
        )

        assertTrue(error.orEmpty().contains("vectoriel"))
    }

    @Test
    fun `refuse le telechargement massif OSM`() {
        val error = OfflineTilePolicy.validateTemplate(
            "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        )

        assertTrue(error.orEmpty().contains("hors ligne"))
    }

    @Test
    fun `refuse les fournisseurs non autorises pour les packs`() {
        listOf(
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
            "https://tile.opentopomap.org/{z}/{x}/{y}.png",
            "https://basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png",
        ).forEach { template ->
            assertTrue(OfflineTilePolicy.validateTemplate(template).orEmpty().contains("hors ligne"))
        }
    }

    @Test
    fun `refuse HTTP et les domaines inattendus`() {
        assertTrue(
            OfflineTilePolicy.validateTemplate(
                "http://data.geopf.fr/wmts?TILEMATRIX={z}&TILEROW={y}&TILECOL={x}"
            ).orEmpty().contains("HTTPS")
        )
        assertTrue(
            OfflineTilePolicy.validateTemplate(
                "https://example.org/{z}/{x}/{y}.png"
            ).orEmpty().contains("hors ligne")
        )
    }

    @Test
    fun `exige les trois coordonnees dans le modele URL`() {
        val error = OfflineTilePolicy.validateTemplate("https://data.geopf.fr/wmts?TILEMATRIX={z}")

        assertTrue(error.orEmpty().contains("coordonnées"))
    }

    @Test
    fun `valide les limites geographiques et les zooms`() {
        assertNull(OfflineTilePolicy.validateRegion(43.0, 44.0, 1.0, 2.0, 8, 16))
        assertTrue(OfflineTilePolicy.validateRegion(Double.NaN, 44.0, 1.0, 2.0, 8, 16).orEmpty().isNotBlank())
        assertTrue(OfflineTilePolicy.validateRegion(44.0, 43.0, 1.0, 2.0, 8, 16).orEmpty().contains("nord"))
        assertTrue(OfflineTilePolicy.validateRegion(43.0, 44.0, 2.0, 1.0, 8, 16).orEmpty().contains("ouest"))
        assertTrue(OfflineTilePolicy.validateRegion(-86.0, 44.0, 1.0, 2.0, 8, 16).orEmpty().contains("latitude"))
        assertTrue(OfflineTilePolicy.validateRegion(43.0, 44.0, 1.0, 2.0, 16, 8).orEmpty().contains("zoom"))
        assertTrue(OfflineTilePolicy.validateRegion(43.0, 44.0, 1.0, 2.0, 0, 23).orEmpty().contains("zoom"))
    }

    @Test
    fun `le journal ne conserve que le domaine sans secret`() {
        assertEquals(
            "api.maptiler.com",
            OfflineTilePolicy.providerIdentifier(
                "https://api.maptiler.com/tiles/topo/{z}/{x}/{y}.pbf?key=secret"
            )
        )
    }
}
