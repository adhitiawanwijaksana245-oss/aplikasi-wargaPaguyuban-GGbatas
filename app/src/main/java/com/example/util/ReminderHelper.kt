package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.model.ReminderMode
import com.example.data.model.UndanganItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ParsedReminder(
    val minutes: Int,
    val targetMillis: Long,
    val activatedMillis: Long
)

/**
 * Codec ringan untuk menyisipkan metadata pengingat ke kolom Keterangan Google Sheets
 * secara backwards-compatible dengan Google Apps Script lama maupun baru.
 */
object ReminderCodec {
    private val MARKER_REGEX = Regex("\\s*\\[\\[PENGINGAT:(\\d+):(\\d+):(\\d+)\\]\\]\\s*")

    fun encodeIntoKeterangan(
        rawKeterangan: String,
        minutes: Int,
        targetMillis: Long,
        activatedMillis: Long
    ): String {
        val clean = stripMarker(rawKeterangan)
        if (minutes <= 0 || targetMillis <= 0L) return clean
        val marker = "[[PENGINGAT:$minutes:$targetMillis:$activatedMillis]]"
        return if (clean.isBlank()) marker else "$clean\n$marker"
    }

    fun stripMarker(rawKeterangan: String): String {
        if (rawKeterangan.isBlank()) return ""
        return MARKER_REGEX.replace(rawKeterangan, "\n").trim()
    }

    fun parseMarker(rawKeterangan: String): ParsedReminder? {
        if (rawKeterangan.isBlank()) return null
        val match = MARKER_REGEX.find(rawKeterangan) ?: return null
        val minutes = match.groupValues[1].toIntOrNull() ?: return null
        val targetMillis = match.groupValues[2].toLongOrNull() ?: return null
        val activatedMillis = match.groupValues[3].toLongOrNull() ?: 0L
        if (minutes <= 0 || targetMillis <= 0L) return null
        return ParsedReminder(minutes = minutes, targetMillis = targetMillis, activatedMillis = activatedMillis)
    }
}

/**
 * Kalkulasi jadwal acara (Rapat / Gotong Royong) dan waktu pemicuan pengingat.
 */
object ReminderCalculator {
    private val TIME_REGEX = Regex("(\\d{1,2})[:.](\\d{2})")
    private val DATE_PATTERNS = listOf("yyyy-MM-dd", "dd-MM-yyyy", "dd/MM/yyyy", "yyyy/MM/dd")

    fun parseEventStartMillis(
        tanggal: String,
        waktu: String,
        timeZone: TimeZone = TimeZone.getTimeZone("Asia/Jakarta")
    ): Long? {
        val cleanDate = tanggal.trim()
        if (cleanDate.isBlank()) return null

        var parsedDate: Date? = null
        for (pattern in DATE_PATTERNS) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                    this.timeZone = timeZone
                }
                parsedDate = sdf.parse(cleanDate)
                if (parsedDate != null) break
            } catch (_: Exception) {
            }
        }
        val date = parsedDate ?: return null

        val timeMatch = TIME_REGEX.find(waktu) ?: return null
        val hour = timeMatch.groupValues[1].toIntOrNull() ?: return null
        val minute = timeMatch.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null

        val cal = Calendar.getInstance(timeZone).apply {
            time = date
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun calculateTargetMillis(
        mode: ReminderMode,
        minutes: Int,
        eventStartMillis: Long?,
        nowMillis: Long = System.currentTimeMillis()
    ): Long? {
        if (minutes <= 0) return null
        val offsetMillis = minutes.toLong() * 60_000L
        return when (mode) {
            ReminderMode.FROM_NOW -> nowMillis + offsetMillis
            ReminderMode.BEFORE_EVENT -> {
                val start = eventStartMillis ?: return null
                start - offsetMillis
            }
        }
    }

    fun isRapatOrGotongRoyong(item: UndanganItem): Boolean {
        val text = "${item.cleanJudul} ${item.cleanKeterangan}".lowercase(Locale.getDefault())
        return text.contains("rapat") ||
            text.contains("musyawarah") ||
            text.contains("gotong royong") ||
            text.contains("kerja bakti") ||
            text.contains("pleno")
    }

    fun formatRemainingTime(targetMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val diff = targetMillis - nowMillis
        if (diff <= 0L) return "Notifikasi pengingat aktif"
        val totalSeconds = (diff + 999L) / 1000L
        val days = totalSeconds / 86400L
        val hours = (totalSeconds % 86400L) / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return when {
            days > 0L -> "Aktif dalam ${days}h ${hours}j lagi"
            hours > 0L -> "Aktif dalam ${hours} jam ${minutes} menit lagi"
            minutes > 0L -> "Aktif dalam ${minutes} menit ${seconds} detik"
            else -> "Aktif dalam ${seconds} detik"
        }
    }

    fun formatClockTime(millis: Long, timeZone: TimeZone = TimeZone.getTimeZone("Asia/Jakarta")): String {
        if (millis <= 0L) return "-"
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm 'WIB'", Locale("in", "ID")).apply {
            this.timeZone = timeZone
        }
        return sdf.format(Date(millis))
    }

    fun buildReminderNotificationMessage(item: UndanganItem): String {
        val locationPart = if (item.lokasi.isNotBlank() && item.lokasi != "-") " di ${item.lokasi}" else ""
        val schedulePart = if (item.waktu.isNotBlank() && item.waktu != "-") "${item.tanggal} pukul ${item.waktu}" else item.tanggal
        val notes = item.cleanKeterangan
        return buildString {
            append("Pengingat acara warga (${item.effectiveReminderMenit} menit): ")
            append(item.cleanJudul)
            append(" pada ")
            append(schedulePart)
            append(locationPart)
            if (notes.isNotBlank()) {
                append(". ")
                append(notes)
            }
        }
    }
}

