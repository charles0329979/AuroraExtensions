# ScriptedSource S1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a Mihon-compatible Aurora demo extension where JS discovers chapter image URLs and Kotlin+OkHttp downloads them under HostPolicy / DownloadInstruction, proven by unit tests and one on-device reader image.

**Architecture:** New Gradle project `AuroraExtensions/extensions/aurorascripted` (clone of `aurorastub` layout) with a JVM-testable `scripted-core` Android library (policy + interceptor + JSON models) and an `app` module hosting `AuroraScriptedHttpSource`, Rhino `ScriptEngine`, and `assets/page_list.js`. Host `AuroraReader-mihon` is not modified. Repo index gains a third APK entry.

**Tech Stack:** Kotlin 2.1.x, AGP 8.8.2, OkHttp 4.12.x (compileOnly in extension; testImplementation for core tests), Rhino `org.mozilla:rhino:1.7.15`, MockWebServer, existing `tachiyomix` compileOnly stub pattern, PowerShell `generate-repo.ps1`.

**Spec:** `D:\Projects\AuroraReader-mihon\docs\superpowers\specs\2026-08-02-scripted-source-s1-design.md`

## Global Constraints

- Primary code root: `D:\Projects\AuroraExtensions` (not AuroraReader-mihon app sources).
- Do **not** modify `NetworkHelper.client`, `ExtensionLoader`, or host Reader/`HttpPageLoader`.
- Do **not** revive Source Contract / ExecutionEngine / ParserPipeline as runtime.
- No login / CAPTCHA / paywall / DRM bypass.
- Extension APK must not package `kotlin-stdlib` (`compileOnly(kotlin("stdlib"))` + packaging excludes, same as Stub).
- JS never opens sockets; only returns JSON URLs + optional safe headers.
- Host policy violation on any page → **fail entire chapter**.
- Header denylist (case-insensitive): `Cookie`, `Authorization`, `Proxy-Authorization`.
- `pageList` JS timeout default: **5 seconds**.
- JS engine for S1: **Rhino 1.7.15** (pure JVM; Spec preferred QuickJS — S1 locks Rhino for unit-testability and no NDK; `ScriptEngine` API must stay engine-swappable).
- Demo chapter HTML: **bundled asset** `assets/fixtures/chapter.html` (no public site dependency); image URLs in JS output must be `https` under `baseUrl` host or `allowedHosts` (use placehold.co **or** same-host MockWebServer in tests; on device use placehold.co with `allowedHosts: ["placehold.co"]`).
- Env for builds: `JAVA_HOME=D:\Android\jbr`, `ANDROID_HOME=D:\AndroidSDK` (or existing machine values).
- Commits: local only, **do not push** unless user explicitly asks; skip commit steps if user has not requested commits.
- Chinese UI communication; code/identifiers in English.

## File map (create)

```text
AuroraExtensions/
  docs/SCRIPTED_SOURCE_PROTOCOL.md
  docs/PHASE_SCRIPTED_S1_REPORT.md
  extensions/aurorascripted/
    settings.gradle.kts
    build.gradle.kts
    gradle.properties          # kotlin.stdlib.default.dependency=false
    gradlew / gradlew.bat / gradle/   # copy from aurorastub
    tachiyomix/               # copy from aurorastub; extend HttpSource stub with client
    scripted-core/
      build.gradle.kts        # com.android.library
      src/main/java/app/aurora/scripted/...
      src/test/java/app/aurora/scripted/...
    app/
      build.gradle.kts
      src/main/AndroidManifest.xml
      src/main/assets/page_list.js
      src/main/assets/fixtures/chapter.html
      src/main/java/.../AuroraScriptedHttpSource.kt
      src/main/java/.../ScriptEngine.kt
      src/main/java/.../PageListResultParser.kt  # or live in scripted-core
  scripts/generate-repo.ps1   # modify: build + index aurorascripted
  repo/icon/<pkg>.png         # copy/adapt stub icon
```

---

### Task 1: scripted-core — HostPolicy + models (TDD)

