package com.mezberg.sleepwave.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mezberg.sleepwave.data.SleepPreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val neededSleepHours: Double = SleepPreferencesManager.DEFAULT_NEEDED_SLEEP_HOURS,
    val tempNeededSleepHours: Double = SleepPreferencesManager.DEFAULT_NEEDED_SLEEP_HOURS,
    val nightStartHour: Int = SleepPreferencesManager.DEFAULT_NIGHT_START_HOUR,
    val nightEndHour: Int = SleepPreferencesManager.DEFAULT_NIGHT_END_HOUR
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferencesManager = SleepPreferencesManager(application)
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
                _uiState.value = _uiState.value.copy(nightStartHour = hour)
            }
        }
        viewModelScope.launch {
            preferencesManager.nightEndHour.collect { hour ->
                _uiState.value = _uiState.value.copy(nightEndHour = hour)
            }
        }
    }

    fun updateTempNeededSleepHours(hours: Double) {
        _uiState.value = _uiState.value.copy(tempNeededSleepHours = hours)
    }

    fun applyNeededSleepHours() {
        viewModelScope.launch {
            preferencesManager.updateNeededSleepHours(_uiState.value.tempNeededSleepHours)
        }
    }

    fun revertChanges() {
        _uiState.value = _uiState.value.copy(
            tempNeededSleepHours = _uiState.value.neededSleepHours
        )
    }

    fun updateNightStartHour(hour: Int) {
        viewModelScope.launch {
            preferencesManager.updateNightStartHour(hour)
        }
    }

    fun updateNightEndHour(hour: Int) {
        viewModelScope.launch {
            preferencesManager.updateNightEndHour(hour)
        }
    }
} 