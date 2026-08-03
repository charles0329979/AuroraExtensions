# Aurora ScriptedSource Protocol Design (Phase 2)

> **Status:** **Approved** by user 2026-08-03 (`确认协议`) — implementation of models/validators may proceed only after a separate plan approval.  
> **Still out of scope until later phases:** QuickJS, OkHttp executor wiring, Mihon Source API changes.  
> **Date:** 2026-08-03  
> **Repos:** AuroraExtensions (protocol docs + future `scripted-core` models)  
> **Relation to S1:** S1 was a vertical slice (`pageList` only, Rhino, informal JSON). Phase 2 defines the **versioned contract** that S1-style flows must migrate toward. S1 demo remains valid until a later migration task.

---

## 0. Goals and non-goals

### Goals

1. JS **never** opens sockets or touches OkHttp / Android APIs / filesystem.
2. JS only returns **structured data** (`ScriptResponse` / `ResourceRequest` / domain payloads).
3. Kotlin **validates** every field against allowlists and limits, then may issue HTTP via OkHttp.
4. Clear correlation IDs: `protocolVersion`, `sourceId`, `operationId`, `requestId`.
5. Security defaults: HTTPS-only, host allowlist, header allowlist, size/time caps.

### Non-goals (this phase)

- Embedding QuickJS / Rhino changes
- Changing Mihon `HttpSource` / `ExtensionLoader`
- Implementing the validator or OkHttp adapter
- Expanding S1 demo to all operations

---

## 1. Architecture (data flow only)

```text
Kotlin Orchestrator
  → builds ScriptRequest (ids + operation + inputs + prior ScriptResponse body?)
  → [future] ScriptEngine.eval(manifest, request)   // out of scope now
  → receives ScriptResponse (JSON-shaped)
  → ProtocolValidator.validate(response, manifest, policy)
       OK → for each ResourceRequest: HostPolicy + HeaderPolicy + Limits
            → OkHttp execute → attach body/status into next ScriptRequest (or finish)
       ERR → ScriptError mapped to host failure
```

**Hard rule:** Any `ResourceRequest` emitted by JS is **advice**, not a live call. Kotlin is the only authority that may perform I/O.

---

## 2. Complete protocol design

### 2.1 Envelope identity

Every message that crosses the JS↔Kotlin boundary carries:

| Field | Type | Who sets | Meaning |
|-------|------|----------|---------|
| `protocolVersion` | `Int` | Manifest / Kotlin | Contract version. Phase 2 = **2** |
| `sourceId` | `String` | Manifest | Stable source id (extension-defined, e.g. package + name hash string) |
| `operationId` | `String` | Kotlin | Stable id for the operation kind, e.g. `PAGES` |
| `requestId` | `String` | Kotlin | UUID per orchestration step (one ScriptRequest ↔ one ScriptResponse) |

### 2.2 Roles of the eight models

| Model | Direction | Role |
|-------|-----------|------|
| `ScriptedSourceManifest` | Static (bundled) | Declares source identity, baseUrl, capabilities, host allowlist, limits |
| `ScriptCapability` | In manifest | What operations JS may implement |
| `ScriptedOperation` | Enum / id | `POPULAR` … `PAGES` |
| `ScriptRequest` | Kotlin → JS | One operation invocation (+ optional prior HTTP result) |
| `ScriptResponse` | JS → Kotlin | Structured result: domain data and/or pending `ResourceRequest`s |
| `ResourceRequest` | Inside ScriptResponse | Declared HTTP need (validated before OkHttp) |
| `ResourceInstruction` | Derived by Kotlin | Post-validation, sanitized request Kotlin will execute |
| `ScriptError` | JS or Kotlin | Typed failure |

### 2.3 Operation set

| Operation | `operationId` | Typical ScriptRequest inputs | Typical successful ScriptResponse |
|-----------|---------------|------------------------------|-----------------------------------|
| POPULAR | `POPULAR` | `page: Int` | `mangas: [...]`, `hasNextPage` |
| LATEST | `LATEST` | `page: Int` | same |
| SEARCH | `SEARCH` | `page`, `query`, optional filters | same |
| DETAILS | `DETAILS` | `mangaUrl` or `mangaKey` | `manga: {...}` |
| CHAPTERS | `CHAPTERS` | `mangaUrl` / `mangaKey` | `chapters: [...]` |
| PAGES | `PAGES` | `chapterUrl` / `chapterKey`, optional `html` | `pages: [...]` |

