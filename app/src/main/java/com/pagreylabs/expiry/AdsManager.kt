package com.pagreylabs.expiry

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Centralizes AdMob/UMP initialization. Ads stay disabled until the real
 * AdMob IDs are supplied in res/values/monetization.xml.
 */
class AdsManager(private val context: Context) {
    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context)

    fun requestConsentAndInitialize(activity: Activity, onReady: (Boolean) -> Unit = {}) {
        if (context.getString(R.string.admob_app_id).isBlank()) {
            onReady(false)
            return
        }

        val params = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    if (consentInformation.canRequestAds()) {
                        MobileAds.initialize(context) { onReady(true) }
                    } else {
                        onReady(false)
                    }
                }
            },
            {
                onReady(consentInformation.canRequestAds())
            }
        )
    }
}

@Composable
fun ExpiryBannerAd(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val context = androidx.compose.ui.platform.LocalContext.current
    val appIdConfigured = context.getString(R.string.admob_app_id).isNotBlank()
    val adUnitConfigured = context.getString(R.string.admob_banner_ad_unit_id).isNotBlank()
    if (!appIdConfigured || !adUnitConfigured) return

    val adView = remember {
        AdView(context).apply {
            adUnitId = context.getString(R.string.admob_banner_ad_unit_id)
            setAdSize(
                AdSize.getLargeAnchoredAdaptiveBannerAdSize(
                    context,
                    (context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density).toInt()
                )
            )
            loadAd(AdRequest.Builder().build())
        }
    }

    DisposableEffect(adView) {
        onDispose { adView.destroy() }
    }

    AndroidView(
        factory = {
            FrameLayout(it).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                addView(adView)
            }
        },
        modifier = modifier.fillMaxWidth()
    )
}
