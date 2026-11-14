package com.quintessences.geosylva.cartography.mapbox

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.google.gson.JsonArray
import com.mapbox.bindgen.Value
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Geometry
import com.mapbox.geojson.GeometryCollection
import com.mapbox.geojson.Point
import com.mapbox.geojson.MultiPoint
import com.mapbox.maps.GeoJSONSourceData
import com.mapbox.maps.Style
import java.util.UUID
import com.mapbox.maps.extension.style.image.image
import kotlin.math.ceil

internal const val OVERLAY_LAYER_PROPERTY = "gs_layer_id"
internal const val OVERLAY_FEATURE_PROPERTY = "gs_feature_id"

/** No business attributes are published to the SDK other than display and opaque selection IDs. */
internal fun overlayFeatureCollection(scene: MapOverlayScene, labelImages: Map<MapFeatureKey, String> = emptyMap(),
    pinImages: Map<MapFeatureKey, String> = emptyMap(), patternImages: Map<MapFeatureKey, String> = emptyMap(),
    dataId: String? = null, renderDensity: Float = 1f): FeatureCollection =
    FeatureCollection.fromFeatures(scene.features.map { item ->
        Feature.fromGeometry(item.geometry).apply {
            addStringProperty(OVERLAY_LAYER_PROPERTY, item.key.layerId)
            addStringProperty(OVERLAY_FEATURE_PROPERTY, item.key.featureId)
            addStringProperty(MAP_OVERLAY_SCENE_REVISION_PROPERTY,scene.revision.toString())
            dataId?.let { addStringProperty(MAP_OVERLAY_DATA_ID_PROPERTY,it) }
            item.label?.takeIf { it.isNotBlank() }?.take(96)?.let { addStringProperty(MAP_OVERLAY_LABEL_PROPERTY,it) }
            addBooleanProperty("gs_selectable", item.selectable)
            addNumberProperty("gs_label_priority", if (item.key == scene.selectedKey) 0 else 1)
            addStringProperty("gs_color", item.style.colorHex)
            addNumberProperty("gs_width", item.style.lineWidth + if (item.key == scene.selectedKey) 2.0 else 0.0)
            addNumberProperty("gs_radius", item.style.pointRadius +
                if (item.style.pointRadius > 0.0 && item.key == scene.selectedKey) 2.0 else 0.0)
            addNumberProperty("gs_point_stroke", if (item.style.pointRadius > 0.0)
                item.style.strokeWidth.takeIf { it > 0.0 } ?: 2.0 else 0.0)
            addStringProperty("gs_stroke_color", item.style.strokeColorHex)
            addNumberProperty("gs_casing_width", if (item.style.strokeWidth > 0.0 && item.style.lineWidth > 0.0)
                item.style.lineWidth + item.style.strokeWidth * 2 + if (item.key == scene.selectedKey) 2 else 0 else 0.0)
            addNumberProperty("gs_pin_scale", if (item.key == scene.selectedKey &&
                item.style.pointSymbol.isPin()) 1.2 else 1.0)
            addNumberProperty("gs_opacity", item.style.opacity)
            addNumberProperty("gs_fill_opacity", item.style.fillOpacity * item.style.opacity)
            addStringProperty("gs_point_symbol", item.style.pointSymbol.name)
            addStringProperty("gs_dash", item.style.lineDashArray.joinToString(","))
            val labelPlacement = pointLabelPlacement(item.style,item.key == scene.selectedKey,pinImages.containsKey(item.key),renderDensity)
            addStringProperty("gs_label_point_anchor",labelPlacement.anchor)
            addProperty("gs_label_point_offset",JsonArray().apply { add(0.0); add(labelPlacement.offsetY) })
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
    return feature.style.pointSymbol.isPin() && feature.style.pointRadius > 0.0 && containsPoints(feature.geometry)
}

/** Exhaustive sprite family: a future point preset must explicitly choose its renderer. */
private fun MapOverlayPointSymbol.isPin(): Boolean = when (this) {
    MapOverlayPointSymbol.CIRCLE -> false
    MapOverlayPointSymbol.PIN, MapOverlayPointSymbol.PIN_CHECKED -> true
}

/** One style owner. Failed replacement hides all owned layers, never stale authorized features. */
internal class NativeMapOverlays {
    private data class LabelKey(val text: String, val color: String, val size: Double, val density: Float, val kind: Int = 0,
        val badge: MapOverlayLabelBadge = MapOverlayLabelBadge.PILL, val badgeOutline: String? = null,
        val pointSymbol: MapOverlayPointSymbol = MapOverlayPointSymbol.CIRCLE)
    private data class ImageAsset(val id: String, val bytes: Long)
    private var installedStyle: Style? = null
    private val layers = mutableListOf<String>()
    private val labelImages = LinkedHashMap<LabelKey, ImageAsset>(16, 0.75f, true)
    private var sourceInstalled = false
    private var imageSerial = 0L
    private var layerPatterns: List<List<Double>>? = null
    private var lastStatus: MapOverlayStatus? = null
    private val sourceAck = OverlaySourceAckGate()
    private var acknowledgedResult: MapOverlayStatus.Applied? = null
    private var lastDensity = 0f
    private var lastFontScale = 1f
    private var protectedImageIds: Set<String> = emptySet()
    var attemptedScene: MapOverlayScene? = null
        private set
    var appliedScene: MapOverlayScene? = null
        private set
    val queryLayerIds: List<String> get() = if (appliedScene == null) emptyList() else layers.toList()

    fun hasAttempted(style: Style, scene: MapOverlayScene, density: Float, fontScale: Float = 1f): Boolean =
        installedStyle === style && attemptedScene === scene && lastDensity == density && lastFontScale == fontScale

    fun apply(style: Style, scene: MapOverlayScene, density: Float, fontScale: Float = 1f): MapOverlayStatus {
        if (hasAttempted(style, scene, density, fontScale)) return requireNotNull(lastStatus)
        if (installedStyle !== style) {
            // Native images/sources of the previous generation are owned by its disposed style.
            layers.clear(); labelImages.clear(); sourceInstalled = false; layerPatterns = null
            protectedImageIds = emptySet()
            installedStyle = style
        }
        attemptedScene = scene
        lastDensity = density
        lastFontScale = fontScale
        appliedScene = null
        sourceAck.invalidate()
        acknowledgedResult = null
        // The former source may still be processing: remove its visible/queryable layers first.
        if (!hideLayers(style)) return fail(style,MapOverlayFailure.LAYER)
        val dataId = UUID.randomUUID().toString()
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
            val key = LabelKey(text,feature.style.labelColorHex,feature.style.labelSize*fontScale,density,
                badge = feature.style.labelBadge, badgeOutline = feature.style.colorHex.takeIf { feature.style.labelBadge == MapOverlayLabelBadge.CIRCLE })
            val shared = activeLabelKeys.contains(key)
            if (!shared && (activeLabelKeys.size >= MAX_ACTIVE_LABELS || !budget.canPrepareNewImage)) { omittedLabels++; return }
            val cached = labelImages[key]?.takeIf { style.hasStyleImage(it.id) }
            val id = if (cached != null) {
                if (budget.reserve(key,cached.bytes)) cached.id else null
            } else {
                val layout = labelLayout(text,feature.style,density,fontScale)
                imageAsset(key,layout.width,layout.height) { labelBitmap(layout) }
            }
            if (id == null) omittedLabels++ else { activeLabelKeys += key; activeImages[feature.key] = id }
        }
        try {
            val prioritized = imagePriority(scene)
            // A selected label gets its budget before every optional raster decoration.
            prioritized.firstOrNull { it.key == scene.selectedKey }?.let(::prepareLabel)
            prioritized.forEach { feature ->
                if (needsPinSprite(feature)) {
                    // Identical dimensions/colour cannot reuse a sprite with a different interior glyph.
                    val key = LabelKey("",feature.style.colorHex,feature.style.pointRadius,density,1,
                        pointSymbol = feature.style.pointSymbol)
                    val dimensions = pinDimensions(feature.style,density)
                    imageAsset(key,dimensions.first,dimensions.second) { pinBitmap(feature.style,density) }?.let { activePins[feature.key] = it }
                }
                if (feature.style.fillPattern == MapOverlayFillPattern.HATCH && hasPolygonGeometry(feature.geometry)) {
                    val tile = hatchTilePixels(density)
                    val key = LabelKey("",feature.style.colorHex,tile?.toDouble() ?: 0.0,density,2)
                    val id = if (tile == null) null
                        else imageAsset(key,tile,tile) { hatchBitmap(feature.style.colorHex,tile,density) }
                    if (id == null) omittedPatterns++ else activePatterns[feature.key] = id
                }
            }
            prioritized.filterNot { it.key == scene.selectedKey }.forEach(::prepareLabel)
        } catch (_: Exception) { return fail(style, MapOverlayFailure.LABEL) }

        val collection = try { overlayFeatureCollection(scene,activeImages,activePins,activePatterns,dataId,density) }
            catch (_: Exception) { return fail(style,MapOverlayFailure.SOURCE) }
        try {
            if (!sourceInstalled) {
                // Initial metadata may be synchronous and has no submission ID; it cannot acknowledge this scene.
                val empty = Value.fromJson(FeatureCollection.fromFeatures(emptyList()).toJson())
                if (empty.isError) return fail(style,MapOverlayFailure.SOURCE)
                val source = Value(hashMapOf("type" to Value("geojson"), "data" to requireNotNull(empty.value)))
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
                    layout = hashMapOf("icon-image" to get("gs_pin_image"), "icon-anchor" to Value("bottom"), "icon-size" to get("gs_pin_scale"),
                        "icon-allow-overlap" to Value(true)),
                    filter = expression("all", eq(expression("geometry-type"), Value("Point")),
                        expression("has", Value("gs_pin_image"))))
                // Local bitmap labels never need a remote glyph endpoint.
                addLayer(style, "labels", "symbol", hashMapOf("icon-opacity" to get("gs_opacity")),
                    layout = hashMapOf("icon-image" to get("gs_label_image"), "icon-allow-overlap" to Value(false),
                        "symbol-sort-key" to get("gs_label_priority"),
                        // Geometry collections may yield both point and line children: decide per native child geometry.
                        "icon-anchor" to expression("case",eq(expression("geometry-type"),Value("Point")),
                            get("gs_label_point_anchor"),Value("center")),
                        "icon-offset" to expression("case",eq(expression("geometry-type"),Value("Point")),
                            expression("array",Value("number"),Value(2L),get("gs_label_point_offset")),
                            expression("literal",Value(listOf(Value(0.0),Value(0.0)))))),
                    filter = expression("has", Value("gs_label_image")))
                layerPatterns = patterns
            }
        } catch (_: Exception) { return fail(style, MapOverlayFailure.LAYER) }

        // Native sprites needed by this scene remain; retain at most 64 unused labels for rapid reselection.
        trimUnusedImages(style)
        sourceAck.submit(style,scene,dataId)
        acknowledgedResult = MapOverlayStatus.Applied(scene.scopeId,scene.revision,omittedLabels,omittedPatterns,dataId)
        lastStatus = MapOverlayStatus.Pending(scene.scopeId,scene.revision,dataId)
        try {
            // Setter acceptance only enqueues work. SourceDataLoaded(dataId) is the processing barrier.
            if (style.setStyleGeoJSONSourceData(SOURCE_ID,dataId,GeoJSONSourceData(collection.features().orEmpty())).isError)
                return fail(style,MapOverlayFailure.SOURCE)
        } catch (_: Exception) { return fail(style,MapOverlayFailure.SOURCE) }
        return requireNotNull(lastStatus)
    }

    fun onSourceDataLoaded(style: Style, scene: MapOverlayScene, sourceId: String, dataId: String?): MapOverlayStatus? {
        val ticket = sourceAck.acknowledge(style,scene,sourceId,dataId) ?: return null
        try {
            if (layers.any { style.setStyleLayerProperty(it,"visibility",Value("visible")).isError })
                return fail(style,MapOverlayFailure.LAYER)
        } catch (_: Exception) { return fail(style,MapOverlayFailure.LAYER) }
        // Date timestamps in the native frame interval share this wall-clock domain.
        sourceAck.revealed(ticket,System.currentTimeMillis())
        return lastStatus
    }

    fun onRenderFrameFinished(style: Style, scene: MapOverlayScene, startedAtMillis: Long, full: Boolean): MapOverlayStatus? {
        sourceAck.fullFrame(style,scene,startedAtMillis,full) ?: return null
        appliedScene = scene
        return requireNotNull(acknowledgedResult).also { lastStatus = it }
    }

    fun awaitingRenderedFrame(style: Style, scene: MapOverlayScene): Boolean = sourceAck.awaitingFrame(style,scene)

    fun onSourceError(style: Style, scene: MapOverlayScene): MapOverlayStatus? =
        if (installedStyle === style && attemptedScene === scene) fail(style,MapOverlayFailure.SOURCE) else null

    private fun hideLayers(style: Style): Boolean = layers.all { id ->
        runCatching { !style.setStyleLayerProperty(id,"visibility",Value("none")).isError }.getOrDefault(false)
    }

    private fun addLayer(
        style: Style, suffix: String, type: String, paint: HashMap<String, Value>,
        layout: HashMap<String, Value> = hashMapOf(), filter: Value,
    ) {
        val id = "gs-overlay-$suffix"
        val properties = hashMapOf("id" to Value(id), "type" to Value(type), "source" to Value(SOURCE_ID),
            "paint" to Value(paint), "layout" to Value(HashMap(layout).apply { put("visibility",Value("none")) }), "filter" to filter)
        check(!style.addStyleLayer(Value(properties), null).isError)
        layers += id
    }

    private fun fail(style: Style, reason: MapOverlayFailure): MapOverlayStatus {
        sourceAck.invalidate(); acknowledgedResult = null
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
        const val SOURCE_ID = MAP_OVERLAY_SOURCE_ID
        const val MAX_ACTIVE_LABELS = 256
        const val MAX_UNUSED_LABELS = 64
        const val MAX_UNUSED_BYTES = 1024L * 1024
    }

}

