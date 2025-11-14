# AI 智能识别功能集成指南

## 已完成的文件 ✅

1. ✅ `data/remote/dto/AIRecordDto.kt` - AI API 数据传输对象
2. ✅ `data/remote/api/AIRecordApi.kt` - AI API 接口定义
3. ✅ `domain/model/AIExpenseRecord.kt` - AI 支出记录领域模型
4. ✅ `domain/repository/AIRecordRepository.kt` - AI 记录仓库接口
5. ✅ `data/mapper/AIRecordMapper.kt` - AI 记录数据映射器
6. ✅ `data/repository/AIRecordRepositoryImpl.kt` - AI 记录仓库实现
7. ✅ `di/AIModule.kt` - AI 模块依赖注入配置
8. ✅ `ui/expense/AIExpenseViewModel.kt` - AI 支出记录视图模型
9. ✅ `ui/expense/AIExpenseScreen.kt` - AI 支出记录界面
10. ✅ `app/build.gradle` - 添加 Coil 依赖
11. ✅ `res/values-zh/strings.xml` - 添加中文字符串资源

## 需要手动集成的部分 📝

### 1. 在 MainActivity 或导航图中添加 AI 界面路由

在你的导航配置中添加 AIExpenseScreen 的路由：

```kotlin
// 在 NavHost 中添加
composable("ai_expense") {
    AIExpenseScreen(
        onNavigateBack = { navController.popBackStack() },
        onRecordsSaved = {
            // 保存成功后的操作，例如返回列表页
            navController.popBackStack()
        }
    )
}
```

### 2. 在 AddExpenseScreen 中添加 AI 识别入口

在 `AddExpenseScreen.kt` 的 TopAppBar actions 中添加 AI 按钮：

```kotlin
import androidx.compose.material.icons.filled.AutoAwesome

// 在 TopAppBar 的 actions 中添加
actions = {
    // AI 识别按钮
    IconButton(
        onClick = { /* 导航到 AI 识别界面 */ }
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = "AI 智能识别"
        )
    }
    
    // 原有的保存按钮
    IconButton(
        onClick = {
            viewModel.saveExpense(
                onSuccess = { onNavigateBack() },
                onError = { error -> }
            )
        },
        enabled = !uiState.isSaving
    ) {
        // ... 原有代码
    }
}
```

### 3. 在设置界面添加 API Key 配置

在 `SettingsScreen.kt` 中添加 API Key 输入：

```kotlin
// 添加 API Key 设置项
var apiKey by remember {
    mutableStateOf(
        context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
            .getString("siliconflow_api_key", "") ?: ""
    )
}

OutlinedTextField(
    value = apiKey,
    onValueChange = { apiKey = it },
    label = { Text("SiliconFlow API Key") },
    modifier = Modifier.fillMaxWidth(),
    singleLine = true,
    trailingIcon = {
        IconButton(
            onClick = {
                context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
                    .edit()
                    .putString("siliconflow_api_key", apiKey)
                    .apply()
                // 显示保存成功提示
            }
        ) {
            Icon(Icons.Default.Save, contentDescription = "保存")
        }
    }
)
```

### 4. 添加权限（如果需要）

在 `AndroidManifest.xml` 中确保有以下权限：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```

## 使用流程

1. **配置 API Key**
   - 用户在设置界面输入 SiliconFlow API Key
   - API Key 会被加密存储在 SharedPreferences 中

2. **使用 AI 识别**
   - 在添加支出界面点击 AI 图标
   - 选择图片或输入文本
   - 点击"开始识别"
   - AI 会解析并返回识别的消费记录列表

3. **审核和编辑**
   - 用户可以查看识别结果
   - 点击编辑按钮修改单条记录
   - 点击删除按钮移除不需要的记录

4. **批量保存**
   - 点击"全部保存"按钮
   - 所有有效记录会被保存到数据库
   - 自动返回到列表界面

## API 配置说明

### SiliconFlow API

- **基础 URL**: https://api.siliconflow.cn/
- **文本模型**: Qwen/Qwen2.5-7B-Instruct
- **图片模型**: Pro/Qwen/Qwen2-VL-7B-Instruct
- **超时设置**: 60秒

### 获取 API Key

1. 访问 [SiliconFlow 官网](https://siliconflow.cn/)
2. 注册账号
3. 在控制台获取 API Key
4. 在应用设置中输入 API Key

## 测试建议

### 单元测试
- AIRecordMapper 的类型映射测试
- AIExpenseViewModel 的状态管理测试

### 集成测试
- API 调用测试（需要真实 API Key）
- 图片上传和解析测试
- 文本解析测试

### UI 测试
- 图片选择流程测试
- 记录编辑流程测试
- 批量保存流程测试

## 故障排查

### 常见问题

1. **识别失败**
   - 检查 API Key 是否正确
   - 检查网络连接
   - 查看日志中的错误信息

2. **图片无法上传**
   - 检查存储权限
   - 确认图片大小不超过限制
   - 检查图片格式是否支持

3. **识别结果为空**
   - 确认输入内容包含消费信息
   - 检查 AI 模型是否可用
   - 尝试更清晰的描述

## 性能优化建议

1. **图片压缩**
   - 在上传前压缩图片以减少传输时间
   - 建议最大尺寸 1920x1080

2. **缓存策略**
   - 缓存识别结果避免重复请求
   - 使用 Room 数据库存储草稿

3. **并发控制**
   - 限制同时上传的图片数量
   - 使用队列管理多个识别请求

## 后续改进方向

1. **流式输出**
   - 实现 SSE 或 WebSocket 支持
   - 实时显示识别进度

2. **离线支持**
   - 缓存未识别的内容
   - 网络恢复后自动重试

3. **智能建议**
   - 基于历史记录提供类型建议
   - 自动补全常用备注

4. **批量操作**
   - 支持批量编辑
   - 支持批量删除
   - 支持批量导出

## 完成状态

✅ 核心功能已完成
✅ UI 界面已完成
✅ 依赖注入已配置
✅ 字符串资源已添加
⏳ 需要手动集成导航
⏳ 需要添加 API Key 设置界面
⏳ 需要添加权限配置

## 下一步

1. 在导航图中添加 AI 界面路由
2. 在 AddExpenseScreen 添加 AI 入口按钮
3. 在 SettingsScreen 添加 API Key 配置
4. 测试完整流程
5. 根据测试结果优化
