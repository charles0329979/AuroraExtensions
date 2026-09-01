<#
.SYNOPSIS
  Add, update, quarantine, restore, or inspect one extension package.
#>
[CmdletBinding(SupportsShouldProcess, ConfirmImpact = "High")]
param(
    [Parameter(Mandatory)]
    [ValidateSet("Status", "Add", "Update", "Quarantine", "Restore")]
    [string]$Action,
    [string]$Package = "",
    [string]$Reason = "",
    [string]$DefinitionPath = "",
    [string]$ArtifactPath = "",
    [string]$IconPath = "",
    [switch]$Publish,
    [switch]$AllowIdentityChange,
    [string]$RepoRoot = ""
)

$ErrorActionPreference = "Stop"
if (-not $RepoRoot) { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path }
if (-not $env:JAVA_HOME -and (Test-Path "D:\Android\jbr")) { $env:JAVA_HOME = "D:\Android\jbr" }
if (-not $env:ANDROID_HOME -and (Test-Path "D:\AndroidSDK")) { $env:ANDROID_HOME = "D:\AndroidSDK" }
$catalogPath = Join-Path $RepoRoot "catalog\sources.yaml"
$schemaPath = Join-Path $RepoRoot "catalog\sources.schema.json"
$syncScript = Join-Path $PSScriptRoot "sync-source-catalog.ps1"
$utf8 = [Text.UTF8Encoding]::new($false)

function Read-Catalog {
    $raw = Get-Content -LiteralPath $catalogPath -Raw
    if (-not ($raw | Test-Json -SchemaFile $schemaPath -ErrorAction Stop)) { throw "Invalid source catalogue" }
    return [pscustomobject]@{ Raw = $raw; Value = ($raw | ConvertFrom-Json) }
}

function Write-Catalog {
    param([object]$Catalog)
    $json = ($Catalog | ConvertTo-Json -Depth 20) + "`n"
    if (-not ($json | Test-Json -SchemaFile $schemaPath -ErrorAction Stop)) { throw "Updated source catalogue is invalid" }
    $temporary = "$catalogPath.tmp"
    [IO.File]::WriteAllText($temporary, $json, $utf8)
    Move-Item -LiteralPath $temporary -Destination $catalogPath -Force
}

function Get-AssetPaths {
    param([object]$Entry, [bool]$Quarantined)
    $root = if ($Quarantined) {
        Join-Path $RepoRoot "quarantine\$($Entry.channel)"
    } elseif ([string]$Entry.channel -eq "stable") {
        Join-Path $RepoRoot "repo"
    } else {
        Join-Path $RepoRoot "repo-testing"
    }
    return [pscustomobject]@{
        Apk = Join-Path $root "apk\$($Entry.apk)"
        Icon = Join-Path $root "icon\$($Entry.icon)"
    }
}

function Resolve-BuildTool {
    param([string]$Name)
    $sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "D:\AndroidSDK" }
    $candidate = Get-ChildItem (Join-Path $sdkRoot "build-tools") -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        ForEach-Object { @(
            (Join-Path $_.FullName "$Name.exe"),
            (Join-Path $_.FullName "$Name.bat"),
            (Join-Path $_.FullName $Name)
        ) } |
        Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } |
        Select-Object -First 1
    if (-not $candidate) { throw "$Name was not found under $sdkRoot" }
    return $candidate
}

function Test-Artifact {
    param([object]$Definition)
    foreach ($path in @($ArtifactPath, $IconPath)) {
        if ([string]::IsNullOrWhiteSpace($path) -or -not (Test-Path -LiteralPath $path -PathType Leaf)) {
            throw "A valid -ArtifactPath and -IconPath are required"
        }
    }
    $aapt2 = Resolve-BuildTool "aapt2"
    $badging = & $aapt2 dump badging $ArtifactPath 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0) { throw "Unable to read APK metadata" }
    $match = [regex]::Match($badging, "package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'")
    if (-not $match.Success -or
        $match.Groups[1].Value -ne [string]$Definition.package -or
        [long]$match.Groups[2].Value -ne [long]$Definition.versionCode -or
        $match.Groups[3].Value -ne [string]$Definition.versionName
    ) {
        throw "APK package/version metadata does not match the source definition"
    }

    $apkSigner = Resolve-BuildTool "apksigner"
    $certificate = & $apkSigner verify --print-certs $ArtifactPath 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed" }
    $signerMatch = [regex]::Match($certificate, 'certificate SHA-256 digest:\s*([0-9a-fA-F]+)')
    $expectedSigner = [string](Get-Content (Join-Path $RepoRoot "repo\repo.json") -Raw | ConvertFrom-Json).meta.signingKeyFingerprint
    if (-not $signerMatch.Success -or $signerMatch.Groups[1].Value.ToLowerInvariant() -ne $expectedSigner.ToLowerInvariant()) {
        throw "APK signer does not match the repository signing identity"
    }

    $pngSignature = [byte[]](137, 80, 78, 71, 13, 10, 26, 10)
    $iconBytes = [IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $IconPath).Path)
    if ($iconBytes.Length -le $pngSignature.Length) { throw "Icon is empty or truncated" }
    for ($i = 0; $i -lt $pngSignature.Length; $i++) {
        if ($iconBytes[$i] -ne $pngSignature[$i]) { throw "Icon is not a valid PNG file" }
    }
}

