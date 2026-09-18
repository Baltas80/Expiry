package com.pagreylabs.expiry

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/** AdMob initialization with UMP consent gating. */
object ExpiryAds {
    var canRequestAds by mutableStateOf(false)
        private set

    private var initialized = false

    fun requestConsent(activity: Activity) {
        if (!BuildConfig.ADS_ENABLED || BuildConfig.ADMOB_APP_ID.isBlank() || BuildConfig.ADMOB_BANNER_UNIT_ID.isBlank()) {
            canRequestAds = false
            return
        }

        if (BuildConfig.DEBUG) {
            initialize(activity.applicationContext)
            canRequestAds = true
            return
        }

        val info = UserMessagingPlatform.getConsentInformation(activity)
        info.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    canRequestAds = info.canRequestAds()
                    if (canRequestAds) initialize(activity.applicationContext)
                }
            },
            {
                canRequestAds = info.canRequestAds()
                if (canRequestAds) initialize(activity.applicationContext)
            }
        )
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { }
    }

    private fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        MobileAds.initialize(context) { }
    }
}
