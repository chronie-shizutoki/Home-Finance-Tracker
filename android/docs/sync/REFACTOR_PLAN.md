# 局域网同步重构方案（C++ / Kotlin 双端）

版本：v3 —— P0~P5 全部启动并合入，单测 364 项全绿；**仍有两块明确欠账**：弱网优化未接入生产路径（9.5）、同步相关仪器测试尚未开工（§8）
适用模块：`android/app/src/main/cpp/`、`android/app/src/main/java/com/chronie/homemoney/data/sync/`、`domain/sync/`

本文档同时承担两个角色：前半部分（§1~§8）是设计方案，§9 是各阶段的**落地记录**，包含实现过程中相对原设计的偏离及其原因。两者出现分歧时，以 §9 为准。

设计章节里凡是与实现不符的地方，都已就地标注实际状态而非删改原文——保留「原本打算怎么做」和「最后为什么没那么做」的对照，比留下一份看起来处处圆满的方案有用。

---

## 1. 现状梳理与关键问题

### 1.1 当前链路

```
A.searchDevices()  --UDP:12345 广播 "DISCOVERY|id|name|ip|ts"-->  B.startDiscoveryResponseServer()
                   <--UDP 同格式应答（发往 A 的临时端口）--
A.syncWithDevice() --TCP:50051 [4B len][全量 protobuf]-->        B.native accept loop
                                                                  -> JNI 回调 Kotlin
                                                                  -> runBlocking 等用户确认（≤60s）
                                                                  -> 逐条写库
                   <--[4B len][B 全量 protobuf]--
A.processDeviceData() -> 逐条写库 -> 关闭连接
```

分层现状：C++ 只做「TCP 字节管道 + 长度分帧」，protobuf 与全部业务语义都在 Kotlin。这个分层本身是合理的，重构保留它。

### 1.2 五个环节的问题清单

#### A. 设备发现

| # | 问题 | 位置 | 影响 |
|---|---|---|---|
| A1 | 明文竖线分隔字符串，无版本号、无能力位、无协议魔数 | `LanDeviceSyncManager:223` | 无法演进；任何 UDP 噪声都可能被误解析 |
| A2 | `DeviceInfo(..., senderIp, 80)` 端口写死 80，实际连接又用常量 50051 | `:267` | 端口冲突时无法协商；`signalStrength` 字段被挪用 |
| A3 | 申请了 `MulticastLock` 但从未 `joinGroup`，组播实际不生效 | `:230`、`:218` | 只有子网广播可用，AP 隔离/跨网段场景全失效 |
| A4 | `deviceType` 硬编码 `"ANDROID"` | `:267` | 无法区分端类型 |
| A5 | 12 秒定时轮询，循环内不检查 `isActive`，无法及时取消 | `:257` | 关闭页面后协程滞留 |
| A6 | 设备无 TTL/过期，`discoveredDevices` 只增不减；IP 变更后残留旧条目 | `:268` | Wi-Fi 切换后列表脏数据 |
| A7 | 本机过滤依赖 `getLocalIpAddress()` 取第一个非环回 IPv4 | `:194` | 多网卡（VPN/热点/蜂窝并存）时误判，可能自发现 |
| A8 | 应答报文与请求报文同格式同前缀，靠端口区分 | `:300` | 协议脆弱，易形成回环放大 |

#### B. 连接建立

| # | 问题 | 位置 | 影响 |
|---|---|---|---|
| B1 | `connect()` 是空实现，只置标志位；真实连接藏在每次 `performSync` 内 | `:314` | 接口语义与实现脱节，UI 的"连接中"状态是假的 |
| B2 | 无握手、无版本协商、无鉴权、无配对、无加密 | 全局 | 同一 LAN 任意设备可推送数据覆盖账本 |
| B3 | `accept` 循环在同一线程串行处理请求，无线程池 | `native-lib.cpp:140` | **多设备并发直接退化为排队**；一个慢客户端阻塞全部 |
| B4 | `stopServer` 从其他线程 `close(fd)`，与 `accept` 存在竞态；`std::thread(...).detach()` 无法 join | `:189`、`:196` | stop→start 快速切换会出现两个服务线程抢同一端口 |
| B5 | `g_engine_obj` 全局引用在 `stopServer` 中不释放 | `:196` | JNI 全局引用泄漏 |
| B6 | `accept` 失败时 `continue` 无退避 | `:145` | listen fd 持久错误态下 100% CPU 忙轮询 |

#### C. 数据传输

| # | 问题 | 位置 | 影响 |
|---|---|---|---|
| C1 | 每次传输**全表快照**，无增量 | `BaseDeviceSyncManager:115` | 数据量线性增长，弱网下必然超时 |
| C2 | 单次请求-响应，10MB 上限，无分片、无续传 | `native-lib.cpp:155` | 传到 99% 断线即全部作废 |
| C3 | `read_all`/`write_all` 把 `n<=0` 一律当致命错误，**未处理 EINTR/EAGAIN** | `:27`、`:39` | 10s `SO_RCVTIMEO` 一到就中断整条流，高延迟下几乎必失败 |
| C4 | 只有单次 socket 操作超时，无整体 deadline | `:219` | 慢速攻击/极慢链路可无限拖延 |
| C5 | 无压缩 | 全局 | 冗余 JSON 文本浪费带宽 |
| C6 | 无完整性校验（无 CRC/哈希） | 全局 | 静默损坏不可检测 |
| C7 | protobuf 里塞 Gson JSON 字符串（`data` 字段） | `sync.proto:12` | 双重编码，且 wire 格式与 Room 实体强耦合 |
| C8 | 攻击者可控的 `len` 直接 `vector(len)` 分配 | `:158` | 内存压力 / OOM 风险 |

#### D. 状态同步

| # | 问题 | 位置 | 影响 |
|---|---|---|---|
| D1 | **没有状态机**，只有 `isSyncing` 布尔 + 魔数进度值（0.1/0.4/0.7/1.0） | `LanDeviceSyncManager:344` | 双端进度语义不一致，无法推断当前阶段 |
| D2 | B 侧在 JNI 线程 `runBlocking(Dispatchers.IO)`，内含 ≤60s 的 `CountDownLatch.await` | `:101`、`:120` | **最严重**：确认对话框弹出期间整个 accept 循环冻结，其他设备全部连不上 |
| D3 | `respondToSyncRequest` / `pendingSyncResponse` 是死代码，从未赋值 | `:183` | 接口存在但无效 |
| D4 | 无 ACK / commit 阶段，A 是否成功落库 B 完全不知道 | 全局 | 响应丢失后 A 重试，B 重复应用 |
| D5 | 逐条 `insertExpense` 无事务 | `BaseDeviceSyncManager:164` | 中途失败留下半成品状态 |

#### E. 断线处理

| # | 问题 | 影响 |
|---|---|---|
| E1 | 完全没有重试、重连、续传 | 任何抖动 = 整次同步失败 |
| E2 | 无心跳保活 | Wi-Fi 切换后 socket 黑洞，只能等 10s 超时 |
| E3 | 未监听网络变化（`NetworkMonitor` 已存在但同步层没用） | 无法快速感知与主动恢复 |
| E4 | 错误全部塌缩成 `null` / `"Fail"` 字符串 | 无法定位失败原因 |

#### F. 数据正确性（功能性 Bug，优先级最高）

| # | 问题 | 位置 |
|---|---|---|
| **F1** | `val localTimestamp = System.currentTimeMillis()` 当作本地记录时间，导致 `entity.timestamp > localTimestamp` **恒为 false**，远端更新永不生效，全部误判为冲突 | `BaseDeviceSyncManager:170` |
| **F2** | `prepareLocalData` 用 `System.currentTimeMillis()` 覆盖实体真实 `updatedAt` | `:127` |
| **F3** | 只取 `deleted_at IS NULL` 且 operation 恒为 `"CREATE"`，**删除永不同步** | `:116`、`:125` |
| F4 | 用 `insertExpense`（REPLACE）而非 `upsertExpense`，可能用旧数据覆盖新数据 | `:164` |
| F5 | 只同步 expenses，budgets / members 被忽略 | `:116` |
| F6 | 无实体级幂等/去重 | 全局 |

---

## 2. 目标架构

### 2.1 分层原则

保持「C++ = 传输层，Kotlin = 协议语义层」的既有分工，但两端共享**同一份帧格式定义**。

