package com.littyl.digix.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.littyl.digix.R
import com.littyl.digix.ui.MainActivity

object Notifications {
    private const val CHANNEL_RESULTS = "results"
    private const val CHANNEL_RUNNING = "running"
    const val ID_RUNNING = 1
    const val ID_ASSISTANT = 3
    const val ID_SOS = 4
    private const val ID_RESULT = 2
    private const val RESULT_TIMEOUT_MS = 60_000L

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                // Silent and without a banner on the phone. Its job is to reach the watch.
                NotificationChannel(
                    CHANNEL_RESULTS,
                    context.getString(R.string.channel_results),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.channel_results_description)
                    setSound(null, null)
                    enableVibration(false)
                },
                NotificationChannel(
                    CHANNEL_RUNNING,
                    context.getString(R.string.channel_running),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.channel_running_description)
                    setShowBadge(false)
                },
            ),
        )
    }

    fun canPost(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    fun postResult(context: Context, title: String, text: String?) {
        if (!canPost(context)) return
        val notification = Notification.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_watch)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setTimeoutAfter(RESULT_TIMEOUT_MS)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ID_RESULT, notification)
    }

    fun running(context: Context, title: String, text: String? = null, stopIntent: PendingIntent? = null, stopLabel: String? = null): Notification {
        val builder = Notification.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_watch)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp(context))
            .setOngoing(true)
        if (stopIntent != null && stopLabel != null) {
            builder.addAction(Notification.Action.Builder(null, stopLabel, stopIntent).build())
        }
        return builder.build()
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
