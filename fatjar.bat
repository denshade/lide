@echo off
setlocal
cd /d "%~dp0"

java -jar lib\ladle.jar release build.ini
if errorlevel 1 (
  echo Fat JAR failed.
  exit /b 1
)

echo Done. Run with: java -jar build\lide.jar
endlocal
