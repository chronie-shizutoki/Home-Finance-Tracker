package com.chronie.homemoney.ui.charts

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.chronie.homemoney.R
import com.chronie.homemoney.ui.expense.ExpenseTypeLocalizer
import com.chronie.homemoney.ui.components.CircularIconButton
import com.chronie.homemoney.ui.components.ExpressiveLinearProgressIndicator
import com.chronie.homemoney.ui.components.ExpressiveLoadingIndicator
import java.text.NumberFormat
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@SuppressLint("DefaultStringFormat", "DefaultLocale")
@Composable
fun WeekdayDetailScreen(
    context: Context,
    dayOfWeek: Int,
    amount: Double,
    count: Int,
    percentage: Float,
    onNavigateBack: () -> Unit,
    viewModel: WeekdayDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(java.util.Locale.getDefault()) }
    
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = getWeekdayName(context, dayOfWeek),
                subtitle = context.getString(R.string.expense_details),
                navigationIcon = {
                    CircularIconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.padding(start = 8.dp, end = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = context.getString(R.string.back)
                        )
                    }
                },
                color = MiuixTheme.colorScheme.surface
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Total amount and percentage
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = context.getString(R.string.total_amount),
                            style = MiuixTheme.textStyles.body2
                        )
                        Text(
                            text = currencyFormat.format(amount),
                            style = MiuixTheme.textStyles.body1,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = context.getString(R.string.count),
                            style = MiuixTheme.textStyles.body2
                        )
                        Text(
                            text = "$count ${context.getString(R.string.records)}",
                            style = MiuixTheme.textStyles.body2,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = context.getString(R.string.percentage_of_total),
                            style = MiuixTheme.textStyles.body2
                        )
                        Text(
                            text = "${String.format("%.1f", percentage)}%",
                            style = MiuixTheme.textStyles.body2,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Category breakdown for this weekday
            when (val state = uiState) {
                is WeekdayDetailUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        ExpressiveLoadingIndicator(
                            containerVisible = false
                        )
                    }
                }
                is WeekdayDetailUiState.Success -> {
                    if (state.categoryBreakdown.isNotEmpty()) {
                        Text(
                            text = context.getString(R.string.category_breakdown),
                            style = MiuixTheme.textStyles.body1,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        state.categoryBreakdown.forEach { category ->
                            CategoryDetailItem(context, category, currencyFormat)
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = context.getString(R.string.no_data),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                modifier = Modifier.padding(vertical = 32.dp, horizontal = 16.dp)
                            )
                        }
                    }
                }
                is WeekdayDetailUiState.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = state.message,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(vertical = 32.dp, horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
private fun CategoryDetailItem(
    context: Context,
    category: CategoryChartData,
    currencyFormat: NumberFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = ExpenseTypeLocalizer.getLocalizedTypeName(context, category.type),
                    style = MiuixTheme.textStyles.body2,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${String.format("%.1f", category.percentage)}%",
                    style = MiuixTheme.textStyles.footnote1,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(60.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            ExpressiveLinearProgressIndicator(
                progress = category.percentage / 100f,
                modifier = Modifier.fillMaxWidth(),
                color = MiuixTheme.colorScheme.primary,
                trackColor = MiuixTheme.colorScheme.surfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${category.count} ${context.getString(R.string.records)}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
                Text(
                    text = currencyFormat.format(category.amount),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun getWeekdayName(context: Context, dayOfWeek: Int): String {
    return when (dayOfWeek) {
        0 -> context.getString(R.string.sunday)
        1 -> context.getString(R.string.monday)
        2 -> context.getString(R.string.tuesday)
        3 -> context.getString(R.string.wednesday)
        4 -> context.getString(R.string.thursday)
        5 -> context.getString(R.string.friday)
        6 -> context.getString(R.string.saturday)
        else -> ""
    }
}
