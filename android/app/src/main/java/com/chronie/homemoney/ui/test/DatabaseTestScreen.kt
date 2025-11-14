package com.chronie.homemoney.ui.test

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chronie.homemoney.R
import java.text.SimpleDateFormat
import java.util.*

/**
 * 数据库测试界面
 * 用于验证数据库基本操作
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseTestScreen(
    onNavigateBack: () -> Unit,
    viewModel: DatabaseTestViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.database_test)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.addTestExpense() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.add_test_data))
                }
                
                Button(
                    onClick = { viewModel.clearAllExpenses() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.clear_data))
                }
            }
            
            // 统计信息
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.database_statistics),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(stringResource(R.string.record_count, uiState.expenseCount))
                    Text(stringResource(R.string.total_amount, uiState.totalAmount))
                }
            }
            
            // 状态消息
            if (uiState.message.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.isError) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    )
                ) {
                    Text(
                        text = uiState.message,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            
            // 支出列表
            Text(
                text = stringResource(R.string.expense_records),
                style = MaterialTheme.typography.titleMedium
            )
            
            if (uiState.expenses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.no_data))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.expenses) { expense ->
                        ExpenseItem(expense = expense)
                    }
                }
            }
        }
    }
}

@Composable
fun ExpenseItem(expense: ExpenseItemUiModel) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = expense.type,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "¥${String.format("%.2f", expense.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
            if (expense.remark.isNotEmpty()) {
                Text(
                    text = expense.remark,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = expense.timeFormatted,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(if (expense.isSynced) R.string.synced else R.string.not_synced),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (expense.isSynced) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
                
                Text(
                    text = "ID: ${expense.id.take(8)}...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

data class ExpenseItemUiModel(
    val id: String,
    val type: String,
    val remark: String,
    val amount: Double,
    val timeFormatted: String,
    val isSynced: Boolean
)
