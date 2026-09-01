# Production extension signing

The production signing identity is the permanent upgrade identity for every
published Aurora extension. Losing it prevents installed plugins from receiving
normal updates. Leaking it allows an attacker to impersonate repository updates.

## Provision once

Choose two directories outside the Git repository. The second copy should be on
a different encrypted/offline device:

```powershell
.\scripts\provision-production-signing.ps1 `
  -PrimaryDirectory E:\AuroraSecrets\primary `
  -BackupDirectory F:\AuroraSecrets\offline-backup
```

The command refuses existing target files, prompts twice for a password, creates
an RSA-4096 PKCS12 keystore, copies it, compares both file hashes, and writes a
non-secret fingerprint manifest beside each copy. Never commit either directory.

Periodically test both copies:

```powershell
.\scripts\verify-production-signing.ps1 `
  -PrimaryKeystore E:\AuroraSecrets\primary\aurora-extensions.jks `
  -BackupKeystore F:\AuroraSecrets\offline-backup\aurora-extensions.jks `
  -ExpectedFingerprint <64-character-certificate-fingerprint>
```

## GitHub Actions secrets

Configure these repository secrets without placing their values in a file inside
the project:

- `AURORA_EXTENSION_KEYSTORE_BASE64`: base64 bytes of the production keystore.
- `AURORA_EXTENSION_STORE_PASSWORD`: keystore password.
- `AURORA_EXTENSION_KEY_ALIAS`: `aurora-extensions` unless deliberately changed.
- `AURORA_EXTENSION_KEY_PASSWORD`: same password used during provisioning.
- `AURORA_EXTENSION_SIGNING_FINGERPRINT`: lowercase 64-character certificate fingerprint.

After provisioning, configure all five without exposing their values in command
arguments or logs:

```powershell
.\scripts\configure-github-signing.ps1
```

The command verifies the password and manifest fingerprint first, then sends
each value to GitHub CLI over standard input.

The first production publication intentionally changes the current development
signer. Trigger `Build extension repo` once with the explicit signing-key rotation
input enabled. Later builds must leave rotation disabled and will fail closed if
the signing identity changes.

## Recovery rule

Never generate a replacement key simply because one workstation lost its copy.
Recover the exact keystore from the verified offline backup. A genuine key
rotation requires a separately reviewed migration and explicit workflow approval.
