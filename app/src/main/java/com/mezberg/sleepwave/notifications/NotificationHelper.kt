package com.mezberg.sleepwave.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mezberg.sleepwave.R
import java.util.Calendar
import java.util.Date

class NotificationHelper(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        const val CHANNEL_ID = "energy_schedule_channel"
        const val NOTIFICATION_ID = 1
        const val REQUEST_CODE_BASE = 1000
        const val DAYS_TO_SCHEDULE = 7
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.notification_channel_name)
            val descriptionText = context.getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun scheduleEnergyNotifications(wakeUpTime: Date, neededSleepHours: Double) {
        // Cancel existing notifications first
        cancelAllScheduledNotifications()

        val calendar = Calendar.getInstance()
        calendar.time = wakeUpTime

        // Add 1 hour to wake up time for notification
        calendar.add(Calendar.HOUR_OF_DAY, 1)

        // Schedule notifications for the next 7 days
        repeat(DAYS_TO_SCHEDULE) { dayOffset ->
            if (dayOffset > 0) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            val notificationTime = calendar.timeInMillis

            // Only schedule if the time is in the future
            if (notificationTime > System.currentTimeMillis()) {
                scheduleNotification(notificationTime, dayOffset + REQUEST_CODE_BASE)
            }
        }
    }

    private fun scheduleNotification(timeMillis: Long, requestCode: Int) {
        val intent = Intent(context, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(timeMillis, pendingIntent),
            pendingIntent
        )
    }

    fun cancelAllScheduledNotifications() {
        // Cancel all pending notifications
        repeat(DAYS_TO_SCHEDULE) { dayOffset ->
            val intent = Intent(context, NotificationReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                dayOffset + REQUEST_CODE_BASE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }

    fun dismissTodayNotification(wakeUpTime: Date, neededSleepHours: Double) {
        val calendar = Calendar.getInstance()
        calendar.time = wakeUpTime

        // Calculate the time window for dismissal
        val dismissalWindow = (neededSleepHours / 5) * 60 * 60 * 1000 // Convert to milliseconds
        val currentTime = System.currentTimeMillis()
        val wakeUpTimeMillis = wakeUpTime.time

        // If current time is within the dismissal window after wake up time
        if (currentTime >= wakeUpTimeMillis && 
            currentTime <= wakeUpTimeMillis + dismissalWindow) {
            // Cancel today's notification
            val intent = Intent(context, NotificationReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_BASE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
} 