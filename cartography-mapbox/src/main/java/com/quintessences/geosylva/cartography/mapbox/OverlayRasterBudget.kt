package com.quintessences.geosylva.cartography.mapbox

/** Per-scene physical RGBA payload bound. A rejected new image closes admission; shared accepted sprites still work. */
internal class OverlayRasterBudget<K>(private val maximumImages: Int = 384, private val maximumBytes: Long = 4L * 1024 * 1024) {
    private val accepted = mutableSetOf<K>()
    var bytes: Long = 0; private set
    private var closed = false
    val canPrepareNewImage: Boolean get() = !closed && accepted.size < maximumImages && bytes < maximumBytes
    fun reserve(key: K, byteCount: Long): Boolean {
        if (key in accepted) return true
        if (!canPrepareNewImage || byteCount <= 0 || byteCount > maximumBytes - bytes) { closed = true; return false }
        accepted += key; bytes += byteCount; return true
    }
}

internal fun rgbaImageBytes(width: Int, height: Int): Long? = if (width <= 0 || height <= 0) null else
    runCatching { Math.multiplyExact(Math.multiplyExact(width.toLong(),height.toLong()),4L) }.getOrNull()

internal fun hatchTilePixels(density: Float): Int? = when {
    !density.isFinite() || density <= 0f -> null
    density <= 2f -> 16
    else -> 32
}

internal fun imagePriority(scene: MapOverlayScene): List<MapOverlayFeature> {
    val selected = scene.features.firstOrNull { it.key == scene.selectedKey } ?: return scene.features
    return listOf(selected) + scene.features.filterNot { it.key == selected.key }
}
