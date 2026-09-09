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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
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
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { ExpiryTheme { ExpiryApp(scannedBarcode, scannedProductName, scannedProductCategory, ::launchBarcodeScanner) { clearScan() } } }
    }
    private fun clearScan() { scannedBarcode = null; scannedProductName = ""; scannedProductCategory = "" }
    private fun launchBarcodeScanner() {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).enableAutoZoom().build()
        GmsBarcodeScanning.getClient(this, options).startScan().addOnSuccessListener { barcode ->
            barcode.rawValue?.trim()?.takeIf { it.isNotEmpty() }?.let { code ->
                scannedBarcode = code; ExpiryRepository(applicationContext).recordScan(code)
                ProductLookup.lookup(code) { result -> runOnUiThread { scannedProductName = result?.name.orEmpty(); scannedProductCategory = result?.category.orEmpty() } }
            }
        }
    }
}

private enum class ExpiryStatus { EXPIRED, TODAY, SOON, OK }
private enum class Filter { ALL, EXPIRED, TODAY, SOON, OK }
private fun status(item: ExpiryItem): ExpiryStatus { val d = ExpiryDateUtils.daysUntil(item.expiryMillis); return when { d < 0 -> ExpiryStatus.EXPIRED; d == 0 -> ExpiryStatus.TODAY; d in 1..7 -> ExpiryStatus.SOON; else -> ExpiryStatus.OK } }
private fun statusText(item: ExpiryItem, r: android.content.res.Resources): String = when (status(item)) { ExpiryStatus.EXPIRED -> r.getString(R.string.expired_status); ExpiryStatus.TODAY -> r.getString(R.string.expires_today); ExpiryStatus.SOON -> r.getString(R.string.expires_in, ExpiryDateUtils.daysUntil(item.expiryMillis)); ExpiryStatus.OK -> r.getString(R.string.ok_status) }
private fun dateText(millis: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(millis))

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ExpiryApp(scannedBarcode: String?, scannedProductName: String, scannedProductCategory: String, onScanBarcode: () -> Unit, onBarcodeConsumed: () -> Unit) {
    val context = LocalContext.current; val r = context.resources; val repository = remember { ExpiryRepository(context.applicationContext) }
    var items by remember { mutableStateOf(repository.all()) }; var search by remember { mutableStateOf("") }; var searching by remember { mutableStateOf(false) }; var filter by remember { mutableStateOf(Filter.ALL) }
    var editing by remember { mutableStateOf<ExpiryItem?>(null) }; var showAdd by remember { mutableStateOf(false) }; var showStats by remember { mutableStateOf(false) }; var deleteTarget by remember { mutableStateOf<ExpiryItem?>(null) }; var outcomeTarget by remember { mutableStateOf<ExpiryItem?>(null) }
    val filtered = remember(items, search, filter) { items.filter { item -> (search.isBlank() || item.name.contains(search,true) || item.category.contains(search,true) || item.barcode.contains(search,true)) && when(filter){Filter.ALL->true;Filter.EXPIRED->status(item)==ExpiryStatus.EXPIRED;Filter.TODAY->status(item)==ExpiryStatus.TODAY;Filter.SOON->status(item)==ExpiryStatus.SOON;Filter.OK->status(item)==ExpiryStatus.OK} }.sortedWith(compareBy({ when(status(it)){ExpiryStatus.EXPIRED->0;ExpiryStatus.TODAY->1;ExpiryStatus.SOON->2;ExpiryStatus.OK->3} },{it.expiryMillis},{it.name.lowercase(Locale.getDefault())})) }
    val counts = remember(items) { Filter.values().associateWith { f -> items.count { when(f){Filter.ALL->true;Filter.EXPIRED->status(it)==ExpiryStatus.EXPIRED;Filter.TODAY->status(it)==ExpiryStatus.TODAY;Filter.SOON->status(it)==ExpiryStatus.SOON;Filter.OK->status(it)==ExpiryStatus.OK} } } }
    fun label(f: Filter) = when(f){Filter.ALL->r.getString(R.string.all);Filter.EXPIRED->r.getString(R.string.expired);Filter.TODAY->r.getString(R.string.today);Filter.SOON->r.getString(R.string.soon);Filter.OK->r.getString(R.string.in_date)}
    Scaffold(topBar={ TopAppBar(title={ if(searching) OutlinedTextField(search,{search=it},placeholder={Text(stringResource(R.string.search_product))},singleLine=true,modifier=Modifier.fillMaxWidth()) else Text(stringResource(R.string.app_name)) },actions={ IconButton({searching=!searching;if(!searching)search=""}){Icon(Icons.Default.Search,stringResource(R.string.search))}; IconButton({showStats=true}){Icon(Icons.Default.BarChart,stringResource(R.string.consumption))}; IconButton(onScanBarcode){Icon(Icons.Default.QrCodeScanner,stringResource(R.string.scan_code))} }) },floatingActionButton={FloatingActionButton({showAdd=true}){Icon(Icons.Default.Add,stringResource(R.string.add_product))}}){ padding ->
        Column(Modifier.fillMaxSize().padding(padding)) { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp,8.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){Filter.values().forEach{f->FilterChip(filter==f,{filter=f},label={Text("${label(f)} (${counts[f]?:0})")})}}
            if(filtered.isEmpty()) Column(Modifier.fillMaxSize().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text(if(items.isEmpty())stringResource(R.string.no_products) else stringResource(R.string.no_matches),style=MaterialTheme.typography.titleMedium);if(items.isEmpty()){Spacer(Modifier.height(8.dp));Text(stringResource(R.string.add_first))}}
            else LazyColumn(Modifier.fillMaxSize().padding(horizontal=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=96.dp)){items(filtered,key={it.id}){item->ExpiryCard(item,{editing=item},{deleteTarget=item},{outcomeTarget=item},r)}} }
    }
    if(showAdd) ExpiryDialog(null,scannedBarcode?:"",scannedProductName,scannedProductCategory,onScanBarcode,{showAdd=false;onBarcodeConsumed()}){item->repository.save(item);scheduleReminder(context,item);items=repository.all();showAdd=false;onBarcodeConsumed()}
    editing?.let{item->ExpiryDialog(item,scannedBarcode?:item.barcode,scannedProductName.ifBlank{item.name},scannedProductCategory.ifBlank{item.category},onScanBarcode,{editing=null;onBarcodeConsumed()}){updated->cancelReminder(context,item.id);repository.save(updated);scheduleReminder(context,updated);items=repository.all();editing=null;onBarcodeConsumed()}}
    deleteTarget?.let{item->AlertDialog(onDismissRequest={deleteTarget=null},title={Text(stringResource(R.string.delete_product))},text={Text(stringResource(R.string.delete_confirm,item.name))},confirmButton={Button({cancelReminder(context,item.id);repository.delete(item.id);items=repository.all();deleteTarget=null}){Text(stringResource(R.string.delete_product))}},dismissButton={TextButton({deleteTarget=null}){Text(stringResource(R.string.cancel))}})}
    outcomeTarget?.let{item->AlertDialog(onDismissRequest={outcomeTarget=null},title={Text(stringResource(R.string.record_result))},text={Text(stringResource(R.string.what_happened,item.name))},confirmButton={Button({repository.recordOutcome(item,OutcomeType.CONSUMED);cancelReminder(context,item.id);repository.delete(item.id);items=repository.all();outcomeTarget=null}){Text(stringResource(R.string.consumed))}},dismissButton={TextButton({repository.recordOutcome(item,OutcomeType.DISCARDED);cancelReminder(context,item.id);repository.delete(item.id);items=repository.all();outcomeTarget=null}){Text(stringResource(R.string.discarded))}})}
    if(showStats) ConsumptionStatsDialog(items,repository.scanHistory(),repository.outcomeHistory(),{showStats=false})
    LaunchedEffect(scannedBarcode){if(scannedBarcode!=null&&!showAdd&&editing==null)showAdd=true}
}

