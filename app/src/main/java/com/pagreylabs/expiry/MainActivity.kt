package com.pagreylabs.expiry

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun ExpiryApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("expiry_store", Context.MODE_PRIVATE) }
    var products by remember { mutableStateOf(ExpiryRepository(context).all()) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(ExpiryFilter.ALL) }
    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ExpiryItem?>(null) }
    var outcomeItem by remember { mutableStateOf<ExpiryItem?>(null) }
    var showStats by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var scannerBarcode by remember { mutableStateOf("") }
    var scannerName by remember { mutableStateOf("") }
    var scannerCategory by remember { mutableStateOf("") }
    var scannerImageUrl by remember { mutableStateOf("") }
    var refreshToken by remember { mutableIntStateOf(0) }

    fun reload() {
        products = ExpiryRepository(context).all()
        refreshToken++
    }

    LaunchedEffect(refreshToken) { products = ExpiryRepository(context).all() }

    val visible = products.filter { item ->
        val matchesQuery = query.isBlank() || item.name.contains(query, true) || item.category.contains(query, true) || item.barcode.contains(query, true)
        val matchesFilter = when (filter) {
            ExpiryFilter.ALL -> true
            ExpiryFilter.EXPIRED -> status(item) == ExpiryStatus.EXPIRED
            ExpiryFilter.TODAY -> status(item) == ExpiryStatus.TODAY
            ExpiryFilter.SOON -> status(item) == ExpiryStatus.SOON
            ExpiryFilter.OK -> status(item) == ExpiryStatus.OK
        }
        matchesQuery && matchesFilter
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold); Text(stringResource(R.string.pantry_title), style = MaterialTheme.typography.labelMedium) } },
                actions = {
                    IconButton({ showStats = true }) { Icon(Icons.Default.BarChart, stringResource(R.string.stats_tab)) }
                    IconButton({ showSettings = true }) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = true, onClick = {}, icon = { Icon(Icons.Default.Home, null) }, label = { Text(stringResource(R.string.home)) })
                NavigationBarItem(selected = false, onClick = { showScanner = true }, icon = { Icon(Icons.Default.QrCodeScanner, null) }, label = { Text(stringResource(R.string.scan_tab)) })
                NavigationBarItem(selected = false, onClick = { editing = null; scannerBarcode = ""; scannerName = ""; scannerCategory = ""; scannerImageUrl = ""; showDialog = true }, icon = { Icon(Icons.Default.Add, null) }, label = { Text(stringResource(R.string.add_tab)) })
                NavigationBarItem(selected = false, onClick = { showStats = true }, icon = { Icon(Icons.Default.BarChart, null) }, label = { Text(stringResource(R.string.stats_tab)) })
                NavigationBarItem(selected = false, onClick = { showSettings = true }, icon = { Icon(Icons.Default.MoreHoriz, null) }, label = { Text(stringResource(R.string.more_tab)) })
            }
        },
        floatingActionButton = { FloatingActionButton(onClick = { editing = null; scannerBarcode = ""; scannerName = ""; scannerCategory = ""; scannerImageUrl = ""; showDialog = true }) { Icon(Icons.Default.Add, stringResource(R.string.add_product)) } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard(stringResource(R.string.expired_count_label), products.count { status(it) == ExpiryStatus.EXPIRED }, MaterialTheme.colorScheme.error, Modifier.weight(1f))
                SummaryCard(stringResource(R.string.today_count_label), products.count { status(it) == ExpiryStatus.TODAY }, MaterialTheme.colorScheme.error, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard(stringResource(R.string.soon_count_label), products.count { status(it) == ExpiryStatus.SOON }, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                SummaryCard(stringResource(R.string.in_date_count_label), products.count { status(it) == ExpiryStatus.OK }, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            }
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(16.dp), label = { Text(stringResource(R.string.search)) }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) })
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(filter == ExpiryFilter.ALL, { filter = ExpiryFilter.ALL }, label = { Text(stringResource(R.string.all)) })
                FilterChip(filter == ExpiryFilter.EXPIRED, { filter = ExpiryFilter.EXPIRED }, label = { Text(stringResource(R.string.expired)) })
                FilterChip(filter == ExpiryFilter.TODAY, { filter = ExpiryFilter.TODAY }, label = { Text(stringResource(R.string.today)) })
                FilterChip(filter == ExpiryFilter.SOON, { filter = ExpiryFilter.SOON }, label = { Text(stringResource(R.string.soon)) })
                FilterChip(filter == ExpiryFilter.OK, { filter = ExpiryFilter.OK }, label = { Text(stringResource(R.string.in_date)) })
            }
            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(12.dp)); Text(if (products.isEmpty()) stringResource(R.string.no_products) else stringResource(R.string.no_matches)); if (products.isEmpty()) Text(stringResource(R.string.add_first), style = MaterialTheme.typography.bodySmall) } }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(visible, key = { it.id }) { item ->
                        ExpiryCard(item, { editing = item; scannerBarcode = item.barcode; scannerName = item.name; scannerCategory = item.category; scannerImageUrl = item.imageUrl; showDialog = true }, { ExpiryRepository(context).delete(item.id); cancelReminder(context, item.id); reload() }, { outcomeItem = item }, context.resources)
                    }
                }
            }
        }
    }

    if (showDialog) {
        ExpiryDialog(editing, scannerBarcode, scannerName, scannerCategory, scannerImageUrl, { showScanner = true }, { showDialog = false }, { item -> ExpiryRepository(context).save(item); scheduleReminder(context, item); showDialog = false; reload() })
    }
    outcomeItem?.let { item ->
        AlertDialog(onDismissRequest = { outcomeItem = null }, title = { Text(stringResource(R.string.record_result)) }, text = { Text(stringResource(R.string.what_happened, item.name)) }, confirmButton = { Button({ ExpiryRepository(context).recordOutcome(item, OutcomeType.CONSUMED); outcomeItem = null }) { Text(stringResource(R.string.consumed)) } }, dismissButton = { TextButton({ ExpiryRepository(context).recordOutcome(item, OutcomeType.DISCARDED); outcomeItem = null }) { Text(stringResource(R.string.discarded)) } })
    }
    if (showStats) { ConsumptionStatsDialog(products, ExpiryRepository(context).scanHistory(), ExpiryRepository(context).outcomeHistory(), { showStats = false }) }
    if (showSettings) { SettingsDialog({ showSettings = false }, { reload() }) }
    if (showScanner) {
        ScannerActivityLauncher(onResult = { barcode ->
            scannerBarcode = barcode
            val found = ExpiryRepository(context).findProductByBarcode(barcode)
            scannerName = found?.name ?: ""
            scannerCategory = found?.category ?: ""
            scannerImageUrl = ""
            showScanner = false
            showDialog = true
        }, onDismiss = { showScanner = false })
    }
}

