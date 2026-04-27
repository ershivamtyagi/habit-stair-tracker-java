param(
    [ValidateSet("app-image", "msi", "exe")]
    [string]$Type = "app-image"
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$targetDir = Join-Path $projectRoot "target"
$releaseDir = Join-Path $targetDir "release"
$installerDir = Join-Path $projectRoot "dist"
$bundledWixDir = Join-Path $projectRoot "tools\wix314"
$version = "1.0.0"
$mainJar = "stair-progress-app-$version.jar"

function Test-WixInstalled {
    $light = Get-Command light.exe -ErrorAction SilentlyContinue
    $candle = Get-Command candle.exe -ErrorAction SilentlyContinue
    return $null -ne $light -and $null -ne $candle
}

if (Test-Path $bundledWixDir) {
    $env:PATH = "$bundledWixDir;$env:PATH"
}

if ($Type -in @("msi", "exe") -and -not (Test-WixInstalled)) {
    throw "WiX Toolset is required to build '$Type' installers with jpackage. Install WiX and ensure light.exe and candle.exe are on PATH, or build with -Type app-image for a portable desktop bundle."
}

if (Test-Path $releaseDir) {
    Remove-Item -LiteralPath $releaseDir -Recurse -Force
}

if (Test-Path $installerDir) {
    Remove-Item -LiteralPath $installerDir -Recurse -Force
}

New-Item -ItemType Directory -Path $releaseDir | Out-Null
New-Item -ItemType Directory -Path $installerDir | Out-Null

Push-Location $projectRoot
try {
    mvn -q clean package dependency:copy-dependencies "-DincludeScope=runtime" "-DoutputDirectory=$releaseDir"

    Copy-Item -LiteralPath (Join-Path $targetDir $mainJar) -Destination $releaseDir

    $jpackageArgs = @(
        "--type", $Type,
        "--input", $releaseDir,
        "--dest", $installerDir,
        "--name", "Stair Progress App",
        "--app-version", $version,
        "--vendor", "Stair Progress",
        "--main-jar", $mainJar,
        "--main-class", "com.stairprogress.StairProgressApp",
        "--java-options", "-Dfile.encoding=UTF-8"
    )

    if ($Type -in @("msi", "exe")) {
        $jpackageArgs += @("--win-dir-chooser", "--win-menu", "--win-shortcut")
    }

    & jpackage @jpackageArgs
}
finally {
    Pop-Location
}
