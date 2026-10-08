package com.pagreylabs.expiry

import org.json.JSONObject

/**
 * Local barcode-to-product catalog used as the offline fallback and cache for
 * the multi-source acquisition pipeline.
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
        ingredients: String = "",
        activeIngredients: String = "",
        manufacturer: String = "",
        dosageForm: String = "",
        quantity: String = "",
        sourceId: String = "",
        externalIds: Map<String, String> = emptyMap(),
        updatedAt: Long = System.currentTimeMillis()
    ) {
        val cleanBarcode = barcode.trim()
        val cleanName = name.trim()
        if (cleanBarcode.isBlank() || cleanName.isBlank()) return

        val current = catalog.optJSONObject(cleanBarcode) ?: JSONObject()
        val next = JSONObject().apply {
            put("name", cleanName)
            put("category", category.trim())
            put("brand", brand.trim())
            put("kind", kind.name)
            put("imageUrl", imageUrl.trim())
            put("ingredients", ingredients.trim())
            put("activeIngredients", activeIngredients.trim())
            put("manufacturer", manufacturer.trim())
            put("dosageForm", dosageForm.trim())
            put("quantity", quantity.trim())
            put("sourceId", sourceId.trim())
            put("externalIds", JSONObject(externalIds))
            put("updatedAt", updatedAt)
        }

        listOf(
            "category", "brand", "imageUrl", "ingredients", "activeIngredients",
            "manufacturer", "dosageForm", "quantity", "sourceId"
        ).forEach { field ->
            if (next.optString(field).isBlank() && current.optString(field).isNotBlank()) {
                next.put(field, current.optString(field))
            }
        }

        if (next.optString("kind") == ProductKind.UNKNOWN.name) {
            next.put("kind", current.optString("kind", ProductKind.UNKNOWN.name))
        }

        val currentExternal = current.optJSONObject("externalIds")
        if (currentExternal != null) {
            val mergedExternal = currentExternal.toMap().toMutableMap().apply {
                putAll(next.optJSONObject("externalIds")?.toMap().orEmpty())
            }
            next.put("externalIds", JSONObject(mergedExternal))
        }

        catalog.put(cleanBarcode, next)
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
            ingredients = entry.optString("ingredients").trim(),
            activeIngredients = entry.optString("activeIngredients").trim(),
            manufacturer = entry.optString("manufacturer").trim(),
            dosageForm = entry.optString("dosageForm").trim(),
            quantity = entry.optString("quantity").trim(),
            sourceId = entry.optString("sourceId").trim(),
            externalIds = entry.optJSONObject("externalIds")?.toMap().orEmpty()
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

    private fun JSONObject.toMap(): Map<String, String> =
        keys().asSequence().associateWith { key -> optString(key) }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 5000
    }
}
