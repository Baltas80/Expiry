package com.pagreylabs.expiry

/** Internal rewards infrastructure. Not exposed in the current UI. */
object RewardsConfig {
    const val SCHEMA_VERSION = 1
    const val POINTS_PER_EURO_REFERENCE = 500
    const val REWARDS_ENABLED = false
}

enum class RewardsEventType { EARNED, SPENT, ADJUSTED, EXPIRED }

data class RewardsLedgerEntry(
    val id: Long,
    val type: RewardsEventType,
    val points: Int,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis(),
    val campaignId: String? = null
)

data class RewardCatalogItem(
    val id: String,
    val title: String,
    val description: String = "",
    val pointsCost: Int,
    val stock: Int? = null,
    val active: Boolean = false,
    val sponsorId: String? = null
)

data class RewardsAccount(
    val schemaVersion: Int = RewardsConfig.SCHEMA_VERSION,
    val pointsBalance: Int = 0,
    val lifetimeEarned: Int = 0,
    val lifetimeSpent: Int = 0,
    val ledger: List<RewardsLedgerEntry> = emptyList()
)

/** Future campaigns can map authorized, non-consumption data value to points. */
interface RewardsEngine {
    fun addPoints(points: Int, reason: String, campaignId: String? = null): RewardsAccount
    fun redeem(catalogItem: RewardCatalogItem): RewardsAccount
}
