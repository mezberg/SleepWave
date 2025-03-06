package com.mezberg.sleepwave.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import com.mezberg.sleepwave.viewmodel.EnergyTimePoint
import com.mezberg.sleepwave.viewmodel.EnergyPointType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.foundation.shape.RoundedCornerShape
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.Paint
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import android.util.Log
import com.mezberg.sleepwave.ui.theme.*


@Composable
fun EnergyLevelsGraph(
    modifier: Modifier = Modifier,
    energyPoints: List<EnergyTimePoint>
) {
    // Add current time state to trigger recomposition
    var currentTimeState by remember { mutableStateOf(Calendar.getInstance().time) }
    
    // Update current time every second
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(10000)
            currentTimeState = Calendar.getInstance().time
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Energy Levels",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            if (energyPoints.isNotEmpty()) {
                val scrollState = rememberScrollState()
                val coroutineScope = rememberCoroutineScope()
                
                // Calculate initial scroll position based on current time
                LaunchedEffect(energyPoints, currentTimeState) {
                    val baseCalendar = Calendar.getInstance().apply {
                        time = energyPoints.first().time
                        add(Calendar.HOUR_OF_DAY, -6) // 6 hours before wake-up time
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    
                    val currentCal = Calendar.getInstance()
                    val baseHour = baseCalendar.get(Calendar.HOUR_OF_DAY)
                    val currentHour = currentCal.get(Calendar.HOUR_OF_DAY)
                    
                    val hoursSince4am = if (currentHour < baseHour) {
                        // If current time is before base time, calculate: 24h - base time + current time
                        24f - ((baseCalendar.timeInMillis - currentCal.timeInMillis) / (1000.0 * 60 * 60)).toFloat()
                    } else {
                        ((currentCal.timeInMillis - baseCalendar.timeInMillis) / (1000.0 * 60 * 60)).toFloat()
                    }
                    
                    val scrollPosition = (hoursSince4am / 24f * 800f).toInt() // Center the current time
                    Log.d("EnergyLevelsGraph", "scrollPosition: $scrollPosition")
                    coroutineScope.launch {
                        scrollState.scrollTo(maxOf(0, scrollPosition))
                    }
                }
                
                // Extract colors before Canvas
                val primaryColor = Blue // Use Blue instead of MaterialTheme.colorScheme.primary
                val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
                val currentTimeColor = Blue // Use Blue instead of Orange
                val density = LocalDensity.current

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(vertical = 16.dp)
                ) {
                    // Y-axis labels (outside scroll)
                    Canvas(
                        modifier = Modifier
                            .width(30.dp)
                            .height(200.dp)
                    ) {
                        val height = size.height
                        val padding = 50f
                        
                        // Draw Y-axis labels
                        val yLabels = listOf(
                            "100%" to 0f,
                            "80%" to 0.2f,
                            "60%" to 0.4f,
                            "40%" to 0.6f,
                            "20%" to 0.8f,
                            "0%" to 1f
                        )
                        
                        val paint = Paint().apply {
                            color = onSurfaceVariantColor.toArgb()
                            textSize = with(density) { 12.dp.toPx() }
                            textAlign = Paint.Align.RIGHT
                            isAntiAlias = true
                        }

                        yLabels.forEach { (label, yPercent) ->
                            val y = padding + (height - 2 * padding) * yPercent
                            drawContext.canvas.nativeCanvas.drawText(
                                label,
                                size.width,
                                y + 10f,
                                paint
                            )
                        }
                    }

                    // Main graph with scroll (positioned after Y-axis)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(start = 30.dp)
                            .horizontalScroll(scrollState)
                    ) {
                        Canvas(
                            modifier = Modifier
                                .width(600.dp)
                                .height(200.dp)
                        ) {
                            val width = size.width
                            val height = size.height
                            val padding = 50f
                            val graphWidth = width - 2 * padding
                            val graphHeight = height - 2 * padding

                            // Create base calendar at wake-up time - 4h
                            val baseCalendar = Calendar.getInstance().apply {
                                time = energyPoints.first().time
                                add(Calendar.HOUR_OF_DAY, -6) // 6 hours before wake-up time
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }

                            // Draw horizontal grid lines
                            val yPositions = listOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f)
                            yPositions.forEach { yPercent ->
                                val y = padding + (height - 2 * padding) * yPercent
                                drawLine(
                                    color = onSurfaceVariantColor.copy(alpha = 0.2f),
                                    start = Offset(padding, y),
                                    end = Offset(width - padding, y),
                                    strokeWidth = 1f
                                )
                            }

                            // Draw time labels and grid lines
                            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                            val paint = Paint().apply {
                                color = onSurfaceVariantColor.toArgb()
                                textSize = with(density) { 12.dp.toPx() }
                                textAlign = Paint.Align.CENTER
                                isAntiAlias = true
                            }

                            // Create time points every 2 hours
                            val timePoints = mutableListOf<Pair<Float, Date>>()
                            for (hourOffset in 0..24 step 2) { // Changed step to 2 hours
                                val cal = Calendar.getInstance().apply {
                                    time = baseCalendar.time
                                    add(Calendar.HOUR_OF_DAY, hourOffset)
                                }
                                val x = padding + (hourOffset / 24f) * graphWidth
                                timePoints.add(x to cal.time)
                            }

                            // Draw time labels and grid lines
                            timePoints.forEach { (x, time) ->
                                val timeLabel = timeFormat.format(time)
                                
                                // Draw vertical grid line
                                drawLine(
                                    color = onSurfaceVariantColor.copy(alpha = 0.2f),
                                    start = Offset(x, padding),
                                    end = Offset(x, height - padding),
                                    strokeWidth = 1f
                                )
                                
                                // Draw time label
                                drawContext.canvas.nativeCanvas.drawText(
                                    timeLabel,
                                    x,
                                    height + 10f,
                                    paint
                                )
                            }

                            // Draw current time line
                            val currentTime = Calendar.getInstance()
                            val baseHour = baseCalendar.get(Calendar.HOUR_OF_DAY)
                            val currentHour = currentTime.get(Calendar.HOUR_OF_DAY)
                            
                            val currentHoursSince4am = if (currentHour < baseHour) {
                                // If current time is before base time, calculate: 24h - base time + current time
                                24f - ((baseCalendar.timeInMillis - currentTime.timeInMillis) / (1000.0 * 60 * 60)).toFloat()
                            } else {
                                ((currentTime.timeInMillis - baseCalendar.timeInMillis) / (1000.0 * 60 * 60)).toFloat()
                            }
                            
                            val currentTimeX = padding + (currentHoursSince4am / 24f) * graphWidth

                            // Draw current time text
                            val currentTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                            val currentTimeText = currentTimeFormat.format(Calendar.getInstance().time) // Use actual current time for display
                            val currentTimePaint = Paint().apply {
                                color = currentTimeColor.toArgb()
                                textSize = with(density) { 12.dp.toPx() }
                                textAlign = Paint.Align.CENTER
                                isAntiAlias = true
                                isFakeBoldText = false
                            }
                            
                            // Draw time text above the line
                            drawContext.canvas.nativeCanvas.drawText(
                                currentTimeText,
                                currentTimeX,
                                padding - 20f, // Position above the line
                                currentTimePaint
                            )

                            // Draw dotted line for current time
                            val pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                floatArrayOf(10f, 10f), // 10px dash, 10px gap
                                0f
                            )
                            
                            drawLine(
                                color = currentTimeColor.copy(alpha = 0.8f),
                                start = Offset(currentTimeX, padding),
                                end = Offset(currentTimeX, height - padding),
                                strokeWidth = 4f,
                                pathEffect = pathEffect
                            )

                            // Map energy points to screen coordinates
                            val wakeUpTime = energyPoints.first().time
                            val points = energyPoints.map { point ->
                                val pointCal = Calendar.getInstance().apply { time = point.time }
                                val hoursSince4am = ((pointCal.timeInMillis - baseCalendar.timeInMillis) / (1000.0 * 60 * 60)).toFloat()
                                val x = padding + (hoursSince4am / 24f) * graphWidth
                                
                                val y = padding + when (point.type) {
                                    EnergyPointType.WAKE_UP -> graphHeight * 0.5f
                                    EnergyPointType.MORNING_PEAK -> graphHeight * 0.1f // 90% energy
                                    EnergyPointType.AFTERNOON_DIP -> graphHeight * 0.9f // 10% energy
                                    EnergyPointType.EVENING_PEAK -> graphHeight * 0.2f // 80% energy
                                }
                                
                                Offset(x, y)
                            }

                            // Add final point at wake-up time + 16h
                            val wakeUpCal = Calendar.getInstance().apply { 
                                time = wakeUpTime
                                add(Calendar.HOUR_OF_DAY, 16) // End at wake-up + 16h
                            }
                            val hoursSinceBase = ((wakeUpCal.timeInMillis - baseCalendar.timeInMillis) / (1000.0 * 60 * 60)).toFloat()
                            val finalX = padding + (hoursSinceBase / 24f) * graphWidth
                            val finalY = padding + graphHeight * 0.8f // 20% energy
                            val finalPoints = points + Offset(finalX, finalY)

                            // Create and draw the path
                            if (finalPoints.isNotEmpty()) {
                                val path = Path()
                                path.moveTo(finalPoints.first().x, finalPoints.first().y)
                                
                                for (i in 1 until finalPoints.size) {
                                    val current = finalPoints[i]
                                    val previous = finalPoints[i - 1]
                                    val controlX = (previous.x + current.x) / 2
                                    
                                    path.cubicTo(
                                        controlX, previous.y,
                                        controlX, current.y,
                                        current.x, current.y
                                    )
                                }

                                drawPath(
                                    path = path,
                                    color = primaryColor,
                                    style = Stroke(
                                        width = 10f,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No energy data available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
} 