package com.chronie.homemoney.ui.main

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chronie.homemoney.R
import com.chronie.homemoney.ui.expense.ExpenseListScreen
import com.chronie.homemoney.ui.settings.SettingsScreen
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.Settings
import com.chronie.homemoney.ui.main.components.FloatingBottomBar
import com.chronie.homemoney.ui.main.components.FloatingBottomBarDefaults
import com.chronie.homemoney.ui.main.components.FloatingBottomBarItem
import com.chronie.homemoney.ui.main.components.FloatingBottomBarMode
import com.chronie.homemoney.ui.main.components.LocalFloatingBottomBarContentColor

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
    val backdrop = rememberLayerBackdrop()
    Box(modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    scaleIn(
                        initialScale = 0.9f,
                        animationSpec = spring(stiffness = 300f, dampingRatio = 0.7f)
                    ) + fadeIn(animationSpec = spring(stiffness = 300f, dampingRatio = 0.7f)) togetherWith
                            scaleOut(
                                targetScale = 0.9f,
                                animationSpec = spring(stiffness = 300f, dampingRatio = 0.7f)
                            ) + fadeOut(animationSpec = spring(stiffness = 300f, dampingRatio = 0.7f))
                },
                contentAlignment = Alignment.Center
            ) { tab ->
                when (tab) {
                    0 -> {
                        ExpenseListScreen(
                            context = context,
                            shouldRefresh = shouldRefreshExpenses,
                            onRefreshHandled = onRefreshHandled,
                            onNavigateToAddExpense = onNavigateToAddExpense,
                            onNavigateToEditExpense = onNavigateToEditExpense
                        )
                    }
                    1 -> {
                        com.chronie.homemoney.ui.charts.ChartsScreen(
                            context = context,
                            onNavigateToWeekdayDetail = onNavigateToWeekdayDetail
                        )
                    }
                    2 -> {
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
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            FloatingBottomBar(
                selectedIndex = { selectedTab },
                onSelected = onTabChange,
                backdrop = backdrop,
                tabsCount = 3,
                mode = FloatingBottomBarMode.LiquidGlass,
                colors = FloatingBottomBarDefaults.colors(
                    containerColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f),
                    indicatorColor = MiuixTheme.colorScheme.primary,
                    contentColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    activeContentColor = MiuixTheme.colorScheme.primary
                )
            ) {
                FloatingBottomBarItem(onClick = { onTabChange(0) }) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = context.getString(R.string.expense_list_title),
                        style = MiuixTheme.textStyles.footnote2,
                        color = LocalFloatingBottomBarContentColor.current
                    )
                }
                FloatingBottomBarItem(onClick = { onTabChange(1) }) {
                    Icon(
                        imageVector = Icons.Default.InsertChart,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = context.getString(R.string.charts_title),
                        style = MiuixTheme.textStyles.footnote2,
                        color = LocalFloatingBottomBarContentColor.current
                    )
                }
                FloatingBottomBarItem(onClick = { onTabChange(2) }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = context.getString(R.string.settings),
                        style = MiuixTheme.textStyles.footnote2,
                        color = LocalFloatingBottomBarContentColor.current
                    )
                }
            }
        }
    }
}