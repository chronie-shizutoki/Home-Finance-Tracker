# Home Finance Tracker — Android

Native Android client providing a complete mobile household finance management experience.

---

## Table of Contents

- [Features](#features)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Build & Run](#build--run)
- [Configuration](#configuration)
- [Feature Guide](#feature-guide)
- [Database Design](#database-design)
- [API Integration](#api-integration)
- [LAN Sync](#lan-sync)
- [Security Design](#security-design)
- [Testing](#testing)
- [Troubleshooting](#troubleshooting)

---

## Features

### Core
| Module | Description |
|--------|-------------|
| Expense Tracking | Add / view / edit / delete expenses across 26 categories |
| AI Recognition | Intelligent expense extraction from images & text via SiliconFlow Qwen models |
| Budget Management | Monthly limits, warning thresholds, real-time usage tracking |
| Data Sync | Background sync via WorkManager with offline queue |
| Charts & Analytics | Bar charts, weekday radar chart, trend analysis |
| Multi-language | 12 languages, instant switch without restart |

### Advanced
| Module | Description |
|--------|-------------|
| **LAN Device Sync** | Native C++ JNI engine for peer-to-peer sync over WiFi LAN |
| **Encrypted Database** | SQLCipher-encrypted Room, key stored in Android Keystore |
| **Built-in Image Editor** | Crop / rotate / eraser via miuix + coil, no third-party lib |
| **Excel Import/Export** | High-speed fastExcel read/write, batch import and backup |
| **Crash Reporting** | Automatic error logs with device info, upload to server |
| **Developer Mode** | Built-in database inspector and test data generator |
| **Dynamic Theme** | Wallpaper-based color extraction + manual color picker |

---

## Architecture

```
┌─────────────────────────────────────────────────┐
│                Presentation                     │
│     Compose UI  +  ViewModels  +  Navigation    │
├─────────────────────────────────────────────────┤
│                  Domain                         │
│     Use Cases  +  Domain Models  +  Repository  │
│     Interfaces  +  SyncManager                  │
├─────────────────────────────────────────────────┤
│                   Data                          │
│  Room (SQLCipher)  +  Retrofit  +  Protobuf     │
│  DataStore  +  Mappers  +  Sync Queue           │
├─────────────────────────────────────────────────┤
│               Native (C++ JNI)                  │
│  Sync Engine  +  Frame Codec  +  Socket I/O     │
│  Thread Pool  +  Retry Policy  +  CRC32C        │
└─────────────────────────────────────────────────┘
```

### Layer Responsibilities

**Presentation**
- Jetpack Compose declarative UI driven by StateFlow
- Type-safe navigation with animated transitions
- Custom components: `ExpressiveLinearProgressIndicator`, `ExpressiveSwitch`, `CircularIconButton`, `ColorPickerBottomSheet`, `LanguageSelectorBottomSheet`

**Domain**
- Pure Kotlin business logic with no Android Framework dependency
- Use Cases: `GetBudget`, `SaveBudget`, `GetStatistics`, `ExportExpenses`, `ImportExpenses`, `Login`, `Logout`, `CheckLoginStatus`
- `DeviceSyncManager` interface implemented by `LanDeviceSyncManager`

**Data**
- Room database version 6 with 6 migrations
- Retrofit + OkHttp networking with independent interceptor chains (auth / logging / error handling)
- Protobuf serialization for LAN sync
- Data Mappers converting Entity ↔ Domain ↔ DTO

**Native** (see [C++ README](app/src/main/cpp/README.md))
- Pure C++23 sync engine compiled to `libsync_engine.so`
- JNI bridge Kotlin ↔ C++, worker threads isolated from main thread
- Zero dependencies: no gRPC/Protobuf C++ libraries, custom frame protocol

---

## Tech Stack

### Core
| Category | Tech | Version |
|----------|------|---------|
| Language | Kotlin | 2.4.10 |
| AGP | Android Gradle Plugin | 9.3.0 |
| KSP | Kotlin Symbol Processing | 2.4.10 |
| UI | Jetpack Compose BOM | 2026.06.01 |
| DI | Hilt | 2.60.1 |

### Data & Network
| Category | Tech | Version |
|----------|------|---------|
| Database | Room + SQLCipher | 2.8.4 / 4.17.0 |
| Networking | Retrofit + OkHttp | 3.0.0 / 5.4.0 |
| Async | Kotlin Coroutines + Flow | 1.11.0 |
| Local Storage | DataStore | 1.2.1 |

### UI & Interaction
| Category | Tech | Version |
|----------|------|---------|
| Material | MDC Android | 1.14.0 |
| Navigation | Navigation Compose | 2.9.8 |
| Pagination | Paging 3 + Compose | 3.5.0 |
| Image | Coil Compose | 3.5.0 |
| Framework | Miuix (HyperOS) | 0.9.3 |

### Utility Libraries
| Category | Tech | Version |
|----------|------|---------|
| Excel | fastExcel + aalto-xml + XZ | 0.20.2 / 1.4.0 / 1.12 |
| Serialization | Gson | — |
| Background | WorkManager + Hilt | 2.11.2 |
| Protobuf | protobuf-javalite | 4.35.1 |
| gRPC | gRPC-Java (okhttp + protobuf-lite) | 1.82.1 |

### Native Compilation
| Category | Tech |
|----------|------|
| Build | CMake 4.1.2 + Ninja |
| NDK | 28.2.13676358 |
| Standard | C++23 (`-std=c++23 -fexceptions`) |
| ABI | arm64-v8a |

---

## Project Structure

```
android/
├── app/
│   ├── build.gradle.kts              # Protobuf + CMake + signing config
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── cpp/                    # Native C++ sync engine ★
│       │   │   ├── CMakeLists.txt
│       │   │   ├── README.md           # C++ layer documentation
│       │   │   ├── native-lib.cpp      # JNI bridge + server/client logic
│       │   │   ├── protocol/           # Wire protocol
│       │   │   │   ├── sync_protocol.h            # Protocol definition (single source of truth)
│       │   │   │   ├── crc32c.h                   # CRC-32C table-driven implementation
│       │   │   │   ├── protocol_conformance.cpp   # Compile-time protocol checks
│       │   │   │   ├── frame_vectors_generated.h  # Golden vectors (auto-generated)
│       │   │   │   └── retry_vectors_generated.h
│       │   │   └── transport/          # Transport layer
│       │   │       ├── byte_stream.h              # Byte stream abstraction (concepts + test doubles)
│       │   │       ├── io_result.h                # I/O status enumeration
│       │   │       ├── socket_stream.h/.cpp       # POSIX socket implementation
│       │   │       ├── frame_codec.h              # Frame read/write + v1 compatibility
│       │   │       ├── retry_policy.h             # Backoff + jitter retry strategy
│       │   │       ├── thread_pool.h/.cpp         # Fixed-size bounded worker pool
│       │   │       └── transport_conformance.cpp  # Compile-time transport checks
│       │   ├── java/com/chronie/homemoney/
│       │   │   ├── core/               # Core utilities
│       │   │   │   ├── coil/           # Coil DataUriMapper
│       │   │   │   ├── common/         # Language / DeveloperMode
│       │   │   │   ├── error/          # Error reporting system
│       │   │   │   └── network/        # NetworkMonitor (Flow-based)
│       │   │   ├── data/               # Data layer
│       │   │   │   ├── local/          # Room (DAO / Entity / Migration)
│       │   │   │   ├── remote/         # Retrofit (API / DTO / Interceptor)
│       │   │   │   ├── repository/     # Repository implementations
│       │   │   │   ├── mapper/         # Entity ↔ Domain ↔ DTO
│       │   │   │   └── sync/           # SyncManager / NativeSyncEngine
│       │   │   ├── di/                 # Hilt modules
│       │   │   ├── domain/             # Domain layer
│       │   │   │   ├── model/          # Domain models (26 expense types)
│       │   │   │   ├── repository/     # Repository interfaces
│       │   │   │   ├── usecase/        # Use cases
│       │   │   │   └── sync/           # Sync interfaces
│       │   │   ├── service/            # HealthCheckService
│       │   │   ├── ui/                 # UI (Compose)
│       │   │   │   ├── budget/         # Budget
│       │   │   │   ├── charts/         # Charts + weekday radar
│       │   │   │   ├── components/     # Custom components
│       │   │   │   ├── expense/        # Expense list / add / AI recognition
│       │   │   │   ├── main/           # Main screen + bottom navigation
│       │   │   │   ├── membership/     # Membership
│       │   │   │   ├── settings/       # Settings
│       │   │   │   ├── sync/           # LAN sync screen
│       │   │   │   ├── test/           # Developer database test
│       │   │   │   ├── theme/          # Theme
│       │   │   │   └── welcome/        # Welcome
│       │   │   ├── worker/             # SyncWorker (HiltWorker)
│       │   │   ├── HomeMoneyApplication.kt
│       │   │   └── MainActivity.kt
│       │   ├── proto/
│       │   │   └── sync.proto          # Protobuf definitions
│       │   └── res/                    # Resources (12 languages + icons)
│       ├── androidTest/                # Instrumented tests
│       └── test/                       # Unit tests
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── variables.gradle                   # Version constants
└── README.md                          # ← This file
```

---

## Build & Run

### Prerequisites

| Tool | Minimum Version |
|------|----------------|
| Android Studio | Hedgehog 2023.1.1+ |
| JDK | 17+ |
| Android SDK | 37 (compileSdk) |
| Minimum OS | Android 13.0 (API 33) |
| NDK | 28.2+ (auto-downloaded) |
| CMake | 4.1.2 (auto-downloaded) |

### Android Studio

```
1. Open Android Studio
2. File → Open → select the android/ directory
3. Wait for Gradle Sync to complete
4. Run (Shift+F10)
```

### Command Line

```bash
cd android

# Clean
./gradlew clean

# Debug APK
./gradlew assembleDebug

# Release APK (requires signing config)
./gradlew assembleRelease

# Install on device
./gradlew installDebug
```

### Windows Batch Scripts

```bash
clean-build.bat     # Clean and build
build-apk.bat       # Build APK
force-build.bat     # Force build (stops Gradle daemon first)
```

### Output Location

```
android/app/build/outputs/apk/debug/app-debug.apk
```

### Release Signing

Configure in `local.properties`:

```properties
RELEASE_STORE_FILE=release.keystore
RELEASE_STORE_PASSWORD=your_password
RELEASE_KEY_ALIAS=your_alias
RELEASE_KEY_PASSWORD=your_key_password
```

---

## Configuration

### Server Address

Edit `app/src/main/java/com/chronie/homemoney/di/NetworkModule.kt`:

```kotlin
private const val BASE_URL = "http://YOUR_SERVER_IP:3010/"
```

### API Key

Configure the SiliconFlow API Key in the app's Settings screen for AI expense recognition.

### Versioning

Dynamic versioning based on build timestamp:
- `versionCode`: Unix timestamp
- `versionName`: Format `1.YYYYMMDD.HHMM`

---

## Feature Guide

### 1. Expense Management

**26 Expense Categories**

Daily Goods, Luxury, Communication, Food, Snacks, Cold Drinks, Convenience Food, Textiles, Beverages, Condiments, Transportation, Dining, Medical, Fruits, Other, Seafood, Dairy, Gifts, Travel, Government, Utilities, Beauty, Bean Products, Cosmetics, Electronics, Household Appliances, Hardware, Clothing

**Main Flow**
- Tap `+` to add an expense
- Select type, enter amount, pick date, add notes
- Saved to encrypted local database, queued for sync
- List uses LazyColumn + Paging 3 for efficient scrolling
- Pull-to-refresh fetches latest data from server

### 2. AI Recognition

**Flow**
1. Tap AI icon on the add expense screen
2. Select images (gallery / camera) or enter text description
3. Optionally edit images in the built-in editor (crop / rotate / eraser)
4. Images sent as base64 Data URIs to SiliconFlow API
5. Qwen model extracts expense records
6. Review, edit, and batch-save

**Technical Details**
- Dedicated Retrofit instance (`AIModule`) with independent timeouts and logging
- Multiple images per request supported
- SiliconFlow endpoint: `v1/chat/completions`

### 3. Budget Management

- Enable in Settings → Budget Management
- Set monthly limit and warning threshold (default 80%)
- Budget card on main screen shows spent / remaining / percentage / daily average / recommended daily
- Three-color status: 🟢Normal → 🟡Warning → 🔴Over budget
- Animated `ExpressiveLinearProgressIndicator`

### 4. Data Sync

**Server Sync**
- `SyncWorker` (HiltWorker) runs every hour in background
- Constraint: requires network connectivity
- `NetworkMonitor` observes connectivity changes, triggers sync on reconnect
- `SyncScheduler` manages periodic and event-driven syncs
- Full sync cycle: upload local changes → download server updates → merge
- `sync_queue` table tracks pending operations

**Manual Sync**
- Settings → Data Sync → "Sync Now"

**Conflict Resolution**
- Auto-resolved by `updatedAt` timestamp — newer version wins

### 5. LAN Device Sync

> See [C++ README](app/src/main/cpp/README.md) for detailed design

- **Protocol layer**: Custom 32-byte frame protocol with CRC-32C integrity
- **Transport layer**: Non-blocking TCP with deadline enforcement, resilient to EINTR/EAGAIN
- **Server**: Fixed-size thread pool with bounded queue, returns BUSY when saturated
- **Client**: Exponential backoff with equal jitter
- **Compatibility**: Serves both v1 (bare length prefix) and v2 (frame protocol) dialects
- **Network binding**: `android_setsocknetwork()` pins sockets to the WiFi interface

### 6. Excel Import/Export

**Export**
- Settings → Import/Export → Export to Excel
- Select date range
- fastExcel writes to device storage

**Import**
- Select Excel file
- Preview, confirm, and batch-import to database

### 7. Multi-language

12 languages, persisted via LocaleConfig, instant switch without restart:

| Code | Language |
|------|----------|
| en | English |
| in-ID | Bahasa Indonesia |
| ja-JP | 日本語 |
| ko-KR | 한국어 |
| ms-MY | Bahasa Melayu |
| th-TH | ไทย |
| vi-VN | Tiếng Việt |
| zh-CN | 简体中文（中国大陆） |
| zh-SG | 简体中文（新加坡） |
| zh-HK | 繁體中文（香港） |
| zh-MO | 繁體中文（澳門） |
| zh-TW | 繁體中文（台灣） |

### 8. Theme Customization

- **Dynamic Color**: Extract Material You palette from wallpaper
- **Manual Picker**: `ColorPickerBottomSheet` for custom accent colors
- Real-time preview, instant apply

### 9. Developer Mode

Enable in Settings to unlock:
- "Database Test" tab in bottom navigation
- Generate test data, view all records with sync status
- Clear database
- Inspect crash logs and error reports

---

## Database Design

Room + SQLCipher encryption. All primary keys are TEXT (UUID).

### expenses

```sql
CREATE TABLE expenses (
    id          TEXT PRIMARY KEY NOT NULL,  -- UUID
    type        TEXT NOT NULL,              -- expense category
    remark      TEXT,                       -- notes
    amount      REAL NOT NULL,              -- amount
    date        TEXT NOT NULL,              -- date YYYY-MM-DD
    version     INTEGER DEFAULT 1,          -- optimistic lock
    updated_at  INTEGER NOT NULL,           -- Unix timestamp
    deleted_at  INTEGER,                    -- soft delete marker
    is_synced   INTEGER DEFAULT 0           -- sync status
);
-- Indexes: date, type, is_synced, updated_at
```

### budgets

```sql
CREATE TABLE budgets (
    id                INTEGER PRIMARY KEY NOT NULL,
    monthly_limit     REAL NOT NULL,
    warning_threshold REAL DEFAULT 0.8,
    is_enabled        INTEGER DEFAULT 0,
    updated_at        INTEGER NOT NULL
);
```

### members

```sql
CREATE TABLE members (
    id          TEXT PRIMARY KEY NOT NULL,
    username    TEXT,
    avatar      TEXT,
    created_at  INTEGER NOT NULL,
    updated_at  INTEGER NOT NULL,
    is_synced   INTEGER DEFAULT 0
);
```

### sync_queue

```sql
CREATE TABLE sync_queue (
    id          INTEGER PRIMARY KEY NOT NULL,
    entity_type TEXT NOT NULL,       -- "expense" / "member"
    entity_id   TEXT NOT NULL,       -- FK to entity table
    operation   TEXT NOT NULL,       -- "INSERT" / "UPDATE" / "DELETE"
    data        TEXT NOT NULL,       -- JSON snapshot
    retry_count INTEGER DEFAULT 0,
    created_at  INTEGER NOT NULL
);
```

### Migration History

| Version | Change |
|---------|--------|
| 1→2 | Added budgets table |
| 2→3 | Added date column to expenses (migrated from time) |
| 3→4 | Added unique index on server_id |
| 4→5 | Added version / updated_at / deleted_at; removed server_id index |
| 5→6 | Recreated expenses table (TEXT id), rebuilt all indexes |

---

## API Integration

### Expense API

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/expenses` | Paginated query with keyword/type/month/amount-range/sort |
| POST | `/api/expenses` | Create expense |
| PUT | `/api/expenses/{id}` | Update expense |
| POST | `/api/expenses/batch` | Batch create |
| DELETE | `/api/expenses/{id}` | Soft delete |
| DELETE | `/api/expenses/{id}/hard` | Hard delete |
| GET | `/api/expenses/statistics` | Statistics query |
| POST | `/api/expenses/sync` | Bidirectional sync |

### Member API

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/health/lite` | Health check |
| POST | `/api/members/members` | Get or create member |
| GET | `/api/members/members/{username}` | Query member info |
| PUT | `/api/members/members/{username}/avatar` | Upload avatar |

### Common Response Format

```json
{
  "data": {},
  "message": "success",
  "success": true
}
```

### AI API (SiliconFlow)

```
POST v1/chat/completions
```
- Dedicated OkHttp/Retrofit instance
- Multiple base64 Data URI images per request
- Uses Qwen vision models

---

## LAN Sync

### Protobuf Protocol (`sync.proto`)

```
SyncEntity      { entity_type, entity_id, operation, data, timestamp }
DeviceSyncData  { device_id, device_name, sync_timestamp, entities[] }
SyncService     { Sync(stream), Ping(stream) }
```

### Native Engine

`NativeSyncEngine` is the JNI bridge between Kotlin and C++:

```kotlin
external fun startServer(port: Int): Int
external fun stopServer()
external fun performSync(address: String, port: Int, data: ByteArray): ByteArray?
external fun openSyncConnection(address: String, port: Int): Long
external fun syncExchange(handle: Long, sessionId: Long, seq: Int,
                          opcode: Int, body: ByteArray?, timeoutMs: Int): ByteArray?
external fun closeSyncConnection(handle: Long)
external fun configureTransport(connectTimeoutMs: Int, ioTimeoutMs: Int, maxAttempts: Int)
external fun lastErrorCode(): Int
external fun transportStats(): String
```

See [cpp/README.md](app/src/main/cpp/README.md) for the full C++ layer design.

---

## Security Design

### Data Protection Chain

```
┌────────────────────────────────────┐
│ Android Keystore (hardware-backed)  │
│   └── Master Key (AES256_GCM)      │
│       └── EncryptedSharedPreferences│
│           └── SQLCipher Passphrase  │
│               └── Room Database     │
│                   └── SQLCipher 4.17│
└────────────────────────────────────┘
```

- SQLCipher-encrypted database with 32-character random passphrase
- Passphrase stored in `EncryptedSharedPreferences` (AES256_GCM)
- Master key backed by Android Keystore hardware
- Release builds: `minifyEnabled` + `shrinkResources`

### Permissions

| Permission | Purpose |
|------------|---------|
| `INTERNET` | Network communication |
| `ACCESS_NETWORK_STATE` | Connectivity monitoring |
| `ACCESS_WIFI_STATE` | WiFi state detection |
| `CHANGE_WIFI_MULTICAST_STATE` | LAN device discovery |
| `CAMERA` | AI expense photo |
| `READ_MEDIA_IMAGES` | Gallery selection (Android 13+) |

---

## Testing

### Unit Tests

```bash
./gradlew test
```

### Instrumented Tests

```bash
./gradlew connectedAndroidTest
```

### Manual Test Checklist

1. Enable Developer Mode
2. Database Test: add / view / clear data
3. Server sync: upload / download / conflict resolution
4. LAN sync: multiple devices on same WiFi
5. Offline mode: operate offline, verify auto-sync on reconnect
6. Excel import / export
7. AI recognition: image and text input
8. Language switch: all 12 languages
9. Theme switch: dynamic color / manual picker

---

## Troubleshooting

### Build Issues

| Symptom | Solution |
|---------|----------|
| Gradle Sync failed | Check network; File → Invalidate Caches / Restart; delete `.gradle` and re-sync |
| R.jar file locked | `./gradlew --stop`; delete `app/build`; restart IDE |
| Native library build failed | Ensure NDK installed (SDK Manager); CMake 4.1.2+ |
| Protobuf generation error | `./gradlew clean` then rebuild |

### Runtime Issues

| Symptom | Investigation |
|---------|---------------|
| Crash on startup | Logcat; verify migration scripts (1→6); clear app data |
| Sync not working | Network; server reachability; check Sync logs |
| LAN sync fails | Same WiFi; multicast not blocked; firewall |
| Language not applying | Verify setting saved; check corresponding strings.xml |
| Import/export fails | Storage permissions; file format (.xlsx); accessible path |
| AI recognition error | API Key configured; network; SiliconFlow quota |

---

## Related Documents

- [C++ Native Sync Engine](app/src/main/cpp/README.md) — Complete native engine design document
- [Main Project README](../README.md) — Full-stack project overview