Multi-step pattern (allowed): JS returns `ResourceRequest`s → Kotlin fetches → Kotlin calls JS again with `priorResource` filled → JS returns final domain payload with empty `resources`.

### 2.4 Validation pipeline (Kotlin)

1. Parse JSON → `ScriptResponse` (strict schema; unknown keys **rejected** in v2).
2. Check envelope ids match the open `ScriptRequest`.
3. Check `operationId` ∈ manifest capabilities.
4. For each `ResourceRequest`: scheme, host, method, headers, lengths, timeouts.
5. Strip / reject forbidden headers; **never** take `Host` from JS.
6. Build `ResourceInstruction` (Kotlin-owned) for OkHttp.
7. Enforce response body size / MIME when results return (future executor).

---

## 3. Kotlin data class draft

Package suggestion: `app.aurora.scripted.protocol` (lives in `scripted-core` later; **not implemented this phase**).

```kotlin
package app.aurora.scripted.protocol

/** Phase 2 protocol version constant. */
const val SCRIPTED_PROTOCOL_VERSION: Int = 2

enum class ScriptedOperation {
    POPULAR,
    LATEST,
    SEARCH,
    DETAILS,
    CHAPTERS,
    PAGES,
}

data class ScriptCapability(
    val operations: Set<ScriptedOperation>,
    /** Extra hosts beyond baseUrl host (CDN, image CDN). Lowercase FQDNs. */
    val allowedHosts: Set<String> = emptySet(),
    /** If false, http:// is rejected even for loopback. Default false → HTTPS only. */
    val allowHttp: Boolean = false,
)

data class ScriptedSourceManifest(
    val protocolVersion: Int = SCRIPTED_PROTOCOL_VERSION,
    val sourceId: String,
    val name: String,
    val lang: String,
    val baseUrl: String,
    val capabilities: ScriptCapability,
    /** Optional default UA override (still applied only by Kotlin). */
    val userAgent: String? = null,
    /** Default Referer template or absolute URL; Kotlin may apply if JS omits Referer. */
    val defaultReferer: String? = null,
    val limits: ProtocolLimits = ProtocolLimits(),
)

data class ProtocolLimits(
    val maxUrlLength: Int = 2048,
    val maxHeaderCount: Int = 16,
    val maxHeaderNameLength: Int = 64,
    val maxHeaderValueLength: Int = 1024,
    val maxRequestBodyBytes: Long = 0L, // GET-only in v2; keep 0
    val maxResponseBodyBytes: Long = 2_000_000L,
    val defaultTimeoutMs: Long = 15_000L,
    val maxTimeoutMs: Long = 30_000L,
    val maxRetries: Int = 2,
    val maxResourcesPerResponse: Int = 8,
    val maxMangasPerPage: Int = 50,
    val maxChapters: Int = 500,
    val maxPages: Int = 200,
)

enum class HttpMethod { GET }

enum class RetryPolicy {
    NONE,
    TRANSIENT_NETWORK, // 408/429/5xx + IOException, bounded by maxRetries
}

/**
 * Kotlin → JS.
 */
data class ScriptRequest(
    val protocolVersion: Int,
    val sourceId: String,
    val operationId: ScriptedOperation,
    val requestId: String,
    /** Operation-specific inputs (page, query, urls, filters). */
    val args: ScriptArgs,
    /**
     * Result of the previous ResourceInstruction execution, if this is a continuation step.
     * Null on the first call of an operation.
     */
    val priorResource: PriorResourceResult? = null,
)

data class ScriptArgs(
    val page: Int? = null,
    val query: String? = null,
    val mangaUrl: String? = null,
    val mangaKey: String? = null,
    val chapterUrl: String? = null,
    val chapterKey: String? = null,
    /** Optional pre-fetched HTML when Kotlin already loaded the document. */
    val html: String? = null,
    val filtersJson: String? = null,
)

data class PriorResourceResult(
    val resourceId: String,
    val finalUrl: String,
    val statusCode: Int,
    val contentType: String?,
    /** UTF-8 text for HTML/JSON; binary resources are not re-injected as body in v2. */
    val bodyText: String? = null,
    val truncated: Boolean = false,
    val error: ScriptError? = null,
)

/**
 * JS → Kotlin.
 */
data class ScriptResponse(
    val protocolVersion: Int,
    val sourceId: String,
    val operationId: ScriptedOperation,
    val requestId: String,
    val status: ScriptResponseStatus,
    val error: ScriptError? = null,
    /** Pending HTTP work; empty when domain payload is complete. */
    val resources: List<ResourceRequest> = emptyList(),
    val payload: ScriptPayload? = null,
)

enum class ScriptResponseStatus {
    NEED_RESOURCE,
    COMPLETE,
    FAILED,
}

/**
 * Declared by JS. Not executable until validated into ResourceInstruction.
 */
data class ResourceRequest(
    val resourceId: String,
    val url: String,
    val method: HttpMethod = HttpMethod.GET,
    /** Only allowlisted header names; Host forbidden. */
    val headers: Map<String, String> = emptyMap(),
    val referer: String? = null,
    val expectedContentType: ExpectedContentType = ExpectedContentType.ANY,
    val timeoutMs: Long? = null,
    val retryPolicy: RetryPolicy = RetryPolicy.TRANSIENT_NETWORK,
    val sourceId: String,
)

enum class ExpectedContentType {
    ANY,
    HTML,
    JSON,
    IMAGE,
}

/**
 * Kotlin-owned sanitized instruction after validation.
 * Host header is set exclusively from the URL by Kotlin.
 */
data class ResourceInstruction(
    val resourceId: String,
    val sourceId: String,
    val url: String,
    val method: HttpMethod,
    /** Sanitized headers; never includes Cookie/Authorization/Host from JS. */
    val headers: Map<String, String>,
    val expectedContentType: ExpectedContentType,
    val timeoutMs: Long,
    val retryPolicy: RetryPolicy,
    val maxResponseBodyBytes: Long,
)

data class ScriptError(
    val code: ScriptErrorCode,
    val message: String,
    val retryable: Boolean = false,
    val details: Map<String, String> = emptyMap(),
)

enum class ScriptErrorCode {
    // Protocol / envelope
    INVALID_JSON,
    PROTOCOL_MISMATCH,
    ENVELOPE_MISMATCH,
    UNKNOWN_OPERATION,
    CAPABILITY_DENIED,
    // Resource validation
    SCHEME_FORBIDDEN,
    HOST_NOT_ALLOWED,
    HEADER_FORBIDDEN,
    HEADER_NOT_ALLOWLISTED,
    LIMIT_EXCEEDED,
    INVALID_URL,
    INVALID_METHOD,
    // Runtime (future executor)
    TIMEOUT,
    NETWORK,
    HTTP_STATUS,
    PAYLOAD_TOO_LARGE,
    MIME_MISMATCH,
    // Script
    SCRIPT_THROW,
    SCRIPT_TIMEOUT,
    UNSUPPORTED,
}

/** Domain payloads — only one branch populated per COMPLETE response. */
data class ScriptPayload(
    val mangasPage: MangasPagePayload? = null,
    val manga: MangaPayload? = null,
    val chapters: List<ChapterPayload>? = null,
    val pages: List<PagePayload>? = null,
)

data class MangasPagePayload(
    val mangas: List<MangaPayload>,
    val hasNextPage: Boolean,
)

data class MangaPayload(
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: MangaStatus = MangaStatus.UNKNOWN,
    val initialized: Boolean = false,
)

enum class MangaStatus { UNKNOWN, ONGOING, COMPLETED, LICENSED, PUBLISHING_FINISHED, CANCELLED, ON_HIATUS }

data class ChapterPayload(
    val url: String,
    val name: String,
    val chapterNumber: Float? = null,
    val dateUpload: Long? = null,
    val scanlator: String? = null,
)

data class PagePayload(
    val index: Int,
    val imageUrl: String,
    /** Optional per-page referer; still subject to header policy. */
    val referer: String? = null,
)
```

