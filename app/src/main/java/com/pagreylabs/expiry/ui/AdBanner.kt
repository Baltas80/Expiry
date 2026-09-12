package com.pagreylabs.expiry.ui

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val adView = remember(context) {
        AdView(context).apply {
            adUnitId = context.getString(com.pagreylabs.expiry.R.string.admob_banner_unit_id)
            setAdSize(AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, context.resources.displayMetrics.widthPixels))
            loadAd(AdRequest.Builder().build())
        }
    }

    DisposableEffect(adView) {
        onDispose {
            (adView.parent as? ViewGroup)?.removeView(adView)
            adView.destroy()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp, max = 150.dp)
    ) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { adView },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
