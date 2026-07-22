package com.chronie.homemoney.ui.charts

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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

@Composable
fun ChartsScreen(
    context: Context,
    viewModel: ChartsViewModel = hiltViewModel(),
    onNavigateToWeekdayDetail: (dayOfWeek: Int, amount: Double, count: Int, percentage: Float, startDate: String, endDate: String) -> Unit = { _, _, _, _, _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedTimeRange by viewModel.selectedTimeRange.collectAsState()   
    var showTimeRangeDialog by remember { mutableStateOf(false) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top toolbar with title and time range selector
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
                    Text(
                        text = context.getString(R.string.charts_title),
                        style = MiuixTheme.textStyles.title3,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                    
                    IconButton(onClick = { showTimeRangeDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Select time range"
                        )
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
        
        TimeRangeDialog(
            show = showTimeRangeDialog,
            context = context,
            selectedTimeRange = selectedTimeRange,
            onDismiss = { showTimeRangeDialog = false },
            onTimeRangeSelected = { timeRange ->
                viewModel.selectTimeRange(timeRange)
                showTimeRangeDialog = false
            }
        )
    }
}

@Composable
private fun ChartsContent(
    context: Context,
    state: ChartsUiState.Success,
    selectedTimeRange: TimeRange,
    onNavigateToWeekdayDetail: (dayOfWeek: Int, amount: Double, count: Int, percentage: Float, startDate: String, endDate: String) -> Unit = { _, _, _, _, _, _ -> }
) {
    val scrollState = rememberScrollState()
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.getDefault()) }
    
    // Debug logging
    LaunchedEffect(state) {
        android.util.Log.d("ChartsScreen", "UI updated: total=${state.statistics.totalAmount}, categories=${state.categoryData.size}, daily=${state.dailyData.size}")
    }
    
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
        
        // Statistics summary
        StatisticsSummaryCard(context, state.statistics, currencyFormat)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Trend line chart
        TrendLineChartCard(context, state.dailyData, currencyFormat)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Category breakdown
        CategoryBreakdownCard(context, state.categoryData, currencyFormat)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Weekday analysis radar chart
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

@SuppressLint("DefaultLocale")
@Composable
private fun HighQualityLineChart(
    data: List<DailyChartData>,
    currencyFormat: NumberFormat,
    modifier: Modifier = Modifier
) {
    val primaryColor = MiuixTheme.colorScheme.primary
    val textColor = MiuixTheme.colorScheme.onSurface
    val gridColor = MiuixTheme.colorScheme.dividerLine
    
    Canvas(modifier = modifier) {
        if (data.isEmpty()) return@Canvas
        
        val maxAmount = data.maxOfOrNull { it.amount } ?: 0.0
        if (maxAmount == 0.0) return@Canvas
        
        val width = size.width
        val height = size.height
        val paddingLeft = 80f
        val paddingRight = 40f
        val paddingTop = 60f
        val paddingBottom = 80f
        
        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom
        
        val paint = android.graphics.Paint().apply {
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 28f
            color = textColor.toArgb()
        }
        
        // Draw Y-axis grid lines and labels
        val ySteps = 5
        for (i in 0..ySteps) {
            val y = paddingTop + (chartHeight / ySteps) * i
            val amount = maxAmount * (1 - i.toFloat() / ySteps)
            
            // Grid line
            drawLine(
                color = gridColor,
                start = Offset(paddingLeft, y),
                end = Offset(width - paddingRight, y),
                strokeWidth = 1f
            )
            
            // Y-axis label
            val label = currencyFormat.format(amount)
            drawContext.canvas.nativeCanvas.drawText(
                label,
                paddingLeft - 10f,
                y + 10f,
                paint.apply { textAlign = android.graphics.Paint.Align.RIGHT }
            )
        }
        
        // Draw axes
        drawLine(
            color = textColor.copy(alpha = 0.5f),
            start = Offset(paddingLeft, paddingTop),
            end = Offset(paddingLeft, height - paddingBottom),
            strokeWidth = 2f
        )
        drawLine(
            color = textColor.copy(alpha = 0.5f),
            start = Offset(paddingLeft, height - paddingBottom),
            end = Offset(width - paddingRight, height - paddingBottom),
            strokeWidth = 2f
        )
        
        // Draw line chart
        val path = Path()
        val points = mutableListOf<Pair<Float, Float>>()
        
        data.forEachIndexed { index, dailyData ->
            val x = paddingLeft + (index.toFloat() / (data.size - 1).coerceAtLeast(1)) * chartWidth
            val y = height - paddingBottom - (dailyData.amount.toFloat() / maxAmount.toFloat()) * chartHeight
            
            points.add(Pair(x, y))
            
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        
        // Draw line chart
        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(width = 4f)
        )
        
        // Draw data points and labels
        data.forEachIndexed { index, dailyData ->
            val (x, y) = points[index]
            
            // Data point
            drawCircle(
                color = primaryColor,
                radius = 6f,
                center = Offset(x, y)
            )
            
            drawCircle(
                color = Color.White,
                radius = 3f,
                center = Offset(x, y)
            )
            
            // Draw value label for non-zero data points
            if (dailyData.amount > 0) {
                val valueLabel = String.format("%.0f", dailyData.amount)
                drawContext.canvas.nativeCanvas.drawText(
                    valueLabel,
                    x,
                    y - 20f,
                    paint.apply {
                        color = primaryColor.toArgb()
                        textAlign = android.graphics.Paint.Align.CENTER
                        textSize = 22f
                    }
                )
            }
        }
        
        // Draw X-axis date labels
        val xLabelStep = (data.size / 7).coerceAtLeast(1)
        data.forEachIndexed { index, dailyData ->
            if (index % xLabelStep == 0 || index == data.size - 1) {
                val (x, _) = points[index]
                val dateLabel = "${dailyData.date.monthValue}/${dailyData.date.dayOfMonth}"
                
                drawContext.canvas.nativeCanvas.drawText(
                    dateLabel,
                    x,
                    height - paddingBottom + 40f,
                    paint.apply {
                        color = textColor.toArgb()
                        textAlign = android.graphics.Paint.Align.CENTER
                        textSize = 26f
                    }
                )
            }
        }
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
                categoryData.forEach { category ->
                    CategoryItem(context, category, currencyFormat)
                    Spacer(modifier = Modifier.height(12.dp))
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
private fun TimeRangeDialog(
    show: Boolean,
    context: Context,
    selectedTimeRange: TimeRange,
    onDismiss: () -> Unit,
    onTimeRangeSelected: (TimeRange) -> Unit
) {
    val viewModel = hiltViewModel<ChartsViewModel>()
    val customStartDate by viewModel.customStartDate.collectAsState()
    val customEndDate by viewModel.customEndDate.collectAsState()

    var showCustomRangeBottomSheet by remember { mutableStateOf(false) }

    WindowBottomSheet(
        show = show,
        title = context.getString(R.string.select_time_range),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                TimeRange.THIS_WEEK,
                TimeRange.THIS_MONTH,
                TimeRange.LAST_MONTH,
                TimeRange.THIS_QUARTER,
                TimeRange.THIS_YEAR,
                TimeRange.CUSTOM
            ).forEach { timeRange ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (timeRange == TimeRange.CUSTOM) {
                                showCustomRangeBottomSheet = true
                            } else {
                                onTimeRangeSelected(timeRange)
                            }
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedTimeRange == timeRange,
                        onClick = {
                            if (timeRange == TimeRange.CUSTOM) {
                                showCustomRangeBottomSheet = true
                            } else {
                                onTimeRangeSelected(timeRange)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = getTimeRangeText(context, timeRange),
                        style = MiuixTheme.textStyles.body1
                    )
                }
            }

            if (selectedTimeRange == TimeRange.CUSTOM && customStartDate != null && customEndDate != null) {
                val start = customStartDate
                val end = customEndDate
                if (start != null && end != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val startDateString = start.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val endDateString = end.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    Text(
                        text = "${context.getString(R.string.expense_list_filter_start_date)} ${formatDateByLocale(startDateString, context.resources.configuration.locales[0].toLanguageTag())} ${context.getString(R.string.expense_list_filter_end_date)} ${formatDateByLocale(endDateString, context.resources.configuration.locales[0].toLanguageTag())}",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
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
            onTimeRangeSelected(TimeRange.CUSTOM)
        }
    )
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
