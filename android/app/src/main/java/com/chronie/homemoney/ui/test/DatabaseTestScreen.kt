package com.chronie.homemoney.ui.test

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.chronie.homemoney.R
import com.chronie.homemoney.data.local.entity.ExpenseEntity
import com.chronie.homemoney.ui.components.CircularIconButton
import com.chronie.homemoney.ui.expense.formatDateByLocale
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DatabaseTestScreen(
    context: android.content.Context,
    onNavigateBack: () -> Unit,
    viewModel: DatabaseTestViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = context.getString(R.string.database_test),
                navigationIcon = {
                    CircularIconButton(onClick = onNavigateBack, modifier = Modifier.padding(start = 8.dp, end = 4.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = context.getString(R.string.back))
                    }
                },
                actions = {
                    Box(modifier = Modifier.padding(end = 8.dp))
                },
                color = MiuixTheme.colorScheme.background
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.addTestExpense() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    Text(context.getString(R.string.add_test_data))
                }

                Button(
                    onClick = { viewModel.clearAllExpenses() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.error
                    )
                ) {
                    Text(context.getString(R.string.clear_data))
                }
            }
            
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = context.getString(R.string.database_statistics),
                        style = MiuixTheme.textStyles.body1
                    )
                    Text(context.getString(R.string.record_count, uiState.expenseCount))
                    Text(context.getString(R.string.total_amount_database, uiState.totalAmount))
                }
            }
            
            if (!uiState.message.isNullOrEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(
                        color = if (uiState.isError) {
                            MiuixTheme.colorScheme.errorContainer
                        } else {
                            MiuixTheme.colorScheme.primaryContainer
                        }
                    )
                ) {
                    Text(
                        text = uiState.message ?: "",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            
            Text(
                text = context.getString(R.string.expense_records),
                style = MiuixTheme.textStyles.body1
            )
            
            if (uiState.expenses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(context.getString(R.string.no_data))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.expenses) { expense ->
                        ExpenseItem(
                            context = context,
                            expense = expense.toUiModel()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExpenseItem(
    context: android.content.Context,
    expense: ExpenseItemUiModel
) {
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
                    style = MiuixTheme.textStyles.body1
                )
                Text(
                        text = context.getString(R.string.currency_format, context.getString(R.string.currency_symbol), expense.amount),
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.error
                    )
            }
            
            if (expense.remark.isNotEmpty()) {
                Text(
                    text = expense.remark,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
            
            Text(
                text = formatDateByLocale(expense.timeFormatted, context.resources.configuration.locales[0].toLanguageTag()),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = context.getString(if (expense.isSynced) R.string.synced else R.string.not_synced),
                    style = MiuixTheme.textStyles.footnote2,
                    color = if (expense.isSynced) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.error
                    }
                )
                
                Text(
                    text = "ID: ${expense.id}",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
        }
    }
}

private fun ExpenseEntity.toUiModel(): ExpenseItemUiModel {
    return ExpenseItemUiModel(
        id = id,
        type = type,
        remark = remark ?: "",
        amount = amount,
        timeFormatted = date,
        isSynced = isSynced
    )
}

data class ExpenseItemUiModel(
    val id: String,
    val type: String,
    val remark: String,
    val amount: Double,
    val timeFormatted: String,
    val isSynced: Boolean
)
