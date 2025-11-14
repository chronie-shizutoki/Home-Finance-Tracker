# 任务3: Room 数据库基础 - 测试说明

## 完成内容

### 1. 数据库实体 (Entity)
已创建以下实体类,对应后端数据库表:

- **ExpenseEntity** (`expenses` 表)
  - 支出记录实体
  - 字段: id, type, remark, amount, time, created_at, updated_at, is_synced, server_id
  - 索引: time, type, is_synced

- **DebtEntity** (`debts` 表)
  - 债务记录实体
  - 字段: id, type, person, amount, date, is_repaid, remark, created_at, updated_at, is_synced, server_id
  - 索引: date, type, is_repaid, is_synced

- **MemberEntity** (`members` 表)
  - 会员信息实体(仅存储当前登录用户)
  - 字段: id, username, is_active, created_at, updated_at

- **SyncQueueEntity** (`sync_queue` 表)
  - 同步队列实体
  - 字段: id, entity_type, entity_id, operation, data, retry_count, created_at
  - 索引: entity_type, created_at

### 2. 数据访问对象 (DAO)
已创建以下DAO接口:

- **ExpenseDao**: 支出记录的CRUD操作
  - 查询所有支出、按ID查询、按时间范围查询、按类型查询
  - 查询未同步的支出
  - 插入、更新、删除操作
  - 统计功能(总数、总金额)

- **DebtDao**: 债务记录的CRUD操作
  - 查询所有债务、按ID查询、按类型查询、按还款状态查询
  - 查询未同步的债务
  - 插入、更新、删除操作
  - 统计功能(按类型统计总金额)

- **MemberDao**: 会员信息的CRUD操作
  - 查询当前会员、按ID查询
  - 插入、更新、删除操作

- **SyncQueueDao**: 同步队列的CRUD操作
  - 查询所有队列项、按类型查询、获取下一批待同步项
  - 插入、更新、删除操作
  - 统计功能(队列数量)

### 3. 数据库配置
- **AppDatabase**: Room数据库主类
  - 版本: 1
  - 包含4个表: expenses, debts, members, sync_queue
  - 数据库名称: homemoney.db

- **DatabaseMigrations**: 数据库迁移策略
  - 预留了版本升级的迁移框架

### 4. 数据库加密
- 使用 **SQLCipher** 加密数据库
- 使用 **EncryptedSharedPreferences** 安全存储数据库密码
- 密码使用 Android Keystore 管理
- 自动生成32位随机密码

### 5. 依赖注入
- **DatabaseModule**: Hilt模块
  - 提供加密的数据库实例
  - 提供所有DAO实例
  - 单例模式

### 6. 测试代码
- **AppDatabaseTest**: 单元测试
  - 测试插入、查询、更新、删除操作
  - 测试未同步数据查询
  - 使用内存数据库进行测试

- **DatabaseTestScreen**: 真机测试界面
  - 可视化的数据库操作界面
  - 添加测试数据功能
  - 查看数据库记录
  - 显示统计信息
  - 清空数据功能

## 数据存储策略

### 应该在客户端存储的数据:
✅ **Expenses (支出记录)** - 用户的个人财务数据,支持离线操作
✅ **Debts (债务记录)** - 用户的个人债务数据,支持离线操作
✅ **Members (会员信息)** - 当前登录用户的基本信息
✅ **SyncQueue (同步队列)** - 本地待同步的操作

### 不应该在客户端存储的数据:
❌ **SubscriptionPlans (订阅计划)** - 系统配置数据,从服务器动态获取
❌ **UserSubscriptions (用户订阅记录)** - 涉及支付和订阅状态,从服务器获取
❌ **operation_logs (操作日志)** - 服务器端的日志系统
❌ **donation_records (捐赠记录)** - 涉及支付,不应该本地存储

## 测试步骤

### 1. 编译应用
```bash
cd android
.\gradlew assembleDebug
```

