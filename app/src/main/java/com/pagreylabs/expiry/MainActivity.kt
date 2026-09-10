package com.pagreylabs.expiry

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.mlkit.vision.barcode.common.Barcode
import java.text.DateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val barcodeScanner = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val code = result.data?.getStringExtra(ScannerActivity.EXTRA_BARCODE)?.trim().orEmpty()
        if (result.resultCode == RESULT_OK && code.isNotEmpty()) {
            scannedBarcode = code
            ExpiryRepository(applicationContext).recordScan(code)
            ProductLookup.lookup(code) { lookupResult ->
                runOnUiThread {
                    when {
                        lookupResult == null -> Toast.makeText(this, R.string.product_lookup_error, Toast.LENGTH_SHORT).show()
                        !lookupResult.found -> Toast.makeText(this, R.string.product_not_found, Toast.LENGTH_SHORT).show()
                        else -> {
                            scannedProductName = lookupResult.name
                            scannedProductCategory = lookupResult.category
                            scannedProductImageUrl = lookupResult.imageUrl
                        }
                    }
                }
            }
        }
    }
    private var scannedBarcode by mutableStateOf<String?>(null)
    private var scannedProductName by mutableStateOf("")
    private var scannedProductCategory by mutableStateOf("")
    private var scannedProductImageUrl by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            ExpiryTheme { ExpiryApp(scannedBarcode, scannedProductName, scannedProductCategory, scannedProductImageUrl, ::launchBarcodeScanner) { clearScan() } }
        }
    }

    private fun clearScan() {
        scannedBarcode = null; scannedProductName = ""; scannedProductCategory = ""; scannedProductImageUrl = ""
    }

    private fun launchBarcodeScanner() {
        barcodeScanner.launch(Intent(this, ScannerActivity::class.java))
    }
