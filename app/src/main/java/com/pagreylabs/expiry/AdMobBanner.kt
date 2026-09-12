package com.pagreylabs.expiry

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

@Composable
fun ExpiryAdBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val premium = remember { context.getSharedPreferences("expiry_settings", 0).getBoolean("premium_enabled", false) }
    if (premium) return
    var canRequestAds by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val consent = UserMessagingPlatform.getConsentInformation(context)
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    canRequestAds = consent.canRequestAds
                }
            },
            { canRequestAds = consent.canRequestAds }
        )
    }
    if (!canRequestAds) return
    Box(modifier = modifier.fillMaxWidth().height(60.dp)) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { viewContext ->
                AdView(viewContext).apply {
                    setAdSize(AdSize.getLargeAnchoredAdaptiveBannerAdSize(viewContext, 360))
                    adUnitId = BuildConfig.ADMOB_BANNER_AD_UNIT_ID
                    MobileAds.initialize(viewContext)
                    loadAd(AdRequest.Builder().build())
                }
            }
        )
    }
}