```
┌─────────────────────────────────────────────────┐
│ UI / ViewModel（接口不变，向后兼容）              │
├─────────────────────────────────────────────────┤
│ SyncEngine        会话编排、进度聚合              │  Kotlin
│ SessionStateMachine  显式状态机                  │
│ DeltaBuilder / EntityMerger / ResumeStore        │
│ IdempotencyGuard                                 │
├─────────────────────────────────────────────────┤
│ FrameCodec + SyncWireProtocol（帧编解码）         │  Kotlin ─┐
│ DiscoveryService（UDP v2）                        │          │ 字节级
├───────────────── JNI 边界（纯异步，无阻塞）───────┤          │ 镜像
│ jni_bridge                                        │          │
│ Server(线程池) / Client(退避重连) / Connection    │  C++  ───┘
│ frame_codec + sync_protocol.h                     │
│ socket_utils（EINTR 安全、deadline、keepalive）   │
│ metrics / log                                     │
└─────────────────────────────────────────────────┘
```

### 2.2 模块划分

**C++（`app/src/main/cpp/`）**

| 文件 | 职责 |
|---|---|
| `protocol/sync_protocol.h/.cpp` | 帧头布局、魔数、版本、opcode、flags、错误码。**协议 SSOT 的 C++ 侧** |
| `protocol/crc32.h/.cpp` | CRC32C 校验 |
| `protocol/frame_codec.h/.cpp` | 帧编解码、分片装配、大小上限 |
| `net/socket_utils.h/.cpp` | EINTR/EAGAIN 安全读写、绝对 deadline、非阻塞 connect、TCP keepalive、`poll` 封装 |
| `net/connection.h/.cpp` | 单连接生命周期、握手、心跳、连接级状态机 |
| `net/server.h/.cpp` | accept 循环 + 固定线程池 + 背压 + 优雅停机（eventfd 唤醒，非 close 竞态） |
| `net/client.h/.cpp` | 带超时拨号、指数退避重连、会话恢复 |
| `obs/metrics.h/.cpp` | 计数器、RTT、吞吐、重传估计 |
| `obs/log.h` | 分级日志 + trace id |
| `jni_bridge.cpp` | **仅** JNI 转换与异步回调，零业务逻辑 |

**Kotlin（`data/sync/`）**

| 文件 | 职责 |
|---|---|
| `protocol/SyncWireProtocol.kt` | 帧头布局常量与编解码。**协议 SSOT 的 Kotlin 侧**，与 `sync_protocol.h` 逐字节对应 |
| `protocol/SyncOpcode.kt` | opcode / flags / 错误码枚举 |
| `protocol/FrameCodec.kt` | 帧编解码 |
| `session/SyncSessionStateMachine.kt` | 显式状态机（下节） |
| `session/SyncSession.kt` | 会话上下文：sessionId、seq、检查点、双端能力 |
| `transport/NativeTransport.kt` | JNI 包装，`suspend` 化，可取消 |
| `transport/DiscoveryService.kt` | UDP 发现 v2，TTL、组播 join、网络切换重发现 |
| `engine/SyncEngine.kt` | 编排；替换 `LanDeviceSyncManager` 的核心逻辑 |
| `engine/DeltaBuilder.kt` | 增量集构建（`getChangesSince` + 墓碑） |
| `engine/EntityMerger.kt` | LWW + version + 墓碑的合并与冲突判定，事务写入 |
| `engine/ResumeStore.kt` | 断点检查点持久化 |
| `engine/IdempotencyGuard.kt` | 会话级与实体级去重 |
| `obs/SyncMetrics.kt` / `obs/SyncLogger.kt` | 指标与结构化日志 |

### 2.3 协议实现方式（已定：C++ 侧引入 protobuf-lite）

**决策**：C++ 侧引入 protobuf-lite（NDK 交叉编译），两端共用同一份 `sync.proto` 生成代码。

- **帧头**：固定 32 字节二进制，C++ (`sync_protocol.h`) 与 Kotlin (`SyncWireProtocol.kt`) 各自实现，用**字节级一致性测试**锁死（同一组测试向量，两端断言相同结果）。帧头不用 protobuf，因为它需要定长、可在流损坏时快速重新定位边界。
- **载荷**：protobuf，**两端均可解析**。同一 `sync.proto` 通过 Gradle protobuf 插件生成 Java，通过 CMake 自定义 target 生成 C++。

引入 protobuf-lite 带来的额外能力：
- C++ 侧可在原生层做流式分片装配与落盘缓冲，大数据集不必整包驻留 JVM 堆
- 心跳、错误、握手载荷在 C++ 侧可直接构造与校验，无需每帧穿越 JNI
- 消除「Kotlin 定义、C++ 猜测」的语义漂移

构建代价（需在 P1 处理）：
1. `libs.versions.toml` 增加 `protobuf-lite` NDK 预构建产物或 vcpkg 依赖
2. `CMakeLists.txt` 增加 `protoc` 生成步骤与 `protobuf::libprotobuf-lite` 链接
3. `abiFilters` 当前仅 `arm64-v8a`，protobuf 交叉编译产物需匹配
4. 包体预计增加约 300~600 KB（lite runtime，启用 `-Os` 与 gc-sections 后）

### 2.4 最小配对鉴权（已定：本期实现）

在 `HELLO` / `HELLO_ACK` 之后插入 `AUTH` / `AUTH_ACK`：

- 首次配对：B 侧展示 6 位随机配对码，用户在 A 侧输入
- 校验值：`HMAC-SHA256(pairing_code, session_id || A.device_id || B.device_id)`，避免配对码明文过网
- 配对成功后双方持久化 `peer_device_id -> shared_secret`（存入 EncryptedSharedPreferences，项目已有 `security-crypto` 依赖）
- 已配对设备后续免输入，直接用存量 secret 完成 `AUTH`
- 配对码 5 分钟有效，连续 3 次校验失败拉黑该 `device_id` 10 分钟
- 用户手动确认对话框保留，作为配对之外的第二道门槛

对应错误码：`AUTH_REJECTED` / `AUTH_TIMEOUT` / `AUTH_RATE_LIMITED` / `PAIRING_REQUIRED`。

---

## 3. 统一协议设计

### 3.1 帧头（32 字节，网络字节序）

| 偏移 | 长度 | 字段 | 说明 |
|---|---|---|---|
| 0 | 4 | `magic` | `0x48465331`（"HFS1"） |
| 4 | 1 | `version` | 协议版本，当前 `2` |
| 5 | 1 | `opcode` | 见 3.2 |
| 6 | 2 | `flags` | 见 3.3 |
| 8 | 8 | `session_id` | 会话唯一标识 |
| 16 | 4 | `seq` | 帧序号，会话内单调递增 |
| 20 | 4 | `payload_len` | 载荷字节数，上限 1 MiB/帧 |
| 24 | 4 | `payload_crc32` | 载荷 CRC32C |
| 28 | 4 | `header_crc32` | 前 28 字节的 CRC32C |

固定长度头 + 头部自校验，使得任何一端都能在流损坏时快速重新同步边界。

### 3.2 opcode

| 值 | 名称 | 方向 | 说明 |
|---|---|---|---|
| 0x01 | `HELLO` | A→B | 版本、设备信息、能力位、期望 sessionId |
| 0x02 | `HELLO_ACK` | B→A | 协商结果、B 侧能力、是否需要用户确认 |
| 0x03 | `AUTH` | A→B | 配对证明：`client_nonce` + HMAC-SHA256 proof |
| 0x04 | `AUTH_ACK` | B→A | 配对判定 + 反向 proof（防反射攻击） |
| 0x10 | `MANIFEST` | 双向 | 增量清单：实体数、总字节、分片数、`since` 水位 |
| 0x11 | `MANIFEST_ACK` | 双向 | 接收方回报已持有的检查点，用于断点续传 |
| 0x12 | `CHUNK` | 双向 | 数据分片 |
| 0x13 | `CHUNK_ACK` | 双向 | 分片确认，携带累计已收 seq |
| 0x14 | `PULL` | A→B | 向对端索取它的增量，按分片索引逐片拉取 |
| 0x15 | `PULL_ACK` | B→A | 对端增量的一个分片，随片携带清单元数据 |
| 0x20 | `COMMIT` | 双向 | 请求落库 |
| 0x21 | `COMMIT_ACK` | 双向 | 落库结果 + 冲突摘要 |
| 0x30 | `PING` | 双向 | 保活 |
| 0x31 | `PONG` | 双向 | |
| 0x40 | `ERROR` | 双向 | 结构化错误码 + 是否可重试 |
| 0x41 | `BYE` | 双向 | 优雅关闭 |

**关于 `PULL` / `PULL_ACK`（P3 新增，原设计中没有）**

传输层是严格的请求/响应模型：一帧进、一帧出，响应方永远无法主动说话。而同步在语义上是双向的——A 要把自己的增量推给 B，也要把 B 的增量取回来。v1 的做法是把 B 的**整库**塞进那一个响应里，这既是「反向不能续传、不能重试、不能限流」的根因，也让反向传输完全绕开了正向那套完整性保证。

