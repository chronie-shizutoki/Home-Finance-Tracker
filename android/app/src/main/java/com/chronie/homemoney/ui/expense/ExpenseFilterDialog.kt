package com.chronie.homemoney.ui.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.chronie.homemoney.R
import com.chronie.homemoney.domain.model.ExpenseFilters
import com.chronie.homemoney.domain.model.ExpenseType
import com.chronie.homemoney.ui.components.CircularIconButton
import com.chronie.homemoney.ui.components.MiuixDatePickerSheet
import com.chronie.homemoney.ui.scroll.RegisterScrollToTop
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.core.graphics.scale

/**
 * Expense Filter BottomSheet
 * Uses WindowBottomSheet for a native bottom-up sliding experience.
 * Height is optimized via the sheet's natural max-height behavior.
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
    var showTypeSelector by remember { mutableStateOf(false) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    WindowBottomSheet(
        show = true,
        title = context.getString(R.string.expense_list_filter_title),
        startAction = {
            CircularIconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = context.getString(R.string.cancel),
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
        },
        endAction = {
            CircularIconButton(
                onClick = {
                    val filters = ExpenseFilters(
                        keyword = keyword.ifBlank { null },
                        type = selectedTypes.firstOrNull(),
                        minAmount = minAmount.toDoubleOrNull(),
                        maxAmount = maxAmount.toDoubleOrNull(),
                        startDate = startDate,
                        endDate = endDate,
                        sortBy = currentFilters.sortBy
                    )
                    onApplyFilters(filters)
                    onDismiss()
                }
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = context.getString(R.string.expense_list_apply_filters),
                    tint = MiuixTheme.colorScheme.primary
                )
            }
        },
        onDismissRequest = onDismiss,
        insideMargin = DpSize(16.dp, 0.dp)
    ) {
        val scrollState = rememberScrollState()
        RegisterScrollToTop(scrollState)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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

            // Bottom spacing for safe area
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Expense Type Selection Dialog
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

    WindowBottomSheet(
        show = show,
        title = context.getString(R.string.expense_list_filter_select_types),
        startAction = {
            CircularIconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = context.getString(R.string.cancel),
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
        },
        endAction = {
            CircularIconButton(onClick = { onConfirm(tempSelectedTypes) }) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = context.getString(R.string.confirm),
                    tint = MiuixTheme.colorScheme.primary
                )
            }
        },
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
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
            }

            val typeScrollState = rememberScrollState()
            RegisterScrollToTop(typeScrollState)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(typeScrollState),
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

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Compress bitmap helper for avatar editor usage
 */
fun compressBitmapToBytes(
    bitmap: android.graphics.Bitmap,
    maxBytes: Int,
    format: android.graphics.Bitmap.CompressFormat,
    quality: Int = 90
): ByteArray {
    var q = quality.coerceIn(1, 100)
    var scale = 1f
    var result: ByteArray
    while (true) {
        val target = if (scale >= 1f) bitmap else bitmap.scale(
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1)
        )
        val out = java.io.ByteArrayOutputStream()
        target.compress(format, if (format == android.graphics.Bitmap.CompressFormat.PNG) 100 else q, out)
        result = out.toByteArray()
        if (result.size <= maxBytes) break
        if (format != android.graphics.Bitmap.CompressFormat.PNG && q > 30) {
            q -= 10
        } else if (scale > 0.2f) {
            scale -= 0.1f
        } else {
            break
        }
    }
    return result
}
