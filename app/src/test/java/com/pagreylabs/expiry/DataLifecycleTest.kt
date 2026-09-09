package com.pagreylabs.expiry

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataLifecycleTest {
    @Test
    fun syncRequiresActiveConsentAndRetention() {
        val now = 1_000_000L
        val valid = ExpiryUserProfile(
            userId = "test",
            syncConsent = true,
            consentAcceptedAt = now - 1L,
            retentionUntil = now + 1L
        )
        assertTrue(DataLifecycle.cloudSyncAllowed(valid, now))
        assertFalse(DataLifecycle.cloudSyncAllowed(valid, now + 1L))
        assertFalse(DataLifecycle.cloudSyncAllowed(valid.copy(syncConsent = false), now))
    }

    @Test
    fun consumptionAndWasteAreNeverCloudEligible() {
        assertFalse(DataLifecycle.isCloudEligibleData("outcomeHistory"))
        assertFalse(DataLifecycle.isCloudEligibleData("consumption"))
        assertFalse(DataLifecycle.isCloudEligibleData("waste"))
        assertTrue(DataLifecycle.isCloudEligibleData("products"))
    }
}
