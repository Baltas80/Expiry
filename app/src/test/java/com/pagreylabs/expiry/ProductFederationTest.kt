package com.pagreylabs.expiry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductFederationTest {

    @Test
    fun extractsSpanishNationalCodeFromGs1Ai712() {
        assertEquals("1234567", ProductSources.barcodeNormalizerNationalCode("(01)08470001234562(17)281231(10)ABC(712)1234567"))
    }

    @Test
    fun extractsSpanishNationalCodeFromNtin14() {
        assertEquals("1234567", ProductSources.barcodeNormalizerNationalCode("08470001234567"))
    }

    @Test
    fun extractsSpanishNationalCodeFromRetailEan13() {
        assertEquals("1234567", ProductSources.barcodeNormalizerNationalCode("8470001234567"))
    }

    @Test
    fun ignoresNonSpanishMedicineBarcodeShapes() {
        assertEquals(null, ProductSources.barcodeNormalizerNationalCode("3017624010701"))
    }

    @Test
    fun createsUsefulBarcodeCandidatesWithoutDroppingOriginal() {
        val candidates = ProductLookup.barcodeCandidates("0034000470693")
        assertEquals("0034000470693", candidates.first())
        assertTrue(candidates.contains("034000470693"))
    }

    @Test
    fun authoritativeMedicineSourceWinsOverMoreCompleteConsumerRecord() {
        val consumer = ProductSources.ProductData(
            barcode = "8470001234567",
            productType = ProductSources.ProductType.FOOD,
            name = "Producto ambiguo",
            brand = "Marca",
            manufacturer = "Fabricante",
            category = "Categoria",
            imageUrl = "https://example.invalid/image.jpg",
            quantity = "500 g",
            ingredients = "Ingredientes",
            productUrl = "https://example.invalid/product",
            sourceId = "openfacts-universal",
            sourceName = "Open Facts universal",
            confidence = 0.72
        )
        val medicine = ProductSources.ProductData(
            barcode = "8470001234567",
            productType = ProductSources.ProductType.MEDICINE,
            name = "Medicamento oficial",
            manufacturer = "Laboratorio oficial",
            nationalCode = "1234567",
            registrationNumber = "12345",
            sourceId = "aemps-cima",
            sourceName = "AEMPS CIMA",
            confidence = 0.98
        )

        val merged = ProductSources.mergeAll(listOf(consumer, medicine))

        assertEquals(ProductSources.ProductType.MEDICINE, merged?.productType)
        assertEquals("Medicamento oficial", merged?.name)
        assertEquals("aemps-cima", merged?.sourceId)
        assertEquals("1234567", merged?.nationalCode)
        assertEquals("500 g", merged?.quantity)
    }

    @Test
    fun inventoryContainsCoreProductAndRegulatorySources() {
        val ids = ProductSources.inventory.map { it.id }.toSet()
        assertTrue(ids.contains("openfacts-universal"))
        assertTrue(ids.contains("openbeautyfacts"))
        assertTrue(ids.contains("aemps-cima"))
        assertTrue(ids.contains("gs1-verified"))
        assertTrue(ids.contains("cosing"))
    }
}
