package com.pagreylabs.expiry

import java.util.Calendar

/** Date-only helpers used for expiry/reminder calculations. */
object ExpiryDateUtils {
    const val REMINDER_HOUR = 10
    const val REMINDER_MINUTE = 0

    fun startOfToday(now: Long = System.currentTimeMillis()): Calendar =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    /** Returns calendar-day distance without assuming every day has 24 hours. */
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

    /**
     * Normalizes a selected expiry calendar date to the fixed daytime reminder hour.
     * The displayed date remains unchanged; only the internal time-of-day is normalized.
     */
    fun normalizeExpiryTime(expiryMillis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = expiryMillis
            set(Calendar.HOUR_OF_DAY, REMINDER_HOUR)
            set(Calendar.MINUTE, REMINDER_MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** Returns the reminder date at the daytime reminder hour, using calendar days. */
    fun reminderTrigger(expiryMillis: Long, reminderDays: Int): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = expiryMillis }
        calendar.add(Calendar.DAY_OF_YEAR, -reminderDays.coerceIn(0, 365))
        calendar.set(Calendar.HOUR_OF_DAY, REMINDER_HOUR)
        calendar.set(Calendar.MINUTE, REMINDER_MINUTE)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun calendarDayNumber(calendar: Calendar): Int {
        return calendar.get(Calendar.YEAR) * 366 + calendar.get(Calendar.DAY_OF_YEAR)
    }
}
