<#
.SYNOPSIS
  Import the connected-device PPCat catalogue audited on 2026-08-28.

.DESCRIPTION
  The complete 440-row inventory is retained for traceability. Only rows that
  passed PPCat browse/search testing are added to the healthy catalogue, and
  only manga rows backed by a signed APK in repo/index.min.json are active.
#>
[CmdletBinding()]
param(
    [string]$RepoRoot = "",
    [string]$CurrentHealthCsv = "",
    [string]$PreviousSourcesCsv = ""
)

$ErrorActionPreference = "Stop"
if (-not $RepoRoot) { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path }
if (-not $CurrentHealthCsv) {
    $CurrentHealthCsv = Join-Path (Split-Path $RepoRoot) "ppcat_device_inspect\health_20260828\ppcat-health-final.csv"
}
if (-not $PreviousSourcesCsv) {
    $PreviousSourcesCsv = Join-Path (Split-Path $RepoRoot) "ppcat_device_inspect\health_20260826\sources.csv"
}
foreach ($path in @($CurrentHealthCsv, $PreviousSourcesCsv)) {
    if (-not (Test-Path -LiteralPath $path)) { throw "Required catalogue not found: $path" }
}

$catalogDir = Join-Path $RepoRoot "catalog"
$indexPath = Join-Path $RepoRoot "repo\index.min.json"
New-Item -ItemType Directory -Force -Path $catalogDir | Out-Null

