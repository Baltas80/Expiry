package com.pagreylabs.expiry

import org.json.JSONArray

/**
 * AEMPS CIMA is the authoritative Spanish medicine source.
 *
 * Lookup is performed by Código Nacional when the scanned value can be
 * deterministically mapped to Spain's 7-digit CN.
 */
object AempsCimaProductDataSource : ProductDataSource {
    override val id: String = "aemps_cima"
    override val label: String = "AEMPS CIMA"
    override val priority: Int = 100

    override fun lookup(barcode: String, candidates: List<String>): ProductRecord? {
        val nationalCodes = BarcodeNormalizer.spanishNationalCodes(barcode)
        nationalCodes.forEach { cn ->
            val url = "https://cima.aemps.es/cima/rest/medicamento?cn=" +
                ProductHttpClient.encodeQuery(cn)
            val response = ProductHttpClient.getJson(url) ?: return@forEach

            val root = response.json
            val name = firstNonBlank(
                root.optString("nombre"),
                firstPresentationName(root.optJSONArray("presentaciones"))
            )
            if (name.isBlank()) return@forEach

            val activeIngredients = activeIngredients(root.optJSONArray("principiosActivos"))
            val manufacturer = firstNonBlank(
                root.optString("laboratorio"),
                root.optString("laboratorioTitular")
            )
            val image = firstPhotoUrl(root.optJSONArray("fotos"))

            return ProductRecord(
                barcode = barcode,
                name = name,
                brand = extractBrand(name),
                category = "Medicamentos",
                kind = ProductKind.MEDICINE,
                imageUrl = image,
                activeIngredients = activeIngredients,
                manufacturer = manufacturer,
                dosageForm = firstNonBlank(
                    root.optString("formaFarmaceuticaSimplificada"),
                    root.optString("formaFarmaceutica")
                ),
                quantity = firstPresentationField(
                    root.optJSONArray("presentaciones"),
                    "cantidad"
                ),
                sourceId = id,
                sourceLabel = label,
                sourcePriority = priority,
                confidence = 0.99,
                externalIds = mapOf(
                    "cn" to cn,
                    "nregistro" to root.optString("nregistro")
                ).filterValues { it.isNotBlank() }
            )
        }
        return null
    }

    private fun activeIngredients(array: JSONArray?): String =
        if (array == null) {
            ""
        } else {
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val name = firstNonBlank(o.optString("nombre"), o.optString("nombrePrincipioActivo"))
                    val amount = firstNonBlank(o.optString("cantidad"), o.optString("dosis"))
                    if (name.isNotBlank()) {
                        add(listOf(name, amount).filter { it.isNotBlank() }.joinToString(" "))
                    }
                }
            }.joinToString(", ")
        }

    private fun firstPresentationName(array: JSONArray?): String =
        array?.optJSONObject(0)?.optString("nombre").orEmpty().trim()

    private fun firstPresentationField(array: JSONArray?, field: String): String =
        array?.optJSONObject(0)?.optString(field).orEmpty().trim()

    private fun firstPhotoUrl(array: JSONArray?): String =
        array?.let {
            for (i in 0 until it.length()) {
                val o = it.optJSONObject(i) ?: continue
                val url = o.optString("url").trim()
                if (url.isNotBlank()) return url
            }
            ""
        } ?: ""

    private fun extractBrand(name: String): String =
        name.substringBefore(' ').trim().takeIf { it.isNotBlank() }.orEmpty()

    private fun firstNonBlank(vararg values: String): String =
        values.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
}
