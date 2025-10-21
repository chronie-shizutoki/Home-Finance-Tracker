# Domestic Fiscal Resource Management System（DFRMS）

## Project Overview
The Domestic Fiscal Resource Management System（DFRMS） is a modern multilingual financial management application designed to help users easily track income and expenses, analyze spending patterns, and improve financial transparency. The system provides a user-friendly interface with powerful data visualization and analysis capabilities, and now supports mini-applications for extended functionality.

## Key Features
- **Multilingual Support**: Auto-adapting UI text and date formats (English, Chinese, etc.)
- **Expense Tracking**: Add/view records with details including type, amount, date, and notes
- **Data Export & Import**: Generate Excel reports and support data backup/restore functionality
- **Smart Analytics**:
  - Category breakdowns (food, shopping, transportation, etc.)
  - Interactive spending trend charts with multiple visualization options (bar, pie, line, doughnut, radar charts)
  - Advanced search and filtering system (by type, date range, amount range)
- **Budget Management**: Set and track monthly spending limits with warnings
- **Security Features**: Data encryption and secure storage
- **Responsive Design**: Optimized for both desktop and mobile devices
- **Mini-Applications System**:
  - Built-in mini-app manager for discovering and launching applications
  - Support for automatic scanning of mini-applications in the public directory
  - Custom metadata definition via package.json
  - Example calculator mini-application included
- **Debt Management**: Track and manage debts with detailed records
- **To-Do List**: Manage tasks and reminders related to financial activities
- **Donation Tracking**: Record and track charitable donations

## Setup & Usage
### Requirements
- Node.js ≥ 18.0.0
- npm ≥ 9.0.0

### Quick Start
```bash
git clone https://github.com/quiettimejsg/homemoney.git
cd homemoney
# Install dependencies for client and server
npm install
# Start development server
npm run dev  # Starts both frontend and backend
# Alternatively, start services separately
npm run dev:client  # Starts frontend development server
npm run dev:server  # Starts backend development server
```

## Project Structure
- `client/`: Vue.js frontend application
  - `public/`: Static assets and mini-applications
    - `miniapp/`: Mini-applications directory containing individual apps
  - `src/`: Source code
    - `components/`: Reusable UI components
    - `views/`: Application views/pages
    - `api/`: API service calls
    - `utils/`: Utility functions
    - `assets/`: Static assets
    - `styles/`: CSS stylesheets
    - `locales/`: Internationalization files
- `server/`: Node.js/Express backend server
  - `src/`: Source code
    - `controllers/`: Request handlers
    - `models/`: Database models
    - `routes/`: API routes
    - `utils/`: Server utilities
  - `data/`: Database files and migrations
- `common.css`: Shared CSS styles
- `start.bat`: Windows startup script

## Available Scripts
### Root Directory
- `npm run dev`: Start both frontend and backend in development mode
- `npm run dev:client`: Start only the frontend in development mode
- `npm run dev:server`: Start only the backend in development mode
- `npm run build`: Build the frontend for production

### Client Directory
- `npm run dev`: Start Vite development server (http://localhost:5173)
- `npm run build`: Build the frontend for production
- `npm run lint`: Run ESLint on the client codebase

### Server Directory
- `npm run dev`: Start backend with nodemon for development
- `npm run start`: Start backend in production mode
- `npm run test`: Run backend tests

## Tech Stack
- **Frontend**:
  - Vue 3 (JavaScript framework)
  - Vite (build tool)
  - Element Plus (Vue 3 UI component library)
  - Chart.js (data visualization)
  - Vue I18n (internationalization)
  - Pinia (state management)
  - Vue Router (client-side routing)
  - Axios (HTTP client)
- **Backend**:
  - Express.js (web server framework)
  - SQLite3 (database)
  - Sequelize ORM (database abstraction)
- **Utilities**:
  - Day.js (date handling)
  - Papa Parse (CSV parsing)
  - bcrypt (encryption)
- **Dev Tools**:
  - ESLint (code quality)
  - Jest (testing)
  - nodemon (development auto-reload)
