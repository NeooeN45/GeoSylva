package com.quintessences.geosylva.cartography.mapbox

import org.junit.Assert.*
import org.junit.Test

class MapStatusBarPolicyTest {
    @Test fun `photography wins over repeated theme effects then releases to the latest theme`() {
        val policy = MapStatusBarPolicy(true); val map = Any()
        assertTrue(policy.darkIcons)
        policy.acquire(map,false); assertFalse(policy.darkIcons)
        policy.themeChanged(true); assertFalse(policy.darkIcons)
        policy.themeChanged(false); assertFalse(policy.darkIcons)
        policy.release(map); assertFalse(policy.darkIcons)
        policy.themeChanged(true); assertTrue(policy.darkIcons)
    }
    @Test fun `late disposal cannot release another active map owner`() {
        val policy = MapStatusBarPolicy(true); val before = Any(); val after = Any()
        policy.acquire(before,false); policy.acquire(after,false); policy.release(before)
        assertFalse(policy.darkIcons)
        policy.release(after); assertTrue(policy.darkIcons)
    }
    @Test fun `explicit bright and night maps override an opposite app theme without URL heuristics`() {
        val policy = MapStatusBarPolicy(false); val day = Any(); val night = Any()
        val dayStyle = MapboxStyles.Plan
        val nightStyle = MapboxStyleSpec("TEST-night","mapbox://styles/TEST/style",backgroundIsDark = true)
        assertEquals(true,dayStyle.preferredStatusBarDarkIcons())
        assertEquals(false,nightStyle.preferredStatusBarDarkIcons())
        policy.acquire(day,requireNotNull(dayStyle.preferredStatusBarDarkIcons())); assertTrue(policy.darkIcons)
        policy.themeChanged(true)
        policy.acquire(night,requireNotNull(nightStyle.preferredStatusBarDarkIcons())); assertFalse(policy.darkIcons)
        policy.release(day); assertFalse(policy.darkIcons)
        policy.release(night); assertTrue(policy.darkIcons)
        assertNull(MapboxStyleSpec("TEST-unknown","mapbox://styles/TEST/unknown").preferredStatusBarDarkIcons())
    }
    @Test fun `only a full width map at the window top may override status bar icons`() {
        assertTrue(mapCoversStatusBar(0f,0f,720,64,720,42))
        assertFalse(mapCoversStatusBar(0f,60f,720,64,720,42))
        assertFalse(mapCoversStatusBar(40f,0f,640,64,720,42))
        assertFalse(mapCoversStatusBar(0f,0f,720,64,720,0))
        assertFalse(mapCoversStatusBar(Float.NaN,0f,720,64,720,42))
        assertFalse(mapCoversStatusBar(0f,0f,720,20,720,42))
        assertFalse(mapCoversStatusBar(0f,-60f,720,64,720,42))
        assertTrue(MapboxStyles.Satellite.isPhotographic); assertTrue(MapboxStyles.SatelliteLabels.isPhotographic)
        assertFalse(MapboxStyles.Plan.isPhotographic); assertFalse(MapboxStyles.Topographic.isPhotographic)
    }
}
