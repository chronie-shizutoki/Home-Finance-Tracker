# 支出记录同步问题修复

## 修复日期：2025-11-15

## 问题描述

### 问题1：日期时间异常
- **现象**：发送到后端的日期时间异常，发送数据是 `2025-11-14T08:00:00`，正确数据应该是 `2025-11-14T16:00:00`
- **原因**：在 `ExpenseMapper.toDto()` 中进行了时区转换（减去8小时），但后端的 `addExpense` 控制器使用 `dayjs(time).toDate()` 也会进行时区转换，导致双重转换
- **影响**：用户选择的日期时间被错误地减去了8小时

### 问题2：本地数据库同步时不会清理云端没有的多余数据
- **现象**：同步后，本地存在但云端已删除的记录仍然保留在本地数据库中
- **原因**：`SyncManagerImpl.downloadServerUpdates()` 只处理服务器上存在的数据，没有删除本地多余的记录
- **影响**：本地数据库中会累积已删除的记录，导致数据不一致

## 修复方案

### 修复1：移除客户端的时区转换

**文件**：`android/app/src/main/java/com/chronie/homemoney/data/mapper/ExpenseMapper.kt`

**修改内容**：

1. **toDto() 方法**：
   - 移除了 `expense.time.minusHours(8)` 的时区转换
   - 直接发送用户选择的日期时间
   - 让后端的 dayjs 自动处理时区转换

2. **toDomain(dto) 方法**：
   - 移除了 `utcTime.plusHours(8)` 的时区转换
   - 直接使用后端返回的时间
   - 后端已经存储了正确的时间

**原理**：
- 后端使用 `dayjs(time).toDate()` 会自动将接收到的时间字符串转换为正确的本地时间
- 客户端不需要进行任何时区转换，直接发送和接收 ISO 格式的时间字符串即可

### 修复2：同步时清理本地多余数据

**文件**：`android/app/src/main/java/com/chronie/homemoney/data/sync/SyncManagerImpl.kt`

**修改内容**：

1. 在 `downloadServerUpdates()` 方法中添加了清理逻辑：
   - 收集服务器上所有的 `serverId` 到一个 Set 中
   - 遍历本地所有支出记录
   - 删除满足以下条件的本地记录：
     - 已同步（`isSynced = true`）
     - 有 `serverId`（表示曾经同步过）
     - `serverId` 不在服务器返回的 ID 集合中

2. 添加了 `deletedItems` 计数器来跟踪删除的记录数

**安全性**：
- 只删除已同步的记录，保护未同步的本地更改
- 只删除有 `serverId` 的记录，避免误删新创建的记录
- 使用异常处理确保清理失败不会影响整个同步流程

## 测试建议

### 测试场景1：日期时间正确性
1. 在 Android 应用中创建一个支出记录，选择日期为 `2025-11-15`
2. 提交后检查后端数据库，确认存储的时间是 `2025-11-15T00:00:00` 或类似的正确时间
3. 在应用中查看该记录，确认显示的日期是 `2025-11-15`

### 测试场景2：同步清理功能
1. 在 Android 应用中创建几条支出记录并同步到服务器
2. 在 Web 端或其他客户端删除其中一些记录
3. 在 Android 应用中执行同步操作
4. 确认本地数据库中已删除的记录也被清理掉

### 测试场景3：保护未同步数据
1. 在 Android 应用中创建一条支出记录但不同步
2. 执行同步操作
3. 确认未同步的记录仍然保留在本地

## 相关文件

- `android/app/src/main/java/com/chronie/homemoney/data/mapper/ExpenseMapper.kt`
- `android/app/src/main/java/com/chronie/homemoney/data/sync/SyncManagerImpl.kt`
- `server/src/controllers/expenseController.js`

## 注意事项

1. 这些修复假设后端使用 dayjs 正确处理时区转换
2. 如果后端的时区处理逻辑发生变化，可能需要相应调整客户端代码
3. 同步清理功能依赖于 `serverId` 字段的正确维护
4. 建议在生产环境部署前进行充分测试