@Composable private fun SummaryCard(label: String, count: Int, accent: androidx.compose.ui.graphics.Color, modifier: Modifier) { Card(modifier) { Column(Modifier.padding(12.dp)) { Text(count.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = accent); Text(label, style = MaterialTheme.typography.labelSmall) } } }

private enum class ExpiryFilter { ALL, EXPIRED, TODAY, SOON, OK }
private enum class ExpiryStatus { EXPIRED, TODAY, SOON, OK }

private fun status(item: ExpiryItem): ExpiryStatus {
    val days = ExpiryDateUtils.daysUntil(item.expiryMillis)
    return when { days < 0 -> ExpiryStatus.EXPIRED; days == 0 -> ExpiryStatus.TODAY; days <= 7 -> ExpiryStatus.SOON; else -> ExpiryStatus.OK }
}

private fun statusText(item: ExpiryItem, r: android.content.res.Resources): String = when (status(item)) { ExpiryStatus.EXPIRED -> r.getString(R.string.expired_status); ExpiryStatus.TODAY -> r.getString(R.string.expires_today); ExpiryStatus.SOON -> r.getString(R.string.expires_in, ExpiryDateUtils.daysUntil(item.expiryMillis)); ExpiryStatus.OK -> r.getString(R.string.ok_status) }
private fun dateText(millis: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(millis))

