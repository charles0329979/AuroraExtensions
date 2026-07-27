<#
.SYNOPSIS
  Extract SHA-256 fingerprint of an Android signing keystore certificate.
#>
[CmdletBinding()]
param(
    [string]$Keystore = "",
    [string]$StorePass = "android",
    [string]$Alias = "androiddebugkey",
    [string]$Keytool = ""
)

$ErrorActionPreference = "Stop"

if (-not $Keystore) {
    $homeDir = if ($env:USERPROFILE) { $env:USERPROFILE } elseif ($env:HOME) { $env:HOME } else { $HOME }
    $Keystore = Join-Path $homeDir ".android/debug.keystore"
}

if (-not (Test-Path $Keystore)) {
    throw "Keystore not found: $Keystore"
}

if (-not $Keytool) {
    $candidates = @(
        (Join-Path $env:JAVA_HOME "bin/keytool"),
        (Join-Path $env:JAVA_HOME "bin/keytool.exe"),
        "D:\Android\jbr\bin\keytool.exe",
        "keytool"
    )
    foreach ($c in $candidates) {
        if ($c -eq "keytool") {
            $Keytool = $c
            break
        }
        if ($c -and (Test-Path $c)) {
            $Keytool = $c
            break
        }
    }
}

$out = & $Keytool -list -v -keystore $Keystore -storepass $StorePass -alias $Alias 2>&1 | Out-String
$m = [regex]::Match($out, "SHA256:\s*([0-9A-Fa-f:]+)")
if (-not $m.Success) {
    throw "Could not parse SHA-256 from keytool output.`n$out"
}

$fingerprint = ($m.Groups[1].Value -replace ":", "").ToLowerInvariant()
if ($fingerprint.Length -ne 64) {
    throw "Unexpected fingerprint length $($fingerprint.Length): $fingerprint"
}

Write-Output $fingerprint