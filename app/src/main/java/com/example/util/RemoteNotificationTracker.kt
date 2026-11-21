package com.example.util

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.AspirasiItem
import com.example.data.model.UndanganItem

/**
 * Mendeteksi item baru dari Spreadsheet (Masukan/Saran, Kritik, Tanggapan Pengurus,
 * Pengumuman, Undangan, dan Mode Pengingat) baik saat aplikasi dibuka maupun saat
 * sinkronisasi latar belakang (standby), lalu memicu notifikasi sistem Android
 * tanpa mengirim notifikasi ganda.
 */
object RemoteNotificationTracker {
    private const val PREFS_NAME = "aplikasi_warga_notif_tracker"
    private const val KEY_BASELINE_INITIALIZED = "baseline_initialized"
    private const val KEY_SEEN_ASPIRASI_IDS = "seen_aspirasi_ids"
    private const val KEY_SEEN_ASPIRASI_RESPONSES = "seen_aspirasi_responses"
    private const val KEY_SEEN_UNDANGAN_IDS = "seen_undangan_ids"
    private const val KEY_SEEN_REMINDER_KEYS = "seen_reminder_keys"
    private const val MAX_TRACKED_KEYS = 300

    private fun prefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun trimSet(set: Set<String>): Set<String> {
        return if (set.size > MAX_TRACKED_KEYS) {
            set.toList().takeLast(MAX_TRACKED_KEYS).toSet()
        } else {
            set
        }
    }

    private fun buildResponseKey(item: AspirasiItem): String {
        return "${item.id}|${item.status.trim()}|${item.tanggapanPengurus.trim()}"
    }

    private fun buildReminderKey(item: UndanganItem): String {
        return "${item.id}_${item.effectiveReminderTargetMillis}"
    }

    private fun formatAspirasiSender(item: AspirasiItem): String {
        return if (item.sembunyikanNama) {
            if (item.noRumah.isNotBlank()) "Warga (${item.noRumah})" else "Warga RT 005"
        } else {
            if (item.noRumah.isNotBlank()) "${item.nama} (${item.noRumah})" else item.nama
        }
    }

    private fun buildUndanganMessage(item: UndanganItem): String {
        if (item.isPengumuman) {
            val detail = item.cleanKeterangan
            val meta = listOf(item.tanggal, item.lokasi)
                .filter { it.isNotBlank() && it != "-" }
                .joinToString(" • ")
            return when {
                detail.isNotBlank() && meta.isNotBlank() -> "$detail ($meta)"
                detail.isNotBlank() -> detail
                meta.isNotBlank() -> meta
                else -> "Pengumuman terbaru dari Pengurus RT 005."
            }
        } else {
            val jadwal = listOf(item.tanggal, item.waktu)
                .filter { it.isNotBlank() && it != "-" }
                .joinToString(" ")
            val lokasiPart = if (item.lokasi.isNotBlank() && item.lokasi != "-") " di ${item.lokasi}" else ""
            val header = if (jadwal.isNotBlank()) "$jadwal$lokasiPart" else lokasiPart.trim()
            val detail = item.cleanKeterangan
            return when {
                header.isNotBlank() && detail.isNotBlank() -> "$header — $detail"
                header.isNotBlank() -> header
                detail.isNotBlank() -> detail
                else -> "Undangan kegiatan warga RT 005."
            }
        }
    }

