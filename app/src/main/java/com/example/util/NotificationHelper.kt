package com.example.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

data class AppNotification(
    val id: String = "NOTIF-${System.currentTimeMillis()}",
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "INFO", // "ASPIRASI", "KAS", "PENGUMUMAN", "UNDANGAN", "PENGINGAT", "INFO"
    val isRead: Boolean = false
)

object NotificationHelper {
    const val CHANNEL_BERITA = "channel_berita_desa_v3"
    const val CHANNEL_ASPIRASI = "channel_aspirasi_warga_v3"
    const val CHANNEL_KAS = "channel_kas_rt_v3"
    const val CHANNEL_PENGINGAT = "channel_pengingat_acara_v3"

    const val EXTRA_STOP_RINGTONE = "extra_stop_ringtone"
    const val EXTRA_TARGET_TAB = "extra_target_tab"

    private val legacyChannels = listOf(
        "channel_berita_desa",
        "channel_aspirasi_warga",
        "channel_kas_rt",
        "channel_pengingat_acara",
        "channel_berita_desa_v2",
        "channel_aspirasi_warga_v2",
        "channel_kas_rt_v2",
        "channel_pengingat_acara_v2"
    )

    private val activeReminderNotificationIds = Collections.synchronizedSet(mutableSetOf<Int>())
    private val notificationIdSeq = AtomicInteger((System.currentTimeMillis() and 0xFFFF).toInt())

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    /**
     * Mengambil URI suara notifikasi bawaan dari pengaturan masing-masing HP pengguna.
     */
    fun getDefaultNotificationSoundUri(): Uri {
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: Settings.System.DEFAULT_NOTIFICATION_URI
    }

    fun getConfiguredNotificationSoundUri(context: Context): Uri? {
        return NotificationToneManager.resolveSoundUri(context)
    }

    fun resolveActiveChannelId(context: Context, baseChannelId: String): String {
        val pref = NotificationToneManager.getPreference(context)
        return NotificationToneManager.buildChannelId(baseChannelId, pref.channelRevision)
    }

    fun recreateNotificationChannelsForToneChange(context: Context) {
        initNotificationChannels(context)
    }

    fun initNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val tonePref = NotificationToneManager.getPreference(context)

            // Hapus channel versi lama & revisi sebelumnya agar perubahan nada langsung aktif di perangkat ini
            val allBaseChannels = listOf(CHANNEL_BERITA, CHANNEL_ASPIRASI, CHANNEL_KAS, CHANNEL_PENGINGAT)
            val staleChannelIds = legacyChannels + allBaseChannels.flatMap { baseId ->
                NotificationToneManager.previousChannelIds(baseId, tonePref.channelRevision)
            }
            staleChannelIds.forEach { oldId ->
                try {
                    manager.deleteNotificationChannel(oldId)
                } catch (_: Exception) {
                }
            }

            val soundUri = NotificationToneManager.resolveSoundUri(context, tonePref)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            fun NotificationChannel.applyTonePreference() {
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                if (soundUri != null) {
                    setSound(soundUri, audioAttributes)
                } else {
                    setSound(null, null)
                }
            }

            val channelBerita = NotificationChannel(
                resolveActiveChannelId(context, CHANNEL_BERITA),
                "Pengumuman & Undangan Warga",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi pengumuman dan undangan kegiatan RT 005 Mulyaharja"
                applyTonePreference()
            }

            val channelAspirasi = NotificationChannel(
                resolveActiveChannelId(context, CHANNEL_ASPIRASI),
                "Masukan, Saran & Kritik Warga",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Pemberitahuan masukan, saran, kritik baru, dan tanggapan pengurus RT"
                applyTonePreference()
            }

            val channelKas = NotificationChannel(
                resolveActiveChannelId(context, CHANNEL_KAS),
                "Kas & Keuangan RT",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Laporan transaksi kas dan transparansi keuangan warga"
                applyTonePreference()
            }

            val channelPengingat = NotificationChannel(
                resolveActiveChannelId(context, CHANNEL_PENGINGAT),
                "Mode Pengingat Rapat & Gotong Royong",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Pengingat terjadwal dari pengurus RT untuk acara rapat atau gotong royong warga"
                applyTonePreference()
            }

            manager.createNotificationChannel(channelBerita)
            manager.createNotificationChannel(channelAspirasi)
            manager.createNotificationChannel(channelKas)
            manager.createNotificationChannel(channelPengingat)
        }
    }

    fun showPushNotification(
        context: Context,
        title: String,
        message: String,
        channelId: String = CHANNEL_BERITA,
        type: String = "INFO",
        silent: Boolean = false,
        targetTab: Int? = null
    ) {
        initNotificationChannels(context)

        // 1. Tambahkan ke riwayat pusat notifikasi dalam aplikasi
        val newNotif = AppNotification(
            id = "NOTIF-${System.currentTimeMillis()}-${notificationIdSeq.incrementAndGet()}",
            title = title,
            message = message,
            type = type
        )
        val current = _notifications.value.toMutableList()
        current.add(0, newNotif)
        _notifications.value = current.take(100)

        // 2. Tampilkan notifikasi sistem Android (muncul di layar kunci / standby seperti WhatsApp)
        try {
            val notificationId = notificationIdSeq.incrementAndGet()
            val resolvedTab = targetTab ?: when (type) {
                "ASPIRASI" -> 4
                "PENGUMUMAN", "UNDANGAN", "PENGINGAT" -> 3
                "KAS" -> 1
                else -> 0
            }
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_STOP_RINGTONE, true)
                putExtra(EXTRA_TARGET_TAB, resolvedTab)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val stopIntent = Intent(context, StopRingtoneBroadcastReceiver::class.java)
            val stopPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 100_000,
                stopIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val category = if (type == "PENGINGAT") {
                NotificationCompat.CATEGORY_EVENT
            } else {
                NotificationCompat.CATEGORY_MESSAGE
            }

            val tonePref = NotificationToneManager.getPreference(context)
            val activeChannelId = resolveActiveChannelId(context, channelId)
            val configuredSoundUri = NotificationToneManager.resolveSoundUri(context, tonePref)
            val isSilentVibrate = !silent && tonePref.option == NotificationToneOption.SILENT_VIBRATE

            val builder = NotificationCompat.Builder(context, activeChannelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground_img)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(category)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDeleteIntent(stopPendingIntent)
                .setSilent(silent)

            if (!silent) {
                if (isSilentVibrate) {
                    builder.setSound(null)
                    builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE)
                } else {
                    builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        builder.setSound(configuredSoundUri ?: getDefaultNotificationSoundUri())
                    }
                }
            }

            if (type == "PENGINGAT") {
                activeReminderNotificationIds.add(notificationId)
            }

            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Izin POST_NOTIFICATIONS mungkin belum diizinkan pengguna pada Android 13+
        } catch (_: Exception) {
        }
    }

    fun silenceActiveRingtone(context: Context) {
        ReminderRingtonePlayer.stop()
        val manager = NotificationManagerCompat.from(context)
        val ids = synchronized(activeReminderNotificationIds) {
            val copy = activeReminderNotificationIds.toList()
            activeReminderNotificationIds.clear()
            copy
        }
        ids.forEach { id ->
            try {
                manager.cancel(id)
            } catch (_: Exception) {
            }
        }
    }

    fun clearNotifications() {
        _notifications.value = emptyList()
    }

    fun markAllAsRead() {
        _notifications.value = _notifications.value.map { it.copy(isRead = true) }
    }
}
