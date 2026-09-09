package com.pagreylabs.expiry

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** Local-only ledger for the future rewards system. Network synchronization is intentionally absent. */
class RewardsRepository(context: Context) : RewardsEngine {
    private val prefs = context.getSharedPreferences("expiry_rewards", Context.MODE_PRIVATE)

    fun account(): RewardsAccount {
        val raw = prefs.getString("ledger", "[]") ?: "[]"
        val array = JSONArray(raw)
        val entries = buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val type = runCatching {
                    RewardsEventType.valueOf(o.getString("type"))
                }.getOrDefault(RewardsEventType.ADJUSTED)
                val storedPoints = o.optInt("points", 0)
                // Backward compatibility with schema v1, where SPENT was stored positive.
                val points = if (type == RewardsEventType.SPENT || type == RewardsEventType.EXPIRED) {
                    -abs(storedPoints)
                } else {
                    storedPoints
                }
                add(RewardsLedgerEntry(
                    id = o.getLong("id"),
                    type = type,
                    points = points,
                    reason = o.optString("reason"),
                    timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                    campaignId = o.optString("campaignId").ifBlank { null }
                ))
            }
        }
        return RewardsAccount(
            pointsBalance = entries.sumOf { it.points },
            lifetimeEarned = entries.filter { it.points > 0 }.sumOf { it.points },
            lifetimeSpent = entries.filter { it.points < 0 }.sumOf { abs(it.points) },
            ledger = entries
        )
    }

    override fun addPoints(points: Int, reason: String, campaignId: String?): RewardsAccount {
        require(points > 0) { "Points must be positive" }
        append(RewardsLedgerEntry(nextId(), RewardsEventType.EARNED, points, reason, System.currentTimeMillis(), campaignId))
        return account()
    }

    override fun adjustPoints(delta: Int, reason: String): RewardsAccount {
        require(delta != 0) { "Adjustment must not be zero" }
        if (delta < 0) require(account().pointsBalance >= abs(delta)) { "Insufficient points" }
        append(RewardsLedgerEntry(nextId(), RewardsEventType.ADJUSTED, delta, reason))
        return account()
    }

    override fun expirePoints(points: Int, reason: String): RewardsAccount {
        require(points > 0) { "Points must be positive" }
        require(account().pointsBalance >= points) { "Insufficient points" }
        append(RewardsLedgerEntry(nextId(), RewardsEventType.EXPIRED, -points, reason))
        return account()
    }

    override fun redeem(item: RewardCatalogItem): RewardsAccount {
        require(item.active && item.pointsCost > 0) { "Reward is not redeemable" }
        val current = account()
        require(current.pointsBalance >= item.pointsCost) { "Insufficient points" }
        if (item.stock != null) require(item.stock > 0) { "Reward is out of stock" }
        append(RewardsLedgerEntry(nextId(), RewardsEventType.SPENT, -item.pointsCost, item.title, System.currentTimeMillis(), item.id))
        return account()
    }

    private fun append(entry: RewardsLedgerEntry) {
        val array = JSONArray(prefs.getString("ledger", "[]") ?: "[]")
        array.put(JSONObject().apply {
            put("id", entry.id)
            put("type", entry.type.name)
            put("points", entry.points)
            put("reason", entry.reason)
            put("timestamp", entry.timestamp)
            entry.campaignId?.let { put("campaignId", it) }
        })
        while (array.length() > MAX_LEDGER_ENTRIES) array.remove(0)
        prefs.edit().putString("ledger", array.toString()).apply()
    }

    private fun nextId(): Long {
        val next = prefs.getLong("next_ledger_id", 0L) + 1L
        prefs.edit().putLong("next_ledger_id", next).apply()
        return next
    }

    companion object {
        private const val MAX_LEDGER_ENTRIES = 1000
    }
}
