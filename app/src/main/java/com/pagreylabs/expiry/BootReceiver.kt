package com.pagreylabs.expiry

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val repository = ExpiryRepository(context.applicationContext)
        val now = System.currentTimeMillis()
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        repository.all().forEach { item ->
            val trigger = ExpiryDateUtils.reminderTrigger(item.expiryMillis, item.reminderDays)
            if (trigger > now) {
                alarm.setAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    trigger,
                    reminderPendingIntent(context, item.id)
                )
            }
        }
    }
}
