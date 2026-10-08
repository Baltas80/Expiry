package com.pagreylabs.expiry

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Product-data federation for barcode capture.
 *
 * The runtime pipeline deliberately separates product identity from enrichment:
 * - local catalog is checked first;
 * - AEMPS CIMA is queried for Spanish medicines when a national code can be
 *   derived from the GS1/GTIN representation;
 * - the Open Facts ecosystem is queried next, with a universal endpoint first
 *   and specialised instances as fallbacks;
 * - richer fields are merged instead of letting a weaker source overwrite a
 *   stronger field.
 *
 * Sources that require commercial credentials are represented in the inventory
 * but are not called from the APK until a secure backend proxy is available.
 */
object ProductSources {
    enum class ProductType {
        FOOD,
        BEAUTY,
        PET_FOOD,
        MEDICINE,
        MEDICAL_DEVICE,
        GENERAL,
        UNKNOWN
    }

    data class ProductData(
        val barcode: String,
        val productType: ProductType = ProductType.UNKNOWN,
        val name: String = "",
        val brand: String = "",
        val manufacturer: String = "",
        val category: String = "",
        val imageUrl: String = "",
        val quantity: String = "",
        val ingredients: String = "",
        val activeIngredients: String = "",
        val nationalCode: String = "",
        val registrationNumber: String = "",
        val commercialized: Boolean? = null,
        val supplyProblem: Boolean? = null,
        val productUrl: String = "",
        val sourceId: String = "",
        val sourceName: String = "",
        val confidence: Double = 0.0
    )

    data class SourceDescriptor(
        val id: String,
        val name: String,
        val supportsBarcode: Boolean,
        val requiresCredentials: Boolean,
        val availableFromClient: Boolean,
        val notes: String
    )

    private const val DEFAULT_TIMEOUT_MS = 6500

    val inventory: List<SourceDescriptor> = listOf(
        SourceDescriptor("local", "Expiry local catalog", true, false, true,
            "Previously resolved products stored on-device."),
        SourceDescriptor("openfacts-universal", "Open Facts universal", true, false, true,
            "Single barcode lookup across Food, Beauty, Pet Food and Products Facts."),
        SourceDescriptor("openfoodfacts", "Open Food Facts", true, false, true,
            "Food products and label/nutrition data."),
        SourceDescriptor("openbeautyfacts", "Open Beauty Facts", true, false, true,
            "Cosmetic products, ingredients and label information."),
        SourceDescriptor("openpetfoodfacts", "Open Pet Food Facts", true, false, true,
            "Pet food products."),
        SourceDescriptor("openproductsfacts", "Open Products Facts", true, false, true,
            "General/non-food products."),
        SourceDescriptor("aemps-cima", "AEMPS CIMA", true, false, true,
            "Spanish medicines. Match GTIN/NTIN to Código Nacional and query the official CIMA REST service."),
        SourceDescriptor("gs1-verified", "GS1 Verified by GS1", true, true, false,
            "Authoritative GTIN/company/product verification. Advanced API access is member-based."),
        SourceDescriptor("cosing", "European Commission CosIng", false, false, false,
            "Cosmetic ingredient/substance enrichment, not a consumer-product barcode catalogue."),
        SourceDescriptor("upcitemdb", "UPCitemdb", true, true, false,
            "Optional secondary barcode catalogue; integrate through a secure backend key."),
        SourceDescriptor("barcode-lookup", "Barcode Lookup", true, true, false,
            "Optional secondary barcode catalogue; integrate through a secure backend key.")
    )

    private val universalOpenFacts = OpenFactsSource(
        id = "openfacts-universal",
        name = "Open Facts universal",
        baseUrl = "https://world.openfoodfacts.org",
        fallbackType = ProductType.UNKNOWN,
        confidence = 0.72
    )

