package com.chronie.homemoney.ui.expense

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.chronie.homemoney.R
import com.chronie.homemoney.domain.model.ExpenseType
import com.chronie.homemoney.ui.components.ExpressiveLoadingIndicator
import com.chronie.homemoney.ui.components.CircularIconButton
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Add Expense Screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    context: android.content.Context,
    expenseId: String? = null,
    viewModel: AddExpenseViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToAI: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Edit mode initialization (no longer forces login)
       LaunchedEffect(expenseId) {
        if (expenseId != null) {
            // If expenseId is available, load expense record for editing
            viewModel.loadExpenseForEdit(expenseId)
        }
    }

    // Date picker state management
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = if (expenseId != null)
                    context.getString(R.string.edit_expense_title)
                else
                    context.getString(R.string.add_expense_title),
                navigationIcon = {
                    CircularIconButton(onClick = onNavigateBack, modifier = Modifier.padding(start = 8.dp, end = 4.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = context.getString(R.string.back))
                    }
                },
                actions = {
                    CircularIconButton(
                        onClick = {
                            viewModel.saveExpense(
                                onSuccess = {
                                    onNavigateBack()
                                },
                                onError = {
                                    // Show error message in snackbar
                                }
                            )
                        },
                        enabled = !uiState.isSaving,
                        modifier = Modifier.padding(start = 4.dp, end = 8.dp)
                    ) {
                        if (uiState.isSaving) {
                            ExpressiveLoadingIndicator(containerVisible = false)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = context.getString(R.string.save))
                        }
                    }
                },
                color = MiuixTheme.colorScheme.background
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
            // AI Expense Entry
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToAI),
                color = MiuixTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = context.getString(R.string.ai_expense_title),
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = context.getString(R.string.ai_expense_entry_description),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                    Text(
                        text = ">",
                        style = MiuixTheme.textStyles.title3,
                        color = MiuixTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider()

            // Expense Type Dropdown Menu
            ExpenseTypeDropdown(
                selectedType = uiState.selectedType,
                error = uiState.typeError,
                context = context,
                onTypeSelected = { viewModel.setType(it) }
            )

            // Expense Amount Input
            ExpenseAmountField(
                amount = uiState.amount,
                error = uiState.amountError,
                context = context,
                onAmountChange = { viewModel.setAmount(it) }
            )

            // Expense Date Input
            ExpenseDateField(
                selectedDate = uiState.selectedDate,
                error = uiState.dateError,
                context = context,
                onClick = { showDatePicker = true }
            )

            // Expense Remark Input Field
            ExpenseRemarkField(
                remark = uiState.remark,
                context = context,
                onRemarkChange = { viewModel.setRemark(it) }
            )
        }
    }

    // Date Picker Dialog
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

    // Show save error in snackbar
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
 * Expense Type Dropdown Menu - Supports search feature
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseTypeDropdown(
    selectedType: ExpenseType?,
    error: String?,
    context: android.content.Context,
    onTypeSelected: (ExpenseType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Filter types based on search query
    val filteredTypes = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            ExpenseType.entries
        } else {
            ExpenseType.entries.filter { type ->
                val displayName = ExpenseTypeLocalizer.getLocalizedName(context, type)
                displayName.contains(searchQuery, ignoreCase = true) ||
                    type.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    // Reset search when dropdown closes
    LaunchedEffect(expanded) {
        if (!expanded) {
            searchQuery = ""
        }
    }

    Column {
        Text(
            text = context.getString(R.string.add_expense_type_label),
            style = MiuixTheme.textStyles.body2
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            TextField(
                value = if (selectedType != null && !expanded) {
                    ExpenseTypeLocalizer.getLocalizedName(context, selectedType)
                } else if (expanded && searchQuery.isNotEmpty()) {
                    searchQuery
                } else if (selectedType != null) {
                    ExpenseTypeLocalizer.getLocalizedName(context, selectedType)
                } else {
                    ""
                },
                onValueChange = {
                    searchQuery = it
                    if (!expanded) expanded = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                label = context.getString(R.string.add_expense_type_hint),
                useLabelAsPlaceholder = true,
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = expanded,
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.SecondaryEditable),
                    )
                },
                singleLine = true
            )
            
            ExposedDropdownMenu(
                modifier = Modifier.heightIn(max = 280.dp),
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                if (filteredTypes.isEmpty()) {
                    DropdownMenuItem(
                        text = { 
                            Text(
                                context.getString(R.string.no_results_found),
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            ) 
                        },
                        onClick = { expanded = false },
                        enabled = false
                    )
                } else {
                    filteredTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { 
                                Text(
                                    ExpenseTypeLocalizer.getLocalizedName(context, type),
                                    fontWeight = if (type == selectedType) FontWeight.Bold else FontWeight.Normal
                                ) 
                            },
                            onClick = {
                                onTypeSelected(type)
                                searchQuery = ""
                                expanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }
        }

        if (error != null) {
            Text(
                text = when (error) {
                    "TYPE_REQUIRED" -> context.getString(R.string.add_expense_validation_type_required)
                    else -> error
                },
                color = MiuixTheme.colorScheme.error,
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}

/**
 * Expense Amount Input Field
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
            style = MiuixTheme.textStyles.body2
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = amount,
            onValueChange = onAmountChange,
            modifier = Modifier.fillMaxWidth(),
            label = context.getString(R.string.add_expense_amount_hint),
            useLabelAsPlaceholder = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )
        if (error != null) {
            Text(
                text = when (error) {
                    "AMOUNT_REQUIRED" -> context.getString(R.string.add_expense_validation_amount_required)
                    "AMOUNT_INVALID" -> context.getString(R.string.add_expense_validation_amount_invalid)
                    else -> error
                },
                color = MiuixTheme.colorScheme.error,
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}

/**
 * Expense Date Input Field
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
            style = MiuixTheme.textStyles.body2
        )
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MiuixTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MiuixTheme.colorScheme.outline)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = selectedDate.format(dateFormatter),
                    style = MiuixTheme.textStyles.body1
                )
            }
        }
        if (error != null) {
            Text(
                text = when (error) {
                    "DATE_REQUIRED" -> context.getString(R.string.add_expense_validation_date_required)
                    else -> error
                },
                color = MiuixTheme.colorScheme.error,
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}

/**
 * Expense Remark Input Field
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
            style = MiuixTheme.textStyles.body2
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = remark,
            onValueChange = onRemarkChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            label = context.getString(R.string.add_expense_remark_hint),
            useLabelAsPlaceholder = true,
            maxLines = 4
        )
    }
}

/**
 * Expense Date Picker Dialog
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

    val surfaceColor = MiuixTheme.colorScheme.surface

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                text = context.getString(R.string.confirm),
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = LocalDate.ofEpochDay(millis / (24 * 60 * 60 * 1000))
                        onDateSelected(date)
                    }
                }
            )
        },
        dismissButton = {
            TextButton(text = context.getString(R.string.cancel), onClick = onDismiss)
        },
        shape = androidx.compose.ui.graphics.RectangleShape,
        colors = DatePickerDefaults.colors(
            containerColor = surfaceColor
        )
    ) {
        DatePicker(
            state = datePickerState,
            colors = DatePickerDefaults.colors(
                containerColor = surfaceColor
            )
        )
    }
}