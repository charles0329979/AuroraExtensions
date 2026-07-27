# Phase 5 — MangaDex Vendor Report

**Date:** 2026-07-27
**Path:** PRIMARY (pin-build from keiyoushi/extensions-source)
**Status:** Catalogue ready; device verify pending (no adb device at last check)

## Done

1. Spike PRIMARY — pin SHA `72979b95438718647d39b8f6133714a4b9b8e2e0`
2. `scripts/vendor-mangadex.ps1` + `generate-repo.ps1` emit Stub + MangaDex
3. `repo/` has both APKs/icons; `index.min.json` has 2 entries
4. NOTICE / README attribution + safety notes

## Pin

| Field | Value |
|-------|-------|
| Upstream | https://github.com/keiyoushi/extensions-source |
| SHA | `72979b95438718647d39b8f6133714a4b9b8e2e0` |
| Package | `eu.kanade.tachiyomi.extension.all.mangadex` |
| Version | 1.4.211 (code 211) |
| Spike | `docs/superpowers/spikes/2026-07-27-mangadex-build.md` |

## Catalogue

| Path | Role |
|------|------|
| `repo/apk/tachiyomi-all.aurorastub-v1.6.1.apk` | Stub (~12 KB) |
| `repo/apk/tachiyomi-all.mangadex-v1.4.211.apk` | MangaDex (~92 KB) |
| `repo/index.min.json` | 2 entries |

## Device verify

Blocked if tablet offline. When HA1XJZHC connected:

```text
adb install -r repo/apk/tachiyomi-all.mangadex-v1.4.211.apk
adb shell pm path eu.kanade.tachiyomi.extension.all.mangadex
```

Then in App: enable English MangaDex → search → open chapter.

Or after push: jsDelivr
`https://cdn.jsdelivr.net/gh/charles0329979/AuroraExtensions@main/repo/index.min.json`

## Safety

No login/CAPTCHA/paywall/DRM bypass. Public MangaDex API only. Not a Keiyoushi full mirror.