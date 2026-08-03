package app.aurora.scripted.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlSchemePolicyTest {
    private val base = "https://example.com"
    private val allowed = setOf("cdn.example.com", "placehold.co")

    @Test
    fun acceptsHttpsBaseHost() {
        assertNull(
            UrlSchemePolicy.check(
                "https://example.com/manga/1",
                base,
                allowed,
                allowHttp = false,
                maxUrlLength = 2048,
            ),
        )
    }

    @Test
    fun acceptsAllowedCdnHost() {
        assertNull(
            UrlSchemePolicy.check(
                "https://cdn.example.com/1.jpg",
                base,
                allowed,
                allowHttp = false,
                maxUrlLength = 2048,
            ),
        )
    }

    @Test
    fun rejectsHttpWhenAllowHttpFalse() {
        val err = UrlSchemePolicy.check(
            "http://evil.com/x",
            base,
            allowed,
            allowHttp = false,
            maxUrlLength = 2048,
        )
        assertEquals(ScriptErrorCode.SCHEME_FORBIDDEN, err!!.code)
    }

    @Test
    fun rejectsJavascriptScheme() {
        val err = UrlSchemePolicy.check(
            "javascript:alert(1)",
            base,
            allowed,
            allowHttp = false,
            maxUrlLength = 2048,
        )
        assertEquals(ScriptErrorCode.SCHEME_FORBIDDEN, err!!.code)
    }

    @Test
    fun rejectsFileScheme() {
        val err = UrlSchemePolicy.check(
            "file:///etc/passwd",
            base,
            allowed,
            allowHttp = false,
            maxUrlLength = 2048,
        )
        assertEquals(ScriptErrorCode.SCHEME_FORBIDDEN, err!!.code)
    }

    @Test
    fun rejectsDataAndContent() {
        assertEquals(
            ScriptErrorCode.SCHEME_FORBIDDEN,
            UrlSchemePolicy.check(
                "data:text/html,hi",
                base,
                allowed,
                allowHttp = false,
                maxUrlLength = 2048,
            )!!.code,
        )
        assertEquals(
            ScriptErrorCode.SCHEME_FORBIDDEN,
            UrlSchemePolicy.check(
                "content://media/1",
                base,
                allowed,
                allowHttp = false,
                maxUrlLength = 2048,
            )!!.code,
        )
    }

    @Test
    fun rejectsUserinfo() {
        val err = UrlSchemePolicy.check(
            "https://user:pass@example.com/x",
            base,
            allowed,
            allowHttp = false,
            maxUrlLength = 2048,
        )
        assertEquals(ScriptErrorCode.INVALID_URL, err!!.code)
    }

    @Test
    fun rejectsUnknownHost() {
        val err = UrlSchemePolicy.check(
            "https://evil.com/x",
            base,
            allowed,
            allowHttp = false,
            maxUrlLength = 2048,
        )
        assertEquals(ScriptErrorCode.HOST_NOT_ALLOWED, err!!.code)
    }

    @Test
    fun rejectsOverlongUrl() {
        val err = UrlSchemePolicy.check(
            "https://example.com/" + "a".repeat(3000),
            base,
            allowed,
            allowHttp = false,
            maxUrlLength = 2048,
        )
        assertEquals(ScriptErrorCode.LIMIT_EXCEEDED, err!!.code)
    }

    @Test
    fun allowsHttpLoopbackWhenAllowHttp() {
        assertNull(
            UrlSchemePolicy.check(
                "http://127.0.0.1:8080/x",
                "http://127.0.0.1:8080",
                emptySet(),
                allowHttp = true,
                maxUrlLength = 2048,
            ),
        )
    }

    @Test
    fun rejectsHttpNonLoopbackEvenWhenAllowHttp() {
        val err = UrlSchemePolicy.check(
            "http://evil.com/x",
            base,
            allowed,
            allowHttp = true,
            maxUrlLength = 2048,
        )
        assertTrue(err!!.code == ScriptErrorCode.SCHEME_FORBIDDEN)
    }
}
