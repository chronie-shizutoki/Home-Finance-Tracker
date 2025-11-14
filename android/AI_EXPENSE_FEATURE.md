# AI 智能记录功能实现方案

## 功能概述

为 Android 应用的添加支出记录界面增加 AI 智能识别功能，支持：
- 多图片上传和识别
- 文本输入和解析
- AI 流式输出（仅用于记录识别，不需要对话功能）
- 用户审核和编辑 AI 提供的多条消费记录
- 批量保存记录

## 技术架构

### 1. API 层
- **AIRecordApi.kt**: 定义 AI 记录识别的 API 接口
- **AIRecordDto.kt**: AI 请求和响应的数据传输对象

### 2. Domain 层
- **AIExpenseRecord.kt**: AI 识别的支出记录领域模型
- **AIRecordRepository.kt**: AI 记录仓库接口

### 3. Data 层
- **AIRecordRepositoryImpl.kt**: AI 记录仓库实现
- **AIRecordMapper.kt**: AI 记录数据映射器

### 4. UI 层
- **AIExpenseScreen.kt**: AI 智能记录界面
- **AIExpenseViewModel.kt**: AI 智能记录视图模型

## 实现步骤

### 步骤 1: 创建 DTO 和 API 接口

文件: `data/remote/dto/AIRecordDto.kt`
- AIRecordRequest: 请求数据结构
- AIRecordResponse: 响应数据结构
- AIExpenseRecordDto: AI 识别的支出记录 DTO

文件: `data/remote/api/AIRecordApi.kt`
- parseTextToRecord(): 文本解析接口
- parseImageToRecord(): 图片解析接口（支持多图片）

### 步骤 2: 创建 Domain 模型

文件: `domain/model/AIExpenseRecord.kt`
- 定义 AI 识别的支出记录模型
- 包含编辑状态、验证状态等

文件: `domain/repository/AIRecordRepository.kt`
- 定义 AI 记录仓库接口

### 步骤 3: 实现 Repository

文件: `data/repository/AIRecordRepositoryImpl.kt`
- 实现文本解析
- 实现图片解析（支持多图片）
- 处理流式响应

文件: `data/mapper/AIRecordMapper.kt`
- DTO 到 Domain 模型的映射

### 步骤 4: 创建 ViewModel

文件: `ui/expense/AIExpenseViewModel.kt`
- 管理 AI 识别状态
- 处理图片上传
- 处理文本输入
- 管理识别结果列表
- 支持编辑和删除单条记录
- 批量保存记录

### 步骤 5: 创建 UI

文件: `ui/expense/AIExpenseScreen.kt`
- 图片选择和预览
- 文本输入框
- AI 识别按钮
- 识别结果列表（可编辑）
- 批量保存按钮

### 步骤 6: 集成到现有界面

修改 `AddExpenseScreen.kt`:
- 添加 "AI 智能识别" 按钮
- 导航到 AI 识别界面

## 数据流

```
用户输入（图片/文本）
    ↓
AIExpenseViewModel
    ↓
AIRecordRepository
    ↓
AIRecordApi (调用 SiliconFlow API)
    ↓
解析响应（流式或非流式）
    ↓
转换为 AIExpenseRecord 列表
    ↓
显示在 UI 供用户审核
    ↓
用户编辑/删除/确认
    ↓
批量保存到 ExpenseRepository
```

## API 配置

需要在设置中添加 SiliconFlow API Key 配置：
- API URL: https://api.siliconflow.cn/v1/chat/completions
- 文本模型: Qwen/Qwen3-8B
- 图片模型: THUDM/GLM-4.1V-9B-Thinking

## UI 设计要点

1. **图片上传区域**
   - 支持多图片选择
   - 图片预览网格
   - 删除单张图片功能

2. **文本输入区域**
   - 多行文本输入框
   - 提示用户输入消费信息

3. **识别结果列表**
   - 卡片式展示每条记录
   - 每条记录可编辑（类型、金额、日期、备注）
   - 删除按钮
   - 验证状态指示

4. **操作按钮**
   - "开始识别" 按钮
   - "全部保存" 按钮
   - "取消" 按钮

## 注意事项

1. **API Key 安全**: API Key 应存储在加密的 SharedPreferences 中
2. **错误处理**: 需要处理网络错误、API 错误、解析错误
3. **加载状态**: 显示加载指示器，因为 AI 识别可能需要时间
4. **数据验证**: 保存前验证每条记录的必填字段
5. **流式输出**: 虽然参考实现使用非流式，但可以考虑实现流式以提升用户体验

## 依赖项

需要在 `build.gradle` 中添加：
```gradle
// 图片选择
implementation "androidx.activity:activity-compose:1.8.0"
implementation "io.coil-kt:coil-compose:2.5.0"

// 图片压缩
implementation "id.zelory:compressor:3.0.1"
```

## 测试计划

1. 单元测试
   - AIRecordMapper 测试
   - AIExpenseViewModel 测试

2. 集成测试
   - API 调用测试
   - 数据流测试

3. UI 测试
   - 图片选择测试
   - 文本输入测试
   - 记录编辑测试
   - 批量保存测试
