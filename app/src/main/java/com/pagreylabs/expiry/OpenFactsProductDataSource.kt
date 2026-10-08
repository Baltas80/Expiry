package com.pagreylabs.expiry

/**
 * Universal Open Facts source.
 *
 * A single product_type=all lookup can resolve food, beauty, pet-food and
 * other-product records and redirect to the appropriate Open Facts instance.
 */
object OpenFactsProductDataSource : ProductDataSource {
    override val id: String = "open_facts"
    override val label: String = "Open Facts"
    override val priority: Int = 70

    override fun lookup(barcode: String, candidates: List<String>): ProductRecord? {
        candidates.forEach { candidate ->
            val query = "?product_type=all&" + localizationQuery() +
                "&fields=code,product_type,product_name,abbreviated_product_name,generic_name," +
                "brands,categories,categories_tags,image_front_url,image_url,ingredients_text,quantity"

            val urls = listOf(
                "https://world.openfoodfacts.org/api/v3/product/$candidate$query",
                "https://world.openfoodfacts.org/api/v2/product/$candidate$query"
            )

            urls.forEach { url ->
                val response = ProductHttpClient.getJson(url) ?: return@forEach
                val root = response.json
                if (root.optInt("status", 0) != 1) return@forEach

                val product = root.optJSONObject("product") ?: return@forEach
                val name = firstNonBlank(
                    product.optString("product_name"),
                    product.optString("abbreviated_product_name"),
                    product.optString("generic_name")
                )
                val category = firstNonBlank(
                    product.optString("categories"),
                    firstTag(product.optJSONArray("categories_tags"))
                )
                val type = product.optString("product_type").lowercase()

                if (name.isBlank() && category.isBlank()) return@forEach

                return ProductRecord(
                    barcode = candidate,
                    name = normalizeName(name),
                    brand = firstNonBlank(product.optString("brands")),
                    category = category,
                    kind = when {
                        type.contains("beauty") -> ProductKind.BEAUTY
                        type.contains("pet") -> ProductKind.PET
                        type.contains("food") -> ProductKind.FOOD
                        type.contains("product") -> ProductKind.GENERAL
                        response.finalUrl.contains("openbeautyfacts.org") -> ProductKind.BEAUTY
                        response.finalUrl.contains("openpetfoodfacts.org") -> ProductKind.PET
                        response.finalUrl.contains("openproductsfacts.org") -> ProductKind.GENERAL
                        else -> ProductKind.FOOD
                    },
                    imageUrl = firstNonBlank(
                        product.optString("image_front_url"),
                        product.optString("image_url")
                    ),
                    ingredients = product.optString("ingredients_text").trim(),
                    quantity = product.optString("quantity").trim(),
                    sourceId = id,
                    sourceLabel = label,
                    sourcePriority = priority,
                    confidence = 0.88,
                    externalIds = mapOf("gtin" to candidate)
                )
            }
        }
        return null
    }

    private fun localizationQuery(): String {
        val locale = java.util.Locale.getDefault()
        val language = locale.language.takeIf { it.length == 2 }
        val country = locale.country.takeIf { it.length == 2 }

        return buildList {
            language?.let { add("lc=$it") }
            country?.let { add("cc=$it") }
            add("tags_lc=" + (language ?: "en"))
        }.joinToString("&")
    }

    private fun firstTag(tags: org.json.JSONArray?): String {
        if (tags == null || tags.length() == 0) return ""
        return tags.optString(0).removePrefix("en:").replace('-', ' ').trim()
    }

    private fun firstNonBlank(vararg values: String): String =
        values.firstOrNull { it.isNotBlank() }?.trim().orEmpty()

    private fun normalizeName(value: String): String =
        value.replace(Regex("\\s+"), " ").trim()
}
