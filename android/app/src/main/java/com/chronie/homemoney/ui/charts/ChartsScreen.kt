package com.chronie.homemoney.ui.charts

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.himanshoe.charty.line.LineChart
import com.himanshoe.charty.line.data.LineData
import com.himanshoe.charty.line.config.LineChartConfig
import com.himanshoe.charty.bar.BarChart
import com.himanshoe.charty.bar.data.BarData
import com.himanshoe.charty.bar.config.BarChartConfig
import com.himanshoe.charty.common.config.ChartScaffoldConfig
import com.himanshoe.charty.color.ChartyColor
import com.chronie.homemoney.R
import com.chronie.homemoney.domain.model.TimeRange
import com.chronie.homemoney.ui.expense.ExpenseTypeLocalizer
import com.chronie.homemoney.ui.expense.formatDateByLocale
import com.chronie.homemoney.ui.components.ExpressiveLinearProgressIndicator
import com.chronie.homemoney.ui.components.ExpressiveLoadingIndicator
import com.chronie.homemoney.ui.components.MiuixDatePickerSheet
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowListPopup
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.theme.LocalDismissState
import androidx.compose.foundation.shape.RoundedCornerShape

enum class ChartType {
    TREND, CATEGORY, WEEKDAY
}

@Composable
fun ChartsScreen(
    context: Context,
    viewModel: ChartsViewModel = hiltViewModel(),
    onNavigateToWeekdayDetail: (dayOfWeek: Int, amount: Double, count: Int, percentage: Float, startDate: String, endDate: String) -> Unit = { _, _, _, _, _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedTimeRange by viewModel.selectedTimeRange.collectAsState()   
    var showTimeRangePopup by remember { mutableStateOf(false) }
    var selectedChartType by remember { mutableStateOf(ChartType.TREND) }
    var showChartTypePopup by remember { mutableStateOf(false) }
    
    // Custom range state
    val customStartDate by viewModel.customStartDate.collectAsState()
    val customEndDate by viewModel.customEndDate.collectAsState()
    var showCustomRangeBottomSheet by remember { mutableStateOf(false) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top toolbar with title and selectors
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MiuixTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = context.getString(R.string.charts_title),
                            style = MiuixTheme.textStyles.title3
                        )
                        
                        // Chart Type Switcher
                        Box {
                            Row(
                                modifier = Modifier
                                    .clickable { showChartTypePopup = true }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = getChartTypeText(context, selectedChartType),
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            
                            WindowListPopup(
                                show = showChartTypePopup,
                                onDismissRequest = { showChartTypePopup = false }
                            ) {
                                val dismiss = LocalDismissState.current
                                ListPopupColumn {
                                    ChartType.entries.forEachIndexed { index, type ->
                                        DropdownImpl(
                                            text = getChartTypeText(context, type),
                                            optionSize = ChartType.entries.size,
                                            isSelected = selectedChartType == type,
                                            index = index,
                                            onSelectedIndexChange = {
                                                selectedChartType = type
                                                dismiss?.invoke()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // Time Range Selector
                    Box {
                        IconButton(onClick = { showTimeRangePopup = true }) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Select time range"
                            )
                        }
                        
                        WindowListPopup(
                            show = showTimeRangePopup,
                            onDismissRequest = { showTimeRangePopup = false }
                        ) {
                            val dismiss = LocalDismissState.current
                            val timeRanges = listOf(
                                TimeRange.THIS_WEEK,
                                TimeRange.THIS_MONTH,
                                TimeRange.LAST_MONTH,
                                TimeRange.THIS_QUARTER,
                                TimeRange.THIS_YEAR,
                                TimeRange.CUSTOM
                            )
                            ListPopupColumn {
                                timeRanges.forEachIndexed { index, timeRange ->
                                    DropdownImpl(
                                        text = getTimeRangeText(context, timeRange),
                                        optionSize = timeRanges.size,
                                        isSelected = selectedTimeRange == timeRange,
                                        index = index,
                                        onSelectedIndexChange = {
                                            if (timeRange == TimeRange.CUSTOM) {
                                                showCustomRangeBottomSheet = true
                                                dismiss?.invoke()
                                            } else {
                                                viewModel.selectTimeRange(timeRange)
                                                dismiss?.invoke()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Content area
            when (val state = uiState) {
                is ChartsUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        ExpressiveLoadingIndicator()
                    }
                }
                is ChartsUiState.Success -> {
                    ChartsContent(
                        context = context,
                        state = state,
                        selectedTimeRange = selectedTimeRange,
                        selectedChartType = selectedChartType,
                        onNavigateToWeekdayDetail = onNavigateToWeekdayDetail
                    )
                }
                is ChartsUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = state.message,
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.refresh() }, colors = ButtonDefaults.buttonColorsPrimary()) {
                            Text(context.getString(R.string.retry))
                        }
                    }
                }
            }
        }
    }

    CustomRangeBottomSheet(
        show = showCustomRangeBottomSheet,
        context = context,
        initialStartDate = customStartDate ?: LocalDate.now().minusMonths(1),
        initialEndDate = customEndDate ?: LocalDate.now(),
        onDismiss = { showCustomRangeBottomSheet = false },
        onConfirm = { startDate, endDate ->
            viewModel.setCustomDateRange(startDate, endDate)
            viewModel.selectTimeRange(TimeRange.CUSTOM)
        }
    )
}

@Composable
private fun ChartsContent(
    context: Context,
    state: ChartsUiState.Success,
    selectedTimeRange: TimeRange,
    selectedChartType: ChartType,
    onNavigateToWeekdayDetail: (dayOfWeek: Int, amount: Double, count: Int, percentage: Float, startDate: String, endDate: String) -> Unit = { _, _, _, _, _, _ -> }
) {
    val scrollState = rememberScrollState()
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.getDefault()) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
            .padding(bottom = 80.dp)
    ) {
        // Time range display
        TimeRangeCard(context, selectedTimeRange, state)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Statistics summary (Always show)
        StatisticsSummaryCard(context, state.statistics, currencyFormat)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        when (selectedChartType) {
            ChartType.TREND -> {
                // Trend line chart
                TrendLineChartCard(context, state.dailyData, currencyFormat)
            }
            ChartType.CATEGORY -> {
                // Category breakdown
                CategoryBreakdownCard(context, state.categoryData, currencyFormat)
            }
            ChartType.WEEKDAY -> {
                // Weekday analysis
                WeekdayRadarChartCard(
                    context = context,
                    weekdayData = state.weekdayData,
                    currencyFormat = currencyFormat,
                    startDate = state.startDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    endDate = state.endDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    onNavigateToWeekdayDetail = onNavigateToWeekdayDetail
                )
            }
        }
    }
}

@Composable
private fun TimeRangeCard(
    context: Context,
    selectedTimeRange: TimeRange,
    state: ChartsUiState.Success
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = getTimeRangeText(context, selectedTimeRange),
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${formatDateByLocale(state.startDate.format(DateTimeFormatter.ISO_LOCAL_DATE), context.resources.configuration.locales[0].toLanguageTag())} - ${formatDateByLocale(state.endDate.format(DateTimeFormatter.ISO_LOCAL_DATE), context.resources.configuration.locales[0].toLanguageTag())}",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }
    }
}

@Composable
private fun StatisticsSummaryCard(
    context: Context,
    statistics: com.chronie.homemoney.domain.model.ExpenseStatistics,
    currencyFormat: NumberFormat
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = context.getString(R.string.statistics_summary),
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatisticItem(
                    label = context.getString(R.string.total_amount),
                    value = currencyFormat.format(statistics.totalAmount)
                )
                StatisticItem(
                    label = context.getString(R.string.count),
                    value = "${statistics.count}"
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatisticItem(
                    label = context.getString(R.string.average_amount),
                    value = currencyFormat.format(statistics.averageAmount)
                )
                StatisticItem(
                    label = context.getString(R.string.median_amount),
                    value = currencyFormat.format(statistics.medianAmount)
                )
            }
        }
    }
}

