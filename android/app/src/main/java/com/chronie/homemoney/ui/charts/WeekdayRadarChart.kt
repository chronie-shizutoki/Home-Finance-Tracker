package com.chronie.homemoney.ui.charts

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chronie.homemoney.R
import com.chronie.homemoney.ui.components.ExpressiveLinearProgressIndicator
import com.chronie.homemoney.ui.expense.ExpenseTypeLocalizer
import java.text.NumberFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI

/**
 * Weekday Radar Chart Card
 */
@Composable
fun WeekdayRadarChartCard(
    context: Context,
    weekdayData: List<WeekdayChartData>,
    currencyFormat: NumberFormat,
    startDate: String,
    endDate: String,
    onNavigateToWeekdayDetail: (dayOfWeek: Int, amount: Double, count: Int, percentage: Float, startDate: String, endDate: String) -> Unit = { _, _, _, _, _, _ -> }
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = context.getString(R.string.weekday_analysis),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (weekdayData.isEmpty() || weekdayData.all { it.amount == 0.0 }) {
                Text(
                    text = context.getString(R.string.no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp)
                )
            } else {
                // Weekday Radar Chart
                WeekdayRadarChart(
                    context = context,
                    weekdayData = weekdayData,
                    currencyFormat = currencyFormat,
                    onWeekdayClick = { weekday ->
                        onNavigateToWeekdayDetail(
                            weekday.dayOfWeek,
                            weekday.amount,
                            weekday.count,
                            weekday.percentage,
                            startDate,
                            endDate
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Weekday data list
                weekdayData.forEach { data ->
                    if (data.amount > 0) {
                        WeekdayDataItem(context, data, currencyFormat)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

/**
 * Weekday Radar Chart
 */
@Composable
private fun WeekdayRadarChart(
    context: Context,
    weekdayData: List<WeekdayChartData>,
    currencyFormat: NumberFormat,
    onWeekdayClick: (WeekdayChartData) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    
    // Store label positions for click detection
    val labelPositions = remember { mutableStateMapOf<Int, Pair<Offset, Float>>() }
    
    Canvas(modifier = modifier.pointerInput(Unit) {
        detectTapGestures { offset ->
            // Detect which weekday label was clicked
            labelPositions.forEach { (dayOfWeek, posAndRadius) ->
                val (labelPos, radius) = posAndRadius
                val distance = sqrt(
                    (offset.x - labelPos.x) * (offset.x - labelPos.x) +
                    (offset.y - labelPos.y) * (offset.y - labelPos.y)
                )
                if (distance <= radius) {
                    weekdayData.getOrNull(dayOfWeek)?.let { data ->
                        onWeekdayClick(data)
                    }
                }
            }
        }
    }) {
        val width = size.width
        val height = size.height
        val centerX = width / 2
        val centerY = height / 2
        val radius = minOf(width, height) / 2 - 120f
        
        // Get maximum amount for normalization
        val maxAmount = weekdayData.maxOfOrNull { it.amount } ?: 1.0
        if (maxAmount == 0.0) return@Canvas
        
        // Draw concentric circles (5 levels) and add amount labels
        val levels = 5
        for (i in 1..levels) {
            val levelRadius = radius * i / levels
            val levelAmount = maxAmount * i / levels
            
            // Draw circle
            drawCircle(
                color = gridColor,
                radius = levelRadius,
                center = Offset(centerX, centerY),
                style = Stroke(width = 1f)
            )
            
            // Add amount label on the right side
            val amountText = String.format("%.0f", levelAmount)
            val amountPaint = android.graphics.Paint().apply {
                textAlign = android.graphics.Paint.Align.LEFT
                textSize = 24f
                color = gridColor.copy(alpha = 0.8f).toArgb()
            }
            
            drawContext.canvas.nativeCanvas.drawText(
                amountText,
                centerX + levelRadius + 10f,
                centerY + 8f,
                amountPaint
            )
        }
        
        // 7 vertices (Sunday to Saturday)
        val vertices = 7
        val angleStep = 2 * PI / vertices
        
        // Start from top (Sunday), clockwise
        val startAngle = -PI / 2 // Start from above
        
        // Draw lines from center to each vertex
        for (i in 0 until vertices) {
            val angle = startAngle + angleStep * i
            val endX = centerX + radius * cos(angle).toFloat()
            val endY = centerY + radius * sin(angle).toFloat()
            
            drawLine(
                color = gridColor,
                start = Offset(centerX, centerY),
                end = Offset(endX, endY),
                strokeWidth = 1f
            )
        }
        
        // Draw data polygon
        val dataPath = Path()
        val points = mutableListOf<Offset>()
        
        for (i in 0 until vertices) {
            val data = weekdayData.getOrNull(i)
            val normalizedValue = if (data != null && maxAmount > 0) {
                (data.amount / maxAmount).toFloat()
            } else {
                0f
            }
            
            val angle = startAngle + angleStep * i
            val pointRadius = radius * normalizedValue
            val x = centerX + pointRadius * cos(angle).toFloat()
            val y = centerY + pointRadius * sin(angle).toFloat()
            
            points.add(Offset(x, y))
            
            if (i == 0) {
                dataPath.moveTo(x, y)
            } else {
                dataPath.lineTo(x, y)
            }
        }
        dataPath.close()
        
        // Fill data area
        drawPath(
            path = dataPath,
            color = primaryColor.copy(alpha = 0.3f)
        )
        
        // Draw data boundary line
        drawPath(
            path = dataPath,
            color = primaryColor,
            style = Stroke(width = 3f)
        )
        
        // Draw data points
        points.forEach { point ->
            drawCircle(
                color = primaryColor,
                radius = 6f,
                center = point
            )
            drawCircle(
                color = Color.White,
                radius = 3f,
                center = point
            )
        }
        
        // Draw weekday labels (clickable)
        val weekdayLabels = listOf(
            context.getString(R.string.sunday_short),
            context.getString(R.string.monday_short),
            context.getString(R.string.tuesday_short),
            context.getString(R.string.wednesday_short),
            context.getString(R.string.thursday_short),
            context.getString(R.string.friday_short),
            context.getString(R.string.saturday_short)
        )
        
        labelPositions.clear()
        
        for (i in 0 until vertices) {
            val angle = startAngle + angleStep * i
            val labelRadius = radius + 60f
            val x = centerX + labelRadius * cos(angle).toFloat()
            val y = centerY + labelRadius * sin(angle).toFloat()
            
            // Store label position for click detection
            labelPositions[i] = Pair(Offset(x, y), 40f)
            
            // Draw label background circle (indicate clickable)
            drawCircle(
                color = primaryColor.copy(alpha = 0.1f),
                radius = 35f,
                center = Offset(x, y)
            )
            
            // Adjust text alignment
            val textPaint = android.graphics.Paint().apply {
                textSize = 36f
                color = textColor.toArgb()
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
            }
            
            drawContext.canvas.nativeCanvas.drawText(
                weekdayLabels[i],
                x,
                y + 12f, // Center vertically
                textPaint
            )
        }
    }
}

/**
 * Weekday Data Item
 */
@Composable
private fun WeekdayDataItem(
    context: Context,
    data: WeekdayChartData,
    currencyFormat: NumberFormat
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = getWeekdayName(context, data.dayOfWeek),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        
        Text(
            text = "${String.format("%.1f", data.percentage)}%",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(60.dp)
        )
        
        Text(
            text = currencyFormat.format(data.amount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(100.dp)
        )
    }
}

/**
 * Category Detail Item
 */
@Composable
private fun CategoryDetailItem(
    context: Context,
    category: CategoryChartData,
    currencyFormat: NumberFormat
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                text = ExpenseTypeLocalizer.getLocalizedTypeName(context, category.type),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${String.format("%.1f", category.percentage)}%",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(60.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        ExpressiveLinearProgressIndicator(
            progress = category.percentage / 100f,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${category.count} ${context.getString(R.string.records)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = currencyFormat.format(category.amount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Get weekday name
 */
private fun getWeekdayName(context: Context, dayOfWeek: Int): String {
    return when (dayOfWeek) {
        0 -> context.getString(R.string.sunday)
        1 -> context.getString(R.string.monday)
        2 -> context.getString(R.string.tuesday)
        3 -> context.getString(R.string.wednesday)
        4 -> context.getString(R.string.thursday)
        5 -> context.getString(R.string.friday)
        6 -> context.getString(R.string.saturday)
        else -> ""
    }
}
