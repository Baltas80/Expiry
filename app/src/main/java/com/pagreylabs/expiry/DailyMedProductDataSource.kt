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
            val url = "https://dailymed.nlm.nih.gov/dailymed/services/v2/ndc/" +
                ProductHttpClient.encodeQuery(ndc) + "/spls.json"

            val response = ProductHttpClient.getJson(url) ?: return@forEach
            val data = response.json.optJSONArray("DATA") ?: return@forEach
            if (data.length() == 0) return@forEach

            val columns = response.json.optJSONArray("COLUMNS")
            val titleIndex = columns?.indexOfValue("TITLE") ?: -1
            val setIdIndex = columns?.indexOfValue("SETID") ?: -1
            val first = data.optJSONArray(0) ?: return@forEach

            val title = if (titleIndex >= 0) first.optString(titleIndex).trim() else ""
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
                    if (setIdIndex >= 0) {
                        first.optString(setIdIndex).trim().takeIf { it.isNotBlank() }?.let {
                            put("spl_set_id", it)
                        }
                    }
                }
            )
        }
        return null
    }

    private fun org.json.JSONArray.indexOfValue(value: String): Int {
        for (i in 0 until length()) {
            if (optString(i).equals(value, ignoreCase = true)) return i
        }
        return -1
    }
}
