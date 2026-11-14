package com.quintessences.geosylva.cartography.mapbox

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.mapbox.bindgen.Value
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Geometry
import com.mapbox.geojson.GeometryCollection
import com.mapbox.geojson.Point
import com.mapbox.geojson.MultiPoint
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.image.image
import kotlin.math.ceil

internal const val OVERLAY_LAYER_PROPERTY = "gs_layer_id"
internal const val OVERLAY_FEATURE_PROPERTY = "gs_feature_id"

/** No business attributes are published to the SDK other than display and opaque selection IDs. */
internal fun overlayFeatureCollection(scene: MapOverlayScene, labelImages: Map<MapFeatureKey, String> = emptyMap(),
    pinImages: Map<MapFeatureKey, String> = emptyMap(), patternImages: Map<MapFeatureKey, String> = emptyMap()): FeatureCollection =
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
            addNumberProperty("gs_opacity", item.style.opacity)
            addNumberProperty("gs_fill_opacity", item.style.fillOpacity * item.style.opacity)
            addStringProperty("gs_point_symbol", item.style.pointSymbol.name)
            addStringProperty("gs_dash", item.style.lineDashArray.joinToString(","))
            labelImages[item.key]?.let { addStringProperty("gs_label_image", it) }
            pinImages[item.key]?.let { addStringProperty("gs_pin_image", it) }
            patternImages[item.key]?.let {
                addStringProperty("gs_fill_pattern", MapOverlayFillPattern.HATCH.name)
                addStringProperty("gs_fill_pattern_image", it)
            }
        }
    })

/** Geometry collections keep the sprite for point children; the native filter excludes their line/area children. */
internal fun needsPinSprite(feature: MapOverlayFeature): Boolean {
    fun containsPoints(geometry: Geometry): Boolean = when (geometry) {
        is Point, is MultiPoint -> true
        is GeometryCollection -> geometry.geometries().any(::containsPoints)
        else -> false
    }
    return feature.style.pointSymbol == MapOverlayPointSymbol.PIN && feature.style.pointRadius > 0.0 && containsPoints(feature.geometry)
}

/** One style owner. Failed replacement hides all owned layers, never stale authorized features. */
internal class NativeMapOverlays {
    private data class LabelKey(val text: String, val color: String, val size: Double, val density: Float, val kind: Int = 0)
    private data class ImageAsset(val id: String, val bytes: Long)
    private var installedStyle: Style? = null
    private val layers = mutableListOf<String>()
    private val labelImages = LinkedHashMap<LabelKey, ImageAsset>(16, 0.75f, true)
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
        val activePins = mutableMapOf<MapFeatureKey, String>()
        val activePatterns = mutableMapOf<MapFeatureKey, String>()
        val budget = OverlayRasterBudget<LabelKey>()
        val activeLabelKeys = mutableSetOf<LabelKey>()
        var omittedLabels = 0
        var omittedPatterns = 0
        fun imageAsset(key: LabelKey, width: Int, height: Int, bitmap: () -> Bitmap): String? {
            val byteCount = rgbaImageBytes(width,height) ?: return null
            if (!budget.reserve(key,byteCount)) return null
            labelImages[key]?.takeIf { style.hasStyleImage(it.id) }?.let { return it.id }
            val created = "gs-overlay-image-${++imageSerial}"
            val raster = bitmap()
            try {
                image(created, raster) { scale(density) }.bindTo(style)
                check(style.hasStyleImage(created))
                labelImages[key] = ImageAsset(created,byteCount)
            } finally { raster.recycle() }
            return created
        }
        fun prepareLabel(feature: MapOverlayFeature) {
            val text = feature.label?.takeIf { it.isNotBlank() } ?: return
            val key = LabelKey(text,feature.style.labelColorHex,feature.style.labelSize,density)
            val shared = activeLabelKeys.contains(key)
            if (!shared && (activeLabelKeys.size >= MAX_ACTIVE_LABELS || !budget.canPrepareNewImage)) { omittedLabels++; return }
            val cached = labelImages[key]?.takeIf { style.hasStyleImage(it.id) }
            val id = if (cached != null) {
                if (budget.reserve(key,cached.bytes)) cached.id else null
            } else {
                val layout = labelLayout(text,feature.style,density)
                imageAsset(key,layout.width,layout.height) { labelBitmap(layout) }
            }
            if (id == null) omittedLabels++ else { activeLabelKeys += key; activeImages[feature.key] = id }
        }
        try {
            val prioritized = imagePriority(scene)
            // A selected label gets its budget before every optional raster decoration.
            prioritized.firstOrNull { it.key == scene.selectedKey }?.let(::prepareLabel)
            prioritized.forEach { feature ->
                if (needsPinSprite(feature) && budget.canPrepareNewImage) {
                    val key = LabelKey("",feature.style.colorHex,feature.style.pointRadius,density,1)
                    val dimensions = pinDimensions(feature.style,density)
                    imageAsset(key,dimensions.first,dimensions.second) { pinBitmap(feature.style,density) }?.let { activePins[feature.key] = it }
                }
                if (feature.style.fillPattern == MapOverlayFillPattern.HATCH && hasPolygonGeometry(feature.geometry)) {
                    val tile = hatchTilePixels(density)
                    val key = LabelKey("",feature.style.colorHex,tile?.toDouble() ?: 0.0,density,2)
                    val id = if (tile == null || !budget.canPrepareNewImage) null
                        else imageAsset(key,tile,tile) { hatchBitmap(feature.style.colorHex,tile,density) }
                    if (id == null) omittedPatterns++ else activePatterns[feature.key] = id
                }
            }
            prioritized.filterNot { it.key == scene.selectedKey }.forEach(::prepareLabel)
        } catch (_: Exception) { return fail(style, MapOverlayFailure.LABEL) }

