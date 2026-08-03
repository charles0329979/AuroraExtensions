# ScriptedSource Protocol v2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement `protocolVersion = 2` Kotlin models, JSON parse/serialize helpers, and pure validators (host/scheme/header/limits/envelope) with JVM unit tests — no script engine and no OkHttp.

**Architecture:** Add package `app.aurora.scripted.protocol` under existing JVM module `extensions/aurorascripted/scripted-core`. Validators take Manifest + ScriptRequest context and produce either `ResourceInstruction`s / accepted `ScriptPayload` or `ScriptError`. S1 `pageList` JSON remains untouched; optional thin adapter can be a later task.

**Tech Stack:** Kotlin JVM (`scripted-core`), JUnit 4, kotlinx.serialization **or** hand-rolled JSON consistent with existing `PageListResultParser` style (prefer **kotlinx.serialization.json** if dependency resolve is fine; otherwise mirror existing pure-Kotlin parser for protocol DTOs).

**Spec:** `docs/superpowers/specs/2026-08-03-scripted-source-protocol-v2-design.md` (approved 2026-08-03)

## Global Constraints

- Primary root: `D:\Projects\AuroraExtensions`
- Branch: prefer `feature/scripted-source-s1` or new `feature/scripted-protocol-v2` from latest remote tip
- **Do not** add QuickJS / change Rhino wiring
- **Do not** modify OkHttp download path / `DownloadInstructionInterceptor` behavior (may *call into* shared HostPolicy concepts, but do not change S1 image interceptor semantics)
- **Do not** modify Mihon / AuroraReader-mihon Source API
- **Do not** change S1 `PageListJs` / `AuroraScriptedHttpSource` in this plan (except optional unused import-free coexistence)
- Default HTTPS only; `Host` header never taken from JS
- Header allowlist/denylist and limits exactly as spec §5
- Commits only if user requested; do not push unless asked
- Env: `JAVA_HOME=D:\Android\jbr`; run tests via `:scripted-core:compileTestKotlin` + manual `JUnitCore` if Gradle Test Worker is broken on this machine

## File map

```text
extensions/aurorascripted/scripted-core/src/main/java/app/aurora/scripted/protocol/
  ProtocolVersion.kt          // SCRIPTED_PROTOCOL_VERSION = 2
  ScriptedOperation.kt
  ScriptCapability.kt
  ScriptedSourceManifest.kt
  ProtocolLimits.kt
  ScriptRequest.kt            // + ScriptArgs, PriorResourceResult
  ScriptResponse.kt           // + status, ScriptPayload, domain payloads
  ResourceRequest.kt
  ResourceInstruction.kt
  ScriptError.kt              // + ScriptErrorCode
  ExpectedContentType.kt
  HttpMethod.kt
  RetryPolicy.kt
  HeaderPolicy.kt             // allowlist / denylist
  UrlSchemePolicy.kt
  ProtocolValidator.kt        // validate ScriptResponse → ValidatedScriptOutcome
  ScriptResponseParser.kt     // JSON → ScriptResponse (strict)
  ScriptRequestEncoder.kt     // optional: ScriptRequest → JSON for engine later

extensions/aurorascripted/scripted-core/src/test/java/app/aurora/scripted/protocol/
  ProtocolValidatorTest.kt
  ScriptResponseParserTest.kt
  HeaderPolicyTest.kt
  UrlSchemePolicyTest.kt
  EnvelopeValidationTest.kt

docs/SCRIPTED_SOURCE_PROTOCOL.md   // update: point to v2 + keep S1 section as legacy
docs/PHASE_PROTOCOL_V2_REPORT.md   // short verification report
```

---

### Task 1: Core enums + limits + error/models (no JSON yet)

**Files:** Create model/enum files listed above (except parsers/validators).

**Interfaces:**
- Produces: all data classes from spec §3 with defaults matching `ProtocolLimits` / security defaults (`allowHttp = false`)

- [x] **Step 1:** Add failing compile-free skeleton tests that reference `SCRIPTED_PROTOCOL_VERSION` and `ScriptedOperation.PAGES` (will fail until types exist)

- [x] **Step 2:** Implement enums + `ProtocolLimits` + `ScriptError` / `ScriptErrorCode`

- [x] **Step 3:** Implement Manifest / Capability / Request / Response / Resource* / Payload types

- [x] **Step 4:** `compileKotlin` succeeds

- [ ] **Step 5:** Commit if requested: `feat(protocol): add ScriptedSource v2 model types`

---

### Task 2: HeaderPolicy + UrlSchemePolicy (TDD)

**Files:**
- Create: `HeaderPolicy.kt`, `UrlSchemePolicy.kt`
- Test: `HeaderPolicyTest.kt`, `UrlSchemePolicyTest.kt`

