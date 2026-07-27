<#
.SYNOPSIS
  Build Aurora Stub APK and generate Mihon-compatible repo/ artefacts.
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

$ExtDir = Join-Path $RepoRoot "extensions\aurorastub"
$RepoDir = Join-Path $RepoRoot "repo"
$ApkDir = Join-Path $RepoDir "apk"
$IconDir = Join-Path $RepoDir "icon"

$Pkg = "eu.kanade.tachiyomi.extension.all.aurorastub"
$VersionName = "1.6.1"
$VersionCode = 1
$ApkName = "tachiyomi-all.aurorastub-v$VersionName.apk"
$SourceName = "Aurora Stub"
$SourceLang = "en"
$VersionId = 1
$BaseUrl = "https://example.invalid"

function Get-HttpSourceId {
    param([string]$Name, [string]$Lang, [int]$VerId)
    $key = "$($Name.ToLowerInvariant())/$Lang/$VerId"
    $md5 = [System.Security.Cryptography.MD5]::Create()
    try {
        $hash = $md5.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($key))
    } finally {
        $md5.Dispose()
    }
    [UInt64]$val = 0
    for ($i = 0; $i -lt 8; $i++) {
        $val = ($val -shl 8) -bor [UInt64]$hash[$i]
    }
    $val = $val -band [UInt64]0x7FFFFFFFFFFFFFFF
    return [string]$val
}

New-Item -ItemType Directory -Force -Path $ApkDir, $IconDir | Out-Null

if (-not $env:JAVA_HOME -and (Test-Path "D:\Android\jbr")) {
    $env:JAVA_HOME = "D:\Android\jbr"
}
if (-not $env:ANDROID_HOME -and (Test-Path "D:\AndroidSDK")) {
    $env:ANDROID_HOME = "D:\AndroidSDK"
}

$sdkDir = $env:ANDROID_HOME
if ($sdkDir) {
    $lp = Join-Path $ExtDir "local.properties"
    $sdkProp = ($sdkDir -replace '\\', '/')
    "sdk.dir=$sdkProp" | Set-Content -Encoding ASCII $lp
}

if (-not $SkipBuild) {
    Write-Host "==> Building :app:assembleDebug"
    Push-Location $ExtDir
    try {
        & .\gradlew.bat :app:assembleDebug --no-daemon
        if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
}

$builtApk = Join-Path $ExtDir "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $builtApk)) {
    throw "Built APK not found: $builtApk"
}

$destApk = Join-Path $ApkDir $ApkName
Copy-Item -Force $builtApk $destApk
$apkSize = (Get-Item $destApk).Length
Write-Host "==> APK -> $destApk ($apkSize bytes)"
if ($apkSize -gt 100KB) {
    Write-Warning "APK is larger than 100KB - verify kotlin-stdlib is not packaged."
}

$iconPath = Join-Path $IconDir "$Pkg.png"
if (-not (Test-Path $iconPath)) {
    throw "Missing icon: $iconPath (place a 128x128 or 512 PNG there)"
}

$fpScript = Join-Path $PSScriptRoot "get-signing-fingerprint.ps1"
$fingerprint = & $fpScript
Write-Host "==> signingKeyFingerprint: $fingerprint"

$sourceId = Get-HttpSourceId -Name $SourceName -Lang $SourceLang -VerId $VersionId
Write-Host "==> source id: $sourceId"

$RepoDirUnix = $RepoDir -replace '\\', '/'
$py = @"
import json, pathlib
repo = pathlib.Path(r'$RepoDirUnix')
entry = {
  "name": "Tachiyomi: Aurora Stub",
  "pkg": "$Pkg",
  "apk": "$ApkName",
  "lang": "all",
  "code": $VersionCode,
  "version": "$VersionName",
  "nsfw": 0,
  "sources": [{
    "id": $sourceId,
    "lang": "$SourceLang",
    "name": "$SourceName",
    "baseUrl": "$BaseUrl"
  }]
}
index = [entry]
(repo / "index.min.json").write_text(json.dumps(index, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
(repo / "index.json").write_text(json.dumps(index, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
repo_meta = {
  "meta": {
    "name": "Aurora Extensions",
    "shortName": "Aurora",
    "website": "https://github.com/aurora-reader/AuroraExtensions",
    "signingKeyFingerprint": "$fingerprint"
  }
}
(repo / "repo.json").write_text(json.dumps(repo_meta, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
print("wrote index.min.json, index.json, repo.json")
"@
$py | & python -
if ($LASTEXITCODE -ne 0) { throw "Python index generation failed" }

Write-Host "==> Done. Repo artefacts in $RepoDir"
Get-ChildItem $RepoDir -Recurse -File | ForEach-Object {
    "{0}`t{1}" -f $_.Length, $_.FullName.Substring($RepoDir.Length + 1)
}