package com.forestry.counter.presentation.screens.forestry

import com.forestry.counter.domain.usecase.export.SpecializedCostUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpecializedCostDraftTest {
    @Test fun `French decimal separators are captured without binary floating point`() {
        val line = draft(quantity = " 18,5 ", price = " 65,25 ").toCostLineOrNull()!!
        assertEquals("18.5", line.quantity)
        assertEquals("65.25", line.unitPriceEur)
    }

    @Test fun `missing quantity is preserved even with an explicit zero price`() {
        val line = draft(quantity = " ", price = "0").toCostLineOrNull()!!
        assertNull(line.quantity)
        assertEquals("0", line.unitPriceEur)
    }

    @Test fun `explicit zero quantity and unknown price remain distinct`() {
        val line = draft(quantity = "0", price = "").toCostLineOrNull()!!
        assertEquals("0", line.quantity)
        assertNull(line.unitPriceEur)
    }

    @Test fun `thousands separators and signed inputs are not silently reinterpreted`() {
        assertNull(draft(quantity = "1 000", price = "5").toCostLineOrNull())
        assertNull(draft(quantity = "1,000.50", price = "5").toCostLineOrNull())
        assertNull(draft(quantity = "2", price = "-5").toCostLineOrNull())
    }

    @Test fun `fixed operation uses one quantity despite another unit draft`() {
        val line = draft(quantity = "2", price = "120")
            .copy(unit = SpecializedCostUnit.FIXED_JOB).toCostLineOrNull()!!
        assertEquals("1", line.quantity)
    }

    @Test fun `invalid calendar dates and missing references disable export`() {
        assertNull(draft("2", "120").copy(priceDate = "2026-02-30").toCostLineOrNull())
        assertNull(draft("2", "120").copy(provenance = " ").toCostLineOrNull())
        assertEquals("2026-10-07", draft("2", "120")
            .copy(priceDate = "2026-10-07").toCostLineOrNull()!!.priceDate)
    }

    private fun draft(quantity: String, price: String) = SpecializedCostDraft(
        id = "TEST-ui-cost", label = "TEST operation", quantity = quantity,
        unitPriceEur = price, provenance = "TEST entered estimate",
    )
}
