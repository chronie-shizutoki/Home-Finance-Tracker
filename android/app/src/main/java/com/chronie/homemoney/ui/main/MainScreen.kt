package com.chronie.homemoney.ui.main

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.chronie.homemoney.R

@Composable
fun MainScreen(
    context: Context,
    onNavigateToSettings: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // WebView 占满整个屏幕
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
        
        // 设置按钮
        FloatingActionButton(
            onClick = onNavigateToSettings,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
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
