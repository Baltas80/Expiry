package com.pagreylabs.expiry

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val repository = ExpiryRepository(context.applicationContext)
        val now = System.currentTimeMillis()
        repository.all().forEach { item ->
            val trigger = ExpiryDateUtils.reminderTrigger(item.expiryMillis, item.reminderDays)
            if (trigger > now) scheduleReminder(context, item)
        }
    }
}
