# 任务6修复 - 添加支出记录

## 修复的问题

### 1. ✅ 日期写入逻辑修复
**问题**：写入的日期为当前的用户系统时间，而不是默认的00:00:00

**修复**：
- 在 `AddExpenseViewModel.kt` 中修改日期创建逻辑
- 用户选择日期后，设置为 `LocalTime.MIDNIGHT` (00:00:00)
- 然后通过 `ExpenseMapper.toDto()` 固定减去8小时

**流程**：
1. 用户选择日期：2025-11-14
2. 设置为：2025-11-14 00:00:00
3. 减8小时：2025-11-13T16:00:00（写入数据库/发送API）
4. 读取时加8小时：2025-11-14（显示）

### 2. ✅ 自定义语言显示修复
**问题**：添加支出页面始终跟随系统语言显示

**修复**：
- 修改 `AddExpenseScreen` 接收 `context` 参数（而不是使用 `LocalContext.current`）
- 在 `MainActivity.kt` 中传递 `localizedContext`
- 确保所有文本使用 `context.getString()` 获取

**参考**：与 `ExpenseListScreen` 等其他组件保持一致

### 3. ✅ 对话框圆角背景修复
**问题**：类型、日期弹窗的圆角不应该显示区域存在白色背景

**修复**：
- 在 `ExpenseTypeDialog` 中添加 `shape = MaterialTheme.shapes.large`
- 在 `ExpenseDatePickerDialog` 中添加 `shape = MaterialTheme.shapes.large`
- 这样对话框会使用Material Design 3的圆角样式，不会有白色背景溢出

### 4. ✅ 日期选择器国际化
**问题**：日期选择器的取消/确定按钮显示为 Cancel/OK

**修复**：
- 修改 `ExpenseDatePickerDialog` 接收 `context` 参数
- 确定按钮：`Text(context.getString(R.string.confirm))`
- 取消按钮：`Text(context.getString(R.string.cancel))`

## 修改的文件

1. `android/app/src/main/java/com/chronie/homemoney/ui/expense/AddExpenseViewModel.kt`
   - 修改日期创建逻辑，使用 `LocalTime.MIDNIGHT`

2. `android/app/src/main/java/com/chronie/homemoney/ui/expense/AddExpenseScreen.kt`
   - 添加 `context` 参数
   - 移除 `LocalContext.current` 的使用
   - 为对话框添加 `shape` 参数
   - 日期选择器按钮国际化

3. `android/app/src/main/java/com/chronie/homemoney/MainActivity.kt`
   - 传递 `context` 到 `AddExpenseScreen`

## 验证清单

- [ ] 编译项目无错误
- [ ] 添加支出时日期显示正确的自定义语言
- [ ] 类型选择对话框圆角无白色背景
- [ ] 日期选择器圆角无白色背景
- [ ] 日期选择器按钮显示正确的语言（确认/取消）
- [ ] 选择日期后写入数据库的时间为00:00:00减8小时
- [ ] 从数据库读取的日期正确显示（加8小时后只显示日期）

## 日期时间处理总结

### 完整流程
```
用户选择: 2025-11-14
    ↓
设置时间: 2025-11-14 00:00:00 (LocalTime.MIDNIGHT)
    ↓
减8小时: 2025-11-13T16:00:00 (ExpenseMapper.toDto)
    ↓
存储/API: 2025-11-13T16:00:00 (UTC格式)
    ↓
读取时: 2025-11-13T16:00:00
    ↓
加8小时: 2025-11-14 00:00:00 (ExpenseMapper.toDomain)
    ↓
显示: 2025-11-14 (只显示日期部分)
```

### 关键代码位置
- **日期设置**：`AddExpenseViewModel.saveExpense()` - 使用 `LocalTime.MIDNIGHT`
- **减8小时**：`ExpenseMapper.toDto()` - `expense.time.minusHours(8)`
- **加8小时**：`ExpenseMapper.toDomain()` - `utcTime.plusHours(8)`
