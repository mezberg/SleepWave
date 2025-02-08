package com.mezberg.sleepwave.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
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

data class DailySleepData(
    val date: Date,
    val totalSleepHours: Float
)

@Composable
fun WeeklySleepGraph(
    sleepData: List<DailySleepData>,
    modifier: Modifier = Modifier
) {
    val maxHours = 12f // Maximum hours to show on Y-axis
    val daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val density = LocalDensity.current
    
    // Remember colors to avoid recomposition issues
    val colors = remember {
        object {
            val primary = Color.Black
            val outlineVariant = Color.LightGray
            val onBackground = Color.Black
        }
    }
    
    // Remember text paint to avoid recreation
    val textPaint = remember {
        Paint().asFrameworkPaint().apply {
            isAntiAlias = true
            textSize = with(density) { 12.sp.toPx() }
            color = colors.onBackground.toArgb()
            textAlign = android.graphics.Paint.Align.RIGHT
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Weekly Sleep Duration",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
        ) {
            // Hour labels and grid lines
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(start = 48.dp, end = 16.dp, bottom = 8.dp)
            ) {
                // Draw horizontal grid lines and hour labels
                for (hour in 0..12 step 2) {
                    val y = size.height * (1 - hour / maxHours)
                    // Draw grid line
                    drawLine(
                        color = colors.outlineVariant,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    
                    // Draw hour label
                    drawContext.canvas.nativeCanvas.drawText(
                        "${hour}h",
                        -8.dp.toPx(),
                        y + 4.dp.toPx(),
                        textPaint
                    )
                }

                // Calculate bar dimensions
                val barWidth = size.width / daysOfWeek.size // Width for each day's bar
                val effectiveBarWidth = barWidth * 0.5f // Make bars wider
                val cornerRadius = 8.dp.toPx()

                // Draw bars for each day
                sleepData.forEachIndexed { index, data ->
                    val x = index * barWidth + (barWidth / 2) - (effectiveBarWidth / 2)
                    val barHeight = (minOf(data.totalSleepHours, maxHours) / maxHours) * size.height
                    
                    drawRoundedBar(
                        color = colors.primary,
                        topLeft = Offset(x, size.height - barHeight),
                        size = Size(effectiveBarWidth, barHeight),
                        cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                    )
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
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawRoundedBar(
    color: Color,
    topLeft: Offset,
    size: Size,
    cornerRadius: CornerRadius
) {
    // Only round the top corners
    drawRoundRect(
        color = color,
        topLeft = topLeft,
        size = size,
        cornerRadius = cornerRadius
    )
}