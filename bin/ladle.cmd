@echo off
setlocal

set "ROOT=%~dp0.."
for %%I in ("%ROOT%") do set "ROOT=%%~fI"

set "JAR=%ROOT%\lib\ladle.jar"
if not exist "%JAR%" (
  echo Cannot find ladle.jar at %JAR% >&2
  echo Run build.ps1 to build it, or commit lib/ladle.jar in your project. >&2
  exit /b 1
)

if defined JAVA_HOME (
  set "JAVA=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA=java"
)

"%JAVA%" -jar "%JAR%" %*
exit /b %ERRORLEVEL%
