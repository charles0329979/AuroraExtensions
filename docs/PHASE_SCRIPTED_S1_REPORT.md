# ScriptedSource S1 Report

**Date:** 2026-08-03  
**Branch:** `feature/scripted-source-s1`  
**Spec:** `AuroraReader-mihon/docs/superpowers/specs/2026-08-02-scripted-source-s1-design.md`

## Delivered

| Item | Status |
|------|--------|
| `scripted-core` (HostPolicy, PageList parser, Instruction interceptor, PageListJs/Rhino) | Done |
| `aurorascripted` extension APK | Done — 758237 bytes |
| Protocol doc | `docs/SCRIPTED_SOURCE_PROTOCOL.md` |
| Repo index (3 packages) | Stub + Scripted + MangaDex |
| Unit tests | **18/18 OK** via `java -cp … JUnitCore` (Gradle Test Executor broken on this machine: `GradleWorkerMain` CNFE) |
| Device install / reader image | **Pass** 2026-08-03 — ReaderActivity shows placehold “Aurora Scripted 1”, indicator `1 / 2` |

## Verification commands

```powershell
# Compile
$env:JAVA_HOME="D:\Android\jbr"
cd D:\Projects\AuroraExtensions\extensions\aurorascripted
# use cached gradle-8.13 bin
gradle :scripted-core:compileTestKotlin :app:assembleDebug --no-daemon

# Tests (manual JUnit — see scripts or prior session classpath)
# OK (18 tests)

# Repo
cd D:\Projects\AuroraExtensions
.\scripts\generate-repo.ps1 -SkipBuild
```

## Notes

- Engine: **Rhino 1.7.15** (not QuickJS) per plan Global Constraints.
- Chapter HTML / `page_list.js` mirrored in APK `assets/` and embedded constants (ClassLoader cannot open assets).
- Host `AuroraReader-mihon` unchanged.
- No git commit/push in this session (user rule).

## Device evidence (HA1XJZHC / TB331FC)

1. Installed `tachiyomi-all.aurorascripted-v1.6.1.apk` (rebuilt after Page ctor fix)
2. Browse → **Aurora Scripted** → Demo → Chapter 1 → **ReaderActivity**
3. Screenshot: page text “Aurora Scripted 1”, footer **1 / 2**
4. Packages still present: `aurorascripted`, `aurorastub`, `mangadex`

### Runtime fix

`Page(index, imageUrl = …)` / wrong 4th-arg type caused `NoSuchMethodError` against host `Page`.  
Fixed by calling `Page(index, "", imageUrl, null as Uri?)` with tachiyomix stub matching host arity.
