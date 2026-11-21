package com.example

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AspirasiItem
import com.example.data.model.UndanganItem
import com.example.util.NotificationHelper
import com.example.util.RemoteNotificationTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RemoteNotificationTrackerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("aplikasi_warga_notif_tracker", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("aplikasi_warga_reminder_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
        NotificationHelper.clearNotifications()
    }

    @Test
    fun firstSync_seedsBaselineSilently_andSubsequentSyncNotifiesAllNewRemoteTypes() {
        val now = 1_750_000_000_000L
        val existingUndangan = listOf(
            UndanganItem(id = "UND-01", judul = "Rapat Lama", tanggal = "2026-11-01", tipe = "Undangan")
        )
        val existingAspirasi = listOf(
            AspirasiItem(id = "ASP-01", nama = "Budi", kategori = "Masukan/Saran", pesan = "Lampu pos")
        )

        // Sinkronisasi pertama: hanya mencatat baseline tanpa membanjiri notifikasi
        val initialCount = RemoteNotificationTracker.processRemoteUpdates(
            context = context,
            undanganList = existingUndangan,
            aspirasiList = existingAspirasi,
            nowMillis = now
        )
        assertEquals(0, initialCount)
        assertTrue(RemoteNotificationTracker.isBaselineInitialized(context))
        assertTrue(NotificationHelper.notifications.value.isEmpty())

        // Sinkronisasi berikutnya: ada Aspirasi kritik, Tanggapan pengurus, Pengumuman, Undangan, & Mode Pengingat
        val updatedUndangan = existingUndangan + listOf(
            UndanganItem(
                id = "PGM-02",
                judul = "Kerja Bakti Minggu",
                tanggal = "2026-11-22",
                lokasi = "Lapangan RT 005",
                keterangan = "Bawa alat kebersihan",
                tipe = "Pengumuman"
            ),
            UndanganItem(
                id = "UND-02",
                judul = "Rapat Bulanan RT",
                tanggal = "2026-11-25",
                waktu = "19:30 WIB",
                lokasi = "Balai Warga",
                keterangan = "Bahas keamanan",
                tipe = "Undangan",
                pengingatMenit = 30,
                pengingatAktif = true,
                pengingatTargetMillis = now + 1_800_000L,
                pengingatActivatedMillis = now
            )
        )
        val updatedAspirasi = listOf(
            AspirasiItem(
                id = "ASP-01",
                nama = "Budi",
                kategori = "Masukan/Saran",
                pesan = "Lampu pos",
                status = "Diproses",
                tanggapanPengurus = "Teknisi segera datang"
            ),
            AspirasiItem(
                id = "ASP-02",
                nama = "Siti",
                noRumah = "Blok A2",
                sembunyikanNama = true,
                kategori = "Kritik",
                pesan = "Saluran air mampet"
            )
        )

        val notifiedCount = RemoteNotificationTracker.processRemoteUpdates(
            context = context,
            undanganList = updatedUndangan,
            aspirasiList = updatedAspirasi,
            nowMillis = now
        )

        // Expected: ASP-02 baru (1) + tanggapan ASP-01 (1) + PGM-02 baru (1) + UND-02 baru (1) + reminder UND-02 aktif (1) = 5
        assertEquals(5, notifiedCount)
        val history = NotificationHelper.notifications.value
        assertEquals(5, history.size)
        assertTrue(history.any { it.title.contains("Kritik Baru") && it.message.contains("Warga (Blok A2)") && !it.message.contains("Siti") })
        assertTrue(history.any { it.title.contains("Tanggapan Pengurus RT") && it.message.contains("Teknisi segera datang") })
        assertTrue(history.any { it.title.contains("Pengumuman RT 005") && it.message.contains("Kerja Bakti") || it.title.contains("Kerja Bakti") })
        assertTrue(history.any { it.title.contains("Undangan RT 005") && it.title.contains("Rapat Bulanan RT") })
        assertTrue(history.any { it.title.contains("Mode Pengingat Aktif") && it.title.contains("Rapat Bulanan RT") })

        // Sinkronisasi ulang dengan data sama persis: tidak boleh kirim notifikasi ganda
        val repeatCount = RemoteNotificationTracker.processRemoteUpdates(
            context = context,
            undanganList = updatedUndangan,
            aspirasiList = updatedAspirasi,
            nowMillis = now + 5_000L
        )
        assertEquals(0, repeatCount)
        assertEquals(5, NotificationHelper.notifications.value.size)
    }

    @Test
    fun localSubmissionMarkedHandled_doesNotDoubleNotifyOnNextSync() {
        val now = 1_750_000_000_000L
        RemoteNotificationTracker.processRemoteUpdates(context, emptyList(), emptyList(), now)

        val localAspirasi = AspirasiItem(
            id = "ASP-LOCAL-1",
            nama = "Andi",
            kategori = "Masukan/Saran",
            pesan = "Perlu cermin cembung di tikungan"
        )
        val localPengumuman = UndanganItem(
            id = "PGM-LOCAL-1",
            judul = "Info Iuran",
            tanggal = "2026-11-20",
            tipe = "Pengumuman"
        )
        RemoteNotificationTracker.markLocalAspirasiSubmitted(context, localAspirasi)
        RemoteNotificationTracker.markLocalUndanganHandled(context, localPengumuman)

        val countAfterSync = RemoteNotificationTracker.processRemoteUpdates(
            context = context,
            undanganList = listOf(localPengumuman),
            aspirasiList = listOf(localAspirasi),
            nowMillis = now + 1000L
        )
        assertEquals(0, countAfterSync)
    }

    @Test
    fun notificationChannels_useDeviceDefaultNotificationSoundAndUsage() {
        NotificationHelper.initNotificationChannels(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val aspirasiChannel = manager.getNotificationChannel(NotificationHelper.CHANNEL_ASPIRASI)
        val beritaChannel = manager.getNotificationChannel(NotificationHelper.CHANNEL_BERITA)
        val pengingatChannel = manager.getNotificationChannel(NotificationHelper.CHANNEL_PENGINGAT)

        assertNotNull(aspirasiChannel)
        assertNotNull(beritaChannel)
        assertNotNull(pengingatChannel)

        val expectedDefaultUri = NotificationHelper.getDefaultNotificationSoundUri()
        assertEquals(expectedDefaultUri, aspirasiChannel?.sound)
        assertEquals(expectedDefaultUri, beritaChannel?.sound)
        assertEquals(expectedDefaultUri, pengingatChannel?.sound)
        assertFalse(aspirasiChannel?.sound.toString().contains("nada_dering_pengingat"))
        assertEquals(AudioAttributes.USAGE_NOTIFICATION, pengingatChannel?.audioAttributes?.usage)
    }
}
