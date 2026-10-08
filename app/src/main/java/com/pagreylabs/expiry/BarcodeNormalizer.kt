package com.pagreylabs.expiry

/**
 * Generates deterministic barcode candidates for the product-data pipeline.
 *
 * The original scanned value is always preserved. Additional candidates cover
 * common GTIN representations and GS1 AI (01) payloads without guessing a
 * manufacturer or country.
 */
object BarcodeNormalizer {
    fun candidates(rawBarcode: String): List<String> {
        val raw = rawBarcode.trim()
        if (raw.isBlank()) return emptyList()

        val result = linkedSetOf<String>()
        fun add(value: String) {
            value.trim().takeIf { it.isNotBlank() }?.let(result::add)
        }

        add(raw)

        val compact = raw.replace(Regex("\\s+"), "")
        add(compact)

        Regex("01\\D*(\\d{14})").findAll(raw)
            .map { it.groupValues[1] }
            .forEach(::add)

        val numeric = compact.filter(Char::isDigit)
        if (numeric.length == compact.length) {
            when (numeric.length) {
                11 -> add("0$numeric")
                12 -> add("0$numeric")
                13 -> if (numeric.startsWith("0")) add(numeric.substring(1))
                14 -> {
                    add(numeric.substring(1))
                    add(numeric.substring(1, 13))
                }
            }
        }

        return result.toList()
    }

    /**
     * Spain's medicines use a 7-digit Código Nacional (CN). AEMPS documents
     * the NTIN form as 0847000 + CN; GTIN forms can also carry the CN with the
     * 847000 prefix.
     */
    fun spanishNationalCodes(rawBarcode: String): List<String> {
        val result = linkedSetOf<String>()
        candidates(rawBarcode).forEach { candidate ->
            when {
                candidate.matches(Regex("0847000\\d{7}")) ->
                    result.add(candidate.takeLast(7))
                candidate.matches(Regex("847000\\d{7}")) ->
                    result.add(candidate.takeLast(7))
                candidate.matches(Regex("\\d{7}")) ->
                    result.add(candidate)
            }

            Regex("01\\D*(\\d{14})").find(candidate)?.groupValues?.get(1)?.let { gtin ->
                if (gtin.startsWith("0847000") && gtin.length == 14) {
                    result.add(gtin.takeLast(7))
                }
            }
        }
        return result.toList()
    }

    fun ndcCandidates(rawBarcode: String): List<String> =
        candidates(rawBarcode)
            .filter { it.matches(Regex("\\d{10,11}")) }
            .distinct()
}
