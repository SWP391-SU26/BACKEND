@echo off
setlocal

set "BACKEND_DIR=%~dp0"
cd /d "%BACKEND_DIR%"

if "%~1"=="" (
  echo Usage:
  echo   train_active.bat path\to\ground_truth.csv --run-name subject-code
  echo.
  echo Examples:
  echo   train_active.bat ..\ground_truth_triethocmaclenin_100.csv --run-name triet301
  echo   train_active.bat data\math_ground_truth.csv --run-name math101 --dry-run
  exit /b 1
)

if not exist ".venv\Scripts\python.exe" (
  echo Missing .venv\Scripts\python.exe
  echo Create the virtual environment and install dependencies first.
  exit /b 1
)

call ".venv\Scripts\activate.bat"
python experiments\train_active.py %*
