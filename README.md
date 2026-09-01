# Aurora Extensions

Mihon-compatible **extension repository** for [AuroraReader](https://github.com/aurora-reader/AuroraReader) Phase 5.

Hosts a curated catalogue with the Aurora test extensions and 37 website-source
extension packages (40 indexed packages in total). The website batch is intentionally bounded so
each package can be pinned, signed, and tested instead of mirroring thousands
of unreviewed APKs.

The maintained batch includes Webtoons, Rawkuma, Sen Manga, 咚漫,
漫画1234, 喵趣漫画, 腾讯动漫, 泰拉记事社, 再漫画, 漫蛙(雫),
CCC追漫台, 哔哩轻漫画, zero搬运网, GoDa漫画, 古风漫画, 读漫屋, 滴答漫画,
追漫画, 漫圈子, and 漫士多. See
`docs/BATCH_SOURCES_REPORT.md` for its validation status and limitations.

The connected-device health audit is imported separately under `catalog/`.
Every audited row is retained there, while only a signed and
device-tested implementation is allowed into the user-visible repository.

MangaDex and the website modules are built from pinned
[keiyoushi/extensions-source](https://github.com/keiyoushi/extensions-source)
revisions (see `vendor/PIN.txt` and `vendor/BATCH_PIN.txt`). This is **not** a
full Keiyoushi mirror.

## Layout

```text
AuroraExtensions/
  LICENSE / NOTICE
  README.md
  .github/workflows/build-repo.yml
  scripts/
    sync-source-catalog.ps1        # validate the single source manifest; generate index + roles
    manage-source.ps1              # transactional quarantine/restore with asset rollback
    generate-repo.ps1              # build Stub + MangaDex; write repo/ index
    vendor-mangadex.ps1            # pin checkout / assemble / copy MangaDex APK
    vendor-batch-sources.ps1       # build/copy/verify the twenty-site batch
    import-healthy-sources.ps1     # import all 81 healthy audit rows and implementation state
    verify-repo-integrity.ps1      # hashes, sizes, package metadata, signer
    generate-maintenance-report.ps1 # grade/freshness report for every indexed package
    maintain-repo.ps1              # one fail-closed maintenance command
    verify-device-extensions.ps1   # compare a connected device with the repository index
    get-signing-fingerprint.ps1    # SHA-256 of signing cert (lowercase hex)
    provision-production-signing.ps1 # create primary + verified offline signing copy
    verify-production-signing.ps1 # periodically verify both copies and fingerprint
    configure-github-signing.ps1 # securely configure the five Actions secrets
    check-release-readiness.ps1   # read-only GitHub secrets/Pages/public URL gate
    generate-index-pb.ps1          # stub / follow-up for protobuf index.pb
  extensions/
    aurorastub/                    # buildable Gradle project (compileOnly :tachiyomix)
  vendor/
    PIN.txt / BATCH_PIN.txt        # committed upstream pins and sparse paths
    extensions-source/             # gitignored sparse clone (local builds)
  repo/                            # generated catalogue (committed snapshot; add URL must be HTTPS)
    apk/
    icon/
    index.min.json
    index.json
    repo.json
    health.json
  repo-testing/                    # generated testing catalogue; published separately from stable
    apk/ / icon/ / index.min.json / index.json / repo.json
  archive/                         # previous signed versions retained by Update
  quarantine/                      # withdrawn signed assets retained outside public repo/
  maintenance/policy.json          # reading-chain, freshness, and grade policy
  maintenance/package-roles.json   # generated compatibility view; do not edit by hand
  maintenance/build-plan.json      # generated package/module/version build inventory
  catalog/sources.yaml             # semantic source of truth (JSON-compatible YAML 1.2)
  catalog/sources.schema.json      # source catalogue field contract
  catalog/device-attestations.json # traceable device evidence for newly verified sources
  reports/maintenance-summary.*   # human and machine-readable maintenance result
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
3. Build and verify the curated website batch
4. Copy APKs under `repo/apk/` and icons under `repo/icon/`
5. Write `repo/index.min.json`, `repo/index.json`, and `repo/repo.json`

### Maintenance gate

Edit `catalog/sources.yaml` for package metadata, publication state, channel,
module ownership, and declared sources. Do not hand-edit `repo/index*.json` or
`maintenance/package-roles.json`; regenerate those derived files with:

```powershell
.\scripts\sync-source-catalog.ps1 -Mode WriteDerived
```

The `.yaml` file intentionally uses JSON syntax, which is valid YAML 1.2 and can
be parsed by the existing PowerShell and CI environments without another module.

Inspect one package or move it out of the public repository without deleting its
metadata or signed assets:

```powershell
.\scripts\manage-source.ps1 -Action Status -Package eu.kanade.tachiyomi.extension.zh.example
.\scripts\manage-source.ps1 -Action Quarantine `
  -Package eu.kanade.tachiyomi.extension.zh.example `
  -Reason "reader image chain failed on three consecutive audits"
```

Quarantine moves the APK and icon under `quarantine/stable/`, removes the package
from the generated public index, records the reason and timestamp, and rolls the
whole operation back if regeneration fails. After the implementation and device
evidence are healthy again, restore the exact signed assets with:

```powershell
.\scripts\manage-source.ps1 -Action Restore `
  -Package eu.kanade.tachiyomi.extension.zh.example
```

Create a definition from `catalog/source-definition.template.json`. New packages
must use `channel: testing`; the command verifies package name, version code,
version name, repository signer, and PNG signature before adding anything:

```powershell
.\scripts\manage-source.ps1 -Action Add `
  -DefinitionPath .\new-source.json `
  -ArtifactPath .\tachiyomi-zh.example-v1.4.1.apk `
  -IconPath .\example.png
```

Updating an existing package requires a strictly higher `versionCode`. Stable
publication is deliberately explicit, and the previous signed assets are moved
under `archive/` so the implementation can be recovered:

```powershell
.\scripts\manage-source.ps1 -Action Update `
  -Package eu.kanade.tachiyomi.extension.zh.example `
  -DefinitionPath .\updated-source.json `
  -ArtifactPath .\tachiyomi-zh.example-v1.4.2.apk `
  -IconPath .\example.png `
  -Publish
```

An update cannot remove an existing source ID unless `-AllowIdentityChange` is
explicitly supplied together with an external migration plan. This protects user
library and history identity from accidental rule rewrites.

Run the complete maintenance gate without rebuilding APKs:

```powershell
.\scripts\maintain-repo.ps1 -VerifyOnly
```

To regenerate existing artefacts first, use `-SkipBuild` instead of `-VerifyOnly`.
The gate rejects duplicate or orphaned files, missing icons, mismatched package/version
metadata, changed signers, bad hashes, incomplete health records, blocked candidates in
the public index, and health audits older than the limit in `maintenance/policy.json`.
It writes `repo/health.json` for clients and `reports/maintenance-summary.md` for maintainers.
The scheduled `maintenance-audit.yml` workflow runs this gate every day, so an expired
device audit is reported even when nobody pushes a new commit.

Before a user release, run `scripts/check-release-readiness.ps1`. It checks the
local gate, required GitHub secret names, Pages configuration, and both public
catalogue URLs without reading or printing secret values. See
`docs/RELEASE_READINESS.md` for the one-time production order.

Grade A requires the complete browse → search → details → chapters → pages → image
chain on a real device. Grade B is readable with a documented limitation; C requires
login/challenge or verification; D is broken; Q is quarantined; U has no current audit.
N is reserved for non-reading test fixtures and runtime containers.

Compare a connected development device with the repository without requiring every
optional test container to be installed:

```powershell
.\scripts\verify-device-extensions.ps1 -DeviceSerial <adb-device-serial> `
  -OutputPath reports/device-inventory.json
```

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

Primary stable repository (GitHub Pages from CI):

```text
https://charles0329979.github.io/AuroraExtensions/index.min.json
```

Maintainer/testing repository (do not give this URL to ordinary users):

```text
https://charles0329979.github.io/AuroraExtensions/testing/index.min.json
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
4. MangaDex and website extensions make live requests to third-party services; respect each site's terms, copyright, age restrictions, and rate limits.
5. Host apps load extension code with a child-first class loader — never ship `kotlin-stdlib` inside extension APKs.
6. This repo is **not** a full Keiyoushi extension dump; it is a pinned curated subset.

## License

Apache License 2.0 — see `LICENSE` and `NOTICE`.
