param(
    [string]$Version = "",
    [switch]$SkipGradle
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

if ([string]::IsNullOrWhiteSpace($Version)) {
    $props = Get-Content (Join-Path $root "gradle.properties")
    $line = $props | Where-Object { $_ -match '^\s*revibeVersion\s*=' } | Select-Object -First 1
    if (-not $line) { throw "revibeVersion is missing from gradle.properties." }
    $Version = ($line -split "=", 2)[1].Trim()
}
if ($Version -notmatch '^\d+\.\d+\.\d+$') {
    throw "Installer version must be three numeric parts. Got '$Version'."
}

$isccCandidates = @(
    (Join-Path $env:LOCALAPPDATA "Programs\Inno Setup 6\ISCC.exe"),
    (Join-Path ${env:ProgramFiles(x86)} "Inno Setup 6\ISCC.exe"),
    (Join-Path $env:ProgramFiles "Inno Setup 6\ISCC.exe")
)
$iscc = $isccCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $iscc) {
    throw "Inno Setup 6 compiler not found. Install it per-user with: winget install --id JRSoftware.InnoSetup -e --scope user"
}

if (-not $SkipGradle) {
    Push-Location $root
    try {
        & (Join-Path $root "gradlew.bat") :desktopApp:createDistributable `
            "-PrevibeVersion=$Version" --console=plain
        if ($LASTEXITCODE -ne 0) { throw "Gradle app-image build failed." }
    } finally {
        Pop-Location
    }
}

$installerDir = Join-Path $root "desktopApp\installer"
Push-Location $installerDir
try {
    & $iscc "/DAppVersion=$Version" "Revibe.iss"
    if ($LASTEXITCODE -ne 0) { throw "Inno Setup compilation failed." }
} finally {
    Pop-Location
}

$output = Join-Path $root "desktopApp\build\installer\Revibe-$Version-Setup.exe"
if (-not (Test-Path -LiteralPath $output)) { throw "Expected installer was not produced: $output" }
$stream = [IO.File]::OpenRead($output)
try {
    $sha = [Security.Cryptography.SHA256]::Create()
    $bytes = $sha.ComputeHash($stream)
    $hash = ([BitConverter]::ToString($bytes)).Replace('-', '').ToLowerInvariant()
} finally {
    $stream.Dispose()
}
Write-Host "Installer: $output"
Write-Host "SHA-256:   $hash"
