package com.pagreylabs.expiry

/** Internal data-lifecycle rules. No network operation is performed here. */
object DataLifecycle {
    const val RETENTION_YEARS = 5

    /** True only while an explicit synchronization consent is active and unexpired. */
    fun cloudSyncAllowed(profile: ExpiryUserProfile, now: Long = System.currentTimeMillis()): Boolean =
        profile.syncConsent &&
            profile.consentAcceptedAt != null &&
            profile.retentionUntil != null &&
            now < profile.retentionUntil

    /** Future transports must refuse cloud export after the consent retention window. */
    fun requireCloudSyncAllowed(profile: ExpiryUserProfile, now: Long = System.currentTimeMillis()) {
        check(cloudSyncAllowed(profile, now)) { "Cloud synchronization is not authorized or retention has expired" }
    }

    /** Consumption and waste are intentionally never eligible for cloud synchronization. */
    fun isCloudEligibleData(dataType: String): Boolean = when (dataType) {
        "products", "scanHistory", "userProfile" -> true
        "outcomeHistory", "consumption", "waste" -> false
        else -> false
    }
}
