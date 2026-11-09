package com.littyl.digix.engine

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import com.littyl.digix.core.HomeCommand
import com.google.home.FactoryRegistry
import com.google.home.HasId
import com.google.home.ForcePermissionFlow
import com.google.home.Home
import com.google.home.HomeClient
import com.google.home.HomeConfig
import com.google.home.HomeDevice
import com.google.home.HomeException
import com.google.home.HomeObjectsFlow
import com.google.home.PermissionsResultStatus
import com.google.home.PermissionsState
import com.google.home.google.GoogleAirCoolerDevice
import com.google.home.matter.standard.ColorTemperatureLightDevice
import com.google.home.matter.standard.DimmableLightDevice
import com.google.home.matter.standard.DimmablePlugInUnitDevice
import com.google.home.matter.standard.ExtendedColorLightDevice
import com.google.home.matter.standard.FanDevice
import com.google.home.matter.standard.HeatingCoolingUnitDevice
import com.google.home.matter.standard.OnOff
import com.google.home.matter.standard.OnOffLightDevice
import com.google.home.matter.standard.OnOffPluginUnitDevice
import com.google.home.matter.standard.RoomAirConditionerDevice
import com.google.home.matter.standard.ThermostatDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

internal fun createHomeBackend(context: Context): HomeBackend? = SdkHomeBackend(context)

/** Google's library, for lights, plugs, fans and air conditioners. Only on and off for now. */
private class SdkHomeBackend(private val context: Context) : HomeBackend {
    // Google asks for exactly one instance, and building it isn't free, so it waits until needed.
    private val client: HomeClient by lazy {
        Home.getClient(
            context,
            HomeConfig(
                coroutineContext = Dispatchers.IO,
                factoryRegistry = FactoryRegistry(
                    traits = listOf(OnOff),
                    types = listOf(
                        OnOffLightDevice,
                        DimmableLightDevice,
                        ColorTemperatureLightDevice,
                        ExtendedColorLightDevice,
                        OnOffPluginUnitDevice,
                        DimmablePlugInUnitDevice,
                        FanDevice,
                        RoomAirConditionerDevice,
                        GoogleAirCoolerDevice,
                        HeatingCoolingUnitDevice,
                        ThermostatDevice,
                    ),
                ),
                // The newer scope needs approval from Google, and nothing here uses it.
                homePlatformScope = HomeConfig.HomePlatformScope.HOME_PLATFORM_SCOPE_VERSION_1,
            ),
        )
    }

    override fun attach(activity: ComponentActivity) {
        // A phone without Google Play services can't build the client, and must still open Digix.
        runCatching { client.registerActivityResultCallerForPermissions(activity) }
    }

    override suspend fun isConnected(): Boolean? = try {
        val state = withTimeoutOrNull(STATE_TIMEOUT_MS) {
            client.hasPermissions().first { it != PermissionsState.PERMISSIONS_STATE_UNINITIALIZED }
        }
        when (state) {
            PermissionsState.GRANTED -> true
            PermissionsState.NOT_GRANTED -> false
            else -> null
        }
    } catch (_: HomeException) {
        null
    }

    override suspend fun connect(changeHome: Boolean): HomeConnectResult = try {
        val result = if (changeHome) client.requestPermissions(ForcePermissionFlow.FORCE_LAUNCH) else client.requestPermissions()
        when (result.status) {
            PermissionsResultStatus.SUCCESS -> HomeConnectResult.CONNECTED
            PermissionsResultStatus.CANCELLED -> HomeConnectResult.CANCELLED
            else -> HomeConnectResult.FAILED
        }
    } catch (_: HomeException) {
        HomeConnectResult.FAILED
    }

    override suspend fun devices(): List<HomeDeviceInfo>? = try {
        val all = loaded(client.devices())
        val rooms = loaded(client.rooms()).associate { it.id.id to it.name }
        // On and off belong to a device's types, not the device itself.
        val switchable = all.filter { onOffOf(it) != null }
        Log.i(TAG, "${all.size} devices, ${switchable.size} switch")
        switchable
            .map { HomeDeviceInfo(it.id.id, it.name, it.roomId?.let { room -> rooms[room.id] }?.ifBlank { null }) }
            .sortedWith(compareBy({ it.room == null }, { it.room }, { it.name }))
    } catch (_: HomeException) {
        null
    }

    override suspend fun send(deviceId: String, command: HomeCommand): HomeSendResult = try {
        // Right after the phone starts, Google's copy of the home is still loading, so wait for the device to appear.
        val device = withTimeoutOrNull(LOAD_TIMEOUT_MS) {
            client.devices().first { all -> all.any { it.id.id == deviceId } }
        }?.firstOrNull { it.id.id == deviceId }
        val onOff = device?.let { onOffOf(it) }
        when {
            device == null -> HomeSendResult.DEVICE_GONE
            onOff == null -> HomeSendResult.FAILED
            command == HomeCommand.ON -> {
                onOff.on()
                HomeSendResult.ON
            }
            command == HomeCommand.OFF -> {
                onOff.off()
                HomeSendResult.OFF
            }
            // Known state means the spoken reply can say which way it went.
            onOff.onOff == true -> {
                onOff.off()
                HomeSendResult.OFF
            }
            onOff.onOff == false -> {
                onOff.on()
                HomeSendResult.ON
            }
            else -> {
                onOff.toggle()
                HomeSendResult.SWITCHED
            }
        }
    } catch (e: HomeException) {
        failureOf(e)
    }

    /** Google fills in the home after connecting, so an empty first answer can mean it hasn't loaded yet. */
    private suspend fun <T : HasId> loaded(objects: HomeObjectsFlow<T>): Set<T> =
        withTimeoutOrNull(LOAD_TIMEOUT_MS) { objects.first { it.isNotEmpty() } } ?: objects.list()

    /** A device can be several things at once, so this finds the part of it that switches. */
    private suspend fun onOffOf(device: HomeDevice): OnOff? =
        device.types().first().firstNotNullOfOrNull { it.trait(OnOff) }

    private fun failureOf(e: HomeException): HomeSendResult = when (e.unwrap().error.code) {
        HomeException.Codes.UNAUTHENTICATED, HomeException.Codes.PERMISSION_DENIED -> HomeSendResult.NOT_CONNECTED
        HomeException.Codes.NOT_FOUND -> HomeSendResult.DEVICE_GONE
        HomeException.Codes.UNAVAILABLE, HomeException.Codes.API_NOT_CONNECTED, HomeException.Codes.DEADLINE_EXCEEDED -> HomeSendResult.UNREACHABLE
        else -> HomeSendResult.FAILED
    }

    private companion object {
        const val TAG = "DigixHome"
        const val STATE_TIMEOUT_MS = 5_000L
        const val LOAD_TIMEOUT_MS = 8_000L
    }
}
