package com.chronie.homemoney.ui.budget

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.chronie.homemoney.R
import com.chronie.homemoney.domain.model.BudgetStatus
import com.chronie.homemoney.ui.components.ExpressiveLinearProgressIndicator
import com.chronie.homemoney.ui.components.ExpressiveLoadingIndicator
import com.chronie.homemoney.ui.expense.formatMonthLabelByLocale
import androidx.compose.ui.platform.LocalLocale
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Budget Management Card
 * Displays budget usage and settings in the expense list screen
 */
@Composable
fun BudgetCard(
    context: android.content.Context,
    viewModel: BudgetViewModel = hiltViewModel(),
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    refreshTrigger: Int = 0
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    
    // Listen for refresh trigger
    LaunchedEffect(refreshTrigger) {
        viewModel.refresh()
    }
    
    // If budget feature is not enabled, show enable prompt
    if (uiState.budget?.isEnabled != true) {
        BudgetEnablePrompt(
            context = context,
            onEnableClick = {
                showSettings = true
            },
            modifier = modifier
        )
    } else {
        // Display budget usage
        val usage = uiState.budgetUsage
        if (usage != null) {
            BudgetUsageCard(
                context = context,
                usage = usage,
                onSettingsClick = { showSettings = true },
                modifier = modifier
            )
        } else {
            // Loading state while fetching budget usage
            Card(
                modifier = modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ExpressiveLoadingIndicator()
                }
            }
        }
    }
    
    // Budget Settings Dialog
    if (showSettings) {
        BudgetSettingsDialog(
            context = context,
            currentBudget = uiState.budget,
            onDismiss = { showSettings = false },
            onSave = { limit, threshold, enabled ->
                viewModel.saveBudget(limit, threshold, enabled)
                showSettings = false
            }
        )
    }
}

/**
 * Budget Enable Prompt Card
 * Displays a prompt to enable budget feature
 */
@Composable
fun BudgetEnablePrompt(
    context: android.content.Context,
    onEnableClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AccountBalance,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MiuixTheme.colorScheme.onSecondaryContainer
            )
            
            Text(
                text = context.getString(R.string.budget_enable_title),
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = context.getString(R.string.budget_enable_description),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
            
            Button(
                onClick = onEnableClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary()
            ) {
                Text(context.getString(R.string.budget_enable_button))
            }
        }
    }
}

/**
 * Budget Usage Card
 * Displays budget usage information
 */
