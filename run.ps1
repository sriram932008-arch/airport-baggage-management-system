param(
    [switch]$CompileOnly
)

# PowerShell Startup Script for Intelligent Airport Baggage Handling and Routing System
$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path (Join-Path $PSScriptRoot "backend\web") | Out-Null
Copy-Item -Path (Join-Path $PSScriptRoot "frontend\*") -Destination (Join-Path $PSScriptRoot "backend\web") -Recurse -Force
Push-Location (Join-Path $PSScriptRoot "backend")
try {

Write-Host "=======================================================================" -ForegroundColor Cyan
Write-Host "  Intelligent Airport Baggage Handling and Routing System" -ForegroundColor Cyan
Write-Host "=======================================================================" -ForegroundColor Cyan
Write-Host ""

if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
    Write-Host "[ERROR] javac was not found. Install a JDK and add its bin folder to PATH." -ForegroundColor Red
    exit 1
}

if (-not (Test-Path "build\classes")) {
    New-Item -ItemType Directory -Force -Path "build\classes" | Out-Null
}

Write-Host "Compiling Java source files..." -ForegroundColor Yellow
$javaSources = Get-ChildItem -Path "src\main\java" -Filter "*.java" -Recurse | ForEach-Object { $_.FullName }
javac -encoding UTF-8 -d build\classes $javaSources

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Compilation failed." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "Compilation successful!" -ForegroundColor Green
if ($CompileOnly) {
    exit 0
}

Write-Host "Starting server on http://localhost:8080 ..." -ForegroundColor Cyan
Start-Process "http://localhost:8080"
java -cp build\classes com.airport.baggage.Main 8080
}
finally {
    Pop-Location
}
