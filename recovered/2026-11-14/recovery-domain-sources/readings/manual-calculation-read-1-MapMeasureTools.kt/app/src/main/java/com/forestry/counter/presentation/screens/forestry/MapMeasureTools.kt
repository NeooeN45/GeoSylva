package com.forestry.counter.presentation.screens.forestry

import com.forestry.counter.presentation.theme.EssenceFeuillu
import com.forestry.counter.presentation.theme.EssenceResineux
import com.forestry.counter.presentation.theme.MartelageEnlever
import com.forestry.counter.presentation.theme.SemanticError
import androidx.compose.ui.graphics.Color
import kotlin.math.*

/**
 * SDK-independent coordinate for the existing manual measurement editor.
 * Constructor and historical JSON deliberately keep latitude then longitude;
 * GeoJSON adapters must convert explicitly to longitude then latitude.
 * Geometry/source validation remains the responsibility of the shared service.
 */
data class MeasurePoint(val latitude: Double, val longitude: Double)

enum class MeasureMode { DISTANCE, AREA }
enum class MeasureDistUnit { M, KM }
enum class MeasureAreaUnit { M2, ARES, HA }

internal val MEASURE_COLORS = listOf(
    MartelageEnlever,
    EssenceResineux,
    EssenceFeuillu,
    Color(0xFFE91E63),
    Color(0xFF9C27B0),
    SemanticError,
    Color(0xFFFFEB3B),
    Color(0xFF00BCD4)
)

internal fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6_371_000.0
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val dPhi = Math.toRadians(lat2 - lat1)
    val dLambda = Math.toRadians(lon2 - lon1)
    val a = sin(dPhi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(dLambda / 2).pow(2)
    return R * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
}

internal fun measurePolylineM(pts: List<MeasurePoint>): Double =
    if (pts.size < 2) 0.0
    else pts.zipWithNext().sumOf { (a, b) -> haversineM(a.latitude, a.longitude, b.latitude, b.longitude) }

internal fun measureAreaM2(pts: List<MeasurePoint>): Double {
    if (pts.size < 3) return 0.0
    val R = 6_371_000.0
    val lat0 = pts[0].latitude
    val lon0 = pts[0].longitude
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
