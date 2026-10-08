package com.pagreylabs.expiry

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ProductHttpResponse(
    val json: JSONObject,
    val finalUrl: String
)

object ProductHttpClient {
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 5_000
    private const val MAX_RESPONSE_CHARS = 2_000_000
    private const val USER_AGENT =
        "Expiry/1.0 (https://github.com/Baltas80/Expiry; product-data integration)"

    fun getJson(urlText: String): ProductHttpResponse? {
        val connection = runCatching {
            (URL(urlText).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
            }
        }.getOrNull() ?: return null

        return try {
            if (connection.responseCode !in 200..299) return null

            val body = buildString {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    val buffer = CharArray(8_192)
                    var total = 0
                    while (true) {
                        val read = reader.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > MAX_RESPONSE_CHARS) return null
                        append(buffer, 0, read)
                    }
                }
            }

            ProductHttpResponse(
                json = JSONObject(body),
                finalUrl = connection.url?.toString().orEmpty()
            )
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    fun encodeQuery(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}
