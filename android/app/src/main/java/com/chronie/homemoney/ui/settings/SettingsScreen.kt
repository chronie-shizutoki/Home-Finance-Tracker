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
            
            // AI 设置部分
            Divider()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            AISettingsSection(viewModel = viewModel, context = context)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 预算管理部分
            Divider()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            BudgetSettingsSection(context = context)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 数据同步部分
            Divider()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            SyncSection(viewModel = viewModel, context = context)
            
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

@Composable
fun AISettingsSection(
    viewModel: SettingsViewModel,
    context: Context
) {
    val apiKey by viewModel.aiApiKey.collectAsState()
    var showApiKeyDialog by remember { mutableStateOf(false) }
    
    Column {
        Text(
            text = context.getString(R.string.settings_ai_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showApiKeyDialog = true },
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = context.getString(R.string.settings_ai_api_key),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = context.getString(R.string.settings_ai_api_key_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (apiKey.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "已设置: ${apiKey.take(8)}...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = "→",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }
    }
    
    // API Key 输入对话框
    if (showApiKeyDialog) {
        var inputApiKey by remember { mutableStateOf(apiKey) }
        
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text(context.getString(R.string.settings_ai_api_key)) },
            text = {
                Column {
                    Text(
                        text = context.getString(R.string.settings_ai_api_key_description),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    OutlinedTextField(
                        value = inputApiKey,
                        onValueChange = { inputApiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(context.getString(R.string.settings_ai_api_key_hint)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setAIApiKey(inputApiKey)
                        showApiKeyDialog = false
                    }
                ) {
                    Text(context.getString(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text(context.getString(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun BudgetSettingsSection(
    context: Context,
    budgetViewModel: com.chronie.homemoney.ui.budget.BudgetViewModel = hiltViewModel()
) {
    val uiState by budgetViewModel.uiState.collectAsState()
    var showBudgetDialog by remember { mutableStateOf(false) }
    
    Column {
        Text(
            text = context.getString(R.string.budget_settings),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showBudgetDialog = true },
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = context.getString(R.string.budget_monthly_limit),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = context.getString(R.string.budget_enable_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 显示当前预算状态
                        if (uiState.budget?.isEnabled == true) {
                            Text(
                                text = "${context.getString(R.string.budget_enable_feature)}: ¥${String.format(java.util.Locale.getDefault(), "%.2f", uiState.budget?.monthlyLimit ?: 0.0)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = context.getString(R.string.budget_enable_title),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = "→",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }
    }
    
    // 预算设置对话框
    if (showBudgetDialog) {
        com.chronie.homemoney.ui.budget.BudgetSettingsDialog(
            context = context,
            currentBudget = uiState.budget,
            onDismiss = { showBudgetDialog = false },
            onSave = { limit, threshold, enabled ->
                budgetViewModel.saveBudget(limit, threshold, enabled)
                showBudgetDialog = false
            }
        )
    }
}

@Composable
fun SyncSection(
    viewModel: SettingsViewModel,
    context: Context
) {
    val syncStatus by viewModel.syncStatus.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    
    // 显示同步消息
    syncMessage?.let { message ->
        LaunchedEffect(message) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearSyncMessage()
        }
        
        Snackbar(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(text = message)
        }
    }
    
    Column {
        Text(
            text = context.getString(R.string.sync_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // 同步状态
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = context.getString(R.string.sync_status),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = when (syncStatus) {
                            com.chronie.homemoney.domain.model.SyncStatus.IDLE -> 
                                context.getString(R.string.sync_status_idle)
                            com.chronie.homemoney.domain.model.SyncStatus.SYNCING -> 
                                context.getString(R.string.sync_status_syncing)
                            com.chronie.homemoney.domain.model.SyncStatus.SUCCESS -> 
                                context.getString(R.string.sync_status_success)
                            com.chronie.homemoney.domain.model.SyncStatus.FAILED -> 
                                context.getString(R.string.sync_status_failed)
                            com.chronie.homemoney.domain.model.SyncStatus.CONFLICT -> 
                                context.getString(R.string.sync_status_conflict)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (syncStatus) {
                            com.chronie.homemoney.domain.model.SyncStatus.SUCCESS -> 
                                MaterialTheme.colorScheme.primary
                            com.chronie.homemoney.domain.model.SyncStatus.FAILED,
                            com.chronie.homemoney.domain.model.SyncStatus.CONFLICT -> 
                                MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 最后同步时间
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = context.getString(R.string.sync_last_time),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = lastSyncTime ?: context.getString(R.string.sync_never),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 待同步项数量
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = context.getString(R.string.sync_pending_count),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = pendingSyncCount.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (pendingSyncCount > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 手动同步按钮
                Button(
                    onClick = { viewModel.manualSync() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = syncStatus != com.chronie.homemoney.domain.model.SyncStatus.SYNCING
                ) {
                    if (syncStatus == com.chronie.homemoney.domain.model.SyncStatus.SYNCING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (syncStatus == com.chronie.homemoney.domain.model.SyncStatus.SYNCING) {
                            context.getString(R.string.sync_syncing)
                        } else {
                            context.getString(R.string.sync_manual_trigger)
                        }
                    )
                }
            }
        }
    }
}
