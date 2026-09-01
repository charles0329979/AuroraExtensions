<#
.SYNOPSIS
  One command for rebuilding (optional), reporting, and fail-closed verification.
#>
[CmdletBinding()]
param(
    [string]$RepoRoot = "",
    [switch]$SkipBuild,
    [switch]$VerifyOnly,
    [switch]$AllowSigningKeyRotation
)

$ErrorActionPreference = "Stop"
if (-not $RepoRoot) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}
if (-not $env:JAVA_HOME -and (Test-Path "D:\Android\jbr")) {
    $env:JAVA_HOME = "D:\Android\jbr"
}
if (-not $env:ANDROID_HOME -and (Test-Path "D:\AndroidSDK")) {
    $env:ANDROID_HOME = "D:\AndroidSDK"
}

if (-not $VerifyOnly) {
    $generateArgs = @{ RepoRoot = $RepoRoot }
    if ($SkipBuild) { $generateArgs.SkipBuild = $true }
    if ($AllowSigningKeyRotation) { $generateArgs.AllowSigningKeyRotation = $true }
    & (Join-Path $PSScriptRoot "generate-repo.ps1") @generateArgs
    if (-not $?) { throw "Repository generation failed" }
}

& (Join-Path $PSScriptRoot "sync-source-catalog.ps1") -RepoRoot $RepoRoot -Mode Check
if (-not $?) { throw "Source catalogue verification failed" }

& (Join-Path $PSScriptRoot "generate-maintenance-report.ps1") -RepoRoot $RepoRoot
if (-not $?) { throw "Maintenance report generation failed" }

& (Join-Path $PSScriptRoot "verify-repo-integrity.ps1") -RepoRoot $RepoRoot
if (-not $?) { throw "Repository verification failed" }

Write-Host "Maintenance gate passed"