private fun get(property: String): Value = expression("get", Value(property))
private fun eq(left: Value, right: Value): Value = expression("==", left, right)
private fun expression(operator: String, vararg args: Value): Value = Value(listOf(Value(operator)) + args)

internal data class PointLabelPlacement(val anchor: String, val offsetY: Double)

/** Visual separation only. Invisible anchors/CIRCLE keep their centre; lines/areas use centre in the native expression. */
internal fun pointLabelPlacement(style: MapOverlayStyle, selected: Boolean, hasPinSprite: Boolean, density: Float = 1f): PointLabelPlacement {
    require(density.isFinite() && density > 0f)
    if (style.labelBadge != MapOverlayLabelBadge.PILL || style.pointRadius <= 0.0) return PointLabelPlacement("center",0.0)
    if (style.pointLabelSide == MapOverlayPointLabelSide.BELOW) {
        // A pin ends at its anchored tip; a circle extends below its coordinate.
        val bottom = if (hasPinSprite) 0.0 else style.pointRadius +
            (if (selected) 2.0 else 0.0) + (style.strokeWidth.takeIf { it > 0.0 } ?: 2.0)
        return PointLabelPlacement("top",bottom+3.0)
    }
    val top = if (hasPinSprite) {
        val physicalHeight = pinDimensions(style,density).second
        physicalHeight.toDouble()/density * if (selected) 1.2 else 1.0
    } else style.pointRadius + (if (selected) 2.0 else 0.0) + (style.strokeWidth.takeIf { it > 0.0 } ?: 2.0)
    return PointLabelPlacement("bottom",-(top+3.0))
}

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
        if (style.pointSymbol == MapOverlayPointSymbol.PIN_CHECKED) {
            // The approved arrival pin uses a check, without changing the geographic tip/anchor.
            val check = Path().apply {
                moveTo(cx - radius * (10f / 22f), cy + radius * (1f / 22f))
                lineTo(cx - radius * (3f / 22f), cy + radius * (8f / 22f))
                lineTo(cx + radius * (11f / 22f), cy - radius * (6f / 22f))
            }
            canvas.drawPath(check, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                this.style = Paint.Style.STROKE
                strokeWidth = radius * (5f / 22f)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            })
        } else {
            canvas.drawCircle(cx, cy, radius * 0.35f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        }
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
    val horizontal: Float, val vertical: Float, val ascent: Float, val descent: Float, val cornerRadius: Float, val paint: Paint,
    val badge: MapOverlayLabelBadge, val outlineColor: Int, val outlineWidth: Float)

