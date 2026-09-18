package com.pagreylabs.expiry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Process-wide Premium entitlement state; never exposed as a user-editable setting. */
object PremiumEntitlement {
    var isPremium by mutableStateOf(false)
        private set

    fun restoreCached(context: android.content.Context) {
        isPremium = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .getBoolean(PREFS_PREMIUM, false)
    }

    internal fun setActive(context: android.content.Context, active: Boolean) {
        isPremium = active
        context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(PREFS_PREMIUM, active).apply()
    }

    private const val PREFS_NAME = "expiry_entitlements"
    private const val PREFS_PREMIUM = "premium_active"
}
