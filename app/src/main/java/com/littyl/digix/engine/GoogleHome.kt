package com.littyl.digix.engine

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import com.littyl.digix.core.HomeCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A device in the person's home that can be switched on and off. */
data class HomeDeviceInfo(val id: String, val name: String, val room: String?)

enum class HomeConnectResult { CONNECTED, CANCELLED, FAILED }

enum class HomeSendResult { ON, OFF, SWITCHED, NOT_CONNECTED, DEVICE_GONE, UNREACHABLE, FAILED }

/** Everything Digix needs from a smart home. Each build supplies one through `createHomeBackend`. */
interface HomeBackend {
    /** Must run while the activity is being created, because the permission screen depends on it. */
    fun attach(activity: ComponentActivity)

    /** Null means the answer isn't known right now, which is different from not connected. */
    suspend fun isConnected(): Boolean?

    suspend fun connect(changeHome: Boolean): HomeConnectResult

    /** Null means the devices couldn't be loaded. */
    suspend fun devices(): List<HomeDeviceInfo>?

    suspend fun send(deviceId: String, command: HomeCommand): HomeSendResult
}

/** What the rest of the app talks to. Remembers the connection, so readiness never has to wait. */
object GoogleHome {
    private const val CONNECTED = "connected"

    private var backend: HomeBackend? = null
    private lateinit var prefs: SharedPreferences

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    /** False in builds without Google's library. */
    val available: Boolean get() = backend != null

    fun init(context: Context) {
        backend = createHomeBackend(context.applicationContext)
        prefs = context.getSharedPreferences("digix_home", Context.MODE_PRIVATE)
        _connected.value = available && prefs.getBoolean(CONNECTED, false)
    }

    fun attach(activity: ComponentActivity) {
        backend?.attach(activity)
    }

    /** Access can be taken away in Google Home at any time, so this looks again. */
    suspend fun refresh() {
        backend?.isConnected()?.let(::remember)
    }

    suspend fun connect(changeHome: Boolean = false): HomeConnectResult {
        val result = backend?.connect(changeHome) ?: HomeConnectResult.FAILED
        if (result == HomeConnectResult.CONNECTED) remember(true)
        return result
    }

    suspend fun devices(): List<HomeDeviceInfo>? = backend?.devices()

    suspend fun send(deviceId: String, command: HomeCommand): HomeSendResult {
        val result = backend?.send(deviceId, command) ?: HomeSendResult.NOT_CONNECTED
        if (result == HomeSendResult.NOT_CONNECTED) remember(false)
        return result
    }

    private fun remember(connected: Boolean) {
        _connected.value = connected
        prefs.edit().putBoolean(CONNECTED, connected).apply()
    }
}
