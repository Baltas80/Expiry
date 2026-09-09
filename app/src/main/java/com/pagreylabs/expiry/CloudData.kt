package com.pagreylabs.expiry

import org.json.JSONArray
import org.json.JSONObject

/**
 * Server-ready data contract. This module deliberately performs no network I/O.
 * A future app update can attach a transport implementation without changing the
 * local storage model.
 */
data class ExpiryUserProfile(
    val userId: String,
    val displayName: String = "",
    val email: String = "",
    val phone: String = "",
    val birthDate: String = "",
    val country: String = "",
    val city: String = "",
    val postalCode: String = "",
    val language: String = "es",
    val timezone: String = "",
    val marketingConsent: Boolean = false,
    val analyticsConsent: Boolean = false,
    val syncConsent: Boolean = false
) {
    fun toJson() = JSONObject().apply {
        put("userId", userId)
        put("displayName", displayName)
        put("email", email)
        put("phone", phone)
        put("birthDate", birthDate)
        put("country", country)
        put("city", city)
        put("postalCode", postalCode)
        put("language", language)
        put("timezone", timezone)
        put("marketingConsent", marketingConsent)
        put("analyticsConsent", analyticsConsent)
        put("syncConsent", syncConsent)
    }
}

data class ExpiryCloudSnapshot(
    val schemaVersion: Int = 1,
    val user: ExpiryUserProfile,
    val products: List<ExpiryItem>,
    val scanHistory: List<ScanEvent>,
    val outcomeHistory: List<OutcomeEvent>,
    val generatedAt: Long = System.currentTimeMillis()
) {
    /** Consumption/outcome records are included for migration compatibility only.
     * They must remain local and must not be transmitted by the future transport. */
    fun toServerJson(includeConsumption: Boolean = false): JSONObject = JSONObject().apply {
        put("schemaVersion", schemaVersion)
        put("generatedAt", generatedAt)
        put("user", user.toJson())
        put("products", JSONArray().apply { products.forEach { put(it.toJson()) } })
        put("scanHistory", JSONArray().apply {
            scanHistory.forEach { put(JSONObject().apply { put("barcode", it.barcode); put("timestamp", it.timestamp) }) }
        })
        if (includeConsumption) {
            put("outcomeHistory", JSONArray().apply {
                outcomeHistory.forEach {
                    put(JSONObject().apply {
                        put("itemId", it.itemId)
                        put("name", it.name)
                        put("barcode", it.barcode)
                        put("outcome", it.outcome.name)
                        put("timestamp", it.timestamp)
                    })
                }
            })
        }
    }
}

interface ExpiryCloudTransport {
    /** Future implementation: upload non-consumption data after explicit consent. */
    fun upload(snapshot: ExpiryCloudSnapshot, callback: (Boolean) -> Unit)
}

object DisabledCloudTransport : ExpiryCloudTransport {
    override fun upload(snapshot: ExpiryCloudSnapshot, callback: (Boolean) -> Unit) {
        callback(false)
    }
}
