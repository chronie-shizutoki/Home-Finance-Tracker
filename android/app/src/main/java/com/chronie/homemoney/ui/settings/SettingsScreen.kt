package com.chronie.homemoney.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.chronie.homemoney.R
import com.chronie.homemoney.ui.components.*
import com.chronie.homemoney.ui.expense.formatDateByLocale
import com.chronie.homemoney.ui.theme.LocalThemeSettings
import com.chronie.homemoney.ui.theme.ThemeSettings
import com.yalantis.ucrop.UCrop
import java.io.File
import java.time.LocalDate

enum class SettingsPage {
    MAIN, ACCOUNT, APPEARANCE, FEATURES, DATA_SYNC, ABOUT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    context: Context,
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToDatabaseTest: () -> Unit = {},
    onNavigateToLanSync: () -> Unit = {},
    onNavigateToOpenSourceLicenses: () -> Unit = {},
    onLogout: () -> Unit = {},
    onRequireLogin: () -> Unit = {}
) {
    var currentPage by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }

    // 处理系统返回键
    BackHandler(enabled = currentPage != SettingsPage.MAIN) {
        currentPage = SettingsPage.MAIN
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部标题栏
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentPage != SettingsPage.MAIN) {
                    CircularIconButton(
                        onClick = { currentPage = SettingsPage.MAIN },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                } else {
                    Spacer(modifier = Modifier.width(12.dp))
                }
                
                Text(
                    text = when (currentPage) {
                        SettingsPage.MAIN -> context.getString(R.string.settings)
                        SettingsPage.ACCOUNT -> context.getString(R.string.auth_account_info)
                        SettingsPage.APPEARANCE -> context.getString(R.string.theme_settings)
                        SettingsPage.FEATURES -> context.getString(R.string.budget_settings)
                        SettingsPage.DATA_SYNC -> context.getString(R.string.sync_title)
                        SettingsPage.ABOUT -> context.getString(R.string.common_more_functions)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        AnimatedContent(
            targetState = currentPage,
            transitionSpec = {
                if (targetState != SettingsPage.MAIN) {
                    // 进入二级页面：从右往左进，带有渐变
                    (slideInHorizontally { it } + fadeIn()) togetherWith 
                    (slideOutHorizontally { -it / 2 } + fadeOut())
                } else {
                    // 返回主页面：从左往右回，带有渐变
                    (slideInHorizontally { -it / 2 } + fadeIn()) togetherWith 
                    (slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "SettingsPageTransition"
        ) { page ->
            when (page) {
                SettingsPage.MAIN -> MainSettingsMenu(
                    viewModel = viewModel,
                    context = context,
                    onNavigate = { currentPage = it }
                )
                SettingsPage.ACCOUNT -> AccountSettingsPage(
                    viewModel = viewModel,
                    context = context,
                    onLogout = onLogout,
                    onRequireLogin = onRequireLogin
                )
                SettingsPage.APPEARANCE -> AppearanceSettingsPage(
                    viewModel = viewModel,
                    context = context
                )
                SettingsPage.FEATURES -> FeaturesSettingsPage(
                    viewModel = viewModel,
                    context = context
                )
                SettingsPage.DATA_SYNC -> DataSyncSettingsPage(
                    viewModel = viewModel,
                    context = context,
                    onNavigateToLanSync = onNavigateToLanSync
                )
                SettingsPage.ABOUT -> AboutSettingsPage(
                    viewModel = viewModel,
                    context = context,
                    onNavigateToOpenSourceLicenses = onNavigateToOpenSourceLicenses,
                    onNavigateToDatabaseTest = onNavigateToDatabaseTest
                )
            }
        }
    }
}

@Composable
fun MainSettingsMenu(
    viewModel: SettingsViewModel,
    context: Context,
    onNavigate: (SettingsPage) -> Unit
) {
    val currentUsername by viewModel.currentUsername.collectAsState()
    val avatar by viewModel.avatar.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // 用户简要信息入口
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigate(SettingsPage.ACCOUNT) },
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            shape = MaterialTheme.shapes.large
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 小头像
                Box(modifier = Modifier.size(60.dp)) {
                    if (avatar != null) {
                        AsyncImage(
                            model = avatar,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentUsername ?: context.getString(R.string.auth_not_logged_in),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = context.getString(R.string.auth_account_info),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 设置分类列表
        SettingsCategoryItem(
            title = context.getString(R.string.theme_settings),
            description = context.getString(R.string.language_settings),
            icon = Icons.Default.Palette,
            onClick = { onNavigate(SettingsPage.APPEARANCE) }
        )
        
        SettingsCategoryItem(
            title = "Features", // 或者使用 R.string.budget_settings
            description = context.getString(R.string.settings_ai_title) + ", " + context.getString(R.string.budget_settings),
            icon = Icons.Default.AutoAwesome,
            onClick = { onNavigate(SettingsPage.FEATURES) }
        )
        
        SettingsCategoryItem(
            title = context.getString(R.string.sync_title),
            description = context.getString(R.string.data_import_export),
            icon = Icons.Default.Sync,
            onClick = { onNavigate(SettingsPage.DATA_SYNC) }
        )
        
        SettingsCategoryItem(
            title = context.getString(R.string.common_more_functions),
            description = context.getString(R.string.feedback_title) + ", " + context.getString(R.string.open_source_licenses),
            icon = Icons.Default.Info,
            onClick = { onNavigate(SettingsPage.ABOUT) }
        )
    }
}

@Composable
fun SettingsCategoryItem(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// --- 子页面组件 ---

@Composable
fun AccountSettingsPage(
    viewModel: SettingsViewModel,
    context: Context,
    onLogout: () -> Unit,
    onRequireLogin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        AccountSection(
            viewModel = viewModel,
            context = context,
            onLogout = onLogout,
            onRequireLogin = onRequireLogin
        )
    }
}

@Composable
fun AppearanceSettingsPage(
    viewModel: SettingsViewModel,
    context: Context
) {
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val useDynamicColor by viewModel.useDynamicColor.collectAsState()
    var showLanguageBottomSheet by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    val themeSettings = LocalThemeSettings.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = context.getString(R.string.language_settings),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        SettingsDetailItem(
            title = context.getString(R.string.select_language),
            subtitle = currentLanguage.localName,
            onClick = {
                showLanguageBottomSheet = true
                openSystemAppLanguageSettings(context)
            }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = context.getString(R.string.theme_settings),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = context.getString(R.string.dynamic_color), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = context.getString(R.string.dynamic_color_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ExpressiveSwitch(
                    checked = useDynamicColor,
                    onCheckedChange = { enabled ->
                        themeSettings.value = ThemeSettings(
                            useDynamicColor = enabled,
                            primaryColor = themeSettings.value.primaryColor,
                            paletteStyle = themeSettings.value.paletteStyle
                        )
                        viewModel.toggleDynamicColor(enabled)
                    }
                )
            }
        }
        
        if (!useDynamicColor) {
            Spacer(modifier = Modifier.height(12.dp))
            SettingsDetailItem(
                title = context.getString(R.string.manual_color_selection),
                subtitle = context.getString(R.string.current_theme_color),
                onClick = { showColorPicker = true },
                trailingContent = {
                    Surface(
                        shape = CircleShape,
                        color = Color(themeSettings.value.primaryColor),
                        modifier = Modifier.size(24.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {}
                }
            )
        }

        // Bottom Sheets
        if (showLanguageBottomSheet) {
            LanguageSelectorBottomSheet(
                currentLanguage = currentLanguage,
                onLanguageSelected = { viewModel.setLanguage(it) },
                onDismiss = { showLanguageBottomSheet = false },
                context = context
            )
        }
        
        if (showColorPicker) {
            ColorPickerBottomSheet(
                currentColor = themeSettings.value.primaryColor,
                onColorSelected = { color ->
                    themeSettings.value = ThemeSettings(
                        useDynamicColor = false,
                        primaryColor = color,
                        paletteStyle = themeSettings.value.paletteStyle
                    )
                    viewModel.setPrimaryColor(color)
                },
                onDismiss = { showColorPicker = false },
                context = context
            )
        }
    }
}

@Composable
fun FeaturesSettingsPage(
    viewModel: SettingsViewModel,
    context: Context
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        AISettingsSection(viewModel = viewModel, context = context)
        Spacer(modifier = Modifier.height(24.dp))
        BudgetSettingsSection(context = context)
    }
}

@Composable
fun DataSyncSettingsPage(
    viewModel: SettingsViewModel,
    context: Context,
    onNavigateToLanSync: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SyncSection(
            viewModel = viewModel,
            context = context,
            onNavigateToLanSync = onNavigateToLanSync
        )
        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))
        DataImportExportSection(viewModel = viewModel, context = context)
    }
}

@Composable
fun AboutSettingsPage(
    viewModel: SettingsViewModel,
    context: Context,
    onNavigateToOpenSourceLicenses: () -> Unit,
    onNavigateToDatabaseTest: () -> Unit
) {
    val isDeveloperMode by viewModel.isDeveloperMode.collectAsState(initial = false)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = context.getString(R.string.feedback_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        SettingsDetailItem(
            title = context.getString(R.string.feedback_title),
            subtitle = context.getString(R.string.feedback_description),
            onClick = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wj.qq.com/s2/24109109/3572/"))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Browser Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = context.getString(R.string.open_source_licenses),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        SettingsDetailItem(
            title = context.getString(R.string.open_source_licenses),
            subtitle = context.getString(R.string.open_source_licenses_description),
            onClick = onNavigateToOpenSourceLicenses
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 开发者选项
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = context.getString(R.string.developer_options),
                style = MaterialTheme.typography.titleMedium
            )
            ExpressiveSwitch(
                checked = isDeveloperMode,
                onCheckedChange = { viewModel.toggleDeveloperMode() }
            )
        }
        
        if (isDeveloperMode) {
            Spacer(modifier = Modifier.height(8.dp))
            SettingsDetailItem(
                title = context.getString(R.string.database_test),
                subtitle = "Local database debugging",
                onClick = onNavigateToDatabaseTest
            )
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        AppVersionInfo(context = context)
        
        // 预留底部空间，避免被悬浮底栏遮挡
        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun SettingsDetailItem(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (trailingContent != null) {
                trailingContent()
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// --- 原始 UI 组件保持或微调 ---

@Composable
fun AppVersionInfo(context: Context) {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val versionName = packageInfo.versionName
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode.toLong()
    }
    
    Text(
        text = "Version $versionName ($versionCode)",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp)
            .wrapContentWidth(Alignment.CenterHorizontally)
    )
}

fun openSystemAppLanguageSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        try {
            val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openAppInfoSettings(context)
        }
    }
}

fun openAppInfoSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to open settings", Toast.LENGTH_SHORT).show()
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
        
        SettingsDetailItem(
            title = context.getString(R.string.settings_ai_api_key),
            subtitle = if (apiKey.isNotEmpty()) context.getString(R.string.api_key_set, apiKey.take(8)) else context.getString(R.string.settings_ai_api_key_description),
            onClick = { showApiKeyDialog = true }
        )
    }
    
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
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = context.getString(R.string.settings_ai_get_api_key),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://cloud.siliconflow.cn/me/account/ak"))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Browser Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setAIApiKey(inputApiKey)
                    showApiKeyDialog = false
                }) {
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
        
        SettingsDetailItem(
            title = context.getString(R.string.budget_monthly_limit),
            subtitle = if (uiState.budget?.isEnabled == true) {
                "${context.getString(R.string.budget_enable_feature)}: " + context.getString(R.string.currency_format, context.getString(R.string.currency_symbol), uiState.budget?.monthlyLimit ?: 0.0)
            } else {
                context.getString(R.string.budget_enable_title)
            },
            onClick = { showBudgetDialog = true }
        )
    }
    
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataImportExportSection(
    viewModel: SettingsViewModel,
    context: Context
) {
    val exportInProgress by viewModel.exportInProgress.collectAsState()
    val importInProgress by viewModel.importInProgress.collectAsState()
    var showExportDialog by remember { mutableStateOf(false) }
    var showDateRangeDialog by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf<LocalDate?>(null) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (!permissions.values.all { it }) {
            Toast.makeText(context, context.getString(R.string.permission_storage_required), Toast.LENGTH_LONG).show()
        }
    }
    
    fun checkAndRequestPermissions(onGranted: () -> Unit) {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        val allGranted = permissions.all { androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (allGranted) onGranted() else permissionLauncher.launch(permissions)
    }
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.importExpenses(it) } }
    
    Column {
        Text(
            text = context.getString(R.string.data_import_export),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Button(
            onClick = { checkAndRequestPermissions { showExportDialog = true } },
            modifier = Modifier.fillMaxWidth(),
            enabled = !exportInProgress && !importInProgress
        ) {
            if (exportInProgress) {
                ExpressiveLoadingIndicator(size = 20.dp, containerVisible = false)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = if (exportInProgress) context.getString(R.string.export_in_progress) else context.getString(R.string.export_data))
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(
            onClick = { checkAndRequestPermissions { filePickerLauncher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") } },
            modifier = Modifier.fillMaxWidth(),
            enabled = !exportInProgress && !importInProgress
        ) {
            if (importInProgress) {
                ExpressiveLoadingIndicator(size = 20.dp, containerVisible = false)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = if (importInProgress) context.getString(R.string.import_in_progress) else context.getString(R.string.import_data))
        }
    }
    
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(context.getString(R.string.export_data)) },
            text = {
                Column {
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { showExportDialog = false; viewModel.exportExpenses(null, null) },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    ) { Text(text = context.getString(R.string.export_all_data), modifier = Modifier.padding(16.dp)) }
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { showExportDialog = false; showDateRangeDialog = true },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    ) { Text(text = context.getString(R.string.export_date_range), modifier = Modifier.padding(16.dp)) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text(context.getString(R.string.cancel)) } }
        )
    }

    if (showDateRangeDialog) {
        var showStartDatePicker by remember { mutableStateOf(false) }
        var showEndDatePicker by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showDateRangeDialog = false },
            title = { Text(context.getString(R.string.export_select_range)) },
            text = {
                Column {
                    Text(text = context.getString(R.string.export_start_date), modifier = Modifier.padding(bottom = 4.dp))
                    Surface(modifier = Modifier.fillMaxWidth().clickable { showStartDatePicker = true }, color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                        Text(text = startDate?.let { formatDateByLocale(it.toString(), context.resources.configuration.locale.toLanguageTag()) } ?: context.getString(R.string.export_start_date), modifier = Modifier.padding(16.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = context.getString(R.string.export_end_date), modifier = Modifier.padding(bottom = 4.dp))
                    Surface(modifier = Modifier.fillMaxWidth().clickable { showEndDatePicker = true }, color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                        Text(text = endDate?.let { formatDateByLocale(it.toString(), context.resources.configuration.locale.toLanguageTag()) } ?: context.getString(R.string.export_end_date), modifier = Modifier.padding(16.dp))
                    }
                }
                if (showStartDatePicker) {
                    val datePickerState = rememberDatePickerState()
                    DatePickerDialog(onDismissRequest = { showStartDatePicker = false }, confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { startDate = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }; showStartDatePicker = false }) { Text(context.getString(R.string.confirm)) } }) { DatePicker(state = datePickerState) }
                }
                if (showEndDatePicker) {
                    val datePickerState = rememberDatePickerState()
                    DatePickerDialog(onDismissRequest = { showEndDatePicker = false }, confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { endDate = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }; showEndDatePicker = false }) { Text(context.getString(R.string.confirm)) } }) { DatePicker(state = datePickerState) }
                }
            },
            confirmButton = { TextButton(onClick = { showDateRangeDialog = false; viewModel.exportExpenses(startDate, endDate) }, enabled = startDate != null && endDate != null) { Text(context.getString(R.string.export_data)) } },
            dismissButton = { TextButton(onClick = { showDateRangeDialog = false }) { Text(context.getString(R.string.cancel)) } }
        )
    }
}

