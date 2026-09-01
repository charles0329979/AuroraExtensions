<#
.SYNOPSIS
  Convert the latest audited source catalogue and the published extension index
  into stable, machine-readable maintenance artefacts.
#>
[CmdletBinding()]
param(
    [string]$RepoRoot = ""
)

$ErrorActionPreference = "Stop"
if (-not $RepoRoot) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}

$repoDir = Join-Path $RepoRoot "repo"
$catalogDir = Join-Path $RepoRoot "catalog"
$reportDir = Join-Path $RepoRoot "reports"
$indexPath = Join-Path $repoDir "index.min.json"
$repoMetaPath = Join-Path $repoDir "repo.json"
$policyPath = Join-Path (Join-Path $RepoRoot "maintenance") "policy.json"
$sourceCatalogPath = Join-Path $catalogDir "sources.yaml"

$index = @(Get-Content -LiteralPath $indexPath -Raw | ConvertFrom-Json)
$repoMeta = Get-Content -LiteralPath $repoMetaPath -Raw | ConvertFrom-Json
$policy = Get-Content -LiteralPath $policyPath -Raw | ConvertFrom-Json
if ([int]$policy.schemaVersion -ne 1) { throw "Unsupported maintenance policy schemaVersion" }
$sourceCatalog = Get-Content -LiteralPath $sourceCatalogPath -Raw | ConvertFrom-Json
if ([int]$sourceCatalog.schemaVersion -ne 1) { throw "Unsupported source catalogue schemaVersion" }
$catalogByPackage = @{}
foreach ($package in @($sourceCatalog.packages)) { $catalogByPackage[[string]$package.package] = $package }
$healthFile = Get-ChildItem -LiteralPath $catalogDir -Filter "healthy-sources-*.json" -File |
    Sort-Object Name -Descending |
    Select-Object -First 1
if (-not $healthFile) { throw "No healthy-sources-*.json catalogue found" }
$health = Get-Content -LiteralPath $healthFile.FullName -Raw | ConvertFrom-Json
$attestationPath = Join-Path $catalogDir "device-attestations.json"
$attestations = if (Test-Path -LiteralPath $attestationPath) {
    $attestationDocument = Get-Content -LiteralPath $attestationPath -Raw | ConvertFrom-Json
    if ([int]$attestationDocument.schemaVersion -ne 1) { throw "Unsupported device attestation schemaVersion" }
    @($attestationDocument.attestations)
} else {
    @()
}

$snapshotMatch = [regex]::Match($healthFile.BaseName, '(\d{8})$')
$snapshotDate = if ($snapshotMatch.Success) {
    [datetime]::ParseExact(
        $snapshotMatch.Groups[1].Value,
        'yyyyMMdd',
        [Globalization.CultureInfo]::InvariantCulture,
        [Globalization.DateTimeStyles]::AssumeUniversal
    ).ToUniversalTime()
} else {
    $healthFile.LastWriteTimeUtc
}
$ageDays = [math]::Max(0, [math]::Floor(((Get-Date).ToUniversalTime() - $snapshotDate).TotalDays))
$freshness = if ($ageDays -le [int]$policy.audit.currentWithinDays) {
    "current"
} elseif ($ageDays -le [int]$policy.audit.staleWithinDays) {
    "stale"
} else {
    "expired"
}

