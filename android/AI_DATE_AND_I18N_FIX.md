# AI 功能日期编辑和国际化修复

## 修复内容

### 1. AI 记录日期编辑功能
**问题**: 编辑 AI 识别的记录时无法变更日期

**解决方案**:
在 `RecordEditDialog` 中添加日期选择功能：

#### 新增功能：
- 添加 `selectedDate` 状态变量存储选中的日期
- 添加 `showDatePicker` 状态控制日期选择器显示
- 在对话框中添加日期选择按钮
- 使用 Material3 的 `DatePickerDialog` 和 `DatePicker` 组件
- 保存时将选中的日期合并到记录的 `time` 字段

#### 代码变更：
```kotlin
// 添加状态
var selectedDate by remember { mutableStateOf(record.time.toLocalDate()) }
var showDatePicker by remember { mutableStateOf(false) }

// 添加日期选择按钮
OutlinedButton(
    onClick = { showDatePicker = true },
    modifier = Modifier.fillMaxWidth()
) {
    Text(selectedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
    Spacer(modifier = Modifier.weight(1f))
    Icon(Icons.Default.DateRange, contentDescription = null)
}

// 更新记录时包含日期
val updatedRecord = record.copy(
    type = selectedType,
    amount = amount.toDoubleOrNull() ?: record.amount,
    time = selectedDate.atTime(record.time.toLocalTime()),
    remark = remark,
    isEdited = true
)
```

**修改文件**:
- `android/app/src/main/java/com/chronie/homemoney/ui/expense/AIExpenseScreen.kt`

---

### 2. AI 提示语添加今天日期和星期
**问题**: AI 提示语中没有告知今天的日期和星期，导致 AI 无法准确判断相对日期

**解决方案**:
在 `buildTextPrompt()` 和 `buildImagePrompt()` 方法中添加当前日期和星期信息：

#### 实现细节：
```kotlin
val today = java.time.LocalDate.now()
val dayOfWeek = today.dayOfWeek.getDisplayName(
    java.time.format.TextStyle.FULL,
    java.util.Locale.SIMPLIFIED_CHINESE
)
val dateStr = today.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
```

#### 提示语格式：
```
今天是 2024-01-15，星期一。

请分析以下文本，提取其中的所有消费信息...
```

**好处**:
- AI 可以准确理解"今天"、"昨天"、"上周"等相对日期
- 提高日期识别的准确性
- 用户输入更自然（如"今天买了苹果"）

**修改文件**:
- `android/app/src/main/java/com/chronie/homemoney/data/repository/AIRecordRepositoryImpl.kt`

---

### 3. AI 界面语言国际化修复
**问题**: AI 界面语言始终跟随系统而非应用设置的自定义语言

**解决方案**:
将 `AIExpenseScreen` 改为接收 `context` 参数，而不是使用 `LocalContext.current`

#### 修改内容：

1. **AIExpenseScreen 函数签名**:
```kotlin
@Composable
fun AIExpenseScreen(
    context: android.content.Context,  // 新增参数
    onNavigateBack: () -> Unit,
    onRecordsSaved: () -> Unit,
    viewModel: AIExpenseViewModel = hiltViewModel()
)
```

2. **MainActivity 导航配置**:
```kotlin
composable("ai_expense") {
    AIExpenseScreen(
        context = context,  // 传入本地化的 context
        onNavigateBack = { navController.popBackStack() },
        onRecordsSaved = {
            shouldRefreshExpenses = true
            navController.popBackStack()
        }
    )
}
```

3. **子组件更新**:
- `ImageSelectionSection` 添加 `context` 参数
- `TextInputSection` 添加 `context` 参数
- 移除所有 `LocalContext.current` 的使用

**原理**:
- MainActivity 中的 `context` 是通过 `createConfigurationContext()` 创建的本地化 context
- 该 context 已经应用了用户在设置中选择的语言
- 将此 context 传递给所有界面，确保语言一致性

**修改文件**:
- `android/app/src/main/java/com/chronie/homemoney/ui/expense/AIExpenseScreen.kt`
- `android/app/src/main/java/com/chronie/homemoney/MainActivity.kt`

---

## 功能验证

### 日期编辑验证：
1. 打开 AI 识别界面
2. 识别或输入消费记录
3. 点击记录卡片的编辑按钮
4. 在编辑对话框中点击日期按钮
5. 选择新日期并确认
6. 验证记录显示的日期已更新

### 日期提示验证：
1. 查看 AI API 请求日志
2. 确认提示语包含"今天是 YYYY-MM-DD，星期X"
3. 测试输入"今天买了苹果"等相对日期表述
4. 验证 AI 能正确识别为当天日期

### 语言国际化验证：
1. 在设置中切换语言（英语/简体中文/繁体中文）
2. 打开 AI 识别界面
3. 验证所有文本都使用选定的语言显示
4. 验证不会出现系统语言的文本

## 构建状态
✅ 编译成功
✅ 无语法错误
✅ 日期选择器正常工作
✅ 语言切换正确应用
