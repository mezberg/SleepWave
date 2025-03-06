package com.mezberg.sleepwave.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*
import com.mezberg.sleepwave.ui.theme.Blue
import com.mezberg.sleepwave.ui.theme.GraphOutlineVariant

data class SleepPeriodData(
    val date: Date,
    val startTime: Date,
    val endTime: Date
)

@Composable
fun BedtimeConsistencyGraph(
    sleepPeriods: List<SleepPeriodData>,
    nightStartHour: Int,
    nightEndHour: Int,
    modifier: Modifier = Modifier
) {
    val daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val density = LocalDensity.current
    val onBackground = MaterialTheme.colorScheme.onBackground
    
    // Remember colors to avoid recomposition issues
    val colors = remember {
        object {
            val primary = Blue
            val outlineVariant = GraphOutlineVariant
        }
    }
    
    // Remember text paint to avoid recreation
    val textPaint = remember {
        Paint().asFrameworkPaint().apply {
            isAntiAlias = true
            textSize = with(density) { 12.sp.toPx() }
            color = onBackground.toArgb()
            textAlign = android.graphics.Paint.Align.RIGHT
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
                text = "Bedtime Consistency",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(start = 48.dp, end = 16.dp, bottom = 8.dp)
                ) {
                    val hoursInDay = 24
                    
                    // Calculate the center of the night period
                    val nightCenter = if (nightStartHour > nightEndHour) {
                        // Night period crosses midnight
                        (nightStartHour + (24 + nightEndHour)) / 2f
                    } else {
                        // Night period within same day
                        (nightStartHour + nightEndHour) / 2f
                    }
                    
                    // Calculate visible range (18 hours centered around night period)
                    val visibleHours = 18
                    val halfVisible = visibleHours / 2f
                    
                    // Calculate first hour to display (centered around night period)
                    val firstHour = ((nightCenter - halfVisible + 24) % 24).toInt()
                    val lastHour = (firstHour + visibleHours) % 24

                    // Draw horizontal grid lines and hour labels
                    for (hour in 0..visibleHours step 3) {
                        val displayHour = (firstHour + hour) % 24
                        // Invert Y position calculation
                        val y = size.height * (1 - hour.toFloat() / visibleHours)
                        
                        // Draw grid line
                        drawLine(
                            color = colors.outlineVariant,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                        
                        // Draw hour label
                        val hourText = String.format("%02d:00", displayHour)
                        drawContext.canvas.nativeCanvas.drawText(
                            hourText,
                            -8.dp.toPx(),
                            y + 4.dp.toPx(),
                            textPaint
                        )
                    }

                    // Calculate bar dimensions
                    val barWidth = size.width / daysOfWeek.size // Width for each day's bar
                    val effectiveBarWidth = barWidth * 0.5f // Make bars slightly wider
                    val cornerRadius = 8.dp.toPx()

                    // Group sleep periods by day
                    val periodsByDay = sleepPeriods.groupBy { period ->
                        val calendar = Calendar.getInstance()
                        calendar.time = period.date
                        calendar.get(Calendar.DAY_OF_WEEK)
                    }

                    // Draw sleep period bars for each day
                    periodsByDay.forEach { (dayOfWeek, periods) ->
                        val dayIndex = (dayOfWeek + 5) % 7 // Convert Calendar.DAY_OF_WEEK to 0-based index starting from Monday
                        val x = dayIndex * barWidth + (barWidth / 2) - (effectiveBarWidth / 2)

                        periods.forEach { period ->
                            val calendar = Calendar.getInstance()
                            
                            // Calculate start position
                            calendar.time = period.startTime
                            val startHourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
                            val startMinute = calendar.get(Calendar.MINUTE)
                            val startY = calculateYPosition(startHourOfDay, startMinute, firstHour, visibleHours, size.height)

                            // Calculate end position
                            calendar.time = period.endTime
                            val endHourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
                            val endMinute = calendar.get(Calendar.MINUTE)
                            val endY = calculateYPosition(endHourOfDay, endMinute, firstHour, visibleHours, size.height)

                            // Calculate if sleep period is outside visible window
                            val timeInHoursStart = startHourOfDay + startMinute / 60f
                            val timeInHoursEnd = endHourOfDay + endMinute / 60f
                            val normalizedStartHour = if (timeInHoursStart < firstHour) timeInHoursStart + 24 else timeInHoursStart
                            val normalizedEndHour = if (timeInHoursEnd < firstHour) timeInHoursEnd + 24 else timeInHoursEnd
                            val relativeStartHour = normalizedStartHour - firstHour
                            val relativeEndHour = normalizedEndHour - firstHour

                            if (relativeStartHour >= 0 && relativeStartHour <= visibleHours &&
                                relativeEndHour >= 0 && relativeEndHour <= visibleHours) {
                                // Draw normal sleep period bar if within visible window
                                drawRoundedBar(
                                    color = colors.primary,
                                    topLeft = Offset(x, startY),
                                    size = Size(effectiveBarWidth, endY - startY),
                                    cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                                )
                            } else if (relativeStartHour > visibleHours || relativeEndHour > visibleHours) {
                                // Draw up arrow at the top of the graph if sleep period is after visible window
                                drawUpArrow(x + effectiveBarWidth / 2, 8.dp.toPx(), colors.primary)
                            }
                        }
                    }
                }

                // Day labels at the bottom
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 48.dp, end = 16.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    daysOfWeek.forEach { day ->
                        Text(
                            text = day,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

private fun calculateYPosition(
    hour: Int,
    minute: Int,
    firstHour: Int,
    visibleHours: Int,
    height: Float
): Float {
    val timeInHours = hour + minute / 60f
    val normalizedHour = if (timeInHours < firstHour) timeInHours + 24 else timeInHours
    val relativeHour = normalizedHour - firstHour
    // Invert Y position calculation
    return height * (1 - relativeHour / visibleHours)
}

private fun DrawScope.drawUpArrow(x: Float, y: Float, color: Color) {
    val arrowWidth = 12.dp.toPx()
    val arrowHeight = 8.dp.toPx()
    
    drawPath(
        path = androidx.compose.ui.graphics.Path().apply {
            moveTo(x, y)  // Top point
            lineTo(x - arrowWidth / 2, y + arrowHeight)  // Bottom left
            lineTo(x + arrowWidth / 2, y + arrowHeight)  // Bottom right
            close()
        },
        color = color
    )
}

private fun DrawScope.drawRoundedBar(
    color: Color,
    topLeft: Offset,
    size: Size,
    cornerRadius: CornerRadius
) {
    drawRoundRect(
        color = color,
        topLeft = topLeft,
        size = size,
        cornerRadius = cornerRadius
    )
} 