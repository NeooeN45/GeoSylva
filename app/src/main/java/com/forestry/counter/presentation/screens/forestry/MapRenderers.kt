package com.forestry.counter.presentation.screens.forestry

import android.util.Log
import com.forestry.counter.BuildConfig
import com.forestry.counter.domain.geo.ShapefileOverlay
import com.forestry.counter.domain.location.GpsParcelTracer
import com.forestry.counter.domain.model.Essence
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.presentation.theme.SemanticSuccess
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.style.expressions.Expression.*
import com.mapbox.mapboxsdk.style.layers.CircleLayer
import com.mapbox.mapboxsdk.style.layers.FillLayer
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.layers.RasterLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonOptions
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.mapbox.mapboxsdk.style.sources.RasterSource
import com.mapbox.mapboxsdk.style.sources.TileSet
import com.mapbox.mapboxsdk.maps.Style
import java.util.Locale
import kotlin.math.roundToInt

private const val TAG_R = "MapRenderers"

// ── IDs des sources/layers ──────────────────────────────────────────────────
internal const val SHP_SOURCE_ID = "shp-parcelles"
internal const val SHP_FILL_ID = "shp-fill"
internal const val SHP_LINE_ID = "shp-line"
internal const val SHP_LABEL_ID = "shp-labels"
internal const val TIGE_SOURCE_ID = "tige-source"
internal const val TIGE_CLUSTER_LAYER_ID = "tige-clusters"
internal const val TIGE_CLUSTER_COUNT_LAYER_ID = "tige-cluster-count"
internal const val TIGE_POINT_LAYER_ID = "tige-points"
internal const val TIGE_SPECIAL_LAYER_ID = "tige-special"
internal const val TIGE_PRECISION_LAYER_ID = "tige-precision"
internal const val TRACE_SOURCE_ID = "trace-source"
internal const val TRACE_FILL_ID = "trace-fill"
internal const val TRACE_LINE_ID = "trace-line"
internal const val TRACE_POINTS_SOURCE_ID = "trace-points-source"
internal const val TRACE_POINTS_LAYER_ID = "trace-points"
internal const val MEAS_LINE_SRC = "meas-line-src"
internal const val MEAS_PTS_SRC = "meas-pts-src"
internal const val MEAS_LINE_LYR = "meas-line-lyr"
internal const val MEAS_PTS_LYR = "meas-pts-lyr"
internal const val ACTIVE_BASE_ID = "active_base"
private const val ACTIVE_OVERLAY_PREFIX = "active_overlay"
private const val ACTIVE_MAX_OVERLAYS = 2

// ── Changement de fond de carte incrémental ─────────────────────────────────

/**
 * Remplace le fond de carte actif en ajoutant/retirant uniquement sa
 * source et sa couche raster (ids stables "active_base"/"active_overlayN"),
 * sans jamais appeler `map.setStyle()` — contrairement à un rechargement
 * complet du style, qui recrée toute la carte de façon asynchrone et peut,
 * si un second changement de calque est demandé pendant qu'un premier est
 * encore en cours de chargement, faire cohabiter des tuiles des deux styles
 * dans le rendu final (observé : quadrants entiers de calques différents
 * mélangés à l'écran). C'est l'approche des clients SIG établis (QGIS,
 * QField, Géoportail) : un seul document cartographique, dont on ajoute ou
 * retire des couches, plutôt qu'un rechargement systématique de tout.
 *
 * Sans effet sur les couches tiges/mesure/tracé, qui gardent leurs propres
 * ids stables et ne sont jamais touchées ici.
 */
