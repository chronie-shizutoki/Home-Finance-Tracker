# AI 导航和国际化修复

## 修复内容

### 1. 国际化修复
**问题**: AddExpenseScreen 第117行硬编码了简体中文 "拍照或输入文本快速记账"

**解决方案**: 
- 添加新的字符串资源 `ai_expense_entry_description`
- 在三个语言文件中添加对应翻译：
  - `values/strings.xml` (英语): "Take photos or enter text for quick bookkeeping"
  - `values-zh/strings.xml` (简体中文): "拍照或输入文本快速记账"
  - `values-zh-rTW/strings.xml` (繁体中文): "拍照或輸入文本快速記賬"

**修改文件**:
- `android/app/src/main/res/values/strings.xml`
- `android/app/src/main/res/values-zh/strings.xml`
- `android/app/src/main/res/values-zh-rTW/strings.xml`
- `android/app/src/main/java/com/chronie/homemoney/ui/expense/AddExpenseScreen.kt`

### 2. AI 界面导航修复
**问题**: 点击 AI 入口无法打开 AI 记录界面

**解决方案**:
在 MainActivity 中添加 AI 界面的导航路由：

1. **导入 AIExpenseScreen**:
```kotlin
import com.chronie.homemoney.ui.expense.AIExpenseScreen
```

2. **添加导航路由**:
```kotlin
composable("ai_expense") {
    AIExpenseScreen(
        onNavigateBack = {
            navController.popBackStack()
        },
        onRecordsSaved = {
            shouldRefreshExpenses = true
            navController.popBackStack()
        }
    )
}
```

3. **连接 AddExpenseScreen 的导航回调**:
```kotlin
composable("add_expense") {
    AddExpenseScreen(
        context = context,
        onNavigateBack = {
            shouldRefreshExpenses = true
            navController.popBackStack()
        },
        onNavigateToAI = {
            navController.navigate("ai_expense")
        }
    )
}
```

**修改文件**:
- `android/app/src/main/java/com/chronie/homemoney/MainActivity.kt`

## 功能验证

### 用户流程：
1. 打开应用 → 主界面
2. 点击添加支出按钮
3. 在添加支出界面顶部看到 "AI 智能识别" 入口卡片
4. 点击该卡片 → 跳转到 AI 识别界面
5. 在 AI 界面识别并保存记录后 → 自动返回并刷新支出列表

### 国际化验证：
- 切换到英语：显示 "Take photos or enter text for quick bookkeeping"
- 切换到简体中文：显示 "拍照或输入文本快速记账"
- 切换到繁体中文：显示 "拍照或輸入文本快速記賬"

## 构建状态
✅ 编译成功
✅ 无语法错误
✅ 导航路由正确配置
✅ 国际化字符串完整
