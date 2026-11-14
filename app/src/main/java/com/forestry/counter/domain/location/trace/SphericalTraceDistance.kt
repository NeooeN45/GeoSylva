package com.forestry.counter.domain.location.trace

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Great-circle horizontal distance on the IUGG mean-radius sphere (6,371,008.8 m).
 * Versioned approximation, NOT an ellipsoidal survey or a terrain/slope distance.
 * Altitude and GPS accuracy are deliberately not converted into length/uncertainty.
 */
object SphericalTraceDistance {
    const val METHOD_ID = "iugg-mean-sphere-haversine-open-portions-v1"
    const val EARTH_RADIUS_M = 6_371_008.8

    fun isValidCoordinate(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0

    fun betweenM(from: TraceFix, to: TraceFix): Double? {
        if (!isValidCoordinate(from.latitude, from.longitude) ||
            !isValidCoordinate(to.latitude, to.longitude)) return null
        val latitude1 = Math.toRadians(from.latitude)
        val latitude2 = Math.toRadians(to.latitude)
        val halfLat = Math.toRadians(to.latitude - from.latitude) / 2
        val halfLon = Math.toRadians(to.longitude - from.longitude) / 2
        val a = (sin(halfLat) * sin(halfLat) +
            cos(latitude1) * cos(latitude2) * sin(halfLon) * sin(halfLon)).coerceIn(0.0, 1.0)
        return EARTH_RADIUS_M * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
