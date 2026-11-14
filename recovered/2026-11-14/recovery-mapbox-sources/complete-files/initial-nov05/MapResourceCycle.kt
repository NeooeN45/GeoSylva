package com.quintessences.geosylva.cartography.mapbox

/**
 * Native Loaded/Idle evidence belongs to one renderer generation and one resource cycle.
 * Parsing a style, registering a source or receiving a rendered frame cannot complete it.
 */
internal class MapResourceCycle {
    private var generation: Any? = null
    private var barrierMillis: Long = Long.MAX_VALUE
    private var complete = false

    fun beginStyleLoad(generation: Any, startedAtMillis: Long) {
        require(startedAtMillis >= 0)
        this.generation = generation
        barrierMillis = startedAtMillis
        complete = false
    }

    /** A readback-confirmed no-op preserves the current cycle instead of inventing another load. */
    fun recordTerrainApplication(generation: Any, completedAtMillis: Long, changed: Boolean) {
        require(completedAtMillis >= 0)
        if (this.generation !== generation || !changed) return
        barrierMillis = completedAtMillis
        complete = false
    }

    fun acceptNativeResourceEvent(generation: Any, occurredAtMillis: Long): Boolean {
        if (this.generation !== generation || occurredAtMillis <= barrierMillis) return false
        complete = true
        return true
    }

    fun isComplete(generation: Any): Boolean = this.generation === generation && complete
}
