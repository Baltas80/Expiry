package com.pagreylabs.expiry

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpiryItemTest {
    @Test
    fun jsonRoundTripPreservesAllProductFields() {
        val original = ExpiryItem(
            id = 42L,
            name = "Leche UHT",
            category = "Lácteos",
            expiryMillis = 1_777_000_000_000L,
            reminderDays = 14,
            barcode = "8412345678901",
            imageUrl = "https://example.invalid/product.jpg"
        )

        val restored = ExpiryItem.fromJson(original.toJson())

        assertEquals(original, restored)
    }

    @Test
    fun legacyJsonDefaultsOptionalFieldsWithoutChangingRequiredData() {
        val json = org.json.JSONObject()
            .put("id", 7L)
            .put("name", "Pan")
            .put("expiryMillis", 1_777_000_000_000L)

        val restored = ExpiryItem.fromJson(json)

        assertEquals(7L, restored.id)
        assertEquals("Pan", restored.name)
        assertEquals("", restored.category)
        assertEquals(7, restored.reminderDays)
        assertEquals("", restored.barcode)
        assertEquals("", restored.imageUrl)
    }
}
