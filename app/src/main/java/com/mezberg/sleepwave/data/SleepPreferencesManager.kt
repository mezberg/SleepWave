package com.mezberg.sleepwave.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sleep_preferences")

class SleepPreferencesManager(private val context: Context) {
    companion object {
        private val MAX_SLEEP_DEBT = doublePreferencesKey("max_sleep_debt")
        private const val DEFAULT_MAX_SLEEP_DEBT = 0.0
    }

    val maxSleepDebt: Flow<Double> = context.dataStore.data
        .map { preferences ->
            preferences[MAX_SLEEP_DEBT] ?: DEFAULT_MAX_SLEEP_DEBT
        }

    suspend fun updateMaxSleepDebt(newMaxDebt: Double) {
        context.dataStore.edit { preferences ->
            preferences[MAX_SLEEP_DEBT] = newMaxDebt
        }
    }
} 