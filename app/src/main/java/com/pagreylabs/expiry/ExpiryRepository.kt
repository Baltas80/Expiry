package com.pagreylabs.expiry

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

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
        rememberProduct(item.barcode, item.name, item.category)
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
        rememberProduct(item.barcode, item.name, item.category)
    }

    fun outcomeHistory(): List<OutcomeEvent> {
        val raw = prefs.getString("outcome_history", "[]") ?: "[]"
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(OutcomeEvent(
                    o.getLong("itemId"), o.optString("barcode"), o.optString("name", "Producto"),
                    o.optString("category"),
                    runCatching { OutcomeType.valueOf(o.optString("outcome")) }.getOrDefault(OutcomeType.CONSUMED),
                    o.getLong("timestamp")
                ))
            }
        }
    }

    /** Creates a stable anonymous installation identifier locally. */
    fun userId(): String = prefs.getString("user_id", null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString("user_id", it).apply()
    }

    fun userProfile(): ExpiryUserProfile {
        val raw = prefs.getString("user_profile", null)
        if (raw.isNullOrBlank()) {
            val profile = ExpiryUserProfile(userId = userId(), timezone = java.util.TimeZone.getDefault().id)
            saveUserProfile(profile)
            return profile
        }
        val o = JSONObject(raw)
        return ExpiryUserProfile(
            userId = o.optString("userId", userId()),
            displayName = o.optString("displayName"), email = o.optString("email"), phone = o.optString("phone"),
            birthDate = o.optString("birthDate"), country = o.optString("country"), city = o.optString("city"),
            postalCode = o.optString("postalCode"), language = o.optString("language", "es"),
            timezone = o.optString("timezone", java.util.TimeZone.getDefault().id),
            marketingConsent = o.optBoolean("marketingConsent"),
            analyticsConsent = o.optBoolean("analyticsConsent"), syncConsent = o.optBoolean("syncConsent"),
            consentAcceptedAt = if (o.has("consentAcceptedAt")) o.optLong("consentAcceptedAt") else null,
            retentionUntil = if (o.has("retentionUntil")) o.optLong("retentionUntil") else null
        )
    }

    /**
     * Records the five-year retention period when synchronization consent is accepted.
     * The period is calendar-based, so leap years are handled correctly.
     */
    fun saveUserProfile(profile: ExpiryUserProfile) {
        val normalized = if (profile.syncConsent && profile.consentAcceptedAt == null) {
            val acceptedAt = System.currentTimeMillis()
            profile.copy(
                consentAcceptedAt = acceptedAt,
                retentionUntil = fiveYearsAfter(acceptedAt)
            )
        } else if (!profile.syncConsent) {
            profile.copy(consentAcceptedAt = null, retentionUntil = null)
        } else {
            profile
        }
        prefs.edit().putString("user_id", normalized.userId).putString("user_profile", normalized.toJson().toString()).apply()
    }

    /** Server-ready export. Consumption history is intentionally excluded. */
    fun cloudSnapshot(): ExpiryCloudSnapshot = ExpiryCloudSnapshot(
        user = userProfile(), products = all(), scanHistory = scanHistory()
    )

    private fun fiveYearsAfter(timestamp: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            add(Calendar.YEAR, RETENTION_YEARS)
        }.timeInMillis
    }

    companion object {
        private const val MAX_SCAN_HISTORY = 500
        private const val MAX_OUTCOME_HISTORY = 500
        private const val RETENTION_YEARS = 5
    }
}

data class CatalogProduct(val name: String, val category: String)

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