`PULL` 把反方向变成一次普通的请求/响应交换：A 按分片索引逐片索取，于是分片、水位、聚合哈希、幂等、断点续传这些机制对两个方向一视同仁。代价是多一对 opcode，换来的是「一次点击完成双向同步」这个既有产品行为不用改，同时反向获得和正向相同的可靠性等级。

### 3.3 flags 位

| 位 | 名称 | 说明 |
|---|---|---|
| 0 | `COMPRESSED` | 载荷经过压缩 |
| 1 | `LAST_CHUNK` | 分片序列结束 |
| 2 | `RESUMED` | 本会话由断点恢复 |
| 3 | `REQUIRE_ACK` | 要求对端确认 |

### 3.4 会话状态机（双端共用同一套状态）

```
IDLE
 ├─ dial/accept ──────────> HANDSHAKING
 HANDSHAKING
 ├─ HELLO_ACK ok ─────────> AUTHORIZING（需确认）或 EXCHANGING_MANIFEST
 ├─ 版本不兼容 ───────────> FAILED(PROTOCOL_MISMATCH)
 AUTHORIZING
 ├─ 用户接受 ─────────────> EXCHANGING_MANIFEST
 ├─ 拒绝/超时 ────────────> FAILED(REJECTED / AUTH_TIMEOUT)
 EXCHANGING_MANIFEST
 ├─ MANIFEST_ACK ─────────> TRANSFERRING
 TRANSFERRING
 ├─ 全部 CHUNK_ACK ───────> COMMITTING
 ├─ 连接中断且可恢复 ─────> RECONNECTING
 RECONNECTING
 ├─ 重连成功 ─────────────> TRANSFERRING（携带 RESUMED + 检查点）
 ├─ 超出重试预算 ─────────> FAILED(NETWORK)
 COMMITTING
 ├─ COMMIT_ACK ok ────────> COMPLETED
 ├─ COMMIT_ACK 失败 ──────> FAILED(APPLY_ERROR)
 任意状态 ─ BYE/取消 ─────> CANCELLED
```

关键点：**A 与 B 运行同一状态机**，只是驱动事件来源不同（A 由本地动作驱动，B 由收到的帧驱动）。这直接消除双端行为不一致。

### 3.5 proto v2 载荷

在现有 `sync.proto` 上**追加**新 message，保留 v1 的 `DeviceSyncData` / `SyncEntity` 不动（向后兼容）。新增：

- `HelloPayload` / `HelloAckPayload`：`protocol_version`、`min_supported_version`、`device_id`、`device_name`、`device_type`、`capabilities`、`app_version`、`trace_id`；`HelloAckPayload` 另有 `requires_user_confirmation`、`error` / `error_message`，以及 `server_nonce`(9)
- `AuthPayload` / `AuthAckPayload`：`client_nonce` / `server_nonce` + 各自方向的 `proof`，`AuthAckPayload` 带 `accepted` 与 `error`
- `ManifestPayload`：`session_id`、`since_watermark`、`total_entities`、`total_bytes`、`chunk_count`、`content_hash`、`chunk_size`
- `ManifestAckPayload`：`accepted`、`resume_from_chunk`、`chunk_size`、`window_size`、`error`
- `ChunkPayload`：`chunk_index`、`entities`（`SyncEntityV2`）
- `ChunkAckPayload`：`acked_through_chunk`、`missing_chunks`、`observed_kbps`、`error`(5) / `error_message`(6)
- `PullPayload` / `PullAckPayload`：`since_watermark`、`chunk_index`、`chunk_size` → `chunk_count`、`total_entities`、`content_hash`、`new_watermark`、`entities`、`error`
- `SyncEntityV2`：结构化字段替代 JSON 字符串，含 `entity_type`、`entity_id`、`operation`（枚举含 `DELETE`）、`updated_at`、`version`、`deleted_at`、`payload`（typed oneof）、`entity_hash`
- `CommitPayload` / `CommitAckPayload`：应用条数、冲突列表（`ConflictSummary` 带 `kept_local` 与双端 `updated_at`，便于事后解释「为什么这条没被覆盖」）
- `ErrorPayload`：`code`、`message`、`retryable`、`offending_seq`

`*_ACK` 一律自带 `error` 字段，而不是让失败走独立的 `ERROR` 帧。传输层是一问一答，若失败改用另一个 opcode 回复，发起方就得在「等 X_ACK」和「等 ERROR」两条路径上都做状态处理，而这两条路径的分叉点恰好是最容易漏测的地方。让每个 ACK 自己表达成败，接收侧只有一种解析路径。

---

## 4. 可靠性机制

| 机制 | 设计 |
|---|---|
| **超时** | 三级：单次 IO 超时（可配，默认 5s）、阶段超时（握手 10s / 传输每分片 15s）、会话总 deadline（默认 5 分钟）。全部用**绝对时间点**而非相对超时，避免累加漂移 |
| **重试** | 指数退避 + 抖动：`min(base * 2^n, cap)` × `random(0.5, 1.5)`，base 500ms、cap 15s、默认 5 次。仅对 `retryable` 错误重试 |
| **断点续传** | 每 N 个分片落一次检查点到 `ResumeStore`（DataStore）。重连时携带 `RESUMED` + 已确认的 `chunk_index`，对端从该点续发 |
| **幂等** | 会话级：`session_id` + `seq` 去重表；实体级：`entity_id` + `updated_at` + `entity_hash` 三元组，已应用则跳过。`COMMIT` 可安全重放 |
| **完整性** | 三层：帧头 CRC32C、载荷 CRC32C、清单级 `content_hash`（全部实体哈希的有序聚合）。任一层不匹配即请求重传该分片 |
| **冲突解决** | 优先级：墓碑优先（删除胜出）→ `version` 大者胜 → `updated_at` 大者胜 → `device_id` 字典序（确定性打破平局）。冲突全部记入 `SyncConflict` 并上报 UI |
| **事务** | 分片应用采用 Room `@Transaction` 批量写入，失败整批回滚 |

---

## 5. 弱网优化

| 场景 | 措施 | 落地（截至 P5） |
|---|---|---|
| 高丢包 | 分片粒度默认 64 KiB，失败只重传单片而非整包；`CHUNK_ACK` 累计确认 | ✅ P3 |
| 高延迟 | 允许 `window_size` 个分片在途（滑动窗口，默认 4），避免停等 | ⚠️ 策略已写未接线，见 9.5 |
| 带宽抖动 | 自适应分片：按最近 3 次 RTT 与吞吐动态在 16 KiB ~ 256 KiB 间调整；连续超时则减半 | ⚠️ 策略已写未接线，见 9.5 |
| 频繁掉线 | `RECONNECTING` 状态 + 断点续传；连接失败保留会话上下文 30s | ⚠️ 状态机与 `resume_from_chunk` 已实现，客户端重连编排未接 |
| Wi-Fi 切换 | 订阅已有的 `NetworkMonitor`；网络变更立即主动断开旧 socket（避免黑洞等待）→ 触发快速重连 → 重新发现 | ⚠️ `SyncScheduler` 已订阅并在恢复联网时触发；主动断开旧 socket 未做 |
| 保活 | TCP keepalive（`TCP_KEEPIDLE` 15s / `TCP_KEEPINTVL` 5s / `TCP_KEEPCNT` 3）+ 应用层 `PING`/`PONG`（空闲 10s 发一次，连续 3 次无响应判定断线） | ✅ P2 `socket_stream.cpp`；`PING` 由 native 直接应答，不上抛 Kotlin |
| 多设备并发 | C++ 服务端固定线程池 + 每连接独立会话 | ✅ P2 |
| 用户确认不阻塞（D2） | JNI 回调立即返回，Kotlin 侧确认完成后再回推 | ⚠️ **未按原设计实现**：`PromptingSyncAuthorizer.confirm()` 用 `CountDownLatch` 同步等待，仍占住一个池线程。见 9.5 偏离表 |
| 压缩 | 载荷 > 4 KiB 时启用压缩，置 `COMPRESSED` 标志位 | ⬜ 仅定义标志位，无压缩实现 |

---

## 6. 可观测性

**错误码体系**（`SyncErrorCode`，双端一致）：
`OK` / `PROTOCOL_MISMATCH` / `AUTH_REJECTED` / `AUTH_TIMEOUT` / `NETWORK_UNREACHABLE` / `CONNECT_TIMEOUT` / `IO_TIMEOUT` / `PEER_CLOSED` / `CRC_MISMATCH` / `PAYLOAD_TOO_LARGE` / `PARSE_ERROR` / `APPLY_ERROR` / `BUSY` / `CANCELLED` / `INTERNAL`。每个错误带 `retryable` 标记。

**结构化日志**：统一前缀 `[sync][<trace_id>][<session_id>][<state>]`，trace_id 贯穿 C++ 与 Kotlin，可在 logcat 中用单一 id 串起整条链路。C++ 侧日志经 JNI 汇入现有 `LogFileManager`。

