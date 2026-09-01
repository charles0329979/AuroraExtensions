<#
.SYNOPSIS
  Import the healthy PPCat audit rows into AuroraExtensions as a machine-readable
  implementation catalogue. Only extensions that exist in repo/index.min.json
  are marked active; catalogue-only rows are never exposed as working plugins.
#>
[CmdletBinding()]
param(
    [string]$RepoRoot = "",
    [string]$HealthCsv = ""
)

$ErrorActionPreference = "Stop"
if (-not $RepoRoot) { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path }
if (-not $HealthCsv) {
    $HealthCsv = Join-Path (Split-Path $RepoRoot) "ppcat_device_inspect\health_20260826\ppcat-health-final.csv"
}
if (-not (Test-Path -LiteralPath $HealthCsv)) { throw "Health report not found: $HealthCsv" }

$catalogDir = Join-Path $RepoRoot "catalog"
$jsonPath = Join-Path $catalogDir "healthy-sources-20260826.json"
$csvPath = Join-Path $catalogDir "healthy-sources-20260826.csv"
$indexPath = Join-Path $RepoRoot "repo\index.min.json"
New-Item -ItemType Directory -Force -Path $catalogDir | Out-Null

$implementation = @{
    1   = @{ package = "eu.kanade.tachiyomi.extension.zh.tencentcomics"; coverage = "exact"; validation = "device_verified" }
    2   = @{ package = "eu.kanade.tachiyomi.extension.zh.zaimanhua"; coverage = "exact"; validation = "device_verified" }
    11  = @{ package = "eu.kanade.tachiyomi.extension.zh.gufengmh"; coverage = "exact"; validation = "device_degraded" }
    12  = @{ package = "eu.kanade.tachiyomi.extension.zh.ycymh"; coverage = "exact"; validation = "device_verified" }
    15  = @{ package = "eu.kanade.tachiyomi.extension.zh.manwashizuku"; coverage = "family"; validation = "device_verified" }
    16  = @{ package = "eu.kanade.tachiyomi.extension.zh.manwashizuku"; coverage = "family"; validation = "device_verified" }
    17  = @{ package = "eu.kanade.tachiyomi.extension.zh.manwashizuku"; coverage = "family"; validation = "device_verified" }
    36  = @{ package = "eu.kanade.tachiyomi.extension.zh.manquanzi"; coverage = "exact"; validation = "device_degraded" }
    47  = @{ package = "eu.kanade.tachiyomi.extension.zh.manshiduo"; coverage = "exact"; validation = "device_verified" }
    49  = @{ package = "eu.kanade.tachiyomi.extension.zh.dumanwu"; coverage = "exact_domain_successor"; validation = "device_verified" }
    56  = @{ package = "eu.kanade.tachiyomi.extension.zh.mh1234"; coverage = "exact"; validation = "device_verified" }
    86  = @{ package = "eu.kanade.tachiyomi.extension.zh.creativecomic"; coverage = "exact"; validation = "device_verified" }
    135 = @{ package = "eu.kanade.tachiyomi.extension.zh.terrahistoricus"; coverage = "official_site"; validation = "device_verified" }
    170 = @{ package = "eu.kanade.tachiyomi.extension.zh.didamanhua"; coverage = "exact"; validation = "device_verified" }
    203 = @{ package = "eu.kanade.tachiyomi.extension.zh.zerobyw"; coverage = "exact"; validation = "device_verified" }
    217 = @{ package = "eu.kanade.tachiyomi.extension.zh.baozimhorg"; coverage = "exact_mirror"; validation = "device_verified" }
    218 = @{ package = "eu.kanade.tachiyomi.extension.zh.baozimhorg"; coverage = "family_mirror"; validation = "device_verified" }
    247 = @{ package = "eu.kanade.tachiyomi.extension.all.mangadex"; coverage = "exact"; validation = "device_verified" }
    271 = @{ package = "eu.kanade.tachiyomi.extension.zh.bilimanga"; coverage = "exact"; validation = "device_verified" }
}

$repoEntries = @(Get-Content -LiteralPath $indexPath -Raw | ConvertFrom-Json)
$repoPackages = @{}
foreach ($entry in $repoEntries) { $repoPackages[$entry.pkg] = $entry }

$healthy = @(Import-Csv -LiteralPath $HealthCsv | Where-Object FinalGrade -eq "可用")
if ($healthy.Count -ne 81) { throw "Expected 81 healthy sources, found $($healthy.Count)" }

$rows = foreach ($row in $healthy) {
    $id = [int]$row.Index
    $kind = if ($row.Name -match "轻小说") { "novel" } elseif ($row.Name -match "动画") { "anime" } elseif ($row.Name -match "游戏") { "game" } else { "manga" }
    $impl = $implementation[$id]
    $package = if ($impl) { [string]$impl.package } else { $null }
    $active = [bool]($package -and $repoPackages.ContainsKey($package))
    $state = if (-not $impl) {
        "implementation_required"
    } elseif (-not $active) {
        "implementation_missing"
    } elseif ($impl.validation -eq "device_verified") {
        "active_verified"
    } elseif ($impl.validation -eq "device_degraded") {
        "active_degraded"
    } else {
        "active_device_pending"
    }

    [pscustomobject][ordered]@{
        id = $id
        name = $row.Name
        url = $row.Url
        kind = $kind
        health = $row.FinalGrade
        functionalClass = $row.FunctionalClass
        resultTiles = [int]$row.ResultTiles
        importState = $state
        package = $package
        coverage = if ($impl) { [string]$impl.coverage } else { $null }
        validation = if ($impl) { [string]$impl.validation } else { "not_implemented" }
    }
}

$summary = [ordered]@{
    totalHealthy = $rows.Count
    activeVerified = @($rows | Where-Object importState -eq "active_verified").Count
    activeDegraded = @($rows | Where-Object importState -eq "active_degraded").Count
    activeDevicePending = @($rows | Where-Object importState -eq "active_device_pending").Count
    implementationMissing = @($rows | Where-Object { $_.importState -like "implementation_*" }).Count
}
$document = [ordered]@{
    schemaVersion = 1
    auditDate = "2026-08-26"
    sourceReport = "ppcat-health-final.csv"
    rule = "Catalogue rows are inventory only. A source is user-visible only when its signed APK is present in repo/index.min.json."
    summary = $summary
    sources = $rows
}

$utf8 = [Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllText($jsonPath, ($document | ConvertTo-Json -Depth 8) + "`n", $utf8)
$rows | Export-Csv -LiteralPath $csvPath -NoTypeInformation -Encoding utf8BOM

Write-Host "Imported $($rows.Count) healthy sources into the project catalogue"
Write-Host "Verified: $($summary.activeVerified); degraded: $($summary.activeDegraded); device pending: $($summary.activeDevicePending); implementation required: $($summary.implementationMissing)"
