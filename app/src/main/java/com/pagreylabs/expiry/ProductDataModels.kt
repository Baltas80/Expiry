package com.pagreylabs.expiry

enum class ProductKind {
    FOOD,
    BEAUTY,
    MEDICINE,
    PET,
    GENERAL,
    UNKNOWN
}

data class ProductRecord(
    val barcode: String,
    val name: String,
    val brand: String = "",
    val category: String = "",
    val kind: ProductKind = ProductKind.UNKNOWN,
    val imageUrl: String = "",
    val ingredients: String = "",
    val activeIngredients: String = "",
    val manufacturer: String = "",
    val dosageForm: String = "",
    val quantity: String = "",
    val sourceId: String,
    val sourceLabel: String,
    val sourcePriority: Int,
    val confidence: Double,
    val externalIds: Map<String, String> = emptyMap()
)

data class ProductLookupSnapshot(
    val queriedBarcode: String,
    val records: List<ProductRecord>,
    val merged: ProductRecord?
)