**指标**（`SyncMetrics`）：
发现耗时 / 发现设备数、握手耗时、各状态停留时长、传输字节数与吞吐、分片重传次数、CRC 失败次数、重连次数、会话成功率、失败原因分布、冲突数量。

**P5 实际落地形态**（详见 9.6）：日志格式最终采用 **logfmt**（`k=v` 空格分隔）而非上述方括号前缀，tag 固定 `HomeMoneySync`，`trace=` 作为普通字段参与关联——理由是方括号嵌套无法表达可变字段集，且值里一旦出现 `]` 就没法机器解析。C++ 侧日志汇入 `LogFileManager` **未做**，目前两端各写各的 logcat，靠 `trace_id` 在同一份 logcat 里关联。

---

## 7. 兼容与迁移

采用**协议协商降级**，不强制同时升级：

1. `HELLO` 帧的魔数与 v1 的「4 字节长度前缀」在首字节即可区分（v1 首 4 字节是长度，v2 是 `0x48465331`）。服务端读取前 4 字节即可判定对端版本。
2. 对端为 v1：走 `LegacyV1Adapter`，保持原有「单次全量请求-响应」语义，但仍修复 F1~F4 的数据正确性问题。
3. 对端为 v2：走完整新链路。
4. UDP 发现报文同时广播 v1 明文格式与 v2 结构化格式（双发一段时间），v2 报文带 `min_supported_version`。
5. ~~两个版本后移除 v1 适配层。~~ 「两个版本后」是个拍脑袋的期限——它假设用户会升级，而局域网同步的对端往往是家里那台没人管的旧平板。P5 改为**用数据判定**：以 `discoveryRepliesLegacy` 与 `legacySessions` 两个计数跨发布归零为下线信号。完整下线计划见 **9.6.4**。

**对上层接口零破坏**：`DeviceSyncManager` 接口签名保持不变，`SettingsViewModel` / `LanSyncScreen` 无需改动。新增能力通过接口的默认方法扩展（如 `observeSyncState(): Flow<SyncState>`）。

`respondToSyncRequest` 从死代码修复为真正生效的实现。

---

## 8. 测试策略

**单元测试（`app/src/test/`）** — 括号内为实际落地情况

- `SyncWireProtocolTest`：帧头编解码、边界值、损坏头拒绝 ✅
- `ProtocolConformanceTest`：与 C++ 侧逐字节比对 ✅（实现为**直接读取 `sync_protocol.h` 源文件**解析常量，而非维护一份手抄向量）
- `SyncSessionStateMachineTest`：全状态迁移矩阵，含非法迁移拒绝 ✅
- `ExpenseMergerTest`：LWW / version / 墓碑 / 平局打破 / **F1 回归用例** ✅（原计划名 `EntityMergerTest`；实体只有 expense 一种，故随实体命名）
- `DeltaBuilderTest`：增量水位、墓碑包含、**F3 回归用例** ✅（P5 补齐，31 项。直接针对 v1 三大缺陷建用例：空集不产生空 chunk、删除走 `SYNC_OPERATION_DELETE` 而非被过滤、水位只前进不回退。另覆盖哈希的排列敏感性与分片边界，见 9.6.2）
- `IdempotencyGuardTest`：重复分片、重放 COMMIT ✅
- `SyncRetryPolicyTest`：退避序列、抖动边界、预算耗尽 ✅（原计划名 `RetryPolicyTest`）
- `SyncResponderTest`：响应方全部协议语义 ✅（P3 新增，原计划未列）
- `NativeSyncEngineJniContractTest`：JNI 上行回调名与描述符 ✅（P3 新增，原计划未列；动机见 9.4）
- `DiscoveryPacketTest` / `DiscoveryDeciderTest` / `DiscoveryRegistryTest` / `LocalNetworkAddressesTest`：发现层 v2 报文、准入判定、TTL 注册表、多网卡本机识别 ✅（P4 新增，共 106 项，见 9.5）
- `AdaptiveChunkPolicyTest`：分片与窗口的收敛、钳制、参数校验 ✅（P4 新增，36 项。**注意：被测类本身尚未接线**，见 9.5 偏离表）
- `SyncMetricsTest` / `SyncObservabilityTest`：计数器并发安全与 logfmt 日志的可解析性 ✅（P5 新增，55 项，见 9.6）

**异常场景测试** — P5 结束时的真实覆盖

| 场景 | 状态 | 说明 |
|---|---|---|
| 载荷超限 → 拒绝且不 OOM | ✅ | `frame_codec` 1 MiB 上限，`transport_conformance` 覆盖 |
| CRC 损坏 → 拒绝并重新对齐边界 | ✅ | 同上；「触发重传并最终成功」的闭环仍需端到端验证 |
| 用户确认超时 → 会话进入 FAILED | ✅ | `SyncResponderTest` 鉴权用例 |
| 时钟回拨 → 不丢数据 | ✅ | P4/P5 补齐至四处：`ExpenseMergerTest`「version 胜过墙钟」（回拨设备不会静默丢编辑）、`DeltaBuilderTest`「水位不回退」、`DiscoveryRegistryTest`「时钟倒跳不清空注册表」、`SyncMetrics/SyncObservability`「时长不为负」。双设备端到端仍未验证 |
| 发现层抗噪 | ✅ | P4 新增：自身回环、伪造 ip、超长/截断报文、TTL 过期、同设备换 ip，见 9.5 |
| 并发同步不串数据 | ⚠️ 部分 | `SyncResponderTest` 覆盖 2 设备会话隔离；3 设备与「确认弹窗不阻塞其他连接」未验证——后者现在已知会阻塞一个池线程（见 9.5 偏离表） |
| 传输中途断开 → 续传后一致 | ⬜ | 续传机制已实现（`resume_from_chunk`、分片缓存），`DeltaBuilderTest` 覆盖 `remainingChunks` 边界，端到端未验证 |
| 同步中切换 Wi-Fi → 快速恢复 | ⬜ | 需仪器测试 |
| 对端为 v1 → 降级路径正常 | ⬜ | v1 适配层保留且未改动，C++ 侧降级逻辑（`performSyncWithRetry`）无自动化验证 |

**仪器测试仍是空白（P5 未兑现项）**。`androidTest/` 下只有 `AppDatabaseTest.kt` 与模板遗留的 `ExampleInstrumentedTest.java`，同步相关一项没有。P5 原计划的「补齐仪器测试」实际只完成了 JVM 单测部分（86 项新增），双设备端到端与 `tc netem` 弱网注入**未开工**。

这里不含糊地记一笔，是因为「机制已实现」和「已验证」之间的差距，正是最容易在交付时被糊弄过去的地方——而上表剩下的 3 个 ⬜ 恰好全是弱网与兼容性，也就是这套东西最可能真出问题的地方。它们需要两台真机 + 可控网损环境，不是补几个 JVM 用例能顶替的。

**C++ 侧** ✅ 已落地，但位置与原计划不同：自检代码放在 `app/src/main/cpp/protocol/protocol_conformance.cpp`（167 行）与 `transport/transport_conformance.cpp`（554 行），随主库一起编译，而非独立的 `app/src/test/cpp/` + CTest。这样 NDK 构建本身就是一道检查，不必额外维护一条 CTest 工具链。覆盖 `frame_codec` 分片装配、EINTR 注入、坏帧恢复、CRC 与退避向量。

**仪器测试（`androidTest/`）**
- 双设备端到端；用 `tc netem` 模拟 20% 丢包 / 300ms 延迟 / 带宽限制

---

## 9. 实施阶段

| 阶段 | 内容 | 风险 | 可独立交付 | 状态 |
|---|---|---|---|---|
| **P0** | 修复 F1~F4 数据正确性 bug（不改架构，纯语义层） | 低 | 是，可立即发版 | ✅ 已完成（9.1） |
| **P1** | 协议 SSOT：`sync_protocol.h` + `SyncWireProtocol.kt` + proto v2 + NDK protobuf-lite 接入 + 一致性测试 | 中（构建链改动） | 是 | ✅ 已完成（9.2） |
| **P2** | C++ 传输层重写：socket_utils / frame_codec / connection / server 线程池 / client 退避 / jni_bridge 异步化 | 中 | 是（v1 适配层保证不破坏） | ✅ 已完成（9.3） |
| **P3** | Kotlin 会话状态机 + 同步引擎（增量、合并、幂等、续传）+ 最小配对鉴权 | 中高 | 是 | ✅ 已完成（9.4） |
| **P4** | 发现层 v2 + 弱网优化（自适应分片、窗口、保活、网络切换） | 中 | 是 | ✅ 部分完成（9.5）——发现层 v2 全量落地；自适应分片**已写未接线** |
| **P5** | 可观测性 + 测试补齐 + v1 适配层下线计划 | 低 | 是 | ✅ 部分完成（9.6）——可观测性与单测落地；**仪器测试未开工** |

