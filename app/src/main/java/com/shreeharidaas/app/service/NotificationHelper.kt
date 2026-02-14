package com.shreeharidaas.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.shreeharidaas.app.MainActivity
import com.shreeharidaas.app.R
import com.shreeharidaas.app.util.Constants

/**
 * Helper for creating and showing notifications.
 * Manages two channels: silent reminders and low-importance service.
 */
class NotificationHelper(private val context: Context) {

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Updates the reminder channel's DND bypass setting.
     * Called when user toggles DND override in settings.
     */
    fun updateDndBypass(enabled: Boolean) {
        val channel = notificationManager.getNotificationChannel(
            Constants.CHANNEL_ID_REMINDERS
        )
        channel?.let {
            it.setBypassDnd(enabled)
            notificationManager.createNotificationChannel(it)
        }
    }

    /**
     * Show a silent reminder notification (sound played via MediaPlayer separately).
     */
    fun showReminderNotification(fallbackMessage: String? = null) {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val body = fallbackMessage
            ?: context.getString(R.string.notification_reminder_body)

        val notification = NotificationCompat.Builder(
            context, Constants.CHANNEL_ID_REMINDERS
        )
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.notification_reminder_title))
            .setContentText(body)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSilent(true)
            .build()

        notificationManager.notify(
            Constants.NOTIFICATION_ID_REMINDER,
            notification
        )
    }

    /**
     * Cancel any visible reminder notification in the drawer.
     * Called when the user stops reminders.
     */
    fun cancelReminderNotification() {
        notificationManager.cancel(Constants.NOTIFICATION_ID_REMINDER)
    }

    /**
     * Build the persistent foreground service notification.
     */
    fun buildServiceNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(
            context, Constants.CHANNEL_ID_SERVICE
        )
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.notification_service_title))
            .setContentText(context.getString(R.string.notification_service_body))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
