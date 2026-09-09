package com.pagreylabs.expiry

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.code_scanner.GmsBarcodeScannerOptions
import com.google.android.gms.code_scanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private var scannedBarcode by mutableStateOf<String?>(null)
    private var scannedProductName by mutableStateOf("")
    private var scannedProductCategory by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            ExpiryTheme {
                ExpiryApp(scannedBarcode, scannedProductName, scannedProductCategory, { launchBarcodeScanner() }) {
                    scannedBarcode = null
                    scannedProductName = ""
                    scannedProductCategory = ""
                }
            }
        }
    }

    private fun launchBarcodeScanner() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(this, options).startScan().addOnSuccessListener { barcode ->
            barcode.rawValue?.trim()?.takeIf { it.isNotEmpty() }?.let { code ->
                scannedBarcode = code
                ExpiryRepository(applicationContext).recordScan(code)
                ProductLookup.lookup(code) { result ->
                    runOnUiThread {
                        scannedProductName = result?.name.orEmpty()
                        scannedProductCategory = result?.category.orEmpty()
                    }
                }
            }
        }
    }
}

private enum class ExpiryStatus { EXPIRED, TODAY, SOON, OK }
private fun startOfToday(): Calendar = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
private fun dayCalendar(millis: Long): Calendar = Calendar.getInstance().apply { timeInMillis = millis; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
private fun daysUntil(millis: Long): Int = ((dayCalendar(millis).timeInMillis - startOfToday().timeInMillis) / 86_400_000L).toInt()
private fun status(item: ExpiryItem): ExpiryStatus = when { item.expiryMillis < System.currentTimeMillis() -> ExpiryStatus.EXPIRED; daysUntil(item.expiryMillis) == 0 -> ExpiryStatus.TODAY; daysUntil(item.expiryMillis) in 1..7 -> ExpiryStatus.SOON; else -> ExpiryStatus.OK }
private fun statusText(item: ExpiryItem) = when (status(item)) { ExpiryStatus.EXPIRED -> "Caducado"; ExpiryStatus.TODAY -> "Caduca hoy"; ExpiryStatus.SOON -> "Caduca en ${daysUntil(item.expiryMillis)} días"; ExpiryStatus.OK -> "En plazo" }
private fun dateText(millis: Long) = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(millis))
private enum class Filter { ALL, EXPIRED, TODAY, SOON, OK }
private fun filterLabel(f: Filter) = when (f) { Filter.ALL -> "Todos"; Filter.EXPIRED -> "Caducados"; Filter.TODAY -> "Hoy"; Filter.SOON -> "Próximos"; Filter.OK -> "En plazo" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpiryApp(scannedBarcode: String?, scannedProductName: String, scannedProductCategory: String, onScanBarcode: () -> Unit, onBarcodeConsumed: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { ExpiryRepository(context.applicationContext) }
    var items by remember { mutableStateOf(repository.all()) }
    var search by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(Filter.ALL) }
    var editing by remember { mutableStateOf<ExpiryItem?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ExpiryItem?>(null) }
    var outcomeTarget by remember { mutableStateOf<ExpiryItem?>(null) }

    val filtered = remember(items, search, filter) {
        items.filter { item ->
            val match = search.isBlank() || item.name.contains(search, true) || item.category.contains(search, true) || item.barcode.contains(search, true)
            val f = when (filter) {
                Filter.ALL -> true
                Filter.EXPIRED -> status(item) == ExpiryStatus.EXPIRED
                Filter.TODAY -> status(item) == ExpiryStatus.TODAY
                Filter.SOON -> status(item) == ExpiryStatus.SOON
                Filter.OK -> status(item) == ExpiryStatus.OK
            }
            match && f
        }.sortedWith(compareBy<ExpiryItem>({ when (status(it)) { ExpiryStatus.EXPIRED -> 0; ExpiryStatus.TODAY -> 1; ExpiryStatus.SOON -> 2; ExpiryStatus.OK -> 3 } }, { it.expiryMillis }, { it.name.lowercase(Locale.getDefault()) }))
    }
    val counts = remember(items) { Filter.values().associateWith { f -> items.count { item -> when (f) { Filter.ALL -> true; Filter.EXPIRED -> status(item) == ExpiryStatus.EXPIRED; Filter.TODAY -> status(item) == ExpiryStatus.TODAY; Filter.SOON -> status(item) == ExpiryStatus.SOON; Filter.OK -> status(item) == ExpiryStatus.OK } } } }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                if (searching) OutlinedTextField(value = search, onValueChange = { search = it }, placeholder = { Text("Buscar producto…") }, singleLine = true, modifier = Modifier.fillMaxWidth()) else Text("Expiry")
            }, actions = {
                IconButton(onClick = { searching = !searching; if (!searching) search = "" }) { Icon(Icons.Default.Search, "Buscar") }
                IconButton(onClick = { showStats = true }) { Icon(Icons.Default.BarChart, "Consumo") }
                IconButton(onClick = onScanBarcode) { Icon(Icons.Default.QrCodeScanner, "Escanear código") }
            })
        },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "Añadir producto") } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Filter.values().forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text("${filterLabel(f)} (${counts[f] ?: 0})") }) }
            }
            if (filtered.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(if (items.isEmpty()) "No tienes productos registrados" else "No hay productos que coincidan", style = MaterialTheme.typography.titleMedium)
                    if (items.isEmpty()) { Spacer(Modifier.height(8.dp)); Text("Pulsa + para añadir el primero.") }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(filtered, key = { it.id }) { item ->
                        ExpiryCard(item, { editing = item }, { deleteTarget = item }, { outcomeTarget = item })
                    }
                }
            }
        }
    }

    if (showAdd) ExpiryDialog(null, scannedBarcode ?: "", scannedProductName, scannedProductCategory, onScanBarcode, { showAdd = false; onBarcodeConsumed() }) { item ->
        repository.save(item)
        scheduleReminder(context, item)
        items = repository.all()
        showAdd = false
        onBarcodeConsumed()
    }
    editing?.let { item -> ExpiryDialog(item, scannedBarcode ?: item.barcode, scannedProductName.ifBlank { item.name }, scannedProductCategory.ifBlank { item.category }, onScanBarcode, { editing = null; onBarcodeConsumed() }) { updated ->
        cancelReminder(context, item.id)
        repository.save(updated)
        scheduleReminder(context, updated)
        items = repository.all()
        editing = null
        onBarcodeConsumed()
    } }
    deleteTarget?.let { item -> AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text("Eliminar producto") }, text = { Text("¿Eliminar ${item.name} de Expiry?") }, confirmButton = { Button(onClick = { cancelReminder(context, item.id); repository.delete(item.id); items = repository.all(); deleteTarget = null }) { Text("Eliminar") } }, dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") } }) }
    outcomeTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { outcomeTarget = null },
            title = { Text("Registrar resultado") },
            text = { Text("¿Qué ha ocurrido con ${item.name}?") },
            confirmButton = { Button(onClick = { repository.recordOutcome(item, OutcomeType.CONSUMED); cancelReminder(context, item.id); repository.delete(item.id); items = repository.all(); outcomeTarget = null }) { Text("Consumido") } },
            dismissButton = { TextButton(onClick = { repository.recordOutcome(item, OutcomeType.DISCARDED); cancelReminder(context, item.id); repository.delete(item.id); items = repository.all(); outcomeTarget = null }) { Text("Desechado") } }
        )
    }
    if (showStats) ConsumptionStatsDialog(items, repository.scanHistory(), repository.outcomeHistory()) { showStats = false }
    LaunchedEffect(scannedBarcode) { if (scannedBarcode != null && !showAdd && editing == null) showAdd = true }
}

