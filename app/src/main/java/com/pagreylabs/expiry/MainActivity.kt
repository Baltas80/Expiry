package com.pagreylabs.expiry

import android.Manifest
import android.app.Activity
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private lateinit var adsManager: AdsManager

    private var scannedBarcode by mutableStateOf<String?>(null)
    private var scannedProductName by mutableStateOf("")
    private var scannedProductCategory by mutableStateOf("")
    private var scannedProductImageUrl by mutableStateOf("")
    private var adsReady by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        adsManager = AdsManager(this)
        adsManager.requestConsentAndInitialize(this) { ready ->
            adsReady = ready
        }

        setContent {
            ExpiryTheme {
                ExpiryApp(
                    scannedBarcode = scannedBarcode,
                    scannedProductName = scannedProductName,
                    scannedProductCategory = scannedProductCategory,
                    scannedProductImageUrl = scannedProductImageUrl,
                    adsReady = adsReady,
                    adsManager = adsManager,
                    onScanBarcode = ::launchBarcodeScanner,
                    onBarcodeConsumed = ::clearScan
                )
            }
        }
    }

    private fun clearScan() {
        scannedBarcode = null
        scannedProductName = ""
        scannedProductCategory = ""
        scannedProductImageUrl = ""
    }

    private fun launchBarcodeScanner() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .enableAutoZoom()
            .build()

        GmsBarcodeScanning.getClient(this, options).startScan()
            .addOnSuccessListener { barcode ->
                barcode.rawValue?.trim()?.takeIf { it.isNotEmpty() }?.let { code ->
                    scannedBarcode = code
                    ExpiryRepository(applicationContext).recordScan(code)
                    ProductLookup.lookup(code) { result ->
                        runOnUiThread {
                            when {
                                result == null ->
                                    Toast.makeText(this, R.string.product_lookup_error, Toast.LENGTH_SHORT).show()
                                !result.found ->
                                    Toast.makeText(this, R.string.product_not_found, Toast.LENGTH_SHORT).show()
                                else -> {
                                    scannedProductName = result.name
                                    scannedProductCategory = result.category
                                    scannedProductImageUrl = result.imageUrl
                                }
                            }
                        }
                    }
                }
            }
    }
}

private enum class ExpiryStatus { EXPIRED, TODAY, SOON, OK }
private enum class Filter { ALL, EXPIRED, TODAY, SOON, OK }
private enum class AppTab { HOME, PRODUCTS, STATS, MORE }

