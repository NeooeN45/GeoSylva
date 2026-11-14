package com.quintessences.geosylva.cartography.mapbox

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.mapbox.bindgen.Value
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.image.image
import kotlin.math.ceil

internal const val OVERLAY_LAYER_PROPERTY = "gs_layer_id"
internal const val OVERLAY_FEATURE_PROPERTY = "gs_feature_id"

/** No business attributes are published to the SDK other than display and opaque selection IDs. */
internal fun overlayFeatureCollection(scene: MapOverlayScene, labelImages: Map<MapFeatureKey, String> = emptyMap()): FeatureCollection =
    FeatureCollection.fromFeatures(scene.features.map { item ->
        Feature.fromGeometry(item.geometry).apply {
            addStringProperty(OVERLAY_LAYER_PROPERTY, item.key.layerId)
            addStringProperty(OVERLAY_FEATURE_PROPERTY, item.key.featureId)
            addBooleanProperty("gs_selectable", item.selectable)
            addStringProperty("gs_color", item.style.colorHex)
            addNumberProperty("gs_width", item.style.lineWidth + if (item.key == scene.selectedKey) 2.0 else 0.0)
            addNumberProperty("gs_radius", item.style.pointRadius +
                if (item.style.pointRadius > 0.0 && item.key == scene.selectedKey) 2.0 else 0.0)
            addNumberProperty("gs_point_stroke", if (item.style.pointRadius > 0.0)
                item.style.strokeWidth.takeIf { it > 0.0 } ?: 2.0 else 0.0)
            addStringProperty("gs_stroke_color", item.style.strokeColorHex)
            addNumberProperty("gs_casing_width", if (item.style.strokeWidth > 0.0 && item.style.lineWidth > 0.0)
                item.style.lineWidth + item.style.strokeWidth * 2 + if (item.key == scene.selectedKey) 2 else 0 else 0.0)
            addNumberProperty("gs_fill_opacity", item.style.fillOpacity)
            addStringProperty("gs_dash", item.style.lineDashArray.joinToString(","))
            labelImages[item.key]?.let { addStringProperty("gs_label_image", it) }
        }
    })

/** One style owner. Failed replacement hides all owned layers, never stale authorized features. */
internal class NativeMapOverlays {
    private var installedStyle: Style? = null
    private val layers = mutableListOf<String>()
    private val images = mutableListOf<String>()
    private var sourceInstalled = false
    private var generation = 0L
    var appliedScene: MapOverlayScene? = null
        private set
    val queryLayerIds: List<String> get() = layers.toList()

