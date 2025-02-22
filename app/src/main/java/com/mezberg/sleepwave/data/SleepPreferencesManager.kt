package com.mezberg.sleepwave.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SleepPreferencesManager(private val context: Context) {

    companion object {
        // Preference keys for storing values in DataStore
        private val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        private val MAX_SLEEP_DEBT = doublePreferencesKey("max_sleep_debt")
        private val NIGHT_START_HOUR = intPreferencesKey("night_start_hour")
        private val NIGHT_END_HOUR = intPreferencesKey("night_end_hour")
        private val NEEDED_SLEEP_HOURS = doublePreferencesKey("needed_sleep_hours")
        private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")

        // Default values used when preferences are not yet set
        const val DEFAULT_NIGHT_START_HOUR = 1 // 7 PM
        const val DEFAULT_NIGHT_END_HOUR = 10 // 6 AM
        const val DEFAULT_NEEDED_SLEEP_HOURS = 8.0 // 8 hours of sleep per night
    }

    // Flow to observe onboarding completion status
    val hasCompletedOnboarding: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[HAS_COMPLETED_ONBOARDING] ?: false
        }

    // Flow to observe maximum sleep debt value
    val maxSleepDebt: Flow<Double> = context.dataStore.data
        .catch { exception ->
            // Handle IO exceptions by emitting empty preferences
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[MAX_SLEEP_DEBT] ?: 0.0
        }

    // Flow to observe night start hour with fallback to default value
    val nightStartHour: Flow<Int> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[NIGHT_START_HOUR] ?: DEFAULT_NIGHT_START_HOUR
        }

    // Flow to observe night end hour with fallback to default value
    val nightEndHour: Flow<Int> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[NIGHT_END_HOUR] ?: DEFAULT_NIGHT_END_HOUR
        }

    // Flow to observe needed sleep hours with fallback to default value
    val neededSleepHours: Flow<Double> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[NEEDED_SLEEP_HOURS] ?: DEFAULT_NEEDED_SLEEP_HOURS
        }

    // Flow to observe notifications enabled status
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[NOTIFICATIONS_ENABLED] ?: true // Enabled by default
        }

    // Update functions to modify preferences
    suspend fun updateMaxSleepDebt(maxDebt: Double) {
        context.dataStore.edit { preferences ->
            preferences[MAX_SLEEP_DEBT] = maxDebt
        }
    }

    // Update night start hour (0-23)
    suspend fun updateNightStartHour(hour: Int) {
        context.dataStore.edit { preferences ->
            preferences[NIGHT_START_HOUR] = hour
        }
    }

    // Update night end hour (0-23)
    suspend fun updateNightEndHour(hour: Int) {
        context.dataStore.edit { preferences ->
            preferences[NIGHT_END_HOUR] = hour
        }
    }

    // Update needed sleep hours
    suspend fun updateNeededSleepHours(hours: Double) {
        context.dataStore.edit { preferences ->
            preferences[NEEDED_SLEEP_HOURS] = hours
        }
    }

    // Update onboarding completion status
    suspend fun completeOnboarding() {
        context.dataStore.edit { preferences ->
            preferences[HAS_COMPLETED_ONBOARDING] = true
        }
    }

    // Reset onboarding status
    suspend fun resetOnboarding() {
        context.dataStore.edit { preferences ->
            preferences[HAS_COMPLETED_ONBOARDING] = false
        }
    }

    // Update notifications enabled status
    suspend fun updateNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[NOTIFICATIONS_ENABLED] = enabled
        }
    }
} 