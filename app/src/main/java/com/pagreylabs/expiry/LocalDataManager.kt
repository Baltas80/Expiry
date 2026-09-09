package com.pagreylabs.expiry

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Internal local data controls for future privacy settings and account tooling.
 * Nothing here performs network I/O.
 */
class LocalDataManager(context: Context) {
    private val appContext = context.applicationContext

    /** Returns a complete local snapshot suitable for an explicit user export. */
    fun exportSnapshot(): LocalDataSnapshot {
        val repository = ExpiryRepository(appContext)
        val rewards = RewardsRepository(appContext)
        return LocalDataSnapshot(
            products = repository.all(),
            scanHistory = repository.scanHistory(),
            outcomeHistory = repository.outcomeHistory(),
            userProfile = repository.userProfile(),
            rewardsAccount = rewards.account()
        )
    }

    /**
     * Deletes application-managed local data without contacting any server.
     * Scheduled expiry alarms are cancelled before the persisted inventory is removed.
     */
    fun deleteAllLocalData() {
        val repository = ExpiryRepository(appContext)
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        repository.all().forEach { item ->
            val intent = Intent(appContext, ExpiryAlarmReceiver::class.java).apply {
                putExtra("id", item.id)
            }
            val requestCode = (item.id xor (item.id ushr 32)).toInt()
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }

        appContext.getSharedPreferences("expiry_store", Context.MODE_PRIVATE)
            .edit().clear().apply()
        appContext.getSharedPreferences("expiry_rewards", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}

data class LocalDataSnapshot(
    val products: List<ExpiryItem>,
    val scanHistory: List<ScanEvent>,
    val outcomeHistory: List<OutcomeEvent>,
    val userProfile: ExpiryUserProfile,
    val rewardsAccount: RewardsAccount
)
