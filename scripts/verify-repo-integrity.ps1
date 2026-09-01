<#
.SYNOPSIS
  Fail closed when Aurora repository metadata, APK bytes, or APK signer disagree.
#>
[CmdletBinding()]
param(
    [string]$RepoRoot = "",
    [string]$ApkSigner = "",
    [string]$Aapt2 = ""
)

$ErrorActionPreference = "Stop"
if (-not $RepoRoot) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}
if (-not $env:JAVA_HOME -and (Test-Path "D:\Android\jbr")) {
    $env:JAVA_HOME = "D:\Android\jbr"
}

& (Join-Path $PSScriptRoot "sync-source-catalog.ps1") -RepoRoot $RepoRoot -Mode Check
if (-not $?) { throw "Source catalogue verification failed" }

$repoDir = Join-Path $RepoRoot "repo"
$apkDir = (Resolve-Path (Join-Path $repoDir "apk")).Path
$policyPath = Join-Path (Join-Path $RepoRoot "maintenance") "policy.json"
$policy = Get-Content -LiteralPath $policyPath -Raw | ConvertFrom-Json
if ([int]$policy.schemaVersion -ne 1) { throw "Unsupported maintenance policy schemaVersion" }
$rolesPath = Join-Path (Join-Path $RepoRoot "maintenance") "package-roles.json"
$rolesDocument = Get-Content -LiteralPath $rolesPath -Raw | ConvertFrom-Json
if ([int]$rolesDocument.schemaVersion -ne 1) { throw "Unsupported package roles schemaVersion" }
$repoMeta = Get-Content (Join-Path $repoDir "repo.json") -Raw | ConvertFrom-Json
$index = @(Get-Content (Join-Path $repoDir "index.min.json") -Raw | ConvertFrom-Json)
$fullIndex = @(Get-Content (Join-Path $repoDir "index.json") -Raw | ConvertFrom-Json)
$sourceCatalog = Get-Content (Join-Path $RepoRoot "catalog\sources.yaml") -Raw | ConvertFrom-Json
$catalogByPackage = @{}
foreach ($package in @($sourceCatalog.packages)) { $catalogByPackage[[string]$package.package] = $package }
if (($index | ConvertTo-Json -Depth 20 -Compress) -ne ($fullIndex | ConvertTo-Json -Depth 20 -Compress)) {
    throw "index.json and index.min.json disagree"
}
$signer = [string]$repoMeta.meta.signingKeyFingerprint
if ($signer -notmatch '^[0-9a-fA-F]{64}$') {
    throw "repo.json has an invalid signingKeyFingerprint"
}
$signer = $signer.ToLowerInvariant()

if (-not $ApkSigner) {
    $sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "D:\AndroidSDK" }
    $candidate = Get-ChildItem (Join-Path $sdkRoot "build-tools") -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        ForEach-Object {
            @(
                (Join-Path $_.FullName "apksigner.bat"),
                (Join-Path $_.FullName "apksigner")
            )
        } |
        Where-Object { Test-Path $_ } |
        Select-Object -First 1
    $ApkSigner = $candidate
}
if (-not $ApkSigner -or -not (Test-Path $ApkSigner)) {
    throw "apksigner was not found"
}
if (-not $Aapt2) {
    $sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "D:\AndroidSDK" }
    $Aapt2 = Get-ChildItem (Join-Path $sdkRoot "build-tools") -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        ForEach-Object { @(Join-Path $_.FullName "aapt2.exe"), @(Join-Path $_.FullName "aapt2") } |
        Where-Object { Test-Path $_ } |
        Select-Object -First 1
}
if (-not $Aapt2 -or -not (Test-Path $Aapt2)) {
    throw "aapt2 was not found"
}

