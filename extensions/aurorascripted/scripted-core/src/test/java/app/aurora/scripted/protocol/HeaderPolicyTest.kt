package app.aurora.scripted.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeaderPolicyTest {
    private val limits = ProtocolLimits()

    @Test
    fun acceptsAllowlistedHeaders() {
        val result = HeaderPolicy.sanitize(
            mapOf("Accept" to "text/html", "Accept-Language" to "en"),
            referer = null,
            limits = limits,
        )
        assertTrue(result is HeaderSanitizeResult.Ok)
        val headers = (result as HeaderSanitizeResult.Ok).headers
        assertEquals("text/html", headers["Accept"])
        assertEquals("en", headers["Accept-Language"])
    }

    @Test
    fun rejectsCookie() {
        val result = HeaderPolicy.sanitize(
            mapOf("Cookie" to "a=b"),
            referer = null,
            limits = limits,
        )
        assertTrue(result is HeaderSanitizeResult.Err)
        assertEquals(ScriptErrorCode.HEADER_FORBIDDEN, (result as HeaderSanitizeResult.Err).error.code)
    }

    @Test
    fun rejectsAuthorization() {
        val result = HeaderPolicy.sanitize(
            mapOf("Authorization" to "Bearer x"),
            referer = null,
            limits = limits,
        )
        assertTrue(result is HeaderSanitizeResult.Err)
        assertEquals(ScriptErrorCode.HEADER_FORBIDDEN, (result as HeaderSanitizeResult.Err).error.code)
    }

    @Test
    fun rejectsHostFromJs() {
        val result = HeaderPolicy.sanitize(
            mapOf("Host" to "evil.com"),
            referer = null,
            limits = limits,
        )
        assertTrue(result is HeaderSanitizeResult.Err)
        assertEquals(ScriptErrorCode.HEADER_FORBIDDEN, (result as HeaderSanitizeResult.Err).error.code)
    }

    @Test
    fun rejectsNonAllowlisted() {
        val result = HeaderPolicy.sanitize(
            mapOf("X-Custom" to "1"),
            referer = null,
            limits = limits,
        )
        assertTrue(result is HeaderSanitizeResult.Err)
        assertEquals(ScriptErrorCode.HEADER_NOT_ALLOWLISTED, (result as HeaderSanitizeResult.Err).error.code)
    }

    @Test
    fun topLevelRefererOverridesHeaderMap() {
        val result = HeaderPolicy.sanitize(
            mapOf("Referer" to "https://from-header.example/"),
            referer = "https://from-top.example/",
            limits = limits,
        )
        assertTrue(result is HeaderSanitizeResult.Ok)
        assertEquals(
            "https://from-top.example/",
            (result as HeaderSanitizeResult.Ok).headers["Referer"],
        )
    }

    @Test
    fun neverIncludesHostInOkResult() {
        val result = HeaderPolicy.sanitize(
            mapOf("Accept" to "*/*"),
            referer = "https://example.com/",
            limits = limits,
            userAgentOverride = "Aurora/1",
        )
        assertTrue(result is HeaderSanitizeResult.Ok)
        val headers = (result as HeaderSanitizeResult.Ok).headers
        assertTrue(headers.keys.none { it.equals("Host", ignoreCase = true) })
        assertEquals("Aurora/1", headers["User-Agent"])
    }

    @Test
    fun rejectsInvalidCacheControl() {
        val result = HeaderPolicy.sanitize(
            mapOf("Cache-Control" to "no-store"),
            referer = null,
            limits = limits,
        )
        assertTrue(result is HeaderSanitizeResult.Err)
    }
}
