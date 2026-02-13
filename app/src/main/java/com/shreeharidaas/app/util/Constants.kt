package com.shreeharidaas.app.util

/**
 * Application-wide constants for Shree Haridaas.
 */
object Constants {
    // Notification Channel IDs
    const val CHANNEL_ID_REMINDERS = "shreeharidaas_reminders"
    const val CHANNEL_ID_SERVICE = "shreeharidaas_service"

    // Alarm Request Code (single fixed code to prevent duplicates)
    const val ALARM_REQUEST_CODE = 1001

    // Notification IDs
    const val NOTIFICATION_ID_REMINDER = 2001
    const val NOTIFICATION_ID_SERVICE = 2002

    // Foreground Service Actions
    const val ACTION_START = "com.shreeharidaas.app.ACTION_START"
    const val ACTION_STOP = "com.shreeharidaas.app.ACTION_STOP"
    const val ACTION_ALARM_TRIGGERED = "com.shreeharidaas.app.ACTION_ALARM_TRIGGERED"

    // Default Values
    const val DEFAULT_FREQUENCY_MINUTES = 5
    const val MIN_FREQUENCY_MINUTES = 1
    const val MAX_FREQUENCY_MINUTES = 1440

    // Sound Defaults
    const val DEFAULT_SOUND_URI = "default:bell"

    // Active Time Window Defaults (all day by default)
    const val DEFAULT_START_HOUR = 0
    const val DEFAULT_START_MINUTE = 0
    const val DEFAULT_END_HOUR = 23
    const val DEFAULT_END_MINUTE = 59

    // DataStore Name
    const val DATASTORE_NAME = "shreeharidaas_preferences"

    // Recording directory
    const val RECORDINGS_DIR = "recordings"
}
