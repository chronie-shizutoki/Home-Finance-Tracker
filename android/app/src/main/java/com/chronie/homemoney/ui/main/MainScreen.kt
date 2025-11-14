package com.chronie.homemoney.ui.main

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.chronie.homemoney.R
import com.chronie.homemoney.ui.expense.ExpenseListScreen

@Composable
fun MainScreen(
    context: Context,
    onNavigateToSettings: () -> Unit,
    onNavigateToDatabaseTest: () -> Unit = {},
    onNavigateToApiTest: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel()
) {
    val isDeveloperMode by viewModel.isDeveloperMode.collectAsState(initial = false)
    var showWebView by remember { mutableStateOf(false) }
    
    if (showWebView) {
        // WebView 界面（原网页功能）
        Box(modifier = Modifier.fillMaxSize()) {
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
                        webViewClient = WebViewClient()
                        loadUrl("http://192.168.0.197:3010")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // 返回按钮
            FloatingActionButton(
                onClick = { showWebView = false },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Text(context.getString(R.string.back))
            }
            
            // 按钮组
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // API测试按钮 (仅开发者模式显示)
                if (isDeveloperMode) {
                    FloatingActionButton(
                        onClick = onNavigateToApiTest,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ) {
                        Text("API")
                    }
                }
                
                // 数据库测试按钮 (仅开发者模式显示)
                if (isDeveloperMode) {
                    FloatingActionButton(
                        onClick = onNavigateToDatabaseTest,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Text("DB")
                    }
                }
                
                // 设置按钮
                FloatingActionButton(
                    onClick = onNavigateToSettings,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = context.getString(R.string.language_settings)
                    )
                }
            }
        }
    } else {
        // 原生支出列表界面（新的主界面）
        ExpenseListScreen(
            onNavigateToMoreFunctions = { showWebView = true }
        )
    }
}
