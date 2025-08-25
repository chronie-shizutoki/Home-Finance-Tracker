## [English](#en) | [中文](#zh)

<a id="en"></a>

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

<a id="zh"></a>

# 家庭财务管理系统

## 项目概述
家庭财务管理系统是一款现代化的多语言财务管理应用，旨在帮助用户轻松跟踪收入和支出、分析消费模式并提高财务透明度。该系统提供了用户友好的界面，具备强大的数据可视化和分析功能。

## 主要功能
- **多语言支持**：自动适配的用户界面文本和日期格式（英语、中文等）
- **支出跟踪**：添加/查看包含类型、金额、日期和备注等详细信息的记录
- **数据导出与导入**：生成 CSV 报告并支持数据备份/恢复功能
- **智能分析**：
  - 按类别分类（食品、购物、交通等）
  - 交互式消费趋势图表，支持多种可视化选项（柱状图、饼图、折线图、环形图、雷达图）
  - 高级搜索和过滤系统（按类型、日期范围、金额范围）
- **预算管理**：设置和跟踪每月支出限额并提供警告
- **安全功能**：数据加密和安全存储
- **响应式设计**：同时针对桌面和移动设备进行了优化

## 设置与使用
### 要求
- Node.js ≥ 18.0.0
- npm ≥ 9.0.0

### 快速开始
```bash
git clone https://github.com/quiettimejsg/homemoney.git
cd homemoney
# 安装客户端和服务器的依赖
npm install
# 启动开发服务器
npm run dev  # 同时启动前端和后端
# 或者，分别启动服务
npm run dev:client  # 仅启动前端开发服务器
npm run dev:server  # 仅启动后端开发服务器
```

## 项目结构
- `client/`: Vue.js 前端应用程序
- `server/`: Node.js/Express 后端服务器
- `common.css`: 共享的 CSS 样式
- `start.bat`: Windows 启动脚本

## 可用脚本
### 根目录
- `npm run dev`: 以开发模式同时启动前端和后端
- `npm run dev:client`: 仅以开发模式启动前端
- `npm run dev:server`: 仅以开发模式启动后端
- `npm run build`: 构建生产环境的前端代码

### 客户端目录
- `npm run dev`: 启动 Vite 开发服务器（http://localhost:5173）
- `npm run build`: 构建生产环境的前端代码
- `npm run lint`: 在客户端代码库上运行 ESLint

### 服务器目录
- `npm run dev`: 使用 nodemon 启动后端进行开发
- `npm run start`: 以生产模式启动后端
- `npm run test`: 运行后端测试

## 技术栈
- **前端**：Vue 3、Vite、Element Plus、Chart.js、Vue I18n、Pinia、Vue Router
- **后端**：Express.js、SQLite3、Sequelize ORM
- **工具**：Day.js（日期处理）、Papa Parse（CSV 解析）、bcrypt（加密）
- **开发工具**：ESLint、Jest

## 版本信息
当前版本：2025.8.26
