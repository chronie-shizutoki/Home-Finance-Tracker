# Household Finance Manager

## Project Overview
The Household Finance Manager is a modern multilingual financial management application designed to help users easily track income and expenses, analyze spending patterns, and improve financial transparency. The system provides a user-friendly interface with powerful data visualization and analysis capabilities.

## Key Features
- **Multilingual Support**: Auto-adapting UI text and date formats (English, Chinese, etc.)
- **Expense Tracking**: Add/view records with details including type, amount, date, and notes
- **Data Export & Import**: Generate CSV reports and support data backup/restore functionality
- **Smart Analytics**:
  - Category breakdowns (food, shopping, transportation, etc.)
  - Interactive spending trend charts with multiple visualization options (bar, pie, line, doughnut, radar charts)
  - Advanced search and filtering system (by type, date range, amount range)
- **Budget Management**: Set and track monthly spending limits with warnings
- **Security Features**: Data encryption and secure storage
- **Responsive Design**: Optimized for both desktop and mobile devices

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
- `server/`: Node.js/Express backend server
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
- **Frontend**: Vue 3, Vite, Element Plus, Chart.js, Vue I18n, Pinia, Vue Router
- **Backend**: Express.js, SQLite3, Sequelize ORM
- **Utilities**: Day.js (date handling), Papa Parse (CSV parsing), bcrypt (encryption)
- **Dev Tools**: ESLint, Jest
