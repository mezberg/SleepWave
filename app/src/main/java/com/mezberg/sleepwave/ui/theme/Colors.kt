package com.mezberg.sleepwave.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Material Theme Colors
 */
object MaterialColors {
    // Primary brand colors
    val Blue = Color(0xFF2196F3)
    val Teal = Color(0xFF03DAC6)
    val DeepPurple = Color(0xFF3700B3)
    val Orange = Color(0xFFFF9800)  // Adding Material Orange

    // Neutral colors
    val White = Color(0xFFFFFFFF)
    val Black = Color(0xFF000000)
    val DarkBackground = Color(0xFF121212)

    // Status colors
    val Success = Color(0xFF4CAF50)  // Material Green
    val Error = Color(0xFFE57373)    // Material Red Light
}

/**
 * Graph Colors
 */
object GraphColors {
    val Primary = Color(0xFF000000)
    val OutlineVariant = Color(0xFFD3D3D3)
}

/**
 * Sleep Tracking Colors
 */
object SleepColors {
    val DebtGreen = MaterialColors.Success
    val DebtRed = MaterialColors.Error
}

// Expose commonly used colors at the top level for convenience
val Blue = MaterialColors.Blue
val Teal = MaterialColors.Teal
val DeepPurple = MaterialColors.DeepPurple
val White = MaterialColors.White
val Black = MaterialColors.Black
val DarkBackground = MaterialColors.DarkBackground
val Error = MaterialColors.Error
val Orange = MaterialColors.Orange  // Exposing Orange

// Graph-specific colors
val GraphPrimary = GraphColors.Primary
val GraphOutlineVariant = GraphColors.OutlineVariant

// Sleep-specific colors
val SleepDebtGreen = SleepColors.DebtGreen
val SleepDebtRed = SleepColors.DebtRed 