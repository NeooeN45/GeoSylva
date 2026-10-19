package com.forestry.counter.domain.field

import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class ReceiptFormTest {
    private fun id()=UUID.randomUUID().toString()
    private fun line()=ReceiptLine(objectId=id(),depotId=id(),observationId=id(),observationRevision=1,
        quantity="100,25",unit=FieldQuantityUnit.APPARENT_M3,convention="Enveloppe déclarée",conventionVersion="1")
    private fun form()=ReceiptForm("TEST réception","19/10/2026 10:00","Europe/Paris",lines=listOf(line()))
    private fun rejects(block:()->Unit) { try { block();fail("Expected invalid form") } catch(_:IllegalArgumentException) {} }
    @Test fun incomplete_draft_is_retained_without_becoming_validatable() {
        val draft=ReceiptForm(lines=listOf(line().copy(quantity="2,")))
        assertEquals(draft,draft.validate(false));rejects {draft.validate(true)}
    }
    @Test fun exact_decimal_and_version_form_one_explicit_ledger() {
        val form=form().validate(true)
        assertEquals("100,25",form.lines.single().quantity)
        assertEquals("Enveloppe déclarée [v:1]",form.lines.single().ledgerConvention())
        assertNotEquals(form.lines.single().ledgerConvention(),form.lines.single().copy(conventionVersion="2").ledgerConvention())
    }
    @Test fun convention_version_suffix_cannot_alias_another_ledger() {
        rejects {form().copy(lines=listOf(line().copy(convention="Enveloppe [v:1]",conventionVersion=""))).validate(true)}
        rejects {form().copy(lines=listOf(line().copy(conventionVersion="1]"))).validate(true)}
    }
    @Test fun same_pile_cannot_be_received_twice_in_one_document() {
        val first=line()
        rejects {form().copy(lines=listOf(first,first.copy(id=id(),observationId=id()))).validate(true)}
    }
    @Test fun quantity_must_be_positive_and_piece_count_integral() {
        listOf("0","-1","NaN","1e3").forEach {q->rejects {form().copy(lines=listOf(line().copy(quantity=q))).validate(true)}}
        rejects {form().copy(lines=listOf(line().copy(quantity="1.5",unit=FieldQuantityUnit.PIECE))).validate(true)}
    }
    @Test fun daylight_saving_ambiguous_time_requires_user_resolution() {
        rejects {form().copy(observed="25/10/2026 02:30").validate(true)}
    }
}
