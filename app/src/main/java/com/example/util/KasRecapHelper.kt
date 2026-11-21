package com.example.util

import com.example.data.model.KasItem
import com.example.data.model.KasMonthlyRecap
import com.example.data.model.KasSummaryTotals
import java.util.Calendar
import java.util.Locale

object KasRecapHelper {

    private val INDONESIAN_MONTH_NAMES = listOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember"
    )

    private val MONTH_NAME_TO_NUMBER = mapOf(
        "januari" to 1, "jan" to 1, "january" to 1,
        "februari" to 2, "feb" to 2, "pebruari" to 2, "february" to 2,
        "maret" to 3, "mar" to 3, "march" to 3,
        "april" to 4, "apr" to 4,
        "mei" to 5, "may" to 5,
        "juni" to 6, "jun" to 6, "june" to 6,
        "juli" to 7, "jul" to 7, "july" to 7,
        "agustus" to 8, "agu" to 8, "agt" to 8, "aug" to 8, "august" to 8,
        "september" to 9, "sep" to 9, "sept" to 9,
        "oktober" to 10, "okt" to 10, "oct" to 10, "october" to 10,
        "november" to 11, "nov" to 11, "nop" to 11, "nopember" to 11,
        "desember" to 12, "des" to 12, "dec" to 12, "december" to 12
    )

    private val REGEX_YYYY_MM_PREFIX = Regex("^(\\d{4})[-/](\\d{1,2})(?:[-/T ].*)?$")
    private val REGEX_DD_MM_YYYY = Regex("^(\\d{1,2})[-/](\\d{1,2})[-/](\\d{4})(?:[T ].*)?$")
    private val REGEX_MM_YYYY = Regex("^(\\d{1,2})[-/](\\d{4})$")
    private val REGEX_NAMED_MONTH = Regex("^(?:\\d{1,2}\\s+)?([A-Za-z]+)\\s+(\\d{4})$")

    fun parseMonthKey(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return ""

        REGEX_YYYY_MM_PREFIX.matchEntire(trimmed)?.let { m ->
            val year = m.groupValues[1].toIntOrNull() ?: return ""
            val month = m.groupValues[2].toIntOrNull() ?: return ""
            if (month in 1..12 && year in 1900..2999) {
                return "%04d-%02d".format(Locale.US, year, month)
            }
        }

        REGEX_DD_MM_YYYY.matchEntire(trimmed)?.let { m ->
            val month = m.groupValues[2].toIntOrNull() ?: return ""
            val year = m.groupValues[3].toIntOrNull() ?: return ""
            if (month in 1..12 && year in 1900..2999) {
                return "%04d-%02d".format(Locale.US, year, month)
            }
        }

        REGEX_MM_YYYY.matchEntire(trimmed)?.let { m ->
            val month = m.groupValues[1].toIntOrNull() ?: return ""
            val year = m.groupValues[2].toIntOrNull() ?: return ""
            if (month in 1..12 && year in 1900..2999) {
                return "%04d-%02d".format(Locale.US, year, month)
            }
        }

        REGEX_NAMED_MONTH.matchEntire(trimmed)?.let { m ->
            val monthName = m.groupValues[1].lowercase(Locale.US)
            val month = MONTH_NAME_TO_NUMBER[monthName] ?: return ""
            val year = m.groupValues[2].toIntOrNull() ?: return ""
            if (year in 1900..2999) {
                return "%04d-%02d".format(Locale.US, year, month)
            }
        }

        return ""
    }

    fun resolvePeriodeKey(periodeBulan: String?, tanggal: String?): String {
        val fromPeriode = parseMonthKey(periodeBulan)
        if (fromPeriode.isNotBlank()) return fromPeriode
        return parseMonthKey(tanggal)
    }

    fun formatMonthYearLabel(periodeKeyOrLabel: String): String {
        val key = parseMonthKey(periodeKeyOrLabel)
        if (key.isBlank()) return periodeKeyOrLabel.trim()
        val parts = key.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: return periodeKeyOrLabel
        val month = parts.getOrNull(1)?.toIntOrNull() ?: return periodeKeyOrLabel
        val monthName = INDONESIAN_MONTH_NAMES.getOrNull(month - 1) ?: return periodeKeyOrLabel
        return "$monthName $year"
    }

    fun currentYearMonthKey(nowMillis: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        return "%04d-%02d".format(Locale.US, year, month)
    }

    fun shiftMonthKey(baseKey: String, offsetMonths: Int): String {
        val normalized = parseMonthKey(baseKey).ifBlank { return baseKey }
        val parts = normalized.split("-")
        val year = parts[0].toInt()
        val monthIndex = parts[1].toInt() - 1
        val totalMonths = year * 12 + monthIndex + offsetMonths
        val newYear = Math.floorDiv(totalMonths, 12)
        val newMonthIndex = Math.floorMod(totalMonths, 12)
        return "%04d-%02d".format(Locale.US, newYear, newMonthIndex + 1)
    }

    fun monthKeySequence(endKey: String, count: Int = 12): List<String> {
        if (count <= 0) return emptyList()
        val normalizedEnd = parseMonthKey(endKey).ifBlank { currentYearMonthKey() }
        return (-(count - 1)..0).map { offset ->
            shiftMonthKey(normalizedEnd, offset)
        }
    }

    fun normalizeAndRecalculateKas(items: List<KasItem>): List<KasItem> {
        var runningBalance = 0L
        return items.map { item ->
            val resolvedPeriode = resolvePeriodeKey(item.periodeBulan, item.tanggal)
            val normalizedPeriode = item.periodeBulan.trim().ifBlank { resolvedPeriode }
            runningBalance += (item.masuk - item.keluar)
            item.copy(
                periodeBulan = normalizedPeriode,
                saldo = runningBalance,
                jumlah = runningBalance
            )
        }
    }

    fun normalizeRecapList(rawList: List<KasMonthlyRecap>): List<KasMonthlyRecap> {
        return rawList.mapNotNull { recap ->
            val key = parseMonthKey(recap.periodeBulan.ifBlank { recap.labelBulan })
            if (key.isBlank()) return@mapNotNull null
            val arus = if (recap.arusKas != 0L || (recap.totalMasuk == 0L && recap.totalKeluar == 0L)) {
                recap.arusKas
            } else {
                recap.totalMasuk - recap.totalKeluar
            }
            recap.copy(
                periodeBulan = key,
                labelBulan = recap.labelBulan.trim().ifBlank { formatMonthYearLabel(key) }
                    .let { if (parseMonthKey(it) == key && it.any { ch -> ch.isLetter() }) it else formatMonthYearLabel(key) },
                arusKas = arus
            )
        }.sortedBy { it.periodeBulan }
    }

    fun buildFullMonthlyRecap(kasList: List<KasItem>): List<KasMonthlyRecap> {
        if (kasList.isEmpty()) return emptyList()

        val grouped = linkedMapOf<String, MutableList<KasItem>>()
        kasList.forEach { item ->
            val key = resolvePeriodeKey(item.periodeBulan, item.tanggal)
            if (key.isNotBlank()) {
                grouped.getOrPut(key) { mutableListOf() }.add(item)
            }
        }

        val sortedKeys = grouped.keys.sorted()
        var runningSaldo = 0L
        return sortedKeys.map { key ->
            val rows = grouped[key].orEmpty()
            val masuk = rows.sumOf { it.masuk }
            val keluar = rows.sumOf { it.keluar }
            val arus = masuk - keluar
            runningSaldo += arus
            KasMonthlyRecap(
                periodeBulan = key,
                labelBulan = formatMonthYearLabel(key),
                totalMasuk = masuk,
                totalKeluar = keluar,
                arusKas = arus,
                saldoAkhir = runningSaldo,
                jumlahTransaksi = rows.size
            )
        }
    }

    fun buildRolling12(
        kasList: List<KasItem>,
        remoteRekap: List<KasMonthlyRecap>? = null,
        remoteRolling12: List<KasMonthlyRecap>? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): List<KasMonthlyRecap> {
        val localRekap = buildFullMonthlyRecap(kasList)
        val normalizedRemoteRekap = normalizeRecapList(remoteRekap.orEmpty())
        val normalizedRemoteRolling = normalizeRecapList(remoteRolling12.orEmpty())

        if (localRekap.isEmpty()) {
            if (normalizedRemoteRolling.isNotEmpty()) {
                return normalizedRemoteRolling.takeLast(12)
            }
            if (normalizedRemoteRekap.isNotEmpty()) {
                return buildRolling12FromRecap(normalizedRemoteRekap, nowMillis)
            }
            val emptyWindow = monthKeySequence(currentYearMonthKey(nowMillis), 12)
            return emptyWindow.map { key ->
                KasMonthlyRecap(
                    periodeBulan = key,
                    labelBulan = formatMonthYearLabel(key),
                    totalMasuk = 0L,
                    totalKeluar = 0L,
                    arusKas = 0L,
                    saldoAkhir = 0L,
                    jumlahTransaksi = 0
                )
            }
        }

        // Merge remote historical months that may not be present in local kasList
        val mergedByKey = linkedMapOf<String, KasMonthlyRecap>()
        normalizedRemoteRekap.forEach { mergedByKey[it.periodeBulan] = it }
        localRekap.forEach { mergedByKey[it.periodeBulan] = it }

        val mergedChrono = recomputeRunningSaldos(mergedByKey.values.sortedBy { it.periodeBulan })
        return buildRolling12FromRecap(mergedChrono, nowMillis)
    }

    fun buildRolling12FromRecap(
        chronologicalRecap: List<KasMonthlyRecap>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<KasMonthlyRecap> {
        val sorted = recomputeRunningSaldos(normalizeRecapList(chronologicalRecap))
        val currentMonthKey = currentYearMonthKey(nowMillis)
        val latestDataMonthKey = sorted.lastOrNull()?.periodeBulan.orEmpty()
        val endKey = if (latestDataMonthKey.isNotBlank() && latestDataMonthKey > currentMonthKey) {
            latestDataMonthKey
        } else {
            currentMonthKey
        }

        val windowKeys = monthKeySequence(endKey, 12)
        val firstWindowKey = windowKeys.first()
        val byKey = sorted.associateBy { it.periodeBulan }

        // Opening balance carried forward from all months strictly before the 12-month window
        var runningBalance = sorted
            .filter { it.periodeBulan < firstWindowKey }
            .sumOf { it.totalMasuk - it.totalKeluar }

        return windowKeys.map { key ->
            val existing = byKey[key]
            val masuk = existing?.totalMasuk ?: 0L
            val keluar = existing?.totalKeluar ?: 0L
            val arus = masuk - keluar
            runningBalance += arus
            KasMonthlyRecap(
                periodeBulan = key,
                labelBulan = formatMonthYearLabel(key),
                totalMasuk = masuk,
                totalKeluar = keluar,
                arusKas = arus,
                saldoAkhir = runningBalance,
                jumlahTransaksi = existing?.jumlahTransaksi ?: 0
            )
        }
    }

    fun monthKeyRange(startKey: String, endKey: String): List<String> {
        val normStart = parseMonthKey(startKey).ifBlank { return emptyList() }
        val normEnd = parseMonthKey(endKey).ifBlank { return emptyList() }
        if (normStart > normEnd) return emptyList()

        val startParts = normStart.split("-")
        val endParts = normEnd.split("-")
        val startTotal = startParts[0].toInt() * 12 + (startParts[1].toInt() - 1)
        val endTotal = endParts[0].toInt() * 12 + (endParts[1].toInt() - 1)

        return (startTotal..endTotal).map { total ->
            val year = Math.floorDiv(total, 12)
            val monthIndex = Math.floorMod(total, 12)
            "%04d-%02d".format(Locale.US, year, monthIndex + 1)
        }
    }

    fun buildTimelineRecap(
        sourceRecap: List<KasMonthlyRecap>,
        startKey: String = "2026-01",
        minEndKey: String = "2030-12"
    ): List<KasMonthlyRecap> {
        val sorted = recomputeRunningSaldos(normalizeRecapList(sourceRecap))
        val normStart = parseMonthKey(startKey).ifBlank { "2026-01" }
        val normMinEnd = parseMonthKey(minEndKey).ifBlank { "2030-12" }
        val latestDataKey = sorted.lastOrNull()?.periodeBulan.orEmpty()
        val effectiveEnd = if (latestDataKey.isNotBlank() && latestDataKey > normMinEnd) {
            latestDataKey
        } else {
            normMinEnd
        }

        val keys = monthKeyRange(normStart, effectiveEnd)
        val byKey = sorted.associateBy { it.periodeBulan }
        var runningBalance = sorted
            .filter { it.periodeBulan < normStart }
            .sumOf { it.totalMasuk - it.totalKeluar }

        return keys.map { key ->
            val existing = byKey[key]
            val masuk = existing?.totalMasuk ?: 0L
            val keluar = existing?.totalKeluar ?: 0L
            val arus = masuk - keluar
            runningBalance += arus
            KasMonthlyRecap(
                periodeBulan = key,
                labelBulan = formatMonthYearLabel(key),
                totalMasuk = masuk,
                totalKeluar = keluar,
                arusKas = arus,
                saldoAkhir = runningBalance,
                jumlahTransaksi = existing?.jumlahTransaksi ?: 0
            )
        }
    }

    fun recomputeRunningSaldos(sortedRecap: List<KasMonthlyRecap>): List<KasMonthlyRecap> {
        var running = 0L
        return sortedRecap.sortedBy { it.periodeBulan }.map { recap ->
            val arus = recap.totalMasuk - recap.totalKeluar
            running += arus
            recap.copy(
                labelBulan = formatMonthYearLabel(recap.periodeBulan),
                arusKas = arus,
                saldoAkhir = running
            )
        }
    }

    fun computeUnifiedSummary(
        kasList: List<KasItem>,
        rekapBulanan: List<KasMonthlyRecap>,
        remoteSummary: KasSummaryTotals? = null
    ): KasSummaryTotals {
        if (kasList.isNotEmpty()) {
            val totalMasuk = kasList.sumOf { it.masuk }
            val totalKeluar = kasList.sumOf { it.keluar }
            return KasSummaryTotals(
                saldoKas = totalMasuk - totalKeluar,
                totalMasuk = totalMasuk,
                totalKeluar = totalKeluar
            )
        }
        if (rekapBulanan.isNotEmpty()) {
            val totalMasuk = rekapBulanan.sumOf { it.totalMasuk }
            val totalKeluar = rekapBulanan.sumOf { it.totalKeluar }
            val saldo = rekapBulanan.lastOrNull()?.saldoAkhir ?: (totalMasuk - totalKeluar)
            return KasSummaryTotals(
                saldoKas = saldo,
                totalMasuk = totalMasuk,
                totalKeluar = totalKeluar
            )
        }
        return remoteSummary ?: KasSummaryTotals()
    }
}
