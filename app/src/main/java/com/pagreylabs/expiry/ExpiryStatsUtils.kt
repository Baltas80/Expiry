package com.pagreylabs.expiry

import java.util.Calendar

/** Date-boundary helpers for statistics; uses local calendar days instead of fixed 24-hour windows. */
object ExpiryStatsUtils {
    fun isWithinLastCalendarDays(timestamp: Long, days: Int, now: Long = System.currentTimeMillis()): Boolean {
        if (days <= 0) return false
        val start = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(days - 1))
        }.timeInMillis
        return timestamp >= start && timestamp <= now
    }
}
