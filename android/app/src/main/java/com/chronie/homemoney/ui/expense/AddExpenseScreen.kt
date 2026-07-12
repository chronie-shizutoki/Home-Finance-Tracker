package com.chronie.homemoney.ui.expense

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
import androidx.compose.material3.*
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
            TopAppBar(
                title = { 
                    Text(
                        if (expenseId != null) 
                            context.getString(R.string.edit_expense_title) 
                        else 
                            context.getString(R.string.add_expense_title) 
                    ) 
                },
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
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
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium
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
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = context.getString(R.string.ai_expense_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = context.getString(R.string.ai_expense_entry_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                    Text(
                        text = ">",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
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
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
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
                placeholder = { Text(context.getString(R.string.add_expense_type_hint)) },
                leadingIcon = { 
                    Icon(Icons.Default.Search, contentDescription = null) 
                },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = expanded,
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.SecondaryEditable),
                    )
                },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                isError = error != null,
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
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
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = amount,
            onValueChange = onAmountChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(context.getString(R.string.add_expense_amount_hint)) },
            prefix = { Text(context.getString(R.string.currency_symbol) + " ") },
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

    val surfaceColor = MaterialTheme.colorScheme.surface

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