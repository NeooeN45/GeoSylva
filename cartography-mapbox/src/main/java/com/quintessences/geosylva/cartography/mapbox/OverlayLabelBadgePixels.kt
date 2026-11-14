package com.quintessences.geosylva.cartography.mapbox

import kotlin.math.ceil

internal data class OverlayLabelBadgePixels(val width: Int, val height: Int, val outlineWidth: Float)

/** Physical dimensions are computed before the existing 4 MiB RGBA admission check. */
internal fun overlayLabelBadgePixels(textWidth: Float, textHeight: Float, density: Float,
    badge: MapOverlayLabelBadge): OverlayLabelBadgePixels {
    require(textWidth.isFinite() && textWidth >= 0 && textHeight.isFinite() && textHeight >= 0)
    require(density.isFinite() && density > 0)
    val width = textWidth + 14f*density
    val height = textHeight + if (badge == MapOverlayLabelBadge.CIRCLE) 10f*density else 6f*density
    if (badge == MapOverlayLabelBadge.CIRCLE) {
        val side = ceil(maxOf(width,height,24f*density)).toInt().coerceAtLeast(1)
        return OverlayLabelBadgePixels(side,side,2f*density)
    }
    return OverlayLabelBadgePixels(ceil(width).toInt().coerceAtLeast(1),ceil(height).toInt().coerceAtLeast(1),0f)
}
