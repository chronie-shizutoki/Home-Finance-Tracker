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
    // Shared click lambda – wired into both the (newly independent) badge
    // row and any future radar-vertex taps, so the navigation behavior is
    // identical regardless of where the user taps.
    val onWeekdayClick: (WeekdayChartData) -> Unit = { weekday ->
        onNavigateToWeekdayDetail(
            weekday.dayOfWeek,
            weekday.amount,
            weekday.count,
            weekday.percentage,
            startDate,
            endDate
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        // BoxWithConstraints → single child Column (box semantics!)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val useTwoColumn = maxWidth >= 600.dp

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
                } else if (useTwoColumn) {
                    // =====================================================
                    // 600dp+: Left pane = chart + badges, Right = data rows
                    // =====================================================
                    //
                    // Align TOP so a shorter left pane does not force the
                    // right data list to crop, and vice versa. Each side is
                    // free to size itself vertically.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // LEFT: radar chart (square aspect ratio, so it
                        // never gets stretched into a weird rectangle) +
                        // the 7 weekday badges directly beneath it.
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            WeekdayRadarChart(
                                context = context,
                                weekdayData = weekdayData,
                                // aspectRatio(1f) + fillMaxWidth = square
                                // that fills exactly 50% of the card's inner
                                // width – no fixed height, no measuring
                                // ambiguity with the Row.
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            WeekdayBadgeRow(
                                context = context,
                                weekdayData = weekdayData,
                                onWeekdayClick = onWeekdayClick
                            )
                        }

                        // RIGHT: per-day breakdown rows. No fixed height so
                        // it can grow to fit all 7 lines without wrapping
                        // or vertical overflow into the bottom nav.
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            weekdayData.forEach { data ->
                                if (data.amount > 0) {
                                    WeekdayDataItem(context, data, currencyFormat)
                                }
                            }
                        }
                    }
                } else {
                    // =====================================================
                    // <600dp: stacked layout (phone) – identical to before
                    // =====================================================
                    WeekdayRadarChart(
                        context = context,
                        weekdayData = weekdayData,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(350.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    WeekdayBadgeRow(
                        context = context,
                        weekdayData = weekdayData,
                        onWeekdayClick = onWeekdayClick
                    )

                    Spacer(modifier = Modifier.height(16.dp))

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
}

/**
 * Weekday Radar Chart (chart ONLY – the 7 clickable weekday badges have been
 * extracted to [WeekdayBadgeRow]).
 *
 * Decoupling the two pieces is essential for the large-screen two-column
 * layout, where badges need to live directly below the radar chart (still in
 * the LEFT column) without being accidentally measured/sized together with a
 * fixed-height radar inside the same composable.
 */
@Composable
private fun WeekdayRadarChart(
    context: Context,
    weekdayData: List<WeekdayChartData>,
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
}

/**
 * Standalone row of 7 weekday "pill" buttons (Sun to Sat).
 *
 * Extracted so the card layout can position them exactly where needed:
 *   - Phone layout: directly under the radar chart, above the data rows.
 *   - Tablet layout: directly under the radar chart, in the LEFT column
 *     (so the data rows on the right are never reflowed by badges).
 */
@Composable
private fun WeekdayBadgeRow(
    context: Context,
    weekdayData: List<WeekdayChartData>,
    onWeekdayClick: (WeekdayChartData) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MiuixTheme.colorScheme.primary
    val weekdayLabels = listOf(
        context.getString(R.string.sunday_short),
        context.getString(R.string.monday_short),
        context.getString(R.string.tuesday_short),
        context.getString(R.string.wednesday_short),
        context.getString(R.string.thursday_short),
        context.getString(R.string.friday_short),
        context.getString(R.string.saturday_short)
    )

    Row(
        modifier = modifier.fillMaxWidth(),
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
 * Single row in the weekday-breakdown list (e.g. "Wednesday  79.7%  $19,316").
 *
 * Re-written NOT to use hard-coded column widths like width(60.dp) /
 * width(100.dp). Those fixed values force vertical wrapping once the
 * parent column becomes narrow (e.g. < 220dp on a tight two-column tablet
 * layout) and result in the "$0 / 0% / Wednesday / 0" one-character-per-
 * column mess we saw on the first iteration. Instead, we use a combination
 * of weight for the flexible label column and wrap-content-with-minimums
 * for the percentage / amount columns, with horizontal Bias pulling the
 * latter two to the right so they still line up across rows.
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
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Label (Sunday … Saturday). Uses remaining space after the two
        // fixed-right-side columns are measured, so even a very narrow
        // row still has a readable label area.
        Text(
            text = getWeekdayName(context, data.dayOfWeek),
            style = MiuixTheme.textStyles.body2,
            modifier = Modifier.weight(1f, fill = true)
        )

        // Percentage column – bold numeric text, right-aligned.
        // width(IntrinsicSize.Min) + softWrap(false) = always exactly as
        // wide as it needs to be, never shrinks into a vertical stack.
        Text(
            text = "${String.format("%.1f", data.percentage)}%",
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Bold,
            softWrap = false,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier
                .wrapContentWidth(Alignment.End)
                .widthIn(min = 56.dp)
        )

        // Amount column – primary color, right-aligned. Same treatment
        // as the percentage column to avoid breakage on narrow widths.
        Text(
            text = currencyFormat.format(data.amount),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.primary,
            softWrap = false,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier
                .wrapContentWidth(Alignment.End)
                .widthIn(min = 80.dp)
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
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