    fun apply(style: Style, scene: MapOverlayScene, density: Float): MapOverlayStatus {
        if (installedStyle === style && appliedScene === scene) return MapOverlayStatus.Applied(scene.scopeId, scene.revision)
        if (installedStyle !== style) {
            // Old style resources belong to that disposed native generation.
            layers.clear(); images.clear(); sourceInstalled = false
            installedStyle = style
        }
        clear(style)
        generation++
        var failure = MapOverlayFailure.SOURCE
        return try {
            val labelImages = mutableMapOf<MapFeatureKey, String>()
            failure = MapOverlayFailure.LABEL
            scene.features.forEachIndexed { index, feature ->
                feature.label?.takeIf { it.isNotBlank() }?.let { text ->
                    val id = "gs-overlay-label-$generation-$index"
                    val bitmap = labelBitmap(text, feature.style, density)
                    try {
                        image(id, bitmap) { scale(density) }.bindTo(style)
                        check(style.hasStyleImage(id))
                        images += id
                        labelImages[feature.key] = id
                    } finally { bitmap.recycle() }
                }
            }
            failure = MapOverlayFailure.SOURCE
            val data = Value.fromJson(overlayFeatureCollection(scene, labelImages).toJson())
            check(!data.isError)
            val source = Value(hashMapOf("type" to Value("geojson"), "data" to requireNotNull(data.value)))
            check(!style.addStyleSource(SOURCE_ID, source).isError)
            sourceInstalled = true
            failure = MapOverlayFailure.LAYER
            addLayer(style, "fill", "fill", hashMapOf(
                "fill-color" to get("gs_color"), "fill-opacity" to get("gs_fill_opacity")),
                filter = eq(expression("geometry-type"), Value("Polygon")))

            // Dash arrays are constant per layer, not falsely data-driven paint properties.
            scene.features.map { it.style.lineDashArray }.distinct().forEachIndexed { index, dash ->
                val paint = hashMapOf("line-color" to get("gs_color"), "line-width" to get("gs_width"))
                if (dash.isNotEmpty()) paint["line-dasharray"] = Value(dash.map(::Value))
                addLayer(style, "line-$index", "line", paint,
                    layout = hashMapOf("line-cap" to Value("round"), "line-join" to Value("round")),
                    filter = expression("all", eq(get("gs_dash"), Value(dash.joinToString(","))),
                        expression("any", eq(expression("geometry-type"), Value("LineString")),
                            eq(expression("geometry-type"), Value("Polygon")))))
            }
            addLayer(style, "points", "circle", hashMapOf(
                "circle-color" to get("gs_color"), "circle-radius" to get("gs_radius"),
                "circle-stroke-color" to Value("#FFFFFF"), "circle-stroke-width" to get("gs_point_stroke")),
                filter = eq(expression("geometry-type"), Value("Point")))
            // Local bitmap labels need no remote glyph endpoint and work with locally available vectors.
            if (labelImages.isNotEmpty()) addLayer(style, "labels", "symbol", hashMapOf(),
                layout = hashMapOf("icon-image" to get("gs_label_image"), "icon-allow-overlap" to Value(false)),
                filter = expression("has", Value("gs_label_image")))
            appliedScene = scene
            MapOverlayStatus.Applied(scene.scopeId, scene.revision)
        } catch (_: Exception) {
            clear(style)
            MapOverlayStatus.Failed(failure)
        }
    }

    private fun addLayer(
        style: Style, suffix: String, type: String, paint: HashMap<String, Value>,
        layout: HashMap<String, Value> = hashMapOf(), filter: Value,
    ) {
        val id = "gs-overlay-$suffix"
        val properties = hashMapOf("id" to Value(id), "type" to Value(type), "source" to Value(SOURCE_ID),
            "paint" to Value(paint), "layout" to Value(layout), "filter" to filter)
        check(!style.addStyleLayer(Value(properties), null).isError)
        layers += id
    }

    private fun clear(style: Style) {
        appliedScene = null
        layers.asReversed().forEach { runCatching { style.removeStyleLayer(it) } }
        layers.clear()
        if (sourceInstalled) runCatching { style.removeStyleSource(SOURCE_ID) }
        sourceInstalled = false
        images.forEach { runCatching { style.removeStyleImage(it) } }
        images.clear()
    }

    private companion object { const val SOURCE_ID = "gs-overlay-source" }
}

private fun get(property: String): Value = expression("get", Value(property))
private fun eq(left: Value, right: Value): Value = expression("==", left, right)
private fun expression(operator: String, vararg args: Value): Value = Value(listOf(Value(operator)) + args)

/** Display truncation only: full label remains in the host object, never used as its identity. */
private fun labelBitmap(text: String, style: MapOverlayStyle, density: Float): Bitmap {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(style.labelColorHex)
        textSize = (style.labelSize * density).toFloat()
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val display = if (text.length > 96) text.take(95) + "…" else text
    val horizontal = 7 * density
    val vertical = 3 * density
    val metrics = paint.fontMetrics
    val width = ceil(paint.measureText(display) + horizontal * 2).toInt().coerceAtLeast(1)
    val height = ceil(metrics.descent - metrics.ascent + vertical * 2).toInt().coerceAtLeast(1)
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        Canvas(bitmap).apply {
            drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 6 * density, 6 * density,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(240, 255, 255, 255) })
            drawText(display, horizontal, vertical - metrics.ascent, paint)
        }
    }
}
