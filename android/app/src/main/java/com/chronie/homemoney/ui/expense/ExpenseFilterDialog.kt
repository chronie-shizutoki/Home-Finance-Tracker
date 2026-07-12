package com.chronie.homemoney.ui.expense

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chronie.homemoney.R
import com.chronie.homemoney.domain.model.ExpenseFilters
import com.chronie.homemoney.domain.model.ExpenseType
import com.chronie.homemoney.domain.model.SortOption
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Expense Filter Dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseFilterDialog(
    context: android.content.Context,
    currentFilters: ExpenseFilters,
    onDismiss: () -> Unit,
    onApplyFilters: (ExpenseFilters) -> Unit
) {
    var keyword by remember { mutableStateOf(currentFilters.keyword ?: "") }
    var selectedTypes by remember { mutableStateOf(currentFilters.type?.let { setOf(it) } ?: emptySet()) }
    var minAmount by remember { mutableStateOf(currentFilters.minAmount?.toString() ?: "") }
    var maxAmount by remember { mutableStateOf(currentFilters.maxAmount?.toString() ?: "") }
    var startDate by remember { mutableStateOf(currentFilters.startDate) }
    var endDate by remember { mutableStateOf(currentFilters.endDate) }
    var sortOption by remember { mutableStateOf(currentFilters.sortBy) }
    var showTypeSelector by remember { mutableStateOf(false) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Title bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = context.getString(R.string.expense_list_filter_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = context.getString(R.string.cancel))
                    }
                }

                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

                // Filter content area
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Search keyword
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        label = { Text(context.getString(R.string.common_search)) },
                        placeholder = { Text(context.getString(R.string.expense_list_search_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    // Expense Type Selection
                    OutlinedButton(
                        onClick = { showTypeSelector = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (selectedTypes.isEmpty()) {
                                context.getString(R.string.expense_list_filter_all_types)
                            } else {
                                context.getString(R.string.expense_list_filter_select_types) + " (${selectedTypes.size})"
                            }
                        )
                    }
                    
                    // Date range
                    Text(
                        text = context.getString(R.string.expense_list_filter_date_range),
                        style = MaterialTheme.typography.titleSmall
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showStartDatePicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = startDate?.format(dateFormatter) 
                                    ?: context.getString(R.string.expense_list_filter_start_date)
                            )
                        }
                        
                        OutlinedButton(
                            onClick = { showEndDatePicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = endDate?.format(dateFormatter) 
                                    ?: context.getString(R.string.expense_list_filter_end_date)
                            )
                        }
                    }
                    
                    // Amount range
                    Text(
                        text = context.getString(R.string.expense_list_filter_amount_range),
                        style = MaterialTheme.typography.titleSmall
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = minAmount,
                            onValueChange = { minAmount = it },
                            label = { Text(context.getString(R.string.expense_list_filter_min_amount)) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        
                        OutlinedTextField(
                            value = maxAmount,
                            onValueChange = { maxAmount = it },
                            label = { Text(context.getString(R.string.expense_list_filter_max_amount)) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }
                    
                    // Sorting options
                    Text(
                        text = context.getString(R.string.expense_list_sort),
                        style = MaterialTheme.typography.titleSmall
                    )
                    
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SortOption.entries.forEach { option ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = sortOption == option,
                                    onClick = { sortOption = option }
                                )
                                Text(
                                    text = getSortOptionText(context, option),
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

                // Bottom buttons area
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            keyword = ""
                            selectedTypes = emptySet()
                            minAmount = ""
                            maxAmount = ""
                            startDate = null
                            endDate = null
                            sortOption = SortOption.DATE_DESC
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(context.getString(R.string.expense_list_clear_filters))
                    }
                    
                    Button(
                        onClick = {
                            val filters = ExpenseFilters(
                                keyword = keyword.ifBlank { null },
                                type = selectedTypes.firstOrNull(),
                                minAmount = minAmount.toDoubleOrNull(),
                                maxAmount = maxAmount.toDoubleOrNull(),
                                startDate = startDate,
                                endDate = endDate,
                                sortBy = sortOption
                            )
                            onApplyFilters(filters)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(context.getString(R.string.expense_list_apply_filters))
                    }
                }
            }
        }
    }
    
    // Expense Type Selection Dialog Box
    if (showTypeSelector) {
        ExpenseTypeSelector(
            context = context,
            selectedTypes = selectedTypes,
            onDismiss = { showTypeSelector = false },
            onConfirm = { types ->
                selectedTypes = types
                showTypeSelector = false
            }
        )
    }
    
    // Start Date Picker
    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate?.toEpochDay()?.times(86400000L)
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        startDate = LocalDate.ofEpochDay(millis / 86400000L)
                    }
                    showStartDatePicker = false
                }) {
                    Text(context.getString(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text(context.getString(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    
    // End Date Picker
    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = endDate?.toEpochDay()?.times(86400000L)
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        endDate = LocalDate.ofEpochDay(millis / 86400000L)
                    }
                    showEndDatePicker = false
                }) {
                    Text(context.getString(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text(context.getString(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * Expense Type Selector - Supports Search Functionality
 */
@Composable
fun ExpenseTypeSelector(
    context: android.content.Context,
    selectedTypes: Set<ExpenseType>,
    onDismiss: () -> Unit,
    onConfirm: (Set<ExpenseType>) -> Unit
) {
    var tempSelectedTypes by remember { mutableStateOf(selectedTypes) }
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
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Column {
                Text(context.getString(R.string.expense_list_filter_select_types))
                if (filteredTypes.size != ExpenseType.entries.size) {
                    Text(
                        text = context.getString(R.string.search_results_count, filteredTypes.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(context.getString(R.string.search_category)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = context.getString(R.string.clear))
                            }
                        }
                    },
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (filteredTypes.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = context.getString(R.string.no_results_found),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        filteredTypes.forEach { type ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = tempSelectedTypes.contains(type),
                                    onCheckedChange = { checked ->
                                        tempSelectedTypes = if (checked) {
                                            tempSelectedTypes + type
                                        } else {
                                            tempSelectedTypes - type
                                        }
                                    }
                                )
                                Text(
                                    text = ExpenseTypeLocalizer.getLocalizedName(context, type),
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(tempSelectedTypes) }) {
                Text(context.getString(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.cancel))
            }
        }
    )
}

/**
 * Get localized text for sorting options
 */
private fun getSortOptionText(context: android.content.Context, option: SortOption): String {
    return when (option) {
        SortOption.DATE_DESC -> context.getString(R.string.expense_list_sort_date_desc)
        SortOption.DATE_ASC -> context.getString(R.string.expense_list_sort_date_asc)
        SortOption.AMOUNT_DESC -> context.getString(R.string.expense_list_sort_amount_desc)
        SortOption.AMOUNT_ASC -> context.getString(R.string.expense_list_sort_amount_asc)
    }
}
