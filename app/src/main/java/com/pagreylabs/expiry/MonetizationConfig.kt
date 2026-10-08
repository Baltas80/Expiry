package com.pagreylabs.expiry

/**
 * Monetization identifiers owned by the Expiry Play/AdMob configuration.
 *
 * These IDs are deliberately kept in one place so the next release can be
 * switched from test/unconfigured monetization to production without changing
 * the entitlement logic.
 */
object MonetizationConfig {
    /** Google Play subscription product. Create this exact ID in Play Console. */
    const val PREMIUM_NO_ADS_PRODUCT_ID = "premium_no_ads"
    const val PREMIUM_LIFETIME_PRODUCT_ID = "premium_lifetime"

    /** Recommended base-plan IDs for the subscription product. */
    const val MONTHLY_BASE_PLAN_ID = "monthly"
    const val ANNUAL_BASE_PLAN_ID = "annual"

    /**
     * Real AdMob application/unit IDs must be supplied before production ads
     * are enabled. Empty means advertising remains disabled rather than
     * attempting to initialize the SDK with an invalid identifier.
     */
    const val ADMOB_APP_ID = ""
    const val BANNER_AD_UNIT_ID = ""
}
