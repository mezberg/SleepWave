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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.mezberg.sleepwave.ui.theme.SleepDebtGreen
import com.mezberg.sleepwave.ui.theme.SleepDebtRed
import com.mezberg.sleepwave.ui.components.EnergyLevelsGraph

@Composable
fun SleepDebtCycle(
    sleepDebt: Float,
    maxSleepDebt: Float,
    modifier: Modifier = Modifier
) {
    val maxVisualizationDebt = 13f // Maximum visualization threshold for sleep debt
    
    Canvas(
        modifier = modifier
            .size(160.dp) // Slightly smaller to fit in the layout
            .padding(8.dp)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val radius = min(canvasWidth, canvasHeight) / 2.5f
        val center = Offset(canvasWidth / 2, canvasHeight / 2)
        val strokeWidth = 55f // Made thicker

        // Draw base circle (green)
        drawCircle(
            color = SleepDebtGreen,
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Calculate sleep debt fill (remember sleep debt is negative in our data)
        val negativeSleepDebt = -sleepDebt // Convert to positive for easier comparison
        
        if (negativeSleepDebt > 0) {
            val fillRatio = (negativeSleepDebt / maxVisualizationDebt).coerceAtMost(1f)
            val sweepAngle = fillRatio * 360f

            drawArc(
                color = SleepDebtRed,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun SleepDebtDisplay(
    sleepDebtHours: Int,
    sleepDebtMinutes: Int,
    sleepDebt: Float,
    maxSleepDebt: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Sleep Debt",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "${sleepDebtHours}h ${sleepDebtMinutes}m",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    modifier = Modifier
                        .align(Alignment.Start),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "Goal: 0h",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            SleepDebtCycle(
                sleepDebt = sleepDebt,
                maxSleepDebt = maxSleepDebt,
                modifier = Modifier.fillMaxHeight()
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
        viewModel.refreshEnergyLevels()
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

            // Add Energy Levels Graph here
            EnergyLevelsGraph(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                energyPoints = uiState.energyLevelsInfo?.energyPoints ?: emptyList()
            )

            uiState.sleepDebtInfo?.let { sleepDebtInfo ->
                val sleepDebtHours = sleepDebtInfo.sleepDebt.toInt()
                val sleepDebtMinutes = ((sleepDebtInfo.sleepDebt - sleepDebtHours) * 60).toInt()
                
                SleepDebtDisplay(
                    sleepDebtHours = kotlin.math.abs(sleepDebtHours),
                    sleepDebtMinutes = kotlin.math.abs(sleepDebtMinutes),
                    sleepDebt = sleepDebtInfo.sleepDebt.toFloat(),
                    maxSleepDebt = uiState.maxSleepDebt.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
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