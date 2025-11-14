@echo off
echo Cleaning Gradle build cache...
call gradlew.bat clean
echo.
echo Build cache cleaned successfully!
echo.
echo Now building debug APK...
call gradlew.bat assembleDebug