internal fun swapRasterLayer(
    style: Style,
    baseTileUrl: String,
    overlayTileUrls: List<String>,
    attribution: String,
    maxZoom: Int,
    tileSize: Int = 256,
) {
    for (i in 0 until ACTIVE_MAX_OVERLAYS) {
        val overlayId = "$ACTIVE_OVERLAY_PREFIX$i"
        try { style.removeLayer(overlayId) } catch (e: Throwable) { Log.w(TAG_R, "removeLayer $overlayId", e) }
        try { style.removeSource(overlayId) } catch (e: Throwable) { Log.w(TAG_R, "removeSource $overlayId", e) }
    }
    try { style.removeLayer(ACTIVE_BASE_ID) } catch (e: Throwable) { Log.w(TAG_R, "removeLayer active_base", e) }
    try { style.removeSource(ACTIVE_BASE_ID) } catch (e: Throwable) { Log.w(TAG_R, "removeSource active_base", e) }

    if (baseTileUrl.isEmpty()) return

    val baseTileSet = TileSet("2.1.0", baseTileUrl).apply {
        this.maxZoom = maxZoom.toFloat()
        if (attribution.isNotEmpty()) this.attribution = attribution
    }
    style.addSource(RasterSource(ACTIVE_BASE_ID, baseTileSet, tileSize))
    // Juste au-dessus du fond beige de repli, donc sous tiges/mesure/tracé
    // (ajoutées séparément, toujours au sommet de la pile de couches).
    // rasterFadeDuration(0) : évite le fade-in depuis le noir natif de MapLibre
    // qui rendait les dalles sombres pendant leur chargement.
    style.addLayerAbove(
        RasterLayer(ACTIVE_BASE_ID, ACTIVE_BASE_ID)
            .withProperties(PropertyFactory.rasterFadeDuration(0f)),
        "background",
    )

    var lastId = ACTIVE_BASE_ID
    overlayTileUrls.take(ACTIVE_MAX_OVERLAYS).forEachIndexed { i, url ->
        val overlayId = "$ACTIVE_OVERLAY_PREFIX$i"
        val overlayTileSet = TileSet("2.1.0", url).apply {
            this.maxZoom = maxZoom.toFloat()
            if (attribution.isNotEmpty()) this.attribution = attribution
        }
        style.addSource(RasterSource(overlayId, overlayTileSet, 256))
        style.addLayerAbove(
            RasterLayer(overlayId, overlayId).withProperties(
                PropertyFactory.rasterOpacity(0.7f),
                PropertyFactory.rasterFadeDuration(0f),
            ),
            lastId,
        )
        lastId = overlayId
    }
}

// ── Shapefile overlay ───────────────────────────────────────────────────────

internal fun applyShapefileOverlay(
    style: Style,
    geoJsonFile: java.io.File,
    overlay: ShapefileOverlay
): String {
    try { style.removeLayer(SHP_LABEL_ID) } catch (e: Throwable) { Log.w(TAG_R, "removeLayer SHP_LABEL_ID", e) }
    try { style.removeLayer(SHP_LINE_ID) } catch (e: Throwable) { Log.w(TAG_R, "removeLayer SHP_LINE_ID", e) }
    try { style.removeLayer(SHP_FILL_ID) } catch (e: Throwable) { Log.w(TAG_R, "removeLayer SHP_FILL_ID", e) }
    try { style.removeSource(SHP_SOURCE_ID) } catch (e: Throwable) { Log.w(TAG_R, "removeSource SHP_SOURCE_ID", e) }

    if (!overlay.visible) return "hidden"

    if (!geoJsonFile.exists()) {
        if (BuildConfig.DEBUG) Log.e(TAG_R, "SHP: file not found: ${geoJsonFile.absolutePath}")
        return "ERR: file not found"
    }
    val geoJson: String = try {
        geoJsonFile.readText(Charsets.UTF_8)
    } catch (e: Throwable) {
        if (BuildConfig.DEBUG) Log.e(TAG_R, "SHP: read failed", e)
        return "ERR: read failed: ${e.message}"
    }
    if (geoJson.length < 10) return "ERR: file too short (${geoJson.length})"

    try { org.json.JSONObject(geoJson) }
    catch (e: Throwable) { return "ERR: invalid JSON: ${e.message?.take(80)}" }

    try {
        val source = GeoJsonSource(SHP_SOURCE_ID)
        style.addSource(source)

        val fillRgb = overlay.fillColor or 0xFF000000.toInt()
        style.addLayer(
            FillLayer(SHP_FILL_ID, SHP_SOURCE_ID).withProperties(
                PropertyFactory.fillColor(fillRgb),
                PropertyFactory.fillOpacity(overlay.fillOpacity)
            )
        )

        val borderRgb = overlay.borderColor or 0xFF000000.toInt()
        style.addLayer(
            LineLayer(SHP_LINE_ID, SHP_SOURCE_ID).withProperties(
                PropertyFactory.lineColor(borderRgb),
                PropertyFactory.lineWidth(overlay.borderWidth),
                PropertyFactory.lineOpacity(overlay.borderOpacity)
            )
        )

        source.setGeoJson(geoJson)
    } catch (e: Throwable) {
        if (BuildConfig.DEBUG) Log.e(TAG_R, "SHP: apply failed", e)
        return "ERR: apply failed: ${e.message?.take(80)}"
    }

    var labelStatus = ""
    if (overlay.labelFields.isNotEmpty()) {
        try {
            try { style.removeLayer(SHP_LABEL_ID) } catch (e: Throwable) { Log.w(TAG_R, "removeLayer SHP_LABEL_ID", e) }
            val textExpr = if (overlay.combineLabels) {
                overlay.labelFields.joinToString(" · ") { "{${it.key}}" }
            } else {
                overlay.labelFields.joinToString("\n") { "{${it.key}}" }
            }
            style.addLayer(
                SymbolLayer(SHP_LABEL_ID, SHP_SOURCE_ID).withProperties(
                    PropertyFactory.textField(textExpr),
                    PropertyFactory.textSize(overlay.labelSize),
                    PropertyFactory.textColor(android.graphics.Color.BLACK),
                    PropertyFactory.textHaloColor(android.graphics.Color.WHITE),
                    PropertyFactory.textHaloWidth(1.5f),
                    PropertyFactory.textAllowOverlap(false),
                    PropertyFactory.textIgnorePlacement(false),
                    PropertyFactory.textMaxWidth(10f)
                )
            )
            labelStatus = " +labels"
        } catch (e: Throwable) {
            labelStatus = " labels ERR: ${e.message?.take(50)}"
        }
    }

    return "OK: ${overlay.featureCount}f$labelStatus"
}