---

## 4. JSON examples

### 4.1 Manifest (bundled)

```json
{
  "protocolVersion": 2,
  "sourceId": "aurora.scripted.demo",
  "name": "Aurora Scripted",
  "lang": "en",
  "baseUrl": "https://example.com",
  "capabilities": {
    "operations": ["POPULAR", "LATEST", "SEARCH", "DETAILS", "CHAPTERS", "PAGES"],
    "allowedHosts": ["cdn.example.com", "placehold.co"],
    "allowHttp": false
  },
  "userAgent": null,
  "defaultReferer": "https://example.com/",
  "limits": {
    "maxUrlLength": 2048,
    "maxHeaderCount": 16,
    "maxHeaderNameLength": 64,
    "maxHeaderValueLength": 1024,
    "maxResponseBodyBytes": 2000000,
    "defaultTimeoutMs": 15000,
    "maxTimeoutMs": 30000,
    "maxRetries": 2,
    "maxResourcesPerResponse": 8,
    "maxPages": 200
  }
}
```

### 4.2 ScriptRequest — first step PAGES

```json
{
  "protocolVersion": 2,
  "sourceId": "aurora.scripted.demo",
  "operationId": "PAGES",
  "requestId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "args": {
    "chapterUrl": "https://example.com/manga/1/chapter/1",
    "html": null
  },
  "priorResource": null
}
```

