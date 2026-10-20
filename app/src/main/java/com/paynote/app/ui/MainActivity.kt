package com.paynote.app.ui

import android.Manifest
import android.app.NotificationManager
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.paynote.app.data.TransactionDatabase
import com.paynote.app.model.*
import com.paynote.app.dedup.DeduplicationEngine
import com.paynote.app.parser.TransactionParser
import com.paynote.app.receiver.PurposeReplyReceiver
import com.paynote.app.service.TransactionNotificationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    private var requestedTransaction by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        readTransactionIntent(intent)
        setContent { PayNoteApp(requestedTransaction, onRequestConsumed = { requestedTransaction = null }) }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); readTransactionIntent(intent) }
    private fun readTransactionIntent(intent: Intent) { requestedTransaction = intent.getLongExtra(PurposeReplyReceiver.EXTRA_TRANSACTION_ID, -1L).takeIf { it > 0 } }
}

@Composable
fun PayNoteApp(requestedTransaction: Long?, onRequestConsumed: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("paynote_preferences", Context.MODE_PRIVATE) }
    var theme by rememberSaveable { mutableStateOf(ThemeMode.entries.firstOrNull { it.name == preferences.getString("theme", null) } ?: ThemeMode.SYSTEM) }
    PayNoteTheme(theme) { dark ->
        val colors = MaterialTheme.colorScheme
        SideEffect {
            val window = (context as ComponentActivity).window
            WindowCompat.getInsetsController(window, window.decorView).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark }
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(colors.background.toArgb()))
        }
        PayNoteContent(theme, { theme = it; preferences.edit().putString("theme", it.name).apply() }, requestedTransaction, onRequestConsumed)
    }
}

