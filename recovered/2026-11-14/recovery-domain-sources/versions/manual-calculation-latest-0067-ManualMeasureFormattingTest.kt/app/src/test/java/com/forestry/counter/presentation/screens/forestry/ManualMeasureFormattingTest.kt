package com.forestry.counter.presentation.screens.forestry

import com.forestry.counter.domain.calculation.cartography.CartographicMeasureKind
import com.quintessences.geosylva.cartography.ui.measurement.MeasureDistanceUnit
import com.quintessences.geosylva.cartography.ui.measurement.MeasureAreaUnit as WorkspaceMeasureAreaUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ManualMeasureFormattingTest {
    @Test fun `French and English use the requested locale without changing the reference value`() {
        assertEquals(ManualMeasureFormattedValue("148,1", "m"), format(148.125, locale = Locale.FRANCE))
        assertEquals(ManualMeasureFormattedValue("148.1", "m"), format(148.125, locale = Locale.US))
    }

    @Test fun `kilometres convert the full reference instead of an already rounded metre label`() {
        val value = 123.456
        assertEquals("123,5", format(value)?.number)
        // Reusing 123.5 instead would incorrectly round this to 0.124 km.
        assertEquals(ManualMeasureFormattedValue("0,123", "km"),
            format(value, distance = MeasureDistanceUnit.KILOMETRES))
        assertEquals("123,5", format(value)?.number)
    }

    @Test fun `area units all convert the same unrounded square metre reference`() {
        assertEquals(ManualMeasureFormattedValue("42", "a"),
            format(4_200.0, CartographicMeasureKind.AREA, area = WorkspaceMeasureAreaUnit.ARES))
        assertEquals(ManualMeasureFormattedValue("0,42", "ha"),
            format(4_200.0, CartographicMeasureKind.AREA, area = WorkspaceMeasureAreaUnit.HECTARES))
        val squareMetres = format(4_200.0, CartographicMeasureKind.AREA, area = WorkspaceMeasureAreaUnit.SQUARE_METRES)
        assertEquals("m²", squareMetres?.unit)
        assertTrue(squareMetres?.number in setOf("4\u202F200", "4\u00A0200"))
    }

    @Test fun `unused area choice cannot change the active distance result`() {
        val metres = format(148.0)
        for (area in WorkspaceMeasureAreaUnit.entries) {
            assertEquals(metres, format(148.0, area = area))
        }
    }

    @Test fun `unused distance choice cannot change the active area result`() {
        val hectares = format(4_200.0, CartographicMeasureKind.AREA)
        for (distance in MeasureDistanceUnit.entries) {
            assertEquals(hectares, format(4_200.0, CartographicMeasureKind.AREA, distance))
        }
    }

    @Test fun `non finite and negative references are not formatted as usable results`() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -0.1)) {
            for (kind in CartographicMeasureKind.entries) assertNull(format(value, kind))
        }
    }

    @Test fun `positive results below the display quantum never become a false zero`() {
        assertEquals(ManualMeasureFormattedValue("< 0,1", "m"), format(0.01))
        assertEquals(ManualMeasureFormattedValue("< 0,001", "km"),
            format(0.01, distance = MeasureDistanceUnit.KILOMETRES))
        assertEquals(ManualMeasureFormattedValue("< 0,1", "m²"),
            format(0.01, CartographicMeasureKind.AREA, area = WorkspaceMeasureAreaUnit.SQUARE_METRES))
        assertEquals(ManualMeasureFormattedValue("< 0,01", "a"),
            format(0.01, CartographicMeasureKind.AREA, area = WorkspaceMeasureAreaUnit.ARES))
        assertEquals(ManualMeasureFormattedValue("< 0,0001", "ha"),
            format(0.01, CartographicMeasureKind.AREA, area = WorkspaceMeasureAreaUnit.HECTARES))
    }

    @Test fun `conversion underflow still keeps a positive result distinct from zero`() {
        assertEquals(ManualMeasureFormattedValue("< 0,001", "km"),
            format(Double.MIN_VALUE, distance = MeasureDistanceUnit.KILOMETRES))
    }

    @Test fun `actual zero and signed zero are consistently displayed without an invented threshold`() {
        assertEquals(ManualMeasureFormattedValue("0", "m"), format(0.0))
        assertEquals(ManualMeasureFormattedValue("0", "m"), format(-0.0))
    }

    @Test fun `fraction limits are presentation choices rather than padded precision`() {
        assertEquals("148", format(148.0)?.number)
        assertEquals("148,1", format(148.123456)?.number)
        assertEquals("1,235", format(1_234.567, distance = MeasureDistanceUnit.KILOMETRES)?.number)
        assertEquals("12,3", format(12.3456, CartographicMeasureKind.AREA,
            area = WorkspaceMeasureAreaUnit.SQUARE_METRES)?.number)
        assertEquals("1,23", format(123.456, CartographicMeasureKind.AREA,
            area = WorkspaceMeasureAreaUnit.ARES)?.number)
        assertEquals("0,0123", format(123.456, CartographicMeasureKind.AREA,
            area = WorkspaceMeasureAreaUnit.HECTARES)?.number)
    }

    private fun format(
        value: Double,
        kind: CartographicMeasureKind = CartographicMeasureKind.DISTANCE,
        distance: MeasureDistanceUnit = MeasureDistanceUnit.METRES,
        area: WorkspaceMeasureAreaUnit = WorkspaceMeasureAreaUnit.HECTARES,
        locale: Locale = Locale.FRANCE,
    ) = formatManualMeasure(value, kind, distance, area, locale)
}