### 4.3 ScriptResponse — NEED_RESOURCE

```json
{
  "protocolVersion": 2,
  "sourceId": "aurora.scripted.demo",
  "operationId": "PAGES",
  "requestId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "status": "NEED_RESOURCE",
  "error": null,
  "resources": [
    {
      "resourceId": "chap-html-1",
      "url": "https://example.com/manga/1/chapter/1",
      "method": "GET",
      "headers": {
        "Accept": "text/html"
      },
      "referer": "https://example.com/manga/1",
      "expectedContentType": "HTML",
      "timeout": 15000,
      "retryPolicy": "TRANSIENT_NETWORK",
      "sourceId": "aurora.scripted.demo"
    }
  ],
  "payload": null
}
```

> Note: JSON field `timeout` maps to Kotlin `timeoutMs`. Serializer alias in implementation.

### 4.4 ScriptRequest — continuation

```json
{
  "protocolVersion": 2,
  "sourceId": "aurora.scripted.demo",
  "operationId": "PAGES",
  "requestId": "a1b2c3d4-1111-2222-3333-444455556666",
  "args": {
    "chapterUrl": "https://example.com/manga/1/chapter/1"
  },
  "priorResource": {
    "resourceId": "chap-html-1",
    "finalUrl": "https://example.com/manga/1/chapter/1",
    "statusCode": 200,
    "contentType": "text/html; charset=utf-8",
    "bodyText": "<html>...data-aurora-image=\"https://cdn.example.com/1.jpg\"...</html>",
    "truncated": false,
    "error": null
  }
}
```

### 4.5 ScriptResponse — COMPLETE PAGES

```json
{
  "protocolVersion": 2,
  "sourceId": "aurora.scripted.demo",
  "operationId": "PAGES",
  "requestId": "a1b2c3d4-1111-2222-3333-444455556666",
  "status": "COMPLETE",
  "error": null,
  "resources": [],
  "payload": {
    "pages": [
      {
        "index": 0,
        "imageUrl": "https://cdn.example.com/1.jpg",
        "referer": "https://example.com/manga/1/chapter/1"
      },
      {
        "index": 1,
        "imageUrl": "https://cdn.example.com/2.jpg",
        "referer": "https://example.com/manga/1/chapter/1"
      }
    ]
  }
}
```

### 4.6 ResourceInstruction (Kotlin internal — not from JS)

```json
{
  "resourceId": "chap-html-1",
  "sourceId": "aurora.scripted.demo",
  "url": "https://example.com/manga/1/chapter/1",
  "method": "GET",
  "headers": {
    "Accept": "text/html",
    "Referer": "https://example.com/manga/1",
    "User-Agent": "AuroraReader/1.0",
    "Host": "example.com"
  },
  "expectedContentType": "HTML",
  "timeoutMs": 15000,
  "retryPolicy": "TRANSIENT_NETWORK",
  "maxResponseBodyBytes": 2000000
}
```

