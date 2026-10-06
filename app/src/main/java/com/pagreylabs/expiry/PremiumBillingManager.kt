package com.pagreylabs.expiry

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the Play Billing connection and the local Premium entitlement state.
 *
 * Expiry uses one subscription product with multiple base plans. The app never
 * grants Premium merely because a purchase flow was launched: entitlement is
 * refreshed from active Play purchases and unacknowledged purchases are
 * acknowledged after Play reports them as purchased.
 *
 * A backend can be added later for server-side purchase-token verification.
 */
class PremiumBillingManager(context: Context) : AutoCloseable {
    data class Offer(
        val productDetails: ProductDetails,
        val offerDetails: ProductDetails.SubscriptionOfferDetails
    ) {
        val basePlanId: String get() = offerDetails.basePlanId
        val offerToken: String get() = offerDetails.offerToken
        val formattedPrice: String
            get() = offerDetails.pricingPhases.pricingPhaseList.firstOrNull()?.formattedPrice.orEmpty()
    }

    private val appContext = context.applicationContext
    private val _isPremium = MutableStateFlow(false)
    private val _offers = MutableStateFlow<List<Offer>>(emptyList())

    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()
    val offers: StateFlow<List<Offer>> = _offers.asStateFlow()

    private val billingClient = BillingClient.newBuilder(appContext)
        .setListener { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                processPurchases(purchases)
            }
        }
        .enablePendingPurchases(
            com.android.billingclient.api.PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    fun connect() {
        if (billingClient.isReady) {
            refresh()
            return
        }
        billingClient.startConnection(object : BillingClient.BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) refresh()
            }

            override fun onBillingServiceDisconnected() {
                // Play may reconnect on the next explicit refresh.
            }
        })
    }

    fun refresh() {
        if (!billingClient.isReady) {
            connect()
            return
        }

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
                _offers.value = details.productDetailsList
                    .flatMap { product ->
                        product.subscriptionOfferDetails.orEmpty()
                            .map { Offer(product, it) }
                    }
            }
        }

        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(ProductType.SUBS).build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases)
            }
        }
    }

    fun purchase(activity: Activity, offer: Offer): BillingResult {
        if (!billingClient.isReady) {
            connect()
            return BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
                .setDebugMessage("Billing service is not ready")
                .build()
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

    private fun processPurchases(purchases: List<Purchase>) {
        val active = purchases.any {
            it.products.contains(MonetizationConfig.PREMIUM_NO_ADS_PRODUCT_ID) &&
                it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        _isPremium.value = active

        purchases
            .filter {
                it.products.contains(MonetizationConfig.PREMIUM_NO_ADS_PRODUCT_ID) &&
                    it.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    !it.isAcknowledged
            }
            .forEach { purchase ->
                billingClient.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                ) { result ->
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        _isPremium.value = true
                    }
                }
            }
    }

    override fun close() {
        billingClient.endConnection()
    }
}