@Composable
fun SyncSection(
    viewModel: SettingsViewModel,
    context: Context,
    onNavigateToLanSync: () -> Unit = {}
) {
    val syncStatus by viewModel.syncStatus.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    var showSyncMethodDialog by remember { mutableStateOf(false) }
    
    syncMessage?.let { message ->
        LaunchedEffect(message) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            kotlinx.coroutines.delay(3000)
            viewModel.clearSyncMessage()
        }
    }
    
    Column {
        Text(text = context.getString(R.string.sync_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = MaterialTheme.shapes.medium) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = context.getString(R.string.sync_status))
                    Text(
                        text = when (syncStatus) {
                            com.chronie.homemoney.domain.model.SyncStatus.IDLE -> context.getString(R.string.sync_status_idle)
                            com.chronie.homemoney.domain.model.SyncStatus.SYNCING -> context.getString(R.string.sync_status_syncing)
                            com.chronie.homemoney.domain.model.SyncStatus.SUCCESS -> context.getString(R.string.sync_status_success)
                            com.chronie.homemoney.domain.model.SyncStatus.FAILED -> context.getString(R.string.sync_status_failed)
                            com.chronie.homemoney.domain.model.SyncStatus.CONFLICT -> context.getString(R.string.sync_status_conflict)
                        },
                        color = when (syncStatus) {
                            com.chronie.homemoney.domain.model.SyncStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                            com.chronie.homemoney.domain.model.SyncStatus.FAILED, com.chronie.homemoney.domain.model.SyncStatus.CONFLICT -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = context.getString(R.string.sync_last_time))
                    Text(text = lastSyncTime ?: context.getString(R.string.sync_never), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = context.getString(R.string.sync_pending_count))
                    Text(
                        text = pendingSyncCount.toString(),
                        color = if (pendingSyncCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { showSyncMethodDialog = true }, modifier = Modifier.fillMaxWidth(), enabled = syncStatus != com.chronie.homemoney.domain.model.SyncStatus.SYNCING) {
                    if (syncStatus == com.chronie.homemoney.domain.model.SyncStatus.SYNCING) {
                        ExpressiveLoadingIndicator(size = 20.dp, containerVisible = false)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(text = if (syncStatus == com.chronie.homemoney.domain.model.SyncStatus.SYNCING) context.getString(R.string.sync_syncing) else context.getString(R.string.sync_manual_trigger))
                }
            }
        }
    }
    
    if (showSyncMethodDialog) {
        AlertDialog(
            onDismissRequest = { showSyncMethodDialog = false },
            title = { Text(context.getString(R.string.sync_select_method)) },
            text = {
                Column {
                    Surface(modifier = Modifier.fillMaxWidth().clickable { showSyncMethodDialog = false; viewModel.manualSync() }, color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                        Text(text = context.getString(R.string.sync_cloud), modifier = Modifier.padding(16.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(modifier = Modifier.fillMaxWidth().clickable { showSyncMethodDialog = false; onNavigateToLanSync() }, color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                        Text(text = context.getString(R.string.sync_lan), modifier = Modifier.padding(16.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSyncMethodDialog = false }) { Text(context.getString(R.string.cancel)) } }
        )
    }
}

@Composable
fun AccountSection(
    viewModel: SettingsViewModel,
    context: Context,
    onLogout: () -> Unit,
    onRequireLogin: () -> Unit = {}
) {
    val currentUsername by viewModel.currentUsername.collectAsState()
    val avatar by viewModel.avatar.collectAsState()
    val avatarLoading by viewModel.avatarLoading.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.logoutEvent.collect { onLogout() } }

    val cropLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            UCrop.getOutput(result.data ?: Intent())?.let { uri ->
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                        val byteArrayOutputStream = java.io.ByteArrayOutputStream()
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
                        val base64String = android.util.Base64.encodeToString(byteArrayOutputStream.toByteArray(), android.util.Base64.DEFAULT)
                        viewModel.updateAvatar("data:image/png;base64,$base64String")
                        File(uri.path ?: "").delete()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, context.getString(R.string.crop_image_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val outputUri = Uri.fromFile(File(context.cacheDir, "cropped_avatar_${System.currentTimeMillis()}.png"))
            val options = UCrop.Options().apply {
                setCircleDimmedLayer(true)
                setToolbarColor(android.graphics.Color.parseColor("#6750A4"))
                setToolbarWidgetColor(android.graphics.Color.WHITE)
                setShowCropGrid(false)
            }
            cropLauncher.launch(UCrop.of(it, outputUri).withAspectRatio(1f, 1f).withMaxResultSize(256, 256).withOptions(options).getIntent(context))
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.padding(bottom = 20.dp), contentAlignment = Alignment.Center) {
                Surface(modifier = Modifier.size(140.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), shadowElevation = 4.dp) {}
                Surface(modifier = Modifier.size(132.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))) {}
                Box(modifier = Modifier.size(120.dp).clickable { imagePickerLauncher.launch("image/*") }) {
                    if (avatarLoading) ExpressiveLoadingIndicator(size = 120.dp, containerVisible = false)
                    else if (avatar != null) {
                        AsyncImage(model = avatar, contentDescription = null, modifier = Modifier.size(120.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    } else {
                        Surface(modifier = Modifier.size(120.dp), color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
            Text(text = currentUsername ?: context.getString(R.string.auth_not_logged_in), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            Text(text = context.getString(R.string.auth_current_user), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            Spacer(modifier = Modifier.height(20.dp))
            if (currentUsername != null) {
                FilledTonalButton(onClick = { showLogoutDialog = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(context.getString(R.string.auth_logout_button))
                }
            } else {
                Button(onClick = { viewModel.clearSkippedLogin(); onRequireLogin() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(context.getString(R.string.auth_login_button))
                }
            }
        }
    }
    
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(context.getString(R.string.auth_logout_confirm_title)) },
            text = { Text(context.getString(R.string.auth_logout_confirm_message)) },
            confirmButton = { TextButton(onClick = { viewModel.logout(); showLogoutDialog = false }) { Text(text = context.getString(R.string.confirm), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text(context.getString(R.string.cancel)) } }
        )
    }
}
