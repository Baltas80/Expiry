package com.pagreylabs.expiry

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

class ExpiryRepository(context: Context) {
    private val prefs = context.getSharedPreferences("expiry_store", Context.MODE_PRIVATE)
    private val outcomePrefs = context.getSharedPreferences("expiry_local_outcomes", Context.MODE_PRIVATE)

    fun all(): List<ExpiryItem> {
        val raw = prefs.getString("items", "[]") ?: "[]"
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) add(ExpiryItem.fromJson(array.getJSONObject(i)))
        }.sortedBy { it.expiryMillis }
    }

    fun get(id: Long): ExpiryItem? = all().firstOrNull { it.id == id }

    fun save(item: ExpiryItem) {
        val normalizedExpiry = ExpiryDateUtils.normalizeExpiryTime(item.expiryMillis)
        val maxReminderDays = ExpiryDateUtils.maxValidReminderDays(normalizedExpiry)
        val normalizedReminderDays = item.reminderDays.coerceIn(0, 365).let { requested ->
            if (maxReminderDays >= 0) requested.coerceAtMost(maxReminderDays) else 0
        }
        val normalized = item.copy(
            expiryMillis = normalizedExpiry,
            reminderDays = normalizedReminderDays
        )
        val items = all().filterNot { it.id == normalized.id } + normalized
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        prefs.edit().putString("items", array.toString()).apply()
        rememberProduct(normalized.barcode, normalized.name, normalized.category)
    }

    fun delete(id: Long) {
        val array = JSONArray()
        all().filterNot { it.id == id }.forEach { array.put(it.toJson()) }
        prefs.edit().putString("items", array.toString()).apply()
    }

    /** Keeps a local barcode-to-product mapping even after an item leaves active inventory. */
    fun rememberProduct(barcode: String, name: String, category: String = "") {
        val catalog = productCatalog()
        catalog.remember(barcode, name, category)
        prefs.edit().putString("product_catalog", catalog.toJson()).apply()
    }

    fun findProductByBarcode(barcode: String): CatalogProduct? =
        productCatalog().find(barcode)

    fun catalogSize(): Int = productCatalog().size()

    private fun productCatalog(): LocalProductCatalog =
        LocalProductCatalog(prefs.getString("product_catalog", "{}") ?: "{}")

    fun recordScan(barcode: String, timestamp: Long = System.currentTimeMillis()) {
        if (barcode.isBlank()) return
        val raw = prefs.getString("scan_history", "[]") ?: "[]"
        val array = JSONArray(raw)
        array.put(JSONObject().apply {
            put("barcode", barcode)
            put("timestamp", timestamp)
        })
        while (array.length() > MAX_SCAN_HISTORY) array.remove(0)
        prefs.edit().putString("scan_history", array.toString()).apply()
    }

    fun scanHistory(): List<ScanEvent> {
        val raw = prefs.getString("scan_history", "[]") ?: "[]"
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(ScanEvent(o.getString("barcode"), o.getLong("timestamp")))
            }
        }
    }

    fun recordOutcome(item: ExpiryItem, outcome: OutcomeType, timestamp: Long = System.currentTimeMillis()) {
        migrateLegacyOutcomeHistory()
        val raw = outcomePrefs.getString("history", "[]") ?: "[]"
        val array = JSONArray(raw)
        array.put(JSONObject().apply {
            put("id", item.id)
            put("name", item.name)
            put("category", item.category)
            put("expiryMillis", item.expiryMillis)
            put("barcode", item.barcode)
            put("outcome", outcome.name)
            put("timestamp", timestamp)
        })
        while (array.length() > MAX_OUTCOME_HISTORY) array.remove(0)
        outcomePrefs.edit().putString("history", array.toString()).apply()
    }

    fun outcomeHistory(): List<OutcomeEvent> {
        migrateLegacyOutcomeHistory()
        val raw = outcomePrefs.getString("history", "[]") ?: "[]"
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(
                    OutcomeEvent(
                        id = o.getLong("id"),
                        name = o.getString("name"),
                        category = o.optString("category"),
                        expiryMillis = o.getLong("expiryMillis"),
                        barcode = o.optString("barcode"),
                        outcome = OutcomeType.valueOf(o.getString("outcome")),
                        timestamp = o.getLong("timestamp")
                    )
                )
            }
        }
    }

    private fun migrateLegacyOutcomeHistory() {
        if (outcomePrefs.contains("history")) return
        val legacyRaw = prefs.getString("outcome_history", null) ?: return
        outcomePrefs.edit().putString("history", legacyRaw).apply()
        prefs.edit().remove("outcome_history").apply()
    }

    companion object {
        private const val MAX_SCAN_HISTORY = 500
        private const val MAX_OUTCOME_HISTORY = 500
    }
}
