package com.forestry.counter.presentation.screens.forestry

import com.forestry.counter.presentation.theme.EssenceFeuillu
import com.forestry.counter.presentation.theme.EssenceResineux
import com.forestry.counter.presentation.theme.MartelageEnlever
import com.forestry.counter.presentation.theme.SemanticError
import androidx.compose.ui.graphics.Color
import com.forestry.counter.domain.calculation.cartography.CartographicCoordinate
import com.forestry.counter.domain.calculation.cartography.CartographicMeasureCalculator
import com.forestry.counter.domain.calculation.cartography.CartographicMeasureKind
import com.forestry.counter.domain.calculation.cartography.CartographicMeasureResult

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

/** Compatibility adapters only; all geometry validation/calculation lives in the domain service. */
internal fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
    measurePolylineM(listOf(MeasurePoint(lat1, lon1), MeasurePoint(lat2, lon2)))

internal fun measurePolylineM(pts: List<MeasurePoint>): Double =
    measureReference(pts, CartographicMeasureKind.DISTANCE)

internal fun measureAreaM2(pts: List<MeasurePoint>): Double =
    measureReference(pts, CartographicMeasureKind.AREA)

private fun measureReference(pts: List<MeasurePoint>, kind: CartographicMeasureKind): Double {
    if (pts.isEmpty()) return 0.0 // Historical empty-list contract; not a valid editor result.
    val result = CartographicMeasureCalculator.calculate(kind, pts.map { CartographicCoordinate(it.latitude, it.longitude) })
    return (result as? CartographicMeasureResult.Available)?.valueSi ?: Double.NaN
}
