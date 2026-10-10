package com.pagreylabs.expiry

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExpiryBackupTest {
    @Test fun validatesBothSectionsBeforeRestoreCanBegin() {
        val incomplete = JSONObject().put("format", "expiry-local-backup").put("version", 1)
            .put("expiry_store", JSONObject().put("items", "[]"))
        assertThrows(Exception::class.java) { parseExpiryBackup(incomplete.toString()) }
    }
    @Test fun parsesPrimitiveAndStringSetValues() {
        val backup = JSONObject().put("format", "expiry-local-backup").put("version", 1)
            .put("expiry_store", JSONObject().put("enabled", true).put("count", 2)
                .put("tags", org.json.JSONArray().put("a").put("b")))
            .put("expiry_local_outcomes", JSONObject())
        val parsed = parseExpiryBackup(backup.toString())
        assertEquals(true, parsed.getValue("expiry_store").getValue("enabled"))
        assertEquals(2, parsed.getValue("expiry_store").getValue("count"))
        assertEquals(setOf("a", "b"), parsed.getValue("expiry_store").getValue("tags"))
    }
    @Test fun rejectsUnsupportedNestedValues() {
        val backup = JSONObject().put("format", "expiry-local-backup").put("version", 1)
            .put("expiry_store", JSONObject().put("bad", JSONObject()))
            .put("expiry_local_outcomes", JSONObject())
        assertThrows(Exception::class.java) { parseExpiryBackup(backup.toString()) }
    }
}
