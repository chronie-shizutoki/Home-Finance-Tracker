# 任务 9 - 当前状态和问题分析

## 当前问题

### 问题 1: 发送给后端的时间异常
- 用户选择：2025-11-15
- 期望发送：2025-11-14T16:00:00 (UTC)
- 实际发送：仍然不正确

### 问题 2: 数据库测试界面和主界面不同步
- 数据库测试界面：显示正确（直接从本地数据库读取）
- 主界面：显示不正确（从服务器API获取）

## 原因分析

### 数据流程
1. **添加支出**：
   - AddExpenseViewModel → ExpenseRepository.addExpense()
   - 保存到本地数据库（使用 ExpenseMapper.toEntity）
   - 添加到同步队列（使用 ExpenseMapper.toDto）

2. **数据库测试界面**：
   - 直接从本地数据库读取
   - 使用 ExpenseMapper.toDomain(entity)
   - 显示正确 ✓

3. **主界面**：
   - 从服务器API获取数据
   - 使用 ExpenseMapper.toDomain(dto)
   - 显示不正确 ✗

## 可能的问题

### 1. 同步队列中的数据格式
同步队列存储的是 JSON 格式的 ExpenseDto，在 `ExpenseRepositoryImpl.addToSyncQueue()` 中：
```kotlin
val dto = ExpenseMapper.toDto(ExpenseMapper.toDomain(data))
val jsonData = gson.toJson(dto)
```

这里可能有问题：
- `data` 是 `ExpenseEntity`
- 转换为 `Expense` (domain)
- 再转换为 `ExpenseDto`
- 在这个过程中时间可能被错误处理

### 2. API 调用时机
主界面优先从服务器获取数据，但：
- 新添加的数据可能还没同步到服务器
- 或者同步时发送的时间就是错误的

## 建议的修复方案

### 方案 1: 调试日志
在关键位置添加日志，查看时间转换的每一步：
1. AddExpenseViewModel.saveExpense() - 创建的时间
2. ExpenseMapper.toEntity() - 转换为 Entity 的时间戳
3. ExpenseMapper.toDto() - 转换为 DTO 的时间字符串
4. 同步队列中存储的 JSON
5. 实际发送到服务器的请求

### 方案 2: 简化数据流
不要在同步队列中存储 DTO，而是存储原始的 Entity 数据，在上传时再转换。

### 方案 3: 统一数据源
主界面在添加数据后，应该先显示本地数据，而不是立即从服务器获取。

## 下一步行动

1. 检查 LoggingInterceptor 的日志，查看实际发送到服务器的请求
2. 检查同步队列中存储的数据格式
3. 确认 ExpenseMapper.toDto() 的输出是否正确
