package com.pagreylabs.expiry

import org.json.JSONObject

/**
 * A user-owned inventory item plus the product metadata captured from external
 * catalogues. New fields are optional so existing installations migrate safely.
 */
data class ExpiryItem(
    val id: Long,
    val name: String,
    val category: String,
    val expiryMillis: Long,
    val reminderDays: Int = 7,
    val barcode: String = "",
    val imageUrl: String = "",
    val quantity: String = "",
    val brand: String = "",
    val manufacturer: String = "",
    val productType: String = "",
    val ingredients: String = "",
    val activeIngredients: String = "",
    val nationalCode: String = "",
    val registrationNumber: String = "",
    val sourceId: String = "",
    val sourceName: String = "",
    val sourceConfidence: Double = 0.0
) {
    fun toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("category", category)
        put("expiryMillis", expiryMillis)
        put("reminderDays", reminderDays)
        put("barcode", barcode)
        put("imageUrl", imageUrl)
        put("quantity", quantity)
        put("brand", brand)
        put("manufacturer", manufacturer)
        put("productType", productType)
        put("ingredients", ingredients)
        put("activeIngredients", activeIngredients)
        put("nationalCode", nationalCode)
        put("registrationNumber", registrationNumber)
        put("sourceId", sourceId)
        put("sourceName", sourceName)
        put("sourceConfidence", sourceConfidence)
    }

    companion object {
        fun fromJson(o: JSONObject) = ExpiryItem(
            id = o.getLong("id"),
            name = o.getString("name"),
            category = o.optString("category"),
            expiryMillis = o.getLong("expiryMillis"),
            reminderDays = o.optInt("reminderDays", 7),
            barcode = o.optString("barcode"),
            imageUrl = o.optString("imageUrl"),
            quantity = o.optString("quantity"),
            brand = o.optString("brand"),
            manufacturer = o.optString("manufacturer"),
            productType = o.optString("productType"),
            ingredients = o.optString("ingredients"),
            activeIngredients = o.optString("activeIngredients"),
            nationalCode = o.optString("nationalCode"),
            registrationNumber = o.optString("registrationNumber"),
            sourceId = o.optString("sourceId"),
            sourceName = o.optString("sourceName"),
            sourceConfidence = o.optDouble("sourceConfidence", 0.0)
        )
    }
}
