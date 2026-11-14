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
    private data class LabelKey(val text: String, val color: String, val size: Double, val density: Float)
    private var installedStyle: Style? = null
    private val layers = mutableListOf<String>()
    private val labelImages = LinkedHashMap<LabelKey, String>(16, 0.75f, true)
    private var sourceInstalled = false
    private var imageSerial = 0L
    private var layerPatterns: List<List<Double>>? = null
    private var lastStatus: MapOverlayStatus? = null
    private var lastDensity = 0f
    private var protectedImageIds: Set<String> = emptySet()
    var attemptedScene: MapOverlayScene? = null
        private set
    var appliedScene: MapOverlayScene? = null
        private set
    val queryLayerIds: List<String> get() = layers.toList()

    fun hasAttempted(style: Style, scene: MapOverlayScene, density: Float): Boolean =
        installedStyle === style && attemptedScene === scene && lastDensity == density

    fun apply(style: Style, scene: MapOverlayScene, density: Float): MapOverlayStatus {
        if (hasAttempted(style, scene, density)) return requireNotNull(lastStatus)
        if (installedStyle !== style) {
            // Native images/sources of the previous generation are owned by its disposed style.
            layers.clear(); labelImages.clear(); sourceInstalled = false; layerPatterns = null
            protectedImageIds = emptySet()
            installedStyle = style
        }
        attemptedScene = scene
        lastDensity = density
        appliedScene = null
        val activeImages = mutableMapOf<MapFeatureKey, String>()
        try {
            scene.features.forEach { feature ->
                feature.label?.takeIf { it.isNotBlank() }?.let { text ->
                    val key = LabelKey(text, feature.style.labelColorHex, feature.style.labelSize, density)
                    var id = labelImages[key]
                    if (id == null || !style.hasStyleImage(id)) {
                        id = "gs-overlay-label-${++imageSerial}"
                        val bitmap = labelBitmap(text, feature.style, density)
                        try {
                            image(id, bitmap) { scale(density) }.bindTo(style)
                            if (!style.hasStyleImage(id)) return fail(style, MapOverlayFailure.LABEL)
                            labelImages[key] = id
                        } finally { bitmap.recycle() }
                    }
                    activeImages[feature.key] = id
                }
            }
        } catch (_: Exception) { return fail(style, MapOverlayFailure.LABEL) }

        try {
            val data = Value.fromJson(overlayFeatureCollection(scene, activeImages).toJson())
            if (data.isError) return fail(style, MapOverlayFailure.SOURCE)
            val value = requireNotNull(data.value)
            if (sourceInstalled) {
                if (style.setStyleSourceProperty(SOURCE_ID, "data", value).isError)
                    return fail(style, MapOverlayFailure.SOURCE)
            } else {
                val source = Value(hashMapOf("type" to Value("geojson"), "data" to value))
                if (style.addStyleSource(SOURCE_ID, source).isError) return fail(style, MapOverlayFailure.SOURCE)
                sourceInstalled = true
            }
            protectedImageIds = activeImages.values.toSet()
        } catch (_: Exception) { return fail(style, MapOverlayFailure.SOURCE) }

        try {
            val patterns = scene.features.map { it.style.lineDashArray }.distinct().sortedBy { it.joinToString(",") }
            if (patterns != layerPatterns) {
                // Retain IDs when native removal fails, so cleanup/retry never forgets an owned layer.
                for (id in layers.asReversed().toList()) {
                    if (style.removeStyleLayer(id).isError) return fail(style, MapOverlayFailure.LAYER)
                    layers.remove(id)
                }
                layerPatterns = null
                addLayer(style, "fill", "fill", hashMapOf(
                    "fill-color" to get("gs_color"), "fill-opacity" to get("gs_fill_opacity")),
                    filter = eq(expression("geometry-type"), Value("Polygon")))
                // Dash arrays stay constant per layer rather than unsupported data-driven dash paint.
                patterns.forEachIndexed { index, dash ->
                    val geometry = expression("any", eq(expression("geometry-type"), Value("LineString")),
                        eq(expression("geometry-type"), Value("Polygon")))
                    val filter = expression("all", eq(get("gs_dash"), Value(dash.joinToString(","))), geometry)
                    val layout = hashMapOf("line-cap" to Value("round"), "line-join" to Value("round"))
                    val casing = hashMapOf("line-color" to get("gs_stroke_color"), "line-width" to get("gs_casing_width"))
                    val paint = hashMapOf("line-color" to get("gs_color"), "line-width" to get("gs_width"))
                    if (dash.isNotEmpty()) {
                        casing["line-dasharray"] = Value(dash.map(::Value))
                        paint["line-dasharray"] = Value(dash.map(::Value))
                    }
                    addLayer(style, "casing-$index", "line", casing, layout, filter)
                    addLayer(style, "line-$index", "line", paint, layout, filter)
                }
                addLayer(style, "points", "circle", hashMapOf(
                    "circle-color" to get("gs_color"), "circle-radius" to get("gs_radius"),
                    "circle-stroke-color" to get("gs_stroke_color"), "circle-stroke-width" to get("gs_point_stroke")),
                    filter = eq(expression("geometry-type"), Value("Point")))
                // Local bitmap labels never need a remote glyph endpoint.
                addLayer(style, "labels", "symbol", hashMapOf(),
                    layout = hashMapOf("icon-image" to get("gs_label_image"), "icon-allow-overlap" to Value(false)),
                    filter = expression("has", Value("gs_label_image")))
                layerPatterns = patterns
            }
            // Restores layers hidden by an earlier failed update only after a coherent replacement.
            layers.forEach { if (style.setStyleLayerProperty(it, "visibility", Value("visible")).isError)
                return fail(style, MapOverlayFailure.LAYER) }
        } catch (_: Exception) { return fail(style, MapOverlayFailure.LAYER) }

        // Native sprites needed by this scene remain; retain at most 64 unused labels for rapid reselection.
        trimUnusedImages(style)
        appliedScene = scene
        return MapOverlayStatus.Applied(scene.scopeId, scene.revision).also { lastStatus = it }
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

    private fun fail(style: Style, reason: MapOverlayFailure): MapOverlayStatus {
        appliedScene = null
        // Never leave the old authorized snapshot visible when the replacement was rejected.
        // Retain ownership of anything the native API cannot remove; a host must also honor Failed.
        layers.toList().forEach { id ->
            val hidden = runCatching { !style.setStyleLayerProperty(id, "visibility", Value("none")).isError }.getOrDefault(false)
            if (!hidden && runCatching { !style.removeStyleLayer(id).isError }.getOrDefault(false)) {
                layers.remove(id); layerPatterns = null
            }
        }
        trimUnusedImages(style)
        return MapOverlayStatus.Failed(reason).also { lastStatus = it }
    }

    private fun trimUnusedImages(style: Style) {
        val unused = labelImages.entries.filter { it.value !in protectedImageIds }
        unused.take((unused.size - MAX_UNUSED_LABELS).coerceAtLeast(0)).forEach { entry ->
            // Keep ownership if the native API rejects removal, so the next update can retry cleanup.
            if (runCatching { !style.removeStyleImage(entry.value).isError }.getOrDefault(false))
                labelImages.remove(entry.key)
        }
    }

    private companion object {
        const val SOURCE_ID = "gs-overlay-source"
        const val MAX_UNUSED_LABELS = 64
    }
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
    var display = if (text.length > 96) text.take(95) + "…" else text
    val maxTextWidth = 384 * density
    while (paint.measureText(display) > maxTextWidth && display.length > 1) {
        display = display.dropLast(if (display.endsWith("…")) 2 else 1) + "…"
    }
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
