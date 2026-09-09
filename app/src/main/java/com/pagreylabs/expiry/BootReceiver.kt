package com.pagreylabs.expiry

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val repository = ExpiryRepository(context.applicationContext)
        val now = System.currentTimeMillis()
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        repository.all().forEach { item ->
            val trigger = ExpiryDateUtils.reminderTrigger(item.expiryMillis, item.reminderDays)
            if (trigger > now) {
                val pendingIntent = reminderPendingIntent(context, item.id)
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
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
