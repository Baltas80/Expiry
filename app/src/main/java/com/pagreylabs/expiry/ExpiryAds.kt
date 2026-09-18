package com.pagreylabs.expiry

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** AdMob initialization with UMP consent gating. */
object ExpiryAds {
    private val _canRequestAds = MutableStateFlow(false)
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()
    private var initialized = false

    fun requestConsent(activity: Activity) {
        if (!BuildConfig.ADS_ENABLED || BuildConfig.ADMOB_APP_ID.isBlank() || BuildConfig.ADMOB_BANNER_UNIT_ID.isBlank()) {
            _canRequestAds.value = false; return
        }
        if (BuildConfig.DEBUG) { initialize(activity.applicationContext); _canRequestAds.value = true; return }
        val info = UserMessagingPlatform.getConsentInformation(activity)
        info.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    _canRequestAds.value = info.canRequestAds()
                    if (_canRequestAds.value) initialize(activity.applicationContext)
                }
            },
            {
                _canRequestAds.value = info.canRequestAds()
                if (_canRequestAds.value) initialize(activity.applicationContext)
            }
        )
    }

    fun showPrivacyOptions(activity: Activity) { UserMessagingPlatform.showPrivacyOptionsForm(activity) { } }

    private fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        MobileAds.initialize(context) { }
    }
}