// ── Tiges (arbres) ──────────────────────────────────────────────────────────

internal fun removeTigeLayers(style: Style) {
    try { style.removeLayer(TIGE_PRECISION_LAYER_ID) } catch (e: Throwable) { Log.w(TAG_R, "removeLayer TIGE_PRECISION", e) }
    try { style.removeLayer(TIGE_SPECIAL_LAYER_ID) } catch (e: Throwable) { Log.w(TAG_R, "removeLayer TIGE_SPECIAL", e) }
    try { style.removeLayer(TIGE_CLUSTER_COUNT_LAYER_ID) } catch (e: Throwable) { Log.w(TAG_R, "removeLayer TIGE_CLUSTER_COUNT", e) }
    try { style.removeLayer(TIGE_CLUSTER_LAYER_ID) } catch (e: Throwable) { Log.w(TAG_R, "removeLayer TIGE_CLUSTER", e) }
    try { style.removeLayer(TIGE_POINT_LAYER_ID) } catch (e: Throwable) { Log.w(TAG_R, "removeLayer TIGE_POINT", e) }
    try { style.removeSource(TIGE_SOURCE_ID) } catch (e: Throwable) { Log.w(TAG_R, "removeSource TIGE_SOURCE", e) }
}

private fun jsonEscape(value: String): String = value
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
    .replace("\r", "\\r")
    .replace("\t", "\\t")

