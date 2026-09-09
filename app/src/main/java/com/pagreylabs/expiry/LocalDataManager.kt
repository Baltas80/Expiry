package com.pagreylabs.expiry

import android.content.Context

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

    /** Deletes application-managed local data without contacting any server. */
    fun deleteAllLocalData() {
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
