<#
.SYNOPSIS
  Ensure Keiyoushi MangaDex pin is checked out, optionally build, copy APK + icon into repo/.
.OUTPUTS
  Prints package, versionName, versionCode, and APK path for callers.
#>
[CmdletBinding()]
param(
    [string]$RepoRoot = "",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

if (-not $RepoRoot) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}

$PinFile = (Join-Path (Join-Path $RepoRoot "vendor") "PIN.txt")
$VendorDir = (Join-Path (Join-Path $RepoRoot "vendor") "extensions-source")
$RepoDir = Join-Path $RepoRoot "repo"
$ApkDir = Join-Path $RepoDir "apk"
$IconDir = Join-Path $RepoDir "icon"

$Pkg = "eu.kanade.tachiyomi.extension.all.mangadex"
$VersionName = "1.4.211"
$VersionCode = 211
$ApkFileName = "tachiyomi-all.mangadex-v$VersionName.apk"
$BuiltApkName = "tachiyomi-all.mangadex-v$VersionName-release.apk"
$BuiltApkRel = (Join-Path "src\all\mangadex\build\outputs\apk\release" $BuiltApkName)
$IconSrcRel = "src\all\mangadex\res\mipmap-hdpi\ic_launcher.png"

$PinSha = "72979b95438718647d39b8f6133714a4b9b8e2e0"
$PinRepo = "https://github.com/keiyoushi/extensions-source.git"
$SparsePaths = @("src/all/mangadex", "lib/i18n", "core", "compiler", "gradle", "common")

function Read-PinSha {
    if (Test-Path $PinFile) {
        $line = Get-Content $PinFile | Where-Object { $_ -match '^sha=' } | Select-Object -First 1
        if ($line -match '^sha=(.+)$') { return $Matches[1].Trim() }
    }
    return $PinSha
}

function Ensure-CommonFiles {
    param([string]$Root, [string]$Sha)
    $commonDir = Join-Path $Root "common"
    $manifest = Join-Path $commonDir "AndroidManifest.xml"
    $proguard = Join-Path $commonDir "proguard-rules.pro"
    New-Item -ItemType Directory -Force -Path $commonDir | Out-Null
    $base = "https://raw.githubusercontent.com/keiyoushi/extensions-source/$Sha/common"
    if (-not (Test-Path $manifest)) {
        Write-Host "==> Fetching common/AndroidManifest.xml"
        Invoke-WebRequest -Uri "$base/AndroidManifest.xml" -OutFile $manifest -UseBasicParsing
    }
    if (-not (Test-Path $proguard)) {
        Write-Host "==> Fetching common/proguard-rules.pro"
        Invoke-WebRequest -Uri "$base/proguard-rules.pro" -OutFile $proguard -UseBasicParsing
    }
}

function Ensure-VendorCheckout {
    $sha = Read-PinSha
    $builtApk = Join-Path $VendorDir $BuiltApkRel
    $iconSrc = Join-Path $VendorDir $IconSrcRel

    if ((Test-Path $builtApk) -and (Test-Path $iconSrc) -and $SkipBuild) {
        Write-Host "==> Vendor tree present (SkipBuild); reusing existing APK"
        return
    }

    if (-not (Test-Path (Join-Path $VendorDir ".git"))) {
        Write-Host "==> Sparse clone keiyoushi/extensions-source @ $sha"
        New-Item -ItemType Directory -Force -Path (Split-Path $VendorDir) | Out-Null
        if (Test-Path $VendorDir) { Remove-Item -Recurse -Force $VendorDir }
        git clone --filter=blob:none --sparse --depth 1 $PinRepo $VendorDir
        if ($LASTEXITCODE -ne 0) { throw "git clone failed with exit $LASTEXITCODE" }
        Push-Location $VendorDir
        try {
            git sparse-checkout set --cone @SparsePaths
            if ($LASTEXITCODE -ne 0) { throw "sparse-checkout set failed with exit $LASTEXITCODE" }
            git fetch --depth 1 origin $sha 2>$null
            git checkout $sha 2>$null
            if ($LASTEXITCODE -ne 0) {
                Write-Warning "Could not checkout exact SHA $sha; staying on clone tip. Recorded pin may differ."
            }
        } finally {
            Pop-Location
        }
    } else {
        Write-Host "==> Vendor checkout exists at $VendorDir"
        Push-Location $VendorDir
        try {
            git sparse-checkout set --cone @SparsePaths 2>$null
        } finally {
            Pop-Location
        }
    }

    Ensure-CommonFiles -Root $VendorDir -Sha $sha
}

if (-not $env:JAVA_HOME -and (Test-Path "D:\Android\jbr")) {
    $env:JAVA_HOME = "D:\Android\jbr"
}
if (-not $env:ANDROID_HOME -and (Test-Path "D:\AndroidSDK")) {
    $env:ANDROID_HOME = "D:\AndroidSDK"
}

Ensure-VendorCheckout

$sdkDir = $env:ANDROID_HOME
if ($sdkDir) {
    $lp = Join-Path $VendorDir "local.properties"
    $sdkProp = ($sdkDir -replace '\\', '/')
    "sdk.dir=$sdkProp" | Set-Content -Encoding ASCII $lp
    Write-Host "==> Wrote $lp"
}

$builtApk = Join-Path $VendorDir $BuiltApkRel

if (-not $SkipBuild) {
    Write-Host "==> Building :src:all:mangadex:assembleRelease"
    Push-Location $VendorDir
    try {
        & .\gradlew.bat :src:all:mangadex:assembleRelease --no-daemon --no-configuration-cache
        if ($LASTEXITCODE -ne 0) { throw "MangaDex Gradle build failed with exit $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path $builtApk)) {
    throw "MangaDex APK not found: $builtApk (run without -SkipBuild to assemble)"
}

New-Item -ItemType Directory -Force -Path $ApkDir, $IconDir | Out-Null

$destApk = Join-Path $ApkDir $ApkFileName
Copy-Item -Force $builtApk $destApk
$apkSize = (Get-Item $destApk).Length
Write-Host "==> MangaDex APK -> $destApk ($apkSize bytes)"

$iconSrc = Join-Path $VendorDir $IconSrcRel
if (-not (Test-Path $iconSrc)) {
    throw "MangaDex icon not found: $iconSrc"
}
$iconDest = Join-Path $IconDir "$Pkg.png"
Copy-Item -Force $iconSrc $iconDest
Write-Host "==> MangaDex icon -> $iconDest"

Write-Host "PACKAGE=$Pkg"
Write-Host "VERSION=$VersionName"
Write-Host "CODE=$VersionCode"
Write-Host "APK=$ApkFileName"

[pscustomobject]@{
    Package     = $Pkg
    VersionName = $VersionName
    VersionCode = $VersionCode
    ApkName     = $ApkFileName
    ApkPath     = $destApk
    IconPath    = $iconDest
}
