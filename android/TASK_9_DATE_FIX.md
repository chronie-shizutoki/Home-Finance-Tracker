# 任务 9 - 日期时间修复

## 问题描述
当用户选择日期 2025-11-15 时：
- 期望提交到服务器：2025-11-14 16:00:00 (UTC)
- 实际提交到服务器：2025-11-14 08:00:00 (UTC)

## 问题分析
1. 用户选择的日期被创建为 `LocalDateTime.of(date, LocalTime.MIDNIGHT)` = `2025-11-15 00:00:00`
2. `LocalDateTime` 没有时区信息
3. 在 `ExpenseMapper.toDto()` 中，我们使用 `ZoneId.systemDefault()` 来获取系统时区
4. 如果系统时区不是 Asia/Shanghai，转换就会出错

## 解决方案
明确指定所有日期时间操作都使用北京时区（Asia/Shanghai），而不依赖系统默认时区。

## 修复内容
1. 在 `toEntity()` 中明确使用北京时区
2. 在 `toDomain(entity)` 中明确使用北京时区
3. 确保整个应用中日期时间的一致性
