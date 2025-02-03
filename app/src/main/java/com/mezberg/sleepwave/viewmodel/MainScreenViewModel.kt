package com.mezberg.sleepwave.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mezberg.sleepwave.data.SleepDatabase
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
    val error: String? = null
)

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {
    private val database = SleepDatabase.getDatabase(application)
    private val _uiState = MutableStateFlow(MainScreenUiState())
    val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()

    companion object {
        private const val NEEDED_SLEEP_HOURS = 8.0 // Recommended sleep hours per night
        private const val TAU = 4.0 // Time constant in days
        private const val DAYS_TO_ANALYZE = 14L
        const val NIGHT_START_HOUR = 19 // 7 PM
        const val NIGHT_END_HOUR = 6 // 6 AM
    }

    init {
        calculateSleepDebt()
    }

    private fun calculateSleepDebt() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                // Get all sleep periods from the last 14 days
                val endDate = Calendar.getInstance()
                val startDate = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -DAYS_TO_ANALYZE.toInt())
                }

                val sleepPeriods = database.sleepPeriodDao().getSleepPeriodsBetweenDates(
                    startDate.time,
                    endDate.time
                )

                // Group sleep periods by date
                val dailySleepMap = sleepPeriods.groupBy { period ->
                    val cal = Calendar.getInstance()
                    cal.time = period.start
                    if (cal.get(Calendar.HOUR_OF_DAY) < NIGHT_END_HOUR) { // If sleep started before 6 AM, count it for previous day
                        cal.add(Calendar.DAY_OF_YEAR, -1)
                    }
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    cal.time
                }

                // Calculate sleep debt
                var totalSleepDebt = 0.0
                val dailySleepInfo = mutableListOf<DailySleepInfo>()
                val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
                
                // Get the current date, adjusted for night logic
                val currentDate = Calendar.getInstance()
                val currentHour = currentDate.get(Calendar.HOUR_OF_DAY)
                // If it's before 6 AM, we're still in the previous day's night
                if (currentHour < NIGHT_END_HOUR) {
                    currentDate.add(Calendar.DAY_OF_YEAR, -1)
                }
                currentDate.set(Calendar.HOUR_OF_DAY, 0)
                currentDate.set(Calendar.MINUTE, 0)
                currentDate.set(Calendar.SECOND, 0)
                currentDate.set(Calendar.MILLISECOND, 0)

                // Determine if we should include the current night in calculations
                val isNightOver = currentHour >= NIGHT_END_HOUR
                val hasCurrentNightSleep = dailySleepMap[currentDate.time]?.isNotEmpty() == true
                val shouldIncludeCurrentNight = hasCurrentNightSleep || isNightOver

                // If we're not including current night, shift back one day
                if (!shouldIncludeCurrentNight) {
                    currentDate.add(Calendar.DAY_OF_YEAR, -1)
                }

                for (i in 0 until DAYS_TO_ANALYZE.toInt()) {
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = currentDate.timeInMillis  // Start from adjusted current date
                    cal.add(Calendar.DAY_OF_YEAR, -i)
                    
                    val date = cal.time
                    val sleepPeriodsForDay = dailySleepMap[date] ?: emptyList()
                    Log.d("MainScreenViewModel", "Sleep periods for day: $sleepPeriodsForDay")
                    val totalSleepHours = sleepPeriodsForDay.sumOf { it.duration } / 60.0 // Convert minutes to hours
                    Log.d("MainScreenViewModel", "Total sleep hours: $totalSleepHours")
                    val sleepDeficit = totalSleepHours - NEEDED_SLEEP_HOURS
                    Log.d("MainScreenViewModel", "Sleep deficit: $sleepDeficit")
                    val daysAgo = i
                    Log.d("MainScreenViewModel", "Days ago: $daysAgo")
                    val debtContribution = sleepDeficit * exp(-daysAgo / TAU)
                    Log.d("MainScreenViewModel", "Debt contribution: $debtContribution")
                    totalSleepDebt += debtContribution
                    Log.d("MainScreenViewModel", "Total sleep debt: $totalSleepDebt")

                    dailySleepInfo.add(
                        DailySleepInfo(
                            date = dateFormat.format(date),
                            sleepAmount = totalSleepHours,
                            daysAgo = daysAgo
                        )
                    )
                }

                _uiState.value = _uiState.value.copy(
                    sleepDebtInfo = SleepDebtInfo(
                        sleepDebt = totalSleepDebt,
                        dailySleepData = dailySleepInfo
                    ),
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