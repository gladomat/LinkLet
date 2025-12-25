package com.gladomat.linklet.data.sync.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
        
        // Create intent to open MainActivity when notification is tapped
        val intent = Intent(context, com.gladomat.linklet.ui.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notificationManager.notify(RESULT_NOTIFICATION_ID, notification)
    }
}
