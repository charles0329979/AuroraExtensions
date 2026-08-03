<#
.SYNOPSIS
  Build Aurora Stub + Scripted + MangaDex APKs and generate Mihon-compatible repo/ artefacts.
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

$ExtDir = (Join-Path (Join-Path $RepoRoot "extensions") "aurorastub")
$RepoDir = Join-Path $RepoRoot "repo"
$ApkDir = Join-Path $RepoDir "apk"
$IconDir = Join-Path $RepoDir "icon"

# --- Stub ---
$StubPkg = "eu.kanade.tachiyomi.extension.all.aurorastub"
$StubVersionName = "1.6.1"
$StubVersionCode = 1
$StubApkName = "tachiyomi-all.aurorastub-v$StubVersionName.apk"
$StubSourceName = "Aurora Stub"
$StubSourceLang = "en"
$StubVersionId = 1
$StubBaseUrl = "https://example.invalid"

# --- Scripted (S1) ---
$ScriptedExtDir = (Join-Path (Join-Path $RepoRoot "extensions") "aurorascripted")
$ScriptedPkg = "eu.kanade.tachiyomi.extension.all.aurorascripted"
$ScriptedVersionName = "1.6.1"
$ScriptedVersionCode = 1
$ScriptedApkName = "tachiyomi-all.aurorascripted-v$ScriptedVersionName.apk"
$ScriptedSourceName = "Aurora Scripted"
$ScriptedSourceLang = "en"
$ScriptedVersionId = 1
$ScriptedBaseUrl = "https://aurora.scripted.invalid"

# --- MangaDex (pin 1.4.211) ---
$MdPkg = "eu.kanade.tachiyomi.extension.all.mangadex"
$MdVersionName = "1.4.211"
$MdVersionCode = 211
$MdApkName = "tachiyomi-all.mangadex-v$MdVersionName.apk"
$MdSourceName = "MangaDex"
$MdSourceLang = "en"
$MdVersionId = 1
$MdBaseUrl = "https://mangadex.org"

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