@Composable
private fun StatisticItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceSecondary
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TrendLineChartCard(
    context: Context,
    dailyData: List<DailyChartData>,
    currencyFormat: NumberFormat
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = context.getString(R.string.trend_chart),
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (dailyData.isEmpty()) {
                Text(
                    text = context.getString(R.string.no_data),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(vertical = 32.dp)
                )
            } else {
                HighQualityLineChart(
                    data = dailyData,
                    currencyFormat = currencyFormat,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
            }
        }
    }
}

@Composable
private fun HighQualityLineChart(
    data: List<DailyChartData>,
    currencyFormat: NumberFormat,
    modifier: Modifier = Modifier
) {
    val primaryColor = MiuixTheme.colorScheme.primary
    
    val labelStep = maxOf(1, data.size / 7)
    val lineData = data.mapIndexed { index, dailyData ->
        val label = if (index % labelStep == 0 || index == data.size - 1) {
            "${dailyData.date.monthValue}/${dailyData.date.dayOfMonth}"
        } else {
            ""
        }
        LineData(
            label = label,
            value = dailyData.amount.toFloat()
        )
    }
    
    Box(modifier = modifier.padding(start = 24.dp, end = 8.dp)) {
        LineChart(
            data = { lineData },
            modifier = Modifier.fillMaxSize(),
            color = ChartyColor.Solid(primaryColor),
            lineConfig = LineChartConfig(
                lineWidth = 4f,
                showPoints = true,
                pointRadius = 6f,
                smoothCurve = true
            ),
            scaffoldConfig = ChartScaffoldConfig(
                gridColor = MiuixTheme.colorScheme.dividerLine,
                axisColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                labelTextStyle = MiuixTheme.textStyles.footnote1.copy(color = MiuixTheme.colorScheme.onSurface)
            )
        )
    }
}

