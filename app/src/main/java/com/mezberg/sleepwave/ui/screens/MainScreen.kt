package com.mezberg.sleepwave.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mezberg.sleepwave.R
import com.mezberg.sleepwave.ui.theme.SleepWaveTheme
import com.mezberg.sleepwave.viewmodel.MainScreenViewModel
import java.text.DecimalFormat
import kotlin.math.min

@Composable
fun SleepDebtCycle(
    sleepDebt: Float,
    maxSleepDebt: Float,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(300.dp)
            .padding(16.dp)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val maxRadius = min(canvasWidth, canvasHeight) / 1.2f
        val center = Offset(canvasWidth / 2, canvasHeight / 2)
        val strokeWidth = 20f
        val dotRadius = 5f

        if (-sleepDebt <= 0) {
            // Draw dot when sleep debt is negative or zero
            drawCircle(
                color = Color.Black,
                radius = dotRadius,
                center = center
            )
        } else {
            // Draw circle with radius proportional to sleep debt
            val radius = (-sleepDebt * maxRadius / 12f).coerceAtMost(maxRadius) // Scale factor of 12 hours
            
            drawCircle(
                color = Color.Black,
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth)
            )
        }
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Refresh data when screen becomes active
    LaunchedEffect(Unit) {
        viewModel.refreshSleepDebt()
    }

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
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 32.dp)
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

            uiState.sleepDebtInfo?.let { sleepDebtInfo ->
                Text(
                    text = "Sleep Debt: ${sleepDebtInfo.formattedSleepDebt} hours",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                SleepDebtCycle(
                    sleepDebt = sleepDebtInfo.sleepDebt.toFloat(),
                    maxSleepDebt = uiState.maxSleepDebt.toFloat(),
                    modifier = Modifier.padding(vertical = 24.dp)
                )

                Text(
                    text = "Calculated using:",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sleepDebtInfo.dailySleepData) { dayInfo ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = dayInfo.date,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${DecimalFormat("#.##").format(dayInfo.sleepAmount)} hours",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    SleepWaveTheme {
        MainScreen()
    }
} 