internal fun buildTigesGeoJson(
    geoTiges: List<Triple<Tige, Double, Double>>,
    essenceMap: Map<String, Essence>,
    essenceColors: Map<String, Int>
): String {
    val sb = StringBuilder(geoTiges.size * 256)
    sb.append("{\"type\":\"FeatureCollection\",\"features\":[")

    geoTiges.forEachIndexed { index, (t, lon, lat) ->
        if (index > 0) sb.append(',')
        val code = t.essenceCode.uppercase()
        val name = essenceMap[code]?.name ?: t.essenceCode
        val colorInt = essenceColors[code] ?: ESSENCE_COLOR_PALETTE[0]
        val colorHex = String.format(Locale.US, "#%06X", (0xFFFFFF and colorInt))
        val label = buildString {
            append("⌀ ")
            append(t.diamCm.roundToInt())
            append(" cm")
            t.hauteurM?.let {
                append(" · H ")
                append(it.roundToInt())
                append(" m")
            }
            t.precisionM?.let {
                append(" · ±")
                append(String.format(Locale.US, "%.1f", it))
                append(" m")
            }
        }

        sb.append("{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[")
        sb.append(String.format(Locale.US, "%.7f", lon))
        sb.append(',')
        sb.append(String.format(Locale.US, "%.7f", lat))
        sb.append("]},\"properties\":{")
        sb.append("\"essence\":\"").append(jsonEscape(code)).append("\",")
        sb.append("\"essence_name\":\"").append(jsonEscape(name)).append("\",")
        sb.append("\"diam\":").append(String.format(Locale.US, "%.2f", t.diamCm)).append(',')
        sb.append("\"height\":").append(t.hauteurM?.let { String.format(Locale.US, "%.2f", it) } ?: "null").append(',')
        sb.append("\"precision\":").append(t.precisionM?.let { String.format(Locale.US, "%.2f", it) } ?: "null").append(',')
        val cat = t.categorie?.uppercase() ?: ""
        val specialColor = when (cat) {
            "DEPERISSANT" -> "#FF9800"
            "ARBRE_BIO" -> "#4CAF50"
            "MORT" -> "#424242"
            "PARASITE" -> "#F44336"
            else -> ""
        }
        sb.append("\"color\":\"").append(if (specialColor.isNotEmpty()) specialColor else colorHex).append("\",")
        sb.append("\"categorie\":\"").append(jsonEscape(cat)).append("\",")
        sb.append("\"is_special\":").append(if (cat.isNotEmpty()) "1" else "0").append(',')
        val precisionColor = when {
            t.precisionM == null -> "#9E9E9E"
            t.precisionM <= 3.0  -> "#4CAF50"
            t.precisionM <= 6.0  -> "#8BC34A"
            t.precisionM <= 12.0 -> "#FF9800"
            else                 -> "#F44336"
        }
        sb.append("\"label\":\"").append(jsonEscape(label)).append("\",")
        sb.append("\"precision_color\":\"").append(precisionColor).append("\"")
        sb.append("}}")
    }

    sb.append("]}")
    return sb.toString()
}

internal fun renderTigesOnMap(
    style: Style,
    geoTiges: List<Triple<Tige, Double, Double>>,
    essenceMap: Map<String, Essence>,
    essenceColors: Map<String, Int>
) {
    val geoJson = buildTigesGeoJson(geoTiges, essenceMap, essenceColors)

    val existingSource = style.getSource(TIGE_SOURCE_ID) as? GeoJsonSource
    if (existingSource != null) {
        existingSource.setGeoJson(geoJson)
        return
    }

    if (geoTiges.isEmpty()) return

    val source = GeoJsonSource(
        TIGE_SOURCE_ID,
        geoJson,
        GeoJsonOptions()
            .withCluster(true)
            .withClusterRadius(50)
            .withClusterMaxZoom(13)
            .withBuffer(8)
            .withTolerance(0.5f)
    )
    style.addSource(source)

    style.addLayer(
        CircleLayer(TIGE_CLUSTER_LAYER_ID, TIGE_SOURCE_ID)
            .withFilter(has("point_count"))
            .withProperties(
                PropertyFactory.circleColor(SemanticSuccess.toArgb()),
                PropertyFactory.circleOpacity(0.88f),
                PropertyFactory.circleRadius(
                    interpolate(
                        linear(),
                        get("point_count"),
                        stop(5, 10f),
                        stop(20, 14f),
                        stop(60, 20f)
                    )
                ),
                PropertyFactory.circleStrokeColor(Color.White.toArgb()),
                PropertyFactory.circleStrokeWidth(1f)
            )
    )

    style.addLayer(
        SymbolLayer(TIGE_CLUSTER_COUNT_LAYER_ID, TIGE_SOURCE_ID)
            .withFilter(has("point_count"))
            .withProperties(
                PropertyFactory.textField("{point_count_abbreviated}"),
                PropertyFactory.textSize(12f),
                PropertyFactory.textColor(Color.White.toArgb()),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true)
            )
    )

    style.addLayer(
        CircleLayer(TIGE_POINT_LAYER_ID, TIGE_SOURCE_ID)
            .withFilter(all(not(has("point_count")), eq(get("is_special"), literal(0))))
            .withProperties(
                PropertyFactory.circleColor(get("color")),
                PropertyFactory.circleOpacity(0.95f),
                PropertyFactory.circleRadius(
                    interpolate(
                        linear(),
                        get("diam"),
                        stop(8, 3f),
                        stop(20, 4.5f),
                        stop(35, 6f),
                        stop(60, 8f)
                    )
                ),
                PropertyFactory.circleStrokeColor(Color.White.toArgb()),
                PropertyFactory.circleStrokeWidth(1f)
            )
    )

    style.addLayer(
        CircleLayer(TIGE_PRECISION_LAYER_ID, TIGE_SOURCE_ID)
            .withFilter(not(has("point_count")))
            .withProperties(
                PropertyFactory.circleColor(get("precision_color")),
                PropertyFactory.circleOpacity(0.25f),
                PropertyFactory.circleRadius(
                    interpolate(
                        linear(),
                        get("diam"),
                        stop(8, 7f),
                        stop(20, 9f),
                        stop(35, 12f),
                        stop(60, 16f)
                    )
                ),
                PropertyFactory.circleStrokeColor(get("precision_color")),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeOpacity(0.8f)
            )
    )

    style.addLayer(
        CircleLayer(TIGE_SPECIAL_LAYER_ID, TIGE_SOURCE_ID)
            .withFilter(all(not(has("point_count")), eq(get("is_special"), literal(1))))
            .withProperties(
                PropertyFactory.circleColor(get("color")),
                PropertyFactory.circleOpacity(0.95f),
                PropertyFactory.circleRadius(
                    interpolate(
                        linear(),
                        get("diam"),
                        stop(8, 4f),
                        stop(20, 5.5f),
                        stop(35, 7f),
                        stop(60, 9f)
                    )
                ),
                PropertyFactory.circleStrokeColor(Color.Black.toArgb()),
                PropertyFactory.circleStrokeWidth(2.5f)
            )
    )
}

