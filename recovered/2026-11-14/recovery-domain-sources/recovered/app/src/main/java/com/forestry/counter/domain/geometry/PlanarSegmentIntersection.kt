package com.forestry.counter.domain.geometry

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Shared planar segment predicates for finite coordinates in one consistent frame.
 * The relative floating-point tolerance is inherited from ParcelGeometry; it is
 * not a distance buffer or a tolerance for measurement/GPS uncertainty.
 * Primitive arguments avoid allocating four points in every quadratic edge check.
 */
internal object PlanarSegmentIntersection {
    fun orientation(ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double): Int {
        val left = (bx - ax) * (cy - ay)
        val right = (by - ay) * (cx - ax)
        val cross = left - right
        val error = 1e-14 * (abs(left) + abs(right))
        return if (abs(cross) <= error) 0 else if (cross > 0.0) 1 else -1
    }

    fun intersects(
        ax: Double, ay: Double, bx: Double, by: Double,
        cx: Double, cy: Double, dx: Double, dy: Double,
    ): Boolean {
        val first = orientation(ax, ay, bx, by, cx, cy)
        val second = orientation(ax, ay, bx, by, dx, dy)
        val third = orientation(cx, cy, dx, dy, ax, ay)
        val fourth = orientation(cx, cy, dx, dy, bx, by)
        return (first * second < 0 && third * fourth < 0) ||
            (first == 0 && onSegment(ax, ay, bx, by, cx, cy)) ||
            (second == 0 && onSegment(ax, ay, bx, by, dx, dy)) ||
            (third == 0 && onSegment(cx, cy, dx, dy, ax, ay)) ||
            (fourth == 0 && onSegment(cx, cy, dx, dy, bx, by))
    }

    private fun onSegment(ax: Double, ay: Double, bx: Double, by: Double, px: Double, py: Double): Boolean =
        px >= min(ax, bx) && px <= max(ax, bx) && py >= min(ay, by) && py <= max(ay, by) &&
            orientation(ax, ay, bx, by, px, py) == 0
}
