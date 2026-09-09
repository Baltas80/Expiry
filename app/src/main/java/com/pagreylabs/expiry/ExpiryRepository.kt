package com.pagreylabs.expiry

import android.content.Context
import org.json.JSONArray

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
}
