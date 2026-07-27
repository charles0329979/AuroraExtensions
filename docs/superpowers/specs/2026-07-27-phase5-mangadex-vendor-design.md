# Phase 5 Design: Vendor MangaDex into AuroraExtensions

**Date:** 2026-07-27
**Status:** Approved approach (方案 2); awaiting plan approval
**Repos:** `D:\Projects\AuroraExtensions`
**Upstream:** [keiyoushi/extensions-source](https://github.com/keiyoushi/extensions-source) (`src/all/mangadex`, Apache-2.0)

## 1. Goal

Ship a **real, public, no-login** Mihon-compatible extension — **MangaDex** — through the existing AuroraExtensions catalogue so AuroraReader can search/browse/read real titles (not only Aurora Stub demos).

## 2. Locked decisions

| Decision | Choice |
|----------|--------|
| Approach | **方案 2** — vendor MangaDex only (not full Keiyoushi mirror) |
| Safety | No login cracking, CAPTCHA bypass, paywall/DRM bypass |
| Stub | Keep Aurora Stub for regression |
| License | Retain Apache-2.0 attribution (LICENSE/NOTICE + upstream credit) |
| Signing | Rebuild/sign with **Aurora debug keystore** so `repo.json` fingerprint stays consistent with Stub |

## 3. Why MangaDex

- Public REST API (`api.mangadex.org`); reading does not require login.
- Mature Keiyoushi extension (versionCode ~211, `libVersion` 1.4).
- Apache-2.0 source; fits reuse of compliant OSS (scope A).

## 4. Upstream shape (facts)

Module path: `src/all/mangadex/`

- Kotlin: `MangaDex.kt`, `MangaDexHelper.kt`, `MangaDexFilters.kt`, `MangaDexIntl.kt`, `MDConstants.kt`, `dto/*`
- Assets: i18n properties + launcher mipmaps
- Gradle: Keiyoushi `extension` plugin; multi-lang `source { }` entries; `implementation(project(":lib:i18n"))`
- Package (expected): `eu.kanade.tachiyomi.extension.all.mangadex`

## 5. Integration strategy (primary + fallback)

### Primary: Pin-build from upstream monorepo slice

1. Pin a **git commit --trailer "Co-authored-by: Cursor <cursoragent@cursor.com>" SHA** of `keiyoushi/extensions-source`.
2. Checkout (submodule, sparse clone, or `vendor/extensions-source` script) enough of the monorepo to build mangadex + `:lib:i18n`.
3. Assemble APK; **sign with Aurora debug keystore**.
4. Copy APK + icon into `repo/apk` / `repo/icon` with Mihon naming.
5. Extend `scripts/generate-repo.ps1` to index **Stub + MangaDex**.
6. Document pin SHA, upgrade procedure, and attribution.

### Fallback (if Keiyoushi Gradle plugin / monorepo build is too heavy)

English-first **API port** into `extensions/mangadex/` using the same `compileOnly :tachiyomix` pattern as Stub:

- Port DTO + helper logic from upstream (keep license headers).
- Single `lang = "en"` source for Phase 5.
- No auth/bypass features.

Spike in Task 1 chooses primary vs fallback with evidence (build log).

## 6. Catalogue / App behavior

- `repo/index.min.json` lists both extensions.
- Install via existing HTTPS URL (prefer jsDelivr in CN).
- In App: enable **English** (and other langs if multi-lang APK); Trust if needed; Browse MangaDex; search/popular; open chapter.

## 7. Safety / legal notes (must appear in README)

- Extension talks to MangaDex public API only.
- Does not bypass paywalls, DRM, CAPTCHA, or authentication gates.
- Not affiliated with MangaDex or Mihon.
- Aurora repo is **not** a dump of all Keiyoushi extensions.

## 8. Out of scope

- Whole Keiyoushi repo mirror / NSFW bulk sources
- Source Lab Kotlin codegen (Phase 6)
- Extension health CI at scale (Phase 7)
- Changing AuroraReader-mihon Source API
- Auto-push unless operator requests

## 9. Success criteria

1. MangaDex APK builds and is listed in `repo/`.
2. Device: HTTPS install MangaDex; search returns real titles; chapter pages load.
3. Stub still listed.
4. NOTICE credits upstream + pin SHA.
5. No bypass-related code introduced.

## 10. Risks

| Risk | Mitigation |
|------|------------|
| Upstream Gradle too complex | Fallback English API port |
| Multi-lang APK large | Accept for primary; EN-only in fallback |
| Debug signing not production-safe | Document; later dedicated keystore |
| API / CDN geo issues | Verify on tablet with network |
| CI keystore fingerprint drift | Same as Phase 4 |