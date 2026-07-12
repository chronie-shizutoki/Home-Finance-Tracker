package com.chronie.homemoney.ui.expense

import android.annotation.SuppressLint
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.material3.SheetValue
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.chronie.homemoney.R
import com.chronie.homemoney.domain.model.Expense
import com.chronie.homemoney.ui.budget.BudgetCard
import com.chronie.homemoney.ui.components.ExpressiveLoadingIndicator
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Expense List Screen
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun ExpenseListScreen(
    context: android.content.Context,
    viewModel: ExpenseListViewModel = hiltViewModel(),
    shouldRefresh: Boolean = false,
    onRefreshHandled: () -> Unit = {},
    onNavigateToAddExpense: () -> Unit = {},
    onNavigateToEditExpense: (expenseId: String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var showFilterDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var budgetRefreshTrigger by remember { mutableIntStateOf(0) }
    
    val pullRefreshState = rememberPullToRefreshState()
    
    // Get window size class for responsive layout
    // Use LocalContext to get the original context (not the localized one passed in)
    val localContext = LocalContext.current
    val activity = localContext as? android.app.Activity
        ?: localContext.findActivity()
    val windowSizeClass = if (activity != null) {
        calculateWindowSizeClass(activity)
    } else {
        null
    }
    // Use table layout only on Medium (600dp+) or Expanded screens
    val useTableLayout = windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Medium ||
                         windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Expanded
    
    // Handle refresh requests from parent scope
    LaunchedEffect(shouldRefresh) {
        if (shouldRefresh) {
            viewModel.refresh()
            budgetRefreshTrigger++
            onRefreshHandled()
        }
    }
    
    // Refresh function
    val onRefresh: () -> Unit = {
        isRefreshing = true
        viewModel.refresh()
        budgetRefreshTrigger++
    }
    
    // Handle refresh state reset after refresh 
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            delay(1000.milliseconds)
            isRefreshing = false
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Top Toolbar - Fixed at top of page, does not scroll with content
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = context.getString(R.string.expense_list_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                
                Box {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = context.getString(R.string.common_more_functions)
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(context.getString(R.string.common_filter)) },
                            onClick = {
                                showMoreMenu = false
                                showFilterDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(context.getString(R.string.expense_list_clear_filters)) },
                            onClick = {
                                showMoreMenu = false
                                viewModel.resetFilters()
                            }
                        )
                    }
                }
            }
        }
        
        // Content Area - Below toolbar, can scroll
        Column(modifier = Modifier.fillMaxSize()) {
            // Leave space for toolbar
            Spacer(modifier = Modifier.height(64.dp)) // Toolbar height approximation
            
            when {
                uiState.isLoading && uiState.expenses.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        ExpressiveLoadingIndicator(containerVisible = true)
                    }
                }
                uiState.error != null && uiState.expenses.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = uiState.error ?: context.getString(R.string.common_error),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Button(onClick = { viewModel.refresh() }) {
                                Text(context.getString(R.string.common_retry))
                            }
                        }
                    }
                }
                uiState.expenses.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = context.getString(R.string.expense_list_empty),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = context.getString(R.string.expense_list_empty_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    val listState = rememberLazyListState()
                    val groupedExpenses = uiState.groupedExpenses
                    var lastLoadTime by remember { mutableLongStateOf(0L) }
                    
                    // Improved scroll detection: only load more when actually near bottom
                    LaunchedEffect(listState, uiState.hasMore, uiState.isLoading) {
                        snapshotFlow { 
                            val layoutInfo = listState.layoutInfo
                            val totalItemsCount = layoutInfo.totalItemsCount
                            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index
                            Triple(totalItemsCount, lastVisibleIndex, layoutInfo.visibleItemsInfo.firstOrNull()?.index)
                        }.collect { (totalItemsCount, lastVisibleIndex, firstVisibleIndex) ->
                            val currentTime = System.currentTimeMillis()
                            if (totalItemsCount > 0 && 
                                lastVisibleIndex != null && 
                                firstVisibleIndex != null &&
                                lastVisibleIndex >= totalItemsCount - 1 && // Only trigger when last item is visible
                                uiState.hasMore && 
                                !uiState.isLoading &&
                                currentTime - lastLoadTime > 1000) { // Debounce, at least 1 second interval
                                lastLoadTime = currentTime
                                viewModel.loadMore()
                            }
                        }
                    }
                    
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = onRefresh,
                        state = pullRefreshState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp), // Leave space for floating action button
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                        // Budget Management Card
                        item(key = "budget_card") {
                            BudgetCard(
                                context = context,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                refreshTrigger = budgetRefreshTrigger
                            )
                        }
                        
                        // Expense Statistics Card
                        item(key = "stats_card") {
                            ExpenseStatisticsCard(
                                statistics = uiState.statistics,
                                context = context,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            )
                        }
                        
                        // Expense List Items
                        groupedExpenses.forEach { (date, expenses) ->
                            // Date Header
                            item(key = "header_$date") {
                                if (useTableLayout) {
                                    ExpenseTableDateHeader(
                                        date = date,
                                        count = expenses.size,
                                        totalAmount = expenses.sumOf { it.amount },
                                        context = context,
                                        locale = context.resources.configuration.locale.toLanguageTag(),
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                } else {
                                    ExpenseDateHeader(
                                        date = date,
                                        count = expenses.size,
                                        totalAmount = expenses.sumOf { it.amount },
                                        context = context,
                                        locale = context.resources.configuration.locale.toLanguageTag(),
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                            }
                            
                            // Expense Items for this Date
                            if (useTableLayout) {
                                item(key = "table_$date") {
                                    ExpenseTableItems(
                                        expenses = expenses,
                                        context = context,
                                        onEdit = { expense -> onNavigateToEditExpense(expense.id) },
                                        onDelete = { expense -> viewModel.deleteExpense(expense) },
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                            } else {
                                items(
                                    items = expenses,
                                    key = { expense -> "expense_${expense.id}" }
                                ) { expense ->
                                    LongPressExpenseItem(
                                        expense = expense,
                                        context = context,
                                        onEdit = { onNavigateToEditExpense(expense.id) },
                                        onDelete = { viewModel.deleteExpense(expense) },
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                            }
                        }
                        
                        // Load More Indicator
                        if (uiState.hasMore) {
                            item(key = "load_more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (uiState.isLoading) {
                                        ExpressiveLoadingIndicator(containerVisible = true)
                                    } else {
                                        Button(onClick = { viewModel.loadMore() }) {
                                            Text(context.getString(R.string.common_loading))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }   
                // Floating Action Button
        FloatingActionButton(
            onClick = onNavigateToAddExpense,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .padding(bottom = 80.dp) // Raise button to avoid overlapping with bottom navigation bar
        ) {
            Icon(Icons.Default.Add, contentDescription = context.getString(R.string.add_expense_title))
        }
        
        // Filter Dialog Box
        if (showFilterDialog) {
            ExpenseFilterDialog(
                context = context,
                currentFilters = uiState.filters,
                onDismiss = { showFilterDialog = false },
                onApplyFilters = { filters ->
                    viewModel.updateFilters(filters)
                }
            )
        }
    }
}

/**
 * Date Header
 */
@Composable
fun ExpenseDateHeader(
    date: String,
    count: Int,
    totalAmount: Double,
    context: android.content.Context,
    locale: String? = null,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val displayDate = formatRelativeDate(date, context, locale)
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = displayDate,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = context.getString(R.string.expense_stats_count) + ": $count",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "-" + context.getString(R.string.currency_format, context.getString(R.string.currency_symbol), totalAmount),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
    }
}

/**
 * Statistics Card
 */
@Composable
fun ExpenseStatisticsCard(
    statistics: com.chronie.homemoney.domain.model.ExpenseStatistics,
    context: android.content.Context,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatisticItem(
                    label = context.getString(R.string.expense_stats_count),
                    value = statistics.count.toString()
                )
                StatisticItem(
                    label = context.getString(R.string.expense_stats_total),
                    value = context.getString(R.string.currency_format, context.getString(R.string.currency_symbol), statistics.totalAmount)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatisticItem(
                    label = context.getString(R.string.expense_stats_average),
                    value = context.getString(R.string.currency_format, context.getString(R.string.currency_symbol), statistics.averageAmount)
                )
                StatisticItem(
                    label = context.getString(R.string.expense_stats_median),
                    value = context.getString(R.string.currency_format, context.getString(R.string.currency_symbol), statistics.medianAmount)
                )
            }
        }
    }
}

/**
 * Statistics Item
 */
@Composable
fun StatisticItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/**
 * Long Press Expense Item
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LongPressExpenseItem(
    expense: Expense,
    context: android.content.Context,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Bottom Sheet Menu Display State
    val showBottomSheetMenu = remember { mutableStateOf(false) }
    val bottomSheetState = rememberBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded
    )
    
    // Dialog State - First Confirm Dialog
    val showFirstConfirmDialog = remember { mutableStateOf(false) }
    // Dialog State - Second Confirm Dialog
    val showSecondConfirmDialog = remember { mutableStateOf(false) }
    
    // Handle First Confirmation
    fun handleFirstConfirm() {
        showFirstConfirmDialog.value = false
        showSecondConfirmDialog.value = true
    }
    
    // Handle Second Confirmation
    fun handleSecondConfirm() {
        showSecondConfirmDialog.value = false
        showBottomSheetMenu.value = false
        onDelete()
    }
    
    // Cancel Delete
    fun cancelDelete() {
        showFirstConfirmDialog.value = false
        showSecondConfirmDialog.value = false
    }
    
    // Show Delete Confirmation Dialog
    fun showDeleteConfirm() {
        showBottomSheetMenu.value = false
        showFirstConfirmDialog.value = true
    }
    
    // Localized Expense Type Name
    val typeDisplayName = ExpenseTypeLocalizer.getLocalizedName(context, expense.type)
    
    Box(modifier = modifier.fillMaxWidth()) {
        // Expense List Item
        ExpenseListItem(
            expense = expense,
            context = context,
            modifier = Modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            showBottomSheetMenu.value = true
                        }
                    )
                }
        )
        
        // Bottom Sheet Menu
        if (showBottomSheetMenu.value) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheetMenu.value = false },
                sheetState = bottomSheetState,
                contentColor = MaterialTheme.colorScheme.onSurface,
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Selected Record Details
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = typeDisplayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (!expense.remark.isNullOrBlank()) {
                            Text(
                                text = expense.remark,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDateByLocale(expense.date, context.resources.configuration.locale.toLanguageTag()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "-" + context.getString(R.string.currency_format, context.getString(R.string.currency_symbol), expense.amount),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    
                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Edit Button
                        Button(
                            onClick = {
                                showBottomSheetMenu.value = false
                                onEdit()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Edit",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = context.getString(R.string.edit))
                        }
                        
                        // Delete Button
                        Button(
                            onClick = {
                                showDeleteConfirm()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = context.getString(R.string.delete))
                        }
                    }
                }
            }
        }
        
        // First Confirmation Dialog
        if (showFirstConfirmDialog.value) {
            AlertDialog(
                onDismissRequest = { cancelDelete() },
                title = { Text(text = context.getString(R.string.delete_confirm_title)) },
                text = { Text(text = context.getString(R.string.delete_confirm_message)) },
                confirmButton = {
                    Button(onClick = { handleFirstConfirm() }) {
                        Text(text = context.getString(R.string.confirm))
                    }
                },
                dismissButton = {
                    Button(onClick = { cancelDelete() }) {
                        Text(text = context.getString(R.string.cancel))
                    }
                }
            )
        }
        
        // Second Confirmation Dialog
        if (showSecondConfirmDialog.value) {
            AlertDialog(
                onDismissRequest = { cancelDelete() },
                title = { Text(text = context.getString(R.string.delete_second_confirm_title)) },
                text = { Text(text = context.getString(R.string.delete_second_confirm_message)) },
                confirmButton = {
                    Button(
                        onClick = { handleSecondConfirm() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text(text = context.getString(R.string.delete))
                    }
                },
                dismissButton = {
                    Button(onClick = { cancelDelete() }) {
                        Text(text = context.getString(R.string.cancel))
                    }
                }
            )
        }
    }
}

/**
 * Expense List Item
 */
@Composable
fun ExpenseListItem(
    expense: Expense,
    context: android.content.Context,
    modifier: Modifier = Modifier
) {
    // Localized Expense Type Name Display
    val typeDisplayName = ExpenseTypeLocalizer.getLocalizedName(context, expense.type)
    
    
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = typeDisplayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                if (!expense.remark.isNullOrBlank()) {
                    Text(
                        text = expense.remark,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatDateByLocale(expense.date, context.resources.configuration.locale.toLanguageTag()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "-" + context.getString(R.string.currency_format, context.getString(R.string.currency_symbol), expense.amount),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * Table date header for wide screens
 */
@Composable
fun ExpenseTableDateHeader(
    date: String,
    count: Int,
    totalAmount: Double,
    context: android.content.Context,
    locale: String? = null,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val displayDate = formatRelativeDate(date, context, locale)
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = displayDate,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${context.getString(R.string.expense_stats_count)}: $count",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "-" + context.getString(R.string.currency_format, context.getString(R.string.currency_symbol), totalAmount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Table layout for expense items on wide screens
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseTableItems(
    expenses: List<Expense>,
    context: android.content.Context,
    onEdit: (Expense) -> Unit,
    onDelete: (Expense) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Table header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = context.getString(R.string.expense_type),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = context.getString(R.string.expense_remark),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1.5f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = context.getString(R.string.expense_amount),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.width(120.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = context.getString(R.string.common_actions),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.width(100.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            
            // Table rows
            expenses.forEachIndexed { index, expense ->
                val typeDisplayName = ExpenseTypeLocalizer.getLocalizedName(context, expense.type)
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = typeDisplayName,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = expense.remark ?: "-",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1.5f),
                        color = if (expense.remark.isNullOrBlank()) 
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) 
                        else 
                            MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = "-" + context.getString(R.string.currency_format, context.getString(R.string.currency_symbol), expense.amount),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.width(120.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Row(
                        modifier = Modifier.width(100.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        IconButton(
                            onClick = { onEdit(expense) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = context.getString(R.string.edit),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { onDelete(expense) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = context.getString(R.string.delete),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                
                if (index < expenses.size - 1) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Extension function to find Activity from Context
 */
private tailrec fun android.content.Context.findActivity(): android.app.Activity? {
    return when (this) {
        is android.app.Activity -> this
        is android.content.ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
