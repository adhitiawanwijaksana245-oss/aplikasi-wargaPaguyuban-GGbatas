package com.example.data.sync

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.AspirasiEntity
import com.example.data.local.KasEntity
import com.example.data.model.AspirasiItem
import com.example.data.model.KasItem
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

    companion object {
        private const val TAG = "RoomFirestoreSync"
        const val COLLECTION_KAS = "kas_rt"
        const val COLLECTION_ASPIRASI = "aspirasi"
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
                                    val id = doc.id
                                    val tanggal = doc.getString("tanggal") ?: ""
                                    val nama = doc.getString("nama") ?: ""
                                    val kategori = doc.getString("kategori") ?: "Masukan/Saran"
                                    val pesan = doc.getString("pesan") ?: ""
                                    val fotoMasalah = doc.getString("fotoMasalah") ?: ""
                                    val status = doc.getString("status") ?: "Menunggu"
                                    val tanggapanPengurus = doc.getString("tanggapanPengurus") ?: ""
                                    val fotoPengerjaan = doc.getString("fotoPengerjaan") ?: ""

                                    AspirasiEntity(
                                        id = id,
                                        tanggal = tanggal,
                                        nama = nama,
                                        kategori = kategori,
                                        pesan = pesan,
                                        fotoMasalah = fotoMasalah,
                                        status = status,
                                        tanggapanPengurus = tanggapanPengurus,
                                        fotoPengerjaan = fotoPengerjaan,
                                        syncStatus = AspirasiEntity.SYNC_STATUS_SYNCED
                                    )
                                }

                                if (remoteAspirasiEntities.isNotEmpty()) {
                                    aspirasiDao.insertAspirasiList(remoteAspirasiEntities)
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
     */
    suspend fun saveAspirasi(item: AspirasiItem) = withContext(Dispatchers.IO) {
        val entity = AspirasiEntity.fromAspirasiItem(item, AspirasiEntity.SYNC_STATUS_PENDING_UPLOAD)
        aspirasiDao.insertAspirasi(entity)

        val firestore = getFirestoreInstance()
        if (firestore != null) {
            try {
                val data = hashMapOf(
                    "id" to item.id,
                    "tanggal" to item.tanggal,
                    "nama" to item.nama,
                    "kategori" to item.kategori,
                    "pesan" to item.pesan,
                    "fotoMasalah" to item.fotoMasalah,
                    "status" to item.status,
                    "tanggapanPengurus" to item.tanggapanPengurus,
                    "fotoPengerjaan" to item.fotoPengerjaan,
                    "updatedAt" to System.currentTimeMillis()
                )
                firestore.collection(COLLECTION_ASPIRASI).document(item.id)
                    .set(data, SetOptions.merge())
                    .await()

                aspirasiDao.insertAspirasi(entity.copy(syncStatus = AspirasiEntity.SYNC_STATUS_SYNCED))
                Log.d(TAG, "Aspirasi warga tersinkron ke Firestore: ${item.id}")
            } catch (e: Exception) {
                Log.w(TAG, "Gagal sync Aspirasi ke Firestore (disimpan di Room offline): ${e.message}")
            }
        }
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
                    val data = hashMapOf(
                        "id" to item.id,
                        "tanggal" to item.tanggal,
                        "nama" to item.nama,
                        "kategori" to item.kategori,
                        "pesan" to item.pesan,
                        "fotoMasalah" to item.fotoMasalah,
                        "status" to item.status,
                        "tanggapanPengurus" to item.tanggapanPengurus,
                        "fotoPengerjaan" to item.fotoPengerjaan,
                        "updatedAt" to System.currentTimeMillis()
                    )
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
