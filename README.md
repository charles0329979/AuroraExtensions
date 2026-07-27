# Aurora Extensions

Mihon-compatible **extension repository** for [AuroraReader](https://github.com/aurora-reader/AuroraReader) Phase 5.

Hosts **two** extensions:

| Extension | Package | Version |
|-----------|---------|---------|
| Aurora Stub (test) | `eu.kanade.tachiyomi.extension.all.aurorastub` | 1.6.1 |
| MangaDex (vendored) | `eu.kanade.tachiyomi.extension.all.mangadex` | 1.4.211 |

MangaDex is built from a pinned [keiyoushi/extensions-source](https://github.com/keiyoushi/extensions-source) SHA (`72979b95438718647d39b8f6133714a4b9b8e2e0`, see `vendor/PIN.txt`). This is **not** a full Keiyoushi mirror — only MangaDex is vendored.

## Layout

```text
AuroraExtensions/
  LICENSE / NOTICE
  README.md
  .github/workflows/build-repo.yml
  scripts/
    generate-repo.ps1              # build Stub + MangaDex; write repo/ index
    vendor-mangadex.ps1            # pin checkout / assemble / copy MangaDex APK
    get-signing-fingerprint.ps1    # SHA-256 of signing cert (lowercase hex)
    generate-index-pb.ps1          # stub / follow-up for protobuf index.pb
  extensions/
    aurorastub/                    # buildable Gradle project (compileOnly :tachiyomix)
  vendor/
    PIN.txt                        # committed pin (SHA + sparse paths)
    extensions-source/             # gitignored sparse clone (local builds)
  repo/                            # generated catalogue (committed snapshot; add URL must be HTTPS)
    apk/
    icon/
    index.min.json
    index.json
    repo.json
```

## Build commands

### Prerequisites

- JDK 17+ (`JAVA_HOME`)
- Android SDK (`ANDROID_HOME`)
- Android debug keystore at `%USERPROFILE%\.android\debug.keystore` (default passwords `android`)
- Network for first-time vendor clone / Gradle deps

### Generate the full repo

```powershell
cd D:\Projects\AuroraExtensions
$env:JAVA_HOME = "D:\Android\jbr"
$env:ANDROID_HOME = "D:\AndroidSDK"
.\scripts\generate-repo.ps1
```

Skip rebuilds when APKs already exist:

```powershell
.\scripts\generate-repo.ps1 -SkipBuild
```

This will:

1. `assembleDebug` the Aurora Stub APK (debug-signed), unless `-SkipBuild`
2. Ensure the MangaDex vendor pin and `assembleRelease` (or copy existing APK with `-SkipBuild`)
3. Copy both APKs under `repo/apk/` and icons under `repo/icon/`
4. Write `repo/index.min.json`, `repo/index.json`, `repo/repo.json` with **two** catalogue entries

### Fingerprint only

```powershell
.\scripts\get-signing-fingerprint.ps1
```

## Add this repo in AuroraReader

**CRITICAL:** Mihon `CreateExtensionRepo` requires an **HTTPS** URL matching `https://.*/index.min.json`.  
**`file://` does NOT work** — the app will reject or fail to load a local path as a repository.

**jsDelivr** (after push to GitHub; replace `<user>`):

```text
https://cdn.jsdelivr.net/gh/<user>/AuroraExtensions@main/repo/index.min.json
```

Primary (GitHub Pages from CI `gh-pages` publish of `repo/`):

```text
https://<user>.github.io/AuroraExtensions/index.min.json
```

Alternate (raw GitHub — base URL is the **parent** of `index.min.json`):

```text
https://raw.githubusercontent.com/<user>/AuroraExtensions/main/repo/index.min.json
```

Local testing **without** a public HTTPS host: use **adb install** of the APKs. Adding a repo still needs HTTPS.

In AuroraReader: **Browse → Extensions → Overflow / Repos → Add** → paste the HTTPS URL → Trust the extension when prompted (debug-signed APKs show as Untrusted until Trust).

`repo.json` sits beside `index.min.json` and supplies display name + `signingKeyFingerprint`.

### `index.pb` (follow-up)

Newer Mihon builds prefer a protobuf catalogue (`index.pb`). This repo ships a working **legacy** `index.min.json` first. See `scripts/generate-index-pb.ps1` for a stub and notes. **Do not** place a fake/broken `index.pb` in `repo/`.

## Signing (TEST ONLY)

- Local and CI use the **Android debug keystore** so fingerprints match for developers who use the default `~/.android/debug.keystore`.
- **Risk:** the debug key is shared and not production-safe.
- Production follow-up: dedicated `keystore/aurora-extensions.jks`, CI secrets, rotate `meta.signingKeyFingerprint`.

## Safety notes

1. Only install extensions from repositories you trust.
2. Debug-signed test APKs are for development devices only.
3. Aurora Stub does not scrape real sites; page images load from `placehold.co` (needs network).
4. MangaDex talks to the public MangaDex API (`api.mangadex.org`); respect MangaDex ToS and rate limits.
5. Host apps load extension code with a child-first class loader — never ship `kotlin-stdlib` inside extension APKs.
6. This repo is **not** a full Keiyoushi extension dump — only Stub + pinned MangaDex.

## License

Apache License 2.0 — see `LICENSE` and `NOTICE`.
