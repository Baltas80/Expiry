package com.pagreylabs.expiry

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PremiumBillingManager(context: Context) : AutoCloseable {
    data class SubscriptionOffer(
        val productDetails: ProductDetails,
        val offerDetails: ProductDetails.SubscriptionOfferDetails
    ) {
        val basePlanId: String get() = offerDetails.basePlanId
        val offerToken: String get() = offerDetails.offerToken
        val formattedPrice: String
            get() = offerDetails.pricingPhases.pricingPhaseList.firstOrNull()?.formattedPrice.orEmpty()
    }

    data class LifetimeOffer(
        val productDetails: ProductDetails
    ) {
        val formattedPrice: String
            get() = productDetails.oneTimePurchaseOfferDetails?.formattedPrice.orEmpty()
    }

    private val appContext = context.applicationContext
    private val _isPremium = MutableStateFlow(false)
    private val subscriptionActive = MutableStateFlow(false)
    private val lifetimeActive = MutableStateFlow(false)
    private val _offers = MutableStateFlow<List<SubscriptionOffer>>(emptyList())
    private val _lifetimeOffer = MutableStateFlow<LifetimeOffer?>(null)

    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()
    val offers: StateFlow<List<SubscriptionOffer>> = _offers.asStateFlow()
    val lifetimeOffer: StateFlow<LifetimeOffer?> = _lifetimeOffer.asStateFlow()

    private val billingClient = BillingClient.newBuilder(appContext)
        .setListener { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                processSubscriptionPurchases(purchases)
                processOneTimePurchases(purchases)
            }
        }
        .enableAutoServiceReconnection()
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    fun connect() {
        if (billingClient.isReady) {
            refresh()
            return
        }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    refresh()
                }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    fun refresh() {
        if (!billingClient.isReady) {
            connect()
            return
        }

        querySubscriptionDetails()
        queryLifetimeDetails()
        queryActivePurchases(ProductType.SUBS)
        queryActivePurchases(ProductType.INAPP)
    }

    private fun querySubscriptionDetails() {
        val query = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(MonetizationConfig.PREMIUM_NO_ADS_PRODUCT_ID)
                        .setProductType(ProductType.SUBS)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(query) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _offers.value = details.productDetailsList.flatMap { product ->
                    product.subscriptionOfferDetails.orEmpty()
                        .map { SubscriptionOffer(product, it) }
                }
            }
        }
    }

    private fun queryLifetimeDetails() {
        val query = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(MonetizationConfig.PREMIUM_LIFETIME_PRODUCT_ID)
                        .setProductType(ProductType.INAPP)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(query) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _lifetimeOffer.value = details.productDetailsList
                    .firstOrNull()
                    ?.let(::LifetimeOffer)
            }
        }
    }

    private fun queryActivePurchases(productType: String) {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(productType)
                .build()
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync

            if (productType == ProductType.SUBS) {
                processSubscriptionPurchases(purchases)
            } else {
                processOneTimePurchases(purchases)
            }
        }
    }

    fun purchase(activity: Activity, offer: SubscriptionOffer): BillingResult {
        if (!billingClient.isReady) {
            connect()
            return serviceDisconnectedResult()
        }

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(offer.productDetails)
            .setOfferToken(offer.offerToken)
            .build()

        return billingClient.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .build()
        )
    }

    fun purchaseLifetime(activity: Activity, offer: LifetimeOffer): BillingResult {
        if (!billingClient.isReady) {
            connect()
            return serviceDisconnectedResult()
        }

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(offer.productDetails)
            .build()

        return billingClient.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .build()
        )
    }

    private fun serviceDisconnectedResult(): BillingResult =
        BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
            .setDebugMessage("Billing service is not ready")
            .build()

    private fun processSubscriptionPurchases(purchases: List<Purchase>) {
        subscriptionActive.value = purchases.any {
            it.products.contains(MonetizationConfig.PREMIUM_NO_ADS_PRODUCT_ID) &&
                it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        recomputePremium()

        purchases
            .filter {
                it.products.contains(MonetizationConfig.PREMIUM_NO_ADS_PRODUCT_ID) &&
                    it.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    !it.isAcknowledged
            }
            .forEach(::acknowledgePurchase)
    }

    private fun processOneTimePurchases(purchases: List<Purchase>) {
        lifetimeActive.value = purchases.any {
            it.products.contains(MonetizationConfig.PREMIUM_LIFETIME_PRODUCT_ID) &&
                it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        recomputePremium()

        purchases
            .filter {
                it.products.contains(MonetizationConfig.PREMIUM_LIFETIME_PRODUCT_ID) &&
                    it.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    !it.isAcknowledged
            }
            .forEach(::acknowledgePurchase)
    }

    private fun recomputePremium() {
        _isPremium.value = subscriptionActive.value || lifetimeActive.value
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        billingClient.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
        ) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                recomputePremium()
            }
        }
    }

    override fun close() {
        billingClient.endConnection()
    }
}
