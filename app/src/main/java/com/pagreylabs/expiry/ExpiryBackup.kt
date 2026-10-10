package com.pagreylabs.expiry

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

private const val BACKUP_VERSION = 1
private val BACKUP_SECTIONS = listOf("expiry_store", "expiry_local_outcomes")
private const val BACKUP_TAG = "ExpiryBackup"

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

private fun writePreferences(target: SharedPreferences, values: Map<String, *>) {
    val editor = target.edit().clear()
    values.forEach { (key, value) ->
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is String -> editor.putString(key, value)
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toMutableSet())
            null -> error("Null preference value")
            else -> error("Unsupported preference value")
        }
    }
    check(editor.commit()) { "Unable to persist preferences" }
}

fun importExpiryBackup(context: Context, raw: String): Boolean {
    val sections = runCatching { parseExpiryBackup(raw) }.getOrNull() ?: return false
    val preferences = BACKUP_SECTIONS.associateWith {
        context.getSharedPreferences(it, Context.MODE_PRIVATE)
    }
    val previous = preferences.mapValues { (_, prefs) ->
        prefs.all.mapValues { (_, value) ->
            if (value is Set<*>) value.filterIsInstance<String>().toMutableSet() else value
        }
    }
    val oldIds = runCatching { ExpiryRepository(context).all().map { it.id } }.getOrDefault(emptyList())

    return try {
        ExpiryReminderScheduler.cancel(context, oldIds)
        sections.forEach { (name, values) -> writePreferences(preferences.getValue(name), values) }
        ExpiryReminderScheduler.rescheduleAll(context)
        true
    } catch (error: Exception) {
        Log.e(BACKUP_TAG, "Backup restore failed; attempting to restore the previous local state", error)
        var rollbackSucceeded = true
        previous.forEach { (name, values) ->
            try {
                writePreferences(preferences.getValue(name), values)
            } catch (rollbackError: Exception) {
                rollbackSucceeded = false
                Log.e(BACKUP_TAG, "Failed to roll back preferences section $name", rollbackError)
            }
        }
        try {
            ExpiryReminderScheduler.rescheduleAll(context)
        } catch (scheduleError: Exception) {
            rollbackSucceeded = false
            Log.e(BACKUP_TAG, "Failed to restore reminder schedule after rollback", scheduleError)
        }
        if (!rollbackSucceeded) Log.e(BACKUP_TAG, "Restore rollback was incomplete; local data needs inspection")
        false
    }
}

fun clearExpiryData(context: Context) {
    val ids = runCatching { ExpiryRepository(context).all().map { it.id } }.getOrDefault(emptyList())
    ExpiryReminderScheduler.cancel(context, ids)
    context.getSharedPreferences("expiry_store", Context.MODE_PRIVATE).edit().clear().apply()
    context.getSharedPreferences("expiry_local_outcomes", Context.MODE_PRIVATE).edit().clear().apply()
}