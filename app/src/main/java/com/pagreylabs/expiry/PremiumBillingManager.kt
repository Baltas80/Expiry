package com.pagreylabs.expiry

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Google Play Billing bridge for the Premium one-time product.
 *
 * Entitlement is granted only for a PURCHASED Play purchase. Acknowledge the
 * purchase so Google Play considers the non-consumable delivery complete.
 *
 * Final production hardening can add server-side purchase-token verification;
 * the UI is already isolated behind PremiumEntitlement so that verification
 * can be introduced without redesigning the app.
 */
class PremiumBillingManager(private val context: Context) {

    companion object {
        const val PREMIUM_PRODUCT_ID = "expiry_premium_lifetime"
    }

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach(::handlePurchase)
        }
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        PremiumEntitlement.restoreCached(context)
        if (billingClient.isReady) {
            reconcilePurchases()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    reconcilePurchases()
                }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    fun launchPurchase(activity: Activity, onUnavailable: (() -> Unit)? = null) {
        if (!billingClient.isReady) {
            start()
            onUnavailable?.invoke()
            return
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PREMIUM_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(params, ProductDetailsResponseListener { result, detailsResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                onUnavailable?.invoke()
                return@ProductDetailsResponseListener
            }

            val details = detailsResult.productDetailsList.firstOrNull()
            if (details == null) {
                onUnavailable?.invoke()
                return@ProductDetailsResponseListener
            }

            val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build()

            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .build()

            billingClient.launchBillingFlow(activity, flowParams)
        })
    }

    fun close() {
        billingClient.endConnection()
    }

    private fun reconcilePurchases() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync

            val premiumPurchase = purchases
                .filter { it.products.contains(PREMIUM_PRODUCT_ID) }
                .firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }

            PremiumEntitlement.setActive(context, premiumPurchase != null)
            premiumPurchase?.let(::handlePurchase)
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (!purchase.products.contains(PREMIUM_PRODUCT_ID)) return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        PremiumEntitlement.setActive(context, true)

        if (!purchase.isAcknowledged) {
            billingClient.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            ) { result ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    PremiumEntitlement.setActive(context, true)
                }
            }
        }
    }
}
