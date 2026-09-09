package com.pagreylabs.expiry

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restores daytime expiry reminders after reboot, app replacement or clock/time-zone changes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> rescheduleAll(context)
        }
    }

    private fun rescheduleAll(context: Context) {
        val appContext = context.applicationContext
        val repository = ExpiryRepository(appContext)
        val now = System.currentTimeMillis()
        val alarm = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        repository.all().forEach { item ->
            val pendingIntent = reminderPendingIntent(appContext, item.id)
            alarm.cancel(pendingIntent)

            val trigger = ExpiryDateUtils.reminderTrigger(item.expiryMillis, item.reminderDays)
            if (trigger > now) {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
            } else {
                pendingIntent.cancel()
            }
        }
    }
}

private fun reminderPendingIntent(context: Context, itemId: Long): PendingIntent {
    val intent = Intent(context.applicationContext, ExpiryAlarmReceiver::class.java).apply {
        putExtra("id", itemId)
    }
    val requestCode = (itemId xor (itemId ushr 32)).toInt()
    return PendingIntent.getBroadcast(
        context.applicationContext,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
