package com.mezberg.sleepwave.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mezberg.sleepwave.navigation.BottomNavItem
import com.mezberg.sleepwave.ui.screens.MainScreen
import com.mezberg.sleepwave.ui.screens.OnboardingScreen
import com.mezberg.sleepwave.ui.screens.SettingsScreen
import com.mezberg.sleepwave.ui.screens.SleepTrackingScreen
import com.mezberg.sleepwave.ui.theme.SleepWaveTheme
import com.mezberg.sleepwave.viewmodel.SleepTrackingViewModel
import com.mezberg.sleepwave.viewmodel.MainScreenViewModel
import com.mezberg.sleepwave.viewmodel.OnboardViewModel
import kotlinx.coroutines.launch

@Composable
fun SleepWaveApp(
    mainScreenViewModel: MainScreenViewModel,
    sleepTrackingViewModel: SleepTrackingViewModel,
    onboardViewModel: OnboardViewModel
) {
    SleepWaveTheme {
        val navController = rememberNavController()
        val context = LocalContext.current
        val hasCompletedOnboarding by onboardViewModel.uiState.collectAsState()

        if (!hasCompletedOnboarding.hasCompletedOnboarding) {
            OnboardingScreen(
                viewModel = onboardViewModel,
                onOnboardingComplete = {
                    // Navigate to main content
                    navController.navigate(BottomNavItem.Home.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        } else {
            Scaffold(
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    ) {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStackEntry?.destination?.route

                        BottomNavItem.items.forEach { item ->
                            NavigationBarItem(
                                icon = { Icon(item.icon, contentDescription = stringResource(item.titleResId)) },
                                label = { Text(stringResource(item.titleResId)) },
                                selected = currentRoute == item.route,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }
            ) { paddingValues ->
                NavHost(
                    navController = navController,
                    startDestination = BottomNavItem.Home.route,
                    modifier = Modifier.padding(paddingValues)
                ) {
                    composable(BottomNavItem.Home.route) {
                        MainScreen()
                    }
                    composable(BottomNavItem.Analysis.route) {
                        val uiState by sleepTrackingViewModel.uiState.collectAsState()
                        SleepTrackingScreen(
                            uiState = uiState,
                            onPermissionRequest = {
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            },
                            onDeleteSleepPeriod = { sleepPeriod ->
                                sleepTrackingViewModel.deleteSleepPeriod(sleepPeriod)
                            },
                            onAddSleepPeriod = { startDate, startTime, endDate, endTime ->
                                sleepTrackingViewModel.addSleepPeriod(startDate, startTime, endDate, endTime)
                            },
                            onPreviousWeek = { sleepTrackingViewModel.navigateToPreviousWeek() },
                            onNextWeek = { sleepTrackingViewModel.navigateToNextWeek() }
                        )
                    }
                    composable(BottomNavItem.Settings.route) {
                        SettingsScreen()
                    }
                }
            }
        }
    }
} 