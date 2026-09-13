package com.pagreylabs.expiry

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Ad placeholder. Real AdMob integration is intentionally disabled until the
 * production App ID and banner unit ID are configured.
 */
@Composable
fun ExpiryAdBanner(modifier: Modifier = Modifier) {
    // Deliberately empty: no AdMob SDK is initialized from the UI lifecycle.
    Box(modifier.fillMaxWidth().height(0.dp))
}
