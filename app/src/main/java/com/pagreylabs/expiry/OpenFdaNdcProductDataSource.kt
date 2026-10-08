package com.pagreylabs.expiry

import org.json.JSONArray
import org.json.JSONObject

/**
 * FDA National Drug Code directory connector.
 *
 * UPC is one of the harmonized identifiers exposed by openFDA when available,
 * so the connector first tries the scanned barcode as UPC/EAN metadata and
 * then tries explicit 10/11 digit NDC representations.
 */
object OpenFdaNdcProductDataSource : ProductDataSource {
    override val id: String = "openfda_ndc"
    override val label: String = "FDA openFDA NDC"
    override val priority: Int = 80

    override fun lookup(barcode: String, candidates: List<String>): ProductRecord? {
        candidates.forEach { candidate ->
            lookupField("upc", candidate)?.let { return it }

            if (candidate.matches(Regex("\\d{10,11}"))) {
                lookupField("product_ndc", candidate)?.let { return it }
                lookupField("package_ndc", candidate)?.let { return it }
            }
        }
        return null
    }

    private fun lookupField(field: String, value: String): ProductRecord? {
        val query = "$field:$value"
        val url = "https://api.fda.gov/drug/ndc.json?search=" +
            ProductHttpClient.encodeQuery(query) + "&limit=5"

        val response = ProductHttpClient.getJson(url) ?: return null
        val results = response.json.optJSONArray("results") ?: return null
        if (results.length() == 0) return null

        return results.optJSONObject(0)?.toRecord()
    }

    private fun JSONObject.toRecord(): ProductRecord? {
        val name = firstNonBlank(
            optString("brand_name"),
            optString("generic_name")
        )
        if (name.isBlank()) return null

        val ingredients = optJSONArray("active_ingredients")
        val active = buildList {
            if (ingredients != null) {
                for (i in 0 until ingredients.length()) {
                    val item = ingredients.optJSONObject(i) ?: continue
                    val ingredientName = item.optString("name").trim()
                    val strength = item.optString("strength").trim()
                    if (ingredientName.isNotBlank()) {
                        add(listOf(ingredientName, strength).filter { it.isNotBlank() }.joinToString(" "))
                    }
                }
            }
        }.joinToString(", ")

        return ProductRecord(
            barcode = firstNonBlank(
                optString("upc"),
                optString("package_ndc"),
                optString("product_ndc")
            ),
            name = name,
            brand = optString("brand_name").trim(),
            category = "Medicamentos",
            kind = ProductKind.MEDICINE,
            activeIngredients = active,
            manufacturer = firstNonBlank(
                optString("labeler_name"),
                optString("manufacturer_name")
            ),
            dosageForm = optString("dosage_form").trim(),
            sourceId = id,
            sourceLabel = label,
            sourcePriority = priority,
            confidence = 0.90,
            externalIds = buildMap {
                optString("product_ndc").takeIf { it.isNotBlank() }?.let { put("product_ndc", it) }
                optString("package_ndc").takeIf { it.isNotBlank() }?.let { put("package_ndc", it) }
                optString("spl_set_id").takeIf { it.isNotBlank() }?.let { put("spl_set_id", it) }
            }
        )
    }

    private fun firstNonBlank(vararg values: String): String =
        values.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
}
