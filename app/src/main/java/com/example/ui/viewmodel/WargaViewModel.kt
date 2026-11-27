package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.WargaRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class WargaViewModel(application: Application) : AndroidViewModel(application) {
    internal val repository = WargaRepository(application)

    val kasList: StateFlow<List<KasItem>> = repository.kasList
    val kasRolling12: StateFlow<List<KasMonthlyRecap>> = repository.rolling12List
    val kasRekapBulanan: StateFlow<List<KasMonthlyRecap>> = repository.rekapBulananList
    val kasSummaryTotals: StateFlow<KasSummaryTotals> = repository.kasSummaryTotals
    val pengurusList: StateFlow<List<PengurusItem>> = repository.pengurusList
    val undanganList: StateFlow<List<UndanganItem>> = repository.undanganList
    val galeriList: StateFlow<List<GaleriItem>> = repository.galeriList
    val aspirasiList: StateFlow<List<AspirasiItem>> = repository.aspirasiList
    val userRole: StateFlow<UserRole> = repository.userRole
    val currentPengurusAccount: StateFlow<PengurusAccount?> = repository.currentPengurusAccount
    val activeRoleDisplayName: StateFlow<String> = repository.activeRoleDisplayName
    val pengurusAccounts: StateFlow<List<PengurusAccount>> = repository.pengurusAccounts
    val splashDurationMs: StateFlow<Long> = repository.splashDurationMs
    val isSyncing: StateFlow<Boolean> = repository.isSyncing
    val syncMessage: StateFlow<String?> = repository.syncMessage
    val needsScriptUpdate: StateFlow<Boolean> = repository.needsScriptUpdate
    val sheetUrl: StateFlow<String> = repository.sheetUrl
    val firestoreSyncState = repository.firestoreSyncState

    // Navigation & UI Tab State
    private val _currentTab = MutableStateFlow(0) // 0: Beranda, 1: Kas RT, 2: Pengurus, 3: Undangan, 4: Aspirasi, 5: Galeri
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    // Authority & Security Dialogs
    private val _showAuthorityDialog = MutableStateFlow(false)
    val showAuthorityDialog: StateFlow<Boolean> = _showAuthorityDialog.asStateFlow()

    private val _authorityTargetAction = MutableStateFlow<(() -> Unit)?>(null)

    private val _authorityError = MutableStateFlow<String?>(null)
    val authorityError: StateFlow<String?> = _authorityError.asStateFlow()

    private val _showConfigDialog = MutableStateFlow(false)
    val showConfigDialog: StateFlow<Boolean> = _showConfigDialog.asStateFlow()

    private val _showChangePasswordDialog = MutableStateFlow(false)
    val showChangePasswordDialog: StateFlow<Boolean> = _showChangePasswordDialog.asStateFlow()

    private val _changePasswordError = MutableStateFlow<String?>(null)
    val changePasswordError: StateFlow<String?> = _changePasswordError.asStateFlow()

    private val _showPengurusAccountsDialog = MutableStateFlow(false)
    val showPengurusAccountsDialog: StateFlow<Boolean> = _showPengurusAccountsDialog.asStateFlow()

    private val _showSplashSettingsDialog = MutableStateFlow(false)
    val showSplashSettingsDialog: StateFlow<Boolean> = _showSplashSettingsDialog.asStateFlow()

    private val _showNotificationToneDialog = MutableStateFlow(false)
    val showNotificationToneDialog: StateFlow<Boolean> = _showNotificationToneDialog.asStateFlow()

    // Entity Creation/Edit Dialogs
    private val _showAddKasDialog = MutableStateFlow(false)
    val showAddKasDialog: StateFlow<Boolean> = _showAddKasDialog.asStateFlow()
    private val _editingKasItem = MutableStateFlow<KasItem?>(null)
    val editingKasItem: StateFlow<KasItem?> = _editingKasItem.asStateFlow()

    private val _showAddPengurusDialog = MutableStateFlow(false)
    val showAddPengurusDialog: StateFlow<Boolean> = _showAddPengurusDialog.asStateFlow()
    private val _editingPengurusItem = MutableStateFlow<PengurusItem?>(null)
    val editingPengurusItem: StateFlow<PengurusItem?> = _editingPengurusItem.asStateFlow()

    private val _showAddUndanganDialog = MutableStateFlow(false)
    val showAddUndanganDialog: StateFlow<Boolean> = _showAddUndanganDialog.asStateFlow()
    private val _editingUndanganItem = MutableStateFlow<UndanganItem?>(null)
    val editingUndanganItem: StateFlow<UndanganItem?> = _editingUndanganItem.asStateFlow()

    private val _showEventReminderDialog = MutableStateFlow(false)
    val showEventReminderDialog: StateFlow<Boolean> = _showEventReminderDialog.asStateFlow()
    private val _selectedUndanganForReminder = MutableStateFlow<UndanganItem?>(null)
    val selectedUndanganForReminder: StateFlow<UndanganItem?> = _selectedUndanganForReminder.asStateFlow()

    private val _showAddGaleriDialog = MutableStateFlow(false)
    val showAddGaleriDialog: StateFlow<Boolean> = _showAddGaleriDialog.asStateFlow()
    private val _editingGaleriItem = MutableStateFlow<GaleriItem?>(null)
    val editingGaleriItem: StateFlow<GaleriItem?> = _editingGaleriItem.asStateFlow()

    private val _showAspirasiDialog = MutableStateFlow(false)
    val showAspirasiDialog: StateFlow<Boolean> = _showAspirasiDialog.asStateFlow()

    private val _showRespondAspirasiDialog = MutableStateFlow(false)
    val showRespondAspirasiDialog: StateFlow<Boolean> = _showRespondAspirasiDialog.asStateFlow()
    private val _selectedAspirasiForResponse = MutableStateFlow<AspirasiItem?>(null)
    val selectedAspirasiForResponse: StateFlow<AspirasiItem?> = _selectedAspirasiForResponse.asStateFlow()

    // Toast / Feedback message
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _photoUploadWarning = MutableStateFlow<String?>(null)
    val photoUploadWarning: StateFlow<String?> = _photoUploadWarning.asStateFlow()

    fun dismissPhotoUploadWarning() {
        _photoUploadWarning.value = null
    }

    private fun handleCloudSaveResult(result: CloudSaveResult) {
        _snackbarMessage.value = result.message
        if (result.photoRequested && !result.photoUploadedToCloud && userRole.value != UserRole.WARGA) {
            _photoUploadWarning.value = result.message
        }
    }

    // Computed Stats (Single source of truth synchronized across Beranda & Kas RT)
    val totalMasuk: StateFlow<Long> = kasSummaryTotals.map { it.totalMasuk }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.kasSummaryTotals.value.totalMasuk)

    val totalKeluar: StateFlow<Long> = kasSummaryTotals.map { it.totalKeluar }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.kasSummaryTotals.value.totalKeluar)

    val saldoKas: StateFlow<Long> = kasSummaryTotals.map { it.saldoKas }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            repository.kasSummaryTotals.value.saldoKas
        )

    init {
        // Periodic polling for real-time updates (initial sync starts automatically in WargaRepository.init)
        viewModelScope.launch {
            while (true) {
                delay(20000) // Poll every 20s automatically without user setup
                if (repository.sheetUrl.value.isNotBlank()) {
                    repository.refreshIfStaleOrUnverified(minIntervalMs = 15000L)
                }
            }
        }

        // Periodic check for active scheduled event reminders on citizens' & pengurus' phones
        viewModelScope.launch {
            while (true) {
                com.example.util.ReminderScheduler.checkAndFireDueReminders(getApplication(), undanganList.value)
                delay(5000)
            }
        }
    }

    fun selectTab(tabIndex: Int) {
        _currentTab.value = tabIndex
        if (repository.sheetUrl.value.isNotBlank()) {
            viewModelScope.launch {
                if (tabIndex == 4) {
                    repository.syncWithSheet(force = true)
                } else {
                    repository.refreshIfStaleOrUnverified(minIntervalMs = 4000L)
                }
            }
        }
    }

    fun syncOnResume() {
        if (repository.isSyncing.value || repository.sheetUrl.value.isBlank()) return
        viewModelScope.launch {
            if (_currentTab.value == 4) {
                repository.syncWithSheet(force = true)
            } else {
                repository.refreshIfStaleOrUnverified(minIntervalMs = 4000L)
            }
        }
    }

    fun triggerRefresh() {
        viewModelScope.launch {
            repository.syncManager.ensureRealtimeSyncConnected()
            try {
                repository.syncManager.flushPendingSync()
            } catch (_: Exception) {}
            val result = repository.syncWithSheet(force = true)
            if (result.isSuccess) {
                _snackbarMessage.value = "Data terbaru berhasil disinkronkan!"
            } else {
                _snackbarMessage.value = "Menampilkan data tersimpan (menunggu koneksi internet)"
            }
        }
    }

    fun saveSheetUrl(url: String) {
        if (userRole.value == UserRole.WARGA) return
        repository.updateSheetUrlOnly(url)
        _showConfigDialog.value = false
        triggerRefresh()
    }

    val notifications = com.example.util.NotificationHelper.notifications

    private val _showNotificationDialog = MutableStateFlow(false)
    val showNotificationDialog: StateFlow<Boolean> = _showNotificationDialog.asStateFlow()

    fun openNotificationDialog() {
        _showNotificationDialog.value = true
        com.example.util.NotificationHelper.markAllAsRead()
    }

    fun dismissNotificationDialog() {
        _showNotificationDialog.value = false
    }

    fun openNotificationToneDialog() {
        _showNotificationToneDialog.value = true
    }

    fun dismissNotificationToneDialog() {
        _showNotificationToneDialog.value = false
    }

    fun saveNotificationTone(
        option: com.example.util.NotificationToneOption,
        customUri: String,
        customTitle: String
    ) {
        val saved = com.example.util.NotificationToneManager.savePreference(
            context = getApplication(),
            option = option,
            customUri = customUri,
            customTitle = customTitle
        )
        _showNotificationToneDialog.value = false
        _snackbarMessage.value = "Nada notifikasi HP ini disimpan: ${saved.displayLabel()}"
    }

    fun testSpreadsheetConnection(urlToTest: String? = null, onResult: (Boolean, String) -> Unit) {
        if (userRole.value == UserRole.WARGA) {
            onResult(false, "Pengaturan sinkronisasi hanya tersedia untuk Pengurus RT.")
            return
        }
        viewModelScope.launch {
            val (ok, msg) = repository.testSpreadsheetConnection(urlToTest)
            onResult(ok, msg)
        }
    }

    fun backupAllToSpreadsheet(urlToUse: String? = null) {
        if (userRole.value == UserRole.WARGA) return
        val trimmed = urlToUse?.trim().orEmpty()
        if (trimmed.isNotBlank() && trimmed.contains("script.google.com/macros/s/")) {
            repository.updateSheetUrlOnly(trimmed)
        }
        viewModelScope.launch {
            _snackbarMessage.value = "Memulai backup seluruh data ke Spreadsheet..."
            val (_, msg) = repository.backupAllToSpreadsheet()
            _snackbarMessage.value = msg
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    // --- Authority Logic ---
    fun requireAuthority(onSuccess: () -> Unit) {
        if (userRole.value != UserRole.WARGA) {
            onSuccess()
        } else {
            _authorityTargetAction.value = onSuccess
            _authorityError.value = null
            _showAuthorityDialog.value = true
        }
    }

    fun requireAdmin(onSuccess: () -> Unit) {
        when (userRole.value) {
            UserRole.ADMIN_UTAMA -> onSuccess()
            UserRole.PENGURUS -> {
                _snackbarMessage.value = "Fitur ini khusus Admin"
            }
            UserRole.WARGA -> {
                _authorityTargetAction.value = {
                    if (userRole.value == UserRole.ADMIN_UTAMA) {
                        onSuccess()
                    } else {
                        _snackbarMessage.value = "Fitur ini khusus Admin"
                    }
                }
                _authorityError.value = null
                _showAuthorityDialog.value = true
            }
        }
    }

    fun verifyPassword(username: String, password: String) {
        when (val result = repository.verifyAuthority(username, password)) {
            is WargaRepository.AuthorityLoginResult.Success -> {
                _showAuthorityDialog.value = false
                _authorityError.value = null
                _snackbarMessage.value = if (result.role == UserRole.ADMIN_UTAMA) {
                    "Akses Admin (${repository.activeRoleDisplayName.value}) Berhasil Terbuka"
                } else {
                    val nama = result.pengurusAccount?.namaAsli?.takeIf { it.isNotBlank() }
                    if (nama != null) "Akses Pengurus ($nama) Terbuka" else "Akses Pengurus RT Berhasil Terbuka"
                }
                _authorityTargetAction.value?.invoke()
                _authorityTargetAction.value = null
                viewModelScope.launch {
                    repository.refreshOnModeSwitch()
                }
            }
            is WargaRepository.AuthorityLoginResult.Failure -> {
                _authorityError.value = result.message
            }
        }
    }

    fun lockToWargaMode() {
        repository.lockToWargaMode()
        _snackbarMessage.value = "Beralih ke Mode Warga (Hanya Baca)"
        viewModelScope.launch {
            repository.refreshOnModeSwitch()
        }
    }

    fun openChangePasswordDialog() {
        requireAdmin {
            _changePasswordError.value = null
            _showChangePasswordDialog.value = true
        }
    }

    fun openPengurusAccountsDialog() {
        requireAdmin {
            _showPengurusAccountsDialog.value = true
        }
    }

    fun dismissPengurusAccountsDialog() {
        _showPengurusAccountsDialog.value = false
    }

    fun savePengurusAccount(account: PengurusAccount): Pair<Boolean, String> {
        if (userRole.value != UserRole.ADMIN_UTAMA) {
            return Pair(false, "Hanya Admin Induk yang dapat mengelola akun pengurus.")
        }
        val res = repository.savePengurusAccount(account)
        _snackbarMessage.value = res.second
        return res
    }

    fun togglePengurusAccountActive(accountId: String, aktif: Boolean) {
        requireAdmin {
            val res = repository.togglePengurusAccountActive(accountId, aktif)
            _snackbarMessage.value = res.second
        }
    }

    fun deletePengurusAccount(accountId: String) {
        requireAdmin {
            val res = repository.deletePengurusAccount(accountId)
            _snackbarMessage.value = res.second
        }
    }

    fun openSplashSettingsDialog() {
        requireAdmin {
            _showSplashSettingsDialog.value = true
        }
    }

    fun dismissSplashSettingsDialog() {
        _showSplashSettingsDialog.value = false
    }

    fun updateSplashDurationSeconds(seconds: Int) {
        requireAdmin {
            val (_, msg) = repository.updateSplashDurationMs(seconds.toLong() * 1000L)
            _showSplashSettingsDialog.value = false
            _snackbarMessage.value = msg
        }
    }

    fun closeChangePasswordDialog() {
        _changePasswordError.value = null
        _showChangePasswordDialog.value = false
    }

    fun clearChangePasswordError() {
        _changePasswordError.value = null
    }

    fun verifyMasterPasswordForChange(masterPassword: String): Boolean {
        val ok = repository.verifyChangePasswordAccess(masterPassword)
        _changePasswordError.value = if (ok) null else "HUB ADHITIAWAN"
        return ok
    }

    fun changePassword(masterPassword: String, newPass: String) {
        val ok = repository.changePassword(masterPassword, newPass)
        if (ok) {
            _changePasswordError.value = null
            _showChangePasswordDialog.value = false
            _snackbarMessage.value = "Password Otoritas Berhasil Diubah"
        } else {
            _changePasswordError.value = "HUB ADHITIAWAN"
        }
    }

    fun dismissAuthorityDialog() {
        _showAuthorityDialog.value = false
        _authorityTargetAction.value = null
        _authorityError.value = null
    }

    fun openConfigDialog() {
        requireAuthority {
            _showConfigDialog.value = true
        }
    }

    fun dismissConfigDialog() {
        _showConfigDialog.value = false
    }

    // --- Kas RT CRUD ---
    fun openAddKasDialog(item: KasItem? = null) {
        requireAuthority {
            _editingKasItem.value = item
            _showAddKasDialog.value = true
        }
    }

    fun dismissAddKasDialog() {
        _showAddKasDialog.value = false
        _editingKasItem.value = null
    }

    fun saveKasTransaction(item: KasItem) {
        if (userRole.value == UserRole.WARGA) return
        viewModelScope.launch {
            if (com.example.util.PhotoStorageHelper.isLocalUri(item.buktiFoto)) {
                _snackbarMessage.value = "Mengunggah bukti foto ke Cloud..."
            }
            dismissAddKasDialog()
            val result = repository.addKas(item)
            handleCloudSaveResult(result)
        }
    }

    fun deleteKasTransaction(id: String) {
        requireAdmin {
            viewModelScope.launch {
                repository.deleteKas(id)
                _snackbarMessage.value = "Transaksi Kas Berhasil Dihapus"
            }
        }
    }

    // --- Pengurus CRUD (Khusus Admin) ---
    fun openPengurusDialog(item: PengurusItem? = null) {
        requireAdmin {
            _editingPengurusItem.value = item
            _showAddPengurusDialog.value = true
        }
    }

    fun dismissPengurusDialog() {
        _showAddPengurusDialog.value = false
        _editingPengurusItem.value = null
    }

    fun savePengurus(item: PengurusItem, isEdit: Boolean) {
        requireAdmin {
            viewModelScope.launch {
                if (com.example.util.PhotoStorageHelper.isLocalUri(item.foto)) {
                    _snackbarMessage.value = "Mengunggah foto pengurus ke Cloud..."
                }
                dismissPengurusDialog()
                val result = repository.addOrUpdatePengurus(item, isEdit)
                handleCloudSaveResult(result)
            }
        }
    }

    fun deletePengurus(id: String) {
        requireAdmin {
            viewModelScope.launch {
                repository.deletePengurus(id)
                _snackbarMessage.value = "Data Pengurus RT Berhasil Dihapus"
            }
        }
    }

    // --- Undangan CRUD ---
    fun openUndanganDialog(item: UndanganItem? = null) {
        requireAuthority {
            _editingUndanganItem.value = item
            _showAddUndanganDialog.value = true
        }
    }

    fun dismissUndanganDialog() {
        _showAddUndanganDialog.value = false
        _editingUndanganItem.value = null
    }

    fun saveUndangan(item: UndanganItem, isEdit: Boolean) {
        requireAuthority {
            viewModelScope.launch {
                if (com.example.util.PhotoStorageHelper.isLocalUri(item.foto)) {
                    _snackbarMessage.value = "Mengunggah foto ke Cloud..."
                }
                dismissUndanganDialog()
                val result = repository.addOrUpdateUndangan(item, isEdit)
                handleCloudSaveResult(result)
            }
        }
    }

    fun deleteUndangan(id: String) {
        requireAdmin {
            viewModelScope.launch {
                repository.deleteUndangan(id)
                _snackbarMessage.value = "Undangan Berhasil Dihapus"
            }
        }
    }

    // --- Mode Pengingat Warga (Khusus Pengurus) ---
    fun openEventReminderDialog(item: UndanganItem? = null) {
        requireAuthority {
            _selectedUndanganForReminder.value = item
            _showEventReminderDialog.value = true
        }
    }

    fun dismissEventReminderDialog() {
        _showEventReminderDialog.value = false
        _selectedUndanganForReminder.value = null
    }

    fun activateEventReminder(undangan: UndanganItem, minutes: Int, mode: ReminderMode) {
        requireAuthority {
            viewModelScope.launch {
                val (ok, msg) = repository.activateEventReminder(undangan, minutes, mode)
                if (ok) {
                    dismissEventReminderDialog()
                }
                _snackbarMessage.value = msg
            }
        }
    }

    fun cancelEventReminder(undangan: UndanganItem) {
        requireAuthority {
            viewModelScope.launch {
                val (_, msg) = repository.cancelEventReminder(undangan)
                dismissEventReminderDialog()
                _snackbarMessage.value = msg
            }
        }
    }

    // --- Galeri CRUD ---
    fun openAddGaleriDialog(item: GaleriItem? = null) {
        requireAuthority {
            _editingGaleriItem.value = item
            _showAddGaleriDialog.value = true
        }
    }

    fun dismissAddGaleriDialog() {
        _showAddGaleriDialog.value = false
        _editingGaleriItem.value = null
    }

    fun saveGaleri(item: GaleriItem) {
        if (userRole.value == UserRole.WARGA) return
        viewModelScope.launch {
            if (item.foto.split(",").any { com.example.util.PhotoStorageHelper.isLocalUri(it) }) {
                _snackbarMessage.value = "Mengunggah foto galeri ke Cloud & Spreadsheet..."
            }
            dismissAddGaleriDialog()
            val result = repository.addGaleri(item)
            handleCloudSaveResult(result)
        }
    }

    fun deleteGaleri(id: String) {
        requireAdmin {
            viewModelScope.launch {
                repository.deleteGaleri(id)
                _snackbarMessage.value = "Foto Galeri Berhasil Dihapus"
            }
        }
    }

    // --- Aspirasi Warga ---
    fun openAspirasiDialog() {
        // Warga is freely allowed to submit!
        _showAspirasiDialog.value = true
    }

    fun dismissAspirasiDialog() {
        _showAspirasiDialog.value = false
    }

    fun submitAspirasi(item: AspirasiItem) {
        viewModelScope.launch {
            if (item.fotoMasalah.split(",").any { com.example.util.PhotoStorageHelper.isLocalUri(it) }) {
                _snackbarMessage.value = "Mengunggah foto masalah ke Cloud..."
            }
            dismissAspirasiDialog()
            val result = repository.submitAspirasiWarga(item)
            _snackbarMessage.value = result.message
        }
    }

    fun openRespondAspirasiDialog(item: AspirasiItem) {
        requireAuthority {
            _selectedAspirasiForResponse.value = item
            _showRespondAspirasiDialog.value = true
        }
    }

    fun dismissRespondAspirasiDialog() {
        _showRespondAspirasiDialog.value = false
        _selectedAspirasiForResponse.value = null
    }

    fun saveTanggapanAspirasi(
        id: String,
        status: String,
        tanggapan: String,
        fotoPengerjaan: String,
        namaPenanggap: String
    ) {
        if (userRole.value == UserRole.WARGA) return
        viewModelScope.launch {
            if (fotoPengerjaan.split(",").any { com.example.util.PhotoStorageHelper.isLocalUri(it) }) {
                _snackbarMessage.value = "Mengunggah foto bukti pengerjaan ke Cloud..."
            }
            dismissRespondAspirasiDialog()
            val result = repository.respondAspirasi(id, status, tanggapan, fotoPengerjaan, namaPenanggap)
            handleCloudSaveResult(result)
        }
    }

    fun deleteAspirasi(id: String) {
        requireAdmin {
            viewModelScope.launch {
                repository.deleteAspirasi(id)
                _snackbarMessage.value = "Aspirasi Berhasil Dihapus"
            }
        }
    }

    companion object {
        fun formatRupiah(amount: Long): String {
            val format = NumberFormat.getNumberInstance(Locale("in", "ID"))
            return "Rp " + format.format(amount)
        }
    }
}
