package com.pagreylabs.expiry

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/** Adaptive banner immediately above the bottom navigation; omitted for Premium. */
@Composable
fun ExpiryAdBanner(isPremium: Boolean, modifier: Modifier = Modifier) {
    val canRequestAds by ExpiryAds.canRequestAds.collectAsState()
    if (isPremium || !BuildConfig.ADS_ENABLED || !canRequestAds) return
    val context = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp.coerceAtLeast(1)
    Box(modifier.fillMaxWidth().heightIn(min = 50.dp), contentAlignment = Alignment.Center) {
        val adView = remember(widthDp) {
            AdView(context).apply {
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
                adUnitId = BuildConfig.ADMOB_BANNER_UNIT_ID
                loadAd(AdRequest.Builder().build())
            }
        }
        AndroidView(factory = { adView }, modifier = Modifier.fillMaxWidth())
        DisposableEffect(adView) { onDispose { adView.destroy() } }
    }
}
