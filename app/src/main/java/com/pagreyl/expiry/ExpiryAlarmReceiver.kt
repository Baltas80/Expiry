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
        val item = if (itemId != Long.MIN_VALUE) {
            ExpiryRepository(context.applicationContext).get(itemId)
        } else null
        val name = item?.name ?: intent.getStringExtra("name") ?: "Producto"
        val notificationId = if (itemId != Long.MIN_VALUE) {
            (itemId xor (itemId ushr 32)).toInt()
        } else {
            System.currentTimeMillis().toInt()
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Caducidades", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Expiry: aviso de caducidad")
            .setContentText("$name caduca pronto")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    companion object { const val CHANNEL = "expiry_alerts" }
}
