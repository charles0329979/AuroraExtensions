<#
.SYNOPSIS
  Maintain the semantic source catalogue and its generated repository files.

.DESCRIPTION
  catalog/sources.yaml intentionally uses JSON syntax, which is valid YAML 1.2.
  This keeps local and CI maintenance dependency-free while preserving one
  human-editable source of truth.
#>
[CmdletBinding()]
param(
    [string]$RepoRoot = "",
    [ValidateSet("Check", "WriteDerived", "ImportCurrentIndex")]
    [string]$Mode = "Check"
)

$ErrorActionPreference = "Stop"
if (-not $RepoRoot) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}

$catalogPath = Join-Path $RepoRoot "catalog\sources.yaml"
$schemaPath = Join-Path $RepoRoot "catalog\sources.schema.json"
$indexPath = Join-Path $RepoRoot "repo\index.min.json"
$fullIndexPath = Join-Path $RepoRoot "repo\index.json"
$testingIndexPath = Join-Path $RepoRoot "repo-testing\index.min.json"
$testingFullIndexPath = Join-Path $RepoRoot "repo-testing\index.json"
$testingRepoMetaPath = Join-Path $RepoRoot "repo-testing\repo.json"
$rolesPath = Join-Path $RepoRoot "maintenance\package-roles.json"
$buildPlanPath = Join-Path $RepoRoot "maintenance\build-plan.json"
$utf8 = [Text.UTF8Encoding]::new($false)

function Write-JsonDocument {
    param([string]$Path, [object]$Value)
    $parent = Split-Path -Parent $Path
    New-Item -ItemType Directory -Force -Path $parent | Out-Null
    $json = if ($null -eq $Value) { "[]" } else { $Value | ConvertTo-Json -Depth 20 }
    [IO.File]::WriteAllText($Path, $json + "`n", $utf8)
}

function Write-JsonArrayDocument {
    param([string]$Path, [AllowEmptyCollection()][object[]]$Value)
    $parent = Split-Path -Parent $Path
    New-Item -ItemType Directory -Force -Path $parent | Out-Null
    $json = ConvertTo-Json -InputObject @($Value) -Depth 20
    [IO.File]::WriteAllText($Path, $json + "`n", $utf8)
}

function Get-ModuleForPackage {
    param([string]$Package)
    $slug = $Package.Split('.')[-1]
    if ($slug -eq "aurorastub") { return "extensions/aurorastub" }
    if ($slug -eq "aurorascripted") { return "extensions/aurorascripted" }
    if ($slug -eq "mangadex") { return "vendor/extensions-source/src/all/mangadex" }
    $custom = Join-Path $RepoRoot "extensions\$slug"
    if (Test-Path -LiteralPath $custom -PathType Container) { return "extensions/$slug" }
    $language = $Package.Split('.')[-2]
    return "vendor/batch-extensions-source/src/$language/$slug"
}

function Import-CurrentIndex {
    if (Test-Path -LiteralPath $catalogPath) {
        throw "Refusing to overwrite existing source catalogue: $catalogPath"
    }
    $index = @(Get-Content -LiteralPath $indexPath -Raw | ConvertFrom-Json)
    $rolesByPackage = @{}
    if (Test-Path -LiteralPath $rolesPath) {
        $roles = Get-Content -LiteralPath $rolesPath -Raw | ConvertFrom-Json
        foreach ($item in @($roles.packages)) { $rolesByPackage[[string]$item.package] = [string]$item.role }
    }
    $packages = foreach ($entry in $index) {
        $role = $rolesByPackage[[string]$entry.pkg]
        if (-not $role) { $role = "reading_source" }
        $implementation = switch ($role) {
            "test_fixture" { "test_fixture" }
            "runtime_container" { "scripted_runtime" }
            default { "native" }
        }
        [ordered]@{
            package = [string]$entry.pkg
            displayName = [string]$entry.name
            language = [string]$entry.lang
            versionCode = [long]$entry.code
            versionName = [string]$entry.version
            apk = [string]$entry.apk
            icon = "$($entry.pkg).png"
            module = Get-ModuleForPackage ([string]$entry.pkg)
            implementation = $implementation
            role = $role
            channel = "stable"
            state = "published"
            nsfw = [int]$entry.nsfw
            sources = @($entry.sources | ForEach-Object {
                [ordered]@{
                    id = [long]$_.id
                    language = [string]$_.lang
                    name = [string]$_.name
                    baseUrl = [string]$_.baseUrl
                }
            })
        }
    }
    $document = [ordered]@{
        schemaVersion = 1
        repository = [ordered]@{ name = "Aurora Extensions"; defaultChannel = "stable" }
        packages = @($packages)
    }
    Write-JsonDocument -Path $catalogPath -Value $document
    Write-Host "Imported $(@($packages).Count) packages into catalog/sources.yaml"
}