**Files:**
- Create: `extensions/aurorascripted/` Gradle shell (copy `extensions/aurorastub` → rename namespaces later in Task 3)
- Create: `extensions/aurorascripted/scripted-core/build.gradle.kts`
- Create: `extensions/aurorascripted/scripted-core/src/main/java/app/aurora/scripted/policy/HostPolicy.kt`
- Create: `extensions/aurorascripted/scripted-core/src/main/java/app/aurora/scripted/model/PageListResult.kt`
- Create: `extensions/aurorascripted/scripted-core/src/main/java/app/aurora/scripted/model/PageListResultParser.kt`
- Test: `extensions/aurorascripted/scripted-core/src/test/java/app/aurora/scripted/policy/HostPolicyTest.kt`
- Test: `extensions/aurorascripted/scripted-core/src/test/java/app/aurora/scripted/model/PageListResultParserTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces:
  - `object HostPolicy { fun isAllowed(baseUrl: String, requestUrl: String, allowedHosts: Set<String> = emptySet()): Boolean }`
  - `data class ScriptedPage(val imageUrl: String, val headers: Map<String, String> = emptyMap())`
  - `data class PageListResult(val pages: List<ScriptedPage>, val allowedHosts: Set<String> = emptySet())`
  - `object PageListResultParser { fun parse(json: String): PageListResult }` — throws `IllegalArgumentException` on invalid JSON / empty pages / bad scheme URLs

**scripted-core/build.gradle.kts (essential):**

```kotlin
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "app.aurora.scripted"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = false }
}
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

Include `:scripted-core` in `settings.gradle.kts`.

- [ ] **Step 1: Write failing HostPolicy tests**

```kotlin
package app.aurora.scripted.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostPolicyTest {
    @Test
    fun acceptsSameHost() {
        assertTrue(HostPolicy.isAllowed("https://example.com/base", "https://example.com/a.png"))
    }

    @Test
    fun rejectsOtherHost() {
        assertFalse(HostPolicy.isAllowed("https://example.com", "https://evil.com/a.png"))
    }

    @Test
    fun acceptsAllowedHosts() {
        assertTrue(
            HostPolicy.isAllowed(
                "https://example.com",
                "https://placehold.co/1.png",
                setOf("placehold.co"),
            ),
        )
    }

    @Test
    fun rejectsFtp() {
        assertFalse(HostPolicy.isAllowed("https://example.com", "ftp://example.com/a"))
    }
}
```

- [ ] **Step 2: Run tests — expect FAIL (HostPolicy missing)**

```powershell
cd D:\Projects\AuroraExtensions\extensions\aurorascripted
$env:JAVA_HOME="D:\Android\jbr"
.\gradlew.bat :scripted-core:test --tests app.aurora.scripted.policy.HostPolicyTest
```

Expected: compile error or FAIL — class/method missing.

- [ ] **Step 3: Implement HostPolicy**

```kotlin
package app.aurora.scripted.policy

import java.net.URI

object HostPolicy {
    fun isAllowed(
        baseUrl: String,
        requestUrl: String,
        allowedHosts: Set<String> = emptySet(),
    ): Boolean {
        val baseHost = try { URI(baseUrl).host?.lowercase() } catch (_: Exception) { null }
            ?: return false
        val uri = try { URI(requestUrl) } catch (_: Exception) { return false }
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.lowercase() ?: return false
        if (host == baseHost) return true
        return allowedHosts.any { it.lowercase() == host }
    }
}
```

- [ ] **Step 4: Write failing PageListResultParser tests + implement parser**

Parser rules: require non-empty `pages`; each `imageUrl` must be http(s); strip denylisted header keys from each page; `allowedHosts` optional array of strings.

- [ ] **Step 5: Run `:scripted-core:test` — all PASS**

- [ ] **Step 6: Commit (if user requested commits)**

```text
feat(scripted): add HostPolicy and PageListResult parser
```

---

### Task 2: DownloadInstructionInterceptor + ImageMimePolicy (TDD)

**Files:**
- Create: `scripted-core/src/main/java/app/aurora/scripted/http/DownloadInstruction.kt`
- Create: `scripted-core/src/main/java/app/aurora/scripted/http/ImageMimePolicy.kt`
- Create: `scripted-core/src/main/java/app/aurora/scripted/http/DownloadInstructionInterceptor.kt`
- Create: `scripted-core/src/main/java/app/aurora/scripted/http/DownloadInstructionTag.kt`
- Test: `scripted-core/src/test/java/app/aurora/scripted/http/DownloadInstructionInterceptorTest.kt`

