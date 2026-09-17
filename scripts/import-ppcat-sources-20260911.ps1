<#
.SYNOPSIS
  Import the PPCat 0.9.0 catalogue audited on 2026-09-11.

.DESCRIPTION
  Keeps the complete device inventory, records only PPCat list-level usable
  sources as healthy, and carries an Aurora implementation forward by stable
  source identity (name and URL) instead of PPCat's mutable list position.
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
    $CurrentHealthCsv = Join-Path (Split-Path $RepoRoot) "ppcat_device_inspect\health_20260911\ppcat-health-final.csv"
}
if (-not $PreviousSourcesCsv) {
    $PreviousSourcesCsv = Join-Path (Split-Path $RepoRoot) "ppcat_device_inspect\current_check_20260828\sources.csv"
}

$catalogDir = Join-Path $RepoRoot "catalog"
$indexPath = Join-Path $RepoRoot "repo\index.min.json"
$packageCatalogPath = Join-Path $catalogDir "sources.yaml"
$previousHealthPath = Join-Path $catalogDir "healthy-sources-20260828.json"
foreach ($path in @($CurrentHealthCsv, $PreviousSourcesCsv, $indexPath, $packageCatalogPath, $previousHealthPath)) {
    if (-not (Test-Path -LiteralPath $path)) { throw "Required input not found: $path" }
}

function Get-Key([string]$Name, [string]$Url) {
    return "$($Name.Trim().ToLowerInvariant())|$($Url.Trim().TrimEnd('/').ToLowerInvariant())"
}

$repoEntries = @(Get-Content -LiteralPath $indexPath -Raw | ConvertFrom-Json)
$repoPackages = @{}
foreach ($entry in $repoEntries) { $repoPackages[[string]$entry.pkg] = $entry }

$previousHealth = Get-Content -LiteralPath $previousHealthPath -Raw | ConvertFrom-Json
$previousByKey = @{}
$previousByName = @{}
foreach ($source in @($previousHealth.sources)) {
    $previousByKey[(Get-Key $source.name $source.url)] = $source
    $nameKey = ([string]$source.name).Trim().ToLowerInvariant()
    if (-not $previousByName.ContainsKey($nameKey)) { $previousByName[$nameKey] = $source }
}

$declaredByName = @{}
$packageCatalog = Get-Content -LiteralPath $packageCatalogPath -Raw | ConvertFrom-Json
foreach ($package in @($packageCatalog.packages)) {
    foreach ($source in @($package.sources)) {
        $nameKey = ([string]$source.name).Trim().ToLowerInvariant()
        if (-not $declaredByName.ContainsKey($nameKey)) {
            $declaredByName[$nameKey] = [pscustomobject]@{ package = $package.package; source = $source }
        }
    }
}

$implementationOverrides = @{
    "猕猴桃漫画" = [pscustomobject]@{
        package = "eu.kanade.tachiyomi.extension.zh.manwashizuku"
        coverage = "same_backend_verified_mirror"
        validation = "device_verified"
        limitation = "published as the manwanu.cc mirror of 漫蛙(雫), not as a duplicate source"
    }
}

$all = @(Import-Csv -LiteralPath $CurrentHealthCsv | Sort-Object { [int]$_.Index })
$healthy = @($all | Where-Object FinalGrade -eq "可用")
if ($all.Count -ne 438) { throw "Expected 438 PPCat 0.9.0 sources, found $($all.Count)" }

