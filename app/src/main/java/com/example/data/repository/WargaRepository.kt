package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.AspirasiEntity
import com.example.data.local.KasEntity
import com.example.data.model.*
import com.example.data.source.GoogleSheetsService
import com.example.data.sync.FirestoreSyncState
import com.example.data.sync.RoomFirestoreSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class WargaRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("aplikasi_warga_prefs", Context.MODE_PRIVATE)
    private val sheetsService = GoogleSheetsService()
    private val appDatabase = AppDatabase.getDatabase(context)
    private val kasDao = appDatabase.kasDao()
    private val aspirasiDao = appDatabase.aspirasiDao()
    val syncManager = RoomFirestoreSyncManager(context, appDatabase)
    val firestoreSyncState: StateFlow<FirestoreSyncState> = syncManager.syncState

    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    private val _kasList = MutableStateFlow<List<KasItem>>(emptyList())
    val kasList: StateFlow<List<KasItem>> = _kasList.asStateFlow()

    private val _rolling12List = MutableStateFlow<List<KasMonthlyRecap>>(emptyList())
    val rolling12List: StateFlow<List<KasMonthlyRecap>> = _rolling12List.asStateFlow()

    private val _rekapBulananList = MutableStateFlow<List<KasMonthlyRecap>>(emptyList())
    val rekapBulananList: StateFlow<List<KasMonthlyRecap>> = _rekapBulananList.asStateFlow()

    private val _kasSummaryTotals = MutableStateFlow(KasSummaryTotals())
    val kasSummaryTotals: StateFlow<KasSummaryTotals> = _kasSummaryTotals.asStateFlow()

    @Volatile
    private var cachedRemoteRekapBulanan: List<KasMonthlyRecap> = emptyList()
    @Volatile
    private var cachedRemoteRolling12: List<KasMonthlyRecap> = emptyList()
    @Volatile
    private var cachedRemoteSummary: KasSummaryTotals? = null

    private val _pengurusList = MutableStateFlow<List<PengurusItem>>(emptyList())
    val pengurusList: StateFlow<List<PengurusItem>> = _pengurusList.asStateFlow()

    private val _undanganList = MutableStateFlow<List<UndanganItem>>(emptyList())
    val undanganList: StateFlow<List<UndanganItem>> = _undanganList.asStateFlow()

    private val _galeriList = MutableStateFlow<List<GaleriItem>>(emptyList())
    val galeriList: StateFlow<List<GaleriItem>> = _galeriList.asStateFlow()

    private val _aspirasiList = MutableStateFlow<List<AspirasiItem>>(emptyList())
    val aspirasiList: StateFlow<List<AspirasiItem>> = _aspirasiList.asStateFlow()

    private val _userRole = MutableStateFlow<UserRole>(UserRole.WARGA)
    val userRole: StateFlow<UserRole> = _userRole.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>("Menghubungkan ke Spreadsheet: Aplikasi_Warga...")
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _needsScriptUpdate = MutableStateFlow(false)
    val needsScriptUpdate: StateFlow<Boolean> = _needsScriptUpdate.asStateFlow()

    private val _sheetUrl = MutableStateFlow<String>(
        prefs.getString("sheet_web_app_url", "")
            ?.trim()
            ?.takeIf { it.contains("script.google.com/macros/s/") }
            ?: com.example.data.source.GoogleAppsScriptTemplate.EMBEDDED_WEB_APP_URL
    )
    val sheetUrl: StateFlow<String> = _sheetUrl.asStateFlow()

    private val activeMutationCount = java.util.concurrent.atomic.AtomicInteger(0)
    private val photoUploadSeq = java.util.concurrent.atomic.AtomicInteger(0)
    private val localUnsyncedGaleriIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val knownGaleriSheetRowIds = java.util.concurrent.ConcurrentHashMap<String, List<String>>()
    private val knownGaleriSheetPhotos = java.util.concurrent.ConcurrentHashMap<String, String>()

    companion object {
        private const val TAG = "WargaRepository"
        const val DEFAULT_PASSWORD = "005011"
        const val ADMIN_CHANGE_PASSWORD_KEY = "Amm080309"
        const val PREF_KEY_PASSWORD = "authority_password"
    }

    init {
        loadFromCache()
        if (_kasList.value.isEmpty()) {
            loadInitialDummyData()
        }
        refreshKasRecapDerivedState()
        com.example.util.ReminderScheduler.syncAlarmsAndCheckDue(context, _undanganList.value)
        observeRoomDatabase()
        repositoryScope.launch {
            if (_sheetUrl.value.isNotBlank()) {
                val firstAttempt = syncWithSheet()
                if (firstAttempt.isFailure) {
                    delay(2500)
                    syncWithSheet()
                }
            }
        }
    }

    private fun observeRoomDatabase() {
        // Observe reactive Room Flow for Kas RT
        repositoryScope.launch {
            kasDao.getAllKasFlow().collect { entities ->
                if (entities.isNotEmpty()) {
                    val items = recalculateKasBalances(entities.map { it.toKasItem() })
                    _kasList.value = items
                    refreshKasRecapDerivedState()
                    saveKasToCache(items)
                }
            }
        }

        // Observe reactive Room Flow for Aspirasi
        repositoryScope.launch {
            aspirasiDao.getAllAspirasiFlow().collect { entities ->
                if (entities.isNotEmpty()) {
                    val items = entities.map { it.toAspirasiItem() }
                        .filterNot { it.nama.contains("Mulyadi") || it.nama.contains("Siti Fatimah") || it.nama.contains("Blok B") }
                    if (items.isNotEmpty()) {
                        _aspirasiList.value = items
                        saveAspirasiToCache(items)
                    }
                }
            }
        }
    }

    fun getAuthorityPassword(): String {
        return prefs.getString(PREF_KEY_PASSWORD, DEFAULT_PASSWORD) ?: DEFAULT_PASSWORD
    }

    fun updateSheetUrlOnly(url: String) {
        val cleanUrl = url.trim().ifBlank { com.example.data.source.GoogleAppsScriptTemplate.EMBEDDED_WEB_APP_URL }
        prefs.edit().putString("sheet_web_app_url", cleanUrl).apply()
        _sheetUrl.value = cleanUrl
        sheetsService.resetCapabilityCache()
    }

    fun setSheetUrl(url: String) {
        updateSheetUrlOnly(url)
        repositoryScope.launch {
            syncWithSheet()
        }
    }

    @Volatile
    private var lastRemoteScriptVersion: Int = 1

    @Volatile
    private var lastRemoteDriveAuthorized: Boolean = false

    private fun updateSyncMessageForSuccess() {
        _syncMessage.value = if (_userRole.value != UserRole.WARGA && _needsScriptUpdate.value) {
            when {
                lastRemoteScriptVersion < 8 ->
                    "⚠️ Apps Script di URL ini masih v$lastRemoteScriptVersion — foto belum bisa tersimpan ke Spreadsheet. Ketuk untuk update ke v9."
                !lastRemoteDriveAuthorized ->
                    "⚠️ Script v$lastRemoteScriptVersion aktif, tapi izin Google Drive belum dijalankan ('otorisasiDanCekSetup'). Ketuk untuk panduan."
                else ->
                    "⚠️ Apps Script perlu diperbarui (Deploy New version) agar foto terbaca sempurna."
            }
        } else {
            "🟢 Tersinkron Realtime — Aplikasi_Warga"
        }
    }

    fun verifyAuthority(password: String): Boolean {
        val currentPass = getAuthorityPassword()
        if (password == currentPass) {
            _userRole.value = UserRole.PENGURUS
            updateSyncMessageForSuccess()
            return true
        }
        return false
    }

    fun setRole(role: UserRole) {
        _userRole.value = role
        updateSyncMessageForSuccess()
    }

    fun verifyChangePasswordAccess(masterPassword: String): Boolean {
        return masterPassword == ADMIN_CHANGE_PASSWORD_KEY
    }

    fun changePassword(masterPassword: String, newPass: String): Boolean {
        if (masterPassword == ADMIN_CHANGE_PASSWORD_KEY && newPass.isNotBlank()) {
            val cleanPass = newPass.trim()
            prefs.edit().putString(PREF_KEY_PASSWORD, cleanPass).apply()
            val url = _sheetUrl.value
            if (url.isNotBlank()) {
                repositoryScope.launch {
                    val json = JSONObject().apply {
                        put("masterKey", masterPassword)
                        put("newPassword", cleanPass)
                    }
                    sheetsService.sendAction(url, "update_auth_password", "", cleanPass, json)
                }
            }
            return true
        }
        return false
    }

    private fun pickPreservedPhoto(remotePhoto: String, localPhoto: String?): String {
        if (remotePhoto.isNotBlank()) return remotePhoto
        val local = localPhoto?.trim().orEmpty()
        if (local.isBlank()) return ""
        return if (com.example.util.PhotoStorageHelper.getDisplayModel(local) != "") local else ""
    }

    private fun pickPreservedMultiPhoto(remotePhoto: String, localPhoto: String?): String {
        val remoteParts = remotePhoto.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val localParts = localPhoto.orEmpty().split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() && com.example.util.PhotoStorageHelper.getDisplayModel(it) != "" }
        if (remoteParts.isEmpty()) {
            return localParts.joinToString(",")
        }
        if (localParts.size > remoteParts.size && remoteParts.all { it in localParts }) {
            return localParts.joinToString(",")
        }
        return remoteParts.joinToString(",")
    }

    // --- REALTIME SYNC WITH GOOGLE SHEETS ---
    suspend fun syncWithSheet(): Result<Unit> = withContext(Dispatchers.IO) {
        val url = _sheetUrl.value
        if (url.isBlank()) {
            _syncMessage.value = "🟢 Terkoneksi ke Spreadsheet: Aplikasi_Warga"
            return@withContext Result.success(Unit)
        }
        if (activeMutationCount.get() > 0) {
            return@withContext Result.success(Unit)
        }

        _isSyncing.value = true
        try {
            val response = sheetsService.fetchAllData(url)
            if (activeMutationCount.get() > 0) {
                return@withContext Result.success(Unit)
            }

            val existingKasById = _kasList.value.associateBy { it.id }
            val existingPgrById = _pengurusList.value.associateBy { it.id }
            val existingUndById = _undanganList.value.associateBy { it.id }
            val existingGalById = _galeriList.value.associateBy { it.id }
            val existingAspById = _aspirasiList.value.associateBy { it.id }

            if (!response.rekapBulanan.isNullOrEmpty()) {
                cachedRemoteRekapBulanan = com.example.util.KasRecapHelper.normalizeRecapList(response.rekapBulanan)
            }
            if (!response.rolling12.isNullOrEmpty()) {
                cachedRemoteRolling12 = com.example.util.KasRecapHelper.normalizeRecapList(response.rolling12)
            }
            if (response.kasSummary != null) {
                cachedRemoteSummary = response.kasSummary
            }

            response.kas?.let { rawKas ->
                val cleanKas = rawKas.filter { it.keterangan.isNotBlank() || it.masuk > 0L || it.keluar > 0L }.map { item ->
                    val withDesc = if (item.keterangan.isBlank()) {
                        if (item.keluar > 0L) item.copy(keterangan = "Pengeluaran Operasional / Kas RT")
                        else item.copy(keterangan = "Iuran / Pemasukan Kas RT")
                    } else item
                    withDesc.copy(
                        periodeBulan = withDesc.periodeBulan.ifBlank {
                            com.example.util.KasRecapHelper.resolvePeriodeKey("", withDesc.tanggal)
                        },
                        buktiFoto = pickPreservedPhoto(withDesc.buktiFoto, existingKasById[withDesc.id]?.buktiFoto)
                    )
                }
                val recalculated = recalculateKasBalances(cleanKas)
                _kasList.value = recalculated
                saveKasToCache(recalculated)
                try {
                    kasDao.clearAllKas()
                    if (recalculated.isNotEmpty()) {
                        kasDao.insertKasList(recalculated.map { KasEntity.fromKasItem(it) })
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating kas Room DB: ${e.message}")
                }
            }
            refreshKasRecapDerivedState(
                remoteRekap = cachedRemoteRekapBulanan.ifEmpty { response.rekapBulanan },
                remoteRolling12 = cachedRemoteRolling12.ifEmpty { response.rolling12 },
                remoteSummary = response.kasSummary ?: cachedRemoteSummary
            )
            response.pengurus?.let { rawPgr ->
                val cleanPgr = rawPgr.filter { it.nama.isNotBlank() || it.jabatan.isNotBlank() }.map { item ->
                    item.copy(foto = pickPreservedPhoto(item.foto, existingPgrById[item.id]?.foto))
                }
                _pengurusList.value = cleanPgr
            }
            response.undangan?.let { rawUnd ->
                val cleanUnd = rawUnd.filter { it.judul.isNotBlank() }.map { item ->
                    item.copy(foto = pickPreservedPhoto(item.foto, existingUndById[item.id]?.foto))
                }
                _undanganList.value = cleanUnd
            }
            response.galeri?.let { rawGal ->
                val validRows = rawGal.filter { it.judul.isNotBlank() }
                val rowGroupsByBaseId = validRows.groupBy { com.example.util.GaleriMultiPhotoHelper.baseGaleriId(it.id) }
                rowGroupsByBaseId.forEach { (baseId, group) ->
                    knownGaleriSheetRowIds[baseId] = group.map { it.id }
                }

                val preservedRows = validRows.map { item ->
                    val baseId = com.example.util.GaleriMultiPhotoHelper.baseGaleriId(item.id)
                    val partIdx = com.example.util.GaleriMultiPhotoHelper.partIndex(item.id)
                    val existingItem = existingGalById[item.id] ?: existingGalById[baseId]
                    val fallbackFromExisting = existingItem?.foto
                        ?.split(",")
                        ?.map { it.trim() }
                        ?.filter { it.isNotBlank() }
                        ?.let { parts ->
                            if (item.id == baseId && (rowGroupsByBaseId[baseId]?.size ?: 1) == 1) {
                                parts.joinToString(",")
                            } else {
                                parts.getOrNull(partIdx - 1) ?: ""
                            }
                        }
                    val fallbackPhoto = knownGaleriSheetPhotos[item.id] ?: fallbackFromExisting
                    val resolvedPhoto = pickPreservedPhoto(item.foto, fallbackPhoto)
                    if (resolvedPhoto.isNotBlank()) {
                        knownGaleriSheetPhotos[item.id] = resolvedPhoto
                    }
                    item.copy(foto = resolvedPhoto)
                }

                val mergedGal = com.example.util.GaleriMultiPhotoHelper.mergeGroupedGaleriRows(preservedRows)
                val remoteBaseIds = mergedGal.map { it.id }.toSet()
                localUnsyncedGaleriIds.removeAll(remoteBaseIds)
                val pendingLocalGal = _galeriList.value.filter { it.id in localUnsyncedGaleriIds && it.id !in remoteBaseIds }
                _galeriList.value = pendingLocalGal + mergedGal
            }
            response.aspirasi?.let { rawAsp ->
                val cleanAsp = rawAsp.filter { it.nama.isNotBlank() || it.pesan.isNotBlank() }.map { item ->
                    val existing = existingAspById[item.id]
                    item.copy(
                        fotoMasalah = pickPreservedMultiPhoto(item.fotoMasalah, existing?.fotoMasalah),
                        fotoPengerjaan = pickPreservedMultiPhoto(item.fotoPengerjaan, existing?.fotoPengerjaan)
                    )
                }
                _aspirasiList.value = cleanAsp
                saveAspirasiToCache(cleanAsp)
                try {
                    aspirasiDao.clearAllAspirasi()
                    if (cleanAsp.isNotEmpty()) {
                        aspirasiDao.insertAspirasiList(cleanAsp.map { AspirasiEntity.fromAspirasiItem(it) })
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating aspirasi Room DB: ${e.message}")
                }
            }

            saveToCache()
            com.example.util.RemoteNotificationTracker.processRemoteUpdates(
                context = context,
                undanganList = if (response.undangan != null) _undanganList.value else emptyList(),
                aspirasiList = if (response.aspirasi != null) _aspirasiList.value else emptyList()
            )
            lastRemoteScriptVersion = response.scriptVersion
            lastRemoteDriveAuthorized = response.driveAuthorized
            _needsScriptUpdate.value = response.hasUnreadableImageFormula ||
                response.scriptVersion < 8 ||
                !response.driveAuthorized
            updateSyncMessageForSuccess()
            Result.success(Unit)
        } catch (e: Exception) {
            val rawErr = e.localizedMessage ?: "Cek koneksi internet/URL"
            _syncMessage.value = when {
                _userRole.value == UserRole.WARGA -> "⚠️ Menunggu koneksi internet untuk sinkronisasi otomatis"
                rawErr.contains("ReferenceError", ignoreCase = true) ->
                    "⚠️ Gagal Sync ($rawErr). Kode Apps Script terpotong/belum lengkap. Ketuk di sini -> Salin Script v9, hapus SEMUA kode lama di Apps Script, tempel ulang, Simpan, jalankan 'otorisasiDanCekSetup', lalu Deploy New version."
                else -> "⚠️ Gagal Sync: $rawErr"
            }
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }

    // --- KAS RT ACTIONS ---
    suspend fun addKas(item: KasItem): CloudSaveResult = withContext(Dispatchers.IO) {
        activeMutationCount.incrementAndGet()
        try {
            val isEdit = item.id.isNotBlank()
            val itemId = if (isEdit) item.id else "KAS-${System.currentTimeMillis()}"
            val initialPhoto = if (item.buktiFoto.isNotBlank()) {
                com.example.util.PhotoStorageHelper.ensurePersistentUri(context, item.buktiFoto)
            } else ""

            val resolvedPeriode = item.periodeBulan.trim().ifBlank {
                com.example.util.KasRecapHelper.resolvePeriodeKey("", item.tanggal)
            }
            val optimisticItem = item.copy(
                id = itemId,
                periodeBulan = resolvedPeriode,
                buktiFoto = initialPhoto
            )
            val baseList = _kasList.value.toMutableList()
            if (isEdit) {
                val idx = baseList.indexOfFirst { it.id == itemId }
                if (idx != -1) baseList[idx] = optimisticItem else baseList.add(optimisticItem)
            } else {
                baseList.add(optimisticItem)
            }
            _kasList.value = recalculateKasBalances(baseList)
            refreshKasRecapDerivedState()
            saveToCache()

            val photoRequested = item.buktiFoto.isNotBlank()
            val cloudBuktiFoto = if (photoRequested) {
                processPhotoForCloudSharing(initialPhoto, "kas_bukti")
            } else ""
            val photoUploaded = !photoRequested || com.example.util.PhotoStorageHelper.isPublicHttpUrl(cloudBuktiFoto)

            val updatedList = _kasList.value.toMutableList()
            val targetIdx = updatedList.indexOfFirst { it.id == itemId }
            val currentItem = if (targetIdx != -1) updatedList[targetIdx] else optimisticItem
            val finalItem = currentItem.copy(buktiFoto = cloudBuktiFoto)
            if (targetIdx != -1) updatedList[targetIdx] = finalItem
            val recalculated = recalculateKasBalances(updatedList)
            _kasList.value = recalculated
            refreshKasRecapDerivedState()
            saveToCache()
            val savedItem = recalculated.firstOrNull { it.id == itemId } ?: finalItem

            syncManager.saveKas(savedItem)

            if (!isEdit) {
                val nominal = if (savedItem.masuk > 0L) savedItem.masuk else savedItem.keluar
                val jenis = if (savedItem.masuk > 0L) "Pemasukan (+)" else "Pengeluaran (-)"
                com.example.util.NotificationHelper.showPushNotification(
                    context = context,
                    title = "💰 Transaksi Kas RT $jenis",
                    message = "${savedItem.keterangan}: Rp $nominal",
                    channelId = com.example.util.NotificationHelper.CHANNEL_KAS,
                    type = "KAS"
                )
            }

            val sheetOk = if (_sheetUrl.value.isNotBlank()) {
                val fotoFormula = toSheetImageFormula(savedItem.buktiFoto)
                val json = JSONObject().apply {
                    put("id", savedItem.id)
                    put("tanggal", savedItem.tanggal)
                    put("periode_bulan", savedItem.periodeBulan)
                    put("periodeBulan", savedItem.periodeBulan)
                    put("kategori", savedItem.kategori)
                    put("keterangan", savedItem.keterangan)
                    put("masuk", savedItem.masuk)
                    put("keluar", savedItem.keluar)
                    put("saldo", savedItem.effectiveSaldo)
                    put("jumlah", savedItem.effectiveSaldo)
                    put("bukti_foto", fotoFormula)
                    put("buktiFoto", fotoFormula)
                }
                sheetsService.sendAction(
                    _sheetUrl.value,
                    if (isEdit) "edit" else "add",
                    "Kas_RT",
                    getAuthorityPassword(),
                    json,
                    if (isEdit) savedItem.id else null
                )
            } else false

            CloudSaveResult(
                localSaved = true,
                photoRequested = photoRequested,
                photoUploadedToCloud = photoUploaded,
                sheetSynced = sheetOk,
                message = buildSaveStatusMessage(
                    entityName = "Transaksi Kas",
                    photoRequested = photoRequested,
                    photoUploaded = photoUploaded,
                    sheetSynced = sheetOk
                )
            )
        } finally {
            activeMutationCount.decrementAndGet()
        }
    }

    suspend fun deleteKas(id: String): Boolean = withContext(Dispatchers.IO) {
        val updated = _kasList.value.filter { it.id != id }
        _kasList.value = recalculateKasBalances(updated)
        refreshKasRecapDerivedState()
        saveToCache()

        // Sync deletion to Room Local DB and Cloud Firestore
        syncManager.deleteKas(id)

        if (_sheetUrl.value.isNotBlank()) {
            sheetsService.sendAction(_sheetUrl.value, "delete", "Kas_RT", getAuthorityPassword(), JSONObject(), id)
        }
        true
    }

    private fun recalculateKasBalances(items: List<KasItem>): List<KasItem> {
        return com.example.util.KasRecapHelper.normalizeAndRecalculateKas(items)
    }

    private fun refreshKasRecapDerivedState(
        remoteRekap: List<KasMonthlyRecap>? = cachedRemoteRekapBulanan,
        remoteRolling12: List<KasMonthlyRecap>? = cachedRemoteRolling12,
        remoteSummary: KasSummaryTotals? = cachedRemoteSummary
    ) {
        val currentKas = _kasList.value
        val localRekap = com.example.util.KasRecapHelper.buildFullMonthlyRecap(currentKas)
        val mergedRekap = if (localRekap.isNotEmpty()) {
            val map = linkedMapOf<String, KasMonthlyRecap>()
            com.example.util.KasRecapHelper.normalizeRecapList(remoteRekap.orEmpty()).forEach { map[it.periodeBulan] = it }
            localRekap.forEach { map[it.periodeBulan] = it }
            com.example.util.KasRecapHelper.recomputeRunningSaldos(map.values.toList())
        } else {
            com.example.util.KasRecapHelper.normalizeRecapList(remoteRekap.orEmpty())
        }

        _rekapBulananList.value = mergedRekap
        _rolling12List.value = com.example.util.KasRecapHelper.buildRolling12(
            kasList = currentKas,
            remoteRekap = mergedRekap,
            remoteRolling12 = remoteRolling12
        )
        _kasSummaryTotals.value = com.example.util.KasRecapHelper.computeUnifiedSummary(
            kasList = currentKas,
            rekapBulanan = mergedRekap,
            remoteSummary = remoteSummary
        )
    }

    // --- PENGURUS ACTIONS ---
    suspend fun addOrUpdatePengurus(item: PengurusItem, isEdit: Boolean): CloudSaveResult = withContext(Dispatchers.IO) {
        activeMutationCount.incrementAndGet()
        try {
            val itemId = if (item.id.isBlank()) "PGR-${System.currentTimeMillis()}" else item.id
            val initialFoto = if (item.foto.isNotBlank()) {
                com.example.util.PhotoStorageHelper.ensurePersistentUri(context, item.foto)
            } else ""
            val optimisticItem = item.copy(id = itemId, foto = initialFoto)

            val baseList = _pengurusList.value.toMutableList()
            if (isEdit) {
                val index = baseList.indexOfFirst { it.id == itemId }
                if (index != -1) baseList[index] = optimisticItem else baseList.add(optimisticItem)
            } else {
                baseList.add(optimisticItem)
            }
            _pengurusList.value = baseList
            saveToCache()

            val photoRequested = item.foto.isNotBlank()
            val processedFoto = if (photoRequested) {
                processPhotoForCloudSharing(initialFoto, "pengurus")
            } else ""
            val photoUploaded = !photoRequested || com.example.util.PhotoStorageHelper.isPublicHttpUrl(processedFoto)

            val fullItem = optimisticItem.copy(foto = processedFoto)
            val updatedList = _pengurusList.value.toMutableList()
            val idx = updatedList.indexOfFirst { it.id == itemId }
            if (idx != -1) updatedList[idx] = fullItem
            _pengurusList.value = updatedList
            saveToCache()

            val sheetOk = if (_sheetUrl.value.isNotBlank()) {
                val json = JSONObject().apply {
                    put("id", fullItem.id)
                    put("jabatan", fullItem.jabatan)
                    put("nama", fullItem.nama)
                    put("noHp", fullItem.noHp)
                    put("alamat", fullItem.alamat)
                    put("masaBakti", fullItem.masaBakti)
                    put("foto", toSheetImageFormula(fullItem.foto))
                }
                val action = if (isEdit) "edit" else "add"
                sheetsService.sendAction(_sheetUrl.value, action, "Pengurus", getAuthorityPassword(), json, fullItem.id)
            } else false

            CloudSaveResult(
                localSaved = true,
                photoRequested = photoRequested,
                photoUploadedToCloud = photoUploaded,
                sheetSynced = sheetOk,
                message = buildSaveStatusMessage(
                    entityName = "Data Pengurus RT",
                    photoRequested = photoRequested,
                    photoUploaded = photoUploaded,
                    sheetSynced = sheetOk
                )
            )
        } finally {
            activeMutationCount.decrementAndGet()
        }
    }

    suspend fun deletePengurus(id: String): Boolean = withContext(Dispatchers.IO) {
        _pengurusList.value = _pengurusList.value.filter { it.id != id }
        saveToCache()
        if (_sheetUrl.value.isNotBlank()) {
            sheetsService.sendAction(_sheetUrl.value, "delete", "Pengurus", getAuthorityPassword(), JSONObject(), id)
        }
        true
    }

    // --- UNDANGAN ACTIONS ---
    suspend fun addOrUpdateUndangan(item: UndanganItem, isEdit: Boolean): CloudSaveResult = withContext(Dispatchers.IO) {
        activeMutationCount.incrementAndGet()
        try {
            val rawBase = if (item.id.isBlank()) item.copy(id = "UND-${System.currentTimeMillis()}") else item
            val initialPhoto = if (rawBase.foto.isNotBlank()) {
                com.example.util.PhotoStorageHelper.ensurePersistentUri(context, rawBase.foto)
            } else ""
            val baseItem = rawBase.copy(foto = initialPhoto)
            val existing = _undanganList.value.firstOrNull { it.id == baseItem.id }
            // Pertahankan pengingat aktif jika sedang mengedit acara dan item input belum membawa metadata pengingat
            val optimisticItem = if (isEdit && existing != null && existing.isReminderActive && !baseItem.isReminderActive && !baseItem.isPengumuman) {
                val mergedKet = com.example.util.ReminderCodec.encodeIntoKeterangan(
                    baseItem.cleanKeterangan,
                    existing.effectiveReminderMenit,
                    existing.effectiveReminderTargetMillis,
                    existing.effectiveReminderActivatedMillis
                )
                baseItem.copy(
                    keterangan = mergedKet,
                    pengingatMenit = existing.effectiveReminderMenit,
                    pengingatAktif = true,
                    pengingatTargetMillis = existing.effectiveReminderTargetMillis,
                    pengingatActivatedMillis = existing.effectiveReminderActivatedMillis
                )
            } else {
                baseItem
            }

            val updated = _undanganList.value.toMutableList()
            if (isEdit) {
                val index = updated.indexOfFirst { it.id == optimisticItem.id }
                if (index != -1) updated[index] = optimisticItem else updated.add(optimisticItem)
            } else {
                updated.add(0, optimisticItem) // Most recent first
            }
            _undanganList.value = updated
            saveToCache()
            com.example.util.ReminderScheduler.syncAlarmsAndCheckDue(context, updated)

            val photoRequested = item.foto.isNotBlank()
            val cloudPhoto = if (photoRequested) {
                processPhotoForCloudSharing(initialPhoto, "undangan_foto")
            } else ""
            val photoUploaded = !photoRequested || com.example.util.PhotoStorageHelper.isPublicHttpUrl(cloudPhoto)
            val resolvedItem = optimisticItem.copy(foto = cloudPhoto)

            if (resolvedItem.foto != optimisticItem.foto) {
                val syncedList = _undanganList.value.toMutableList()
                val idx = syncedList.indexOfFirst { it.id == resolvedItem.id }
                if (idx != -1) syncedList[idx] = resolvedItem
                _undanganList.value = syncedList
                saveToCache()
            }
            com.example.util.RemoteNotificationTracker.markLocalUndanganHandled(context, resolvedItem)

            val sheetOk = if (_sheetUrl.value.isNotBlank()) {
                val json = buildUndanganJson(resolvedItem, forSheet = true)
                val action = if (isEdit) "edit" else "add"
                sheetsService.sendAction(_sheetUrl.value, action, "Undangan", getAuthorityPassword(), json, resolvedItem.id)
            } else false

            val entityLabel = if (resolvedItem.isPengumuman) "Pengumuman" else "Undangan"
            CloudSaveResult(
                localSaved = true,
                photoRequested = photoRequested,
                photoUploadedToCloud = photoUploaded,
                sheetSynced = sheetOk,
                message = buildSaveStatusMessage(
                    entityName = entityLabel,
                    photoRequested = photoRequested,
                    photoUploaded = photoUploaded,
                    sheetSynced = sheetOk
                )
            )
        } finally {
            activeMutationCount.decrementAndGet()
        }
    }

    suspend fun deleteUndangan(id: String): Boolean = withContext(Dispatchers.IO) {
        activeMutationCount.incrementAndGet()
        try {
            com.example.util.ReminderScheduler.cancelReminderAlarm(context, id)
            _undanganList.value = _undanganList.value.filter { it.id != id }
            saveToCache()
            if (_sheetUrl.value.isNotBlank()) {
                sheetsService.sendAction(_sheetUrl.value, "delete", "Undangan", getAuthorityPassword(), JSONObject(), id)
            }
            true
        } finally {
            activeMutationCount.decrementAndGet()
        }
    }

    suspend fun activateEventReminder(
        undangan: UndanganItem,
        minutes: Int,
        mode: ReminderMode
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        activeMutationCount.incrementAndGet()
        try {
            if (undangan.isPengumuman) {
                return@withContext Pair(false, "Pengingat hanya untuk acara terjadwal (Rapat / Gotong Royong).")
            }
            val nowMillis = System.currentTimeMillis()
            val eventStartMillis = com.example.util.ReminderCalculator.parseEventStartMillis(undangan.tanggal, undangan.waktu)
            val targetMillis = com.example.util.ReminderCalculator.calculateTargetMillis(
                mode = mode,
                minutes = minutes,
                eventStartMillis = eventStartMillis,
                nowMillis = nowMillis
            ) ?: return@withContext Pair(false, "Format tanggal/waktu acara belum mendukung mode sebelum acara. Gunakan mode 'Dalam X Menit'.")

            if (mode == ReminderMode.BEFORE_EVENT && targetMillis <= nowMillis) {
                return@withContext Pair(false, "Waktu pengingat (${minutes} menit sebelum acara) sudah terlewat. Pilih menit lebih kecil atau mode 'Dalam X Menit'.")
            }

            val encodedKet = com.example.util.ReminderCodec.encodeIntoKeterangan(
                rawKeterangan = undangan.cleanKeterangan,
                minutes = minutes,
                targetMillis = targetMillis,
                activatedMillis = nowMillis
            )
            val updatedItem = undangan.copy(
                keterangan = encodedKet,
                pengingatMenit = minutes,
                pengingatAktif = true,
                pengingatTargetMillis = targetMillis,
                pengingatActivatedMillis = nowMillis
            )

            val list = _undanganList.value.toMutableList()
            val idx = list.indexOfFirst { it.id == updatedItem.id }
            if (idx != -1) list[idx] = updatedItem else list.add(0, updatedItem)
            _undanganList.value = list
            saveToCache()

            com.example.util.RemoteNotificationTracker.markLocalUndanganHandled(context, updatedItem)
            com.example.util.ReminderScheduler.syncAlarmsAndCheckDue(context, list)

            val sheetOk = if (_sheetUrl.value.isNotBlank()) {
                sheetsService.sendAction(
                    _sheetUrl.value,
                    "edit",
                    "Undangan",
                    getAuthorityPassword(),
                    buildUndanganJson(updatedItem, forSheet = true),
                    updatedItem.id
                )
            } else false

            val clockStr = com.example.util.ReminderCalculator.formatClockTime(targetMillis)
            val msg = if (sheetOk) {
                "Pengingat '${updatedItem.cleanJudul}' diaktifkan (${minutes} menit, aktif pada $clockStr) & dikirim ke HP warga!"
            } else {
                "Pengingat '${updatedItem.cleanJudul}' tersimpan di HP ini (${minutes} menit), namun gagal sinkron ke Spreadsheet."
            }
            Pair(true, msg)
        } finally {
            activeMutationCount.decrementAndGet()
        }
    }

    suspend fun cancelEventReminder(undangan: UndanganItem): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        activeMutationCount.incrementAndGet()
        try {
            com.example.util.ReminderScheduler.cancelReminderAlarm(context, undangan.id)
            val updatedItem = undangan.copy(
                keterangan = undangan.cleanKeterangan,
                pengingatMenit = 0,
                pengingatAktif = false,
                pengingatTargetMillis = 0L,
                pengingatActivatedMillis = 0L
            )

            val list = _undanganList.value.toMutableList()
            val idx = list.indexOfFirst { it.id == updatedItem.id }
            if (idx != -1) list[idx] = updatedItem
            _undanganList.value = list
            saveToCache()

            val sheetOk = if (_sheetUrl.value.isNotBlank()) {
                sheetsService.sendAction(
                    _sheetUrl.value,
                    "edit",
                    "Undangan",
                    getAuthorityPassword(),
                    buildUndanganJson(updatedItem, forSheet = true),
                    updatedItem.id
                )
            } else false

            val msg = if (sheetOk) {
                "Pengingat '${updatedItem.cleanJudul}' dinonaktifkan untuk seluruh warga."
            } else {
                "Pengingat '${updatedItem.cleanJudul}' dinonaktifkan secara lokal."
            }
            Pair(true, msg)
        } finally {
            activeMutationCount.decrementAndGet()
        }
    }

    private fun buildUndanganJson(item: UndanganItem, forSheet: Boolean = false): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("judul", item.judul)
        put("tanggal", item.tanggal)
        put("waktu", item.waktu)
        put("lokasi", item.lokasi)
        put("keterangan", item.keterangan)
        put("tipe", item.tipe)
        put("pengingatMenit", item.effectiveReminderMenit)
        put("pengingatAktif", item.isReminderActive)
        put("pengingatTargetMillis", item.effectiveReminderTargetMillis)
        put("pengingatActivatedMillis", item.effectiveReminderActivatedMillis)
        put("foto", if (forSheet) toSheetImageFormula(item.foto) else item.foto)
    }

    private fun toSheetImageFormula(urlOrUri: String): String {
        return com.example.util.PhotoStorageHelper.formatAsImageFormula(urlOrUri)
    }

    private fun buildSaveStatusMessage(
        entityName: String,
        photoRequested: Boolean,
        photoUploaded: Boolean,
        sheetSynced: Boolean
    ): String {
        val isWarga = _userRole.value == UserRole.WARGA
        val driveErr = sheetsService.lastDriveUploadError?.takeIf { it.isNotBlank() }
        val errSuffix = if (driveErr != null && !isWarga) " ($driveErr)" else ""
        return when {
            photoRequested && photoUploaded && sheetSynced ->
                "$entityName & foto berhasil diunggah ke Cloud dan dicatat ke Spreadsheet (=IMAGE)."
            !photoRequested && sheetSynced ->
                "$entityName berhasil disimpan & dicatat ke Spreadsheet!"
            photoRequested && !photoUploaded && sheetSynced ->
                "$entityName masuk ke Spreadsheet, namun foto gagal diunggah ke Google Drive/Cloud$errSuffix."
            photoRequested && photoUploaded && !sheetSynced ->
                if (isWarga) {
                    "Foto berhasil diunggah, namun $entityName belum terkirim ke server RT. Periksa koneksi internet Anda."
                } else {
                    "Foto berhasil diunggah ke Cloud, namun $entityName gagal dicatat ke Spreadsheet. Periksa koneksi / URL Apps Script."
                }
            else ->
                if (isWarga) {
                    "$entityName tersimpan di HP, akan dikirim ulang saat koneksi internet tersedia."
                } else {
                    "$entityName tersimpan di HP, namun gagal dikirim ke Spreadsheet. Periksa koneksi internet / URL Apps Script."
                }
        }
    }

    /**
     * Mengunggah foto lokal HP (file:// atau content://) ke Cloud Storage / Google Drive
     * terlebih dahulu sebelum disimpan ke Google Sheets agar mendapatkan URL publik permanen.
     */
    private suspend fun processPhotoForCloudSharing(rawUri: String, filenamePrefix: String): String {
        val clean = com.example.util.PhotoStorageHelper.extractUrlFromFormula(rawUri)
        if (clean.isBlank()) return ""
        if (clean.startsWith("http://") || clean.startsWith("https://")) {
            return clean
        }

        // Simpan salinan lokal permanen terlebih dahulu agar stream foto aman dibaca
        val persistentUri = com.example.util.PhotoStorageHelper.ensurePersistentUri(context, clean)
        val seq = photoUploadSeq.incrementAndGet()
        val filename = "${filenamePrefix}_${System.currentTimeMillis()}_$seq.jpg"

        // 1. Prioritas Utama: Unggah ke Google Drive melalui Google Apps Script Web App
        val base64Data = com.example.util.PhotoStorageHelper.encodeUriToBase64(context, persistentUri, maxDimension = 800, quality = 65)
        if (_sheetUrl.value.isNotBlank() && base64Data.isNotBlank()) {
            val driveUrl = sheetsService.uploadPhotoToDrive(
                _sheetUrl.value,
                getAuthorityPassword(),
                base64Data,
                filename
            )
            if (!driveUrl.isNullOrBlank()) {
                return driveUrl
            }
        }

        // 2. Prioritas Kedua: Unggah langsung ke Cloud Storage publik (FreeImage CDN + fallback x0.at)
        val imageBytes = com.example.util.PhotoStorageHelper.compressAndGetImageBytes(context, persistentUri, maxDimension = 800, quality = 65)
        if (imageBytes != null && imageBytes.isNotEmpty()) {
            val publicCloudUrl = sheetsService.uploadPhotoToPublicCloud(imageBytes, filename)
            if (!publicCloudUrl.isNullOrBlank()) {
                return publicCloudUrl
            }
        }

        // Jika offline/gagal unggah, simpan lokal di perangkat (tidak dikirim ke Google Sheets)
        return persistentUri
    }

    // --- GALERI ACTIONS ---
    suspend fun addGaleri(item: GaleriItem): CloudSaveResult = withContext(Dispatchers.IO) {
        activeMutationCount.incrementAndGet()
        try {
            val isEdit = item.id.isNotBlank()
            val rawId = if (!isEdit) "GAL-${System.currentTimeMillis()}" else item.id
            val itemId = com.example.util.GaleriMultiPhotoHelper.baseGaleriId(rawId)
            val cleanJudul = com.example.util.GaleriMultiPhotoHelper.stripPhotoSuffix(item.judul)
            localUnsyncedGaleriIds.add(itemId)

            val rawPhotos = item.foto.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct().take(10)
            val persistentPhotos = rawPhotos.map {
                com.example.util.PhotoStorageHelper.ensurePersistentUri(context, it)
            }.filter { it.isNotBlank() }

            // Optimistic local update so user immediately sees their new/updated card & photo preview
            val optimisticItem = item.copy(
                id = itemId,
                judul = cleanJudul,
                foto = persistentPhotos.joinToString(",")
            )
            val baseList = _galeriList.value.toMutableList()
            val existingIdx = baseList.indexOfFirst { it.id == itemId }
            if (isEdit && existingIdx != -1) {
                baseList[existingIdx] = optimisticItem
            } else {
                baseList.removeAll { it.id == itemId }
                baseList.add(0, optimisticItem)
            }
            _galeriList.value = baseList
            saveToCache()

            // Upload all new local photos sequentially to Cloud (existing https URLs are preserved directly)
            val processedPhotos = mutableListOf<String>()
            persistentPhotos.forEachIndexed { index, uri ->
                if (index > 0 && com.example.util.PhotoStorageHelper.isLocalUri(uri)) delay(150)
                val uploaded = processPhotoForCloudSharing(uri, "galeri")
                if (uploaded.isNotBlank()) processedPhotos.add(uploaded)
            }
            val finalFotoString = processedPhotos.joinToString(",")
            val photoRequested = persistentPhotos.isNotEmpty()
            val allPhotosUploaded = !photoRequested || (
                processedPhotos.size == persistentPhotos.size &&
                    processedPhotos.all { com.example.util.PhotoStorageHelper.isPublicHttpUrl(it) }
                )

            val fullItem = optimisticItem.copy(foto = finalFotoString)
            val updatedList = _galeriList.value.toMutableList()
            val idx = updatedList.indexOfFirst { it.id == itemId }
            if (idx != -1) updatedList[idx] = fullItem else updatedList.add(0, fullItem)
            _galeriList.value = updatedList
            saveToCache()

            val sheetAction = if (isEdit) "edit" else "add"
            val previousRowIds = if (isEdit) knownGaleriSheetRowIds[itemId].orEmpty() else emptyList()

            val sheetOk = if (_sheetUrl.value.isNotBlank()) {
                val url = _sheetUrl.value
                val sheetFormulas = processedPhotos.map { toSheetImageFormula(it) }.filter { it.isNotBlank() }
                val use10ColumnRow = sheetsService.supportsGaleri10Columns(url)
                val useMultiColumnRow = sheetsService.supportsMultiPhotoColumns(url)

                if (use10ColumnRow || (useMultiColumnRow && sheetFormulas.size <= 5) || sheetFormulas.size <= 1) {
                    val json = com.example.util.GaleriMultiPhotoHelper.buildCombinedRowPayload(fullItem, sheetFormulas)
                    val sent = sheetsService.sendAction(
                        url,
                        sheetAction,
                        "Galeri",
                        getAuthorityPassword(),
                        json,
                        if (isEdit) itemId else null
                    )
                    if (sent) {
                        knownGaleriSheetRowIds[itemId] = listOf(itemId)
                        knownGaleriSheetPhotos[itemId] = processedPhotos.joinToString(",")
                        if (allPhotosUploaded) {
                            localUnsyncedGaleriIds.remove(itemId)
                        }
                        if (isEdit) {
                            previousRowIds.filter { it != itemId }.forEach { obsoleteId ->
                                sheetsService.sendAction(url, "delete", "Galeri", getAuthorityPassword(), JSONObject(), obsoleteId)
                            }
                        }
                    }
                    sent
                } else if (useMultiColumnRow) {
                    // Script v2 mendukung 5 kolom Foto per baris: pecah menjadi 2 baris (1-5 dan 6-10)
                    val chunkedRows = com.example.util.GaleriMultiPhotoHelper.chunkPhotosForFiveColumnSheet(fullItem, sheetFormulas)
                    var sentCount = 0
                    val savedRowIds = mutableListOf<String>()
                    chunkedRows.forEachIndexed { rIdx, (rowId, json) ->
                        if (rIdx > 0) delay(150)
                        if (sheetsService.sendAction(url, sheetAction, "Galeri", getAuthorityPassword(), json, if (isEdit) rowId else null)) {
                            sentCount++
                            savedRowIds.add(rowId)
                        }
                    }
                    if (savedRowIds.isNotEmpty()) {
                        knownGaleriSheetRowIds[itemId] = savedRowIds
                        if (isEdit) {
                            previousRowIds.filter { it !in savedRowIds }.forEach { obsoleteId ->
                                sheetsService.sendAction(url, "delete", "Galeri", getAuthorityPassword(), JSONObject(), obsoleteId)
                            }
                        }
                    }
                    val allSent = sentCount == chunkedRows.size && sentCount > 0
                    if (allSent) {
                        knownGaleriSheetPhotos[itemId] = processedPhotos.joinToString(",")
                        if (allPhotosUploaded) {
                            localUnsyncedGaleriIds.remove(itemId)
                        }
                    }
                    allSent
                } else {
                    // Apps Script v1 hanya membaca kolom "Foto": pecah rapi ke baris berurutan agar SEMUA foto tersimpan!
                    val expandedRows = com.example.util.GaleriMultiPhotoHelper.expandRowsForSingleColumnSheet(fullItem, sheetFormulas)
                    var sentCount = 0
                    val savedRowIds = mutableListOf<String>()
                    expandedRows.forEachIndexed { rIdx, rowPayload ->
                        if (rIdx > 0) delay(150)
                        if (sheetsService.sendAction(url, sheetAction, "Galeri", getAuthorityPassword(), rowPayload.json, if (isEdit) rowPayload.rowId else null)) {
                            sentCount++
                            savedRowIds.add(rowPayload.rowId)
                            knownGaleriSheetPhotos[rowPayload.rowId] =
                                com.example.util.PhotoStorageHelper.extractUrlFromFormula(rowPayload.formula)
                        }
                    }
                    if (savedRowIds.isNotEmpty()) {
                        knownGaleriSheetRowIds[itemId] = savedRowIds
                        if (isEdit) {
                            previousRowIds.filter { it !in savedRowIds }.forEach { obsoleteId ->
                                sheetsService.sendAction(url, "delete", "Galeri", getAuthorityPassword(), JSONObject(), obsoleteId)
                            }
                        }
                    }
                    val allSent = sentCount == expandedRows.size && sentCount > 0
                    if (allSent && allPhotosUploaded) {
                        localUnsyncedGaleriIds.remove(itemId)
                    }
                    allSent
                }
            } else false

            CloudSaveResult(
                localSaved = true,
                photoRequested = photoRequested,
                photoUploadedToCloud = allPhotosUploaded,
                sheetSynced = sheetOk,
                message = buildSaveStatusMessage(
                    entityName = if (isEdit) "Perubahan Galeri (${processedPhotos.size} foto)" else "Dokumentasi Galeri (${processedPhotos.size} foto)",
                    photoRequested = photoRequested,
                    photoUploaded = allPhotosUploaded,
                    sheetSynced = sheetOk
                )
            )
        } finally {
            activeMutationCount.decrementAndGet()
        }
    }

    suspend fun deleteGaleri(id: String): Boolean = withContext(Dispatchers.IO) {
        val baseId = com.example.util.GaleriMultiPhotoHelper.baseGaleriId(id)
        val existingItem = _galeriList.value.firstOrNull { it.id == baseId || it.id == id }
        val photoCount = existingItem?.foto?.split(",")?.count { it.isNotBlank() }?.coerceIn(1, 10) ?: 10

        localUnsyncedGaleriIds.remove(baseId)
        localUnsyncedGaleriIds.remove(id)
        _galeriList.value = _galeriList.value.filter {
            it.id != baseId && it.id != id && com.example.util.GaleriMultiPhotoHelper.baseGaleriId(it.id) != baseId
        }
        saveToCache()

        if (_sheetUrl.value.isNotBlank()) {
            val recordedRowIds = knownGaleriSheetRowIds.remove(baseId).orEmpty()
            val candidateIds = com.example.util.GaleriMultiPhotoHelper.candidateRowIdsForDeletion(baseId, photoCount)
            val rowIdsToDelete = (recordedRowIds + candidateIds + listOf(id)).distinct()
            rowIdsToDelete.forEachIndexed { idx, rowId ->
                knownGaleriSheetPhotos.remove(rowId)
                if (idx > 0) delay(80)
                sheetsService.sendAction(_sheetUrl.value, "delete", "Galeri", getAuthorityPassword(), JSONObject(), rowId)
            }
        }
        true
    }

    // --- ASPIRASI ACTIONS ---
    suspend fun submitAspirasiWarga(item: AspirasiItem): CloudSaveResult = withContext(Dispatchers.IO) {
        activeMutationCount.incrementAndGet()
        try {
            val itemId = if (item.id.isBlank()) "ASP-${System.currentTimeMillis()}" else item.id
            val rawMasalahPhotos = item.fotoMasalah.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct().take(5)
            val persistentPhotos = rawMasalahPhotos.map {
                com.example.util.PhotoStorageHelper.ensurePersistentUri(context, it)
            }.filter { it.isNotBlank() }
            val optimisticItem = item.copy(
                id = itemId,
                fotoMasalah = persistentPhotos.joinToString(","),
                status = "Menunggu"
            )

            val baseList = _aspirasiList.value.toMutableList()
            baseList.removeAll { it.id == itemId }
            baseList.add(0, optimisticItem)
            _aspirasiList.value = baseList
            saveToCache()

            val photoRequested = persistentPhotos.isNotEmpty()
            val processedPhotos = mutableListOf<String>()
            persistentPhotos.forEachIndexed { index, uri ->
                if (index > 0) delay(150)
                val uploaded = processPhotoForCloudSharing(uri, "aspirasi_masalah")
                if (uploaded.isNotBlank()) processedPhotos.add(uploaded)
            }
            val finalFotoString = processedPhotos.joinToString(",")
            val photoUploaded = !photoRequested || (
                processedPhotos.size == persistentPhotos.size &&
                    processedPhotos.all { com.example.util.PhotoStorageHelper.isPublicHttpUrl(it) }
                )

            val fullItem = optimisticItem.copy(fotoMasalah = finalFotoString)
            val updated = _aspirasiList.value.toMutableList()
            val idx = updated.indexOfFirst { it.id == itemId }
            if (idx != -1) updated[idx] = fullItem else updated.add(0, fullItem)
            _aspirasiList.value = updated
            saveToCache()

            syncManager.saveAspirasi(fullItem)
            com.example.util.RemoteNotificationTracker.markLocalAspirasiSubmitted(context, fullItem)

            com.example.util.NotificationHelper.showPushNotification(
                context = context,
                title = "🚨 Aspirasi Baru dari Warga!",
                message = "${fullItem.nama} (${fullItem.kategori}): ${fullItem.pesan}",
                channelId = com.example.util.NotificationHelper.CHANNEL_ASPIRASI,
                type = "ASPIRASI"
            )

            val sheetOk = if (_sheetUrl.value.isNotBlank()) {
                val masalahFormulas = processedPhotos.map { toSheetImageFormula(it) }.filter { it.isNotBlank() }
                val json = JSONObject().apply {
                    put("id", fullItem.id)
                    put("tanggal", fullItem.tanggal)
                    put("nama", fullItem.nama)
                    put("kategori", fullItem.kategori)
                    put("pesan", fullItem.pesan)
                    put("fotoMasalah", masalahFormulas.getOrNull(0) ?: "")
                    put("fotoMasalah1", masalahFormulas.getOrNull(0) ?: "")
                    put("fotoMasalah2", masalahFormulas.getOrNull(1) ?: "")
                    put("fotoMasalah3", masalahFormulas.getOrNull(2) ?: "")
                    put("fotoMasalah4", masalahFormulas.getOrNull(3) ?: "")
                    put("fotoMasalah5", masalahFormulas.getOrNull(4) ?: "")
                    put("status", fullItem.status)
                    put("tanggapanPengurus", "")
                    put("fotoPengerjaan", "")
                    put("fotoPengerjaan1", "")
                    put("fotoPengerjaan2", "")
                    put("fotoPengerjaan3", "")
                    put("fotoPengerjaan4", "")
                    put("fotoPengerjaan5", "")
                }
                sheetsService.sendAction(_sheetUrl.value, "add", "Aspirasi", "", json)
            } else false

            CloudSaveResult(
                localSaved = true,
                photoRequested = photoRequested,
                photoUploadedToCloud = photoUploaded,
                sheetSynced = sheetOk,
                message = buildSaveStatusMessage(
                    entityName = if (processedPhotos.size > 1) "Aspirasi Warga (${processedPhotos.size} foto)" else "Aspirasi Warga",
                    photoRequested = photoRequested,
                    photoUploaded = photoUploaded,
                    sheetSynced = sheetOk
                )
            )
        } finally {
            activeMutationCount.decrementAndGet()
        }
    }

    suspend fun respondAspirasi(id: String, status: String, tanggapan: String, fotoPengerjaan: String): CloudSaveResult = withContext(Dispatchers.IO) {
        activeMutationCount.incrementAndGet()
        try {
            val baseList = _aspirasiList.value.toMutableList()
            val index = baseList.indexOfFirst { it.id == id }
            if (index != -1) {
                val current = baseList[index]
                val rawPengerjaanPhotos = fotoPengerjaan.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct().take(5)
                val persistentPhotos = rawPengerjaanPhotos.map {
                    com.example.util.PhotoStorageHelper.ensurePersistentUri(context, it)
                }.filter { it.isNotBlank() }
                val optimisticItem = current.copy(
                    status = status,
                    tanggapanPengurus = tanggapan,
                    fotoPengerjaan = persistentPhotos.joinToString(",")
                )
                baseList[index] = optimisticItem
                _aspirasiList.value = baseList
                saveToCache()

                val photoRequested = persistentPhotos.isNotEmpty()
                val processedPhotos = mutableListOf<String>()
                persistentPhotos.forEachIndexed { pIdx, uri ->
                    if (pIdx > 0) delay(150)
                    val uploaded = processPhotoForCloudSharing(uri, "aspirasi_solusi")
                    if (uploaded.isNotBlank()) processedPhotos.add(uploaded)
                }
                val processedFotoPengerjaan = processedPhotos.joinToString(",")
                val photoUploaded = !photoRequested || (
                    processedPhotos.size == persistentPhotos.size &&
                        processedPhotos.all { com.example.util.PhotoStorageHelper.isPublicHttpUrl(it) }
                    )

                val updatedItem = optimisticItem.copy(fotoPengerjaan = processedFotoPengerjaan)
                val updated = _aspirasiList.value.toMutableList()
                val idx = updated.indexOfFirst { it.id == id }
                if (idx != -1) updated[idx] = updatedItem
                _aspirasiList.value = updated
                saveToCache()

                syncManager.saveAspirasi(updatedItem)
                com.example.util.RemoteNotificationTracker.markLocalAspirasiResponded(context, updatedItem)

                com.example.util.NotificationHelper.showPushNotification(
                    context = context,
                    title = "✅ Tanggapan Pengurus RT",
                    message = "Untuk ${updatedItem.nama}: Status [$status] - $tanggapan",
                    channelId = com.example.util.NotificationHelper.CHANNEL_ASPIRASI,
                    type = "ASPIRASI"
                )

                val sheetOk = if (_sheetUrl.value.isNotBlank()) {
                    val pengerjaanFormulas = processedPhotos.map { toSheetImageFormula(it) }.filter { it.isNotBlank() }
                    val json = JSONObject().apply {
                        put("status", status)
                        put("tanggapanPengurus", tanggapan)
                        put("fotoPengerjaan", pengerjaanFormulas.getOrNull(0) ?: "")
                        put("fotoPengerjaan1", pengerjaanFormulas.getOrNull(0) ?: "")
                        put("fotoPengerjaan2", pengerjaanFormulas.getOrNull(1) ?: "")
                        put("fotoPengerjaan3", pengerjaanFormulas.getOrNull(2) ?: "")
                        put("fotoPengerjaan4", pengerjaanFormulas.getOrNull(3) ?: "")
                        put("fotoPengerjaan5", pengerjaanFormulas.getOrNull(4) ?: "")
                    }
                    sheetsService.sendAction(_sheetUrl.value, "respond_aspirasi", "Aspirasi", getAuthorityPassword(), json, id)
                } else false

                CloudSaveResult(
                    localSaved = true,
                    photoRequested = photoRequested,
                    photoUploadedToCloud = photoUploaded,
                    sheetSynced = sheetOk,
                    message = buildSaveStatusMessage(
                        entityName = "Tanggapan Pengurus",
                        photoRequested = photoRequested,
                        photoUploaded = photoUploaded,
                        sheetSynced = sheetOk
                    )
                )
            } else {
                CloudSaveResult(
                    localSaved = false,
                    photoRequested = fotoPengerjaan.isNotBlank(),
                    photoUploadedToCloud = false,
                    sheetSynced = false,
                    message = "Gagal menyimpan tanggapan: data aspirasi tidak ditemukan."
                )
            }
        } finally {
            activeMutationCount.decrementAndGet()
        }
    }

    suspend fun deleteAspirasi(id: String): Boolean = withContext(Dispatchers.IO) {
        _aspirasiList.value = _aspirasiList.value.filter { it.id != id }
        saveToCache()

        // Sync deletion to Room Local DB and Cloud Firestore
        syncManager.deleteAspirasi(id)

        if (_sheetUrl.value.isNotBlank()) {
            sheetsService.sendAction(_sheetUrl.value, "delete", "Aspirasi", getAuthorityPassword(), JSONObject(), id)
        }
        true
    }

    suspend fun testSpreadsheetConnection(customUrl: String? = null): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val targetUrl = customUrl?.trim()?.takeIf { it.isNotBlank() } ?: _sheetUrl.value
        if (targetUrl.isBlank()) return@withContext Pair(false, "URL Google Apps Script belum diisi!")
        if (targetUrl.contains("script.google.com/macros/s/")) {
            updateSheetUrlOnly(targetUrl)
        } else {
            sheetsService.resetCapabilityCache()
        }
        sheetsService.testConnection(targetUrl)
    }

    suspend fun backupAllToSpreadsheet(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val url = _sheetUrl.value
        if (url.isBlank()) return@withContext Pair(false, "URL Google Apps Script belum diisi!")
        if (url.contains("docs.google.com/spreadsheets")) {
            return@withContext Pair(
                false,
                "URL yang dimasukkan adalah link lembar kerja browser. Diperlukan URL Web App Google Apps Script (/exec) agar Google mengizinkan backup data."
            )
        }

        sheetsService.resetCapabilityCache()
        activeMutationCount.incrementAndGet()
        try {
            // 1. Unggah seluruh foto lokal yang tersisa ke Cloud/Drive lalu perbarui state & cache
            val resolvedKas = _kasList.value.map { k ->
                val cloudUrl = if (k.buktiFoto.isNotBlank()) processPhotoForCloudSharing(k.buktiFoto, "kas_bukti") else ""
                k.copy(buktiFoto = cloudUrl)
            }
            _kasList.value = resolvedKas

            val resolvedPgr = _pengurusList.value.map { p ->
                val cloudUrl = if (p.foto.isNotBlank()) processPhotoForCloudSharing(p.foto, "pengurus") else ""
                p.copy(foto = cloudUrl)
            }
            _pengurusList.value = resolvedPgr

            val resolvedGal = _galeriList.value.map { g ->
                val rawPhotos = g.foto.split(",").map { it.trim() }.filter { it.isNotBlank() }
                val cloudUrls = rawPhotos.map { processPhotoForCloudSharing(it, "galeri") }.filter { it.isNotBlank() }
                g.copy(foto = cloudUrls.joinToString(","))
            }
            _galeriList.value = resolvedGal

            val resolvedAsp = _aspirasiList.value.map { a ->
                val rawMasalah = a.fotoMasalah.split(",").map { it.trim() }.filter { it.isNotBlank() }.take(5)
                val masalahUrl = rawMasalah.map { processPhotoForCloudSharing(it, "aspirasi_masalah") }
                    .filter { it.isNotBlank() }
                    .joinToString(",")
                val rawSolusi = a.fotoPengerjaan.split(",").map { it.trim() }.filter { it.isNotBlank() }.take(5)
                val solusiUrl = rawSolusi.map { processPhotoForCloudSharing(it, "aspirasi_solusi") }
                    .filter { it.isNotBlank() }
                    .joinToString(",")
                a.copy(fotoMasalah = masalahUrl, fotoPengerjaan = solusiUrl)
            }
            _aspirasiList.value = resolvedAsp
            saveToCache()

            // 2. Susun payload bulk backup dengan rumus =IMAGE("URL")
            val kasPayloads = resolvedKas.map { k ->
                val fotoFormula = toSheetImageFormula(k.buktiFoto)
                k.id to JSONObject().apply {
                    put("id", k.id)
                    put("tanggal", k.tanggal)
                    put("periode_bulan", k.periodeBulan)
                    put("periodeBulan", k.periodeBulan)
                    put("kategori", k.kategori)
                    put("keterangan", k.keterangan)
                    put("masuk", k.masuk)
                    put("keluar", k.keluar)
                    put("saldo", k.effectiveSaldo)
                    put("jumlah", k.effectiveSaldo)
                    put("bukti_foto", fotoFormula)
                    put("buktiFoto", fotoFormula)
                }
            }
            val pgrPayloads = resolvedPgr.map { p ->
                p.id to JSONObject().apply {
                    put("id", p.id)
                    put("jabatan", p.jabatan)
                    put("nama", p.nama)
                    put("noHp", p.noHp)
                    put("alamat", p.alamat)
                    put("masaBakti", p.masaBakti)
                    put("foto", toSheetImageFormula(p.foto))
                }
            }
            val undPayloads = _undanganList.value.map { u ->
                val resolvedFoto = if (u.foto.isNotBlank()) processPhotoForCloudSharing(u.foto, "undangan_foto") else ""
                u.id to buildUndanganJson(u.copy(foto = resolvedFoto), forSheet = true)
            }
            val supportsMultiPhotoCols = sheetsService.supportsMultiPhotoColumns(url)
            val supportsGaleri10Cols = sheetsService.supportsGaleri10Columns(url)
            val galCombinedPayloads = resolvedGal.flatMap { g ->
                val formulas = g.foto.split(",").map { it.trim() }.filter { it.isNotBlank() }.take(10)
                    .map { toSheetImageFormula(it) }.filter { it.isNotBlank() }
                if (supportsGaleri10Cols) {
                    listOf(g.id to com.example.util.GaleriMultiPhotoHelper.buildCombinedRowPayload(g, formulas))
                } else {
                    com.example.util.GaleriMultiPhotoHelper.chunkPhotosForFiveColumnSheet(g, formulas)
                }
            }
            val galExpandedPayloads = resolvedGal.flatMap { g ->
                val formulas = g.foto.split(",").map { it.trim() }.filter { it.isNotBlank() }.take(10)
                    .map { toSheetImageFormula(it) }.filter { it.isNotBlank() }
                com.example.util.GaleriMultiPhotoHelper.expandRowsForSingleColumnSheet(g, formulas)
            }
            val aspPayloads = resolvedAsp.map { a ->
                val masalahFormulas = a.fotoMasalah.split(",").map { it.trim() }.filter { it.isNotBlank() }.take(5)
                    .map { toSheetImageFormula(it) }.filter { it.isNotBlank() }
                val pengerjaanFormulas = a.fotoPengerjaan.split(",").map { it.trim() }.filter { it.isNotBlank() }.take(5)
                    .map { toSheetImageFormula(it) }.filter { it.isNotBlank() }
                a.id to JSONObject().apply {
                    put("id", a.id)
                    put("tanggal", a.tanggal)
                    put("nama", a.nama)
                    put("kategori", a.kategori)
                    put("pesan", a.pesan)
                    put("fotoMasalah", masalahFormulas.getOrNull(0) ?: "")
                    put("fotoMasalah1", masalahFormulas.getOrNull(0) ?: "")
                    put("fotoMasalah2", masalahFormulas.getOrNull(1) ?: "")
                    put("fotoMasalah3", masalahFormulas.getOrNull(2) ?: "")
                    put("fotoMasalah4", masalahFormulas.getOrNull(3) ?: "")
                    put("fotoMasalah5", masalahFormulas.getOrNull(4) ?: "")
                    put("status", a.status)
                    put("tanggapanPengurus", a.tanggapanPengurus)
                    put("fotoPengerjaan", pengerjaanFormulas.getOrNull(0) ?: "")
                    put("fotoPengerjaan1", pengerjaanFormulas.getOrNull(0) ?: "")
                    put("fotoPengerjaan2", pengerjaanFormulas.getOrNull(1) ?: "")
                    put("fotoPengerjaan3", pengerjaanFormulas.getOrNull(2) ?: "")
                    put("fotoPengerjaan4", pengerjaanFormulas.getOrNull(3) ?: "")
                    put("fotoPengerjaan5", pengerjaanFormulas.getOrNull(4) ?: "")
                }
            }

            if (supportsMultiPhotoCols) {
                val bulkJson = JSONObject().apply {
                    put("kas", JSONArray().apply { kasPayloads.forEach { put(it.second) } })
                    put("pengurus", JSONArray().apply { pgrPayloads.forEach { put(it.second) } })
                    put("undangan", JSONArray().apply { undPayloads.forEach { put(it.second) } })
                    put("galeri", JSONArray().apply { galCombinedPayloads.forEach { put(it.second) } })
                    put("aspirasi", JSONArray().apply { aspPayloads.forEach { put(it.second) } })
                }

                val bulkOk = sheetsService.backupAll(url, getAuthorityPassword(), bulkJson)
                val totalBulkItems = kasPayloads.size + pgrPayloads.size + undPayloads.size + galCombinedPayloads.size + aspPayloads.size

                if (bulkOk) {
                    localUnsyncedGaleriIds.clear()
                    return@withContext Pair(true, "Berhasil membackup $totalBulkItems data (termasuk semua foto Galeri, Kas RT, Pengurus & Aspirasi) ke spreadsheet!")
                }
            }

            // Upsert seluruh 5 tab satu per satu (memakai action=edit agar tidak duplikat; Galeri multi-foto otomatis dipecah per baris jika script masih v1)
            val galTargetPayloads = if (supportsMultiPhotoCols) {
                galCombinedPayloads
            } else {
                galExpandedPayloads.map { it.rowId to it.json }
            }
            val totalItems = kasPayloads.size + pgrPayloads.size + undPayloads.size + galTargetPayloads.size + aspPayloads.size
            var successCount = 0
            for ((id, json) in kasPayloads) {
                if (sheetsService.sendAction(url, "edit", "Kas_RT", getAuthorityPassword(), json, id)) successCount++
            }
            for ((id, json) in pgrPayloads) {
                if (sheetsService.sendAction(url, "edit", "Pengurus", getAuthorityPassword(), json, id)) successCount++
            }
            for ((id, json) in undPayloads) {
                if (sheetsService.sendAction(url, "edit", "Undangan", getAuthorityPassword(), json, id)) successCount++
            }
            galTargetPayloads.forEachIndexed { idx, (rowId, json) ->
                if (idx > 0) delay(120)
                if (sheetsService.sendAction(url, "edit", "Galeri", getAuthorityPassword(), json, rowId)) {
                    val baseId = com.example.util.GaleriMultiPhotoHelper.baseGaleriId(rowId)
                    localUnsyncedGaleriIds.remove(baseId)
                    val formulaUrl = com.example.util.PhotoStorageHelper.extractUrlFromFormula(json.optString("foto", ""))
                    if (formulaUrl.isNotBlank()) {
                        knownGaleriSheetPhotos[rowId] = formulaUrl
                    }
                    successCount++
                }
            }
            for ((id, json) in aspPayloads) {
                if (sheetsService.sendAction(url, "edit", "Aspirasi", getAuthorityPassword(), json, id)) successCount++
            }

            if (successCount > 0 || totalItems == 0) {
                Pair(true, "Berhasil membackup $successCount dari $totalItems baris data (termasuk semua foto Galeri) ke Google Spreadsheet 'Aplikasi_Warga'!")
            } else {
                Pair(false, "Gagal membackup data ke Spreadsheet. Periksa URL Apps Script & izin akses 'Anyone'.")
            }
        } catch (e: Exception) {
            Pair(false, "Gagal backup: ${e.localizedMessage}")
        } finally {
            activeMutationCount.decrementAndGet()
        }
    }

    // --- CACHE & INITIAL DATA ---
    private fun saveToCache() {
        try {
            val editor = prefs.edit()
            // Save Kas
            val kasArr = JSONArray()
            _kasList.value.forEach { item ->
                kasArr.put(JSONObject().apply {
                    put("id", item.id)
                    put("tanggal", item.tanggal)
                    put("periode_bulan", item.periodeBulan)
                    put("kategori", item.kategori)
                    put("keterangan", item.keterangan)
                    put("masuk", item.masuk)
                    put("keluar", item.keluar)
                    put("saldo", item.effectiveSaldo)
                    put("jumlah", item.effectiveSaldo)
                    put("bukti_foto", item.buktiFoto)
                    put("buktiFoto", item.buktiFoto)
                })
            }
            editor.putString("cache_kas", kasArr.toString())

            // Save Pengurus
            val pgrArr = JSONArray()
            _pengurusList.value.forEach { item ->
                pgrArr.put(JSONObject().apply {
                    put("id", item.id)
                    put("jabatan", item.jabatan)
                    put("nama", item.nama)
                    put("noHp", item.noHp)
                    put("alamat", item.alamat)
                    put("masaBakti", item.masaBakti)
                    put("foto", item.foto)
                })
            }
            editor.putString("cache_pengurus", pgrArr.toString())

            // Save Undangan
            val undArr = JSONArray()
            _undanganList.value.forEach { item ->
                undArr.put(buildUndanganJson(item))
            }
            editor.putString("cache_undangan", undArr.toString())

            // Save Galeri
            val galArr = JSONArray()
            _galeriList.value.forEach { item ->
                galArr.put(JSONObject().apply {
                    put("id", item.id)
                    put("tanggal", item.tanggal)
                    put("judul", item.judul)
                    put("kategori", item.kategori)
                    put("keterangan", item.keterangan)
                    put("foto", item.foto)
                    put("petugas", item.petugas)
                })
            }
            editor.putString("cache_galeri", galArr.toString())

            // Save Aspirasi
            val aspArr = JSONArray()
            _aspirasiList.value.forEach { item ->
                aspArr.put(JSONObject().apply {
                    put("id", item.id)
                    put("tanggal", item.tanggal)
                    put("nama", item.nama)
                    put("noRumah", item.noRumah)
                    put("sembunyikanNama", item.sembunyikanNama)
                    put("kategori", item.kategori)
                    put("pesan", item.pesan)
                    put("fotoMasalah", item.fotoMasalah)
                    put("status", item.status)
                    put("tanggapanPengurus", item.tanggapanPengurus)
                    put("fotoPengerjaan", item.fotoPengerjaan)
                })
            }
            editor.putString("cache_aspirasi", aspArr.toString())
            editor.apply()
        } catch (_: Exception) {}
    }

    private fun saveKasToCache(items: List<KasItem>) {
        try {
            val kasArr = JSONArray()
            items.forEach { item ->
                kasArr.put(JSONObject().apply {
                    put("id", item.id)
                    put("tanggal", item.tanggal)
                    put("periode_bulan", item.periodeBulan)
                    put("kategori", item.kategori)
                    put("keterangan", item.keterangan)
                    put("masuk", item.masuk)
                    put("keluar", item.keluar)
                    put("saldo", item.effectiveSaldo)
                    put("jumlah", item.effectiveSaldo)
                    put("bukti_foto", item.buktiFoto)
                    put("buktiFoto", item.buktiFoto)
                })
            }
            prefs.edit().putString("cache_kas", kasArr.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun saveAspirasiToCache(items: List<AspirasiItem>) {
        try {
            val aspArr = JSONArray()
            items.forEach { item ->
                aspArr.put(JSONObject().apply {
                    put("id", item.id)
                    put("tanggal", item.tanggal)
                    put("nama", item.nama)
                    put("noRumah", item.noRumah)
                    put("sembunyikanNama", item.sembunyikanNama)
                    put("kategori", item.kategori)
                    put("pesan", item.pesan)
                    put("fotoMasalah", item.fotoMasalah)
                    put("status", item.status)
                    put("tanggapanPengurus", item.tanggapanPengurus)
                    put("fotoPengerjaan", item.fotoPengerjaan)
                })
            }
            prefs.edit().putString("cache_aspirasi", aspArr.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun loadFromCache() {
        try {
            val kasJson = prefs.getString("cache_kas", null)
            if (!kasJson.isNullOrBlank()) {
                val arr = JSONArray(kasJson)
                val list = mutableListOf<KasItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val tgl = obj.optString("tanggal", "")
                    val periode = obj.optString("periode_bulan", obj.optString("periodeBulan", ""))
                        .ifBlank { com.example.util.KasRecapHelper.resolvePeriodeKey("", tgl) }
                    val saldoVal = obj.optLong("saldo", obj.optLong("jumlah", 0L))
                    list.add(KasItem(
                        id = obj.getString("id"),
                        tanggal = tgl,
                        periodeBulan = periode,
                        kategori = obj.optString("kategori", ""),
                        keterangan = obj.optString("keterangan", ""),
                        masuk = obj.optLong("masuk", 0L),
                        keluar = obj.optLong("keluar", 0L),
                        saldo = saldoVal,
                        jumlah = saldoVal,
                        buktiFoto = obj.optString("bukti_foto", obj.optString("buktiFoto", ""))
                    ))
                }
                _kasList.value = recalculateKasBalances(list)
            }

            val pgrJson = prefs.getString("cache_pengurus", null)
            if (!pgrJson.isNullOrBlank()) {
                val arr = JSONArray(pgrJson)
                val list = mutableListOf<PengurusItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(PengurusItem(
                        id = obj.getString("id"),
                        jabatan = obj.getString("jabatan"),
                        nama = obj.getString("nama"),
                        noHp = obj.getString("noHp"),
                        alamat = obj.getString("alamat"),
                        masaBakti = obj.getString("masaBakti"),
                        foto = obj.optString("foto", "")
                    ))
                }
                _pengurusList.value = list
            }

            val undJson = prefs.getString("cache_undangan", null)
            if (!undJson.isNullOrBlank()) {
                val arr = JSONArray(undJson)
                val list = mutableListOf<UndanganItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val rawKet = obj.optString("keterangan")
                    val marker = com.example.util.ReminderCodec.parseMarker(rawKet)
                    val pMenit = obj.optInt("pengingatMenit", marker?.minutes ?: 0)
                    val pTarget = obj.optLong("pengingatTargetMillis", marker?.targetMillis ?: 0L)
                    val pActivated = obj.optLong("pengingatActivatedMillis", marker?.activatedMillis ?: 0L)
                    val pAktif = obj.optBoolean("pengingatAktif", pMenit > 0 && pTarget > 0L)
                    list.add(UndanganItem(
                        id = obj.getString("id"),
                        judul = obj.getString("judul"),
                        tanggal = obj.getString("tanggal"),
                        waktu = obj.optString("waktu"),
                        lokasi = obj.optString("lokasi"),
                        keterangan = rawKet,
                        tipe = obj.optString("tipe", if (obj.optString("judul").startsWith("[Pengumuman]", ignoreCase = true)) "Pengumuman" else "Undangan"),
                        pengingatMenit = pMenit,
                        pengingatAktif = pAktif,
                        pengingatTargetMillis = pTarget,
                        pengingatActivatedMillis = pActivated,
                        foto = obj.optString("foto", "")
                    ))
                }
                _undanganList.value = list
            }

            val galJson = prefs.getString("cache_galeri", null)
            if (!galJson.isNullOrBlank()) {
                val arr = JSONArray(galJson)
                val list = mutableListOf<GaleriItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val fotoStr = obj.optString("foto", "")
                    val firstFoto = fotoStr.split(",").firstOrNull()?.trim() ?: ""
                    // Jika berisi file:// lokal dari perangkat lain yang tidak ada di HP ini, abaikan
                    if (firstFoto.startsWith("file://") && !java.io.File(android.net.Uri.parse(firstFoto).path ?: "").exists()) {
                        continue
                    }
                    list.add(GaleriItem(
                        id = obj.getString("id"),
                        tanggal = obj.getString("tanggal"),
                        judul = obj.getString("judul"),
                        kategori = obj.getString("kategori"),
                        keterangan = obj.getString("keterangan"),
                        foto = fotoStr,
                        petugas = obj.optString("petugas", "")
                    ))
                }
                _galeriList.value = com.example.util.GaleriMultiPhotoHelper.mergeGroupedGaleriRows(list)
            }

            val aspJson = prefs.getString("cache_aspirasi", null)
            if (!aspJson.isNullOrBlank()) {
                val arr = JSONArray(aspJson)
                val list = mutableListOf<AspirasiItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val nama = obj.getString("nama")
                    if (nama.contains("Mulyadi") || nama.contains("Siti Fatimah") || nama.contains("Blok B")) {
                        continue
                    }
                    list.add(AspirasiItem(
                        id = obj.getString("id"),
                        tanggal = obj.getString("tanggal"),
                        nama = nama,
                        noRumah = obj.optString("noRumah", ""),
                        sembunyikanNama = obj.optBoolean("sembunyikanNama", false),
                        kategori = obj.optString("kategori", "Masukan/Saran"),
                        pesan = obj.getString("pesan"),
                        fotoMasalah = obj.optString("fotoMasalah", ""),
                        status = obj.optString("status", "Menunggu"),
                        tanggapanPengurus = obj.optString("tanggapanPengurus", ""),
                        fotoPengerjaan = obj.optString("fotoPengerjaan", "")
                    ))
                }
                if (list.isNotEmpty()) {
                    _aspirasiList.value = list
                }
            }
        } catch (_: Exception) {}
    }

    private fun loadInitialDummyData() {
        // Data awal sinkron dengan data riil Google Spreadsheet & HP Admin:
        // Saldo Masuk: Rp 1.560.000, Keluar: Rp 260.000, Saldo Kas: Rp 1.300.000!
        _kasList.value = listOf(
            KasItem(
                id = "KAS-01",
                tanggal = "2026-11-08",
                periodeBulan = "2026-11",
                kategori = "Donatur",
                keterangan = "donatur",
                masuk = 500000L,
                keluar = 0L,
                saldo = 500000L,
                jumlah = 500000L,
                buktiFoto = ""
            ),
            KasItem(
                id = "KAS-02",
                tanggal = "2026-11-10",
                periodeBulan = "2026-11",
                kategori = "Iuran Warga",
                keterangan = "prelek",
                masuk = 60000L,
                keluar = 0L,
                saldo = 560000L,
                jumlah = 560000L,
                buktiFoto = ""
            ),
            KasItem(
                id = "KAS-03",
                tanggal = "2026-11-10",
                periodeBulan = "2026-11",
                kategori = "Operasional",
                keterangan = "Pengeluaran Operasional / Kas RT",
                masuk = 0L,
                keluar = 260000L,
                saldo = 300000L,
                jumlah = 300000L,
                buktiFoto = ""
            ),
            KasItem(
                id = "KAS-04",
                tanggal = "2026-11-10",
                periodeBulan = "2026-11",
                kategori = "Donatur",
                keterangan = "donasi dr bpk cecep",
                masuk = 1000000L,
                keluar = 0L,
                saldo = 1300000L,
                jumlah = 1300000L,
                buktiFoto = ""
            )
        )

        _pengurusList.value = listOf(
            PengurusItem(
                id = "PGR-01",
                jabatan = "Sekertaris",
                nama = "Adithiawan Wijaksana",
                noHp = "+6281294849796",
                alamat = "No. 31",
                masaBakti = "2026 - 2030",
                foto = ""
            ),
            PengurusItem(
                id = "PGR-02",
                jabatan = "Bendahara",
                nama = "Riyad",
                noHp = "+6281318920658",
                alamat = "RT 005 / RW 011",
                masaBakti = "2025 - 2030",
                foto = ""
            ),
            PengurusItem(
                id = "PGR-03",
                jabatan = "Humas",
                nama = "Cecep",
                noHp = "+6285774001599",
                alamat = "RT 005 / RW 011",
                masaBakti = "2025 - 2030",
                foto = ""
            ),
            PengurusItem(
                id = "PGR-04",
                jabatan = "SDM",
                nama = "Robi",
                noHp = "+6281319608958",
                alamat = "RT 005 / RW 011",
                masaBakti = "2025 - 2030",
                foto = ""
            ),
            PengurusItem(
                id = "PGR-05",
                jabatan = "Ekonomi kemasyarakatan",
                nama = "Subur",
                noHp = "+6281535370503",
                alamat = "RT 005 / RW 011",
                masaBakti = "2025 - 2030",
                foto = ""
            ),
            PengurusItem(
                id = "PGR-06",
                jabatan = "Agama Sosial Budaya",
                nama = "Sarmad",
                noHp = "+6285810883689",
                alamat = "RT 005 / RW 011",
                masaBakti = "2025 - 2030",
                foto = ""
            ),
            PengurusItem(
                id = "PGR-07",
                jabatan = "Ketentraman dan Pelayanan Umum",
                nama = "Deni",
                noHp = "+6281383157648",
                alamat = "RT 005 / RW 011",
                masaBakti = "2025 - 2030",
                foto = ""
            ),
            PengurusItem(
                id = "PGR-08",
                jabatan = "Pemberdayaan perempuan dan anak",
                nama = "Anisa",
                noHp = "",
                alamat = "RT 005 / RW 011",
                masaBakti = "2025 - 2030",
                foto = ""
            ),
            PengurusItem(
                id = "PGR-09",
                jabatan = "Ketua RT",
                nama = "Ketua RT 005 / RW 011",
                noHp = "",
                alamat = "RT 005 / RW 011",
                masaBakti = "2025 - 2030",
                foto = ""
            )
        )

        _undanganList.value = listOf(
            UndanganItem(
                id = "PGM-01",
                judul = "Laporan Transparansi Kas & Gotong Royong Lingkungan RT 005",
                tanggal = "2026-11-10",
                waktu = "24 Jam",
                lokasi = "Wilayah RT 005 / RW 011",
                keterangan = "Kas RT periode berjalan Rp 1.300.000 (Pemasukan: Rp 1.560.000, Pengeluaran: Rp 260.000). Terima kasih atas partisipasi aktif seluruh warga RT 005/011 Mulyaharja.",
                tipe = "Pengumuman"
            ),
            UndanganItem(
                id = "UND-01",
                judul = "acara ngaliwet",
                tanggal = "2026-11-10",
                waktu = "19:30 WIB",
                lokasi = "lap badminton",
                keterangan = "silaturahmi dan ngaliwet bareng warga",
                tipe = "Undangan"
            )
        )

        // Galeri dimulai dalam kondisi bersih (kosong) sesuai permintaan,
        // agar admin dapat mengunggah foto kegiatan asli yang langsung tersinkron ke semua warga.
        _galeriList.value = emptyList()

        // Aspirasi andi persis sesuai data dari HP Admin Induk & Spreadsheet
        _aspirasiList.value = listOf(
            AspirasiItem(
                id = "ASP-TEST",
                tanggal = "2026-11-10",
                nama = "andi",
                kategori = "Masukan/Saran",
                pesan = "kabel lampu sudah mulai keropos",
                fotoMasalah = "",
                status = "Selesai",
                tanggapanPengurus = "ok terima kasih masukan nya akan segera dikondisikan",
                fotoPengerjaan = ""
            )
        )
        saveToCache()
    }
}