function Read-AndValidateCatalog {
    if (-not (Test-Path -LiteralPath $catalogPath -PathType Leaf)) {
        throw "Missing source catalogue: $catalogPath"
    }
    try {
        $raw = Get-Content -LiteralPath $catalogPath -Raw
        if (-not ($raw | Test-Json -SchemaFile $schemaPath -ErrorAction Stop)) {
            throw "catalog/sources.yaml does not satisfy catalog/sources.schema.json"
        }
        $document = $raw | ConvertFrom-Json
    } catch {
        throw "catalog/sources.yaml must be JSON-compatible YAML: $($_.Exception.Message)"
    }
    if ([int]$document.schemaVersion -ne 1) { throw "Unsupported source catalogue schemaVersion" }
    if ([string]::IsNullOrWhiteSpace([string]$document.repository.name)) { throw "Repository name is required" }
    if ([string]$document.repository.defaultChannel -ne "stable") { throw "defaultChannel must be stable" }

    $packages = @($document.packages)
    if ($packages.Count -eq 0) { throw "Source catalogue must contain packages" }
    $seenPackages = [Collections.Generic.HashSet[string]]::new()
    $seenApks = [Collections.Generic.HashSet[string]]::new()
    $seenSourceIds = [Collections.Generic.HashSet[long]]::new()
    foreach ($package in $packages) {
        $pkg = [string]$package.package
        if ($pkg -notmatch '^eu\.kanade\.tachiyomi\.extension\.[a-z0-9_.]+$') { throw "Invalid package: $pkg" }
        if (-not $seenPackages.Add($pkg)) { throw "Duplicate package: $pkg" }
        if ([string]::IsNullOrWhiteSpace([string]$package.displayName)) { throw "Missing displayName: $pkg" }
        if ([long]$package.versionCode -le 0 -or [string]$package.versionName -notmatch '^\d+\.\d+\.\d+$') {
            throw "Invalid version metadata: $pkg"
        }
        $apk = [string]$package.apk
        if ([IO.Path]::GetFileName($apk) -ne $apk -or $apk -notmatch '\.apk$') { throw "Invalid APK name: $pkg" }
        if (-not $seenApks.Add($apk)) { throw "Duplicate APK name: $apk" }
        if ([string]$package.icon -ne "$pkg.png") { throw "Icon must be named $pkg.png" }
        if ([string]$package.implementation -notin @('native', 'scripted_runtime', 'test_fixture')) {
            throw "Invalid implementation: $pkg"
        }
        if ([string]$package.role -notin @('reading_source', 'runtime_container', 'test_fixture')) { throw "Invalid role: $pkg" }
        if ([string]$package.channel -notin @('stable', 'testing')) { throw "Invalid channel: $pkg" }
        if ([string]$package.state -notin @('published', 'quarantined', 'retired')) { throw "Invalid state: $pkg" }
        if ([int]$package.nsfw -notin 0, 1, 2) { throw "Invalid nsfw value: $pkg" }
        if (@($package.sources).Count -eq 0) { throw "Package has no declared sources: $pkg" }
        foreach ($source in @($package.sources)) {
            $sourceId = [long]$source.id
            if ($sourceId -le 0 -or -not $seenSourceIds.Add($sourceId)) { throw "Invalid or duplicate source ID: $sourceId" }
            if ([string]::IsNullOrWhiteSpace([string]$source.name)) { throw "Source name is required: $pkg" }
            if ([string]$source.baseUrl -notmatch '^https?://') { throw "Invalid source URL: $pkg/$($source.name)" }
        }
    }
    return $document
}

