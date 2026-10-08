package com.pagreylabs.expiry

/**
 * DailyMed Structured Product Label connector.
 *
 * DailyMed is most useful when the scanned identifier is already an NDC-like
 * 10/11 digit value. Normal retail GTIN scans generally do not expose the NDC
 * directly, so the connector is intentionally conservative.
 */
object DailyMedProductDataSource : ProductDataSource {
    override val id: String = "dailymed"
    override val label: String = "DailyMed"
    override val priority: Int = 75

    override fun lookup(barcode: String, candidates: List<String>): ProductRecord? {
        BarcodeNormalizer.ndcCandidates(barcode).forEach { ndc ->
            val url = "https://dailymed.nlm.nih.gov/dailymed/services/v2/spls.json?ndc=" +
                ProductHttpClient.encodeQuery(ndc) + "&pagesize=5&page=1"

            val response = ProductHttpClient.getJson(url) ?: return@forEach
            val data = response.json.optJSONArray("data") ?: return@forEach
            if (data.length() == 0) return@forEach

            val first = data.optJSONObject(0) ?: return@forEach
            val title = first.optString("title").trim()
            if (title.isBlank()) return@forEach

            return ProductRecord(
                barcode = ndc,
                name = title.substringBefore(" [").trim(),
                category = "Medicamentos",
                kind = ProductKind.MEDICINE,
                sourceId = id,
                sourceLabel = label,
                sourcePriority = priority,
                confidence = 0.82,
                externalIds = buildMap {
                    put("ndc", ndc)
                    first.optString("setid").trim().takeIf { it.isNotBlank() }?.let {
                        put("spl_set_id", it)
                    }
                }
            )
        }
        return null
    }
}
