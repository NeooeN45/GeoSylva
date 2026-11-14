package com.quintessences.geosylva.cartography.mapbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapboxConfigurationTest {
    @Test fun `missing public token blocks renderer construction`() {
        for (token in listOf(null, "", "  ")) {
            assertEquals(MapRenderStatus.ConfigurationRequired,
                mapConfigurationIssue(token, MapboxStyles.Satellite, MapRelief()))
        }
    }

    @Test fun `a secret token is never accepted by the mobile renderer`() {
        assertEquals(MapRenderStatus.Failed(MapFailureReason.PUBLIC_TOKEN_REQUIRED),
            mapConfigurationIssue("sk.invalid-test-value", MapboxStyles.Satellite, MapRelief()))
    }

    @Test fun `enabled terrain needs a declared DEM and never silently falls back to flat`() {
        val flatStyle = MapboxStyleSpec("flat", "https://example.invalid/style.json", demSourceUrl = null)
        assertEquals(MapRenderStatus.Failed(MapFailureReason.DEM_UNAVAILABLE),
            mapConfigurationIssue("pk.invalid-test-value", flatStyle, MapRelief(enabled = true)))
        assertNull(mapConfigurationIssue("pk.invalid-test-value", flatStyle, MapRelief(enabled = false)))
        assertNull(mapConfigurationIssue("pk.invalid-test-value", MapboxStyles.Satellite, MapRelief(enabled = true)))
    }

    @Test fun `camera wrapping preserves canonical geographic position`() {
        assertEquals(-179.0, canonicalLongitude(181.0), 0.0)
        assertEquals(179.0, canonicalLongitude(-181.0), 0.0)
        assertEquals(3.25, canonicalLongitude(723.25), 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `nonfinite camera values are rejected before reaching native code`() {
        MapCameraPosition(longitude = Double.NaN, latitude = 45.0, zoom = 12.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `nonfinite exaggeration is rejected before reaching native code`() {
        MapRelief(enabled = true, exaggeration = Double.POSITIVE_INFINITY)
    }
}
