package com.mezberg.sleepwave.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mezberg.sleepwave.R
import com.mezberg.sleepwave.viewmodel.SleepTrackingUiState
import com.mezberg.sleepwave.ui.components.WeeklySleepGraph
import com.mezberg.sleepwave.ui.components.WeekNavigationHeader
import com.mezberg.sleepwave.ui.components.BedtimeConsistencyGraph

@Composable
fun SleepGraphsScreen(
    uiState: SleepTrackingUiState,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
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
                text = stringResource(R.string.sleep_graphs),
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(vertical = 24.dp)
            )

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
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    WeekNavigationHeader(
                        startDate = uiState.weekStartDate,
                        endDate = uiState.weekEndDate,
                        onPreviousWeek = onPreviousWeek,
                        onNextWeek = onNextWeek
                    )
                    
                    WeeklySleepGraph(
                        sleepData = uiState.weeklySleepData,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    )

                    BedtimeConsistencyGraph(
                        sleepPeriods = uiState.weeklyBedtimeData,
                        nightStartHour = uiState.nightStartHour,
                        nightEndHour = uiState.nightEndHour,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    )
                }
            }
        }
    }
} 