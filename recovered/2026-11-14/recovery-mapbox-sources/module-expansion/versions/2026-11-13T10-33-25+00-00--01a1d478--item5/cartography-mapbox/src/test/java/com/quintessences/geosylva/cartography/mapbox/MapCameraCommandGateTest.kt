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
        applyFit(gate,current,camera)
        assertNull(gate.observe(camera.copy(zoom=15.0),true))
        assertSame(current,gate.observe(camera,true))
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
        applyFit(gate,explicitOverview,camera)
        assertSame(explicitOverview,gate.observe(camera,true))
    }

    @Test fun `native bearing wrap and subpixel roundoff qualify while a different viewport does not`() {
        val target=camera.copy(longitude=180.0,bearing=359.9999)
        assertTrue(cameraMatches(target,target.copy(longitude=-180.0,bearing=-0.0001,
            padding=target.padding.copy(top=200.25))))
        assertFalse(cameraMatches(target,target.copy(padding=target.padding.copy(bottom=601.0))))
        assertFalse(cameraMatches(target,target.copy(zoom=15.0)))
    }

    @Test fun `a fit adopts native constrained zoom only inside its setter then requires a new idle and geometry`() {
        val gate=MapCameraCommandGate()
        val requested=camera.copy(zoom=17.778736167098458)
        val actual=requested.copy(zoom=17.779)
        val ticket=gate.submit("fit",MapCameraCommandKind.FIT,camera.padding)
        gate.resolve(ticket,requested)
        val oldIdle=gate.nextNativeIdleSequence()
        assertTrue(gate.beginNativeFitApplication(ticket,camera,"full-native-options") {true})
        gate.captureNativeCamera(actual)
        assertNull(gate.observe(actual,true))
        gate.endNativeFitApplication(ticket,true)
        gate.acceptNativeIdle(oldIdle)
        assertNull(gate.observe(actual,true))
        gate.acceptNativeIdle(gate.nextNativeIdleSequence())
        assertNull(gate.observe(actual))
        assertNull(gate.observe(requested,true))
        assertSame(ticket,gate.observe(actual,true))
    }

    @Test fun `an old getter or coalesced event cannot normalize the zoom`() {
        val gate=MapCameraCommandGate()
        val ticket=gate.submit("fit",MapCameraCommandKind.FIT,camera.padding)
        val requested=camera.copy(zoom=17.778736167098458)
        val actual=requested.copy(zoom=17.779)
        gate.resolve(ticket,requested)
        gate.captureNativeCamera(actual)
        assertTrue(gate.beginNativeFitApplication(ticket,actual,"options") {true})
        gate.captureNativeCamera(actual) // unchanged incompatible pre-view is insufficient
        gate.endNativeFitApplication(ticket,true)
        gate.acceptNativeIdle(gate.nextNativeIdleSequence())
        gate.captureNativeCamera(actual) // delayed read-back remains insufficient
        assertNull(gate.observe(actual,true))
    }

    @Test fun `native zoom adoption cannot change centre or padding`() {
        for (actual in listOf(camera.copy(zoom=12.0002,longitude=5.56),
            camera.copy(zoom=12.0002,padding=camera.padding.copy(bottom=650.0)))) {
            val gate=MapCameraCommandGate()
            val ticket=gate.submit("fit",MapCameraCommandKind.FIT,camera.padding)
            gate.resolve(ticket,camera)
            applyFit(gate,ticket,actual)
            assertNull(gate.observe(actual,true))
        }
    }

    @Test fun `context change or failed native setter cannot be acknowledged`() {
        for (returnsNormally in listOf(true,false)) {
            val gate=MapCameraCommandGate()
            var valid=true
            val ticket=gate.submit("fit",MapCameraCommandKind.FIT,camera.padding)
            gate.resolve(ticket,camera)
            assertTrue(gate.beginNativeFitApplication(ticket,camera.copy(longitude=4.0),"options") {valid})
            gate.captureNativeCamera(camera)
            if (returnsNormally) valid=false
            gate.endNativeFitApplication(ticket,returnsNormally)
            gate.acceptNativeIdle(gate.nextNativeIdleSequence())
            assertNull(gate.observe(camera,true))
        }
    }

    @Test fun `gesture during application revokes pending native read-back`() {
        val gate=MapCameraCommandGate()
        val ticket=gate.submit("fit",MapCameraCommandKind.FIT,camera.padding)
        gate.resolve(ticket,camera)
        gate.beginNativeFitApplication(ticket,camera.copy(longitude=4.0),"options") {true}
        gate.captureNativeCamera(camera)
        gate.invalidate()
        gate.endNativeFitApplication(ticket,true)
        gate.acceptNativeIdle(gate.nextNativeIdleSequence())
        assertNull(gate.observe(camera,true))
    }

    @Test fun `repeat native no-op requires an earlier verified application of the exact full key`() {
        val gate=MapCameraCommandGate()
        val requested=camera.copy(zoom=17.778736167098458)
        val actual=requested.copy(zoom=17.779)
        val first=gate.submit("first",MapCameraCommandKind.FIT,camera.padding)
        gate.resolve(first,requested)
        applyFit(gate,first,actual,key="same-full-options")
        assertSame(first,gate.observe(actual,true))
        val repeat=gate.submit("repeat",MapCameraCommandKind.FIT,camera.padding)
        gate.resolve(repeat,requested)
        applyFit(gate,repeat,actual,before=actual,key="same-full-options")
        assertSame(repeat,gate.observe(actual,true))
        val changed=gate.submit("changed",MapCameraCommandKind.FIT,camera.padding)
        gate.resolve(changed,requested)
        applyFit(gate,changed,actual,before=actual,key="different-full-options")
        assertNull(gate.observe(actual,true))
    }

    @Test fun `later terrain zoom cannot replace the setter observation and point commands stay exact`() {
        val gate=MapCameraCommandGate()
        val ticket=gate.submit("fit",MapCameraCommandKind.FIT,camera.padding)
        gate.resolve(ticket,camera)
        applyFit(gate,ticket,camera)
        val changed=camera.copy(zoom=12.1)
        gate.captureNativeCamera(changed)
        assertNull(gate.observe(changed,true))
        val point=gate.submit("point",MapCameraCommandKind.POINT,camera.padding)
        gate.resolve(point,camera)
        gate.captureNativeCamera(camera.copy(zoom=12.0002))
        assertNull(gate.observe(camera.copy(zoom=12.0002),true))
        assertSame(point,gate.observe(camera))
    }

    private fun applyFit(gate: MapCameraCommandGate, ticket: MapCameraCommandGate.Ticket,
        actual: MapCameraPosition, before: MapCameraPosition=camera.copy(longitude=4.0), key: Any="full-options") {
        assertTrue(gate.beginNativeFitApplication(ticket,before,key) {true})
        gate.captureNativeCamera(actual)
        gate.endNativeFitApplication(ticket,true)
        gate.acceptNativeIdle(gate.nextNativeIdleSequence())
    }
}
