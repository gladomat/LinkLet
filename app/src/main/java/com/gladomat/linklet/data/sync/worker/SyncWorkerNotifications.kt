package com.gladomat.linklet.data.sync.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.gladomat.linklet.R

internal object SyncWorkerNotifications {

    const val CHANNEL_ID = "linklet_sync"
    const val NOTIFICATION_ID = 1001
    private const val RESULT_NOTIFICATION_ID = 1002

    fun ensureChannel(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sync",
            NotificationManager.IMPORTANCE_LOW,
        )
        notificationManager.createNotificationChannel(channel)
    }

    fun build(
        context: Context,
        title: String,
        text: String,
        completed: Int? = null,
        total: Int? = null,
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (completed != null && total != null && total > 0) {
            builder.setProgress(total, completed, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    fun notifyResult(context: Context, title: String, text: String) {
        ensureChannel(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notificationManager.notify(RESULT_NOTIFICATION_ID, notification)
    }
}
