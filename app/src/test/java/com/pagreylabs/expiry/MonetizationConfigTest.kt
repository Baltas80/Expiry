package com.pagreylabs.expiry

import org.junit.Assert.assertEquals
import org.junit.Test

class MonetizationConfigTest {
    @Test
    fun premiumProductIdIsStable() {
        assertEquals("premium_no_ads", MonetizationConfig.PREMIUM_NO_ADS_PRODUCT_ID)
    }

    @Test
    fun basePlanIdsAreStable() {
        assertEquals("monthly", MonetizationConfig.MONTHLY_BASE_PLAN_ID)
        assertEquals("annual", MonetizationConfig.ANNUAL_BASE_PLAN_ID)
    }
}