每阶段结束后 v1 链路仍可用，随时可停。

**关于 P4/P5 标「部分完成」**：两个阶段的主线产出都已合入且全绿，但各自有一块明确没做完——P4 的 `AdaptiveChunkPolicy` 写完测完却没接进发送路径，P5 的仪器测试一项没写。把它们标成 ✅ 会让后来人以为弱网这块已经收口，而实际上**弱网优化目前一行都没有真正跑在生产路径上**。详见各自的偏离表。

### 9.1 P0 明细：数据正确性

| 项 | 改动 | 文件 |
|---|---|---|
| F1 | 用本地记录真实 `updatedAt` 参与比较，替换 `System.currentTimeMillis()` | `BaseDeviceSyncManager` |
| F2 | 传输时携带实体真实 `updatedAt`，不再覆盖为当前时间 | `BaseDeviceSyncManager` |
| F3 | 增量集包含软删除墓碑，`operation` 正确标记 `DELETE` | `BaseDeviceSyncManager` + `ExpenseDao` |
| F4 | 引入确定性合并策略替代盲目 REPLACE | 新增 `merge/ExpenseMerger.kt` |
| F6 | 批次内同 id 去重 | `BaseDeviceSyncManager` |
| D5 | 批量事务写入替代逐条插入 | `BaseDeviceSyncManager` |

合并优先级（双端一致，确定性）：
`墓碑优先` → `version 大者胜` → `updatedAt 大者胜` → `deviceId 字典序大者胜`

P0 与 v1 对端保持线上兼容：墓碑以「带 `deletedAt` 的普通记录」形式传输，旧版本收到后其查询层天然过滤，删除语义自动生效。

### 9.2 P1 明细：协议 SSOT

| 产出 | 文件 | 说明 |
|---|---|---|
| C++ 协议定义 | `cpp/protocol/sync_protocol.h` | 帧头布局、opcode、flags、错误码的唯一来源 |
| Kotlin 镜像 | `protocol/SyncOpcode.kt`、`protocol/SyncWireProtocol.kt` | 逐条对应 §3.1~§3.3 的定义，注释写明「不得重编号，只能追加」 |
| v2 载荷 | `proto/sync_v2.proto` | 与 v1 的 `sync.proto` 并存，互不影响 |
| CRC-32C | `cpp/protocol/crc32c.h`、`protocol/Crc32c.kt` | Castagnoli 多项式，双端同一套向量 |
| 退避策略 | `cpp/transport/retry_policy.h`、`protocol/SyncRetryPolicy.kt` | 含抖动，双端同一套向量 |
| 一致性验证 | `protocol/ProtocolConformanceTest.kt`、`cpp/protocol/protocol_conformance.cpp` | 见下 |

**双端一致性怎么保证不靠人盯**：`ProtocolConformanceTest` 不比对一份手抄的期望值，而是**直接读取 `sync_protocol.h` 源文件**并解析其中的常量，再和 Kotlin 侧逐个比对。任何一端改了数值而另一端没跟上，测试立刻红。同时 `frame_vectors_generated.h` / `retry_vectors_generated.h` 提供固化的黄金向量，让 C++ 侧也能在没有 JVM 的环境下自证。

这个「读源码而非抄常量」的模式在 P3 被复用（见 9.4 的 JNI 契约测试），它是本次重构里唯一能真正防住**静默漂移**的手段——静默漂移的特征是编译通过、链接通过、只在运行时表现为「对端版本太旧」，人工 review 基本抓不住。

### 9.3 P2 明细：C++ 传输层

| 产出 | 文件 | 行数 | 职责 |
|---|---|---|---|
| 字节流抽象 | `transport/byte_stream.h` | 158 | 读写接口，便于用内存流做单测 |
| Socket 实现 | `transport/socket_stream.{h,cpp}` | 111 / 350 | 超时、EINTR 重试、部分读写循环 |
| 帧编解码 | `transport/frame_codec.h` | 316 | 头部校验、载荷 CRC、1 MiB 上限、坏帧后重新对齐边界 |
| 结果类型 | `transport/io_result.h` | 69 | 显式错误码，不用 errno 裸传 |
| 退避 | `transport/retry_policy.h` | 121 | 与 Kotlin 侧同向量 |
| 线程池 | `transport/thread_pool.{h,cpp}` | 69 / 94 | 服务端并发接入，避免 per-connection 裸线程 |
| 一致性测试 | `transport/transport_conformance.cpp` | 554 | 分片装配、EINTR 注入、坏帧恢复 |

同时 JNI 面新增 3 个导出：`configureTransport(connectTimeoutMs, ioTimeoutMs, maxAttempts)`、`lastErrorCode()`、`transportStats()`，把原先硬编码在 C++ 里的超时参数交给上层调，并让失败原因可以被 Kotlin 侧读到而不是只能翻 logcat。

v1 链路通过 `handleIncomingSyncRequest` 适配层原样保留，P2 上线后旧对端行为不变。

### 9.4 P3 明细：Kotlin 会话层与同步引擎

**新增文件**

| 模块 | 文件 | 职责 |
|---|---|---|
| 会话 | `session/SyncSessionStateMachine.kt` | 10 状态 / 12 事件，非法迁移显式拒绝而非默默忽略 |
| | `session/SyncSession.kt` | 单会话状态 + `serialized {}` 串行化临界区 |
| | `session/SyncSessionRegistry.kt` | 并发会话上限、按 `sessionId` 查找、过期清理 |
| 引擎 | `engine/SyncResponder.kt` | 帧分派与全部协议语义（响应方） |
| | `engine/DeltaBuilder.kt` | 按水位取增量、分片、算聚合哈希 |
| | `engine/EntityFingerprint.kt` | 规范字节编码 + CRC-32C 单体哈希 + SHA-256 聚合哈希 |
| | `engine/IdempotencyGuard.kt` | 三层去重：帧级 `(sessionId, seq)`、修订级 `(entityId, updatedAt, entityHash)`、COMMIT 结果缓存 |
| | `engine/EntityApplier.kt` | 事务落库，复用 P0 的 `ExpenseMerger` |
| | `engine/WireEntityMapper.kt` | proto ↔ Room 双向映射，含指纹校验 |
| | `engine/SyncEntityStore.kt` | 窄接口 + `RoomSyncEntityStore` 实现，让引擎可脱离 Room 测试 |
| | `engine/SyncErrorMapping.kt` | 异常 → 结构化错误码 |
| | `engine/SyncResponderObserver.kt` | 观测钩子，为 P5 预留 |
| 鉴权 | `auth/SyncPairing.kt` | HMAC-SHA256 配对证明（纯函数、无 Android 依赖） |
| | `auth/SyncAuthorizer.kt` | 两道闸门的抽象，默认 `DENY_ALL` |
| 接线 | `transport/SyncFrameHandler.kt` | native → Kotlin 的帧入口 |

**幂等与重放（D4）**

三层去重，各管一件事：

| 层 | 键 | 作用 |
|---|---|---|
| 帧级 | `(sessionId, seq)` | 识别对端重发的同一帧 |
| 修订级 | `(entityId, updatedAt, entityHash)` | 同一条记录的同一版本只落库一次 |
| 提交级 | `sessionId → CommitRecord` | `COMMIT` 重放时返回上次结果，不重复落库 |

两个反直觉但必要的决策：

1. **重复帧不跳过，照常重算并回复。** 对端重发是因为它没收到上次的回复，它需要的正是那个回复；直接丢弃只会让它一直重试到超时。之所以敢重算，是因为下面两层保证了重算不产生副作用——重复检测因此只用于观测，不用于控制流。
2. **修订键是三元组，不是 `(entityId, updatedAt)`。** 两台设备完全可能在同一毫秒内对同一条记录写入不同内容；只看时间戳会把其中一次静默丢弃。加上 `entityHash` 后，真正的后续编辑（时间戳或内容任一不同）不会被误过滤，只有逐字节相同的重放才会。

**最小配对鉴权（`AUTH` / `AUTH_ACK` 落地形态）**

```
A -> B  HELLO      capabilities 含 PAIRING
B -> A  HELLO_ACK  server_nonce
A -> B  AUTH       client_nonce, clientProof(code, cn, sn, sessionId)
B -> A  AUTH_ACK   server_nonce, serverProof(code, cn, sn, sessionId)
```

三个承重细节，缺一个整套就形同虚设：

1. **方向标签**。两个 proof 用不同 tag（`"HFS1 client proof"` / `"HFS1 server proof"`）。否则两者字节相同，攻击者把 A 的 proof 原样反射回去就能冒充 B，全程不需要知道配对码。
2. **长度前缀 nonce**。裸拼接会让 `("AB","C")` 与 `("A","BC")` 哈希相同，控制其中一个 nonce 的攻击者可以移动边界复用抓到的 proof。
3. **会话绑定**。`sessionId` 参与 MAC，跨会话重放无效。

