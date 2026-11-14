package com.forestry.counter.domain.location

import com.forestry.counter.domain.location.trace.SphericalTraceDistance
import com.forestry.counter.domain.location.trace.TraceFix
import com.forestry.counter.domain.location.trace.TracePositionSource
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

enum class TreePositionStatus { WAITING, FRESH, STALE, UNAVAILABLE, PERMISSION_REQUIRED, PAUSED }
enum class TreeOrientationStatus { WAITING, READY, UNKNOWN, UNRELIABLE, STALE, UNAVAILABLE }

/** Engineering display limits; neither a field protocol nor a GPS precision guarantee. */
object TreeGuidancePolicy {
    const val MAX_FIX_AGE_NANOS = 15_000_000_000L
    const val MAX_HEADING_AGE_NANOS = 5_000_000_000L
    const val NEAR_POINT_CUE_M = 5.0

    fun positionStatus(
        nowNanos: Long,
        fixNanos: Long?,
        paused: Boolean,
        permissionGranted: Boolean,
        providerAvailable: Boolean,
    ): TreePositionStatus = when {
        paused -> TreePositionStatus.PAUSED
        !permissionGranted -> TreePositionStatus.PERMISSION_REQUIRED
        !providerAvailable -> TreePositionStatus.UNAVAILABLE
        fixNanos == null -> TreePositionStatus.WAITING
        fixNanos <= 0 || nowNanos < fixNanos || nowNanos - fixNanos > MAX_FIX_AGE_NANOS -> TreePositionStatus.STALE
        else -> TreePositionStatus.FRESH
    }

    fun orientationStatus(nowNanos: Long, headingNanos: Long?, available: Boolean, accuracy: Int?): TreeOrientationStatus = when {
        !available -> TreeOrientationStatus.UNAVAILABLE
        headingNanos == null -> TreeOrientationStatus.WAITING
        headingNanos <= 0 || nowNanos < headingNanos || nowNanos - headingNanos > MAX_HEADING_AGE_NANOS -> TreeOrientationStatus.STALE
        accuracy == null -> TreeOrientationStatus.UNKNOWN
        // Android reports UNRELIABLE=0, LOW=1, MEDIUM=2, HIGH=3.
        accuracy < 2 -> TreeOrientationStatus.UNRELIABLE
        else -> TreeOrientationStatus.READY
    }

    fun normalise(degrees: Float): Float = ((degrees % 360f) + 360f) % 360f
    fun relativeBearing(trueBearing: Float?, trueDeviceHeading: Float?, position: TreePositionStatus,
        orientation: TreeOrientationStatus): Float? =
        if (position == TreePositionStatus.FRESH && orientation == TreeOrientationStatus.READY &&
            trueBearing?.isFinite() == true && trueDeviceHeading?.isFinite() == true)
            normalise(trueBearing - trueDeviceHeading) else null

    /** Same horizontal spherical length as the shared GPS trace calculator. */
    fun distanceM(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double? {
        fun point(lat: Double, lon: Double) = TraceFix(lat, lon, null, 0, TracePositionSource.PHONE_LOCATION)
        return SphericalTraceDistance.betweenM(point(fromLat, fromLon), point(toLat, toLon))
    }

    /** Initial great-circle bearing from true north; undefined at coincident/antipodal points. */
    fun trueBearingDeg(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Float? {
        if (!SphericalTraceDistance.isValidCoordinate(fromLat, fromLon) ||
            !SphericalTraceDistance.isValidCoordinate(toLat, toLon)) return null
        val lat1 = Math.toRadians(fromLat)
        val lat2 = Math.toRadians(toLat)
        val deltaLon = Math.toRadians(toLon - fromLon)
        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        if (kotlin.math.abs(x) + kotlin.math.abs(y) < 1e-12) return null
        return normalise(Math.toDegrees(atan2(y, x)).toFloat())
    }
}