$seenPackages = [System.Collections.Generic.HashSet[string]]::new()
foreach ($entry in $index) {
    if (-not $seenPackages.Add([string]$entry.pkg)) {
        throw "Duplicate package in index: $($entry.pkg)"
    }
    $apkName = [string]$entry.apk
    if ([IO.Path]::GetFileName($apkName) -ne $apkName) {
        throw "Unsafe APK path in index: $apkName"
    }
    $apkPath = Join-Path $apkDir $apkName
    if (-not (Test-Path -LiteralPath $apkPath -PathType Leaf)) {
        throw "Missing APK: $apkName"
    }

    $expectedHash = [string]$entry.sha256
    $expectedSize = [long]$entry.size
    if ($expectedHash -notmatch '^[0-9a-fA-F]{64}$' -or $expectedSize -le 0) {
        throw "Missing or invalid sha256/size for $apkName"
    }
    $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $apkPath).Hash.ToLowerInvariant()
    $actualSize = (Get-Item -LiteralPath $apkPath).Length
    if ($actualHash -ne $expectedHash.ToLowerInvariant()) {
        throw "SHA-256 mismatch for $apkName"
    }
    if ($actualSize -ne $expectedSize) {
        throw "Size mismatch for $apkName (expected $expectedSize, got $actualSize)"
    }

    $badging = & $Aapt2 dump badging $apkPath 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0) { throw "Unable to read APK metadata for $apkName`n$badging" }
    $packageMatch = [regex]::Match($badging, "package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'")
    if (-not $packageMatch.Success) { throw "Invalid APK package metadata for $apkName" }
    if ($packageMatch.Groups[1].Value -ne [string]$entry.pkg) {
        throw "Package mismatch for $apkName"
    }
    if ([long]$packageMatch.Groups[2].Value -ne [long]$entry.code) {
        throw "Version code mismatch for $apkName"
    }
    if ($packageMatch.Groups[3].Value -ne [string]$entry.version) {
        throw "Version name mismatch for $apkName"
    }

    $iconPath = Join-Path (Join-Path $repoDir "icon") "$($entry.pkg).png"
    if (-not (Test-Path -LiteralPath $iconPath -PathType Leaf)) {
        throw "Missing icon for $($entry.pkg)"
    }

    $certOutput = & $ApkSigner verify --print-certs $apkPath 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0) {
        throw "APK signature verification failed for $apkName`n$certOutput"
    }
    $match = [regex]::Match($certOutput, 'certificate SHA-256 digest:\s*([0-9a-fA-F]+)')
    if (-not $match.Success -or $match.Groups[1].Value.ToLowerInvariant() -ne $signer) {
        throw "Signer mismatch for $apkName"
    }
    Write-Host "OK $apkName ($actualSize bytes, $actualHash)"
}

$seenRoles = [System.Collections.Generic.HashSet[string]]::new()
foreach ($role in @($rolesDocument.packages)) {
    $rolePackage = [string]$role.package
    if (-not $seenRoles.Add($rolePackage)) { throw "Duplicate package role: $rolePackage" }
    if (-not $seenPackages.Contains($rolePackage)) { throw "Package role is not indexed: $rolePackage" }
    if ([string]$role.role -notin @('test_fixture', 'runtime_container')) {
        throw "Unsupported non-reading package role: $($role.role)"
    }
    if ($role.excludeFromReadingHealth -ne $true) { throw "Non-reading package role must be excluded from reading health" }
}

$indexedApks = @($index | ForEach-Object { [string]$_.apk })
$orphanApks = @(Get-ChildItem -LiteralPath $apkDir -Filter "*.apk" -File | Where-Object Name -notin $indexedApks)
if ($orphanApks.Count -gt 0) { throw "Orphan APK(s): $($orphanApks.Name -join ', ')" }
$indexedIcons = @($index | ForEach-Object { "$($_.pkg).png" })
$orphanIcons = @(Get-ChildItem -LiteralPath (Join-Path $repoDir "icon") -Filter "*.png" -File | Where-Object Name -notin $indexedIcons)
if ($orphanIcons.Count -gt 0) { throw "Orphan icon(s): $($orphanIcons.Name -join ', ')" }

