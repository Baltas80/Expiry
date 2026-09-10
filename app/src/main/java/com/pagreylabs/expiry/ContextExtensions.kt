package com.pagreylabs.expiry

import android.content.Context
import androidx.activity.ComponentActivity

/** Restarts the hosting activity when a settings/data change requires a fresh UI. */
fun Context.recreate() {
    (this as? ComponentActivity)?.recreate()
}
