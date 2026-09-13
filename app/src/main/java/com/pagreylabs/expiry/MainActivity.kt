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
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.text.DateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
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
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).enableAutoZoom().build()
        GmsBarcodeScanning.getClient(this, options).startScan().addOnSuccessListener { barcode ->
            barcode.rawValue?.trim()?.takeIf { it.isNotEmpty() }?.let { code ->
                scannedBarcode = code
                ExpiryRepository(applicationContext).recordScan(code)
                ProductLookup.lookup(code) { result ->
                    runOnUiThread {
                        when {
                            result == null -> Toast.makeText(this, R.string.product_lookup_error, Toast.LENGTH_SHORT).show()
                            !result.found -> Toast.makeText(this, R.string.product_not_found, Toast.LENGTH_SHORT).show()
                            else -> { scannedProductName = result.name; scannedProductCategory = result.category; scannedProductImageUrl = result.imageUrl }
                        }
                    }
                }
            }
        }
    }
}

private enum class ExpiryStatus { EXPIRED, TODAY, SOON, OK }
private enum class Filter { ALL, EXPIRED, TODAY, SOON, OK }

private fun status(item: ExpiryItem): ExpiryStatus = when (val d = ExpiryDateUtils.daysUntil(item.expiryMillis)) {
    in Int.MIN_VALUE..-1 -> ExpiryStatus.EXPIRED
    0 -> ExpiryStatus.TODAY
    in 1..7 -> ExpiryStatus.SOON
    else -> ExpiryStatus.OK
}

