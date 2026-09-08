package com.pagreylabs.expiry

import org.json.JSONObject

data class ExpiryItem(
    val id: Long,
    val name: String,
    val category: String,
    val expiryMillis: Long,
    val reminderDays: Int = 7
) {
    fun toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("category", category)
        put("expiryMillis", expiryMillis)
        put("reminderDays", reminderDays)
    }

    companion object {
        fun fromJson(o: JSONObject) = ExpiryItem(
            o.getLong("id"),
            o.getString("name"),
            o.optString("category"),
            o.getLong("expiryMillis"),
            o.optInt("reminderDays", 7)
        )
    }
}
