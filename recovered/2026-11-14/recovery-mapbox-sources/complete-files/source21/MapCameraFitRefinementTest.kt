package com.quintessences.geosylva.cartography.mapbox

import org.junit.Assert.*
import org.junit.Test

class MapCameraFitRefinementTest {
    private val padding = MapCameraPadding(100.0,50.0,200.0,70.0)
    private val camera = MapCameraPosition(5.4681,45.0544,16.592,padding=padding)
    private val inside = listOf(MapFitPixel(60.0,110.0),MapFitPixel(620.0,790.0))
    private fun policy(minZoom: Double = 0.0, max: Int = 8) =
        MapCameraFitRefinement(720,1000,padding,2,minZoom,100L,max)
    private fun retry(value: MapFitDecision): MapFitAttempt {
        assertTrue(value.toString(),value is MapFitDecision.Retry)
        return (value as MapFitDecision.Retry).attempt
    }

    @Test fun `no measurement is accepted before its own proposal was applied`() {
        val fit=policy()
        assertEquals(MapFitDecision.Waiting,fit.observe(camera,inside,101L))
        assertTrue(fit.applied(fit.initialAttempt))
        assertEquals(MapFitDecision.Verified,fit.observe(camera,inside,102L))
        assertEquals(MapFitDecision.Waiting,fit.observe(camera,inside,103L))
    }

    @Test fun `finite residuals accumulate without changing absolute padding or zoom policy`() {
        val fit=policy()
        fit.applied(fit.initialAttempt)
        val second=retry(fit.observe(camera,listOf(MapFitPixel(42.0,110.0),MapFitPixel(620.0,807.0)),101L))
        assertEquals(MapCameraPadding(left=8.0,bottom=7.0),second.coordinatesPadding)
        assertNull(second.maxZoom)
        assertEquals(padding,camera.padding)
        assertEquals(MapFitDecision.Waiting,fit.observe(camera,inside,102L))
        assertFalse(fit.applied(fit.initialAttempt))
        fit.applied(second)
        val third=retry(fit.observe(camera.copy(zoom=16.55),
            listOf(MapFitPixel(47.0,110.0),MapFitPixel(620.0,802.0)),103L))
        assertEquals(MapCameraPadding(left=11.0,bottom=9.0),third.coordinatesPadding)
        assertNull(third.maxZoom)
    }

    @Test fun `censored projections bracket ground scale with a standard bounded max zoom`() {
        val fit=policy(minZoom=14.0)
        fit.applied(fit.initialAttempt)
        val second=retry(fit.observe(camera,listOf(MapFitPixel(-1.0,-1.0),inside[1]),101L))
        assertEquals(15.592,second.maxZoom!!,1e-12)
        assertEquals(MapCameraPadding(),second.coordinatesPadding)
        fit.applied(second)
        val third=retry(fit.observe(camera.copy(zoom=15.592),listOf(MapFitPixel(-1.0,-1.0),inside[1]),102L))
        assertEquals(14.592,third.maxZoom!!,1e-12)
        fit.applied(third)
        val fourth=retry(fit.observe(camera.copy(zoom=14.592),listOf(MapFitPixel(-1.0,-1.0),inside[1]),103L))
        assertEquals(14.0,fourth.maxZoom!!,0.0)
        fit.applied(fourth)
        val failed=fit.observe(camera.copy(zoom=14.0),listOf(MapFitPixel(-1.0,-1.0),inside[1]),104L)
        assertEquals(MapFitDecision.Failed(MapFitFailure.MIN_ZOOM),failed)
    }

    @Test fun `all inside stops immediately without cosmetic maximization`() {
        val fit=policy()
        fit.applied(fit.initialAttempt)
        val next=retry(fit.observe(camera,listOf(MapFitPixel(-1.0,-1.0),inside[1]),101L))
        fit.applied(next)
        assertEquals(MapFitDecision.Verified,fit.observe(camera.copy(zoom=15.592),inside,102L))
        assertFalse(fit.isCurrent(next))
    }

    @Test fun `finite padding remains accumulated after a later censored result`() {
        val fit=policy()
        fit.applied(fit.initialAttempt)
        val padded=retry(fit.observe(camera,listOf(MapFitPixel(42.0,110.0),inside[1]),101L))
        fit.applied(padded)
        val bracket=retry(fit.observe(camera.copy(zoom=16.5),listOf(MapFitPixel(-1.0,-1.0),inside[1]),102L))
        assertEquals(padded.coordinatesPadding,bracket.coordinatesPadding)
        assertEquals(15.5,bracket.maxZoom!!,0.0)
    }

    @Test fun `repeated native camera and projections fail closed instead of spinning`() {
        val fit=policy()
        val outside=listOf(MapFitPixel(42.0,110.0),inside[1])
        fit.applied(fit.initialAttempt)
        val next=retry(fit.observe(camera,outside,101L))
        fit.applied(next)
        assertEquals(MapFitDecision.Failed(MapFitFailure.NO_PROGRESS),fit.observe(camera,outside,102L))
        assertEquals(MapFitDecision.Waiting,fit.observe(camera,inside,103L))
    }

    @Test fun `application count and elapsed time each bound work without geometry acknowledgement`() {
        val count=policy(max=1)
        count.applied(count.initialAttempt)
        assertEquals(MapFitDecision.Failed(MapFitFailure.BUDGET_EXHAUSTED),
            count.observe(camera,listOf(MapFitPixel(-1.0,-1.0),inside[1]),101L))
        val timed=policy()
        timed.applied(timed.initialAttempt)
        assertEquals(MapFitDecision.Failed(MapFitFailure.BUDGET_EXHAUSTED),timed.observe(camera,inside,12_100L))
    }

