@echo off
setlocal
cd /d "%~dp0"

call build.bat
if errorlevel 1 exit /b 1

echo Packaging Lide for Windows...
java -cp out lide.AppPackager .
if errorlevel 1 (
  echo Packaging failed.
  exit /b 1
)

echo Done. Run dist\Lide\Lide.exe
endlocal
