package com.pagreylabs.expiry

interface ProductDataSource {
    val id: String
    val label: String
    val priority: Int

    /**
     * A source may try several representations of the same scanned barcode.
     * Returning null means that this source has no usable match.
     */
    fun lookup(barcode: String, candidates: List<String>): ProductRecord?
}