        try {
            val data = Value.fromJson(overlayFeatureCollection(scene, activeImages, activePins, activePatterns).toJson())
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
            protectedImageIds = activeImages.values.toSet() + activePins.values + activePatterns.values
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
                    filter = expression("all",eq(expression("geometry-type"),Value("Polygon")),
                        expression("!",expression("has",Value("gs_fill_pattern")))))
                addLayer(style, "fill-hatch", "fill", hashMapOf(
                    "fill-pattern" to get("gs_fill_pattern_image"), "fill-opacity" to get("gs_fill_opacity")),
                    filter = expression("all",eq(expression("geometry-type"),Value("Polygon")),
                        eq(get("gs_fill_pattern"),Value(MapOverlayFillPattern.HATCH.name))))
                // Dash arrays stay constant per layer rather than unsupported data-driven dash paint.
                patterns.forEachIndexed { index, dash ->
                    val geometry = expression("any", eq(expression("geometry-type"), Value("LineString")),
                        eq(expression("geometry-type"), Value("Polygon")))
                    val filter = expression("all", eq(get("gs_dash"), Value(dash.joinToString(","))), geometry)
                    val layout = hashMapOf("line-cap" to Value("round"), "line-join" to Value("round"))
                    val casing = hashMapOf("line-color" to get("gs_stroke_color"), "line-width" to get("gs_casing_width"), "line-opacity" to get("gs_opacity"))
                    val paint = hashMapOf("line-color" to get("gs_color"), "line-width" to get("gs_width"), "line-opacity" to get("gs_opacity"))
                    if (dash.isNotEmpty()) {
                        casing["line-dasharray"] = Value(dash.map(::Value))
                        paint["line-dasharray"] = Value(dash.map(::Value))
                    }
                    addLayer(style, "casing-$index", "line", casing, layout, filter)
                    addLayer(style, "line-$index", "line", paint, layout, filter)
                }
                addLayer(style, "points", "circle", hashMapOf(
                    "circle-color" to get("gs_color"), "circle-radius" to get("gs_radius"),
                    "circle-opacity" to get("gs_opacity"), "circle-stroke-opacity" to get("gs_opacity"),
                    "circle-stroke-color" to get("gs_stroke_color"), "circle-stroke-width" to get("gs_point_stroke")),
                    filter = expression("all", eq(expression("geometry-type"), Value("Point")),
                        expression("!",expression("has",Value("gs_pin_image")))))
                addLayer(style, "pins", "symbol", hashMapOf("icon-opacity" to get("gs_opacity")),
                    layout = hashMapOf("icon-image" to get("gs_pin_image"), "icon-anchor" to Value("bottom"),
                        "icon-allow-overlap" to Value(true)),
                    filter = expression("all", eq(expression("geometry-type"), Value("Point")),
                        expression("has", Value("gs_pin_image"))))
                // Local bitmap labels never need a remote glyph endpoint.
                addLayer(style, "labels", "symbol", hashMapOf("icon-opacity" to get("gs_opacity")),
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
        return MapOverlayStatus.Applied(scene.scopeId, scene.revision, omittedLabels, omittedPatterns).also { lastStatus = it }
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
        val unused = labelImages.entries.filter { it.value.id !in protectedImageIds }
        var count = unused.size
        var bytes = unused.sumOf { it.value.bytes }
        unused.forEach { entry ->
            if (count <= MAX_UNUSED_LABELS && bytes <= MAX_UNUSED_BYTES) return@forEach
            if (runCatching { !style.removeStyleImage(entry.value.id).isError }.getOrDefault(false)) {
                labelImages.remove(entry.key); count--; bytes -= entry.value.bytes
            }
        }
    }