比对用 `constantTimeEquals`（定长 XOR 累积），不用 `contentEquals`——后者首字节不同即返回，会泄漏「猜对了几个字节」。局域网上这个时序信号很嘈杂，但消除它的成本只是几次异或。

配对码在 HMAC 前经 `normalizeCode` 归一（去空格与连字符、转大写），因为用户是照着另一块屏幕手抄的。归一规则只存在于这一处，两端共用。

失败预算 `MAX_AUTH_ATTEMPTS = 3`，用尽后返回 null 直接断连，让暴力猜解每三次就要付一次完整 TCP 握手加 HELLO 往返的代价。

**注意边界**：这是配对校验，不是加密信道。载荷在局域网上仍是明文。它挡的是冒充和对同步端口的随手窥探，这也正是家庭网络下真实存在的威胁。加密正文明确不在本阶段范围内，单独跟踪。

**两道闸门的关系**：配对（密码学，`pairingCode`）与确认（人工弹窗，`confirm`）是**互相独立**的。`isTrusted` 只跳过弹窗，不豁免证明。未配置配对码时 `pairingCode()` 返回 null，退化为 v1 的纯弹窗行为，保证升级不打断既有用户。

**相对原设计的偏离（均为实现中发现的必要修正）**

| 偏离 | 原因 |
|---|---|
| 新增 `PULL`(0x14) / `PULL_ACK`(0x15) | 传输层严格一问一答，响应方无法主动发送。详见 §3.2 下方说明 |
| `ChunkAckPayload` 增加 `error`(5) / `error_message`(6) | 让每个 ACK 自带成败，接收侧只有一条解析路径。详见 §3.5 末尾 |
| `HelloAckPayload` 增加 `server_nonce`(9) | 把 nonce 搭在 HELLO_ACK 上，配对握手省去一个往返 |
| `session_id` 归发起方所有 | native 用请求头重建每个响应头，响应方无法改写。因此 `HELLO` 里 `session_id == 0` 直接拒绝——0 正是未初始化头部的样子，放行会把不相关的对端并进同一会话 |
| 哈希用手写规范编码，不用 protobuf 字节 | protobuf 序列化并非规范形式：字段顺序、默认值省略、varint padding 在不同运行时/版本间都可能合法地不同。直接哈希 `toByteArray()` 会让两台设备对同一份数据算出不同哈希，每次同步都像损坏。编码布局完整写在 `EntityFingerprint` 的注释里，任何语言照做都能复现同样字节 |
| 聚合哈希带下标参与 SHA-256 | 纯 XOR 或求和对排列不敏感，检测不出整块分片丢失或乱序——而每个存活帧的 CRC 都仍然正确，这正是分片级错误逃逸的路径 |
| `remark` 在哈希前 null → `""` 归一 | Room 可空、proto3 的 `string` 不可空。不归一的话，每条 remark 为 null 的记录一过线就「看起来变了」 |
| 增量按会话缓存 | 一次 `PULL` 建好后续片直接取缓存。每片重算是**正确性** bug 而非性能问题：用户在第 3 片和第 4 片之间记一笔账，分片会重新编号，对端正在校验的聚合哈希随之失效 |

**接线到 native**

`NativeSyncEngine` 补上 v2 上行回调：

```kotlin
@Keep
fun handleIncomingFrame(
    peerAddress: String, opcode: Int, sessionId: Long, seq: Int, payload: ByteArray
): ByteArray?
```

`LanDeviceSyncManager` 在 `startSyncServer()` 里于 `startServer(...)` **之前**装载 handler，`stopSyncServer()` 里卸载并清空会话。`PromptingSyncAuthorizer` 把 `SyncAuthorizer` 桥接到既有的 `SyncRequestCallback`：`confirm()` 切回主线程弹窗，用 `CountDownLatch` 阻塞 native 工作线程直到用户响应或超时。

**JNI 契约测试（新增，`NativeSyncEngineJniContractTest.kt`）**

这个测试是为一类特定事故写的：native 在 `startServer` 时按**方法名 + JVM 描述符字符串**解析上行回调，不是链接期绑定。Kotlin 侧改名、调序、换参数类型，**编译和链接全都正常**，只在运行时 logcat 里留下一行 `handleIncomingFrame is missing`，然后服务端静默拒绝所有 v2 帧、却仍正常应答 v1——现场表现完全就是「对端版本太旧」。这是整个 JNI 缝里最难查的失败模式。

测试直接读 `native-lib.cpp`，解析其中所有 `GetMethodID` 调用与所有 `Java_..._NativeSyncEngine_*` 导出符号，再用反射比对 Kotlin 侧的实际方法与描述符：

- 上行回调集合必须恰好是 `{handleIncomingFrame, handleIncomingSyncRequest}`
- `handleIncomingFrame` 描述符必须是 `(Ljava/lang/String;IJI[B)[B`
- 每个导出的 JNI 符号必须有对应的 `external fun`，反之亦然

已做变异验证：把 `handleIncomingFrame` 改名后编译链接均通过（复现真实事故），契约测试如期报 2 条失败；改回后全绿。

**验证结果**

单元测试 136 项全绿，0 失败 0 跳过。分布：

| 测试 | 项数 | 覆盖 |
|---|---|---|
| `SyncResponderTest` | 35 | 握手、幂等/D4、阶段守卫、完整性、拉取、鉴权、配对、准入、并发会话隔离 |
| `SyncWireProtocolTest` | 22 | 帧编解码、CRC、边界与坏帧 |
| `BaseDeviceSyncManagerTest` | 15 | P0 的 F1~F4 / F6 / D5 回归 |
| `IdempotencyGuardTest` | 13 | 帧级与修订级去重 |
| `ExpenseMergerTest` | 12 | 合并优先级的确定性 |
| `SyncSessionStateMachineTest` | 12 | 全量 状态 × 事件 矩阵 |
| `SyncRetryPolicyTest` | 10 | 退避向量与抖动边界 |
| `Crc32cTest` | 7 | CRC-32C 向量 |
| `ProtocolConformanceTest` | 5 | 读 `sync_protocol.h` 比对 Kotlin 常量 |
| `NativeSyncEngineJniContractTest` | 4 | 读 `native-lib.cpp` 比对 JNI 描述符 |
| `ExampleUnitTest` | 1 | 模板遗留，与同步无关 |

其余验证：

- `externalNativeBuildDebug` 构建成功，6 个 JNI 符号确认导出
- `javap -s` 确认 Kotlin 侧编译产物的描述符与 native 的 `GetMethodID` 字符串逐字节相同

**工具链注意**：`build.gradle.kts` 设了 `sourceCompatibility = VERSION_25`。Android Studio 用自带 JBR 编译没问题，但命令行若 `JAVA_HOME` 指向 JDK 17，会在 `compileDebugJavaWithJavac` 阶段报「无效的源发行版：25」——而 Kotlin 编译任务本身是能过的，所以报错点看着与改动无关。命令行跑 gradle 前先设 `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`。另外 Git Bash 下 `gradlew.bat` 会因 cygpath 转换失败，需改用 PowerShell。

### 9.5 P4 明细：发现层 v2 与弱网优化

**新增文件**

| 模块 | 文件 | 行数 | 职责 |
|---|---|---|---|
| 报文 | `discovery/DiscoveryPacket.kt` | 289 | v2 二进制报文编解码 + v1 明文兼容 |
| 判定 | `discovery/DiscoveryDecider.kt` | 161 | 纯函数：收到一个报文该忽略、该回复、还是该登记 |
| 注册表 | `discovery/DiscoveryRegistry.kt` | 136 | TTL 过期 + 地址变更替换 + 容量上限 |
| 本机地址 | `discovery/LocalNetworkAddresses.kt` | 154 | 多网卡枚举与择优，纯逻辑与 JDK 调用分离 |
| 服务 | `discovery/LanDiscoveryService.kt` | 436 | 收发循环、v1/v2 双发、遥测出口 |
| 弱网 | `transport/AdaptiveChunkPolicy.kt` | 177 | 分片大小与在途窗口（**未接线**） |

**v1 发现的三个缺陷**

v1 把 `"DISCOVERY|<id>|<name>|<ip>|<ts>"` 明文广播出去。三个问题都属于「只在真实网络里才疼」的那类：

1. **没有魔数、没有版本号。** 任何恰好含竖线的 UDP 噪声都会被解析成一台设备。12345 是个很多人随手用的端口，一个走错的包就能在用户的设备列表里留下一个幽灵。
2. **没有端口。** 接收方只能假设对端监听在自己硬编码的那个端口上。两个不同构建的设备互相看得见却连不上——用户看到的现象是「找到了，但同步用不了」。
3. **请求和响应字节相同**，只靠「从哪个 socket 收到」区分。设备一旦有第二块网卡就会收到自己的广播并回复自己，而这个回复在别人眼里和一次全新的查询完全一样。