$implementation = @{
    1   = @{ package = "eu.kanade.tachiyomi.extension.zh.tencentcomics"; coverage = "exact"; validation = "device_verified" }
    2   = @{ package = "eu.kanade.tachiyomi.extension.zh.zaimanhua"; coverage = "exact"; validation = "device_verified" }
    7   = @{ package = "eu.kanade.tachiyomi.extension.zh.soman"; coverage = "readable_backend"; validation = "device_verified"; limitation = "filters_out_paid_and_unreadable_external_routes" }
    15  = @{ package = "eu.kanade.tachiyomi.extension.zh.gufengmh"; coverage = "exact"; validation = "device_degraded" }
    16  = @{ package = "eu.kanade.tachiyomi.extension.zh.ycymh"; coverage = "scheme_successor"; validation = "device_verified" }
    19  = @{ package = "eu.kanade.tachiyomi.extension.zh.manwashizuku"; coverage = "family"; validation = "device_verified" }
    20  = @{ package = "eu.kanade.tachiyomi.extension.zh.manwashizuku"; coverage = "family"; validation = "device_verified" }
    21  = @{ package = "eu.kanade.tachiyomi.extension.zh.manwashizuku"; coverage = "family"; validation = "device_verified" }
    22  = @{ package = "eu.kanade.tachiyomi.extension.zh.miaoqu"; coverage = "exact"; validation = "device_verified" }
    26  = @{ package = "eu.kanade.tachiyomi.extension.zh.dumanwu"; coverage = "same_site_successor"; validation = "device_verified" }
    27  = @{ package = "eu.kanade.tachiyomi.extension.zh.yumanhua"; coverage = "exact"; validation = "device_verified" }
    45  = @{ package = "eu.kanade.tachiyomi.extension.zh.mh250"; coverage = "exact"; validation = "device_verified" }
    52  = @{ package = "eu.kanade.tachiyomi.extension.zh.baozimanhua"; coverage = "exact_mirror"; validation = "device_verified"; limitation = "latest_chapters_may_be_app_only" }
    54  = @{ package = "eu.kanade.tachiyomi.extension.zh.manquanzi"; coverage = "exact"; validation = "device_degraded" }
    58  = @{ package = "eu.kanade.tachiyomi.extension.zh.kaixinman"; coverage = "exact"; validation = "device_verified" }
    59  = @{ package = "eu.kanade.tachiyomi.extension.zh.sisimanhua"; coverage = "exact"; validation = "device_verified" }
    70  = @{ package = "eu.kanade.tachiyomi.extension.zh.rumanhua"; coverage = "scheme_successor"; validation = "device_verified" }
    69  = @{ package = "eu.kanade.tachiyomi.extension.zh.manshiduo"; coverage = "exact"; validation = "device_verified" }
    71  = @{ package = "eu.kanade.tachiyomi.extension.zh.dumanwu"; coverage = "exact"; validation = "device_verified" }
    73  = @{ package = "eu.kanade.tachiyomi.extension.zh.bikabika"; coverage = "exact"; validation = "device_verified" }
    120 = @{ package = "eu.kanade.tachiyomi.extension.zh.dmanhua"; coverage = "exact"; validation = "device_verified" }
    145 = @{ package = "eu.kanade.tachiyomi.extension.zh.manhua360"; coverage = "exact"; validation = "device_degraded"; limitation = "recent_chapters_may_be_app_promo_only" }
    84  = @{ package = "eu.kanade.tachiyomi.extension.zh.mh1234"; coverage = "exact"; validation = "device_verified" }
    129 = @{ package = "eu.kanade.tachiyomi.extension.zh.manhuadaquan"; coverage = "exact"; validation = "device_verified" }
    135 = @{ package = "eu.kanade.tachiyomi.extension.zh.creativecomic"; coverage = "official_alias"; validation = "device_verified" }
    166 = @{ package = "eu.kanade.tachiyomi.extension.zh.mycomic"; coverage = "exact"; validation = "device_verified" }
    204 = @{ package = "eu.kanade.tachiyomi.extension.zh.ttkmh"; coverage = "exact"; validation = "device_verified" }
    212 = @{ package = "eu.kanade.tachiyomi.extension.zh.terrahistoricus"; coverage = "official_alias"; validation = "device_verified" }
    229 = @{ package = "eu.kanade.tachiyomi.extension.zh.manhua456"; coverage = "exact"; validation = "device_verified" }
    266 = @{ package = "eu.kanade.tachiyomi.extension.zh.didamanhua"; coverage = "exact"; validation = "device_verified" }
    290 = @{ package = "eu.kanade.tachiyomi.extension.zh.manhua36"; coverage = "exact"; validation = "device_verified" }
    296 = @{ package = "eu.kanade.tachiyomi.extension.zh.boylove"; coverage = "exact_current_mirror"; validation = "device_verified" }
    319 = @{ package = "eu.kanade.tachiyomi.extension.zh.zerobyw"; coverage = "scheme_successor"; validation = "device_verified" }
    337 = @{ package = "eu.kanade.tachiyomi.extension.zh.baozimhorg"; coverage = "family_mirror"; validation = "device_verified" }
    382 = @{ package = "eu.kanade.tachiyomi.extension.ja.rawkuma"; coverage = "exact"; validation = "device_verified" }
    386 = @{ package = "eu.kanade.tachiyomi.extension.all.mangadex"; coverage = "exact"; validation = "device_verified" }
    425 = @{ package = "eu.kanade.tachiyomi.extension.zh.bilimanga"; coverage = "exact"; validation = "device_verified" }
}

function Get-Kind($row) {
    $index = [int]$row.Index
    if ($row.Name -match "轻小说") { return "novel" }
    if ($row.Name -match "动画") { return "anime" }
    if ($row.Name -match "游戏") { return "game" }
    if ($index -ge 407 -and $index -le 423) { return "gallery" }
    return "manga"
}

$repoEntries = @(Get-Content -LiteralPath $indexPath -Raw | ConvertFrom-Json)
$repoPackages = @{}
foreach ($entry in $repoEntries) { $repoPackages[$entry.pkg] = $entry }

$all = @(Import-Csv -LiteralPath $CurrentHealthCsv | Sort-Object { [int]$_.Index })
$healthy = @($all | Where-Object FinalGrade -eq "可用")
if ($all.Count -ne 440) { throw "Expected 440 current PPCat sources, found $($all.Count)" }
if ($healthy.Count -ne 109) { throw "Expected 109 usable PPCat sources, found $($healthy.Count)" }

