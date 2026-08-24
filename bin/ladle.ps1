$ErrorActionPreference = "Stop"

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$JarPath = Join-Path $Root "lib\ladle.jar"
if (-not (Test-Path -LiteralPath $JarPath)) {
    Write-Error "Cannot find ladle.jar at $JarPath. Run build.ps1 to build it, or commit lib/ladle.jar in your project."
    exit 1
}

if ($env:JAVA_HOME) {
    $Java = Join-Path $env:JAVA_HOME "bin\java.exe"
} else {
    $Java = "java"
}

& $Java -jar $JarPath @args
exit $LASTEXITCODE
