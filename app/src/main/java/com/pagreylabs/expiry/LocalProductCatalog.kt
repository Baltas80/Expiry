package com.pagreylabs.expiry

import org.json.JSONObject

/**
 * Small local barcode-to-product catalog with provenance and enriched metadata.
 *
 * Existing entries remain compatible: every new field is optional on read.
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
        manufacturer: String = "",
        imageUrl: String = "",
        productType: String = "",
        ingredients: String = "",
        activeIngredients: String = "",
        sourceId: String = "",
        sourceName: String = "",
        confidence: Double = 0.0,
        nationalCode: String = "",
        registrationNumber: String = "",
        updatedAt: Long = System.currentTimeMillis()
    ) {
        val cleanBarcode = barcode.trim()
        val cleanName = name.trim()
        if (cleanBarcode.isBlank() || cleanName.isBlank()) return

        catalog.put(cleanBarcode, JSONObject().apply {
            put("name", cleanName)
            put("category", category.trim())
            put("brand", brand.trim())
            put("manufacturer", manufacturer.trim())
            put("imageUrl", imageUrl.trim())
            put("productType", productType.trim())
            put("ingredients", ingredients.trim())
            put("activeIngredients", activeIngredients.trim())
            put("sourceId", sourceId.trim())
            put("sourceName", sourceName.trim())
            put("confidence", confidence)
            put("nationalCode", nationalCode.trim())
            put("registrationNumber", registrationNumber.trim())
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

        return CatalogProduct(
            name = name,
            category = entry.optString("category").trim(),
            brand = entry.optString("brand").trim(),
            manufacturer = entry.optString("manufacturer").trim(),
            imageUrl = entry.optString("imageUrl").trim(),
            productType = entry.optString("productType").trim(),
            ingredients = entry.optString("ingredients").trim(),
            activeIngredients = entry.optString("activeIngredients").trim(),
            sourceId = entry.optString("sourceId").trim(),
            sourceName = entry.optString("sourceName").trim(),
            confidence = entry.optDouble("confidence", 0.0),
            nationalCode = entry.optString("nationalCode").trim(),
            registrationNumber = entry.optString("registrationNumber").trim()
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

