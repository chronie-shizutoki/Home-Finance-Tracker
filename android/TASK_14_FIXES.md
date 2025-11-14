# 任务14编译错误修复

## 修复时间
2024年

## 修复的问题

### 1. 字符串资源格式问题

**问题**: 多个字符串资源使用了多个替换参数但没有使用位置格式
```
Multiple substitutions specified in non-positional format
```

**修复**: 将所有多参数字符串改为位置格式
- `budget_alert_normal_message`: `¥%s，还有 %.0f%%` → `¥%1$s，还有 %2$.0f%%`
- `budget_alert_warning_message`: `¥%s（%.0f%%）` → `¥%1$s（%2$.0f%%）`

**影响文件**:
- `values/strings.xml`
- `values-zh/strings.xml`
- `values-zh-rTW/strings.xml`

### 2. ExpenseDao缺少方法

**问题**: `BudgetRepositoryImpl`调用了不存在的方法
```kotlin
Unresolved reference: getExpensesByDateRange
```

**修复**: 在`ExpenseDao`中添加方法
```kotlin
@Query("SELECT * FROM expenses WHERE date(time/1000, 'unixepoch') BETWEEN :startDate AND :endDate ORDER BY time DESC")
suspend fun getExpensesByDateRange(startDate: String, endDate: String): List<ExpenseEntity>
```

**影响文件**:
- `ExpenseDao.kt`

### 3. 类型推断歧义

**问题**: Kotlin无法推断减法操作的类型
```kotlin
Overload resolution ambiguity
```

**修复**: 显式声明变量类型
```kotlin
val currentSpending: Double = expenses.sumOf { it.amount }
val remainingAmount: Double = budget.monthlyLimit - currentSpending
```

**影响文件**:
- `BudgetRepositoryImpl.kt`

### 4. 缺少Material Icons

**问题**: `TrendingUp`图标不存在
```kotlin
Unresolved reference: TrendingUp
```

**修复**: 使用`ShowChart`图标替代
```kotlin
import androidx.compose.material.icons.filled.ShowChart
Icon(imageVector = Icons.Default.ShowChart, ...)
```

**影响文件**:
- `BudgetCard.kt`

### 5. Alignment.Baseline不存在

**问题**: `Baseline`对齐方式不可用
```kotlin
Unresolved reference: Baseline
```

**修复**: 使用`Bottom`对齐替代
```kotlin
verticalAlignment = Alignment.Bottom
```

**影响文件**:
- `BudgetCard.kt`

### 6. 缺少通用字符串资源

**问题**: `common_save`和`common_cancel`未定义

**修复**: 在所有语言的字符串资源中添加
```xml
<string name="common_save">保存/Save</string>
<string name="common_cancel">取消/Cancel</string>
```

**影响文件**:
- `values/strings.xml`
- `values-zh/strings.xml`
- `values-zh-rTW/strings.xml`

## 验证

所有文件通过了诊断检查:
- ✅ ExpenseDao.kt
- ✅ BudgetRepositoryImpl.kt
- ✅ BudgetCard.kt
- ✅ BudgetSettingsDialog.kt

## 额外修复

### 7. DatabaseModule缺少BudgetDao提供方法

**问题**: Hilt无法注入BudgetDao
```
'com.chronie.homemoney.data.local.dao.BudgetDao' could not be resolved
```

**修复**: 在`DatabaseModule`中添加提供方法
```kotlin
@Provides
fun provideBudgetDao(database: AppDatabase): BudgetDao {
    return database.budgetDao()
}
```

**影响文件**:
- `DatabaseModule.kt`

### 8. Material Icons可用性

经过测试,以下图标在项目中可用:
- ✅ Add, Settings, Home, Star, Check, Edit, Delete
- ✅ ArrowBack, ArrowForward, ArrowDropDown
- ✅ Close, Refresh, MoreVert
- ✅ DateRange, PlayArrow
- ❌ TrendingUp, ShowChart, AccountBalance, AttachMoney (不可用)

最终使用`Star`图标作为预算功能的图标。

## 构建状态

✅ 构建成功!
```bash
.\gradlew.bat assembleDebug
BUILD SUCCESSFUL in 1m 32s
```

## 技术说明

### 字符串格式化
Android要求多参数字符串使用位置格式以支持不同语言的参数顺序:
- `%s` → `%1$s` (第1个字符串参数)
- `%.0f` → `%2$.0f` (第2个浮点数参数,0位小数)

### Room查询
使用SQLite的date函数将时间戳转换为日期字符串进行比较:
```sql
date(time/1000, 'unixepoch') BETWEEN :startDate AND :endDate
```

### Material Icons
Material Icons库中可用的图标有限,需要选择合适的替代图标:
- `TrendingUp` → `ShowChart` (都表示趋势/图表)
- `Baseline` → `Bottom` (底部对齐)
