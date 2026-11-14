package com.forestry.counter.presentation.screens.forestry

import com.forestry.counter.domain.model.Essence
import com.forestry.counter.domain.model.Tige
import com.google.gson.JsonNull
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.quintessences.geosylva.cartography.mapbox.MapCoordinate
import com.quintessences.geosylva.cartography.mapbox.MapFeatureKey
import com.quintessences.geosylva.cartography.mapbox.MapOverlayFeature
import com.quintessences.geosylva.cartography.mapbox.MapOverlayScene
import com.quintessences.geosylva.cartography.mapbox.MapOverlayStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan

/** Renderer input already authorized by ObserveMapTrees. No repository, permission or GPS access. */
internal data class TerrainMapProjection(
    val scene: MapOverlayScene,
    val treesByKey: Map<MapFeatureKey, TappedTreeInfo>,
    val clustersByKey: Map<MapFeatureKey, List<MapCoordinate>>,
    val refusedTreeCount: Int = 0,
    val refusedMeasurementGeometry: Boolean = false,
)

/**
 * Builds one SDK-neutral scene. Trees keep their compound parcel/tree identity;
 * a rendered hit resolves through the dictionaries, never its label or coordinates.
 *
 * At zoom <14, a deterministic Web-Mercator grid groups trees in 96 world pixels
 * (512px world at zoom0). This is a display approximation, NOT a distance/radius
 * query or a forest sampling algorithm. Grid borders may split nearby objects.
 * Above that level, all points are separate. The selected tree remains separate.
 */
internal fun buildTerrainMapProjection(
    scopeId: String,
    revision: Long,
    geoTiges: List<Triple<Tige, Double, Double>>,
    essenceMap: Map<String, Essence>,
    essenceColors: Map<String, Int>,
    zoom: Double,
    measurePoints: List<MeasurePoint>,
    measureMode: MeasureMode,
    measureColor: Int,
    selectedIdentity: MapTreeIdentity? = null,
): TerrainMapProjection {
    require(zoom.isFinite() && zoom in 0.0..25.5)
    val features = mutableListOf<MapOverlayFeature>()
    val treesByKey = linkedMapOf<MapFeatureKey, TappedTreeInfo>()
    val clustersByKey = linkedMapOf<MapFeatureKey, List<MapCoordinate>>()
    val duplicateIdentities = geoTiges.groupingBy { MapTreeIdentity(it.first.id, it.first.parcelleId) }
        .eachCount().filterValues { it > 1 }.keys
    val validTrees = geoTiges.filter { (tree, longitude, latitude) ->
        tree.id.isNotBlank() && tree.parcelleId.isNotBlank() && tree.deletedAt == null &&
            MapTreeIdentity(tree.id, tree.parcelleId) !in duplicateIdentities &&
            validMapCoordinate(longitude, latitude)
    }
    fun addTree(item: Triple<Tige, Double, Double>) {
        val (tree, longitude, latitude) = item
        val info = treeInfo(tree, longitude, latitude, essenceMap)
        val identity = MapTreeIdentity(tree.id, tree.parcelleId)
        val key = treeFeatureKey(identity)
        treesByKey[key] = info
        features += MapOverlayFeature(
            key = key,
            geometry = Point.fromLngLat(longitude, latitude),
            style = MapOverlayStyle(
                colorHex = treeColor(tree, essenceColors),
                pointRadius = treeRadius(info.diamCm) + if (info.categorie != null) 1.0 else 0.0,
            ),
            label = tree.numero?.takeIf { zoom >= 17.0 }?.toString(),
        )
    }

    if (zoom < 14.0) {
        val level = floor(zoom).toInt()
        val worldSize = 512.0 * 2.0.pow(level)
        val cells = validTrees.filterNot { MapTreeIdentity(it.first.id, it.first.parcelleId) == selectedIdentity }
            .groupBy { (_, longitude, latitude) ->
                floor(mercatorX(longitude, worldSize) / 96.0).toLong() to
                    floor(mercatorY(latitude, worldSize) / 96.0).toLong()
            }
        cells.forEach { (cell, members) ->
            if (members.size == 1) addTree(members.single())
            else {
                val key = MapFeatureKey("terrain-tree-clusters", "$level:${cell.first}:${cell.second}")
                val coordinates = members.map { MapCoordinate(it.second, it.third) }
                clustersByKey[key] = coordinates
                val longitude = coordinates.map { it.longitude }.average()
                val meanY = coordinates.map { mercatorY(it.latitude, worldSize) }.average()
                val latitude = Math.toDegrees(2.0 * atan(exp(PI * (1.0 - 2.0 * meanY / worldSize))) - PI / 2)
                features += MapOverlayFeature(
                    key, Point.fromLngLat(longitude, latitude),
                    MapOverlayStyle(colorHex = "#176544", pointRadius = clusterRadius(members.size),
                        labelColorHex = "#12285B", labelSize = 12.0),
                    label = members.size.toString(),
                )
            }
        }
        validTrees.filter { MapTreeIdentity(it.first.id, it.first.parcelleId) == selectedIdentity }.forEach(::addTree)
    } else validTrees.forEach(::addTree)

    // A bad vertex invalidates the whole draft display. Dropping it and joining its
    // neighbours would invent a measured segment that the user never drew.
    val validMeasure = measurePoints.all { validMapCoordinate(it.longitude, it.latitude) }
    if (validMeasure) {
        val coordinates = measurePoints.map { Point.fromLngLat(it.longitude, it.latitude) }
        if (coordinates.size >= 2) {
            val distinctCount = coordinates.map { it.longitude() to it.latitude() }.distinct().size
            val polygon = measureMode == MeasureMode.AREA && distinctCount >= 3
            val geometry = if (polygon) {
                val ring = if (coordinates.first() == coordinates.last()) coordinates else coordinates + coordinates.first()
                Polygon.fromLngLats(listOf(ring))
            } else LineString.fromLngLats(coordinates)
            features += MapOverlayFeature(
                MapFeatureKey("terrain-measure-geometry", "draft"), geometry,
                MapOverlayStyle(colorHex = colorHex(measureColor), lineWidth = 3.0, fillOpacity = 0.16),
                selectable = false,
            )
        }
        coordinates.forEachIndexed { index, coordinate ->
            // Draft vertices have no persistent identity yet. Their labels are
            // presentation only and cannot be used as a stored object identity.
            features += MapOverlayFeature(
                MapFeatureKey("terrain-measure-vertices", "draft-vertex-$index"), coordinate,
                MapOverlayStyle(colorHex = colorHex(measureColor), pointRadius = 6.0),
                label = vertexLabel(index), selectable = false,
            )
        }
    }
    val selectedKey = selectedIdentity?.let(::treeFeatureKey)?.takeIf { it in treesByKey }
    return TerrainMapProjection(
        MapOverlayScene(scopeId, revision, features, selectedKey), treesByKey, clustersByKey,
        refusedTreeCount = geoTiges.size - validTrees.size,
        refusedMeasurementGeometry = !validMeasure,
    )
}

