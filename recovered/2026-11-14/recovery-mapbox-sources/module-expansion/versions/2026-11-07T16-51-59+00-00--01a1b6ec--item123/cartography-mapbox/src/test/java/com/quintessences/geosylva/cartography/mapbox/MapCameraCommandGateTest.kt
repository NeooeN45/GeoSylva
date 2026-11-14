package com.quintessences.geosylva.cartography.mapbox

import org.junit.Assert.*
import org.junit.Test

class MapCameraCommandGateTest {
    private val camera = MapCameraPosition(5.55,45.07,12.0,pitch=48.0,
        padding=MapCameraPadding(200.0,112.0,600.0,196.0))

    @Test fun `calculation and old padding cannot consume an intended camera`() {
        val gate=MapCameraCommandGate()
        val ticket=gate.submit("place-42",MapCameraCommandKind.POINT,camera.padding)
        assertNull(gate.observe(camera))
        assertTrue(gate.resolve(ticket,camera))
        assertNull(gate.observe(camera.copy(padding=MapCameraPadding(402.5,112.0,189.0,196.0))))
        assertSame(ticket,gate.observe(camera))
        assertNull(gate.observe(camera))
    }

    @Test fun `a late fit calculation cannot replace a newer chrome or route intent`() {
        val gate=MapCameraCommandGate()
        val old=gate.submit("route-before-layout",MapCameraCommandKind.FIT,camera.padding)
        val current=gate.submit("route-after-layout",MapCameraCommandKind.FIT,camera.padding)
        assertFalse(gate.resolve(old,camera.copy(zoom=15.0)))
        assertTrue(gate.resolve(current,camera))
        assertNull(gate.observe(camera.copy(zoom=15.0)))
        assertSame(current,gate.observe(camera))
    }

    @Test fun `a real user gesture invalidates both pending async fit and pending observation`() {
        val gate=MapCameraCommandGate()
        val ticket=gate.submit("route-47",MapCameraCommandKind.FIT,camera.padding)
        assertTrue(gate.resolve(ticket,camera))
        assertSame(ticket,gate.invalidate())
        assertFalse(gate.isCurrent(ticket))
        assertFalse(gate.resolve(ticket,camera))
        assertNull(gate.observe(camera))
        val explicitOverview=gate.submit("new-overview",MapCameraCommandKind.FIT,camera.padding)
        assertTrue(gate.resolve(explicitOverview,camera))
        assertSame(explicitOverview,gate.observe(camera))
    }

    @Test fun `native bearing wrap and subpixel roundoff qualify while a different viewport does not`() {
        val target=camera.copy(longitude=180.0,bearing=359.9999)
        assertTrue(cameraMatches(target,target.copy(longitude=-180.0,bearing=-0.0001,
            padding=target.padding.copy(top=200.25))))
        assertFalse(cameraMatches(target,target.copy(padding=target.padding.copy(bottom=601.0))))
        assertFalse(cameraMatches(target,target.copy(zoom=15.0)))
    }
}
