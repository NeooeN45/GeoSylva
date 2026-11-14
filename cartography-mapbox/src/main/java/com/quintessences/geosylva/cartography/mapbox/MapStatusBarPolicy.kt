package com.quintessences.geosylva.cartography.mapbox

/** Per-root-view state only: no business preferences or captured stale theme are restored. */
internal class MapStatusBarPolicy(initialThemeDarkIcons: Boolean) {
    private var themeDarkIcons = initialThemeDarkIcons
    private val photographicOwners = mutableSetOf<Any>()
    val darkIcons: Boolean get() = photographicOwners.isEmpty() && themeDarkIcons
    fun themeChanged(darkIcons: Boolean) { themeDarkIcons = darkIcons }
    fun acquire(owner: Any) { photographicOwners += owner }
    fun release(owner: Any) { photographicOwners -= owner }
}

/** A map embedded below app chrome must never change icons for a status bar it does not cover. */
internal fun mapCoversStatusBar(left: Float, top: Float, width: Int, windowWidth: Int, insetHeight: Int): Boolean =
    left.isFinite() && top.isFinite() && left <= 1f && top <= 1f && width > 0 &&
        windowWidth > 0 && width + left >= windowWidth-1f && insetHeight > 0
