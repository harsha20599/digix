package com.littyl.digix.engine

import android.content.Context
import androidx.activity.ComponentActivity
import com.littyl.digix.core.HomeCommand
import kotlinx.coroutines.delay

/** A pretend home for emulators, which can't run Google's library. Never part of a real build. */
internal fun createHomeBackend(context: Context): HomeBackend? = FakeHomeBackend(context)

private class FakeHomeBackend(context: Context) : HomeBackend {
    private val prefs = context.getSharedPreferences("digix_fake_home", Context.MODE_PRIVATE)

    override fun attach(activity: ComponentActivity) = Unit

    override suspend fun isConnected(): Boolean = prefs.getBoolean(GRANTED, false)

    override suspend fun connect(changeHome: Boolean): HomeConnectResult {
        delay(NETWORK_MS)
        prefs.edit().putBoolean(GRANTED, true).commit()
        return HomeConnectResult.CONNECTED
    }

    override suspend fun devices(): List<HomeDeviceInfo> {
        delay(NETWORK_MS)
        return listOf(
            HomeDeviceInfo("ac", "Air conditioner", "Bedroom"),
            HomeDeviceInfo("light", "Bedroom light", "Bedroom"),
            HomeDeviceInfo("offline", "Porch light", "Outside"),
            HomeDeviceInfo("hang", "Old plug", null),
        )
    }

    override suspend fun send(deviceId: String, command: HomeCommand): HomeSendResult {
        if (!prefs.getBoolean(GRANTED, false)) return HomeSendResult.NOT_CONNECTED
        delay(NETWORK_MS)
        return when (deviceId) {
            "offline" -> HomeSendResult.UNREACHABLE
            "hang" -> {
                delay(Long.MAX_VALUE)
                HomeSendResult.FAILED
            }
            "ac", "light" -> {
                val on = when (command) {
                    HomeCommand.ON -> true
                    HomeCommand.OFF -> false
                    HomeCommand.TOGGLE -> !prefs.getBoolean(deviceId, false)
                }
                prefs.edit().putBoolean(deviceId, on).commit()
                if (on) HomeSendResult.ON else HomeSendResult.OFF
            }
            else -> HomeSendResult.DEVICE_GONE
        }
    }

    private companion object {
        const val GRANTED = "granted"
        const val NETWORK_MS = 400L
    }
}
