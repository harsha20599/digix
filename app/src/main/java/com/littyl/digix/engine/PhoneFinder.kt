package com.littyl.digix.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator

/** Rings at full volume on the alarm stream, so it is heard even on silent. */
class PhoneFinder(private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var previousVolume = -1
    private var timeout: Runnable? = null

    val isRinging: Boolean get() = player != null

    fun start(onTimeout: () -> Unit) {
        if (isRinging) return
        previousVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        player = createPlayer()
        val pattern = VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(pattern, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
        timeout = Runnable(onTimeout).also { handler.postDelayed(it, MAX_RING_MS) }
    }

    fun stop() {
        timeout?.let(handler::removeCallbacks)
        timeout = null
        vibrator.cancel()
        player?.let { active ->
            runCatching { active.stop() }
            active.release()
        }
        player = null
        if (previousVolume >= 0) {
            runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, previousVolume, 0) }
            previousVolume = -1
        }
    }

    private fun createPlayer(): MediaPlayer? {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: return null
        return runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(context, uri)
                isLooping = true
                // Loading can be slow, and must not hold up the service or its report to the watch.
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
        }.getOrNull()
    }

    private companion object {
        const val MAX_RING_MS = 120_000L
    }
}
