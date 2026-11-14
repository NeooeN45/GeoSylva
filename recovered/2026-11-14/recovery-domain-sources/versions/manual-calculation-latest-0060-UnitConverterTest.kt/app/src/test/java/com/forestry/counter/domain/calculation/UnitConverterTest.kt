package com.forestry.counter.domain.calculation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests du convertisseur d'unités — spec GEOSYLVA-003 §7.6.
 *
 * Couvre : conversions intra-dimension, conversions inter-dimensions
 * (rejetées), unités inconnues, formatage, compatibilité.
 */
class UnitConverterTest {

    // --- Conversions length ---

    @Test
    fun should_convert_cm_to_m() {
        assertEquals(1.5, UnitConverter.convert(150.0, UnitCatalog.Code.CM, UnitCatalog.Code.M), 1e-9)
    }

    @Test
    fun should_convert_m_to_cm() {
        assertEquals(150.0, UnitConverter.convert(1.5, UnitCatalog.Code.M, UnitCatalog.Code.CM), 1e-9)
    }

    @Test
    fun should_convert_km_to_m() {
        assertEquals(1000.0, UnitConverter.convert(1.0, UnitCatalog.Code.KM, UnitCatalog.Code.M), 1e-9)
    }

    @Test
    fun should_return_same_value_when_same_unit() {
        assertEquals(42.0, UnitConverter.convert(42.0, UnitCatalog.Code.M, UnitCatalog.Code.M), 1e-9)
    }

    // --- Conversions area ---

    @Test
    fun should_convert_ares_through_the_shared_area_reference() {
        assertEquals(100.0, UnitConverter.convert(1.0, UnitCatalog.Code.ARE, UnitCatalog.Code.M2), 1e-9)
        assertEquals(100.0, UnitConverter.convert(1.0, UnitCatalog.Code.HA, UnitCatalog.Code.ARE), 1e-9)
        assertEquals(0.01, UnitConverter.convert(1.0, UnitCatalog.Code.ARE, UnitCatalog.Code.HA), 1e-12)
        assertTrue(UnitConverter.areCompatible(UnitCatalog.Code.ARE, UnitCatalog.Code.M2))
        assertEquals("a", UnitCatalog.get(UnitCatalog.Code.ARE)?.symbol)
    }

    @Test
    fun should_convert_ha_to_m2() {
        assertEquals(10_000.0, UnitConverter.convert(1.0, UnitCatalog.Code.HA, UnitCatalog.Code.M2), 1e-9)
    }

    @Test
    fun should_convert_m2_to_ha() {
        assertEquals(1.0, UnitConverter.convert(10_000.0, UnitCatalog.Code.M2, UnitCatalog.Code.HA), 1e-9)
    }

    @Test
    fun should_convert_km2_to_ha() {
        assertEquals(100.0, UnitConverter.convert(1.0, UnitCatalog.Code.KM2, UnitCatalog.Code.HA), 1e-9)
    }

    // --- Conversions volume ---

    @Test
    fun should_convert_l_to_m3() {
        assertEquals(0.001, UnitConverter.convert(1.0, UnitCatalog.Code.L, UnitCatalog.Code.M3), 1e-9)
    }

    @Test
    fun should_convert_m3_to_l() {
        assertEquals(1000.0, UnitConverter.convert(1.0, UnitCatalog.Code.M3, UnitCatalog.Code.L), 1e-9)
    }

    // --- Conversions mass ---

    @Test
    fun should_convert_t_to_kg() {
        assertEquals(2500.0, UnitConverter.convert(2.5, UnitCatalog.Code.T, UnitCatalog.Code.KG), 1e-9)
    }

    @Test
    fun should_convert_kg_to_t() {
        assertEquals(0.5, UnitConverter.convert(500.0, UnitCatalog.Code.KG, UnitCatalog.Code.T), 1e-9)
    }

    // --- Conversions dimensionless ---

    @Test
    fun should_convert_pct_to_ratio() {
        assertEquals(0.25, UnitConverter.convert(25.0, UnitCatalog.Code.PCT, UnitCatalog.Code.RATIO), 1e-9)
    }

    @Test
    fun should_convert_ratio_to_pct() {
        assertEquals(75.0, UnitConverter.convert(0.75, UnitCatalog.Code.RATIO, UnitCatalog.Code.PCT), 1e-9)
    }

    // --- Conversions angle ---

