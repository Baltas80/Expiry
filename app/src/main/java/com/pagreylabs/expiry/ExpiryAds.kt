package com.pagreylabs.expiry

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Handles AdMob initialization and consent gating.
 *
 * Debug builds use Google's test configuration. Release builds require the
 * real AdMob IDs to be injected at build time; no production identifiers are
 * stored in source control.
 */
object ExpiryAds {
    private val _canRequestAds = MutableStateFlow(false)
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private var initialized = false

    fun requestConsent(activity: Activity) {
        if (!BuildConfig.ADS_ENABLED || BuildConfig.ADMOB_APP_ID.isBlank() || BuildConfig.ADMOB_BANNER_UNIT_ID.isBlank()) {
            _canRequestAds.value = false
            return
        }

        if (BuildConfig.DEBUG) {
            initialize(activity.applicationContext)
            _canRequestAds.value = true
            return
        }

        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        val requestParameters = ConsentRequestParameters.Builder().build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            requestParameters,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    _canRequestAds.value = consentInformation.canRequestAds()
                    if (_canRequestAds.value) initialize(activity.applicationContext)
                }
            },
            {
                // A previous valid consent decision may still allow ads after an
                // unsuccessful update attempt, so consult the current state.
                _canRequestAds.value = consentInformation.canRequestAds()
                if (_canRequestAds.value) initialize(activity.applicationContext)
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
