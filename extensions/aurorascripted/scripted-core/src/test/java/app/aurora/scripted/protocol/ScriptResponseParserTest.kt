package app.aurora.scripted.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptResponseParserTest {
    @Test
    fun parsesNeedResource() {
        val json = """
            {
              "protocolVersion": 3,
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
                  "headers": { "Accept": "text/html" },
                  "referer": "https://example.com/manga/1",
                  "expectedContentType": "HTML",
                  "timeout": 15000,
                  "retryPolicy": "TRANSIENT_NETWORK",
                  "sourceId": "aurora.scripted.demo"
                }
              ],
              "payload": null
            }
        """.trimIndent()

        val result = ScriptResponseParser.parse(json)
        assertTrue(result is ParseResult.Ok)
        val response = (result as ParseResult.Ok).response
        assertEquals(ScriptResponseStatus.NEED_RESOURCE, response.status)
        assertEquals(1, response.resources.size)
        assertEquals(15000L, response.resources[0].timeoutMs)
        assertEquals("text/html", response.resources[0].headers["Accept"])
    }

    @Test
    fun parsesCompletePages() {
        val json = """
            {
              "protocolVersion": 3,
              "sourceId": "aurora.scripted.demo",
              "operationId": "PAGES",
              "requestId": "a1b2c3d4-1111-2222-3333-444455556666",
              "status": "COMPLETE",
              "error": null,
              "resources": [],
              "payload": {
                "pages": [
                  { "index": 0, "imageUrl": "https://cdn.example.com/1.jpg", "referer": "https://example.com/" },
                  { "index": 1, "imageUrl": "https://cdn.example.com/2.jpg" }
                ]
              }
            }
        """.trimIndent()

        val result = ScriptResponseParser.parse(json)
        assertTrue(result is ParseResult.Ok)
        val pages = (result as ParseResult.Ok).response.payload!!.pages!!
        assertEquals(2, pages.size)
        assertEquals(0, pages[0].index)
    }

    @Test
    fun rejectsUnknownResourceField() {
        val json = """
            {
              "protocolVersion": 3,
              "sourceId": "aurora.scripted.demo",
              "operationId": "PAGES",
              "requestId": "req-1",
              "status": "NEED_RESOURCE",
              "resources": [
                {
                  "resourceId": "r1",
                  "url": "https://example.com/x",
                  "sourceId": "aurora.scripted.demo",
                  "proxy": "http://evil"
                }
              ]
            }
        """.trimIndent()

        val result = ScriptResponseParser.parse(json)
        assertTrue(result is ParseResult.Err)
        assertEquals(ScriptErrorCode.INVALID_JSON, (result as ParseResult.Err).error.code)
    }

    @Test
    fun rejectsBadEnum() {
        val json = """
            {
              "protocolVersion": 3,
              "sourceId": "aurora.scripted.demo",
              "operationId": "PAGES",
              "requestId": "req-1",
              "status": "NOT_A_STATUS"
            }
        """.trimIndent()

        val result = ScriptResponseParser.parse(json)
        assertTrue(result is ParseResult.Err)
        assertEquals(ScriptErrorCode.INVALID_JSON, (result as ParseResult.Err).error.code)
    }
}
