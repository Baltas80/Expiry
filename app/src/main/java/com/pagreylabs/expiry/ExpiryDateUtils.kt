package com.pagreylabs.expiry

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

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
     * Returns the largest number of days that can be used for a reminder whose
     * trigger is still in the future. Returns -1 when no future reminder exists.
     */
    fun maxValidReminderDays(expiryMillis: Long, now: Long = System.currentTimeMillis()): Int {
        var max = -1
        for (days in 0..365) {
            if (reminderTrigger(expiryMillis, days) > now) max = days else break
        }
        return max
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
        // Convert the calendar date to a UTC midnight ordinal so leap years and
        // year boundaries are counted correctly without relying on 24-hour days.
        val utc = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        }
        return (utc.timeInMillis / MILLIS_PER_DAY).toInt()
    }

    private const val MILLIS_PER_DAY = 86_400_000L
}
