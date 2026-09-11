package com.pagreylabs.expiry

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudDataTest {
    @Test
    fun serverJsonContainsOnlyCloudEligibleCollections() {
        val profile = ExpiryUserProfile(
            userId = "test",
            syncConsent = true,
            consentAcceptedAt = 1_000L,
            retentionUntil = 10_000L
        )
        val snapshot = ExpiryCloudSnapshot(
            user = profile,
            products = emptyList(),
            scanHistory = emptyList(),
            generatedAt = 2_000L
        )

        val json = snapshot.toServerJson()
        assertTrue(json.has("user"))
        assertTrue(json.has("products"))
        assertTrue(json.has("scanHistory"))
        assertFalse(json.has("outcomeHistory"))
        assertFalse(json.has("consumption"))
        assertFalse(json.has("waste"))
    }

    @Test
    fun serverJsonRejectsMissingSynchronizationConsent() {
        val profile = ExpiryUserProfile(
            userId = "test",
            syncConsent = false
        )
        val snapshot = ExpiryCloudSnapshot(
            user = profile,
            products = emptyList(),
            scanHistory = emptyList()
        )

        var rejected = false
        try {
            snapshot.toServerJson()
        } catch (_: IllegalStateException) {
            rejected = true
        }
        assertTrue(rejected)
    }
}
