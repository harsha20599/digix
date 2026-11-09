package com.littyl.digix.engine

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.littyl.digix.R

/** Android only lets a background app use the microphone while it shows it's doing so. */
class AssistantService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            Notifications.ID_ASSISTANT,
            Notifications.running(this, getString(R.string.running_assistant), getString(R.string.running_assistant_text)),
        )
        return START_NOT_STICKY
    }

    companion object {
        /** False when Android refused. A conversation can't hear anything without this. */
        fun start(context: Context): Boolean = try {
            context.startForegroundService(Intent(context, AssistantService::class.java))
            true
        } catch (_: RuntimeException) {
            false
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AssistantService::class.java))
        }
    }
}
