package com.mezberg.sleepwave.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mezberg.sleepwave.R
import com.mezberg.sleepwave.ui.theme.SleepWaveTheme
import com.mezberg.sleepwave.viewmodel.SleepPeriodDisplayData
import com.mezberg.sleepwave.viewmodel.SleepTrackingUiState
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SleepTrackingScreen(
    uiState: SleepTrackingUiState,
    onPermissionRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.sleep_tracking),
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 24.dp)
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
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SleepPeriodsList(
    sleepPeriods: List<SleepPeriodDisplayData>,
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(sleepPeriods) { dayData ->
                DaySleepCard(dayData = dayData)
            }
        }
    }
}

@Composable
private fun DaySleepCard(
    dayData: SleepPeriodDisplayData,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = dayData.date,
                style = MaterialTheme.typography.titleMedium
            )

            dayData.periods.forEach { period ->
                val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                Text(
                    text = "${dateFormat.format(period.start)} - ${dateFormat.format(period.end)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Text(
                text = "Total sleep: ${dayData.totalSleepHours}h ${dayData.totalSleepMinutes}m",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
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
            onPermissionRequest = {}
        )
    }
} 