package com.littyl.digix.engine

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Records one spoken turn from the call's microphone. It learns the room's noise level first, then
 * starts at speech and stops after a pause, so people talk naturally and press nothing.
 */
class VoiceListener(private val useTestAudio: Boolean) {

    /** WAV audio of what was said, or null if nobody spoke. */
    @SuppressLint("MissingPermission") // Checked by the caller before a conversation starts.
    suspend fun listen(): ByteArray? = withContext(Dispatchers.IO) {
        if (useTestAudio) return@withContext wav(ShortArray(RATE))
        val minimum = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            max(minimum, RATE),
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return@withContext null
        }
        try {
            recorder.startRecording()
            capture(recorder)
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
    }

    private suspend fun capture(recorder: AudioRecord): ByteArray? {
        val frame = ShortArray(FRAME_SAMPLES)
        val speech = ArrayList<ShortArray>()
        var noise = 0.0
        var frames = 0
        var heardSpeech = false
        var quietFrames = 0
        while (true) {
            coroutineContext.ensureActive()
            val read = recorder.read(frame, 0, frame.size)
            if (read <= 0) return null
            frames++
            val level = rms(frame, read)
            if (frames <= CALIBRATION_FRAMES) {
                noise = max(noise, level)
                continue
            }
            val threshold = max(MIN_SPEECH_LEVEL, noise * NOISE_MARGIN)
            if (level >= threshold) {
                heardSpeech = true
                quietFrames = 0
            } else if (heardSpeech) {
                quietFrames++
            }
            if (heardSpeech) speech.add(frame.copyOf(read))
            when {
                heardSpeech && quietFrames >= END_QUIET_FRAMES -> return wav(speech.flatten())
                heardSpeech && speech.size >= MAX_SPEECH_FRAMES -> return wav(speech.flatten())
                !heardSpeech && frames >= MAX_WAIT_FRAMES -> return null
            }
        }
    }

    private fun List<ShortArray>.flatten(): ShortArray {
        val all = ShortArray(sumOf { it.size })
        var position = 0
        forEach { part ->
            part.copyInto(all, position)
            position += part.size
        }
        return all
    }

    private fun rms(samples: ShortArray, count: Int): Double {
        var sum = 0.0
        for (i in 0 until count) sum += samples[i].toDouble() * samples[i]
        return sqrt(sum / count)
    }

    private fun wav(samples: ShortArray): ByteArray {
        val dataSize = samples.size * 2
        val header = ByteBuffer.allocate(WAV_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(WAV_HEADER_BYTES - 8 + dataSize).put("WAVE".toByteArray())
        header.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
        header.putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
        header.put("data".toByteArray()).putInt(dataSize)
        val data = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { data.putShort(it) }
        return ByteArrayOutputStream(WAV_HEADER_BYTES + dataSize).apply {
            write(header.array())
            write(data.array())
        }.toByteArray()
    }

    private companion object {
        const val RATE = 16_000
        const val FRAME_SAMPLES = 480 // 30 ms.
        const val WAV_HEADER_BYTES = 44
        const val CALIBRATION_FRAMES = 10 // 0.3 s.
        const val END_QUIET_FRAMES = 30 // 0.9 s.
        const val MAX_SPEECH_FRAMES = 666 // 20 s.
        const val MAX_WAIT_FRAMES = 267 // 8 s.
        const val MIN_SPEECH_LEVEL = 300.0
        const val NOISE_MARGIN = 2.5
    }
}
