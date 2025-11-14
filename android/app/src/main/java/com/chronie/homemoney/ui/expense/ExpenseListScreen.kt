package com.chronie.homemoney.ui.expense

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chronie.homemoney.R
import com.chronie.homemoney.domain.model.Expense
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 支出列表界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseListScreen(
    context: android.content.Context,
    viewModel: ExpenseListViewModel = hiltViewModel(),
    shouldRefresh: Boolean = false,
    onRefreshHandled: () -> Unit = {},
    onNavigateToMoreFunctions: () -> Unit = {},
    onNavigateToAddExpense: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // 处理刷新请求
    LaunchedEffect(shouldRefresh) {
        if (shouldRefresh) {
            viewModel.refresh()
            onRefreshHandled()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.expense_list_title)) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = context.getString(R.string.common_refresh))
                    }
                    IconButton(onClick = onNavigateToMoreFunctions) {
                        Icon(Icons.Default.MoreVert, contentDescription = context.getString(R.string.common_more_functions))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddExpense
            ) {
                Icon(Icons.Default.Add, contentDescription = context.getString(R.string.add_expense_title))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 统计信息卡片
            ExpenseStatisticsCard(
                statistics = uiState.statistics,
                context = context,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            
            // 支出列表
            when {
                uiState.isLoading && uiState.expenses.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
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
                    
                    // 计算总项数（日期标题 + 支出项）
                    val totalItems = groupedExpenses.size + uiState.expenses.size
                    
                    // 检测是否滚动到底部
                    LaunchedEffect(listState) {
                        snapshotFlow { 
                            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index 
                        }.collect { lastVisibleIndex ->
                            if (lastVisibleIndex != null && 
                                lastVisibleIndex >= totalItems - 3 && 
                                uiState.hasMore && 
                                !uiState.isLoading) {
                                viewModel.loadMore()
                            }
                        }
                    }
                    
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        groupedExpenses.forEach { (date, expenses) ->
                            // 日期标题
                            item(key = "header_$date") {
                                ExpenseDateHeader(
                                    date = date,
                                    count = expenses.size,
                                    totalAmount = expenses.sumOf { it.amount },
                                    context = context
                                )
                            }
                            
                            // 该日期下的支出项
                            items(
                                items = expenses,
                                key = { expense -> "expense_${expense.id}" }
                            ) { expense ->
                                ExpenseListItem(
                                    expense = expense,
                                    context = context
                                )
                            }
                        }
                        
                        // 加载更多指示器
                        if (uiState.hasMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (uiState.isLoading) {
                                        CircularProgressIndicator()
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
}

/**
 * 日期标题
 */
@Composable
fun ExpenseDateHeader(
    date: String,
    count: Int,
    totalAmount: Double,
    context: android.content.Context,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = date,
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
            text = String.format(Locale.getDefault(), "-¥%.2f", totalAmount),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
    }
}

/**
 * 统计信息卡片
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
                    value = String.format(Locale.getDefault(), "¥%.2f", statistics.totalAmount)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatisticItem(
                    label = context.getString(R.string.expense_stats_average),
                    value = String.format(Locale.getDefault(), "¥%.2f", statistics.averageAmount)
                )
                StatisticItem(
                    label = context.getString(R.string.expense_stats_median),
                    value = String.format(Locale.getDefault(), "¥%.2f", statistics.medianAmount)
                )
            }
        }
    }
}

/**
 * 统计项
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
 * 支出列表项
 */
@Composable
fun ExpenseListItem(
    expense: Expense,
    context: android.content.Context,
    modifier: Modifier = Modifier
) {
    // 使用传递的context来获取本地化字符串
    val typeDisplayName = ExpenseTypeLocalizer.getLocalizedName(context, expense.type)
    
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
    
    Card(
        modifier = modifier.fillMaxWidth()
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
                    text = expense.time.format(dateFormatter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = String.format(Locale.getDefault(), "-¥%.2f", expense.amount),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
