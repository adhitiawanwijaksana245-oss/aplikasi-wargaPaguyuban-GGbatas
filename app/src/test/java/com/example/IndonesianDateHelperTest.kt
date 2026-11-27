package com.example

import com.example.util.IndonesianDateHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class IndonesianDateHelperTest {

    @Test
    fun formatDates_convertsUtcIsoToWibIndonesian() {
        val rawUtcIso = "2026-11-17T17:00:00.000Z"
        assertEquals("2026-11-18", IndonesianDateHelper.toIsoDate(rawUtcIso))
        assertEquals("18 November 2026", IndonesianDateHelper.formatFullDate(rawUtcIso))
        assertEquals("18 Nov 2026", IndonesianDateHelper.formatShortDate(rawUtcIso))
    }

    @Test
    fun formatDates_handlesPlainDatesAndNonIsoStringsGracefully() {
        assertEquals("1 Desember 2025", IndonesianDateHelper.formatFullDate("2025-12-01"))
        assertEquals("1 Des 2025", IndonesianDateHelper.formatShortDate("2025-12-01"))
        assertEquals("18 November 2026", IndonesianDateHelper.formatFullDate("18/11/2026"))
        assertEquals("", IndonesianDateHelper.formatFullDate(""))
    }

    @Test
    fun formatDateTimeWib_formatsIsoTimestampsAndMillisInWib() {
        assertEquals(
            "24 Nov 2026 • 10:35 WIB",
            IndonesianDateHelper.formatDateTimeWib("2026-11-24T10:35:12+07:00")
        )
        assertEquals(
            "18 Nov 2026 • 00:00 WIB",
            IndonesianDateHelper.formatDateTimeWib("2026-11-17T17:00:00.000Z")
        )
        assertEquals("24 Nov 2026", IndonesianDateHelper.formatDateTimeWib("2026-11-24"))
        assertEquals("", IndonesianDateHelper.formatDateTimeWib(""))
        val nowIso = IndonesianDateHelper.nowWibIsoTimestamp(1763955300000L)
        assertEquals("2025-11-24T10:35:00+07:00", nowIso)
    }
}
