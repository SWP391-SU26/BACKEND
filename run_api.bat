@echo off
setlocal
cd /d "%~dp0"

if not exist ".venv\Scripts\python.exe" (
  echo Missing .venv\Scripts\python.exe
  exit /b 1
)

for /f "tokens=5" %%P in ('netstat -ano ^| findstr /R /C:":8001 .*LISTENING"') do (
  echo Port 8001 is already in use by PID %%P. Stop that AI Engine before starting another instance.
  exit /b 1
)

rem Runtime/model settings are loaded from .env by src/config.py.
".venv\Scripts\python.exe" -m uvicorn app.main:app --host 127.0.0.1 --port 8001 --workers 1
