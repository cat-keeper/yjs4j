@echo off
echo Running Yjs4j Unit Tests...
echo.

REM Compile the project
echo Compiling project...
mvn clean compile test-compile

if %ERRORLEVEL% neq 0 (
    echo Compilation failed!
    pause
    exit /b 1
)

echo.
echo Running all tests...
mvn test

if %ERRORLEVEL% neq 0 (
    echo Tests failed!
    pause
    exit /b 1
)

echo.
echo All tests passed successfully!
pause