`Host` is shown for clarity of ownership; OkHttp typically derives Host from the URL — Kotlin must **not** copy a JS-supplied Host, and should omit setting Host manually unless a deliberate override path exists (default: leave to OkHttp from URL).

---

## 5. Field allowlists and denylists

### 5.1 ResourceRequest — allowed fields only

| Field | JSON key | Notes |
|-------|----------|--------|
| url | `url` | Required |
| method | `method` | Only `GET` in v2 |
| headers | `headers` | Map; names must be allowlisted |
| referer | `referer` | Optional; validated as URL; applied as `Referer` header by Kotlin |
| expectedContentType | `expectedContentType` | Enum |
| timeout | `timeout` / `timeoutMs` | Clamped to `[1, maxTimeoutMs]` |
| retryPolicy | `retryPolicy` | Enum |
| sourceId | `sourceId` | Must equal envelope / manifest `sourceId` |
| resourceId | `resourceId` | Required correlation id |

Any other field → `INVALID_JSON` / schema error.

### 5.2 Header name allowlist (JS → Kotlin)

Case-insensitive match; only these may appear in `ResourceRequest.headers`:

- `Accept`
- `Accept-Language`
- `User-Agent` (optional; Kotlin may override with manifest UA)
- `Referer` (prefer top-level `referer` field; if both set, top-level wins)
- `Cache-Control` (`no-cache` / `max-age=…` only — value pattern validated)

### 5.3 Header denylist (always rejected if present)

- `Cookie`
- `Authorization`
- `Proxy-Authorization`
- `Host` (**JS must never set**; Kotlin/OkHttp owns Host from URL)
- `Proxy-*`
- `X-Forwarded-*`
- `Connection`, `Transfer-Encoding`, `Content-Length`, `Expect`
- Any header name containing `\r`, `\n`, or `:`

### 5.4 URL / scheme policy

| Rule | Behavior |
|------|----------|
| Allowed schemes | `https` default; `http` only if `capabilities.allowHttp == true` **and** host is loopback (`127.0.0.1`, `localhost`, `::1`) for tests |
| Forbidden schemes | `javascript:`, `file:`, `content:`, `data:`, `blob:`, `about:`, `ws:`, `wss:`, … |
| Host | Must equal `URI(baseUrl).host` **or** ∈ `capabilities.allowedHosts` (lowercase ASCII FQDN) |
| Userinfo | Forbidden (`user:pass@host`) |
| Length | `url.length <= limits.maxUrlLength` |

### 5.5 Limits (defaults)

See `ProtocolLimits` above. Violations → `LIMIT_EXCEEDED`.

---

## 6. Error classification

| Code | Origin | Meaning | Typical host mapping |
|------|--------|---------|----------------------|
| `INVALID_JSON` | Kotlin parse | Malformed / unknown fields | Fail operation |
| `PROTOCOL_MISMATCH` | Kotlin | `protocolVersion` ≠ 2 / unsupported | Fail |
| `ENVELOPE_MISMATCH` | Kotlin | `sourceId`/`operationId`/`requestId` ≠ open request | Fail |
| `UNKNOWN_OPERATION` | Kotlin | Bad operation id | Fail |
| `CAPABILITY_DENIED` | Kotlin | Op not in manifest | Fail |
| `SCHEME_FORBIDDEN` | Validator | Bad / dangerous scheme | Fail resource / chapter |
| `HOST_NOT_ALLOWED` | Validator | Host not allowlisted | Fail |
| `HEADER_FORBIDDEN` | Validator | Denylisted header | Fail |
| `HEADER_NOT_ALLOWLISTED` | Validator | Unknown header name | Fail |
| `LIMIT_EXCEEDED` | Validator | Size/count/timeout clamp fail | Fail |
| `INVALID_URL` / `INVALID_METHOD` | Validator | Parse / method | Fail |
| `TIMEOUT` / `NETWORK` / `HTTP_STATUS` | Executor (later) | I/O | Fail or retry per policy |
| `PAYLOAD_TOO_LARGE` / `MIME_MISMATCH` | Executor (later) | Response policy | Fail |
| `SCRIPT_THROW` / `SCRIPT_TIMEOUT` | Engine (later) | JS failure | Fail |
| `UNSUPPORTED` | JS or Kotlin | Login/CAPTCHA/paywall/DRM — **no bypass** | Mark unsupported |

