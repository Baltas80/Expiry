package com.pagreylabs.expiry

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Multi-source product acquisition pipeline.
 *
 * Every independent source is queried concurrently. Results are collected,
 * deduplicated and merged into one canonical record without letting one
 * provider failure block the others.
 */
object ProductDataAggregator {
    private val executor = Executors.newFixedThreadPool(5)
    private val timeoutExecutor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor()

    private val sources: List<ProductDataSource> = listOf(
        LocalProductDataSource,
        AempsCimaProductDataSource,
        OpenFdaNdcProductDataSource,
        DailyMedProductDataSource,
        OpenFactsProductDataSource
    )

    fun lookup(
        barcode: String,
        callback: (ProductLookupSnapshot) -> Unit
    ) {
        val candidates = BarcodeNormalizer.candidates(barcode)
        if (candidates.isEmpty()) {
            callback(ProductLookupSnapshot(barcode, emptyList(), null))
            return
        }

        val results = mutableListOf<ProductRecord>()
        val lock = Any()
        val remaining = AtomicInteger(sources.size)
        val delivered = AtomicBoolean(false)

        fun finish() {
            if (!delivered.compareAndSet(false, true)) return

            val sorted = synchronized(lock) {
                results
                    .distinctBy { record ->
                        listOf(record.sourceId, record.barcode, record.name)
                            .joinToString("|")
                    }
                    .sortedWith(
                        compareByDescending<ProductRecord> { it.confidence }
                            .thenByDescending { it.sourcePriority }
                    )
            }

            val merged = merge(sorted)
            merged?.let { remember(it, sorted) }
            callback(ProductLookupSnapshot(barcode, sorted, merged))
        }

        sources.forEach { source ->
            executor.execute {
                val result = runCatching {
                    source.lookup(barcode, candidates)
                }.getOrNull()

                result?.let {
                    synchronized(lock) { results += it }
                }

                if (remaining.decrementAndGet() == 0) finish()
            }
        }

        timeoutExecutor.schedule(::finish, 8, TimeUnit.SECONDS)
    }

    fun sources(): List<ProductDataSource> = sources

    private fun merge(records: List<ProductRecord>): ProductRecord? {
        val primary = records.firstOrNull() ?: return null
        val ordered = records.sortedWith(
            compareByDescending<ProductRecord> { it.sourcePriority }
                .thenByDescending { it.confidence }
        )

        fun first(field: (ProductRecord) -> String): String =
            ordered.asSequence()
                .map(field)
                .firstOrNull { it.isNotBlank() }
                .orEmpty()

        val mergedExternalIds = linkedMapOf<String, String>().apply {
            ordered.asReversed().forEach { putAll(it.externalIds) }
            ordered.forEach { putAll(it.externalIds) }
        }

        return primary.copy(
            name = first(ProductRecord::name),
            brand = first(ProductRecord::brand),
            category = first(ProductRecord::category),
            kind = ordered.firstOrNull { it.kind != ProductKind.UNKNOWN }?.kind
                ?: primary.kind,
            imageUrl = first(ProductRecord::imageUrl),
            ingredients = first(ProductRecord::ingredients),
            activeIngredients = first(ProductRecord::activeIngredients),
            manufacturer = first(ProductRecord::manufacturer),
            dosageForm = first(ProductRecord::dosageForm),
            quantity = first(ProductRecord::quantity),
            confidence = ordered.maxOf { it.confidence },
            externalIds = mergedExternalIds
        )
    }

    private fun remember(merged: ProductRecord, records: List<ProductRecord>) {
        val repository = ExpiryRepository(ExpiryApplication.appContext)
        repository.rememberProduct(merged)
        records.forEach { record ->
            if (record.barcode != merged.barcode) {
                repository.rememberProduct(record)
            }
        }
    }
}