@Composable private fun SettingsDialog(onDismiss: () -> Unit, onChanged: () -> Unit) { val context = LocalContext.current; var confirmClear by remember { mutableStateOf(false) }; val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) runCatching { context.contentResolver.openOutputStream(uri)?.use { out -> out.write(ExpiryBackup.exportJson(context).toByteArray()) } } }; val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { ExpiryBackup.importJson(context, it.readText()) }; onChanged() } }; AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.settings)) }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { SettingsSectionTitle(stringResource(R.string.settings_notifications_section)); Text(stringResource(R.string.settings_notifications, if (context.getSharedPreferences("expiry_store", Context.MODE_PRIVATE).getBoolean("notifications", true)) stringResource(R.string.enabled) else stringResource(R.string.disabled))); TextButton({ context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName) }) }) { Text(stringResource(R.string.settings_open_notifications)) }; SettingsSectionTitle(stringResource(R.string.settings_language_section)); Text(stringResource(R.string.settings_language_auto)); TextButton({ context.startActivity(Intent(Settings.ACTION_LOCALE_SETTINGS)) }) { Text(stringResource(R.string.settings_change_language)) }; SettingsSectionTitle(stringResource(R.string.settings_appearance_section)); Text(stringResource(R.string.settings_appearance_description)); TextButton({ context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }) { Text(stringResource(R.string.theme_system)) }; SettingsSectionTitle(stringResource(R.string.settings_data_section)); TextButton({ exportLauncher.launch("expiry-backup.json") }) { Text(stringResource(R.string.data_export)) }; TextButton({ importLauncher.launch(arrayOf("application/json", "text/plain")) }) { Text(stringResource(R.string.data_import)) }; TextButton({ confirmClear = true }) { Text(stringResource(R.string.data_clear), color = MaterialTheme.colorScheme.error) }; SettingsSectionTitle(stringResource(R.string.settings_privacy_section)); Text(stringResource(R.string.settings_privacy_description)); SettingsSectionTitle(stringResource(R.string.settings_about_section)); Text(stringResource(R.string.settings_about_description)) } }, confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.close)) } })
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text(stringResource(R.string.data_clear)) }, text = { Text(stringResource(R.string.data_clear_confirm)) }, confirmButton = { Button(onClick = { clearExpiryData(context); confirmClear = false; context.recreate() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.data_clear_confirm_action)) } }, dismissButton = { TextButton({ confirmClear = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun SettingsSectionTitle(text: String) { Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }

@Composable private fun ExpiryCard(item: ExpiryItem, onEdit: () -> Unit, onDelete: () -> Unit, onOutcome: () -> Unit, r: android.content.res.Resources) { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), shape = MaterialTheme.shapes.large) { Column(Modifier.fillMaxWidth().padding(12.dp)) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { ProductThumbnail(item); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f).padding(top = 2.dp)) { Text(item.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 2); if (item.category.isNotBlank()) Text(item.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1); Spacer(Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically) { StatusDot(status(item)); Spacer(Modifier.width(7.dp)); Text(statusText(item, r), style = MaterialTheme.typography.labelLarge, color = statusColor(item), fontWeight = FontWeight.SemiBold) }; Spacer(Modifier.height(4.dp)); Text(r.getString(R.string.expiry_date, dateText(item.expiryMillis)), style = MaterialTheme.typography.bodySmall); Text(r.getString(R.string.notice, item.reminderDays), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; IconButton(onEdit, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Edit, stringResource(R.string.edit_product)) }; IconButton(onDelete, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Delete, stringResource(R.string.delete_product)) } }; Spacer(Modifier.height(10.dp)); OutlinedButton(onClick = onOutcome, modifier = Modifier.fillMaxWidth().height(44.dp), contentPadding = PaddingValues(horizontal = 12.dp)) { Icon(Icons.Default.Restaurant, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text(stringResource(R.string.record_consumption_waste), style = MaterialTheme.typography.labelLarge) } } } }