JS may return `status: FAILED` with `error`; Kotlin may also synthesize `ScriptError` without JS.

---

## 7. Backward compatibility strategy

| Version | Status |
|---------|--------|
| **S1 informal** (`pageList` → `{pages, allowedHosts}`) | Supported until migration task; treated as **protocolVersion 1** adapter |
| **protocolVersion 2** | This document; unknown fields rejected |
| **protocolVersion ≥ 3** | Additive fields only behind explicit version bump; old clients reject higher versions |

### Migration path (later tasks, not now)

1. Keep S1 `PageListJs` working.
2. Add `S1PageListAdapter.toScriptResponse(v1Json): ScriptResponse` for `PAGES`.
3. New scripts emit v2 only.
4. Deprecate v1 after one release window.

**Compat rules for v2→v3 (future):**

- New optional fields OK at higher version.
- Removing/renaming fields requires major bump.
- Enum extensions OK if unknown enum → `UNSUPPORTED` / fail closed.

---

## 8. Unit test plan (no engine, no OkHttp yet)

Target module (later): `scripted-core` / `app.aurora.scripted.protocol`.

### 8.1 Schema / parse

- Valid COMPLETE PAGES / NEED_RESOURCE round-trip
- Reject unknown ResourceRequest fields
- Reject envelope mismatch (`requestId` differs)
- Reject `protocolVersion: 1` when expecting 2 (except via explicit S1 adapter tests)

### 8.2 Host / scheme

- Accept `https://example.com/...` when baseUrl host matches
- Accept CDN host in `allowedHosts`
- Reject `http://evil.com` when `allowHttp=false`
- Reject `javascript:alert(1)`, `file:///`, `content://`, `data:text/html,...`
- Reject userinfo URLs

### 8.3 Headers

- Accept `Accept`, `Accept-Language`
- Reject `Cookie`, `Authorization`, `Host` from JS
- Reject non-allowlisted custom headers
- Referer top-level overrides headers map
- Kotlin-built `ResourceInstruction` never copies JS `Host`

### 8.4 Limits

- URL longer than `maxUrlLength` → `LIMIT_EXCEEDED`
- Header count / name / value limits
- `timeout` clamped; above `maxTimeoutMs` → error (fail closed, do not silently raise)
- More than `maxResourcesPerResponse` → error
- More than `maxPages` → error

### 8.5 Capabilities

- `PAGES` allowed when listed
- `SEARCH` denied when not in manifest → `CAPABILITY_DENIED`

### 8.6 Errors

- JS `FAILED` + `UNSUPPORTED` preserves code
- Validator-produced errors have stable `code` strings for logging

**Out of test scope this phase:** Rhino/QuickJS, MockWebServer OkHttp, Mihon UI.

---

## 9. Security boundary checklist (acceptance for this design)

| # | Requirement | Protocol coverage |
|---|-------------|-------------------|
| 1 | Default HTTPS only | `allowHttp=false` default |
| 2 | Host allowlist | baseUrl host ∪ `allowedHosts` |
| 3 | No Cookie/Authorization/proxy/file from JS | Header denylist + scheme ban |
| 4 | Header allowlist | §5.2 |
| 5 | UA / Referer configurable | Manifest + allowlisted headers / `referer` |
| 6 | Host header Kotlin-only | Denylist `Host`; OkHttp from URL |
| 7 | Ban javascript/file/content/data | Scheme policy |
| 8 | Limit URL/header sizes | `ProtocolLimits` |
| 9 | Limit response size / timeout | Limits + executor later |
| 10 | No JS filesystem / Android API | Engine sandbox (later); protocol gives no such surface |

---

## 10. Stop point

This phase delivers **design only**.  

**Not done / must not start without confirmation:**

- Implementing data classes in repo
- QuickJS / Rhino protocol wiring
- OkHttp executor / Mihon `HttpSource` changes

---

## Approval record

- Design approved by user: **2026-08-03** (`确认协议`)
