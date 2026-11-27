package com.example

import com.example.util.NotificationToneManager
import com.example.util.NotificationToneOption
import com.example.util.NotificationTonePreference
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationToneUnitTest {

    @Test
    fun resolveOptionFromKey_mapsAllKnownKeysAndFallsBackToSystemDefault() {
        assertEquals(NotificationToneOption.SYSTEM_DEFAULT, NotificationToneManager.resolveOptionFromKey(null))
        assertEquals(NotificationToneOption.SYSTEM_DEFAULT, NotificationToneManager.resolveOptionFromKey(""))
        assertEquals(NotificationToneOption.SYSTEM_DEFAULT, NotificationToneManager.resolveOptionFromKey("unknown_tone"))
        assertEquals(NotificationToneOption.SYSTEM_DEFAULT, NotificationToneManager.resolveOptionFromKey("system_default"))
        assertEquals(NotificationToneOption.APP_REMINDER, NotificationToneManager.resolveOptionFromKey("app_reminder"))
        assertEquals(NotificationToneOption.SYSTEM_ALARM, NotificationToneManager.resolveOptionFromKey("system_alarm"))
        assertEquals(NotificationToneOption.SYSTEM_RINGTONE, NotificationToneManager.resolveOptionFromKey("system_ringtone"))
        assertEquals(NotificationToneOption.DEVICE_PICKED, NotificationToneManager.resolveOptionFromKey("device_picked"))
        assertEquals(NotificationToneOption.SILENT_VIBRATE, NotificationToneManager.resolveOptionFromKey("silent_vibrate"))
    }

    @Test
    fun sanitizePreference_validatesCustomUriAndPreservesPresets() {
        val missingCustomUri = NotificationToneManager.sanitizePreference(
            option = NotificationToneOption.DEVICE_PICKED,
            customUri = "  ",
            customTitle = "Tone A",
            channelRevision = -2
        )
        assertEquals(NotificationToneOption.SYSTEM_DEFAULT, missingCustomUri.option)
        assertEquals("", missingCustomUri.customUri)
        assertEquals(0, missingCustomUri.channelRevision)

        val validCustom = NotificationToneManager.sanitizePreference(
            option = NotificationToneOption.DEVICE_PICKED,
            customUri = "content://media/internal/audio/media/42",
            customTitle = "Chime",
            channelRevision = 3
        )
        assertEquals(NotificationToneOption.DEVICE_PICKED, validCustom.option)
        assertEquals("content://media/internal/audio/media/42", validCustom.customUri)
        assertEquals("Chime", validCustom.customTitle)
        assertEquals("Chime", validCustom.displayLabel())
        assertEquals(3, validCustom.channelRevision)

        val preset = NotificationTonePreference(option = NotificationToneOption.APP_REMINDER)
        assertEquals("Nada Pengingat Aplikasi", preset.displayLabel())
    }

    @Test
    fun buildChannelIdAndPreviousChannelIds_handlesRevisionsCleanly() {
        val base = "channel_berita_desa_v3"
        assertEquals(base, NotificationToneManager.buildChannelId(base, 0))
        assertEquals("${base}_r1", NotificationToneManager.buildChannelId(base, 1))
        assertEquals("${base}_r4", NotificationToneManager.buildChannelId(base, 4))

        assertEquals(emptyList<String>(), NotificationToneManager.previousChannelIds(base, 0))
        assertEquals(listOf(base), NotificationToneManager.previousChannelIds(base, 1))
        assertEquals(
            listOf(base, "${base}_r1", "${base}_r2"),
            NotificationToneManager.previousChannelIds(base, 3)
        )
    }
}