$rows = foreach ($row in $healthy) {
    $id = [int]$row.Index
    $kind = Get-Kind $row
    $impl = $implementation[$id]
    $package = if ($impl) { [string]$impl.package } else { $null }
    $packagePresent = [bool]($package -and $repoPackages.ContainsKey($package))
    $state = if ($kind -ne "manga") {
        "unsupported_kind"
    } elseif (-not $impl) {
        "implementation_required"
    } elseif (-not $packagePresent) {
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
        limitation = if ($impl -and $impl.limitation) { [string]$impl.limitation } else { $null }
    }
}

$summary = [ordered]@{
    totalInventory = $all.Count
    totalUsable = $rows.Count
    supportedManga = @($rows | Where-Object kind -eq "manga").Count
    unsupportedKind = @($rows | Where-Object importState -eq "unsupported_kind").Count
    activeVerified = @($rows | Where-Object importState -eq "active_verified").Count
    activeDegraded = @($rows | Where-Object importState -eq "active_degraded").Count
    activeDevicePending = @($rows | Where-Object importState -eq "active_device_pending").Count
    implementationMissing = @($rows | Where-Object { $_.importState -like "implementation_*" }).Count
}

$document = [ordered]@{
    schemaVersion = 2
    auditDate = "2026-08-28"
    sourceReport = "ppcat-health-final.csv"
    rule = "Usable is a PPCat list-level result. A manga source is active only when its signed APK exists in repo/index.min.json; non-manga kinds remain inventory-only."
    summary = $summary
    sources = $rows
}

$old = @(Import-Csv -LiteralPath $PreviousSourcesCsv)
$oldKeys = @{}; $newKeys = @{}
foreach ($row in $old) { $oldKeys["$($row.Name)|$($row.Url)"] = $row }
foreach ($row in $all) { $newKeys["$($row.Name)|$($row.Url)"] = $row }
$added = @($all | Where-Object { -not $oldKeys.ContainsKey("$($_.Name)|$($_.Url)") })
$removed = @($old | Where-Object { -not $newKeys.ContainsKey("$($_.Name)|$($_.Url)") })
$oldByName = $old | Group-Object Name -AsHashTable -AsString
$newByName = $all | Group-Object Name -AsHashTable -AsString
$changed = @(foreach ($name in $oldByName.Keys) {
    if (-not $newByName.ContainsKey($name)) { continue }
    $oldUrls = @($oldByName[$name].Url)
    $newUrls = @($newByName[$name].Url)
    if (Compare-Object $oldUrls $newUrls) {
        [pscustomobject][ordered]@{ name = $name; oldUrl = ($oldUrls -join "; "); newUrl = ($newUrls -join "; ") }
    }
})
$comparison = [ordered]@{
    previousDate = "2026-08-26"
    currentDate = "2026-08-28"
    previousCount = $old.Count
    currentCount = $all.Count
    exactUnchanged = $all.Count - $added.Count
    exactAdded = $added.Count
    exactRemoved = $removed.Count
    sameNameUrlChanged = $changed.Count
    changed = $changed
}

$utf8 = [Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllText((Join-Path $catalogDir "healthy-sources-20260828.json"), ($document | ConvertTo-Json -Depth 8) + "`n", $utf8)
$rows | Export-Csv -LiteralPath (Join-Path $catalogDir "healthy-sources-20260828.csv") -NoTypeInformation -Encoding utf8BOM
$all | Export-Csv -LiteralPath (Join-Path $catalogDir "ppcat-sources-20260828.csv") -NoTypeInformation -Encoding utf8BOM
[IO.File]::WriteAllText((Join-Path $catalogDir "ppcat-comparison-20260826-to-20260828.json"), ($comparison | ConvertTo-Json -Depth 6) + "`n", $utf8)

Write-Host "Imported $($rows.Count) usable rows from a $($all.Count)-source PPCat inventory"
Write-Host "Manga=$($summary.supportedManga); verified=$($summary.activeVerified); degraded=$($summary.activeDegraded); pending=$($summary.activeDevicePending); implementation required=$($summary.implementationMissing); unsupported kind=$($summary.unsupportedKind)"
