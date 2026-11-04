<#
.SYNOPSIS
  Builds (optionally) and installs TV Art on the Fire TV, pushes art, and grants the one-time permissions.

.EXAMPLE
  .\deploy.ps1 -TvIp <TV_IP>
  .\deploy.ps1 -TvIp <TV_IP> -ImageFolder .\Walls -Build
  .\deploy.ps1 -TvIp <TV_IP> -SkipImages
#>
param(
    [Parameter(Mandatory = $true)][string]$TvIp,
    [string]$ImageFolder = "",
    [switch]$Build,
    [switch]$SkipImages
)
# "Continue": adb writes normal progress (and "unauthorized" while waiting) to stderr, which
# Windows PowerShell 5 would otherwise turn into terminating errors. Failures are checked explicitly.
$ErrorActionPreference = "Continue"
$Root = $PSScriptRoot
$Pkg = "com.tvart.slideshow"
$Apk = Join-Path $Root "app\build\outputs\apk\debug\app-debug.apk"
$Dest = "/sdcard/Pictures/TVArt"

# ---- find adb ----
$Adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $Adb) { $Adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe" }
if (-not (Test-Path $Adb)) { throw "adb not found. Install Android SDK platform-tools." }
$Target = if ($TvIp -like "*:*") { $TvIp } else { "${TvIp}:5555" }
function Adb { & $Adb -s $Target @args }

# ---- build ----
if ($Build -or -not (Test-Path $Apk)) {
    Write-Host "==> Building APK..." -ForegroundColor Cyan
    if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr" }
    Push-Location $Root
    try { & .\gradlew.bat assembleDebug -q; if ($LASTEXITCODE -ne 0) { throw "Build failed" } } finally { Pop-Location }
}

# Start adb's background server detached, so it can't hold this script's output pipe open
# (otherwise a caller that captures output waits until the server exits).
Start-Process -FilePath $Adb -ArgumentList "start-server" -NoNewWindow -Wait

# ---- connect ----
Write-Host "==> Connecting to $Target ..." -ForegroundColor Cyan
& $Adb connect $Target | Out-Host
Write-Host "    If the TV shows 'Allow USB debugging?', choose 'Always allow from this computer' and OK." -ForegroundColor Yellow
$ok = $false
for ($i = 0; $i -lt 60; $i++) {
    $state = (& $Adb -s $Target get-state 2>$null | Out-String).Trim()
    if ($state -eq "device") { $ok = $true; break }
    if ($i % 5 -eq 0) { & $Adb connect $Target 2>$null | Out-Null }
    Start-Sleep -Seconds 2
}
if (-not $ok) { throw "TV did not authorize ADB. Check the IP, that ADB Debugging is on, and accept the prompt on the TV." }
Write-Host "    Connected: $(Adb shell getprop ro.product.model) / Fire OS $(Adb shell getprop ro.build.version.name)"

# ---- install ----
Write-Host "==> Installing $Apk ..." -ForegroundColor Cyan
Adb install -r $Apk 2>&1 | Out-Host
if ($LASTEXITCODE -ne 0) { throw "Install failed" }

# ---- art ----
Adb shell mkdir -p $Dest
if (-not $SkipImages) {
    if (-not $ImageFolder) { $def = Join-Path $Root "Walls"; if (Test-Path $def) { $ImageFolder = $def } }
    if ($ImageFolder) {
        $ImageFolder = (Resolve-Path $ImageFolder).Path
        Write-Host "==> Pushing art from $ImageFolder (subfolders like Christmas\ go into matching slideshows)..." -ForegroundColor Cyan
        Adb push "$ImageFolder\." "$Dest/" | Out-Host
        Adb shell "find $Dest -iname Thumbs.db -delete; find $Dest -iname desktop.ini -delete" | Out-Null
    }
}

# ---- permissions ----
Write-Host "==> Granting permissions..." -ForegroundColor Cyan
Adb shell pm grant $Pkg android.permission.READ_EXTERNAL_STORAGE
Adb shell pm grant $Pkg android.permission.WRITE_EXTERNAL_STORAGE
Adb shell pm grant $Pkg android.permission.WRITE_SECURE_SETTINGS

# ---- safety copy of the original screensaver + accessibility values ----
$backup = Join-Path $Root "screensaver_backup.txt"
$ss = (Adb shell settings get secure screensaver_components) -join ""
$sse = (Adb shell settings get secure screensaver_enabled) -join ""
$a11y = (Adb shell settings get secure enabled_accessibility_services) -join ""
if (-not (Test-Path $backup) -or (Get-Content $backup -Raw) -notmatch "screensaver_components=") {
    @(
        "# Saved $(Get-Date -Format s) before TV Art changed anything"
        "screensaver_components=$ss"
        "screensaver_enabled=$sse"
        "enabled_accessibility_services=$a11y"
        "# Restore manually with:"
        "#   adb shell settings put secure screensaver_components `"$ss`""
    ) | Set-Content $backup
    Write-Host "    Saved original values to $backup"
} else {
    Write-Host "    $backup already exists; kept the first (original) copy. Current screensaver: $ss"
}

# ---- font check ----
$ember = (Adb shell "ls /system/fonts | grep -i ember") -join ", "
Write-Host "    Amazon Ember fonts on TV: $(if ($ember) { $ember } else { 'none (clock uses sans-serif)' })"

# ---- launch ----
Adb shell am start -n "$Pkg/.ui.MainActivity" | Out-Null
Write-Host ""
Write-Host "Done. TV Art is open on the TV." -ForegroundColor Green
Write-Host "On the TV: TV Art > Screensaver & YouTube > turn on 'Art screensaver' and 'Art over YouTube'."
