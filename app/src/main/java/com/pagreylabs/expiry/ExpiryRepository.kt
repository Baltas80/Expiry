package com.pagreylabs.expiry

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ExpiryRepository(context: Context) {
    private val prefs = context.getSharedPreferences("expiry_store", Context.MODE_PRIVATE)

    fun all(): List<ExpiryItem> {
        val raw = prefs.getString("items", "[]") ?: "[]"
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) add(ExpiryItem.fromJson(array.getJSONObject(i)))
        }.sortedBy { it.expiryMillis }
    }

    fun get(id: Long): ExpiryItem? = all().firstOrNull { it.id == id }

    fun save(item: ExpiryItem) {
        val items = all().filterNot { it.id == item.id } + item
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        prefs.edit().putString("items", array.toString()).apply()
    }

    fun delete(id: Long) {
        val array = JSONArray()
        all().filterNot { it.id == id }.forEach { array.put(it.toJson()) }
        prefs.edit().putString("items", array.toString()).apply()
    }

    /** Records a local barcode scan timestamp. No scan history is uploaded. */
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
        val raw = prefs.getString("outcome_history", "[]") ?: "[]"
        val array = JSONArray(raw)
        array.put(JSONObject().apply {
            put("itemId", item.id)
            put("barcode", item.barcode)
            put("name", item.name)
            put("category", item.category)
            put("outcome", outcome.name)
            put("timestamp", timestamp)
        })
        while (array.length() > MAX_OUTCOME_HISTORY) array.remove(0)
        prefs.edit().putString("outcome_history", array.toString()).apply()
    }

    fun outcomeHistory(): List<OutcomeEvent> {
        val raw = prefs.getString("outcome_history", "[]") ?: "[]"
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(
                    OutcomeEvent(
                        o.getLong("itemId"),
                        o.optString("barcode"),
                        o.optString("name", "Producto"),
                        o.optString("category"),
                        runCatching { OutcomeType.valueOf(o.optString("outcome")) }.getOrDefault(OutcomeType.CONSUMED),
                        o.getLong("timestamp")
                    )
                )
            }
        }
    }

    companion object {
        private const val MAX_SCAN_HISTORY = 500
        private const val MAX_OUTCOME_HISTORY = 500
    }
}

data class ScanEvent(val barcode: String, val timestamp: Long)

enum class OutcomeType { CONSUMED, DISCARDED }

data class OutcomeEvent(
    val itemId: Long,
    val barcode: String,
    val name: String,
    val category: String,
    val outcome: OutcomeType,
    val timestamp: Long
)