private fun statusText(item: ExpiryItem, r: android.content.res.Resources): String = when (status(item)) {
    ExpiryStatus.EXPIRED -> r.getString(R.string.expired_status)
    ExpiryStatus.TODAY -> r.getString(R.string.expires_today)
    ExpiryStatus.SOON -> r.getString(R.string.expires_in, ExpiryDateUtils.daysUntil(item.expiryMillis))
    ExpiryStatus.OK -> r.getString(R.string.ok_status)
}
private fun dateText(millis: Long): String = DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(millis))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpiryApp(scannedBarcode: String?, scannedProductName: String, scannedProductCategory: String, scannedProductImageUrl: String, onScanBarcode: () -> Unit, onBarcodeConsumed: () -> Unit) {
    val context = LocalContext.current
    val r = context.resources
    val repository = remember { ExpiryRepository(context.applicationContext) }
    var items by remember { mutableStateOf(repository.all()) }
    var search by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(Filter.ALL) }
    var editing by remember { mutableStateOf<ExpiryItem?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ExpiryItem?>(null) }
    var outcomeTarget by remember { mutableStateOf<ExpiryItem?>(null) }

    val filtered = remember(items, search, filter) {
        items.filter { item ->
            (search.isBlank() || item.name.contains(search, true) || item.category.contains(search, true) || item.barcode.contains(search, true)) && when (filter) {
                Filter.ALL -> true; Filter.EXPIRED -> status(item) == ExpiryStatus.EXPIRED; Filter.TODAY -> status(item) == ExpiryStatus.TODAY
                Filter.SOON -> status(item) == ExpiryStatus.SOON; Filter.OK -> status(item) == ExpiryStatus.OK
            }
        }.sortedWith(compareBy({ when (status(it)) { ExpiryStatus.EXPIRED -> 0; ExpiryStatus.TODAY -> 1; ExpiryStatus.SOON -> 2; ExpiryStatus.OK -> 3 } }, { it.expiryMillis }, { it.name.lowercase(Locale.getDefault()) }))
    }
    val counts = remember(items) { Filter.values().associateWith { f -> items.count { when (f) { Filter.ALL -> true; Filter.EXPIRED -> status(it) == ExpiryStatus.EXPIRED; Filter.TODAY -> status(it) == ExpiryStatus.TODAY; Filter.SOON -> status(it) == ExpiryStatus.SOON; Filter.OK -> status(it) == ExpiryStatus.OK } } } }
    fun label(f: Filter) = when (f) { Filter.ALL -> r.getString(R.string.all); Filter.EXPIRED -> r.getString(R.string.expired); Filter.TODAY -> r.getString(R.string.today); Filter.SOON -> r.getString(R.string.soon); Filter.OK -> r.getString(R.string.in_date) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Menu, stringResource(R.string.settings)) } },
                title = { if (searching) OutlinedTextField(search, { search = it }, placeholder = { Text(stringResource(R.string.search_product)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) else Column { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(stringResource(R.string.tagline), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                actions = { IconButton(onClick = { searching = !searching; if (!searching) search = "" }) { Icon(Icons.Default.Search, stringResource(R.string.search)) } }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = true, onClick = { filter = Filter.ALL }, icon = { Icon(Icons.Default.Home, null) }, label = { Text(stringResource(R.string.home), maxLines = 1) })
                NavigationBarItem(selected = false, onClick = onScanBarcode, icon = { Icon(Icons.Default.QrCodeScanner, null) }, label = { Text(stringResource(R.string.scan_tab), maxLines = 1) })
                NavigationBarItem(selected = false, onClick = { showAdd = true }, icon = { Icon(Icons.Default.Add, null) }, label = { Text(stringResource(R.string.add_tab), maxLines = 1) })
                NavigationBarItem(selected = false, onClick = { showStats = true }, icon = { Icon(Icons.Default.BarChart, null) }, label = { Text(stringResource(R.string.stats_tab), maxLines = 1) })
                NavigationBarItem(selected = false, onClick = { showSettings = true }, icon = { Icon(Icons.Default.MoreHoriz, null) }, label = { Text(stringResource(R.string.more_tab), maxLines = 1) })
            }
        },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = MaterialTheme.shapes.extraLarge) { Icon(Icons.Default.Add, stringResource(R.string.add_product), modifier = Modifier.size(30.dp)) } }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = MaterialTheme.shapes.large) { Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) { Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) { Icon(Icons.Default.Eco, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(30.dp)) }; Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(stringResource(R.string.pantry_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(stringResource(R.string.pantry_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { SummaryCard(counts[Filter.EXPIRED] ?: 0, stringResource(R.string.expired_count_label), MaterialTheme.colorScheme.error, Modifier.weight(1f)); SummaryCard(counts[Filter.TODAY] ?: 0, stringResource(R.string.today_count_label), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f)); SummaryCard(counts[Filter.SOON] ?: 0, stringResource(R.string.soon_count_label), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f)); SummaryCard(counts[Filter.OK] ?: 0, stringResource(R.string.in_date_count_label), MaterialTheme.colorScheme.primary, Modifier.weight(1f)) } }
            item { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Filter.values().forEach { f -> FilterChip(filter == f, { filter = f }, label = { Text("${label(f)} (${counts[f] ?: 0})") }) } } }
            item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.upcoming_products), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("${filtered.size}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) } }
            if (filtered.isEmpty()) item { Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) { Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(12.dp)); Text(if (items.isEmpty()) stringResource(R.string.no_products) else stringResource(R.string.no_matches), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); if (items.isEmpty()) { Spacer(Modifier.height(6.dp)); Text(stringResource(R.string.add_first), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } } else items(filtered, key = { it.id }) { item -> ExpiryCard(item, { editing = item }, { deleteTarget = item }, { outcomeTarget = item }, r) }
        }
    }

    if (showAdd) ExpiryDialog(null, scannedBarcode ?: "", scannedProductName, scannedProductCategory, scannedProductImageUrl, onScanBarcode, { showAdd = false; onBarcodeConsumed() }) { item -> repository.save(item); scheduleReminder(context, item); items = repository.all(); showAdd = false; onBarcodeConsumed() }
    editing?.let { item -> ExpiryDialog(item, scannedBarcode ?: item.barcode, scannedProductName.ifBlank { item.name }, scannedProductCategory.ifBlank { item.category }, scannedProductImageUrl.ifBlank { item.imageUrl }, onScanBarcode, { editing = null; onBarcodeConsumed() }) { updated -> cancelReminder(context, item.id); repository.save(updated); scheduleReminder(context, updated); items = repository.all(); editing = null; onBarcodeConsumed() } }
    deleteTarget?.let { item -> AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text(stringResource(R.string.delete_product)) }, text = { Text(stringResource(R.string.delete_confirm, item.name)) }, confirmButton = { Button({ cancelReminder(context, item.id); repository.delete(item.id); items = repository.all(); deleteTarget = null }) { Text(stringResource(R.string.delete_product)) } }, dismissButton = { TextButton({ deleteTarget = null }) { Text(stringResource(R.string.cancel)) } }) }
    outcomeTarget?.let { item -> AlertDialog(onDismissRequest = { outcomeTarget = null }, title = { Text(stringResource(R.string.record_result)) }, text = { Text(stringResource(R.string.what_happened, item.name)) }, confirmButton = { Button({ repository.recordOutcome(item, OutcomeType.CONSUMED); cancelReminder(context, item.id); repository.delete(item.id); items = repository.all(); outcomeTarget = null }) { Text(stringResource(R.string.consumed)) } }, dismissButton = { TextButton({ repository.recordOutcome(item, OutcomeType.DISCARDED); cancelReminder(context, item.id); repository.delete(item.id); items = repository.all(); outcomeTarget = null }) { Text(stringResource(R.string.discarded)) } }) }
    if (showStats) ConsumptionStatsDialog(items, repository.scanHistory(), repository.outcomeHistory()) { showStats = false }
    if (showSettings) ExpirySettingsDialog(onDismiss = { showSettings = false })
    LaunchedEffect(scannedBarcode) { if (scannedBarcode != null && !showAdd && editing == null) showAdd = true }
}

