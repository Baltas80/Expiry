package com.pagreylabs.expiry

import java.util.Calendar

/** Date-only helpers used for expiry/reminder calculations. */
object ExpiryDateUtils {
    private const val MILLIS_PER_DAY = 86_400_000L

    fun startOfToday(now: Long = System.currentTimeMillis()): Calendar =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    /**
     * Returns calendar-day distance, avoiding the DST bug caused by dividing
     * epoch milliseconds by 24 hours.
     */
    fun daysUntil(expiryMillis: Long, now: Long = System.currentTimeMillis()): Int {
        val today = startOfToday(now)
        val expiry = Calendar.getInstance().apply {
            timeInMillis = expiryMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendarDayNumber(expiry) - calendarDayNumber(today)
    }

    /** Keeps reminder timing aligned to the product's calendar date across DST. */
    fun reminderTrigger(expiryMillis: Long, reminderDays: Int): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = expiryMillis }
        calendar.add(Calendar.DAY_OF_YEAR, -reminderDays.coerceIn(0, 365))
        return calendar.timeInMillis
    }

    private fun calendarDayNumber(calendar: Calendar): Int {
        return (calendar.timeInMillis / MILLIS_PER_DAY).toInt()
    }
}
