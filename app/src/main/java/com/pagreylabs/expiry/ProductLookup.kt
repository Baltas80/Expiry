package com.pagreylabs.expiry

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Global barcode lookup.
 *
 * Expiry first checks its local catalog, then queries Open Food Facts' universal
 * Product Opener endpoint with product_type=all. The endpoint can resolve food,
 * beauty, pet-food and other product records across the Open* Facts instances.
 *
 * Barcode input is normalized conservatively so common GTIN representations from
 * different markets can be resolved without changing the user's original code.
 */
object ProductLookup {
    data class Result(
        val found: Boolean,
        val name: String = "",
        val category: String = "",
        val imageUrl: String = ""
    )

    fun lookup(barcode: String, callback: (Result?) -> Unit) {
        Thread {
            val result = runCatching {
                barcodeCandidates(barcode).asSequence()
                    .mapNotNull { candidate ->
                        lookupLocal(candidate) ?: lookupRemote(candidate)
                    }
                    .firstOrNull()
            }.getOrNull()
            callback(result)
        }.start()
    }

    /**
     * Keeps candidate generation deterministic and network-free for unit tests.
     *
     * We preserve the scanned value first, then add common GTIN equivalents:
     * UPC-A <-> EAN-13-with-leading-zero, 11-digit UPC payload, and zero-padded
     * GTIN-14 variants. For GS1 strings containing AI (01), the 14-digit GTIN
     * is extracted too.
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
                    if (numeric.startsWith("0")) add(numeric.substring(1))
                    add(numeric.substring(1, 13))
                }
            }
        }

        return result.toList()
    }

    private fun lookupLocal(barcode: String): Result? {
        if (barcode.isBlank()) return null
        val product = ExpiryRepository(ExpiryApplication.appContext).findProductByBarcode(barcode) ?: return null
        return Result(found = true, name = product.name, category = product.category)
    }

    private fun lookupRemote(barcode: String): Result? {
        val query = "?product_type=all&" + localizationQuery() +
            "&fields=code,product_name,abbreviated_product_name,generic_name,categories,categories_tags,image_front_url,image_url"

        val v3 = requestProduct("https://world.openfoodfacts.org/api/v3/product/" + barcode + query)
        return v3 ?: requestProduct("https://world.openfoodfacts.org/api/v2/product/" + barcode + query)
    }

    /** v3/v2 share the same result parsing; HttpURLConnection follows redirects. */
    private fun requestProduct(urlText: String): Result? {
        val url = URL(urlText)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 7000
            readTimeout = 7000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty(
                "User-Agent",
                "Expiry/1.0 (https://github.com/Baltas80/Expiry; contact via GitHub)"
            )
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (root.optInt("status", 0) != 1) return null
            productResult(root.optJSONObject("product"), urlText.substringAfter("/product/").substringBefore("?"))
        } finally {
            connection.disconnect()
        }
    }

    private fun productResult(product: JSONObject?, barcode: String): Result {
        if (product == null) return Result(found = false)

        val name = normalizeProductName(
            firstNonBlank(
                product.optString("product_name"),
                product.optString("abbreviated_product_name"),
                product.optString("generic_name")
            )
        )
        val category = firstCategory(
            product.optString("categories"),
            product.optString("categories_tags")
        )
        val imageUrl = firstNonBlank(
            product.optString("image_front_url"),
            product.optString("image_url")
        )

        val result = Result(
            found = name.isNotBlank() || category.isNotBlank(),
            name = name,
            category = category,
            imageUrl = imageUrl
        )
        if (result.found) {
            ExpiryRepository(ExpiryApplication.appContext).rememberProduct(barcode, result.name, result.category)
        }
        return result
    }

    private fun firstCategory(vararg values: String): String =
        values.asSequence()
            .flatMap { raw -> raw.split(',').asSequence().map { it.trim() } }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

    private fun localizationQuery(): String {
        val locale = Locale.getDefault()
        val language = locale.language.takeIf { it.length == 2 }.orEmpty()
        val country = locale.country.takeIf { it.length == 2 }.orEmpty()
        val tagsLanguage = language.ifBlank { "en" }
        return buildList {
            if (language.isNotBlank()) add("lc=" + language)
            if (country.isNotBlank()) add("cc=" + country)
            add("tags_lc=" + tagsLanguage)
        }.joinToString("&")
    }

    private fun normalizeProductName(value: String): String {
        if (value.isBlank()) return value
        if (!value.contains("leche", ignoreCase = true)) return value
        return value.replace(Regex("\\busted\\b", RegexOption.IGNORE_CASE), "UHT")
    }

    private fun firstNonBlank(vararg values: String): String =
        values.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
}
