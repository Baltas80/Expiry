package com.pagreylabs.expiry

/**
 * Backward-compatible UI facade over the multi-source acquisition pipeline.
 *
 * The UI still receives the compact Result it expects, while the underlying
 * aggregator queries all configured sources concurrently and persists the
 * best enriched record locally.
 */
object ProductLookup {
    data class Result(
        val found: Boolean,
        val name: String = "",
        val category: String = "",
        val imageUrl: String = ""
    )

    fun lookup(barcode: String, callback: (Result?) -> Unit) {
        ProductDataAggregator.lookup(barcode) { snapshot ->
            val product = snapshot.merged
            callback(
                product?.let {
                    Result(
                        found = true,
                        name = it.name,
                        category = it.category,
                        imageUrl = it.imageUrl
                    )
                }
            )
        }
    }
}