$healthCatalogPaths = @(Get-ChildItem -LiteralPath (Join-Path $RepoRoot "catalog") -Filter "healthy-sources-*.json" -File -ErrorAction SilentlyContinue | Sort-Object Name)
foreach ($healthCatalogFile in $healthCatalogPaths) {
    $healthCatalogPath = $healthCatalogFile.FullName
    $healthCatalog = Get-Content -LiteralPath $healthCatalogPath -Raw | ConvertFrom-Json
    $healthSources = @($healthCatalog.sources)
    $declaredTotal = if ($null -ne $healthCatalog.summary.totalUsable) {
        [int]$healthCatalog.summary.totalUsable
    } else {
        [int]$healthCatalog.summary.totalHealthy
    }
    if ($healthSources.Count -ne $declaredTotal) {
        throw "Healthy source catalogue row count mismatch: $($healthCatalogFile.Name)"
    }
    $duplicateIds = @($healthSources | Group-Object id | Where-Object Count -gt 1)
    if ($duplicateIds.Count -gt 0) {
        throw "Duplicate healthy source ID(s): $($duplicateIds.Name -join ', ')"
    }
    foreach ($source in $healthSources) {
        $isActive = [string]$source.importState -like "active_*"
        $sourcePackage = [string]$source.package
        $catalogPackage = $catalogByPackage[$sourcePackage]
        if ($isActive -and (-not $source.package -or -not $catalogPackage)) {
            throw "Active healthy source has no catalogue package: $($source.id) $($source.name)"
        }
        if ($isActive -and [string]$catalogPackage.state -eq 'published' -and -not $seenPackages.Contains($sourcePackage)) {
            throw "Active healthy source has no indexed APK: $($source.id) $($source.name)"
        }
        if (-not $isActive -and $source.validation -eq "device_verified") {
            throw "Verified healthy source is not active: $($source.id) $($source.name)"
        }
    }
    $verifiedCount = @($healthSources | Where-Object importState -eq "active_verified").Count
    $degradedCount = @($healthSources | Where-Object importState -eq "active_degraded").Count
    $pendingCount = @($healthSources | Where-Object importState -eq "active_device_pending").Count
    $missingCount = @($healthSources | Where-Object { $_.importState -like "implementation_*" }).Count
    if (
        $verifiedCount -ne [int]$healthCatalog.summary.activeVerified -or
        $degradedCount -ne [int]$healthCatalog.summary.activeDegraded -or
        $pendingCount -ne [int]$healthCatalog.summary.activeDevicePending -or
        $missingCount -ne [int]$healthCatalog.summary.implementationMissing
    ) {
        throw "Healthy source catalogue summary does not match its rows"
    }
    if ($null -ne $healthCatalog.summary.supportedManga) {
        $mangaCount = @($healthSources | Where-Object kind -eq "manga").Count
        $unsupportedCount = @($healthSources | Where-Object importState -eq "unsupported_kind").Count
        if (
            $mangaCount -ne [int]$healthCatalog.summary.supportedManga -or
            $unsupportedCount -ne [int]$healthCatalog.summary.unsupportedKind
        ) {
            throw "Healthy source catalogue kind summary does not match its rows: $($healthCatalogFile.Name)"
        }
    }
    Write-Host "$($healthCatalogFile.Name) verified: $verifiedCount verified, $degradedCount degraded, $pendingCount pending, $missingCount implementation-required"
}

$candidateCatalogPaths = @(Get-ChildItem -LiteralPath (Join-Path $RepoRoot "catalog") -Filter "source-candidates-*.json" -File -ErrorAction SilentlyContinue | Sort-Object Name)
foreach ($candidateCatalogFile in $candidateCatalogPaths) {
    $candidateCatalog = Get-Content -LiteralPath $candidateCatalogFile.FullName -Raw | ConvertFrom-Json
    foreach ($source in @($candidateCatalog.sources)) {
        if ($source.publishable -eq $true) {
            throw "Candidate catalogue must not mark a source publishable: $($source.name)"
        }
        if ([string]::IsNullOrWhiteSpace([string]$source.status) -or [string]$source.status -notlike "blocked_*") {
            throw "Candidate source must have a blocked status: $($source.name)"
        }
        $candidatePackage = [string]$source.intendedPackage
        if (-not [string]::IsNullOrWhiteSpace($candidatePackage) -and $seenPackages.Contains($candidatePackage)) {
            throw "Blocked candidate source is present in the public index: $($source.name) ($candidatePackage)"
        }
    }
    Write-Host "$($candidateCatalogFile.Name) verified: $(@($candidateCatalog.sources).Count) blocked candidate source(s) excluded from the public index"
}

