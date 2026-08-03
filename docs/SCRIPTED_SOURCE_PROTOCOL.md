# ScriptedSource Protocol

Aurora Scripted extensions use **JavaScript only to discover URLs / structured data**. Kotlin + OkHttp perform all network I/O under host / header / scheme policy.

## Protocol v2 (current contract)

- **Version:** `protocolVersion = 2`
- **Design:** `docs/superpowers/specs/2026-08-03-scripted-source-protocol-v2-design.md`
- **Kotlin package:** `app.aurora.scripted.protocol` in `scripted-core`
- **Models:** Manifest, Capability, Operation, ScriptRequest/Response, ResourceRequest → ResourceInstruction, ScriptError
- **Operations:** `POPULAR` | `LATEST` | `SEARCH` | `DETAILS` | `CHAPTERS` | `PAGES`
- **Security defaults:** HTTPS-only, host allowlist, header allowlist, size/time caps; JS never sets `Host` / Cookie / Authorization
- **Status:** Models + validators + unit tests landed; engine / OkHttp executor / Mihon wiring still deferred

---

# Protocol v1 (S1 — legacy)

S1 informal `pageList` JSON (`{pages, allowedHosts}`) remains supported until a migration adapter. Treat as **protocolVersion 1**.

## Engine (S1)

- **Rhino 1.7.15** embedded in the extension APK
- Entry function: `pageList(input) -> string | object`
- Default timeout: **5000 ms**
- JS must not open sockets

## Kotlin → JS input

```json
{
  "html": "<html>...</html>",
  "chapterUrl": "https://example.com/chapter/1",
  "baseUrl": "https://example.com"
}
```

## JS → Kotlin output

```json
{
  "pages": [
    {
      "imageUrl": "https://example.com/1.jpg",
      "headers": { "Referer": "https://example.com/" }
    }
  ],
  "allowedHosts": ["cdn.example.com"]
}
```

### Rules

| Rule | Detail |
|------|--------|
| Scheme | `http` / `https` only |
| Host | Must match `baseUrl` host **or** appear in `allowedHosts` |
| Violation | Fail the **entire** chapter |
| Denied headers | `Cookie`, `Authorization`, `Proxy-Authorization` (case-insensitive) |
| Image MIME | `image/jpeg`, `image/jpg`, `image/png`, `image/webp`, `image/gif` |

## S1 catalogue surfaces

| API | Owner |
|-----|--------|
| popular / search / detail / chapters | Kotlin |
| `getPageList` / image URLs | JS via `PageListJs` |
| image bytes | OkHttp + `DownloadInstructionInterceptor` |

## Demo package

- `eu.kanade.tachiyomi.extension.all.aurorascripted`
- Source name: `Aurora Scripted`
- `baseUrl`: `https://aurora.scripted.invalid`
- Chapter HTML bundled; demo images on `placehold.co` (listed in `allowedHosts`)
