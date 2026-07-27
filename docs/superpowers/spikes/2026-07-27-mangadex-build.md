# Spike: MangaDex pin-build from keiyoushi/extensions-source

**Date:** 2026-07-27  
**Decision:** **PRIMARY**

## Pin

| Field | Value |
|-------|-------|
| Repo | https://github.com/keiyoushi/extensions-source |
| Ref | `main` (shallow depth 1) |
| SHA | `72979b95438718647d39b8f6133714a4b9b8e2e0` |
| Recorded in | `vendor/PIN.txt` |
| Sparse paths | `src/all/mangadex`, `lib/i18n`, `core`, `compiler`, `gradle`, `common` |

`vendor/extensions-source/` is gitignored. `common/` was fetched via `raw.githubusercontent.com` after `git sparse-checkout add common` hit a connection reset.

## Environment

- `JAVA_HOME=D:\Android\jbr` (OpenJDK 21.0.10)
- `ANDROID_HOME=D:\AndroidSDK` (platforms android-37.0, build-tools 37.0.0)
- Gradle wrapper: 9.6.1 (distribution downloaded from Tencent mirror then installed via local `file:///` URL after services.gradle.org timed out / was too slow)

## Commands tried

```powershell
# Sparse clone
git clone --filter=blob:none --sparse --depth 1 https://github.com/keiyoushi/extensions-source.git vendor/extensions-source
cd vendor/extensions-source
git sparse-checkout set --cone src/all/mangadex lib/i18n
git sparse-checkout add gradle core compiler

# Spike-only settings: load mangadex only (function defs left intact)
# loadAllIndividualExtensions()  → commented
# loadIndividualExtension("all", "mangadex") → enabled

# Local SDK
echo sdk.dir=D:\\AndroidSDK > local.properties

# Gradle dist (focused env fix)
# Official URL timed out (networkTimeout=10000); Tencent mirror OK
# gradle-wrapper.properties: networkTimeout=120000, local file:/// zip

.\gradlew.bat projects --no-daemon
# → :src:all:mangadex present; BUILD SUCCESSFUL in ~17m (cold deps)

.\gradlew.bat :src:all:mangadex:assembleRelease --no-daemon
# → FAILED: missing common/AndroidManifest.xml

# Focused fix: fetch common/
curl raw.githubusercontent.com/.../common/AndroidManifest.xml
curl raw.githubusercontent.com/.../common/proguard-rules.pro

.\gradlew.bat :src:all:mangadex:assembleRelease --no-daemon --no-configuration-cache
# → BUILD SUCCESSFUL in 1m 50s
```

## Errors and fixes

1. **Gradle wrapper download timeout** (`networkTimeout=10000`, services.gradle.org slow)  
   - Fix: download `gradle-9.6.1-bin.zip` from `mirrors.cloud.tencent.com`, point wrapper `distributionUrl` at local `file:///`, bump timeout/retries.

2. **Botched settings.gradle.kts regex** temporarily broke `fun loadAllIndividualExtensions()` → restored via `git checkout`, re-patched with exact line equality.

3. **`processReleaseMainManifest`**: expected `common/AndroidManifest.xml` (and `common/proguard-rules.pro`) missing from sparse tree  
   - **One focused fix:** materialize `common/` from pinned SHA. Rebuild succeeded.

## Build result (PRIMARY)

| Field | Value |
|-------|-------|
| Gradle task | `:src:all:mangadex:assembleRelease` |
| APK path | `vendor/extensions-source/src/all/mangadex/build/outputs/apk/release/tachiyomi-all.mangadex-v1.4.211-release.apk` |
| Package | `eu.kanade.tachiyomi.extension.all.mangadex` |
| versionName | `1.4.211` |
| versionCode | `211` |
| libVersion (upstream) | `1.4` |
| App label | `Tachiyomi: MangaDex` |
| minSdk / targetSdk | 26 / 37 |
| APK size | ~92 KB |

## Signing

Upstream `ExtensionPlugin` uses Android **debug** signing when `signingkey.jks` is absent.

- APK cert SHA-1: `e2478fa954d1810db8b9348122482be2b258bd10`
- Matches `%USERPROFILE%\.android\debug.keystore` alias `androiddebugkey` (pass `android`)
- No re-sign required for Aurora debug install path

## Decision rationale

Pin-build of upstream `src/all/mangadex` + `:lib:i18n` (+ `:core`, `:compiler`, `common`, `gradle/build-logic`) works on the operator machine within the spike budget after one sparse-checkout gap fix. Prefer **PRIMARY** over English-only port under `extensions/mangadex/`.

## Notes for Task 2A

- Keep sparse pin; ensure `common/` is in the vendor script checkout list.
- Prefer `loadIndividualExtension("all", "mangadex")` for CI/local speed (optional; sparse tree alone is enough).
- Document Gradle 9.6.1 mirror/local zip for China-network operators.
- Do not commit the full `vendor/extensions-source` tree; keep `/vendor/extensions-source/` ignored; commit `vendor/PIN.txt` + spike docs.
- Task 2 not started in this spike session (decision-first).