@Composable private fun SummaryCard(count: Int, label: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) { Card(modifier = modifier.heightIn(min = 92.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = MaterialTheme.shapes.medium) { Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(count.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color); Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2) } } }

@Composable
private fun ExpirySettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("expiry_settings", Context.MODE_PRIVATE) }
    val notificationsEnabled = if (Build.VERSION.SDK_INT >= 24) (context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).areNotificationsEnabled() else true
    var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "system") ?: "system") }
    var confirmClear by remember { mutableStateOf(false) }
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(exportExpiryBackup(context).toByteArray(Charsets.UTF_8)) } }.onFailure { Toast.makeText(context, R.string.data_operation_error, Toast.LENGTH_SHORT).show() }
    }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: "" }.onSuccess { raw ->
            if (importExpiryBackup(context, raw)) { Toast.makeText(context, R.string.data_imported, Toast.LENGTH_SHORT).show(); context.recreate() } else Toast.makeText(context, R.string.data_invalid, Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(context, R.string.data_operation_error, Toast.LENGTH_SHORT).show() }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }, text = { Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) { SettingsSectionTitle(stringResource(R.string.settings_notifications_section)); Text(stringResource(R.string.settings_notifications, if (notificationsEnabled) stringResource(R.string.enabled) else stringResource(R.string.disabled)), style = MaterialTheme.typography.bodyMedium); FilledTonalButton(onClick = { if (Build.VERSION.SDK_INT >= 26) context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName) }) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Notifications, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.settings_open_notifications)) }; SettingsSectionTitle(stringResource(R.string.settings_language_section)); Text(stringResource(R.string.settings_language_auto), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); OutlinedButton(onClick = { if (Build.VERSION.SDK_INT >= 33) context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply { data = Uri.parse("package:${context.packageName}") }) else context.startActivity(Intent(Settings.ACTION_LOCALE_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Language, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.settings_change_language)) }; SettingsSectionTitle(stringResource(R.string.settings_appearance_section)); Text(stringResource(R.string.settings_appearance_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("system" to R.string.theme_system, "light" to R.string.theme_light, "dark" to R.string.theme_dark).forEach { (mode, label) -> FilterChip(selected = themeMode == mode, onClick = { themeMode = mode; prefs.edit().putString("theme_mode", mode).apply(); context.recreate() }, label = { Text(stringResource(label)) }) } }; SettingsSectionTitle(stringResource(R.string.settings_data_section)); Text(stringResource(R.string.settings_storage), style = MaterialTheme.typography.bodyMedium); FilledTonalButton(onClick = { exportLauncher.launch("expiry-backup.json") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.data_export)) }; OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileUpload, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.data_import)) }; OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.DeleteSweep, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.data_clear)) }; SettingsSectionTitle(stringResource(R.string.settings_privacy_section)); Text(stringResource(R.string.settings_privacy_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); SettingsSectionTitle(stringResource(R.string.settings_about_section)); Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium); Text(stringResource(R.string.settings_about_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }, confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.close)) } })
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text(stringResource(R.string.data_clear)) }, text = { Text(stringResource(R.string.data_clear_confirm)) }, confirmButton = { Button(onClick = { clearExpiryData(context); confirmClear = false; context.recreate() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.data_clear_confirm_action)) } }, dismissButton = { TextButton({ confirmClear = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun SettingsSectionTitle(text: String) { Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }

@Composable private fun ExpiryCard(item: ExpiryItem, onEdit: () -> Unit, onDelete: () -> Unit, onOutcome: () -> Unit, r: android.content.res.Resources) { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), shape = MaterialTheme.shapes.large) { Column(Modifier.fillMaxWidth().padding(12.dp)) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { ProductThumbnail(item); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f).padding(top = 2.dp)) { Text(item.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 2); if (item.category.isNotBlank()) Text(item.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1); Spacer(Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically) { StatusDot(status(item)); Spacer(Modifier.width(7.dp)); Text(statusText(item, r), style = MaterialTheme.typography.labelLarge, color = statusColor(item), fontWeight = FontWeight.SemiBold) }; Spacer(Modifier.height(4.dp)); Text(r.getString(R.string.expiry_date, dateText(item.expiryMillis)), style = MaterialTheme.typography.bodySmall); Text(r.getString(R.string.notice, item.reminderDays), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; IconButton(onEdit, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Edit, stringResource(R.string.edit_product)) }; IconButton(onDelete, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Delete, stringResource(R.string.delete_product)) } }; Spacer(Modifier.height(10.dp)); OutlinedButton(onClick = onOutcome, modifier = Modifier.fillMaxWidth().height(44.dp), contentPadding = PaddingValues(horizontal = 12.dp)) { Icon(Icons.Default.Restaurant, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text(stringResource(R.string.record_consumption_waste), style = MaterialTheme.typography.labelLarge) } } } }

