package com.chronie.homemoney.ui.expense

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chronie.homemoney.R
import com.chronie.homemoney.domain.model.ExpenseFilters
import com.chronie.homemoney.domain.model.ExpenseType
import com.chronie.homemoney.domain.model.SortOption
import com.chronie.homemoney.ui.components.MiuixDatePickerSheet
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.window.WindowListPopup
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.theme.LocalDismissState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color

/**
 * Expense Filter Dialog
 */
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
    var showSortPopup by remember { mutableStateOf(false) }
    
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = MiuixTheme.colorScheme.surface
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
                        style = MiuixTheme.textStyles.title3
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = context.getString(R.string.cancel))
                    }
                }

                HorizontalDivider(Modifier, 0.5.dp, MiuixTheme.colorScheme.dividerLine)

                // Filter content area
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Search keyword
                    TextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        label = context.getString(R.string.common_search),
                        useLabelAsPlaceholder = true,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    // Expense Type Selection
                    Button(
                        onClick = { showTypeSelector = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors()
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
                        style = MiuixTheme.textStyles.body2
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showStartDatePicker = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors()
                        ) {
                            Text(
                                text = startDate?.format(dateFormatter) 
                                    ?: context.getString(R.string.expense_list_filter_start_date)
                            )
                        }
                        
                        Button(
                            onClick = { showEndDatePicker = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors()
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
                        style = MiuixTheme.textStyles.body2
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = minAmount,
                            onValueChange = { minAmount = it },
                            label = context.getString(R.string.expense_list_filter_min_amount),
                            useLabelAsPlaceholder = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )

                        TextField(
                            value = maxAmount,
                            onValueChange = { maxAmount = it },
                            label = context.getString(R.string.expense_list_filter_max_amount),
                            useLabelAsPlaceholder = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }
                    
                    // Sorting options
                    Text(
                        text = context.getString(R.string.expense_list_sort),
                        style = MiuixTheme.textStyles.body2
                    )
                    
                    Box {
                        Surface(
                            onClick = { showSortPopup = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MiuixTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MiuixTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = getSortOptionText(context, sortOption),
                                    style = MiuixTheme.textStyles.body1
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onSurfaceSecondary
                                )
                            }
                        }

                        WindowListPopup(
                            show = showSortPopup,
                            onDismissRequest = { showSortPopup = false }
                        ) {
                            val dismiss = LocalDismissState.current
                            ListPopupColumn {
                                SortOption.entries.forEachIndexed { index, option ->
                                    DropdownImpl(
                                        text = getSortOptionText(context, option),
                                        optionSize = SortOption.entries.size,
                                        isSelected = sortOption == option,
                                        index = index,
                                        onSelectedIndexChange = {
                                            sortOption = option
                                            showSortPopup = false
                                            dismiss?.invoke()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(Modifier, 0.5.dp, MiuixTheme.colorScheme.dividerLine)

                // Bottom buttons area
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            keyword = ""
                            selectedTypes = emptySet()
                            minAmount = ""
                            maxAmount = ""
                            startDate = null
                            endDate = null
                            sortOption = SortOption.DATE_DESC
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors()
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
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColorsPrimary()
                    ) {
                        Text(context.getString(R.string.expense_list_apply_filters))
                    }
                }
            }
        }
    }
    
    // Expense Type Selection Dialog Box
    ExpenseTypeSelector(
        show = showTypeSelector,
        context = context,
        selectedTypes = selectedTypes,
        onDismiss = { showTypeSelector = false },
        onConfirm = { types ->
            selectedTypes = types
            showTypeSelector = false
        }
    )
    
    // Start Date Picker
    MiuixDatePickerSheet(
        context = context,
        show = showStartDatePicker,
        initialDate = startDate ?: LocalDate.now(),
        onDismiss = { showStartDatePicker = false },
        onDateSelected = { date -> startDate = date },
        title = context.getString(R.string.expense_list_filter_start_date)
    )

    // End Date Picker
    MiuixDatePickerSheet(
        context = context,
        show = showEndDatePicker,
        initialDate = endDate ?: LocalDate.now(),
        onDismiss = { showEndDatePicker = false },
        onDateSelected = { date -> endDate = date },
        title = context.getString(R.string.expense_list_filter_end_date)
    )
}

/**
 * Expense Type Selector - Supports Search Functionality
 */
@Composable
fun ExpenseTypeSelector(
    show: Boolean,
    context: android.content.Context,
    selectedTypes: Set<ExpenseType>,
    onDismiss: () -> Unit,
    onConfirm: (Set<ExpenseType>) -> Unit
) {
    var tempSelectedTypes by remember(show) { mutableStateOf(selectedTypes) }
    var searchQuery by remember(show) { mutableStateOf("") }

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

    WindowDialog(
        show = show,
        title = context.getString(R.string.expense_list_filter_select_types),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (filteredTypes.size != ExpenseType.entries.size) {
                Text(
                    text = context.getString(R.string.search_results_count, filteredTypes.size),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }

            // Search field
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    label = context.getString(R.string.search_category),
                    useLabelAsPlaceholder = true,
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = context.getString(R.string.clear))
                            }
                        }
                    },
                    singleLine = true
                )
                
                IconButton(
                    onClick = { /* Search is already filtering in real-time */ }
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = context.getString(R.string.common_search),
                        tint = MiuixTheme.colorScheme.primary
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                } else {
                    filteredTypes.forEach { type ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                state = if (tempSelectedTypes.contains(type)) ToggleableState.On else ToggleableState.Off,
                                onClick = {
                                    tempSelectedTypes = if (tempSelectedTypes.contains(type)) {
                                        tempSelectedTypes - type
                                    } else {
                                        tempSelectedTypes + type
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

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(text = context.getString(R.string.cancel), onClick = onDismiss)
                TextButton(text = context.getString(R.string.confirm), onClick = { onConfirm(tempSelectedTypes) })
            }
        }
    }
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
