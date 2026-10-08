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

    fun remember(
        barcode: String,
        name: String,
        category: String = "",
        brand: String = "",
        kind: ProductKind = ProductKind.UNKNOWN,
        imageUrl: String = "",
        sourceId: String = "",
        updatedAt: Long = System.currentTimeMillis()
    ) {
        val cleanBarcode = barcode.trim()
        val cleanName = name.trim()
        if (cleanBarcode.isBlank() || cleanName.isBlank()) return

        val current = catalog.optJSONObject(cleanBarcode) ?: JSONObject()
        catalog.put(cleanBarcode, JSONObject().apply {
            put("name", cleanName)
            put("category", category.trim())
            put("brand", brand.trim())
            put("kind", kind.name)
            put("imageUrl", imageUrl.trim())
            put("sourceId", sourceId.trim())
            put("updatedAt", updatedAt)
        }.also { next ->
            // Do not throw away fields from an earlier richer record when a
            // lower-priority source only knows the product name.
            listOf("brand", "imageUrl", "sourceId").forEach { field ->
                if (next.optString(field).isBlank() && current.optString(field).isNotBlank()) {
                    next.put(field, current.optString(field))
                }
            }
            if (next.optString("category").isBlank() && current.optString("category").isNotBlank()) {
                next.put("category", current.optString("category"))
            }
            if (next.optString("kind") == ProductKind.UNKNOWN.name) {
                next.put("kind", current.optString("kind", ProductKind.UNKNOWN.name))
            }
        })
        trimToLimit()
    }

    fun find(barcode: String): CatalogProduct? {
        val cleanBarcode = barcode.trim()
        if (cleanBarcode.isBlank()) return null
        val entry = catalog.optJSONObject(cleanBarcode) ?: return null
        val name = entry.optString("name").trim()
        if (name.isBlank()) return null

        val kind = runCatching {
            ProductKind.valueOf(entry.optString("kind", ProductKind.UNKNOWN.name))
        }.getOrDefault(ProductKind.UNKNOWN)

        return CatalogProduct(
            name = name,
            category = entry.optString("category").trim(),
            brand = entry.optString("brand").trim(),
            kind = kind,
            imageUrl = entry.optString("imageUrl").trim(),
            sourceId = entry.optString("sourceId").trim()
        )
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
