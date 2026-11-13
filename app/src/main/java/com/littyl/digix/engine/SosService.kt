package com.littyl.digix.engine

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.telecom.TelecomManager
import android.util.Log
import com.littyl.digix.R
import com.littyl.digix.core.ActionType
import com.littyl.digix.core.CodeBinding
import com.littyl.digix.core.RunEntry
import com.littyl.digix.core.RunStatus
import com.littyl.digix.core.SosConfig
import com.littyl.digix.core.SosContact
import com.littyl.digix.core.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Sends the alert, then keeps sending location updates. The order matters: the first message goes
 * out at once with whatever position is known, because help must never wait for a GPS fix. A fresh
 * position follows as soon as there is one.
 */
class SosService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sms by lazy { SmsSender(this) }
    private val location by lazy { LocationTracker(this) }
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Declared as a location service, or Android leaves "while using" location requests idle.
        try {
            startForeground(
                Notifications.ID_SOS,
                Notifications.running(this, getString(R.string.sos_running), null, stopIntent(), getString(R.string.action_stop)),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } catch (e: SecurityException) {
            Log.w("Digix", "SOS location permission denied", e)
            SosConfig.markStopped(this)
            reportStartFailure(this, codeForLog())
            if (intent?.action == ACTION_TEST) lastTestResult.value = TestResult(0, SosConfig.settings.value.contacts.size, false)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_STOP -> stop(notifyContacts = true)
            ACTION_TEST -> sendTest()
            else -> resumeOrBegin()
        }
        // If Android ever kills this, it restarts it, and the alert carries on from its saved state.
        return START_STICKY
    }

    private fun resumeOrBegin() {
        if (job?.isActive == true) return
        val now = System.currentTimeMillis()
        val resuming = SosConfig.activeUntil(this) > now
        val until = if (resuming) SosConfig.activeUntil(this) else now + DURATION_MS
        SosConfig.markActive(this, until)
        acquireWakeLock(until - now)
        location.start()
        job = scope.launch {
            if (!resuming) {
                alertEveryone()
            } else {
                // Restarted part way through, so catch up rather than wait out a whole interval.
                val since = now - SosConfig.lastUpdate(this@SosService)
                if (since < UPDATE_INTERVAL_MS) delay(UPDATE_INTERVAL_MS - since)
            }
            while (System.currentTimeMillis() < until) {
                sendUpdate()
                SosConfig.markUpdate(this@SosService, System.currentTimeMillis())
                delay(UPDATE_INTERVAL_MS)
            }
            stop(notifyContacts = true)
        }
    }

    private suspend fun alertEveryone() {
        val settings = SosConfig.settings.value
        val text = settings.message.ifBlank { getString(R.string.sos_default_message) }
        val known = location.latest
        val first = buildString {
            append(text)
            append('\n')
            if (known != null) {
                append(getString(R.string.sos_last_known, LocationTracker.describe(known)))
            } else {
                append(getString(R.string.sos_finding_location))
            }
            append('\n')
            append(batteryLine())
        }
        val sent = sendToAll(settings.contacts, first, ALERT_ATTEMPTS)
        record(sent, settings.contacts.size)
        SosConfig.markUpdate(this, System.currentTimeMillis())
        if (settings.callFirstContact) settings.contacts.firstOrNull()?.let(::callContact)

        // Follows with an accurate position right away, without waiting for the first interval.
        val fresh = location.awaitFresh(FIRST_FIX_TIMEOUT_MS)
        if (fresh != null) {
            sendToAll(settings.contacts, getString(R.string.sos_update, LocationTracker.describe(fresh), batteryLine()), UPDATE_ATTEMPTS)
            SosConfig.markUpdate(this, System.currentTimeMillis())
        } else if (location.latest == null || !LocationTracker.isEnabled(this)) {
            sendToAll(settings.contacts, getString(R.string.sos_no_location, batteryLine()), UPDATE_ATTEMPTS)
        }
        delay(UPDATE_INTERVAL_MS)
    }

    private suspend fun sendUpdate() {
        val contacts = SosConfig.settings.value.contacts
        val position = location.latest
        val text = if (position != null) {
            getString(R.string.sos_update, LocationTracker.describe(position), batteryLine())
        } else {
            getString(R.string.sos_no_location, batteryLine())
        }
        sendToAll(contacts, text, UPDATE_ATTEMPTS)
    }

    /** Everyone at once, so one contact's failing number can't delay the others. */
    private suspend fun sendToAll(contacts: List<SosContact>, text: String, attempts: Int): Int {
        val results = contacts.map { contact -> scope.async { sms.send(contact.phone, text, attempts) } }.awaitAll()
        return results.count { it }
    }

    private fun record(sent: Int, total: Int) {
        val status = if (sent > 0) RunStatus.DONE else RunStatus.FAILED
        val message = if (sent > 0) getString(R.string.sos_result_sent, sent, total) else getString(R.string.sos_result_failed)
        Store.record(RunEntry(System.currentTimeMillis(), codeForLog(), ActionType.SOS, status, message))
        // Deliberately plain, since someone else may see the watch or phone.
        Notifications.postResult(this, getString(if (sent > 0) R.string.sos_discreet_done else R.string.sos_discreet_failed), null)
    }

    private fun codeForLog() = Store.codes.value.firstOrNull { it.action == ActionType.SOS }?.code ?: "SOS"

    /** Needs nothing on screen: this goes straight to the phone's call system. */
    @SuppressLint("MissingPermission")
    private fun callContact(contact: SosContact) {
        runCatching {
            getSystemService(TelecomManager::class.java).placeCall(Uri.fromParts("tel", contact.phone, null), Bundle())
        }
    }

    private fun sendTest() {
        scope.launch {
            val settings = SosConfig.settings.value
            val tracker = LocationTracker(this@SosService)
            tracker.start()
            val position = tracker.awaitFresh(TEST_FIX_TIMEOUT_MS) ?: tracker.latest
            tracker.stop()
            val text = buildString {
                append(getString(R.string.sos_test_message))
                append('\n')
                append(position?.let(LocationTracker::describe) ?: getString(R.string.sos_test_no_location))
            }
            val sent = sendToAll(settings.contacts, text, 1)
            lastTestResult.value = TestResult(sent, settings.contacts.size, position != null)
            if (job?.isActive != true) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun stop(notifyContacts: Boolean) {
        val wasActive = SosConfig.activeUntil(this) > 0
        job?.cancel()
        job = null
        location.stop()
        SosConfig.markStopped(this)
        releaseWakeLock()
        scope.launch {
            if (notifyContacts && wasActive) {
                sendToAll(SosConfig.settings.value.contacts, getString(R.string.sos_stopped_message), 1)
                Store.record(RunEntry(System.currentTimeMillis(), codeForLog(), ActionType.SOS, RunStatus.DONE, getString(R.string.sos_result_stopped)))
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun batteryLine(): String {
        val level = getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return getString(R.string.sos_battery, level)
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock(timeoutMs: Long) {
        if (wakeLock != null) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "digix:sos")
            .apply { acquire(timeoutMs + WAKE_LOCK_MARGIN_MS) }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun stopIntent(): PendingIntent = PendingIntent.getService(
        this,
        ACTION_STOP.hashCode(),
        Intent(this, SosService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun onDestroy() {
        location.stop()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    /** Android restarts nothing after a reboot by itself, so an alert in progress resumes here. */
    class BootReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
            if (SosConfig.activeUntil(context) > System.currentTimeMillis()) start(context)
        }
    }

    data class TestResult(val sent: Int, val total: Int, val hadLocation: Boolean)

    companion object {
        private const val ACTION_STOP = "com.littyl.digix.SOS_STOP"
        private const val ACTION_TEST = "com.littyl.digix.SOS_TEST"
        private const val DURATION_MS = 60 * 60_000L
        private const val UPDATE_INTERVAL_MS = 5 * 60_000L
        private const val FIRST_FIX_TIMEOUT_MS = 45_000L
        private const val TEST_FIX_TIMEOUT_MS = 20_000L
        private const val ALERT_ATTEMPTS = 4
        private const val UPDATE_ATTEMPTS = 2
        private const val WAKE_LOCK_MARGIN_MS = 60_000L

        val lastTestResult = kotlinx.coroutines.flow.MutableStateFlow<TestResult?>(null)

        internal fun reportStartFailure(context: Context, code: String) {
            val message = context.getString(R.string.sos_start_unavailable)
            Store.record(RunEntry(System.currentTimeMillis(), code, ActionType.SOS, RunStatus.FAILED, message))
            Notifications.postResult(context, context.getString(R.string.sos_discreet_failed), null)
        }

        fun start(context: Context) {
            context.startForegroundService(Intent(context, SosService::class.java))
        }

        fun stop(context: Context) {
            context.startForegroundService(Intent(context, SosService::class.java).setAction(ACTION_STOP))
        }

        fun sendTest(context: Context) {
            lastTestResult.value = null
            context.startForegroundService(Intent(context, SosService::class.java).setAction(ACTION_TEST))
        }

        /** Dialing the SOS code starts it, and dialing it again stops it. */
        fun toggle(context: Context, binding: CodeBinding) {
            if (binding.action != ActionType.SOS) return
            if (SosConfig.activeUntil(context) > System.currentTimeMillis()) stop(context) else start(context)
        }
    }
}
