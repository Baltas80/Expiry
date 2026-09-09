package com.pagreylabs.expiry

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ExpiryStatsUtilsTest {
    @Test
    fun includesTodayAndPreviousSixCalendarDays() {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 15)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val sevenDaysStart = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -6)
        }.timeInMillis
        val eightDaysStart = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -7)
        }.timeInMillis

        assertTrue(ExpiryStatsUtils.isWithinLastCalendarDays(now, 7, now))
        assertTrue(ExpiryStatsUtils.isWithinLastCalendarDays(sevenDaysStart, 7, now))
        assertFalse(ExpiryStatsUtils.isWithinLastCalendarDays(eightDaysStart - 1, 7, now))
    }
}
