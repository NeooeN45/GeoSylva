package com.forestry.counter.presentation.screens.forestry

import org.junit.Assert.assertEquals
import org.junit.Test

class MartelageSessionControlsTest {
    @Test fun zero_and_negative_times_show_a_safe_zero() {
        assertEquals("00:00:00", formatMartelageDuration(0L))
        assertEquals("00:00:00", formatMartelageDuration(-1L))
    }
    @Test fun milliseconds_do_not_advance_the_second_early() {
        assertEquals("00:00:00", formatMartelageDuration(999L))
        assertEquals("00:00:01", formatMartelageDuration(1_000L))
    }
    @Test fun minutes_and_hours_roll_over_consistently() {
        assertEquals("00:59:59", formatMartelageDuration(3_599_000L))
        assertEquals("01:00:00", formatMartelageDuration(3_600_000L))
        assertEquals("01:02:03", formatMartelageDuration(3_723_000L))
    }
    @Test fun work_longer_than_one_day_is_not_wrapped_or_lost() {
        assertEquals("27:15:00", formatMartelageDuration(98_100_000L))
    }
    @Test fun large_saved_durations_do_not_overflow() {
        assertEquals("2562047788015:12:55", formatMartelageDuration(Long.MAX_VALUE))
    }
}
