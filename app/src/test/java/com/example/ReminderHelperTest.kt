package com.example

import com.example.data.model.ReminderMode
import com.example.data.model.UndanganItem
import com.example.util.ReminderCalculator
import com.example.util.ReminderCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class ReminderHelperTest {

    @Test
    fun reminderCodec_roundTripPreservesCleanTextAndMetadata() {
        val originalText = "Wajib hadir membawa alat kebersihan masing-masing."
        val targetMillis = 1794650000000L
        val activatedMillis = 1794649100000L

        val encoded = ReminderCodec.encodeIntoKeterangan(
            rawKeterangan = originalText,
            minutes = 15,
            targetMillis = targetMillis,
            activatedMillis = activatedMillis
        )

        assertTrue(encoded.contains("[[PENGINGAT:15:$targetMillis:$activatedMillis]]"))
        assertEquals(originalText, ReminderCodec.stripMarker(encoded))

        val parsed = ReminderCodec.parseMarker(encoded)
        assertNotNull(parsed)
        assertEquals(15, parsed?.minutes)
        assertEquals(targetMillis, parsed?.targetMillis)
        assertEquals(activatedMillis, parsed?.activatedMillis)
    }

    @Test
    fun undanganItem_readsReminderFromMarkerFallbackAndHidesMarkerInCleanKeterangan() {
        val targetMillis = 1794650000000L
        val encoded = ReminderCodec.encodeIntoKeterangan(
            rawKeterangan = "Membahas iuran keamanan",
            minutes = 30,
            targetMillis = targetMillis,
            activatedMillis = 1794648200000L
        )
        val item = UndanganItem(
            id = "UND-01",
            judul = "Rapat Bulanan RT 005",
            tanggal = "2026-11-15",
            waktu = "19:30 WIB",
            lokasi = "Pos Ronda",
            keterangan = encoded,
            tipe = "Undangan"
        )

        assertTrue(item.isReminderActive)
        assertEquals(30, item.effectiveReminderMenit)
        assertEquals(targetMillis, item.effectiveReminderTargetMillis)
        assertEquals("Membahas iuran keamanan", item.cleanKeterangan)
    }

    @Test
    fun pengumumanItem_neverActivatesEventReminder() {
        val encoded = ReminderCodec.encodeIntoKeterangan("Info rutin", 10, 1794650000000L, 1794649400000L)
        val item = UndanganItem(
            id = "PGM-01",
            judul = "[Pengumuman] Info Kas",
            keterangan = encoded,
            tipe = "Pengumuman"
        )
        assertFalse(item.isReminderActive)
    }

    @Test
    fun parseEventStartMillis_and_calculateTargetMillis_beforeEventAndFromNow() {
        val tz = TimeZone.getTimeZone("Asia/Jakarta")
        val startMillis = ReminderCalculator.parseEventStartMillis("2026-11-15", "19:30 WIB", tz)
        assertNotNull(startMillis)

        val before30Min = ReminderCalculator.calculateTargetMillis(
            mode = ReminderMode.BEFORE_EVENT,
            minutes = 30,
            eventStartMillis = startMillis,
            nowMillis = startMillis!! - 3600_000L
        )
        assertEquals(startMillis - 30L * 60_000L, before30Min)

        val now = 1_700_000_000_000L
        val in15Min = ReminderCalculator.calculateTargetMillis(
            mode = ReminderMode.FROM_NOW,
            minutes = 15,
            eventStartMillis = null,
            nowMillis = now
        )
        assertEquals(now + 15L * 60_000L, in15Min)

        assertNull(ReminderCalculator.parseEventStartMillis("2026-11-15", "Pagi", tz))
    }

    @Test
    fun isRapatOrGotongRoyong_and_formatRemainingTime() {
        val rapat = UndanganItem(judul = "Rapat Pleno RT 005", keterangan = "Membahas kas")
        val gotong = UndanganItem(judul = "Kerja Bakti Minggu", keterangan = "Gotong royong bersih selokan")
        val other = UndanganItem(judul = "Acara Makan Bersama", keterangan = "Silaturahmi warga")

        assertTrue(ReminderCalculator.isRapatOrGotongRoyong(rapat))
        assertTrue(ReminderCalculator.isRapatOrGotongRoyong(gotong))
        assertFalse(ReminderCalculator.isRapatOrGotongRoyong(other))

        val now = 1_700_000_000_000L
        assertEquals("Aktif dalam 15 menit 0 detik", ReminderCalculator.formatRemainingTime(now + 15L * 60_000L, now))
        assertEquals("Notifikasi pengingat aktif", ReminderCalculator.formatRemainingTime(now - 1000L, now))
    }
}
