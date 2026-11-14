package com.forestry.counter.presentation.screens.forestry

import com.forestry.counter.domain.calculation.UnitCatalog
import com.forestry.counter.domain.calculation.UnitConverter
import com.forestry.counter.domain.calculation.cartography.CartographicMeasureKind
import com.quintessences.geosylva.cartography.ui.measurement.MeasureDistanceUnit
import com.quintessences.geosylva.cartography.ui.measurement.MeasureAreaUnit as WorkspaceMeasureAreaUnit
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

internal data class ManualMeasureFormattedValue(val number: String, val unit: String)

/**
 * One formatter for editor, collapsed result, save summary and unit preview.
 * Always converts the full SI value through UnitCatalog; no rounded text is read
 * back and no geometry, reference value, preference or saved record is changed.
 * Display digits are presentation choices, never a physical accuracy claim.
 */
internal fun formatManualMeasure(
    valueSi: Double,
    kind: CartographicMeasureKind,
    distanceUnit: MeasureDistanceUnit,
    areaUnit: WorkspaceMeasureAreaUnit,
    locale: Locale = Locale.getDefault(),
): ManualMeasureFormattedValue? {
    if (!valueSi.isFinite() || valueSi < 0.0) return null
    val (targetCode, decimals) = when (kind) {
        CartographicMeasureKind.DISTANCE -> when (distanceUnit) {
            MeasureDistanceUnit.METRES -> UnitCatalog.Code.M to 1
            MeasureDistanceUnit.KILOMETRES -> UnitCatalog.Code.KM to 3
        }
        CartographicMeasureKind.AREA -> when (areaUnit) {
            WorkspaceMeasureAreaUnit.SQUARE_METRES -> UnitCatalog.Code.M2 to 1
            WorkspaceMeasureAreaUnit.ARES -> UnitCatalog.Code.ARE to 2
            WorkspaceMeasureAreaUnit.HECTARES -> UnitCatalog.Code.HA to 4
        }
    }
    val referenceCode = if (kind == CartographicMeasureKind.DISTANCE) UnitCatalog.Code.M else UnitCatalog.Code.M2
    val target = UnitCatalog.get(targetCode) ?: return null
    // Normalize signed zero, which is not a distinct physical reference value.
    val converted = UnitConverter.convert(if (valueSi == 0.0) 0.0 else valueSi, referenceCode, targetCode)
    if (!converted.isFinite()) return null
    val formatter = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = decimals
        roundingMode = RoundingMode.HALF_UP
    }
    val rounded = BigDecimal.valueOf(converted).setScale(decimals, RoundingMode.HALF_UP)
    val number = if (valueSi > 0.0 && rounded.signum() == 0) {
        // A positive result smaller than the display quantum must not become a
        // fabricated zero. This also covers conversion underflow of tiny doubles.
        val quantum = BigDecimal.ONE.movePointLeft(decimals).toDouble()
        "< ${formatter.format(quantum)}"
    } else formatter.format(converted)
    return ManualMeasureFormattedValue(number, target.symbol)
}
37: * a rendered hit resolves through the dictionaries, never its label or coordinates.
51:    measurePoints: List<MeasurePoint>,
52:    measureMode: MeasureMode,
53:    measureColor: Int,
80:            label = tree.numero?.takeIf { zoom >= 17.0 }?.toString(),
104:                        labelColorHex = "#12285B", labelSize = 12.0),
105:                    label = members.size.toString(),
113:    // neighbours would invent a measured segment that the user never drew.
114:    val validMeasure = measurePoints.all { validMapCoordinate(it.longitude, it.latitude) }
116:        val coordinates = measurePoints.map { Point.fromLngLat(it.longitude, it.latitude) }
119:            val polygon = measureMode == MeasureMode.AREA && distinctCount >= 3
125:                MapFeatureKey("terrain-measure-geometry", "draft"), geometry,
126:                MapOverlayStyle(colorHex = colorHex(measureColor), lineWidth = 3.0, fillOpacity = 0.16),
131:            // Draft vertices have no persistent identity yet. Their labels are
134:                MapFeatureKey("terrain-measure-vertices", "draft-vertex-$index"), coordinate,
135:                MapOverlayStyle(colorHex = colorHex(measureColor), pointRadius = 6.0),
136:                label = vertexLabel(index), selectable = false,
234:            addStringProperty("label", buildString {
264:/** Identity comes from the rendered object, never its label or tap coordinates. */
MeasurePanels.kt
MeasureUi.kt
MeasureWorkspace.kt
MeasureWorkspaceState.kt
