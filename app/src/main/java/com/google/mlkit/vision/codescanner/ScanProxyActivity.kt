package com.google.mlkit.vision.codescanner

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.google.zxing.integration.android.IntentIntegrator

class ScanProxyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        IntentIntegrator(this).apply {
            setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES)
            setBeepEnabled(false)
            setOrientationLocked(false)
            setPrompt("Scan barcode")
            initiateScan()
        }
    }

    @Deprecated("ZXing's IntentIntegrator uses the legacy activity result API internally")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            sendBroadcast(
                Intent("${packageName}.EXPIRY_BARCODE_SCAN")
                    .setPackage(packageName)
                    .putExtra(ScanTask.EXTRA_REQUEST_ID, intent.getStringExtra(ScanTask.EXTRA_REQUEST_ID))
                    .putExtra(ScanTask.EXTRA_VALUE, result.contents)
            )
            finish()
        }
    }
}
