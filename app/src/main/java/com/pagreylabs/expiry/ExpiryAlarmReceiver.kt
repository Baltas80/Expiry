package com.pagreylabs.expiry

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class ExpiryAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val itemId = intent.getLongExtra("id", Long.MIN_VALUE)
        val item = if (itemId != Long.MIN_VALUE) ExpiryRepository(context.applicationContext).get(itemId) else null

        // An item-specific alarm can outlive a deletion/edit race. Do not notify for stale alarms.
        if (itemId != Long.MIN_VALUE && item == null) return

        val name = item?.name ?: intent.getStringExtra("name") ?: context.getString(R.string.product)
        val notificationId = if (itemId != Long.MIN_VALUE) (itemId xor (itemId ushr 32)).toInt() else System.currentTimeMillis().toInt()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_expiry_notification)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.notification_text, name))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    companion object { const val CHANNEL = "expiry_alerts" }
}