@Composable
private fun PayNoteContent(theme: ThemeMode, onTheme: (ThemeMode) -> Unit, requestedTransaction: Long?, onRequestConsumed: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { TransactionDatabase.getInstance(context).transactionDao() }
    val transactions by dao.observeAllTransactions().collectAsState(initial = emptyList())
    var page by rememberSaveable { mutableStateOf(MainPage.HOME) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedWallet by rememberSaveable { mutableStateOf<String?>(null) }
    var tools by rememberSaveable { mutableStateOf(false) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var savingEntry by remember { mutableStateOf(false) }
    var entryError by remember { mutableStateOf<String?>(null) }
    var filters by rememberSaveable(stateSaver = ActivityFiltersSaver) { mutableStateOf(ActivityFilters()) }
    var transferHelp by remember { mutableStateOf(false) }
    var sampleRunning by remember { mutableStateOf(false) }
    var listenerOn by remember { mutableStateOf(isNotificationServiceEnabled(context)) }
    var promptsOn by remember { mutableStateOf(canPostNotifications(context)) }
    val snackbar = remember { SnackbarHostState() }
    val homeList = rememberLazyListState()
    val activityList = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val selected = transactions.firstOrNull { it.id == selectedId }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { promptsOn = canPostNotifications(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { listenerOn = isNotificationServiceEnabled(context); promptsOn = canPostNotifications(context) } }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { TransactionNotificationService.repairIncompleteTransactions(context) } }
    LaunchedEffect(requestedTransaction, transactions) {
        if (requestedTransaction != null && transactions.any { it.id == requestedTransaction }) { selectedId = requestedTransaction; onRequestConsumed() }
    }
    val setup = {
        if (!promptsOn && Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
    val openActivity: (ActivityFilters) -> Unit = { filters = it; selectedWallet = null; tools = false; selectedId = null; page = MainPage.ACTIVITY; scope.launch { activityList.scrollToItem(0) } }
    val openTransaction: (Long) -> Unit = { keyboard?.hide(); selectedId = it }
    val addEntry = { selectedId = null; entryError = null; editorOpen = true }
    val back = { if (editorOpen) { editorOpen = false; entryError = null } else if (selectedId != null) selectedId = null else if (selectedWallet != null) selectedWallet = null else if (tools) tools = false else page = MainPage.HOME }
    BackHandler(enabled = editorOpen || selectedId != null || selectedWallet != null || tools || page != MainPage.HOME, onBack = back)
    val destination = when { editorOpen -> "editor"; selected != null -> "detail:${selected.id}"; selectedWallet != null -> "wallet:$selectedWallet"; tools -> "tools"; else -> page.name }
    val nested = editorOpen || selected != null || selectedWallet != null || tools
    Scaffold(containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = { if (!nested) PayNoteNavigation(page) { keyboard?.hide(); page = it } }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        AnimatedContent(targetState = destination, modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), label = "page transition",
            transitionSpec = { (fadeIn(tween(210)) + slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 14 }) togetherWith
                (fadeOut(tween(100)) + slideOutHorizontally(tween(180)) { -it / 20 }) }) { screen ->
            when {
                screen == "editor" -> TransactionEditorScreen(selected, wallets = transactions.map { it.walletName }.distinct(), saving = savingEntry, error = entryError,
                    onCancel = { if (!savingEntry) { editorOpen = false; entryError = null } }, onSave = { draft ->
                        if (!savingEntry) {
                            savingEntry = true
                            scope.launch {
                                try {
                                    val row = selected
                                    if (row == null) selectedId = dao.insert(draft.toManualEntry())
                                    else {
                                        dao.updateUserDetails(row.id, draft.wallet.trim(), draft.counterparty.trim(), draft.direction,
                                            draft.category.trim().ifEmpty { null }, draft.purpose.trim().ifEmpty { null })
                                        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(row.id.toInt())
                                    }
                                    editorOpen = false; entryError = null
                                } catch (_: Exception) { entryError = "Could not save this entry. Please try again." }
                                finally { savingEntry = false }
                            }
                        }
                    })
                screen.startsWith("detail:") -> transactions.firstOrNull { it.id == screen.substringAfter(':').toLongOrNull() }?.let { row ->
                    TransactionDetailScreen(row, onBack = { selectedId = null }, onSavePurpose = { purpose -> scope.launch {
                        try { dao.updatePurpose(row.id, purpose); (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(row.id.toInt()); snackbar.showSnackbar("Purpose saved") }
                        catch (_: Exception) { snackbar.showSnackbar("Could not save the purpose. Please try again.") }
                    } }, onDelete = { scope.launch {
                        dao.deleteById(row.id); (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(row.id.toInt()); selectedId = null
                        if (snackbar.showSnackbar("Entry deleted", actionLabel = "Undo", duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) dao.insert(row)
                    } }, onExport = { exportTransactionsCsv(context, listOf(row)) }, onEdit = { entryError = null; editorOpen = true })
                }
                screen.startsWith("wallet:") -> WalletDetailScreen(screen.substringAfter(':'), transactions, onBack = { selectedWallet = null }, onActivity = openActivity, onOpen = openTransaction)
                screen == "tools" -> ToolsScreen(onBack = { tools = false }, running = sampleRunning) { kind -> scope.launch {
                    sampleRunning = true
                    try { snackbar.showSnackbar(runSample(context, kind)) }
                    catch (_: Exception) { snackbar.showSnackbar("The sample could not run. Check notification permissions.") }
                    finally { sampleRunning = false }
                } }
                screen == MainPage.HOME.name -> OverviewScreen(transactions, listenerOn, promptsOn, homeList, setup, openActivity, openTransaction, addEntry) { transferHelp = true }
                screen == MainPage.ACTIVITY.name -> ActivityScreen(transactions, filters, activityList, onFilters = { filters = it }, onOpen = openTransaction, onAdd = addEntry) { exportTransactionsCsv(context, it) }
                screen == MainPage.WALLETS.name -> WalletsScreen(transactions) { selectedWallet = it }
                else -> SettingsScreen(theme, onTheme, listenerOn, promptsOn,
                    onListenerSettings = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    onPromptSettings = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) },
                    onExport = { exportTransactionsCsv(context, transactions) }, onTools = { tools = true }, onTransferHelp = { transferHelp = true })
            }
        }
    }
    if (transferHelp) TransferHelpSheet { transferHelp = false }
}

fun isNotificationServiceEnabled(context: Context): Boolean {
    val component = ComponentName(context, TransactionNotificationService::class.java)
    return Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")?.split(':')?.contains(component.flattenToString()) == true
}

private fun canPostNotifications(context: Context) = (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
    (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).areNotificationsEnabled()

fun exportTransactionsCsv(context: Context, rows: List<TransactionEntity>) {
    if (rows.isEmpty()) return
    val folder = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(folder, "PayNote_${System.currentTimeMillis()}.csv").apply { writeText("\uFEFF" + transactionCsv(rows), Charsets.UTF_8) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_SUBJECT, "PayNote activity")
        clipData = ClipData.newRawUri("PayNote activity", uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Export activity"))
}

private suspend fun runSample(context: Context, kind: String): String {
    val now = System.currentTimeMillis()
    val data = when (kind) {
        "sadapay" -> Triple("com.sadapay.app", "SadaPay sample", "You sent Rs. 2,500 to Ali Khan. TID: 84920174")
        "meezan" -> Triple("pk.com.meezanbank.mobilebanking", "Meezan sample", "PKR 4,800.00 debited from A/C *4910 for IBFT to HASSAN RAZA on 20-Oct-26. STAN: 592014")
        else -> Triple("com.google.android.apps.walletnfcrel", "Google Wallet sample", "Paid Rs. 1,450 at Espresso Coffee with Visa ••4021")
    }
    val parsed = TransactionParser.parse(data.first, data.second, data.third, now) ?: return "The sample could not be parsed"
    TransactionNotificationService.processParsedNotification(context, parsed)
    if (kind == "sadapay") {
        val sms = TransactionParser.parse("com.google.android.apps.messaging", "SadaPay", "PKR 2,500.00 sent to ALI KHAN (Meezan Bank). Ref: 000084920174.", now + 4_000)!!
        val duplicate = TransactionNotificationService.processParsedNotification(context, sms)
        return if (duplicate is DeduplicationEngine.DedupDecision.DuplicateOf) "One entry saved. Duplicate SMS suppressed." else "Sample completed"
    }
    return "Sample entry ready. Check your notification."
}
