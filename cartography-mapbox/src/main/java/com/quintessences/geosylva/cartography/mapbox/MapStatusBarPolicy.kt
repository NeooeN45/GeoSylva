package com.quintessences.geosylva.cartography.mapbox

/** Per-root-view state only: no business preferences or captured stale theme are restored. */
internal class MapStatusBarPolicy(initialThemeDarkIcons: Boolean) {
    private var themeDarkIcons = initialThemeDarkIcons
    private val mapOwners = linkedMapOf<Any,Boolean>()
    val darkIcons: Boolean get() = mapOwners.values.lastOrNull() ?: themeDarkIcons
    fun themeChanged(darkIcons: Boolean) { themeDarkIcons = darkIcons }
    fun acquire(owner: Any, preferDarkIcons: Boolean) { mapOwners.remove(owner); mapOwners[owner] = preferDarkIcons }
    fun release(owner: Any) { mapOwners -= owner }
}

/** Display contrast comes from explicit style metadata, never a URL or inferred image brightness. */
internal fun MapboxStyleSpec.preferredStatusBarDarkIcons(): Boolean? = when {
    isPhotographic -> false
    backgroundIsDark != null -> !backgroundIsDark
    else -> null
}

/** A nested canvas must not change icons for a system bar it does not physically cover. */
internal fun mapCoversStatusBar(left: Float, top: Float, width: Int, height: Int, windowWidth: Int, insetHeight: Int): Boolean =
    left.isFinite() && top.isFinite() && left in -1f..1f && top in -1f..1f && width > 0 &&
        windowWidth > 0 && width + left >= windowWidth-1f && insetHeight > 0 && height >= insetHeight
