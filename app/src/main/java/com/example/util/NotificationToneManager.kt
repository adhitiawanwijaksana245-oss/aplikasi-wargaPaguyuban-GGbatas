package com.example.util

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NotificationToneOption(
    val storageKey: String,
    val label: String,
    val subtitle: String
) {
    SYSTEM_DEFAULT(
        storageKey = "system_default",
        label = "Nada Default Sistem",
        subtitle = "Suara notifikasi bawaan dari pengaturan HP ini"
    ),
    APP_REMINDER(
        storageKey = "app_reminder",
        label = "Nada Pengingat Aplikasi",
        subtitle = "Nada pengingat khusus aplikasi Paguyuban Warga"
    ),
    SYSTEM_ALARM(
        storageKey = "system_alarm",
        label = "Nada Alarm Perangkat",
        subtitle = "Nada alarm bawaan HP yang lebih terdengar jelas"
    ),
    SYSTEM_RINGTONE(
        storageKey = "system_ringtone",
        label = "Nada Dering Perangkat",
        subtitle = "Nada dering panggilan bawaan HP ini"
    ),
    DEVICE_PICKED(
        storageKey = "device_picked",
        label = "Nada Pilihan Perangkat",
        subtitle = "Nada notifikasi lain yang tersedia di HP ini"
    ),
    SILENT_VIBRATE(
        storageKey = "silent_vibrate",
        label = "Senyap / Getar Saja",
        subtitle = "Notifikasi muncul dengan getaran tanpa suara"
    )
}

data class NotificationTonePreference(
    val option: NotificationToneOption = NotificationToneOption.SYSTEM_DEFAULT,
    val customUri: String = "",
    val customTitle: String = "",
    val channelRevision: Int = 0
) {
    fun displayLabel(): String {
        return when (option) {
            NotificationToneOption.DEVICE_PICKED ->
                customTitle.takeIf { it.isNotBlank() } ?: option.label
            else -> option.label
        }
    }
}

data class DeviceToneItem(
    val title: String,
    val uriString: String
)

object NotificationToneManager {
    const val PREFS_NAME = "aplikasi_warga_notif_tone_prefs"
    private const val KEY_TONE_OPTION = "notif_tone_option"
    private const val KEY_CUSTOM_URI = "notif_tone_custom_uri"
    private const val KEY_CUSTOM_TITLE = "notif_tone_custom_title"
    private const val KEY_CHANNEL_REVISION = "notif_channel_revision"

    private val _preferenceFlow = MutableStateFlow(NotificationTonePreference())
    val preferenceFlow: StateFlow<NotificationTonePreference> = _preferenceFlow.asStateFlow()

    fun resolveOptionFromKey(key: String?): NotificationToneOption {
        val clean = key?.trim().orEmpty()
        return NotificationToneOption.entries.firstOrNull { it.storageKey == clean }
            ?: NotificationToneOption.SYSTEM_DEFAULT
    }

    fun sanitizePreference(
        option: NotificationToneOption,
        customUri: String?,
        customTitle: String?,
        channelRevision: Int
    ): NotificationTonePreference {
        val cleanUri = customUri?.trim().orEmpty()
        val cleanTitle = customTitle?.trim().orEmpty()
        val resolvedOption = if (option == NotificationToneOption.DEVICE_PICKED && cleanUri.isBlank()) {
            NotificationToneOption.SYSTEM_DEFAULT
        } else {
            option
        }
        return NotificationTonePreference(
            option = resolvedOption,
            customUri = if (resolvedOption == NotificationToneOption.DEVICE_PICKED) cleanUri else "",
            customTitle = if (resolvedOption == NotificationToneOption.DEVICE_PICKED) {
                cleanTitle.ifBlank { "Nada Pilihan Perangkat" }
            } else {
                ""
            },
            channelRevision = channelRevision.coerceAtLeast(0)
        )
    }

    fun buildChannelId(baseChannelId: String, channelRevision: Int): String {
        return if (channelRevision <= 0) {
            baseChannelId
        } else {
            "${baseChannelId}_r$channelRevision"
        }
    }

