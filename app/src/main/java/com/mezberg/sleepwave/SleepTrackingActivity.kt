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
    
    // This will hold our TextView where we display the sleep data
    private lateinit var screenEventsTextView: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sleep_tracking)

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
        } else {
            screenEventsTextView.text = "Please grant usage access permission using the button above"
        }
    }

    // This function checks if we have permission to access usage stats
    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        // Get the mode for PACKAGE_USAGE_STATS permission
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    // This function fetches screen on/off events for the last 14 days
    private fun fetchScreenUsageEvents(): List<Pair<Long, String>> {
        // Get the UsageStatsManager service
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        // Calculate the time range (last 14 days)
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis  // Current time
        calendar.add(Calendar.DAY_OF_YEAR, -14)  // Go back 14 days
        val startTime = calendar.timeInMillis


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
            
            // Check if period starts between 7 PM and 6 AM and is longer than 1.5 hours
            if ((hour >= 19 || hour < 6) && period.duration >= 90) {
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
        
        // Helper function to check if two dates are in the same night (19:00-06:00)
        fun areSameNight(date1: Date, date2: Date): Boolean {
            val cal1 = Calendar.getInstance().apply { time = date1 }
            val cal2 = Calendar.getInstance().apply { time = date2 }
            
            // If first date is before midnight and second is after, adjust second date back one day
            if (cal1.get(Calendar.HOUR_OF_DAY) >= 19 && cal2.get(Calendar.HOUR_OF_DAY) < 6) {
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
                if (timeBetween > 30) {
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
                            
                            // If period is longer than 30 minutes, mark it as sleep
                            if (nextPeriod.duration > 30) {
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
        try {
            // Get the screen events
            val events = fetchScreenUsageEvents()
            
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
            // Display the results
            displaySleepPeriods(sleepPeriods)
            
        } catch (e: Exception) {
            screenEventsTextView.text = "Error analyzing sleep data: ${e.message}"
        }
    }

    // Display the calculated sleep periods
    private fun displaySleepPeriods(sleepPeriods: List<ScreenOffPeriod>) {
        if (sleepPeriods.isEmpty()) {
            screenEventsTextView.text = "No sleep periods detected in the last 14 days"
            return
        }

        // Create date formatters for different display needs
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        val headerDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        
        // Group sleep periods by date (using the adjusted date logic)
        val periodsByDate = sleepPeriods.groupBy { period ->
            // Create calendar for the start time
            val cal = Calendar.getInstance()
            cal.time = period.start
            
            // If sleep started after midnight but before 6 PM,
            // we want to group it with the previous day
            if (cal.get(Calendar.HOUR_OF_DAY) < 18) {
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

    // When we resume the activity (e.g., coming back from permission settings)
    override fun onResume() {
        super.onResume()
        // Check permission and update display if needed
        if (hasUsageStatsPermission()) {
            analyzeSleepData()
        }
    }
} 