// ── Tracé GPS ───────────────────────────────────────────────────────────────

internal fun renderTraceOnMap(style: Style, tracer: GpsParcelTracer) {
    val state = tracer.state.value
    if (state.points.isEmpty()) {
        try { style.removeLayer(TRACE_POINTS_LAYER_ID) } catch (e: Throwable) { Log.w(TAG_R, "removeLayer TRACE_POINTS", e) }
        try { style.removeLayer(TRACE_LINE_ID) } catch (e: Throwable) { Log.w(TAG_R, "removeLayer TRACE_LINE", e) }
        try { style.removeLayer(TRACE_FILL_ID) } catch (e: Throwable) { Log.w(TAG_R, "removeLayer TRACE_FILL", e) }
        try { style.removeSource(TRACE_POINTS_SOURCE_ID) } catch (e: Throwable) { Log.w(TAG_R, "removeSource TRACE_POINTS", e) }
        try { style.removeSource(TRACE_SOURCE_ID) } catch (e: Throwable) { Log.w(TAG_R, "removeSource TRACE_SOURCE", e) }
        return
    }

    val polyJson = tracer.toGeoJsonPolygon()
    val lineJson = tracer.toGeoJsonLine()
    val geom = polyJson ?: lineJson ?: return

    val existingTraceSource = style.getSource(TRACE_SOURCE_ID) as? GeoJsonSource
    if (existingTraceSource != null) {
        existingTraceSource.setGeoJson(geom)
    } else {
        style.addSource(GeoJsonSource(TRACE_SOURCE_ID, geom))
        if (polyJson != null) {
            style.addLayer(
                FillLayer(TRACE_FILL_ID, TRACE_SOURCE_ID).withProperties(
                    PropertyFactory.fillColor(android.graphics.Color.parseColor("#1B5E20")),
                    PropertyFactory.fillOpacity(0.20f)
                )
            )
        }
        style.addLayer(
            LineLayer(TRACE_LINE_ID, TRACE_SOURCE_ID).withProperties(
                PropertyFactory.lineColor(android.graphics.Color.parseColor("#2E7D32")),
                PropertyFactory.lineWidth(3f),
                PropertyFactory.lineOpacity(0.9f)
            )
        )
    }

    val pointsJson = tracer.toGeoJsonPoints()
    val existingPointsSource = style.getSource(TRACE_POINTS_SOURCE_ID) as? GeoJsonSource
    if (existingPointsSource != null) {
        existingPointsSource.setGeoJson(pointsJson)
    } else {
        style.addSource(GeoJsonSource(TRACE_POINTS_SOURCE_ID, pointsJson))
        style.addLayer(
            CircleLayer(TRACE_POINTS_LAYER_ID, TRACE_POINTS_SOURCE_ID).withProperties(
                PropertyFactory.circleColor(android.graphics.Color.parseColor("#4CAF50")),
                PropertyFactory.circleRadius(5f),
                PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleOpacity(0.95f)
            )
        )
    }
}

