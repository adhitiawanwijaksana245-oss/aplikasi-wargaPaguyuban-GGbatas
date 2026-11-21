package com.example

import com.example.data.model.KasItem
import com.example.data.model.KasMonthlyRecap
import com.example.util.KasRecapHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class KasRecapHelperTest {

    @Test
    fun parseMonthKey_and_formatMonthYearLabel_supportAllSpreadsheetFormats() {
        assertEquals("2026-01", KasRecapHelper.parseMonthKey("2026-01-15"))
        assertEquals("2026-11", KasRecapHelper.parseMonthKey("2026-11-10T17:00:00.000Z"))
        assertEquals("2025-12", KasRecapHelper.parseMonthKey("Desember 2025"))
        assertEquals("2026-02", KasRecapHelper.parseMonthKey("14/02/2026"))
        assertEquals("2026-08", KasRecapHelper.parseMonthKey("Agustus 2026"))

        assertEquals("Januari 2026", KasRecapHelper.formatMonthYearLabel("2026-01"))
        assertEquals("Desember 2025", KasRecapHelper.formatMonthYearLabel("2025-12"))
        assertEquals("November 2026", KasRecapHelper.formatMonthYearLabel("2026-11"))
    }

    @Test
    fun buildRolling12_shiftsAutomaticallyWithOpeningBalanceCarryForward() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta")).apply {
            set(2026, Calendar.NOVEMBER, 15, 10, 0, 0)
        }
        val items = listOf(
            KasItem(id = "K-OLD", tanggal = "2025-10-05", kategori = "Donatur", keterangan = "Saldo awal", masuk = 1_000_000L, keluar = 0L),
            KasItem(id = "K-JAN", tanggal = "2026-01-12", kategori = "Iuran", keterangan = "Iuran Jan", masuk = 500_000L, keluar = 100_000L),
            KasItem(id = "K-NOV", tanggal = "2026-11-08", kategori = "Operasional", keterangan = "Lampu", masuk = 0L, keluar = 200_000L)
        )

        val normalized = KasRecapHelper.normalizeAndRecalculateKas(items)
        assertEquals("2025-10", normalized[0].periodeBulan)
        assertEquals(1_000_000L, normalized[0].saldo)
        assertEquals(1_400_000L, normalized[1].saldo)
        assertEquals(1_200_000L, normalized[2].saldo)

        val rolling12 = KasRecapHelper.buildRolling12(
            kasList = normalized,
            nowMillis = cal.timeInMillis
        )

        assertEquals(12, rolling12.size)
        assertEquals("2025-12", rolling12.first().periodeBulan)
        assertEquals("Desember 2025", rolling12.first().labelBulan)
        assertEquals(1_000_000L, rolling12.first().saldoAkhir)

        val jan = rolling12.first { it.periodeBulan == "2026-01" }
        assertEquals("Januari 2026", jan.labelBulan)
        assertEquals(500_000L, jan.totalMasuk)
        assertEquals(100_000L, jan.totalKeluar)
        assertEquals(1_400_000L, jan.saldoAkhir)

        val nov = rolling12.last()
        assertEquals("2026-11", nov.periodeBulan)
        assertEquals("November 2026", nov.labelBulan)
        assertEquals(1_200_000L, nov.saldoAkhir)

        val summary = KasRecapHelper.computeUnifiedSummary(
            kasList = normalized,
            rekapBulanan = KasRecapHelper.buildFullMonthlyRecap(normalized)
        )
        assertEquals(1_500_000L, summary.totalMasuk)
        assertEquals(300_000L, summary.totalKeluar)
        assertEquals(1_200_000L, summary.saldoKas)
    }

    @Test
    fun buildRolling12_fallsBackToRemoteRecapWhenKasListIsEmpty() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta")).apply {
            set(2026, Calendar.NOVEMBER, 15, 10, 0, 0)
        }
        val remoteRolling = listOf(
            KasMonthlyRecap(periodeBulan = "2026-10", labelBulan = "Oktober 2026", totalMasuk = 700_000L, totalKeluar = 100_000L, saldoAkhir = 600_000L),
            KasMonthlyRecap(periodeBulan = "2026-11", labelBulan = "November 2026", totalMasuk = 300_000L, totalKeluar = 50_000L, saldoAkhir = 850_000L)
        )

        val resolved = KasRecapHelper.buildRolling12(
            kasList = emptyList(),
            remoteRolling12 = remoteRolling,
            nowMillis = cal.timeInMillis
        )
        assertEquals(2, resolved.size)
        assertEquals("Oktober 2026", resolved[0].labelBulan)
        assertEquals(850_000L, resolved[1].saldoAkhir)

        val summary = KasRecapHelper.computeUnifiedSummary(emptyList(), remoteRolling)
        assertEquals(1_000_000L, summary.totalMasuk)
        assertEquals(150_000L, summary.totalKeluar)
        assertEquals(850_000L, summary.saldoKas)
        assertTrue(summary.saldoKas == summary.totalMasuk - summary.totalKeluar)
    }

    @Test
    fun buildTimelineRecap_spansJanuari2026ToDesember2030WithOpeningBalance() {
        val source = listOf(
            KasMonthlyRecap(periodeBulan = "2025-12", labelBulan = "Desember 2025", totalMasuk = 200_000L, totalKeluar = 50_000L),
            KasMonthlyRecap(periodeBulan = "2026-01", labelBulan = "Januari 2026", totalMasuk = 1_000_000L, totalKeluar = 100_000L),
            KasMonthlyRecap(periodeBulan = "2026-03", labelBulan = "Maret 2026", totalMasuk = 500_000L, totalKeluar = 250_000L)
        )

        val timeline = KasRecapHelper.buildTimelineRecap(
            sourceRecap = source,
            startKey = "2026-01",
            minEndKey = "2030-12"
        )

        assertEquals(60, timeline.size)
        assertEquals("2026-01", timeline.first().periodeBulan)
        assertEquals("Januari 2026", timeline.first().labelBulan)
        assertEquals(1_050_000L, timeline.first().saldoAkhir)

        val feb2026 = timeline[1]
        assertEquals("2026-02", feb2026.periodeBulan)
        assertEquals("Februari 2026", feb2026.labelBulan)
        assertEquals(1_050_000L, feb2026.saldoAkhir)

        assertEquals("2030-12", timeline.last().periodeBulan)
        assertEquals("Desember 2030", timeline.last().labelBulan)
        assertEquals(1_300_000L, timeline.last().saldoAkhir)
    }
}
