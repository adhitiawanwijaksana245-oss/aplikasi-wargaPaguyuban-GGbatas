package com.example.data.sync

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.AspirasiEntity
import com.example.data.local.KasEntity
import com.example.data.model.AspirasiItem
import com.example.data.model.KasItem
import com.example.data.model.PengurusAccount
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

data class FirestoreSyncState(
    val isConnected: Boolean = false,
    val isSyncing: Boolean = false,
    val lastSyncTime: Long = 0L,
    val message: String = "🟢 Terkoneksi ke Spreadsheet: Aplikasi_Warga"
)

class RoomFirestoreSyncManager(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context),
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val kasDao = database.kasDao()
    private val aspirasiDao = database.aspirasiDao()

    private val _syncState = MutableStateFlow(FirestoreSyncState())
    val syncState: StateFlow<FirestoreSyncState> = _syncState.asStateFlow()

    private var kasListenerRegistration: ListenerRegistration? = null
    private var aspirasiListenerRegistration: ListenerRegistration? = null
    private var configListenerRegistration: ListenerRegistration? = null

    private val _remotePengurusAccounts = MutableStateFlow<List<PengurusAccount>?>(null)
    val remotePengurusAccounts: StateFlow<List<PengurusAccount>?> = _remotePengurusAccounts.asStateFlow()

    private val _remoteSplashDurationMs = MutableStateFlow<Long?>(null)
    val remoteSplashDurationMs: StateFlow<Long?> = _remoteSplashDurationMs.asStateFlow()

    companion object {
        private const val TAG = "RoomFirestoreSync"
        const val COLLECTION_KAS = "kas_rt"
        const val COLLECTION_ASPIRASI = "aspirasi"
        const val COLLECTION_CONFIG = "app_config"
        const val DOC_SECURITY_SETTINGS = "security_settings"
    }

    init {
        startRealtimeSync()
    }

    private fun getFirestoreInstance(): FirebaseFirestore? {
        return try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                Log.d(TAG, "FirebaseApp belum diinisialisasi. Berjalan dalam mode Room lokal.")
                null
            } else {
                val dbIdRes = context.resources.getIdentifier("firestore_database_id", "string", context.packageName)
                if (dbIdRes != 0) {
                    val dbId = context.getString(dbIdRes)
                    FirebaseFirestore.getInstance(dbId)
                } else {
                    FirebaseFirestore.getInstance()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal mendapatkan instance Firestore: ${e.message}")
            null
        }
    }

    fun isFirebaseConfigured(): Boolean = getFirestoreInstance() != null

    /**
     * Memulai real-time listener dari Cloud Firestore ke Room Database lokal.
     */
    fun startRealtimeSync() {
        val firestore = getFirestoreInstance()
        if (firestore == null) {
            _syncState.value = FirestoreSyncState(
                isConnected = false,
                isSyncing = false,
                message = "🟢 Terkoneksi ke Spreadsheet: Aplikasi_Warga"
            )
            return
        }

        _syncState.value = _syncState.value.copy(
            isSyncing = true,
            message = "Menghubungkan ke Cloud Firestore..."
        )

        // 1. Inbound SnapshotListener untuk Kas RT
        try {
            kasListenerRegistration?.remove()
            kasListenerRegistration = firestore.collection(COLLECTION_KAS)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error mendengarkan koleksi Kas RT: ${error.message}", error)
                        _syncState.value = _syncState.value.copy(
                            isConnected = false,
                            isSyncing = false,
                            message = "⚠️ Error Firestore Kas RT: ${error.localizedMessage}"
                        )
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        coroutineScope.launch {
                            try {
                                val remoteKasEntities = snapshot.documents.mapNotNull { doc ->
                                    val id = doc.id
                                    val tanggal = doc.getString("tanggal") ?: ""
                                    val periodeBulan = doc.getString("periode_bulan") ?: doc.getString("periodeBulan") ?: ""
                                    val kategori = doc.getString("kategori") ?: ""
                                    val keterangan = doc.getString("keterangan") ?: ""
                                    val masuk = doc.getLong("masuk") ?: 0L
                                    val keluar = doc.getLong("keluar") ?: 0L
                                    val saldo = doc.getLong("saldo") ?: doc.getLong("jumlah") ?: 0L
                                    val buktiFoto = doc.getString("bukti_foto") ?: doc.getString("buktiFoto") ?: ""

                                    KasEntity(
                                        id = id,
                                        tanggal = tanggal,
                                        periodeBulan = periodeBulan,
                                        kategori = kategori,
                                        keterangan = keterangan,
                                        masuk = masuk,
                                        keluar = keluar,
                                        saldo = saldo,
                                        jumlah = saldo,
                                        buktiFoto = buktiFoto,
                                        syncStatus = KasEntity.SYNC_STATUS_SYNCED
                                    )
                                }

                                if (remoteKasEntities.isNotEmpty()) {
                                    kasDao.insertKasList(remoteKasEntities)
                                }

                                _syncState.value = FirestoreSyncState(
                                    isConnected = true,
                                    isSyncing = false,
                                    lastSyncTime = System.currentTimeMillis(),
                                    message = "🟢 Terhubung ke Firestore Realtime (Kas RT & Aspirasi)"
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Gagal mengupdate Room dari Firestore Kas RT", e)
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memasang listener Kas RT", e)
        }

        // 2. Inbound SnapshotListener untuk Aspirasi Warga
        try {
            aspirasiListenerRegistration?.remove()
            aspirasiListenerRegistration = firestore.collection(COLLECTION_ASPIRASI)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error mendengarkan koleksi Aspirasi: ${error.message}", error)
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        coroutineScope.launch {
                            try {
                                val remoteAspirasiEntities = snapshot.documents.mapNotNull { doc ->
                                    val id = doc.getString("ID")?.takeIf { it.isNotBlank() } ?: doc.id
                                    val tanggal = doc.getString("Tanggal") ?: doc.getString("tanggal") ?: ""
                                    val nama = doc.getString("Nama") ?: doc.getString("nama") ?: ""
                                    val noRumah = doc.getString("No_Rumah") ?: doc.getString("noRumah") ?: ""
                                    val sembunyikanNama = doc.getBoolean("Sembunyikan_Nama") ?: doc.getBoolean("sembunyikanNama") ?: false
                                    val kategori = (doc.getString("Kategori") ?: doc.getString("kategori") ?: "Masukan/Saran")
                                        .ifBlank { "Masukan/Saran" }
                                    val pesan = doc.getString("Pesan") ?: doc.getString("pesan") ?: ""
                                    val fotoMasalah = readMultiSlotFirestorePhotos(
                                        doc = doc,
                                        slotKeys = listOf(
                                            "Foto_Masalah_1", "Foto_Masalah_2", "Foto_Masalah_3",
                                            "Foto_Masalah_4", "Foto_Masalah_5"
                                        ),
                                        legacyKeys = listOf("fotoMasalah", "Foto_Masalah")
                                    )
                                    val status = (doc.getString("Status") ?: doc.getString("status") ?: "Menunggu")
                                        .ifBlank { "Menunggu" }
                                    val rawTanggapan = doc.getString("Tanggapan_pengurus")
                                        ?: doc.getString("Tanggapan_Pengurus")
                                        ?: doc.getString("tanggapanPengurus")
                                        ?: ""
                                    val parsedReplyMeta = com.example.util.AspirasiReplyCodec.parseAndStrip(rawTanggapan)
                                    val tanggapanPengurus = parsedReplyMeta.cleanTanggapan
                                    val penanggapNama = (doc.getString("Nama_Pengurus")
                                        ?: doc.getString("namaPengurus")
                                        ?: doc.getString("penanggapNama")
                                        ?: "").ifBlank { parsedReplyMeta.penanggapNama }
                                    val fotoPengerjaan = readMultiSlotFirestorePhotos(
                                        doc = doc,
                                        slotKeys = listOf(
                                            "Foto_pengerjaan_1", "Foto_pengerjaan_2", "Foto_pengerjaan_3",
                                            "Foto_pengerjaan_4", "Foto_pengerjaan_5"
                                        ),
                                        legacyKeys = listOf("fotoPengerjaan", "Foto_pengerjaan", "Foto_Pengerjaan")
                                    )
                                    val penanggapUsername = doc.getString("Penanggap_Username")
                                        ?: doc.getString("penanggapUsername") ?: ""
                                    val penanggapNamaAsli = doc.getString("Penanggap_Nama_Asli")
                                        ?: doc.getString("penanggapNamaAsli") ?: ""
                                    val penanggapPeran = (doc.getString("Penanggap_Peran")
                                        ?: doc.getString("penanggapPeran")
                                        ?: "").ifBlank { parsedReplyMeta.penanggapPeran }
                                    val waktuTanggapan = (doc.getString("Waktu_Tanggapan")
                                        ?: doc.getString("waktuTanggapan")
                                        ?: "").ifBlank { parsedReplyMeta.waktuTanggapan }

                                    AspirasiEntity(
                                        id = id,
                                        tanggal = tanggal,
                                        nama = nama,
                                        noRumah = noRumah,
                                        sembunyikanNama = sembunyikanNama,
                                        kategori = kategori,
                                        pesan = pesan,
                                        fotoMasalah = fotoMasalah,
                                        status = status,
                                        tanggapanPengurus = tanggapanPengurus,
                                        fotoPengerjaan = fotoPengerjaan,
                                        penanggapNama = penanggapNama,
                                        penanggapUsername = penanggapUsername,
                                        penanggapNamaAsli = penanggapNamaAsli,
                                        penanggapPeran = penanggapPeran,
                                        waktuTanggapan = waktuTanggapan,
                                        syncStatus = AspirasiEntity.SYNC_STATUS_SYNCED
                                    )
                                }

                                val pendingIds = aspirasiDao.getPendingSyncAspirasi().map { it.id }.toSet()
                                val toUpsert = remoteAspirasiEntities.filterNot { it.id in pendingIds }
                                if (toUpsert.isNotEmpty()) {
                                    aspirasiDao.insertAspirasiList(toUpsert)
                                }

                                _syncState.value = _syncState.value.copy(
                                    isConnected = true,
                                    isSyncing = false,
                                    lastSyncTime = System.currentTimeMillis(),
                                    message = "🟢 Terhubung ke Firestore Realtime (Kas RT & Aspirasi)"
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Gagal mengupdate Room dari Firestore Aspirasi", e)
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memasang listener Aspirasi", e)
        }

        // 3. Inbound SnapshotListener untuk Pengaturan Akun Pengurus & Splash Screen
        try {
            configListenerRegistration?.remove()
            configListenerRegistration = firestore.collection(COLLECTION_CONFIG)
                .document(DOC_SECURITY_SETTINGS)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Error mendengarkan config Firestore: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        try {
                            val splashMs = snapshot.getLong("splashDurationMs")
                            if (splashMs != null && splashMs >= 1000L) {
                                _remoteSplashDurationMs.value = splashMs
                            }
                            @Suppress("UNCHECKED_CAST")
                            val rawAccounts = snapshot.get("pengurusAccounts") as? List<Map<String, Any?>>
                            if (rawAccounts != null) {
                                val parsed = rawAccounts.mapNotNull { m ->
                                    val username = (m["username"] as? String)?.trim().orEmpty()
                                    val password = (m["password"] as? String)?.trim().orEmpty()
                                    if (username.isBlank() || password.isBlank()) return@mapNotNull null
                                    PengurusAccount(
                                        id = (m["id"] as? String).orEmpty().ifBlank { "ACC-$username" },
                                        namaAsli = (m["namaAsli"] as? String).orEmpty(),
                                        username = username,
                                        password = password,
                                        aktif = (m["aktif"] as? Boolean) ?: true,
                                        createdAt = (m["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                                    )
                                }
                                _remotePengurusAccounts.value = parsed
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Gagal parsing config Firestore: ${e.message}")
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memasang listener config", e)
        }

        // Flush item yang masih tertunda di antrian lokal Room
        coroutineScope.launch {
            flushPendingSync()
        }
    }

    fun stopRealtimeSync() {
        kasListenerRegistration?.remove()
        kasListenerRegistration = null
        aspirasiListenerRegistration?.remove()
        aspirasiListenerRegistration = null
        configListenerRegistration?.remove()
        configListenerRegistration = null
    }

    fun ensureRealtimeSyncConnected() {
        val firestore = getFirestoreInstance() ?: return
        val hasAllListeners = kasListenerRegistration != null &&
            aspirasiListenerRegistration != null &&
            configListenerRegistration != null
        if (!hasAllListeners) {
            startRealtimeSync()
        } else {
            coroutineScope.launch {
                flushPendingSync()
            }
        }
        // Keep firestore reference check clear
        firestore.app
    }

    suspend fun saveAppSecurityConfig(
        pengurusAccounts: List<PengurusAccount>,
        splashDurationMs: Long
    ) = withContext(Dispatchers.IO) {
        val firestore = getFirestoreInstance() ?: return@withContext
        try {
            val serializedAccounts = pengurusAccounts.map { acc ->
                hashMapOf(
                    "id" to acc.id,
                    "namaAsli" to acc.namaAsli,
                    "username" to acc.username,
                    "password" to acc.password,
                    "aktif" to acc.aktif,
                    "createdAt" to acc.createdAt
                )
            }
            val data = hashMapOf(
                "pengurusAccounts" to serializedAccounts,
                "splashDurationMs" to splashDurationMs,
                "updatedAt" to System.currentTimeMillis()
            )
            firestore.collection(COLLECTION_CONFIG)
                .document(DOC_SECURITY_SETTINGS)
                .set(data, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Gagal menyimpan config ke Firestore: ${e.message}")
        }
    }

    /**
     * Menyimpan transaksi Kas RT ke Room lokal (offline-first) dan menyinkronkannya ke Firestore.
     */
    suspend fun saveKas(item: KasItem) = withContext(Dispatchers.IO) {
        val entity = KasEntity.fromKasItem(item, KasEntity.SYNC_STATUS_PENDING_UPLOAD)
        kasDao.insertKas(entity)

        val firestore = getFirestoreInstance()
        if (firestore != null) {
            try {
                val data = hashMapOf(
                    "id" to item.id,
                    "tanggal" to item.tanggal,
                    "periode_bulan" to item.periodeBulan,
                    "periodeBulan" to item.periodeBulan,
                    "kategori" to item.kategori,
                    "keterangan" to item.keterangan,
                    "masuk" to item.masuk,
                    "keluar" to item.keluar,
                    "saldo" to item.effectiveSaldo,
                    "jumlah" to item.effectiveSaldo,
                    "bukti_foto" to item.buktiFoto,
                    "buktiFoto" to item.buktiFoto,
                    "updatedAt" to System.currentTimeMillis()
                )
                firestore.collection(COLLECTION_KAS).document(item.id)
                    .set(data, SetOptions.merge())
                    .await()

                kasDao.insertKas(entity.copy(syncStatus = KasEntity.SYNC_STATUS_SYNCED))
                Log.d(TAG, "Transaksi Kas RT tersinkron ke Firestore: ${item.id}")
            } catch (e: Exception) {
                Log.w(TAG, "Gagal sync Kas RT ke Firestore (disimpan di Room offline): ${e.message}")
            }
        }
    }

    /**
     * Menghapus transaksi Kas RT dari Room lokal dan Firestore.
     */
    suspend fun deleteKas(id: String) = withContext(Dispatchers.IO) {
        kasDao.markKasPendingDelete(id)

        val firestore = getFirestoreInstance()
        if (firestore != null) {
            try {
                firestore.collection(COLLECTION_KAS).document(id).delete().await()
                kasDao.deleteKasById(id)
                Log.d(TAG, "Kas RT terhapus dari Firestore: $id")
            } catch (e: Exception) {
                Log.w(TAG, "Gagal menghapus Kas RT di Firestore: ${e.message}")
            }
        } else {
            kasDao.deleteKasById(id)
        }
    }

    /**
     * Menyimpan atau memperbarui Aspirasi warga ke Room lokal dan menyinkronkannya ke Firestore.
     * Mengembalikan true jika berhasil tersinkron ke Firestore.
     */
    private fun readMultiSlotFirestorePhotos(
        doc: com.google.firebase.firestore.DocumentSnapshot,
        slotKeys: List<String>,
        legacyKeys: List<String>
    ): String {
        val fromSlots = slotKeys.mapNotNull { key ->
            doc.getString(key)?.trim()?.takeIf { it.isNotBlank() }
        }.flatMap { raw ->
            raw.split(",").map { com.example.util.PhotoStorageHelper.extractUrlFromFormula(it.trim()) }
        }.filter { it.isNotBlank() }.distinct().take(5)
        if (fromSlots.isNotEmpty()) {
            return fromSlots.joinToString(",")
        }
        val fromLegacy = legacyKeys.firstNotNullOfOrNull { key ->
            doc.getString(key)?.trim()?.takeIf { it.isNotBlank() }
        }.orEmpty()
        return fromLegacy.split(",")
            .map { com.example.util.PhotoStorageHelper.extractUrlFromFormula(it.trim()) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(5)
            .joinToString(",")
    }

    private fun buildAspirasiFirestoreMap(item: AspirasiItem): HashMap<String, Any> {
        val cleanTanggapan = com.example.util.AspirasiReplyCodec.stripMarker(item.tanggapanPengurus)
        val masalahSlots = item.fotoMasalahSlots
        val pengerjaanSlots = item.fotoPengerjaanSlots
        val masalahJoined = masalahSlots.joinToString(",")
        val pengerjaanJoined = pengerjaanSlots.joinToString(",")
        val statusVal = item.status.ifBlank { "Menunggu" }
        return hashMapOf(
            // Kolom A..R sesuai struktur Spreadsheet tab Aspirasi
            "ID" to item.id,
            "Tanggal" to item.tanggal,
            "Nama" to item.nama,
            "Kategori" to item.kategori,
            "Pesan" to item.pesan,
            "Foto_Masalah_1" to (masalahSlots.getOrNull(0) ?: ""),
            "Foto_Masalah_2" to (masalahSlots.getOrNull(1) ?: ""),
            "Foto_Masalah_3" to (masalahSlots.getOrNull(2) ?: ""),
            "Foto_Masalah_4" to (masalahSlots.getOrNull(3) ?: ""),
            "Foto_Masalah_5" to (masalahSlots.getOrNull(4) ?: ""),
            "Status" to statusVal,
            "Nama_Pengurus" to item.penanggapNama,
            "Tanggapan_pengurus" to cleanTanggapan,
            "Foto_pengerjaan_1" to (pengerjaanSlots.getOrNull(0) ?: ""),
            "Foto_pengerjaan_2" to (pengerjaanSlots.getOrNull(1) ?: ""),
            "Foto_pengerjaan_3" to (pengerjaanSlots.getOrNull(2) ?: ""),
            "Foto_pengerjaan_4" to (pengerjaanSlots.getOrNull(3) ?: ""),
            "Foto_pengerjaan_5" to (pengerjaanSlots.getOrNull(4) ?: ""),
            // Field camelCase & metadata tambahan aplikasi
            "id" to item.id,
            "tanggal" to item.tanggal,
            "nama" to item.nama,
            "noRumah" to item.noRumah,
            "sembunyikanNama" to item.sembunyikanNama,
            "kategori" to item.kategori,
            "pesan" to item.pesan,
            "fotoMasalah" to masalahJoined,
            "status" to statusVal,
            "namaPengurus" to item.penanggapNama,
            "tanggapanPengurus" to cleanTanggapan,
            "fotoPengerjaan" to pengerjaanJoined,
            "penanggapNama" to item.penanggapNama,
            "penanggapUsername" to item.penanggapUsername,
            "penanggapNamaAsli" to item.penanggapNamaAsli,
            "penanggapPeran" to item.penanggapPeran,
            "waktuTanggapan" to item.waktuTanggapan,
            "updatedAt" to System.currentTimeMillis()
        )
    }

    suspend fun saveAspirasi(item: AspirasiItem): Boolean = withContext(Dispatchers.IO) {
        val entity = AspirasiEntity.fromAspirasiItem(item, AspirasiEntity.SYNC_STATUS_PENDING_UPLOAD)
        aspirasiDao.insertAspirasi(entity)

        val firestore = getFirestoreInstance()
        if (firestore != null) {
            try {
                val data = buildAspirasiFirestoreMap(item)
                firestore.collection(COLLECTION_ASPIRASI).document(item.id)
                    .set(data, SetOptions.merge())
                    .await()

                aspirasiDao.insertAspirasi(entity.copy(syncStatus = AspirasiEntity.SYNC_STATUS_SYNCED))
                Log.d(TAG, "Aspirasi warga tersinkron ke Firestore: ${item.id}")
                true
            } catch (e: Exception) {
                Log.w(TAG, "Gagal sync Aspirasi ke Firestore (disimpan di Room offline): ${e.message}")
                false
            }
        } else {
            false
        }
    }

    suspend fun markAspirasiSynced(item: AspirasiItem) = withContext(Dispatchers.IO) {
        aspirasiDao.insertAspirasi(AspirasiEntity.fromAspirasiItem(item, AspirasiEntity.SYNC_STATUS_SYNCED))
    }

    /**
     * Menghapus Aspirasi warga dari Room lokal dan Firestore.
     */
    suspend fun deleteAspirasi(id: String) = withContext(Dispatchers.IO) {
        aspirasiDao.markAspirasiPendingDelete(id)

        val firestore = getFirestoreInstance()
        if (firestore != null) {
            try {
                firestore.collection(COLLECTION_ASPIRASI).document(id).delete().await()
                aspirasiDao.deleteAspirasiById(id)
                Log.d(TAG, "Aspirasi terhapus dari Firestore: $id")
            } catch (e: Exception) {
                Log.w(TAG, "Gagal menghapus Aspirasi di Firestore: ${e.message}")
            }
        } else {
            aspirasiDao.deleteAspirasiById(id)
        }
    }

    /**
     * Mengirim semua antrian yang masih berstatus PENDING ke Firestore ketika online.
     */
    suspend fun flushPendingSync() = withContext(Dispatchers.IO) {
        val firestore = getFirestoreInstance() ?: return@withContext

        // Flush pending Kas
        val pendingKas = kasDao.getPendingSyncKas()
        for (item in pendingKas) {
            try {
                if (item.syncStatus == KasEntity.SYNC_STATUS_PENDING_DELETE) {
                    firestore.collection(COLLECTION_KAS).document(item.id).delete().await()
                    kasDao.deleteKasById(item.id)
                } else {
                    val resolvedSaldo = if (item.saldo != 0L || item.jumlah == 0L) item.saldo else item.jumlah
                    val data = hashMapOf(
                        "id" to item.id,
                        "tanggal" to item.tanggal,
                        "periode_bulan" to item.periodeBulan,
                        "periodeBulan" to item.periodeBulan,
                        "kategori" to item.kategori,
                        "keterangan" to item.keterangan,
                        "masuk" to item.masuk,
                        "keluar" to item.keluar,
                        "saldo" to resolvedSaldo,
                        "jumlah" to resolvedSaldo,
                        "bukti_foto" to item.buktiFoto,
                        "buktiFoto" to item.buktiFoto,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    firestore.collection(COLLECTION_KAS).document(item.id)
                        .set(data, SetOptions.merge())
                        .await()
                    kasDao.insertKas(item.copy(syncStatus = KasEntity.SYNC_STATUS_SYNCED))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gagal flush pending Kas ID ${item.id}: ${e.message}")
            }
        }

        // Flush pending Aspirasi
        val pendingAspirasi = aspirasiDao.getPendingSyncAspirasi()
        for (item in pendingAspirasi) {
            try {
                if (item.syncStatus == AspirasiEntity.SYNC_STATUS_PENDING_DELETE) {
                    firestore.collection(COLLECTION_ASPIRASI).document(item.id).delete().await()
                    aspirasiDao.deleteAspirasiById(item.id)
                } else {
                    val data = buildAspirasiFirestoreMap(item.toAspirasiItem())
                    firestore.collection(COLLECTION_ASPIRASI).document(item.id)
                        .set(data, SetOptions.merge())
                        .await()
                    aspirasiDao.insertAspirasi(item.copy(syncStatus = AspirasiEntity.SYNC_STATUS_SYNCED))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gagal flush pending Aspirasi ID ${item.id}: ${e.message}")
            }
        }
    }
}
