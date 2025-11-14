package com.chronie.homemoney.ui.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chronie.homemoney.R
import com.chronie.homemoney.core.common.Language

@Composable
fun SettingsScreen(
    context: Context,
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToDatabaseTest: () -> Unit = {},
    onNavigateToApiTest: () -> Unit = {},
    onNavigateToWebView: () -> Unit = {}
) {
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val scrollState = androidx.compose.foundation.rememberScrollState()
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 顶部标题栏
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = context.getString(R.string.settings),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
        
        // 可滚动内容
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            Text(
                text = context.getString(R.string.select_language),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Language.values().forEach { language ->
                LanguageItem(
                    language = language,
                    isSelected = language == currentLanguage,
                    onClick = { viewModel.setLanguage(language) }
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 网页版入口
            Divider()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = context.getString(R.string.settings_webview_entry),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToWebView),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = context.getString(R.string.settings_webview_entry),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = context.getString(R.string.settings_webview_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "→",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 开发者模式开关
            val isDeveloperMode by viewModel.isDeveloperMode.collectAsState(initial = false)
            
            Divider()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = context.getString(R.string.developer_options),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = context.getString(R.string.developer_mode),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = context.getString(R.string.developer_mode_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isDeveloperMode,
                        onCheckedChange = { viewModel.toggleDeveloperMode() }
                    )
                }
            }
            
            // 开发者工具（仅在开发者模式下显示）
            if (isDeveloperMode) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = context.getString(R.string.developer_tools),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // 数据库测试按钮
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToDatabaseTest),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = context.getString(R.string.database_test),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "→",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // API 测试按钮
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToApiTest),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = context.getString(R.string.api_test),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "→",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageItem(
    language: Language,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.displayName,
                style = MaterialTheme.typography.bodyLarge
            )
            if (isSelected) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
