package com.littyl.digix.engine

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.littyl.digix.R
import com.littyl.digix.core.ActionType
import com.littyl.digix.core.CodeBinding
import com.littyl.digix.core.RunEntry
import com.littyl.digix.core.RunStatus
import com.littyl.digix.core.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Runs actions and stays alive while something must keep going, such as the flashlight. */
class ActionService : Service() {
    private val finder by lazy { PhoneFinder(this) }
    private val torch by lazy { Torch(this) }
    private val running = mutableSetOf<Int>()
    private var userPresentReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_FINDER -> {
                goForeground(getString(R.string.running_generic))
                stopFinder()
            }
            ACTION_STOP_TORCH -> {
                goForeground(getString(R.string.running_generic))
                scope.launch {
                    torch.set(false)
                    release()
                }
            }
            ACTION_RUN -> {
                val code = intent.getStringExtra(EXTRA_CODE).orEmpty()
                val binding = Store.find(code)
                goForeground(
                    binding?.let { getString(it.action.title) } ?: getString(R.string.running_generic),
                    needsLocation = binding?.action == ActionType.SHARE_LOCATION,
                )
                if (binding != null) run(binding, startId) else release()
            }
            else -> {
                goForeground(getString(R.string.running_generic))
                release()
            }
        }
        return START_NOT_STICKY
    }

    private fun run(binding: CodeBinding, startId: Int) {
        running += startId
        scope.launch {
            val outcome = when (binding.action) {
                ActionType.FIND_PHONE -> toggleFinder()
                ActionType.FLASHLIGHT -> toggleTorch()
                else -> ActionRunner(this@ActionService).run(binding)
            }
            report(this@ActionService, binding, outcome)
            running -= startId
            release()
        }
    }

    private fun toggleFinder(): ActionOutcome {
        if (finder.isRinging) {
            stopFinder()
            return ActionOutcome(RunStatus.DONE, getString(R.string.result_find_phone_off))
        }
        finder.start { stopFinder() }
        isFinderRinging = true
        listenForUnlock()
        goForeground(
            getString(R.string.running_find_phone),
            getString(R.string.running_find_phone_text),
            stopIntent(ACTION_STOP_FINDER),
            getString(R.string.action_stop),
        )
        return ActionOutcome(RunStatus.DONE, getString(R.string.result_find_phone_on))
    }

    private fun stopFinder() {
        finder.stop()
        isFinderRinging = false
        userPresentReceiver?.let { runCatching { unregisterReceiver(it) } }
        userPresentReceiver = null
        release()
    }

    private fun listenForUnlock() {
        if (userPresentReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = stopFinder()
        }
        registerReceiver(receiver, IntentFilter(Intent.ACTION_USER_PRESENT))
        userPresentReceiver = receiver
    }

    private suspend fun toggleTorch(): ActionOutcome {
        if (!torch.isAvailable) return ActionOutcome(RunStatus.FAILED, getString(R.string.result_torch_missing))
        val turnOn = !torch.isOn()
        if (!torch.set(turnOn)) return ActionOutcome(RunStatus.FAILED, getString(R.string.result_torch_failed))
        if (turnOn) {
            goForeground(
                getString(R.string.running_torch),
                getString(R.string.running_torch_text),
                stopIntent(ACTION_STOP_TORCH),
                getString(R.string.action_turn_off),
            )
        }
        return ActionOutcome(RunStatus.DONE, getString(if (turnOn) R.string.result_torch_on else R.string.result_torch_off))
    }

    /** Ends the service once nothing is left to keep alive. */
    private fun release() {
        scope.launch {
            if (running.isNotEmpty() || finder.isRinging || (torch.isAvailable && torch.isOn())) return@launch
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun goForeground(
        title: String,
        text: String? = null,
        stop: PendingIntent? = null,
        stopLabel: String? = null,
        needsLocation: Boolean = false,
    ) {
        val notification = Notifications.running(this, title, text, stop, stopLabel)
        if (needsLocation) {
            // Otherwise Android leaves "while using" location requests idle in the background.
            startForeground(Notifications.ID_RUNNING, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && applicationInfo.targetSdkVersion >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Notifications.ID_RUNNING, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifications.ID_RUNNING, notification)
        }
    }

    private fun stopIntent(action: String): PendingIntent = PendingIntent.getService(
        this,
        action.hashCode(),
        Intent(this, ActionService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun onDestroy() {
        finder.stop()
        isFinderRinging = false
        userPresentReceiver?.let { runCatching { unregisterReceiver(it) } }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Digix"
        private const val ACTION_RUN = "com.littyl.digix.RUN"
        private const val ACTION_STOP_FINDER = "com.littyl.digix.STOP_FINDER"
        private const val ACTION_STOP_TORCH = "com.littyl.digix.STOP_TORCH"
        private const val EXTRA_CODE = "code"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        /** Whether Find my phone is ringing right now. */
        var isFinderRinging = false
            private set

        /** Called the moment a code is caught. Must not throw: the call is already stopped. */
        fun dispatch(context: Context, binding: CodeBinding) {
            // SOS has its own service, started when the code was caught. Never run it twice.
            if (binding.action == ActionType.SOS) return
            if (!Store.enabled.value) {
                report(
                    context,
                    binding,
                    ActionOutcome(RunStatus.STOPPED_OFF, context.getString(R.string.result_off, binding.code)),
                )
                return
            }
            try {
                context.startForegroundService(
                    Intent(context, ActionService::class.java).setAction(ACTION_RUN).putExtra(EXTRA_CODE, binding.code),
                )
            } catch (e: RuntimeException) {
                // Android refused a background start. Quick actions still run; the rest report it.
                Log.w(TAG, "foreground start refused, running inline", e)
                scope.launch { report(context, binding, ActionRunner(context).run(binding)) }
            }
        }

        fun report(context: Context, binding: CodeBinding, outcome: ActionOutcome) {
            Store.record(RunEntry(System.currentTimeMillis(), binding.code, binding.action, outcome.status, outcome.message))
            Notifications.postResult(context, outcome.message, null)
        }
    }
}

data class ActionOutcome(val status: RunStatus, val message: String)
