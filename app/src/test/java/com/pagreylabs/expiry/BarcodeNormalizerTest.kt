package com.pagreylabs.expiry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeNormalizerTest {
    @Test
    fun normalizesUpcToEanCandidate() {
        val candidates = BarcodeNormalizer.candidates("012345678905")
        assertTrue(candidates.contains("012345678905"))
        assertTrue(candidates.contains("0012345678905"))
    }

    @Test
    fun extractsSpanishNationalCodeFromNtin() {
        assertEquals(
            listOf("1234567"),
            BarcodeNormalizer.spanishNationalCodes("08470001234567")
        )
    }

    @Test
    fun extractsNdcCandidatesForTenOrElevenDigits() {
        assertEquals(
            listOf("1234567890"),
            BarcodeNormalizer.ndcCandidates("1234567890")
        )
    }
}
