param(
    [ValidateSet("app-image", "exe", "msi")]
    [string]$Type = "app-image",
    [switch]$Console,
    [string]$WiXPath = ""
)

$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$target = Join-Path $projectRoot "target"
$input = Join-Path $target "windows-input"
$destination = Join-Path $target "windows-package"
$mainJarName = "firmauna-desktop-2.0.0.jar"
$mainJar = Join-Path $target $mainJarName
$icon = Join-Path $projectRoot "packaging\macos\IconGroup32512.ico"

if (-not $env:JAVA_HOME) {
    throw "JAVA_HOME must point to a JDK 21 installation."
}

$javaVersion = & (Join-Path $env:JAVA_HOME "bin\javac.exe") -version
if ($javaVersion -notmatch '^javac 21\.') {
    throw "FirmaUNA Windows packaging requires JDK 21."
}

if ($WiXPath) {
    if (-not (Test-Path -LiteralPath (Join-Path $WiXPath "candle.exe"))) {
        throw "WiXPath does not contain candle.exe: $WiXPath"
    }
    $env:PATH = $WiXPath + ";" + $env:PATH
}

$maven = (Get-Command mvn.cmd -ErrorAction Stop).Source
$jpackage = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
if (-not (Test-Path -LiteralPath $jpackage)) {
    throw "jpackage was not found in JAVA_HOME."
}

Push-Location $projectRoot
try {
    & $maven clean package dependency:copy-dependencies `
        "-DincludeGroupIds=org.openjfx" `
        "-DoutputDirectory=$input"
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed."
    }

    Copy-Item -LiteralPath $mainJar -Destination $input
    if (Test-Path -LiteralPath $destination) {
        Remove-Item -LiteralPath $destination -Recurse -Force
    }

    $arguments = @(
        "--type", $Type,
        "--name", "FirmaUNA",
        "--app-version", "2.0.0",
        "--vendor", "Universidad Nacional del Altiplano",
        "--description", "Firma digital de documentos PDF",
        "--input", $input,
        "--dest", $destination,
        "--main-jar", $mainJarName,
        "--main-class", "unap.oti.firmauna.Launcher",
        "--icon", $icon,
        "--add-modules", "java.base,java.desktop,java.logging,java.naming,jdk.crypto.cryptoki,jdk.crypto.ec,jdk.crypto.mscapi,jdk.unsupported",
        "--java-options", "--add-exports=jdk.crypto.cryptoki/sun.security.pkcs11=ALL-UNNAMED"
    )

    if ($Type -ne "app-image") {
        $arguments += @("--win-menu", "--win-shortcut")
    }
    if ($Console) {
        $arguments += "--win-console"
    }

    & $jpackage @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage failed."
    }

    "Windows package created at $destination"
} finally {
    Pop-Location
}
