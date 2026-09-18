package com.pagreylabs.expiry

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-wide Premium entitlement state; never exposed as a user-editable setting. */
object PremiumEntitlement {
    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    fun restoreCached(context: android.content.Context) {
        _isPremium.value = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .getBoolean(PREFS_PREMIUM, false)
    }

    internal fun setActive(context: android.content.Context, active: Boolean) {
        _isPremium.value = active
        context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(PREFS_PREMIUM, active).apply()
    }

    private const val PREFS_NAME = "expiry_entitlements"
    private const val PREFS_PREMIUM = "premium_active"
}
