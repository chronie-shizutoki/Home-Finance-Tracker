# 预算卡片格式化崩溃修复

## 问题描述

应用在显示预算卡片时发生崩溃，错误信息：
```
java.util.IllegalFormatConversionException: f != java.lang.String
at com.chronie.homemoney.ui.budget.BudgetCardKt$BudgetUsageCard$1.invoke(BudgetCard.kt:264)
```

## 根本原因

在 `BudgetCard.kt` 中，代码存在双重格式化问题：

1. 先使用 `String.format(Locale.getDefault(), "%.0f", value)` 将数字格式化为字符串
2. 然后将格式化后的字符串传递给 `context.getString(R.string.xxx, ...)`
3. 但字符串资源中使用 `%2$.0f` 期望接收浮点数
4. 当传入已格式化的字符串时，`%.0f` 格式化符尝试将字符串当作浮点数处理，导致类型不匹配异常

## 修复方案

直接传递原始数字值给 `context.getString()`，让字符串资源中的格式化符处理格式化：

### 修改前（错误）：
```kotlin
context.getString(
    R.string.budget_alert_warning_message,
    String.format(Locale.getDefault(), "%.2f", usage.remainingAmount),
    String.format(Locale.getDefault(), "%.0f", usage.spendingPercentage)  // ❌ 错误：传递字符串给 %f
)
```

### 修改后（正确）：
```kotlin
context.getString(
    R.string.budget_alert_warning_message,
    String.format(Locale.getDefault(), "%.2f", usage.remainingAmount),
    usage.spendingPercentage  // ✅ 正确：直接传递数字
)
```

## 修改文件

- `android/app/src/main/java/com/chronie/homemoney/ui/budget/BudgetCard.kt`
  - 第 267 行：WARNING 状态消息的第二个参数
  - 第 278 行：NORMAL 状态消息的第二个参数

## 字符串资源格式

字符串资源中的格式化符定义（正确）：
```xml
<!-- values/strings.xml -->
<string name="budget_alert_warning_message">¥%1$s remaining (%2$.0f%%), please control spending</string>
<string name="budget_alert_normal_message">¥%1$s remaining, %2$.0f%% available</string>
```

- `%1$s`：字符串类型（已格式化的金额）
- `%2$.0f`：浮点数类型（百分比数字）

## 测试结果

✅ 构建成功
✅ 预算卡片正常显示
✅ 所有状态（正常、警告、超支）的消息格式化正确

## 经验教训

1. **避免双重格式化**：不要先格式化为字符串再传递给需要数字的格式化符
2. **类型匹配**：确保传递给 `getString()` 的参数类型与字符串资源中的格式化符匹配
3. **格式化符类型**：
   - `%s` 或 `%1$s`：字符串
   - `%d` 或 `%1$d`：整数
   - `%f` 或 `%1$.2f`：浮点数

## 日期

2025-11-15
