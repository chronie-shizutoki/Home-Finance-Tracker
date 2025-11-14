package com.chronie.homemoney.ui.main

import android.content.Context
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.chronie.homemoney.R
import com.chronie.homemoney.ui.expense.ExpenseListScreen
import com.chronie.homemoney.ui.settings.SettingsScreen
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding

@Composable
fun MainScreen(
    context: Context,
    shouldRefreshExpenses: Boolean = false,
    onRefreshHandled: () -> Unit = {},
    onNavigateToSettings: () -> Unit,
    onNavigateToDatabaseTest: () -> Unit = {},
    onNavigateToApiTest: () -> Unit = {},
    onNavigateToAddExpense: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel()
) {
    val isDeveloperMode by viewModel.isDeveloperMode.collectAsState(initial = false)
    var showWebView by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    
    if (showWebView) {
        // WebView 界面（原网页功能）
        var webView by remember { mutableStateOf<WebView?>(null) }
        var pageTitle by remember { mutableStateOf("") }
        var canGoBack by remember { mutableStateOf(false) }
        var canGoForward by remember { mutableStateOf(false) }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            // 顶部工具栏
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 离开按钮
                    IconButton(onClick = { showWebView = false }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                    
                    // 页面标题
                    Text(
                        text = pageTitle.ifEmpty { "Loading..." },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // 后退按钮
                    IconButton(
                        onClick = { webView?.goBack() },
                        enabled = canGoBack
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    
                    // 前进按钮
                    IconButton(
                        onClick = { webView?.goForward() },
                        enabled = canGoForward
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Forward"
                        )
                    }
                    
                    // 刷新按钮
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                }
            }
            
            // WebView
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            @Suppress("DEPRECATION")
                            databaseEnabled = true
                            setSupportZoom(true)
                            builtInZoomControls = false
                            displayZoomControls = false
                            loadWithOverviewMode = true
                            useWideViewPort = true
                        }
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                canGoBack = view?.canGoBack() ?: false
                                canGoForward = view?.canGoForward() ?: false
                            }
                        }
                        
                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                super.onReceivedTitle(view, title)
                                pageTitle = title ?: ""
                            }
                        }
                        
                        loadUrl("http://192.168.0.197:3010")
                        webView = this
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    } else {
        // 原生界面（带底部 Tab 栏）
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text(context.getString(R.string.expense_list_title)) },
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.InsertChart, contentDescription = null) },
                        label = { Text(context.getString(R.string.charts_title)) },
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text(context.getString(R.string.settings)) },
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 }
                    )
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                when (selectedTab) {
                    0 -> {
                        // 支出记录界面
                        ExpenseListScreen(
                            context = context,
                            shouldRefresh = shouldRefreshExpenses,
                            onRefreshHandled = onRefreshHandled,
                            onNavigateToMoreFunctions = { showWebView = true },
                            onNavigateToAddExpense = onNavigateToAddExpense
                        )
                    }
                    1 -> {
                        // 图表界面
                        com.chronie.homemoney.ui.charts.ChartsScreen(
                            context = context
                        )
                    }
                    2 -> {
                        // 设置界面
                        SettingsScreen(
                            context = context,
                            onNavigateBack = { selectedTab = 0 },
                            onNavigateToDatabaseTest = onNavigateToDatabaseTest,
                            onNavigateToApiTest = onNavigateToApiTest,
                            onNavigateToWebView = { showWebView = true }
                        )
                    }
                }
            }
        }
    }
}
