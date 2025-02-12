package com.mezberg.sleepwave.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mezberg.sleepwave.data.SleepPreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OnboardingState(
    val currentStep: Int = 0,
    val totalSteps: Int = 6,
    val hasCompletedOnboarding: Boolean = false,
    val shouldNavigateToMain: Boolean = false
)

class OnboardViewModel(
    private val preferencesManager: SleepPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingState())
    val uiState: StateFlow<OnboardingState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.hasCompletedOnboarding.collect { hasCompleted ->
                _uiState.value = _uiState.value.copy(
                    hasCompletedOnboarding = hasCompleted,
                    shouldNavigateToMain = hasCompleted
                )
            }
        }
    }

    fun nextStep() {
        if (_uiState.value.currentStep < _uiState.value.totalSteps - 1) {
            _uiState.value = _uiState.value.copy(
                currentStep = _uiState.value.currentStep + 1
            )
        }
    }

    fun previousStep() {
        if (_uiState.value.currentStep > 0) {
            _uiState.value = _uiState.value.copy(
                currentStep = _uiState.value.currentStep - 1
            )
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            preferencesManager.completeOnboarding()
            _uiState.value = _uiState.value.copy(shouldNavigateToMain = true)
        }
    }

    fun onNavigatedToMain() {
        _uiState.value = _uiState.value.copy(shouldNavigateToMain = false)
    }
} 