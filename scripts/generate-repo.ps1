<#
.SYNOPSIS
  Build Aurora Stub + Scripted + MangaDex APKs and generate Mihon-compatible repo/ artefacts.
#>
[CmdletBinding()]
param(
    [string]$RepoRoot = "",
    [switch]$SkipBuild,
    [switch]$AllowSigningKeyRotation,
    [ValidateSet("Debug", "Release")]
    [string]$BuildType = $(if ($env:AURORA_EXTENSION_BUILD_TYPE) { $env:AURORA_EXTENSION_BUILD_TYPE } else { "Debug" }),
    [string]$SigningKeystore = $env:AURORA_EXTENSION_SIGNING_KEYSTORE,
    [string]$SigningAlias = $env:AURORA_EXTENSION_KEY_ALIAS,
    [string]$ExpectedSigningFingerprint = $env:AURORA_EXTENSION_SIGNING_FINGERPRINT
)

$ErrorActionPreference = "Stop"

if (-not $RepoRoot) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}

$ExtDir = (Join-Path (Join-Path $RepoRoot "extensions") "aurorastub")
$RepoDir = Join-Path $RepoRoot "repo"
$ApkDir = Join-Path $RepoDir "apk"
$IconDir = Join-Path $RepoDir "icon"
$variant = $BuildType.ToLowerInvariant()
$assembleTask = ":app:assemble$BuildType"

function Find-BuiltAppApk {
    param([string]$ProjectDir)
    $outputDir = Join-Path $ProjectDir "app\build\outputs\apk\$variant"
    $candidates = @(
        (Join-Path $outputDir "app-$variant.apk"),
        (Join-Path $outputDir "app-$variant-unsigned.apk")
    )
    $found = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $found) { throw "Built $BuildType APK not found under $outputDir" }
    return $found
}

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
if (-not $SkipBuild) {
    Write-Host "==> Building Stub $assembleTask"
    Push-Location $ExtDir
    try {
        if ($IsWindows -or $env:OS -match "Windows") {
            & .\gradlew.bat $assembleTask --no-daemon
        } else {
            & chmod +x ./gradlew
            & ./gradlew $assembleTask --no-daemon
        }
        if ($LASTEXITCODE -ne 0) { throw "Stub Gradle build failed with exit $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
} elseif (-not (Test-Path (Join-Path $ExtDir "app\build\outputs\apk\$variant"))) {
    Write-Host "==> Stub APK missing under SkipBuild; assembling Stub"
    Push-Location $ExtDir
    try {
        & .\gradlew.bat $assembleTask --no-daemon
        if ($LASTEXITCODE -ne 0) { throw "Stub Gradle build failed with exit $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
}

$builtStubApk = Find-BuiltAppApk $ExtDir

$destStubApk = Join-Path $ApkDir $StubApkName
Copy-Item -Force $builtStubApk $destStubApk
$stubSize = (Get-Item $destStubApk).Length
$stubSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $destStubApk).Hash.ToLowerInvariant()
Write-Host "==> Stub APK -> $destStubApk ($stubSize bytes)"
if ($stubSize -gt 100KB) {
    Write-Warning "Stub APK is larger than 100KB - verify kotlin-stdlib is not packaged."
}

$stubIconPath = Join-Path $IconDir "$StubPkg.png"
if (-not (Test-Path $stubIconPath)) {
    throw "Missing Stub icon: $stubIconPath (place a 128x128 or 512 PNG there)"
}

# --- Build / copy Scripted ---
if (-not $SkipBuild) {
    Write-Host "==> Building Scripted $assembleTask"
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
            & .\gradlew.bat $assembleTask --no-daemon
        } else {
            & chmod +x ./gradlew
            & ./gradlew $assembleTask --no-daemon
        }
        if ($LASTEXITCODE -ne 0) { throw "Scripted Gradle build failed with exit $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
} elseif (-not (Test-Path (Join-Path $ScriptedExtDir "app\build\outputs\apk\$variant"))) {
    throw "Scripted $BuildType APK output missing under SkipBuild"
}

$builtScriptedApk = Find-BuiltAppApk $ScriptedExtDir

$destScriptedApk = Join-Path $ApkDir $ScriptedApkName
Copy-Item -Force $builtScriptedApk $destScriptedApk
$scriptedSize = (Get-Item $destScriptedApk).Length
$scriptedSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $destScriptedApk).Hash.ToLowerInvariant()
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
$mdSize = (Get-Item $destMdApk).Length
$mdSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $destMdApk).Hash.ToLowerInvariant()
$mdIconPath = Join-Path $IconDir "$MdPkg.png"
if (-not (Test-Path $mdIconPath)) {
    throw "MangaDex icon missing after vendor script: $mdIconPath"
}

if ($SigningKeystore) {
    if (-not (Test-Path $SigningKeystore)) { throw "Signing keystore not found: $SigningKeystore" }
    if (-not $SigningAlias) { throw "AURORA_EXTENSION_KEY_ALIAS is required for production signing" }
    if (-not $env:AURORA_EXTENSION_STORE_PASSWORD -or -not $env:AURORA_EXTENSION_KEY_PASSWORD) {
        throw "AURORA_EXTENSION_STORE_PASSWORD and AURORA_EXTENSION_KEY_PASSWORD are required"
    }
    $sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "D:\AndroidSDK" }
    $apkSigner = Get-ChildItem (Join-Path $sdkRoot "build-tools") -Directory |
        Sort-Object Name -Descending |
        ForEach-Object {
            @(
                (Join-Path $_.FullName "apksigner.bat"),
                (Join-Path $_.FullName "apksigner")
            )
        } |
        Where-Object { Test-Path $_ } |
        Select-Object -First 1
    if (-not $apkSigner) { throw "apksigner not found under $sdkRoot" }
    foreach ($apk in @($destStubApk, $destScriptedApk, $destMdApk)) {
        $signed = "$apk.signed"
        Remove-Item -Force $signed -ErrorAction SilentlyContinue
        & $apkSigner sign `
            --ks $SigningKeystore `
            --ks-key-alias $SigningAlias `
            --ks-pass env:AURORA_EXTENSION_STORE_PASSWORD `
            --key-pass env:AURORA_EXTENSION_KEY_PASSWORD `
            --out $signed `
            $apk
        if ($LASTEXITCODE -ne 0) { throw "Failed to sign $apk" }
        Move-Item -Force $signed $apk
    }
}

