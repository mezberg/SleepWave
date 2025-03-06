package com.mezberg.sleepwave.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Blue,
    secondary = Blue,
    tertiary = Blue,
    background = DarkBackground,
    surface = DarkBackground,
    onPrimary = White,
    onSecondary = White,
    onTertiary = White,
    onBackground = White,
    onSurface = White,
    error = Error,
    errorContainer = Error.copy(alpha = 0.12f),
    onError = White,
    primaryContainer = Blue.copy(alpha = 0.15f),
    secondaryContainer = Blue.copy(alpha = 0.15f),
    tertiaryContainer = Blue.copy(alpha = 0.15f),
    onPrimaryContainer = White,
    onSecondaryContainer = White,
    onTertiaryContainer = White,
    surfaceVariant = DarkBackground,
    onSurfaceVariant = White.copy(alpha = 0.7f),
    outline = White.copy(alpha = 0.2f)
)

private val LightColorScheme = lightColorScheme(
    primary = Blue,
    secondary = Blue,
    tertiary = Blue,
    background = White,
    surface = White,
    onPrimary = White,
    onSecondary = White,
    onTertiary = White,
    onBackground = Black,
    onSurface = Black,
    error = Error,
    errorContainer = Error.copy(alpha = 0.12f),
    onError = White,
    primaryContainer = Blue.copy(alpha = 0.15f),
    secondaryContainer = Blue.copy(alpha = 0.15f),
    tertiaryContainer = Blue.copy(alpha = 0.15f),
    onPrimaryContainer = Blue,
    onSecondaryContainer = Blue,
    onTertiaryContainer = Blue,
    surfaceVariant = White,
    onSurfaceVariant = Black.copy(alpha = 0.7f),
    outline = Black.copy(alpha = 0.2f)
)

@Composable
fun SleepWaveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
} 