object ReminderDeliveryTracker {
    private const val PREFS_NAME = "aplikasi_warga_reminder_prefs"
    private const val KEY_TRIGGERED_SET = "triggered_reminder_keys"

    fun buildTriggerKey(undanganId: String, targetMillis: Long): String = "${undanganId}_$targetMillis"

    fun isTriggered(context: Context, triggerKey: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_TRIGGERED_SET, emptySet()) ?: emptySet()
        return triggerKey in set
    }

    fun markTriggered(context: Context, triggerKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_TRIGGERED_SET, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(triggerKey)
        // Batasi jumlah histori agar SharedPreferences tetap ramping
        val trimmed = if (current.size > 100) current.toList().takeLast(100).toSet() else current
        prefs.edit().putStringSet(KEY_TRIGGERED_SET, trimmed).apply()
    }
}

object ReminderScheduler {
    private const val MAX_STALE_WINDOW_MS = 12L * 60L * 60L * 1000L // 12 jam

    const val EXTRA_TRIGGER_KEY = "extra_trigger_key"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_MESSAGE = "extra_message"

    fun syncAlarmsAndCheckDue(
        context: Context,
        undanganList: List<UndanganItem>,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        syncAlarmsAndCheckDueCount(context, undanganList, nowMillis)
    }

    fun syncAlarmsAndCheckDueCount(
        context: Context,
        undanganList: List<UndanganItem>,
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        val activeItems = undanganList.filter { it.isReminderActive }
        activeItems.forEach { item ->
            val targetMillis = item.effectiveReminderTargetMillis
            val key = ReminderDeliveryTracker.buildTriggerKey(item.id, targetMillis)
            if (targetMillis > nowMillis) {
                if (!ReminderDeliveryTracker.isTriggered(context, key)) {
                    scheduleReminderAlarm(context, item)
                }
            }
        }
        return checkAndFireDueReminders(context, activeItems, nowMillis)
    }

    fun checkAndFireDueReminders(
        context: Context,
        undanganList: List<UndanganItem>,
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        var firedCount = 0
        undanganList.filter { it.isReminderActive }.forEach { item ->
            val targetMillis = item.effectiveReminderTargetMillis
            val key = ReminderDeliveryTracker.buildTriggerKey(item.id, targetMillis)
            val isDue = nowMillis >= targetMillis
            val withinWindow = (nowMillis - targetMillis) <= MAX_STALE_WINDOW_MS
            if (isDue && withinWindow && !ReminderDeliveryTracker.isTriggered(context, key)) {
                ReminderDeliveryTracker.markTriggered(context, key)
                NotificationHelper.initNotificationChannels(context)
                NotificationHelper.showPushNotification(
                    context = context,
                    title = "⏰ Pengingat Acara RT: ${item.cleanJudul}",
                    message = ReminderCalculator.buildReminderNotificationMessage(item),
                    channelId = NotificationHelper.CHANNEL_PENGINGAT,
                    type = "PENGINGAT",
                    silent = false,
                    targetTab = 3
                )
                firedCount++
            }
        }
        return firedCount
    }

    fun scheduleReminderAlarm(context: Context, item: UndanganItem) {
        val targetMillis = item.effectiveReminderTargetMillis
        if (targetMillis <= System.currentTimeMillis()) return
        val triggerKey = ReminderDeliveryTracker.buildTriggerKey(item.id, targetMillis)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            putExtra(EXTRA_TRIGGER_KEY, triggerKey)
            putExtra(EXTRA_TITLE, "⏰ Pengingat Acara RT: ${item.cleanJudul}")
            putExtra(EXTRA_MESSAGE, ReminderCalculator.buildReminderNotificationMessage(item))
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            item.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetMillis, pendingIntent)
        } catch (_: Exception) {
        }
    }

    fun cancelReminderAlarm(context: Context, undanganId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderBroadcastReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            undanganId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}

class ReminderBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val triggerKey = intent.getStringExtra(ReminderScheduler.EXTRA_TRIGGER_KEY).orEmpty()
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "⏰ Pengingat Acara RT"
        val message = intent.getStringExtra(ReminderScheduler.EXTRA_MESSAGE) ?: "Acara warga RT 005 akan segera dimulai."
        if (triggerKey.isNotBlank() && ReminderDeliveryTracker.isTriggered(context, triggerKey)) {
            return
        }
        if (triggerKey.isNotBlank()) {
            ReminderDeliveryTracker.markTriggered(context, triggerKey)
        }
        NotificationHelper.initNotificationChannels(context)
        NotificationHelper.showPushNotification(
            context = context,
            title = title,
            message = message,
            channelId = NotificationHelper.CHANNEL_PENGINGAT,
            type = "PENGINGAT",
            silent = false,
            targetTab = 3
        )
    }
}
