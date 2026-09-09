package com.pagreylabs.expiry

import org.json.JSONObject

/**
 * Small local barcode-to-product catalog.
 *
 * The repository owns persistence; this class owns catalog rules so they can be
 * tested without an Android Context.
 */
class LocalProductCatalog(
    rawJson: String = "{}",
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES
) {
    private val catalog = runCatching { JSONObject(rawJson) }.getOrElse { JSONObject() }

    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
        trimToLimit()
    }

    fun remember(barcode: String, name: String, category: String = "", updatedAt: Long = System.currentTimeMillis()) {
        val cleanBarcode = barcode.trim()
        val cleanName = name.trim()
        if (cleanBarcode.isBlank() || cleanName.isBlank()) return

        catalog.put(cleanBarcode, JSONObject().apply {
            put("name", cleanName)
            put("category", category.trim())
            put("updatedAt", updatedAt)
        })
        trimToLimit()
    }

    fun find(barcode: String): CatalogProduct? {
        val cleanBarcode = barcode.trim()
        if (cleanBarcode.isBlank()) return null
        val entry = catalog.optJSONObject(cleanBarcode) ?: return null
        val name = entry.optString("name").trim()
        if (name.isBlank()) return null
        return CatalogProduct(name, entry.optString("category").trim())
    }

    fun size(): Int = catalog.length()

    fun toJson(): String = catalog.toString()

    private fun trimToLimit() {
        while (catalog.length() > maxEntries) {
            val oldest = catalog.keys().asSequence().minByOrNull { key ->
                catalog.optJSONObject(key)?.optLong("updatedAt", Long.MIN_VALUE) ?: Long.MIN_VALUE
            } ?: break
            catalog.remove(oldest)
        }
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 5000
    }
}
