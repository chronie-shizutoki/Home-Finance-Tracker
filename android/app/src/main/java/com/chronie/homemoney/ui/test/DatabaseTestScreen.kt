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
                title = { Text("数据库测试") },
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
                    Text("添加测试数据")
                }
                
                Button(
                    onClick = { viewModel.clearAllExpenses() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("清空数据")
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
                        text = "数据库统计",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text("记录数: ${uiState.expenseCount}")
                    Text("总金额: ¥${String.format("%.2f", uiState.totalAmount)}")
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
                text = "支出记录",
                style = MaterialTheme.typography.titleMedium
            )
            
            if (uiState.expenses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无数据")
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
                    text = if (expense.isSynced) "已同步" else "未同步",
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
