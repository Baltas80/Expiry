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
    val sectionTitle = when (filter) {
        Filter.ALL -> stringResource(R.string.upcoming_products)
        Filter.EXPIRED -> stringResource(R.string.expired)
        Filter.TODAY -> stringResource(R.string.expires_today)
        Filter.SOON -> stringResource(R.string.soon)
        Filter.OK -> stringResource(R.string.in_date)
    }

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
                NavigationBarItem(selected = filter == Filter.ALL, onClick = { filter = Filter.ALL }, icon = { Icon(Icons.Default.Home, stringResource(R.string.home)) }, label = { Text(stringResource(R.string.home), maxLines = 1) })
                NavigationBarItem(selected = false, onClick = onScanBarcode, icon = { Icon(Icons.Default.QrCodeScanner, stringResource(R.string.scan_tab)) }, label = { Text(stringResource(R.string.scan_tab), maxLines = 1) })
                NavigationBarItem(selected = false, onClick = { showAdd = true }, icon = { Icon(Icons.Default.Add, stringResource(R.string.add_tab)) }, label = { Text(stringResource(R.string.add_tab), maxLines = 1) })
                NavigationBarItem(selected = false, onClick = { showStats = true }, icon = { Icon(Icons.Default.BarChart, stringResource(R.string.stats_tab)) }, label = { Text(stringResource(R.string.stats_tab), maxLines = 1) })
                NavigationBarItem(selected = false, onClick = { showSettings = true }, icon = { Icon(Icons.Default.MoreHoriz, stringResource(R.string.more_tab)) }, label = { Text(stringResource(R.string.more_tab), maxLines = 1) })
            }
        },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = MaterialTheme.shapes.extraLarge) { Icon(Icons.Default.Add, stringResource(R.string.add_product), modifier = Modifier.size(30.dp)) } }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = MaterialTheme.shapes.large) { Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) { Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) { Icon(Icons.Default.Eco, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(30.dp)) }; Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(stringResource(R.string.pantry_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(stringResource(R.string.pantry_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { SummaryCard(counts[Filter.EXPIRED] ?: 0, stringResource(R.string.expired_count_label), MaterialTheme.colorScheme.error, Modifier.weight(1f)); SummaryCard(counts[Filter.TODAY] ?: 0, stringResource(R.string.today_count_label), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f)); SummaryCard(counts[Filter.SOON] ?: 0, stringResource(R.string.soon_count_label), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f)); SummaryCard(counts[Filter.OK] ?: 0, stringResource(R.string.in_date_count_label), MaterialTheme.colorScheme.primary, Modifier.weight(1f)) } }
            item { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Filter.values().forEach { f -> FilterChip(filter == f, { filter = f }, label = { Text("${label(f)} (${counts[f] ?: 0})") }) } } }
            item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(sectionTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("${filtered.size}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) } }
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
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }, text = { Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) { SettingsSectionTitle(stringResource(R.string.settings_notifications_section)); Text(stringResource(R.string.settings_notifications, if (notificationsEnabled) stringResource(R.string.enabled) else stringResource(R.string.disabled))); if (!notificationsEnabled) TextButton({ context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName) }) }) { Text(stringResource(R.string.settings_open_notifications)) }; SettingsSectionTitle(stringResource(R.string.settings_language_section)); Text(stringResource(R.string.settings_language_auto)); TextButton({ context.startActivity(Intent(Settings.ACTION_LOCALE_SETTINGS)) }) { Text(stringResource(R.string.settings_change_language)) }; SettingsSectionTitle(stringResource(R.string.settings_appearance_section)); Text(stringResource(R.string.settings_appearance_description)); SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { listOf("system" to R.string.theme_system, "light" to R.string.theme_light, "dark" to R.string.theme_dark).forEachIndexed { index, (mode, labelRes) -> SegmentedButton(selected = themeMode == mode, onClick = { themeMode = mode; prefs.edit().putString("theme_mode", mode).apply() }, shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(stringResource(labelRes)) } } }; SettingsSectionTitle(stringResource(R.string.settings_data_section)); TextButton({ exportLauncher.launch("expiry-backup.json") }) { Text(stringResource(R.string.data_export)) }; TextButton({ importLauncher.launch(arrayOf("application/json", "text/plain")) }) { Text(stringResource(R.string.data_import)) }; TextButton({ confirmClear = true }) { Text(stringResource(R.string.data_clear), color = MaterialTheme.colorScheme.error) }; SettingsSectionTitle(stringResource(R.string.settings_privacy_section)); Text(stringResource(R.string.settings_privacy_description)); SettingsSectionTitle(stringResource(R.string.settings_about_section)); Text(stringResource(R.string.settings_about_description)); Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } })
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text(stringResource(R.string.data_clear)) }, text = { Text(stringResource(R.string.data_clear_confirm)) }, confirmButton = { Button({ clearExpiryData(context); confirmClear = false; onDismiss(); context.recreate() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.data_clear_confirm_action)) } }, dismissButton = { TextButton({ confirmClear = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun SettingsSectionTitle(text: String) { Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
