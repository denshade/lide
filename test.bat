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

echo Compiling tests...
javac --release 21 -d out -cp out -sourcepath test test\lide\*.java
if errorlevel 1 (
  echo Test compile failed.
  exit /b 1
)

echo Running tests...
java -cp out lide.ContextMenuTest
if errorlevel 1 (
  echo Tests failed.
  exit /b 1
)

java -cp out lide.ClassNavigatorTest
if errorlevel 1 (
  echo Tests failed.
  exit /b 1
)

echo All tests passed.
endlocal
