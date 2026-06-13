package com.shreeharidas.app.util

/**
 * Application-wide constants for Shree Haridas.
 */
object Constants {
    // Notification Channel IDs
    const val CHANNEL_ID_REMINDERS = "shreeharidas_reminders"
    const val CHANNEL_ID_SERVICE = "shreeharidas_service"
    const val CHANNEL_ID_FESTIVALS = "shreeharidas_festivals"

    // Alarm Request Code (single fixed code to prevent duplicates)
    const val ALARM_REQUEST_CODE = 1001

    // Notification IDs
    const val NOTIFICATION_ID_REMINDER = 2001
    const val NOTIFICATION_ID_SERVICE = 2002
    const val NOTIFICATION_ID_FESTIVAL_BASE = 60000

    // Foreground Service Actions
    const val ACTION_START = "com.shreeharidas.app.ACTION_START"
    const val ACTION_STOP = "com.shreeharidas.app.ACTION_STOP"
    const val ACTION_ALARM_TRIGGERED = "com.shreeharidas.app.ACTION_ALARM_TRIGGERED"
    const val ACTION_FESTIVAL_NOTIFICATION =
        "com.shreeharidas.app.ACTION_FESTIVAL_NOTIFICATION"

    // Festival notifications use a distinct request-code range from reminders.
    const val FESTIVAL_REQUEST_CODE_BASE = 50000
    const val FESTIVAL_REQUEST_CODE_SPAN = 1_000_000

    // Default Values
    const val DEFAULT_FREQUENCY_MINUTES = 5
    const val MIN_FREQUENCY_MINUTES = 1
    const val MAX_FREQUENCY_MINUTES = 1440

    // Sound Defaults
    const val DEFAULT_SOUND_URI = "default:kunjvihari_shreeharidas"

    // Active Time Window Defaults (all day by default)
    const val DEFAULT_START_HOUR = 0
    const val DEFAULT_START_MINUTE = 0
    const val DEFAULT_END_HOUR = 23
    const val DEFAULT_END_MINUTE = 59

    // DataStore Name
    const val DATASTORE_NAME = "shreeharidas_preferences"

    // Recording directory
    const val RECORDINGS_DIR = "recordings"
}
