package com.forestry.counter.presentation.navigation

import org.junit.Assert.*
import org.junit.Test

class MapsObservationRouteBoundaryTest {
    @Test fun `longitude and latitude preserve their identity`() {
        val place = parsedMapsObservationPlace("TEST access", "5.5", "45.2")
        assertEquals(5.5, checkNotNull(place).point.longitude, 0.0)
        assertEquals(45.2, place.point.latitude, 0.0)
    }
    @Test fun `invalid or incomplete map context is rejected`() {
        listOf("NaN", "Infinity", "181", "bad").forEach { assertNull(parsedMapsObservationPlace("TEST", it, "45")) }
        assertNull(parsedMapsObservationPlace("TEST", "5", "91"))
        assertNull(parsedMapsObservationPlace("", "5", "45"))
        assertNull(parsedMapsObservationPlace("TEST\nprivate", "5", "45"))
        assertNull(parsedMapsObservationPlace("TEST", null, "45"))
    }
}
