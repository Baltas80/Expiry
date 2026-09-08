package com.pagreylabs.expiry

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val repository = ExpiryRepository(context.applicationContext)
        repository.all().forEach { item ->
            val trigger = item.expiryMillis - item.reminderDays * 86_400_000L
            if (trigger > System.currentTimeMillis()) {
                val alarmIntent = Intent(context, ExpiryAlarmReceiver::class.java).apply {
                    putExtra("name", item.name)
                    putExtra("id", item.id)
                }
                val requestCode = (item.id xor (item.id ushr 32)).toInt()
                val pending = android.app.PendingIntent.getBroadcast(
                    context, requestCode, alarmIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                val alarm = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                alarm.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, trigger, pending)
            }
        }
    }
}
