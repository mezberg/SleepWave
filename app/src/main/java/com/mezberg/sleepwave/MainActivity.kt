package com.mezberg.sleepwave

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mezberg.sleepwave.ui.SleepWaveApp
import com.mezberg.sleepwave.viewmodel.MainScreenViewModel
import com.mezberg.sleepwave.viewmodel.SleepTrackingViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        // Shared flow to emit app foreground events
        val appForegroundFlow = MutableSharedFlow<Unit>()
    }

    // Initialize ViewModels at Activity level
    private val mainScreenViewModel: MainScreenViewModel by viewModels()
    private val sleepTrackingViewModel: SleepTrackingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Observe lifecycle to detect foreground state
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Emit event when app comes to foreground
                appForegroundFlow.emit(Unit)
            }
        }

        setContent {
            SleepWaveApp(
                mainScreenViewModel = mainScreenViewModel,
                sleepTrackingViewModel = sleepTrackingViewModel
            )
        }
    }
}