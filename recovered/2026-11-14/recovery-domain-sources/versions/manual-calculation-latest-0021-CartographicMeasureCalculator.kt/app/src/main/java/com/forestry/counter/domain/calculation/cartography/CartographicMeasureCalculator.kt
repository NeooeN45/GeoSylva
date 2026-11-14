package com.forestry.counter.domain.calculation.cartography

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** WGS84 angular coordinates, explicitly latitude then longitude (not GeoJSON order). */
data class CartographicCoordinate(val latitude: Double, val longitude: Double)

enum class CartographicMeasureKind { DISTANCE, AREA }

enum class CartographicMeasureIssue {
    NOT_ENOUGH_POINTS,
    INVALID_COORDINATE,
    DUPLICATE_VERTEX,
    DEGENERATE_GEOMETRY,
    SELF_INTERSECTION,
    OUTSIDE_LOCAL_AREA_DOMAIN,
    NUMERIC_FAILURE,
}

sealed interface CartographicMeasureResult {
    /** valueSi is metres for DISTANCE and square metres for AREA; never rounded. */
    data class Available(
        val kind: CartographicMeasureKind,
        val valueSi: Double,
        val pointCount: Int,
        val methodId: String,
        val methodVersion: Int,
        /** Open consecutive legs only, in metres; empty for an area. */
        val segmentLengthsM: List<Double> = emptyList(),
    ) : CartographicMeasureResult

    data class Unavailable(val issue: CartographicMeasureIssue) : CartographicMeasureResult
}

/**
 * Shared calculation for manually drawn map measurements. It neither reads a map
 * nor accesses GPS, altitude, a business scope, a saved file or a display unit.
 *
 * Distance sums open Haversine legs on the historical 6,371,000 m sphere. Area is
 * an equirectangular local-plane shoelace approximation, with mean vertex latitude
 * as the standard parallel and the first point as a numerical XY origin. Neither
 * method is an ellipsoidal survey, cadastral area or slope-corrected ground result.
 *
 * The area envelope (10 km from the first vertex, |latitude| <= 80 degrees) is a
 * conservative technical acceptance limit, NOT a validated precision guarantee.
 * Area precision remains unqualified even inside that envelope. Numeric tolerances
 * below only handle floating-point degeneracies; they are not physical uncertainty.
 */
object CartographicMeasureCalculator {
    const val EARTH_RADIUS_M = 6_371_000.0
    const val MAX_LOCAL_AREA_RADIUS_M = 10_000.0
    const val MAX_LOCAL_AREA_ABSOLUTE_LATITUDE = 80.0
    const val DISTANCE_METHOD_ID = "sphere-haversine-polyline"
    const val AREA_METHOD_ID = "local-equirectangular-shoelace"
    const val METHOD_VERSION = 1

    fun calculate(
        kind: CartographicMeasureKind,
        points: List<CartographicCoordinate>,
    ): CartographicMeasureResult {
        // A caller mutating its own collection cannot alter a returned calculation.
        val snapshot = points.toList()
        if (snapshot.any { !isValidCoordinate(it) }) return unavailable(CartographicMeasureIssue.INVALID_COORDINATE)
        if (snapshot.size < if (kind == CartographicMeasureKind.DISTANCE) 2 else 3) {
            return unavailable(CartographicMeasureIssue.NOT_ENOUGH_POINTS)
        }
        return when (kind) {
            CartographicMeasureKind.DISTANCE -> distance(snapshot)
            CartographicMeasureKind.AREA -> area(snapshot)
        }
    }

    fun isValidCoordinate(point: CartographicCoordinate): Boolean =
        point.latitude.isFinite() && point.longitude.isFinite() &&
            point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0

    private fun distance(points: List<CartographicCoordinate>): CartographicMeasureResult {
        if (points.zipWithNext().any { (a, b) -> positionKey(a) == positionKey(b) }) {
            return unavailable(CartographicMeasureIssue.DUPLICATE_VERTEX)
        }
        // A-B-A is valid: non-consecutive repeated points represent a return leg.
        val segments = points.zipWithNext().map { (a, b) -> betweenM(a, b) }
        val value = compensatedSum(segments)
        if (!value.isFinite() || segments.any { !it.isFinite() }) {
            return unavailable(CartographicMeasureIssue.NUMERIC_FAILURE)
        }
        if (value <= 0.0) return unavailable(CartographicMeasureIssue.DEGENERATE_GEOMETRY)
        return CartographicMeasureResult.Available(
            CartographicMeasureKind.DISTANCE, value, points.size,
            DISTANCE_METHOD_ID, METHOD_VERSION, segments,
        )
    }