@Composable private fun StatusDot(itemStatus: ExpiryStatus) { Surface(modifier = Modifier.size(10.dp), shape = MaterialTheme.shapes.extraLarge, color = when (itemStatus) { ExpiryStatus.EXPIRED, ExpiryStatus.TODAY -> MaterialTheme.colorScheme.error; ExpiryStatus.SOON -> MaterialTheme.colorScheme.tertiary; ExpiryStatus.OK -> MaterialTheme.colorScheme.primary }) {} }

@Composable private fun ProductThumbnail(item: ExpiryItem) { Surface(Modifier.size(96.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) { if (item.imageUrl.isNotBlank()) AsyncImage(model = item.imageUrl, contentDescription = item.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.Inventory2, contentDescription = item.category.ifBlank { item.name }, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp)) } } }

@Composable private fun statusColor(item: ExpiryItem) = when (status(item)) { ExpiryStatus.EXPIRED, ExpiryStatus.TODAY -> MaterialTheme.colorScheme.error; ExpiryStatus.SOON -> MaterialTheme.colorScheme.tertiary; ExpiryStatus.OK -> MaterialTheme.colorScheme.primary }

@Composable private fun ExpiryDialog(existing: ExpiryItem?, initialBarcode: String, initialName: String, initialCategory: String, initialImageUrl: String, onScanBarcode: () -> Unit, onDismiss: () -> Unit, onSave: (ExpiryItem) -> Unit) { val context = LocalContext.current; var name by remember(existing?.id, initialBarcode, initialName) { mutableStateOf(initialName.ifBlank { existing?.name ?: "" }) }; var category by remember(existing?.id, initialBarcode, initialCategory) { mutableStateOf(initialCategory.ifBlank { existing?.category ?: "" }) }; var barcode by remember(existing?.id, initialBarcode) { mutableStateOf(initialBarcode.ifBlank { existing?.barcode ?: "" }) }; var imageUrl by remember(existing?.id, initialBarcode, initialImageUrl) { mutableStateOf(initialImageUrl.ifBlank { existing?.imageUrl ?: "" }) }; var reminder by remember(existing?.id) { mutableStateOf((existing?.reminderDays ?: 7).toString()) }; var date by remember(existing?.id) { mutableLongStateOf(existing?.expiryMillis ?: System.currentTimeMillis()) }; var error by remember(existing?.id) { mutableStateOf(false) }; AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(if (existing == null) R.string.new_product else R.string.edit_product)) }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { if (imageUrl.isNotBlank()) ProductThumbnail(ExpiryItem(0, name.ifBlank { "Producto" }, category, date, 0, barcode, imageUrl)); OutlinedTextField(name, { name = it; error = false }, label = { Text(stringResource(R.string.product)) }, singleLine = true, isError = error, modifier = Modifier.fillMaxWidth()); OutlinedTextField(category, { category = it }, label = { Text(stringResource(R.string.category_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(barcode, { barcode = it }, label = { Text(stringResource(R.string.barcode_optional)) }, singleLine = true, trailingIcon = { IconButton(onScanBarcode) { Icon(Icons.Default.QrCodeScanner, stringResource(R.string.scan_code)) } }, modifier = Modifier.fillMaxWidth()); Button(onScanBarcode, Modifier.fillMaxWidth()) { Icon(Icons.Default.QrCodeScanner, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.scan_code)) }; Button({ val c = Calendar.getInstance().apply { timeInMillis = date }; DatePickerDialog(context, { _, y, m, d -> c.set(y, m, d, 12, 0, 0); c.set(Calendar.MILLISECOND, 0); date = c.timeInMillis }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show() }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.expiry_date, dateText(date))) }; OutlinedTextField(reminder, { if (it.all(Char::isDigit)) reminder = it }, label = { Text(stringResource(R.string.reminder_days)) }, supportingText = { Text(stringResource(R.string.reminder_help)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) } }, confirmButton = { Button({ if (name.isBlank()) { error = true; return@Button }; val id = existing?.id ?: UUID.randomUUID().mostSignificantBits; onSave(ExpiryItem(id, name.trim(), category.trim(), date, reminder.toIntOrNull()?.coerceIn(0, 365) ?: 7, barcode.trim(), imageUrl.trim())) }) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } }) }

