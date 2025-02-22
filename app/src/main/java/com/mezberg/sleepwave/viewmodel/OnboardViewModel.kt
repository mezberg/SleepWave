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
    val totalSteps: Int = 4,
    val hasCompletedOnboarding: Boolean = false,
    val shouldNavigateToMain: Boolean = false
) {
    val currentStepContent: String
        get() = when (currentStep) {
            0 -> """
                **What is Sleep Debt?**
                
                Sleep debt is the total amount of sleep you've missed compared to your ideal sleep duration. The more debt you accumulate, the harder it is for your body to fully recover.
                
                To feel your best, you should aim to keep your sleep debt as low as possible by maintaining a consistent sleep schedule and catching up strategically over time.
            """.trimIndent()
            
            1 -> """
                How Do We Calculate Sleep Debt?
                
                We compare:
                ✅ Your ideal sleep duration (what your body needs)
                ✅ Your actual sleep (tracked automatically)
                
                Missed sleep hours accumulate over days, affecting how rested you feel. Keeping sleep debt low improves overall energy and cognitive function.
            """.trimIndent()
            
            2 -> """
                Energy Levels & Circadian Rhythms
                
                Your energy naturally rises and falls throughout the day based on circadian rhythms—your internal 24-hour clock. We analyze your sleep patterns to predict when you'll feel most alert or fatigued.
                
                Typical energy cycles include:
                🌅 Morning Boost – Higher alertness after waking up
                📉 Afternoon Dip – Natural drop in energy mid-day
                🌙 Evening Wind-Down – Body prepares for sleep
                
                By tracking your rhythm, we help you plan activities at the best times for peak performance.
            """.trimIndent()
            
            3 -> """
                Customize for Best Accuracy
                
                To get the most precise insights, go to Settings and set:
                ⚡ Preferred bedtime – When you ideally go to sleep
                ⏰ Wake-up time – Your target wake-up schedule
                😴 Ideal sleep duration – How much sleep you aim for
                
                The more accurate your settings, the better we can track sleep debt and predict energy.
            """.trimIndent()
            
            else -> ""
        }
}

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