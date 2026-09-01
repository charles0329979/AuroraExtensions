<#
.SYNOPSIS
  Compare extension packages installed on an Android device with repo/index.min.json.
#>
[CmdletBinding()]
param(
    [string]$RepoRoot = "",
    [string]$DeviceSerial = "",
    [switch]$RequireAll,
    [string]$OutputPath = ""
)

$ErrorActionPreference = "Stop"
if (-not $RepoRoot) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}
$adb = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adb) { throw "adb was not found" }

$adbArgs = @()
if ($DeviceSerial) { $adbArgs += @('-s', $DeviceSerial) }
$state = (& $adb.Source @adbArgs get-state 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $state -ne 'device') { throw "Android device is not available" }

$index = @(Get-Content -LiteralPath (Join-Path $RepoRoot 'repo\index.min.json') -Raw | ConvertFrom-Json)
$raw = @(& $adb.Source @adbArgs shell cmd package list packages --show-versioncode)
$installed = [ordered]@{}
foreach ($line in $raw) {
    if ($line -match '^package:([^ ]+) versionCode:(\d+)' -and $Matches[1] -like 'eu.kanade.tachiyomi.extension.*') {
        $installed[$Matches[1]] = [long]$Matches[2]
    }
}

$indexedPackages = @($index | ForEach-Object { [string]$_.pkg })
$missing = @($indexedPackages | Where-Object { -not $installed.Contains($_) })
$extra = @($installed.Keys | Where-Object { $_ -notin $indexedPackages })
$mismatches = @(foreach ($entry in $index) {
    $package = [string]$entry.pkg
    if ($installed.Contains($package) -and [long]$installed[$package] -ne [long]$entry.code) {
        [ordered]@{
            package = $package
            repositoryVersionCode = [long]$entry.code
            deviceVersionCode = [long]$installed[$package]
        }
    }
})

$document = [ordered]@{
    schemaVersion = 1
    checkedAt = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
    indexed = $index.Count
    installed = $installed.Count
    matched = @($indexedPackages | Where-Object { $installed.Contains($_) }).Count
    missing = $missing
    extra = $extra
    versionMismatches = $mismatches
}

if ($OutputPath) {
    $resolvedOutput = if ([IO.Path]::IsPathRooted($OutputPath)) { $OutputPath } else { Join-Path $RepoRoot $OutputPath }
    New-Item -ItemType Directory -Force -Path (Split-Path $resolvedOutput) | Out-Null
    [IO.File]::WriteAllText(
        $resolvedOutput,
        ($document | ConvertTo-Json -Depth 6) + "`n",
        [Text.UTF8Encoding]::new($false)
    )
}

Write-Host "Device extension inventory: $($document.matched)/$($document.indexed) indexed packages installed"
Write-Host "Missing: $($missing.Count); extra: $($extra.Count); version mismatch: $($mismatches.Count)"
if ($extra.Count -gt 0 -or $mismatches.Count -gt 0 -or ($RequireAll -and $missing.Count -gt 0)) {
    throw "Device extension inventory verification failed"
}
