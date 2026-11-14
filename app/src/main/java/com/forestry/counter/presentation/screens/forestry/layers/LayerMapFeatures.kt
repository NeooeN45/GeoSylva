package com.forestry.counter.presentation.screens.forestry.layers

import com.forestry.counter.domain.model.cartography.LayerPointSymbol
import com.forestry.counter.domain.model.cartography.LayerVisualSize
import com.mapbox.geojson.FeatureCollection
import com.quintessences.geosylva.cartography.mapbox.MapFeatureKey
import com.quintessences.geosylva.cartography.mapbox.MapOverlayFeature
import com.quintessences.geosylva.cartography.mapbox.MapOverlayPointSymbol
import com.quintessences.geosylva.cartography.mapbox.MapOverlayStyle

private const val IMPORTED_LAYER_PREFIX = "imported-layer:"
private const val MAX_RENDER_FEATURES = 7999
private const val MAX_RENDER_JSON_CHARACTERS = 20 * 1024 * 1024
private const val MAX_LABEL_CHARACTERS = 120

data class LayerFeatureIdentity(val layerId: String, val featureId: String)

/** Prefix isolates imported-layer identities from business data and shared measure overlays. */
fun layerFeatureIdentity(key: MapFeatureKey): LayerFeatureIdentity? {
    if (!key.layerId.startsWith(IMPORTED_LAYER_PREFIX)) return null
    val layerId = key.layerId.removePrefix(IMPORTED_LAYER_PREFIX)
    if (layerId.isBlank() || layerId.length > 100 || !key.featureId.matches(Regex("f-[0-9]{1,5}"))) return null
    return LayerFeatureIdentity(layerId, key.featureId)
}

/** Pure projection only: no DAO, scope substitution, clipping or implicit camera movement. */
fun buildLayerMapFeatures(renders: List<LayerRenderSnapshot>): List<MapOverlayFeature> {
    if (renders.map { it.id }.distinct().size != renders.size) return emptyList()
    val output = mutableListOf<MapOverlayFeature>()
    renders.filter { it.style.visible }.forEach { layer ->
        if (layer.id.isBlank() || layer.id.length > 100 || layer.geoJson.length > MAX_RENDER_JSON_CHARACTERS) return@forEach
        val features = runCatching { FeatureCollection.fromJson(layer.geoJson).features() }.getOrNull() ?: return@forEach
        if (features.size > MAX_RENDER_FEATURES) return@forEach
        val ids = features.map { it.id() }
        if (ids.any { it == null || !it.matches(Regex("f-[0-9]{1,5}")) } || ids.distinct().size != ids.size) return@forEach
        val size = when (layer.style.size) { LayerVisualSize.SMALL -> 4.5; LayerVisualSize.MEDIUM -> 6.5; LayerVisualSize.LARGE -> 9.0 }
        val alpha = ((layer.style.colorArgb ushr 24) and 0xFF) / 255.0
        val style = MapOverlayStyle(
            colorHex = "#%06X".format(layer.style.colorArgb and 0xFFFFFF),
            pointRadius = size,
            lineWidth = when (layer.style.size) { LayerVisualSize.SMALL -> 2.0; LayerVisualSize.MEDIUM -> 3.0; LayerVisualSize.LARGE -> 5.0 },
            fillOpacity = 0.18,
            labelSize = when (layer.style.size) { LayerVisualSize.SMALL -> 11.0; LayerVisualSize.MEDIUM -> 13.0; LayerVisualSize.LARGE -> 16.0 },
            strokeWidth = 2.0,
            pointSymbol = if (layer.style.pointSymbol == LayerPointSymbol.PIN) MapOverlayPointSymbol.PIN else MapOverlayPointSymbol.CIRCLE,
            opacity = alpha,
        )
        val projected = features.mapNotNull { feature ->
            val geometry = feature.geometry() ?: return@mapNotNull null // Unlocated objects remain in the data browser.
            val id = feature.id() ?: return@mapNotNull null
            val label = if (layer.style.labels) layer.style.labelField?.let { field ->
                feature.properties()?.get(field)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.let { scalar ->
                    scalar.asString.filterNot(Char::isISOControl).trim().take(MAX_LABEL_CHARACTERS).takeIf(String::isNotEmpty)
                }
            } else null
            runCatching { MapOverlayFeature(MapFeatureKey(IMPORTED_LAYER_PREFIX + layer.id, id), geometry, style, label) }.getOrNull()
        }
        // A bad located geometry rejects this rendered layer; never show a misleading partial subset.
        if (projected.size == features.count { it.geometry() != null }) output += projected
    }
    return output
}

fun selectedLayerMapKey(renders: List<LayerRenderSnapshot>): MapFeatureKey? = renders.firstNotNullOfOrNull { layer ->
    layer.selectedFeatureId?.takeIf { layer.style.visible }?.let { MapFeatureKey(IMPORTED_LAYER_PREFIX + layer.id, it) }
}
