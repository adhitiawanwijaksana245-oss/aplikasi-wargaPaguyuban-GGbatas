package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.model.UndanganItem
import com.example.data.source.GoogleAppsScriptTemplate
import com.example.data.source.GoogleSheetsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Worker latar belakang yang tetap berjalan meski aplikasi ditutup atau HP dalam mode standby,
 * untuk mengambil data terbaru dari Google Sheets dan memicu notifikasi sistem Android
 * bila ada Aspirasi (Masukan/Saran/Kritik), Pengumuman, Undangan, atau Mode Pengingat baru.
 */
class BackgroundSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sheetUrl = prefs.getString("sheet_web_app_url", "")
            ?.trim()
            ?.takeIf { it.contains("script.google.com/macros/s/") }
            ?: GoogleAppsScriptTemplate.EMBEDDED_WEB_APP_URL

        if (sheetUrl.isBlank()) {
            return@withContext Result.success()
        }

        return@withContext try {
            val sheetsService = GoogleSheetsService()
            val response = sheetsService.fetchAllData(sheetUrl)

            val cleanUndangan = response.undangan
                ?.filter { it.judul.isNotBlank() }
                .orEmpty()
            val cleanAspirasi = response.aspirasi
                ?.filter { it.nama.isNotBlank() || it.pesan.isNotBlank() }
                .orEmpty()

            if (cleanUndangan.isNotEmpty()) {
                saveUndanganCache(prefs, cleanUndangan)
            }
            if (cleanAspirasi.isNotEmpty()) {
                saveAspirasiCache(prefs, cleanAspirasi)
            }

            RemoteNotificationTracker.processRemoteUpdates(
                context = applicationContext,
                undanganList = cleanUndangan,
                aspirasiList = cleanAspirasi
            )

            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Sinkronisasi latar belakang tertunda: ${e.message}")
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private fun saveUndanganCache(
        prefs: android.content.SharedPreferences,
        items: List<UndanganItem>
    ) {
        try {
            val arr = JSONArray()
            items.forEach { item ->
                arr.put(JSONObject().apply {
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
                    put("foto", item.foto)
                })
            }
            prefs.edit().putString("cache_undangan", arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    private fun saveAspirasiCache(
        prefs: android.content.SharedPreferences,
        items: List<com.example.data.model.AspirasiItem>
    ) {
        try {
            val arr = JSONArray()
            items.forEach { item ->
                arr.put(JSONObject().apply {
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
                    put("penanggapNama", item.penanggapNama)
                    put("penanggapUsername", item.penanggapUsername)
                    put("penanggapNamaAsli", item.penanggapNamaAsli)
                    put("penanggapPeran", item.penanggapPeran)
                    put("waktuTanggapan", item.waktuTanggapan)
                })
            }
            prefs.edit().putString("cache_aspirasi", arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "BackgroundSyncWorker"
        const val PREFS_NAME = "aplikasi_warga_prefs"
    }
}

object BackgroundSyncScheduler {
    private const val PERIODIC_WORK_NAME = "warga_background_sync_periodic"
    private const val DELAYED_WORK_NAME = "warga_background_sync_delayed"
    private const val IMMEDIATE_WORK_NAME = "warga_background_sync_immediate"

    private fun networkConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }

    fun schedulePeriodicSync(context: Context) {
        try {
            val request = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES
            )
                .setConstraints(networkConstraints())
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        } catch (_: Exception) {
        }
    }

    fun scheduleDelayedCheck(context: Context, delayMinutes: Long = 2L) {
        try {
            val request = OneTimeWorkRequestBuilder<BackgroundSyncWorker>()
                .setInitialDelay(delayMinutes.coerceAtLeast(1L), TimeUnit.MINUTES)
                .setConstraints(networkConstraints())
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                DELAYED_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        } catch (_: Exception) {
        }
    }

    fun triggerImmediateSync(context: Context) {
        try {
            val request = OneTimeWorkRequestBuilder<BackgroundSyncWorker>()
                .setConstraints(networkConstraints())
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        } catch (_: Exception) {
        }
    }
}

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        NotificationHelper.initNotificationChannels(context)
        rearmCachedReminderAlarms(context)
        BackgroundSyncScheduler.schedulePeriodicSync(context)
        BackgroundSyncScheduler.triggerImmediateSync(context)
    }

    private fun rearmCachedReminderAlarms(context: Context) {
        try {
            val prefs = context.getSharedPreferences(BackgroundSyncWorker.PREFS_NAME, Context.MODE_PRIVATE)
            val undJson = prefs.getString("cache_undangan", null) ?: return
            val arr = JSONArray(undJson)
            val list = mutableListOf<UndanganItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val rawKet = obj.optString("keterangan")
                val marker = ReminderCodec.parseMarker(rawKet)
                val pMenit = obj.optInt("pengingatMenit", marker?.minutes ?: 0)
                val pTarget = obj.optLong("pengingatTargetMillis", marker?.targetMillis ?: 0L)
                val pActivated = obj.optLong("pengingatActivatedMillis", marker?.activatedMillis ?: 0L)
                val pAktif = obj.optBoolean("pengingatAktif", pMenit > 0 && pTarget > 0L)
                list.add(
                    UndanganItem(
                        id = obj.optString("id"),
                        judul = obj.optString("judul"),
                        tanggal = obj.optString("tanggal"),
                        waktu = obj.optString("waktu"),
                        lokasi = obj.optString("lokasi"),
                        keterangan = rawKet,
                        tipe = obj.optString(
                            "tipe",
                            if (obj.optString("judul").startsWith("[Pengumuman]", ignoreCase = true)) {
                                "Pengumuman"
                            } else {
                                "Undangan"
                            }
                        ),
                        pengingatMenit = pMenit,
                        pengingatAktif = pAktif,
                        pengingatTargetMillis = pTarget,
                        pengingatActivatedMillis = pActivated,
                        foto = obj.optString("foto", "")
                    )
                )
            }
            if (list.isNotEmpty()) {
                ReminderScheduler.syncAlarmsAndCheckDue(context, list)
            }
        } catch (_: Exception) {
        }
    }
}
