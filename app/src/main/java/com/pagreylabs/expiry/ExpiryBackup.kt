package com.pagreylabs.expiry

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

private const val BACKUP_VERSION = 1
private val BACKUP_SECTIONS = listOf("expiry_store", "expiry_local_outcomes")

fun exportExpiryBackup(context: Context): String {
    fun encode(source: SharedPreferences) = JSONObject().apply {
        source.all.forEach { (key, value) ->
            when (value) {
                is String, is Boolean, is Int, is Long -> put(key, value)
                is Float -> put(key, value.toDouble())
                is Set<*> -> put(key, JSONArray(value.filterIsInstance<String>()))
            }
        }
    }
    return JSONObject().put("format", "expiry-local-backup").put("version", BACKUP_VERSION)
        .put("expiry_store", encode(context.getSharedPreferences("expiry_store", Context.MODE_PRIVATE)))
        .put("expiry_local_outcomes", encode(context.getSharedPreferences("expiry_local_outcomes", Context.MODE_PRIVATE)))
        .toString(2)
}

internal fun parseExpiryBackup(raw: String): Map<String, Map<String, Any>> {
    val root = JSONObject(raw)
    require(root.optString("format") == "expiry-local-backup")
    require(root.optInt("version", -1) == BACKUP_VERSION)
    return BACKUP_SECTIONS.associateWith { section ->
        val source = root.getJSONObject(section)
        buildMap {
            source.keys().forEach { key ->
                val value = source.get(key)
                put(key, when (value) {
                    is Boolean, is Int, is Long, is String -> value
                    is Double -> value.toLong()
                    is JSONArray -> buildSet { for (i in 0 until value.length()) add(value.getString(i)) }
                    else -> error("Unsupported backup value")
                })
            }
        }
    }
}

fun importExpiryBackup(context: Context, raw: String): Boolean {
    val sections = runCatching { parseExpiryBackup(raw) }.getOrNull() ?: return false
    val oldIds = runCatching { ExpiryRepository(context).all().map { it.id } }.getOrDefault(emptyList())
    ExpiryReminderScheduler.cancel(context, oldIds)
    sections.forEach { (name, values) ->
        val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
        values.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toMutableSet())
            }
        }
        check(editor.commit()) { "Unable to restore $name" }
    }
    ExpiryReminderScheduler.rescheduleAll(context)
    return true
}

fun clearExpiryData(context: Context) {
    val ids = runCatching { ExpiryRepository(context).all().map { it.id } }.getOrDefault(emptyList())
    ExpiryReminderScheduler.cancel(context, ids)
    context.getSharedPreferences("expiry_store", Context.MODE_PRIVATE).edit().clear().apply()
    context.getSharedPreferences("expiry_local_outcomes", Context.MODE_PRIVATE).edit().clear().apply()
}
