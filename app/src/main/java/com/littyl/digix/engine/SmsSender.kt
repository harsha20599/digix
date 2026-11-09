package com.littyl.digix.engine

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

/**
 * Sends a text and reports what the network actually said. Each SIM is named explicitly, because
 * the phone's default can be "ask every time", which would wait for an answer nobody is there to give.
 */
class SmsSender(private val context: Context) {

    /** True only when the network accepted every part of the message. */
    suspend fun send(phone: String, message: String, attempts: Int = 1): Boolean {
        repeat(attempts) { attempt ->
            for (manager in managers()) {
                if (sendOnce(manager, phone, message)) return true
            }
            if (attempt < attempts - 1) delay(RETRY_DELAY_MS * (attempt + 1))
        }
        return false
    }

    /** The default SMS SIM first if there is one, then every other active SIM. */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun managers(): List<SmsManager> {
        val default = SmsManager.getDefaultSmsSubscriptionId()
        val active = runCatching {
            context.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty().map { it.subscriptionId }
        }.getOrDefault(emptyList())
        val ordered = (listOf(default) + active).filter { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }.distinct()
        return ordered.map { SmsManager.getSmsManagerForSubscriptionId(it) }.ifEmpty { listOf(SmsManager.getDefault()) }
    }

    private suspend fun sendOnce(manager: SmsManager, phone: String, message: String): Boolean {
        val parts = try {
            manager.divideMessage(message)
        } catch (_: RuntimeException) {
            return false
        }
        val action = "com.littyl.digix.SMS_SENT.${NEXT_ID.incrementAndGet()}"
        val finished = CompletableDeferred<Boolean>()
        var remaining = parts.size
        var failed = false
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                if (resultCode != Activity.RESULT_OK) failed = true
                remaining--
                if (remaining <= 0) finished.complete(!failed)
            }
        }
        context.registerReceiver(receiver, IntentFilter(action))
        return try {
            val sent = ArrayList(
                parts.indices.map { part ->
                    PendingIntent.getBroadcast(
                        context,
                        action.hashCode() + part,
                        Intent(action).setPackage(context.packageName),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT,
                    )
                },
            )
            manager.sendMultipartTextMessage(phone, null, parts, sent, null)
            withTimeoutOrNull(SEND_TIMEOUT_MS) { finished.await() } ?: false
        } catch (_: RuntimeException) {
            false
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    private companion object {
        val NEXT_ID = AtomicInteger()
        const val SEND_TIMEOUT_MS = 30_000L
        const val RETRY_DELAY_MS = 20_000L
    }
}
