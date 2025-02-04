package com.mezberg.sleepwave.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mezberg.sleepwave.R
import com.mezberg.sleepwave.data.SleepPeriodEntity
import com.mezberg.sleepwave.ui.theme.SleepWaveTheme
import com.mezberg.sleepwave.viewmodel.SleepPeriodDisplayData
import com.mezberg.sleepwave.viewmodel.SleepTrackingUiState
import java.text.SimpleDateFormat
import java.util.*


@Composable
fun SleepTrackingScreen(
    uiState: SleepTrackingUiState,
    onPermissionRequest: () -> Unit,
    onDeleteSleepPeriod: (SleepPeriodEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.sleep_tracking),
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(vertical = 24.dp)
            )

            if (!uiState.hasPermission) {
                Button(
                    onClick = onPermissionRequest,
                    modifier = Modifier.padding(vertical = 16.dp)
                ) {
                    Text(text = stringResource(R.string.grant_usage_access))
                }
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(16.dp)
                )
            }

            uiState.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }

            if (!uiState.isLoading && uiState.error == null) {
                SleepPeriodsList(
                    sleepPeriods = uiState.sleepPeriods,
                    onDeleteSleepPeriod = onDeleteSleepPeriod,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SleepPeriodsList(
    sleepPeriods: List<SleepPeriodDisplayData>,
    onDeleteSleepPeriod: (SleepPeriodEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (sleepPeriods.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.no_sleep_periods),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(sleepPeriods) { dayData ->
                DaySleepCard(
                    dayData = dayData,
                    onDeleteSleepPeriod = onDeleteSleepPeriod
                )
            }
        }
    }
}

@Composable
private fun DaySleepCard(
    dayData: SleepPeriodDisplayData,
    onDeleteSleepPeriod: (SleepPeriodEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var isEditMode by remember { mutableStateOf(false) }
    var sleepPeriodToDelete by remember { mutableStateOf<SleepPeriodEntity?>(null) }

    // Confirmation Dialog
    sleepPeriodToDelete?.let { period ->
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        AlertDialog(
            onDismissRequest = { sleepPeriodToDelete = null },
            title = { Text("Confirm Deletion") },
            text = { 
                Text(
                    "Do you want to delete sleep period on \n" +
                    "${dateFormat.format(period.start)} from ${timeFormat.format(period.start)} to ${timeFormat.format(period.end)}?"
                ) 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSleepPeriod(period)
                        sleepPeriodToDelete = null
                    }
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { sleepPeriodToDelete = null }) {
                    Text("No")
                }
            }
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dayData.date,
                    style = MaterialTheme.typography.titleMedium
                )
                
                IconButton(
                    onClick = { isEditMode = !isEditMode },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = if (isEditMode) "Exit edit mode" else "Enter edit mode",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            dayData.periods.forEach { period ->
                val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${dateFormat.format(period.start)} - ${dateFormat.format(period.end)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    
                    if (isEditMode) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { sleepPeriodToDelete = period },
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete sleep period",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total sleep: ${dayData.totalSleepHours}h ${dayData.totalSleepMinutes}m",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                
                if (isEditMode) {
                    IconButton(
                        onClick = { /* TODO: Implement add functionality */ },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add sleep period",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SleepTrackingScreenPreview() {
    SleepWaveTheme {
        SleepTrackingScreen(
            uiState = SleepTrackingUiState(
                sleepPeriods = listOf(
                    SleepPeriodDisplayData(
                        date = "Mar 15, 2024",
                        periods = emptyList(),
                        totalSleepHours = 7,
                        totalSleepMinutes = 30
                    )
                )
            ),
            onPermissionRequest = {},
            onDeleteSleepPeriod = {}
        )
    }
} 