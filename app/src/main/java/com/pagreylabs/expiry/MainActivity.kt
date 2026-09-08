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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch("android.permission.POST_NOTIFICATIONS")
        }
        setContent { ExpiryTheme { ExpiryApp() } }
    }
}

private enum class ExpiryStatus { EXPIRED, TODAY, SOON, OK }

private fun status(item: ExpiryItem): ExpiryStatus {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = item.expiryMillis }
    val startToday = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    val days = ((target.timeInMillis - startToday.timeInMillis) / 86_400_000L).toInt()
    return when {
        item.expiryMillis < now.timeInMillis && days < 0 -> ExpiryStatus.EXPIRED
        days == 0 -> ExpiryStatus.TODAY
        days in 1..7 -> ExpiryStatus.SOON
        else -> ExpiryStatus.OK
    }
}

private fun statusText(item: ExpiryItem): String = when (status(item)) {
    ExpiryStatus.EXPIRED -> "Caducado"
    ExpiryStatus.TODAY -> "Caduca hoy"
    ExpiryStatus.SOON -> "Caduca pronto"
    ExpiryStatus.OK -> "En plazo"
}

private fun dateText(millis: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(millis))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpiryApp() {
    val context = LocalContext.current
    val repository = remember { ExpiryRepository(context.applicationContext) }
    var items by remember { mutableStateOf(repository.all()) }
    var showAdd by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    val filtered = remember(items, search) {
        items.filter { search.isBlank() || it.name.contains(search, true) || it.category.contains(search, true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { if (showSearch) OutlinedTextField(search, { search = it }, placeholder = { Text("Buscar…") }, singleLine = true) else Text("Expiry") },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch; if (!showSearch) search = "" }) { Icon(Icons.Default.Search, "Buscar") }
                }
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "Añadir") } }
    ) { padding ->
        if (filtered.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("No tienes productos registrados", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Pulsa + para añadir el primero.")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { item ->
                    ExpiryCard(item, onDelete = {
                        repository.delete(item.id)
                        items = repository.all()
                    })
                }
            }
        }
    }

    if (showAdd) {
        AddExpiryDialog(
            onDismiss = { showAdd = false },
            onSave = { name, category, date, reminder ->
                val item = ExpiryItem(UUID.randomUUID().mostSignificantBits, name.trim(), category.trim(), date, reminder)
                repository.save(item)
                scheduleReminder(context, item)
                items = repository.all()
                showAdd = false
            }
        )
    }
}

@Composable
private fun ExpiryCard(item: ExpiryItem, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                if (item.category.isNotBlank()) Text(item.category, style = MaterialTheme.typography.bodySmall)
                Text(dateText(item.expiryMillis), style = MaterialTheme.typography.bodyMedium)
                Text(statusText(item), style = MaterialTheme.typography.labelLarge)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Eliminar") }
        }
    }
}

@Composable
private fun AddExpiryDialog(onDismiss: () -> Unit, onSave: (String, String, Long, Int) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var reminder by remember { mutableStateOf("7") }
    var date by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo producto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it; error = false }, label = { Text("Producto") }, singleLine = true, isError = error, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(category, { category = it }, label = { Text("Categoría (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    val c = Calendar.getInstance().apply { timeInMillis = date }
                    DatePickerDialog(context, { _, y, m, d ->
                        c.set(y, m, d, 12, 0, 0); c.set(Calendar.MILLISECOND, 0); date = c.timeInMillis
                    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("Caducidad: ${dateText(date)}") }
                OutlinedTextField(reminder, { if (it.all(Char::isDigit)) reminder = it }, label = { Text("Avisar con días de antelación") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = {
            if (name.isBlank()) { error = true; return@Button }
            onSave(name, category, date, reminder.toIntOrNull()?.coerceIn(0, 365) ?: 7)
        }) { Text("Guardar") } },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun scheduleReminder(context: Context, item: ExpiryItem) {
    val trigger = item.expiryMillis - item.reminderDays * 86_400_000L
    if (trigger <= System.currentTimeMillis()) return
    val intent = Intent(context, ExpiryAlarmReceiver::class.java).apply {
        putExtra("name", item.name)
        putExtra("id", item.id)
    }
    val requestCode = (item.id xor (item.id ushr 32)).toInt()
    val pending = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
}

@Composable
private fun ExpiryTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
