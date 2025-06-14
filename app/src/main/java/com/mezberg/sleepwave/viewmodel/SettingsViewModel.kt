package com.mezberg.sleepwave.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mezberg.sleepwave.data.SleepPreferencesManager
import com.mezberg.sleepwave.notifications.NotificationHelper
import com.mezberg.sleepwave.utils.PermissionUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll


data class SettingsUiState(
    val neededSleepHours: Double = SleepPreferencesManager.DEFAULT_NEEDED_SLEEP_HOURS,
    val tempNeededSleepHours: Double = SleepPreferencesManager.DEFAULT_NEEDED_SLEEP_HOURS,
    val nightStartHour: Int = SleepPreferencesManager.DEFAULT_NIGHT_START_HOUR,
    val nightEndHour: Int = SleepPreferencesManager.DEFAULT_NIGHT_END_HOUR,
    val tempNightStartHour: Int = SleepPreferencesManager.DEFAULT_NIGHT_START_HOUR,
    val tempNightEndHour: Int = SleepPreferencesManager.DEFAULT_NIGHT_END_HOUR,
    val notificationsEnabled: Boolean = true,
    val showNotificationPermissionRequest: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferencesManager = SleepPreferencesManager(application)
    private val notificationHelper = NotificationHelper(application)
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Collect preferences
        viewModelScope.launch {
            preferencesManager.neededSleepHours.collect { hours ->
                _uiState.value = _uiState.value.copy(
                    neededSleepHours = hours,
                    tempNeededSleepHours = hours
                )
            }
        }
        viewModelScope.launch {
            preferencesManager.nightStartHour.collect { hour ->
                _uiState.value = _uiState.value.copy(
                    nightStartHour = hour,
                    tempNightStartHour = hour
                )
            }
        }
        viewModelScope.launch {
            preferencesManager.nightEndHour.collect { hour ->
                _uiState.value = _uiState.value.copy(
                    nightEndHour = hour,
                    tempNightEndHour = hour
                )
            }
        }
        viewModelScope.launch {
            preferencesManager.notificationsEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(notificationsEnabled = enabled)
            }
        }
    }

    fun updateTempNeededSleepHours(hours: Double) {
        _uiState.value = _uiState.value.copy(tempNeededSleepHours = hours)
    }

    fun updateTempNightStartHour(hour: Int) {
        if (hour in 0..23) {
            _uiState.value = _uiState.value.copy(tempNightStartHour = hour)
        }
    }

    fun updateTempNightEndHour(hour: Int) {
        if (hour in 0..23) {
            _uiState.value = _uiState.value.copy(tempNightEndHour = hour)
        }
    }

    fun applyNeededSleepHours() {
        viewModelScope.launch {
            preferencesManager.updateNeededSleepHours(_uiState.value.tempNeededSleepHours)
        }
    }

    fun applyNightHours() {
        viewModelScope.launch {
            coroutineScope {
                val startUpdate = async { 
                    preferencesManager.updateNightStartHour(_uiState.value.tempNightStartHour)
                }
                val endUpdate = async {
                    preferencesManager.updateNightEndHour(_uiState.value.tempNightEndHour)
                }
                awaitAll(startUpdate, endUpdate)
            }
        }
    }

    fun revertChanges() {
        _uiState.value = _uiState.value.copy(
            tempNeededSleepHours = _uiState.value.neededSleepHours,
            tempNightStartHour = _uiState.value.nightStartHour,
            tempNightEndHour = _uiState.value.nightEndHour
        )
    }

    fun isTimeValid(hour: Int): Boolean {
        return hour in 0..23
    }

    fun resetOnboarding() {
        viewModelScope.launch {
            preferencesManager.resetOnboarding()
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        if (enabled) {
            // Always request permission when trying to enable notifications
            // This will trigger the permission dialog if it was previously denied
            _uiState.value = _uiState.value.copy(showNotificationPermissionRequest = true)
            return
        }

        viewModelScope.launch {
            // Disable and cancel all notifications
            preferencesManager.updateNotificationsEnabled(false)
            notificationHelper.cancelAllScheduledNotifications()
        }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(showNotificationPermissionRequest = false)
        
        viewModelScope.launch {
            if (granted) {
                try {
                    // Enable notifications and schedule them
                    preferencesManager.updateNotificationsEnabled(true)
                    notificationHelper.scheduleNotifications()
                } catch (e: Exception) {
                    // Log the error but don't crash
                    android.util.Log.e("SettingsViewModel", "Error enabling notifications", e)
                }
            } else {
                // Update UI to reflect that notifications are still disabled
                preferencesManager.updateNotificationsEnabled(false)
                _uiState.value = _uiState.value.copy(notificationsEnabled = false)
            }
        }
    }
} 