**Interfaces:**
- `object HeaderPolicy { fun sanitize(jsHeaders: Map<String, String>, referer: String?): HeaderSanitizeResult }`
- `HeaderSanitizeResult` = `Ok(Map<String,String>)` | `Err(ScriptError)`
- `object UrlSchemePolicy { fun check(url: String, baseUrl: String, allowedHosts: Set<String>, allowHttp: Boolean, maxUrlLength: Int): ScriptError? }`

**Rules (copy from spec):**
- Allowlist: Accept, Accept-Language, User-Agent, Referer, Cache-Control (value pattern)
- Denylist includes Cookie, Authorization, Host, Proxy-*, etc.
- Top-level referer wins over headers[`Referer`]
- Schemes: https default; forbid javascript/file/content/data/blob/about/ws/wss; reject userinfo

- [x] **Step 1:** Write failing tests for allow/deny/referer override/scheme bans

- [x] **Step 2:** Implement policies

- [x] **Step 3:** Run tests (JUnitCore or gradle) — PASS

- [ ] **Step 4:** Commit if requested: `feat(protocol): add header and URL scheme policies`

---

### Task 3: ScriptResponseParser (strict JSON)

**Files:**
- Create: `ScriptResponseParser.kt`
- Test: `ScriptResponseParserTest.kt`

**Interfaces:**
- `object ScriptResponseParser { fun parse(json: String): ScriptResponse }` — throws or returns error wrapper; prefer `Result`-like `ParseResult.Ok | ParseResult.Err(ScriptError)`

**Rules:**
- Require envelope fields
- Unknown keys on ResourceRequest → `INVALID_JSON`
- `timeout` JSON alias → `timeoutMs`
- Empty `pages`/`mangas` when COMPLETE for that op → invalid (op-specific)

- [x] **Step 1:** Failing tests: valid NEED_RESOURCE, valid COMPLETE PAGES, unknown field, bad enum

- [x] **Step 2:** Implement parser (hand parser; no kotlinx.serialization)

- [x] **Step 3:** Tests PASS

- [ ] **Step 4:** Commit if requested: `feat(protocol): parse ScriptResponse JSON strictly`

---

### Task 4: ProtocolValidator (envelope + resources → ResourceInstruction)

**Files:**
- Create: `ProtocolValidator.kt`
- Test: `ProtocolValidatorTest.kt`, `EnvelopeValidationTest.kt`

**Interfaces:**
```kotlin
sealed class ValidatedScriptOutcome {
  data class NeedResources(val instructions: List<ResourceInstruction>) : ValidatedScriptOutcome()
  data class Complete(val payload: ScriptPayload) : ValidatedScriptOutcome()
  data class Failed(val error: ScriptError) : ValidatedScriptOutcome()
}

object ProtocolValidator {
  fun validate(
    manifest: ScriptedSourceManifest,
    request: ScriptRequest,
    response: ScriptResponse,
  ): ValidatedScriptOutcome
}
```

**Rules:**
- Envelope match: protocolVersion, sourceId, operationId, requestId
- Operation ∈ capabilities
- Clamp/reject timeout; build Host-free sanitized headers; Kotlin may add UA from manifest
- **Do not** set Host from JS; do not put Host into instruction headers map (OkHttp derives it)
- resource.sourceId must equal manifest.sourceId
- Max resources / pages / mangas limits

- [x] **Step 1:** Failing tests covering spec §8.2–8.5

- [x] **Step 2:** Implement validator composing HeaderPolicy + UrlSchemePolicy

- [x] **Step 3:** Tests PASS

- [ ] **Step 4:** Commit if requested: `feat(protocol): validate ScriptResponse into ResourceInstruction`

---

### Task 5: Docs + report

**Files:**
- Update: `docs/SCRIPTED_SOURCE_PROTOCOL.md` — add “Protocol v2” section linking to design; mark S1 as legacy
- Create: `docs/PHASE_PROTOCOL_V2_REPORT.md` — list test commands + counts

- [x] **Step 1:** Write docs

- [x] **Step 2:** Re-run full protocol test suite; paste counts into report (34/34 protocol)

- [x] **Step 3:** Stop for user acceptance (`验收` / next phase)

---

## Spec coverage

| Spec section | Task |
|--------------|------|
| Eight models + operations | T1 |
| Header/scheme security | T2 |
| JSON examples parse | T3 |
| Validator pipeline | T4 |
| Error codes used in validator | T2–T4 |
| Unit test plan §8 | T2–T4 |
| No QuickJS / no OkHttp / no Mihon | Global |

## Explicitly deferred

- S1 → v2 adapter
- Script engine binding
- OkHttp executor using `ResourceInstruction`
- Wiring `AuroraScriptedHttpSource` to v2