**Interfaces:**
- Consumes: `HostPolicy`
- Produces:
  - `data class DownloadInstruction(val baseUrl: String, val allowedHosts: Set<String>, val headers: Map<String, String>)`
  - `class DownloadInstructionTag` — OkHttp request tag type holding `DownloadInstruction`
  - `object ImageMimePolicy { fun isAllowed(contentType: String?): Boolean }` — allow `image/jpeg|png|webp|gif` and types starting with those prefixes; null/empty → false
  - `class DownloadInstructionInterceptor : Interceptor` — if tag present: HostPolicy check; merge non-denylisted headers; after proceed, MIME check or throw `IOException("IMAGE_BLOCKED")` / `IOException("HOST_NOT_ALLOWED")`

- [ ] **Step 1: Write MockWebServer interceptor tests**

Cover: (a) allowed image 200 + png content-type succeeds; (b) host mismatch throws; (c) Cookie header from instruction is not sent; (d) `text/html` response throws IMAGE_BLOCKED.

- [ ] **Step 2: Run tests — expect FAIL**

- [ ] **Step 3: Implement interceptor + MIME policy**

Denylist: `cookie`, `authorization`, `proxy-authorization`.

- [ ] **Step 4: `:scripted-core:test` PASS**

- [ ] **Step 5: Commit (if requested)** `feat(scripted): add DownloadInstructionInterceptor`

---

### Task 3: aurorascripted app shell + ScriptEngine (Rhino)

**Files:**
- Create/copy: full `extensions/aurorascripted` from `aurorastub` (app + tachiyomix + wrapper)
- Modify: `tachiyomix/.../HttpSource.kt` — add compile-time stubs:

```kotlin
import okhttp3.OkHttpClient

abstract class HttpSource : CatalogueSource {
    abstract val baseUrl: String
    open val versionId: Int = 1
    override val id: Long = 0L
    open val client: OkHttpClient
        get() = throw UnsupportedOperationException("Provided by host at runtime")
}
```

Add `compileOnly("com.squareup.okhttp3:okhttp:4.12.0")` to `:tachiyomix`.

- Modify: `app/build.gradle.kts` — `applicationId = "eu.kanade.tachiyomi.extension.all.aurorascripted"`, `versionName = "1.6.1"`, `versionCode = 1`, dependencies:

```kotlin
compileOnly(project(":tachiyomix"))
compileOnly(kotlin("stdlib"))
implementation(project(":scripted-core"))
implementation("org.mozilla:rhino:1.7.15")
// Do NOT implementation kotlin-stdlib
```

Keep packaging excludes identical to Stub.

- Create: `app/src/main/java/eu/kanade/tachiyomi/extension/all/aurorascripted/ScriptEngine.kt`
- Create: `app/src/main/assets/page_list.js`
- Create: `app/src/main/assets/fixtures/chapter.html`
- Test: prefer putting Rhino integration test in `scripted-core` **or** `app/src/test` if app unit tests are wired; minimum: a JVM test module. Simplest path: add `scripted-core` test `PageListJsFixtureTest` that loads JS string + HTML fixture without Android by extracting pure function `PageListJs.evaluate(script: String, inputJson: String): String` into scripted-core using Rhino (`scripted-core` then `implementation("org.mozilla:rhino:1.7.15")`).

**Revised placement (lock):** put Rhino `PageListJs` evaluator inside **scripted-core** so Task 3 tests stay JVM; app only wires HttpSource.

**Interfaces:**
- Produces: `object PageListJs { fun evaluate(script: String, inputJson: String, timeoutMs: Long = 5_000): String }`
  - Creates Rhino `Context`, sets optimization -1, evaluates script, calls global `function pageList(input) { ... }` by evaluating `pageList(${inputJson})` **or** define script as `function pageList(input){...}` then `cx.evaluateString(..., "pageList(" + inputJson + ")", ...)`
  - On timeout/error: throw `IllegalStateException` with message prefix `SCRIPT_ERROR:`

**page_list.js:**

```javascript
function pageList(input) {
  // input is object with html, chapterUrl, baseUrl (Rhino may pass Java Map — prefer JSON string)
  var data = (typeof input === "string") ? JSON.parse(input) : input;
  var html = data.html || "";
  var re = /data-aurora-image="([^"]+)"/g;
  var pages = [];
  var m;
  while ((m = re.exec(html)) !== null) {
    pages.push({ imageUrl: m[1], headers: { Referer: data.baseUrl + "/" } });
  }
  return JSON.stringify({
    pages: pages,
    allowedHosts: ["placehold.co"]
  });
}
```

