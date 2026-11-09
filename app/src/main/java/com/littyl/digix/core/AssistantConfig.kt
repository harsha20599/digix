package com.littyl.digix.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class AssistantProvider(
    val label: String,
    val keyPage: String?,
    val baseUrl: String,
    val chatModel: String,
    val speechModel: String?,
) {
    GEMINI(
        label = "Gemini",
        keyPage = "https://aistudio.google.com/apikey",
        baseUrl = "https://generativelanguage.googleapis.com/v1beta",
        chatModel = "gemini-3.5-flash-lite",
        speechModel = null,
    ),
    GROQ(
        label = "Groq",
        keyPage = "https://console.groq.com/keys",
        baseUrl = "https://api.groq.com/openai/v1",
        chatModel = "llama-3.3-70b-versatile",
        speechModel = "whisper-large-v3-turbo",
    ),

    /** Any service with OpenAI-style chat and transcription endpoints. */
    CUSTOM(
        label = "Other",
        keyPage = null,
        baseUrl = "",
        chatModel = "",
        speechModel = "whisper-1",
    ),
}

data class AssistantSettings(
    val provider: AssistantProvider,
    val baseUrl: String,
    val chatModel: String,
    val speechModel: String?,
    val apiKey: String,
)

/** The voice assistant's setup. The key is encrypted with a key held by Android's keystore. */
object AssistantConfig {
    private const val ALIAS = "digix_assistant"
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    private val _configured = MutableStateFlow(false)
    val configured: StateFlow<Boolean> = _configured

    private fun prefs(context: Context) = context.getSharedPreferences("assistant", Context.MODE_PRIVATE)

    private fun isDebuggable(context: Context) =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    fun refresh(context: Context) {
        _configured.value = load(context) != null
    }

    fun load(context: Context): AssistantSettings? {
        val stored = prefs(context)
        val provider = stored.getString("provider", null)
            ?.let { name -> runCatching { AssistantProvider.valueOf(name) }.getOrNull() }
            ?: return null
        // The test suite can't reach the keystore, so debuggable builds accept a plain test key.
        val testKey = if (isDebuggable(context)) stored.getString("test_key", null) else null
        val key = testKey ?: stored.getString("key", null)?.let(::decrypt) ?: return null
        val baseUrl = stored.getString("base_url", null) ?: provider.baseUrl
        if (baseUrl.isBlank()) return null
        return AssistantSettings(
            provider = provider,
            baseUrl = baseUrl.trimEnd('/'),
            chatModel = stored.getString("chat_model", null) ?: provider.chatModel,
            speechModel = stored.getString("speech_model", null) ?: provider.speechModel,
            apiKey = key,
        )
    }

    fun save(context: Context, settings: AssistantSettings) {
        prefs(context).edit()
            .putString("provider", settings.provider.name)
            .putString("base_url", settings.baseUrl)
            .putString("chat_model", settings.chatModel)
            .putString("speech_model", settings.speechModel)
            .putString("key", encrypt(settings.apiKey))
            .commit()
        _configured.value = true
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().commit()
        _configured.value = false
    }

    /** Debuggable builds only: lets the test suite feed recorded speech instead of a microphone. */
    fun usesTestAudio(context: Context): Boolean =
        isDebuggable(context) && prefs(context).getBoolean("test_audio", false)

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String? = runCatching {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, bytes, 0, GCM_IV_BYTES))
        String(cipher.doFinal(bytes, GCM_IV_BYTES, bytes.size - GCM_IV_BYTES))
    }.getOrNull()
}
