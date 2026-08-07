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

if not exist out\lide\icons mkdir out\lide\icons
if exist src\lide\icons\*.png (
  copy /y src\lide\icons\*.png out\lide\icons\ >nul
)

echo Build OK. Run with: run.bat  or  Lide.lnk
endlocal
