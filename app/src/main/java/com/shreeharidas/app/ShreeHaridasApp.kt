package com.shreeharidas.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import com.shreeharidas.app.util.Constants

/**
 * Application class for Shree Haridas.
 * Initializes notification channels on app startup.
 */
class ShreeHaridasApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        // Reminder notification channel (IMPORTANCE_HIGH, sound disabled - played via MediaPlayer)
        // DND bypass enabled by default so reminders always come through
        val reminderChannel = NotificationChannel(
            Constants.CHANNEL_ID_REMINDERS,
            getString(R.string.channel_reminders_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.channel_reminders_description)
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(true)
        }

        // Foreground service channel (IMPORTANCE_LOW, silent)
        val serviceChannel = NotificationChannel(
            Constants.CHANNEL_ID_SERVICE,
            getString(R.string.channel_service_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_service_description)
            setSound(null, null)
            enableVibration(false)
        }

        notificationManager.createNotificationChannel(reminderChannel)
        notificationManager.createNotificationChannel(serviceChannel)
    }
}
