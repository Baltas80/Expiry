package com.pagreylabs.expiry

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

internal object ExpiryReminderScheduler {
    fun cancel(context: Context, ids: Iterable<Long>) {
        val app = context.applicationContext
        val alarm = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        ids.forEach { id ->
            val pending = reminderPendingIntent(app, id)
            alarm.cancel(pending)
            pending.cancel()
        }
    }

    fun rescheduleAll(context: Context) {
        val app = context.applicationContext
        val items = ExpiryRepository(app).all()
        val alarm = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        items.forEach { item ->
            val pending = reminderPendingIntent(app, item.id)
            alarm.cancel(pending)
            val trigger = ExpiryDateUtils.reminderTrigger(item.expiryMillis, item.reminderDays)
            if (trigger > now) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            else pending.cancel()
        }
    }

    fun schedule(context: Context, item: ExpiryItem) {
        val trigger = ExpiryDateUtils.reminderTrigger(item.expiryMillis, item.reminderDays)
        if (trigger <= System.currentTimeMillis()) {
            cancel(context, listOf(item.id))
            return
        }
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, reminderPendingIntent(context, item.id))
    }

    private fun reminderPendingIntent(context: Context, id: Long): PendingIntent {
        val intent = Intent(context.applicationContext, ExpiryAlarmReceiver::class.java).putExtra("id", id)
        val requestCode = (id xor (id ushr 32)).toInt()
        return PendingIntent.getBroadcast(context.applicationContext, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                try {
                    ExpiryReminderScheduler.rescheduleAll(context)
                } catch (error: IllegalStateException) {
                    Log.e("BootReceiver", "Cannot reschedule reminders because inventory data is corrupt", error)
                }
            }
        }
    }
}