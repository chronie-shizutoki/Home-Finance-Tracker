# Home Finance Tracker

## Project Overview
The Home Finance Tracker is a modern multilingual financial management application designed to help users easily track income and expenses, analyze spending patterns, and improve financial transparency. The system provides a user-friendly interface with powerful data visualization and analysis capabilities. Supports Web, Android platforms.

## Key Features
- **Multilingual Support**: Auto-adapting UI text and date formats (English, Simplified Chinese, Traditional Chinese)
- **Expense Tracking**: Add/view records with details including type, amount, date, and notes
- **Data Export & Import**: Generate Excel reports and support data backup/restore functionality
- **Smart Analytics**:
  - Category breakdowns (food, shopping, transportation, etc.)
  - Interactive spending trend charts with multiple visualization options (bar, pie, line, doughnut, radar charts)
  - Advanced search and filtering system (by type, date range, amount range)
- **Budget Management**: Set and track monthly spending limits with warnings
- **Membership System**: Subscription management with premium features
- **Security Features**: Data encryption, secure storage
- **Markdown Support**: Rich text display with syntax highlighting
- **Responsive Design**: Optimized for both desktop and mobile devices
- **Android App**: Native Android application support

## Setup & Usage
### Requirements
- Node.js ≥ 18.0.0
- npm ≥ 9.0.0
- Go ≥ 1.26


### Quick Start
```bash
cd server-go
# Install dependencies
go mod download
# Run the server (port 3010)
go run cmd/server/main.go
# Or build and run
go build -o server.exe cmd/server/main.go
./server.exe
```

The Go server automatically serves the frontend static files from `client/dist` when available, and uses `database.sqlite` in the project root directory.

## Project Structure
- `client/`: Vue.js frontend application
  - `public/`: Static assets
  - `src/`: Source code
    - `api/`: API service calls
    - `components/`: Reusable UI components (Glass design system)
    - `composables/`: Vue composition API utilities
    - `views/`: Application views/pages
    - `router/`: Vue Router configuration
    - `stores/`: Pinia state management
    - `utils/`: Utility functions
    - `assets/`: Static assets
    - `styles/`: CSS stylesheets
    - `locales/`: Internationalization files (en-US, zh-CN, zh-TW)
  - `vite.config.js`: Vite build configuration with PWA support
  - `data/`: Database files
- `server-go/`: Go/Gin backend server (alternative implementation)
  - `cmd/server/`: Application entry point
  - `internal/`: Core application code
    - `handler/`: HTTP request handlers
    - `handlers/`: Expense-specific handlers
    - `models/`: Data models and query structures
    - `repository/`: Database access layer (GORM)
    - `routes/`: API route definitions
    - `service/`: Business logic services
  - `pkg/`: Shared packages
    - `database/`: Database connection and migration
    - `utils/`: Utility functions and response helpers
- `android/`: Android native application (Kotlin)
  - `app/`: Main Android app module
  - `gradle/`: Gradle wrapper configuration
- `.github/`: GitHub Actions workflows
- `.vercel/`: Vercel deployment configuration
- `common.css`: Shared CSS styles
- `start.bat` / `start-dev.bat`: Windows startup scripts

## Available Scripts
### Root Directory
- `npm run dev`: Start backend server
- `npm run dev:client`: Start frontend in development mode (port 5173)
- `npm run dev:server`: Start backend with nodemon for development (port 3010)
- `npm run build`: Build the frontend for production
- `npm run start`: Start backend in production mode

### Client Directory
- `npm run dev`: Start Vite development server (http://localhost:5173)
- `npm run build`: Build the frontend for production
- `npm run lint`: Run ESLint on the client codebase


### Server-Go Directory
- `go run cmd/server/main.go`: Start Go server in development mode (port 3010)
- `go build -o server.exe cmd/server/main.go`: Build Go server binary
- `go mod download`: Download Go module dependencies
- `go mod tidy`: Clean up Go module dependencies

## Tech Stack
- **Frontend**:
  - Vue 3.5 (JavaScript framework)
  - Vite 8 (build tool)
  - Vue Router 5 (client-side routing)
  - Pinia 3 (state management)
  - Vue I18n 11 (internationalization)
  - Chart.js 4 (data visualization)
  - Font Awesome 7 (icons)
  - Day.js (date handling)
  - Papa Parse (CSV parsing)
  - XLSX (Excel export/import)
  - Marked + Highlight.js (Markdown rendering)
  - Lunar JavaScript (Chinese lunar calendar)
  - vite-plugin-pwa (PWA support)
- **Backend**:
  - Go 1.26
  - Gin (web framework)
  - GORM (ORM)
  - SQLite (database, via glebarez driver)
  - excelize (Excel export/import)
  - gopsutil (system monitoring)
  - uuid (unique ID generation)
- **Android**:
  - Kotlin
  - Capacitor
  - Hilt (dependency injection)
  - WorkManager (background sync)
- **Dev Tools**:
  - ESLint (code quality)
  - Jest (testing)
  - nodemon (development auto-reload)
  - unplugin-auto-import / unplugin-vue-components (auto import)

## API Documentation
The application provides a comprehensive API documentation endpoint that returns all available endpoints with descriptions in both English and Chinese.

### Accessing the API Documentation
To view the complete API documentation, start the server and navigate to:

```
http://localhost:3010/api
```

or view the [api-help.json](api-help.json) file in the project root — snapshot as of 2026-03-28.

This endpoint returns a JSON response containing:
- All available API endpoints organized by category
- HTTP methods (GET, POST, PUT, DELETE)
- English and Chinese descriptions for each endpoint
- Usage instructions for each API

### API Categories
The documentation includes endpoints for:
- Base system health checks
- Expense tracking and management
- Expense statistics and analysis
- JSON file operations
- Member and subscription management
- Data import/export functionality
- Error reporting
- Logging and monitoring

The API documentation is dynamically generated and will reflect any changes to the API structure.
