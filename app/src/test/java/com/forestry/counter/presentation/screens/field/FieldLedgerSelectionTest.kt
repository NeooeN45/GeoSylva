package com.forestry.counter.presentation.screens.field

import com.forestry.counter.domain.field.*
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class FieldLedgerSelectionTest {
    private val target=FieldTarget(FieldTargetKind.WOOD_OBJECT,UUID.randomUUID().toString())
    private fun base(unit:FieldQuantityUnit=FieldQuantityUnit.APPARENT_M3,convention:String="TEST enveloppe [v:1]")=FieldEntry(
        UUID.randomUUID().toString(),target,FieldEntryForm(FieldEntryKind.INITIAL_STOCK,title="TEST",observed="19/10/2026 10:00",
            zone="Europe/Paris",quantity="100",unit=unit,convention=convention),0,"anonymous")
    @Test fun single_basis_reuses_exact_versioned_convention_without_copying_quantity() {
        val form=withUniqueFieldLedger(FieldEntryForm(FieldEntryKind.REMOVAL),listOf(base()))
        assertEquals(FieldQuantityUnit.APPARENT_M3,form.unit);assertEquals("TEST enveloppe [v:1]",form.convention)
        assertEquals("",form.quantity)
    }
    @Test fun multiple_bases_require_explicit_selection() {
        val input=FieldEntryForm(FieldEntryKind.REMOVAL)
        assertEquals(input,withUniqueFieldLedger(input,listOf(base(),base(FieldQuantityUnit.TONNE,"TEST weight"))))
    }
    @Test fun correction_and_explicit_choice_remain_untouched() {
        val original=base()
        val correction=original.form.copy(correctionOf=original.id,correctionReason="TEST")
        assertEquals(correction,withUniqueFieldLedger(correction,listOf(original)))
        val input=FieldEntryForm(FieldEntryKind.REMAINING,unit=FieldQuantityUnit.PIECE,convention="TEST manual")
        assertEquals(input,withUniqueFieldLedger(input,listOf(original)))
    }
    @Test fun superseded_initial_stock_is_not_offered_as_a_second_basis() {
        val old=base();val correction=old.copy(id=UUID.randomUUID().toString(),form=old.form.copy(convention="TEST corrected",correctionOf=old.id,correctionReason="TEST"))
        assertEquals(listOf(FieldQuantityUnit.APPARENT_M3 to "TEST corrected"),fieldLedgerChoices(listOf(old,correction)))
    }
}
