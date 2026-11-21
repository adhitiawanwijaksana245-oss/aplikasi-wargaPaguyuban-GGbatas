package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.UserRole
import com.example.ui.components.*
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NavyPrimary
import com.example.ui.viewmodel.WargaViewModel
import com.example.util.BackgroundSyncScheduler
import com.example.util.NotificationHelper
import com.example.util.ReminderRingtonePlayer
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val viewModel: WargaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Android notification channels for push notifications
        NotificationHelper.initNotificationChannels(this)
        BackgroundSyncScheduler.schedulePeriodicSync(this)
        handleNotificationIntent(intent)

        setContent {
            MyApplicationTheme {
                var showSplash by rememberSaveable { mutableStateOf(true) }

                LaunchedEffect(showSplash) {
                    if (showSplash) {
                        delay(4000L)
                        showSplash = false
                    }
                }

                Crossfade(
                    targetState = showSplash,
                    animationSpec = tween(durationMillis = 500),
                    label = "splash_to_main_transition"
                ) { isSplashVisible ->
                    if (isSplashVisible) {
                        SplashScreen()
                    } else {
                        MainContent(viewModel = viewModel)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN && ReminderRingtonePlayer.isPlaying.value) {
            NotificationHelper.silenceActiveRingtone(this)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(NotificationHelper.EXTRA_STOP_RINGTONE, false) == true) {
            NotificationHelper.silenceActiveRingtone(this)
        }
        val targetTab = intent?.getIntExtra(NotificationHelper.EXTRA_TARGET_TAB, -1) ?: -1
        if (targetTab in 0..5) {
            viewModel.selectTab(targetTab)
        }
    }
}

@Composable
fun MainContent(viewModel: WargaViewModel) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val userRole by viewModel.userRole.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
    val needsScriptUpdate by viewModel.needsScriptUpdate.collectAsStateWithLifecycle()
    val sheetUrl by viewModel.sheetUrl.collectAsStateWithLifecycle()
    val firestoreSyncState by viewModel.firestoreSyncState.collectAsStateWithLifecycle()

    val displaySyncMessage = when {
        syncMessage?.startsWith("⚠️") == true -> syncMessage
        firestoreSyncState.isConnected -> firestoreSyncState.message
        else -> syncMessage ?: firestoreSyncState.message
    }

    val kasList by viewModel.kasList.collectAsStateWithLifecycle()
    val kasRolling12 by viewModel.kasRolling12.collectAsStateWithLifecycle()
    val kasRekapBulanan by viewModel.kasRekapBulanan.collectAsStateWithLifecycle()
    val pengurusList by viewModel.pengurusList.collectAsStateWithLifecycle()
    val undanganList by viewModel.undanganList.collectAsStateWithLifecycle()
    val galeriList by viewModel.galeriList.collectAsStateWithLifecycle()
    val aspirasiList by viewModel.aspirasiList.collectAsStateWithLifecycle()

    val saldoKas by viewModel.saldoKas.collectAsStateWithLifecycle()
    val totalMasuk by viewModel.totalMasuk.collectAsStateWithLifecycle()
    val totalKeluar by viewModel.totalKeluar.collectAsStateWithLifecycle()

    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val showNotificationDialog by viewModel.showNotificationDialog.collectAsStateWithLifecycle()
    val unreadNotifications = notifications.count { !it.isRead }

    // Dialog visibility states
    val showAuthorityDialog by viewModel.showAuthorityDialog.collectAsStateWithLifecycle()
    val authorityError by viewModel.authorityError.collectAsStateWithLifecycle()
    val showConfigDialog by viewModel.showConfigDialog.collectAsStateWithLifecycle()
    val showChangePasswordDialog by viewModel.showChangePasswordDialog.collectAsStateWithLifecycle()
    val changePasswordError by viewModel.changePasswordError.collectAsStateWithLifecycle()

    val showAddKasDialog by viewModel.showAddKasDialog.collectAsStateWithLifecycle()
    val editingKasItem by viewModel.editingKasItem.collectAsStateWithLifecycle()

    val showAddPengurusDialog by viewModel.showAddPengurusDialog.collectAsStateWithLifecycle()
    val editingPengurusItem by viewModel.editingPengurusItem.collectAsStateWithLifecycle()

    val showAddUndanganDialog by viewModel.showAddUndanganDialog.collectAsStateWithLifecycle()
    val editingUndanganItem by viewModel.editingUndanganItem.collectAsStateWithLifecycle()

    val showEventReminderDialog by viewModel.showEventReminderDialog.collectAsStateWithLifecycle()
    val selectedUndanganForReminder by viewModel.selectedUndanganForReminder.collectAsStateWithLifecycle()

    val showAddGaleriDialog by viewModel.showAddGaleriDialog.collectAsStateWithLifecycle()
    val editingGaleriItem by viewModel.editingGaleriItem.collectAsStateWithLifecycle()
    val showAspirasiDialog by viewModel.showAspirasiDialog.collectAsStateWithLifecycle()

    val showRespondAspirasiDialog by viewModel.showRespondAspirasiDialog.collectAsStateWithLifecycle()
    val selectedAspirasiForResponse by viewModel.selectedAspirasiForResponse.collectAsStateWithLifecycle()

    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val photoUploadWarning by viewModel.photoUploadWarning.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Request notification runtime permission on Android 13+ (API 33)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val appContext = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.syncOnResume()
                Lifecycle.Event.ON_STOP -> {
                    BackgroundSyncScheduler.schedulePeriodicSync(appContext)
                    BackgroundSyncScheduler.scheduleDelayedCheck(appContext, delayMinutes = 2L)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Long
            )
            viewModel.clearSnackbar()
        }
    }

    // Handle Android system Back button: if not on Beranda (0), back to Beranda
    BackHandler(enabled = currentTab != 0) {
        viewModel.selectTab(0)
    }

    val context = LocalContext.current
    val isRingtonePlaying by ReminderRingtonePlayer.isPlaying.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppHeader(
                userRole = userRole,
                isSyncing = isSyncing,
                unreadNotificationCount = unreadNotifications,
                onRoleClick = {
                    if (userRole == UserRole.WARGA) {
                        viewModel.requireAuthority { /* Role is updated to PENGURUS */ }
                    } else {
                        viewModel.lockToWargaMode()
                    }
                },
                onRefreshClick = { viewModel.triggerRefresh() },
                onNotificationClick = { viewModel.openNotificationDialog() },
                onOpenSettings = { viewModel.openConfigDialog() },
                onOpenChangePassword = { viewModel.openChangePasswordDialog() },
                onOpenReminderMode = { viewModel.openEventReminderDialog() }
            )
        },
        bottomBar = {
            Surface(
                color = Color.White,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bottom_nav_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .height(60.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val navTabs = listOf(
                        WargaNavTab("Beranda", Icons.Filled.Home, Icons.Outlined.Home, "tab_beranda"),
                        WargaNavTab("Kas RT", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet, "tab_kas_rt"),
                        WargaNavTab("Pengurus", Icons.Filled.Groups, Icons.Outlined.Groups, "tab_pengurus"),
                        WargaNavTab("Undangan", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth, "tab_undangan"),
                        WargaNavTab("Aspirasi", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline, "tab_aspirasi"),
                        WargaNavTab("Galeri", Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary, "tab_galeri")
                    )

                    navTabs.forEachIndexed { index, tab ->
                        val selected = currentTab == index
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { viewModel.selectTab(index) }
                                .testTag(tab.tag)
                                .padding(horizontal = 1.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) Color(0xFFE2E8F0) else Color.Transparent)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label,
                                    tint = if (selected) NavyPrimary else Color(0xFF64748B),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tab.label,
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) NavyPrimary else Color(0xFF64748B),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = isRingtonePlaying) {
                Surface(
                    color = Color(0xFFEA580C),
                    contentColor = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { NotificationHelper.silenceActiveRingtone(context) }
                        .testTag("active_ringtone_banner")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = "Pengingat sedang berbunyi",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pengingat Warga Berbunyi • Ketuk di mana saja untuk mematikan suara",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (currentTab) {
                0 -> BerandaScreen(
                    saldoKas = saldoKas,
                    totalMasuk = totalMasuk,
                    totalKeluar = totalKeluar,
                    latestKasPeriodLabel = kasRekapBulanan.lastOrNull()?.labelBulan.orEmpty(),
                    upcomingUndangan = undanganList,
                    recentAspirasi = aspirasiList,
                    syncStatusMessage = displaySyncMessage,
                    userRole = userRole,
                    onNavigateTab = { viewModel.selectTab(it) },
                    onOpenAspirasiDialog = { viewModel.openAspirasiDialog() },
                    onOpenReminderMode = { item -> viewModel.openEventReminderDialog(item) },
                    onCancelReminder = { item -> viewModel.cancelEventReminder(item) },
                    onOpenConfigDialog = { viewModel.openConfigDialog() }
                )
                1 -> KasRTScreen(
                    kasList = kasList,
                    rolling12 = kasRolling12,
                    rekapBulanan = kasRekapBulanan,
                    saldoKas = saldoKas,
                    totalMasuk = totalMasuk,
                    totalKeluar = totalKeluar,
                    userRole = userRole,
                    onAddKasClick = { viewModel.openAddKasDialog() },
                    onEditKasClick = { item -> viewModel.openAddKasDialog(item) },
                    onDeleteKasClick = { id -> viewModel.deleteKasTransaction(id) }
                )
                2 -> PengurusScreen(
                    pengurusList = pengurusList,
                    userRole = userRole,
                    onAddPengurusClick = { viewModel.openPengurusDialog() },
                    onEditPengurusClick = { item -> viewModel.openPengurusDialog(item) },
                    onDeletePengurusClick = { id -> viewModel.deletePengurus(id) }
                )
                3 -> UndanganScreen(
                    undanganList = undanganList,
                    userRole = userRole,
                    onAddUndanganClick = { viewModel.openUndanganDialog() },
                    onEditUndanganClick = { item -> viewModel.openUndanganDialog(item) },
                    onDeleteUndanganClick = { id -> viewModel.deleteUndangan(id) },
                    onOpenReminderMode = { item -> viewModel.openEventReminderDialog(item) },
                    onCancelReminder = { item -> viewModel.cancelEventReminder(item) }
                )
                4 -> AspirasiScreen(
                    aspirasiList = aspirasiList,
                    userRole = userRole,
                    sheetUrl = sheetUrl,
                    onOpenConfigDialog = { viewModel.openConfigDialog() },
                    onOpenSubmitAspirasi = { viewModel.openAspirasiDialog() },
                    onRespondAspirasi = { item -> viewModel.openRespondAspirasiDialog(item) },
                    onDeleteAspirasi = { id -> viewModel.deleteAspirasi(id) }
                )
                5 -> GaleriScreen(
                    galeriList = galeriList,
                    userRole = userRole,
                    isSyncing = isSyncing,
                    needsScriptUpdate = needsScriptUpdate,
                    onRefreshClick = { viewModel.triggerRefresh() },
                    onOpenConfigDialog = { viewModel.openConfigDialog() },
                    onAddGaleriClick = { viewModel.openAddGaleriDialog() },
                    onEditGaleriClick = { item -> viewModel.openAddGaleriDialog(item) },
                    onDeleteGaleriClick = { id -> viewModel.deleteGaleri(id) }
                )
                }
            }
        }
    }

    // --- DIALOGS ---

    // Authority Password Dialog
    if (showAuthorityDialog) {
        AuthorityPasswordDialog(
            errorMessage = authorityError,
            onDismiss = { viewModel.dismissAuthorityDialog() },
            onSubmit = { enteredPassword -> viewModel.verifyPassword(enteredPassword) }
        )
    }

    // Notification Center Dialog
    if (showNotificationDialog) {
        NotificationCenterDialog(
            notifications = notifications,
            onDismiss = { viewModel.dismissNotificationDialog() },
            onClearAll = { NotificationHelper.clearNotifications() }
        )
    }

    // Google Sheets Config & Script Copy Dialog (Hanya untuk Pengurus/Admin, warga otomatis tersinkron)
    if (showConfigDialog && userRole != UserRole.WARGA) {
        SheetConfigDialog(
            currentUrl = sheetUrl,
            onDismiss = { viewModel.dismissConfigDialog() },
            onSaveUrl = { newUrl -> viewModel.saveSheetUrl(newUrl) },
            onTestConnection = { urlToTest, onResult -> viewModel.testSpreadsheetConnection(urlToTest, onResult) },
            onBackupAll = { urlToUse -> viewModel.backupAllToSpreadsheet(urlToUse) }
        )
    }

    // Change Authority Password Dialog (Wajib Password Khusus Amm080309)
    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            errorMessage = changePasswordError,
            onVerifyMasterPassword = { masterPass -> viewModel.verifyMasterPasswordForChange(masterPass) },
            onClearError = { viewModel.clearChangePasswordError() },
            onDismiss = { viewModel.closeChangePasswordDialog() },
            onSubmit = { masterPass, newPass -> viewModel.changePassword(masterPass, newPass) }
        )
    }

    // Kas Transaction Form Dialog
    if (showAddKasDialog) {
        KasTransactionDialog(
            item = editingKasItem,
            onDismiss = { viewModel.dismissAddKasDialog() },
            onSubmit = { item -> viewModel.saveKasTransaction(item) }
        )
    }

    // Pengurus Form Dialog
    if (showAddPengurusDialog) {
        PengurusDialog(
            item = editingPengurusItem,
            onDismiss = { viewModel.dismissPengurusDialog() },
            onSubmit = { item, isEdit -> viewModel.savePengurus(item, isEdit) }
        )
    }

    // Undangan Form Dialog
    if (showAddUndanganDialog) {
        UndanganDialog(
            item = editingUndanganItem,
            onDismiss = { viewModel.dismissUndanganDialog() },
            onSubmit = { item, isEdit -> viewModel.saveUndangan(item, isEdit) }
        )
    }

    // Event Reminder Mode Dialog (Strictly Pengurus only)
    if (showEventReminderDialog && userRole != UserRole.WARGA) {
        EventReminderDialog(
            undanganList = undanganList,
            initialSelectedItem = selectedUndanganForReminder,
            onDismiss = { viewModel.dismissEventReminderDialog() },
            onCreateNewEvent = { viewModel.openUndanganDialog() },
            onActivateReminder = { item, minutes, mode ->
                viewModel.activateEventReminder(item, minutes, mode)
            },
            onCancelReminder = { item ->
                viewModel.cancelEventReminder(item)
            }
        )
    }

    // Galeri Form Dialog
    if (showAddGaleriDialog) {
        GaleriDialog(
            item = editingGaleriItem,
            onDismiss = { viewModel.dismissAddGaleriDialog() },
            onSubmit = { item -> viewModel.saveGaleri(item) }
        )
    }

    // Citizen Aspirasi Submission Dialog (Accessible to all Warga)
    if (showAspirasiDialog) {
        AspirasiDialog(
            onDismiss = { viewModel.dismissAspirasiDialog() },
            onSubmit = { item -> viewModel.submitAspirasi(item) }
        )
    }

    // Pengurus Response to Aspirasi Dialog
    if (showRespondAspirasiDialog && selectedAspirasiForResponse != null) {
        TanggapanAspirasiDialog(
            item = selectedAspirasiForResponse!!,
            onDismiss = { viewModel.dismissRespondAspirasiDialog() },
            onSubmit = { status, tanggapan, fotoPengerjaan ->
                viewModel.saveTanggapanAspirasi(
                    selectedAspirasiForResponse!!.id,
                    status,
                    tanggapan,
                    fotoPengerjaan
                )
            }
        )
    }

    // Photo Upload Failure Warning Dialog (Khusus Pengurus agar langsung tahu penyebab foto tidak masuk Spreadsheet)
    photoUploadWarning?.let { warningMsg ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissPhotoUploadWarning() },
            icon = {
                Icon(
                    imageVector = Icons.Filled.CloudOff,
                    contentDescription = null,
                    tint = Color(0xFFD97706)
                )
            },
            title = {
                Text(
                    text = "Foto Belum Masuk Spreadsheet",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = "$warningMsg\n\nPastikan Apps Script v9 sudah di-Deploy dengan Version: 'New version' dan fungsi 'otorisasiDanCekSetup' sudah dijalankan 1x di Google Apps Script.",
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissPhotoUploadWarning()
                        viewModel.openConfigDialog()
                    }
                ) {
                    Text("Buka Pengaturan Sync")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPhotoUploadWarning() }) {
                    Text("Tutup")
                }
            }
        )
    }
}

private data class WargaNavTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
)