function Get-ChannelIndex {
    param([object]$Catalog, [string]$Channel, [string]$AssetRoot)
    $entries = foreach ($package in @($Catalog.packages)) {
        if ([string]$package.channel -ne $Channel -or [string]$package.state -ne 'published') { continue }
        $apkPath = Join-Path $AssetRoot "apk\$($package.apk)"
        $iconPath = Join-Path $AssetRoot "icon\$($package.icon)"
        if (-not (Test-Path -LiteralPath $apkPath -PathType Leaf)) { throw "Missing APK: $($package.apk)" }
        if (-not (Test-Path -LiteralPath $iconPath -PathType Leaf)) { throw "Missing icon: $($package.icon)" }
        $file = Get-Item -LiteralPath $apkPath
        [ordered]@{
            name = [string]$package.displayName
            pkg = [string]$package.package
            apk = [string]$package.apk
            sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $apkPath).Hash.ToLowerInvariant()
            size = [long]$file.Length
            lang = [string]$package.language
            code = [long]$package.versionCode
            version = [string]$package.versionName
            nsfw = [int]$package.nsfw
            sources = @($package.sources | ForEach-Object {
                [ordered]@{
                    id = [long]$_.id
                    lang = [string]$_.language
                    name = [string]$_.name
                    baseUrl = [string]$_.baseUrl
                }
            })
        }
    }
    return @($entries)
}

function Get-TestingRepoMetadata {
    $stableMetadata = Get-Content -LiteralPath (Join-Path $RepoRoot "repo\repo.json") -Raw | ConvertFrom-Json
    return [ordered]@{
        meta = [ordered]@{
            name = "$($stableMetadata.meta.name) Testing"
            shortName = "$($stableMetadata.meta.shortName) Testing"
            website = [string]$stableMetadata.meta.website
            signingKeyFingerprint = [string]$stableMetadata.meta.signingKeyFingerprint
        }
    }
}

function Assert-ChannelAssets {
    param([string]$AssetRoot, [object[]]$Index)
    foreach ($kind in @('apk', 'icon')) {
        $directory = Join-Path $AssetRoot $kind
        New-Item -ItemType Directory -Force -Path $directory | Out-Null
        $expected = if ($kind -eq 'apk') { @($Index.apk) } else { @($Index.pkg | ForEach-Object { "$_.png" }) }
        $pattern = if ($kind -eq 'apk') { '*.apk' } else { '*.png' }
        $unexpected = @(Get-ChildItem -LiteralPath $directory -Filter $pattern -File | Where-Object Name -notin $expected)
        if ($unexpected.Count -gt 0) { throw "Unexpected $kind asset(s) in $AssetRoot`: $($unexpected.Name -join ', ')" }
    }
}

function Get-PackageRoles {
    param([object]$Catalog)
    $packages = @($Catalog.packages | Where-Object role -ne 'reading_source' | ForEach-Object {
        [ordered]@{
            package = [string]$_.package
            role = [string]$_.role
            excludeFromReadingHealth = $true
        }
    })
    return [ordered]@{ schemaVersion = 1; packages = $packages }
}

function Get-BuildPlan {
    param([object]$Catalog)
    $packages = foreach ($package in @($Catalog.packages)) {
        $slug = ([string]$package.package).Split('.')[-1]
        $builder = if ($slug -in @('aurorastub', 'aurorascripted')) {
            'standalone'
        } elseif ($slug -eq 'mangadex') {
            'vendor_mangadex'
        } else {
            'vendor_batch'
        }
        $gradleModule = if ($builder -eq 'vendor_batch') {
            "src/$($package.language)/$slug"
        } elseif ($builder -eq 'vendor_mangadex') {
            'src/all/mangadex'
        } else {
            [string]$package.module
        }
        [ordered]@{
            package = [string]$package.package
            builder = $builder
            ownerModule = [string]$package.module
            gradleModule = $gradleModule
            versionCode = [long]$package.versionCode
            versionName = [string]$package.versionName
            apk = [string]$package.apk
            channel = [string]$package.channel
            state = [string]$package.state
        }
    }
    return [ordered]@{ schemaVersion = 1; packages = @($packages) }
}

if ($Mode -eq "ImportCurrentIndex") {
    Import-CurrentIndex
    $Mode = "WriteDerived"
}

