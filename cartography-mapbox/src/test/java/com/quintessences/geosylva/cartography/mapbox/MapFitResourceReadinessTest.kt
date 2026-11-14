package com.quintessences.geosylva.cartography.mapbox

import org.junit.Assert.*
import org.junit.Test

class MapFitResourceReadinessTest {
    @Test fun `events captured before the new setter cannot release its resource wait`() {
        val events=NativeRestoreEvents()
        events.fullSequence=1;events.idleSequence=1
        events.fullAtMs=900L;events.idleAtMs=950L
        val fence=events.fence(1_000L)
        assertFalse(events.readyAfter(fence))
        events.fullSequence++;events.idleSequence++
        assertFalse(events.readyAfter(fence)) // Old native timestamps delivered later.
    }

    @Test fun `both a fresh full and a later idle are required without geometry acknowledgement`() {
        val events=NativeRestoreEvents()
        val fence=events.fence(1_000L)
        events.cameraAtMs=1_001L
        events.fullSequence=1;events.fullAtMs=1_010L
        assertFalse(events.readyAfter(fence))
        events.idleSequence=1;events.idleAtMs=1_005L
        assertFalse(events.readyAfter(fence))
        events.idleAtMs=1_011L
        assertTrue(events.readyAfter(fence))
    }

    @Test fun `a later camera movement requires a new native image and idle`() {
        val events=NativeRestoreEvents()
        val fence=events.fence(1_000L)
        events.fullSequence=1;events.fullAtMs=1_010L
        events.idleSequence=1;events.idleAtMs=1_011L
        assertTrue(events.readyAfter(fence))
        events.cameraAtMs=1_015L
        assertFalse(events.readyAfter(fence))
        events.fullSequence++;events.fullAtMs=1_016L
        assertFalse(events.readyAfter(fence))
        events.idleSequence++;events.idleAtMs=1_017L
        assertTrue(events.readyAfter(fence))
    }

    @Test fun `a replacement intent cannot reuse the prior view resource witnesses`() {
        val events=NativeRestoreEvents()
        events.fullSequence=2;events.fullAtMs=1_010L
        events.idleSequence=2;events.idleAtMs=1_011L
        val replacement=events.fence(2_000L)
        assertFalse(events.readyAfter(replacement))
        events.fullSequence++;events.fullAtMs=2_010L
        events.idleSequence++;events.idleAtMs=2_011L
        assertTrue(events.readyAfter(replacement))
    }
}
