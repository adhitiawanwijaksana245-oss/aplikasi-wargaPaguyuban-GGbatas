package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pemutar suara notifikasi mengikuti pengaturan bawaan masing-masing HP
 * (menghormati mode senyap/getar dan pilihan nada dering perangkat).
 */
object ReminderRingtonePlayer {
    private const val TAG = "ReminderRingtonePlayer"
    private const val DEFAULT_MAX_DURATION_MS = 15_000L

    private var mediaPlayer: MediaPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val autoStopRunnable = Runnable { stop() }

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    @Synchronized
    fun play(
        context: Context,
        loop: Boolean = false,
        maxDurationMs: Long = DEFAULT_MAX_DURATION_MS
    ): Boolean {
        stopInternal()

        val appContext = context.applicationContext
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager != null && audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
            // Hormati pengaturan mode senyap / getar masing-masing HP
            return false
        }

        val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: return false

        val player = try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(appContext, defaultUri)
                prepare()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal memutar suara notifikasi bawaan perangkat: ${e.message}")
            null
        } ?: return false

        return try {
            player.isLooping = loop
            player.setOnCompletionListener {
                if (!loop) {
                    stop()
                }
            }
            player.setOnErrorListener { _, _, _ ->
                stop()
                true
            }
            player.start()
            mediaPlayer = player
            _isPlaying.value = true

            mainHandler.removeCallbacks(autoStopRunnable)
            if (maxDurationMs > 0L) {
                mainHandler.postDelayed(autoStopRunnable, maxDurationMs)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memulai pemutaran notifikasi: ${e.message}", e)
            try {
                player.release()
            } catch (_: Exception) {
            }
            _isPlaying.value = false
            false
        }
    }

    @Synchronized
    fun stop() {
        stopInternal()
    }

    private fun stopInternal() {
        mainHandler.removeCallbacks(autoStopRunnable)
        val current = mediaPlayer
        mediaPlayer = null
        if (current != null) {
            try {
                if (current.isPlaying) {
                    current.stop()
                }
            } catch (_: Exception) {
            }
            try {
                current.reset()
            } catch (_: Exception) {
            }
            try {
                current.release()
            } catch (_: Exception) {
            }
        }
        _isPlaying.value = false
    }
}

class StopRingtoneBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        ReminderRingtonePlayer.stop()
        NotificationHelper.silenceActiveRingtone(context)
    }
}
