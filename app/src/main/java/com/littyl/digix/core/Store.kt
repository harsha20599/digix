package com.littyl.digix.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** The app's only source of truth. Everything runs in one process, so this is safe to share. */
object Store {
    private const val MAX_LOG = 100

    private lateinit var prefs: SharedPreferences

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled

    private val _codes = MutableStateFlow<List<CodeBinding>>(emptyList())
    val codes: StateFlow<List<CodeBinding>> = _codes

    private val _log = MutableStateFlow<List<RunEntry>>(emptyList())
    val log: StateFlow<List<RunEntry>> = _log

    private val _onboarded = MutableStateFlow(false)
    val onboarded: StateFlow<Boolean> = _onboarded

    private val _haptics = MutableStateFlow(true)
    val haptics: StateFlow<Boolean> = _haptics

    private val _spokenReplies = MutableStateFlow(true)

    /** Whether a dialed code is answered with a short spoken call. */
    val spokenReplies: StateFlow<Boolean> = _spokenReplies

    private val _tileLabels = MutableStateFlow<Map<ActionType, String>>(emptyMap())
    val tileLabels: StateFlow<Map<ActionType, String>> = _tileLabels

    private val _undoable = MutableStateFlow<Pair<CodeBinding, Int>?>(null)

    /** The most recently deleted code and where it was, until it is restored or dismissed. */
    val undoable: StateFlow<Pair<CodeBinding, Int>?> = _undoable

    private val _entries = MutableSharedFlow<RunEntry>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Emits each finished run, so an open screen can react to the real outcome. */
    val entries: SharedFlow<RunEntry> = _entries

    fun init(context: Context) {
        prefs = context.getSharedPreferences("digix", Context.MODE_PRIVATE)
        _enabled.value = prefs.getBoolean("enabled", true)
        _onboarded.value = prefs.getBoolean("onboarded", false)
        _haptics.value = prefs.getBoolean("haptics", true)
        _spokenReplies.value = prefs.getBoolean("spoken_replies", true)
        _codes.value = readCodes()
        _log.value = readLog()
        _tileLabels.value = readTileLabels()
    }

