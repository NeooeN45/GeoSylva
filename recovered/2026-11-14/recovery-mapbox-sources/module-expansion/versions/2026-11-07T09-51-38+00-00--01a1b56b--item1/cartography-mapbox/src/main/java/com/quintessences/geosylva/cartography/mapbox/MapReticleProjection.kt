package com.quintessences.geosylva.cartography.mapbox

import androidx.compose.ui.geometry.Offset
import com.mapbox.maps.ScreenCoordinate

/** A reticle is a viewport pixel, never a camera-centre approximation. */
internal fun reticlePixelInsideView(position: Offset?, width: Int, height: Int): ScreenCoordinate? {
    if (position == null || width <= 0 || height <= 0 ||
        !position.x.isFinite() || !position.y.isFinite() ||
        position.x < 0f || position.y < 0f || position.x >= width || position.y >= height) return null
    return ScreenCoordinate(position.x.toDouble(), position.y.toDouble())
}
