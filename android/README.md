# Home Money - Android Native Application

## Overview

This is the native Android implementation of the Home Money financial tracking application. The app is built using modern Android development practices with Kotlin, Jetpack Compose, and follows Clean Architecture principles. It provides a hybrid architecture that allows gradual migration from WebView-based features to native implementations.

## Features

### Core Functionality
- **Expense Tracking**: Add, view, edit, and delete expense records with support for 21 expense categories
- **AI-Powered Recognition**: Intelligent expense recognition from images and text using SiliconFlow API
- **Budget Management**: Set monthly spending limits with warning thresholds and real-time usage tracking
- **Data Synchronization**: Automatic background sync with server, offline support with local caching
- **Search & Filtering**: Advanced filtering by date range, expense type, amount range, and keywords
- **Multi-language Support**: Full internationalization support for English, Simplified Chinese, and Traditional Chinese

### Technical Features
- **Hybrid Architecture**: Seamless integration between native features and WebView content
- **Encrypted Database**: SQLCipher-encrypted local storage for sensitive financial data
- **Material Design 3**: Modern UI following Google's latest design guidelines
- **Edge-to-Edge Display**: Immersive full-screen experience
- **Developer Mode**: Built-in database testing and debugging tools

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
│  (Room DB + Retrofit + Mappers)         │
├─────────────────────────────────────────┤
│         Framework Layer                 │
│  (Android SDK + Third-party Libraries)  │
└─────────────────────────────────────────┘
```

### Key Components

#### Data Layer
- **Room Database**: Encrypted local storage with SQLCipher
- **Retrofit**: RESTful API client for server communication
- **Repository Pattern**: Abstraction layer for data sources
- **Data Mappers**: Convert between Entity, Domain, and DTO models

#### Domain Layer
- **Use Cases**: Business logic encapsulation
- **Domain Models**: Pure Kotlin data classes
- **Repository Interfaces**: Contracts for data operations

#### Presentation Layer
- **Jetpack Compose**: Modern declarative UI framework
- **ViewModels**: UI state management with Kotlin Flow
- **Navigation Component**: Type-safe navigation between screens

## Tech Stack

### Core Technologies
- **Language**: Kotlin 1.9.20
- **UI Framework**: Jetpack Compose (BOM 2023.10.01)
- **Dependency Injection**: Hilt 2.48.1
- **Database**: Room 2.6.1 with SQLCipher 4.5.4
- **Networking**: Retrofit 2.9.0 + OkHttp 4.12.0
- **Async**: Kotlin Coroutines 1.7.3 + Flow

### Key Libraries
- **Material Design 3**: Modern UI components
- **Navigation Compose**: Type-safe navigation
- **Paging 3**: Efficient data loading
- **WorkManager**: Background task scheduling
- **Coil**: Image loading and caching
- **Gson**: JSON serialization
- **DataStore**: Preferences storage

### Security
- **SQLCipher**: Database encryption
- **EncryptedSharedPreferences**: Secure key storage
- **Android Keystore**: Hardware-backed key management

## Project Structure

```
android/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/chronie/homemoney/
│   │   │   │   ├── core/              # Core utilities
│   │   │   │   │   ├── common/        # Common utilities
│   │   │   │   │   ├── hybrid/        # Hybrid architecture
│   │   │   │   │   └── network/       # Network monitoring
│   │   │   │   ├── data/              # Data layer
│   │   │   │   │   ├── local/         # Room database
│   │   │   │   │   │   ├── dao/       # Data access objects
│   │   │   │   │   │   └── entity/    # Database entities
│   │   │   │   │   ├── remote/        # API layer
│   │   │   │   │   │   ├── api/       # Retrofit interfaces
│   │   │   │   │   │   ├── dto/       # Data transfer objects
│   │   │   │   │   │   └── interceptor/ # HTTP interceptors
│   │   │   │   │   ├── repository/    # Repository implementations
│   │   │   │   │   ├── mapper/        # Data mappers
│   │   │   │   │   └── sync/          # Sync management
│   │   │   │   ├── di/                # Dependency injection
│   │   │   │   ├── domain/            # Domain layer
│   │   │   │   │   ├── model/         # Domain models
│   │   │   │   │   ├── repository/    # Repository interfaces
│   │   │   │   │   ├── usecase/       # Use cases
│   │   │   │   │   └── sync/          # Sync interfaces
│   │   │   │   ├── ui/                # Presentation layer
│   │   │   │   │   ├── budget/        # Budget management
│   │   │   │   │   ├── expense/       # Expense tracking
│   │   │   │   │   ├── main/          # Main screen
│   │   │   │   │   ├── settings/      # Settings
│   │   │   │   │   ├── test/          # Testing screens
│   │   │   │   │   ├── theme/         # Material theme
│   │   │   │   │   └── welcome/       # Welcome screen
│   │   │   │   ├── worker/            # Background workers
│   │   │   │   └── MainActivity.kt    # Main activity
│   │   │   ├── res/                   # Resources
│   │   │   │   ├── values/            # English strings
│   │   │   │   ├── values-zh/         # Simplified Chinese
│   │   │   │   └── values-zh-rTW/     # Traditional Chinese
│   │   │   └── AndroidManifest.xml
│   │   └── androidTest/               # Instrumented tests
│   └── build.gradle                   # App build config
├── gradle/                            # Gradle wrapper
├── build.gradle                       # Project build config
├── settings.gradle                    # Project settings
└── README.md                          # This file
```

## Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17 or later
- Android SDK 35 (Android 15)
- Minimum SDK 23 (Android 6.0)

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

# Build release APK
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

## Configuration

### Server Connection
Update the base URL in `NetworkModule.kt`:
```kotlin
private const val BASE_URL = "http://YOUR_SERVER_IP:3010/"
```

### API Keys
Configure API keys in the Settings screen:
- **SiliconFlow API Key**: Required for AI expense recognition feature

## Features Guide

### 1. Expense Management

#### Adding Expenses
- Tap the "+" button on the expense list screen
- Fill in expense details (type, amount, date, notes)
- Save to local database and sync queue

#### AI Recognition
- Tap the AI icon in the add expense screen
- Select images or enter text description
- Review and edit recognized expenses
- Save all records at once

#### Viewing Expenses
- Scroll through the expense list
- View statistics card showing total, average, and median
- Pull to refresh for latest data
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
- See current spending, remaining amount, and percentage
- Color-coded status indicators:
  - Green: Normal (below warning threshold)
  - Yellow: Warning (above threshold)
  - Red: Over budget

### 3. Data Synchronization

#### Automatic Sync
- Background sync runs every hour
- Syncs when network becomes available
- Uploads local changes to server
- Downloads server updates

#### Manual Sync
- Go to Settings → Data Sync
- Tap "Sync Now" button
- View sync status and last sync time
- See pending items count

#### Conflict Resolution
- Automatic resolution based on timestamps
- Newer version always wins
- Conflicts are logged for review

### 4. Language Settings
- Go to Settings → Language
- Choose from English, Simplified Chinese, or Traditional Chinese
- UI updates immediately without restart
- Preference is saved and persists across app restarts

### 5. Developer Mode
- Go to Settings → Developer Options
- Enable Developer Mode
- Access database testing screen from main menu
- Add test data, view records, and clear database

## Database Schema

### Expenses Table
```sql
CREATE TABLE expenses (
    id INTEGER PRIMARY KEY,
    type TEXT NOT NULL,
    remark TEXT,
    amount REAL NOT NULL,
    time INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    is_synced INTEGER NOT NULL DEFAULT 0,
    server_id TEXT
)
```

### Budgets Table
```sql
CREATE TABLE budgets (
    id INTEGER PRIMARY KEY,
    monthly_limit REAL NOT NULL,
    warning_threshold REAL NOT NULL DEFAULT 0.8,
    is_enabled INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL
)
```

### Sync Queue Table
```sql
CREATE TABLE sync_queue (
    id INTEGER PRIMARY KEY,
    entity_type TEXT NOT NULL,
    entity_id INTEGER NOT NULL,
    operation TEXT NOT NULL,
    data TEXT NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL
)
```

## API Integration

### Expense API
- `GET /api/expenses` - List expenses with pagination and filters
- `POST /api/expenses` - Create new expense
- `PUT /api/expenses/:id` - Update expense
- `DELETE /api/expenses/:id` - Delete expense
- `GET /api/expenses/statistics` - Get expense statistics

### AI Recognition API
- `POST /api/ai/parse` - Parse text or images to extract expense records
- Uses SiliconFlow API with Qwen models
- Supports multiple images in a single request

### Member API
- `GET /api/members/current` - Get current user info
- `POST /api/members` - Create or update member

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
4. Check sync functionality
5. Test offline mode by disabling network

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

### Runtime Issues

#### App Crashes on Startup
- Check Logcat for error messages
- Verify database migrations are correct
- Clear app data and reinstall

#### Sync Not Working
- Check network connection
- Verify server is running and accessible
- Check API key configuration
- Review sync logs in Settings

#### Language Not Changing
- Ensure language is saved in Settings
- Restart the app
- Check that string resources exist for all languages

## Performance Optimization

### Database
- Indexes on frequently queried columns (time, type, is_synced)
- Pagination for large datasets
- Efficient queries using Room's compile-time verification

### Network
- Request/response caching with OkHttp
- Automatic retry with exponential backoff
- Connection pooling for better performance

### UI
- LazyColumn for efficient list rendering
- Image loading with Coil's memory and disk caching
- Debounced search input to reduce queries

## Security Considerations

### Data Protection
- SQLCipher encryption for local database
- Encrypted SharedPreferences for sensitive data
- HTTPS for all network communication
- No sensitive data in logs (production builds)

### Authentication
- JWT token-based authentication
- Automatic token refresh
- Secure token storage in EncryptedSharedPreferences

## Contributing

### Code Style
- Follow Kotlin coding conventions
- Use ktlint for code formatting
- Write meaningful commit messages
- Add comments for complex logic

### Pull Request Process
1. Create a feature branch
2. Make your changes
3. Write/update tests
4. Update documentation
5. Submit pull request with description

## Version History

### Current Version: 1.0.0

#### Completed Features
- ✅ Multi-language support (English, Simplified Chinese, Traditional Chinese)
- ✅ Hybrid architecture with WebView integration
- ✅ Encrypted Room database with SQLCipher
- ✅ Expense list with pagination and filtering
- ✅ Add/edit expense functionality
- ✅ AI-powered expense recognition
- ✅ Budget management with real-time tracking
- ✅ Data synchronization with conflict resolution
- ✅ Background sync with WorkManager
- ✅ Network monitoring and auto-sync
- ✅ Material Design 3 UI
- ✅ Developer mode with database testing

#### Known Issues
- None currently reported

## License

This project is part of the Home Money application. See the main project README for license information.

## Contact & Support

For issues, questions, or contributions, please refer to the main project repository.

## Acknowledgments

- Built with Jetpack Compose and Material Design 3
- Uses SiliconFlow API for AI features
- Inspired by modern Android development best practices
