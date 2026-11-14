package com.quintessences.geosylva.cartography.mapbox

import org.junit.Assert.*
import org.junit.Test

class MapStatusBarPolicyTest {
    @Test fun `photography wins over repeated theme effects then releases to the latest theme`() {
        val policy = MapStatusBarPolicy(true); val map = Any()
        assertTrue(policy.darkIcons)
        policy.acquire(map); assertFalse(policy.darkIcons)
        policy.themeChanged(true); assertFalse(policy.darkIcons)
        policy.themeChanged(false); assertFalse(policy.darkIcons)
        policy.release(map); assertFalse(policy.darkIcons)
        policy.themeChanged(true); assertTrue(policy.darkIcons)
    }
    @Test fun `late disposal cannot release another active map owner`() {
        val policy = MapStatusBarPolicy(true); val before = Any(); val after = Any()
        policy.acquire(before); policy.acquire(after); policy.release(before)
        assertFalse(policy.darkIcons)
        policy.release(after); assertTrue(policy.darkIcons)
    }
    @Test fun `only a full width map at the window top may override status bar icons`() {
        assertTrue(mapCoversStatusBar(0f,0f,720,720,42))
        assertFalse(mapCoversStatusBar(0f,60f,720,720,42))
        assertFalse(mapCoversStatusBar(40f,0f,640,720,42))
        assertFalse(mapCoversStatusBar(0f,0f,720,720,0))
        assertFalse(mapCoversStatusBar(Float.NaN,0f,720,720,42))
        assertTrue(MapboxStyles.Satellite.isPhotographic); assertTrue(MapboxStyles.SatelliteLabels.isPhotographic)
        assertFalse(MapboxStyles.Plan.isPhotographic); assertFalse(MapboxStyles.Topographic.isPhotographic)
    }
}
