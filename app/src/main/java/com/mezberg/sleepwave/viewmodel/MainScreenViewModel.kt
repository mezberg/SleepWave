package com.mezberg.sleepwave.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mezberg.sleepwave.data.SleepDatabase
import com.mezberg.sleepwave.data.SleepPreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.exp
import android.util.Log
import java.text.DecimalFormat
import com.mezberg.sleepwave.MainActivity

data class SleepDebtInfo(
    val sleepDebt: Double,
    val dailySleepData: List<DailySleepInfo>
) {
    val formattedSleepDebt: String
        get() {
            val formatter = DecimalFormat("0.##")
            return if (sleepDebt > 0) "+${formatter.format(sleepDebt)}" else formatter.format(sleepDebt)
        }
}

data class DailySleepInfo(
    val date: String,
    val sleepAmount: Double,
    val daysAgo: Int
)

data class MainScreenUiState(
    val sleepDebtInfo: SleepDebtInfo? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val maxSleepDebt: Double = 0.0,
    val nightStartHour: Int = SleepPreferencesManager.DEFAULT_NIGHT_START_HOUR,
    val nightEndHour: Int = SleepPreferencesManager.DEFAULT_NIGHT_END_HOUR,
    val neededSleepHours: Double = SleepPreferencesManager.DEFAULT_NEEDED_SLEEP_HOURS
)

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {
    private val database = SleepDatabase.getDatabase(application)
    private val preferencesManager = SleepPreferencesManager(application)
    private val _uiState = MutableStateFlow(MainScreenUiState())
    val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAU = 4.0 // Time constant in days
        private const val DAYS_TO_ANALYZE = 14L
    }

    init {
        calculateSleepDebt()
        // Collect preferences changes
        viewModelScope.launch {
            preferencesManager.maxSleepDebt.collect { maxDebt ->
                _uiState.value = _uiState.value.copy(maxSleepDebt = maxDebt)
            }
        }
        viewModelScope.launch {
            preferencesManager.nightStartHour.collect { startHour ->
                _uiState.value = _uiState.value.copy(nightStartHour = startHour)
            }
        }
        viewModelScope.launch {
            preferencesManager.nightEndHour.collect { endHour ->
                _uiState.value = _uiState.value.copy(nightEndHour = endHour)
            }
        }
        viewModelScope.launch {
            preferencesManager.neededSleepHours.collect { hours ->
                _uiState.value = _uiState.value.copy(neededSleepHours = hours)
                calculateSleepDebt() // Recalculate when needed sleep hours change
            }
        }

        // Listen for app foreground events
        viewModelScope.launch {
            MainActivity.appForegroundFlow.collect {
                calculateSleepDebt()
            }
        }

        // Initial delay for first calculation
        viewModelScope.launch {
            kotlinx.coroutines.delay(800) // Wait for 800ms
            calculateSleepDebt()
        }
    }

    // Add a public function to manually trigger recalculation
    fun refreshSleepDebt() {
        calculateSleepDebt()
    }

    private fun calculateSleepDebt() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                // Get current time and check if we're still in night period
                val currentDate = Calendar.getInstance()
                val currentHour = currentDate.get(Calendar.HOUR_OF_DAY)
                val nightStartHour = _uiState.value.nightStartHour
                val nightEndHour = _uiState.value.nightEndHour
                
                // Determine if we should skip the current night
                val isInNightPeriod = if (nightStartHour > nightEndHour) {
                    // Night crosses midnight (e.g., 19:00-06:00)
                    // We're in night period if:
                    // - It's after night start (e.g., 20:00)
                    // - Or before night end (e.g., 05:00)
                    currentHour >= nightStartHour || currentHour < nightEndHour
                } else {
                    // Night is within same day (e.g., 02:00-10:00)
                    currentHour <= nightEndHour
                }

                // Get the latest sleep period to check if we have any sleep for the current night
                val latestSleep = database.sleepPeriodDao().getLatestSleepPeriod()
                
                // Calculate current night's date using the same logic as in SleepTrackingViewModel
                val currentNightDate = Calendar.getInstance().apply {
                    // Reset to start of day
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    
                    // If night period crosses midnight (e.g., 21:00-06:00)
                    if (nightStartHour > nightEndHour) {
                        // If current hour is between midnight and night end, this belongs to previous day's night
                        if (currentHour < nightEndHour) {
                            add(Calendar.DAY_OF_YEAR, -1)
                        }
                    }
                }.time

                // Set end date to previous day only if we're in night period AND no sleep detected for current night
                val endDate = Calendar.getInstance().apply {
                    if (isInNightPeriod && (latestSleep == null || latestSleep.sleepDate != currentNightDate)) {
                        add(Calendar.DAY_OF_YEAR, -1)
                    }
                    // Set time to end of day to include all sleep periods
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                
                val startDate = Calendar.getInstance().apply {
                    timeInMillis = endDate.timeInMillis
                    // Reset to start of the day for proper day calculation
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_YEAR, -DAYS_TO_ANALYZE.toInt())
                }

                val sleepPeriods = database.sleepPeriodDao().getSleepPeriodsBetweenDates(
                    startDate.time,
                    endDate.time
                )

                // Calculate sleep debt using sleepDate from DB
                var totalSleepDebt = 0.0
                val dailySleepInfo = mutableListOf<DailySleepInfo>()
                val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())

                // Group sleep periods by sleepDate (already calculated in DB)
                val dailySleepMap = sleepPeriods.groupBy { it.sleepDate }

                // Find the earliest date with sleep data
                val earliestSleepDate = dailySleepMap.keys.minOrNull()
                if (earliestSleepDate == null) {
                    _uiState.value = _uiState.value.copy(
                        sleepDebtInfo = null,
                        isLoading = false
                    )
                    return@launch
                }

                // Calculate how many days to analyze based on available data
                val daysToAnalyze = ((endDate.timeInMillis - earliestSleepDate.time) / (24 * 60 * 60 * 1000)).toInt()
                val daysToUse = minOf(daysToAnalyze + 1, DAYS_TO_ANALYZE.toInt())

                for (i in 0 until daysToUse) {
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = endDate.timeInMillis
                    cal.add(Calendar.DAY_OF_YEAR, -i)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    
                    val date = cal.time
                    // Skip if this date is before our earliest sleep data
                    if (date.before(earliestSleepDate)) {
                        break
                    }

                    val sleepPeriodsForDay = dailySleepMap[date] ?: emptyList()
                    //Log.d("MainScreenViewModel", "Sleep periods for day: $sleepPeriodsForDay")
                    val totalSleepHours = sleepPeriodsForDay.sumOf { it.duration } / 60.0 // Convert minutes to hours
                    //Log.d("MainScreenViewModel", "Total sleep hours: $totalSleepHours")
                    val sleepDeficit = totalSleepHours - _uiState.value.neededSleepHours
                    //Log.d("MainScreenViewModel", "Sleep deficit: $sleepDeficit")
                    val daysAgo = i
                    //Log.d("MainScreenViewModel", "Days ago: $daysAgo")
                    val debtContribution = sleepDeficit * exp(-daysAgo / TAU)
                    //Log.d("MainScreenViewModel", "Debt contribution: $debtContribution")
                    totalSleepDebt += debtContribution
                    //Log.d("MainScreenViewModel", "Total sleep debt: $totalSleepDebt")

                    dailySleepInfo.add(
                        DailySleepInfo(
                            date = dateFormat.format(date),
                            sleepAmount = totalSleepHours,
                            daysAgo = daysAgo
                        )
                    )
                }

                val sleepDebtInfo = SleepDebtInfo(
                    sleepDebt = totalSleepDebt,
                    dailySleepData = dailySleepInfo
                )

                // Update max sleep debt if current debt is higher (remember sleep debt is negative)
                if (-totalSleepDebt > _uiState.value.maxSleepDebt) {
                    preferencesManager.updateMaxSleepDebt(-totalSleepDebt)
                }

                _uiState.value = _uiState.value.copy(
                    sleepDebtInfo = sleepDebtInfo,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
} 