v2 因此是带魔数、显式类型、真实端口和 nonce 的二进制帧：24 字节定长头（`magic(4) version(1) type(1) flags(2) port(2) minVersion(2) caps(4) nonce(8)`）后接三个长度前缀字符串。魔数取 `"HFSD"`（`0x48465344`），与 TCP 帧的 `"HFS1"` 刻意不同——两条链路的报文误入对方解析器时应该立刻失败，而不是碰巧通过头部检查。

**512 字节上限不是随手定的。** 它远在 1500 字节 MTU 之下，为的是让发现报文永不分片。UDP 分片是全有或全无：拥塞的 Wi-Fi 上丢掉任意一个分片，整个通告就静默消失，而发送方完全不知情。

**nonce 解决的是「过期回复复活已清空的列表」**：搜索方忽略携带非本轮 nonce 的通告。没有它，用户点「清空」后上一轮的迟到回复会把列表重新填上，看起来就像清空按钮坏了。

**前向兼容契约**：解析器接受任何 `version >= MIN_COMPATIBLE_VERSION` 并**忽略尾部多余字节**。未来的 v3 只要遵守「24 字节头布局不变、三个字符串顺序不变、新字段一律追加在后面」，老构建就仍能发现它。这条契约写死在 `DiscoveryPacket` 的注释里，因为破坏它的后果是静默脑裂——两个版本的设备互相看不见，且没有任何一端会报错。

**判定与副作用分离**：`DiscoveryDecider` 是纯函数，输入一个报文输出 `Ignore/Reply/Register` 三选一，`LanDiscoveryService` 只负责执行。这样「自身回环该不该忽略」「伪造 ip 该不该采信」这类判定全都能在 JVM 上穷举，不需要真的架两台设备。

**注册表从「只增不删的 Map」改为带 TTL 的表**。v1 的 `ConcurrentHashMap` 只 put 不 remove，用户日常就会撞上两个后果：离网设备变成幽灵，点进去只有一个没有解释的连接超时；Wi-Fi 切换后由于 `containsKey` 短路，**保留的恰恰是那个过期 IP**——用户唯一看得见的条目，正是唯一连不上的那个。现在条目会过期，地址变更走替换而非丢弃。时间由外部传入而不是内部读时钟，过期逻辑因此不用 sleep 就能测。

**本机地址从「取第一个非回环 IPv4」改为「比对全部本机地址」**。手机上 `tun0`（VPN）、`ap0`（热点）、`wlan0` 可能同时在线，而枚举顺序平台并不保证。选错网卡直接导致两个 bug：自己的广播不再匹配「本机 IP」，于是设备把自己列成了对端；以及往点对点 VPN 里广播——既到不了任何人，在计费链路上还不免费。修法是两部分：判重时比对**每一个**本机地址；确实需要指定单个地址时，按网卡属性刻意挑选而不是拿到手就用。

**自适应分片的算法**（`AdaptiveChunkPolicy`）：两条规则，不对称是重点。

- **成功时缓调。** 目标是让一个分片的确认耗时落在 `targetChunkDurationMs` 附近，每步变化钳制在 2 倍以内。不阻尼的话，一次 GC 停顿或一次重传这样的孤立样本就能把分片大小甩过整个值域，链路会把时间花在震荡而不是传输上。
- **超时立即减半**（分片与窗口同时减半）。超时不是一个有噪声的测量值，它是「当前尺寸不合适」的确证。这里退让得慢，意味着还要再丢几个完整分片才能收敛到能用的尺寸。

**相对原设计的偏离**

| 偏离 | 原因 |
|---|---|
| **`AdaptiveChunkPolicy` 已写已测但未接线** | 它需要 per-chunk 的 RTT 观测点，而当前发送路径在 C++ 侧（`performSyncWithRetry` 一次性收发），Kotlin 侧的 `DeltaBuilder` 只在建增量时决定一次分片大小。真正接上需要把分片循环从 C++ 上移到 Kotlin 会话层，或者在 JNI 面加一组逐片回调——两者都超出 P4 的范围。**当前生产路径仍是固定分片、无窗口。** 这是 P4 最大的一块未兑现，单独跟踪 |
| 滑动窗口同上 | `windowSize` 与分片大小由同一个策略对象持有，一起未接线 |
| 用户确认仍同步阻塞 | §5 原设计写的是「JNI 回调立即返回，Kotlin 确认完成后回推」。实际 `PromptingSyncAuthorizer.confirm()` 用 `CountDownLatch` 等到用户响应或超时，占住一个 native 池线程。线程池（P2）把影响从「阻塞整个服务端」摊薄成「占用 1/N 线程」，但 D2 并未按原设计根除。改成真异步要求 native 支持「挂起一个连接、稍后回推响应」，即传输层从一问一答改为可延迟应答，代价大于收益，暂缓 |
| 压缩未实现 | `COMPRESSED` 标志位在 `SyncOpcode.kt` 里定义了，无人置位。家庭账本的增量集通常只有几 KiB，压缩收益小于引入一条新失败路径的风险 |
| v1 明文发现保留双发 | 见 9.6.4 的下线计划 |

**验证结果**：新增 142 项单测全绿——`DiscoveryPacketTest` 36（编解码、截断、超长字段、伪造魔数、前向兼容尾部字节）、`AdaptiveChunkPolicyTest` 36（收敛、钳制、超时减半、参数校验）、`DiscoveryDeciderTest` 25（自身回环、nonce 不匹配、v1 准入开关）、`LocalNetworkAddressesTest` 23（VPN/热点/多网卡拓扑择优）、`DiscoveryRegistryTest` 22（TTL 过期、地址变更替换、容量上限、时钟倒跳不清空）。

### 9.6 P5 明细：可观测性与测试补齐

#### 9.6.1 可观测性落地

| 产出 | 文件 | 行数 | 职责 |
|---|---|---|---|
| 指标 | `telemetry/SyncMetrics.kt` | 316 | 无锁计数器 + 快照 + 人读格式化 |
| 观测实现 | `telemetry/SyncObservability.kt` | 258 | `MetricsResponderObserver` / `MetricsDiscoveryTelemetry` |
| 日志落地 | `telemetry/LogcatSyncLogSink.kt` | 41 | `SyncLogSink` → logcat，独立成文件 |

**为什么日志出口要单独抽一个 `SyncLogSink`**：observer 里没有任何 Android 依赖，于是 55 项遥测测试全部能在 JVM 上跑，不需要 Robolectric 也不需要模拟器。日志内容的正确性——尤其是下面那条注入防护——是可以断言的，前提是日志不直接调 `android.util.Log`。

**日志格式选 logfmt 而非原计划的方括号前缀**。`[sync][trace][session][state]` 这种嵌套前缀有两个死结：字段集是可变的（发现层的行根本没有 session），而值里一旦出现 `]` 就无法机器解析。logfmt 的 `k=v` 空格分隔没有这个问题，且 `trace=` 作为普通字段一样能 grep 关联。

**一条被专门测试覆盖的攻击面**：设备名是对端可控的字符串。`name=Bob's Phone` 会在空格处把一行劈成两个字段；更糟的是对端把设备名设成 `x\ntrace=deadbeef`，就能在日志里伪造一整行、把自己的失败记到别的会话头上。因此所有可能含空格或引号的值一律加引号并转义，`SyncObservabilityTest` 里有三个用例专门盯这件事（含引号名、注入换行、空值 `detail=""` 仍可解析）。局域网上这算不上高危，但日志是排障时唯一的依据，让它可被对端污染是件很蠢的事。

**指标全部是 `AtomicLong`，枚举键预先展开成 EnumMap**。同步的收发循环在 native 工作线程上，UI 在主线程读快照；用锁会把观测代价压到热路径上。唯一的例外是 `errorsByStage`（`ConcurrentHashMap`），因为 stage 是字符串、集合不封闭——它带 33 个键的上限，溢出并入 `"other"`，避免对端可控的字符串把 map 撑爆。

**接线点**（`LanDeviceSyncManager`）：`metrics = SyncMetrics()`（对外可读）、`logSink = LogcatSyncLogSink()`，发现层接 `MetricsDiscoveryTelemetry(metrics, logSink)`，响应方接 `MetricsResponderObserver(metrics, logSink)`。P3 预留的 `SyncResponderObserver` 钩子到这里才真正有了实现。

**未做**：C++ 侧日志汇入 `LogFileManager`（§6 原计划）。目前两端各写各的 logcat，靠 `trace_id` 在同一份 logcat 里关联——够用于开发期排障，但用户报障时拿不到结构化日志文件。