/** Compute real density-scaled pixel dimensions before admitting any allocation. */
private fun labelLayout(text: String, style: MapOverlayStyle, density: Float, fontScale: Float): LabelLayout {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(style.labelColorHex)
        textSize = (style.labelSize * fontScale * density).toFloat()
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
    val dimensions = overlayLabelBadgePixels(paint.measureText(display),metrics.descent-metrics.ascent,density,style.labelBadge)
    return LabelLayout(display,dimensions.width,dimensions.height,horizontal,vertical,metrics.ascent,metrics.descent,6*density,paint,
        style.labelBadge,Color.parseColor(style.colorHex),dimensions.outlineWidth)
}

/** Only already-admitted rasters allocate; full text remains in the host object. */
private fun labelBitmap(layout: LabelLayout): Bitmap = with(layout) {
    Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).also { bitmap ->
        Canvas(bitmap).apply {
            if (badge == MapOverlayLabelBadge.CIRCLE) {
                val cx = width / 2f; val cy = height / 2f
                val radius = (minOf(width,height)-outlineWidth-1f).coerceAtLeast(0f)/2f
                drawCircle(cx,cy,radius,Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
                drawCircle(cx,cy,radius,Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = outlineColor; style = Paint.Style.STROKE; strokeWidth = outlineWidth
                })
                drawText(display,cx-paint.measureText(display)/2f,cy-(ascent+descent)/2f,paint)
            } else {
                drawRoundRect(0f,0f,width.toFloat(),height.toFloat(),cornerRadius,cornerRadius,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(240,255,255,255) })
                drawText(display,horizontal,vertical-ascent,paint)
            }
        }
    }
}