    @Synchronized
    fun isBaselineInitialized(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_BASELINE_INITIALIZED, false)
    }

    @Synchronized
    fun markLocalUndanganHandled(context: Context, item: UndanganItem) {
        if (item.id.isBlank()) return
        val p = prefs(context)
        val seenUnd = (p.getStringSet(KEY_SEEN_UNDANGAN_IDS, emptySet()) ?: emptySet()).toMutableSet()
        seenUnd.add(item.id)

        val seenRem = (p.getStringSet(KEY_SEEN_REMINDER_KEYS, emptySet()) ?: emptySet()).toMutableSet()
        if (item.isReminderActive) {
            seenRem.add(buildReminderKey(item))
        }

        p.edit()
            .putStringSet(KEY_SEEN_UNDANGAN_IDS, trimSet(seenUnd))
            .putStringSet(KEY_SEEN_REMINDER_KEYS, trimSet(seenRem))
            .commit()
    }

    @Synchronized
    fun markLocalAspirasiSubmitted(context: Context, item: AspirasiItem) {
        if (item.id.isBlank()) return
        val p = prefs(context)
        val seenAsp = (p.getStringSet(KEY_SEEN_ASPIRASI_IDS, emptySet()) ?: emptySet()).toMutableSet()
        seenAsp.add(item.id)
        p.edit()
            .putStringSet(KEY_SEEN_ASPIRASI_IDS, trimSet(seenAsp))
            .commit()
    }

    @Synchronized
    fun markLocalAspirasiResponded(context: Context, item: AspirasiItem) {
        if (item.id.isBlank()) return
        val p = prefs(context)
        val seenAsp = (p.getStringSet(KEY_SEEN_ASPIRASI_IDS, emptySet()) ?: emptySet()).toMutableSet()
        seenAsp.add(item.id)

        val seenResp = (p.getStringSet(KEY_SEEN_ASPIRASI_RESPONSES, emptySet()) ?: emptySet()).toMutableSet()
        if (item.tanggapanPengurus.isNotBlank()) {
            seenResp.add(buildResponseKey(item))
        }

        p.edit()
            .putStringSet(KEY_SEEN_ASPIRASI_IDS, trimSet(seenAsp))
            .putStringSet(KEY_SEEN_ASPIRASI_RESPONSES, trimSet(seenResp))
            .commit()
    }

    /**
     * Membandingkan data remote terbaru dari Spreadsheet dengan riwayat yang sudah pernah
     * dinotifikasikan pada perangkat ini.
     *
     * @return jumlah notifikasi baru yang dikirim pada siklus sinkronisasi ini.
     */
    @Synchronized
    fun processRemoteUpdates(
        context: Context,
        undanganList: List<UndanganItem>,
        aspirasiList: List<AspirasiItem>,
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        NotificationHelper.initNotificationChannels(context)
        val p = prefs(context)

        val validUndangan = undanganList.filter { it.id.isNotBlank() && it.judul.isNotBlank() }
        val validAspirasi = aspirasiList.filter { it.id.isNotBlank() && (it.nama.isNotBlank() || it.pesan.isNotBlank()) }

        if (!p.getBoolean(KEY_BASELINE_INITIALIZED, false)) {
            val baseAspIds = validAspirasi.map { it.id }.toSet()
            val baseRespKeys = validAspirasi
                .filter { it.tanggapanPengurus.isNotBlank() }
                .map { buildResponseKey(it) }
                .toSet()
            val baseUndIds = validUndangan.map { it.id }.toSet()
            val baseRemKeys = validUndangan
                .filter { it.isReminderActive }
                .map { buildReminderKey(it) }
                .toSet()

            p.edit()
                .putBoolean(KEY_BASELINE_INITIALIZED, true)
                .putStringSet(KEY_SEEN_ASPIRASI_IDS, trimSet(baseAspIds))
                .putStringSet(KEY_SEEN_ASPIRASI_RESPONSES, trimSet(baseRespKeys))
                .putStringSet(KEY_SEEN_UNDANGAN_IDS, trimSet(baseUndIds))
                .putStringSet(KEY_SEEN_REMINDER_KEYS, trimSet(baseRemKeys))
                .commit()

            // Tetap daftarkan alarm Mode Pengingat aktif yang sudah ada di Spreadsheet
            ReminderScheduler.syncAlarmsAndCheckDue(context, validUndangan, nowMillis)
            return 0
        }

        val seenAspIds = (p.getStringSet(KEY_SEEN_ASPIRASI_IDS, emptySet()) ?: emptySet()).toMutableSet()
        val seenRespKeys = (p.getStringSet(KEY_SEEN_ASPIRASI_RESPONSES, emptySet()) ?: emptySet()).toMutableSet()
        val seenUndIds = (p.getStringSet(KEY_SEEN_UNDANGAN_IDS, emptySet()) ?: emptySet()).toMutableSet()
        val seenRemKeys = (p.getStringSet(KEY_SEEN_REMINDER_KEYS, emptySet()) ?: emptySet()).toMutableSet()

        var notifiedCount = 0

        // 1. Cek Masukan / Saran / Kritik baru & Tanggapan Pengurus
        validAspirasi.forEach { item ->
            val sender = formatAspirasiSender(item)
            if (item.id !in seenAspIds) {
                seenAspIds.add(item.id)
                if (item.tanggapanPengurus.isNotBlank()) {
                    seenRespKeys.add(buildResponseKey(item))
                }
                val kategoriLabel = item.kategori.trim().ifBlank { "Masukan/Saran" }
                NotificationHelper.showPushNotification(
                    context = context,
                    title = "💬 $kategoriLabel Baru dari Warga",
                    message = "$sender: ${item.pesan}",
                    channelId = NotificationHelper.CHANNEL_ASPIRASI,
                    type = "ASPIRASI",
                    silent = false,
                    targetTab = 4
                )
                notifiedCount++
            } else if (item.tanggapanPengurus.isNotBlank()) {
                val respKey = buildResponseKey(item)
                if (respKey !in seenRespKeys) {
                    seenRespKeys.add(respKey)
                    NotificationHelper.showPushNotification(
                        context = context,
                        title = "✅ Tanggapan Pengurus RT (${item.status})",
                        message = "Untuk $sender: ${item.tanggapanPengurus}",
                        channelId = NotificationHelper.CHANNEL_ASPIRASI,
                        type = "ASPIRASI",
                        silent = false,
                        targetTab = 4
                    )
                    notifiedCount++
                }
            }
        }

        // 2. Cek Pengumuman & Undangan baru + Mode Pengingat aktif
        validUndangan.forEach { item ->
            if (item.id !in seenUndIds) {
                seenUndIds.add(item.id)
                if (item.isPengumuman) {
                    NotificationHelper.showPushNotification(
                        context = context,
                        title = "📢 Pengumuman RT 005: ${item.cleanJudul}",
                        message = buildUndanganMessage(item),
                        channelId = NotificationHelper.CHANNEL_BERITA,
                        type = "PENGUMUMAN",
                        silent = false,
                        targetTab = 3
                    )
                } else {
                    NotificationHelper.showPushNotification(
                        context = context,
                        title = "🗓️ Undangan RT 005: ${item.cleanJudul}",
                        message = buildUndanganMessage(item),
                        channelId = NotificationHelper.CHANNEL_BERITA,
                        type = "UNDANGAN",
                        silent = false,
                        targetTab = 3
                    )
                }
                notifiedCount++
            }

            if (item.isReminderActive) {
                val remKey = buildReminderKey(item)
                if (remKey !in seenRemKeys) {
                    seenRemKeys.add(remKey)
                    val targetMillis = item.effectiveReminderTargetMillis
                    // Jika waktu target masih di masa depan, kabari warga bahwa Mode Pengingat telah diaktifkan.
                    // Jika sudah tepat waktu, syncAlarmsAndCheckDue di bawah langsung membunyikan notifikasi pengingat utama.
                    if (targetMillis > nowMillis) {
                        val clockStr = ReminderCalculator.formatClockTime(targetMillis)
                        val locPart = if (item.lokasi.isNotBlank() && item.lokasi != "-") " di ${item.lokasi}" else ""
                        NotificationHelper.showPushNotification(
                            context = context,
                            title = "⏰ Mode Pengingat Aktif: ${item.cleanJudul}",
                            message = "Pengingat diatur ${item.effectiveReminderMenit} menit ($clockStr)$locPart.",
                            channelId = NotificationHelper.CHANNEL_PENGINGAT,
                            type = "PENGINGAT",
                            silent = false,
                            targetTab = 3
                        )
                        notifiedCount++
                    }
                }
            }
        }

        p.edit()
            .putStringSet(KEY_SEEN_ASPIRASI_IDS, trimSet(seenAspIds))
            .putStringSet(KEY_SEEN_ASPIRASI_RESPONSES, trimSet(seenRespKeys))
            .putStringSet(KEY_SEEN_UNDANGAN_IDS, trimSet(seenUndIds))
            .putStringSet(KEY_SEEN_REMINDER_KEYS, trimSet(seenRemKeys))
            .commit()

        // Jadwalkan & picu alarm pengingat standby melalui AlarmManager
        notifiedCount += ReminderScheduler.syncAlarmsAndCheckDueCount(context, validUndangan, nowMillis)
        return notifiedCount
    }
}
