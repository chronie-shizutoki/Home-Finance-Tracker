# 任务14调试指南

## 最新修复 (2024)

### 问题
启用预算管理后应用闪退

### 已实施的修复

#### 1. 添加详细日志
在关键位置添加了日志输出:
- `BudgetViewModel`: 预算加载和使用情况加载
- `BudgetRepositoryImpl`: 数据库查询和计算过程

#### 2. 增强错误处理
- 所有异步操作都包裹在try-catch中
- 错误信息会更新到UI状态
- 防止未捕获的异常导致崩溃

#### 3. 添加加载状态
- 预算启用后显示加载指示器
- 避免在数据未加载完成时渲染UI
- 防止null指针异常

### 如何查看日志

#### 使用ADB Logcat
```bash
# Windows
adb logcat | findstr "BudgetViewModel BudgetRepository AndroidRuntime"

# Linux/Mac
adb logcat | grep -E "BudgetViewModel|BudgetRepository|AndroidRuntime"
```

#### 关键日志标签
- `BudgetViewModel`: ViewModel层的操作
- `BudgetRepository`: 数据层的操作
- `AndroidRuntime`: 崩溃堆栈信息

### 预期日志输出

#### 正常流程
```
D/BudgetViewModel: Loading budget usage...
D/BudgetRepository: Getting current month usage...
D/BudgetRepository: No budget found / Budget is not enabled / Querying expenses...
D/BudgetRepository: Current spending: 0.0
D/BudgetRepository: Budget usage calculated successfully: BudgetUsage(...)
D/BudgetViewModel: Budget usage loaded: BudgetUsage(...)
```

#### 错误流程
```
E/BudgetViewModel: Error loading budget usage
E/BudgetRepository: Error calculating budget usage
    at ...
```

### 可能的崩溃原因

#### 1. 数据库查询失败
**症状**: 日志显示"Error calculating budget usage"
**原因**: SQL查询语法错误或数据类型不匹配
**解决**: 检查ExpenseDao的查询方法

#### 2. 时间计算错误
**症状**: 日志显示时间戳异常
**原因**: 时区转换或日期计算错误
**解决**: 验证LocalDate和时间戳转换逻辑

#### 3. 空指针异常
**症状**: NullPointerException in BudgetUsageCard
**原因**: UI尝试渲染null数据
**解决**: 已添加加载状态,应该已修复

#### 4. 主线程阻塞
**症状**: ANR (Application Not Responding)
**原因**: 在主线程执行数据库操作
**解决**: 所有操作都在viewModelScope中执行

### 测试步骤

#### 基础测试
1. 清除应用数据
2. 打开应用
3. 进入设置 → 预算管理
4. 启用预算功能
5. 设置预算限额: 1000
6. 设置警告阈值: 80
7. 点击保存
8. 观察是否崩溃

#### 带数据测试
1. 先添加几条支出记录
2. 然后启用预算功能
3. 验证预算使用情况是否正确显示

#### 边界测试
1. 预算为0
2. 预算为负数(应该被验证拦截)
3. 警告阈值为0或100
4. 没有任何支出记录

### 代码改进

#### BudgetViewModel
```kotlin
private fun loadBudget() {
    viewModelScope.launch {
        try {
            getBudgetUseCase().collect { budget ->
                _uiState.update { it.copy(budget = budget, error = null) }
                if (budget?.isEnabled == true) {
                    loadBudgetUsage()
                } else {
                    _uiState.update { it.copy(budgetUsage = null) }
                }
            }
        } catch (e: Exception) {
            Log.e("BudgetViewModel", "Error loading budget", e)
            _uiState.update { it.copy(error = e.message ?: "Unknown error") }
        }
    }
}
```

#### BudgetRepositoryImpl
```kotlin
override suspend fun getCurrentMonthUsage(): BudgetUsage? {
    return try {
        // 详细的日志和错误处理
        // ...
    } catch (e: Exception) {
        Log.e("BudgetRepository", "Error calculating budget usage", e)
        null
    }
}
```

#### BudgetCard
```kotlin
if (uiState.budget?.isEnabled == true) {
    val usage = uiState.budgetUsage
    if (usage != null) {
        BudgetUsageCard(...)
    } else {
        // 显示加载指示器
        CircularProgressIndicator()
    }
}
```

### 性能监控

#### 查询性能
```sql
-- 应该很快 (<10ms)
SELECT SUM(amount) FROM expenses 
WHERE time BETWEEN ? AND ?
```

#### 内存使用
- 预算数据很小,不应该有内存问题
- 不加载完整的支出列表,只查询总额

### 如果仍然崩溃

#### 1. 获取完整崩溃日志
```bash
adb logcat -d > crash_log.txt
```

#### 2. 查找关键信息
- FATAL EXCEPTION
- Caused by
- at com.chronie.homemoney...

#### 3. 检查数据库状态
```bash
adb shell
run-as com.chronie.homemoney
cd databases
ls -la
```

#### 4. 清除应用数据重试
```bash
adb shell pm clear com.chronie.homemoney
```

#### 5. 检查数据库迁移
确保从版本1到版本2的迁移正确执行:
```kotlin
MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS budgets (...)
        """)
    }
}
```

### 临时禁用预算功能

如果需要临时禁用预算功能进行测试:

#### 方法1: 注释掉BudgetCard
```kotlin
// ExpenseListScreen.kt
// BudgetCard(...)  // 注释掉这行
```

#### 方法2: 添加功能开关
```kotlin
// BudgetCard.kt
if (false) {  // 临时禁用
    // 预算卡片内容
}
```

### 构建状态
✅ BUILD SUCCESSFUL in 52s

新版本APK包含:
- 详细的调试日志
- 完整的错误处理
- 加载状态指示器
- 防崩溃保护

安装新APK后测试,如果仍然崩溃,请提供logcat输出以便进一步诊断。