function Invoke-AddOrUpdate {
    param([object]$CatalogDocument)
    if ([string]::IsNullOrWhiteSpace($DefinitionPath) -or -not (Test-Path -LiteralPath $DefinitionPath -PathType Leaf)) {
        throw "-DefinitionPath is required for $Action"
    }
    $definition = Get-Content -LiteralPath $DefinitionPath -Raw | ConvertFrom-Json
    $definitionPackage = [string]$definition.package
    if ($Package -and $Package -ne $definitionPackage) { throw "-Package does not match the definition" }
    $script:Package = $definitionPackage
    $existing = @($CatalogDocument.Value.packages | Where-Object package -eq $definitionPackage)

    if ($Action -eq "Add") {
        if ($existing.Count -ne 0) { throw "Package already exists: $definitionPackage" }
        if ([string]$definition.channel -ne "testing" -or [string]$definition.state -ne "published") {
            throw "New packages must enter as testing/published"
        }
    } else {
        if ($existing.Count -ne 1) { throw "Package was not found or is duplicated: $definitionPackage" }
        if ([string]$existing[0].state -ne "published") { throw "Update requires a published package" }
        if ([long]$definition.versionCode -le [long]$existing[0].versionCode) {
            throw "Update versionCode must be greater than the installed catalogue version"
        }
        $removedSourceIds = @($existing[0].sources.id | Where-Object { $_ -notin @($definition.sources.id) })
        if ($removedSourceIds.Count -gt 0 -and -not $AllowIdentityChange) {
            throw "Update removes stable source IDs: $($removedSourceIds -join ', '); use -AllowIdentityChange only with a migration plan"
        }
    }
    if ([string]$definition.channel -eq "stable" -and -not $Publish) {
        throw "Publishing to stable requires the explicit -Publish switch"
    }
    Test-Artifact -Definition $definition

    $destination = Get-AssetPaths -Entry $definition -Quarantined $false
    $apkBytes = [IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $ArtifactPath).Path)
    $iconBytes = [IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $IconPath).Path)
    $archiveMoves = @()
    $written = @()
    $operation = "$Action $definitionPackage $($definition.versionName) to $($definition.channel)"
    if (-not $PSCmdlet.ShouldProcess($definitionPackage, $operation)) { return }

    try {
        if ($Action -eq "Update") {
            $oldEntry = $existing[0]
            $oldPaths = Get-AssetPaths -Entry $oldEntry -Quarantined $false
            $archiveRoot = Join-Path $RepoRoot "archive\$($oldEntry.channel)\$definitionPackage\$($oldEntry.versionName)"
            foreach ($pair in @(
                [pscustomobject]@{ Source = $oldPaths.Apk; Destination = Join-Path $archiveRoot "apk\$($oldEntry.apk)" },
                [pscustomobject]@{ Source = $oldPaths.Icon; Destination = Join-Path $archiveRoot "icon\$($oldEntry.icon)" }
            )) {
                if (-not (Test-Path -LiteralPath $pair.Source -PathType Leaf)) { throw "Current asset is missing: $($pair.Source)" }
                if (Test-Path -LiteralPath $pair.Destination) { throw "Archive version already exists: $($pair.Destination)" }
                New-Item -ItemType Directory -Force -Path (Split-Path -Parent $pair.Destination) | Out-Null
                Move-Item -LiteralPath $pair.Source -Destination $pair.Destination
                $archiveMoves += $pair
            }
        } elseif ((Test-Path -LiteralPath $destination.Apk) -or (Test-Path -LiteralPath $destination.Icon)) {
            throw "Destination assets already exist for $definitionPackage"
        }

        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination.Apk), (Split-Path -Parent $destination.Icon) | Out-Null
        [IO.File]::WriteAllBytes($destination.Apk, $apkBytes)
        $written += $destination.Apk
        [IO.File]::WriteAllBytes($destination.Icon, $iconBytes)
        $written += $destination.Icon

        if ($Action -eq "Add") {
            $CatalogDocument.Value.packages = @($CatalogDocument.Value.packages) + $definition
        } else {
            for ($i = 0; $i -lt @($CatalogDocument.Value.packages).Count; $i++) {
                if ([string]$CatalogDocument.Value.packages[$i].package -eq $definitionPackage) {
                    $CatalogDocument.Value.packages[$i] = $definition
                    break
                }
            }
        }
        Write-Catalog -Catalog $CatalogDocument.Value
        & $syncScript -RepoRoot $RepoRoot -Mode WriteDerived
        if (-not $?) { throw "Unable to regenerate repository metadata" }
        Write-Host "$operation completed"
    } catch {
        [IO.File]::WriteAllText($catalogPath, $CatalogDocument.Raw, $utf8)
        foreach ($path in $written) { if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Force } }
        foreach ($pair in @($archiveMoves)) {
            if (Test-Path -LiteralPath $pair.Destination) {
                New-Item -ItemType Directory -Force -Path (Split-Path -Parent $pair.Source) | Out-Null
                Move-Item -LiteralPath $pair.Destination -Destination $pair.Source -Force
            }
        }
        & $syncScript -RepoRoot $RepoRoot -Mode WriteDerived | Out-Null
        throw
    }
}

