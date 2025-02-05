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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mezberg.sleepwave.R
import com.mezberg.sleepwave.ui.theme.SleepWaveTheme
import com.mezberg.sleepwave.viewmodel.MainScreenViewModel
import java.text.DecimalFormat
import kotlin.math.min
import kotlin.math.cos
import kotlin.math.sin
import java.lang.Math


@Composable
fun SleepDebtCycle(
    sleepDebt: Float,
    maxSleepDebt: Float,
    healthyDebtHours: Float = 1f,
    dangerousDebtHours: Float = 10f,
    modifier: Modifier = Modifier
) {
    val maxVisualizationDebt = 13f // Maximum visualization threshold for sleep debt
    
    Canvas(
        modifier = modifier
            .size(240.dp)
            .padding(16.dp)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val radius = min(canvasWidth, canvasHeight) / 2
        val center = Offset(canvasWidth / 2, canvasHeight / 2)
        val strokeWidth = 20f

        // Draw base circle (green)
        drawCircle(
            color = Color(0xFF4CAF50),
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth)
        )

        // Calculate sleep debt fill
        val negativeSleepDebt = -sleepDebt
        
        if (negativeSleepDebt > 0) {
            val fillRatio = (negativeSleepDebt / maxVisualizationDebt).coerceAtMost(1f)
            val sweepAngle = fillRatio * 360f

            drawArc(
                color = Color(0xFFE57373),
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth)
            )
        }

        // Draw marks
        val markLength = 10f
        val textRadius = radius + strokeWidth / 2 + markLength + 20f

        // Healthy debt mark
        val healthyAngle = -90f + (healthyDebtHours / maxVisualizationDebt * 360f)
        val healthyRadians = Math.toRadians(healthyAngle.toDouble())
        val healthyMarkStart = Offset(
            center.x + (radius - strokeWidth/2) * cos(healthyRadians).toFloat(),
            center.y + (radius - strokeWidth/2) * sin(healthyRadians).toFloat()
        )
        val healthyMarkEnd = Offset(
            center.x + (radius + strokeWidth/2 + markLength) * cos(healthyRadians).toFloat(),
            center.y + (radius + strokeWidth/2 + markLength) * sin(healthyRadians).toFloat()
        )
        drawLine(
            color = Color.Black,
            start = healthyMarkStart,
            end = healthyMarkEnd,
            strokeWidth = 2f
        )

        // Dangerous mark
        val dangerousAngle = -90f + (dangerousDebtHours / maxVisualizationDebt * 360f)
        val dangerousRadians = Math.toRadians(dangerousAngle.toDouble())
        val dangerousMarkStart = Offset(
            center.x + (radius - strokeWidth/2) * cos(dangerousRadians).toFloat(),
            center.y + (radius - strokeWidth/2) * sin(dangerousRadians).toFloat()
        )
        val dangerousMarkEnd = Offset(
            center.x + (radius + strokeWidth/2 + markLength) * cos(dangerousRadians).toFloat(),
            center.y + (radius + strokeWidth/2 + markLength) * sin(dangerousRadians).toFloat()
        )
        drawLine(
            color = Color.Black,
            start = dangerousMarkStart,
            end = dangerousMarkEnd,
            strokeWidth = 2f
        )

        // Draw text labels
        drawIntoCanvas { canvas ->
            val textPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 30f
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
            }

            // Healthy debt label
            val healthyTextX = center.x + textRadius * cos(healthyRadians).toFloat() + 50f
            val healthyTextY = center.y + textRadius * sin(healthyRadians).toFloat() - 20f
            canvas.nativeCanvas.drawText("${healthyDebtHours.toInt()}h healthy sleep debt (?)", healthyTextX, healthyTextY, textPaint)

            // Dangerous debt label
            val dangerousTextX = center.x + textRadius * cos(dangerousRadians).toFloat()
            val dangerousTextY = center.y + textRadius * sin(dangerousRadians).toFloat() - 20f
            canvas.nativeCanvas.drawText("${dangerousDebtHours.toInt()}h dangerous for health (?)", dangerousTextX, dangerousTextY, textPaint)
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
                    healthyDebtHours = MainScreenViewModel.HEALTHY_DEBT_HOURS.toFloat(),
                    dangerousDebtHours = MainScreenViewModel.DANGEROUS_DEBT_HOURS.toFloat(),
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