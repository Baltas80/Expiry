package com.pagreylabs.expiry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductLookupTest {
    @Test
    fun upcAProducesEan13Equivalent() {
        val candidates = ProductLookup.barcodeCandidates("012345678905")

        assertEquals("012345678905", candidates.first())
        assertTrue(candidates.contains("0012345678905"))
    }

    @Test
    fun elevenDigitPayloadProducesZeroPaddedCandidate() {
        val candidates = ProductLookup.barcodeCandidates("12345678905")

        assertEquals("12345678905", candidates.first())
        assertTrue(candidates.contains("012345678905"))
    }

    @Test
    fun gtin14ProducesBaseGtinCandidates() {
        val candidates = ProductLookup.barcodeCandidates("10012345678902")

        assertTrue(candidates.contains("0012345678902"))
        assertTrue(candidates.contains("001234567890"))
    }

    @Test
    fun gs1Ai01ExtractsProductGtinFromScannedText() {
        val candidates = ProductLookup.barcodeCandidates("(01)08412345678901(17)270101")

        assertTrue(candidates.contains("08412345678901"))
        assertEquals("(01)08412345678901(17)270101", candidates.first())
    }

    @Test
    fun whitespaceIsNormalizedWithoutDroppingOriginalValue() {
        val candidates = ProductLookup.barcodeCandidates("8414 0667 28020")

        assertEquals("8414 0667 28020", candidates.first())
        assertTrue(candidates.contains("8414066728020"))
    }

    @Test
    fun blankBarcodeProducesNoCandidates() {
        assertTrue(ProductLookup.barcodeCandidates("   ").isEmpty())
    }
}
