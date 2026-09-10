package com.pagreylabs.expiry

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

private const val BACKUP_VERSION = 1

/** Explicit user-controlled local backup. Does not require storage permissions. */
fun exportExpiryBackup(context: Context): String {
    val prefs = context.getSharedPreferences("expiry_store", Context.MODE_PRIVATE)
    val outcomes = context.getSharedPreferences("expiry_local_outcomes", Context.MODE_PRIVATE)
    fun encode(source: android.content.SharedPreferences): JSONObject = JSONObject().apply {
        source.all.forEach { (key, value) ->
            when (value) {
                is String -> put(key, value)
                is Boolean -> put(key, value)
                is Int -> put(key, value)
                is Long -> put(key, value)
                is Float -> put(key, value.toDouble())
                is Set<*> -> put(key, JSONArray(value.filterIsInstance<String>()))
            }
        }
    }
    return JSONObject()
        .put("format", "expiry-local-backup")
        .put("version", BACKUP_VERSION)
        .put("expiry_store", encode(prefs))
        .put("expiry_local_outcomes", encode(outcomes))
        .toString(2)
}

/** Restores a backup created by exportExpiryBackup. Returns false for invalid formats. */
fun importExpiryBackup(context: Context, raw: String): Boolean = runCatching {
    val root = JSONObject(raw)
    require(root.optString("format") == "expiry-local-backup")
    require(root.optInt("version", -1) == BACKUP_VERSION)

    fun restore(name: String) {
        val target = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val editor = target.edit().clear()
        val source = root.getJSONObject(name)
        source.keys().forEach { key ->
            when (val value = source.get(key)) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Double -> editor.putLong(key, value.toLong())
                is String -> editor.putString(key, value)
                is JSONArray -> editor.putStringSet(key, buildSet { for (i in 0 until value.length()) add(value.getString(i)) })
            }
        }
        editor.apply()
    }
    restore("expiry_store")
    restore("expiry_local_outcomes")
    true
}.getOrDefault(false)

fun clearExpiryData(context: Context) {
    context.getSharedPreferences("expiry_store", Context.MODE_PRIVATE).edit().clear().apply()
    context.getSharedPreferences("expiry_local_outcomes", Context.MODE_PRIVATE).edit().clear().apply()
}
