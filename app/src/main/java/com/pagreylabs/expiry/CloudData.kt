package com.pagreylabs.expiry

import org.json.JSONArray
import org.json.JSONObject

/**
 * Server-ready data contract. This module deliberately performs no network I/O.
 * A future app update can attach a transport implementation without changing the
 * local storage model.
 *
 * Retention policy: data covered by an accepted synchronization consent is retained
 * for five years from the consent timestamp. Consumption/outcome data is excluded
 * from this cloud contract and remains local to the device.
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
    val syncConsent: Boolean = false,
    val consentAcceptedAt: Long? = null,
    val retentionUntil: Long? = null
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
        consentAcceptedAt?.let { put("consentAcceptedAt", it) }
        retentionUntil?.let { put("retentionUntil", it) }
    }
}

data class ExpiryCloudSnapshot(
    val schemaVersion: Int = 2,
    val user: ExpiryUserProfile,
    val products: List<ExpiryItem>,
    val scanHistory: List<ScanEvent>,
    val generatedAt: Long = System.currentTimeMillis()
) {
    /** Cloud payload deliberately contains no consumption/outcome history. */
    fun toServerJson(): JSONObject = JSONObject().apply {
        put("schemaVersion", schemaVersion)
        put("generatedAt", generatedAt)
        put("user", user.toJson())
        put("products", JSONArray().apply { products.forEach { put(it.toJson()) } })
        put("scanHistory", JSONArray().apply {
            scanHistory.forEach {
                put(JSONObject().apply {
                    put("barcode", it.barcode)
                    put("timestamp", it.timestamp)
                })
            }
        })
    }
}

interface ExpiryCloudTransport {
    /** Future implementation: upload only cloud-eligible data after explicit consent. */
    fun upload(snapshot: ExpiryCloudSnapshot, callback: (Boolean) -> Unit)
}

object DisabledCloudTransport : ExpiryCloudTransport {
    override fun upload(snapshot: ExpiryCloudSnapshot, callback: (Boolean) -> Unit) {
        callback(false)
    }
}
