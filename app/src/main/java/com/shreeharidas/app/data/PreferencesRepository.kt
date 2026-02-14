package com.shreeharidas.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shreeharidas.app.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = Constants.DATASTORE_NAME
)

/**
 * Repository for reading/writing all user preferences via DataStore.
 * Exposes reactive Flow<UserPreferences> for Compose observation.
 */
class PreferencesRepository(private val context: Context) {

    private object Keys {
        val FREQUENCY_MINUTES = intPreferencesKey("frequency_minutes")
        val IS_RUNNING = booleanPreferencesKey("is_running")
        val SOUND_URI = stringPreferencesKey("sound_uri")
        val SOUND_TYPE = stringPreferencesKey("sound_type")
        val DND_OVERRIDE = booleanPreferencesKey("dnd_override")
        val NEXT_TRIGGER_TIME = longPreferencesKey("next_trigger_time")
        val ACTIVE_WINDOW_ENABLED = booleanPreferencesKey("active_window_enabled")
        val START_HOUR = intPreferencesKey("start_hour")
        val START_MINUTE = intPreferencesKey("start_minute")
        val END_HOUR = intPreferencesKey("end_hour")
        val END_MINUTE = intPreferencesKey("end_minute")
        val HAS_LAUNCHED_BEFORE = booleanPreferencesKey("has_launched_before")
    }

    /** Reactive stream of all user preferences. */
    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            frequencyMinutes = prefs[Keys.FREQUENCY_MINUTES]
                ?: Constants.DEFAULT_FREQUENCY_MINUTES,
            isRunning = prefs[Keys.IS_RUNNING] ?: false,
            soundUri = prefs[Keys.SOUND_URI] ?: Constants.DEFAULT_SOUND_URI,
            soundType = try {
                SoundType.valueOf(
                    prefs[Keys.SOUND_TYPE] ?: SoundType.DEFAULT.name
                )
            } catch (_: IllegalArgumentException) {
                SoundType.DEFAULT
            },
            dndOverride = prefs[Keys.DND_OVERRIDE] ?: true,
            nextTriggerTime = prefs[Keys.NEXT_TRIGGER_TIME] ?: 0L,
            activeWindowEnabled = prefs[Keys.ACTIVE_WINDOW_ENABLED] ?: false,
            startHour = prefs[Keys.START_HOUR] ?: Constants.DEFAULT_START_HOUR,
            startMinute = prefs[Keys.START_MINUTE] ?: Constants.DEFAULT_START_MINUTE,
            endHour = prefs[Keys.END_HOUR] ?: Constants.DEFAULT_END_HOUR,
            endMinute = prefs[Keys.END_MINUTE] ?: Constants.DEFAULT_END_MINUTE
        )
    }

    /** Blocking read of current preferences (for use in BroadcastReceivers). */
    suspend fun getPreferences(): UserPreferences {
        return preferencesFlow.first()
    }

    /** Update frequency interval in minutes. */
    suspend fun setFrequencyMinutes(minutes: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FREQUENCY_MINUTES] = minutes
        }
    }

    /** Update running state. */
    suspend fun setRunning(running: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.IS_RUNNING] = running
        }
    }

    /** Update selected sound URI and type. */
    suspend fun setSound(uri: String, type: SoundType) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SOUND_URI] = uri
            prefs[Keys.SOUND_TYPE] = type.name
        }
    }

    /** Update sound URI only (e.g. fallback to default). */
    suspend fun setSoundUri(uri: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SOUND_URI] = uri
        }
    }

    /** Update DND override setting. */
    suspend fun setDndOverride(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DND_OVERRIDE] = enabled
        }
    }

    /** Update the next alarm trigger time (epoch millis). */
    suspend fun setNextTriggerTime(timeMillis: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NEXT_TRIGGER_TIME] = timeMillis
        }
    }

    /** Update active time window enabled state. */
    suspend fun setActiveWindowEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACTIVE_WINDOW_ENABLED] = enabled
        }
    }

    /** Update active time window start and end times. */
    suspend fun setActiveWindow(
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.START_HOUR] = startHour
            prefs[Keys.START_MINUTE] = startMinute
            prefs[Keys.END_HOUR] = endHour
            prefs[Keys.END_MINUTE] = endMinute
        }
    }

    /** Save full start configuration at once. */
    suspend fun saveStartConfiguration(
        frequencyMinutes: Int,
        soundUri: String,
        soundType: SoundType,
        nextTriggerTime: Long
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FREQUENCY_MINUTES] = frequencyMinutes
            prefs[Keys.SOUND_URI] = soundUri
            prefs[Keys.SOUND_TYPE] = soundType.name
            prefs[Keys.IS_RUNNING] = true
            prefs[Keys.NEXT_TRIGGER_TIME] = nextTriggerTime
        }
    }

    /** Clear running state (on stop). */
    suspend fun clearRunningState() {
        context.dataStore.edit { prefs ->
            prefs[Keys.IS_RUNNING] = false
            prefs[Keys.NEXT_TRIGGER_TIME] = 0L
        }
    }

    /** Check if this is the first launch. */
    suspend fun hasLaunchedBefore(): Boolean {
        return context.dataStore.data.first()[Keys.HAS_LAUNCHED_BEFORE] ?: false
    }

    /** Mark that the app has been launched. */
    suspend fun setHasLaunchedBefore() {
        context.dataStore.edit { prefs ->
            prefs[Keys.HAS_LAUNCHED_BEFORE] = true
        }
    }
}
