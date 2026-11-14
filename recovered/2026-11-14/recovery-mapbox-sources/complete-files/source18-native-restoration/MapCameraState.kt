package com.quintessences.geosylva.cartography.mapbox

import androidx.compose.runtime.saveable.listSaver

/** One observed logical camera and its optional physical witness; old nine-Double states remain valid. */
val MapCameraPositionSaver = listSaver<MapCameraPosition, Any>(
    save = { encodeMapCameraPosition(it) },
    restore = { decodeMapCameraPosition(it) },
)
