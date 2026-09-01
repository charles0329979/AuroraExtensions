<#
.SYNOPSIS
  Read-only check for the remaining GitHub publication prerequisites.
#>
[CmdletBinding()]
param(
    [string]$Repository = "charles0329979/AuroraExtensions",
    [string]$StableIndexUrl = "https://charles0329979.github.io/AuroraExtensions/index.min.json",
    [string]$TestingIndexUrl = "https://charles0329979.github.io/AuroraExtensions/testing/index.min.json",
    [switch]$SkipLocalGate
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$blockers = [Collections.Generic.List[string]]::new()

function Get-CatalogueIdentity {
    param([object[]]$Entries)
    return @($Entries | ForEach-Object {
        $hash = if ($_.sha256) { ([string]$_.sha256).ToLowerInvariant() } else { "missing-sha256" }
        "$($_.pkg)|$($_.code)|$hash"
    } | Sort-Object)
}

if (-not $SkipLocalGate) {
    try {
        & (Join-Path $PSScriptRoot "maintain-repo.ps1") -RepoRoot $repoRoot -VerifyOnly
    } catch {
        $blockers.Add("Local maintenance gate failed: $($_.Exception.Message)")
    }
}

$gh = Get-Command gh -ErrorAction SilentlyContinue
if (-not $gh) {
    $blockers.Add("GitHub CLI is not installed; repository settings could not be checked")
} else {
    & $gh.Source auth status 1>$null 2>$null
    if ($LASTEXITCODE -ne 0) {
        $blockers.Add("GitHub CLI is not authenticated")
    } else {
        $requiredSecrets = @(
            "AURORA_EXTENSION_KEYSTORE_BASE64",
            "AURORA_EXTENSION_STORE_PASSWORD",
            "AURORA_EXTENSION_KEY_ALIAS",
            "AURORA_EXTENSION_KEY_PASSWORD",
            "AURORA_EXTENSION_SIGNING_FINGERPRINT"
        )
        $secretJson = & $gh.Source secret list --repo $Repository --json name 2>$null | Out-String
        if ($LASTEXITCODE -ne 0) {
            $blockers.Add("Unable to list GitHub Actions secrets for $Repository")
        } else {
            $configured = @($secretJson | ConvertFrom-Json | ForEach-Object { $_.name })
            foreach ($name in $requiredSecrets) {
                if ($name -notin $configured) { $blockers.Add("Missing GitHub Actions secret: $name") }
            }
        }

        & $gh.Source api "repos/$Repository/pages" 1>$null 2>$null
        if ($LASTEXITCODE -ne 0) {
            $blockers.Add("GitHub Pages is not configured for $Repository")
        }
    }
}

foreach ($publication in @(
    @{ Name = "stable"; Url = $StableIndexUrl; LocalPath = (Join-Path $repoRoot "repo\index.min.json") },
    @{ Name = "testing"; Url = $TestingIndexUrl; LocalPath = (Join-Path $repoRoot "repo-testing\index.min.json") }
)) {
    try {
        $response = Invoke-WebRequest -Uri $publication.Url -Method Get -TimeoutSec 20
        if ($response.StatusCode -ne 200) { throw "HTTP $($response.StatusCode)" }
        $entries = @($response.Content | ConvertFrom-Json)
        $localEntries = @(Get-Content -LiteralPath $publication.LocalPath -Raw | ConvertFrom-Json)
        $difference = @(Compare-Object `
            (Get-CatalogueIdentity -Entries $localEntries) `
            (Get-CatalogueIdentity -Entries $entries))
        if ($difference.Count -gt 0) {
            throw "published catalogue does not match the local package/version/hash set"
        }
    } catch {
        $blockers.Add("Published $($publication.Name) catalogue is not current: $($publication.Url) ($($_.Exception.Message))")
    }
}

if ($blockers.Count -gt 0) {
    Write-Host "Release readiness: BLOCKED ($($blockers.Count) item(s))"
    $blockers | ForEach-Object { Write-Host "- $_" }
    exit 1
}

Write-Host "Release readiness: READY"