**chapter.html:**

```html
<!DOCTYPE html>
<html><body>
  <img data-aurora-image="https://placehold.co/800x1200/png?text=Aurora+Scripted+1" />
  <img data-aurora-image="https://placehold.co/800x1200/png?text=Aurora+Scripted+2" />
</body></html>
```

- [ ] **Step 1: Scaffold Gradle project + PageListJs failing test with fixture HTML/JS inline**

- [ ] **Step 2: Implement PageListJs + assets; test PASS**

- [ ] **Step 3: Commit (if requested)** `feat(scripted): add Rhino PageListJs evaluator`

---

### Task 4: AuroraScriptedHttpSource

**Files:**
- Create: `app/src/main/java/eu/kanade/tachiyomi/extension/all/aurorascripted/AuroraScriptedHttpSource.kt`
- Modify: `AndroidManifest.xml` — same meta pattern as Stub (`tachiyomi.extension` class name)

**Interfaces:**
- Consumes: `HostPolicy`, `PageListResultParser`, `PageListJs`, `DownloadInstructionInterceptor`
- Produces: `class AuroraScriptedHttpSource : HttpSource()` registered as extension entry

**Behavior:**

```kotlin
class AuroraScriptedHttpSource : HttpSource() {
    override val name = "Aurora Scripted"
    override val lang = "en"
    override val baseUrl = "https://aurora.scripted.invalid"
    override val supportsLatest = false

    // At runtime host HttpSource provides network; override client when available.
    // Pattern: build from network.client if field/property exists; else OkHttpClient().
    // Concrete implementation: copy runtime pattern from Mihon HttpSource —
    // use lazy client = baseClient.newBuilder()
    //   .addInterceptor(DownloadInstructionInterceptor())
    //   .build()
    // For compile-time stub without network: open val client override constructing
    // OkHttpClient.Builder().addInterceptor(...).build() is acceptable for S1 demo
    // because getPageList reads bundled HTML and images are absolute placehold.co URLs.

    private val instructionInterceptor = DownloadInstructionInterceptor()

    override val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(instructionInterceptor)
            .build()
    }

    override suspend fun getPopularManga(page: Int): MangasPage = ...
    // One manga: "Aurora Scripted Demo", url "/manga/1"

    override suspend fun getSearchManga(...): MangasPage = popular or filter

    override suspend fun getLatestUpdates(page: Int): MangasPage =
        MangasPage(emptyList(), false)

    override suspend fun getMangaUpdate(...): SMangaUpdate =
        details + one chapter url "/manga/1/chapter/1"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val html = readAsset("fixtures/chapter.html")
        val script = readAsset("page_list.js")
        val input = """{"html":${jsonQuote(html)},"chapterUrl":"$baseUrl${chapter.url}","baseUrl":"$baseUrl"}"""
        val raw = PageListJs.evaluate(script, input, 5_000)
        val result = PageListResultParser.parse(raw)
        for (p in result.pages) {
            if (!HostPolicy.isAllowed(baseUrl, p.imageUrl, result.allowedHosts)) {
                throw IllegalStateException("HOST_NOT_ALLOWED: ${p.imageUrl}")
            }
        }
        // Store instruction for image fetches via interceptor tag:
        // override imageRequest(page) when host API allows; if stub lacks imageRequest,
        // rely on interceptor no-op without tag for placehold.co public images +
        // still validate MIME if tag attached in getImage path.
        // S1 minimum: return Page(index, imageUrl=...) after policy check.
        return result.pages.mapIndexed { i, p -> Page(i, imageUrl = p.imageUrl) }
    }
}
```

**imageRequest tagging (required if host calls `source.client`):** Add override only if tachiyomix/host compile surface includes `imageRequest`. If not in stub, extend tachiyomix with:

```kotlin
open fun imageRequest(page: Page): Request =
    throw UnsupportedOperationException()
```

Then in source:

```kotlin
override fun imageRequest(page: Page): Request {
    val url = page.imageUrl ?: error("null imageUrl")
    val instr = DownloadInstruction(
        baseUrl = baseUrl,
        allowedHosts = setOf("placehold.co"),
        headers = mapOf("Referer" to "$baseUrl/"),
    )
    return Request.Builder()
        .url(url)
        .tag(DownloadInstructionTag::class.java, DownloadInstructionTag(instr))
        .build()
}
```

