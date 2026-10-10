package com.pagreylabs.expiry

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExpiryRepositoryTest {
    @Test fun parsesCompleteInventory() {
        val item = ExpiryItem(3L, "Yogur", "Lácteos", 1_800_000_000_000L)
        assertEquals(listOf(item), ExpiryRepository.parseItems(JSONArray().put(item.toJson()).toString()))
    }
    @Test fun rejectsMalformedEntryInsteadOfDroppingIt() {
        val valid = ExpiryItem(3L, "Yogur", "Lácteos", 1_800_000_000_000L).toJson()
        val invalid = JSONObject().put("id", 4L).put("name", "Sin fecha")
        assertThrows(Exception::class.java) { ExpiryRepository.parseItems(JSONArray().put(valid).put(invalid).toString()) }
    }
    @Test fun rejectsInvalidJson() {
        assertThrows(Exception::class.java) { ExpiryRepository.parseItems("{broken") }
    }
}