private fun status(item: ExpiryItem): ExpiryStatus = when (ExpiryDateUtils.daysUntil(item.expiryMillis)) {
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

private fun dateText(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(millis))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpiryApp(
    scannedBarcode: String?,
    scannedProductName: String,
    scannedProductCategory: String,
    scannedProductImageUrl: String,
    adsReady: Boolean,
    adsManager: AdsManager,
    onScanBarcode: () -> Unit,
    onBarcodeConsumed: () -> Unit
) {
    val context = LocalContext.current
    val r = context.resources
    val repository = remember { ExpiryRepository(context.applicationContext) }
    val billingManager = remember { PremiumBillingManager(context.applicationContext) }
    val isPremium by billingManager.isPremium.collectAsState()
    val billingOffers by billingManager.offers.collectAsState()
    val lifetimeOffer by billingManager.lifetimeOffer.collectAsState()

    DisposableEffect(billingManager) {
        billingManager.connect()
        onDispose { billingManager.close() }
    }

    var items by remember { mutableStateOf(repository.all()) }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(Filter.ALL) }
    var tab by remember { mutableStateOf(AppTab.HOME) }
    var editing by remember { mutableStateOf<ExpiryItem?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showPremium by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ExpiryItem?>(null) }
    var outcomeTarget by remember { mutableStateOf<ExpiryItem?>(null) }

    val filtered = remember(items, search, filter) {
        items
            .filter { item ->
                (search.isBlank() ||
                    item.name.contains(search, true) ||
                    item.category.contains(search, true) ||
                    item.barcode.contains(search, true)) &&
                    when (filter) {
                        Filter.ALL -> true
                        Filter.EXPIRED -> status(item) == ExpiryStatus.EXPIRED
                        Filter.TODAY -> status(item) == ExpiryStatus.TODAY
                        Filter.SOON -> status(item) == ExpiryStatus.SOON
                        Filter.OK -> status(item) == ExpiryStatus.OK
                    }
            }
            .sortedWith(
                compareBy(
                    {
                        when (status(it)) {
                            ExpiryStatus.EXPIRED -> 0
                            ExpiryStatus.TODAY -> 1
                            ExpiryStatus.SOON -> 2
                            ExpiryStatus.OK -> 3
                        }
                    },
                    { it.expiryMillis },
                    { it.name.lowercase(Locale.getDefault()) }
                )
            )
    }

    val counts = remember(items) {
        mapOf(
            Filter.EXPIRED to items.count { status(it) == ExpiryStatus.EXPIRED },
            Filter.TODAY to items.count { status(it) == ExpiryStatus.TODAY },
            Filter.SOON to items.count { status(it) == ExpiryStatus.SOON },
            Filter.OK to items.count { status(it) == ExpiryStatus.OK }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = { tab = AppTab.MORE }) {
                        Icon(Icons.Default.Menu, stringResource(R.string.more_tab))
                    }
                },
                title = {
                    Column {
                        Text(
                            stringResource(R.string.app_name),
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            when (tab) {
                                AppTab.HOME -> stringResource(R.string.tagline)
                                AppTab.PRODUCTS -> stringResource(R.string.add_tab)
                                AppTab.STATS -> stringResource(R.string.stats_title)
                                AppTab.MORE -> stringResource(R.string.settings)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (tab == AppTab.HOME || tab == AppTab.PRODUCTS) {
                        IconButton(onClick = { tab = AppTab.PRODUCTS }) {
                            Icon(Icons.Default.Search, stringResource(R.string.search))
                        }
                    }
                    if (tab == AppTab.HOME) {
                        IconButton(onClick = { tab = AppTab.MORE }) {
                            Icon(
                                Icons.Default.NotificationsNone,
                                stringResource(R.string.settings_notifications_section)
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column {
                ExpiryBannerAd(
                    visible = adsReady && !isPremium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == AppTab.HOME,
                        onClick = { tab = AppTab.HOME },
                        icon = { Icon(Icons.Default.Eco, null) },
                        label = { Text(stringResource(R.string.home), maxLines = 1) }
                    )
                    NavigationBarItem(
                        selected = tab == AppTab.PRODUCTS,
                        onClick = { tab = AppTab.PRODUCTS },
                        icon = { Icon(Icons.Default.Inventory2, null) },
                        label = { Text(stringResource(R.string.add_tab), maxLines = 1) }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onScanBarcode,
                        icon = {
                            Surface(
                                modifier = Modifier.size(52.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.QrCodeScanner,
                                        contentDescription = stringResource(R.string.scan_code),
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        },
                        label = { Text(stringResource(R.string.scan_tab), maxLines = 1) }
                    )
                    NavigationBarItem(
                        selected = tab == AppTab.STATS,
                        onClick = { tab = AppTab.STATS },
                        icon = { Icon(Icons.Default.BarChart, null) },
                        label = { Text(stringResource(R.string.stats_tab), maxLines = 1) }
                    )
                    NavigationBarItem(
                        selected = tab == AppTab.MORE,
                        onClick = { tab = AppTab.MORE },
                        icon = { Icon(Icons.Default.MoreHoriz, null) },
                        label = { Text(stringResource(R.string.more_tab), maxLines = 1) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == AppTab.HOME || tab == AppTab.PRODUCTS) {
                androidx.compose.material3.FloatingActionButton(
                    onClick = { showAdd = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Icon(Icons.Default.Add, stringResource(R.string.add_product), modifier = Modifier.size(28.dp))
                }
            }
        }
    ) { padding ->
        when (tab) {
            AppTab.HOME -> HomeScreen(
                padding = padding,
                items = items,
                counts = counts,
                isPremium = isPremium,
                onAdd = { showAdd = true },
                onScan = onScanBarcode,
                onOpenProducts = { tab = AppTab.PRODUCTS },
                onOpenStats = { tab = AppTab.STATS },
                onOpenPremium = { showPremium = true },
                onFilter = {
                    filter = it
                    tab = AppTab.PRODUCTS
                }
            )

            AppTab.PRODUCTS -> ProductsScreen(
                padding = padding,
                productItems = filtered,
                allItemsCount = items.size,
                search = search,
                filter = filter,
                counts = counts,
                onSearchChange = { search = it },
                onFilterChange = { filter = it },
                onEdit = { editing = it },
                onDelete = { deleteTarget = it },
                onOutcome = { outcomeTarget = it },
                onClearSearch = { search = "" },
                onAdd = { showAdd = true }
            )

            AppTab.STATS -> StatsScreen(
                padding = padding,
                productItems = items,
                scans = repository.scanHistory(),
                outcomes = repository.outcomeHistory()
            )

            AppTab.MORE -> MoreScreen(
                padding = padding,
                isPremium = isPremium,
                onOpenPremium = { showPremium = true },
                onPrivacyOptions = {
                    (context as? Activity)?.let(adsManager::showPrivacyOptions)
                }
            )
        }
    }

    if (showAdd) {
        ExpiryDialog(
            existing = null,
            initialBarcode = scannedBarcode ?: "",
            initialName = scannedProductName,
            initialCategory = scannedProductCategory,
            initialImageUrl = scannedProductImageUrl,
            onScanBarcode = onScanBarcode,
            onDismiss = {
                showAdd = false
                onBarcodeConsumed()
            }
        ) { item ->
            repository.save(item)
            scheduleReminder(context, item)
            items = repository.all()
            showAdd = false
            onBarcodeConsumed()
        }
    }

    editing?.let { item ->
        ExpiryDialog(
            existing = item,
            initialBarcode = scannedBarcode ?: item.barcode,
            initialName = scannedProductName.ifBlank { item.name },
            initialCategory = scannedProductCategory.ifBlank { item.category },
            initialImageUrl = scannedProductImageUrl.ifBlank { item.imageUrl },
            onScanBarcode = onScanBarcode,
            onDismiss = {
                editing = null
                onBarcodeConsumed()
            }
        ) { updated ->
            cancelReminder(context, item.id)
            repository.save(updated)
            scheduleReminder(context, updated)
            items = repository.all()
            editing = null
            onBarcodeConsumed()
        }
    }

    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_product)) },
            text = { Text(stringResource(R.string.delete_confirm, item.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        cancelReminder(context, item.id)
                        repository.delete(item.id)
                        items = repository.all()
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.delete_product))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    outcomeTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { outcomeTarget = null },
            title = { Text(stringResource(R.string.record_result)) },
            text = { Text(stringResource(R.string.what_happened, item.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        repository.recordOutcome(item, OutcomeType.CONSUMED)
                        cancelReminder(context, item.id)
                        repository.delete(item.id)
                        items = repository.all()
                        outcomeTarget = null
                    }
                ) {
                    Text(stringResource(R.string.consumed))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        repository.recordOutcome(item, OutcomeType.DISCARDED)
                        cancelReminder(context, item.id)
                        repository.delete(item.id)
                        items = repository.all()
                        outcomeTarget = null
                    }
                ) {
                    Text(stringResource(R.string.discarded))
                }
            }
        )
    }

    if (showPremium) {
        PremiumDialog(
            isPremium = isPremium,
            offers = billingOffers,
            lifetimeOffer = lifetimeOffer,
            billingManager = billingManager,
            onDismiss = { showPremium = false }
        )
    }

    androidx.compose.runtime.LaunchedEffect(scannedBarcode) {
        if (scannedBarcode != null && !showAdd && editing == null) {
            showAdd = true
        }
    }
}

@Composable
private fun HomeScreen(
    padding: PaddingValues,
    items: List<ExpiryItem>,
    counts: Map<Filter, Int>,
    isPremium: Boolean,
    onAdd: () -> Unit,
    onScan: () -> Unit,
    onOpenProducts: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenPremium: () -> Unit,
    onFilter: (Filter) -> Unit
) {
    val heroImage = items.firstOrNull { it.imageUrl.isNotBlank() }?.imageUrl
    val soonItems = items
        .filter { status(it) == ExpiryStatus.TODAY || status(it) == ExpiryStatus.SOON }
        .sortedBy { it.expiryMillis }
        .take(3)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            HeroCard(imageUrl = heroImage, onAdd = onAdd)
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.upcoming_products),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            stringResource(R.string.pantry_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (items.isNotEmpty()) {
                        TextButton(onClick = onOpenProducts) {
                            Text(items.size.toString())
                            Icon(Icons.Filled.ArrowForward, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeStatusCard(
                        modifier = Modifier.weight(1f),
                        count = counts[Filter.EXPIRED] ?: 0,
                        label = stringResource(R.string.expired_count_label),
                        icon = Icons.Default.Close,
                        color = MaterialTheme.colorScheme.error,
                        onClick = { onFilter(Filter.EXPIRED) }
                    )
                    HomeStatusCard(
                        modifier = Modifier.weight(1f),
                        count = (counts[Filter.TODAY] ?: 0) + (counts[Filter.SOON] ?: 0),
                        label = stringResource(R.string.soon_count_label),
                        icon = Icons.Default.CalendarToday,
                        color = MaterialTheme.colorScheme.tertiary,
                        onClick = { onFilter(Filter.SOON) }
                    )
                    HomeStatusCard(
                        modifier = Modifier.weight(1f),
                        count = counts[Filter.OK] ?: 0,
                        label = stringResource(R.string.in_date_count_label),
                        icon = Icons.Default.CheckCircle,
                        color = MaterialTheme.colorScheme.primary,
                        onClick = { onFilter(Filter.OK) }
                    )
                }
            }
        }

        item {
            SectionHeader(
                title = stringResource(R.string.upcoming_products),
                action = null,
                onAction = null
            )
        }

        if (soonItems.isEmpty()) {
            item { EmptyHomeCard(onAdd = onAdd) }
        } else {
            itemsIndexed(soonItems, key = { _, item -> item.id }) { _, item ->
                CompactExpiryCard(item)
            }
        }

        item {
            Text(
                stringResource(R.string.quick_actions),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickAction(Icons.Default.QrCodeScanner, stringResource(R.string.scan_code), onScan)
                QuickAction(Icons.Default.Add, stringResource(R.string.add_product), onAdd)
                QuickAction(Icons.Default.Search, stringResource(R.string.search), onOpenProducts)
                QuickAction(Icons.Default.BarChart, stringResource(R.string.stats_tab), onOpenStats)
            }
        }

        if (!isPremium) {
            item { PremiumPromoCard(onClick = onOpenPremium) }
        } else {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.WorkspacePremium,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.premium_no_ads_active),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                stringResource(R.string.premium_no_ads_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(imageUrl: String?, onAdd: () -> Unit) {
    val ink = Color(0xFF17201B)
    val emerald = Color(0xFF0F7A4A)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(244.dp),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = ink)
    ) {
        Box(Modifier.fillMaxSize()) {
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(30.dp)),
                    contentScale = ContentScale.Crop
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xF517201B),
                                    Color(0xAA17201B),
                                    Color(0x3317201B)
                                )
                            )
                        )
                )
            } else {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(220.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0x6689D6AE), Color.Transparent)
                            )
                        )
                )
                Icon(
                    Icons.Default.Eco,
                    null,
                    tint = Color.White.copy(alpha = 0.05f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(170.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = emerald.copy(alpha = 0.18f)
                    ) {
                        Icon(
                            Icons.Default.Eco,
                            null,
                            tint = Color(0xFFE6F6ED),
                            modifier = Modifier.padding(10.dp).size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.app_name),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(Modifier.width(250.dp)) {
                    Text(
                        stringResource(R.string.hero_title),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.hero_subtitle),
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onAdd,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = emerald
                        ),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(
                            stringResource(R.string.add_product),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeStatusCard(
    modifier: Modifier,
    count: Int,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(122.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.12f)
            ) {
                Icon(
                    icon,
                    null,
                    tint = color,
                    modifier = Modifier.padding(8.dp).size(20.dp)
                )
            }
            Column {
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = color
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String?, onAction: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            modifier = Modifier.weight(1f)
        )
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(action)
                Icon(Icons.Filled.ArrowForward, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun EmptyHomeCard(onAdd: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    Icons.Default.Inventory2,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(18.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.no_products),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.add_first),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onAdd) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_product))
            }
        }
    }
}