private fun validMapCoordinate(longitude: Double, latitude: Double): Boolean =
    longitude.isFinite() && longitude in -180.0..180.0 && latitude.isFinite() && latitude in -90.0..90.0

/** Length prefixes avoid collisions when an ID contains separators or quotes. */
private fun treeFeatureKey(identity: MapTreeIdentity) = MapFeatureKey(
    "terrain-trees", "${identity.parcelleId.length}:${identity.parcelleId}${identity.tigeId.length}:${identity.tigeId}",
)

private fun colorHex(color: Int): String = String.format(Locale.ROOT, "#%06X", color and 0xFFFFFF)

private fun treeColor(tree: Tige, essenceColors: Map<String, Int>): String = when (tree.categorie?.uppercase(Locale.ROOT)) {
    "DEPERISSANT" -> "#FF9800"
    "ARBRE_BIO" -> "#4CAF50"
    "MORT" -> "#424242"
    "PARASITE" -> "#F44336"
    else -> colorHex(essenceColors[tree.essenceCode.uppercase(Locale.ROOT)] ?: 0xFF2E7D32.toInt())
}

private fun positive(value: Double?): Double? = value?.takeIf { it.isFinite() && it > 0.0 }

private fun treeInfo(tree: Tige, longitude: Double, latitude: Double, essenceMap: Map<String, Essence>): TappedTreeInfo {
    val code = tree.essenceCode.uppercase(Locale.ROOT)
    return TappedTreeInfo(tree.id, tree.parcelleId, essenceMap[code]?.name ?: tree.essenceCode, code,
        positive(tree.diamCm), positive(tree.hauteurM), positive(tree.precisionM),
        tree.categorie?.takeIf { it.isNotBlank() }, latitude, longitude)
}

