package com.chronie.homemoney.ui.budget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chronie.homemoney.R
import com.chronie.homemoney.domain.model.Budget
import com.chronie.homemoney.ui.components.ExpressiveSwitch
import com.chronie.homemoney.ui.components.CircularIconButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * Budget Settings Dialog
 */
@Composable
fun BudgetSettingsDialog(
    show: Boolean,
    context: android.content.Context,
    currentBudget: Budget?,
    onDismiss: () -> Unit,
    onSave: (monthlyLimit: Double, warningThreshold: Double, isEnabled: Boolean) -> Unit
) {
    var monthlyLimit by remember(show, currentBudget) { 
        mutableStateOf(currentBudget?.monthlyLimit?.toString() ?: "")
    }
    var warningThreshold by remember(show, currentBudget) { 
        mutableStateOf(((currentBudget?.warningThreshold ?: 0.8) * 100).toString())
    }
    var isEnabled by remember(show, currentBudget) { 
        mutableStateOf(currentBudget?.isEnabled ?: false)
    }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    WindowDialog(
        show = show,
        title = context.getString(R.string.budget_settings_title),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                // Enable feature switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = context.getString(R.string.budget_enable_feature),
                        style = MiuixTheme.textStyles.body1
                    )
                    ExpressiveSwitch(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it }
                    )
                }
                
                // Monthly Budget Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularIconButton(
                        onClick = {
                            val current = monthlyLimit.toDoubleOrNull() ?: 0.0
                            monthlyLimit = (current - 1000.0).coerceAtLeast(0.0).toString()
                            showError = false
                        },
                        enabled = isEnabled
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = null)
                    }
                    TextField(
                        value = monthlyLimit,
                        onValueChange = {
                            monthlyLimit = it
                            showError = false
                        },
                        label = context.getString(R.string.budget_monthly_limit),
                        useLabelAsPlaceholder = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        enabled = isEnabled
                    )
                    CircularIconButton(
                        onClick = {
                            val current = monthlyLimit.toDoubleOrNull() ?: 0.0
                            monthlyLimit = (current + 1000.0).toString()
                            showError = false
                        },
                        enabled = isEnabled
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                }
                
                // Warning threshold input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularIconButton(
                        onClick = {
                            val current = warningThreshold.toDoubleOrNull() ?: 80.0
                            warningThreshold = (current - 10.0).coerceAtLeast(0.0).toString()
                            showError = false
                        },
                        enabled = isEnabled
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = null)
                    }
                    TextField(
                        value = warningThreshold,
                        onValueChange = {
                            warningThreshold = it
                            showError = false
                        },
                        label = context.getString(R.string.budget_warning_threshold),
                        useLabelAsPlaceholder = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        enabled = isEnabled
                    )
                    CircularIconButton(
                        onClick = {
                            val current = warningThreshold.toDoubleOrNull() ?: 80.0
                            warningThreshold = (current + 10.0).coerceAtMost(100.0).toString()
                            showError = false
                        },
                        enabled = isEnabled
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                }

                // Warning threshold hint (was supportingText)
                Text(
                    text = context.getString(R.string.budget_warning_threshold_hint),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
                
                // Error message
                if (showError) {
                    Text(
                        text = errorMessage,
                        color = MiuixTheme.colorScheme.error,
                        style = MiuixTheme.textStyles.footnote1
                    )
                }

                // Top header with X (cancel) and Check (save) icons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularIconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = context.getString(R.string.common_cancel),
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                    CircularIconButton(
                        onClick = {
                            // Validate input
                            val limit = monthlyLimit.toDoubleOrNull()
                            val threshold = warningThreshold.toDoubleOrNull()

                            when {
                                isEnabled && (limit == null || limit <= 0) -> {
                                    showError = true
                                    errorMessage = context.getString(R.string.budget_error_invalid_limit)
                                }
                                isEnabled && (threshold == null || threshold < 0 || threshold > 100) -> {
                                    showError = true
                                    errorMessage = context.getString(R.string.budget_error_invalid_threshold)
                                }
                                else -> {
                                    onSave(
                                        limit ?: 0.0,
                                        (threshold ?: 80.0) / 100,
                                        isEnabled
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = context.getString(R.string.common_save),
                            tint = MiuixTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
}
