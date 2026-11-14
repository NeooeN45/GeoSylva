package com.forestry.counter.domain.model.cartography

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/** Imported coordinates and source bytes never change when a local attribute/style changes. */
@Serializable
data class LayerSource(
    val filename: String,
    val format: String,
    val sha256: String,
    val originalBase64: String,
    val sourceCrs: String? = null,
    val sourceCoordinateOrder: String? = null,
    val producer: String? = null,
    val sourceVersion: String? = null,
    val normalizedCrs: String = "EPSG:4326",
    val normalizedCoordinateOrder: String = "longitude_latitude",
    val parserVersion: Int = 1,
    val warnings: List<String> = emptyList(),
    val crsEvidence: String = "UNKNOWN",
)

@Serializable
data class LayerStyle(
    val colorArgb: Long = 0xFF087C96,
    val size: LayerVisualSize = LayerVisualSize.MEDIUM,
    val pointSymbol: LayerPointSymbol = LayerPointSymbol.DOT,
    val labels: Boolean = true,
    val labelField: String? = null,
    val visible: Boolean = true,
)
@Serializable enum class LayerVisualSize { SMALL, MEDIUM, LARGE }
@Serializable enum class LayerPointSymbol { DOT, PIN }

enum class LayerGeometryFamily { POINT, LINE, POLYGON, MIXED }

@Serializable
data class LayerFeature(
    /** Stable internal identity, independent of duplicate or absent IDs in the source. */
    val id: String,
    val sourceFeature: JsonObject,
    val localAttributes: Map<String, JsonElement> = emptyMap(),
    val removedAttributes: Set<String> = emptySet(),
    val version: Long = 1,
) {
    val properties: Map<String, JsonElement> get() =
        ((sourceFeature["properties"] as? JsonObject)?.toMap().orEmpty() + localAttributes) - removedAttributes
    val geometryType: String? get() = (sourceFeature["geometry"] as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull
    fun featureJson(): JsonObject = JsonObject(sourceFeature + mapOf(
        "id" to JsonPrimitive(id), "properties" to JsonObject(properties)))
}

@Serializable
data class CartographicLayer(
    val id: String,
    val name: String,
    val source: LayerSource,
    val features: List<LayerFeature>,
    val style: LayerStyle = LayerStyle(),
    val version: Long = 1,
    val recordedAt: Long,
) {
    val fieldNames: List<String> get() = features.flatMap {
        (it.sourceFeature["properties"] as? JsonObject)?.keys.orEmpty() + it.localAttributes.keys + it.removedAttributes
    }.distinct().sorted()
    val geometryFamily: LayerGeometryFamily get() {
        if (features.any { it.geometryType == "GeometryCollection" }) return LayerGeometryFamily.MIXED
        val families = features.mapNotNull { feature -> when (feature.geometryType) {
            "Point", "MultiPoint" -> LayerGeometryFamily.POINT
            "LineString", "MultiLineString" -> LayerGeometryFamily.LINE
            "Polygon", "MultiPolygon" -> LayerGeometryFamily.POLYGON
            else -> null
        } }.distinct()
        return families.singleOrNull() ?: LayerGeometryFamily.MIXED
    }
    fun geoJson(visibleFeatures: List<LayerFeature> = features): String = buildJsonObject {
        put("type", "FeatureCollection")
        put("features", JsonArray(visibleFeatures.map(LayerFeature::featureJson)))
    }.toString()
}

/** Absent is represented by null here; JsonNull remains an explicit source/local null. */
data class LayerAttributeChange(val field: String, val value: JsonElement?)

enum class LayerFilterOperator { EQUALS, CONTAINS, IS_NULL, IS_ABSENT, IS_EMPTY }
data class LayerFilter(val field: String, val operator: LayerFilterOperator, val value: JsonElement? = null)

/** Pure shared browser predicate; map, rows, counters and selection all use its result. */
object LayerBrowser {
    fun filter(layer: CartographicLayer, query: String = "", filter: LayerFilter? = null): List<LayerFeature> = layer.features.filter { f ->
        val properties = f.properties
        val searched = query.isBlank() || properties.any { (key, value) ->
            key.contains(query, true) || value.toString().contains(query, true)
        } || f.id.contains(query, true)
        val value = filter?.let { properties[it.field] }
        searched && (filter == null || when (filter.operator) {
            LayerFilterOperator.EQUALS -> value != null && value == filter.value
            LayerFilterOperator.CONTAINS -> value is JsonPrimitive && value.isString &&
                filter.value is JsonPrimitive && filter.value.isString && value.content.contains(filter.value.content, true)
            LayerFilterOperator.IS_NULL -> value == JsonNull
            LayerFilterOperator.IS_ABSENT -> !properties.containsKey(filter.field)
            LayerFilterOperator.IS_EMPTY -> value == JsonPrimitive("")
        })
    }
    fun observedValues(layer: CartographicLayer, field: String): List<JsonElement> =
        layer.features.mapNotNull { it.properties[field] }.distinct()
}

/** Strict optional alpha; invalid strings never become black or zero. */
object LayerColor {
    fun parse(value: String): Long? {
        val hex = value.trim().removePrefix("#")
        if (hex.length !in listOf(6, 8) || hex.any { it !in "0123456789abcdefABCDEF" }) return null
        return hex.toLongOrNull(16)?.let { if (hex.length == 6) 0xFF000000 or it else it }
    }
    fun format(argb: Long): String = if ((argb ushr 24) and 0xFF == 0xFFL) "#%06X".format(argb and 0xFFFFFF) else "#%08X".format(argb and 0xFFFFFFFF)
}

/** Supported local scalar profile only; ownership/access is checked separately by the repository. */
object LayerAttributePolicy {
    private val csvCoordinateFields = setOf("lat", "latitude", "lon", "lng", "longitude")
    fun supportsLocalField(layer: CartographicLayer, feature: LayerFeature, field: String, knownFields: Set<String> = layer.fieldNames.toSet()): Boolean {
        val old = feature.properties[field]
        return field in knownFields && field.length <= 200 && field.none(Char::isISOControl) &&
            !(layer.source.format == "CSV" && field.lowercase() in csvCoordinateFields) && old !is JsonObject && old !is JsonArray
    }
    fun validate(layer: CartographicLayer, feature: LayerFeature, changes: List<LayerAttributeChange>): Boolean {
        val known = layer.fieldNames.toSet()
        return changes.all { change ->
            val old = feature.properties[change.field]
            val value = change.value
            val scalar = value == null || value == JsonNull || (value is JsonPrimitive &&
                (value.isString && value.content.length <= 4000 || value.booleanOrNull != null || value.doubleOrNull?.isFinite() == true))
            val sameType = value == null || value == JsonNull || old == null || old == JsonNull ||
                (old is JsonPrimitive && value is JsonPrimitive && old.isString == value.isString &&
                    (old.isString || (old.booleanOrNull != null) == (value.booleanOrNull != null)))
            supportsLocalField(layer, feature, change.field, known) && scalar && sameType
        }
    }
}

object LayerLabelFields {
    fun available(features: List<LayerFeature>): List<String> = features.flatMap { feature ->
        feature.properties.filterValues { it is JsonPrimitive && it != JsonNull && it.content.isNotEmpty() }.keys
    }.distinct().sorted()
}
