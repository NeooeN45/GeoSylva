package com.quintessences.geosylva.cartography.mapbox

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.LayoutDirection
import com.mapbox.maps.ScreenCoordinate

/** Physical map-view pixels; these are exposed-content insets, not camera padding. */
internal data class ViewportInsetsPx(val top: Double, val left: Double, val bottom: Double, val right: Double)

internal fun viewportInsetsPx(padding: PaddingValues, density: Float, direction: LayoutDirection): ViewportInsetsPx = ViewportInsetsPx(
    padding.calculateTopPadding().value.toDouble() * density,
    padding.calculateLeftPadding(direction).value.toDouble() * density,
    padding.calculateBottomPadding().value.toDouble() * density,
    padding.calculateRightPadding(direction).value.toDouble() * density,
)

/** Clockwise NW/NE/SE/SW in view pixels. Invalid or fully covered views have no footprint. */
internal fun visibleViewportCorners(width: Int, height: Int, insets: ViewportInsetsPx): List<ScreenCoordinate>? {
    if (width <= 0 || height <= 0 || listOf(insets.top, insets.left, insets.bottom, insets.right).any { !it.isFinite() || it < 0.0 }) return null
    val left = insets.left
    val top = insets.top
    val rawRight = width.toDouble() - insets.right
    val rawBottom = height.toDouble() - insets.bottom
    if (rawRight <= left || rawBottom <= top) return null
    // Pixel coordinates use the MapView's half-open extent. At an unpadded edge,
    // take the closest representable interior coordinate, without shifting a pixel.
    val right = minOf(rawRight, Math.nextDown(width.toDouble()))
    val bottom = minOf(rawBottom, Math.nextDown(height.toDouble()))
    if (right <= left || bottom <= top) return null
    return listOf(ScreenCoordinate(left, top), ScreenCoordinate(right, top),
        ScreenCoordinate(right, bottom), ScreenCoordinate(left, bottom))
}

/** Four native surface hits, closed once; sky, failures and degenerate projections stay unavailable. */
internal fun projectVisibleFootprint(corners: List<ScreenCoordinate>?, project: (ScreenCoordinate) -> MapCoordinate?): List<MapCoordinate>? {
    if (corners == null || corners.size != 4) return null
    val projected = corners.map { pixel -> runCatching { project(pixel) }.getOrNull() ?: return null }
    if (projected.distinct().size != 4) return null
    return projected + projected.first()
}