    @Test
    fun should_convert_rad_to_deg() {
        assertEquals(180.0, UnitConverter.convert(Math.PI, UnitCatalog.Code.RAD, UnitCatalog.Code.DEG), 1e-6)
    }

    // --- Rejets inter-dimension ---

    @Test(expected = IllegalArgumentException::class)
    fun should_reject_cm_to_ha() {
        UnitConverter.convert(100.0, UnitCatalog.Code.CM, UnitCatalog.Code.HA)
    }

    @Test(expected = IllegalArgumentException::class)
    fun should_reject_m3_to_kg() {
        UnitConverter.convert(1.0, UnitCatalog.Code.M3, UnitCatalog.Code.KG)
    }

    // --- Unités inconnues ---

    @Test(expected = IllegalArgumentException::class)
    fun should_reject_unknown_from_unit() {
        UnitConverter.convert(1.0, "unknown", UnitCatalog.Code.M)
    }

    @Test(expected = IllegalArgumentException::class)
    fun should_reject_unknown_to_unit() {
        UnitConverter.convert(1.0, UnitCatalog.Code.M, "unknown")
    }

    @Test
    fun should_return_same_value_when_unknown_unit_convertOrSame() {
        assertEquals(42.0, UnitConverter.convertOrSame(42.0, "unknown", UnitCatalog.Code.M), 1e-9)
    }

    @Test
    fun should_return_same_value_when_incompatible_convertOrSame() {
        assertEquals(100.0, UnitConverter.convertOrSame(100.0, UnitCatalog.Code.CM, UnitCatalog.Code.HA), 1e-9)
    }

    // --- Compatibilité ---

    @Test
    fun should_be_compatible_same_dimension() {
        assertTrue(UnitConverter.areCompatible(UnitCatalog.Code.CM, UnitCatalog.Code.M))
    }

    @Test
    fun should_be_incompatible_different_dimension() {
        assertFalse(UnitConverter.areCompatible(UnitCatalog.Code.CM, UnitCatalog.Code.HA))
    }

    @Test
    fun should_be_incompatible_with_unknown() {
        assertFalse(UnitConverter.areCompatible(UnitCatalog.Code.CM, "unknown"))
    }

    // --- Unité de base ---

    @Test
    fun should_return_base_unit_for_length() {
        val base = UnitConverter.baseUnit(UnitCatalog.Dimension.LENGTH)
        assertEquals(UnitCatalog.Code.M, base?.code)
    }

    @Test
    fun should_return_base_unit_for_mass() {
        val base = UnitConverter.baseUnit(UnitCatalog.Dimension.MASS)
        assertEquals(UnitCatalog.Code.KG, base?.code)
    }

    @Test
    fun should_return_null_base_unit_for_unknown_dimension() {
        assertNull(UnitConverter.baseUnit("unknown"))
    }

    // --- Formatage ---

    @Test
    fun should_format_value_with_unit_symbol() {
        val formatted = UnitConverter.format(1.5, UnitCatalog.Code.M)
        assertEquals("1,50 m", formatted)
    }

    @Test
    fun should_format_with_custom_decimals() {
        val formatted = UnitConverter.format(1.5678, UnitCatalog.Code.M, 3)
        assertEquals("1,568 m", formatted)
    }

    @Test
    fun should_format_ha() {
        val formatted = UnitConverter.format(2.5, UnitCatalog.Code.HA)
        assertEquals("2,50 ha", formatted)
    }

    // --- Catalogue ---

    @Test
    fun should_find_unit_by_code() {
        val unit = UnitCatalog.get(UnitCatalog.Code.CM)
        assertEquals("cm", unit?.symbol)
        assertEquals(UnitCatalog.Dimension.LENGTH, unit?.dimension)
    }

    @Test
    fun should_return_null_for_unknown_code() {
        assertNull(UnitCatalog.get("unknown"))
    }

    @Test
    fun should_list_units_by_dimension() {
        val lengths = UnitCatalog.byDimension(UnitCatalog.Dimension.LENGTH)
        assertTrue(lengths.isNotEmpty())
        assertTrue(lengths.all { it.dimension == UnitCatalog.Dimension.LENGTH })
    }

    @Test
    fun should_have_all_units_with_valid_base_factor() {
        UnitCatalog.ALL.forEach { unit ->
            assertTrue("Unité ${unit.code} a un toBaseFactor invalide", unit.toBaseFactor > 0)
        }
    }
}