@Composable private fun StatusDot(itemStatus: ExpiryStatus) { Surface(modifier = Modifier.size(10.dp), shape = MaterialTheme.shapes.extraLarge, color = when (itemStatus) { ExpiryStatus.EXPIRED, ExpiryStatus.TODAY -> MaterialTheme.colorScheme.error; ExpiryStatus.SOON -> MaterialTheme.colorScheme.tertiary; ExpiryStatus.OK -> MaterialTheme.colorScheme.primary }) {} }

@Composable private fun ProductThumbnail(item: ExpiryItem) { Surface(Modifier.size(96.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) { if (item.imageUrl.isNotBlank()) AsyncImage(model = item.imageUrl, contentDescription = item.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.Inventory2, contentDescription = item.category.ifBlank { item.name }, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp)) } } }

@Composable private fun statusColor(item: ExpiryItem) = when (status(item)) { ExpiryStatus.EXPIRED, ExpiryStatus.TODAY -> MaterialTheme.colorScheme.error; ExpiryStatus.SOON -> MaterialTheme.colorScheme.tertiary; ExpiryStatus.OK -> MaterialTheme.colorScheme.primary }

@Composable private fun ExpiryDialog(existing: ExpiryItem?, initialBarcode: String, initialName: String, initialCategory: String, initialImageUrl: String, onScanBarcode: () -> Unit, onDismiss: () -> Unit, onSave: (ExpiryItem) -> Unit) { val context = LocalContext.current; var name by remember(existing?.id, initialBarcode, initialName) { mutableStateOf(initialName.ifBlank { existing?.name ?: "" }) }; var category by remember(existing?.id, initialBarcode, initialCategory) { mutableStateOf(initialCategory.ifBlank { existing?.category ?: "" }) }; var barcode by remember(existing?.id, initialBarcode) { mutableStateOf(initialBarcode.ifBlank { existing?.barcode ?: "" }) }; var imageUrl by remember(existing?.id, initialBarcode, initialImageUrl) { mutableStateOf(initialImageUrl.ifBlank { existing?.imageUrl ?: "" }) }; var reminder by remember(existing?.id) { mutableStateOf((existing?.reminderDays ?: 7).toString()) }; var date by remember(existing?.id) { mutableLongStateOf(existing?.expiryMillis ?: System.currentTimeMillis()) }; var error by remember(existing?.id) { mutableStateOf(false) }; AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(if (existing == null) R.string.new_product else R.string.edit_product)) }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { if (imageUrl.isNotBlank()) ProductThumbnail(ExpiryItem(0, name.ifBlank { "Producto" }, category, date, 0, barcode, imageUrl)); OutlinedTextField(name, { name = it; error = false }, label = { Text(stringResource(R.string.product)) }, singleLine = true, isError = error, modifier = Modifier.fillMaxWidth()); OutlinedTextField(category, { category = it }, label = { Text(stringResource(R.string.category_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(barcode, { barcode = it }, label = { Text(stringResource(R.string.barcode_optional)) }, singleLine = true, trailingIcon = { IconButton(onScanBarcode) { Icon(Icons.Default.QrCodeScanner, stringResource(R.string.scan_code)) } }, modifier = Modifier.fillMaxWidth()); Button(onScanBarcode, Modifier.fillMaxWidth()) { Icon(Icons.Default.QrCodeScanner, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.scan_code)) }; Button({ val c = Calendar.getInstance().apply { timeInMillis = date }; DatePickerDialog(context, { _, y, m, d -> c.set(y, m, d, 12, 0, 0); c.set(Calendar.MILLISECOND, 0); date = c.timeInMillis }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show() }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.expiry_date, dateText(date))) }; OutlinedTextField(reminder, { if (it.all(Char::isDigit)) reminder = it }, label = { Text(stringResource(R.string.reminder_days)) }, supportingText = { Text(stringResource(R.string.reminder_help)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) } }, confirmButton = { Button({ if (name.isBlank()) { error = true; return@Button }; val id = existing?.id ?: UUID.randomUUID().mostSignificantBits; onSave(ExpiryItem(id, name.trim(), category.trim(), date, reminder.toIntOrNull()?.coerceIn(0, 365) ?: 7, barcode.trim(), imageUrl.trim())) }) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } }) }

