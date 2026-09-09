package com.pagreylabs.expiry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RewardsRepositoryTest {
    @Test
    fun ledgerEntriesUseSignedBalanceDeltas() {
        val earned = RewardsLedgerEntry(1L, RewardsEventType.EARNED, 500, "test")
        val spent = RewardsLedgerEntry(2L, RewardsEventType.SPENT, -200, "test")
        val expired = RewardsLedgerEntry(3L, RewardsEventType.EXPIRED, -100, "test")
        assertEquals(200, listOf(earned, spent, expired).sumOf { it.points })
    }

    @Test
    fun rewardCatalogDefaultsToInactive() {
        val item = RewardCatalogItem("test", "Test", pointsCost = 100)
        assertTrue(!item.active)
    }

    @Test
    fun rewardsSchemaIsVersionedAndDisabled() {
        assertEquals(2, RewardsConfig.SCHEMA_VERSION)
        assertTrue(!RewardsConfig.REWARDS_ENABLED)
    }
}
