package com.quintessences.geosylva.cartography.mapbox.offline

import org.junit.Assert.*
import org.junit.Test

class OfflineMapContractTest {
    private val geometry = """{"type":"Polygon","coordinates":[[[5.6,45.1],[5.61,45.1],[5.61,45.11],[5.6,45.11],[5.6,45.1]]]}"""
    private fun definition() = OfflineMapDefinition(geometry, 11, 14, 2f, "mapbox://styles/mapbox/satellite-v9")

    @Test fun `every requested resource and the original geometry must be verified`() {
        val complete = OfflineResourceValidation(true, true, true, true, 100, 200, null)
        assertTrue(complete.ready)
        assertFalse(complete.copy(styleComplete = false).ready)
        assertFalse(complete.copy(tilesComplete = false).ready)
        assertFalse(complete.copy(descriptorsMatch = false).ready)
        assertFalse(complete.copy(geometryMatches = false).ready)
    }
    @Test fun `snapshot identities change for every offline coverage change`() {
        val original = definition()
        val changes = listOf(original.copy(maxZoom = 15), original.copy(minZoom = 10), original.copy(pixelRatio = 3f),
            original.copy(styleUri = "mapbox://styles/mapbox/outdoors-v12"),
            original.copy(extraTilesets = listOf("mapbox://mapbox.mapbox-terrain-dem-v1")),
            original.copy(geometryJson = geometry.replace("5.61", "5.62")))
        assertTrue(changes.all { it.fingerprint != original.fingerprint })
        assertEquals(original.fingerprint, original.copy().fingerprint)
    }
    @Test fun `progress without a required count is unknown rather than complete`() {
        assertNull(OfflineResourceProgress(0, 0, 0).fraction)
        assertEquals(0.5f, OfflineResourceProgress(5, 10, 100).fraction ?: -1f, 0.0001f)
        assertEquals(1f, OfflineResourceProgress(12, 10, 100).fraction ?: -1f, 0.0001f)
    }
    @Test fun `unqualified remote styles and invalid zooms are rejected`() {
        listOf("https://example.com/style.json", "mapbox://styles/mapbox/satellite-v9?access_token=secret").forEach {
            assertThrows(IllegalArgumentException::class.java) { definition().copy(styleUri = it) }
        }
        assertThrows(IllegalArgumentException::class.java) { definition().copy(maxZoom = 23) }
        assertThrows(IllegalArgumentException::class.java) { definition().copy(minZoom = 15) }
    }
    @Test fun `native estimates preserve separate transfer storage and uncertainty`() {
        val estimate = OfflineMapEstimate(120, 250, 0.2)
        assertEquals(120, estimate.tileTransferBytes)
        assertEquals(250, estimate.tileStorageBytes)
        assertEquals(0.2, estimate.errorMargin, 0.0)
        assertThrows(IllegalArgumentException::class.java) { estimate.copy(tileTransferBytes = -1) }
    }
}
