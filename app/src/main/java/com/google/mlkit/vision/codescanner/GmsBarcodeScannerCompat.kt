package com.google.mlkit.vision.codescanner

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.UUID

class GmsBarcodeScannerOptions private constructor() {
    class Builder {
        fun setBarcodeFormats(@Suppress("UNUSED_PARAMETER") formats: Int): Builder = this
        fun enableAutoZoom(): Builder = this
        fun build(): GmsBarcodeScannerOptions = GmsBarcodeScannerOptions()
    }
}

object GmsBarcodeScanning {
    fun getClient(activity: Activity, @Suppress("UNUSED_PARAMETER") options: GmsBarcodeScannerOptions): GmsBarcodeScanner = GmsBarcodeScanner(activity)
}

class GmsBarcodeScanner(private val activity: Activity) {
    fun startScan(): ScanTask {
        val task = ScanTask(activity)
        task.start()
        return task
    }
}

class ScanTask(private val activity: Activity) {
    private val action = "${activity.packageName}.EXPIRY_BARCODE_SCAN"
    private val requestId = UUID.randomUUID().toString()
    private var listener: ((Barcode) -> Unit)? = null
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getStringExtra(EXTRA_REQUEST_ID) != requestId) return
            val value = intent.getStringExtra(EXTRA_VALUE)?.trim().orEmpty()
            unregister()
            if (value.isNotEmpty()) listener?.invoke(Barcode(value))
        }
    }

    fun addOnSuccessListener(callback: (Barcode) -> Unit): ScanTask {
        listener = callback
        return this
    }

    internal fun start() {
        val filter = IntentFilter(action)
        if (Build.VERSION.SDK_INT >= 33) {
            activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION") activity.registerReceiver(receiver, filter)
        }
        activity.startActivity(Intent(activity, ScanProxyActivity::class.java).putExtra(EXTRA_REQUEST_ID, requestId))
    }

    private fun unregister() {
        runCatching { activity.unregisterReceiver(receiver) }
    }

    companion object {
        const val EXTRA_REQUEST_ID = "expiry_scan_request_id"
        const val EXTRA_VALUE = "expiry_scan_value"
    }
}
