package com.pagreylabs.expiry

import android.app.Application

/** Application-scoped context used by local product catalog services. */
class ExpiryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }

    companion object {
        lateinit var appContext: android.content.Context
            private set
    }
}