Wire `DownloadInstructionTag` as `data class DownloadInstructionTag(val instruction: DownloadInstruction)`.

- [ ] **Step 1: Implement source + manifest + readAsset helper**

- [ ] **Step 2: `.\gradlew.bat :app:assembleDebug` — BUILD SUCCESSFUL**

- [ ] **Step 3: Confirm APK size; warn if kotlin-stdlib packaged (Stub lesson: keep << 1MB; Rhino will increase size — document actual bytes)**

- [ ] **Step 4: Commit (if requested)** `feat(scripted): add AuroraScriptedHttpSource demo extension`

---

### Task 5: Protocol doc + generate-repo + index

**Files:**
- Create: `AuroraExtensions/docs/SCRIPTED_SOURCE_PROTOCOL.md` (mirror Spec §6 + `pageList` function name + Rhino note)
- Modify: `scripts/generate-repo.ps1` — build `extensions/aurorascripted`, copy APK as `tachiyomi-all.aurorascripted-v1.6.1.apk`, add index entry (pkg `eu.kanade.tachiyomi.extension.all.aurorascripted`, source name `Aurora Scripted`, lang `en`, baseUrl `https://aurora.scripted.invalid`)
- Create: `repo/icon/eu.kanade.tachiyomi.extension.all.aurorascripted.png` (copy Stub icon)
- Create: `docs/PHASE_SCRIPTED_S1_REPORT.md`
- Copy plan pointer: optional copy of this plan under `AuroraExtensions/docs/superpowers/plans/`

Index entry fields must match existing stub/mangadex shape (`name`, `pkg`, `apk`, `lang`, `code`, `version`, `nsfw`, `sources[]`).

- [ ] **Step 1: Write SCRIPTED_SOURCE_PROTOCOL.md**

- [ ] **Step 2: Update generate-repo.ps1 + icon**

- [ ] **Step 3: Run**

```powershell
cd D:\Projects\AuroraExtensions
$env:JAVA_HOME="D:\Android\jbr"
$env:ANDROID_HOME="D:\AndroidSDK"
.\scripts\generate-repo.ps1
```

Expected: `index.min.json` contains three packages including `aurorascripted`.

- [ ] **Step 4: Write PHASE_SCRIPTED_S1_REPORT.md** (APK size, test commands, device steps)

- [ ] **Step 5: Commit (if requested)** `feat(scripted): publish Aurora Scripted in extension repo index`

---

### Task 6: Device verification + host regression

**Files:** none required (ops)

- [ ] **Step 1: Push or use jsDelivr commit URL** (only if user asks to push; otherwise `adb install` local APK)

Local install path:

```powershell
adb -s HA1XJZHC install -r D:\Projects\AuroraExtensions\repo\apk\tachiyomi-all.aurorascripted-v1.6.1.apk
```

- [ ] **Step 2: In AuroraReader — Trust extension, enable English, open Aurora Scripted → Demo → Chapter 1 → confirm ≥1 image**

- [ ] **Step 3: Smoke Stub + MangaDex still listed/usable**

- [ ] **Step 4: Attach evidence to PHASE report (package list, screenshot optional)**

- [ ] **Step 5: Update** `C:\Users\郭帅齐\.cursor\workflow-state.json` → `phase: feedback`

---

## Spec coverage checklist

| Spec item | Task |
|-----------|------|
| HostPolicy + allowedHosts | T1 |
| PageList JSON protocol | T1 + T5 doc |
| DownloadInstructionInterceptor + MIME + denylist | T2 |
| JS pageList discovery | T3 |
| AuroraScriptedHttpSource Kotlin catalogue + JS pages | T4 |
| Bundled chapter HTML | T3/T4 |
| Repo index / protocol doc | T5 |
| Device ≥1 image + Stub/MangaDex regression | T6 |
| No host NetworkHelper change | All tasks |
| No Contract runtime | All tasks |
| Engine swappable (Rhino locked S1) | T3 Global Constraints |

## Self-review notes

- No TBD placeholders; Rhino vs QuickJS deviation is explicit in Global Constraints.
- Types consistent: `PageListResult` / `DownloadInstruction` / `HostPolicy.isAllowed`.
- `imageRequest` stub extension called out so compile succeeds against tachiyomix.