// ── Outil de mesure (rendu carte) ───────────────────────────────────────────

internal fun removeMeasLayers(style: Style) {
    try { style.removeLayer(MEAS_PTS_LYR) } catch (e: Throwable) { Log.w(TAG_R, "removeLayer MEAS_PTS", e) }
    try { style.removeLayer(MEAS_LINE_LYR) } catch (e: Throwable) { Log.w(TAG_R, "removeLayer MEAS_LINE", e) }
    try { style.removeSource(MEAS_PTS_SRC) } catch (e: Throwable) { Log.w(TAG_R, "removeSource MEAS_PTS", e) }
    try { style.removeSource(MEAS_LINE_SRC) } catch (e: Throwable) { Log.w(TAG_R, "removeSource MEAS_LINE", e) }
}

internal fun renderMeasureOnMap(style: Style, pts: List<LatLng>, mode: MeasureMode, lineColor: Int = android.graphics.Color.parseColor("#FF6F00")) {
    if (pts.isEmpty()) {
        removeMeasLayers(style)
        return
    }

    if (pts.size >= 2) {
        val lineJson = measLineGeoJson(pts, mode == MeasureMode.AREA)
        val existingLineSrc = style.getSource(MEAS_LINE_SRC) as? GeoJsonSource
        if (existingLineSrc != null) {
            existingLineSrc.setGeoJson(lineJson)
        } else {
            style.addSource(GeoJsonSource(MEAS_LINE_SRC, lineJson))
            style.addLayer(
                LineLayer(MEAS_LINE_LYR, MEAS_LINE_SRC).withProperties(
                    PropertyFactory.lineColor(lineColor),
                    PropertyFactory.lineWidth(2.5f),
                    PropertyFactory.lineOpacity(0.9f)
                )
            )
        }
    }

    val ptsJson = measPtsGeoJson(pts)
    val existingPtsSrc = style.getSource(MEAS_PTS_SRC) as? GeoJsonSource
    if (existingPtsSrc != null) {
        existingPtsSrc.setGeoJson(ptsJson)
    } else {
        style.addSource(GeoJsonSource(MEAS_PTS_SRC, ptsJson))
        style.addLayer(
            CircleLayer(MEAS_PTS_LYR, MEAS_PTS_SRC).withProperties(
                PropertyFactory.circleColor(lineColor),
                PropertyFactory.circleRadius(6f),
                PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleOpacity(0.95f)
            )
        )
    }
}

// ── Helpers GeoJSON pour mesures ────────────────────────────────────────────

internal fun measLineGeoJson(pts: List<LatLng>, closed: Boolean): String {
    if (pts.size < 2) return """{"type":"FeatureCollection","features":[]}"""
    val raw = if (closed && pts.size >= 3) pts + pts[0] else pts
    val coords = raw.joinToString(",") {
        "[${String.format(Locale.US, "%.7f", it.longitude)},${String.format(Locale.US, "%.7f", it.latitude)}]"
    }
    return """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coords]},"properties":{}}]}"""
}

internal fun measPtsGeoJson(pts: List<LatLng>): String {
    if (pts.isEmpty()) return """{"type":"FeatureCollection","features":[]}"""
    val feats = pts.joinToString(",") { p ->
        "[${String.format(Locale.US, "%.7f", p.longitude)},${String.format(Locale.US, "%.7f", p.latitude)}]".let { c ->
            """{"type":"Feature","geometry":{"type":"Point","coordinates":$c},"properties":{}}"""
        }
    }
    return """{"type":"FeatureCollection","features":[$feats]}"""
}

// ── Info arbre au tap ───────────────────────────────────────────────────────

data class TappedTreeInfo(
    val essenceName: String,
    val essenceCode: String,
    val diamCm: Double?,
    val hauteurM: Double?,
    val precisionM: Double?,
    val categorie: String?,
    val lat: Double,
    val lon: Double
)