### 2. 安装APK
生成的APK位于: `android/app/build/outputs/apk/debug/app-debug.apk`

### 3. 测试数据库功能

#### 3.1 启动应用
- 打开应用
- 点击"开始使用"进入主界面

#### 3.2 启用开发者模式
- 在主界面右下角,点击"设置"按钮(齿轮图标)
- 进入设置界面
- 向下滚动到"开发者选项"部分
- 打开"开发者模式"开关
- 返回主界面

#### 3.3 进入数据库测试界面
- 在主界面右下角,现在会显示"DB"按钮(仅开发者模式可见)
- 点击"DB"按钮
- 进入数据库测试界面

#### 3.4 测试添加数据
- 点击"添加测试数据"按钮
- 观察界面是否显示新增的记录
- 检查统计信息是否更新(记录数、总金额)
- 多次点击,添加多条记录

#### 3.5 验证数据持久化
- 关闭应用
- 重新打开应用
- 进入数据库测试界面
- 确认之前添加的数据仍然存在

#### 3.6 测试清空数据
- 点击"清空数据"按钮
- 确认所有记录被删除
- 统计信息归零

#### 3.7 验证数据加密
- 使用文件管理器查看应用数据目录
- 确认数据库文件已加密(无法直接读取)

### 4. 运行单元测试
```bash
cd android
.\gradlew test
```

## 验证点

### ✅ 功能验证
- [ ] 数据库可以正常创建
- [ ] 可以成功插入数据
- [ ] 可以查询数据
- [ ] 可以更新数据
- [ ] 可以删除数据
- [ ] 数据持久化正常(关闭应用后数据不丢失)
- [ ] 统计功能正常(记录数、总金额)

### ✅ 安全验证
- [ ] 数据库文件已加密
- [ ] 密码安全存储在EncryptedSharedPreferences
- [ ] 无法直接读取数据库文件内容

### ✅ 性能验证
- [ ] 插入操作响应快速
- [ ] 查询操作响应快速
- [ ] 大量数据(100+条)时仍然流畅

### ✅ 索引验证
- [ ] 按时间查询快速
- [ ] 按类型查询快速
- [ ] 按同步状态查询快速

## 开发者模式

### 启用方式
数据库测试功能默认隐藏,需要启用开发者模式才能访问:
1. 进入设置界面
2. 找到"开发者选项"部分
3. 打开"开发者模式"开关
4. 返回主界面,会看到"DB"按钮

### 功能说明
- 开发者模式使用DataStore持久化存储
- 关闭应用后设置会保留
- 可以随时在设置中关闭开发者模式
- 关闭后"DB"按钮会自动隐藏

## 已知问题和注意事项

### 1. 开发阶段配置
当前数据库配置使用了 `fallbackToDestructiveMigration()`,这意味着:
- ⚠️ 数据库结构变更时会删除所有数据
- ⚠️ 仅适用于开发阶段
- ✅ 生产环境必须移除此配置,使用正确的迁移策略

### 2. 数据库加密
- 首次启动时会自动生成加密密码
- 密码存储在EncryptedSharedPreferences中
- 如果清除应用数据,密码会丢失,数据库将无法打开

### 3. 后端数据库对比
后端使用SQLite + Sequelize ORM:
- 主键: 后端使用自增ID,客户端使用UUID
- 时间戳: 后端使用DATE类型,客户端使用Long(毫秒)
- 同步时需要进行数据类型转换

## 下一步

任务3完成后,可以继续:
- **任务4**: Retrofit API 客户端 - 实现与后端的网络通信
- **任务5**: 支出列表展示 - 实现第一个原生功能界面

## 技术栈

- **Room**: 2.6.1
- **SQLCipher**: 4.5.4
- **Hilt**: 2.48.1
- **Kotlin Coroutines**: 1.7.3
- **Jetpack Compose**: BOM 2023.10.01
