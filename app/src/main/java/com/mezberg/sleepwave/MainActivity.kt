package com.mezberg.sleepwave

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.mezberg.sleepwave.navigation.SleepWaveNavigation
import com.mezberg.sleepwave.ui.theme.SleepWaveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SleepWaveTheme {
                val navController = rememberNavController()
                SleepWaveNavigation(navController = navController)
            }
        }
    }
}