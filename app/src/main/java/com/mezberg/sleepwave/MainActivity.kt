package com.mezberg.sleepwave

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.mezberg.sleepwave.ui.SleepWaveApp
import com.mezberg.sleepwave.viewmodel.MainScreenViewModel
import com.mezberg.sleepwave.viewmodel.SleepTrackingViewModel
import com.mezberg.sleepwave.viewmodel.OnboardViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import com.mezberg.sleepwave.data.SleepPreferencesManager
import com.mezberg.sleepwave.data.SleepDatabase
import androidx.lifecycle.viewmodel.compose.viewModel


class MainActivity : ComponentActivity() {
    companion object {
        // Shared flow to emit app foreground events
        val appForegroundFlow = MutableSharedFlow<Unit>()
    }

    private lateinit var sleepPreferencesManager: SleepPreferencesManager
    private lateinit var sleepDatabase: SleepDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sleepPreferencesManager = SleepPreferencesManager(applicationContext)
        sleepDatabase = Room.databaseBuilder(
            applicationContext,
            SleepDatabase::class.java,
            "sleep_database"
        ).build()

        // Observe lifecycle to detect foreground state
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Emit event when app comes to foreground
                appForegroundFlow.emit(Unit)
            }
        }

        setContent {
            val mainScreenViewModel = viewModel<MainScreenViewModel>()
            val sleepTrackingViewModel = viewModel<SleepTrackingViewModel>()
            val onboardViewModel = viewModel<OnboardViewModel>(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return OnboardViewModel(sleepPreferencesManager) as T
                    }
                }
            )

            SleepWaveApp(
                mainScreenViewModel = mainScreenViewModel,
                sleepTrackingViewModel = sleepTrackingViewModel,
                onboardViewModel = onboardViewModel
            )
        }
    }
}