internal fun attachTigeTapInfo(
    map: com.mapbox.mapboxsdk.maps.MapboxMap,
    context: android.content.Context,
    onTreeTapped: (TappedTreeInfo) -> Unit
) {
    map.addOnMapClickListener { latLng ->
        try {
            val point = map.projection.toScreenLocation(latLng)
            val features = map.queryRenderedFeatures(point, TIGE_POINT_LAYER_ID, TIGE_SPECIAL_LAYER_ID)
            val f = features.firstOrNull() ?: return@addOnMapClickListener false

            val props = f.properties() ?: return@addOnMapClickListener false
            val geom = f.geometry()

            val essenceName = try {
                val v = props.get("essence_name")
                if (v != null && !v.isJsonNull) v.asString
                else {
                    val v2 = props.get("essence")
                    if (v2 != null && !v2.isJsonNull) v2.asString else "?"
                }
            } catch (_: Throwable) { "?" }

            val essenceCode = try {
                val v = props.get("essence")
                if (v != null && !v.isJsonNull) v.asString else "?"
            } catch (_: Throwable) { "?" }

            val diam = try {
                val v = props.get("diam")
                if (v != null && !v.isJsonNull) v.asDouble else null
            } catch (_: Throwable) { null }

            val h = try {
                val v = props.get("height")
                if (v != null && !v.isJsonNull) v.asDouble else null
            } catch (_: Throwable) { null }

            val precision = try {
                val v = props.get("precision")
                if (v != null && !v.isJsonNull) v.asDouble else null
            } catch (_: Throwable) { null }

            val categorie = try {
                val v = props.get("categorie")
                if (v != null && !v.isJsonNull) v.asString.takeIf { it.isNotEmpty() } else null
            } catch (_: Throwable) { null }

            val treeLat: Double
            val treeLon: Double
            if (geom != null && geom is com.mapbox.geojson.Point) {
                treeLon = geom.longitude()
                treeLat = geom.latitude()
            } else {
                treeLon = latLng.longitude
                treeLat = latLng.latitude
            }

            onTreeTapped(
                TappedTreeInfo(
                    essenceName = essenceName,
                    essenceCode = essenceCode,
                    diamCm = diam,
                    hauteurM = h,
                    precisionM = precision,
                    categorie = categorie,
                    lat = treeLat,
                    lon = treeLon
                )
            )
            true
        } catch (e: Throwable) {
            Log.e(TAG_R, "Tige tap handler error", e)
            false
        }
    }
}

// ── Location component ──────────────────────────────────────────────────────

internal fun enableLocationComponent(map: com.mapbox.mapboxsdk.maps.MapboxMap, style: Style, context: android.content.Context) {
    try {
        val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return

        val locationComponent = map.locationComponent
        val baseColor = com.forestry.counter.presentation.theme.GpsBon.toArgb()
        val locationOptions = com.mapbox.mapboxsdk.location.LocationComponentOptions.builder(context)
            .accuracyColor(baseColor)
            .accuracyAlpha(0f)
            .pulseEnabled(true)
            .pulseColor(baseColor)
            .build()
        // Sans requête explicite, le moteur de localisation par défaut peut
        // retenir un intervalle/priorité économe en énergie (mise à jour
        // toutes les quelques secondes) — haute précision + 1s/500ms demandés
        // explicitement pour un puck qui suit vraiment la position terrain.
        val locationEngineRequest = com.mapbox.mapboxsdk.location.engine.LocationEngineRequest
            .Builder(1000L)
            .setFastestInterval(500L)
            .setPriority(com.mapbox.mapboxsdk.location.engine.LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
            .build()
        val activationOptions = com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
            .builder(context, style)
            .locationComponentOptions(locationOptions)
            .useDefaultLocationEngine(true)
            .locationEngineRequest(locationEngineRequest)
            .build()
        locationComponent.activateLocationComponent(activationOptions)
        locationComponent.isLocationComponentEnabled = true
        locationComponent.cameraMode = com.mapbox.mapboxsdk.location.modes.CameraMode.NONE
        locationComponent.renderMode = com.mapbox.mapboxsdk.location.modes.RenderMode.COMPASS
    } catch (e: Throwable) {
        Log.w(TAG_R, "Could not enable location component", e)
    }
}
