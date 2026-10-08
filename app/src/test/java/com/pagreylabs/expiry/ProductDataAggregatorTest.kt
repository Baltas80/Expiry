package com.pagreylabs.expiry

import org.junit.Assert.assertEquals
import org.junit.Test

class ProductDataAggregatorTest {
    @Test
    fun mergePrefersHigherPriorityFieldsAndKeepsSupplementalData() {
        val primary = ProductRecord(
            barcode = "123",
            name = "Producto A",
            category = "Medicamentos",
            kind = ProductKind.MEDICINE,
            sourceId = "aemps_cima",
            sourceLabel = "AEMPS CIMA",
            sourcePriority = 100,
            confidence = 0.99
        )
        val secondary = ProductRecord(
            barcode = "123",
            name = "Product A",
            brand = "Marca",
            imageUrl = "https://example/image.jpg",
            sourceId = "openfda_ndc",
            sourceLabel = "FDA openFDA NDC",
            sourcePriority = 80,
            confidence = 0.90
        )

        val method = ProductDataAggregator::class.java.getDeclaredMethod(
            "merge",
            List::class.java
        ).apply { isAccessible = true }

        val merged = method.invoke(ProductDataAggregator, listOf(primary, secondary)) as ProductRecord

        assertEquals("Producto A", merged.name)
        assertEquals("Marca", merged.brand)
        assertEquals("https://example/image.jpg", merged.imageUrl)
        assertEquals(ProductKind.MEDICINE, merged.kind)
    }
}
