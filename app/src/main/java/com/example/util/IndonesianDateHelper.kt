package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Helper terpusat untuk memformat seluruh tanggal aplikasi ke format Indonesia (WIB / Asia/Jakarta)
 * sehingga timestamp ISO UTC dari Google Sheets (seperti "2026-11-17T17:00:00.000Z")
 * selalu tampil rapi sebagai tanggal lokal WIB (mis. "18 November 2026" atau "18 Nov 2026").
 */
object IndonesianDateHelper {

    val WIB_TIME_ZONE: TimeZone = TimeZone.getTimeZone("Asia/Jakarta")

    private val FULL_MONTHS = arrayOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember"
    )

    private val SHORT_MONTHS = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "Mei", "Jun",
        "Jul", "Agu", "Sep", "Okt", "Nov", "Des"
    )

    private val ISO_OFFSET_REGEX = Regex("^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})(?:\\.(\\d{1,3}))?(Z|[+-]\\d{2}:?\\d{2})?$")
    private val YMD_REGEX = Regex("^(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})$")
    private val DMY_REGEX = Regex("^(\\d{1,2})[-/](\\d{1,2})[-/](\\d{4})$")

    fun parseToWibCalendar(raw: String?): Calendar? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return null

        // 1. ISO-8601 datetime (mis. "2026-11-17T17:00:00.000Z" atau "+07:00" atau tanpa offset)
        ISO_OFFSET_REGEX.matchEntire(trimmed)?.let { match ->
            val basePart = match.groupValues[1]
            val millisPart = match.groupValues[2]
            val zonePart = match.groupValues[3]

            val sourceTz = when {
                zonePart.equals("Z", ignoreCase = true) -> TimeZone.getTimeZone("UTC")
                zonePart.isNotBlank() -> {
                    val normalizedZone = if (zonePart.contains(":")) {
                        "GMT$zonePart"
                    } else {
                        "GMT${zonePart.substring(0, 3)}:${zonePart.substring(3)}"
                    }
                    TimeZone.getTimeZone(normalizedZone)
                }
                else -> WIB_TIME_ZONE
            }

            val parsed = try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                    isLenient = false
                    timeZone = sourceTz
                }
                sdf.parse(basePart)?.let { date ->
                    val extraMillis = if (millisPart.isNotBlank()) {
                        millisPart.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                    } else 0L
                    Date(date.time + extraMillis)
                }
            } catch (_: Exception) {
                null
            }

            if (parsed != null) {
                return Calendar.getInstance(WIB_TIME_ZONE).apply {
                    time = parsed
                }
            }
        }

        // 2. yyyy-MM-dd atau yyyy/MM/dd
        YMD_REGEX.matchEntire(trimmed)?.let { match ->
            val year = match.groupValues[1].toIntOrNull() ?: return null
            val month = match.groupValues[2].toIntOrNull() ?: return null
            val day = match.groupValues[3].toIntOrNull() ?: return null
            if (month in 1..12 && day in 1..31 && year in 1900..2999) {
                return Calendar.getInstance(WIB_TIME_ZONE).apply {
                    clear()
                    set(year, month - 1, day, 0, 0, 0)
                }
            }
        }

        // 3. dd-MM-yyyy atau dd/MM/yyyy
        DMY_REGEX.matchEntire(trimmed)?.let { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return null
            val month = match.groupValues[2].toIntOrNull() ?: return null
            val year = match.groupValues[3].toIntOrNull() ?: return null
            if (month in 1..12 && day in 1..31 && year in 1900..2999) {
                return Calendar.getInstance(WIB_TIME_ZONE).apply {
                    clear()
                    set(year, month - 1, day, 0, 0, 0)
                }
            }
        }

        return null
    }

    fun formatFullDate(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return ""
        val cal = parseToWibCalendar(trimmed) ?: return trimmed
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val monthName = FULL_MONTHS.getOrNull(cal.get(Calendar.MONTH)) ?: return trimmed
        val year = cal.get(Calendar.YEAR)
        return "$day $monthName $year"
    }

    fun formatShortDate(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return ""
        val cal = parseToWibCalendar(trimmed) ?: return trimmed
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val monthName = SHORT_MONTHS.getOrNull(cal.get(Calendar.MONTH)) ?: return trimmed
        val year = cal.get(Calendar.YEAR)
        return "$day $monthName $year"
    }

    fun toIsoDate(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return ""
        val cal = parseToWibCalendar(trimmed) ?: return trimmed
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        return "%04d-%02d-%02d".format(Locale.US, year, month, day)
    }

    fun nowWibIsoTimestamp(nowMillis: Long = System.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = WIB_TIME_ZONE
        }
        return "${sdf.format(Date(nowMillis))}+07:00"
    }

    fun formatDateTimeWib(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return ""
        val cal = parseToWibCalendar(trimmed) ?: return trimmed
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val monthName = SHORT_MONTHS.getOrNull(cal.get(Calendar.MONTH)) ?: return trimmed
        val year = cal.get(Calendar.YEAR)
        return if (ISO_OFFSET_REGEX.matches(trimmed)) {
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)
            "%d %s %d • %02d:%02d WIB".format(Locale.US, day, monthName, year, hour, minute)
        } else {
            "$day $monthName $year"
        }
    }
}