private fun treeRadius(diameterCm: Double?): Double {
    if (diameterCm == null) return 4.5
    val stops = listOf(8.0 to 3.0, 20.0 to 4.5, 35.0 to 6.0, 60.0 to 8.0)
    if (diameterCm <= stops.first().first) return stops.first().second
    stops.zipWithNext().forEach { (a, b) ->
        if (diameterCm <= b.first) return a.second + (b.second - a.second) *
            (diameterCm - a.first) / (b.first - a.first)
    }
    return stops.last().second
}

private fun clusterRadius(count: Int): Double = when {
    count <= 5 -> 10.0
    count <= 20 -> 10.0 + (count - 5) * 4.0 / 15.0
    count <= 60 -> 14.0 + (count - 20) * 6.0 / 40.0
    else -> 20.0
}

private fun mercatorX(longitude: Double, worldSize: Double) = (longitude + 180.0) / 360.0 * worldSize
private fun mercatorY(latitude: Double, worldSize: Double): Double {
    val projectedLatitude = latitude.coerceIn(-85.0511287798066, 85.0511287798066)
    return (1.0 - ln(tan(PI / 4.0 + Math.toRadians(projectedLatitude) / 2.0)) / PI) / 2.0 * worldSize
}

private fun vertexLabel(index: Int): String {
    var ordinal = index + 1
    return buildString {
        while (ordinal > 0) {
            ordinal--
            insert(0, ('A'.code + ordinal % 26).toChar())
            ordinal /= 26
        }
    }
}

/** GeoJSON compatibility/export projection, retaining both stable string IDs. */
internal fun buildTigesGeoJson(
    geoTiges: List<Triple<Tige, Double, Double>>,
    essenceMap: Map<String, Essence>,
    essenceColors: Map<String, Int>,
): String {
    val features = geoTiges.filter { (tree, longitude, latitude) ->
        tree.id.isNotBlank() && tree.parcelleId.isNotBlank() && validMapCoordinate(longitude, latitude)
    }.map { (tree, longitude, latitude) ->
        val info = treeInfo(tree, longitude, latitude, essenceMap)
        Feature.fromGeometry(Point.fromLngLat(longitude, latitude)).apply {
            addStringProperty("tige_id", info.tigeId)
            addStringProperty("parcelle_id", info.parcelleId)
            addStringProperty("essence", info.essenceCode)
            addStringProperty("essence_name", info.essenceName)
            fun number(name: String, value: Double?) {
                if (value == null) addProperty(name, JsonNull.INSTANCE) else addNumberProperty(name, value)
            }
            number("diam", info.diamCm)
            number("height", info.hauteurM)
            number("precision", info.precisionM)
            addStringProperty("color", treeColor(tree, essenceColors))
            addStringProperty("categorie", info.categorie?.uppercase(Locale.ROOT).orEmpty())
            addNumberProperty("is_special", if (info.categorie == null) 0 else 1)
            addStringProperty("label", buildString {
                info.diamCm?.let { append("⌀ ${it.roundToInt()} cm") }
                info.hauteurM?.let { if (isNotEmpty()) append(" · "); append("H ${it.roundToInt()} m") }
                info.precisionM?.let { if (isNotEmpty()) append(" · "); append(String.format(Locale.ROOT, "±%.1f m", it)) }
            })
            addStringProperty("precision_color", when {
                info.precisionM == null -> "#9E9E9E"
                info.precisionM <= 3.0 -> "#4CAF50"
                info.precisionM <= 6.0 -> "#8BC34A"
                info.precisionM <= 12.0 -> "#FF9800"
                else -> "#F44336"
            })
        }
    }
    return FeatureCollection.fromFeatures(features).toJson()
}

data class TappedTreeInfo(
    val tigeId: String,
    val parcelleId: String,
    val essenceName: String,
    val essenceCode: String,
    val diamCm: Double?,
    val hauteurM: Double?,
    val precisionM: Double?,
    val categorie: String?,
    val lat: Double,
    val lon: Double,
)

/** Identity comes from the rendered object, never its label or tap coordinates. */
internal data class MapTreeIdentity(val tigeId: String, val parcelleId: String)

internal fun mapTreeIdentity(feature: Feature): MapTreeIdentity? {
    fun id(key: String): String? {
        val value = feature.properties()?.get(key) ?: return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) return null
        return value.asString.takeIf { it.isNotBlank() }
    }
    return MapTreeIdentity(id("tige_id") ?: return null, id("parcelle_id") ?: return null)
}
