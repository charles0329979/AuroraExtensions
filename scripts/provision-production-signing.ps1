<#
.SYNOPSIS
  Create one production extension signing identity with a verified second copy.
#>
[CmdletBinding(SupportsShouldProcess, ConfirmImpact = "High")]
param(
    [Parameter(Mandatory)]
    [string]$PrimaryDirectory,
    [Parameter(Mandatory)]
    [string]$BackupDirectory,
    [string]$Alias = "aurora-extensions",
    [string]$DistinguishedName = "CN=Aurora Extensions, OU=Release, O=AuroraReader, C=CN",
    [int]$ValidityDays = 9125,
    [string]$RepoRoot = "",
    [switch]$UseWindowsDialog
)

$ErrorActionPreference = "Stop"
if (-not $RepoRoot) { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path }
if (-not $env:JAVA_HOME -and (Test-Path "D:\Android\jbr")) { $env:JAVA_HOME = "D:\Android\jbr" }
$keytool = Join-Path $env:JAVA_HOME "bin\keytool.exe"
if (-not (Test-Path -LiteralPath $keytool)) { $keytool = "keytool" }

function Get-AbsolutePath {
    param([string]$Path)
    return [IO.Path]::GetFullPath($Path).TrimEnd('\', '/')
}

function Read-PasswordFromWindowsDialog {
    Add-Type -AssemblyName System.Windows.Forms
    Add-Type -AssemblyName System.Drawing
    $form = [Windows.Forms.Form]::new()
    $form.Text = "Aurora 生产签名密钥"
    $form.Size = [Drawing.Size]::new(470, 235)
    $form.StartPosition = "CenterScreen"
    $form.TopMost = $true
    $form.FormBorderStyle = "FixedDialog"
    $form.MaximizeBox = $false
    $form.MinimizeBox = $false

    $description = [Windows.Forms.Label]::new()
    $description.Location = [Drawing.Point]::new(20, 15)
    $description.Size = [Drawing.Size]::new(420, 38)
    $description.Text = "请输入至少 16 位的专用密码。密码只在本机内存中使用，不会写入项目。"
    $form.Controls.Add($description)

    $firstLabel = [Windows.Forms.Label]::new()
    $firstLabel.Location = [Drawing.Point]::new(20, 65)
    $firstLabel.Size = [Drawing.Size]::new(90, 24)
    $firstLabel.Text = "密码"
    $form.Controls.Add($firstLabel)
    $first = [Windows.Forms.TextBox]::new()
    $first.Location = [Drawing.Point]::new(115, 62)
    $first.Size = [Drawing.Size]::new(320, 24)
    $first.UseSystemPasswordChar = $true
    $form.Controls.Add($first)

    $secondLabel = [Windows.Forms.Label]::new()
    $secondLabel.Location = [Drawing.Point]::new(20, 105)
    $secondLabel.Size = [Drawing.Size]::new(90, 24)
    $secondLabel.Text = "重复密码"
    $form.Controls.Add($secondLabel)
    $second = [Windows.Forms.TextBox]::new()
    $second.Location = [Drawing.Point]::new(115, 102)
    $second.Size = [Drawing.Size]::new(320, 24)
    $second.UseSystemPasswordChar = $true
    $form.Controls.Add($second)

    $ok = [Windows.Forms.Button]::new()
    $ok.Location = [Drawing.Point]::new(275, 150)
    $ok.Size = [Drawing.Size]::new(75, 30)
    $ok.Text = "确定"
    $ok.DialogResult = [Windows.Forms.DialogResult]::OK
    $form.AcceptButton = $ok
    $form.Controls.Add($ok)
    $cancel = [Windows.Forms.Button]::new()
    $cancel.Location = [Drawing.Point]::new(360, 150)
    $cancel.Size = [Drawing.Size]::new(75, 30)
    $cancel.Text = "取消"
    $cancel.DialogResult = [Windows.Forms.DialogResult]::Cancel
    $form.CancelButton = $cancel
    $form.Controls.Add($cancel)

    try {
        while ($true) {
            if ($form.ShowDialog() -ne [Windows.Forms.DialogResult]::OK) {
                throw "Production signing was cancelled"
            }
            if ($first.Text.Length -lt 16) {
                [Windows.Forms.MessageBox]::Show("密码必须至少 16 位。", "密码不符合要求") | Out-Null
                continue
            }
            if ($first.Text -ne $second.Text) {
                [Windows.Forms.MessageBox]::Show("两次输入的密码不一致。", "密码不一致") | Out-Null
                continue
            }
            return $first.Text
        }
    } finally {
        $form.Dispose()
    }
}

$primaryRoot = Get-AbsolutePath $PrimaryDirectory
$backupRoot = Get-AbsolutePath $BackupDirectory
$repositoryRoot = Get-AbsolutePath $RepoRoot
if ($primaryRoot.StartsWith($repositoryRoot, [StringComparison]::OrdinalIgnoreCase) -or
    $backupRoot.StartsWith($repositoryRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Signing keys must be stored outside the repository"
}
if ($primaryRoot -eq $backupRoot) { throw "Primary and backup directories must be different" }

$fileName = "aurora-extensions.jks"
$primaryKey = Join-Path $primaryRoot $fileName
$backupKey = Join-Path $backupRoot $fileName
foreach ($target in @($primaryKey, $backupKey)) {
    if (Test-Path -LiteralPath $target) { throw "Refusing to overwrite an existing signing key: $target" }
}

if (-not $PSCmdlet.ShouldProcess("$primaryKey and $backupKey", "Create production signing identity")) { return }

if ($UseWindowsDialog) {
    $plainPassword = Read-PasswordFromWindowsDialog
    $plainConfirmation = $plainPassword
} else {
    $password = Read-Host "Create a production signing password (minimum 16 characters)" -AsSecureString
    $confirmation = Read-Host "Repeat the production signing password" -AsSecureString
    $plainPassword = [Net.NetworkCredential]::new('', $password).Password
    $plainConfirmation = [Net.NetworkCredential]::new('', $confirmation).Password
    if ($plainPassword -ne $plainConfirmation) { throw "Passwords do not match" }
    if ($plainPassword.Length -lt 16) { throw "Production signing password must contain at least 16 characters" }
}

$passwordVariable = "AURORA_PROVISION_KEYSTORE_PASSWORD"
try {
    Set-Item -Path "Env:$passwordVariable" -Value $plainPassword
    New-Item -ItemType Directory -Force -Path $primaryRoot, $backupRoot | Out-Null
    & $keytool -genkeypair `
        -keystore $primaryKey `
        -storetype PKCS12 `
        -alias $Alias `
        -keyalg RSA `
        -keysize 4096 `
        -validity $ValidityDays `
        -dname $DistinguishedName `
        -storepass:env $passwordVariable `
        -keypass:env $passwordVariable
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $primaryKey)) { throw "keytool failed to create the keystore" }

    Copy-Item -LiteralPath $primaryKey -Destination $backupKey
    $primaryHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $primaryKey).Hash.ToLowerInvariant()
    $backupHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $backupKey).Hash.ToLowerInvariant()
    if ($primaryHash -ne $backupHash) { throw "Primary and backup keystore hashes differ" }

    $certificate = & $keytool -list -v -keystore $primaryKey -alias $Alias -storepass:env $passwordVariable 2>&1 | Out-String
    $fingerprintMatch = [regex]::Match($certificate, 'SHA256:\s*([0-9A-Fa-f:]+)')
    if (-not $fingerprintMatch.Success) { throw "Unable to read the production certificate fingerprint" }
    $fingerprint = ($fingerprintMatch.Groups[1].Value -replace ':', '').ToLowerInvariant()
    $manifest = [ordered]@{
        schemaVersion = 1
        createdAt = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
        alias = $Alias
        certificateSha256 = $fingerprint
        keystoreSha256 = $primaryHash
        keyAlgorithm = 'RSA-4096'
        validityDays = $ValidityDays
    }
    $json = ($manifest | ConvertTo-Json) + "`n"
    $utf8 = [Text.UTF8Encoding]::new($false)
    [IO.File]::WriteAllText((Join-Path $primaryRoot 'aurora-extensions-signing.json'), $json, $utf8)
    [IO.File]::WriteAllText((Join-Path $backupRoot 'aurora-extensions-signing.json'), $json, $utf8)

    Write-Host "Production signing identity created and backed up"
    Write-Host "Certificate SHA-256: $fingerprint"
    Write-Host "Keystore SHA-256: $primaryHash"
    Write-Host "Next: configure the five AURORA_EXTENSION_* GitHub Actions secrets described in docs/PRODUCTION_SIGNING.md"
} catch {
    foreach ($target in @($primaryKey, $backupKey)) {
        if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Force }
    }
    throw
} finally {
    Remove-Item -Path "Env:$passwordVariable" -ErrorAction SilentlyContinue
    $plainPassword = $null
    $plainConfirmation = $null
}
