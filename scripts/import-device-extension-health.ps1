<#
.SYNOPSIS
  Convert AuroraReader's real-device extension probe into the current health snapshot.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ProbeReport,
    [string]$RepoRoot = "",
    [datetime]$AuditDate = (Get-Date),
    [string]$OutputPath = "",
    [switch]$PreserveExistingActive
)

$ErrorActionPreference = "Stop"
if (-not $RepoRoot) { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path }
$reportPath = if ([IO.Path]::IsPathRooted($ProbeReport)) { $ProbeReport } else { Join-Path $RepoRoot $ProbeReport }
$report = Get-Content -LiteralPath $reportPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ([int]$report.schemaVersion -ne 1 -or $report.completed -ne $true) {
    throw "The device probe is incomplete or unsupported"
}

$dateText = $AuditDate.ToString('yyyy-MM-dd')
$dateStamp = $AuditDate.ToString('yyyyMMdd')
if (-not $OutputPath) { $OutputPath = Join-Path $RepoRoot "catalog\healthy-sources-$dateStamp.json" }
$catalog = Get-Content -LiteralPath (Join-Path $RepoRoot 'catalog\sources.yaml') -Raw -Encoding UTF8 | ConvertFrom-Json
$packages = @{}
$sources = @{}
foreach ($package in @($catalog.packages)) {
    $packages[[string]$package.package] = $package
    foreach ($source in @($package.sources)) { $sources[[long]$source.id] = $source }
}

$previous = Get-ChildItem -LiteralPath (Join-Path $RepoRoot 'catalog') -Filter 'healthy-sources-*.json' -File |
    Where-Object FullName -ne ([IO.Path]::GetFullPath($OutputPath)) |
    Sort-Object Name -Descending |
    Select-Object -First 1
$auditedKeys = @{}
foreach ($result in @($report.sources)) {
    $auditedKeys["$([string]$result.package):$([long]$result.sourceId)"] = $true
}
$preserved = if ($previous) {
    @((Get-Content -LiteralPath $previous.FullName -Raw -Encoding UTF8 | ConvertFrom-Json).sources | Where-Object {
        $isActive = [string]$_.importState -like 'active_*'
        $key = "$([string]$_.package):$([long]$_.id)"
        (-not $isActive) -or ($PreserveExistingActive -and -not $auditedKeys.ContainsKey($key))
    })
} else {
    @()
}

$activeRows = foreach ($result in @($report.sources)) {
    $packageName = [string]$result.package
    $package = $packages[$packageName]
    $sourceId = [long]$result.sourceId
    $source = $sources[$sourceId]
    if (-not $package -or -not $source) { throw "Probe result is not declared in the catalogue: $packageName/$sourceId" }
    if ([long]$result.versionCode -ne [long]$package.versionCode) {
        throw "Probe version does not match the catalogue: $packageName"
    }

    $failedStages = @($result.stages.PSObject.Properties | Where-Object { [string]$_.Value.status -ne 'pass' })
    $limitation = @($failedStages | ForEach-Object {
        "$($_.Name)=$($_.Value.status):$($_.Value.message)"
    }) -join '; '
    $passedStages = @($result.stages.PSObject.Properties | Where-Object {
        [string]$_.Value.status -eq 'pass'
    } | ForEach-Object Name)
    $mapping = switch ([string]$result.status) {
        'healthy' { @{ importState = 'active_verified'; validation = 'device_verified'; health = '可用'; functionalClass = 'FullReadingChain' } }
        'partial' { @{ importState = 'active_degraded'; validation = 'device_degraded'; health = '部分可用'; functionalClass = 'PartialReadingChain' } }
        'login_required' { @{ importState = 'active_device_pending'; validation = 'login_required'; health = '需要登录'; functionalClass = 'LoginRequired' } }
        'failed' { @{ importState = 'active_failed'; validation = 'device_failed'; health = '失效'; functionalClass = 'Failed' } }
        default { throw "Unknown probe status: $($result.status)" }
    }

    [ordered]@{
        id = $sourceId
        name = [string]$result.sourceName
        url = [string]$source.baseUrl
        kind = 'manga'
        health = $mapping.health
        functionalClass = $mapping.functionalClass
        resultTiles = 0
        importState = $mapping.importState
        package = $packageName
        packageVersionCode = [long]$result.versionCode
        coverage = 'exact'
        validation = $mapping.validation
        limitation = if ($limitation) { $limitation } else { $null }
        checkedAt = $dateText
        evidence = "AuroraReader device probe completed: $($passedStages -join ', ')"
        passedStages = $passedStages
    }
}

$rows = @($activeRows) + @($preserved)
$document = [ordered]@{
    schemaVersion = 3
    auditDate = $dateText
    sourceReport = [IO.Path]::GetFileName($reportPath)
    rule = 'Publication health is derived from the signed extension version and a real-device browse, search, details, chapters, pages, and image probe.'
    summary = [ordered]@{
        totalInventory = $rows.Count
        totalUsable = $rows.Count
        supportedManga = @($rows | Where-Object kind -eq 'manga').Count
        unsupportedKind = @($rows | Where-Object importState -eq 'unsupported_kind').Count
        activeVerified = @($rows | Where-Object importState -eq 'active_verified').Count
        activeDegraded = @($rows | Where-Object importState -eq 'active_degraded').Count
        activeDevicePending = @($rows | Where-Object importState -eq 'active_device_pending').Count
        activeFailed = @($rows | Where-Object importState -eq 'active_failed').Count
        implementationMissing = @($rows | Where-Object { [string]$_.importState -like 'implementation_*' }).Count
    }
    sources = $rows
}

$json = $document | ConvertTo-Json -Depth 20
[IO.File]::WriteAllText($OutputPath, $json + "`n", [Text.UTF8Encoding]::new($false))
Write-Host "Imported device health: $($document.summary.activeVerified) healthy, $($document.summary.activeDegraded) partial, $($document.summary.activeFailed) failed, $($document.summary.activeDevicePending) login/pending"