    private companion object {
        const val SOURCE_ID = "gs-overlay-source"
        const val MAX_ACTIVE_LABELS = 256
        const val MAX_UNUSED_LABELS = 64
        const val MAX_UNUSED_BYTES = 1024L * 1024
    }

}

private fun get(property: String): Value = expression("get", Value(property))
private fun eq(left: Value, right: Value): Value = expression("==", left, right)
private fun expression(operator: String, vararg args: Value): Value = Value(listOf(Value(operator)) + args)

/** One reusable geographic pin; its tip, not its bitmap centre, anchors the coordinate. */
private fun pinBitmap(style: MapOverlayStyle, density: Float): Bitmap {
    val radius = (style.pointRadius * density).toFloat().coerceAtLeast(2f)
    val (width,height) = pinDimensions(style,density)
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        val cx = width / 2f
        val cy = radius * 1.3f
        val tip = height - density
        val silhouette = Path().apply {
            moveTo(cx, tip)
            cubicTo(cx - radius * 0.25f, tip - radius * 0.7f, cx - radius, cy + radius * 0.8f, cx - radius, cy)
            cubicTo(cx - radius, cy - radius * 1.35f, cx + radius, cy - radius * 1.35f, cx + radius, cy)
            cubicTo(cx + radius, cy + radius * 0.8f, cx + radius * 0.25f, tip - radius * 0.7f, cx, tip)
            close()
        }
        canvas.drawPath(silhouette, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; this.style = Paint.Style.STROKE; strokeWidth = 2f * density })
        canvas.drawPath(silhouette, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(style.colorHex) })
        canvas.drawCircle(cx, cy, radius * 0.35f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
    }
}

private fun pinDimensions(style: MapOverlayStyle, density: Float): Pair<Int,Int> {
    val radius = (style.pointRadius * density).toFloat().coerceAtLeast(2f)
    return ceil(radius * 2.6f).toInt() to ceil(radius * 3.6f).toInt()
}

internal fun hasPolygonGeometry(geometry: Geometry): Boolean = when (geometry) {
    is com.mapbox.geojson.Polygon, is com.mapbox.geojson.MultiPolygon -> true
    is GeometryCollection -> geometry.geometries().any(::hasPolygonGeometry)
    else -> false
}

/** Seamless local diagonal tile; real fill-pattern repeat, with a power-of-two physical bitmap. */
private fun hatchBitmap(colorHex: String, size: Int, density: Float): Bitmap =
    Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(colorHex); strokeWidth = (density * 0.9f).coerceIn(1f,3f)
        }
        for (offset in -size..size step size) canvas.drawLine(offset.toFloat(),size.toFloat(),(offset+size).toFloat(),0f,paint)
    }

private data class LabelLayout(val display: String, val width: Int, val height: Int,
    val horizontal: Float, val vertical: Float, val ascent: Float, val paint: Paint)

/** Compute real density-scaled pixel dimensions before admitting any allocation. */
private fun labelLayout(text: String, style: MapOverlayStyle, density: Float): LabelLayout {
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
    return LabelLayout(display,ceil(paint.measureText(display)+horizontal*2).toInt().coerceAtLeast(1),
        ceil(metrics.descent-metrics.ascent+vertical*2).toInt().coerceAtLeast(1),horizontal,vertical,metrics.ascent,paint)
}

/** Only already-admitted rasters allocate; full text remains in the host object. */
private fun labelBitmap(layout: LabelLayout): Bitmap = with(layout) {
    Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).also { bitmap ->
        Canvas(bitmap).apply {
            drawRoundRect(0f,0f,width.toFloat(),height.toFloat(),6*paint.textSize/13f,6*paint.textSize/13f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(240,255,255,255) })
            drawText(display,horizontal,vertical-ascent,paint)
        }
    }
}
