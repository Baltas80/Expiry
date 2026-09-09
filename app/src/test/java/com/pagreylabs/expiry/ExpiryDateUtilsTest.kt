package com.pagreylabs.expiry

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class ExpiryDateUtilsTest {
    @Test
    fun sameCalendarDateIsZeroDaysAway() {
        val now = calendar(2026, Calendar.MARCH, 29, 10)
        val expiry = calendar(2026, Calendar.MARCH, 29, 23)
        assertEquals(0, ExpiryDateUtils.daysUntil(expiry.timeInMillis, now.timeInMillis))
    }

    @Test
    fun calendarDayDistanceIsStableAcrossDstBoundary() {
        val now = calendar(2026, Calendar.MARCH, 28, 10)
        val expiry = calendar(2026, Calendar.MARCH, 30, 10)
        assertEquals(2, ExpiryDateUtils.daysUntil(expiry.timeInMillis, now.timeInMillis))
    }

    @Test
    fun reminderUsesCalendarDays() {
        val expiry = calendar(2026, Calendar.MARCH, 30, 12)
        val trigger = Calendar.getInstance().apply { timeInMillis = ExpiryDateUtils.reminderTrigger(expiry.timeInMillis, 7) }
        assertEquals(2026, trigger.get(Calendar.YEAR))
        assertEquals(Calendar.MARCH, trigger.get(Calendar.MONTH))
        assertEquals(23, trigger.get(Calendar.DAY_OF_MONTH))
    }

    private fun calendar(year: Int, month: Int, day: Int, hour: Int): Calendar =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, 0, 0)
        }
}
