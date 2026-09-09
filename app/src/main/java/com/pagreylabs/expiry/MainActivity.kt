package com.pagreylabs.expiry

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch("android.permission.POST_NOTIFICATIONS")
        setContent { ExpiryTheme { ExpiryApp() } }
    }
}

private enum class ExpiryStatus { EXPIRED, TODAY, SOON, OK }

private fun status(item: ExpiryItem): ExpiryStatus {
    val now = Calendar.getInstance()
    val startToday = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val target = Calendar.getInstance().apply { timeInMillis = item.expiryMillis }
    val days = ((target.timeInMillis - startToday.timeInMillis) / 86_400_000L).toInt()
    return when {
        days < 0 -> ExpiryStatus.EXPIRED
        days == 0 -> ExpiryStatus.TODAY
        days <= 7 -> ExpiryStatus.SOON
        else -> ExpiryStatus.OK
    }
}

private fun statusText(item: ExpiryItem) = when (status(item)) {
    ExpiryStatus.EXPIRED -> "Caducado"
    ExpiryStatus.TODAY -> "Caduca hoy"
    ExpiryStatus.SOON -> "Caduca en ${daysUntil(item.expiryMillis)} días"
    ExpiryStatus.OK -> "En plazo"
}

private fun daysUntil(millis: Long): Int {
    val start = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    return max(0, ((millis - start.timeInMillis) / 86_400_000L).toInt())
}

private fun dateText(millis: Long) = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(millis))

private enum class Filter { ALL, EXPIRED, TODAY, SOON, OK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpiryApp() {
    val context = LocalContext.current
    val repository = remember { ExpiryRepository(context.applicationContext) }
    var items by remember { mutableStateOf(repository.all()) }
    var search by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(Filter.ALL) }
    var editing by remember { mutableStateOf<ExpiryItem?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ExpiryItem?>(null) }

    val filtered = remember(items, search, filter) {
        items.filter { item ->
            val matchesSearch = search.isBlank() || item.name.contains(search, true) || item.category.contains(search, true)
            val matchesFilter = when (filter) {
                Filter.ALL -> true
                Filter.EXPIRED -> status(item) == ExpiryStatus.EXPIRED
                Filter.TODAY -> status(item) == ExpiryStatus.TODAY
                Filter.SOON -> status(item) == ExpiryStatus.SOON
                Filter.OK -> status(item) == ExpiryStatus.OK
            }
            matchesSearch && matchesFilter
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { if (searching) OutlinedTextField(search, { search = it }, placeholder = { Text("Buscar producto…") }, singleLine = true) else Text("Expiry") },
                actions = { IconButton(onClick = { searching = !searching; if (!searching) search = "" }) { Icon(Icons.Default.Search, "Buscar") } }
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "Añadir producto") } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            FilterRow(filter) { filter = it }
            if (filtered.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(if (items.isEmpty()) "No tienes productos registrados" else "No hay productos que coincidan", style = MaterialTheme.typography.titleMedium)
                    if (items.isEmpty()) { Spacer(Modifier.height(8.dp)); Text("Pulsa + para añadir el primero.") }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(filtered, key = { it.id }) { item ->
                        ExpiryCard(item, onEdit = { editing = item }, onDelete = { deleteTarget = item })
                    }
                }
            }
        }
    }

    if (showAdd) ExpiryDialog(null, { showAdd = false }, { item -> repository.save(item); scheduleReminder(context, item); items = repository.all(); showAdd = false })
    editing?.let { item -> ExpiryDialog(item, { editing = null }, { updated -> cancelReminder(context, item); repository.save(updated); scheduleReminder(context, updated); items = repository.all(); editing = null }) }
    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Eliminar producto") },
            text = { Text("¿Eliminar ${item.name} de Expiry?") },
            confirmButton = { Button(onClick = { cancelReminder(context, item); repository.delete(item.id); items = repository.all(); deleteTarget = null }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun FilterRow(selected: Filter, onSelect: (Filter) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(Filter.ALL to "Todos", Filter.EXPIRED to "Caducados", Filter.TODAY to "Hoy", Filter.SOON to "Próximos", Filter.OK to "En plazo").forEach { (filter, label) ->
            FilterChip(selected = selected == filter, onClick = { onSelect(filter) }, label = { Text(label) })
        }
    }
}

@Composable
private fun ExpiryCard(item: ExpiryItem, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                if (item.category.isNotBlank()) Text(item.category, style = MaterialTheme.typography.bodySmall)
                Text("Caducidad: ${dateText(item.expiryMillis)}", style = MaterialTheme.typography.bodyMedium)
                Text(statusText(item), style = MaterialTheme.typography.labelLarge)
                Text("Aviso: ${item.reminderDays} días antes", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Eliminar") }
        }
    }
}

@Composable
private fun ExpiryDialog(existing: ExpiryItem?, onDismiss: () -> Unit, onSave: (ExpiryItem) -> Unit) {
    val context = LocalContext.current
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var category by remember(existing?.id) { mutableStateOf(existing?.category ?: "") }
    var reminder by remember(existing?.id) { mutableStateOf((existing?.reminderDays ?: 7).toString()) }
    var date by remember(existing?.id) { mutableLongStateOf(existing?.expiryMillis ?: System.currentTimeMillis()) }
    var error by remember { mutableStateOf(false) }
    val isEdit = existing != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Editar producto" else "Nuevo producto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it; error = false }, label = { Text("Producto") }, singleLine = true, isError = error, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(category, { category = it }, label = { Text("Categoría (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    val c = Calendar.getInstance().apply { timeInMillis = date }
                    DatePickerDialog(context, { _, y, m, d -> c.set(y, m, d, 12, 0, 0); c.set(Calendar.MILLISECOND, 0); date = c.timeInMillis }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("Caducidad: ${dateText(date)}") }
                OutlinedTextField(reminder, { if (it.all(Char::isDigit)) reminder = it }, label = { Text("Avisar con días de antelación") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = {
            if (name.isBlank()) { error = true; return@Button }
            val id = existing?.id ?: UUID.randomUUID().mostSignificantBits
            onSave(ExpiryItem(id, name.trim(), category.trim(), date, reminder.toIntOrNull()?.coerceIn(0, 365) ?: 7))
        }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun reminderPendingIntent(context: Context, id: Long, name: String): PendingIntent {
    val intent = Intent(context, ExpiryAlarmReceiver::class.java).apply { putExtra("name", name); putExtra("id", id) }
    val requestCode = (id xor (id ushr 32)).toInt()
    return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

private fun scheduleReminder(context: Context, item: ExpiryItem) {
    val trigger = item.expiryMillis - item.reminderDays * 86_400_000L
    if (trigger <= System.currentTimeMillis()) return
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, reminderPendingIntent(context, item.id, item.name))
}

private fun cancelReminder(context: Context, item: ExpiryItem) {
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarm.cancel(reminderPendingIntent(context, item.id, item.name))
}

@Composable
private fun ExpiryTheme(content: @Composable () -> Unit) { MaterialTheme(content = content) }
