<#
.SYNOPSIS
  Verify the production keystore, backup copy, alias, password, and fingerprint.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$PrimaryKeystore,
    [Parameter(Mandatory)]
    [string]$BackupKeystore,
    [string]$Alias = "aurora-extensions",
    [string]$ExpectedFingerprint = ""
)

$ErrorActionPreference = "Stop"
if (-not $env:JAVA_HOME -and (Test-Path "D:\Android\jbr")) { $env:JAVA_HOME = "D:\Android\jbr" }
$keytool = Join-Path $env:JAVA_HOME "bin\keytool.exe"
if (-not (Test-Path -LiteralPath $keytool)) { $keytool = "keytool" }
foreach ($path in @($PrimaryKeystore, $BackupKeystore)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Keystore not found: $path" }
}
$primaryHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $PrimaryKeystore).Hash.ToLowerInvariant()
$backupHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $BackupKeystore).Hash.ToLowerInvariant()
if ($primaryHash -ne $backupHash) { throw "Primary and backup keystores do not match" }

$password = Read-Host "Production signing password" -AsSecureString
$plainPassword = [Net.NetworkCredential]::new('', $password).Password
$passwordVariable = "AURORA_VERIFY_KEYSTORE_PASSWORD"
try {
    Set-Item -Path "Env:$passwordVariable" -Value $plainPassword
    $certificate = & $keytool -list -v -keystore $PrimaryKeystore -alias $Alias -storepass:env $passwordVariable 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0) { throw "Unable to open keystore with the supplied password and alias" }
    $match = [regex]::Match($certificate, 'SHA256:\s*([0-9A-Fa-f:]+)')
    if (-not $match.Success) { throw "Unable to parse signing fingerprint" }
    $fingerprint = ($match.Groups[1].Value -replace ':', '').ToLowerInvariant()
    if ($ExpectedFingerprint -and ($ExpectedFingerprint -replace ':', '').ToLowerInvariant() -ne $fingerprint) {
        throw "Signing fingerprint does not match the expected production identity"
    }
    Write-Host "Production signing backup verified"
    Write-Host "Certificate SHA-256: $fingerprint"
    Write-Host "Keystore SHA-256: $primaryHash"
} finally {
    Remove-Item -Path "Env:$passwordVariable" -ErrorAction SilentlyContinue
    $plainPassword = $null
}
