package com.shreeharidaas.app.data

import com.shreeharidaas.app.util.Constants

/**
 * Enum representing the type/source of the selected sound.
 */
enum class SoundType {
    DEFAULT,
    RECORDED,
    FILE
}

/**
 * Data class holding all persisted user preferences.
 * Maps to PRD Section 7 (State Persistence).
 */
data class UserPreferences(
    val frequencyMinutes: Int = Constants.DEFAULT_FREQUENCY_MINUTES,
    val isRunning: Boolean = false,
    val soundUri: String = Constants.DEFAULT_SOUND_URI,
    val soundType: SoundType = SoundType.DEFAULT,
    val dndOverride: Boolean = false,
    val vibrationEnabled: Boolean = false,
    val nextTriggerTime: Long = 0L,
    val activeWindowEnabled: Boolean = false,
    val startHour: Int = Constants.DEFAULT_START_HOUR,
    val startMinute: Int = Constants.DEFAULT_START_MINUTE,
    val endHour: Int = Constants.DEFAULT_END_HOUR,
    val endMinute: Int = Constants.DEFAULT_END_MINUTE
) {
    /** Returns true if the active time window covers the entire day (default). */
    val isAllDay: Boolean
        get() = !activeWindowEnabled ||
            (startHour == 0 && startMinute == 0 && endHour == 23 && endMinute == 59)

    /** Total start time in minutes from midnight. */
    val startTotalMinutes: Int get() = startHour * 60 + startMinute

    /** Total end time in minutes from midnight. */
    val endTotalMinutes: Int get() = endHour * 60 + endMinute
}