@Composable private fun ConsumptionStatsDialog(items: List<ExpiryItem>, scans: List<ScanEvent>, outcomes: List<OutcomeEvent>, onDismiss: () -> Unit) { val r = LocalContext.current.resources; val now = System.currentTimeMillis(); val recent = scans.count { ExpiryStatsUtils.isWithinLastCalendarDays(it.timestamp, 7, now) }; val consumed = outcomes.count { it.outcome == OutcomeType.CONSUMED }; val discarded = outcomes.count { it.outcome == OutcomeType.DISCARDED }; val total = consumed + discarded; val rate = if (total == 0) 0 else discarded * 100 / total; val scanCounts = scans.groupingBy { it.barcode }.eachCount().entries.sortedByDescending { it.value }.take(5); val names = scanCounts.map { (b, c) -> (items.firstOrNull { it.barcode == b }?.name ?: b) to c }; val categories = items.filter { it.category.isNotBlank() }.groupingBy { it.category }.eachCount().entries.sortedByDescending { it.value }.take(5); val discardedNames = outcomes.filter { it.outcome == OutcomeType.DISCARDED }.groupingBy { it.name }.eachCount().entries.sortedByDescending { it.value }.take(5); AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.stats_title)) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(r.getString(R.string.active_products, items.size)); Text(r.getString(R.string.total_scans, scans.size)); Text(r.getString(R.string.scans_7_days, recent)); Text(r.getString(R.string.consumed_registered, consumed)); Text(r.getString(R.string.discarded_registered, discarded)); Text(r.getString(R.string.discard_rate, rate)); if (names.isNotEmpty()) { Text(stringResource(R.string.most_scanned), fontWeight = FontWeight.SemiBold); names.forEach { Text(r.getString(R.string.scan_count, it.first, it.second)) } }; if (discardedNames.isNotEmpty()) { Text(stringResource(R.string.most_discarded), fontWeight = FontWeight.SemiBold); discardedNames.forEach { Text(r.getString(R.string.discard_count, it.key, it.value)) } }; if (categories.isNotEmpty()) { Text(stringResource(R.string.active_categories), fontWeight = FontWeight.SemiBold); categories.forEach { Text(r.getString(R.string.product_count, it.key, it.value)) } } } }, confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.close)) } }) }

private fun scheduleReminder(context: Context, item: ExpiryItem) { if (item.reminderDays < 0) return; val trigger = ExpiryDateUtils.reminderTrigger(item.expiryMillis, item.reminderDays); if (trigger <= System.currentTimeMillis()) return; val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager; val intent = Intent(context, ExpiryAlarmReceiver::class.java).apply { putExtra("id", item.id) }; val requestCode = (item.id xor (item.id ushr 32)).toInt(); val pi = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE); am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi) }
private fun cancelReminder(context: Context, id: Long) { val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager; val intent = Intent(context, ExpiryAlarmReceiver::class.java).apply { putExtra("id", id) }; val requestCode = (id xor (id ushr 32)).toInt(); val pi = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE); am.cancel(pi); pi.cancel() }
