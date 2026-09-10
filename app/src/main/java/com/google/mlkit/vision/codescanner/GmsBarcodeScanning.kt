package com.google.mlkit.vision.codescanner

import android.app.Activity
import android.content.Intent
import com.google.mlkit.vision.barcode.common.Barcode
import com.pagreylabs.expiry.ScannerActivity

object GmsBarcodeScanning {
    private var successListener: ((Barcode) -> Unit)? = null

    fun getClient(activity: Activity, options: GmsBarcodeScannerOptions): GmsBarcodeScanner = GmsBarcodeScanner(activity)

    internal fun deliver(barcode: Barcode) {
        successListener?.invoke(barcode)
        successListener = null
    }

    class GmsBarcodeScanner(private val activity: Activity) {
        fun startScan(): ScanTask {
            activity.startActivity(Intent(activity, ScannerActivity::class.java))
            return ScanTask()
        }
    }

    class ScanTask {
        fun addOnSuccessListener(listener: (Barcode) -> Unit): ScanTask {
            successListener = listener
            return this
        }
    }
}
