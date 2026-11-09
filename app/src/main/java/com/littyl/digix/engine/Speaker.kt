package com.littyl.digix.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale

/**
 * Says a short message on the current call. Speech is rendered to a file once and played by this
 * app as call audio, so it follows the call to the watch. Repeat messages skip the slow rendering.
 * Every failure is quiet: the result still reaches the watch as a notification.
 */
class Speaker(private val context: Context) {
    private var player: MediaPlayer? = null
    private var engine: TextToSpeech? = null

    /** Set [keep] false for one-off speech, such as a conversation, so it isn't left on disk. */
    suspend fun speak(message: String, keep: Boolean = true) {
        val file = withTimeoutOrNull(RENDER_TIMEOUT_MS) { render(message) } ?: return
        withTimeoutOrNull(maxOf(PLAY_TIMEOUT_MS, message.length * MS_PER_CHARACTER)) { play(file) }
        stopPlayback()
        if (!keep) file.delete()
    }

    fun stop() {
        stopPlayback()
        engine?.shutdown()
        engine = null
    }

    private fun stopPlayback() {
        player?.let { active ->
            runCatching { active.stop() }
            active.release()
        }
        player = null
    }

    /** The engine is slow to start, so a conversation keeps it for every turn. */
    private suspend fun readyEngine(): TextToSpeech? {
        engine?.let { return it }
        val ready = CompletableDeferred<Boolean>()
        val created = TextToSpeech(context.applicationContext) { status -> ready.complete(status == TextToSpeech.SUCCESS) }
        if (!ready.await()) {
            created.shutdown()
            return null
        }
        val locale = Locale.getDefault()
        if (created.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) created.language = locale
        engine = created
        return created
    }

    private suspend fun render(message: String): File? {
        val locale = Locale.getDefault()
        val directory = File(context.cacheDir, "speech").apply { mkdirs() }
        val file = File(directory, "${(locale.toLanguageTag() + message).hashCode()}.wav")
        if (file.length() > 0) return file
        val tts = readyEngine() ?: return null

        val rendered = CompletableDeferred<Boolean>()
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    rendered.complete(true)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    rendered.complete(false)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    rendered.complete(false)
                }
            },
        )
        val temporary = File(directory, "${file.name}.part")
        val started = tts.synthesizeToFile(message, null, temporary, UTTERANCE) == TextToSpeech.SUCCESS
        if (!started || !rendered.await()) {
            temporary.delete()
            return null
        }
        return if (temporary.renameTo(file)) file else null
    }

    private suspend fun play(file: File) = withContext(Dispatchers.Main.immediate) {
        val finished = CompletableDeferred<Unit>()
        val media = MediaPlayer()
        player = media
        media.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        media.setOnPreparedListener { it.start() }
        media.setOnCompletionListener { finished.complete(Unit) }
        media.setOnErrorListener { _, _, _ ->
            finished.complete(Unit)
            true
        }
        try {
            media.setDataSource(file.path)
            media.prepareAsync()
            finished.await()
        } catch (_: java.io.IOException) {
            // Nothing to play. The notification still carries the result.
        } catch (_: IllegalStateException) {
            // The call ended and released the player first.
        }
    }

    private companion object {
        const val UTTERANCE = "digix"
        const val RENDER_TIMEOUT_MS = 6_000L
        const val PLAY_TIMEOUT_MS = 10_000L
        const val MS_PER_CHARACTER = 120L
    }
}
