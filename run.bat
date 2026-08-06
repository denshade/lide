@echo off
setlocal
cd /d "%~dp0"

if not exist out\lide\LideApp.class (
  call build.bat
  if errorlevel 1 exit /b 1
)

java -cp out lide.LideApp
endlocal
