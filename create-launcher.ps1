$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$ico = Join-Path $root "assets\lide.ico"
$shortcutPath = Join-Path $root "Lide.lnk"
$outDir = Join-Path $root "out"

if (-not (Test-Path $ico)) {
    throw "Missing icon: $ico (run create-launcher.bat to generate it)"
}
if (-not (Test-Path (Join-Path $outDir "lide\LideApp.class"))) {
    throw "Missing compiled classes under out\ (run build.bat first)"
}

$javaw = Get-Command javaw -ErrorAction SilentlyContinue
$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($shortcutPath)

if ($javaw) {
    $shortcut.TargetPath = $javaw.Source
    $shortcut.Arguments = "-cp `"$outDir`" lide.LideApp"
} else {
    $java = Get-Command java -ErrorAction Stop
    $shortcut.TargetPath = $java.Source
    $shortcut.Arguments = "-cp `"$outDir`" lide.LideApp"
}

$shortcut.WorkingDirectory = $root
$shortcut.WindowStyle = 1
$shortcut.Description = "Lide - lightweight Java IDE"
$shortcut.IconLocation = "$ico,0"
$shortcut.Save()

Write-Host "Created $shortcutPath"
Write-Host "Target: $($shortcut.TargetPath) $($shortcut.Arguments)"
