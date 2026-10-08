package com.pagreylabs.expiry

object LocalProductDataSource : ProductDataSource {
    override val id: String = "local_catalog"
    override val label: String = "Catálogo local"
    override val priority: Int = 50

    override fun lookup(barcode: String, candidates: List<String>): ProductRecord? {
        val repository = ExpiryRepository(ExpiryApplication.appContext)
        candidates.forEach { candidate ->
            repository.findProductByBarcode(candidate)?.let { product ->
                return ProductRecord(
                    barcode = candidate,
                    name = product.name,
                    brand = product.brand,
                    category = product.category,
                    kind = product.kind,
                    imageUrl = product.imageUrl,
                    ingredients = product.ingredients,
                    activeIngredients = product.activeIngredients,
                    manufacturer = product.manufacturer,
                    dosageForm = product.dosageForm,
                    quantity = product.quantity,
                    sourceId = id,
                    sourceLabel = label,
                    sourcePriority = priority,
                    confidence = 0.97,
                    externalIds = product.externalIds
                )
            }
        }
        return null
    }
}
