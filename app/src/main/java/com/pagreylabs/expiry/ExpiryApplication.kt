package com.pagreylabs.expiry

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** Application-scoped context used by local product catalog services. */
class ExpiryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }

    companion object {
        lateinit var appContext: android.content.Context
            private set
    }
}

/** Minimal app theme; kept here so the activity and receivers share one support file. */
@Composable
fun ExpiryTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

fun reminderPendingIntent(context: Context, itemId: Long): PendingIntent {
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

fun scheduleReminder(context: Context, item: ExpiryItem) {
    val trigger = ExpiryDateUtils.reminderTrigger(item.expiryMillis, item.reminderDays)
    if (trigger <= System.currentTimeMillis()) return
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarmManager.setAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        trigger,
        reminderPendingIntent(context, item.id)
    )
}

fun cancelReminder(context: Context, itemId: Long) {
    val pendingIntent = reminderPendingIntent(context, itemId)
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarmManager.cancel(pendingIntent)
    pendingIntent.cancel()
}
