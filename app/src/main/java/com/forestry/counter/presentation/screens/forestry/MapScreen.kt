package com.forestry.counter.presentation.screens.forestry

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.forestry.counter.R
import com.forestry.counter.data.preferences.UserPreferencesManager
import com.forestry.counter.domain.geo.LabelField
import com.forestry.counter.domain.geo.ShapefileOverlay
import com.forestry.counter.domain.geo.ShapefileOverlayManager
import com.forestry.counter.domain.location.GpsParcelTracer
import com.forestry.counter.domain.location.TreeNavigator
import com.forestry.counter.domain.location.WktUtils
import com.forestry.counter.domain.model.Essence
import com.forestry.counter.domain.model.Parcelle
import com.forestry.counter.domain.model.Tige
import com.forestry.counter.domain.repository.IbpRepository
import com.forestry.counter.domain.repository.RipisylveRepository
import com.forestry.counter.domain.repository.StationRepository
import com.forestry.counter.domain.repository.ParcelleRepository
import com.forestry.counter.domain.repository.TigeRepository
import com.forestry.counter.domain.repository.EssenceRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.forestry.counter.domain.location.OfflineTileManager
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.expressions.Expression.*
import com.mapbox.mapboxsdk.style.layers.CircleLayer
import com.mapbox.mapboxsdk.style.layers.FillLayer
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonOptions
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import kotlin.math.*
import java.io.File
import java.util.Locale

private const val TAG = "MapScreen"

// Palette de couleurs pour les pickers de couleur (fill / border)
private val SHP_COLOR_PALETTE = listOf(
    0xFF2E7D32.toInt(), // vert forêt
    0xFF1B5E20.toInt(), // vert foncé
    0xFF4CAF50.toInt(), // vert clair
    0xFF81C784.toInt(), // vert pastel
    0xFF1565C0.toInt(), // bleu
    0xFF42A5F5.toInt(), // bleu clair
    0xFFEF6C00.toInt(), // orange
    0xFFF44336.toInt(), // rouge
    0xFF9C27B0.toInt(), // violet
    0xFF795548.toInt(), // marron
    0xFF607D8B.toInt(), // gris-bleu
    0xFF000000.toInt(), // noir
)

private const val SHP_SOURCE_ID = "shp-parcelles"
private const val SHP_FILL_ID = "shp-fill"
private const val SHP_LINE_ID = "shp-line"
private const val SHP_LABEL_ID = "shp-labels"
private const val TIGE_SOURCE_ID = "tiges-source"
private const val DIAGNOSTICS_SOURCE_ID = "diagnostics-source"
private const val DIAGNOSTICS_LAYER_ID = "diagnostics-layer"
private const val DIAGNOSTICS_LABEL_LAYER_ID = "diagnostics-label-layer"
private const val TIGE_CLUSTER_LAYER_ID = "tige-clusters"
private const val TIGE_CLUSTER_COUNT_LAYER_ID = "tige-cluster-count"
private const val TIGE_POINT_LAYER_ID = "tige-points"
private const val TIGE_SPECIAL_LAYER_ID = "tige-special"
private const val TIGE_PRECISION_LAYER_ID = "tige-precision"
private const val TRACE_SOURCE_ID = "trace-source"
private const val TRACE_FILL_ID = "trace-fill"
private const val TRACE_LINE_ID = "trace-line"
private const val TRACE_POINTS_SOURCE_ID = "trace-points-source"
private const val TRACE_POINTS_LAYER_ID = "trace-points"

private const val MEAS_LINE_SRC = "meas-line-src"
private const val MEAS_PTS_SRC  = "meas-pts-src"
private const val MEAS_LINE_LYR = "meas-line-lyr"
private const val MEAS_PTS_LYR  = "meas-pts-lyr"

private enum class MeasureMode { DISTANCE, AREA }
private enum class MeasureDistUnit { M, KM }
private enum class MeasureAreaUnit { M2, ARES, HA }
private val MEASURE_COLORS = listOf(
    androidx.compose.ui.graphics.Color(0xFFFF6F00),
    androidx.compose.ui.graphics.Color(0xFF2196F3),
    androidx.compose.ui.graphics.Color(0xFF4CAF50),
    androidx.compose.ui.graphics.Color(0xFFE91E63),
    androidx.compose.ui.graphics.Color(0xFF9C27B0),
    androidx.compose.ui.graphics.Color(0xFFF44336),
    androidx.compose.ui.graphics.Color(0xFFFFEB3B),
    androidx.compose.ui.graphics.Color(0xFF00BCD4)
)

private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6_371_000.0
    val φ1 = Math.toRadians(lat1); val φ2 = Math.toRadians(lat2)
    val Δφ = Math.toRadians(lat2 - lat1); val Δλ = Math.toRadians(lon2 - lon1)
    val a = sin(Δφ / 2).pow(2) + cos(φ1) * cos(φ2) * sin(Δλ / 2).pow(2)
    return R * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
}

private fun measurePolylineM(pts: List<LatLng>): Double =
    if (pts.size < 2) 0.0
    else pts.zipWithNext().sumOf { (a, b) -> haversineM(a.latitude, a.longitude, b.latitude, b.longitude) }

private fun measureAreaM2(pts: List<LatLng>): Double {
    if (pts.size < 3) return 0.0
    val R = 6_371_000.0
    val lat0 = pts[0].latitude; val lon0 = pts[0].longitude
    val cLat = cos(Math.toRadians(lat0))
    val xy = pts.map { p ->
        Pair(
            Math.toRadians(p.longitude - lon0) * R * cLat,
            Math.toRadians(p.latitude - lat0) * R
        )
    }
    var s = 0.0
    xy.indices.forEach { i ->
        val j = (i + 1) % xy.size
        s += xy[i].first * xy[j].second - xy[j].first * xy[i].second
    }
    return abs(s) / 2.0
}

private fun measLineGeoJson(pts: List<LatLng>, closed: Boolean): String {
    if (pts.size < 2) return """{"type":"FeatureCollection","features":[]}"""
    val raw = if (closed && pts.size >= 3) pts + pts[0] else pts
    val coords = raw.joinToString(",") {
        "[${String.format(Locale.US, "%.7f", it.longitude)},${String.format(Locale.US, "%.7f", it.latitude)}]"
    }
    return """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coords]},"properties":{}}]}"""
}

private fun measPtsGeoJson(pts: List<LatLng>): String {
    if (pts.isEmpty()) return """{"type":"FeatureCollection","features":[]}"""
    val feats = pts.joinToString(",") { p ->
        "[${String.format(Locale.US, "%.7f", p.longitude)},${String.format(Locale.US, "%.7f", p.latitude)}]".let { c ->
            """{"type":"Feature","geometry":{"type":"Point","coordinates":$c},"properties":{}}"""
        }
    }
    return """{"type":"FeatureCollection","features":[$feats]}"""
}

private fun removeMeasLayers(style: Style) {
    try { style.removeLayer(MEAS_PTS_LYR)  } catch (_: Throwable) {}
    try { style.removeLayer(MEAS_LINE_LYR) } catch (_: Throwable) {}
    try { style.removeSource(MEAS_PTS_SRC)  } catch (_: Throwable) {}
    try { style.removeSource(MEAS_LINE_SRC) } catch (_: Throwable) {}
}

private fun renderMeasureOnMap(style: Style, pts: List<LatLng>, mode: MeasureMode, lineColor: Int = android.graphics.Color.parseColor("#FF6F00")) {
    removeMeasLayers(style)
    if (pts.isEmpty()) return
    if (pts.size >= 2) {
        style.addSource(GeoJsonSource(MEAS_LINE_SRC, measLineGeoJson(pts, mode == MeasureMode.AREA)))
        style.addLayer(
            LineLayer(MEAS_LINE_LYR, MEAS_LINE_SRC).withProperties(
                PropertyFactory.lineColor(lineColor),
                PropertyFactory.lineWidth(2.5f),
                PropertyFactory.lineOpacity(0.9f)
            )
        )
    }
    style.addSource(GeoJsonSource(MEAS_PTS_SRC, measPtsGeoJson(pts)))
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

/**
 * Applique (ou met à jour) un overlay shapefile GeoJSON sur le style MapLibre courant.
 */
private fun applyShapefileOverlay(
    style: Style,
    geoJsonFile: java.io.File,
    overlay: ShapefileOverlay
): String {
    // 1. Supprimer les couches/source existantes
    try { style.removeLayer(SHP_LABEL_ID) } catch (_: Throwable) {}
    try { style.removeLayer(SHP_LINE_ID) } catch (_: Throwable) {}
    try { style.removeLayer(SHP_FILL_ID) } catch (_: Throwable) {}
    try { style.removeSource(SHP_SOURCE_ID) } catch (_: Throwable) {}

    if (!overlay.visible) return "hidden"

    // 2. Lire le fichier GeoJSON
    if (!geoJsonFile.exists()) {
        Log.e(TAG, "SHP: GeoJSON file not found: ${geoJsonFile.absolutePath}")
        return "ERR: file not found"
    }
    val geoJson: String
    try {
        geoJson = geoJsonFile.readText(Charsets.UTF_8)
    } catch (e: Throwable) {
        Log.e(TAG, "SHP: failed to read GeoJSON file", e)
        return "ERR: read failed: ${e.message}"
    }
    if (geoJson.length < 10) {
        Log.e(TAG, "SHP: GeoJSON too short (${geoJson.length} chars)")
        return "ERR: file too short (${geoJson.length})"
    }
    Log.d(TAG, "SHP: read ${geoJson.length} chars from ${geoJsonFile.name}")
    Log.d(TAG, "SHP: first 200 chars: ${geoJson.take(200)}")

    // 3. Valider le JSON avant de passer à MapLibre
    try {
        org.json.JSONObject(geoJson)
    } catch (e: Throwable) {
        Log.e(TAG, "SHP: INVALID JSON — ${e.message}")
        return "ERR: invalid JSON: ${e.message?.take(80)}"
    }

    // 4. Créer source, layers, puis injecter les données
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

        Log.i(TAG, "SHP: fill+line OK — ${overlay.featureCount} features")

    } catch (e: Throwable) {
        Log.e(TAG, "SHP: failed to apply fill/line", e)
        return "ERR: apply failed: ${e.message?.take(80)}"
    }

    // 5. Étiquettes (try/catch séparé pour ne pas casser les polygones)
    var labelStatus = ""
    if (overlay.labelFields.isNotEmpty()) {
        try {
            try { style.removeLayer(SHP_LABEL_ID) } catch (_: Throwable) {}
            val textExpr = if (overlay.combineLabels) {
                overlay.labelFields.joinToString(" · ") { "{${it.key}}" }
            } else {
                overlay.labelFields.joinToString("\n") { "{${it.key}}" }
            }
            Log.d(TAG, "SHP: adding labels with expr='$textExpr'")
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
            Log.i(TAG, "SHP: labels OK")
        } catch (e: Throwable) {
            Log.e(TAG, "SHP: labels FAILED", e)
            labelStatus = " labels ERR: ${e.message?.take(50)}"
        }
    }

    return "OK: ${overlay.featureCount}f$labelStatus"
}