@Composable private fun ExpiryCard(item: ExpiryItem,onEdit:()->Unit,onDelete:()->Unit,onOutcome:()->Unit,r:android.content.res.Resources){Card(Modifier.fillMaxWidth()){Column(Modifier.fillMaxWidth().padding(16.dp)){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(item.name,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.titleMedium);if(item.category.isNotBlank())Text(item.category,style=MaterialTheme.typography.bodySmall);Text(r.getString(R.string.expiry_date,dateText(item.expiryMillis)),style=MaterialTheme.typography.bodyMedium);Text(statusText(item,r),style=MaterialTheme.typography.labelLarge);Text(r.getString(R.string.notice,item.reminderDays),style=MaterialTheme.typography.bodySmall);if(item.barcode.isNotBlank())Text(r.getString(R.string.code,item.barcode),style=MaterialTheme.typography.bodySmall)};IconButton(onEdit){Icon(Icons.Default.Edit,stringResource(R.string.edit_product))};IconButton(onDelete){Icon(Icons.Default.Delete,stringResource(R.string.delete_product))}};Spacer(Modifier.height(8.dp));OutlinedButton(onOutcome,Modifier.fillMaxWidth()){Text("${r.getString(R.string.consumed)} / ${r.getString(R.string.discarded)}")}}}}

