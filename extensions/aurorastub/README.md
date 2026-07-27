# Aurora Stub (extension module)

Mihon-compatible **test** extension with hardcoded stub titles only.
Built as part of the parent [AuroraExtensions](../..) repository.

| Field | Value |
|-------|-------|
| Package | `eu.kanade.tachiyomi.extension.all.aurorastub` |
| Class | `AuroraStub` |
| Label | Tachiyomi: Aurora Stub |
| versionName / versionCode | 1.6.1 / 1 |
| extensionLib | 1.6 |

Compiles against local `:tachiyomix` (`compileOnly`). Host provides kotlin-stdlib at runtime — do not package it.

## Build

```powershell
$env:JAVA_HOME="D:\Android\jbr"
$env:ANDROID_HOME="D:\AndroidSDK"
.\gradlew.bat :app:assembleDebug
```

Or from repo root: `..\..\scripts\generate-repo.ps1`