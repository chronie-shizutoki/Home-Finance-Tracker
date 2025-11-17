@echo off
echo Running API Test...

echo Checking server status...
echo Go: http://localhost:8080
echo JS: http://localhost:3010

echo.
echo Running API Test Tool...
cd server
node api-tester.js

echo.
echo Test completed! Check the generated report file.
pause