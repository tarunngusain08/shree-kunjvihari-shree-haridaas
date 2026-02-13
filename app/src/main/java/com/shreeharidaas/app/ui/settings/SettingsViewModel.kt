package com.shreeharidaas.app.ui.settings

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shreeharidaas.app.data.PreferencesRepository
import com.shreeharidaas.app.data.UserPreferences
import com.shreeharidaas.app.service.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the settings screen.
 * Manages DND override, vibration, and battery optimization state.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesRepository = PreferencesRepository(application)
    private val notificationHelper = NotificationHelper(application)

    private val notificationManager = application.getSystemService(
        Context.NOTIFICATION_SERVICE
    ) as NotificationManager

    /** Current user preferences as a reactive state. */
    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserPreferences()
        )

    /** Whether DND policy access is currently granted. */
    private val _isDndPermissionGranted = MutableStateFlow(false)
    val isDndPermissionGranted: StateFlow<Boolean> = _isDndPermissionGranted.asStateFlow()

    /** Whether battery optimization is already disabled. */
    private val _isBatteryOptimized = MutableStateFlow(true)
    val isBatteryOptimized: StateFlow<Boolean> = _isBatteryOptimized.asStateFlow()

    /** Refresh permission states (call on screen resume). */
    fun refreshPermissionStates() {
        val dndGranted = notificationManager.isNotificationPolicyAccessGranted
        _isDndPermissionGranted.value = dndGranted

        // If DND permission was revoked, reset the toggle
        val prefs = preferences.value
        if (!dndGranted && prefs.dndOverride) {
            viewModelScope.launch {
                preferencesRepository.setDndOverride(false)
                notificationHelper.updateDndBypass(false)
            }
        }

        // Check battery optimization status
        val powerManager = getApplication<Application>().getSystemService(
            Context.POWER_SERVICE
        ) as PowerManager
        _isBatteryOptimized.value = !powerManager.isIgnoringBatteryOptimizations(
            getApplication<Application>().packageName
        )
    }

    /** Toggle DND override. Returns true if permission is needed. */
    fun toggleDndOverride(enabled: Boolean): Boolean {
        if (enabled && !notificationManager.isNotificationPolicyAccessGranted) {
            return true // Caller should open DND settings
        }

        viewModelScope.launch {
            preferencesRepository.setDndOverride(enabled)
            notificationHelper.updateDndBypass(enabled)
        }
        return false
    }

    /** Toggle vibration. */
    fun toggleVibration(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setVibrationEnabled(enabled)
        }
    }

    /** Get the app version string. */
    fun getAppVersion(): String {
        return try {
            val packageInfo = getApplication<Application>().packageManager
                .getPackageInfo(getApplication<Application>().packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }
}