#### 9.6.2 `DeltaBuilderTest`（P3 欠账，31 项）

P3 把它标了 ⬜ 并说「补齐成本低」。补的时候确认了成本确实低（`DeltaBuilder` 是纯类），但覆盖的东西不低——它直接对着 v1 的三个数据缺陷建用例：

- **空集不产生空 chunk**，但仍产出带聚合哈希的 manifest。空同步和「同步完了但什么都没发」在协议上必须能区分。
- **删除走 `SYNC_OPERATION_DELETE`**，墓碑仍带 body（对端要靠 body 里的 `updatedAt` 参与合并），活行带 `UPSERT` 且 `deletedAt=0`。这是 F3 的直接回归。
- **水位只前进不回退**，零水位等于全量。

另有两组容易漏的：**哈希的排列敏感性**（shuffle 输入行得到同一 hash，因为 builder 内部按 `(updatedAt, id)` 排序；但删掉任意一行 hash 必变）——这验证了 §9.4 里「聚合哈希带下标」那个决策真的生效；以及**分片边界**（超长单行独占一片不丢、分片不重不漏、`chunkSize` 越界钳制、`remainingChunks` 在 -99/-1/0/满 四种输入下都不越界）。

#### 9.6.3 测试总览

单元测试 **364 项全绿**，0 失败 0 跳过，19 个测试类。较 P3 的 136 项净增 228（P4 142 + P5 86）。

| 测试 | 项数 | 阶段 |
|---|---|---|
| `DiscoveryPacketTest` | 36 | P4 |
| `AdaptiveChunkPolicyTest` | 36 | P4 |
| `SyncResponderTest` | 35 | P3 |
| `DeltaBuilderTest` | 31 | P5 |
| `SyncObservabilityTest` | 29 | P5 |
| `SyncMetricsTest` | 26 | P5 |
| `DiscoveryDeciderTest` | 25 | P4 |
| `LocalNetworkAddressesTest` | 23 | P4 |
| `DiscoveryRegistryTest` | 22 | P4 |
| `SyncWireProtocolTest` | 22 | P1 |
| `BaseDeviceSyncManagerTest` | 15 | P0 |
| `IdempotencyGuardTest` | 13 | P3 |
| `ExpenseMergerTest` | 12 | P0 |
| `SyncSessionStateMachineTest` | 12 | P3 |
| `SyncRetryPolicyTest` | 10 | P1 |
| `Crc32cTest` | 7 | P1 |
| `ProtocolConformanceTest` | 5 | P1 |
| `NativeSyncEngineJniContractTest` | 4 | P3 |
| `ExampleUnitTest` | 1 | 模板遗留 |

**仪器测试 0 项**（见 §8）。这是 P5 名义范围内最大的缺口。

**两个写测试时踩到的坑，记下来免得再踩**：

1. **`assertEquals(2, map[key])` 会静默失败。** 指标的 map 查表返回 `Long?`，字面量 `2` 装箱成 `Integer`，`Integer(2) != Long(2)`，但两边类型都对得上、编译无警告。必须写 `assertEquals(2L, ...)`。这类断言错了不会报「类型不符」，只会报「期望 2 实际 2」，极难看出来。
2. **`SyncErrorCode` 没有 `UNSUPPORTED_VERSION`**，版本不匹配对应的是 `PROTOCOL_MISMATCH`。写测试时按记忆拼枚举名会编译失败——这次是好事，但同一类记忆偏差发生在字符串键上就不会有编译器兜底。

#### 9.6.4 v1 适配层下线计划

**为什么不按原计划的「两个版本后移除」**：那个期限假设用户会升级。局域网同步的对端常常是家里那台没人管的旧平板——它可能两年不更新，而它恰恰是这个功能存在的理由。用版本号数期限，等于在赌一件我们既不能控制也观测不到的事。

P5 把判据换成数据：埋点已经能区分每一次交互走的是 v1 还是 v2，**下线信号是这两个计数跨发布归零**。

| 计数 | 位置 | 含义 |
|---|---|---|
| `discoveryRepliesLegacy` | `SyncMetrics.kt:81` | 我们回复了多少个 v1 发现查询 |
| `legacySessions` | `native-lib.cpp:115` | 有多少条 TCP 连接走了 v1 帧格式 |

**完整 v1 残留面**（下线时需一并清理，按位置列全，避免遗漏后留下死代码）：

*Kotlin 侧*

- `NativeSyncEngine.handleIncomingSyncRequest`（`NativeSyncEngine.kt:63`）—— v1 上行入口
- `DiscoveryWire.encodeLegacy` / `parseLegacy` / `LEGACY_PREFIX`（`DiscoveryPacket.kt:138,244,255`）
- `DiscoveryDecider(acceptLegacy)` + `IgnoreReason.LEGACY_DISABLED` + `DiscoveryAction.Reply.legacy`（`DiscoveryDecider.kt:31,57,79`）
- `LanDiscoveryService(emitLegacy, acceptLegacy)`（`LanDiscoveryService.kt:83,85`）
- `DiscoveryTelemetry.onReplied(legacy)`（`LanDiscoveryService.kt:44`）与 `SyncMetrics.recordDiscoveryReply(legacy)`
- `BaseDeviceSyncManager` 中兼容 v1 对端的 operation 取值（`BaseDeviceSyncManager.kt:41`）

*C++ 侧*

- `g_mid_handle_legacy` / `callKotlinLegacy`（`native-lib.cpp:91,291`）
- `serveLegacy`（522）、`exchangeV1`（708）、`readLegacyBody` / `writeLegacyMessage`
- dialect 协商与回退：`performSyncWithRetry`（765）、`knownPeerVersion` / `rememberPeerVersion`
- `g_metrics.legacySessions`（115）

**分四步下线，每步单独发一个版本，各自可回滚**：

| 步 | 动作 | 影响面 | 回滚判据 |
|---|---|---|---|
| 1 | **观测期**（已就位） | 无行为变化 | —— |
| 2 | `emitLegacy = false`：不再广播 v1 明文查询，但仍应答 v1 | 单向收缩。老设备自己广播的 v1 查询我们照常回，所以**双方仍能互相发现** | `discoveryRepliesLegacy` 不降反升 → 说明存量比预期多，回滚 |
| 3 | `acceptLegacy = false`：不再解析 v1 发现报文 | 老设备从发现列表消失。但手动输入 IP 的连接路径**仍走 v1 TCP 适配层**，功能未断 | 用户报障「设备列表里找不到旧平板」 |
| 4 | 移除 v1 TCP 适配层与上表全部残留 | 老设备彻底连不上 | `legacySessions` 非零 |

**为什么不能跳步**：第 2 步和第 3 步之间隔着一个真实差别——停止双发只是不再往网络里灌明文，停止接受则是主动切断对端的发现能力。合并成一步的话，一旦出问题无法判断是哪一半造成的。第 3 步和第 4 步之间同理：发现失败还有手动 IP 兜底，适配层移除之后就没有退路了。

**第 4 步会撞到的一处**：`NativeSyncEngineJniContractTest` 断言上行回调集合**恰好**是 `{handleIncomingFrame, handleIncomingSyncRequest}`。移除 v1 后这个测试会红——这是设计如此，它的职责就是让 JNI 面的任何增删都必须被显式确认一次，而不是悄悄漂移。改测试是下线动作的一部分，不是修 bug。

**前置条件（下线计划启动前唯一的硬依赖）**：上线第 2 步之前，那两个计数必须真的能被看到。

现状是：**两个计数都已经可读，但没有任何人去读。**

- Kotlin 侧 `LanDeviceSyncManager.metrics` 声明为 public，注释写明「诊断页与 bug 上报器都需要它」——这两个消费方一个都还不存在，全工程没有任何地方调用 `metrics.snapshot()` 或 `format()`。
- C++ 侧 `legacySessions` 与 `v2Sessions` 已随 `transportStats()` 以扁平 JSON 导出（`native-lib.cpp:1047`），Kotlin 侧 `NativeSyncEngine.transportStats()` 也已声明（`NativeSyncEngine.kt:142`）——**但同样从未被调用过**。

所以缺的不是埋点，也不是 JNI 出口，而是最后一段：没人把数据取出来，进程一死就归零。启动下线前需要补上——

- **最小可行**：诊断页加一个只读入口，把 `metrics.format()` 与 `transportStats()` 拼在一起显示，让用户报障时能直接截图。两个数据源都已就绪，这一步基本只是接线。
- **真正可判定**：把快照落盘并随 bug 上报回传。「跨发布归零」这个判据要成立，前提是有跨发布的数据可比——只能看当前进程的计数，是判不出趋势的。

在此之前，第 2 步不具备启动条件——不是因为改动本身有风险，而是因为一旦出问题，我们连「该不该回滚」都无从判断。