private fun removeTigeLayers(style: Style) {
    try { style.removeLayer(TIGE_PRECISION_LAYER_ID) } catch (_: Throwable) {}
    try { style.removeLayer(TIGE_SPECIAL_LAYER_ID) } catch (_: Throwable) {}
    try { style.removeLayer(TIGE_CLUSTER_COUNT_LAYER_ID) } catch (_: Throwable) {}
    try { style.removeLayer(TIGE_CLUSTER_LAYER_ID) } catch (_: Throwable) {}
    try { style.removeLayer(TIGE_POINT_LAYER_ID) } catch (_: Throwable) {}
    try { style.removeSource(TIGE_SOURCE_ID) } catch (_: Throwable) {}
}

private fun jsonEscape(value: String): String = value
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
    .replace("\r", "\\r")
    .replace("\t", "\\t")

private fun buildTigesGeoJson(
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
        // Couleur du cercle de précision selon la qualité GPS
        val precisionColor = when {
            t.precisionM == null -> "#9E9E9E"   // gris = pas de GPS
            t.precisionM <= 3.0  -> "#4CAF50"   // vert = excellent
            t.precisionM <= 6.0  -> "#8BC34A"   // vert clair = bon
            t.precisionM <= 12.0 -> "#FF9800"   // orange = modéré
            else                 -> "#F44336"   // rouge = mauvais
        }
        sb.append("\"precision_m\":").append(String.format(Locale.US, "%.1f", t.precisionM ?: 0.0)).append(',')
        sb.append("\"precision_color\":\"").append(jsonEscape(precisionColor)).append("\"")
        sb.append("}}")
    }

    sb.append("]}")
    return sb.toString()
}

