@echo off
setlocal
cd /d "%~dp0"

echo =======================================================================
echo   Intelligent Airport Baggage Handling and Routing System
echo =======================================================================
echo.

where javac >nul 2>nul
if errorlevel 1 (
    echo [ERROR] javac was not found. Install a JDK and add its bin folder to PATH.
    pause
    exit /b 1
)

if not exist "backend\web" mkdir "backend\web"
xcopy "frontend\*" "backend\web\" /E /I /Y >nul
if errorlevel 1 (
    echo [ERROR] Could not stage frontend files for the backend.
    exit /b 1
)

cd /d "%~dp0backend"

echo Compiling Java source files...
if not exist "build\classes" mkdir "build\classes"
javac -encoding UTF-8 -d build\classes src\main\java\com\airport\baggage\model\*.java src\main\java\com\airport\baggage\service\*.java src\main\java\com\airport\baggage\server\*.java src\main\java\com\airport\baggage\test\*.java src\main\java\com\airport\baggage\Main.java
if errorlevel 1 (
    echo [ERROR] Java compilation failed. Check the compiler output above.
    pause
    exit /b 1
)

echo Compilation successful.
if /I "%~1"=="--compile-only" exit /b 0

echo Starting web server on http://localhost:8080 ...
echo Opening web browser...
start http://localhost:8080
echo.
java -cp build\classes com.airport.baggage.Main 8080
