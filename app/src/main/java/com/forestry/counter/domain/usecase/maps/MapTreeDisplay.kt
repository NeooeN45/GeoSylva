package com.forestry.counter.domain.usecase.maps

import com.forestry.counter.domain.location.WktUtils
import com.forestry.counter.domain.model.Essence
import com.forestry.counter.domain.model.Tige
import java.util.Locale

/** Composite identity: a label/number alone never authorises a record or a target. */
data class MapTreeKey(val treeId: String, val parcelId: String)
data class MapTreePoint(val tree: Tige, val longitude: Double, val latitude: Double) {
    val key: MapTreeKey get() = MapTreeKey(tree.id, tree.parcelleId)
}

data class MapTreeSpecies(
    val code: String,
    val name: String,
    val colorArgb: Int,
    /** Geolocated records before display filters, across the authorised scope. */
    val locatedCount: Int,
    val selected: Boolean,
)

data class MapTreeDisplayCounts(
    val total: Int,
    val located: Int,
    val withoutCoordinates: Int,
    val speciesHidden: Int,
    val selectedLocated: Int,
    val aboveThreshold: Int,
    val unknownAccuracy: Int,
    val shown: Int,
)

data class MapTreeDisplayProjection(
    val visible: List<MapTreePoint>,
    val species: List<MapTreeSpecies>,
    val counts: MapTreeDisplayCounts,
    /** True only when accuracy filtering itself removed every selected point. */
    val emptyBecauseOfAccuracy: Boolean,
) {
    val visibleKeys: Set<MapTreeKey> get() = visible.mapTo(mutableSetOf()) { it.key }
}

/**
 * One projection feeds map markers, the list, species legend and counters. Its input
 * must be ObserveMapTrees.Ready.trees; this function cannot broaden repository access.
 * No camera command is emitted when filters change, including an empty result.
 */
object MapTreeDisplay {
    const val MIN_ACCURACY_THRESHOLD_M = 3f
    const val MAX_ACCURACY_THRESHOLD_M = 100f

    private val palette = intArrayOf(
        0xFF176F39.toInt(), 0xFF386BA5.toInt(), 0xFF6F3D91.toInt(),
        0xFFAE643E.toInt(), 0xFF18857C.toInt(), 0xFFB17820.toInt(),
        0xFF9E4876.toInt(), 0xFF536A30.toInt(),
    )

    fun speciesCode(code: String): String = code.trim().uppercase(Locale.ROOT)
    fun knownAccuracy(value: Double?): Double? = value?.takeIf { it.isFinite() && it > 0 }

    /** Stable across sorting, filtering, camera and repository emission order. */
    fun speciesColor(code: String, configuredHex: String? = null): Int {
        val hex = configuredHex?.trim()?.removePrefix("#")
        if (hex != null && hex.length in setOf(6, 8) && hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            val color = hex.toLongOrNull(16)
            if (color != null) return (if (hex.length == 6) color or 0xFF000000 else color).toInt()
        }
        return palette[(speciesCode(code).hashCode().toLong() and 0x7fffffffL).rem(palette.size).toInt()]
    }

    fun project(
        trees: List<Tige>,
        essences: List<Essence>,
        hiddenSpecies: Set<String>,
        accuracyFilterEnabled: Boolean,
        accuracyThresholdM: Float,
        treesVisible: Boolean = true,
    ): MapTreeDisplayProjection {
        require(accuracyThresholdM.isFinite() && accuracyThresholdM in MIN_ACCURACY_THRESHOLD_M..MAX_ACCURACY_THRESHOLD_M)
        val unique = trees.distinctBy { MapTreeKey(it.id, it.parcelleId) }
        val located = unique.mapNotNull { tree ->
            val (lon, lat, _) = WktUtils.parsePointZ(tree.gpsWkt)
            if (lon != null && lat != null && lon.isFinite() && lat.isFinite() && lon in -180.0..180.0 && lat in -90.0..90.0)
                MapTreePoint(tree, lon, lat) else null
        }
        val hidden = hiddenSpecies.mapTo(mutableSetOf(), ::speciesCode)
        val selected = located.filter { speciesCode(it.tree.essenceCode) !in hidden }
        val above = selected.count { knownAccuracy(it.tree.precisionM)?.let { a -> a > accuracyThresholdM } == true }
        val unknown = selected.count { knownAccuracy(it.tree.precisionM) == null }
        val displayed = if (!treesVisible) emptyList() else selected.filter {
            !accuracyFilterEnabled || knownAccuracy(it.tree.precisionM)?.let { a -> a <= accuracyThresholdM } == true
        }
        val essenceByCode = essences.associateBy { speciesCode(it.code) }
        val grouped = located.groupBy { speciesCode(it.tree.essenceCode) }
        val species = grouped.keys.sorted().map { code ->
            val essence = essenceByCode[code]
            MapTreeSpecies(code, essence?.name ?: code, speciesColor(code, essence?.colorHex), grouped.getValue(code).size, code !in hidden)
        }
        return MapTreeDisplayProjection(displayed, species,
            MapTreeDisplayCounts(unique.size, located.size, unique.size - located.size,
                located.size - selected.size, selected.size, above, unknown, displayed.size),
            treesVisible && accuracyFilterEnabled && selected.isNotEmpty() && displayed.isEmpty())
    }
}
