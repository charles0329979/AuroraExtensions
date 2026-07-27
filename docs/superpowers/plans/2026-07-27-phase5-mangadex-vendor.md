# Phase 5 MangaDex Vendor Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development or executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Vendor Keiyoushi MangaDex (Apache-2.0) into AuroraExtensions, rebuild/sign with Aurora debug keystore, publish via existing `repo/` catalogue alongside Stub, verify search/read on device.

**Architecture:** Prefer pin-build of upstream `src/all/mangadex` (+ `:lib:i18n`) at a fixed SHA; fallback to English-only port under `extensions/mangadex/` using Stub`s `compileOnly :tachiyomix` pattern if monorepo build fails. Spec: `docs/superpowers/specs/2026-07-27-phase5-mangadex-vendor-design.md`.

**Tech Stack:** Kotlin, Mihon extension APK, MangaDex public API, PowerShell `generate-repo.ps1`, existing GitHub Actions repo workflow, Apache-2.0 attribution.

## File map

| Path | Role |
|------|------|
| `docs/superpowers/specs/2026-07-27-phase5-mangadex-vendor-design.md` | Design (done) |
| `vendor/extensions-source/` OR `extensions/mangadex/` | Upstream pin or fallback port |
| `scripts/generate-repo.ps1` | Build Stub + MangaDex; write multi-entry index |
| `scripts/vendor-mangadex.ps1` (new) | Fetch pin / build / copy APK |
| `repo/apk/`, `repo/icon/`, `repo/index*.json` | Catalogue artefacts |
| `NOTICE`, `README.md` | Attribution + install notes |
| `docs/PHASE5_MANGADEX_REPORT.md` | Delivery / verification report |
| `.github/workflows/build-repo.yml` | Ensure MangaDex path builds in CI |

## Global constraints

- No login/CAPTCHA/paywall/DRM bypass code
- Do not mirror full Keiyoushi extension set
- Do not push unless user asks
- Keep Stub
- `JAVA_HOME=D:\Android\jbr`, `ANDROID_HOME=D:\AndroidSDK` on operator machine

---

### Task 1: Spike — can we pin-build MangaDex?

**Deliverable:** Written decision PRIMARY vs FALLBACK with build log snippet.

- [ ] Create work dir `D:\Projects\AuroraExtensions\vendor\` (gitignored or submodule — prefer **sparse clone** into `vendor/extensions-source` + record SHA in `vendor/PIN.txt`)
- [ ] Clone/sparse-checkout `keiyoushi/extensions-source` at latest `main` tip; save SHA to `vendor/PIN.txt`
- [ ] Attempt upstream Gradle assemble for mangadex module only (document exact task name from settings)
- [ ] If build succeeds: sign/align with debug keystore; note APK path + package name + version → **PRIMARY**
- [ ] If build fails after one focused fix attempt: stop and choose **FALLBACK**; capture error in spike notes under `docs/superpowers/spikes/2026-07-27-mangadex-build.md`
- [ ] Commit spike notes + PIN (no huge vendor tree if fallback) — only if user previously allowed commits; else leave dirty and note in report

### Task 2A (PRIMARY): Integrate pin-build into repo scripts

**Deliverable:** `generate-repo.ps1` produces Stub + MangaDex entries; APK in `repo/apk/`.

- [ ] Add `scripts/vendor-mangadex.ps1` wrapping pin checkout + assemble + copy
- [ ] Update `generate-repo.ps1` to call vendor script and emit second index object (`pkg`, `apk`, `version`, `sources` array from built metadata or known EN source ids)
- [ ] Copy icon from upstream `res/mipmap-hdpi/ic_launcher.png` → `repo/icon/eu.kanade.tachiyomi.extension.all.mangadex.png`
- [ ] Ensure APK does not bundle conflicting kotlin-stdlib (follow upstream packaging; verify size / zip contents)
- [ ] Run `.\scripts\generate-repo.ps1`; confirm `index.min.json` has 2 entries
- [ ] Update `.github/workflows/build-repo.yml` if extra setup needed for vendor clone

### Task 2B (FALLBACK): English API port under `extensions/mangadex/`

**Deliverable:** Standalone Gradle project mirroring Stub layout; `HttpSource` talking to `api.mangadex.org`.

- [ ] Scaffold `extensions/mangadex/` from `extensions/aurorastub/` (settings, tachiyomix compileOnly, no kotlin in APK)
- [ ] Copy upstream DTO + helper files with license headers; trim to `lang=en` single source
- [ ] Implement popular/latest/search/details/chapters/pages via public API only
- [ ] Wire into `generate-repo.ps1` like Stub
- [ ] Unit-test or script smoke: HTTP GET manga list returns JSON (optional JVM test)

### Task 3: Attribution and docs

**Deliverable:** README/NOTICE updated; Phase 5 report stub filled.

- [ ] Update root `NOTICE` with keiyoushi/extensions-source + pin SHA + MangaDex API note
- [ ] Update `README.md`: hosts Stub + MangaDex; jsDelivr add URL; safety bullets; not a Keiyoushi mirror
- [ ] Write `docs/PHASE5_MANGADEX_REPORT.md` checklist
- [ ] Copy/sync short note to `D:\Projects\AuroraReader-mihon\docs\PHASE5_MANGADEX_REPORT.md` if host docs are the operator brief

### Task 4: Device verification

**Deliverable:** Evidence on HA1XJZHC (or noted blocker).

- [ ] Push only if user asks; else use local HTTPS alternative (jsDelivr needs push) — if not pushed, use `adb install` of MangaDex APK for read smoke, and document that catalogue install needs push
- [ ] Preferred after push: deeplink/add store → install MangaDex → enable English → search a known title → open chapter → confirm pages
- [ ] Confirm Stub still listed
- [ ] Paste `pm path` / versionName and UI evidence into Phase 5 report
- [ ] Mark workflow-state `phase: feedback`

### Task 5: Commit (local only)

**Deliverable:** Local commit(s); no push unless requested.

- [ ] `git status` / `git diff` / recent log style
- [ ] Commit message e.g. `feat: vendor MangaDex extension (Phase 5)`
- [ ] Do **not** push unless user says push

## Verification gates

1. `index.min.json` contains Stub + MangaDex
2. MangaDex APK installs; package `eu.kanade.tachiyomi.extension.all.mangadex` (or documented fallback pkg)
3. Search/read smoke passes OR explicit network blocker recorded
4. NOTICE attribution present

## Rollback

Remove MangaDex APK/index entry; keep Stub; delete vendor dir / mangadex module.