@Composable
private fun ExpiryCard(item: ExpiryItem, onEdit: () -> Unit, onDelete: () -> Unit, onOutcome: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                    if (item.category.isNotBlank()) Text(item.category, style = MaterialTheme.typography.bodySmall)
                    Text("Caducidad: ${dateText(item.expiryMillis)}", style = MaterialTheme.typography.bodyMedium)
                    Text(statusText(item), style = MaterialTheme.typography.labelLarge)
                    Text("Aviso: ${item.reminderDays} días antes", style = MaterialTheme.typography.bodySmall)
                    if (item.barcode.isNotBlank()) Text("Código: ${item.barcode}", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Eliminar") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOutcome, modifier = Modifier.fillMaxWidth()) { Text("Registrar consumo / desperdicio") }
        }
    }
}

@Composable
private fun ExpiryDialog(existing: ExpiryItem?, initialBarcode: String, initialName: String, initialCategory: String, onScanBarcode: () -> Unit, onDismiss: () -> Unit, onSave: (ExpiryItem) -> Unit) {
    val context = LocalContext.current
    var name by remember(existing?.id, initialBarcode, initialName) { mutableStateOf(initialName.ifBlank { existing?.name ?: "" }) }
    var category by remember(existing?.id, initialBarcode, initialCategory) { mutableStateOf(initialCategory.ifBlank { existing?.category ?: "" }) }
    var barcode by remember(existing?.id, initialBarcode) { mutableStateOf(initialBarcode.ifBlank { existing?.barcode ?: "" }) }
    var reminder by remember(existing?.id) { mutableStateOf((existing?.reminderDays ?: 7).toString()) }
    var date by remember(existing?.id) { mutableLongStateOf(existing?.expiryMillis ?: System.currentTimeMillis()) }
    var error by remember(existing?.id) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existing == null) "Nuevo producto" else "Editar producto") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it; error = false }, label = { Text("Producto") }, singleLine = true, isError = error, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Categoría (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = barcode, onValueChange = { barcode = it }, label = { Text("Código de barras (opcional)") }, singleLine = true, trailingIcon = { IconButton(onClick = onScanBarcode) { Icon(Icons.Default.QrCodeScanner, "Escanear código") } }, modifier = Modifier.fillMaxWidth())
            Button(onClick = onScanBarcode, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.QrCodeScanner, null); Spacer(Modifier.width(8.dp)); Text("Escanear código") }
            Button(onClick = { val c = Calendar.getInstance().apply { timeInMillis = date }; DatePickerDialog(context, { _, y, m, d -> c.set(y, m, d, 12, 0, 0); c.set(Calendar.MILLISECOND, 0); date = c.timeInMillis }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show() }, modifier = Modifier.fillMaxWidth()) { Text("Caducidad: ${dateText(date)}") }
            OutlinedTextField(value = reminder, onValueChange = { if (it.all(Char::isDigit)) reminder = it }, label = { Text("Avisar con días de antelación") }, supportingText = { Text("0 = el mismo día · máximo 365") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }, confirmButton = { Button(onClick = { if (name.isBlank()) { error = true; return@Button }; val id = existing?.id ?: UUID.randomUUID().mostSignificantBits; onSave(ExpiryItem(id, name.trim(), category.trim(), date, reminder.toIntOrNull()?.coerceIn(0, 365) ?: 7, barcode.trim())) }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun ConsumptionStatsDialog(items: List<ExpiryItem>, scans: List<ScanEvent>, outcomes: List<OutcomeEvent>, onDismiss: () -> Unit) {
    val now = System.currentTimeMillis()
    val weekAgo = now - 7L * 86_400_000L
    val recentScans = scans.count { it.timestamp >= weekAgo }
    val consumed = outcomes.count { it.outcome == OutcomeType.CONSUMED }
    val discarded = outcomes.count { it.outcome == OutcomeType.DISCARDED }
    val outcomeTotal = consumed + discarded
    val discardRate = if (outcomeTotal == 0) 0 else (discarded * 100 / outcomeTotal)
    val scanCounts = scans.groupingBy { it.barcode }.eachCount().entries.sortedByDescending { it.value }.take(5)
    val names = scanCounts.map { (barcode, count) -> (items.firstOrNull { it.barcode == barcode }?.name ?: barcode) to count }
    val categories = items.filter { it.category.isNotBlank() }.groupingBy { it.category }.eachCount().entries.sortedByDescending { it.value }.take(5)
    val discardedNames = outcomes.filter { it.outcome == OutcomeType.DISCARDED }.groupingBy { it.name }.eachCount().entries.sortedByDescending { it.value }.take(5)

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Consumo y desperdicio") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Productos activos: ${items.size}")
            Text("Escaneos totales: ${scans.size}")
            Text("Escaneos últimos 7 días: $recentScans")
            Text("Consumidos registrados: $consumed")
            Text("Desechados registrados: $discarded")
            Text("Tasa de desperdicio registrada: $discardRate%")
            if (names.isNotEmpty()) { Text("Más escaneados", fontWeight = FontWeight.SemiBold); names.forEach { Text("• ${it.first}: ${it.second} escaneos") } }
            if (discardedNames.isNotEmpty()) { Text("Productos más desechados", fontWeight = FontWeight.SemiBold); discardedNames.forEach { Text("• ${it.key}: ${it.value}") } }
            if (categories.isNotEmpty()) { Text("Categorías activas", fontWeight = FontWeight.SemiBold); categories.forEach { Text("• ${it.key}: ${it.value} productos") } }
            Text("Los resultados de consumo/desperdicio solo se contabilizan cuando los registras manualmente. Los datos permanecen en el dispositivo.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}

internal fun reminderPendingIntent(context: Context, id: Long): PendingIntent {
    val intent = Intent(context, ExpiryAlarmReceiver::class.java).apply { putExtra("id", id) }
    val requestCode = (id xor (id ushr 32)).toInt()
    return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

private fun scheduleReminder(context: Context, item: ExpiryItem) {
    val trigger = item.expiryMillis - item.reminderDays * 86_400_000L
    if (trigger <= System.currentTimeMillis()) return
    (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, reminderPendingIntent(context, item.id))
}

private fun cancelReminder(context: Context, id: Long) {
    (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(reminderPendingIntent(context, id))
}

@Composable
private fun ExpiryTheme(content: @Composable () -> Unit) { MaterialTheme(content = content) }
