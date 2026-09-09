package com.pagreylabs.expiry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalProductCatalogTest {
    @Test
    fun remembersAndFindsNormalizedProduct() {
        val catalog = LocalProductCatalog()

        catalog.remember(" 8414066728020 ", "  Yogur natural ", " Lácteos ", updatedAt = 100)

        assertEquals(CatalogProduct("Yogur natural", "Lácteos"), catalog.find("8414066728020"))
        assertEquals(1, catalog.size())
    }

    @Test
    fun ignoresBlankBarcodeOrName() {
        val catalog = LocalProductCatalog()

        catalog.remember("", "Producto")
        catalog.remember("123", "   ")

        assertEquals(0, catalog.size())
        assertNull(catalog.find("123"))
    }

    @Test
    fun updatingBarcodeReplacesProductWithoutGrowingCatalog() {
        val catalog = LocalProductCatalog()

        catalog.remember("123", "Producto A", "A", updatedAt = 100)
        catalog.remember("123", "Producto B", "B", updatedAt = 200)

        assertEquals(1, catalog.size())
        assertEquals(CatalogProduct("Producto B", "B"), catalog.find("123"))
    }

    @Test
    fun evictsOldestEntryWhenLimitIsReached() {
        val catalog = LocalProductCatalog(maxEntries = 2)

        catalog.remember("old", "Old", updatedAt = 100)
        catalog.remember("new", "New", updatedAt = 200)
        catalog.remember("latest", "Latest", updatedAt = 300)

        assertEquals(2, catalog.size())
        assertNull(catalog.find("old"))
        assertTrue(catalog.find("new") != null)
        assertTrue(catalog.find("latest") != null)
    }

    @Test
    fun malformedStoredJsonStartsEmpty() {
        val catalog = LocalProductCatalog("not-json")

        assertEquals(0, catalog.size())
        assertNull(catalog.find("123"))
    }
}
