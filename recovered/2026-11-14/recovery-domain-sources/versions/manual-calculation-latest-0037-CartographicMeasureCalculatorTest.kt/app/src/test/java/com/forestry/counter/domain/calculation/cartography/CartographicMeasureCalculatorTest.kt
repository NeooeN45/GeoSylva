package com.forestry.counter.domain.calculation.cartography

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class CartographicMeasureCalculatorTest {
    @Test fun `oversized input is rejected before traversing coordinates or copying geometry`() {
        val points = object : AbstractList<CartographicCoordinate>() {
            override val size = CartographicMeasureCalculator.MAX_POINTS + 1
            override fun get(index: Int): CartographicCoordinate = error("Oversized input must not be traversed")
        }
        for (kind in CartographicMeasureKind.entries) {
            assertUnavailable(kind, points, CartographicMeasureIssue.TOO_MANY_POINTS)
        }
    }

    @Test fun `distance sums open consecutive legs instead of closing or measuring chord`() {
        val result = distance(p(0.0, 0.0), p(0.0, 0.001), p(0.001, 0.001))
        assertEquals(222.389853, result.valueSi, 0.001)
        assertEquals(2, result.segmentLengthsM.size)
        assertEquals(result.valueSi, result.segmentLengthsM.sum(), 1e-9)
        assertEquals(3, result.pointCount)
        assertEquals(CartographicMeasureKind.DISTANCE, result.kind)
        assertEquals(CartographicMeasureCalculator.DISTANCE_METHOD_ID, result.methodId)
        assertEquals(1, result.methodVersion)
    }

    @Test fun `a return to an earlier vertex remains a valid return leg`() {
        val result = distance(p(48.0, 2.0), p(48.0, 2.001), p(48.0, 2.0))
        val outward = distance(p(48.0, 2.0), p(48.0, 2.001))
        assertEquals(outward.valueSi * 2.0, result.valueSi, 1e-9)
    }

    @Test fun `consecutive duplicate vertices cannot create a valid measurement`() {
        assertUnavailable(CartographicMeasureKind.DISTANCE,
            listOf(p(48.0, 2.0), p(48.0, 2.0)), CartographicMeasureIssue.DUPLICATE_VERTEX)
        assertUnavailable(CartographicMeasureKind.DISTANCE,
            listOf(p(48.0, 2.0), p(48.0, 2.001), p(48.0, 2.001)), CartographicMeasureIssue.DUPLICATE_VERTEX)
    }

    @Test fun `dateline aliases and different longitudes at one pole are the same position`() {
        listOf(
            listOf(p(0.0, 180.0), p(0.0, -180.0)),
            listOf(p(90.0, 25.0), p(90.0, -140.0)),
            listOf(p(-90.0, -15.0), p(-90.0, 120.0)),
            listOf(p(-0.0, 0.0), p(0.0, -0.0)),
        ).forEach { points ->
            assertUnavailable(CartographicMeasureKind.DISTANCE, points, CartographicMeasureIssue.DUPLICATE_VERTEX)
        }
    }

    @Test fun `incomplete drafts are unavailable rather than fabricated zero results`() {
        for (count in 0..1) assertUnavailable(CartographicMeasureKind.DISTANCE,
            listOf(p(48.0, 2.0)).take(count), CartographicMeasureIssue.NOT_ENOUGH_POINTS)
        for (count in 0..2) assertUnavailable(CartographicMeasureKind.AREA,
            listOf(p(48.0, 2.0), p(48.0, 2.001)).take(count), CartographicMeasureIssue.NOT_ENOUGH_POINTS)
    }

    @Test fun `non finite and out of range coordinates fail explicitly`() {
        val invalid = listOf(p(Double.NaN, 2.0), p(48.0, Double.POSITIVE_INFINITY),
            p(90.1, 2.0), p(-90.1, 2.0), p(48.0, 180.1), p(48.0, -180.1))
        for (point in invalid) {
            for (kind in CartographicMeasureKind.entries) {
                assertUnavailable(kind, listOf(p(48.0, 2.0), p(48.0, 2.001), point),
                    CartographicMeasureIssue.INVALID_COORDINATE)
            }
        }
    }

    @Test fun `a short dateline crossing is a short open distance`() {
        assertEquals(222.389853, distance(p(0.0, 179.999), p(0.0, -179.999)).valueSi, 0.001)
    }

    @Test fun `antipodal haversine remains finite at rounding boundaries`() {
        for (latitude in listOf(-62.1, -15.6, 0.0, 37.8, 67.5)) {
            val result = distance(p(latitude, 123.4), p(-latitude, -56.6))
            assertTrue(result.valueSi.isFinite())
            assertEquals(PI * CartographicMeasureCalculator.EARTH_RADIUS_M, result.valueSi, 0.5)
        }
    }

    @Test fun `returned result is not affected by later draft list mutation`() {
        val points = mutableListOf(p(0.0, 0.0), p(0.0, 0.001))
        val result = calculate(CartographicMeasureKind.DISTANCE, points)
        points.clear()
        assertEquals(2, result.pointCount)
        assertEquals(111.194927, result.valueSi, 0.001)
        assertEquals(1, result.segmentLengthsM.size)
    }

    @Test fun `small square produces a horizontal local plane area with explicit method`() {
        val result = calculate(CartographicMeasureKind.AREA, square())
        assertEquals(12_364.31, result.valueSi, 0.1)
        assertEquals(CartographicMeasureKind.AREA, result.kind)
        assertEquals(4, result.pointCount)
        assertTrue(result.segmentLengthsM.isEmpty())
        assertEquals(CartographicMeasureCalculator.AREA_METHOD_ID, result.methodId)
        assertEquals(1, result.methodVersion)
    }

    @Test fun `area is invariant to winding and first vertex inside its technical envelope`() {
        val original = listOf(p(48.0, 2.0), p(48.0, 2.001), p(48.001, 2.001), p(48.001, 2.0))
        val area = calculate(CartographicMeasureKind.AREA, original).valueSi
        assertEquals(area, calculate(CartographicMeasureKind.AREA, original.reversed()).valueSi, 1e-6)
        for (start in original.indices) {
            val rotated = original.drop(start) + original.take(start)
            assertEquals(area, calculate(CartographicMeasureKind.AREA, rotated).valueSi, 1e-6)
        }
    }

    @Test fun `simple concave polygons are allowed`() {
        val points = listOf(p(0.0, 0.0), p(0.0, 0.002), p(0.002, 0.002),
            p(0.001, 0.001), p(0.002, 0.0))
        val result = calculate(CartographicMeasureKind.AREA, points)
        assertTrue(result.valueSi > 0.0)
        assertEquals(5, result.pointCount)
    }

    @Test fun `collinear area drafts are unavailable`() {
        assertUnavailable(CartographicMeasureKind.AREA,
            listOf(p(48.0, 2.0), p(48.0, 2.001), p(48.0, 2.002)),
            CartographicMeasureIssue.DEGENERATE_GEOMETRY)
    }

    @Test fun `closing or repeating an area vertex is not silently normalized`() {
        assertUnavailable(CartographicMeasureKind.AREA, square() + square().first(),
            CartographicMeasureIssue.DUPLICATE_VERTEX)
        assertUnavailable(CartographicMeasureKind.AREA,
            listOf(p(0.0, 0.0), p(0.0, 0.001), p(0.001, 0.001), p(0.0, 0.001)),
            CartographicMeasureIssue.DUPLICATE_VERTEX)
    }

    @Test fun `bow tie is rejected before its cancelling signed area becomes zero`() {
        assertUnavailable(CartographicMeasureKind.AREA,
            listOf(p(0.0, 0.0), p(0.001, 0.001), p(0.0, 0.001), p(0.001, 0.0)),
            CartographicMeasureIssue.SELF_INTERSECTION)
    }

    @Test fun `a vertex touching a non adjacent edge is rejected`() {
        assertUnavailable(CartographicMeasureKind.AREA,
            listOf(p(0.0, 0.0), p(0.0, 0.002), p(0.002, 0.002), p(0.0, 0.001), p(0.002, 0.0)),
            CartographicMeasureIssue.SELF_INTERSECTION)
    }

    @Test fun `overlapping backtracking boundary edges are rejected`() {
        assertUnavailable(CartographicMeasureKind.AREA,
            listOf(p(0.0, 0.0), p(0.0, 0.002), p(0.0, 0.001), p(0.001, 0.001), p(0.001, 0.0)),
            CartographicMeasureIssue.SELF_INTERSECTION)
    }

    @Test fun `area outside local radius is unavailable instead of using a worldwide shoelace`() {
        assertUnavailable(CartographicMeasureKind.AREA,
            listOf(p(48.0, 2.0), p(48.0, 2.2), p(48.01, 2.0)),
            CartographicMeasureIssue.OUTSIDE_LOCAL_AREA_DOMAIN)
    }

    @Test fun `polar area is outside the local plane technical latitude envelope`() {
        assertUnavailable(CartographicMeasureKind.AREA,
            listOf(p(81.0, 2.0), p(81.0, 2.001), p(81.001, 2.0)),
            CartographicMeasureIssue.OUTSIDE_LOCAL_AREA_DOMAIN)
    }

    @Test fun `small local area across the dateline uses the nearby longitude branch`() {
        val across = listOf(p(0.0, 179.999), p(0.0, -179.999), p(0.001, -179.999), p(0.001, 179.999))
        val equivalent = listOf(p(0.0, 0.0), p(0.0, 0.002), p(0.001, 0.002), p(0.001, 0.0))
        assertEquals(calculate(CartographicMeasureKind.AREA, equivalent).valueSi,
            calculate(CartographicMeasureKind.AREA, across).valueSi, 0.001)
    }

    private fun distance(vararg points: CartographicCoordinate) = calculate(CartographicMeasureKind.DISTANCE, points.toList())

    private fun calculate(kind: CartographicMeasureKind, points: List<CartographicCoordinate>): CartographicMeasureResult.Available {
        val result = CartographicMeasureCalculator.calculate(kind, points)
        assertTrue("Expected an available measurement, got $result", result is CartographicMeasureResult.Available)
        return result as CartographicMeasureResult.Available
    }

    private fun assertUnavailable(kind: CartographicMeasureKind, points: List<CartographicCoordinate>, issue: CartographicMeasureIssue) {
        assertEquals(CartographicMeasureResult.Unavailable(issue), CartographicMeasureCalculator.calculate(kind, points))
    }

    private fun p(latitude: Double, longitude: Double) = CartographicCoordinate(latitude, longitude)

    private fun square() = listOf(p(0.0, 0.0), p(0.0, 0.001), p(0.001, 0.001), p(0.001, 0.0))
}
