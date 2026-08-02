package com.chronie.homemoney.ui.charts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronie.homemoney.domain.model.Expense
import com.chronie.homemoney.domain.model.ExpenseFilters
import com.chronie.homemoney.domain.model.ExpenseStatistics
import com.chronie.homemoney.domain.model.TimeRange
import com.chronie.homemoney.domain.repository.ExpenseRepository
import com.chronie.homemoney.domain.usecase.GetStatisticsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/**
 * ViewModel for the Charts / Statistics screen.
 *
 * Generates three types of chart data for the selected time range:
 * 1. **Daily breakdown** — spending per day.
 * 2. **Category breakdown** — spending by expense type.
 * 3. **Weekday breakdown** — spending by day of the week with per-category drill-down.
 *
 * Supports all [TimeRange] presets plus a custom date range.
 */
@HiltViewModel
class ChartsViewModel @Inject constructor(
    private val getStatisticsUseCase: GetStatisticsUseCase,
    private val expenseRepository: ExpenseRepository,
    val checkLoginStatusUseCase: com.chronie.homemoney.domain.usecase.CheckLoginStatusUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<ChartsUiState>(ChartsUiState.Loading)
    val uiState: StateFlow<ChartsUiState> = _uiState.asStateFlow()
    
    private val _selectedTimeRange = MutableStateFlow(TimeRange.THIS_MONTH)
    val selectedTimeRange: StateFlow<TimeRange> = _selectedTimeRange.asStateFlow()
    
    private val _customStartDate = MutableStateFlow<LocalDate?>(null)
    val customStartDate: StateFlow<LocalDate?> = _customStartDate.asStateFlow()
    
    private val _customEndDate = MutableStateFlow<LocalDate?>(null)
    val customEndDate: StateFlow<LocalDate?> = _customEndDate.asStateFlow()
    
    init {
        loadStatistics()
    }
    
    /** Selects a time range preset and triggers a data reload. */
    fun selectTimeRange(timeRange: TimeRange) {
        android.util.Log.d("ChartsViewModel", "Time range changed to: $timeRange")
        _selectedTimeRange.value = timeRange
        loadStatistics()
    }
    
    /** Applies a custom date range and switches to [TimeRange.CUSTOM] mode. */
    fun setCustomDateRange(startDate: LocalDate, endDate: LocalDate) {
        _customStartDate.value = startDate
        _customEndDate.value = endDate
        _selectedTimeRange.value = TimeRange.CUSTOM
        loadStatistics()
    }
    
    /** Updates the custom range start date without triggering a reload. */
    fun setCustomStartDate(startDate: LocalDate) {
        _customStartDate.value = startDate
    }
    
    /** Updates the custom range end date without triggering a reload. */
    fun setCustomEndDate(endDate: LocalDate) {
        _customEndDate.value = endDate
    }
    
    /** Pull-to-refresh: reloads all chart data. */
    fun refresh() {
        loadStatistics()
    }
    
    /**
     * Loads expense data and generates all three chart datasets.
     * Fetches up to 10,000 expenses for the selected time range to ensure
     * complete coverage for accurate statistics.
     */
    private fun loadStatistics() {
        viewModelScope.launch {
            _uiState.value = ChartsUiState.Loading
            
            try {
                val (startDate, endDate) = getDateRange()
                val filters = ExpenseFilters(
                    startDate = startDate,
                    endDate = endDate
                )
                
                // Load aggregate statistics
                val statisticsResult = getStatisticsUseCase(filters)
                
                if (statisticsResult.isSuccess) {
                    val statistics = statisticsResult.getOrNull()!!
                    
                    // Load the full expense list for chart data generation
                    val expensesResult = expenseRepository.getExpensesList(
                        page = 1,
                        limit = 10000,
                        filters = filters
                    )
                    
                    if (expensesResult.isSuccess) {
                        val expenses = expensesResult.getOrNull()!!
                        
                        // Generate all three chart datasets
                        val dailyData = generateDailyData(expenses, startDate, endDate)
                        val categoryData = generateCategoryData(expenses)
                        val weekdayData = generateWeekdayData(expenses)
                        
                        android.util.Log.d("ChartsViewModel", "Loaded data: expenses=${expenses.size}, dailyData=${dailyData.size}, categoryData=${categoryData.size}, weekdayData=${weekdayData.size}, stats=${statistics.totalAmount}")
                        
                        _uiState.value = ChartsUiState.Success(
                            statistics = statistics,
                            dailyData = dailyData,
                            categoryData = categoryData,
                            weekdayData = weekdayData,
                            startDate = startDate,
                            endDate = endDate
                        )
                    } else {
                        _uiState.value = ChartsUiState.Error(
                            expensesResult.exceptionOrNull()?.message ?: "Failed to load expenses"
                        )
                    }
                } else {
                    _uiState.value = ChartsUiState.Error(
                        statisticsResult.exceptionOrNull()?.message ?: "Failed to load statistics"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = ChartsUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Computes the start and end dates based on the selected [TimeRange].
     *
     * - THIS_WEEK: Monday to Sunday of the current week.
     * - THIS_MONTH: First to last day of the current month.
     * - LAST_MONTH: First to last day of the previous month.
     * - THIS_QUARTER: First day of the current quarter to the last day.
     * - THIS_YEAR: January 1 to December 31.
     * - CUSTOM: Uses [_customStartDate] and [_customEndDate].
     */
    private fun getDateRange(): Pair<LocalDate, LocalDate> {
        val today = LocalDate.now()
        
        val range = when (_selectedTimeRange.value) {
            TimeRange.THIS_WEEK -> {
                val startOfWeek = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                val endOfWeek = today.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY))
                Pair(startOfWeek, endOfWeek)
            }
            TimeRange.THIS_MONTH -> {
                val startOfMonth = today.with(TemporalAdjusters.firstDayOfMonth())
                val endOfMonth = today.with(TemporalAdjusters.lastDayOfMonth())
                Pair(startOfMonth, endOfMonth)
            }
            TimeRange.LAST_MONTH -> {
                val lastMonth = today.minusMonths(1)
                val startOfLastMonth = lastMonth.with(TemporalAdjusters.firstDayOfMonth())
                val endOfLastMonth = lastMonth.with(TemporalAdjusters.lastDayOfMonth())
                Pair(startOfLastMonth, endOfLastMonth)
            }
            TimeRange.THIS_QUARTER -> {
                val currentMonth = today.monthValue
                val quarterStartMonth = ((currentMonth - 1) / 3) * 3 + 1
                val startOfQuarter = today.withMonth(quarterStartMonth).with(TemporalAdjusters.firstDayOfMonth())
                val endOfQuarter = startOfQuarter.plusMonths(2).with(TemporalAdjusters.lastDayOfMonth())
                Pair(startOfQuarter, endOfQuarter)
            }
            TimeRange.THIS_YEAR -> {
                val startOfYear = today.with(TemporalAdjusters.firstDayOfYear())
                val endOfYear = today.with(TemporalAdjusters.lastDayOfYear())
                Pair(startOfYear, endOfYear)
            }
            TimeRange.CUSTOM -> {
                val start = _customStartDate.value ?: today.minusMonths(1)
                val end = _customEndDate.value ?: today
                Pair(start, end)
            }
        }
        
        android.util.Log.d("ChartsViewModel", "Date range: ${range.first} to ${range.second}")
        return range
    }
    
    /**
     * Generates daily spending data — one entry per day in the date range.
     * Days with no expenses show zero amounts.
     */
    private fun generateDailyData(
        expenses: List<Expense>,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<DailyChartData> {
        val expensesByDate = expenses.groupBy { LocalDate.parse(it.date) }
        
        val dailyData = mutableListOf<DailyChartData>()
        var currentDate = startDate
        
        while (!currentDate.isAfter(endDate)) {
            val dayExpenses = expensesByDate[currentDate] ?: emptyList()
            val totalAmount = dayExpenses.sumOf { it.amount }
            
            dailyData.add(
                DailyChartData(
                    date = currentDate,
                    amount = totalAmount,
                    count = dayExpenses.size
                )
            )
            
            currentDate = currentDate.plusDays(1)
        }
        
        return dailyData
    }
    
    /**
     * Generates category spending data grouped by expense type.
     * Includes the percentage of total spending for each category.
     * Results are sorted by amount descending.
     */
    private fun generateCategoryData(expenses: List<Expense>): List<CategoryChartData> {
        if (expenses.isEmpty()) return emptyList()
        
        val expensesByType = expenses.groupBy { it.type }
        val totalAmount = expenses.sumOf { it.amount }
        
        return expensesByType.map { (type, typeExpenses) ->
            val typeAmount = typeExpenses.sumOf { it.amount }
            CategoryChartData(
                type = type.name,
                amount = typeAmount,
                count = typeExpenses.size,
                percentage = if (totalAmount > 0) (typeAmount / totalAmount * 100).toFloat() else 0f
            )
        }.sortedByDescending { it.amount }
    }
    
    /**
     * Generates weekday spending data — one entry per day of the week (Sunday–Saturday).
     * Each weekday includes a per-category breakdown for drill-down analysis.
     * Java's DayOfWeek maps Sunday=7, so we convert to 0=Sunday for easier indexing.
     */
    private fun generateWeekdayData(expenses: List<Expense>): List<WeekdayChartData> {
        if (expenses.isEmpty()) {
            // Return empty entries for all 7 days
            return (0..6).map { dayOfWeek ->
                WeekdayChartData(
                    dayOfWeek = dayOfWeek,
                    amount = 0.0,
                    count = 0,
                    percentage = 0f,
                    categoryBreakdown = emptyList()
                )
            }
        }
        
        // Group by weekday: DayOfWeek.value % 7 maps Sunday (7) to 0, Monday (1) stays 1, etc.
        val expensesByWeekday = expenses.groupBy { expense ->
            val dayOfWeek = LocalDate.parse(expense.date).dayOfWeek.value % 7
            dayOfWeek
        }
        
        val totalAmount = expenses.sumOf { it.amount }
        
        // Generate one entry per weekday (0=Sunday through 6=Saturday)
        return (0..6).map { dayOfWeek ->
            val dayExpenses = expensesByWeekday[dayOfWeek] ?: emptyList()
            val dayAmount = dayExpenses.sumOf { it.amount }
            
            // Build per-category breakdown for this weekday
            val categoryBreakdown = if (dayExpenses.isNotEmpty()) {
                val expensesByType = dayExpenses.groupBy { it.type }
                expensesByType.map { (type, typeExpenses) ->
                    val typeAmount = typeExpenses.sumOf { it.amount }
                    CategoryChartData(
                        type = type.name,
                        amount = typeAmount,
                        count = typeExpenses.size,
                        percentage = if (dayAmount > 0) (typeAmount / dayAmount * 100).toFloat() else 0f
                    )
                }.sortedByDescending { it.amount }
            } else {
                emptyList()
            }
            
            WeekdayChartData(
                dayOfWeek = dayOfWeek,
                amount = dayAmount,
                count = dayExpenses.size,
                percentage = if (totalAmount > 0) (dayAmount / totalAmount * 100).toFloat() else 0f,
                categoryBreakdown = categoryBreakdown
            )
        }
    }
}

/**
 * Sealed class representing the charts screen UI state.
 */
sealed class ChartsUiState {
    /** Initial loading state while data is being fetched. */
    object Loading : ChartsUiState()
    /** Charts data loaded successfully. */
    data class Success(
        val statistics: ExpenseStatistics,
        val dailyData: List<DailyChartData>,
        val categoryData: List<CategoryChartData>,
        val weekdayData: List<WeekdayChartData>,
        val startDate: LocalDate,
        val endDate: LocalDate
    ) : ChartsUiState()
    /** Data loading failed with an error message. */
    data class Error(val message: String) : ChartsUiState()
}

/**
 * Data for a single day in the daily spending chart.
 *
 * @property date The calendar date.
 * @property amount Total spending on this day.
 * @property count Number of expense records on this day.
 */
data class DailyChartData(
    val date: LocalDate,
    val amount: Double,
    val count: Int
)

/**
 * Data for a single category in the category breakdown chart.
 *
 * @property type The expense type/category name (enum string).
 * @property amount Total spending in this category.
 * @property count Number of expense records in this category.
 * @property percentage Percentage of total spending this category represents.
 */
data class CategoryChartData(
    val type: String,
    val amount: Double,
    val count: Int,
    val percentage: Float
)

/**
 * Data for a single weekday in the weekday spending chart.
 *
 * @property dayOfWeek Day index: 0=Sunday, 1=Monday, ..., 6=Saturday.
 * @property amount Total spending on this weekday.
 * @property count Number of expense records on this weekday.
 * @property percentage Percentage of total spending on this weekday.
 * @property categoryBreakdown Per-category breakdown for drill-down analysis on this weekday.
 */
data class WeekdayChartData(
    val dayOfWeek: Int,
    val amount: Double,
    val count: Int,
    val percentage: Float,
    val categoryBreakdown: List<CategoryChartData>
)
