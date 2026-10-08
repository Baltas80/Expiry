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
    fun inventoryContainsCoreProductAndRegulatorySources() {
        val ids = ProductSources.inventory.map { it.id }.toSet()
        assertTrue(ids.contains("openfacts-universal"))
        assertTrue(ids.contains("openbeautyfacts"))
        assertTrue(ids.contains("aemps-cima"))
        assertTrue(ids.contains("gs1-verified"))
        assertTrue(ids.contains("cosing"))
    }
}
