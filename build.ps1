param(
    [switch]$NoClean
)

$ErrorActionPreference = 'Stop'
$Dir = $PSScriptRoot
$DestDir = Join-Path $Dir 'dist'
New-Item -ItemType Directory -Force -Path $DestDir | Out-Null
$env:NO_GS = 'true'

if (-not $env:ANDROID_SDK_ROOT) {
    $localProps = Join-Path $Dir 'local.properties'
    if (Test-Path $localProps) {
        $match = Select-String -Path $localProps -Pattern '^sdk\.dir=(.+)$'
        if ($match) { $env:ANDROID_SDK_ROOT = $match.Matches[0].Groups[1].Value }
    }
}

if (-not $env:ANDROID_SDK_ROOT) {
    throw 'ANDROID_SDK_ROOT environment variable is not set'
}

Write-Host "ANDROID_SDK_ROOT=$($env:ANDROID_SDK_ROOT)"

$cmakeBin = Get-ChildItem -Path (Join-Path $env:ANDROID_SDK_ROOT 'cmake\*\bin') -Directory -ErrorAction SilentlyContinue |
    Sort-Object { [version]($_.Parent.Name) } |
    Select-Object -Last 1
if ($cmakeBin) {
    Write-Host "CMAKE_PATH=$($cmakeBin.FullName)"
    $env:PATH = "$($cmakeBin.FullName);$env:PATH"
}

$gradleArgs = @('releaseDist', '-PAPP_ID_SFX=', '-Pfree=true')
if (-not $NoClean) { $gradleArgs = @('clean') + $gradleArgs }

Push-Location $Dir
try {
    & .\gradlew @gradleArgs
    Write-Host "`nBuilt release artifacts in $DestDir`:"
    Get-ChildItem -Path (Join-Path $DestDir '*') -Include '*.aab', '*-universal.apk' -File |
            Format-Table Name, @{N='SizeMB';E={[math]::Round($_.Length/1MB,2)}}
} finally {
    Pop-Location
}
