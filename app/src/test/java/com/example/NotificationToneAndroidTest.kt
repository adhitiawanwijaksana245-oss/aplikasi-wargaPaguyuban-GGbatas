package com.example

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.UserRole
import com.example.ui.components.AppHeader
import com.example.ui.theme.MyApplicationTheme
import com.example.util.NotificationHelper
import com.example.util.NotificationToneManager
import com.example.util.NotificationToneOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationToneAndroidTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(NotificationToneManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun savePreference_persistsLocallyAndUpdatesNotificationChannelsAndSoundUri() {
        // Initial default
        val initial = NotificationToneManager.getPreference(context)
        assertEquals(NotificationToneOption.SYSTEM_DEFAULT, initial.option)
        assertEquals(0, initial.channelRevision)

        // Switch to built-in APP_REMINDER (R.raw.nada_dering_pengingat)
        val appSaved = NotificationToneManager.savePreference(context, NotificationToneOption.APP_REMINDER)
        assertEquals(NotificationToneOption.APP_REMINDER, appSaved.option)
        assertEquals(1, appSaved.channelRevision)

        val reloadedApp = NotificationToneManager.getPreference(context)
        assertEquals(NotificationToneOption.APP_REMINDER, reloadedApp.option)
        val appUri = NotificationHelper.getConfiguredNotificationSoundUri(context)
        assertNotNull(appUri)
        assertEquals(
            "android.resource://${context.packageName}/${R.raw.nada_dering_pengingat}",
            appUri.toString()
        )

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val activeBeritaChannelId = NotificationHelper.resolveActiveChannelId(context, NotificationHelper.CHANNEL_BERITA)
        val activeChannel = manager.getNotificationChannel(activeBeritaChannelId)
        assertNotNull(activeChannel)
        assertEquals(appUri, activeChannel?.sound)

        // Switch to custom device tone URI
        val customUriStr = "content://media/internal/audio/media/99"
        val customSaved = NotificationToneManager.savePreference(
            context = context,
            option = NotificationToneOption.DEVICE_PICKED,
            customUri = customUriStr,
            customTitle = "Chime Lembut"
        )
        assertEquals(NotificationToneOption.DEVICE_PICKED, customSaved.option)
        assertEquals(customUriStr, customSaved.customUri)
        assertEquals("Chime Lembut", customSaved.customTitle)
        assertEquals(2, customSaved.channelRevision)
        assertEquals(Uri.parse(customUriStr), NotificationHelper.getConfiguredNotificationSoundUri(context))

        // Switch to SILENT_VIBRATE
        val silentSaved = NotificationToneManager.savePreference(context, NotificationToneOption.SILENT_VIBRATE)
        assertEquals(NotificationToneOption.SILENT_VIBRATE, silentSaved.option)
        assertTrue(NotificationToneManager.isSilentMode(context))
        assertNull(NotificationHelper.getConfiguredNotificationSoundUri(context))
        val silentChannelId = NotificationHelper.resolveActiveChannelId(context, NotificationHelper.CHANNEL_BERITA)
        assertNotEquals(activeBeritaChannelId, silentChannelId)
        val silentChannel = manager.getNotificationChannel(silentChannelId)
        assertNotNull(silentChannel)
        assertNull(silentChannel?.sound)
        assertFalse(silentChannel?.shouldVibrate() == false)
    }

    @Test
    fun overflowMenu_showsNadaNotifikasiInWargaPengurusAndAdminModes() {
        var currentRole by mutableStateOf(UserRole.WARGA)
        var clickCount = 0

        composeRule.setContent {
            MyApplicationTheme {
                AppHeader(
                    userRole = currentRole,
                    isSyncing = false,
                    onRoleClick = {},
                    onRefreshClick = {},
                    onOpenSettings = {},
                    onOpenChangePassword = {},
                    onOpenNotificationTone = { clickCount++ }
                )
            }
        }

        // Verify in WARGA mode
        composeRule.onNodeWithTag("more_menu_button").performClick()
        composeRule.onNodeWithTag("menu_nada_notifikasi").assertIsDisplayed().performClick()
        assertEquals(1, clickCount)

        // Verify in PENGURUS mode
        composeRule.runOnUiThread { currentRole = UserRole.PENGURUS }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("more_menu_button").performClick()
        composeRule.onNodeWithTag("menu_nada_notifikasi").assertIsDisplayed().performClick()
        assertEquals(2, clickCount)

        // Verify in ADMIN_UTAMA mode
        composeRule.runOnUiThread { currentRole = UserRole.ADMIN_UTAMA }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("more_menu_button").performClick()
        composeRule.onNodeWithTag("menu_nada_notifikasi").assertIsDisplayed().performClick()
        assertEquals(3, clickCount)
    }
}
