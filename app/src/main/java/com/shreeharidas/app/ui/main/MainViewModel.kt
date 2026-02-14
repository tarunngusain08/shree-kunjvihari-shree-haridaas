package com.shreeharidas.app.ui.main

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shreeharidas.app.audio.SoundPlayer
import com.shreeharidas.app.data.PreferencesRepository
import com.shreeharidas.app.data.SoundOption
import com.shreeharidas.app.data.SoundRepository
import com.shreeharidas.app.data.SoundType
import com.shreeharidas.app.data.UserPreferences
import com.shreeharidas.app.service.AlarmScheduler
import com.shreeharidas.app.service.ReminderForegroundService
import com.shreeharidas.app.util.Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel for the main screen.
 * Manages reminder state, sound selection, frequency, and countdown.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesRepository = PreferencesRepository(application)
    val soundRepository = SoundRepository(application)
    private val alarmScheduler = AlarmScheduler(application)
    val soundPlayer = SoundPlayer(application)

    /** Current user preferences as a reactive state. */
    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserPreferences()
        )

    /** Frequency input text (separate from persisted value for validation). */
    private val _frequencyInput = MutableStateFlow(
        Constants.DEFAULT_FREQUENCY_MINUTES.toString()
    )
    val frequencyInput: StateFlow<String> = _frequencyInput.asStateFlow()

    /** Validation error for frequency input. */
    private val _frequencyError = MutableStateFlow<String?>(null)
    val frequencyError: StateFlow<String?> = _frequencyError.asStateFlow()

    /** Countdown text for next reminder. */
    private val _countdownText = MutableStateFlow("")
    val countdownText: StateFlow<String> = _countdownText.asStateFlow()

    /** Whether the sound picker dialog should be shown. */
    private val _showSoundPicker = MutableStateFlow(false)
    val showSoundPicker: StateFlow<Boolean> = _showSoundPicker.asStateFlow()

    /** Whether the audio recorder dialog should be shown. */
    private val _showAudioRecorder = MutableStateFlow(false)
    val showAudioRecorder: StateFlow<Boolean> = _showAudioRecorder.asStateFlow()

    /** Default sounds list. */
    val defaultSounds: List<SoundOption> = soundRepository.getDefaultSounds()

    init {
        // Sync frequency input with persisted value
        viewModelScope.launch {
            preferencesRepository.preferencesFlow.collect { prefs ->
                if (!prefs.isRunning) {
                    _frequencyInput.value = prefs.frequencyMinutes.toString()
                }
            }
        }

        // Countdown timer updater
        viewModelScope.launch {
            while (isActive) {
                updateCountdown()
                delay(1000)
            }
        }

    }

    /** Update frequency input text and validate. */
    fun onFrequencyChanged(input: String) {
        _frequencyInput.value = input
        validateFrequency(input)
    }

    /** Validate frequency input. Returns parsed value or null. */
    private fun validateFrequency(input: String): Int? {
        if (input.isBlank()) {
            _frequencyError.value = "Please enter an interval"
            return null
        }
        val value = input.toIntOrNull()
        if (value == null || value < Constants.MIN_FREQUENCY_MINUTES ||
            value > Constants.MAX_FREQUENCY_MINUTES
        ) {
            _frequencyError.value = "Enter a number between 1 and 1440"
            return null
        }
        _frequencyError.value = null
        return value
    }

    /** Start the recurring reminder. */
    fun startReminder() {
        val frequency = validateFrequency(_frequencyInput.value) ?: return
        val prefs = preferences.value

        viewModelScope.launch {
            val nextTriggerTime = System.currentTimeMillis() +
                (frequency * 60L * 1000L)

            preferencesRepository.saveStartConfiguration(
                frequencyMinutes = frequency,
                soundUri = prefs.soundUri,
                soundType = prefs.soundType,
                nextTriggerTime = nextTriggerTime
            )

            ReminderForegroundService.startService(getApplication())
        }
    }

    /** Stop the recurring reminder. */
    fun stopReminder() {
        ReminderForegroundService.stopService(getApplication())
    }

    /** Select a sound. */
    fun selectSound(uri: String, type: SoundType) {
        viewModelScope.launch {
            preferencesRepository.setSound(uri, type)
        }
    }

    /** Handle a file selected from the system file picker. */
    fun onFilePicked(uri: Uri) {
        // Take persistable permission for scoped storage
        try {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // May already have permission
        }
        selectSound(uri.toString(), SoundType.FILE)
    }

    /** Handle a recording saved. */
    fun onRecordingSaved(filePath: String) {
        val uri = Uri.fromFile(java.io.File(filePath))
        selectSound(uri.toString(), SoundType.RECORDED)
    }

    /** Preview a sound. */
    fun previewSound(uriString: String) {
        soundPlayer.play(uriString)
    }

    /** Stop sound preview. */
    fun stopPreview() {
        soundPlayer.stop()
    }

    /** Toggle the active time window on/off. */
    fun toggleActiveWindow(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setActiveWindowEnabled(enabled)
        }
    }

    /** Update the start time of the active window. */
    fun setStartTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            val prefs = preferences.value
            preferencesRepository.setActiveWindow(
                startHour = hour,
                startMinute = minute,
                endHour = prefs.endHour,
                endMinute = prefs.endMinute
            )
        }
    }

    /** Update the end time of the active window. */
    fun setEndTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            val prefs = preferences.value
            preferencesRepository.setActiveWindow(
                startHour = prefs.startHour,
                startMinute = prefs.startMinute,
                endHour = hour,
                endMinute = minute
            )
        }
    }

    /** Format a time as HH:MM AM/PM. */
    fun formatTime(hour: Int, minute: Int): String {
        val amPm = if (hour < 12) "AM" else "PM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format("%d:%02d %s", displayHour, minute, amPm)
    }

    fun showSoundPicker() {
        _showSoundPicker.value = true
    }

    fun hideSoundPicker() {
        _showSoundPicker.value = false
        soundPlayer.stop()
    }

    fun showAudioRecorder() {
        _showAudioRecorder.value = true
    }

    fun hideAudioRecorder() {
        _showAudioRecorder.value = false
    }

    /** Get display name for the current sound. */
    fun getSoundDisplayName(): String {
        val prefs = preferences.value
        return soundRepository.getSoundDisplayName(prefs.soundUri, prefs.soundType)
    }

    /** Update the countdown text based on next trigger time. */
    private fun updateCountdown() {
        val prefs = preferences.value
        if (!prefs.isRunning || prefs.nextTriggerTime == 0L) {
            _countdownText.value = ""
            return
        }

        val remaining = prefs.nextTriggerTime - System.currentTimeMillis()
        if (remaining <= 0) {
            _countdownText.value = "Any moment now..."
            return
        }

        val totalSeconds = remaining / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        _countdownText.value = String.format("%d:%02d", minutes, seconds)
    }

    override fun onCleared() {
        super.onCleared()
        soundPlayer.release()
    }
}