$stubSize = (Get-Item $destStubApk).Length
$stubSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $destStubApk).Hash.ToLowerInvariant()
$scriptedSize = (Get-Item $destScriptedApk).Length
$scriptedSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $destScriptedApk).Hash.ToLowerInvariant()
$mdSize = (Get-Item $destMdApk).Length
$mdSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $destMdApk).Hash.ToLowerInvariant()

$fpScript = Join-Path $PSScriptRoot "get-signing-fingerprint.ps1"
$fingerprint = if ($SigningKeystore) {
    & $fpScript `
        -Keystore $SigningKeystore `
        -StorePass $env:AURORA_EXTENSION_STORE_PASSWORD `
        -Alias $SigningAlias
} else {
    & $fpScript
}
Write-Host "==> signingKeyFingerprint: $fingerprint"

if ($ExpectedSigningFingerprint) {
    $expected = ($ExpectedSigningFingerprint -replace ":", "").ToLowerInvariant()
    if ($expected -ne $fingerprint) {
        throw "Signing fingerprint mismatch: expected $expected, got $fingerprint"
    }
}

$existingRepoJson = Join-Path $RepoDir "repo.json"
if ((Test-Path $existingRepoJson) -and -not $AllowSigningKeyRotation) {
    $existingFingerprint = (Get-Content $existingRepoJson -Raw | ConvertFrom-Json).meta.signingKeyFingerprint
    if ($existingFingerprint -and $existingFingerprint -ne $fingerprint) {
        throw "Signing key changed from $existingFingerprint to $fingerprint. Use -AllowSigningKeyRotation only after explicit rotation approval."
    }
}

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
  "sha256": "$stubSha256",
  "size": $stubSize,
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
  "sha256": "$scriptedSha256",
  "size": $scriptedSize,
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
  "sha256": "$mdSha256",
  "size": $mdSize,
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
    "website": "https://github.com/charles0329979/AuroraExtensions",
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
$tmpPy = Join-Path ([IO.Path]::GetTempPath()) "aurora-generate-index-$PID.py"
[System.IO.File]::WriteAllText($tmpPy, $py, (New-Object System.Text.UTF8Encoding $false))
& $python.Source $tmpPy
$pyExit = $LASTEXITCODE
Remove-Item -Force $tmpPy -ErrorAction SilentlyContinue
if ($pyExit -ne 0) { throw "Python index generation failed (exit $pyExit)" }

# Append the curated website batch without replacing Stub, Scripted, or MangaDex.
$batchVendorScript = Join-Path $PSScriptRoot "vendor-batch-sources.ps1"
$batchVendorArgs = @{ RepoRoot = $RepoRoot }
if ($SkipBuild) { $batchVendorArgs.SkipBuild = $true }
& $batchVendorScript @batchVendorArgs
if (-not $?) { throw "vendor-batch-sources.ps1 failed" }

# The source catalogue owns all user-visible package metadata. Build scripts may
# create intermediate indexes, but the final published bytes always come from it.
& (Join-Path $PSScriptRoot "sync-source-catalog.ps1") -RepoRoot $RepoRoot -Mode WriteDerived
if (-not $?) { throw "sync-source-catalog.ps1 failed" }

Write-Host "==> Done. Repo artefacts in $RepoDir"
Get-ChildItem $RepoDir -Recurse -File | ForEach-Object {
    "{0}`t{1}" -f $_.Length, $_.FullName.Substring($RepoDir.Length + 1)
}
