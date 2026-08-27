param(
    [switch]$SkipAppImage,
    [string]$InnoDir = ""
)

$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$scriptDir = $PSScriptRoot
$target = Join-Path $projectRoot "target"
$appImage = Join-Path $target "windows-package\FirmaUNA"
$outName = "FirmaUNA-2.0.0-setup.exe"
$outputExe = Join-Path $target $outName

if (-not $SkipAppImage) {
    $packageScript = Join-Path $scriptDir "package.ps1"
    $wixBin = Join-Path $env:TEMP "opencode\wix\bin"
    if (-not (Test-Path -LiteralPath (Join-Path $wixBin "candle.exe"))) {
        throw "Portable WiX not found at $wixBin. Provide WiX on PATH or adjust the WiXPath."
    }
    & powershell -NoProfile -ExecutionPolicy Bypass -File $packageScript -Type app-image -WiXPath $wixBin
    if ($LASTEXITCODE -ne 0) {
        throw "App-image generation failed."
    }
}

if (-not (Test-Path -LiteralPath (Join-Path $appImage "FirmaUNA.exe"))) {
    throw "App image not found at $appImage. Run without -SkipAppImage first."
}

if ($InnoDir) {
    $iscc = Join-Path $InnoDir "ISCC.exe"
} else {
    $iscc = Join-Path $env:LOCALAPPDATA "Programs\Inno Setup 6\ISCC.exe"
}
if (-not (Test-Path -LiteralPath $iscc)) {
    throw "ISCC.exe not found at $iscc. Install Inno Setup 6 or pass -InnoDir."
}

$iss = Join-Path $scriptDir "firmauna.iss"
$arguments = @(
    $iss,
    "/DVERSION=2.0.0",
    "/DAPP_IMG=$appImage",
    "/DOUTPUT=$target"
)

& $iscc @arguments
if ($LASTEXITCODE -ne 0) {
    throw "Inno Setup compilation failed."
}

if (-not (Test-Path -LiteralPath $outputExe)) {
    throw "Expected installer not found: $outputExe"
}

$hash = (Get-FileHash -LiteralPath $outputExe -Algorithm SHA256).Hash
"Installer created: $outputExe"
"SHA-256: $hash"
