package com.pagreylabs.expiry

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local-only ledger for the future rewards system. Network synchronization is intentionally absent. */
class RewardsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("expiry_rewards", Context.MODE_PRIVATE)

    fun account(): RewardsAccount {
        val raw = prefs.getString("ledger", "[]") ?: "[]"
        val array = JSONArray(raw)
        val entries = buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(RewardsLedgerEntry(
                    id = o.getLong("id"),
                    type = runCatching { RewardsEventType.valueOf(o.getString("type")) }.getOrDefault(RewardsEventType.ADJUSTED),
                    points = o.getInt("points"),
                    reason = o.optString("reason"),
                    timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                    campaignId = o.optString("campaignId").ifBlank { null }
                ))
            }
        }
        return RewardsAccount(
            pointsBalance = entries.sumOf { if (it.type == RewardsEventType.SPENT) -kotlin.math.abs(it.points) else it.points },
            lifetimeEarned = entries.filter { it.type == RewardsEventType.EARNED }.sumOf { kotlin.math.abs(it.points) },
            lifetimeSpent = entries.filter { it.type == RewardsEventType.SPENT }.sumOf { kotlin.math.abs(it.points) },
            ledger = entries
        )
    }

    fun addPoints(points: Int, reason: String, campaignId: String? = null): RewardsAccount {
        require(points > 0)
        append(RewardsLedgerEntry(System.currentTimeMillis(), RewardsEventType.EARNED, points, reason, System.currentTimeMillis(), campaignId))
        return account()
    }

    fun redeem(item: RewardCatalogItem): RewardsAccount {
        require(item.active && item.pointsCost > 0)
        val current = account()
        require(current.pointsBalance >= item.pointsCost)
        append(RewardsLedgerEntry(System.currentTimeMillis(), RewardsEventType.SPENT, item.pointsCost, item.title, System.currentTimeMillis(), item.id))
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
        while (array.length() > 1000) array.remove(0)
        prefs.edit().putString("ledger", array.toString()).apply()
    }
}