    private val specialisedOpenFacts = listOf(
        OpenFactsSource("openfoodfacts", "Open Food Facts", "https://world.openfoodfacts.org", ProductType.FOOD, 0.78),
        OpenFactsSource("openbeautyfacts", "Open Beauty Facts", "https://world.openbeautyfacts.org", ProductType.BEAUTY, 0.84),
        OpenFactsSource("openpetfoodfacts", "Open Pet Food Facts", "https://world.openpetfoodfacts.org", ProductType.PET_FOOD, 0.76),
        OpenFactsSource("openproductsfacts", "Open Products Facts", "https://world.openproductsfacts.org", ProductType.GENERAL, 0.68)
    )

    fun local(context: Context, barcode: String): ProductData? {
        val product = ExpiryRepository(context.applicationContext).findProductByBarcode(barcode) ?: return null
        return ProductData(
            barcode = barcode,
            productType = product.productType.toProductType(),
            name = product.name,
            brand = product.brand,
            manufacturer = product.manufacturer,
            category = product.category,
            imageUrl = product.imageUrl,
            ingredients = product.ingredients,
            activeIngredients = product.activeIngredients,
            nationalCode = product.nationalCode,
            registrationNumber = product.registrationNumber,
            sourceId = product.sourceId.ifBlank { "local" },
            sourceName = product.sourceName.ifBlank { "Expiry local catalog" },
            confidence = maxOf(0.95, product.confidence)
        )
    }