$catalog = Read-Catalog
if ($Action -eq "Status") {
    @($catalog.Value.packages) |
        Where-Object { -not $Package -or [string]$_.package -eq $Package } |
        Select-Object package, versionName, channel, state, statusReason
    return
}

if ($Action -in @("Add", "Update")) {
    Invoke-AddOrUpdate -CatalogDocument $catalog
    return
}

if ([string]::IsNullOrWhiteSpace($Package)) { throw "-Package is required for $Action" }
$entry = @($catalog.Value.packages | Where-Object package -eq $Package)
if ($entry.Count -ne 1) { throw "Package was not found or is duplicated: $Package" }
$entry = $entry[0]
$fromQuarantine = $Action -eq "Restore"
$sourcePaths = Get-AssetPaths -Entry $entry -Quarantined $fromQuarantine
$destinationPaths = Get-AssetPaths -Entry $entry -Quarantined (-not $fromQuarantine)

if ($Action -eq "Quarantine") {
    if ([string]$entry.state -ne "published") { throw "Only a published package can be quarantined" }
    if ([string]::IsNullOrWhiteSpace($Reason)) { throw "-Reason is required when quarantining a package" }
} elseif ([string]$entry.state -ne "quarantined") {
    throw "Only a quarantined package can be restored"
}
foreach ($path in @($sourcePaths.Apk, $sourcePaths.Icon)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required asset is missing: $path" }
}

$operation = "$Action $Package"
if (-not $PSCmdlet.ShouldProcess($Package, $operation)) { return }

$moved = @()
try {
    foreach ($pair in @(
        [pscustomobject]@{ Source = $sourcePaths.Apk; Destination = $destinationPaths.Apk },
        [pscustomobject]@{ Source = $sourcePaths.Icon; Destination = $destinationPaths.Icon }
    )) {
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $pair.Destination) | Out-Null
        Move-Item -LiteralPath $pair.Source -Destination $pair.Destination
        $moved += $pair
    }
    $entry.state = if ($Action -eq "Quarantine") { "quarantined" } else { "published" }
    if ($entry.PSObject.Properties.Name -notcontains "statusReason") {
        $entry | Add-Member -NotePropertyName statusReason -NotePropertyValue $null
        $entry | Add-Member -NotePropertyName statusChangedAt -NotePropertyValue $null
    }
    $entry.statusReason = if ($Action -eq "Quarantine") { $Reason.Trim() } else { $null }
    $entry.statusChangedAt = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    Write-Catalog -Catalog $catalog.Value
    & $syncScript -RepoRoot $RepoRoot -Mode WriteDerived
    if (-not $?) { throw "Unable to regenerate repository metadata" }
    Write-Host "$operation completed"
} catch {
    [IO.File]::WriteAllText($catalogPath, $catalog.Raw, $utf8)
    foreach ($pair in @($moved) | Sort-Object -Descending) {
        if (Test-Path -LiteralPath $pair.Destination) {
            New-Item -ItemType Directory -Force -Path (Split-Path -Parent $pair.Source) | Out-Null
            Move-Item -LiteralPath $pair.Destination -Destination $pair.Source -Force
        }
    }
    & $syncScript -RepoRoot $RepoRoot -Mode WriteDerived | Out-Null
    throw
}
