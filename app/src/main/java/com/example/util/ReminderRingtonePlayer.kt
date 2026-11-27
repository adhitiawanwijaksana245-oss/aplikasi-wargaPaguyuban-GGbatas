package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pemutar suara notifikasi dan pratinjau nada lokal per perangkat
 * (menghormati mode senyap/getar perangkat dan pilihan Nada Notifikasi lokal).
 */
object ReminderRingtonePlayer {
    private const val TAG = "ReminderRingtonePlayer"
    private const val DEFAULT_MAX_DURATION_MS = 15_000L
    private const val PREVIEW_MAX_DURATION_MS = 5_000L

    private var mediaPlayer: MediaPlayer? = null
    private var fallbackRingtone: Ringtone? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val autoStopRunnable = Runnable { stop() }

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isPreviewPlaying = MutableStateFlow(false)
    val isPreviewPlaying: StateFlow<Boolean> = _isPreviewPlaying.asStateFlow()

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
            return false
        }

        if (NotificationToneManager.isSilentMode(appContext)) {
            return false
        }

        val targetUri = NotificationToneManager.resolveSoundUri(appContext)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return false

        return startPlayback(
            appContext = appContext,
            uri = targetUri,
            loop = loop,
            maxDurationMs = maxDurationMs,
            isPreview = false
        )
    }

    @Synchronized
    fun playPreview(
        context: Context,
        option: NotificationToneOption,
        customUri: String = "",
        maxDurationMs: Long = PREVIEW_MAX_DURATION_MS
    ): Boolean {
        stopInternal()

        val appContext = context.applicationContext
        if (option == NotificationToneOption.SILENT_VIBRATE) {
            NotificationToneManager.triggerShortVibrate(appContext)
            return false
        }

        val targetUri = NotificationToneManager.resolvePreviewSoundUri(appContext, option, customUri)
            ?: return false

        return startPlayback(
            appContext = appContext,
            uri = targetUri,
            loop = false,
            maxDurationMs = maxDurationMs,
            isPreview = true
        )
    }

    private fun startPlayback(
        appContext: Context,
        uri: Uri,
        loop: Boolean,
        maxDurationMs: Long,
        isPreview: Boolean
    ): Boolean {
        val player = try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(appContext, uri)
                prepare()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal memutar dengan MediaPlayer, mencoba RingtoneManager: ${e.message}")
            null
        }

        if (player != null) {
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
                if (isPreview) {
                    _isPreviewPlaying.value = true
                } else {
                    _isPlaying.value = true
                }

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
                _isPreviewPlaying.value = false
                false
            }
        }

        // Fallback via RingtoneManager untuk URI sistem tertentu
        return try {
            val ringtone = RingtoneManager.getRingtone(appContext, uri) ?: return false
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone.play()
            fallbackRingtone = ringtone
            if (isPreview) {
                _isPreviewPlaying.value = true
            } else {
                _isPlaying.value = true
            }
            mainHandler.removeCallbacks(autoStopRunnable)
            if (maxDurationMs > 0L) {
                mainHandler.postDelayed(autoStopRunnable, maxDurationMs)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Fallback RingtoneManager gagal: ${e.message}")
            _isPlaying.value = false
            _isPreviewPlaying.value = false
            false
        }
    }

    @Synchronized
    fun stopPreview() {
        if (_isPreviewPlaying.value) {
            stopInternal()
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
        val currentRingtone = fallbackRingtone
        fallbackRingtone = null
        if (currentRingtone != null) {
            try {
                if (currentRingtone.isPlaying) {
                    currentRingtone.stop()
                }
            } catch (_: Exception) {
            }
        }
        _isPlaying.value = false
        _isPreviewPlaying.value = false
    }
}

class StopRingtoneBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        ReminderRingtonePlayer.stop()
        NotificationHelper.silenceActiveRingtone(context)
    }
}
