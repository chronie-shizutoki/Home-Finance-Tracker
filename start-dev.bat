@echo off
setlocal enabledelayedexpansion

:: ======================
:: One-click Server Startup Script
:: ======================

:: Validate server directory

cd client && npm run dev

endlocal