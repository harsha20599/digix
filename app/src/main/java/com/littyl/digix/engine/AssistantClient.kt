package com.littyl.digix.engine

import android.util.Base64
import com.littyl.digix.BuildConfig
import com.littyl.digix.core.AssistantProvider
import com.littyl.digix.core.AssistantSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One thing the assistant said, and what it wants to happen next. */
data class AssistantReply(val heard: String, val say: String, val action: String?, val end: Boolean)

data class AssistantTurn(val user: String, val assistant: String)

enum class AssistantFailure { BAD_KEY, LIMIT_REACHED, NETWORK, UNREADABLE }

class AssistantException(val failure: AssistantFailure, message: String) : Exception(message)

/** Talks to the provider a person chose, with their own key. Nothing else ever sees the audio. */
class AssistantClient(private val settings: AssistantSettings) {

    suspend fun reply(wav: ByteArray, history: List<AssistantTurn>, instructions: String): AssistantReply =
        withContext(Dispatchers.IO) {
            if (settings.provider == AssistantProvider.GEMINI) {
                geminiReply(wav, history, instructions)
            } else {
                val heard = transcribe(wav)
                openAiReply(heard, history, instructions)
            }
        }

    /** Proves the key and model work, without audio. */
    suspend fun check() = withContext(Dispatchers.IO) {
        val instructions = """Reply with JSON only: {"heard":"","say":"ok","action":null,"end":false}"""
        if (settings.provider == AssistantProvider.GEMINI) {
            val body = JSONObject()
                .put("contents", JSONArray().put(userText("Say ok.")))
                .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instructions))))
            post(geminiUrl(), body.toString(), geminiHeaders())
        } else {
            openAiReply("Say ok.", emptyList(), instructions)
        }
        Unit
    }

    private fun geminiUrl() =
        "${settings.baseUrl}/models/${URLEncoder.encode(settings.chatModel, "UTF-8")}:generateContent"

    private fun geminiHeaders() = mapOf("x-goog-api-key" to settings.apiKey, "Content-Type" to "application/json")

    private fun userText(text: String) =
        JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", text)))

    private fun geminiReply(wav: ByteArray, history: List<AssistantTurn>, instructions: String): AssistantReply {
        val contents = JSONArray()
        history.forEach { turn ->
            contents.put(userText(turn.user))
            contents.put(
                JSONObject().put("role", "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", turn.assistant))),
            )
        }
        val audio = JSONObject().put(
            "inline_data",
            JSONObject().put("mime_type", "audio/wav").put("data", Base64.encodeToString(wav, Base64.NO_WRAP)),
        )
        contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(audio)))
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instructions))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
        val response = JSONObject(post(geminiUrl(), body.toString(), geminiHeaders()))
        val text = response.optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
            ?: throw AssistantException(AssistantFailure.UNREADABLE, "no text in reply")
        return parse(text, "")
    }

    private fun transcribe(wav: ByteArray): String {
        val model = settings.speechModel ?: throw AssistantException(AssistantFailure.UNREADABLE, "no speech model")
        val boundary = "digix${System.nanoTime()}"
        val out = ByteArrayOutputStream()
        fun part(text: String) = out.write(text.toByteArray())
        part("--$boundary\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\n$model\r\n")
        part("--$boundary\r\nContent-Disposition: form-data; name=\"response_format\"\r\n\r\njson\r\n")
        part("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\nContent-Type: audio/wav\r\n\r\n")
        out.write(wav)
        part("\r\n--$boundary--\r\n")
        val response = postBytes(
            "${settings.baseUrl}/audio/transcriptions",
            out.toByteArray(),
            mapOf("Authorization" to "Bearer ${settings.apiKey}", "Content-Type" to "multipart/form-data; boundary=$boundary"),
        )
        return JSONObject(response).optString("text").trim()
    }

    private fun openAiReply(heard: String, history: List<AssistantTurn>, instructions: String): AssistantReply {
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", instructions))
        history.forEach { turn ->
            messages.put(JSONObject().put("role", "user").put("content", turn.user))
            messages.put(JSONObject().put("role", "assistant").put("content", turn.assistant))
        }
        messages.put(JSONObject().put("role", "user").put("content", heard))
        val body = JSONObject()
            .put("model", settings.chatModel)
            .put("messages", messages)
            .put("response_format", JSONObject().put("type", "json_object"))
        val response = JSONObject(
            post(
                "${settings.baseUrl}/chat/completions",
                body.toString(),
                mapOf("Authorization" to "Bearer ${settings.apiKey}", "Content-Type" to "application/json"),
            ),
        )
        val text = response.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
            ?: throw AssistantException(AssistantFailure.UNREADABLE, "no text in reply")
        return parse(text, heard)
    }

    /** Models occasionally wrap JSON in other text, or skip it. Plain text is still worth saying. */
    private fun parse(text: String, heard: String): AssistantReply {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        val json = if (start >= 0 && end > start) runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull() else null
        return if (json == null) {
            AssistantReply(heard, text.trim(), null, false)
        } else {
            AssistantReply(
                heard = json.optString("heard").ifBlank { heard },
                say = json.optString("say").trim(),
                action = json.optString("action").takeIf { it.isNotBlank() && it != "null" },
                end = json.optBoolean("end", false),
            )
        }
    }

    private fun post(url: String, body: String, headers: Map<String, String>) =
        postBytes(url, body.toByteArray(), headers)

    private fun postBytes(url: String, body: ByteArray, headers: Map<String, String>): String {
        try {
            val endpoint = URL(url)
            val secureProvider = BuildConfig.PLAY_EDITION && !BuildConfig.DEBUG
            if (secureProvider && endpoint.protocol != "https") {
                throw AssistantException(AssistantFailure.NETWORK, "The Play edition requires an HTTPS provider")
            }
            val connection = endpoint.openConnection() as HttpURLConnection
            if (secureProvider) connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            if (status in 200..299) return connection.inputStream.bufferedReader().use { it.readText() }
            connection.errorStream?.close()
            throw when (status) {
                400, 401, 403 -> AssistantException(AssistantFailure.BAD_KEY, "status $status")
                429 -> AssistantException(AssistantFailure.LIMIT_REACHED, "status $status")
                else -> AssistantException(AssistantFailure.NETWORK, "status $status")
            }
        } catch (e: IOException) {
            throw AssistantException(AssistantFailure.NETWORK, e.javaClass.simpleName)
        } catch (e: IllegalArgumentException) {
            throw AssistantException(AssistantFailure.NETWORK, "bad address")
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 25_000
    }
}