$packages = foreach ($entry in $index | Sort-Object pkg) {
    $catalogPackage = $catalogByPackage[[string]$entry.pkg]
    if (-not $catalogPackage) { throw "Indexed package is missing from catalog/sources.yaml: $($entry.pkg)" }
    $excludedFromReadingHealth = [string]$catalogPackage.role -ne "reading_source"
    $rows = @($health.sources | Where-Object {
        [string]$_.package -eq [string]$entry.pkg -and [string]$_.importState -like 'active_*'
    })
    $packageAttestations = @($attestations | Where-Object {
        if ([string]$_.package -ne [string]$entry.pkg) { return $false }
        $checkedAt = [datetime]::ParseExact(
            [string]$_.checkedAt,
            'yyyy-MM-dd',
            [Globalization.CultureInfo]::InvariantCulture,
            [Globalization.DateTimeStyles]::AssumeUniversal
        ).ToUniversalTime()
        ((Get-Date).ToUniversalTime() - $checkedAt).TotalDays -le [int]$policy.audit.publishableWithinDays
    })
    $attestedRows = @($packageAttestations | ForEach-Object {
        $attestation = $_
        @($attestation.sources | ForEach-Object {
            [pscustomobject]@{
                id = $null
                name = [string]$_.name
                url = [string]$_.baseUrl
                importState = [string]$attestation.state
                validation = [string]$attestation.validation
                limitation = if ($_.limitation) { [string]$_.limitation } else { $null }
                checkedAt = [string]$attestation.checkedAt
                evidence = [string]$attestation.evidence
                passedStages = @($attestation.passedStages)
            }
        })
    })
    $rows = @($rows) + @($attestedRows)
    $grade = if ($excludedFromReadingHealth) {
        "N"
    } elseif (@($rows | Where-Object importState -eq 'active_degraded').Count -gt 0) {
        "B"
    } elseif (@($rows | Where-Object importState -eq 'active_device_pending').Count -gt 0) {
        "C"
    } elseif (@($rows | Where-Object importState -eq 'active_verified').Count -gt 0) {
        "A"
    } else {
        "U"
    }
    $state = switch ($grade) {
        "A" { "healthy" }
        "B" { "degraded" }
        "C" { "verification_pending" }
        "N" { "not_applicable" }
        default { "unassessed" }
    }
    $limitations = @($rows | ForEach-Object { [string]$_.limitation } | Where-Object { $_ } | Sort-Object -Unique)
    [ordered]@{
        package = [string]$entry.pkg
        name = [string]$entry.name
        version = [string]$entry.version
        versionCode = [long]$entry.code
        grade = $grade
        state = $state
        role = [string]$catalogPackage.role
        implementation = [string]$catalogPackage.implementation
        channel = [string]$catalogPackage.channel
        publicationState = [string]$catalogPackage.state
        catalogueFreshness = $freshness
        auditedSources = @($rows).Count
        sources = @($rows | ForEach-Object {
            [ordered]@{
                id = $_.id
                name = [string]$_.name
                baseUrl = [string]$_.url
                validation = [string]$_.validation
                limitation = if ($_.limitation) { [string]$_.limitation } else { $null }
                checkedAt = if ($_.checkedAt) { [string]$_.checkedAt } else { $null }
                evidence = if ($_.evidence) { [string]$_.evidence } else { $null }
                passedStages = if ($_.passedStages) { @($_.passedStages) } else { @() }
            }
        })
        limitations = $limitations
    }
}

$gradeCounts = [ordered]@{}
foreach ($grade in @('A', 'B', 'C', 'D', 'Q', 'U', 'N')) {
    $gradeCounts[$grade] = @($packages | Where-Object grade -eq $grade).Count
}
$activePackageCount = @($packages | Where-Object grade -in @('A', 'B', 'C')).Count
$implementationMissing = @($health.sources | Where-Object { [string]$_.importState -like 'implementation_*' }).Count

$document = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
    repository = [ordered]@{
        name = [string]$repoMeta.meta.name
        signingKeyFingerprint = ([string]$repoMeta.meta.signingKeyFingerprint).ToLowerInvariant()
        indexedPackages = @($index).Count
    }
    audit = [ordered]@{
        snapshot = $healthFile.Name
        snapshotDate = $snapshotDate.ToString('yyyy-MM-dd')
        ageDays = $ageDays
        freshness = $freshness
        publishableWithinDays = [int]$policy.audit.publishableWithinDays
        requiredReadingChain = @($policy.requiredReadingChain)
    }
    summary = [ordered]@{
        grades = $gradeCounts
        assessedPackages = $activePackageCount
        unassessedPackages = $gradeCounts.U
        nonReadingPackages = $gradeCounts.N
        implementationBacklog = $implementationMissing
    }
    packages = @($packages)
}

New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
$utf8 = New-Object System.Text.UTF8Encoding $false
$json = ($document | ConvertTo-Json -Depth 10) + "`n"
[IO.File]::WriteAllText((Join-Path $repoDir "health.json"), $json, $utf8)
[IO.File]::WriteAllText((Join-Path $reportDir "maintenance-summary.json"), $json, $utf8)

$lines = @(
    "# Aurora Extensions maintenance summary",
    "",
    "Generated: $($document.generatedAt)",
    "",
    "- Indexed packages: $(@($index).Count)",
    "- Assessed packages: $activePackageCount",
    "- Grade A: $($gradeCounts.A)",
    "- Grade B: $($gradeCounts.B)",
    "- Grade C: $($gradeCounts.C)",
    "- Unassessed: $($gradeCounts.U)",
    "- Non-reading containers: $($gradeCounts.N)",
    "- Implementation backlog: $implementationMissing",
    "- Audit snapshot: $($healthFile.Name) ($freshness, $ageDays day(s) old)",
    "",
    "| Grade | Package | Version | State | Audited sources |",
    "|---|---|---:|---|---:|"
)
foreach ($package in $packages | Sort-Object grade, package) {
    $lines += "| $($package.grade) | $($package.package) | $($package.version) | $($package.state) | $($package.auditedSources) |"
}
[IO.File]::WriteAllText((Join-Path $reportDir "maintenance-summary.md"), ($lines -join "`n") + "`n", $utf8)

Write-Host "Maintenance report generated: $(@($index).Count) indexed, $activePackageCount assessed, $($gradeCounts.U) unassessed"
Write-Host "Audit snapshot: $($healthFile.Name) ($freshness, $ageDays day(s) old)"