    private fun area(points: List<CartographicCoordinate>): CartographicMeasureResult {
        // The editor supplies an open vertex list. Closing the ring is implicit;
        // a repeated final/first point is not silently removed from user input.
        if (points.map(::positionKey).toSet().size != points.size) {
            return unavailable(CartographicMeasureIssue.DUPLICATE_VERTEX)
        }
        val origin = points.first()
        if (points.any { abs(it.latitude) > MAX_LOCAL_AREA_ABSOLUTE_LATITUDE ||
                betweenM(origin, it) > MAX_LOCAL_AREA_RADIUS_M }) {
            return unavailable(CartographicMeasureIssue.OUTSIDE_LOCAL_AREA_DOMAIN)
        }
        val parallelLatitude = compensatedSum(points.map { it.latitude }) / points.size
        val parallelCos = cos(Math.toRadians(parallelLatitude))
        val projected = points.map {
            XY(
                Math.toRadians(longitudeDelta(it.longitude, origin.longitude)) * EARTH_RADIUS_M * parallelCos,
                Math.toRadians(it.latitude - origin.latitude) * EARTH_RADIUS_M,
            )
        }
        if (projected.any { !it.x.isFinite() || !it.y.isFinite() }) {
            return unavailable(CartographicMeasureIssue.NUMERIC_FAILURE)
        }
        for (i in projected.indices) {
            val nextI = (i + 1) % projected.size
            for (j in i + 1 until projected.size) {
                val nextJ = (j + 1) % projected.size
                if (nextI == j || nextJ == i) continue // Adjacent edges share a permitted endpoint.
                if (intersects(projected[i], projected[nextI], projected[j], projected[nextJ])) {
                    return unavailable(CartographicMeasureIssue.SELF_INTERSECTION)
                }
            }
        }
        val twiceSignedArea = compensatedSum(projected.indices.map { i ->
            val next = (i + 1) % projected.size
            projected[i].x * projected[next].y - projected[next].x * projected[i].y
        })
        val value = abs(twiceSignedArea) / 2.0
        if (!value.isFinite()) return unavailable(CartographicMeasureIssue.NUMERIC_FAILURE)
        val extentX = projected.maxOf { it.x } - projected.minOf { it.x }
        val extentY = projected.maxOf { it.y } - projected.minOf { it.y }
        val numericAreaTolerance = max(1.0, extentX * extentY) * 1e-12
        if (value <= numericAreaTolerance) return unavailable(CartographicMeasureIssue.DEGENERATE_GEOMETRY)
        return CartographicMeasureResult.Available(
            CartographicMeasureKind.AREA, value, points.size, AREA_METHOD_ID, METHOD_VERSION,
        )
    }

    private fun betweenM(from: CartographicCoordinate, to: CartographicCoordinate): Double {
        val fromLat = Math.toRadians(from.latitude)
        val toLat = Math.toRadians(to.latitude)
        val halfLat = Math.toRadians(to.latitude - from.latitude) / 2.0
        val halfLon = Math.toRadians(longitudeDelta(to.longitude, from.longitude)) / 2.0
        val a = (sin(halfLat) * sin(halfLat) + cos(fromLat) * cos(toLat) *
            sin(halfLon) * sin(halfLon)).coerceIn(0.0, 1.0)
        return EARTH_RADIUS_M * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    }

    private fun longitudeDelta(value: Double, origin: Double): Double {
        val delta = value - origin
        return when {
            delta > 180.0 -> delta - 360.0
            delta < -180.0 -> delta + 360.0
            else -> delta
        }
    }

    private fun positionKey(point: CartographicCoordinate): Pair<Double, Double> {
        val latitude = if (point.latitude == 0.0) 0.0 else point.latitude
        val longitude = when {
            abs(latitude) == 90.0 -> 0.0 // All longitudes name the same pole.
            abs(point.longitude) == 180.0 -> -180.0
            point.longitude == 0.0 -> 0.0
            else -> point.longitude
        }
        return latitude to longitude
    }

    private data class XY(val x: Double, val y: Double)

    private fun orientation(a: XY, b: XY, c: XY): Int {
        val left = (b.x - a.x) * (c.y - a.y)
        val right = (b.y - a.y) * (c.x - a.x)
        val cross = left - right
        val tolerance = max(1.0, abs(left) + abs(right)) * 1e-12
        return when {
            cross > tolerance -> 1
            cross < -tolerance -> -1
            else -> 0
        }
    }

    private fun onSegment(a: XY, b: XY, p: XY): Boolean =
        p.x >= minOf(a.x, b.x) - 1e-7 && p.x <= maxOf(a.x, b.x) + 1e-7 &&
            p.y >= minOf(a.y, b.y) - 1e-7 && p.y <= maxOf(a.y, b.y) + 1e-7

    private fun intersects(a: XY, b: XY, c: XY, d: XY): Boolean {
        if (maxOf(a.x, b.x) < minOf(c.x, d.x) - 1e-7 ||
            maxOf(c.x, d.x) < minOf(a.x, b.x) - 1e-7 ||
            maxOf(a.y, b.y) < minOf(c.y, d.y) - 1e-7 ||
            maxOf(c.y, d.y) < minOf(a.y, b.y) - 1e-7) return false
        val abc = orientation(a, b, c)
        val abd = orientation(a, b, d)
        val cda = orientation(c, d, a)
        val cdb = orientation(c, d, b)
        return abc * abd < 0 && cda * cdb < 0 ||
            abc == 0 && onSegment(a, b, c) || abd == 0 && onSegment(a, b, d) ||
            cda == 0 && onSegment(c, d, a) || cdb == 0 && onSegment(c, d, b)
    }

    private fun compensatedSum(values: Iterable<Double>): Double {
        var sum = 0.0
        var correction = 0.0
        for (value in values) {
            val adjusted = value - correction
            val next = sum + adjusted
            correction = (next - sum) - adjusted
            sum = next
        }
        return sum
    }

    private fun unavailable(issue: CartographicMeasureIssue) = CartographicMeasureResult.Unavailable(issue)
}
