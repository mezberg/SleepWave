package com.mezberg.sleepwave

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mezberg.sleepwave.data.SleepDatabase
import com.mezberg.sleepwave.data.SleepPeriodEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import android.util.Log

// Data class to hold information about any period when the screen was off
data class ScreenOffPeriod(
    val start: Date,
    val end: Date,
    val duration: Long,  // Duration in minutes
    var isPotentialSleep: Boolean = false
)

class SleepTrackingActivity : AppCompatActivity() {
    
    companion object {
        private const val NIGHT_START_HOUR = 19 // 7 PM
        private const val NIGHT_END_HOUR = 6    // 6 AM
        private const val DEFAULT_DAYS_TO_FETCH = 14L
    }
    
    // This will hold our TextView where we display the sleep data
    private lateinit var screenEventsTextView: TextView
    private lateinit var database: SleepDatabase
    
    // Flag to prevent simultaneous execution of analyzeSleepData
    private var isAnalyzing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sleep_tracking)

        // Initialize database
        database = SleepDatabase.getDatabase(this)

        // Get reference to our TextView where we'll show the sleep periods
        screenEventsTextView = findViewById(R.id.screenEventsTextView)

        // Set up the permission button click handler
        findViewById<Button>(R.id.permissionButton).setOnClickListener {
            // Open the usage access settings screen
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        // Try to analyze sleep data if we have permission
        if (hasUsageStatsPermission()) {
            analyzeSleepData()
            displaySleepPeriods()
        } else {
            screenEventsTextView.setText(R.string.grant_permission_message)
        }
    }

    // This function checks if we have permission to access usage stats
    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private suspend fun checkLatestSleep(): Date? {
        return try {
            val latestSleep = database.sleepPeriodDao().getLatestSleepPeriod()
            Log.d("SleepTrackingActivity", "Latest sleep: ${latestSleep?.end}")
            latestSleep?.end
        } catch (e: Exception) {
            Log.d("SleepTrackingActivity", "Error getting latest sleep: ${e.message}")
            null
        }
    }

    // This function fetches screen on/off events for the specified time range
    private fun fetchScreenUsageEvents(startTime: Long, endTime: Long): List<Pair<Long, String>> {
        // Get the UsageStatsManager service
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        // Create a list to store our events
        val screenEvents = mutableListOf<Pair<Long, String>>()

        // Query the events
        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()

        // Loop through all events
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            
            // Check if this is a screen event (on or off)
            when (event.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> 
                    screenEvents.add(Pair(event.timeStamp, "SCREEN_ON"))
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> 
                    screenEvents.add(Pair(event.timeStamp, "SCREEN_OFF"))
            }
        }

        return screenEvents
    }

    // Convert screen events into periods when screen was off
    private fun getScreenOffPeriods(events: List<Pair<Long, String>>): List<ScreenOffPeriod> {
        val screenOffPeriods = mutableListOf<ScreenOffPeriod>()
        var screenOffTime: Long? = null

        // Go through each event
        for (i in events.indices) {
            val (timestamp, eventType) = events[i]
            
            when (eventType) {
                "SCREEN_OFF" -> screenOffTime = timestamp
                "SCREEN_ON" -> {
                    // If we have a previous screen off time, calculate the period
                    screenOffTime?.let { offTime ->
                        val duration = TimeUnit.MILLISECONDS.toMinutes(timestamp - offTime)
                        screenOffPeriods.add(
                            ScreenOffPeriod(
                                start = Date(offTime),
                                end = Date(timestamp),
                                duration = duration,
                                isPotentialSleep = false  // Initially set to false
                            )
                        )
                    }
                    screenOffTime = null
                }
            }
        }
        return screenOffPeriods
    }

    // Mark periods that could be sleep based on duration and time
    private fun markSleepPeriods(screenOffPeriods: List<ScreenOffPeriod>) {
        val calendar = Calendar.getInstance()
        
        for (period in screenOffPeriods) {
            calendar.time = period.start
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            
            // Check if period starts between NIGHT_START_HOUR and NIGHT_END_HOUR and is longer than 1.5 hours
            if ((hour >= NIGHT_START_HOUR || hour < NIGHT_END_HOUR) && period.duration >= 90) {
                period.isPotentialSleep = true
            }
        }
    }

    // Get only the periods marked as potential sleep
    private fun getSleepPeriods(screenOffPeriods: List<ScreenOffPeriod>): List<ScreenOffPeriod> {
        return screenOffPeriods.filter { it.isPotentialSleep }
    }

    // Delete false sleep periods based on proximity and duration rules
    private fun deleteFalseSleeps(screenOffPeriods: List<ScreenOffPeriod>): List<ScreenOffPeriod> {
        // Get only potential sleep periods, sorted by start time
        val sleepPeriods = screenOffPeriods.filter { it.isPotentialSleep }.sortedBy { it.start }
        val periodsToRemove = mutableSetOf<ScreenOffPeriod>()
        
        // Helper function to check if two dates are in the same night (NIGHT_START_HOUR-NIGHT_END_HOUR)
        fun areSameNight(date1: Date, date2: Date): Boolean {
            val cal1 = Calendar.getInstance().apply { time = date1 }
            val cal2 = Calendar.getInstance().apply { time = date2 }
            
            // If first date is before midnight and second is after, adjust second date back one day
            if (cal1.get(Calendar.HOUR_OF_DAY) >= NIGHT_START_HOUR && cal2.get(Calendar.HOUR_OF_DAY) < NIGHT_END_HOUR) {
                cal2.add(Calendar.DAY_OF_YEAR, -1)
            }
            
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                   cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
        }

        for (i in 0 until sleepPeriods.size - 1) {
            val currentPeriod = sleepPeriods[i]
            val nextPeriod = sleepPeriods[i + 1]
            
            // Check if periods are in the same night
            if (areSameNight(currentPeriod.start, nextPeriod.start)) {
                // Calculate time between periods in minutes
                val timeBetween = TimeUnit.MILLISECONDS.toMinutes(
                    nextPeriod.start.time - currentPeriod.end.time
                )
                
                // If periods are more than 0.5 hour apart
                if (timeBetween > 15) {
                    // Find the shorter period
                    val shorterPeriod = if (currentPeriod.duration < nextPeriod.duration) {
                        currentPeriod
                    } else {
                        nextPeriod
                    }
                    
                    // Only remove if shorter period is less than 3 hours
                    if (shorterPeriod.duration < 180) {
                        periodsToRemove.add(shorterPeriod)
                    }
                }
            }
        }

        // Return all periods except those marked for removal
        return screenOffPeriods.map { period ->
            if (period in periodsToRemove) {
                period.copy(isPotentialSleep = false)
            } else {
                period
            }
        }
    }

    // Mark additional sleep periods based on proximity to existing sleep periods
    private fun markAdditionalSleeps(screenOffPeriods: List<ScreenOffPeriod>): List<ScreenOffPeriod> {
        var madeChanges: Boolean
        val calendar = Calendar.getInstance()
        
        // Helper function to check if time is outside night period
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
                    // Look at all subsequent periods that start within 30 minutes
                    for (j in (i + 1) until sortedPeriods.size) {
                        val nextPeriod = sortedPeriods[j]
                        if (!nextPeriod.isPotentialSleep) {
                            // Calculate time between periods in minutes
                            val timeBetween = TimeUnit.MILLISECONDS.toMinutes(
                                nextPeriod.start.time - currentPeriod.end.time
                            )
                            
                            // If we've gone beyond 30 minutes, stop checking further periods
                            if (timeBetween > 30) {
                                break
                            }
                            
                            // Mark as sleep only if period is longer than 30 minutes and starts outside night period
                            if (nextPeriod.duration > 30 && isOutsideNightPeriod(nextPeriod.start)) {
                                nextPeriod.isPotentialSleep = true
                                madeChanges = true
                                Log.d("SleepTrackingActivity", "Marked additional sleep period: $nextPeriod (after ${currentPeriod})")
                            }
                        }
                    }
                }
            }
        } while (madeChanges)
        
        return screenOffPeriods
    }

    // Main function to analyze sleep data
    private fun analyzeSleepData() {
        // Check if analysis is already running
        if (isAnalyzing) {
            Log.d("SleepTrackingActivity", "Sleep analysis already in progress, skipping...")
            return
        }

        lifecycleScope.launch {
            // Set the flag to indicate analysis is starting
            isAnalyzing = true
            
            try {
                // Get the current time
                val endTime = System.currentTimeMillis()
                
                // Get the start time based on latest sleep or default to 14 days
                val startTime = checkLatestSleep()?.time ?: run {
                    val calendar = Calendar.getInstance()
                    calendar.add(Calendar.DAY_OF_YEAR, -DEFAULT_DAYS_TO_FETCH.toInt())
                    Log.d("SleepTrackingActivity", "Did not find latest sleep, using default: ${calendar.time}")
                    calendar.timeInMillis
                }
                Log.d("SleepTrackingActivity", "Start time: ${Date(startTime)}")
                
                // Get the screen events for the time range
                val events = fetchScreenUsageEvents(startTime, endTime)
                Log.d("SleepTrackingActivity", "Fetched ${events.size} events from ${Date(startTime)} to ${Date(endTime)}")
                
                // Convert events to screen off periods
                val screenOffPeriods = getScreenOffPeriods(events)
                Log.d("SleepTrackingActivity", "Screen off periods: $screenOffPeriods")
                
                // Mark potential sleep periods
                markSleepPeriods(screenOffPeriods)
                Log.d("SleepTrackingActivity", "Marked sleep periods: $screenOffPeriods")
                
                // Remove false sleep periods
                val cleanedPeriods = deleteFalseSleeps(screenOffPeriods)
                Log.d("SleepTrackingActivity", "Cleaned periods: $cleanedPeriods")
                
                // Mark additional sleep periods
                val periodsWithAdditional = markAdditionalSleeps(cleanedPeriods)
                Log.d("SleepTrackingActivity", "Periods with additional sleep: $periodsWithAdditional")
                
                // Get only sleep periods
                val sleepPeriods = getSleepPeriods(periodsWithAdditional)
                Log.d("SleepTrackingActivity", "Sleep periods: $sleepPeriods")

                // Save new sleep periods to database
                saveSleepPeriodsToDatabase(sleepPeriods)
                
            } catch (e: Exception) {
                Log.e("SleepTrackingActivity", "Error analyzing sleep data: ${e.message}")
                screenEventsTextView.setText(getString(R.string.error_analyzing_sleep, e.message))
            } finally {
                // Reset the flag in finally block to ensure it's always reset, even if an error occurs
                isAnalyzing = false
                Log.d("SleepTrackingActivity", "Sleep analysis completed")
            }
        }
    }

    //Converts a ScreenOffPeriod (class) to a SleepPeriodEntity which is a Room entity
    private fun convertToEntity(screenOffPeriod: ScreenOffPeriod): SleepPeriodEntity {
        return SleepPeriodEntity(
            start = screenOffPeriod.start,
            end = screenOffPeriod.end,
            duration = screenOffPeriod.duration,
            isPotentialSleep = screenOffPeriod.isPotentialSleep
        )
    }

    //Saves the detected sleep periods to the database. Only saves periods that don't already exist in the database
    private suspend fun saveSleepPeriodsToDatabase(sleepPeriods: List<ScreenOffPeriod>) {
        try {
            // Get the DAO (Data Access Object) for database operations
            val sleepPeriodDao = database.sleepPeriodDao()
            
            // Counter for new periods added
            var newPeriodsCount = 0
            
            // Process each sleep period
            for (period in sleepPeriods) {
                // Check if this period already exists in the database
                val exists = sleepPeriodDao.doesSleepPeriodExist(period.start, period.end)
                Log.d("SleepTrackingActivity", "Checking if sleep period exists, sleep period start: ${period.start}, end: ${period.end}")
                if (!exists) {
                    // Convert the period to a database entity and insert it
                    val entity = convertToEntity(period)
                    sleepPeriodDao.insert(entity)
                    newPeriodsCount++
                    Log.d("SleepTrackingActivity", "Saved new sleep period: $period")
                } else {
                    Log.d("SleepTrackingActivity", "Sleep period already exists: $period")
                }
            }
            
            Log.d("SleepTrackingActivity", "Saved $newPeriodsCount new sleep periods to database")
        } catch (e: Exception) {
            Log.e("SleepTrackingActivity", "Error saving sleep periods to database: ${e.message}")
        }
    }

    // When we resume the activity (e.g., coming back from permission settings)
    override fun onResume() {
        super.onResume()
        // Check permission and update display if needed
        if (hasUsageStatsPermission()) {
            analyzeSleepData()
            displaySleepPeriods()
        }
    }

    //Displays all sleep periods from the database
    private fun displaySleepPeriods() {
        lifecycleScope.launch {
            try {
                // Get all sleep periods from database as Flow
                database.sleepPeriodDao().getAllSleepPeriods().collect { sleepPeriods ->
                    if (sleepPeriods.isEmpty()) {
                        screenEventsTextView.setText(R.string.no_sleep_periods)
                        return@collect
                    }

                    // Create date formatters for different display needs
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                    val headerDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    
                    // Group sleep periods by date (using the adjusted date logic)
                    val periodsByDate = sleepPeriods.groupBy { period ->
                        // Create calendar for the start time
                        val cal = Calendar.getInstance()
                        cal.time = period.start
                        
                        // If sleep started after midnight but before NIGHT_START_HOUR-1,
                        // we want to group it with the previous day
                        if (cal.get(Calendar.HOUR_OF_DAY) < NIGHT_START_HOUR-1) {
                            cal.add(Calendar.DAY_OF_YEAR, -1)
                        }
                        
                        // Return the date without time as the key
                        cal.set(Calendar.HOUR_OF_DAY, 0)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        cal.time
                    }
                    
                    // Build the display text
                    val displayText = StringBuilder()
                    
                    // Sort the dates in descending order (most recent first)
                    periodsByDate.keys.sortedDescending().forEach { date ->
                        val periodsForDate = periodsByDate[date] ?: return@forEach
                        
                        // Add date header
                        displayText.append("${headerDateFormat.format(date)}\n\n")
                        
                        // Add each sleep period for this date
                        periodsForDate.forEach { period ->
                            displayText.append("""
                                From: ${dateFormat.format(period.start)}
                                To: ${dateFormat.format(period.end)}
                                Duration: ${period.duration / 60} hours ${period.duration % 60} minutes
                                
                                """.trimIndent())
                            displayText.append("\n")
                        }
                        
                        // Calculate and add total sleep for this date
                        val totalMinutes = periodsForDate.sumOf { it.duration }
                        val totalHours = totalMinutes / 60
                        val remainingMinutes = totalMinutes % 60
                        
                        displayText.append("Total sleep: $totalHours hours $remainingMinutes minutes\n")
                        displayText.append("----------------------------------------\n\n")
                    }
                    
                    // Show the results
                    screenEventsTextView.text = displayText.toString()
                }
            } catch (e: Exception) {
                Log.e("SleepTrackingActivity", "Error displaying sleep periods: ${e.message}")
                screenEventsTextView.setText(getString(R.string.error_analyzing_sleep, e.message))
            }
        }
    }
} 