@Composable
fun BudgetUsageCard(
    context: android.content.Context,
    usage: com.chronie.homemoney.domain.model.BudgetUsage,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    val status = when {
        usage.isOverLimit -> BudgetStatus.OVER_LIMIT
        usage.isNearLimit -> BudgetStatus.WARNING
        else -> BudgetStatus.NORMAL
    }
    
    val progressColor = when (status) {
        BudgetStatus.OVER_LIMIT -> MiuixTheme.colorScheme.error
        BudgetStatus.WARNING -> MiuixTheme.colorScheme.primaryVariant
        BudgetStatus.NORMAL -> MiuixTheme.colorScheme.primary
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title row (always displayed)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Expanded state summary
                if (!isExpanded) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatMonthLabelByLocale(usage.currentMonth + "-01", context.resources.configuration.locales[0].toLanguageTag()),
                            style = MiuixTheme.textStyles.body2,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = context.getString(R.string.currency_format_no_decimal, context.getString(R.string.currency_symbol), usage.currentSpending) + "/" + context.getString(R.string.currency_format_no_decimal, context.getString(R.string.currency_symbol), usage.monthlyLimit),
                            style = MiuixTheme.textStyles.body2,
                            fontWeight = FontWeight.Bold,
                            color = progressColor
                        )
                        Text(
                            text = "(${String.format(LocalLocale.current.platformLocale, "%.0f", usage.spendingPercentage)}%)",
                            style = MiuixTheme.textStyles.footnote1,
                            color = progressColor
                        )
                    }
                } else {
                    // Expanded state title
                    Column {
                        Text(
                            text = context.getString(R.string.budget_monthly_progress),
                            style = MiuixTheme.textStyles.body1,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatMonthLabelByLocale(usage.currentMonth + "-01", context.resources.configuration.locales[0].toLanguageTag()),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
                
                // Expand/Collapse button and settings button
                Row {
                    IconButton(onClick = { isExpanded = !isExpanded }) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) context.getString(R.string.budget_collapse) else context.getString(R.string.budget_expand)
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = context.getString(R.string.budget_settings)
                        )
                    }
                }
            }
            
            // Expanded state content (expandable/collapsible)
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Expanded state amount info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = context.getString(R.string.currency_format, context.getString(R.string.currency_symbol), usage.currentSpending),
                            style = MiuixTheme.textStyles.title2,
                            fontWeight = FontWeight.Bold,
                            color = progressColor
                        )
                        Text(
                            text = "/ " + context.getString(R.string.currency_format, context.getString(R.string.currency_symbol), usage.monthlyLimit),
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "(${String.format(LocalLocale.current.platformLocale, "%.0f", usage.spendingPercentage)}%)",
                            style = MiuixTheme.textStyles.body2,
                            fontWeight = FontWeight.Medium,
                            color = progressColor
                        )
                    }
                    
                    // Expanded state progress bar
                    ExpressiveLinearProgressIndicator(
                        progress = (usage.spendingPercentage / 100).toFloat().coerceIn(0f, 1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = progressColor,
                        trackColor = MiuixTheme.colorScheme.surfaceVariant
                    )
                    
                    // Status alert
                    when (status) {
                        BudgetStatus.OVER_LIMIT -> {
                            AlertCard(
                                title = context.getString(R.string.budget_alert_over_title),
                                message = context.getString(
                                    R.string.budget_alert_over_message,
                                    context.getString(R.string.currency_format, context.getString(R.string.currency_symbol), usage.currentSpending - usage.monthlyLimit)
                                ),
                                containerColor = MiuixTheme.colorScheme.errorContainer,
                                contentColor = MiuixTheme.colorScheme.onErrorContainer
                            )
                        }
                        BudgetStatus.WARNING -> {
                            AlertCard(
                                title = context.getString(R.string.budget_alert_warning_title),
                                message = context.getString(
                                    R.string.budget_alert_warning_message,
                                    context.getString(R.string.currency_format, context.getString(R.string.currency_symbol), usage.remainingAmount),
                                    usage.spendingPercentage
                                ),
                                containerColor = MiuixTheme.colorScheme.tertiaryContainer,
                                contentColor = MiuixTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        BudgetStatus.NORMAL -> {
                            AlertCard(
                                title = context.getString(R.string.budget_alert_normal_title),
                                message = context.getString(
                                    R.string.budget_alert_normal_message,
                                    context.getString(R.string.currency_format, context.getString(R.string.currency_symbol), usage.remainingAmount),
                                    100 - usage.spendingPercentage
                                ),
                                containerColor = MiuixTheme.colorScheme.secondaryContainer,
                                contentColor = MiuixTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    
                    // Expanded state detailed info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DetailItem(
                            label = context.getString(R.string.budget_daily_average),
                            value = context.getString(R.string.currency_format, context.getString(R.string.currency_symbol), usage.dailyAverage)
                        )
                        
                        DetailItem(
                            label = context.getString(R.string.budget_recommended_daily),
                            value = context.getString(R.string.currency_format, context.getString(R.string.currency_symbol), usage.recommendedDaily),
                            valueColor = if (usage.recommendedDaily <= 0) {
                                MiuixTheme.colorScheme.error
                            } else if (usage.recommendedDaily < usage.dailyAverage * 0.8) {
                                MiuixTheme.colorScheme.primaryVariant
                            } else {
                                MiuixTheme.colorScheme.primary
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Alert Card
 */
@Composable
fun AlertCard(
    title: String,
    message: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Text(
                text = message,
                style = MiuixTheme.textStyles.footnote1,
                color = contentColor.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * Detail Item
 */
@Composable
fun DetailItem(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MiuixTheme.colorScheme.onPrimaryContainer,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Medium,
            color = valueColor
        )
    }
}