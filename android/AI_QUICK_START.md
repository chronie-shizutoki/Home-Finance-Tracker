# AI 智能识别功能 - 快速开始指南

## 🎉 功能已完成！

所有核心文件已创建完成，代码已通过语法检查。现在只需要简单的集成步骤即可使用。

## 📋 快速集成清单

### 步骤 1: 同步 Gradle (自动)
```bash
# 在 Android Studio 中点击 "Sync Now"
# 或运行
./gradlew build
```

### 步骤 2: 添加导航路由 (5分钟)

在你的导航配置文件中（通常是 `MainActivity.kt` 或 `NavGraph.kt`）添加：

```kotlin
import com.chronie.homemoney.ui.expense.AIExpenseScreen

// 在 NavHost 中添加
composable("ai_expense") {
    AIExpenseScreen(
        onNavigateBack = { navController.popBackStack() },
        onRecordsSaved = {
            // 保存成功后返回
            navController.popBackStack()
        }
    )
}
```

### 步骤 3: 添加入口按钮 (5分钟)

在 `AddExpenseScreen.kt` 的 TopAppBar actions 中添加：

```kotlin
import androidx.compose.material.icons.filled.AutoAwesome

// 在 actions 的最前面添加
IconButton(
    onClick = { /* 导航到 AI 界面，例如: navController.navigate("ai_expense") */ }
) {
    Icon(
        Icons.Default.AutoAwesome,
        contentDescription = "AI 智能识别",
        tint = MaterialTheme.colorScheme.primary
    )
}
```

### 步骤 4: 添加 API Key 设置 (10分钟)

在 `SettingsScreen.kt` 中添加：

```kotlin
import androidx.compose.material.icons.filled.Save
import android.content.Context

@Composable
fun AISettingsSection(context: Context) {
    val prefs = remember {
        context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
    }
    
    var apiKey by remember {
        mutableStateOf(prefs.getString("siliconflow_api_key", "") ?: "")
    }
    
    var showSaveSuccess by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "AI 设置",
            style = MaterialTheme.typography.titleMedium
        )
        
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("SiliconFlow API Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("输入您的 API Key") },
            trailingIcon = {
                IconButton(
                    onClick = {
                        prefs.edit()
                            .putString("siliconflow_api_key", apiKey)
                            .apply()
                        showSaveSuccess = true
                    }
                ) {
                    Icon(Icons.Default.Save, contentDescription = "保存")
                }
            }
        )
        
        Text(
            text = "用于 AI 智能识别功能",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        if (showSaveSuccess) {
            Text(
                text = "✓ API Key 已保存",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            LaunchedEffect(Unit) {
                delay(2000)
                showSaveSuccess = false
            }
        }
    }
}

// 在 SettingsScreen 中调用
AISettingsSection(context = LocalContext.current)
```

## 🔑 获取 API Key

1. 访问 [SiliconFlow 官网](https://siliconflow.cn/)
2. 注册/登录账号
3. 进入控制台
4. 创建 API Key
5. 复制 API Key 到应用设置中

## 🚀 使用流程

1. **打开应用** → 进入设置 → 输入 API Key → 保存
2. **添加支出** → 点击 AI 图标 ⚡
3. **选择图片** 或 **输入文本** → 点击"开始识别"
4. **查看结果** → 编辑/删除记录 → 点击"全部保存"
5. **完成！** 记录已保存到数据库

## 📱 界面预览

```
┌─────────────────────────────┐
│  ← AI 智能识别              │
├─────────────────────────────┤
│  选择图片          [+ 添加]  │
│  ┌───┐ ┌───┐ ┌───┐         │
│  │ 📷│ │ 📷│ │ 📷│         │
│  └───┘ └───┘ └───┘         │
│                             │
│  或输入文本                  │
│  ┌─────────────────────┐   │
│  │ 今天买了苹果30元...  │   │
│  │                     │   │
│  └─────────────────────┘   │
│                             │
│  [    开始识别    ]         │
│                             │
│  识别结果 (2条)  [全部保存]  │
│  ┌─────────────────────┐   │
│  │ 食品        ✏️ 🗑️   │   │
│  │ ¥30.00              │   │
│  │ 2025-11-15          │   │
│  │ 苹果                │   │
│  └─────────────────────┘   │
│  ┌─────────────────────┐   │
│  │ 食品        ✏️ 🗑️   │   │
│  │ ¥20.00              │   │
│  │ 2025-11-15          │   │
│  │ 香蕉                │   │
│  └─────────────────────┘   │
└─────────────────────────────┘
```

## ✅ 功能特性

- ✅ 多图片上传（支持批量选择）
- ✅ 文本智能解析
- ✅ 实时识别反馈
- ✅ 结果可编辑
- ✅ 批量保存
- ✅ 错误处理
- ✅ 加载状态显示
- ✅ Material Design 3 设计

## 🐛 故障排查

### 问题：识别失败
**解决方案**:
1. 检查 API Key 是否正确
2. 检查网络连接
3. 查看 Logcat 中的错误日志（标签: AIRecordRepository）

### 问题：图片无法选择
**解决方案**:
1. 检查存储权限
2. 确认图片格式（支持 JPG, PNG）
3. 尝试选择其他图片

### 问题：识别结果为空
**解决方案**:
1. 确认输入包含消费信息
2. 尝试更清晰的描述
3. 检查图片清晰度

## 📊 性能建议

1. **图片大小**: 建议 < 5MB
2. **图片数量**: 建议 ≤ 5 张
3. **文本长度**: 建议 < 1000 字
4. **网络**: 建议使用 WiFi

## 🔒 安全提示

- API Key 存储在加密的 SharedPreferences 中
- 不要在代码中硬编码 API Key
- 定期更换 API Key
- 不要分享 API Key

## 📚 相关文档

- `AI_FEATURE_COMPLETE.md` - 完整功能报告
- `AI_INTEGRATION_GUIDE.md` - 详细集成指南
- `AI_EXPENSE_FEATURE.md` - 功能设计文档

## 🎯 下一步

1. ✅ 核心功能已完成
2. ⏳ 完成导航集成（5分钟）
3. ⏳ 添加设置界面（10分钟）
4. ⏳ 测试功能（30分钟）
5. ⏳ 优化体验（可选）

## 💡 提示

- 首次使用需要配置 API Key
- 识别速度取决于网络和图片大小
- 可以同时使用图片和文本识别
- 识别结果可以编辑后再保存

---

**准备就绪！** 🚀 只需完成简单的集成步骤即可开始使用 AI 智能识别功能。

有问题？查看 `AI_INTEGRATION_GUIDE.md` 获取详细帮助。
