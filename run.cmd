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

echo Compiling Java source files...
if not exist "bin" mkdir bin
javac -encoding UTF-8 -d bin src\com\airport\baggage\model\*.java src\com\airport\baggage\service\*.java src\com\airport\baggage\server\*.java src\com\airport\baggage\test\*.java src\com\airport\baggage\Main.java
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
java -cp bin com.airport.baggage.Main 8080
