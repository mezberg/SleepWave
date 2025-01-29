package com.mezberg.sleepwave.navigation

import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.mezberg.sleepwave.ui.screens.MainScreen
import com.mezberg.sleepwave.ui.screens.SleepTrackingScreen
import com.mezberg.sleepwave.utils.PermissionUtils
import com.mezberg.sleepwave.viewmodel.SleepTrackingViewModel
import android.util.Log


sealed class Screen(val route: String) {
    object Main : Screen("main")
    object SleepTracking : Screen("sleep_tracking")
}

@Composable
fun SleepWaveNavigation(
    navController: NavHostController,
    startDestination: String = Screen.Main.route
) {
    val sleepTrackingViewModel: SleepTrackingViewModel = viewModel()
    val sleepTrackingUiState by sleepTrackingViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Handle permission check on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val hasPermission = PermissionUtils.hasUsageStatsPermission(context)
                sleepTrackingViewModel.updatePermissionStatus(hasPermission)
                Log.d("SleepWaveNavigation", "Permission status updated: $hasPermission")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Main.route) {
            MainScreen(
                onTrackSleepClick = {
                    navController.navigate(Screen.SleepTracking.route)
                }
            )
        }

        composable(Screen.SleepTracking.route) {
            SleepTrackingScreen(
                uiState = sleepTrackingUiState,
                onPermissionRequest = {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            )
        }
    }
} 