    /**
     * Queries all public client-safe sources concurrently.
     *
     * CIMA is intentionally queried at the same time as the Open Facts
     * ecosystem, because a Spanish medicine may exist in CIMA but not in an
     * open consumer catalogue.
     */
    fun lookupRemote(barcode: String): ProductData? {
        if (barcode.isBlank()) return null

        val executor = Executors.newFixedThreadPool(6)
        return try {
            val tasks = mutableListOf<Callable<ProductData?>>()
            tasks += Callable { CimaSource.lookup(barcode) }
            tasks += Callable { universalOpenFacts.lookup(barcode) }

            // Specific Open Facts instances are fallback/enrichment sources.
            for (source in specialisedOpenFacts) {
                tasks += Callable { source.lookup(barcode) }
            }

            executor.invokeAll(tasks, DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .mapNotNull { future -> runCatching { future.get() }.getOrNull() }
                .filterNot(ProductData::isEmpty)
                .let(::mergeAll)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun mergeAll(results: List<ProductData>): ProductData? {
        if (results.isEmpty()) return null

        val ordered = results.sortedWith(
            compareByDescending<ProductData> { completeness(it) }
                .thenByDescending { it.confidence }
                .thenByDescending { sourcePriority(it.sourceId) }
        )

        var merged = ordered.first()
        for (candidate in ordered.drop(1)) {
            merged = merged.merge(candidate)
        }
        return merged
    }

    private fun completeness(product: ProductData): Int =
        listOf(
            product.name, product.brand, product.manufacturer, product.category,
            product.imageUrl, product.quantity, product.ingredients,
            product.activeIngredients, product.nationalCode, product.registrationNumber,
            product.productUrl
        ).count { it.isNotBlank() } +
            if (product.commercialized != null) 1 else 0 +
            if (product.supplyProblem != null) 1 else 0

    private fun sourcePriority(sourceId: String): Int = when (sourceId) {
        "aemps-cima" -> 100
        "openbeautyfacts" -> 90
        "openfoodfacts" -> 80
        "openfacts-universal" -> 75
        "openpetfoodfacts" -> 70
        "openproductsfacts" -> 60
        "local" -> 50
        else -> 0
    }

    private data class OpenFactsSource(
        val id: String,
        val name: String,
        val baseUrl: String,
        val fallbackType: ProductType,
        val confidence: Double
    ) {
        fun lookup(barcode: String): ProductData? {
            val fields = listOf(
                "code", "product_name", "abbreviated_product_name", "generic_name",
                "brands", "categories", "categories_tags", "image_front_url",
                "image_url", "quantity", "ingredients_text", "ingredients_text_es",
                "ingredients_text_en", "product_type"
            ).joinToString(",")

            val encodedFields = URLEncoder.encode(fields, Charsets.UTF_8.name())
            val locale = Locale.getDefault()
            val language = locale.language.takeIf { it.length == 2 } ?: "en"
            val country = locale.country.takeIf { it.length == 2 } ?: "ES"
            val query = "?product_type=all&lc=$language&cc=$country&fields=$encodedFields"

            request("$baseUrl/api/v3/product/$barcode$query", barcode)
                ?: request("$baseUrl/api/v2/product/$barcode$query", barcode)
        }

        private fun request(urlText: String, barcode: String): ProductData? {
            val url = runCatching { URL(urlText) }.getOrNull() ?: return null
            val connection = runCatching { url.openConnection() as HttpURLConnection }.getOrNull() ?: return null

            return try {
                connection.requestMethod = "GET"
                connection.connectTimeout = DEFAULT_TIMEOUT_MS
                connection.readTimeout = DEFAULT_TIMEOUT_MS
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty(
                    "User-Agent",
                    "Expiry/1.0.6 (https://github.com/Baltas80/Expiry; barcode lookup)"
                )

                if (connection.responseCode !in 200..299) return null
                val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                if (root.optInt("status", 0) != 1) return null
                val product = root.optJSONObject("product") ?: return null

                val type = when (product.optString("product_type").lowercase(Locale.ROOT)) {
                    "beauty" -> ProductType.BEAUTY
                    "food" -> ProductType.FOOD
                    "petfood", "pet_food" -> ProductType.PET_FOOD
                    "product" -> ProductType.GENERAL
                    else -> fallbackType
                }

                ProductData(
                    barcode = barcode,
                    productType = type,
                    name = firstNonBlank(
                        product.optString("product_name"),
                        product.optString("abbreviated_product_name"),
                        product.optString("generic_name")
                    ),
                    brand = firstToken(product.optString("brands")),
                    category = firstCategory(
                        product.optString("categories"),
                        product.optString("categories_tags")
                    ),
                    imageUrl = firstNonBlank(
                        product.optString("image_front_url"),
                        product.optString("image_url")
                    ),
                    quantity = product.optString("quantity"),
                    ingredients = firstNonBlank(
                        product.optString("ingredients_text"),
                        product.optString("ingredients_text_$language"),
                        product.optString("ingredients_text_en")
                    ),
                    productUrl = "https://\${URL(baseUrl).host}/product/$barcode",
                    sourceId = id,
                    sourceName = name,
                    confidence = confidence
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    private object CimaSource {
        fun lookup(barcode: String): ProductData? {
            val nationalCode = barcodeNormalizerNationalCode(barcode) ?: return null
            return lookupNationalCode(nationalCode)
        }

        private fun lookupNationalCode(nationalCode: String): ProductData? {
            val url = runCatching {
                URL("https://cima.aemps.es/cima/rest/presentacion/$nationalCode")
            }.getOrNull() ?: return null
            val connection = runCatching {
                url.openConnection() as HttpURLConnection
            }.getOrNull() ?: return null

            return try {
                connection.requestMethod = "GET"
                connection.connectTimeout = DEFAULT_TIMEOUT_MS
                connection.readTimeout = DEFAULT_TIMEOUT_MS
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "Expiry/1.0.6 (public AEMPS CIMA client)")

                if (connection.responseCode !in 200..299) return null
                val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val name = firstNonBlank(root.optString("nombre"), root.optString("name"))
                if (name.isBlank()) return null

                ProductData(
                    barcode = firstNonBlank(root.optString("gtin"), barcode),
                    productType = ProductType.MEDICINE,
                    name = name,
                    manufacturer = firstNonBlank(root.optString("labtitular"), root.optString("laboratorio")),
                    activeIngredients = firstNonBlank(root.optString("pactivos"), root.optString("principiosActivos")),
                    nationalCode = firstNonBlank(root.optString("cn"), nationalCode),
                    registrationNumber = root.optString("nregistro"),
                    commercialized = nullableBoolean(root, "comerc", "comercializado"),
                    supplyProblem = nullableBoolean(root, "psum"),
                    productUrl = root.optString("nregistro").takeIf(String::isNotBlank)?.let {
                        "https://cima.aemps.es/cima/publico/detalle.html?nregistro=$it"
                    }.orEmpty(),
                    sourceId = "aemps-cima",
                    sourceName = "AEMPS CIMA",
                    confidence = 0.98
                )
            } finally {
                connection.disconnect()
            }
        }

        private fun nullableBoolean(root: JSONObject, vararg keys: String): Boolean? {
            for (key in keys) {
                if (root.has(key)) return root.optBoolean(key)
            }
            return null
        }
    }

    /**
     * Spain-specific medicine barcode decoding.
     *
     * - GS1 AI (712) carries the seven-digit Código Nacional.
     * - Spanish NTINs begin with 0847000; the final seven digits are the CN,
     *   including its check digit.
     * - 13-digit retail scans beginning 847000 are also tested as NTIN.
     */
    internal fun barcodeNormalizerNationalCode(barcode: String): String? {
        val raw = barcode.trim()
        if (raw.isBlank()) return null

        Regex("712\\D*(\\d{7})").find(raw)?.groupValues?.get(1)?.let { return it }

        val compact = raw.filterNot(Char::isWhitespace)
        val numeric = compact.filter(Char::isDigit)
        if (numeric != compact) return null

        return when {
            numeric.length == 14 && numeric.startsWith("0847000") -> numeric.takeLast(7)
            numeric.length == 13 && numeric.startsWith("847000") -> ("0$numeric").takeLast(7)
            else -> null
        }
    }

    private fun ProductData.isEmpty(): Boolean =
        name.isBlank() && brand.isBlank() && manufacturer.isBlank() &&
            category.isBlank() && imageUrl.isBlank() && quantity.isBlank() &&
            ingredients.isBlank() && activeIngredients.isBlank() &&
            nationalCode.isBlank() && registrationNumber.isBlank() &&
            productUrl.isBlank()

    private fun ProductData.merge(other: ProductData): ProductData {
        val preferOtherIdentity = other.confidence > confidence
        return copy(
            productType = if (productType == ProductType.UNKNOWN) other.productType else productType,
            name = firstNonBlank(name, other.name),
            brand = firstNonBlank(brand, other.brand),
            manufacturer = firstNonBlank(manufacturer, other.manufacturer),
            category = firstNonBlank(category, other.category),
            imageUrl = firstNonBlank(imageUrl, other.imageUrl),
            quantity = firstNonBlank(quantity, other.quantity),
            ingredients = firstNonBlank(ingredients, other.ingredients),
            activeIngredients = firstNonBlank(activeIngredients, other.activeIngredients),
            nationalCode = firstNonBlank(nationalCode, other.nationalCode),
            registrationNumber = firstNonBlank(registrationNumber, other.registrationNumber),
            commercialized = commercialized ?: other.commercialized,
            supplyProblem = supplyProblem ?: other.supplyProblem,
            productUrl = firstNonBlank(productUrl, other.productUrl),
            sourceId = if (preferOtherIdentity) other.sourceId else sourceId,
            sourceName = if (preferOtherIdentity) other.sourceName else sourceName,
            confidence = maxOf(confidence, other.confidence),
            barcode = firstNonBlank(barcode, other.barcode)
        )
    }

    private fun String.toProductType(): ProductType =
        runCatching { ProductType.valueOf(this) }.getOrDefault(ProductType.UNKNOWN)

    private fun firstNonBlank(vararg values: String): String =
        values.asSequence().map(String::trim).firstOrNull(String::isNotBlank).orEmpty()

    private fun firstToken(value: String): String =
        value.split(',').asSequence().map(String::trim).firstOrNull(String::isNotBlank).orEmpty()

    private fun firstCategory(vararg values: String): String =
        values.asSequence()
            .flatMap { it.split(',').asSequence().map(String::trim) }
            .firstOrNull(String::isNotBlank)
            .orEmpty()
}
