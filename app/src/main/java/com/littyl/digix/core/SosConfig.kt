package com.littyl.digix.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

data class SosContact(val name: String, val phone: String)

data class SosSettings(
    val contacts: List<SosContact> = emptyList(),
    val message: String = "",
    val callFirstContact: Boolean = false,
)

/** SOS setup, and whether an alert is running. Both survive the app being killed. */
object SosConfig {
    private val _settings = MutableStateFlow(SosSettings())
    val settings: StateFlow<SosSettings> = _settings

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active

    private fun prefs(context: Context) = context.getSharedPreferences("sos", Context.MODE_PRIVATE)

    fun init(context: Context) {
        val stored = prefs(context)
        val contacts = runCatching {
            val array = JSONArray(stored.getString("contacts", "[]"))
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                SosContact(item.getString("n"), item.getString("p"))
            }
        }.getOrDefault(emptyList())
        _settings.value = SosSettings(
            contacts = contacts,
            message = stored.getString("message", "").orEmpty(),
            callFirstContact = stored.getBoolean("call_first", false),
        )
        _active.value = activeUntil(context) > System.currentTimeMillis()
    }

    fun save(context: Context, settings: SosSettings) {
        _settings.value = settings
        val contacts = JSONArray()
        settings.contacts.forEach { contact -> contacts.put(JSONObject().put("n", contact.name).put("p", contact.phone)) }
        prefs(context).edit()
            .putString("contacts", contacts.toString())
            .putString("message", settings.message)
            .putBoolean("call_first", settings.callFirstContact)
            .commit()
    }

    /** When the running alert ends by itself, or 0 when none is running. */
    fun activeUntil(context: Context): Long = prefs(context).getLong("active_until", 0)

    fun lastUpdate(context: Context): Long = prefs(context).getLong("last_update", 0)

    fun markUpdate(context: Context, time: Long) {
        prefs(context).edit().putLong("last_update", time).commit()
    }

    fun markActive(context: Context, until: Long) {
        prefs(context).edit().putLong("active_until", until).commit()
        _active.value = true
    }

    fun markStopped(context: Context) {
        prefs(context).edit().remove("active_until").remove("last_update").commit()
        _active.value = false
    }
}
