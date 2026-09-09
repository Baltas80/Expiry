package com.pagreylabs.expiry

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Barcode lookup with a persistent local Expiry catalog plus Open Food Facts fallback. */
object ProductLookup {
    data class Result(
        val found: Boolean,
        val name: String = "",
        val category: String = ""
    )

    fun lookup(barcode: String, callback: (Result?) -> Unit) {
        Thread {
            val cleanBarcode = barcode.trim()
            val result = runCatching {
                lookupLocal(cleanBarcode)
                    ?: lookupV3(cleanBarcode)
                    ?: lookupV2(cleanBarcode)
            }.getOrNull()
            callback(result)
        }.start()
    }

    private fun lookupLocal(barcode: String): Result? {
        if (barcode.isBlank()) return null
        val product = ExpiryRepository(ExpiryApplication.appContext).findProductByBarcode(barcode) ?: return null
        return Result(found = true, name = product.name, category = product.category)
    }

    private fun lookupV3(barcode: String): Result? {
        val url = URL(
            "https://world.openfoodfacts.org/api/v3/product/$barcode" +
                "?product_type=all&lc=es&cc=es&fields=code,product_name,product_name_es,categories,categories_tags"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 7000
            readTimeout = 7000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Expiry/0.5 (https://github.com/Baltas80/Expiry)")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (root.optInt("status", 0) != 1) return null
            productResult(root.optJSONObject("product"), barcode)
        } finally {
            connection.disconnect()
        }
    }

    /** v2 fallback for products not yet served by the current v3 route. */
    private fun lookupV2(barcode: String): Result? {
        val url = URL(
            "https://world.openfoodfacts.org/api/v2/product/$barcode" +
                "?product_type=all&lc=es&cc=es&fields=product_name,product_name_es,categories,categories_tags"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 7000
            readTimeout = 7000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Expiry/0.5 (https://github.com/Baltas80/Expiry)")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (root.optInt("status", 0) != 1) return null
            productResult(root.optJSONObject("product"), barcode)
        } finally {
            connection.disconnect()
        }
    }

    private fun productResult(product: JSONObject?, barcode: String): Result {
        if (product == null) return Result(found = false)

        val name = firstNonBlank(
            product.optString("product_name_es"),
            product.optString("product_name")
        )
        val category = product.optString("categories")
            .split(',')
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            .orEmpty()

        val result = Result(
            found = name.isNotBlank() || category.isNotBlank(),
            name = name,
            category = category
        )
        if (result.found) {
            ExpiryRepository(ExpiryApplication.appContext).rememberProduct(barcode, result.name, result.category)
        }
        return result
    }

    private fun firstNonBlank(vararg values: String): String =
        values.firstOrNull { it.isNotBlank() }.orEmpty()
}
