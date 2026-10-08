package com.pagreylabs.expiry

import java.util.concurrent.Executors

/**
 * Backward-compatible facade used by the UI.
 *
 * The actual source federation lives in ProductSources. Keeping this facade
 * small prevents UI code from depending on individual databases.
 */
object ProductLookup {
    data class Result(
        val found: Boolean,
        val name: String = "",
        val category: String = "",
        val imageUrl: String = "",
        val brand: String = "",
        val manufacturer: String = "",
        val quantity: String = "",
        val ingredients: String = "",
        val activeIngredients: String = "",
        val productType: ProductSources.ProductType = ProductSources.ProductType.UNKNOWN,
        val nationalCode: String = "",
        val registrationNumber: String = "",
        val commercialized: Boolean? = null,
        val supplyProblem: Boolean? = null,
        val productUrl: String = "",
        val sourceId: String = "",
        val sourceName: String = "",
        val confidence: Double = 0.0
    )

    fun lookup(barcode: String, callback: (Result?) -> Unit) {
        val candidates = barcodeCandidates(barcode)
        val primary = candidates.firstOrNull() ?: return callback(null)
        Executors.newSingleThreadExecutor().execute {
            val result = runCatching {
                candidates.asSequence()
                    .mapNotNull { ProductSources.local(ExpiryApplication.appContext, it) }
                    .firstOrNull()
                    ?: ProductSources.lookupRemote(primary)
            }.getOrNull()

            if (result != null) {
                runCatching {
                    ExpiryRepository(ExpiryApplication.appContext).rememberProduct(result)
                }
            }
            callback(result?.toResult())
        }
    }

    /**
     * Deterministic barcode candidate generation shared with the capture layer.
     *
     * The original scan is preserved, followed by compacted GS1 content and
     * common UPC/EAN/GTIN equivalents. Leading zeroes are not discarded from
     * the canonical candidate list.
     */
    internal fun barcodeCandidates(barcode: String): List<String> {
        val raw = barcode.trim()
        if (raw.isBlank()) return emptyList()

        val result = linkedSetOf<String>()
        fun add(value: String) {
            val clean = value.trim()
            if (clean.isNotBlank()) result += clean
        }

        add(raw)

        val compact = raw.replace(Regex("\\s+"), "")
        if (compact != raw) add(compact)

        // A GS1 DataMatrix may contain AI (01) followed by a 14-digit GTIN.
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

        // Prefer the canonical GTIN-13/GTIN-14-looking candidate for network
        // calls, but retain the original value in the result for traceability.
        return result.toList()
    }

    private fun ProductSources.ProductData.toResult(): Result = Result(
        found = !isEmpty(),
        name = name,
        category = category,
        imageUrl = imageUrl,
        brand = brand,
        manufacturer = manufacturer,
        quantity = quantity,
        ingredients = ingredients,
        activeIngredients = activeIngredients,
        productType = productType,
        nationalCode = nationalCode,
        registrationNumber = registrationNumber,
        commercialized = commercialized,
        supplyProblem = supplyProblem,
        productUrl = productUrl,
        sourceId = sourceId,
        sourceName = sourceName,
        confidence = confidence
    )

    private fun ProductSources.ProductData.isEmpty(): Boolean =
        name.isBlank() && brand.isBlank() && manufacturer.isBlank() &&
            category.isBlank() && imageUrl.isBlank() && quantity.isBlank() &&
            ingredients.isBlank() && activeIngredients.isBlank() &&
            nationalCode.isBlank() && registrationNumber.isBlank() &&
            productUrl.isBlank()
}
