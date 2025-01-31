package com.mezberg.sleepwave.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Science
import androidx.compose.ui.graphics.vector.ImageVector
import com.mezberg.sleepwave.R

sealed class BottomNavItem(
    val route: String,
    val titleResId: Int,
    val icon: ImageVector
) {
    object Home : BottomNavItem(
        route = "home",
        titleResId = R.string.home,
        icon = Icons.Default.Home
    )
    
    object Analysis : BottomNavItem(
        route = "analysis",
        titleResId = R.string.analysis,
        icon = Icons.Default.Science
    )
    
    object Settings : BottomNavItem(
        route = "settings",
        titleResId = R.string.settings,
        icon = Icons.Default.Settings
    )

    companion object {
        val items = listOf(Home, Analysis, Settings)
    }
} 