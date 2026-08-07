@echo off
setlocal
cd /d "%~dp0"

call build.bat
if errorlevel 1 exit /b 1

echo Generating icons...
java -cp out lide.IconGenerator .
if errorlevel 1 (
  echo Icon generation failed.
  exit /b 1
)

if not exist out\lide\icons mkdir out\lide\icons
copy /y src\lide\icons\*.png out\lide\icons\ >nul

echo Creating Lide.lnk launcher...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0create-launcher.ps1"
if errorlevel 1 (
  echo Launcher creation failed.
  exit /b 1
)

echo Done. Double-click Lide.lnk to start Lide.
endlocal
