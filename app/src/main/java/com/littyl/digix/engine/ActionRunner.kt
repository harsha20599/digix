package com.littyl.digix.engine

import android.Manifest
import android.app.KeyguardManager
import android.content.pm.PackageManager
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.view.KeyEvent
import com.littyl.digix.R
import com.littyl.digix.core.ActionType
import com.littyl.digix.core.CodeBinding
import com.littyl.digix.core.HomeCommand
import com.littyl.digix.core.RunStatus
import com.littyl.digix.ui.Readiness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.URL

/** Runs the actions that finish immediately and need nothing kept alive afterward. */
class ActionRunner(private val context: Context) {

    suspend fun run(binding: CodeBinding): ActionOutcome = try {
        when (binding.action) {
            ActionType.BATTERY -> battery()
            ActionType.PLAY_PAUSE -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, R.string.result_play_pause)
            ActionType.NEXT_TRACK -> mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT, R.string.result_next_track)
            ActionType.VIBRATE_MODE -> vibrateMode()
            ActionType.DO_NOT_DISTURB -> doNotDisturb()
            ActionType.WEBHOOK -> webhook(binding.webAddress)
            ActionType.HOTSPOT -> hotspot()
            ActionType.WIFI -> wifi()
            ActionType.LOCK_PHONE -> lock()
            ActionType.SEND_SMS -> sendSms(binding)
            ActionType.SHARE_LOCATION -> shareLocation(binding)
            ActionType.WHATSAPP -> whatsapp(binding)
            ActionType.GOOGLE_HOME -> googleHome(binding)
            // Handled before reaching here: SOS by its own service, a call by redirecting the dial.
            ActionType.SOS -> done(context.getString(R.string.action_sos))
            ActionType.CALL_CONTACT -> needsSetup(R.string.result_contact_missing)
            // A conversation needs a call, and reaching here means the phone is busy with a real one.
            ActionType.ASSISTANT -> needsSetup(R.string.result_assistant_busy)
            // These two must stay running. Reaching here means Android refused the service.
            ActionType.FLASHLIGHT, ActionType.FIND_PHONE -> needsSetup(R.string.result_needs_background)
        }
    } catch (e: RuntimeException) {
        failed(R.string.result_failed)
    }

    private fun battery(): ActionOutcome {
        val battery = context.getSystemService(BatteryManager::class.java)
        val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val message = if (battery.isCharging) R.string.result_battery_charging else R.string.result_battery
        return done(context.getString(message, level))
    }

    private fun mediaKey(key: Int, message: Int): ActionOutcome {
        val audio = context.getSystemService(AudioManager::class.java)
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
        return done(context.getString(message))
    }

    private fun vibrateMode(): ActionOutcome {
        val audio = context.getSystemService(AudioManager::class.java)
        val turningOn = audio.ringerMode == AudioManager.RINGER_MODE_NORMAL
        return try {
            audio.ringerMode = if (turningOn) AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL
            done(context.getString(if (turningOn) R.string.result_vibrate_on else R.string.result_ring_on))
        } catch (_: SecurityException) {
            // Android blocks ringer changes during Do Not Disturb without extra access.
            needsSetup(R.string.result_vibrate_blocked)
        }
    }

    private fun doNotDisturb(): ActionOutcome {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.isNotificationPolicyAccessGranted) return needsSetup(R.string.result_dnd_needs_access)
        val turningOn = manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
        manager.setInterruptionFilter(
            if (turningOn) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL,
        )
        return done(context.getString(if (turningOn) R.string.result_dnd_on else R.string.result_dnd_off))
    }

    private suspend fun webhook(address: String?): ActionOutcome {
        if (address.isNullOrBlank()) return needsSetup(R.string.result_webhook_missing)
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(address).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = NETWORK_TIMEOUT_MS
                connection.readTimeout = NETWORK_TIMEOUT_MS
                connection.doOutput = false
                val code = connection.responseCode
                connection.disconnect()
                if (code in 200..299) {
                    done(context.getString(R.string.result_webhook_ok))
                } else {
                    failedText(context.getString(R.string.result_webhook_status, code))
                }
            } catch (_: java.io.IOException) {
                failed(R.string.result_webhook_network)
            } catch (_: IllegalArgumentException) {
                failed(R.string.result_webhook_network)
            }
        }
    }

    private suspend fun hotspot(): ActionOutcome {
        val wasOn = isHotspotOn()
        val on = !wasOn
        val viaShizuku = ShizukuBridge.isReady()
        val direct = !viaShizuku && Readiness.canSwitchHotspotDirectly(context)
        val sent = when {
            viaShizuku -> ShizukuBridge.setHotspot(context, on)
            direct -> if (on) LegacyTether.start(context, {}, {}) else LegacyTether.stop(context)
            HelperService.isConfigured(ActionType.HOTSPOT) -> HelperService.tapTile(ActionType.HOTSPOT)
            else -> return needsSetup(R.string.result_hotspot_needs_setup)
        }
        if (!sent) return failed(R.string.result_control_failed)
        val message = R.string.result_hotspot_on.takeIf { on } ?: R.string.result_hotspot_off
        val outcome = confirm({ isHotspotOn() == on }, message)
        if (outcome.status == RunStatus.DONE || viaShizuku || direct) return outcome
        // Without Shizuku some phones hide the hotspot's state, so a tap can't always be verified.
        val locked = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        return if (locked) failed(R.string.result_control_failed) else done(context.getString(R.string.result_hotspot_tapped))
    }

    private suspend fun wifi(): ActionOutcome {
        val wifi = context.getSystemService(WifiManager::class.java)
        val on = !wifi.isWifiEnabled
        // Android still allows this directly for apps at this target, with no permission prompt.
        @Suppress("DEPRECATION")
        if (wifi.setWifiEnabled(on)) {
            return confirm(
                { wifi.isWifiEnabled == on },
                R.string.result_wifi_on.takeIf { on } ?: R.string.result_wifi_off,
            )
        }
        val sent = when {
            ShizukuBridge.isReady() -> ShizukuBridge.setWifi(on)
            HelperService.isConfigured(ActionType.WIFI) -> HelperService.tapTile(ActionType.WIFI)
            else -> return needsSetup(R.string.result_wifi_needs_setup)
        }
        if (!sent) return failed(R.string.result_control_failed)
        return confirm(
            { wifi.isWifiEnabled == on },
            R.string.result_wifi_on.takeIf { on } ?: R.string.result_wifi_off,
        )
    }

    private suspend fun googleHome(binding: CodeBinding): ActionOutcome {
        val id = binding.homeDeviceId
        if (id.isNullOrBlank()) return needsSetup(R.string.result_home_missing)
        val name = binding.homeDeviceName.orEmpty()
        val sent = withTimeoutOrNull(HOME_TIMEOUT_MS) { GoogleHome.send(id, binding.homeCommand ?: HomeCommand.TOGGLE) }
        return when (sent) {
            HomeSendResult.ON -> done(context.getString(R.string.result_home_on, name))
            HomeSendResult.OFF -> done(context.getString(R.string.result_home_off, name))
            HomeSendResult.SWITCHED -> done(context.getString(R.string.result_home_switched, name))
            HomeSendResult.NOT_CONNECTED -> needsSetup(R.string.result_home_not_connected)
            HomeSendResult.DEVICE_GONE -> failedText(context.getString(R.string.result_home_gone, name))
            HomeSendResult.UNREACHABLE, null -> failedText(context.getString(R.string.result_home_unreachable, name))
            HomeSendResult.FAILED -> failedText(context.getString(R.string.result_home_failed, name))
        }
    }

    private fun nameOf(binding: CodeBinding) = binding.contactName ?: binding.phone.orEmpty()

    private suspend fun sendSms(binding: CodeBinding): ActionOutcome {
        val phone = binding.phone
        val message = binding.message
        if (phone.isNullOrBlank() || message.isNullOrBlank()) return needsSetup(R.string.result_contact_missing)
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return needsSetup(R.string.result_sms_permission)
        }
        return if (SmsSender(context).send(phone, message, SMS_ATTEMPTS)) {
            done(context.getString(R.string.result_sms_sent, nameOf(binding)))
        } else {
            failedText(context.getString(R.string.result_sms_failed, nameOf(binding)))
        }
    }

    private suspend fun shareLocation(binding: CodeBinding): ActionOutcome {
        val phone = binding.phone
        if (phone.isNullOrBlank()) return needsSetup(R.string.result_contact_missing)
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED ||
            !LocationTracker.hasPermission(context)
        ) {
            return needsSetup(R.string.result_location_permission)
        }
        val tracker = LocationTracker(context)
        tracker.start()
        val position = tracker.awaitFresh(LOCATION_WAIT_MS) ?: tracker.latest
        tracker.stop()
        if (position == null) return failed(R.string.result_location_unavailable)
        val intro = binding.message ?: context.getString(R.string.share_location_default)
        return if (SmsSender(context).send(phone, "$intro\n${LocationTracker.describe(position)}", SMS_ATTEMPTS)) {
            done(context.getString(R.string.result_location_sent, nameOf(binding)))
        } else {
            failedText(context.getString(R.string.result_sms_failed, nameOf(binding)))
        }
    }

    private suspend fun whatsapp(binding: CodeBinding): ActionOutcome {
        val phone = binding.phone
        val message = binding.message
        if (phone.isNullOrBlank() || message.isNullOrBlank()) return needsSetup(R.string.result_contact_missing)
        if (!HelperService.isRunning) return needsSetup(R.string.result_whatsapp_helper)
        if (context.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return failed(R.string.result_whatsapp_locked)
        return if (HelperService.sendWhatsApp(phone, message)) {
            done(context.getString(R.string.result_whatsapp_sent, nameOf(binding)))
        } else {
            failed(R.string.result_whatsapp_failed)
        }
    }

    private fun lock(): ActionOutcome {
        if (!HelperService.isRunning) return needsSetup(R.string.result_lock_needs_setup)
        return if (HelperService.lockScreen()) done(context.getString(R.string.result_lock)) else failed(R.string.result_failed)
    }

    /** Never claims success until the phone reports the new state. */
    private suspend fun confirm(check: suspend () -> Boolean, message: Int): ActionOutcome {
        repeat(CONFIRM_ATTEMPTS) {
            if (check()) return done(context.getString(message))
            delay(CONFIRM_INTERVAL_MS)
        }
        return failed(R.string.result_not_confirmed)
    }

    /**
     * Android has no public way to ask, so this tries the most reliable source available: the
     * tethering service through Shizuku, then the Wi-Fi manager's unlisted state, then whether a
     * hotspot network interface is up.
     */
    private suspend fun isHotspotOn(): Boolean {
        ShizukuBridge.isTethering()?.let { return it }
        wifiManagerHotspotState()?.let { return it }
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().any { network ->
                HOTSPOT_INTERFACE.matches(network.name) && network.isUp && network.inetAddresses.hasMoreElements()
            }
        } catch (_: java.io.IOException) {
            false
        }
    }

    private fun wifiManagerHotspotState(): Boolean? = try {
        val wifi = context.getSystemService(WifiManager::class.java)
        WifiManager::class.java.getMethod("isWifiApEnabled").invoke(wifi) as? Boolean
    } catch (_: ReflectiveOperationException) {
        null
    } catch (_: RuntimeException) {
        null
    }

    private fun done(message: String) = ActionOutcome(RunStatus.DONE, message)
    private fun needsSetup(message: Int) = ActionOutcome(RunStatus.NEEDS_SETUP, context.getString(message))
    private fun failed(message: Int) = failedText(context.getString(message))
    private fun failedText(message: String) = ActionOutcome(RunStatus.FAILED, message)

    private companion object {
        const val SMS_ATTEMPTS = 2
        const val LOCATION_WAIT_MS = 25_000L
        const val NETWORK_TIMEOUT_MS = 10_000
        const val HOME_TIMEOUT_MS = 15_000L
        const val CONFIRM_ATTEMPTS = 16
        const val CONFIRM_INTERVAL_MS = 250L
        val HOTSPOT_INTERFACE = Regex("swlan\\d+|softap\\d+|ap\\d+|ap_br_.*|wlan[1-9]\\d*")
    }
}