    fun find(code: String): CodeBinding? = _codes.value.firstOrNull { it.code == code }

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        prefs.edit().putBoolean("enabled", value).apply()
    }

    fun setOnboarded(value: Boolean) {
        _onboarded.value = value
        prefs.edit().putBoolean("onboarded", value).apply()
    }

    fun setSpokenReplies(value: Boolean) {
        _spokenReplies.value = value
        prefs.edit().putBoolean("spoken_replies", value).apply()
    }

    fun setHaptics(value: Boolean) {
        _haptics.value = value
        prefs.edit().putBoolean("haptics", value).apply()
    }

    fun setTileLabel(action: ActionType, label: String) {
        _tileLabels.value = _tileLabels.value + (action to label)
        val json = JSONObject()
        _tileLabels.value.forEach { (key, value) -> json.put(key.name, value) }
        prefs.edit().putString("tile_labels", json.toString()).apply()
    }

    /** Sets up default codes once, ever. Replaying the introduction must not change anything. */
    fun prepareFirstRun() {
        if (prefs.getBoolean("first_run_done", false)) return
        setEnabled(true)
        writeCodes(
            listOf(
                CodeBinding("0101", ActionType.FLASHLIGHT),
                CodeBinding("0102", ActionType.FIND_PHONE),
                CodeBinding("0103", ActionType.BATTERY),
                CodeBinding("0104", ActionType.PLAY_PAUSE),
            ),
        )
        // Marked last: if the app dies part way through, the next launch simply sets up again.
        prefs.edit().putBoolean("first_run_done", true).commit()
    }

    /** Adds a binding, or replaces the one currently stored under [previousCode]. */
    fun save(binding: CodeBinding, previousCode: String? = null) {
        val list = _codes.value.toMutableList()
        val index = list.indexOfFirst { it.code == (previousCode ?: binding.code) }
        if (index >= 0) list[index] = binding else list += binding
        writeCodes(list)
    }

    fun delete(code: String) {
        val index = _codes.value.indexOfFirst { it.code == code }
        if (index < 0) return
        _undoable.value = _codes.value[index] to index
        writeCodes(_codes.value.filterNot { it.code == code })
    }

    fun undoDelete() {
        val (binding, index) = _undoable.value ?: return
        _undoable.value = null
        // The same code may have been added again since; a code must stay unique.
        if (_codes.value.any { it.code == binding.code }) return
        val list = _codes.value.toMutableList()
        list.add(index.coerceAtMost(list.size), binding)
        writeCodes(list)
    }

    fun dismissUndo() {
        _undoable.value = null
    }

    fun record(entry: RunEntry) {
        val updated = (listOf(entry) + _log.value).take(MAX_LOG)
        _log.value = updated
        val json = JSONArray()
        updated.forEach { item ->
            json.put(
                JSONObject()
                    .put("t", item.timeMillis)
                    .put("c", item.code)
                    .put("a", item.action.name)
                    .put("s", item.status.name)
                    .put("m", item.message),
            )
        }
        // commit, not apply: the process may be killed right after a run finishes.
        prefs.edit().putString("log", json.toString()).commit()
        _entries.tryEmit(entry)
    }

    fun clearLog() {
        _log.value = emptyList()
        prefs.edit().remove("log").apply()
    }

    fun lastRun(code: String): RunEntry? = _log.value.firstOrNull { it.code == code }

    private fun writeCodes(list: List<CodeBinding>) {
        _codes.value = list
        val json = JSONArray()
        list.forEach { item ->
            json.put(
                JSONObject()
                    .put("c", item.code)
                    .put("a", item.action.name)
                    .apply {
                        item.webAddress?.let { put("w", it) }
                        item.phone?.let { put("p", it) }
                        item.contactName?.let { put("n", it) }
                        item.message?.let { put("m", it) }
                        item.homeDeviceId?.let { put("hd", it) }
                        item.homeDeviceName?.let { put("hn", it) }
                        item.homeCommand?.let { put("hc", it.name) }
                    },
            )
        }
        // commit, not apply: a code saved a moment before dialing must be there if the process restarts.
        prefs.edit().putString("codes", json.toString()).commit()
    }

    private fun readCodes(): List<CodeBinding> = parseArray("codes") { item ->
        CodeBinding(
            code = item.getString("c"),
            action = ActionType.valueOf(item.getString("a")),
            webAddress = item.optString("w").ifEmpty { null },
            phone = item.optString("p").ifEmpty { null },
            contactName = item.optString("n").ifEmpty { null },
            message = item.optString("m").ifEmpty { null },
            homeDeviceId = item.optString("hd").ifEmpty { null },
            homeDeviceName = item.optString("hn").ifEmpty { null },
            homeCommand = item.optString("hc").ifEmpty { null }?.let { name -> runCatching { HomeCommand.valueOf(name) }.getOrNull() },
        )
    }

    private fun readLog(): List<RunEntry> = parseArray("log") { item ->
        RunEntry(
            timeMillis = item.getLong("t"),
            code = item.getString("c"),
            action = ActionType.valueOf(item.getString("a")),
            status = RunStatus.valueOf(item.getString("s")),
            message = item.getString("m"),
        )
    }

    private fun readTileLabels(): Map<ActionType, String> {
        val json = runCatching { JSONObject(prefs.getString("tile_labels", "{}")!!) }.getOrNull() ?: return emptyMap()
        return json.keys().asSequence().mapNotNull { key ->
            runCatching { ActionType.valueOf(key) to json.getString(key) }.getOrNull()
        }.toMap()
    }

    private fun <T> parseArray(key: String, parse: (JSONObject) -> T): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i -> runCatching { parse(array.getJSONObject(i)) }.getOrNull() }
    }
}