$attestationPath = Join-Path (Join-Path $RepoRoot "catalog") "device-attestations.json"
if (Test-Path -LiteralPath $attestationPath) {
    $attestationDocument = Get-Content -LiteralPath $attestationPath -Raw | ConvertFrom-Json
    if ([int]$attestationDocument.schemaVersion -ne 1) { throw "Unsupported device attestation schemaVersion" }
    $allowedStates = @('active_verified', 'active_degraded', 'active_device_pending')
    foreach ($attestation in @($attestationDocument.attestations)) {
        if (-not $catalogByPackage.ContainsKey([string]$attestation.package)) {
            throw "Device attestation package is not catalogued: $($attestation.package)"
        }
        if ([string]$attestation.state -notin $allowedStates) {
            throw "Invalid device attestation state for $($attestation.package)"
        }
        if ([string]$attestation.checkedAt -notmatch '^\d{4}-\d{2}-\d{2}$') {
            throw "Invalid device attestation date for $($attestation.package)"
        }
        if ([string]::IsNullOrWhiteSpace([string]$attestation.evidence) -or @($attestation.sources).Count -eq 0) {
            throw "Device attestation lacks evidence or sources: $($attestation.package)"
        }
        if ([string]$attestation.state -eq 'active_verified') {
            $missingStages = @($policy.requiredReadingChain | Where-Object { $_ -notin @($attestation.passedStages) })
            if ($missingStages.Count -gt 0) {
                throw "Verified device attestation is missing stage(s) for $($attestation.package): $($missingStages -join ', ')"
            }
        }
    }
    Write-Host "device-attestations.json verified: $(@($attestationDocument.attestations).Count) attestation(s)"
}

$healthReportPath = Join-Path $repoDir "health.json"
if (Test-Path -LiteralPath $healthReportPath) {
    $healthReport = Get-Content -LiteralPath $healthReportPath -Raw | ConvertFrom-Json
    if ([int]$healthReport.schemaVersion -ne 1) { throw "Unsupported health.json schemaVersion" }
    if ([string]$healthReport.repository.signingKeyFingerprint -ne $signer) {
        throw "health.json signer does not match repo.json"
    }
    if (@($healthReport.packages).Count -ne @($index).Count) {
        throw "health.json package count does not match the repository index"
    }
    if ([int]$healthReport.audit.ageDays -gt [int]$policy.audit.publishableWithinDays) {
        throw "Health audit is too old to publish: $($healthReport.audit.ageDays) day(s)"
    }
    $requiredChain = @($policy.requiredReadingChain)
    $reportedChain = @($healthReport.audit.requiredReadingChain)
    $missingStages = @($requiredChain | Where-Object { $_ -notin $reportedChain })
    if ($missingStages.Count -gt 0) {
        throw "health.json is missing required reading-chain stage(s): $($missingStages -join ', ')"
    }
    $healthPackages = @($healthReport.packages | ForEach-Object { [string]$_.package })
    $missingHealthPackages = @($seenPackages | Where-Object { $_ -notin $healthPackages })
    if ($missingHealthPackages.Count -gt 0) {
        throw "health.json is missing package(s): $($missingHealthPackages -join ', ')"
    }
    Write-Host "health.json verified: $(@($healthReport.packages).Count) package status record(s)"
}

Write-Host "Repository integrity verified: $($index.Count) APK(s), signer $signer"