@Composable
private fun CategoryBreakdownCard(
    context: Context,
    categoryData: List<CategoryChartData>,
    currencyFormat: NumberFormat
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = context.getString(R.string.category_breakdown),
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (categoryData.isEmpty()) {
                Text(
                    text = context.getString(R.string.no_data),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                val barData = categoryData.map { category ->
                    BarData(
                        label = ExpenseTypeLocalizer.getLocalizedTypeName(context, category.type),
                        value = category.amount.toFloat()
                    )
                }
                
                Box(modifier = Modifier.padding(start = 24.dp, end = 8.dp, bottom = 8.dp)) {
                    BarChart(
                        data = { barData },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        color = ChartyColor.Solid(MiuixTheme.colorScheme.primary),
                        barConfig = BarChartConfig(
                            barWidthFraction = 0.4f
                        ),
                        scaffoldConfig = ChartScaffoldConfig(
                            gridColor = MiuixTheme.colorScheme.dividerLine,
                            axisColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            labelTextStyle = MiuixTheme.textStyles.footnote2.copy(
                                color = MiuixTheme.colorScheme.onSurface,
                                fontSize = 9.sp
                            )
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                categoryData.forEach { category ->
                    CategoryItem(context, category, currencyFormat)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
private fun CategoryItem(
    context: Context,
    category: CategoryChartData,
    currencyFormat: NumberFormat
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = ExpenseTypeLocalizer.getLocalizedTypeName(context, category.type),
                style = MiuixTheme.textStyles.body2
            )
            Text(
                text = "${String.format("%.1f", category.percentage)}%",
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        ExpressiveLinearProgressIndicator(
            progress = category.percentage / 100f,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = MiuixTheme.colorScheme.primary,
            trackColor = MiuixTheme.colorScheme.surfaceVariant
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = "${currencyFormat.format(category.amount)} (${category.count} ${context.getString(R.string.records)})",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceSecondary
        )
    }
}

@Composable
private fun CustomRangeBottomSheet(
    show: Boolean,
    context: Context,
    initialStartDate: LocalDate,
    initialEndDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit
) {
    var startDate by remember(show) { mutableStateOf(initialStartDate) }
    var endDate by remember(show) { mutableStateOf(initialEndDate) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    WindowBottomSheet(
        show = show,
        title = context.getString(R.string.custom_range),
        onDismissRequest = onDismiss,
        startAction = {
            TextButton(text = context.getString(R.string.cancel), onClick = onDismiss)
        },
        endAction = {
            TextButton(
                text = context.getString(R.string.confirm),
                onClick = {
                    onConfirm(startDate, endDate)
                },
                enabled = !startDate.isAfter(endDate)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                onClick = { showStartDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = MiuixTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = context.getString(R.string.expense_list_filter_start_date),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                        Text(
                            text = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                            style = MiuixTheme.textStyles.body1
                        )
                    }
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = context.getString(R.string.expense_list_filter_start_date),
                        tint = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }

            Surface(
                onClick = { showEndDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = MiuixTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = context.getString(R.string.expense_list_filter_end_date),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                        Text(
                            text = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                            style = MiuixTheme.textStyles.body1
                        )
                    }
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = context.getString(R.string.expense_list_filter_end_date),
                        tint = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }
        }
    }

    MiuixDatePickerSheet(
        context = context,
        show = showStartDatePicker,
        initialDate = startDate,
        onDismiss = { showStartDatePicker = false },
        onDateSelected = { date -> startDate = date },
        title = context.getString(R.string.expense_list_filter_start_date)
    )

    MiuixDatePickerSheet(
        context = context,
        show = showEndDatePicker,
        initialDate = endDate,
        onDismiss = { showEndDatePicker = false },
        onDateSelected = { date -> endDate = date },
        title = context.getString(R.string.expense_list_filter_end_date)
    )
}

@Composable
private fun TimeRangeOption(
    context: Context,
    timeRange: TimeRange,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = isSelected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = getTimeRangeText(context, timeRange),
            style = MiuixTheme.textStyles.body1
        )
    }
}

private fun getChartTypeText(context: Context, type: ChartType): String {
    return when (type) {
        ChartType.TREND -> context.getString(R.string.trend_chart)
        ChartType.CATEGORY -> context.getString(R.string.category_breakdown)
        ChartType.WEEKDAY -> context.getString(R.string.weekday_analysis)
    }
}

private fun getTimeRangeText(context: Context, timeRange: TimeRange): String {
    return when (timeRange) {
        TimeRange.THIS_WEEK -> context.getString(R.string.this_week)
        TimeRange.THIS_MONTH -> context.getString(R.string.this_month)
        TimeRange.LAST_MONTH -> context.getString(R.string.last_month)
        TimeRange.THIS_QUARTER -> context.getString(R.string.this_quarter)
        TimeRange.THIS_YEAR -> context.getString(R.string.this_year)
        TimeRange.CUSTOM -> context.getString(R.string.custom_range)
    }
}
