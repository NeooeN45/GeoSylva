package com.forestry.counter.domain.field

import java.math.BigDecimal
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class FieldBalanceTest {
    private val target=FieldTarget(FieldTargetKind.WOOD_OBJECT,UUID.randomUUID().toString())
    private fun event(kind:FieldEntryKind,quantity:String,date:String="18/10/2026 10:00",unit:FieldQuantityUnit=FieldQuantityUnit.APPARENT_M3,convention:String="TEST pile apparente")=FieldEntry(
        UUID.randomUUID().toString(),target,FieldEntryForm(kind,title="TEST",quantity=quantity,unit=unit,convention=convention,observed=date,zone="Europe/Paris",
            remainder=if(kind==FieldEntryKind.REMAINING) FieldRemainder.PARTIAL else null).validate(target),0,"TEST")
    private fun balance(entries:List<FieldEntry>,time:String="18/10/2026 12:00")=FieldBalance.at(entries,checkNotNull(FieldEntryForm(observed=time,zone="Europe/Paris").observedInstant()),FieldQuantityUnit.APPARENT_M3,"TEST pile apparente")
    @Test fun partial_pickups_and_remaining_check_do_not_create_stock() {
        val events=listOf(event(FieldEntryKind.INITIAL_STOCK,"30"),event(FieldEntryKind.REMOVAL,"7,50","18/10/2026 11:00"),event(FieldEntryKind.REMAINING,"22","18/10/2026 12:00"))
        val b=balance(events)
        assertEquals(0,b.expected?.compareTo(BigDecimal("22.50")))
        assertEquals(0,b.difference?.compareTo(BigDecimal("-0.50")))
    }
    @Test fun controls_without_baseline_are_not_stock() {assertNull(balance(listOf(event(FieldEntryKind.REMAINING,"10"))).expected)}
    @Test fun other_units_and_conventions_are_not_added() {
        assertEquals(BigDecimal("30"),balance(listOf(event(FieldEntryKind.INITIAL_STOCK,"30"),event(FieldEntryKind.REMOVAL,"20",unit=FieldQuantityUnit.TONNE))).expected)
    }
    @Test fun overdraw_is_detected_at_intermediate_date_even_if_later_intake_restores_balance() {
        assertEquals("NEGATIVE_BALANCE",balance(listOf(event(FieldEntryKind.INITIAL_STOCK,"10"),event(FieldEntryKind.REMOVAL,"12","18/10/2026 11:00"),event(FieldEntryKind.INCOMING,"10","18/10/2026 12:00"))).reason)
    }
    @Test fun future_pickup_is_not_deducted_from_an_earlier_control() {
        assertEquals(BigDecimal("30"),balance(listOf(event(FieldEntryKind.INITIAL_STOCK,"30"),event(FieldEntryKind.REMOVAL,"12","19/10/2026 11:00"))).expected)
    }
    @Test fun corrections_replace_effect_but_preserve_original() {
        val removal=event(FieldEntryKind.REMOVAL,"7","18/10/2026 11:00")
        val correction=event(FieldEntryKind.REMOVAL,"8","18/10/2026 11:00").let {it.copy(form=it.form.copy(correctionOf=removal.id,correctionReason="TEST corrected ticket"))}
        val events=listOf(event(FieldEntryKind.INITIAL_STOCK,"30"),removal,correction)
        assertEquals(BigDecimal("22"),balance(events).expected);assertEquals(3,events.size)
    }
    @Test fun multiple_controls_at_same_time_do_not_choose_arbitrarily() {
        assertEquals("MULTIPLE_CONTROLS",balance(listOf(event(FieldEntryKind.INITIAL_STOCK,"30"),event(FieldEntryKind.REMAINING,"30","18/10/2026 12:00"),event(FieldEntryKind.REMAINING,"29","18/10/2026 12:00"))).reason)
    }
    @Test fun unknown_remaining_is_not_zero() {
        val form=FieldEntryForm(FieldEntryKind.REMAINING,title="TEST",observed="18/10/2026 12:00",zone="Europe/Paris",remainder=FieldRemainder.UNKNOWN)
        form.validate(target);assertTrue(form.quantity.isEmpty())
    }
    @Test fun observed_zero_is_accepted_with_explicit_empty_state() {
        event(FieldEntryKind.REMAINING,"1").form.copy(quantity="0,00",remainder=FieldRemainder.EMPTY).validate(target)
    }
    @Test fun management_cannot_emit_wood_stock_movements() {
        assertThrows(IllegalArgumentException::class.java) {event(FieldEntryKind.INITIAL_STOCK,"1").form.validate(FieldTarget(FieldTargetKind.PARCEL,"legacy-parcel"))}
    }
    @Test fun ambiguous_clock_time_is_not_silently_selected() {
        assertThrows(IllegalArgumentException::class.java) {FieldEntryForm(observed="25/10/2026 02:30",zone="Europe/Paris").observedInstant()}
    }
    @Test fun partial_coordinates_and_fractional_pieces_are_invalid() {
        assertThrows(IllegalArgumentException::class.java) {FieldEntryForm(title="TEST",latitude="46").validate(target)}
        assertThrows(IllegalArgumentException::class.java) {event(FieldEntryKind.INITIAL_STOCK,"1.5",unit=FieldQuantityUnit.PIECE)}
    }
}