    @Test fun `partial measurements nonfinite projections and an invalid pane cannot fake a fit`() {
        val partial=policy();partial.applied(partial.initialAttempt)
        assertEquals(MapFitDecision.Failed(MapFitFailure.INVALID_MEASUREMENT),partial.observe(camera,inside.take(1),101L))
        val nonfinite=policy();nonfinite.applied(nonfinite.initialAttempt)
        assertNotNull(retry(nonfinite.observe(camera,listOf(MapFitPixel(Double.NaN,0.0),inside[1]),101L)).maxZoom)
        val invalid=MapCameraFitRefinement(100,100,padding,2,0.0,100L)
        invalid.applied(invalid.initialAttempt)
        assertEquals(MapFitDecision.Failed(MapFitFailure.INVALID_PANE),invalid.observe(camera,inside,101L))
    }

    @Test fun `cancellation revokes an old asynchronous proposal and later apparent success`() {
        val fit=policy();fit.applied(fit.initialAttempt)
        val next=retry(fit.observe(camera,listOf(MapFitPixel(-1.0,-1.0),inside[1]),101L))
        fit.cancel()
        assertFalse(fit.isCurrent(next));assertFalse(fit.applied(next))
        assertEquals(MapFitDecision.Waiting,fit.observe(camera,inside,102L))
    }

    @Test fun `refinement reuses the same gate ticket but cannot acknowledge an intermediate or stale idle`() {
        val gate=MapCameraCommandGate();val fit=policy()
        val ticket=gate.submit("same-business-fit",MapCameraCommandKind.FIT,padding)
        fun apply(attempt: MapFitAttempt, actual: MapCameraPosition) {
            assertTrue(gate.resolve(ticket,actual))
            assertTrue(gate.beginNativeFitApplication(ticket,camera.copy(longitude=4.0),
                listOf("map-style-layout",attempt),1_000L) {true})
            gate.captureNativeCamera(actual);gate.endNativeFitApplication(ticket,true);fit.applied(attempt)
        }
        apply(fit.initialAttempt,camera)
        gate.acceptNativeIdle(gate.nextNativeIdleSequence(),1_000L)
        val next=retry(fit.observe(camera,listOf(MapFitPixel(-1.0,-1.0),inside[1]),101L))
        assertNull(gate.observe(camera,false))
        val oldIdle=gate.nextNativeIdleSequence()
        val corrected=camera.copy(zoom=15.592)
        apply(next,corrected)
        gate.acceptNativeIdle(oldIdle,1_000L)
        assertNull(gate.observe(corrected,true))
        gate.acceptNativeIdle(gate.nextNativeIdleSequence(),1_000L)
        assertEquals(MapFitDecision.Verified,fit.observe(corrected,inside,102L))
        assertSame(ticket,gate.observe(corrected,true))
    }

    @Test fun `first target resources can wait without consuming refinement time`() {
        val fit=MapCameraFitRefinement(720,1000,padding,2,0.0)
        fit.applied(fit.initialAttempt)
        assertTrue(fit.awaitingResources)
        assertEquals(MapFitDecision.Waiting,fit.observe(camera,inside,90_000L))
        assertTrue(fit.startAfterResources(90_000L))
        assertFalse(fit.awaitingResources)
        val second=retry(fit.observe(camera,listOf(MapFitPixel(42.0,110.0),inside[1]),90_001L))
        fit.applied(second)
        assertEquals(MapFitDecision.Verified,fit.observe(camera.copy(zoom=16.5),inside,101_999L))
    }

    @Test fun `first ready arms exactly twelve seconds once and a later ready cannot reset them`() {
        val fit=MapCameraFitRefinement(720,1000,padding,2,0.0)
        assertEquals(12_000L,fit.maxDurationMs)
        fit.applied(fit.initialAttempt)
        assertTrue(fit.startAfterResources(40_000L))
        assertFalse(fit.startAfterResources(50_000L))
        assertEquals(MapFitDecision.Failed(MapFitFailure.BUDGET_EXHAUSTED),
            fit.observe(camera,inside,52_000L))
    }

    @Test fun `an unapplied seed cannot start computation and cancellation prevents later readiness`() {
        val fit=MapCameraFitRefinement(720,1000,padding,2,0.0)
        assertFalse(fit.startAfterResources(10L))
        fit.applied(fit.initialAttempt)
        fit.cancel()
        assertFalse(fit.awaitingResources)
        assertFalse(fit.startAfterResources(90_000L))
        assertFalse(fit.applied(fit.initialAttempt))
        assertEquals(MapFitDecision.Waiting,fit.observe(camera,inside,90_001L))
    }

    @Test fun `the initial application still counts toward the unchanged eight application limit`() {
        val fit=MapCameraFitRefinement(720,1000,padding,2,0.0)
        fit.applied(fit.initialAttempt)
        fit.startAfterResources(90_000L)
        for(nonce in 1 until 8) {
            val attempt=retry(fit.observe(camera.copy(zoom=16.0-nonce*0.01),
                listOf(MapFitPixel(49.0,110.0),inside[1]),90_000L+nonce))
            assertEquals(nonce+1,attempt.nonce)
            assertTrue(fit.applied(attempt))
        }
        assertEquals(MapFitDecision.Failed(MapFitFailure.BUDGET_EXHAUSTED),
            fit.observe(camera.copy(zoom=15.9),listOf(MapFitPixel(49.0,110.0),inside[1]),90_008L))
    }
}