$rows = foreach ($row in $healthy) {
    $nameKey = ([string]$row.Name).Trim().ToLowerInvariant()
    $previous = $previousByKey[(Get-Key $row.Name $row.Url)]
    $sameNamePrevious = $previousByName[$nameKey]
    $declared = $declaredByName[$nameKey]

    $kind = if ($previous) {
        [string]$previous.kind
    } elseif ($sameNamePrevious) {
        [string]$sameNamePrevious.kind
    } elseif ($row.Name -match "轻小说") {
        "novel"
    } elseif ($row.Name -match "动画") {
        "anime"
    } elseif ($row.Name -match "游戏") {
        "game"
    } elseif ($row.Name -match "壁纸|图片|图库") {
        "gallery"
    } else {
        "manga"
    }

    $implemented = if ($implementationOverrides.ContainsKey([string]$row.Name)) {
        $implementationOverrides[[string]$row.Name]
    } elseif ($previous -and $previous.package) {
        $previous
    } elseif ($sameNamePrevious -and $sameNamePrevious.package) {
        $sameNamePrevious
    } elseif ($declared) {
        [pscustomobject]@{
            package = $declared.package
            coverage = "exact_name"
            validation = "device_verified"
            limitation = $null
        }
    } else {
        $null
    }

    $package = if ($implemented) { [string]$implemented.package } else { $null }
    $packagePresent = [bool]($package -and $repoPackages.ContainsKey($package))
    $validation = if ($implemented) { [string]$implemented.validation } else { "not_implemented" }
    $state = if ($kind -ne "manga") {
        "unsupported_kind"
    } elseif (-not $implemented) {
        "implementation_required"
    } elseif (-not $packagePresent) {
        "implementation_missing"
    } elseif ($validation -eq "device_verified") {
        "active_verified"
    } elseif ($validation -eq "device_degraded") {
        "active_degraded"
    } else {
        "active_device_pending"
    }

    [pscustomobject][ordered]@{
        id = [int]$row.Index
        name = $row.Name
        url = $row.Url
        kind = $kind
        health = $row.FinalGrade
        functionalClass = $row.FunctionalClass
        resultTiles = [int]$row.ResultTiles
        importState = $state
        package = $package
        coverage = if ($implemented) { [string]$implemented.coverage } else { $null }
        validation = $validation
        limitation = if ($implemented -and $implemented.limitation) { [string]$implemented.limitation } else { $null }
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
    auditDate = "2026-09-11"
    sourceReport = "ppcat-health-final.csv"
    rule = "Usable is a PPCat list-level result. Publication still requires a signed Aurora APK and full device validation of catalogue, search, detail, chapters, page list, and image download."
    summary = $summary
    sources = $rows
}

$old = @(Import-Csv -LiteralPath $PreviousSourcesCsv)
$oldKeys = @{}; $newKeys = @{}
foreach ($row in $old) { $oldKeys[(Get-Key $row.Name $row.Url)] = $row }
foreach ($row in $all) { $newKeys[(Get-Key $row.Name $row.Url)] = $row }
$added = @($all | Where-Object { -not $oldKeys.ContainsKey((Get-Key $_.Name $_.Url)) })
$removed = @($old | Where-Object { -not $newKeys.ContainsKey((Get-Key $_.Name $_.Url)) })
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
    previousDate = "2026-08-28"
    currentDate = "2026-09-11"
    previousCount = $old.Count
    currentCount = $all.Count
    exactUnchanged = $all.Count - $added.Count
    exactAdded = $added.Count
    exactRemoved = $removed.Count
    sameNameUrlChanged = $changed.Count
    changed = $changed
}

$utf8 = [Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllText((Join-Path $catalogDir "healthy-sources-20260911.json"), ($document | ConvertTo-Json -Depth 8) + "`n", $utf8)
$rows | Export-Csv -LiteralPath (Join-Path $catalogDir "healthy-sources-20260911.csv") -NoTypeInformation -Encoding utf8BOM
$all | Export-Csv -LiteralPath (Join-Path $catalogDir "ppcat-sources-20260911.csv") -NoTypeInformation -Encoding utf8BOM
[IO.File]::WriteAllText((Join-Path $catalogDir "ppcat-comparison-20260828-to-20260911.json"), ($comparison | ConvertTo-Json -Depth 6) + "`n", $utf8)

Write-Host "Imported $($rows.Count) usable rows from $($all.Count) PPCat sources"
Write-Host "Manga=$($summary.supportedManga); verified=$($summary.activeVerified); degraded=$($summary.activeDegraded); pending=$($summary.activeDevicePending); implementation required=$($summary.implementationMissing); unsupported kind=$($summary.unsupportedKind)"
