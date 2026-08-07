@echo off
setlocal
cd /d "%~dp0"

if not exist out\lide\LideApp.class (
  call build.bat
  if errorlevel 1 exit /b 1
)

if not exist out\lide\icons mkdir out\lide\icons
if exist src\lide\icons\*.png (
  copy /y src\lide\icons\*.png out\lide\icons\ >nul
)

where javaw >nul 2>&1
if errorlevel 1 (
  java -cp out lide.LideApp
) else (
  start "" javaw -cp out lide.LideApp
)
endlocal