$catalog = Read-AndValidateCatalog
$expectedIndex = @(Get-ChannelIndex -Catalog $catalog -Channel stable -AssetRoot (Join-Path $RepoRoot 'repo'))
$expectedTestingIndex = @(Get-ChannelIndex -Catalog $catalog -Channel testing -AssetRoot (Join-Path $RepoRoot 'repo-testing'))
$expectedRoles = Get-PackageRoles -Catalog $catalog
$expectedBuildPlan = Get-BuildPlan -Catalog $catalog

if ($Mode -eq "WriteDerived") {
    Write-JsonArrayDocument -Path $indexPath -Value $expectedIndex
    Write-JsonArrayDocument -Path $fullIndexPath -Value $expectedIndex
    Write-JsonArrayDocument -Path $testingIndexPath -Value $expectedTestingIndex
    Write-JsonArrayDocument -Path $testingFullIndexPath -Value $expectedTestingIndex
    Write-JsonDocument -Path $testingRepoMetaPath -Value (Get-TestingRepoMetadata)
    Write-JsonDocument -Path $rolesPath -Value $expectedRoles
    Write-JsonDocument -Path $buildPlanPath -Value $expectedBuildPlan
    Assert-ChannelAssets -AssetRoot (Join-Path $RepoRoot 'repo-testing') -Index $expectedTestingIndex
    Write-Host "Generated repository index and package roles from $(@($catalog.packages).Count) catalogue packages"
} else {
    $actualIndex = @(Get-Content -LiteralPath $indexPath -Raw | ConvertFrom-Json)
    $actualFullIndex = @(Get-Content -LiteralPath $fullIndexPath -Raw | ConvertFrom-Json)
    $actualTestingIndex = @(Get-Content -LiteralPath $testingIndexPath -Raw | ConvertFrom-Json)
    $actualTestingFullIndex = @(Get-Content -LiteralPath $testingFullIndexPath -Raw | ConvertFrom-Json)
    $actualRoles = Get-Content -LiteralPath $rolesPath -Raw | ConvertFrom-Json
    $actualBuildPlan = Get-Content -LiteralPath $buildPlanPath -Raw | ConvertFrom-Json
    $expectedIndexJson = ConvertTo-Json -InputObject @($expectedIndex) -Depth 20 -Compress
    if ((ConvertTo-Json -InputObject @($actualIndex) -Depth 20 -Compress) -ne $expectedIndexJson) {
        throw "repo/index.min.json drifted from catalog/sources.yaml; run sync-source-catalog.ps1 -Mode WriteDerived"
    }
    if ((ConvertTo-Json -InputObject @($actualFullIndex) -Depth 20 -Compress) -ne $expectedIndexJson) {
        throw "repo/index.json drifted from catalog/sources.yaml; run sync-source-catalog.ps1 -Mode WriteDerived"
    }
    $expectedTestingJson = ConvertTo-Json -InputObject @($expectedTestingIndex) -Depth 20 -Compress
    if ((ConvertTo-Json -InputObject @($actualTestingIndex) -Depth 20 -Compress) -ne $expectedTestingJson -or
        (ConvertTo-Json -InputObject @($actualTestingFullIndex) -Depth 20 -Compress) -ne $expectedTestingJson) {
        throw "repo-testing index drifted from catalog/sources.yaml"
    }
    if (($actualRoles | ConvertTo-Json -Depth 10 -Compress) -ne ($expectedRoles | ConvertTo-Json -Depth 10 -Compress)) {
        throw "maintenance/package-roles.json drifted from catalog/sources.yaml"
    }
    if (($actualBuildPlan | ConvertTo-Json -Depth 10 -Compress) -ne ($expectedBuildPlan | ConvertTo-Json -Depth 10 -Compress)) {
        throw "maintenance/build-plan.json drifted from catalog/sources.yaml"
    }
    Assert-ChannelAssets -AssetRoot (Join-Path $RepoRoot 'repo-testing') -Index $expectedTestingIndex
    Write-Host "Source catalogue verified: $(@($catalog.packages).Count) packages, $($expectedIndex.Count) stable and $($expectedTestingIndex.Count) testing publications"
}
