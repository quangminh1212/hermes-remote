@echo off
REM Hermes Android Bridge - Quick Start Script (Windows)

echo =========================================
echo 🚀 Hermes Android Bridge Setup
echo =========================================
echo.

REM Step 1: Check Python installation
echo Step 1/4: Checking Python installation...
python --version >nul 2>&1
if %errorlevel% equ 0 (
    python --version
    echo [SUCCESS] Python found
) else (
    echo [ERROR] Python not found. Please install Python 3.8 or higher.
    pause
    exit /b 1
)
echo.

REM Step 2: Install dependencies
echo Step 2/4: Installing Python dependencies...
cd py.relay

REM Create virtual environment if it doesn't exist
if not exist "venv" (
    echo Creating virtual environment...
    python -m venv venv
)

REM Activate venv
call venv\Scripts\activate.bat

REM Install requirements
echo Installing packages from requirements.txt...
pip install -q -r requirements.txt
echo [SUCCESS] Dependencies installed
echo.

REM Step 3: Verify script works
echo Step 3/4: Testing Python script syntax...
python -m py_compile relay_server.py
if %errorlevel% equ 0 (
    echo [SUCCESS] Syntax check passed
) else (
    echo [ERROR] Python script has syntax errors
    pause
    exit /b 1
)
echo.

REM Step 4: Start server
echo Step 4/4: Starting relay server...
echo.
echo =========================================
echo Server starting...
echo WebSocket endpoint: ws://localhost:8765/ws
echo Health check: http://localhost:8765/
echo Press Ctrl+C to stop server
echo =========================================
echo.

REM Run server
python relay_server.py --host 0.0.0.0 --port 8765

pause
