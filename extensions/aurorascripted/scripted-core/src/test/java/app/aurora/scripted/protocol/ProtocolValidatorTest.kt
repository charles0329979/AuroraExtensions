package app.aurora.scripted.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolValidatorTest {
    private val manifest = ScriptedSourceManifest(
        sourceId = "aurora.scripted.demo",
        name = "Aurora Scripted",
        lang = "en",
        baseUrl = "https://example.com",
        capabilities = ScriptCapability(
            operations = setOf(ScriptedOperation.PAGES, ScriptedOperation.POPULAR),
            allowedHosts = setOf("cdn.example.com"),
            allowHttp = false,
        ),
        userAgent = "AuroraReader/1.0",
        defaultReferer = "https://example.com/",
        limits = ProtocolLimits(maxTimeoutMs = 30_000L, maxPages = 200, maxResourcesPerResponse = 8),
    )

    private fun request(
        op: ScriptedOperation = ScriptedOperation.PAGES,
        requestId: String = "req-1",
    ) = ScriptRequest(
        protocolVersion = SCRIPTED_PROTOCOL_VERSION,
        sourceId = manifest.sourceId,
        operationId = op,
        requestId = requestId,
        args = ScriptArgs(chapterUrl = "https://example.com/c/1"),
    )

    @Test
    fun needResourceBuildsInstructionWithoutHost() {
        val response = ScriptResponse(
            protocolVersion = 3,
            sourceId = manifest.sourceId,
            operationId = ScriptedOperation.PAGES,
            requestId = "req-1",
            status = ScriptResponseStatus.NEED_RESOURCE,
            resources = listOf(
                ResourceRequest(
                    resourceId = "chap-html-1",
                    url = "https://example.com/manga/1/chapter/1",
                    headers = mapOf("Accept" to "text/html", "Host" to "evil.com"),
                    referer = "https://example.com/manga/1",
                    expectedContentType = ExpectedContentType.HTML,
                    timeoutMs = 15_000L,
                    sourceId = manifest.sourceId,
                ),
            ),
        )

        // Host in headers should fail before instruction built
        val failed = ProtocolValidator.validate(manifest, request(), response)
        assertTrue(failed is ValidatedScriptOutcome.Failed)
        assertEquals(
            ScriptErrorCode.HEADER_FORBIDDEN,
            (failed as ValidatedScriptOutcome.Failed).error.code,
        )

        val clean = response.copy(
            resources = listOf(
                response.resources[0].copy(headers = mapOf("Accept" to "text/html")),
            ),
        )
        val ok = ProtocolValidator.validate(manifest, request(), clean)
        assertTrue(ok is ValidatedScriptOutcome.NeedResources)
        val instr = (ok as ValidatedScriptOutcome.NeedResources).instructions[0]
        assertTrue(instr.headers.keys.none { it.equals("Host", ignoreCase = true) })
        assertEquals("text/html", instr.headers["Accept"])
        assertEquals("https://example.com/manga/1", instr.headers["Referer"])
        assertEquals("AuroraReader/1.0", instr.headers["User-Agent"])
        assertEquals(15_000L, instr.timeoutMs)
    }

    @Test
    fun completePagesOk() {
        val response = ScriptResponse(
            protocolVersion = 3,
            sourceId = manifest.sourceId,
            operationId = ScriptedOperation.PAGES,
            requestId = "req-1",
            status = ScriptResponseStatus.COMPLETE,
            payload = ScriptPayload(
                pages = listOf(
                    PagePayload(0, "https://cdn.example.com/1.jpg"),
                    PagePayload(1, "https://cdn.example.com/2.jpg"),
                ),
            ),
        )
        val ok = ProtocolValidator.validate(manifest, request(), response)
        assertTrue(ok is ValidatedScriptOutcome.Complete)
        assertEquals(2, (ok as ValidatedScriptOutcome.Complete).payload.pages!!.size)
    }

    @Test
    fun envelopeMismatch() {
        val response = ScriptResponse(
            protocolVersion = 3,
            sourceId = manifest.sourceId,
            operationId = ScriptedOperation.PAGES,
            requestId = "other-id",
            status = ScriptResponseStatus.COMPLETE,
            payload = ScriptPayload(pages = listOf(PagePayload(0, "https://cdn.example.com/1.jpg"))),
        )
        val out = ProtocolValidator.validate(manifest, request(), response)
        assertEquals(
            ScriptErrorCode.ENVELOPE_MISMATCH,
            (out as ValidatedScriptOutcome.Failed).error.code,
        )
    }

    @Test
    fun protocolMismatch() {
        val response = ScriptResponse(
            protocolVersion = 1,
            sourceId = manifest.sourceId,
            operationId = ScriptedOperation.PAGES,
            requestId = "req-1",
            status = ScriptResponseStatus.COMPLETE,
            payload = ScriptPayload(pages = listOf(PagePayload(0, "https://cdn.example.com/1.jpg"))),
        )
        val out = ProtocolValidator.validate(manifest, request(), response)
        assertEquals(
            ScriptErrorCode.PROTOCOL_MISMATCH,
            (out as ValidatedScriptOutcome.Failed).error.code,
        )
    }

    @Test
    fun capabilityDenied() {
        val response = ScriptResponse(
            protocolVersion = 3,
            sourceId = manifest.sourceId,
            operationId = ScriptedOperation.SEARCH,
            requestId = "req-1",
            status = ScriptResponseStatus.COMPLETE,
            payload = ScriptPayload(
                mangasPage = MangasPagePayload(
                    mangas = listOf(MangaPayload(url = "https://example.com/m", title = "t")),
                    hasNextPage = false,
                ),
            ),
        )
        val out = ProtocolValidator.validate(
            manifest,
            request(op = ScriptedOperation.SEARCH),
            response,
        )
        assertEquals(
            ScriptErrorCode.CAPABILITY_DENIED,
            (out as ValidatedScriptOutcome.Failed).error.code,
        )
    }

    @Test
    fun timeoutAboveMaxFailsClosed() {
        val response = ScriptResponse(
            protocolVersion = 3,
            sourceId = manifest.sourceId,
            operationId = ScriptedOperation.PAGES,
            requestId = "req-1",
            status = ScriptResponseStatus.NEED_RESOURCE,
            resources = listOf(
                ResourceRequest(
                    resourceId = "r1",
                    url = "https://example.com/x",
                    timeoutMs = 60_000L,
                    sourceId = manifest.sourceId,
                ),
            ),
        )
        val out = ProtocolValidator.validate(manifest, request(), response)
        assertEquals(
            ScriptErrorCode.LIMIT_EXCEEDED,
            (out as ValidatedScriptOutcome.Failed).error.code,
        )
    }

    @Test
    fun tooManyPages() {
        val pages = (0 until 201).map { PagePayload(it, "https://cdn.example.com/$it.jpg") }
        val response = ScriptResponse(
            protocolVersion = 3,
            sourceId = manifest.sourceId,
            operationId = ScriptedOperation.PAGES,
            requestId = "req-1",
            status = ScriptResponseStatus.COMPLETE,
            payload = ScriptPayload(pages = pages),
        )
        val out = ProtocolValidator.validate(manifest, request(), response)
        assertEquals(
            ScriptErrorCode.LIMIT_EXCEEDED,
            (out as ValidatedScriptOutcome.Failed).error.code,
        )
    }

    @Test
    fun failedUnsupportedPreserved() {
        val response = ScriptResponse(
            protocolVersion = 3,
            sourceId = manifest.sourceId,
            operationId = ScriptedOperation.PAGES,
            requestId = "req-1",
            status = ScriptResponseStatus.FAILED,
            error = ScriptError(ScriptErrorCode.UNSUPPORTED, "login required"),
        )
        val out = ProtocolValidator.validate(manifest, request(), response)
        assertEquals(
            ScriptErrorCode.UNSUPPORTED,
            (out as ValidatedScriptOutcome.Failed).error.code,
        )
    }

    @Test
    fun rejectsJavascriptImageUrl() {
        val response = ScriptResponse(
            protocolVersion = 3,
            sourceId = manifest.sourceId,
            operationId = ScriptedOperation.PAGES,
            requestId = "req-1",
            status = ScriptResponseStatus.COMPLETE,
            payload = ScriptPayload(
                pages = listOf(PagePayload(0, "javascript:alert(1)")),
            ),
        )
        val out = ProtocolValidator.validate(manifest, request(), response)
        assertEquals(
            ScriptErrorCode.SCHEME_FORBIDDEN,
            (out as ValidatedScriptOutcome.Failed).error.code,
        )
    }
}
