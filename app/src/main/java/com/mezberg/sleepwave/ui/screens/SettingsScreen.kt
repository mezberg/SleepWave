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
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import androidx.compose.ui.graphics.Color


@Composable
fun TimePickerButton(
    hour: Int,
    onHourChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:00") }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedButton(
            onClick = { showDialog = true }
        ) {
            Text(LocalTime.of(hour, 0).format(timeFormatter))
        }
    }

    if (showDialog) {
        TimePickerDialog(
            onDismissRequest = { showDialog = false },
            onConfirm = { 
                showDialog = false
                onHourChange(it)
            },
            initialHour = hour
        )
    }
}

@Composable
fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (Int) -> Unit,
    initialHour: Int
) {
    var selectedHour by remember { mutableStateOf(initialHour) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Select hour") },
        text = {
            Column {
                TimePicker(
                    selectedHour = selectedHour,
                    onHourSelected = { selectedHour = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(selectedHour)
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePicker(
    selectedHour: Int,
    onHourSelected: (Int) -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = false,
        onExpandedChange = {}
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Hour",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Display hours in 4 rows of 6 hours each
                for (rowStart in 0..22 step 4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (h in rowStart until rowStart + 4) {
                            val timeText = String.format("%02d:00", h)
                            Surface(
                                modifier = Modifier.padding(4.dp),
                                shape = MaterialTheme.shapes.small,
                                color = if (h == selectedHour) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else 
                                    MaterialTheme.colorScheme.surface,
                                onClick = { onHourSelected(h) }
                            ) {
                                Text(
                                    text = timeText,
                                    modifier = Modifier.padding(8.dp),
                                    color = if (h == selectedHour)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

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

            // Night Hours Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Night Hours",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Button(
                        onClick = { viewModel.applyNightHours() },
                        enabled = uiState.tempNightStartHour != uiState.nightStartHour || 
                                uiState.tempNightEndHour != uiState.nightEndHour
                    ) {
                        Text("Apply")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TimePickerButton(
                        hour = uiState.tempNightStartHour,
                        onHourChange = viewModel::updateTempNightStartHour,
                        label = "Bedtime"
                    )

                    TimePickerButton(
                        hour = uiState.tempNightEndHour,
                        onHourChange = viewModel::updateTempNightEndHour,
                        label = "Wake up time"
                    )
                }
            }

            // Reset Onboarding Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Debug Options",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = { viewModel.resetOnboarding() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Black, // Set button background color to black
                        contentColor = Color.White // Set button text color to white
                    )
                ) {
                    Text("Reset Onboarding")
                }
            }
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