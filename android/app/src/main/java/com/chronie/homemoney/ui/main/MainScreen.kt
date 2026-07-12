package com.chronie.homemoney.ui.main

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.chronie.homemoney.ui.expense.ExpenseListScreen
import com.chronie.homemoney.ui.settings.SettingsScreen
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    context: Context,
    shouldRefreshExpenses: Boolean = false,
    onRefreshHandled: () -> Unit = {},
    selectedTab: Int = 0,
    onTabChange: (Int) -> Unit = {},
    onNavigateToDatabaseTest: () -> Unit = {},
    onNavigateToAddExpense: () -> Unit = {},
    onNavigateToEditExpense: (expenseId: String) -> Unit = {},
    onNavigateToWeekdayDetail: (dayOfWeek: Int, amount: Double, count: Int, percentage: Float, startDate: String, endDate: String) -> Unit = { _, _, _, _, _, _ -> },
    onNavigateToLanSync: () -> Unit = {},
    onNavigateToOpenSourceLicenses: () -> Unit = {},
    onRequireLogin: () -> Unit = {}
) {

    // Native screen (with bottom navigation bar)
    Box(modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            when (selectedTab) {
                0 -> {
                    // Expense list screen
                    ExpenseListScreen(
                        context = context,
                        shouldRefresh = shouldRefreshExpenses,
                        onRefreshHandled = onRefreshHandled,
                        onNavigateToAddExpense = onNavigateToAddExpense,
                        onNavigateToEditExpense = onNavigateToEditExpense
                    )
                }
                1 -> {
                    // Chart screen
                    com.chronie.homemoney.ui.charts.ChartsScreen(
                        context = context,
                        onNavigateToWeekdayDetail = onNavigateToWeekdayDetail
                    )
                }
                2 -> {
                    // Settings screen
                    SettingsScreen(
                        context = context,
                        onNavigateToDatabaseTest = onNavigateToDatabaseTest,
                        onNavigateToLanSync = onNavigateToLanSync,
                        onNavigateToOpenSourceLicenses = onNavigateToOpenSourceLicenses,
                        onLogout = {
                            android.util.Log.d("MainScreen", "Received onLogout callback")
                            onRequireLogin()
                        },
                        onRequireLogin = onRequireLogin
                    )
                }
            }
        }

        // Floating navigation bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            BottomNavigationBar(
                context = context,
                selectedTab = selectedTab,
                onTabChange = onTabChange
            )
        }
    }
}
