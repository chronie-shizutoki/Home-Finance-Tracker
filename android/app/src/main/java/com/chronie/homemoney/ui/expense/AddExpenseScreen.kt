package com.chronie.homemoney.ui.expense

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chronie.homemoney.R
import com.chronie.homemoney.domain.model.ExpenseType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 添加支出界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    context: android.content.Context,
    viewModel: AddExpenseViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // 显示类型选择对话框的状态
    var showTypeDialog by remember { mutableStateOf(false) }
    
    // 显示日期选择器的状态
    var showDatePicker by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.add_expense_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = context.getString(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.saveExpense(
                                onSuccess = {
                                    onNavigateBack()
                                },
                                onError = { error ->
                                    // 错误会通过 snackbar 显示
                                }
                            )
                        },
                        enabled = !uiState.isSaving
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Check, contentDescription = context.getString(R.string.save))
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 类型选择
            ExpenseTypeField(
                selectedType = uiState.selectedType,
                error = uiState.typeError,
                context = context,
                onClick = { showTypeDialog = true }
            )
            
            // 金额输入
            ExpenseAmountField(
                amount = uiState.amount,
                error = uiState.amountError,
                context = context,
                onAmountChange = { viewModel.setAmount(it) }
            )
            
            // 日期选择
            ExpenseDateField(
                selectedDate = uiState.selectedDate,
                error = uiState.dateError,
                context = context,
                onClick = { showDatePicker = true }
            )
            
            // 备注输入
            ExpenseRemarkField(
                remark = uiState.remark,
                context = context,
                onRemarkChange = { viewModel.setRemark(it) }
            )
        }
    }
    
    // 类型选择对话框
    if (showTypeDialog) {
        ExpenseTypeDialog(
            context = context,
            onDismiss = { showTypeDialog = false },
            onTypeSelected = { type ->
                viewModel.setType(type)
                showTypeDialog = false
            }
        )
    }
    
    // 日期选择器
    if (showDatePicker) {
        ExpenseDatePickerDialog(
            context = context,
            initialDate = uiState.selectedDate,
            onDismiss = { showDatePicker = false },
            onDateSelected = { date ->
                viewModel.setDate(date)
                showDatePicker = false
            }
        )
    }
    
    // 显示保存错误
    LaunchedEffect(uiState.saveError) {
        uiState.saveError?.let { error ->
            snackbarHostState.showSnackbar(
                message = context.getString(R.string.add_expense_save_failed, error),
                duration = SnackbarDuration.Short
            )
        }
    }
}

/**
 * 类型选择字段
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseTypeField(
    selectedType: ExpenseType?,
    error: String?,
    context: android.content.Context,
    onClick: () -> Unit
) {
    Column {
        Text(
            text = context.getString(R.string.add_expense_type_label),
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedCard(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = if (selectedType != null) {
                        ExpenseTypeLocalizer.getLocalizedName(context, selectedType)
                    } else {
                        context.getString(R.string.add_expense_type_hint)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selectedType != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        if (error != null) {
            Text(
                text = when (error) {
                    "TYPE_REQUIRED" -> context.getString(R.string.add_expense_validation_type_required)
                    else -> error
                },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}

/**
 * 金额输入字段
 */
@Composable
fun ExpenseAmountField(
    amount: String,
    error: String?,
    context: android.content.Context,
    onAmountChange: (String) -> Unit
) {
    Column {
        Text(
            text = context.getString(R.string.add_expense_amount_label),
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = amount,
            onValueChange = onAmountChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(context.getString(R.string.add_expense_amount_hint)) },
            prefix = { Text("¥ ") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            isError = error != null
        )
        if (error != null) {
            Text(
                text = when (error) {
                    "AMOUNT_REQUIRED" -> context.getString(R.string.add_expense_validation_amount_required)
                    "AMOUNT_INVALID" -> context.getString(R.string.add_expense_validation_amount_invalid)
                    else -> error
                },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}

/**
 * 日期选择字段
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDateField(
    selectedDate: LocalDate,
    error: String?,
    context: android.content.Context,
    onClick: () -> Unit
) {
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    
    Column {
        Text(
            text = context.getString(R.string.add_expense_date_label),
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedCard(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = selectedDate.format(dateFormatter),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        if (error != null) {
            Text(
                text = when (error) {
                    "DATE_REQUIRED" -> context.getString(R.string.add_expense_validation_date_required)
                    else -> error
                },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}

/**
 * 备注输入字段
 */
@Composable
fun ExpenseRemarkField(
    remark: String,
    context: android.content.Context,
    onRemarkChange: (String) -> Unit
) {
    Column {
        Text(
            text = context.getString(R.string.add_expense_remark_label),
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = remark,
            onValueChange = onRemarkChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            placeholder = { Text(context.getString(R.string.add_expense_remark_hint)) },
            maxLines = 4
        )
    }
}

/**
 * 类型选择对话框
 */
@Composable
fun ExpenseTypeDialog(
    context: android.content.Context,
    onDismiss: () -> Unit,
    onTypeSelected: (ExpenseType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.add_expense_type_label)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                ExpenseType.values().forEach { type ->
                    TextButton(
                        onClick = { onTypeSelected(type) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = ExpenseTypeLocalizer.getLocalizedName(context, type),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.cancel))
            }
        },
        shape = MaterialTheme.shapes.large
    )
}

/**
 * 日期选择器对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDatePickerDialog(
    context: android.content.Context,
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.toEpochDay() * 24 * 60 * 60 * 1000
    )
    
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = LocalDate.ofEpochDay(millis / (24 * 60 * 60 * 1000))
                        onDateSelected(date)
                    }
                }
            ) {
                Text(context.getString(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.cancel))
            }
        },
        shape = MaterialTheme.shapes.large
    ) {
        DatePicker(state = datePickerState)
    }
}
