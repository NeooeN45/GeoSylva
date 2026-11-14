package com.quintessences.geosylva.cartography.mapbox

import androidx.compose.runtime.saveable.listSaver

/** Camera and its native framing padding are one state, including after rotation. */
val MapCameraPositionSaver = listSaver<MapCameraPosition, Double>(
    save = { listOf(it.longitude, it.latitude, it.zoom, it.bearing, it.pitch,
        it.padding.top, it.padding.left, it.padding.bottom, it.padding.right) },
    restore = { values ->
        if (values.size != 9) null else runCatching {
            MapCameraPosition(values[0], values[1], values[2], values[3], values[4],
                MapCameraPadding(values[5], values[6], values[7], values[8]))
        }.getOrNull()
    },
)