@Composable private fun ExpiryDialog(existing:ExpiryItem?,initialBarcode:String,initialName:String,initialCategory:String,onScanBarcode:()->Unit,onDismiss:()->Unit,onSave:(ExpiryItem)->Unit){val context=LocalContext.current;var name by remember(existing?.id,initialBarcode,initialName){mutableStateOf(initialName.ifBlank{existing?.name?:""})};var category by remember(existing?.id,initialBarcode,initialCategory){mutableStateOf(initialCategory.ifBlank{existing?.category?:""})};var barcode by remember(existing?.id,initialBarcode){mutableStateOf(initialBarcode.ifBlank{existing?.barcode?:""})};var reminder by remember(existing?.id){mutableStateOf((existing?.reminderDays?:7).toString())};var date by remember(existing?.id){mutableLongStateOf(existing?.expiryMillis?:System.currentTimeMillis())};var error by remember(existing?.id){mutableStateOf(false)};AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(if(existing==null)R.string.new_product else R.string.edit_product))},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){OutlinedTextField(name,{name=it;error=false},label={Text(stringResource(R.string.product))},singleLine=true,isError=error,modifier=Modifier.fillMaxWidth());OutlinedTextField(category,{category=it},label={Text(stringResource(R.string.category_optional))},singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(barcode,{barcode=it},label={Text(stringResource(R.string.barcode_optional))},singleLine=true,trailingIcon={IconButton(onScanBarcode){Icon(Icons.Default.QrCodeScanner,stringResource(R.string.scan_code))}},modifier=Modifier.fillMaxWidth());Button(onScanBarcode,Modifier.fillMaxWidth()){Icon(Icons.Default.QrCodeScanner,null);Spacer(Modifier.width(8.dp));Text(stringResource(R.string.scan_code))};Button({val c=Calendar.getInstance().apply{timeInMillis=date};DatePickerDialog(context,{_,y,m,d->c.set(y,m,d,12,0,0);c.set(Calendar.MILLISECOND,0);date=c.timeInMillis},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show()},Modifier.fillMaxWidth()){Text(stringResource(R.string.expiry_date,dateText(date)))};OutlinedTextField(reminder,{if(it.all(Char::isDigit))reminder=it},label={Text(stringResource(R.string.reminder_days))},supportingText={Text(stringResource(R.string.reminder_help))},singleLine=true,modifier=Modifier.fillMaxWidth())}},confirmButton={Button({if(name.isBlank()){error=true;return@Button};val id=existing?.id?:UUID.randomUUID().mostSignificantBits;onSave(ExpiryItem(id,name.trim(),category.trim(),date,reminder.toIntOrNull()?.coerceIn(0,365)?:7,barcode.trim()))}){Text(stringResource(R.string.save))}},dismissButton={TextButton(onDismiss){Text(stringResource(R.string.cancel))}})}

@Composable private fun ConsumptionStatsDialog(items:List<ExpiryItem>,scans:List<ScanEvent>,outcomes:List<OutcomeEvent>,onDismiss:()->Unit){val r=LocalContext.current.resources;val recent=scans.count{it.timestamp>=System.currentTimeMillis()-7L*86_400_000L};val consumed=outcomes.count{it.outcome==OutcomeType.CONSUMED};val discarded=outcomes.count{it.outcome==OutcomeType.DISCARDED};val total=consumed+discarded;val rate=if(total==0)0 else discarded*100/total;val scanCounts=scans.groupingBy{it.barcode}.eachCount().entries.sortedByDescending{it.value}.take(5);val names=scanCounts.map{(b,c)->(items.firstOrNull{it.barcode==b}?.name?:b) to c};val categories=items.filter{it.category.isNotBlank()}.groupingBy{it.category}.eachCount().entries.sortedByDescending{it.value}.take(5);val discardedNames=outcomes.filter{it.outcome==OutcomeType.DISCARDED}.groupingBy{it.name}.eachCount().entries.sortedByDescending{it.value}.take(5);AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.stats_title))},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text(r.getString(R.string.active_products,items.size));Text(r.getString(R.string.total_scans,scans.size));Text(r.getString(R.string.scans_7_days,recent));Text(r.getString(R.string.consumed_registered,consumed));Text(r.getString(R.string.discarded_registered,discarded));Text(r.getString(R.string.discard_rate,rate));if(names.isNotEmpty()){Text(stringResource(R.string.most_scanned),fontWeight=FontWeight.SemiBold);names.forEach{Text(r.getString(R.string.scan_count,it.first,it.second))}};if(discardedNames.isNotEmpty()){Text(stringResource(R.string.most_discarded),fontWeight=FontWeight.SemiBold);discardedNames.forEach{Text(r.getString(R.string.discard_count,it.key,it.value))}};if(categories.isNotEmpty()){Text(stringResource(R.string.active_categories),fontWeight=FontWeight.SemiBold);categories.forEach{Text(r.getString(R.string.product_count,it.key,it.value))}};Text(stringResource(R.string.stats_note),style=MaterialTheme.typography.bodySmall)}},confirmButton={TextButton(onDismiss){Text(stringResource(R.string.close))}})}

internal fun reminderPendingIntent(context:Context,id:Long):PendingIntent{val intent=Intent(context,ExpiryAlarmReceiver::class.java).apply{putExtra("id",id)};val requestCode=(id xor(id ushr 32)).toInt();return PendingIntent.getBroadcast(context,requestCode,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)}
private fun scheduleReminder(context:Context,item:ExpiryItem){val trigger=ExpiryDateUtils.reminderTrigger(item.expiryMillis,item.reminderDays);if(trigger<=System.currentTimeMillis())return;(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,reminderPendingIntent(context,item.id))}
private fun cancelReminder(context:Context,id:Long){(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(reminderPendingIntent(context,id))}
@Composable private fun ExpiryTheme(content:@Composable()->Unit){MaterialTheme(content=content)}