    fun previousChannelIds(baseChannelId: String, currentRevision: Int): List<String> {
        if (currentRevision <= 0) return emptyList()
        val ids = mutableListOf(baseChannelId)
        for (rev in 1 until currentRevision) {
            ids.add("${baseChannelId}_r$rev")
        }
        return ids
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun getPreference(context: Context): NotificationTonePreference {
        val p = prefs(context)
        val loaded = sanitizePreference(
            option = resolveOptionFromKey(p.getString(KEY_TONE_OPTION, null)),
            customUri = p.getString(KEY_CUSTOM_URI, ""),
            customTitle = p.getString(KEY_CUSTOM_TITLE, ""),
            channelRevision = p.getInt(KEY_CHANNEL_REVISION, 0)
        )
        _preferenceFlow.value = loaded
        return loaded
    }

    @Synchronized
    fun savePreference(
        context: Context,
        option: NotificationToneOption,
        customUri: String = "",
        customTitle: String = ""
    ): NotificationTonePreference {
        val appContext = context.applicationContext
        val current = getPreference(appContext)
        val sanitizedCandidate = sanitizePreference(
            option = option,
            customUri = customUri,
            customTitle = customTitle,
            channelRevision = current.channelRevision
        )
        val changed = sanitizedCandidate.option != current.option ||
            sanitizedCandidate.customUri != current.customUri ||
            (current.channelRevision == 0 && sanitizedCandidate.option != NotificationToneOption.SYSTEM_DEFAULT)

        val nextRevision = if (changed) current.channelRevision + 1 else current.channelRevision
        val finalPref = sanitizedCandidate.copy(channelRevision = nextRevision)

        prefs(appContext).edit()
            .putString(KEY_TONE_OPTION, finalPref.option.storageKey)
            .putString(KEY_CUSTOM_URI, finalPref.customUri)
            .putString(KEY_CUSTOM_TITLE, finalPref.customTitle)
            .putInt(KEY_CHANNEL_REVISION, finalPref.channelRevision)
            .commit()

        _preferenceFlow.value = finalPref
        NotificationHelper.recreateNotificationChannelsForToneChange(appContext)
        return finalPref
    }

    fun isSilentMode(context: Context): Boolean {
        return getPreference(context).option == NotificationToneOption.SILENT_VIBRATE
    }

    fun resolveSoundUri(
        context: Context,
        preference: NotificationTonePreference = getPreference(context)
    ): Uri? {
        return resolvePreviewSoundUri(context, preference.option, preference.customUri)
    }

    fun resolvePreviewSoundUri(
        context: Context,
        option: NotificationToneOption,
        customUri: String = ""
    ): Uri? {
        val appContext = context.applicationContext
        return when (option) {
            NotificationToneOption.SYSTEM_DEFAULT ->
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: Settings.System.DEFAULT_NOTIFICATION_URI

            NotificationToneOption.APP_REMINDER ->
                Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${appContext.packageName}/${R.raw.nada_dering_pengingat}")

            NotificationToneOption.SYSTEM_ALARM ->
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: Settings.System.DEFAULT_NOTIFICATION_URI

            NotificationToneOption.SYSTEM_RINGTONE ->
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: Settings.System.DEFAULT_NOTIFICATION_URI

            NotificationToneOption.DEVICE_PICKED -> {
                val clean = customUri.trim()
                if (clean.isNotBlank()) {
                    Uri.parse(clean)
                } else {
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                        ?: Settings.System.DEFAULT_NOTIFICATION_URI
                }
            }

            NotificationToneOption.SILENT_VIBRATE -> null
        }
    }

    fun queryDeviceNotificationTones(context: Context, limit: Int = 25): List<DeviceToneItem> {
        val appContext = context.applicationContext
        return try {
            val manager = RingtoneManager(appContext).apply {
                setType(RingtoneManager.TYPE_NOTIFICATION)
            }
            val cursor = manager.cursor ?: return emptyList()
            val results = mutableListOf<DeviceToneItem>()
            cursor.use { c ->
                var count = 0
                while (c.moveToNext() && count < limit) {
                    val pos = c.position
                    val title = c.getString(RingtoneManager.TITLE_COLUMN_INDEX)?.trim().orEmpty()
                    val uri = try {
                        manager.getRingtoneUri(pos)?.toString().orEmpty()
                    } catch (_: Exception) {
                        ""
                    }
                    if (title.isNotBlank() && uri.isNotBlank()) {
                        results.add(DeviceToneItem(title = title, uriString = uri))
                        count++
                    }
                }
            }
            results.distinctBy { it.uriString }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun resolveToneTitle(context: Context, uri: Uri): String {
        return try {
            val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri)
            val title = ringtone?.getTitle(context.applicationContext)?.trim().orEmpty()
            title.ifBlank { "Nada Pilihan Perangkat" }
        } catch (_: Exception) {
            "Nada Pilihan Perangkat"
        }
    }

    fun triggerShortVibrate(context: Context) {
        try {
            val appContext = context.applicationContext
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(180L, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(
                        VibrationEffect.createOneShot(180L, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(180L)
                }
            }
        } catch (_: Exception) {
        }
    }
}