@Composable private fun ConsumptionStatsDialog(items: List<ExpiryItem>, scans: List<ScanEvent>, outcomes: List<OutcomeEvent>, onDismiss: () -> Unit) { val r = LocalContext.current.resources; val now = System.currentTimeMillis(); val recent = scans.count { ExpiryStatsUtils.isWithinLastCalendarDays(it.timestamp, 7, now) }; val consumed = outcomes.count { it.outcome == OutcomeType.CONSUMED }; val discarded = outcomes.count { it.outcome == OutcomeType.DISCARDED }; val total = consumed + discarded; val rate = if (total == 0) 0 else discarded * 100 / total; val scanCounts = scans.groupingBy { it.barcode }.eachCount().entries.sortedByDescending { it.value }.take(5); val names = scanCounts.map { (b, c) -> (items.firstOrNull { it.barcode == b }?.name ?: b) to c }; val categories = items.filter { it.category.isNotBlank() }.groupingBy { it.category }.eachCount().entries.sortedByDescending { it.value }.take(5); val discardedNames = outcomes.filter { it.outcome == OutcomeType.DISCARDED }.groupingBy { it.name }.eachCount().entries.sortedByDescending { it.value }.take(5); AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.stats_title)) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(r.getString(R.string.active_products, items.size)); Text(r.getString(R.string.total_scans, scans.size)); Text(r.getString(R.string.scans_7_days, recent)); Text(r.getString(R.string.consumed_registered, consumed)); Text(r.getString(R.string.discarded_registered, discarded)); Text(r.getString(R.string.discard_rate, rate)); if (names.isNotEmpty()) { Text(stringResource(R.string.most_scanned, )); names.forEach { Text(r.getString(R.string.scan_count, it.first, it.second)) } }; if (discardedNames.isNotEmpty()) { Text(stringResource(R.string.most_discarded), fontWeight = FontWeight.SemiBold); discardedNames.forEach { Text(r.getString(R.string.discard_count, it.key.toString(), it.value)) } }; if (categories.isNotEmpty()) { Text(stringResource(R.string.active_categories), fontWeight = FontWeight.SemiBold); categories.forEach { Text(r.getString(R.string.product_count, it.key, it.value)) } } } }, confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.close)) } }) }

private fun scheduleReminder(context: Context, item: ExpiryItem) { if (item.reminderDays < 0) return; val trigger = ExpiryDateUtils.reminderTrigger(item.expiryMillis, item.reminderDays); if (trigger <= System.currentTimeMillis()) return; val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager; val intent = Intent(context, ExpiryAlarmReceiver::class.java).apply { putExtra("id", item.id) }; val requestCode = (item.id xor (item.id ushr 32)).toInt(); val pi = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE); am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi) }
private fun cancelReminder(context: Context, id: Long) { val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager; val intent = Intent(context, ExpiryAlarmReceiver::class.java).apply { putExtra("id", id) }; val requestCode = (id xor (id ushr 32)).toInt(); val pi = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE); am.cancel(pi); pi.cancel() }

@Composable private fun ScannerActivityLauncher(onResult: (String) -> Unit, onDismiss: () -> Unit) { val context = LocalContext.current; LaunchedEffect(Unit) { val intent = Intent(context, ScannerActivity::class.java); context.startActivity(intent); onDismiss() } }

private fun clearExpiryData(context: Context) { LocalDataManager(context).deleteAllLocalData() }
