package com.chronie.homemoney.ui.charts

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.himanshoe.charty.radar.RadarChart
import com.himanshoe.charty.radar.data.RadarDataSet
import com.himanshoe.charty.radar.data.RadarAxisData
import com.himanshoe.charty.radar.config.RadarChartConfig
import com.himanshoe.charty.radar.config.RadarGridConfig
import com.himanshoe.charty.radar.config.RadarLabelConfig
import com.himanshoe.charty.radar.config.RadarGridStyle
import com.himanshoe.charty.radar.config.RadarCenterConfig
import com.himanshoe.charty.color.ChartyColor
import com.chronie.homemoney.R
import com.chronie.homemoney.ui.components.ExpressiveLinearProgressIndicator
import com.chronie.homemoney.ui.expense.ExpenseTypeLocalizer
import java.text.NumberFormat
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (weekdayData.isEmpty() || weekdayData.all { it.amount == 0.0 }) {
                Text(
                    text = context.getString(R.string.no_data),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(vertical = 32.dp)
                )
            } else {
                // Weekday Radar Chart
                WeekdayRadarChart(
                    context = context,
                    weekdayData = weekdayData,
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
                        .height(350.dp)
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
    onWeekdayClick: (WeekdayChartData) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MiuixTheme.colorScheme.primary
    val textColor = MiuixTheme.colorScheme.onSurface
    val gridColor = MiuixTheme.colorScheme.dividerLine
    
    val weekdayLabels = listOf(
        context.getString(R.string.sunday_short),
        context.getString(R.string.monday_short),
        context.getString(R.string.tuesday_short),
        context.getString(R.string.wednesday_short),
        context.getString(R.string.thursday_short),
        context.getString(R.string.friday_short),
        context.getString(R.string.saturday_short)
    )
    
    val maxAmount = weekdayData.maxOfOrNull { it.amount } ?: 1.0
    
    val radarAxisData = weekdayData.mapIndexed { index, data ->
        RadarAxisData(
            label = weekdayLabels[index],
            value = data.amount.toFloat(),
            maxValue = maxAmount.toFloat()
        )
    }
    
    val dataSets = listOf(
        RadarDataSet(
            label = context.getString(R.string.weekday_analysis),
            axes = radarAxisData,
            color = ChartyColor.Solid(primaryColor),
            fillAlpha = 0.3f
        )
    )
    
    RadarChart(
        data = { dataSets },
        modifier = modifier,
        config = RadarChartConfig(
            gridConfig = RadarGridConfig(
                showGridLines = true,
                showAxisLines = true,
                gridStyle = RadarGridStyle.POLYGON,
                numberOfGridLevels = 5,
                gridLineWidth = 1f,
                gridLineColor = ChartyColor.Solid(gridColor),
                axisLineWidth = 1f,
                axisLineColor = ChartyColor.Solid(textColor.copy(alpha = 0.5f))
            ),
            labelConfig = RadarLabelConfig(
                showLabels = true,
                labelTextStyle = MiuixTheme.textStyles.body2.copy(color = textColor)
            ),
            centerConfig = RadarCenterConfig(
                centerBackgroundRadius = 0f
            ),
            showDataPoints = true,
            dataPointRadius = 6f,
            dataLineWidth = 3f,
            paddingFraction = 0.2f,
            startAngleDegrees = -90f
        )
    )
    
    Spacer(modifier = Modifier.height(16.dp))
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        weekdayData.forEachIndexed { index, data ->
            Surface(
                onClick = { onWeekdayClick(data) },
                shape = androidx.compose.foundation.shape.CircleShape,
                color = if (data.amount > 0) primaryColor.copy(alpha = 0.1f) else MiuixTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = weekdayLabels[index],
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

/**
 * Weekday Data Item
 */
@SuppressLint("DefaultLocale")
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
            style = MiuixTheme.textStyles.body2,
            modifier = Modifier.weight(1f)
        )
        
        Text(
            text = "${String.format("%.1f", data.percentage)}%",
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(60.dp)
        )
        
        Text(
            text = currencyFormat.format(data.amount),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.width(100.dp)
        )
    }
}

/**
 * Category Detail Item
 */
@SuppressLint("DefaultLocale")
@Composable
private fun CategoryDetailItem(
    context: Context,
    category: CategoryChartData,
    currencyFormat: NumberFormat
) {
    val categoryColor = getColorForExpenseType(category.type)
    
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(8.dp)
                        .height(8.dp)
                        .background(
                            color = categoryColor,
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
                Text(
                    text = ExpenseTypeLocalizer.getLocalizedTypeName(context, category.type),
                    style = MiuixTheme.textStyles.body2,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "${String.format("%.1f", category.percentage)}%",
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.Bold,
                color = categoryColor,
                modifier = Modifier.width(60.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        ExpressiveLinearProgressIndicator(
            progress = category.percentage / 100f,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = categoryColor,
            trackColor = MiuixTheme.colorScheme.surfaceVariant
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${category.count} ${context.getString(R.string.records)}",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            Text(
                text = currencyFormat.format(category.amount),
                style = MiuixTheme.textStyles.footnote1,
                color = categoryColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private val expenseTypeColors = mapOf(
    "DAILY_GOODS" to androidx.compose.ui.graphics.Color(0xFFEF4444),
    "LUXURY" to androidx.compose.ui.graphics.Color(0xFF8B5CF6),
    "COMMUNICATION" to androidx.compose.ui.graphics.Color(0xFF3B82F6),
    "FOOD" to androidx.compose.ui.graphics.Color(0xFF10B981),
    "SNACKS" to androidx.compose.ui.graphics.Color(0xFFF59E0B),
    "COLD_DRINKS" to androidx.compose.ui.graphics.Color(0xFF06B6D4),
    "CONVENIENCE_FOOD" to androidx.compose.ui.graphics.Color(0xFF84CC16),
    "TEXTILES" to androidx.compose.ui.graphics.Color(0xFFF97316),
    "BEVERAGES" to androidx.compose.ui.graphics.Color(0xFFEC4899),
    "CONDIMENTS" to androidx.compose.ui.graphics.Color(0xFF14B8A6),
    "TRANSPORTATION" to androidx.compose.ui.graphics.Color(0xFF6366F1),
    "DINING" to androidx.compose.ui.graphics.Color(0xFFF43F5E),
    "MEDICAL" to androidx.compose.ui.graphics.Color(0xFF0EA5E9),
    "FRUITS" to androidx.compose.ui.graphics.Color(0xFF22C55E),
    "OTHER" to androidx.compose.ui.graphics.Color(0xFF6B7280),
    "SEAFOOD" to androidx.compose.ui.graphics.Color(0xFF0891B2),
    "DAIRY" to androidx.compose.ui.graphics.Color(0xFFA855F7),
    "GIFTS" to androidx.compose.ui.graphics.Color(0xFFD946EF),
    "TRAVEL" to androidx.compose.ui.graphics.Color(0xFF0284C7),
    "GOVERNMENT" to androidx.compose.ui.graphics.Color(0xFF7C3AED),
    "UTILITIES" to androidx.compose.ui.graphics.Color(0xFFF97316),
    "BEAUTY" to androidx.compose.ui.graphics.Color(0xFFF472B6),
    "BEAN_PRODUCTS" to androidx.compose.ui.graphics.Color(0xFF84CC16),
    "COSMETICS" to androidx.compose.ui.graphics.Color(0xFFEC4899),
    "ELECTRONICS" to androidx.compose.ui.graphics.Color(0xFF3B82F6),
    "HOUSEHOLD_APPLIANCES" to androidx.compose.ui.graphics.Color(0xFF10B981),
    "HARDWARE" to androidx.compose.ui.graphics.Color(0xFF6B7280),
    "CLOTHING" to androidx.compose.ui.graphics.Color(0xFFF59E0B)
)

private fun getColorForExpenseType(type: String): androidx.compose.ui.graphics.Color {
    return expenseTypeColors[type] ?: androidx.compose.ui.graphics.Color(0xFF6B7280)
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
