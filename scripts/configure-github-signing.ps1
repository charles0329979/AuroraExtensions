<#
.SYNOPSIS
  Verify the production keystore and securely configure GitHub Actions secrets.
#>
[CmdletBinding(SupportsShouldProcess, ConfirmImpact = "High")]
param(
    [string]$Keystore = "D:\AuroraSecrets\primary\aurora-extensions.jks",
    [string]$Alias = "aurora-extensions",
    [string]$Repository = "charles0329979/AuroraExtensions"
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path -LiteralPath $Keystore -PathType Leaf)) { throw "Keystore not found: $Keystore" }
$manifestPath = Join-Path (Split-Path -Parent $Keystore) "aurora-extensions-signing.json"
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw "Signing manifest not found: $manifestPath" }
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$expectedFingerprint = ([string]$manifest.certificateSha256 -replace ':', '').ToLowerInvariant()
if ($expectedFingerprint -notmatch '^[0-9a-f]{64}$') { throw "Signing manifest fingerprint is invalid" }

$gh = Get-Command gh -ErrorAction SilentlyContinue
if (-not $gh) { throw "GitHub CLI is not installed" }
& $gh.Source auth status 1>$null 2>$null
if ($LASTEXITCODE -ne 0) { throw "GitHub CLI is not authenticated" }
if (-not $env:JAVA_HOME -and (Test-Path "D:\Android\jbr")) { $env:JAVA_HOME = "D:\Android\jbr" }
$keytool = Join-Path $env:JAVA_HOME "bin\keytool.exe"
if (-not (Test-Path -LiteralPath $keytool)) { $keytool = "keytool" }

function Read-PasswordFromWindowsDialog {
    Add-Type -AssemblyName System.Windows.Forms
    Add-Type -AssemblyName System.Drawing
    $form = [Windows.Forms.Form]::new()
    $form.Text = "配置 Aurora GitHub 生产签名"
    $form.Size = [Drawing.Size]::new(470, 180)
    $form.StartPosition = "CenterScreen"
    $form.TopMost = $true
    $form.FormBorderStyle = "FixedDialog"
    $form.MaximizeBox = $false
    $form.MinimizeBox = $false
    $label = [Windows.Forms.Label]::new()
    $label.Location = [Drawing.Point]::new(20, 18)
    $label.Size = [Drawing.Size]::new(420, 36)
    $label.Text = "请输入刚才创建的生产签名密码。密码不会显示或写入文件。"
    $form.Controls.Add($label)
    $passwordBox = [Windows.Forms.TextBox]::new()
    $passwordBox.Location = [Drawing.Point]::new(20, 62)
    $passwordBox.Size = [Drawing.Size]::new(415, 24)
    $passwordBox.UseSystemPasswordChar = $true
    $form.Controls.Add($passwordBox)
    $ok = [Windows.Forms.Button]::new()
    $ok.Location = [Drawing.Point]::new(275, 102)
    $ok.Size = [Drawing.Size]::new(75, 30)
    $ok.Text = "确定"
    $ok.DialogResult = [Windows.Forms.DialogResult]::OK
    $form.AcceptButton = $ok
    $form.Controls.Add($ok)
    $cancel = [Windows.Forms.Button]::new()
    $cancel.Location = [Drawing.Point]::new(360, 102)
    $cancel.Size = [Drawing.Size]::new(75, 30)
    $cancel.Text = "取消"
    $cancel.DialogResult = [Windows.Forms.DialogResult]::Cancel
    $form.CancelButton = $cancel
    $form.Controls.Add($cancel)
    try {
        if ($form.ShowDialog() -ne [Windows.Forms.DialogResult]::OK) { throw "GitHub signing configuration was cancelled" }
        if (-not $passwordBox.Text) { throw "Password cannot be empty" }
        return $passwordBox.Text
    } finally {
        $form.Dispose()
    }
}

function Set-GitHubSecretFromStandardInput {
    param([string]$Name, [string]$Value)
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $gh.Source
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    foreach ($argument in @("secret", "set", $Name, "--repo", $Repository)) { $start.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $start
    if (-not $process.Start()) { throw "Unable to start GitHub CLI" }
    $process.StandardInput.Write($Value)
    $process.StandardInput.Close()
    $errorText = $process.StandardError.ReadToEnd()
    $process.StandardOutput.ReadToEnd() | Out-Null
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) { throw "Failed to configure $Name`: $errorText" }
}

if (-not $PSCmdlet.ShouldProcess($Repository, "Configure five production signing secrets")) { return }
$plainPassword = Read-PasswordFromWindowsDialog
$passwordVariable = "AURORA_CONFIGURE_KEYSTORE_PASSWORD"
try {
    Set-Item -Path "Env:$passwordVariable" -Value $plainPassword
    $certificate = & $keytool -list -v -keystore $Keystore -alias $Alias -storepass:env $passwordVariable 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0) { throw "Unable to open the keystore with this password" }
    $match = [regex]::Match($certificate, 'SHA256:\s*([0-9A-Fa-f:]+)')
    if (-not $match.Success) { throw "Unable to parse signing certificate fingerprint" }
    $actualFingerprint = ($match.Groups[1].Value -replace ':', '').ToLowerInvariant()
    if ($actualFingerprint -ne $expectedFingerprint) { throw "Keystore fingerprint does not match its manifest" }

    $keystoreBase64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path $Keystore)))
    Set-GitHubSecretFromStandardInput "AURORA_EXTENSION_STORE_PASSWORD" $plainPassword
    Set-GitHubSecretFromStandardInput "AURORA_EXTENSION_KEY_PASSWORD" $plainPassword
    Set-GitHubSecretFromStandardInput "AURORA_EXTENSION_KEY_ALIAS" $Alias
    Set-GitHubSecretFromStandardInput "AURORA_EXTENSION_SIGNING_FINGERPRINT" $actualFingerprint
    Set-GitHubSecretFromStandardInput "AURORA_EXTENSION_KEYSTORE_BASE64" $keystoreBase64
    Write-Host "GitHub production signing secrets configured for $Repository"
    Write-Host "Certificate SHA-256: $actualFingerprint"
} finally {
    Remove-Item -Path "Env:$passwordVariable" -ErrorAction SilentlyContinue
    $plainPassword = $null
    $keystoreBase64 = $null
}
