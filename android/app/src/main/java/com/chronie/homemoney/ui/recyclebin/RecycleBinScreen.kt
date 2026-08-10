package com.chronie.homemoney.ui.recyclebin

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.chronie.homemoney.R
import com.chronie.homemoney.domain.model.Expense
import com.chronie.homemoney.domain.model.ExpenseType
import com.chronie.homemoney.ui.components.CircularIconButton
import com.chronie.homemoney.ui.scroll.RegisterScrollToTop
import com.chronie.homemoney.ui.expense.ExpenseTypeLocalizer
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.util.concurrent.TimeUnit

/** Days a soft-deleted expense survives in the bin before the worker purges it. */
private const val PURGE_AGE_DAYS = 30L

/**
 * Recycle Bin screen content.
 *
 * Renders soft-deleted expenses grouped by category ("分类显示"), each row
 * showing the deletion date and the days remaining before the 30-day auto-purge.
 * Offers restore and permanent-delete (with confirmation) per item.
 *
 * Intended to be embedded inside the settings [SettingsSubPage] chrome, so it
 * receives [paddingValues] rather than drawing its own top bar.
 */
@Composable
fun RecycleBinScreen(
    context: Context,
    viewModel: RecycleBinViewModel = hiltViewModel(),
    paddingValues: PaddingValues = PaddingValues()
) {
    val deletedExpenses by viewModel.deletedExpenses.collectAsState(initial = emptyList())
    var pendingPermanentDelete by remember { mutableStateOf<Expense?>(null) }

    if (deletedExpenses.isEmpty()) {
        EmptyRecycleBin(context = context, paddingValues = paddingValues)
    } else {
        val listState = rememberLazyListState()
        RegisterScrollToTop(listState)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = paddingValues.calculateTopPadding(),
                end = 16.dp,
                bottom = 24.dp
            )
        ) {
            item {
                // Summary banner — how many items and the auto-purge rule.
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    color = MiuixTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = context.getString(R.string.recycle_bin_summary, deletedExpenses.size, PURGE_AGE_DAYS),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // Group by category so related deletions cluster together.
            deletedExpenses
                .groupBy { it.type }
                .forEach { (type, items) ->
                    item(key = "header_${type.name}") {
                        CategoryHeader(
                            context = context,
                            type = type,
                            count = items.size
                        )
                    }
                    items(
                        items = items,
                        key = { it.id },
                        contentType = { "expense" }
                    ) { expense ->
                        RecycleBinItem(
                            context = context,
                            expense = expense,
                            onRestore = { viewModel.restoreExpense(expense.id) },
                            onDelete = { pendingPermanentDelete = expense }
                        )
                    }
                }
        }
    }

    pendingPermanentDelete?.let { expense ->
        PermanentDeleteDialog(
            context = context,
            expense = expense,
            onConfirm = {
                viewModel.permanentDeleteExpense(expense.id)
                pendingPermanentDelete = null
            },
            onDismiss = { pendingPermanentDelete = null }
        )
    }
}

@Composable
private fun EmptyRecycleBin(context: Context, paddingValues: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.RestoreFromTrash,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = context.getString(R.string.recycle_bin_empty),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }
    }
}

@Composable
private fun CategoryHeader(context: Context, type: ExpenseType, count: Int) {
    Text(
        text = "${ExpenseTypeLocalizer.getLocalizedName(context, type)} · $count",
        style = MiuixTheme.textStyles.body2,
        fontWeight = FontWeight.Bold,
        color = MiuixTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun RecycleBinItem(
    context: Context,
    expense: Expense,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val daysLeft = remember(expense.deletedAt) { daysUntilPurge(expense.deletedAt) }
    val remark = expense.remark

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        color = MiuixTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "-" + context.getString(
                            R.string.currency_format,
                            context.getString(R.string.currency_symbol),
                            expense.amount
                        ),
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = context.getString(R.string.expense_list_sort_date_desc).let {
                            "${expense.date}${if (remark.isNullOrBlank()) "" else " · $remark"}"
                        },
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }

                CircularIconButton(onClick = onRestore) {
                    Icon(
                        Icons.Default.Restore,
                        contentDescription = context.getString(R.string.recycle_bin_restore),
                        tint = MiuixTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                CircularIconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = context.getString(R.string.delete),
                        tint = MiuixTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val purgeText = when (daysLeft) {
                null -> context.getString(R.string.recycle_bin_deleted)
                else -> context.getString(R.string.recycle_bin_expires_in, daysLeft)
            }
            Text(
                text = purgeText,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }
    }
}

@Composable
private fun PermanentDeleteDialog(
    context: Context,
    expense: Expense,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    WindowDialog(
        show = true,
        title = context.getString(R.string.recycle_bin_permanent_delete_title),
        onDismissRequest = onDismiss
    ) {
        Column {
            Text(
                text = context.getString(R.string.recycle_bin_permanent_delete_message),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    text = context.getString(R.string.cancel),
                    onClick = onDismiss
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    text = context.getString(R.string.delete),
                    onClick = onConfirm
                )
            }
        }
    }
}

/**
 * Days remaining before the 30-day auto-purge deletes [deletedAt].
 * Returns null when the deletion timestamp is missing.
 */
private fun daysUntilPurge(deletedAt: Long?): Int? {
    if (deletedAt == null) return null
    val deadline = deletedAt + TimeUnit.DAYS.toMillis(PURGE_AGE_DAYS)
    val remaining = deadline - System.currentTimeMillis()
    return maxOf(0, (remaining / TimeUnit.DAYS.toMillis(1)).toInt())
}