@Composable
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(118.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    icon,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(9.dp).size(22.dp)
                )
            }
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CompactExpiryCard(item: ExpiryItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProductThumbnail(item, 82.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                if (item.category.isNotBlank()) {
                    Text(
                        item.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.height(6.dp))
                StatusPill(item)
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.ArrowForward,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProductsScreen(
    padding: PaddingValues,
    productItems: List<ExpiryItem>,
    allItemsCount: Int,
    search: String,
    filter: Filter,
    counts: Map<Filter, Int>,
    onSearchChange: (String) -> Unit,
    onFilterChange: (Filter) -> Unit,
    onEdit: (ExpiryItem) -> Unit,
    onDelete: (ExpiryItem) -> Unit,
    onOutcome: (ExpiryItem) -> Unit,
    onClearSearch: () -> Unit,
    onAdd: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = onSearchChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.search_product)) },
                    placeholder = { Text(stringResource(R.string.search_product)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Filter.values().forEach { f ->
                        val count = when (f) {
                            Filter.ALL -> allItemsCount
                            else -> counts[f] ?: 0
                        }
                        val label = when (f) {
                            Filter.ALL -> stringResource(R.string.all)
                            Filter.EXPIRED -> stringResource(R.string.expired)
                            Filter.TODAY -> stringResource(R.string.today)
                            Filter.SOON -> stringResource(R.string.soon)
                            Filter.OK -> stringResource(R.string.in_date)
                        }
                        FilterChip(
                            selected = filter == f,
                            onClick = { onFilterChange(f) },
                            label = { Text("$label ($count)") }
                        )
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (filter) {
                        Filter.ALL -> stringResource(R.string.add_tab)
                        Filter.EXPIRED -> stringResource(R.string.expired)
                        Filter.TODAY -> stringResource(R.string.today)
                        Filter.SOON -> stringResource(R.string.soon)
                        Filter.OK -> stringResource(R.string.in_date)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    productItems.size.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (productItems.isEmpty()) {
            item {
                EmptyHomeCard(
                    onAdd = onAdd
                )
            }
        } else {
            items(productItems, key = { it.id }) { item ->
                ProductsListCard(
                    item = item,
                    onEdit = { onEdit(item) },
                    onDelete = { onDelete(item) },
                    onOutcome = { onOutcome(item) }
                )
            }
        }
    }
}

@Composable
private fun ProductsListCard(
    item: ExpiryItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOutcome: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                ProductThumbnail(item, 92.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                    if (item.category.isNotBlank()) {
                        Text(
                            item.category,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    StatusPill(item)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.expiry_date, dateText(item.expiryMillis)),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        stringResource(R.string.notice, item.reminderDays),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, stringResource(R.string.edit_product))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, stringResource(R.string.delete_product))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onOutcome,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.Restaurant, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.record_consumption_waste))
            }
        }
    }
}

