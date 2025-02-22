package com.mezberg.sleepwave.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mezberg.sleepwave.data.SleepDatabase
import com.mezberg.sleepwave.data.SleepPreferencesManager
import com.mezberg.sleepwave.data.SleepPeriodEntity
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
import com.mezberg.sleepwave.notifications.NotificationHelper

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

data class WakeUpInfo(
    val date: Date,
    val wakeUpTime: Date
)

data class EnergyTimePoint(
    val time: Date,
    val type: EnergyPointType
)

enum class EnergyPointType {
    WAKE_UP,
    MORNING_PEAK,
    AFTERNOON_DIP,
    EVENING_PEAK
}

data class EnergyLevelsInfo(
    /*val averageWakeUpTime: Date?,
    val wakeUpTimes: List<WakeUpInfo>,*/
    val energyPoints: List<EnergyTimePoint>
)

data class MainScreenUiState(
    val sleepDebtInfo: SleepDebtInfo? = null,
    val energyLevelsInfo: EnergyLevelsInfo? = null,
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
    private val notificationHelper = NotificationHelper(application)
    private val _uiState = MutableStateFlow(MainScreenUiState())
    val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAU = 4.0 // Time constant in days
        private const val DAYS_TO_ANALYZE = 14L
        private const val WAKE_UP_WINDOW_HOURS = 3 // Hours after night end to look for wake up time
        
        // Energy level timing constants (hours after wake up)
        private const val MORNING_PEAK_HOURS = 3.0
        private const val AFTERNOON_DIP_HOURS = 8.0
        private const val EVENING_PEAK_HOURS = 11.0
    }

    init {
        // Create notification channel
        notificationHelper.createNotificationChannel()
        
        calculateSleepDebt()
        calculateEnergyLevels()
        // Collect preferences changes
        viewModelScope.launch {
            preferencesManager.maxSleepDebt.collect { maxDebt ->
                _uiState.value = _uiState.value.copy(maxSleepDebt = maxDebt)
            }
        }
        viewModelScope.launch {
            preferencesManager.nightStartHour.collect { startHour ->
                _uiState.value = _uiState.value.copy(nightStartHour = startHour)
                // Recalculate energy levels when night hours change
                calculateEnergyLevels()
            }
        }
        viewModelScope.launch {
            preferencesManager.nightEndHour.collect { endHour ->
                _uiState.value = _uiState.value.copy(nightEndHour = endHour)
                // Recalculate energy levels when night hours change
                calculateEnergyLevels()
            }
        }
        viewModelScope.launch {
            preferencesManager.neededSleepHours.collect { hours ->
                _uiState.value = _uiState.value.copy(neededSleepHours = hours)
                calculateSleepDebt() // Recalculate when needed sleep hours change
                calculateEnergyLevels()
            }
        }

        // Listen for app foreground events
        viewModelScope.launch {
            MainActivity.appForegroundFlow.collect {
                calculateSleepDebt()
                calculateEnergyLevels()
                // Check if we should dismiss today's notification
                _uiState.value.energyLevelsInfo?.energyPoints?.find { it.type == EnergyPointType.WAKE_UP }?.let { wakeUpPoint ->
                    notificationHelper.dismissTodayNotification(wakeUpPoint.time, _uiState.value.neededSleepHours)
                }
            }
        }

        // Initial delay for first calculation
        viewModelScope.launch {
            kotlinx.coroutines.delay(800) // Wait for 800ms
            calculateSleepDebt()
            calculateEnergyLevels()
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
                    // If night period crosses midnight (e.g., 21:00-06:00)
                    if (nightStartHour > nightEndHour) {
                        // If current hour is between midnight and night end, this belongs to previous day's night
                        if (currentHour < nightEndHour) {
                            add(Calendar.DAY_OF_YEAR, -1)
                        } else {
                            add(Calendar.DAY_OF_YEAR, 1)
                        }
                    }
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time
                Log.d("MainScreenViewModel", "Current night date: $currentNightDate")
                // Set end date to current night date first
                val endDate = Calendar.getInstance().apply {
                    time = currentNightDate
                    // If we're in night period AND no sleep detected for current night, skip to previous day
                    if (isInNightPeriod && (latestSleep == null || latestSleep.sleepDate != currentNightDate)) {
                        add(Calendar.DAY_OF_YEAR, -1)
                        Log.d("MainScreenViewModel", "Setting end date to previous day: $currentNightDate")
                        Log.d("MainScreenViewModel", "Is in night period: $isInNightPeriod")
                        Log.d("MainScreenViewModel", "Latest sleep: $latestSleep")
                    }
                    // Set time to end of day to include all sleep periods
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                Log.d("MainScreenViewModel", "End date: ${endDate.time}")
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
                    Log.d("MainScreenViewModel", "Calculating sleep debt for date: ${cal.time}")
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

    private fun calculateEnergyLevels() {
        viewModelScope.launch {
            try {
                val endDate = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }

                // Calculate current night's date
                val currentDate = Calendar.getInstance()
                val currentHour = currentDate.get(Calendar.HOUR_OF_DAY)
                val nightStartHour = _uiState.value.nightStartHour
                val nightEndHour = _uiState.value.nightEndHour

                val currentNightDate = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    
                    if (nightStartHour > nightEndHour) {
                        if (currentHour < nightEndHour) {
                            add(Calendar.DAY_OF_YEAR, -1)
                        }
                    }
                }.time

                // Get current night's sleep periods
                val currentNightSleepPeriods = database.sleepPeriodDao().getSleepPeriodsByDate(currentNightDate)

                // Calculate current wake-up time
                val currentWakeUpTime = calculateWakeUpTime(currentNightDate, currentNightSleepPeriods, nightStartHour, nightEndHour)

                /*// Get historical data for average calculation
                val startDate = Calendar.getInstance().apply {
                    timeInMillis = endDate.timeInMillis
                    add(Calendar.DAY_OF_YEAR, -DAYS_TO_ANALYZE.toInt())
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                } 

                val sleepPeriods = database.sleepPeriodDao().getSleepPeriodsBetweenDates(
                    startDate.time,
                    endDate.time
                )

                val wakeUpTimes = mutableListOf<WakeUpInfo>()
                val sleepByDate = sleepPeriods.groupBy { it.sleepDate }

                // Calculate historical wake-up times (excluding current date)
                for ((date, periods) in sleepByDate) {
                    if (date == currentNightDate) continue
                    
                    val wakeUpTime = calculateWakeUpTime(date, periods, nightStartHour, nightEndHour)
                    wakeUpTime?.let {
                        wakeUpTimes.add(WakeUpInfo(date = date, wakeUpTime = it))
                    }
                }

                // Calculate average wake-up time from historical data
                var averageWakeUpTime: Date? = null
                if (wakeUpTimes.isNotEmpty()) {
                    val totalMinutes = wakeUpTimes.sumOf { wakeUpInfo ->
                        val cal = Calendar.getInstance().apply { time = wakeUpInfo.wakeUpTime }
                        cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                    }
                    val avgMinutes = totalMinutes / wakeUpTimes.size
                    
                    averageWakeUpTime = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, avgMinutes / 60)
                        set(Calendar.MINUTE, avgMinutes % 60)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.time
                } */

                // Calculate energy points using current wake-up time
                val energyPoints = currentWakeUpTime?.let { wakeUpTime ->
                    // Schedule notifications when wake-up time changes
                    notificationHelper.scheduleEnergyNotifications(wakeUpTime, _uiState.value.neededSleepHours)
                    calculateEnergyTimePoints(wakeUpTime)
                } ?: emptyList()

                _uiState.value = _uiState.value.copy(
                    energyLevelsInfo = EnergyLevelsInfo(
                        /*averageWakeUpTime = averageWakeUpTime,
                        wakeUpTimes = wakeUpTimes,*/
                        energyPoints = energyPoints
                    )
                )
            } catch (e: Exception) {
                Log.e("MainScreenViewModel", "Error calculating energy levels", e)
                _uiState.value = _uiState.value.copy(
                    error = "Error calculating energy levels: ${e.message}"
                )
            }
        }
    }

    private fun calculateWakeUpTime(date: Date, periods: List<SleepPeriodEntity>, nightStartHour: Int, nightEndHour: Int): Date? {
        val nightEndCal = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, nightEndHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val nightStartCal = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, nightStartHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val wakeUpWindowEndCal = Calendar.getInstance().apply {
            time = nightEndCal.time
            add(Calendar.HOUR_OF_DAY, WAKE_UP_WINDOW_HOURS)
        }

        return periods.filter { period ->
            val periodStartCal = Calendar.getInstance().apply {
                timeInMillis = period.start.time
            }
            periodStartCal.time.after(nightStartCal.time) && 
            periodStartCal.time.before(wakeUpWindowEndCal.time)
        }.maxByOrNull { it.end }?.let { period ->
            Calendar.getInstance().apply {
                timeInMillis = period.end.time
            }.time
        } ?: nightEndCal.time // Default to night end hour if no wake-up time found
    }

    private fun calculateEnergyTimePoints(wakeUpTime: Date): List<EnergyTimePoint> {
        val points = mutableListOf<EnergyTimePoint>()
        
        // Add wake up point
        points.add(EnergyTimePoint(wakeUpTime, EnergyPointType.WAKE_UP))
        
        // Calculate other points based on wake up time
        val calendar = Calendar.getInstance()
        
        // Morning peak (3 hours after wake up)
        calendar.time = wakeUpTime
        calendar.add(Calendar.HOUR_OF_DAY, MORNING_PEAK_HOURS.toInt())
        points.add(EnergyTimePoint(calendar.time, EnergyPointType.MORNING_PEAK))
        
        // Afternoon dip (8 hours after wake up)
        calendar.time = wakeUpTime
        calendar.add(Calendar.HOUR_OF_DAY, AFTERNOON_DIP_HOURS.toInt())
        points.add(EnergyTimePoint(calendar.time, EnergyPointType.AFTERNOON_DIP))
        
        // Evening peak (11 hours after wake up)
        calendar.time = wakeUpTime
        calendar.add(Calendar.HOUR_OF_DAY, EVENING_PEAK_HOURS.toInt())
        points.add(EnergyTimePoint(calendar.time, EnergyPointType.EVENING_PEAK))
        
        return points
    }

    // Add a public function to manually trigger recalculation
    fun refreshEnergyLevels() {
        calculateEnergyLevels()
    }
} 