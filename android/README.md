# Home Finance Tracker - Android Native Application

[![Crowdin](https://badges.crowdin.net/home-finance-tracker/localized.svg)](https://crowdin.com/project/home-finance-tracker)

## Overview

This is the native Android implementation of the Home Money financial tracking application. The app is built using modern Android development practices with Kotlin, Jetpack Compose, and follows Clean Architecture principles. It provides a comprehensive set of features for expense tracking, budget management, data synchronization, and more.

## Features

### Core Functionality
- **Expense Tracking**: Add, view, edit, and delete expense records with support for 26 expense categories
- **AI-Powered Recognition**: Intelligent expense recognition from images and text using SiliconFlow API (Qwen models)
- **Budget Management**: Set monthly spending limits with warning thresholds and real-time usage tracking
- **Data Synchronization**: Automatic background sync with server via WorkManager, offline support with local caching
- **Search & Filtering**: Advanced filtering by date range, expense type, amount range, and keywords
- **Multi-language Support**: 12 languages including English, Indonesian, Japanese, Korean, Malay, Simplified Chinese (Mainland & Singapore), Thai, Traditional Chinese (Hong Kong, Macau, Taiwan), and Vietnamese

### New & Enhanced Features
- **LAN Device Sync**: Peer-to-peer data synchronization via gRPC + Protobuf over UDP multicast discovery on WiFi local network
- **Native C++ Sync Engine**: High-performance JNI-based sync server with socket-level networking for device-to-device communication
- **Data Visualization**: Interactive charts with weekday radar chart and spending trend analysis
- **Membership Management**: User profile with avatar upload via server API
- **Excel Import/Export**: Import expenses from Excel files and export data for backup using fastExcel
- **Image Cropping**: Built-in uCrop integration for AI expense recognition image preprocessing
- **Error Reporting**: Automatic crash reporting, error logging, and log file management with server upload
- **Health Check Service**: Server health monitoring via `/api/health/lite` endpoint

### Technical Features
- **Encrypted Database**: SQLCipher-encrypted Room database with hardware-backed key storage via EncryptedSharedPreferences
- **Material Design 3**: Modern UI with dynamic color support (Android 12+) and manual color picker customization
- **Edge-to-Edge Display**: Immersive full-screen experience with transparent system bars
- **Developer Mode**: Built-in database testing and debugging tools
- **Customizable Theme**: Manual color picker and dynamic color from wallpaper
- **Protobuf Serialization**: Protocol Buffers for efficient LAN sync data exchange

## Architecture

### Clean Architecture Layers

```
┌─────────────────────────────────────────┐
│         Presentation Layer              │
│  (Compose UI + ViewModels)              │
├─────────────────────────────────────────┤
│         Domain Layer                    │
│  (Use Cases + Models + Repositories)    │
├─────────────────────────────────────────┤
│         Data Layer                      │
│  (Room DB + Retrofit + gRPC + Mappers)  │
├─────────────────────────────────────────┤
│         Native Layer                    │
│  (C++ JNI Sync Engine + Protobuf)       │
├─────────────────────────────────────────┤
│         Framework Layer                 │
│  (Android SDK + Third-party Libraries)  │
└─────────────────────────────────────────┘
```

### Key Components

#### Data Layer
- **Room Database**: Encrypted local storage with SQLCipher (version 6, 6 migrations)
- **Retrofit**: RESTful API client for server communication with auth, logging, and error-handling interceptors
- **gRPC + Protobuf**: High-performance device-to-device sync protocol for LAN sync
- **Repository Pattern**: Abstraction layer for data sources (Expense, Budget, Member, AI Record)
- **Data Mappers**: Convert between Entity, Domain, and DTO models
- **Excel Integration**: fastExcel for import/export with aalto-xml StAX parser and XZ compression

#### Domain Layer
- **Use Cases**: Business logic encapsulation (GetBudget, SaveBudget, GetStatistics, Export/Import, Login/Logout, CheckLoginStatus)
- **Domain Models**: Pure Kotlin data classes (Expense with 26 types, Budget, Member, AIExpenseRecord, SyncResult)
- **Repository Interfaces**: Contracts for data operations
- **Sync Managers**: DeviceSyncManager interface with LanDeviceSyncManager implementation

#### Presentation Layer
- **Jetpack Compose**: Modern declarative UI framework
- **ViewModels**: UI state management with Kotlin StateFlow
- **Navigation Component**: Type-safe navigation with animated transitions
- **Material 3**: Material Design 3 components with window size classes
- **Custom Components**: ExpressiveLinearProgressIndicator, ExpressiveLoadingIndicator, ExpressiveSwitch, CircularIconButton, ColorPickerBottomSheet, LanguageSelectorBottomSheet

#### Native Layer
- **C++ JNI Engine**: Native socket server for LAN sync (`native-lib.cpp`)
- **Protobuf Schema**: Defined in `sync.proto` with SyncService gRPC service definition
- **SyncEngine**: JNI bridge between Kotlin and native C++ for high-performance data transfer

## Tech Stack

### Core Technologies
- **Language**: Kotlin 2.4.10
- **Android Gradle Plugin**: 9.3.0
- **KSP**: 2.4.10
- **UI Framework**: Jetpack Compose (BOM 2026.06.01)
- **Dependency Injection**: Hilt 2.60.1
- **Database**: Room 2.8.4 with SQLCipher 4.17.0
- **Networking**: Retrofit 3.0.0 + OkHttp logging-interceptor 5.4.0
- **Async**: Kotlin Coroutines 1.11.0 + Flow

### Key Libraries
- **Material Design 3**: Modern UI components (1.5.0-alpha23) with window-size-class
- **Material (MDC)**: Material Components for Android (1.14.0)
- **Navigation Compose**: Type-safe navigation (2.9.8)
- **Paging 3**: Efficient data loading with Compose integration (3.5.0)
- **WorkManager**: Background task scheduling (2.11.2) with Hilt integration
- **Coil Compose**: Image loading and caching (3.5.0) with custom DataUriMapper
- **Gson**: JSON serialization
- **DataStore**: Preferences storage (1.2.1)
- **fastExcel**: Excel file handling (0.20.2) with aalto-xml (1.4.0) and XZ (1.12)
- **uCrop**: Image cropping (2.2.11)
- **m3color**: Material 3 color utilities (2026.1)
- **Protobuf**: Protocol Buffers (4.35.1) with protobuf-javalite
- **gRPC**: gRPC-Java (1.82.1) with okhttp and protobuf-lite

### Security
- **SQLCipher**: Database encryption with runtime-generated random passphrase
- **EncryptedSharedPreferences**: Secure key storage with AES256_GCM
- **Android Keystore**: Hardware-backed master key management
- **Error Reporting**: Secure error logging with device info anonymization

## Project Structure

```
android/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── cpp/                       # Native C++ code
│   │   │   │   ├── CMakeLists.txt         # CMake build config (C++17)
│   │   │   │   └── native-lib.cpp         # JNI sync engine
│   │   │   ├── java/com/chronie/homemoney/
│   │   │   │   ├── core/                  # Core utilities
│   │   │   │   │   ├── coil/              # Coil custom mapper (DataUriMapper)
│   │   │   │   │   ├── common/            # Language, LanguageManager, DeveloperMode
│   │   │   │   │   ├── error/             # Error reporting system (ErrorReporter, ErrorInfo, LogFileManager, DeviceInfoUtils, UncaughtExceptionHandler, MockErrorReportApi)
│   │   │   │   │   │   └── di/            # ErrorReportModule
│   │   │   │   │   └── network/           # NetworkMonitor with Flow-based connectivity observation
│   │   │   │   ├── data/                  # Data layer
│   │   │   │   │   ├── local/             # Room database
│   │   │   │   │   │   ├── dao/           # ExpenseDao, BudgetDao, MemberDao, SyncQueueDao
│   │   │   │   │   │   ├── entity/        # ExpenseEntity, BudgetEntity, MemberEntity, SyncQueueEntity
│   │   │   │   │   │   ├── AppDatabase.kt # Room database (version 6)
│   │   │   │   │   │   ├── DatabaseMigrations.kt # 5 migrations (1→2→3→4→5→6)
│   │   │   │   │   │   └── PreferencesManager.kt # User preferences and membership cache
│   │   │   │   │   ├── remote/            # API layer
│   │   │   │   │   │   ├── api/           # ExpenseApi, MemberApi, ApiService, AIRecordApi
│   │   │   │   │   │   ├── dto/           # DTOs (ExpenseDto, MemberDto, AIRecordDto, ApiResponse, HealthDto, SyncRequestDto, SyncResponseDto)
│   │   │   │   │   │   └── interceptor/   # AuthInterceptor, LoggingInterceptor, ErrorHandlingInterceptor
│   │   │   │   │   ├── repository/        # Repository implementations (Expense, Budget, Member, AIRecord)
│   │   │   │   │   ├── mapper/            # ExpenseMapper, MemberMapper, AIRecordMapper
│   │   │   │   │   └── sync/              # SyncManagerImpl, SyncScheduler, LanDeviceSyncManager, BaseDeviceSyncManager, DeviceSyncManagerFactory, NativeSyncEngine, SyncProtoConverter
│   │   │   │   ├── di/                    # Dependency injection modules
│   │   │   │   │   ├── DatabaseModule.kt  # SQLCipher-encrypted Room database
│   │   │   │   │   ├── NetworkModule.kt   # Retrofit + OkHttp with interceptors
│   │   │   │   │   ├── RepositoryModule.kt
│   │   │   │   │   ├── AIModule.kt        # Separate Retrofit instance for SiliconFlow API
│   │   │   │   │   ├── SyncModule.kt      # SyncManager + DeviceSyncManagerFactory
│   │   │   │   │   └── ImageLoaderModule.kt
│   │   │   │   ├── domain/                # Domain layer
│   │   │   │   │   ├── model/             # Expense (26 types), Budget, BudgetUsage, Member, AIExpenseRecord, ExpenseFilters, ExpenseStatistics, SyncResult
│   │   │   │   │   ├── repository/        # ExpenseRepository, BudgetRepository, MemberRepository, AIRecordRepository
│   │   │   │   │   ├── usecase/           # GetBudgetUseCase, SaveBudgetUseCase, GetBudgetUsageUseCase, GetStatisticsUseCase, ExportExpensesUseCase, ImportExpensesUseCase, LoginUseCase, LogoutUseCase, CheckLoginStatusUseCase
│   │   │   │   │   └── sync/              # SyncManager, DeviceSyncManager interfaces
│   │   │   │   ├── service/               # HealthCheckService
│   │   │   │   ├── ui/                    # Presentation layer
│   │   │   │   │   ├── budget/            # BudgetCard, BudgetSettingsDialog, BudgetViewModel
│   │   │   │   │   ├── charts/            # ChartsScreen, ChartsViewModel, WeekdayDetailScreen, WeekdayDetailViewModel, WeekdayRadarChart
│   │   │   │   │   ├── components/        # CircularIconButton, ColorPickerBottomSheet, ColorPickerData, ExpressiveLinearProgressIndicator, ExpressiveLoadingIndicator, ExpressiveSwitch, LanguageSelectorBottomSheet
│   │   │   │   │   ├── expense/           # ExpenseListScreen, AddExpenseScreen, AIExpenseScreen, ExpenseFilterDialog, DateFormatter, ExpenseTypeLocalizer + ViewModels
│   │   │   │   │   ├── main/              # MainScreen, MainViewModel, BottomNavigationBar
│   │   │   │   │   ├── membership/        # MembershipScreen, MembershipViewModel
│   │   │   │   │   ├── settings/          # SettingsScreen, SettingsViewModel, OpenSourceLicensesScreen
│   │   │   │   │   ├── sync/              # LanSyncScreen
│   │   │   │   │   ├── test/              # DatabaseTestScreen, DatabaseTestViewModel
│   │   │   │   │   ├── theme/             # Theme.kt, Type.kt
│   │   │   │   │   └── welcome/           # WelcomeScreen, WelcomeViewModel
│   │   │   │   ├── worker/                # SyncWorker (HiltWorker via WorkManager)
│   │   │   │   ├── HomeMoneyApplication.kt # Application class with Hilt, WorkManager, Coil, ErrorReporter
│   │   │   │   └── MainActivity.kt        # Single Activity with Compose navigation
│   │   │   ├── proto/                     # Protobuf definitions
│   │   │   │   └── sync.proto             # SyncService, SyncRequest/Response, PingRequest/Response, DeviceSyncData, SyncEntity
│   │   │   ├── res/                       # Resources
│   │   │   │   ├── values/                # English strings
│   │   │   │   ├── values-in-rID/             # Indonesian
│   │   │   │   ├── values-ja-rJP/             # Japanese
│   │   │   │   ├── values-ko-rKR/             # Korean
│   │   │   │   ├── values-ms-rMY/             # Malay
│   │   │   │   ├── values-th-rTH/             # Thai
│   │   │   │   ├── values-vi-rVN/             # Vietnamese
│   │   │   │   ├── values-zh-rCN/         # Simplified Chinese (Mainland)
│   │   │   │   ├── values-zh-rHK/         # Traditional Chinese (Hong Kong)
│   │   │   │   ├── values-zh-rMO/         # Traditional Chinese (Macau)
│   │   │   │   ├── values-zh-rSG/         # Simplified Chinese (Singapore)
│   │   │   │   ├── values-zh-rTW/         # Traditional Chinese (Taiwan)
│   │   │   │   ├── xml/                   # file_paths.xml, locale_config.xml
│   │   │   │   └── drawable/              # App icons
│   │   │   └── AndroidManifest.xml
│   │   ├── androidTest/                   # Instrumented tests (AppDatabaseTest)
│   │   └── test/                          # Unit tests
│   ├── build.gradle                       # App build config with Protobuf, CMake, signing configs
│   └── proguard-rules.pro
├── gradle/                                # Gradle wrapper
├── build.gradle                           # Project build config (plugins, repositories)
├── settings.gradle                        # Project settings
├── variables.gradle                       # Version variables (minSdk, compileSdk, targetSdk, AndroidX)
└── README.md                              # This file
```

## Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17 or later
- Android SDK 37 (compileSdkVersion)
- Minimum SDK 33 (Android 13.0)
- NDK for native C++ compilation (CMake 3.22.1+, C++17)

### Building the Project

#### Using Android Studio
1. Open Android Studio
2. Select "Open an Existing Project"
3. Navigate to the `android` directory
4. Wait for Gradle sync to complete
5. Click "Run" or press Shift+F10

#### Using Command Line
```bash
cd android

# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing config in local.properties)
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug
```

#### Using Batch Scripts (Windows)
```bash
# Clean and build
clean-build.bat

# Build APK
build-apk.bat

# Force build (stops Gradle daemon first)
force-build.bat
```

### APK Location
After building, the APK can be found at:
```
android/app/build/outputs/apk/debug/app-debug.apk
```

### Signing (Release Builds)
Configure signing in `local.properties`:
```properties
RELEASE_STORE_FILE=release.keystore
RELEASE_STORE_PASSWORD=your_password
RELEASE_KEY_ALIAS=your_alias
RELEASE_KEY_PASSWORD=your_key_password
```

## Configuration

### Server Connection
Update the base URL in `app/src/main/java/com/chronie/homemoney/di/NetworkModule.kt`:
```kotlin
private const val BASE_URL = "http://YOUR_SERVER_IP:3010/"
```

### API Keys
Configure API keys in the Settings screen:
- **SiliconFlow API Key**: Required for AI expense recognition feature (calls `v1/chat/completions`)

### Versioning
The app uses dynamic versioning based on build date and time:
- `versionCode`: Unix timestamp-based unique identifier
- `versionName`: Format `1.YYYYMMDD.HHMM`

## Features Guide

### 1. Expense Management

#### Expense Categories (26 types)
Daily Goods, Luxury, Communication, Food, Snacks, Cold Drinks, Convenience Food, Textiles, Beverages, Condiments, Transportation, Dining, Medical, Fruits, Other, Seafood, Dairy, Gifts, Travel, Government, Utilities, Beauty, Bean Products, Cosmetics, Electronics, Household Appliances, Hardware, Clothing

#### Adding Expenses
- Tap the "+" button on the expense list screen
- Fill in expense details (type, amount, date, notes)
- Save to local database and sync queue

#### AI Recognition
- Tap the AI icon in the add expense screen
- Select images from gallery or camera, or enter text description
- Crop images using uCrop if needed
- Images are sent as base64-encoded data URIs to SiliconFlow API
- Review and edit recognized expenses
- Save all records at once

#### Viewing Expenses
- Scroll through the expense list with LazyColumn
- View statistics card showing total, average, and median
- Pull to refresh for latest data from server
- Automatic pagination for large datasets

#### Filtering & Search
- Tap the filter icon in the toolbar
- Set date range, expense types, amount range
- Enter keywords to search notes
- Apply filters to narrow down results

### 2. Budget Management

#### Setting Budget
- Go to Settings → Budget Management
- Enable budget tracking
- Set monthly limit and warning threshold (default 80%)
- Save settings

#### Monitoring Budget
- View budget card on expense list screen
- See current spending, remaining amount, percentage, daily average, and recommended daily spending
- Color-coded status indicators:
  - Green: Normal (below warning threshold)
  - Yellow: Warning (above threshold)
  - Red: Over budget

### 3. Data Visualization

#### Charts Screen
- View weekly spending bar chart with customizable time range
- Weekday radar chart for spending pattern analysis across days of the week
- Tap on weekdays to see detailed breakdown with expense type distribution
- Filter by time range (1 week, 1 month, 3 months, 6 months, 1 year)
- Animated ExpressiveLinearProgressIndicator for loading states

### 4. Data Synchronization

#### Server Sync
- Background sync runs every hour via WorkManager with network connectivity constraint
- NetworkMonitor triggers sync when connectivity becomes available
- SyncScheduler manages periodic and network-triggered syncs
- Full sync: uploads local changes, downloads server updates, resolves conflicts
- Sync queue tracks pending operations for offline resilience

#### LAN Device Sync (gRPC + UDP)
- Uses Protocol Buffers for efficient serialization
- UDP multicast discovery on port 12345 (group 239.255.255.250)
- gRPC sync server on port 50051
- Native C++ JNI engine handles socket-level communication
- Conflict resolution based on timestamps (newer version wins)
- Singleton DeviceSyncManagerFactory ensures server remains running

#### Manual Sync
- Go to Settings → Data Sync
- Tap "Sync Now" button
- View sync status and last sync time
- See pending items count

#### Conflict Resolution
- Automatic resolution based on timestamps (updatedAt)
- Newer version always wins
- Conflicts are logged for review

### 5. Data Import/Export

#### Export Expenses
- Go to Settings → Import/Export
- Select "Export to Excel"
- Choose date range
- Save Excel file to device storage

#### Import Expenses
- Go to Settings → Import/Export
- Select "Import from Excel"
- Choose Excel file from device
- Review and confirm imported data
- Save to database

### 6. Membership Management

#### User Profile
- View and edit user profile with avatar
- Avatar upload via server API (`PUT /api/members/members/{username}/avatar`)
- Offline membership status caching in PreferencesManager

#### Login/Logout
- Server-based login with username
- Persistent login state in SharedPreferences
- Skip login option available

### 7. Language & Theme

#### Language Settings
- Go to Settings → Language
- 12 supported languages: English, Indonesian, Japanese, Korean, Malay, Simplified Chinese (Mainland & Singapore), Thai, Traditional Chinese (Hong Kong, Macau, Taiwan), Vietnamese
- UI updates immediately without restart
- Preference persisted via LocaleConfig

#### Theme Customization
- Go to Settings → Theme
- Dynamic Color: Use colors from your wallpaper (Android 12+)
- Manual Color Selection: Use color picker to select custom accent colors
- Preview theme changes in real-time
- Save custom theme preferences

### 8. Developer Mode
- Go to Settings → Developer Options
- Enable Developer Mode
- Access Database Test screen from bottom navigation
- Add test data, view all records with sync status, and clear database
- View error logs and crash reports

## Database Schema

The database uses Room with SQLCipher encryption. All primary keys are TEXT (UUID strings).

### Expenses Table (version 6)
```sql
CREATE TABLE expenses (
    id TEXT NOT NULL PRIMARY KEY,
    type TEXT NOT NULL,
    remark TEXT,
    amount REAL NOT NULL,
    date TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    updated_at INTEGER NOT NULL,
    deleted_at INTEGER,
    is_synced INTEGER NOT NULL DEFAULT 0
)
-- Indexes: date, type, is_synced, updated_at
```

### Budgets Table
```sql
CREATE TABLE budgets (
    id INTEGER PRIMARY KEY NOT NULL,
    monthly_limit REAL NOT NULL,
    warning_threshold REAL NOT NULL DEFAULT 0.8,
    is_enabled INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL
)
```

### Members Table
```sql
CREATE TABLE members (
    id TEXT NOT NULL PRIMARY KEY,
    username TEXT,
    avatar TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    is_synced INTEGER NOT NULL DEFAULT 0
)
```

### Sync Queue Table
```sql
CREATE TABLE sync_queue (
    id INTEGER PRIMARY KEY NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    operation TEXT NOT NULL,
    data TEXT NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL
)
```

### Database Migrations
- **1→2**: Added budgets table
- **2→3**: Added date column to expenses, migrated from time field
- **3→4**: Added unique index on server_id
- **4→5**: Added version, updated_at, deleted_at columns; removed server_id index
- **5→6**: Recreated expenses table with TEXT id primary key, rebuilt indexes

## API Integration

### Expense API
- `GET /api/expenses` - List expenses with pagination, keyword, type, month, amount range, and sort filters
- `POST /api/expenses` - Create new expense
- `PUT /api/expenses/{id}` - Update expense
- `POST /api/expenses/batch` - Batch create expenses
- `DELETE /api/expenses/{id}` - Soft delete expense
- `DELETE /api/expenses/{id}/hard` - Hard delete expense
- `GET /api/expenses/statistics` - Get expense statistics with filters
- `POST /api/expenses/sync` - Bidirectional sync with local changes and server updates

### AI Recognition API (SiliconFlow)
- `POST v1/chat/completions` - Parse text or images to extract expense records
- Uses Qwen vision models via SiliconFlow API
- Supports multiple images as base64 data URIs in a single request
- Separate OkHttp/Retrofit instance with dedicated timeouts and logging

### Member API
- `GET /api/health/lite` - Health check (also used by HealthCheckService)
- `POST /api/members/members` - Get or create member by username
- `GET /api/members/members/{username}` - Get member info
- `PUT /api/members/members/{username}/avatar` - Update member avatar

### API Response Format
```json
{
  "data": { ... },
  "message": "success",
  "success": true
}
```

## Protobuf & gRPC (LAN Sync)

### Protocol Definition (`sync.proto`)
- **SyncEntity**: entity_type, entity_id, operation, data, timestamp
- **DeviceSyncData**: device_id, device_name, sync_timestamp, entities[]
- **SyncService**: Sync RPC (request/response), Ping RPC (device discovery)
- **SyncRequest/Response**: Wraps DeviceSyncData with accepted/error status

### Native C++ Engine (`native-lib.cpp`)
- JNI bridge between Kotlin and native socket server
- Handles TCP socket communication with read_all/write_all helpers
- Calls back to Kotlin `NativeSyncEngine.handleIncomingSyncRequest()` for data processing
- Server lifecycle managed by `LanDeviceSyncManager`

## Testing

### Unit Tests
```bash
./gradlew test
```

### Instrumented Tests
```bash
./gradlew connectedAndroidTest
```

### Manual Testing
1. Enable Developer Mode in Settings
2. Access Database Test screen
3. Add test data and verify operations
4. Check sync functionality (server and LAN)
5. Test offline mode by disabling network
6. Test Excel import/export
7. Test LAN device sync between multiple devices on same WiFi network

## Troubleshooting

### Build Issues

#### Gradle Sync Failed
- Check internet connection
- Invalidate caches: File → Invalidate Caches / Restart
- Delete `.gradle` folder and sync again

#### R.jar File Locked
- Stop all Gradle daemons: `./gradlew --stop`
- Close Android Studio
- Delete `app/build` directory
- Restart and rebuild

#### Native Library Build Failed
- Ensure NDK is installed via SDK Manager
- Verify CMake 3.22.1+ is available
- Check `app/src/main/cpp/CMakeLists.txt` configuration

### Runtime Issues

#### App Crashes on Startup
- Check Logcat for error messages
- Verify database migrations are correct (version 6 with all 5 migrations)
- Clear app data and reinstall
- Check error reports in Developer Mode

#### Sync Not Working
- Check network connection
- Verify server is running and accessible at the configured BASE_URL
- Review sync logs in Settings
- For LAN sync: Ensure devices are on the same WiFi network and multicast is not blocked

#### Language Not Changing
- Ensure language is saved in Settings
- Check that string resources exist for all 12 languages

#### Import/Export Issues
- Verify storage permissions are granted
- Check Excel file format is correct (.xlsx)
- Ensure file path is accessible

## Performance Optimization

### Database
- Indexes on frequently queried columns (date, type, is_synced, updated_at)
- Pagination for large datasets via Paging 3
- Efficient queries using Room's compile-time verification
- Upsert pattern for conflict-free sync merges

### Network
- Connection pooling with OkHttp
- Automatic retry with exponential backoff
- 5-second connect/read/write timeouts
- NetworkMonitor for connectivity-aware sync scheduling

### UI
- LazyColumn for efficient list rendering
- Image loading with Coil's memory and disk caching
- Debounced search input to reduce queries
- Expressive loading indicators for perceived performance

### Native
- C++ socket server for low-latency device sync
- Protobuf binary serialization for minimal data transfer
- JNI calls offloaded from main thread

## Security Considerations

### Data Protection
- SQLCipher 4.17.0 encryption for local database with 32-character random passphrase
- Passphrase stored in EncryptedSharedPreferences with AES256_GCM
- Master Key backed by Android Keystore (AES256_GCM)
- Cleartext traffic enabled for local development; use HTTPS in production
- `minifyEnabled` and `shrinkResources` for release builds

### Authentication
- Username-based login with persistent SharedPreferences
- AuthInterceptor for automatic token injection

### Error Reporting
- MockErrorReportApi for development (no data sent to server)
- ErrorInfo captures device info, stack trace, and thread information
- LogFileManager for local log persistence
- Rate-limited error queue (max 10, 3 retries)

### Permissions
- `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`: Network operations and sync
- `CHANGE_WIFI_MULTICAST_STATE`: LAN device discovery
- `CAMERA`: AI expense recognition (not required)
- `READ_EXTERNAL_STORAGE` (max SDK 32), `WRITE_EXTERNAL_STORAGE` (max SDK 32): Excel import/export
- `READ_MEDIA_IMAGES` (Android 13+): Gallery image selection

## Contributing

### Code Style
- Follow Kotlin coding conventions
- Write meaningful commit messages in English (Conventional Commits format)
- Add comments for complex logic

### Pull Request Process
1. Create a feature branch
2. Make your changes
3. Write/update tests
4. Update documentation
5. Submit pull request with description

## License

This project is part of the Home Finance Tracker application. See the main project README for license information.

## Contact & Support

For issues, questions, or contributions, please refer to the main project repository.

## Acknowledgments

- Built with Jetpack Compose and Material Design 3
- Uses SiliconFlow API for AI expense recognition
- Uses fastExcel for Excel file handling
- Uses uCrop for image cropping
- Uses gRPC and Protocol Buffers for LAN device sync
- Native C++ sync engine via JNI