private fun buildDiagnosticsGeoJson(
    ibp: List<com.forestry.counter.domain.model.IbpEvaluation>,
    ripisylve: List<com.forestry.counter.domain.model.ripisylve.RipisylveObservation>,
    station: List<com.forestry.counter.domain.model.station.StationObservation>
): String {
    val features = mutableListOf<String>()
    
    ibp.filter { it.latitude != null && it.longitude != null }.forEach { eval ->
        val type = "IBP"
        val score = eval.scoreTotal
        val level = if (score != null) {
            when {
                score >= 80 -> "A"
                score >= 60 -> "B"
                score >= 40 -> "C"
                score >= 20 -> "D"
                else -> "E"
            }
        } else "?"
        
        val color = if (score != null) {
            when {
                score >= 80 -> "#2E7D32"
                score >= 60 -> "#8BC34A"
                score >= 40 -> "#FFEB3B"
                score >= 20 -> "#FF9800"
                else -> "#F44336"
            }
        } else "#9E9E9E"

        features.add("""
            {"type":"Feature","geometry":{"type":"Point","coordinates":[${eval.longitude},${eval.latitude}]},
            "properties":{"type":"$type","label":"🐞 IBP $level","color":"$color","id":"${eval.id}"}}
        """.trimIndent().replace("\n", ""))
    }
    
    ripisylve.filter { it.latitude != null && it.longitude != null }.forEach { obs ->
        val color = "#1976D2" // Blue for water
        features.add("""
            {"type":"Feature","geometry":{"type":"Point","coordinates":[${obs.longitude},${obs.latitude}]},
            "properties":{"type":"Ripisylve","label":"💧 Ripisylve","color":"$color","id":"${obs.id}"}}
        """.trimIndent().replace("\n", ""))
    }
    
    station.filter { it.latitude != null && it.longitude != null }.forEach { obs ->
        val color = "#795548" // Brown for soil/station
        features.add("""
            {"type":"Feature","geometry":{"type":"Point","coordinates":[${obs.longitude},${obs.latitude}]},
            "properties":{"type":"Station","label":"⛰️ Station","color":"$color","id":"${obs.id}"}}
        """.trimIndent().replace("\n", ""))
    }
    
    return """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
}

private fun renderTigesOnMap(
    style: Style,
    geoTiges: List<Triple<Tige, Double, Double>>,
    essenceMap: Map<String, Essence>,
    essenceColors: Map<String, Int>,
    parcelles: List<Parcelle> = emptyList(),
    ibpDiagnostics: List<com.forestry.counter.domain.model.IbpEvaluation> = emptyList(),
    ripisylveDiagnostics: List<com.forestry.counter.domain.model.ripisylve.RipisylveObservation> = emptyList(),
    stationDiagnostics: List<com.forestry.counter.domain.model.station.StationObservation> = emptyList()
) {
    removeTigeLayers(style)
    try { style.removeLayer(DIAGNOSTICS_LABEL_LAYER_ID) } catch (_: Throwable) {}
    try { style.removeLayer(DIAGNOSTICS_LAYER_ID) } catch (_: Throwable) {}
    try { style.removeSource(DIAGNOSTICS_SOURCE_ID) } catch (_: Throwable) {}
    
    if (ibpDiagnostics.isNotEmpty() || ripisylveDiagnostics.isNotEmpty() || stationDiagnostics.isNotEmpty()) {
        val diagGeoJson = buildDiagnosticsGeoJson(ibpDiagnostics, ripisylveDiagnostics, stationDiagnostics)
        style.addSource(GeoJsonSource(DIAGNOSTICS_SOURCE_ID, diagGeoJson))
        
        style.addLayer(
            CircleLayer(DIAGNOSTICS_LAYER_ID, DIAGNOSTICS_SOURCE_ID).withProperties(
                PropertyFactory.circleColor(get("color")),
                PropertyFactory.circleRadius(10f),
                PropertyFactory.circleStrokeColor(Color.White.toArgb()),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleOpacity(0.9f)
            )
        )
        
        style.addLayer(
            SymbolLayer(DIAGNOSTICS_LABEL_LAYER_ID, DIAGNOSTICS_SOURCE_ID).withProperties(
                PropertyFactory.textField(get("label")),
                PropertyFactory.textSize(12f),
                PropertyFactory.textColor(Color.Black.toArgb()),
                PropertyFactory.textHaloColor(Color.White.toArgb()),
                PropertyFactory.textHaloWidth(2f),
                PropertyFactory.textOffset(arrayOf(0f, 1.0f)),
                PropertyFactory.textAnchor(com.mapbox.mapboxsdk.style.layers.Property.TEXT_ANCHOR_TOP),
                PropertyFactory.textAllowOverlap(false),
                PropertyFactory.textIgnorePlacement(false)
            )
        )
    }

    if (geoTiges.isEmpty()) return

    val geoJson = buildTigesGeoJson(geoTiges, essenceMap, essenceColors)
    val source = GeoJsonSource(
        TIGE_SOURCE_ID,
        geoJson,
        GeoJsonOptions()
            .withCluster(true)
            .withClusterRadius(50)
            .withClusterMaxZoom(13)
    )
    style.addSource(source)

    style.addLayer(
        CircleLayer(TIGE_CLUSTER_LAYER_ID, TIGE_SOURCE_ID)
            .withFilter(has("point_count"))
            .withProperties(
                PropertyFactory.circleColor(Color(0xFF2E7D32).toArgb()),
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

    // Couche de précision GPS : anneau coloré selon la qualité (excellent=vert, bon=vert clair, modéré=orange, mauvais=rouge, absent=gris)
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

    // Special trees layer (DEPERISSANT, ARBRE_BIO, MORT, PARASITE) with thicker stroke
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

/**
 * Dessine le tracé GPS (ligne + polygone + points) sur la carte.
 */
private fun renderTraceOnMap(style: Style, tracer: GpsParcelTracer) {
    // Remove existing trace layers
    try { style.removeLayer(TRACE_POINTS_LAYER_ID) } catch (_: Throwable) {}
    try { style.removeLayer(TRACE_LINE_ID) } catch (_: Throwable) {}
    try { style.removeLayer(TRACE_FILL_ID) } catch (_: Throwable) {}
    try { style.removeSource(TRACE_POINTS_SOURCE_ID) } catch (_: Throwable) {}
    try { style.removeSource(TRACE_SOURCE_ID) } catch (_: Throwable) {}

    val state = tracer.state.value
    if (state.points.isEmpty()) return

    // Polygon fill + line (if ≥3 points)
    val polyJson = tracer.toGeoJsonPolygon()
    val lineJson = tracer.toGeoJsonLine()
    val geom = polyJson ?: lineJson ?: return

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

    // Points
    val pointsJson = tracer.toGeoJsonPoints()
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

/**
 * Info d'un arbre tapé sur la carte.
 */
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

data class TappedDiagnosticInfo(
    val type: String,
    val label: String,
    val id: String,
    val lat: Double,
    val lon: Double
)

private fun attachTigeTapInfo(
    map: MapboxMap,
    context: Context,
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

            // Extract coordinates from geometry
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
            Log.e(TAG, "Tige tap handler error", e)
            false
        }
    }
}

private fun attachDiagnosticTapInfo(
    map: MapboxMap,
    onDiagnosticTapped: (TappedDiagnosticInfo) -> Unit
) {
    map.addOnMapClickListener { latLng ->
        try {
            val point = map.projection.toScreenLocation(latLng)
            val features = map.queryRenderedFeatures(point, DIAGNOSTICS_LAYER_ID, DIAGNOSTICS_LABEL_LAYER_ID)
            val f = features.firstOrNull() ?: return@addOnMapClickListener false

            val props = f.properties() ?: return@addOnMapClickListener false
            val geom = f.geometry()

            val type = props.get("type")?.asString ?: return@addOnMapClickListener false
            val label = props.get("label")?.asString ?: ""
            val id = props.get("id")?.asString ?: ""

            val diagLat: Double
            val diagLon: Double
            if (geom != null && geom is com.mapbox.geojson.Point) {
                diagLon = geom.longitude()
                diagLat = geom.latitude()
            } else {
                diagLon = latLng.longitude
                diagLat = latLng.latitude
            }

            onDiagnosticTapped(
                TappedDiagnosticInfo(
                    type = type,
                    label = label,
                    id = id,
                    lat = diagLat,
                    lon = diagLon
                )
            )
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Diagnostic tap handler error", e)
            false
        }
    }
}

private fun offlineLocalStyle(name: String = "Offline Local"): String {
    return """{
  "version": 8,
  "name": "$name",
  "sources": {},
  "layers": [
    { "id": "background", "type": "background", "paint": { "background-color": "#EFF5EC" } }
  ]
}"""
}

/**
 * Builds a raster-only MapLibre style JSON from a single tile URL template.
 * This is more reliable than inline multi-line JSON strings.
 */
private fun rasterStyle(name: String, tileUrl: String, tileSize: Int = 256, maxZoom: Int = 19): String {
    return """{
  "version": 8,
  "name": "$name",
  "glyphs": "https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf",
  "sources": {
    "tiles": {
      "type": "raster",
      "tiles": ["$tileUrl"],
      "tileSize": $tileSize,
      "maxzoom": $maxZoom
    }
  },
  "layers": [
    { "id": "tiles", "type": "raster", "source": "tiles" }
  ]
}"""
}

/**
 * Construit un style MapLibre avec couche de base + couches overlay optionnelles.
 */
private fun rasterStyleMulti(
    name: String,
    baseTileUrl: String,
    overlayTileUrls: List<String> = emptyList(),
    tileSize: Int = 256,
    maxZoom: Int = 19
): String {
    val sources = mutableListOf<String>()
    val layers = mutableListOf<String>()
    sources += """"base":{"type":"raster","tiles":["$baseTileUrl"],"tileSize":$tileSize,"maxzoom":$maxZoom}"""
    layers += """{ "id": "base", "type": "raster", "source": "base" }"""
    overlayTileUrls.forEachIndexed { i, url ->
        sources += """"overlay$i":{"type":"raster","tiles":["$url"],"tileSize":256,"maxzoom":$maxZoom}"""
        layers += """{ "id": "overlay$i", "type": "raster", "source": "overlay$i", "paint": { "raster-opacity": 0.7 } }"""
    }
    return """{"version":8,"name":"$name","glyphs":"https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf","sources":{${sources.joinToString(",")}},"layers":[${layers.joinToString(",")}]}"""
}

// ── URL WMTS GéoPortail (data.geopf.fr) ──
private fun geopfLayer(layer: String, format: String = "image/png") =
    "https://data.geopf.fr/wmts?" +
    "SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&STYLE=normal&FORMAT=$format" +
    "&TILEMATRIXSET=PM&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&LAYER=$layer"

/**
 * Catégorie de couche (pour organiser le sélecteur).
 */
enum class LayerCategory(val labelResId: Int) {
    GENERAL(R.string.map_category_general)
}

/**
 * Description d'une couche de fond de carte.
 */
data class MapLayerDef(
    val key: String,
    val labelResId: Int,
    val emoji: String,
    val styleJson: String,
    val isDark: Boolean = false,
    val category: LayerCategory = LayerCategory.GENERAL,
    val tileUrls: List<String> = emptyList()
)

private val MAP_LAYERS = listOf(
    // ── Couches Principales IGN ──
    MapLayerDef(
        key = "PLAN_IGN",
        labelResId = R.string.map_layer_plan_ign,
        emoji = "🗺️", // Carte classique
        styleJson = rasterStyle(
            "Plan IGN v2",
            geopfLayer("GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2")
        ),
        category = LayerCategory.GENERAL,
        tileUrls = listOf(geopfLayer("GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2"))
    ),
    MapLayerDef(
        key = "ORTHO_IGN",
        labelResId = R.string.map_layer_ortho_ign,
        emoji = "🛰️", // Satellite
        styleJson = rasterStyle(
            "Ortho IGN",
            geopfLayer("ORTHOIMAGERY.ORTHOPHOTOS", "image/jpeg")
        ),
        isDark = true,
        tileUrls = listOf(geopfLayer("ORTHOIMAGERY.ORTHOPHOTOS", "image/jpeg"))
    ),
    
    // ── Couches Cadastre ──
    MapLayerDef(
        key = "PLAN_IGN_CADASTRE",
        labelResId = R.string.map_layer_plan_ign_cadastre,
        emoji = "📐", // Règle/Mesure (Cadastre)
        styleJson = rasterStyleMulti(
            "Plan IGN + Cadastre",
            geopfLayer("GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2"),
            overlayTileUrls = listOf(
                geopfLayer("CADASTRALPARCELS.PARCELLAIRE_EXPRESS")
            )
        ),
        category = LayerCategory.GENERAL,
        tileUrls = listOf(
            geopfLayer("GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2"),
            geopfLayer("CADASTRALPARCELS.PARCELLAIRE_EXPRESS")
        )
    ),
    MapLayerDef(
        key = "ORTHO_CADASTRE",
        labelResId = R.string.map_layer_ortho_cadastre,
        emoji = "🏘️", // Maisons (Parcelles)
        styleJson = rasterStyleMulti(
            "Ortho IGN + Cadastre",
            geopfLayer("ORTHOIMAGERY.ORTHOPHOTOS", "image/jpeg"),
            overlayTileUrls = listOf(
                geopfLayer("CADASTRALPARCELS.PARCELLAIRE_EXPRESS")
            )
        ),
        isDark = true,
        category = LayerCategory.GENERAL,
        tileUrls = listOf(
            geopfLayer("ORTHOIMAGERY.ORTHOPHOTOS", "image/jpeg"),
            geopfLayer("CADASTRALPARCELS.PARCELLAIRE_EXPRESS")
        )
    ),

    // ── Couches Internationales & Spéciales ──
    MapLayerDef(
        key = "TOPO",
        labelResId = R.string.map_layer_topo,
        emoji = "⛰️", // Montagne
        styleJson = rasterStyle(
            "OpenTopoMap",
            "https://tile.opentopomap.org/{z}/{x}/{y}.png",
            maxZoom = 17
        ),
        tileUrls = listOf("https://tile.opentopomap.org/{z}/{x}/{y}.png")
    ),
    MapLayerDef(
        key = "CARTO_VOYAGER",
        labelResId = R.string.map_layer_carto,
        emoji = "🧭", // Boussole (Carto claire)
        styleJson = rasterStyle(
            "Carto Voyager",
            "https://basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}@2x.png"
        ),
        tileUrls = listOf("https://basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}@2x.png")
    ),
    MapLayerDef(
        key = "SATELLITE",
        labelResId = R.string.map_layer_satellite,
        emoji = "🌍", // Globe
        styleJson = rasterStyle(
            "ESRI Satellite",
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
        ),
        isDark = true,
        tileUrls = listOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}")
    ),

    // ── Couche hors-ligne locale ──
    MapLayerDef(
        key = "OFFLINE_LOCAL",
        labelResId = R.string.map_layer_offline_local,
        emoji = "📥",
        styleJson = offlineLocalStyle("Offline Local"),
        tileUrls = emptyList()
    )
)

private val ESSENCE_COLOR_PALETTE = intArrayOf(
    0xFF2E7D32.toInt(), // vert forêt
    0xFF1565C0.toInt(), // bleu
    0xFFEF6C00.toInt(), // orange
    0xFF7B1FA2.toInt(), // violet
    0xFFC62828.toInt(), // rouge
    0xFF00838F.toInt(), // cyan
    0xFF4E342E.toInt(), // brun
    0xFF9E9D24.toInt(), // olive
    0xFFAD1457.toInt(), // rose
    0xFF37474F.toInt(), // gris-bleu
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(
    parcelleId: String,
    tigeRepository: TigeRepository,
    essenceRepository: EssenceRepository? = null,
    parcelleRepository: ParcelleRepository,
    ibpRepository: IbpRepository,
    ripisylveRepository: RipisylveRepository,
    stationRepository: StationRepository,
    preferencesManager: UserPreferencesManager,
    offlineTileManager: OfflineTileManager? = null,
    onNavigateBack: () -> Unit,
    initialNavLat: Double? = null,
    initialNavLon: Double? = null,
    initialNavEssence: String? = null,
    initialNavDiam: Double? = null
) {
    val context = LocalContext.current

    // Déterminer le flux de tiges selon le scope
    val tigesFlow = remember(parcelleId) {
        when {
            parcelleId == "none" -> flowOf(emptyList<Tige>())
            parcelleId == "all" -> tigeRepository.getAllTiges()
            parcelleId.startsWith("forest_") -> {
                val forestId = parcelleId.removePrefix("forest_")
                if (parcelleRepository != null) {
                    parcelleRepository.getParcellesByForest(forestId).flatMapLatest { parcelles ->
                        if (parcelles.isEmpty()) flowOf(emptyList())
                        else {
                            val flows = parcelles.map { tigeRepository.getTigesByParcelle(it.id) }
                            combine(flows) { arrays -> arrays.flatMap { it } }
                        }
                    }
                } else flowOf(emptyList())
            }
            else -> tigeRepository.getTigesByParcelle(parcelleId)
        }
    }
    val tiges by tigesFlow.collectAsState(initial = emptyList())
    val ibpDiagnostics by ibpRepository.getAll().collectAsState(initial = emptyList())
    val ripisylveDiagnostics by ripisylveRepository.getAll().collectAsState(initial = emptyList())
    val stationDiagnostics by stationRepository.getAll().collectAsState(initial = emptyList())
    val essences by (essenceRepository?.getAllEssences()
        ?: kotlinx.coroutines.flow.flowOf(emptyList<Essence>())).collectAsState(initial = emptyList())
    val animationsEnabled by preferencesManager.animationsEnabled.collectAsState(initial = true)
    val mapLastLayerKey by preferencesManager.mapLastLayerKey.collectAsState(initial = "PLAN_IGN")
    val mapShowLegendPref by preferencesManager.mapShowLegend.collectAsState(initial = false)
    val mapOnlyReliableGps by preferencesManager.mapOnlyReliableGps.collectAsState(initial = false)
    val mapReliableGpsThresholdM by preferencesManager.mapReliableGpsThresholdM.collectAsState(initial = 8f)

    val parcelles by parcelleRepository.getAllParcelles().collectAsState(initial = emptyList())

    // Permission localisation
    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    val hasLocationPermission = locationPermission.status.isGranted ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    // Demander la permission au lancement si pas encore accordée
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            locationPermission.launchPermissionRequest()
        }
    }

    // Initialiser MapLibre de façon synchrone AVANT toute création de MapView
    remember(context) {
        try { Mapbox.getInstance(context) } catch (_: Throwable) {}
        true
    }

    @Suppress("NAME_SHADOWING")
    val offlineTileManager = offlineTileManager ?: remember(context) { OfflineTileManager(context) }
    val offlineProgress by offlineTileManager.downloadProgress.collectAsState()
    var showOfflineSnackbar by remember { mutableStateOf(false) }

    // Présence de tuiles hors-ligne (se met à jour après un téléchargement)
    var hasOfflineTilesState by remember { mutableStateOf(offlineTileManager.hasOfflineTiles()) }
    LaunchedEffect(offlineProgress) {
        if (offlineProgress?.isComplete == true && offlineProgress?.error == null) {
            hasOfflineTilesState = offlineTileManager.hasOfflineTiles()
        }
    }

    val essenceMap = remember(essences) { essences.associateBy { it.code.uppercase() } }

    var dismissedGpsBanner by remember { mutableStateOf(false) }

    val geoTiges = remember(tiges) {
        tiges.mapNotNull { t ->
            val (lon, lat, _) = WktUtils.parsePointZ(t.gpsWkt)
            if (lon != null && lat != null) Triple(t, lon, lat) else null
        }
    }
    val reliableGeoTiges = remember(geoTiges, mapReliableGpsThresholdM) {
        geoTiges.filter { (t, _, _) ->
            val precision = t.precisionM ?: Double.MAX_VALUE
            precision <= mapReliableGpsThresholdM.toDouble()
        }
    }
    val displayedGeoTiges = remember(geoTiges, reliableGeoTiges, mapOnlyReliableGps) {
        if (mapOnlyReliableGps) reliableGeoTiges else geoTiges
    }
    val withGps = displayedGeoTiges.size
    val total = tiges.size

    // Couleurs par essence (stables)
    val essenceColors = remember(displayedGeoTiges) {
        val map = mutableMapOf<String, Int>()
        var idx = 0
        displayedGeoTiges.forEach { (t, _, _) ->
            map.getOrPut(t.essenceCode.uppercase()) {
                ESSENCE_COLOR_PALETTE[idx++ % ESSENCE_COLOR_PALETTE.size]
            }
        }
        map
    }

    // Compteurs par essence
    val essenceCounts = remember(displayedGeoTiges) {
        displayedGeoTiges.groupBy { it.first.essenceCode.uppercase() }.mapValues { it.value.size }
    }

    // Filtre d'essences (légende cliquable)
    var hiddenEssences by remember { mutableStateOf(emptySet<String>()) }
    val filteredGeoTiges = remember(displayedGeoTiges, hiddenEssences) {
        if (hiddenEssences.isEmpty()) displayedGeoTiges
        else displayedGeoTiges.filter { (t, _, _) -> t.essenceCode.uppercase() !in hiddenEssences }
    }

    var mapReady by remember { mutableStateOf(false) }
    var mapLibreMap by remember { mutableStateOf<MapboxMap?>(null) }
    val initialLayerIdx = remember(mapLastLayerKey) {
        MAP_LAYERS.indexOfFirst { it.key == mapLastLayerKey }.takeIf { it >= 0 } ?: 0
    }
    var currentLayerIdx by remember(mapLastLayerKey) { mutableIntStateOf(initialLayerIdx) }
    var showLegend by remember(mapShowLegendPref) { mutableStateOf(mapShowLegendPref) }
    var showLayerPicker by remember { mutableStateOf(false) }
    var showCoords by remember { mutableStateOf(false) }
    var coordsText by remember { mutableStateOf("") }
    var tigeTapAttached by remember { mutableStateOf(false) }

    // ── Shapefile overlay state ──
    val scope = rememberCoroutineScope()
    val shpManager = remember { ShapefileOverlayManager(context) }
    var shpOverlay by remember { mutableStateOf<ShapefileOverlay?>(shpManager.listOverlays().firstOrNull()) }
    var shpGeoJsonFile by remember { mutableStateOf<java.io.File?>(null) }
    var showShpPanel by remember { mutableStateOf(false) }
    var shpImporting by remember { mutableStateOf(false) }

    // ── GPS Parcel Trace state ──
    val gpsTracer = remember(context) { GpsParcelTracer(context) }
    val traceState by gpsTracer.state.collectAsState()
    var showTraceSaveDialog by remember { mutableStateOf(false) }
    var traceName by remember { mutableStateOf("") }

    // ── Measure tool state ──
    val measureActiveState: androidx.compose.runtime.MutableState<Boolean> = remember { mutableStateOf(false) }
    var measureActive by measureActiveState
    val measurePointsState: androidx.compose.runtime.MutableState<List<LatLng>> = remember { mutableStateOf(emptyList()) }
    var measurePoints by measurePointsState
    var measureMode by remember { mutableStateOf(MeasureMode.DISTANCE) }
    var showMeasureSaveDialog by remember { mutableStateOf(false) }
    var measureSaveName by remember { mutableStateOf("") }
    var measureDistUnit by remember { mutableStateOf(MeasureDistUnit.M) }
    var measureAreaUnit by remember { mutableStateOf(MeasureAreaUnit.HA) }
    var measureColor by remember { mutableStateOf(MEASURE_COLORS[0]) }
    var showSavedMeasuresPanel by remember { mutableStateOf(false) }

    // ── Tree navigation state ──
    val treeNavigator = remember(context) { TreeNavigator(context) }
    val navState by treeNavigator.state.collectAsState()
    var tappedTree by remember { mutableStateOf<TappedTreeInfo?>(null) }
    var tappedDiagnostic by remember { mutableStateOf<TappedDiagnosticInfo?>(null) }

    // Cleanup navigator on dispose
    DisposableEffect(Unit) {
        onDispose {
            treeNavigator.stopNavigation()
            gpsTracer.clearTrace()
        }
    }

    // ── Auto-start navigation if initial nav params are provided ──
    LaunchedEffect(initialNavLat, initialNavLon, hasLocationPermission) {
        if (initialNavLat != null && initialNavLon != null && hasLocationPermission) {
            val target = TreeNavigator.Target(
                tigeId = "",
                essenceName = initialNavEssence ?: "?",
                essenceCode = initialNavEssence ?: "?",
                diamCm = initialNavDiam ?: 0.0,
                hauteurM = null,
                lat = initialNavLat,
                lon = initialNavLon
            )
            treeNavigator.startNavigation(target)
        }
    }

    // Résoudre le fichier GeoJSON au démarrage si un overlay existe
    LaunchedEffect(shpOverlay?.id) {
        val overlay = shpOverlay ?: run { shpGeoJsonFile = null; return@LaunchedEffect }
        shpGeoJsonFile = shpManager.getGeoJsonFile(overlay)
        Log.d(TAG, "Overlay ${overlay.id}: geojson file=${shpGeoJsonFile?.absolutePath}, exists=${shpGeoJsonFile?.exists()}")
    }

    // File picker pour importer un .zip shapefile
    val shpPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        shpImporting = true
        scope.launch {
            val overlay = shpManager.importFromUri(uri)
            shpImporting = false
            if (overlay != null) {
                shpOverlay = overlay
                Toast.makeText(
                    context,
                    context.getString(R.string.shp_import_success, overlay.featureCount),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(context, context.getString(R.string.shp_import_error), Toast.LENGTH_LONG).show()
            }
        }
    }

    // Appliquer l'overlay shapefile quand le fichier ou les paramètres changent
    fun applyCurrentShpOverlay(style: Style): String {
        val file = shpGeoJsonFile ?: return "no file"
        val ov = shpOverlay ?: return "no overlay"
        return applyShapefileOverlay(style, file, ov)
    }

    fun switchLayer(index: Int) {
        currentLayerIdx = index
        val map = mapLibreMap ?: return
        val layer = MAP_LAYERS.getOrElse(index) { MAP_LAYERS[0] }
        scope.launch { preferencesManager.setMapLastLayerKey(layer.key) }

        // Pour la couche offline, utiliser le style avec tuiles locales si disponible
        val styleJson = if (layer.key == "OFFLINE_LOCAL" && offlineTileManager.hasOfflineTiles()) {
            offlineTileManager.buildOfflineStyle(offlineTileManager.downloadedLayerCount().coerceAtLeast(1))
        } else {
            layer.styleJson
        }

        try {
            map.setStyle(Style.Builder().fromJson(styleJson)) { style ->
                enableLocationComponent(map, style, context)
                applyCurrentShpOverlay(style)
                renderTigesOnMap(style, filteredGeoTiges, essenceMap, essenceColors,
                    parcelles = parcelles,
                    ibpDiagnostics = ibpDiagnostics,
                    ripisylveDiagnostics = ripisylveDiagnostics,
                    stationDiagnostics = stationDiagnostics
                )
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Style switch failed", e)
            map.setStyle(Style.Builder().fromJson(offlineLocalStyle("Offline fallback"))) { style ->
                enableLocationComponent(map, style, context)
                applyCurrentShpOverlay(style)
                renderTigesOnMap(style, filteredGeoTiges, essenceMap, essenceColors)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.map_parcelle_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (withGps > 0) {
                            Text(
                                stringResource(R.string.map_subtitle_stems_format, withGps, total),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    // Télécharger tuiles hors-ligne
                    IconButton(onClick = {
                        val map = mapLibreMap
                        if (map != null) {
                            val bounds = map.projection.visibleRegion.latLngBounds
                            val layer = MAP_LAYERS.getOrElse(currentLayerIdx) { MAP_LAYERS[0] }
                            offlineTileManager.downloadRegion(
                                name = parcelleId,
                                latSouth = bounds.southWest.latitude,
                                latNorth = bounds.northEast.latitude,
                                lonWest = bounds.southWest.longitude,
                                lonEast = bounds.northEast.longitude,
                                tileUrlTemplates = layer.tileUrls,
                                minZoom = map.cameraPosition.zoom.toInt().coerceAtLeast(8),
                                maxZoom = (map.cameraPosition.zoom.toInt() + 4).coerceAtMost(17)
                            )
                        }
                    }) {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = stringResource(R.string.offline_download),
                            tint = if (offlineProgress != null && !offlineProgress!!.isComplete)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // Shapefile overlay
                    IconButton(onClick = { showShpPanel = !showShpPanel }) {
                        Icon(
                            Icons.Default.Map,
                            contentDescription = stringResource(R.string.shp_overlay),
                            tint = if (shpOverlay != null && shpOverlay?.visible == true) Color(0xFF2E7D32)
                                   else if (showShpPanel) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // Toggle légende
                    IconButton(onClick = {
                        val next = !showLegend
                        showLegend = next
                        scope.launch { preferencesManager.setMapShowLegend(next) }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.FormatListBulleted,
                            contentDescription = stringResource(R.string.map_legend),
                            tint = if (showLegend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // Ouvrir sélecteur de couches
                    IconButton(onClick = { showLayerPicker = !showLayerPicker }) {
                        Icon(
                            Icons.Default.Layers,
                            contentDescription = stringResource(R.string.map_style),
                            tint = if (showLayerPicker) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Diagnostic Tap Card ──
            tappedDiagnostic?.let { diag ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 72.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    diag.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(onClick = { tappedDiagnostic = null }) {
                                    Icon(Icons.Default.Close, contentDescription = "Fermer")
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Coordonnées : ${String.format(Locale.US, "%.5f", diag.lat)}, ${String.format(Locale.US, "%.5f", diag.lon)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── Tree Tap Card ──
            val lifecycleOwner = LocalLifecycleOwner.current
            var mapError by remember { mutableStateOf(false) }
            val mapView = remember {
                try {
                    MapView(context)
                } catch (e: Throwable) {
                    mapError = true
                    null
                }
            }

            if (mapView != null && !mapError) {
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        try {
                            when (event) {
                                Lifecycle.Event.ON_START -> mapView.onStart()
                                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                                Lifecycle.Event.ON_STOP -> mapView.onStop()
                                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                                else -> {}
                            }
                        } catch (_: Throwable) {}
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                        try { mapView.onDestroy() } catch (_: Throwable) {}
                    }
                }

                AndroidView(
                    factory = {
                        mapView.apply {
                            try {
                                onCreate(null)
                                getMapAsync { map ->
                                    try {
                                        val selectedLayer = MAP_LAYERS.getOrElse(currentLayerIdx) { MAP_LAYERS[0] }
                                        val initStyleJson = if (selectedLayer.key == "OFFLINE_LOCAL" && offlineTileManager.hasOfflineTiles()) {
                                            offlineTileManager.buildOfflineStyle(offlineTileManager.downloadedLayerCount().coerceAtLeast(1))
                                        } else {
                                            selectedLayer.styleJson
                                        }
                                        map.setStyle(Style.Builder().fromJson(initStyleJson)) { style ->
                                            mapLibreMap = map
                                            mapReady = true
                                            enableLocationComponent(map, style, context)
                                            applyCurrentShpOverlay(style)
                                            renderTigesOnMap(style, filteredGeoTiges, essenceMap, essenceColors)
                                            if (!tigeTapAttached) {
                                                map.addOnMapClickListener { latLng ->
                                                    if (measureActiveState.value) {
                                                        measurePointsState.value = measurePointsState.value + latLng
                                                        true
                                                    } else false
                                                }
                                                attachTigeTapInfo(map, context) { info -> tappedTree = info }
                                                attachDiagnosticTapInfo(map) { info -> tappedDiagnostic = info }
                                                tigeTapAttached = true
                                            }
                                        }
                                        map.uiSettings.apply {
                                            isCompassEnabled = true
                                            isRotateGesturesEnabled = true
                                            isZoomGesturesEnabled = true
                                            isScrollGesturesEnabled = true
                                            isTiltGesturesEnabled = false
                                            setAttributionMargins(16, 0, 0, 16)
                                        }
                                    } catch (e: Throwable) {
                                        Log.w(TAG, "Error setting selected map style, fallback to offline local", e)
                                        map.setStyle(Style.Builder().fromJson(offlineLocalStyle("Offline fallback"))) { style ->
                                            mapLibreMap = map
                                            mapReady = true
                                            enableLocationComponent(map, style, context)
                                        }
                                    }
                                }
                            } catch (_: Throwable) {}
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Zoom initial sur la position GPS à altitude modérée quand pas de données d'arbres
            LaunchedEffect(mapReady, hasLocationPermission) {
                val map = mapLibreMap ?: return@LaunchedEffect
                if (!mapReady) return@LaunchedEffect
                // Attendre un peu pour laisser le LocationComponent s'initialiser
                kotlinx.coroutines.delay(600)
                if (displayedGeoTiges.isNotEmpty()) return@LaunchedEffect
                try {
                    val lc = map.locationComponent
                    val lastLoc = lc.lastKnownLocation
                    if (lastLoc != null) {
                        map.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(lastLoc.latitude, lastLoc.longitude), 13.0
                            ), 1000
                        )
                    }
                } catch (_: Throwable) { /* permission pas encore accordée */ }
            }

            // Ajouter/mettre à jour les tiges (source GeoJSON + clusters) quand la carte et les données sont prêtes
            LaunchedEffect(mapReady, filteredGeoTiges, essenceColors) {
                val map = mapLibreMap ?: return@LaunchedEffect
                if (!mapReady) return@LaunchedEffect

                map.getStyle { style ->
                    renderTigesOnMap(style, filteredGeoTiges, essenceMap, essenceColors)
                }

                if (displayedGeoTiges.isNotEmpty()) {
                    val boundsBuilder = LatLngBounds.Builder()
                    displayedGeoTiges.forEach { (_, lon, lat) ->
                        boundsBuilder.include(LatLng(lat, lon))
                    }
                    try {
                        val bounds = boundsBuilder.build()
                        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100), 800)
                    } catch (_: Throwable) {
                        val first = displayedGeoTiges.first()
                        map.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(LatLng(first.third, first.second), 17.0),
                            800
                        )
                    }
                }
            }

            // ── Mettre à jour le tracé GPS sur la carte ──
            LaunchedEffect(mapReady, traceState) {
                val map = mapLibreMap ?: return@LaunchedEffect
                if (!mapReady) return@LaunchedEffect
                map.getStyle { style -> renderTraceOnMap(style, gpsTracer) }
            }

            // ── Mettre à jour la couche de mesure ──
            LaunchedEffect(mapReady, measurePoints, measureMode, measureColor) {
                val map = mapLibreMap ?: return@LaunchedEffect
                if (!mapReady) return@LaunchedEffect
                map.getStyle { style ->
                    try { renderMeasureOnMap(style, measurePoints, measureMode, measureColor.toArgb()) }
                    catch (_: Throwable) {}
                }
            }

            // ── Appliquer/mettre à jour overlay shapefile quand les données changent ──
            LaunchedEffect(shpGeoJsonFile, shpOverlay, mapReady) {
                val map = mapLibreMap ?: return@LaunchedEffect
                if (!mapReady) return@LaunchedEffect
                Log.d(TAG, "Applying shapefile overlay: file=${shpGeoJsonFile?.absolutePath}, overlay=${shpOverlay?.id}")
                map.getStyle { style ->
                    val result = applyCurrentShpOverlay(style)
                    Log.d(TAG, "SHP apply result: $result")
                }
            }

            // ── Panneau sélecteur de couches (par catégorie) ──
            AnimatedVisibility(
                visible = showLayerPicker,
                enter = fadeIn(tween(200)) + slideInVertically(tween(250)) { -it / 4 },
                exit = fadeOut(tween(150)) + slideOutVertically(tween(200)) { -it / 4 },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp, start = 6.dp, end = 6.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // En-tête
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Layers,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    stringResource(R.string.map_layer_picker_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = { showLayerPicker = false },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MAP_LAYERS.forEachIndexed { index, layer ->
                                if (layer.key == "OFFLINE_LOCAL") return@forEachIndexed
                                LayerChip(
                                    layer = layer,
                                    isSelected = index == currentLayerIdx,
                                    onClick = {
                                        switchLayer(index)
                                        showLayerPicker = false
                                    }
                                )
                            }
                            // Couche hors-ligne : visible uniquement si des tuiles sont téléchargées
                            val offlineIdx = MAP_LAYERS.indexOfFirst { it.key == "OFFLINE_LOCAL" }
                            if (hasOfflineTilesState && offlineIdx >= 0) {
                                val (tileCount, _) = remember(hasOfflineTilesState) {
                                    offlineTileManager.cacheStats()
                                }
                                Box {
                                    LayerChip(
                                        layer = MAP_LAYERS[offlineIdx],
                                        isSelected = offlineIdx == currentLayerIdx,
                                        onClick = {
                                            switchLayer(offlineIdx)
                                            showLayerPicker = false
                                        }
                                    )
                                    if (tileCount > 0) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(2.dp)
                                        ) {
                                            Text(
                                                "$tileCount",
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontSize = 8.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Panneau shapefile overlay ──
            @OptIn(ExperimentalLayoutApi::class)
            AnimatedVisibility(
                visible = showShpPanel,
                enter = fadeIn(tween(200)) + slideInVertically(tween(250)) { -it / 4 },
                exit = fadeOut(tween(150)) + slideOutVertically(tween(200)) { -it / 4 },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp, start = 6.dp, end = 6.dp)
                    .fillMaxWidth()
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(14.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // En-tête
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color(0xFF2E7D32))
                                Text(stringResource(R.string.shp_overlay), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = { showShpPanel = false }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (shpOverlay == null) {
                            Text(
                                stringResource(R.string.shp_no_overlay),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                onClick = {
                                    shpPickerLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                                },
                                color = Color(0xFF2E7D32),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Text(stringResource(R.string.shp_import), color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        } else {
                            val ov = shpOverlay!!

                            // ── Info + Visibilité ──
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(ov.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32))
                                    Text(stringResource(R.string.shp_info_format, ov.featureCount, ov.forestNames.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = ov.visible,
                                    onCheckedChange = { vis ->
                                        val updated = ov.copy(visible = vis)
                                        shpOverlay = updated
                                        shpManager.updateOverlay(updated)
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.fillMaxWidth().height(1.dp)) {}

                            // ── REMPLISSAGE ──
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.shp_fill_color), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SHP_COLOR_PALETTE.forEach { c ->
                                    val isSelected = (ov.fillColor and 0x00FFFFFF) == (c and 0x00FFFFFF)
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(c))
                                            .then(
                                                if (isSelected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                                else Modifier
                                            )
                                            .clickable {
                                                val updated = ov.copy(fillColor = c)
                                                shpOverlay = updated
                                                shpManager.updateOverlay(updated)
                                            }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(stringResource(R.string.shp_fill_opacity, (ov.fillOpacity * 100).toInt()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Slider(
                                value = ov.fillOpacity,
                                onValueChange = { v -> shpOverlay = ov.copy(fillOpacity = v) },
                                onValueChangeFinished = { shpOverlay?.let { shpManager.updateOverlay(it) } },
                                valueRange = 0f..1f,
                                modifier = Modifier.fillMaxWidth().height(32.dp)
                            )

                            // ── CONTOUR ──
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.fillMaxWidth().height(1.dp)) {}
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.shp_border_color), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SHP_COLOR_PALETTE.forEach { c ->
                                    val isSelected = (ov.borderColor and 0x00FFFFFF) == (c and 0x00FFFFFF)
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(c))
                                            .then(
                                                if (isSelected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                                else Modifier
                                            )
                                            .clickable {
                                                val updated = ov.copy(borderColor = c)
                                                shpOverlay = updated
                                                shpManager.updateOverlay(updated)
                                            }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(stringResource(R.string.shp_border_opacity, (ov.borderOpacity * 100).toInt()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Slider(
                                value = ov.borderOpacity,
                                onValueChange = { v -> shpOverlay = ov.copy(borderOpacity = v) },
                                onValueChangeFinished = { shpOverlay?.let { shpManager.updateOverlay(it) } },
                                valueRange = 0f..1f,
                                modifier = Modifier.fillMaxWidth().height(32.dp)
                            )
                            Text(
                                stringResource(R.string.shp_border_width, "%.1f".format(java.util.Locale.US, ov.borderWidth)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = ov.borderWidth,
                                onValueChange = { v -> shpOverlay = ov.copy(borderWidth = v) },
                                onValueChangeFinished = { shpOverlay?.let { shpManager.updateOverlay(it) } },
                                valueRange = 0.5f..5f,
                                modifier = Modifier.fillMaxWidth().height(32.dp)
                            )

                            // ── ÉTIQUETTES ──
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.fillMaxWidth().height(1.dp)) {}
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.shp_labels_title), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))

                            val isFr = java.util.Locale.getDefault().language == "fr"
                            LabelField.entries.forEach { field ->
                                val checked = field in ov.labelFields
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val newFields = if (checked) ov.labelFields - field else ov.labelFields + field
                                            val updated = ov.copy(labelFields = newFields)
                                            shpOverlay = updated
                                            shpManager.updateOverlay(updated)
                                        }
                                        .padding(vertical = 1.dp)
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { isChecked ->
                                            val newFields = if (isChecked) ov.labelFields + field else ov.labelFields - field
                                            val updated = ov.copy(labelFields = newFields)
                                            shpOverlay = updated
                                            shpManager.updateOverlay(updated)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        if (isFr) field.frLabel else field.enLabel,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }

                            // Option combiner
                            if (ov.labelFields.size > 1) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val updated = ov.copy(combineLabels = !ov.combineLabels)
                                            shpOverlay = updated
                                            shpManager.updateOverlay(updated)
                                        }
                                        .padding(vertical = 2.dp)
                                ) {
                                    Switch(
                                        checked = ov.combineLabels,
                                        onCheckedChange = { combine ->
                                            val updated = ov.copy(combineLabels = combine)
                                            shpOverlay = updated
                                            shpManager.updateOverlay(updated)
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.shp_combine_labels), style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            // Taille des étiquettes
                            if (ov.labelFields.isNotEmpty()) {
                                Text(
                                    stringResource(R.string.shp_label_size, ov.labelSize.toInt()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Slider(
                                    value = ov.labelSize,
                                    onValueChange = { v -> shpOverlay = ov.copy(labelSize = v) },
                                    onValueChangeFinished = { shpOverlay?.let { shpManager.updateOverlay(it) } },
                                    valueRange = 6f..24f,
                                    modifier = Modifier.fillMaxWidth().height(32.dp)
                                )
                            }

                            // ── ACTIONS ──
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.fillMaxWidth().height(1.dp)) {}
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    onClick = {
                                        shpPickerLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                                    },
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        stringResource(R.string.shp_replace),
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Surface(
                                    onClick = {
                                        shpManager.deleteOverlay(ov.id)
                                        shpOverlay = null
                                        shpGeoJsonFile = null
                                    },
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        stringResource(R.string.shp_delete),
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        // Indicateur d'import en cours
                        if (shpImporting) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(stringResource(R.string.shp_importing), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // ── Overlay : couverture GPS (compact, en bas à gauche) ──
            AnimatedVisibility(
                visible = total > 0 && !showLayerPicker,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(150)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp).widthIn(max = 160.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.GpsFixed,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "$withGps / $total",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        val progressVal = if (total > 0) withGps.toFloat() / total.toFloat() else 0f
                        LinearProgressIndicator(
                            progress = { progressVal },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                    }
                }
            }

            // ── Légende par essence (améliorée) ──
            AnimatedVisibility(
                visible = showLegend && essenceColors.isNotEmpty(),
                enter = fadeIn(tween(200)) + expandVertically(tween(250)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(200)),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 88.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Forest,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                stringResource(R.string.map_legend),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        val totalWithGps = essenceCounts.values.sum()
                        essenceColors.forEach { (code, color) ->
                            val name = essenceMap[code]?.name ?: code
                            val count = essenceCounts[code] ?: 0
                            val pct = if (totalWithGps > 0) count * 100 / totalWithGps else 0
                            val isHidden = code in hiddenEssences
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.clickable {
                                    hiddenEssences = if (isHidden) hiddenEssences - code
                                    else hiddenEssences + code
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(if (isHidden) Color.LightGray else Color(color))
                                        .border(1.5.dp, Color.White, CircleShape)
                                )
                                Text(
                                    name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "($count · $pct%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ── Avertissement GPS mauvais (discret) ──
            val poorGpsTiges = remember(geoTiges) {
                geoTiges.count { (t, _, _) -> (t.precisionM ?: 0.0) > 20.0 }
            }
            AnimatedVisibility(
                visible = poorGpsTiges > 0 && !dismissedGpsBanner,
                enter = fadeIn(tween(400)),
                exit = fadeOut(tween(250)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = 6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFE65100).copy(alpha = 0.82f),
                    modifier = Modifier.clickable { dismissedGpsBanner = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.GpsFixed,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            stringResource(R.string.map_gps_poor_warning, poorGpsTiges),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // ── Coordonnées au tap ──
            AnimatedVisibility(
                visible = showCoords && coordsText.isNotEmpty(),
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        coordsText,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // ── Outils droite : Zoom +/- et Nord ──
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Zoom +
                MapToolButton(
                    onClick = {
                        val map = mapLibreMap ?: return@MapToolButton
                        map.animateCamera(CameraUpdateFactory.zoomIn(), 200)
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.map_zoom_in), modifier = Modifier.size(20.dp)) }
                )
                // Zoom -
                MapToolButton(
                    onClick = {
                        val map = mapLibreMap ?: return@MapToolButton
                        map.animateCamera(CameraUpdateFactory.zoomOut(), 200)
                    },
                    icon = { Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.map_zoom_out), modifier = Modifier.size(20.dp)) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Nord / Boussole
                MapToolButton(
                    onClick = {
                        val map = mapLibreMap ?: return@MapToolButton
                        map.animateCamera(CameraUpdateFactory.bearingTo(0.0), 400)
                    },
                    icon = { Icon(Icons.Default.Explore, contentDescription = stringResource(R.string.map_north), modifier = Modifier.size(20.dp)) }
                )
            }

            // ── Tapped tree info card (top center) ──
            val currentTapped = tappedTree
            if (currentTapped != null && !navState.isActive) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 70.dp, start = 16.dp, end = 16.dp)
                        .widthIn(max = 340.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val catLabel = when (currentTapped.categorie) {
                                "DEPERISSANT" -> "\u26A0 Dépérissant"
                                "ARBRE_BIO" -> "\uD83C\uDF3F Arbre bio"
                                "MORT" -> "\uD83D\uDC80 Mort"
                                "PARASITE" -> "\uD83D\uDC1B Parasité"
                                else -> null
                            }
                            Column {
                                if (catLabel != null) {
                                    Text(catLabel, style = MaterialTheme.typography.labelSmall, color = Color(0xFFEF6C00))
                                }
                                Text(
                                    currentTapped.essenceName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = { tappedTree = null },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            currentTapped.diamCm?.let {
                                Text("\u2300 ${it.roundToInt()} cm", style = MaterialTheme.typography.bodySmall)
                            }
                            currentTapped.hauteurM?.let {
                                Text(stringResource(R.string.map_height_label, it.roundToInt()), style = MaterialTheme.typography.bodySmall)
                            }
                            currentTapped.precisionM?.let {
                                Text("\u00B1${String.format(Locale.US, "%.1f", it)} m", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        // Navigate button
                        SmallFloatingActionButton(
                            onClick = {
                                val target = TreeNavigator.Target(
                                    tigeId = "",
                                    essenceName = currentTapped.essenceName,
                                    essenceCode = currentTapped.essenceCode,
                                    diamCm = currentTapped.diamCm ?: 0.0,
                                    hauteurM = currentTapped.hauteurM,
                                    lat = currentTapped.lat,
                                    lon = currentTapped.lon
                                )
                                if (!hasLocationPermission) {
                                    locationPermission.launchPermissionRequest()
                                } else {
                                    treeNavigator.startNavigation(target)
                                    tappedTree = null
                                }
                            },
                            containerColor = Color(0xFF1565C0),
                            contentColor = Color.White,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(stringResource(R.string.nav_navigate_to), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            // ── Navigation compass overlay (top center) ──
            if (navState.isActive) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 70.dp, start = 16.dp, end = 16.dp)
                        .widthIn(max = 300.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (navState.arrived) Color(0xFF2E7D32).copy(alpha = 0.95f)
                                         else MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Close button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.nav_title),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (navState.arrived) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(
                                onClick = { treeNavigator.stopNavigation() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.nav_stop),
                                    modifier = Modifier.size(16.dp),
                                    tint = if (navState.arrived) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Target info
                        navState.target?.let { target ->
                            Text(
                                "${target.essenceName} · \u2300 ${target.diamCm.roundToInt()} cm",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (navState.arrived) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (navState.arrived) {
                            // Arrived!
                            Text(
                                stringResource(R.string.nav_arrived),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        } else {
                            // Compass
                            val relativeBearing = navState.relativeBearingDeg ?: 0f
                            val compassColor = if (navState.arrived) Color.White else Color(0xFF1565C0)

                            Box(
                                modifier = Modifier.size(120.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.size(120.dp)) {
                                    // Outer ring
                                    drawCircle(
                                        color = compassColor.copy(alpha = 0.15f),
                                        radius = size.minDimension / 2f
                                    )
                                    drawCircle(
                                        color = compassColor.copy(alpha = 0.3f),
                                        radius = size.minDimension / 2f,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                                    )

                                    // Arrow
                                    val angleRad = Math.toRadians(relativeBearing.toDouble()).toFloat()
                                    val cx = center.x
                                    val cy = center.y
                                    val arrowLen = size.minDimension / 2f - 10f

                                    val tipX = cx + arrowLen * kotlin.math.sin(angleRad)
                                    val tipY = cy - arrowLen * kotlin.math.cos(angleRad)

                                    val baseLen = 12f
                                    val leftX = cx + baseLen * kotlin.math.sin(angleRad - Math.PI.toFloat() * 0.85f)
                                    val leftY = cy - baseLen * kotlin.math.cos(angleRad - Math.PI.toFloat() * 0.85f)
                                    val rightX = cx + baseLen * kotlin.math.sin(angleRad + Math.PI.toFloat() * 0.85f)
                                    val rightY = cy - baseLen * kotlin.math.cos(angleRad + Math.PI.toFloat() * 0.85f)

                                    val path = androidx.compose.ui.graphics.Path().apply {
                                        moveTo(tipX, tipY)
                                        lineTo(leftX, leftY)
                                        lineTo(cx, cy)
                                        lineTo(rightX, rightY)
                                        close()
                                    }
                                    drawPath(path, color = compassColor)
                                }
                            }

                            // Distance
                            navState.distanceM?.let { dist ->
                                val distText = if (dist >= 1000f) {
                                    String.format(Locale.getDefault(), "%.1f km", dist / 1000f)
                                } else {
                                    String.format(Locale.getDefault(), "%.0f m", dist)
                                }
                                Text(
                                    distText,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1565C0)
                                )
                            }

                            // Accuracy
                            navState.userAccuracyM?.let { acc ->
                                Text(
                                    stringResource(R.string.nav_accuracy, String.format(Locale.getDefault(), "%.1f", acc)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ── GPS Trace control panel (bas gauche) ──
            AnimatedVisibility(
                visible = traceState.isRecording || traceState.points.isNotEmpty(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 16.dp),
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Info line
                        Text(
                            stringResource(R.string.trace_title),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (traceState.isRecording) Color(0xFF2E7D32)
                                    else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            stringResource(R.string.trace_points_count, traceState.points.size),
                            style = MaterialTheme.typography.bodySmall
                        )
                        traceState.surfaceHa?.let { ha ->
                            Text(
                                stringResource(R.string.trace_surface_ha, String.format(Locale.getDefault(), "%.4f", ha)),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                        traceState.perimeterM?.let { p ->
                            Text(
                                stringResource(R.string.trace_perimeter_m, String.format(Locale.getDefault(), "%.0f", p)),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        // Action buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (traceState.isRecording) {
                                // Add manual point
                                SmallFloatingActionButton(
                                    onClick = {
                                        val map = mapLibreMap ?: return@SmallFloatingActionButton
                                        try {
                                            val loc = map.locationComponent.lastKnownLocation
                                            if (loc != null) {
                                                gpsTracer.addManualPoint(
                                                    lat = loc.latitude,
                                                    lon = loc.longitude,
                                                    alt = if (loc.hasAltitude()) loc.altitude else null,
                                                    precisionM = loc.accuracy
                                                )
                                            }
                                        } catch (_: Throwable) {}
                                    },
                                    containerColor = Color(0xFF4CAF50),
                                    contentColor = Color.White,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.trace_add_point), modifier = Modifier.size(18.dp))
                                }
                                // Undo last point
                                SmallFloatingActionButton(
                                    onClick = { gpsTracer.undoLastPoint() },
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.trace_undo), modifier = Modifier.size(18.dp))
                                }
                                // Stop recording
                                SmallFloatingActionButton(
                                    onClick = { gpsTracer.stopRecording() },
                                    containerColor = Color(0xFFC62828),
                                    contentColor = Color.White,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.trace_stop), modifier = Modifier.size(18.dp))
                                }
                            } else if (traceState.points.isNotEmpty()) {
                                // Save trace
                                if (traceState.points.size >= 3) {
                                    SmallFloatingActionButton(
                                        onClick = {
                                            traceName = ""
                                            showTraceSaveDialog = true
                                        },
                                        containerColor = Color(0xFF2E7D32),
                                        contentColor = Color.White,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.LocationOn, contentDescription = stringResource(R.string.trace_save), modifier = Modifier.size(18.dp))
                                    }
                                }
                                // Clear trace
                                SmallFloatingActionButton(
                                    onClick = { gpsTracer.clearTrace() },
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.trace_clear), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            // ── Panneau outil de mesure (bas gauche) ──
            AnimatedVisibility(
                visible = measurePoints.isNotEmpty() || measureActive,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = 12.dp,
                        bottom = if (traceState.isRecording || traceState.points.isNotEmpty()) 170.dp else 16.dp
                    ),
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp).widthIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Titre + bascule mode
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Straighten,
                                contentDescription = null,
                                tint = Color(0xFFFF6F00),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                stringResource(R.string.measure_tool_title),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF6F00)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(MeasureMode.DISTANCE to R.string.measure_mode_distance,
                                   MeasureMode.AREA to R.string.measure_mode_area).forEach { (mode, resId) ->
                                val sel = measureMode == mode
                                Surface(
                                    onClick = { if (measureMode != mode) { measureMode = mode; measurePoints = emptyList() } },
                                    color = if (sel) Color(0xFFFF6F00) else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text(
                                        stringResource(resId),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        // Indice si aucun point
                        if (measureActive && measurePoints.isEmpty()) {
                            Text(
                                stringResource(R.string.measure_tap_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Sélecteur d'unité (affiché selon le mode)
                        if (measureMode == MeasureMode.DISTANCE) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(MeasureDistUnit.M to "m", MeasureDistUnit.KM to "km").forEach { (unit, label) ->
                                    val sel = measureDistUnit == unit
                                    Surface(
                                        onClick = { measureDistUnit = unit },
                                        color = if (sel) measureColor else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.height(22.dp)
                                    ) {
                                        Text(
                                            label,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(MeasureAreaUnit.M2 to "m²", MeasureAreaUnit.ARES to "ares", MeasureAreaUnit.HA to "ha").forEach { (unit, label) ->
                                    val sel = measureAreaUnit == unit
                                    Surface(
                                        onClick = { measureAreaUnit = unit },
                                        color = if (sel) measureColor else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.height(22.dp)
                                    ) {
                                        Text(
                                            label,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }

                        // Résultats
                        if (measurePoints.size >= 2 && measureMode == MeasureMode.DISTANCE) {
                            val dist = measurePolylineM(measurePoints)
                            val t = when (measureDistUnit) {
                                MeasureDistUnit.M  -> String.format(Locale.getDefault(), "%.1f m", dist)
                                MeasureDistUnit.KM -> String.format(Locale.getDefault(), "%.4f km", dist / 1000.0)
                            }
                            Text(
                                stringResource(R.string.measure_panel_distance, t),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = measureColor
                            )
                        }
                        if (measureMode == MeasureMode.AREA && measurePoints.size >= 3) {
                            val areaM2 = measureAreaM2(measurePoints)
                            val t = when (measureAreaUnit) {
                                MeasureAreaUnit.M2   -> String.format(Locale.getDefault(), "%.1f m²", areaM2)
                                MeasureAreaUnit.ARES -> String.format(Locale.getDefault(), "%.2f ares", areaM2 / 100.0)
                                MeasureAreaUnit.HA   -> String.format(Locale.getDefault(), "%.4f ha", areaM2 / 10_000.0)
                            }
                            Text(
                                stringResource(R.string.measure_panel_area, t),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = measureColor
                            )
                        }
                        if (measurePoints.isNotEmpty()) {
                            Text(
                                stringResource(R.string.measure_points_count, measurePoints.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Palette de couleurs
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            MEASURE_COLORS.forEach { c ->
                                val isSelected = c == measureColor
                                Box(
                                    modifier = Modifier
                                        .size(if (isSelected) 20.dp else 16.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                        .border(if (isSelected) 2.dp else 0.dp, Color.White, CircleShape)
                                        .clickable { measureColor = c }
                                )
                            }
                        }

                        // Boutons action
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (measurePoints.isNotEmpty()) {
                                SmallFloatingActionButton(
                                    onClick = { if (measurePoints.isNotEmpty()) measurePoints = measurePoints.dropLast(1) },
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.measure_undo), modifier = Modifier.size(16.dp))
                                }
                                SmallFloatingActionButton(
                                    onClick = { measurePoints = emptyList() },
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.measure_clear), modifier = Modifier.size(16.dp))
                                }
                                if (measurePoints.size >= 2) {
                                    SmallFloatingActionButton(
                                        onClick = { measureSaveName = ""; showMeasureSaveDialog = true },
                                        containerColor = Color(0xFFFF6F00),
                                        contentColor = Color.White,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Default.LocationOn, contentDescription = stringResource(R.string.measure_save), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            // Bouton mesures sauvegardées
                            SmallFloatingActionButton(
                                onClick = { showSavedMeasuresPanel = !showSavedMeasuresPanel },
                                containerColor = if (showSavedMeasuresPanel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (showSavedMeasuresPanel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.Layers, contentDescription = "Mesures sauvegardées", modifier = Modifier.size(16.dp))
                            }
                        }

                        // Panneau mesures sauvegardées
                        if (showSavedMeasuresPanel) {
                            val measureDir = remember { File(context.getExternalFilesDir(null), "measurements") }
                            val savedFiles = remember(showSavedMeasuresPanel) {
                                if (measureDir.exists()) measureDir.listFiles { f -> f.extension == "json" }?.sortedByDescending { it.lastModified() } ?: emptyList()
                                else emptyList()
                            }
                            if (savedFiles.isEmpty()) {
                                Text("Aucune mesure sauvegardée", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                                    savedFiles.forEach { file ->
                                        val raw = remember(file) { try { file.readText() } catch (_: Throwable) { "" } }
                                        val name  = remember(raw) { Regex("\"name\":\"([^\"]*)\"").find(raw)?.groupValues?.get(1) ?: file.nameWithoutExtension }
                                        val mode  = remember(raw) { if (raw.contains("\"mode\":\"AREA\"")) MeasureMode.AREA else MeasureMode.DISTANCE }
                                        val value = remember(raw) {
                                            if (mode == MeasureMode.AREA) {
                                                val ha = Regex("\"areaHa\":([\\d.E-]+)").find(raw)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                                                String.format(Locale.getDefault(), "%.4f ha", ha)
                                            } else {
                                                val m = Regex("\"distanceM\":([\\d.E-]+)").find(raw)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                                                if (m >= 1000.0) String.format(Locale.getDefault(), "%.3f km", m / 1000.0)
                                                else String.format(Locale.getDefault(), "%.1f m", m)
                                            }
                                        }
                                        Surface(
                                            onClick = {
                                                try {
                                                    val ptsStr = Regex("\"points\":\\[([^\\]]+(?:\\][^\\]]*)*?)\\],\"distanceM\"").find(raw)?.groupValues?.get(1) ?: ""
                                                    val coordPattern = Regex("\\[([\\d.E+-]+),([\\d.E+-]+)\\]")
                                                    val parsed = coordPattern.findAll(ptsStr).map { m ->
                                                        LatLng(m.groupValues[1].toDouble(), m.groupValues[2].toDouble())
                                                    }.toList()
                                                    if (parsed.isNotEmpty()) {
                                                        measureMode = mode
                                                        measurePoints = parsed
                                                        showSavedMeasuresPanel = false
                                                    }
                                                } catch (_: Throwable) {}
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    if (mode == MeasureMode.AREA) Icons.Default.Map else Icons.Default.Straighten,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = Color(0xFFFF6F00)
                                                )
                                                Column(Modifier.weight(1f)) {
                                                    Text(name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                                    Text(value, style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF6F00))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── FABs principaux (bas droite) ──
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Outil de mesure
                SmallFloatingActionButton(
                    onClick = {
                        measureActive = !measureActive
                        if (!measureActive) measurePoints = emptyList()
                    },
                    containerColor = if (measureActive) Color(0xFFFF6F00) else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (measureActive) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Straighten, contentDescription = stringResource(R.string.measure_tool_title))
                }
                // Ma position
                SmallFloatingActionButton(
                    onClick = {
                        val map = mapLibreMap ?: return@SmallFloatingActionButton
                        if (!hasLocationPermission) {
                            locationPermission.launchPermissionRequest()
                            return@SmallFloatingActionButton
                        }
                        try {
                            val loc = map.locationComponent.lastKnownLocation
                            if (loc != null) {
                                map.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(loc.latitude, loc.longitude), 18.0
                                    ), 600
                                )
                                coordsText = String.format(
                                    java.util.Locale.US,
                                    "%.6f, %.6f",
                                    loc.latitude, loc.longitude
                                )
                                showCoords = true
                            }
                        } catch (_: Throwable) {}
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.GpsFixed, contentDescription = stringResource(R.string.map_my_location))
                }
                // Start GPS trace
                if (!traceState.isRecording && traceState.points.isEmpty()) {
                    SmallFloatingActionButton(
                        onClick = {
                            if (!hasLocationPermission) {
                                locationPermission.launchPermissionRequest()
                                return@SmallFloatingActionButton
                            }
                            gpsTracer.startRecording()
                        },
                        containerColor = Color(0xFF2E7D32),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = stringResource(R.string.trace_start))
                    }
                }
                // Recentrer sur les arbres
                if (displayedGeoTiges.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = {
                            val map = mapLibreMap ?: return@FloatingActionButton
                            try {
                                val builder = LatLngBounds.Builder()
                                displayedGeoTiges.forEach { (_, lon, lat) -> builder.include(LatLng(lat, lon)) }
                                map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100), 600)
                            } catch (_: Throwable) {}
                            showCoords = false
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Forest, contentDescription = stringResource(R.string.map_recenter))
                    }
                }
            }

            // ── Barre de progression téléchargement hors-ligne ──
            val progress = offlineProgress
            if (progress != null) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 64.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val sizeMb = String.format("%.1f", progress.completedSize / 1_048_576.0)
                            Text(
                                when {
                                    progress.isComplete && progress.error == null ->
                                        "${stringResource(R.string.offline_download_done)} (${progress.completedResources} tuiles, $sizeMb Mo)"
                                    progress.isComplete && progress.error != null ->
                                        progress.error ?: stringResource(R.string.offline_download_error)
                                    else -> stringResource(R.string.offline_downloading)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (progress.error != null && progress.isComplete) MaterialTheme.colorScheme.error
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            if (progress.isComplete) {
                                IconButton(
                                    onClick = { offlineTileManager.clearProgress() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        if (!progress.isComplete) {
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { (progress.progressPct / 100.0).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                            val sizeMbDl = String.format("%.1f", progress.completedSize / 1_048_576.0)
                            Text(
                                "${progress.completedResources}/${progress.requiredResources} tuiles · $sizeMbDl Mo (${String.format("%.0f", progress.progressPct)}%)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }

            // ── Message si aucun GPS (fermable) ──
            if (total > 0 && withGps == 0 && !dismissedGpsBanner) {
                // Distinguer : points GPS absents vs filtrés (imprécis)
                val allGeoTigesCount = geoTiges.size
                val isFilteredOut = allGeoTigesCount > 0 && mapOnlyReliableGps
                val bannerBg = if (isFilteredOut)
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.95f)
                else
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
                val bannerIcon = if (isFilteredOut)
                    MaterialTheme.colorScheme.tertiary
                else
                    MaterialTheme.colorScheme.error

                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    colors = CardDefaults.cardColors(containerColor = bannerBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Close button
                        IconButton(
                            onClick = { dismissedGpsBanner = true },
                            modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = bannerIcon
                            )
                            Text(
                                text = if (isFilteredOut)
                                    stringResource(R.string.map_gps_filtered_out, allGeoTigesCount, mapReliableGpsThresholdM.toInt())
                                else
                                    stringResource(R.string.map_no_gps_data),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isFilteredOut) MaterialTheme.colorScheme.onTertiaryContainer
                                       else MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // ── Message si aucune tige (fermable) ──
            if (total == 0 && !dismissedGpsBanner) {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        IconButton(
                            onClick = { dismissedGpsBanner = true },
                            modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.map_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Save measure dialog ──
    if (showMeasureSaveDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showMeasureSaveDialog = false },
            title = { Text(stringResource(R.string.measure_save_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (measureMode == MeasureMode.DISTANCE && measurePoints.size >= 2) {
                        val dist = measurePolylineM(measurePoints)
                        val t = if (dist >= 1000.0) String.format(Locale.getDefault(), "%.3f km", dist / 1000.0)
                                else String.format(Locale.getDefault(), "%.1f m", dist)
                        Text(
                            stringResource(R.string.measure_panel_distance, t),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFF6F00)
                        )
                    }
                    if (measureMode == MeasureMode.AREA && measurePoints.size >= 3) {
                        val ha = measureAreaM2(measurePoints) / 10_000.0
                        val t = if (ha >= 0.01) String.format(Locale.getDefault(), "%.4f ha", ha)
                                else String.format(Locale.getDefault(), "%.0f m²", measureAreaM2(measurePoints))
                        Text(
                            stringResource(R.string.measure_panel_area, t),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFF6F00)
                        )
                    }
                    Text(
                        stringResource(R.string.measure_points_count, measurePoints.size),
                        style = MaterialTheme.typography.bodySmall
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = measureSaveName,
                        onValueChange = { measureSaveName = it },
                        label = { Text(stringResource(R.string.measure_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val pts = measurePoints
                        val mode = measureMode
                        val dist = if (mode == MeasureMode.DISTANCE) measurePolylineM(pts) else 0.0
                        val areaM2 = if (mode == MeasureMode.AREA) measureAreaM2(pts) else 0.0
                        val name = measureSaveName.trim().ifBlank {
                            "${if (mode == MeasureMode.DISTANCE) "distance" else "surface"}_${System.currentTimeMillis()}"
                        }
                        val ptsJson = pts.joinToString(",") {
                            "[${String.format(Locale.US, "%.7f", it.latitude)},${String.format(Locale.US, "%.7f", it.longitude)}]"
                        }
                        val json = """{"name":"${name.replace("\"", "\\\"")}","mode":"$mode","points":[$ptsJson],"distanceM":$dist,"areaHa":${areaM2 / 10_000.0},"timestamp":${System.currentTimeMillis()}}"""
                        try {
                            val dir = File(context.getExternalFilesDir(null), "measurements")
                            dir.mkdirs()
                            File(dir, "${name}_${System.currentTimeMillis()}.json").writeText(json)
                            android.widget.Toast.makeText(context, context.getString(R.string.measure_saved), android.widget.Toast.LENGTH_SHORT).show()
                        } catch (_: Throwable) {}
                        showMeasureSaveDialog = false
                    }
                ) { Text(stringResource(R.string.measure_save)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showMeasureSaveDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // ── Save trace dialog ──
    if (showTraceSaveDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTraceSaveDialog = false },
            title = { Text(stringResource(R.string.trace_save_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    traceState.surfaceHa?.let { ha ->
                        Text(
                            stringResource(R.string.trace_surface_ha, String.format(Locale.getDefault(), "%.4f", ha)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    traceState.perimeterM?.let { p ->
                        Text(
                            stringResource(R.string.trace_perimeter_m, String.format(Locale.getDefault(), "%.0f", p)),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        stringResource(R.string.trace_points_count, traceState.points.size),
                        style = MaterialTheme.typography.bodySmall
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = traceName,
                        onValueChange = { traceName = it },
                        label = { Text(stringResource(R.string.trace_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val wkt = gpsTracer.toWktPolygon()
                        val surfHa = traceState.surfaceHa
                        if (wkt != null && parcelleRepository != null) {
                            scope.launch {
                                if (parcelleId != "none" && parcelleId != "all" && !parcelleId.startsWith("forest_")) {
                                    try {
                                        val parcelle = parcelleRepository.getParcelleById(parcelleId).first()
                                        if (parcelle != null) {
                                            parcelleRepository.updateParcelle(
                                                parcelle.copy(
                                                    shape = wkt,
                                                    surfaceHa = surfHa ?: parcelle.surfaceHa,
                                                    updatedAt = System.currentTimeMillis()
                                                )
                                            )
                                        }
                                    } catch (_: Throwable) {}
                                }
                                gpsTracer.clearTrace()
                                showTraceSaveDialog = false
                            }
                        } else {
                            gpsTracer.clearTrace()
                            showTraceSaveDialog = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.trace_save))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showTraceSaveDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * Tuile individuelle de couche dans le sélecteur.
 */
@Composable
private fun LayerChip(
    layer: MapLayerDef,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val bgColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    Card(
        modifier = Modifier
            .width(72.dp)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                layer.emoji,
                fontSize = 24.sp
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                stringResource(layer.labelResId),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Bouton outil carte (petit, rond, semi-transparent).
 */
@Composable
private fun MapToolButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        shadowElevation = 3.dp,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Box(contentAlignment = Alignment.Center) {
            icon()
        }
    }
}

/**
 * Active le composant de localisation MapLibre pour afficher le point bleu de l'utilisateur.
 */
private fun enableLocationComponent(map: MapboxMap, style: Style, context: android.content.Context) {
    try {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return

        val locationComponent = map.locationComponent
        val activationOptions = LocationComponentActivationOptions
            .builder(context, style)
            .useDefaultLocationEngine(true)
            .build()
        locationComponent.activateLocationComponent(activationOptions)
        locationComponent.isLocationComponentEnabled = true
        locationComponent.cameraMode = CameraMode.NONE
        locationComponent.renderMode = RenderMode.COMPASS
    } catch (e: Throwable) {
        Log.w(TAG, "Could not enable location component", e)
    }
}
