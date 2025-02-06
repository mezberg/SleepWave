package com.mezberg.sleepwave.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mezberg.sleepwave.R
import com.mezberg.sleepwave.ui.theme.SleepWaveTheme
import com.mezberg.sleepwave.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Cleanup when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            viewModel.revertChanges()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Needed Sleep Hours Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val hours = uiState.tempNeededSleepHours.toInt()
                    val minutes = ((uiState.tempNeededSleepHours - hours) * 60).toInt()
                    Text(
                        text = "Needed Sleep: ${hours}h ${minutes}min",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Button(
                        onClick = { viewModel.applyNeededSleepHours() },
                        enabled = uiState.tempNeededSleepHours != uiState.neededSleepHours
                    ) {
                        Text("Apply")
                    }
                }

                Slider(
                    value = uiState.tempNeededSleepHours.toFloat(),
                    onValueChange = { 
                        // Round to nearest 0.5 hour
                        val roundedHours = (it * 2).roundToInt() / 2.0
                        viewModel.updateTempNeededSleepHours(roundedHours)
                    },
                    valueRange = 4f..12f,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "4h",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "12h",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Night Hours Section (to be implemented)
            // ...
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    SleepWaveTheme {
        SettingsScreen()
    }
} 