@Composable
private fun StatusPill(item: ExpiryItem) {
    val state = status(item)
    val color = when (state) {
        ExpiryStatus.EXPIRED, ExpiryStatus.TODAY -> MaterialTheme.colorScheme.error
        ExpiryStatus.SOON -> MaterialTheme.colorScheme.tertiary
        ExpiryStatus.OK -> MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = color.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                statusText(item, LocalContext.current.resources),
                color = color,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ProductThumbnail(item: ExpiryItem, size: androidx.compose.ui.unit.Dp) {
    Surface(
        modifier = Modifier.size(size),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        if (item.imageUrl.isNotBlank()) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Inventory2,
                    contentDescription = item.category.ifBlank { item.name },
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(size * 0.36f)
                )
            }
        }
    }
}

@Composable
private fun StatsScreen(
    padding: PaddingValues,
    productItems: List<ExpiryItem>,
    scans: List<ScanEvent>,
    outcomes: List<OutcomeEvent>
) {
    val now = System.currentTimeMillis()
    val recent = scans.count { ExpiryStatsUtils.isWithinLastCalendarDays(it.timestamp, 7, now) }
    val consumed = outcomes.count { it.outcome == OutcomeType.CONSUMED }
    val discarded = outcomes.count { it.outcome == OutcomeType.DISCARDED }
    val total = consumed + discarded
    val rate = if (total == 0) 0 else discarded * 100 / total

    val scanCounts = scans.groupingBy { it.barcode }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .take(5)

    val names = scanCounts.map { (barcode, count) ->
        (productItems.firstOrNull { it.barcode == barcode }?.name ?: barcode) to count
    }

    val categories = productItems
        .filter { it.category.isNotBlank() }
        .groupingBy { it.category }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .take(5)

    val discardedNames = outcomes
        .filter { it.outcome == OutcomeType.DISCARDED }
        .groupingBy { it.name }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .take(5)

    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        stringResource(R.string.stats_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.stats_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    Modifier.weight(1f),
                    productItems.size.toString(),
                    stringResource(R.string.active_products_label),
                    Icons.Default.Inventory2
                )
                MetricCard(
                    Modifier.weight(1f),
                    scans.size.toString(),
                    stringResource(R.string.total_scans_label),
                    Icons.Default.QrCodeScanner
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    Modifier.weight(1f),
                    consumed.toString(),
                    stringResource(R.string.consumed_registered_label),
                    Icons.Default.Restaurant
                )
                MetricCard(
                    Modifier.weight(1f),
                    discarded.toString(),
                    stringResource(R.string.discarded_registered_label),
                    Icons.Default.DeleteSweep
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.BarChart,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.discard_rate, rate),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { (rate / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.scans_7_days, recent),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (names.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.most_scanned),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
            }
            itemsIndexed(names) { index, pair ->
                RankedRow(index + 1, pair.first, pair.second)
            }
        }

        if (categories.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.active_categories),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
            }
            items(categories) { entry ->
                ListItem(
                    headlineContent = { Text(entry.key, fontWeight = FontWeight.SemiBold) },
                    trailingContent = {
                        Text(
                            entry.value.toString(),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    leadingContent = {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                Icons.Default.Eco,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(9.dp).size(20.dp)
                            )
                        }
                    }
                )
            }
        }

        if (discardedNames.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.most_discarded),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
            }
            items(discardedNames) { entry ->
                RankedRow(0, entry.key, entry.value)
            }
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier,
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = modifier.height(128.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                icon,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun RankedRow(index: Int, name: String, count: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (index > 0) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            index.toString(),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
            }
            Text(name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(
                count.toString(),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MoreScreen(
    padding: PaddingValues,
    isPremium: Boolean,
    onOpenPremium: () -> Unit,
    onPrivacyOptions: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("expiry_settings", Context.MODE_PRIVATE)
    }
    var themeMode by remember {
        mutableStateOf(prefs.getString("theme_mode", "system") ?: "system")
    }
    var confirmClear by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(exportExpiryBackup(context).toByteArray(Charsets.UTF_8))
                }
            }.onFailure {
                Toast.makeText(context, R.string.data_operation_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: ""
            }.onSuccess { raw ->
                if (importExpiryBackup(context, raw)) {
                    Toast.makeText(context, R.string.data_imported, Toast.LENGTH_SHORT).show()
                    context.recreate()
                } else {
                    Toast.makeText(context, R.string.data_invalid, Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                Toast.makeText(context, R.string.data_operation_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PremiumSettingsCard(isPremium = isPremium, onClick = onOpenPremium)
        }

        item {
            SettingsCard(
                icon = Icons.Default.Notifications,
                title = stringResource(R.string.settings_notifications_section),
                subtitle = stringResource(
                    R.string.settings_notifications,
                    if (Build.VERSION.SDK_INT < 24 ||
                        (context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
                            .areNotificationsEnabled()
                    ) stringResource(R.string.enabled) else stringResource(R.string.disabled)
                ),
                onClick = {
                    if (Build.VERSION.SDK_INT >= 26) {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                        )
                    }
                }
            )
        }

        item {
            SettingsCard(
                icon = Icons.Default.Language,
                title = stringResource(R.string.settings_language_section),
                subtitle = stringResource(R.string.settings_language_auto),
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                                data = Uri.parse("package:" + context.packageName)
                            }
                        )
                    } else {
                        context.startActivity(Intent(Settings.ACTION_LOCALE_SETTINGS))
                    }
                }
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (themeMode == "dark") Icons.Default.DarkMode else Icons.Default.LightMode,
                            null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_appearance_section),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                stringResource(R.string.settings_appearance_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "system" to R.string.theme_system,
                            "light" to R.string.theme_light,
                            "dark" to R.string.theme_dark
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = {
                                    themeMode = mode
                                    prefs.edit().putString("theme_mode", mode).apply()
                                    context.recreate()
                                },
                                label = { Text(stringResource(label)) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                stringResource(R.string.settings_data_section),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
        }

        item {
            SettingsCard(
                icon = Icons.Default.FileDownload,
                title = stringResource(R.string.data_export),
                subtitle = stringResource(R.string.settings_storage),
                onClick = { exportLauncher.launch("expiry-backup.json") }
            )
        }

        item {
            SettingsCard(
                icon = Icons.Default.FileUpload,
                title = stringResource(R.string.data_import),
                subtitle = stringResource(R.string.settings_storage),
                onClick = {
                    importLauncher.launch(arrayOf("application/json", "text/json", "text/plain"))
                }
            )
        }

        item {
            SettingsCard(
                icon = Icons.Default.DeleteSweep,
                title = stringResource(R.string.data_clear),
                subtitle = stringResource(R.string.data_clear_confirm),
                tint = MaterialTheme.colorScheme.error,
                onClick = { confirmClear = true }
            )
        }

        item {
            Text(
                stringResource(R.string.settings_privacy_section),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
        }

        item {
            SettingsCard(
                icon = Icons.Default.PrivacyTip,
                title = stringResource(R.string.settings_privacy_section),
                subtitle = stringResource(R.string.settings_privacy_description),
                onClick = onPrivacyOptions
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        stringResource(R.string.settings_about_section),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_about_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.data_clear)) },
            text = { Text(stringResource(R.string.data_clear_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        clearExpiryData(context)
                        confirmClear = false
                        context.recreate()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.data_clear_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun PremiumSettingsCard(isPremium: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPremium) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                Color(0xFF17201B)
            }
        )
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (isPremium) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    Color.White.copy(alpha = 0.1f)
                }
            ) {
                Icon(
                    Icons.Default.WorkspacePremium,
                    null,
                    tint = if (isPremium) MaterialTheme.colorScheme.primary else Color(0xFFFFD36A),
                    modifier = Modifier.padding(10.dp).size(28.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.premium_no_ads_title),
                    color = if (isPremium) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        Color.White
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    if (isPremium) {
                        stringResource(R.string.premium_no_ads_active)
                    } else {
                        stringResource(R.string.premium_no_ads_description)
                    },
                    color = if (isPremium) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        Color.White.copy(alpha = 0.72f)
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Icon(
                Icons.Filled.ArrowForward,
                null,
                tint = if (isPremium) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun SettingsCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        ListItem(
            headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text(subtitle, maxLines = 3) },
            leadingContent = {
                Surface(
                    shape = CircleShape,
                    color = tint.copy(alpha = 0.11f)
                ) {
                    Icon(
                        icon,
                        null,
                        tint = tint,
                        modifier = Modifier.padding(9.dp).size(22.dp)
                    )
                }
            },
            trailingContent = {
                Icon(
                    Icons.Filled.ArrowForward,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
}

@Composable
private fun PremiumPromoCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17201B))
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.10f)
                ) {
                    Icon(
                        Icons.Default.WorkspacePremium,
                        null,
                        tint = Color(0xFFFFD36A),
                        modifier = Modifier.padding(10.dp).size(25.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.premium_no_ads_title),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.premium_no_ads_description),
                color = Color.White.copy(alpha = 0.76f),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(
                onClick = onClick,
                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF0F7A4A)
                )
            ) {
                Text(
                    stringResource(R.string.premium_no_ads_buy),
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumDialog(
    isPremium: Boolean,
    offers: List<PremiumBillingManager.SubscriptionOffer>,
    lifetimeOffer: PremiumBillingManager.LifetimeOffer?,
    billingManager: PremiumBillingManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.width(42.dp))
                    Text(
                        stringResource(R.string.premium_no_ads_title),
                        Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, stringResource(R.string.close))
                    }
                }

                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(26.dp),
                        color = Color(0xFF17201B)
                    ) {
                        Column(Modifier.padding(24.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.10f)
                                ) {
                                    Icon(
                                        Icons.Default.WorkspacePremium,
                                        null,
                                        tint = Color(0xFFFFD36A),
                                        modifier = Modifier.padding(11.dp).size(30.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        stringResource(R.string.premium_no_ads_title),
                                        color = Color.White,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        stringResource(R.string.premium_no_ads_description),
                                        color = Color.White.copy(alpha = 0.78f),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                            Spacer(Modifier.height(18.dp))
                            listOf(
                                stringResource(R.string.premium_no_ads_description),
                                stringResource(R.string.settings_about_description),
                                stringResource(R.string.settings_privacy_description)
                            ).forEach {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        null,
                                        tint = Color(0xFF53C98A),
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Spacer(Modifier.width(9.dp))
                                    Text(
                                        it,
                                        color = Color.White.copy(alpha = 0.84f),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }

                    if (isPremium) {
                        Surface(
                            Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    stringResource(R.string.premium_no_ads_active),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        val orderedOffers = offers.sortedBy {
                            if (it.basePlanId == "annual") 0 else 1
                        }

                        orderedOffers.forEach { offer ->
                            PremiumOfferCard(
                                title = when (offer.basePlanId) {
                                    "annual" -> stringResource(R.string.premium_annual_label)
                                    else -> stringResource(R.string.premium_monthly_label)
                                },
                                price = offer.formattedPrice,
                                highlighted = offer.basePlanId == "annual",
                                onClick = {
                                    (context as? Activity)?.let {
                                        billingManager.purchase(it, offer)
                                    }
                                }
                            )
                        }

                        lifetimeOffer?.let { offer ->
                            PremiumOfferCard(
                                title = stringResource(R.string.premium_lifetime_title),
                                price = offer.formattedPrice,
                                highlighted = false,
                                onClick = {
                                    (context as? Activity)?.let {
                                        billingManager.purchaseLifetime(it, offer)
                                    }
                                }
                            )
                        }

                        if (offers.isEmpty() && lifetimeOffer == null) {
                            Surface(
                                Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    stringResource(R.string.premium_no_ads_unavailable),
                                    Modifier.padding(18.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumOfferCard(
    title: String,
    price: String,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                if (highlighted) {
                    Text(
                        stringResource(R.string.premium_recommended),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(4.dp))
                Text(price, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = onClick) {
                Text(stringResource(R.string.premium_no_ads_buy))
            }
        }
    }
}

@Composable
private fun ExpiryDialog(
    existing: ExpiryItem?,
    initialBarcode: String,
    initialName: String,
    initialCategory: String,
    initialImageUrl: String,
    onScanBarcode: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (ExpiryItem) -> Unit
) {
    val context = LocalContext.current
    var name by remember(existing?.id, initialBarcode, initialName) {
        mutableStateOf(initialName.ifBlank { existing?.name ?: "" })
    }
    var category by remember(existing?.id, initialBarcode, initialCategory) {
        mutableStateOf(initialCategory.ifBlank { existing?.category ?: "" })
    }
    var barcode by remember(existing?.id, initialBarcode) {
        mutableStateOf(initialBarcode.ifBlank { existing?.barcode ?: "" })
    }
    var imageUrl by remember(existing?.id, initialBarcode, initialImageUrl) {
        mutableStateOf(initialImageUrl.ifBlank { existing?.imageUrl ?: "" })
    }
    var reminder by remember(existing?.id) {
        mutableStateOf((existing?.reminderDays ?: 7).toString())
    }
    var date by remember(existing?.id) {
        mutableLongStateOf(existing?.expiryMillis ?: System.currentTimeMillis())
    }
    var error by remember(existing?.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(if (existing == null) R.string.new_product else R.string.edit_product),
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (imageUrl.isNotBlank()) {
                    ProductThumbnail(
                        ExpiryItem(0, name.ifBlank { "Producto" }, category, date, 0, barcode, imageUrl),
                        110.dp
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = false
                    },
                    label = { Text(stringResource(R.string.product)) },
                    singleLine = true,
                    isError = error,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(stringResource(R.string.category_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text(stringResource(R.string.barcode_optional)) },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = onScanBarcode) {
                            Icon(Icons.Default.QrCodeScanner, stringResource(R.string.scan_code))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                FilledTonalButton(
                    onClick = onScanBarcode,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.QrCodeScanner, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.scan_code))
                }

                OutlinedButton(
                    onClick = {
                        val c = Calendar.getInstance().apply { timeInMillis = date }
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                c.set(y, m, d, 12, 0, 0)
                                c.set(Calendar.MILLISECOND, 0)
                                date = c.timeInMillis
                            },
                            c.get(Calendar.YEAR),
                            c.get(Calendar.MONTH),
                            c.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CalendarToday, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.expiry_date, dateText(date)))
                }

                OutlinedTextField(
                    value = reminder,
                    onValueChange = { if (it.all(Char::isDigit)) reminder = it },
                    label = { Text(stringResource(R.string.reminder_days)) },
                    supportingText = { Text(stringResource(R.string.reminder_help)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        error = true
                        return@Button
                    }

                    val id = existing?.id ?: UUID.randomUUID().mostSignificantBits
                    onSave(
                        ExpiryItem(
                            id = id,
                            name = name.trim(),
                            category = category.trim(),
                            expiryMillis = date,
                            reminderDays = reminder.toIntOrNull()?.coerceIn(0, 365) ?: 7,
                            barcode = barcode.trim(),
                            imageUrl = imageUrl.trim()
                        )
                    )
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun scheduleReminder(context: Context, item: ExpiryItem) {
    if (item.reminderDays < 0) return
    val trigger = ExpiryDateUtils.reminderTrigger(item.expiryMillis, item.reminderDays)
    if (trigger <= System.currentTimeMillis()) return

    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, ExpiryAlarmReceiver::class.java).apply {
        putExtra("id", item.id)
    }
    val requestCode = (item.id xor (item.id ushr 32)).toInt()
    val pi = PendingIntent.getBroadcast(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
}

private fun cancelReminder(context: Context, id: Long) {
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, ExpiryAlarmReceiver::class.java).apply {
        putExtra("id", id)
    }
    val requestCode = (id xor (id ushr 32)).toInt()
    val pi = PendingIntent.getBroadcast(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    am.cancel(pi)
    pi.cancel()
}