# --- Build / copy Stub ---
$builtStubApk = Join-Path $ExtDir "app\build\outputs\apk\debug\app-debug.apk"
if (-not $SkipBuild) {
    Write-Host "==> Building Stub :app:assembleDebug"
    Push-Location $ExtDir
    try {
        if ($IsWindows -or $env:OS -match "Windows") {
            & .\gradlew.bat :app:assembleDebug --no-daemon
        } else {
            & chmod +x ./gradlew
            & ./gradlew :app:assembleDebug --no-daemon
        }
        if ($LASTEXITCODE -ne 0) { throw "Stub Gradle build failed with exit $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
} elseif (-not (Test-Path $builtStubApk)) {
    Write-Host "==> Stub APK missing under SkipBuild; assembling Stub"
    Push-Location $ExtDir
    try {
        & .\gradlew.bat :app:assembleDebug --no-daemon
        if ($LASTEXITCODE -ne 0) { throw "Stub Gradle build failed with exit $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path $builtStubApk)) {
    throw "Built Stub APK not found: $builtStubApk"
}

$destStubApk = Join-Path $ApkDir $StubApkName
Copy-Item -Force $builtStubApk $destStubApk
$stubSize = (Get-Item $destStubApk).Length
Write-Host "==> Stub APK -> $destStubApk ($stubSize bytes)"
if ($stubSize -gt 100KB) {
    Write-Warning "Stub APK is larger than 100KB - verify kotlin-stdlib is not packaged."
}

$stubIconPath = Join-Path $IconDir "$StubPkg.png"
if (-not (Test-Path $stubIconPath)) {
    throw "Missing Stub icon: $stubIconPath (place a 128x128 or 512 PNG there)"
}

# --- Build / copy Scripted ---
$builtScriptedApk = Join-Path $ScriptedExtDir "app\build\outputs\apk\debug\app-debug.apk"
if (-not $SkipBuild) {
    Write-Host "==> Building Scripted :app:assembleDebug"
    if (-not $env:JAVA_HOME -and (Test-Path "D:\Android\jbr")) {
        $env:JAVA_HOME = "D:\Android\jbr"
    }
    if (-not $env:ANDROID_HOME -and (Test-Path "D:\AndroidSDK")) {
        $env:ANDROID_HOME = "D:\AndroidSDK"
    }
    if ($env:ANDROID_HOME) {
        $lp = Join-Path $ScriptedExtDir "local.properties"
        $sdkProp = ($env:ANDROID_HOME -replace '\\', '/')
        "sdk.dir=$sdkProp" | Set-Content -Encoding ASCII $lp
    }
    Push-Location $ScriptedExtDir
    try {
        if ($IsWindows -or $env:OS -match "Windows") {
            & .\gradlew.bat :app:assembleDebug --no-daemon
        } else {
            & ./gradlew :app:assembleDebug --no-daemon
        }
        if ($LASTEXITCODE -ne 0) { throw "Scripted Gradle build failed with exit $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
} elseif (-not (Test-Path $builtScriptedApk)) {
    throw "Scripted APK missing under SkipBuild: $builtScriptedApk"
}

if (-not (Test-Path $builtScriptedApk)) {
    throw "Built Scripted APK not found: $builtScriptedApk"
}

$destScriptedApk = Join-Path $ApkDir $ScriptedApkName
Copy-Item -Force $builtScriptedApk $destScriptedApk
$scriptedSize = (Get-Item $destScriptedApk).Length
Write-Host "==> Scripted APK -> $destScriptedApk ($scriptedSize bytes)"

$scriptedIconPath = Join-Path $IconDir "$ScriptedPkg.png"
if (-not (Test-Path $scriptedIconPath)) {
    throw "Missing Scripted icon: $scriptedIconPath"
}

# --- Vendor MangaDex ---
$vendorScript = Join-Path $PSScriptRoot "vendor-mangadex.ps1"
Write-Host "==> Running vendor-mangadex.ps1 (SkipBuild=$SkipBuild)"
$vendorArgs = @{ RepoRoot = $RepoRoot }
if ($SkipBuild) { $vendorArgs.SkipBuild = $true }
$null = & $vendorScript @vendorArgs
if (-not $?) { throw "vendor-mangadex.ps1 failed" }

$destMdApk = Join-Path $ApkDir $MdApkName
if (-not (Test-Path $destMdApk)) {
    throw "MangaDex APK missing after vendor script: $destMdApk"
}
$mdIconPath = Join-Path $IconDir "$MdPkg.png"
if (-not (Test-Path $mdIconPath)) {
    throw "MangaDex icon missing after vendor script: $mdIconPath"
}

$fpScript = Join-Path $PSScriptRoot "get-signing-fingerprint.ps1"
$fingerprint = & $fpScript
Write-Host "==> signingKeyFingerprint: $fingerprint"

$stubSourceId = Get-HttpSourceId -Name $StubSourceName -Lang $StubSourceLang -VerId $StubVersionId
$scriptedSourceId = Get-HttpSourceId -Name $ScriptedSourceName -Lang $ScriptedSourceLang -VerId $ScriptedVersionId
$mdSourceId = Get-HttpSourceId -Name $MdSourceName -Lang $MdSourceLang -VerId $MdVersionId
Write-Host "==> Stub source id: $stubSourceId"
Write-Host "==> Scripted source id: $scriptedSourceId"
Write-Host "==> MangaDex source id: $mdSourceId"

$RepoDirUnix = $RepoDir -replace '\\', '/'
$py = @"
import json, pathlib
repo = pathlib.Path(r'$RepoDirUnix')
stub = {
  "name": "Tachiyomi: Aurora Stub",
  "pkg": "$StubPkg",
  "apk": "$StubApkName",
  "lang": "all",
  "code": $StubVersionCode,
  "version": "$StubVersionName",
  "nsfw": 0,
  "sources": [{
    "id": $stubSourceId,
    "lang": "$StubSourceLang",
    "name": "$StubSourceName",
    "baseUrl": "$StubBaseUrl"
  }]
}
scripted = {
  "name": "Tachiyomi: Aurora Scripted",
  "pkg": "$ScriptedPkg",
  "apk": "$ScriptedApkName",
  "lang": "all",
  "code": $ScriptedVersionCode,
  "version": "$ScriptedVersionName",
  "nsfw": 0,
  "sources": [{
    "id": $scriptedSourceId,
    "lang": "$ScriptedSourceLang",
    "name": "$ScriptedSourceName",
    "baseUrl": "$ScriptedBaseUrl"
  }]
}
mangadex = {
  "name": "Tachiyomi: MangaDex",
  "pkg": "$MdPkg",
  "apk": "$MdApkName",
  "lang": "all",
  "code": $MdVersionCode,
  "version": "$MdVersionName",
  "nsfw": 0,
  "sources": [{
    "id": $mdSourceId,
    "lang": "$MdSourceLang",
    "name": "$MdSourceName",
    "baseUrl": "$MdBaseUrl"
  }]
}
index = [stub, scripted, mangadex]
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
print("wrote index.min.json, index.json, repo.json (3 entries)")
"@
function Resolve-Python {
    foreach ($name in @("python", "python3")) {
        $cmd = Get-Command $name -ErrorAction SilentlyContinue
        if (-not $cmd) { continue }
        # Skip WindowsApps Store stubs (exit 9009)
        if ($cmd.Source -match 'WindowsApps') { continue }
        return $cmd
    }
    throw "python/python3 not found (or only WindowsApps stub present)"
}
$python = Resolve-Python
$tmpPy = Join-Path $env:TEMP "aurora-generate-index.py"
[System.IO.File]::WriteAllText($tmpPy, $py, (New-Object System.Text.UTF8Encoding $false))
& $python.Source $tmpPy
$pyExit = $LASTEXITCODE
Remove-Item -Force $tmpPy -ErrorAction SilentlyContinue
if ($pyExit -ne 0) { throw "Python index generation failed (exit $pyExit)" }

Write-Host "==> Done. Repo artefacts in $RepoDir"
Get-ChildItem $RepoDir -Recurse -File | ForEach-Object {
    "{0}`t{1}" -f $_.Length, $_.FullName.Substring($RepoDir.Length + 1)
}
