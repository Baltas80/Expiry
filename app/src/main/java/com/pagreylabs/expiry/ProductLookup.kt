package com.pagreylabs.expiry

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

/** Lightweight Open Food Facts lookup. Runs off the main thread. */
object ProductLookup {
    data class Result(val name: String, val category: String)

    fun lookup(barcode: String, callback: (Result?) -> Unit) {
        Thread {
            val result = runCatching {
                val encoded = URLEncoder.encode(barcode.trim(), "UTF-8")
                val connection = (URL("https://world.openfoodfacts.org/api/v2/product/$encoded?fields=product_name,product_name_es,categories,categories_tags").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("User-Agent", "Expiry/0.5 (PAGREY LABS)")
                }
                try {
                    if (connection.responseCode !in 200..299) return@runCatching null
                    val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    if (root.optInt("status", 0) != 1) return@runCatching null
                    val product = root.optJSONObject("product") ?: return@runCatching null
                    val name = product.optString("product_name_es").ifBlank { product.optString("product_name") }
                    val category = product.optString("categories").split(',').firstOrNull()?.trim().orEmpty()
                    name.takeIf { it.isNotBlank() }?.let { Result(it, category) }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
            callback(result)
        }.start()
    }
}
