package com.shreeharidas.app.ui.festival

import android.app.AlarmManager
import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shreeharidas.app.data.PreferencesRepository
import com.shreeharidas.app.festival.FestivalCalendarItem
import com.shreeharidas.app.festival.FestivalDataStatus
import com.shreeharidas.app.festival.FestivalNotificationStatus
import com.shreeharidas.app.festival.FestivalPreferences
import com.shreeharidas.app.festival.FestivalUiState
import com.shreeharidas.app.festival.data.FestivalRepository
import com.shreeharidas.app.festival.data.FestivalRepositoryState
import com.shreeharidas.app.festival.notification.FestivalNotificationScheduler
import com.shreeharidas.app.util.PermissionUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FestivalViewModel(application: Application) : AndroidViewModel(application) {

    private val festivalRepository = FestivalRepository(application)
    private val preferencesRepository = PreferencesRepository(application)
    private val notificationScheduler = FestivalNotificationScheduler(application)

    private val _uiState = MutableStateFlow(FestivalUiState())
    val uiState: StateFlow<FestivalUiState> = _uiState.asStateFlow()

    private var repositoryState: FestivalRepositoryState? = null
    private var festivalPreferences = FestivalPreferences()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repositoryState = festivalRepository.loadState()
            festivalPreferences = preferencesRepository.getFestivalPreferences()

            val years = repositoryState?.availableYears.orEmpty()
            val selectedYear = when {
                _uiState.value.selectedYear in years -> _uiState.value.selectedYear
                years.contains(java.time.LocalDate.now().year) ->
                    java.time.LocalDate.now().year
                years.isNotEmpty() -> years.first()
                else -> java.time.LocalDate.now().year
            }

            renderState(selectedYear = selectedYear)
            notificationScheduler.resyncUpcomingNotifications()
        }
    }

    fun onSearchChanged(query: String) {
        renderState(query = query)
    }

    fun previousYear() {
        renderState(selectedYear = _uiState.value.selectedYear - 1)
    }

    fun nextYear() {
        renderState(selectedYear = _uiState.value.selectedYear + 1)
    }

    fun selectItem(item: FestivalCalendarItem) {
        _uiState.value = _uiState.value.copy(selectedItem = item)
    }

    fun clearSelectedItem() {
        _uiState.value = _uiState.value.copy(selectedItem = null)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setFestivalNotificationsEnabled(enabled)
            festivalPreferences = preferencesRepository.getFestivalPreferences()
            renderState()
            notificationScheduler.resyncUpcomingNotifications()
        }
    }

    private fun renderState(
        selectedYear: Int = _uiState.value.selectedYear,
        query: String = _uiState.value.query
    ) {
        val state = repositoryState
        if (state == null) {
            _uiState.value = FestivalUiState(isLoading = true)
            return
        }

        val message = when (state.status) {
            FestivalDataStatus.AVAILABLE -> null
            FestivalDataStatus.UNAVAILABLE ->
                "Festival data unavailable. Reviewed dates are not bundled yet."
            FestivalDataStatus.INVALID ->
                state.errors.firstOrNull() ?: "Festival data is invalid."
        }

        _uiState.value = FestivalUiState(
            isLoading = false,
            query = query,
            selectedYear = selectedYear,
            availableYears = state.availableYears,
            groups = state.groupedItemsForYear(selectedYear, query),
            upcoming = state.upcomingItems().take(5),
            notificationsEnabled = festivalPreferences.notificationsEnabled,
            notificationStatus = notificationStatus(
                dataAvailable = state.hasValidOccurrenceData,
                preferences = festivalPreferences
            ),
            dataStatus = state.status,
            message = message,
            selectedItem = _uiState.value.selectedItem
        )
    }

    private fun notificationStatus(
        dataAvailable: Boolean,
        preferences: FestivalPreferences
    ): FestivalNotificationStatus {
        if (!dataAvailable) return FestivalNotificationStatus.DATA_UNAVAILABLE
        if (!preferences.notificationsEnabled) return FestivalNotificationStatus.DISABLED
        if (!PermissionUtils.hasNotificationPermission(getApplication())) {
            return FestivalNotificationStatus.NOTIFICATION_PERMISSION_MISSING
        }
        if (!canScheduleExactAlarms()) {
            return FestivalNotificationStatus.EXACT_ALARM_PERMISSION_MISSING
        }
        return FestivalNotificationStatus.ENABLED
    }

    private fun canScheduleExactAlarms(): Boolean {
        val alarmManager = getApplication<Application>().getSystemService(
            AlarmManager::class.java
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }
}
