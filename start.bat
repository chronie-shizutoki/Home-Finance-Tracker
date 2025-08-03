@echo off
setlocal enabledelayedexpansion

:: ======================
:: One-click Server Startup Script
:: ======================

:: Validate server directory

cd server && npm run start

endlocal