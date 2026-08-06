@echo off
setlocal
cd /d "%~dp0"

if not exist out mkdir out

echo Compiling Lide...
javac --release 21 -d out -sourcepath src src\lide\*.java
if errorlevel 1 (
  echo Build failed.
  exit /b 1
)

echo Build OK. Run with: run.bat
endlocal
