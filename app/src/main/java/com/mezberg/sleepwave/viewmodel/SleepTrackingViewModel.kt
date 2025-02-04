package com.mezberg.sleepwave.viewmodel

import android.app.Application
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mezberg.sleepwave.data.SleepDatabase
import com.mezberg.sleepwave.data.SleepPeriodEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import com.mezberg.sleepwave.utils.PermissionUtils

// Data class representing a period when the screen is off
data class ScreenOffPeriod(
    val start: Date,
    val end: Date,
    val duration: Long,  // Duration in minutes
    var isPotentialSleep: Boolean = false
)

// Data class representing sleep period data to be displayed in the UI
data class SleepPeriodDisplayData(
    val date: String,
    val periods: List<SleepPeriodEntity>,
    val totalSleepHours: Int,
    val totalSleepMinutes: Int
)

// Data class representing the UI state for sleep tracking
data class SleepTrackingUiState(
    val sleepPeriods: List<SleepPeriodDisplayData> = emptyList(),
    val hasPermission: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

class SleepTrackingViewModel(application: Application) : AndroidViewModel(application) {
    private val database = SleepDatabase.getDatabase(application)
    private val _uiState = MutableStateFlow(SleepTrackingUiState())
    val uiState: StateFlow<SleepTrackingUiState> = _uiState.asStateFlow()

    companion object {
        const val NIGHT_START_HOUR = 19 // 7 PM
        const val NIGHT_END_HOUR = 6 // 6 AM
        private const val DEFAULT_DAYS_TO_FETCH = 14L
    }

    // Flag to prevent simultaneous execution of analyzeSleepData
    private var isAnalyzing = false

    // Initialize the ViewModel by checking permissions and loading data
    init {
        checkPermissionAndLoadData()
    }

    private fun checkPermissionAndLoadData() {
        val hasPermission = PermissionUtils.hasUsageStatsPermission(getApplication())
        _uiState.value = _uiState.value.copy(hasPermission = hasPermission)
        if (hasPermission) {
            analyzeSleepData()
        }
    }

    // Fetch the latest sleep period from the database
    private suspend fun checkLatestSleep(): Date? {
        return try {
            val latestSleep = database.sleepPeriodDao().getLatestSleepPeriod()
            Log.d("SleepTrackingViewModel", "Latest sleep: ${latestSleep?.end}")
            latestSleep?.end
        } catch (e: Exception) {
            Log.d("SleepTrackingViewModel", "Error getting latest sleep: ${e.message}")
            null
        }
    }

    // Fetch screen usage events (screen on/off) within a given time range
    private fun fetchScreenUsageEvents(startTime: Long, endTime: Long): List<Pair<Long, String>> {
        val usageStatsManager = getApplication<Application>().getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val screenEvents = mutableListOf<Pair<Long, String>>()
        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> 
                    screenEvents.add(Pair(event.timeStamp, "SCREEN_ON"))
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> 
                    screenEvents.add(Pair(event.timeStamp, "SCREEN_OFF"))
            }
        }
        return screenEvents
    }

    private fun getScreenOffPeriods(events: List<Pair<Long, String>>): List<ScreenOffPeriod> {
        val screenOffPeriods = mutableListOf<ScreenOffPeriod>()
        var screenOffTime: Long? = null

        for (i in events.indices) {
            val (timestamp, eventType) = events[i]
            when (eventType) {
                "SCREEN_OFF" -> screenOffTime = timestamp
                "SCREEN_ON" -> {
                    screenOffTime?.let { offTime ->
                        val duration = TimeUnit.MILLISECONDS.toMinutes(timestamp - offTime)
                        screenOffPeriods.add(
                            ScreenOffPeriod(
                                start = Date(offTime),
                                end = Date(timestamp),
                                duration = duration,
                                isPotentialSleep = false
                            )
                        )
                    }
                    screenOffTime = null
                }
            }
        }
        return screenOffPeriods
    }

    private fun markSleepPeriods(screenOffPeriods: List<ScreenOffPeriod>) {
        val calendar = Calendar.getInstance()
        for (period in screenOffPeriods) {
            calendar.time = period.start
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            if ((hour >= NIGHT_START_HOUR || hour < NIGHT_END_HOUR) && period.duration >= 90) {
                period.isPotentialSleep = true
            }
        }
    }

    private fun getSleepPeriods(screenOffPeriods: List<ScreenOffPeriod>): List<ScreenOffPeriod> {
        return screenOffPeriods.filter { it.isPotentialSleep }
    }

    private fun deleteFalseSleeps(screenOffPeriods: List<ScreenOffPeriod>): List<ScreenOffPeriod> {
        val sleepPeriods = screenOffPeriods.filter { it.isPotentialSleep }.sortedBy { it.start }
        val periodsToRemove = mutableSetOf<ScreenOffPeriod>()

        fun areSameNight(date1: Date, date2: Date): Boolean {
            val cal1 = Calendar.getInstance().apply { time = date1 }
            val cal2 = Calendar.getInstance().apply { time = date2 }
            
            if (cal1.get(Calendar.HOUR_OF_DAY) >= NIGHT_START_HOUR && cal2.get(Calendar.HOUR_OF_DAY) < NIGHT_END_HOUR) {
                cal2.add(Calendar.DAY_OF_YEAR, -1)
            }
            
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                   cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
        }

        for (i in 0 until sleepPeriods.size - 1) {
            val currentPeriod = sleepPeriods[i]
            val nextPeriod = sleepPeriods[i + 1]
            
            if (areSameNight(currentPeriod.start, nextPeriod.start)) {
                val timeBetween = TimeUnit.MILLISECONDS.toMinutes(
                    nextPeriod.start.time - currentPeriod.end.time
                )
                
                if (timeBetween > 15) {
                    val shorterPeriod = if (currentPeriod.duration < nextPeriod.duration) {
                        currentPeriod
                    } else {
                        nextPeriod
                    }
                    
                    if (shorterPeriod.duration < 180) {
                        periodsToRemove.add(shorterPeriod)
                    }
                }
            }
        }

        return screenOffPeriods.map { period ->
            if (period in periodsToRemove) {
                period.copy(isPotentialSleep = false)
            } else {
                period
            }
        }
    }

    private fun markAdditionalSleeps(screenOffPeriods: List<ScreenOffPeriod>): List<ScreenOffPeriod> {
        var madeChanges: Boolean
        val calendar = Calendar.getInstance()
        
        fun isOutsideNightPeriod(date: Date): Boolean {
            calendar.time = date
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            return hour in NIGHT_END_HOUR until NIGHT_START_HOUR
        }
        
        do {
            madeChanges = false
            val sortedPeriods = screenOffPeriods.sortedBy { it.start }
            
            for (i in 0 until sortedPeriods.size) {
                val currentPeriod = sortedPeriods[i]
                
                if (currentPeriod.isPotentialSleep) {
                    for (j in (i + 1) until sortedPeriods.size) {
                        val nextPeriod = sortedPeriods[j]
                        if (!nextPeriod.isPotentialSleep) {
                            val timeBetween = TimeUnit.MILLISECONDS.toMinutes(
                                nextPeriod.start.time - currentPeriod.end.time
                            )
                            
                            if (timeBetween > 30) {
                                break
                            }
                            
                            if (nextPeriod.duration > 30 && isOutsideNightPeriod(nextPeriod.start)) {
                                nextPeriod.isPotentialSleep = true
                                madeChanges = true
                                Log.d("SleepTrackingViewModel", "Marked additional sleep period: $nextPeriod (after ${currentPeriod})")
                            }
                        }
                    }
                }
            }
        } while (madeChanges)
        
        return screenOffPeriods
    }

    private fun convertToEntity(screenOffPeriod: ScreenOffPeriod): SleepPeriodEntity {
        return SleepPeriodEntity(
            start = screenOffPeriod.start,
            end = screenOffPeriod.end,
            duration = screenOffPeriod.duration,
            isPotentialSleep = screenOffPeriod.isPotentialSleep
        )
    }

    private suspend fun saveSleepPeriodsToDatabase(sleepPeriods: List<ScreenOffPeriod>) {
        try {
            val sleepPeriodDao = database.sleepPeriodDao()
            var newPeriodsCount = 0
            
            for (period in sleepPeriods) {
                val exists = sleepPeriodDao.doesSleepPeriodExist(period.start, period.end)
                if (!exists) {
                    val entity = convertToEntity(period)
                    sleepPeriodDao.insert(entity)
                    newPeriodsCount++
                    Log.d("SleepTrackingViewModel", "Saved new sleep period: $period")
                } else {
                    Log.d("SleepTrackingViewModel", "Sleep period already exists: $period")
                }
            }
            
            Log.d("SleepTrackingViewModel", "Saved $newPeriodsCount new sleep periods to database")
        } catch (e: Exception) {
            Log.e("SleepTrackingViewModel", "Error saving sleep periods to database: ${e.message}")
            throw e
        }
    }

    fun loadSleepPeriods() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                database.sleepPeriodDao().getAllSleepPeriods().collect { periods ->
                    val groupedPeriods = periods.groupBy { period ->
                        val cal = Calendar.getInstance()
                        cal.time = period.start
                        if (cal.get(Calendar.HOUR_OF_DAY) < NIGHT_START_HOUR) {
                            cal.add(Calendar.DAY_OF_YEAR, -1)
                        }
                        cal.set(Calendar.HOUR_OF_DAY, 0)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        cal.time
                    }.map { (date, periodsForDate) ->
                        val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
                        val nextDay = Calendar.getInstance().apply {
                            time = date
                            add(Calendar.DAY_OF_YEAR, 1)
                        }
                        val displayDate = "${dateFormat.format(date)} - ${dateFormat.format(nextDay.time)}"
                        
                        val totalMinutes = periodsForDate.sumOf { it.duration }
                        SleepPeriodDisplayData(
                            date = displayDate,
                            periods = periodsForDate,
                            totalSleepHours = totalMinutes.toInt() / 60,
                            totalSleepMinutes = totalMinutes.toInt() % 60
                        )
                    }.sortedByDescending { 
                        // Sort by the actual date, not the display string
                        val parts = it.date.split(" - ")[0].trim()
                        SimpleDateFormat("MMM d", Locale.getDefault()).parse(parts)?.time ?: 0L
                    }

                    _uiState.value = _uiState.value.copy(
                        sleepPeriods = groupedPeriods,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun analyzeSleepData() {
        if (isAnalyzing) {
            Log.d("SleepTrackingViewModel", "Sleep analysis already in progress, skipping...")
            return
        }

        viewModelScope.launch {
            isAnalyzing = true
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                val endTime = System.currentTimeMillis()
                val startTime = checkLatestSleep()?.time ?: run {
                    val calendar = Calendar.getInstance()
                    calendar.add(Calendar.DAY_OF_YEAR, -DEFAULT_DAYS_TO_FETCH.toInt())
                    calendar.timeInMillis
                }

                // Fetch screen usage events and process them to identify sleep periods
                val events = fetchScreenUsageEvents(startTime, endTime)
                // Get screen off periods from the events
                val screenOffPeriods = getScreenOffPeriods(events)
                // Mark potential sleep periods
                markSleepPeriods(screenOffPeriods)
                // Delete false sleep periods
                val cleanedPeriods = deleteFalseSleeps(screenOffPeriods)
                // Mark additional sleep periods
                val periodsWithAdditional = markAdditionalSleeps(cleanedPeriods)
                // Get final sleep periods
                val sleepPeriods = getSleepPeriods(periodsWithAdditional)
                // Save sleep periods to the database
                saveSleepPeriodsToDatabase(sleepPeriods)
                // Load sleep periods for display
                loadSleepPeriods()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            } finally {
                isAnalyzing = false
            }
        }
    }

    fun updatePermissionStatus(hasPermission: Boolean) {
        _uiState.value = _uiState.value.copy(hasPermission = hasPermission)
        if (hasPermission) {
            analyzeSleepData()
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun deleteSleepPeriod(sleepPeriod: SleepPeriodEntity) {
        viewModelScope.launch {
            try {
                database.sleepPeriodDao().delete(sleepPeriod)
                loadSleepPeriods() // Refresh the UI after deletion
                kotlinx.coroutines.delay(100) // Small delay to ensure deletion is complete
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to delete sleep period: ${e.message}"
                )
            }
        }
    }

    sealed class AddSleepPeriodError : Exception() {
        object Overlap : AddSleepPeriodError() {
            override val message: String = "Sleep period overlaps with existing period"
        }
        object TooLong : AddSleepPeriodError() {
            override val message: String = "Sleep period cannot be longer than 24 hours"
        }
        object FutureDateTime : AddSleepPeriodError() {
            override val message: String = "Cannot select future dates or times"
        }
        object EndBeforeStart : AddSleepPeriodError() {
            override val message: String = "End time cannot be before start time"
        }
    }

    private suspend fun validateNewSleepPeriod(
        startDate: Date,
        startTime: String,
        endDate: Date,
        endTime: String
    ): Result<Pair<Date, Date>> {
        try {
            // Combine dates and times
            val calendar = Calendar.getInstance()
            
            // Set start date and time
            calendar.time = startDate
            val (startHour, startMinute) = startTime.split(":").map { it.toInt() }
            calendar.set(Calendar.HOUR_OF_DAY, startHour)
            calendar.set(Calendar.MINUTE, startMinute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val finalStartDate = calendar.time

            // Set end date and time
            calendar.time = endDate
            val (endHour, endMinute) = endTime.split(":").map { it.toInt() }
            calendar.set(Calendar.HOUR_OF_DAY, endHour)
            calendar.set(Calendar.MINUTE, endMinute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val finalEndDate = calendar.time

            // Check if dates are in the future
            val currentTime = System.currentTimeMillis()
            if (finalStartDate.time > currentTime || finalEndDate.time > currentTime) {
                return Result.failure(AddSleepPeriodError.FutureDateTime)
            }

            // Check if end date is before start date
            if (finalEndDate.before(finalStartDate)) {
                return Result.failure(AddSleepPeriodError.EndBeforeStart)
            }

            // Check if period is longer than 24 hours
            val durationHours = (finalEndDate.time - finalStartDate.time) / (1000 * 60 * 60.0)
            if (durationHours > 24) {
                return Result.failure(AddSleepPeriodError.TooLong)
            }

            // Check for overlapping periods
            if (database.sleepPeriodDao().hasOverlappingPeriods(finalStartDate, finalEndDate)) {
                return Result.failure(AddSleepPeriodError.Overlap)
            }

            return Result.success(Pair(finalStartDate, finalEndDate))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    suspend fun addSleepPeriod(
        startDate: Date,
        startTime: String,
        endDate: Date,
        endTime: String
    ): Result<Unit> {
        return try {
            val validationResult = validateNewSleepPeriod(startDate, startTime, endDate, endTime)
            
            validationResult.fold(
                onSuccess = { (finalStartDate, finalEndDate) ->
                    val duration = (finalEndDate.time - finalStartDate.time) / (1000 * 60) // Convert to minutes

                    val newPeriod = SleepPeriodEntity(
                        start = finalStartDate,
                        end = finalEndDate,
                        duration = duration,
                        isPotentialSleep = true
                    )

                    database.sleepPeriodDao().insert(newPeriod)
                    loadSleepPeriods() // Refresh UI
                    Result.success(Unit)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 