@echo off
echo Stopping all Gradle daemons...
call gradlew.bat --stop

echo Waiting for processes to release files...
timeout /t 5 /nobreak

echo Attempting to delete build directory...
rmdir /s /q app\build 2>nul

echo Waiting...
timeout /t 3 /nobreak

echo Building APK...
call gradlew.bat assembleDebug --no-daemon

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo Build successful!
    echo APK location: app\build\outputs\apk\debug\app-debug.apk
    echo ========================================
) else (
    echo.
    echo ========================================
    echo Build failed!
    echo Please close all programs that might be using the files
    echo and try again, or restart your computer.
    echo ========================================
)

pause
